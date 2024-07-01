/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.util.StaticUtils.toLowerCase;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_OID_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

/**
 * This class defines the objectIdentifierMatch matching rule defined in X.520
 * and referenced in RFC 4517. This expects to work on OIDs and will match
 * either an attribute/objectclass name or a numeric OID. NOTE: This matching
 * rule requires a schema to lookup object identifiers in the descriptor form.
 */
final class ObjectIdentifierEqualityMatchingRuleImpl extends AbstractMatchingRuleImpl {
    /**
     * NOTE: this matching rule is used by the "objectClass" attribute type which is frequently used in filters and
     * which is also usually indexed for equality. Unfortunately, it is non-trivial to implement correctly and there
     * are a number of subtleties that need to be understood.
     *
     * Comparing two values for equality
     * =================================
     *
     * The LDAP RFCs describe the algorithm for comparing assertion values with attribute values, but never discusses
     * how to compare two attribute values for equality in order to detect duplicate or missing values during updates.
     *
     * Given two schema elements:
     *
     * - object class with OID 1.2.3 and name "xxx"
     * - attribute type with OID 4.5.6 and name "xxx"
     *
     * When:
     *
     * - an attribute "oids" whose syntax is OID and which has the value "xxx"
     *
     * Then:
     *
     * - the filter "(oids=1.2.3)" matches, as does "(oids=4.5.6)", as does "(oids=xxx)".
     *
     * However, if the attribute's value is "1.2.3" then only the filters "(oids=1.2.3)" and "(oids=xxx)" match. In
     * order to compare two values with each other when enforcing set semantics in an LDAP attribute we need a function
     * which is reflexive, symmetric, and transitive. While the first two are possible, the last is not:
     *
     * - reflexive: 1.2.3 == 1.2.3, xxx == xxx
     * - symmetric: 1.2.3 == xxx, xxx == 1.2.3
     * - transitive: 1.2.3 == xxx, xxx = 4.5.6, 1.2.3 != 4.5.6
     *
     * The implication is that it is impossible to implement a reliable normalization method. There are three
     * options:
     *
     * 1) avoid resolving OID names to numeric OIDs during normalization and, instead, convert the OID to lower-case.
     *    This approach has the undesirable effect of allowing users to add equivalent object classes to entries. For
     *    example, the OIDs "2.5.6.6" and "person" are equivalent, but would have different normalized representations
     *
     * 2) resolve OID names to their equivalent numeric OID. For example, the normalized representation of "Person"
     *    would be "2.5.6.6". Unfortunately, there are rare cases where two schema elements share the same name - one
     *    can imagine having a custom object class called "manager" which would clash with the standard attribute
     *    type "manager". In this situation, the algorithm must choose whether to convert the value to the object
     *    class's numeric OID or the attribute type's. Unfortunately, this approach suffers from the same problem as
     *    the first if it happens to prioritize the wrong type of schema element
     *
     * 3) as (2) but resolve numeric OIDs to their "primary" OID name. For example, the normalized representation of
     *    "2.5.6.6" would be "person". The drawback with this approach is that an OID valued attribute cannot
     *    contain two values having the same "primary" name. In the example above, the attribute "oids" cannot
     *    contain the values "1.2.3" and "4.5.6" because they both share the same normalized primary name "xxx". Such
     *    collisions will be extremely rare in practice because attributes rarely reference a heterogeneous set of
     *    schema elements, such as a mix of attribute types and object classes. For example, the "objectClass"
     *    attribute should only contain object class OIDs whose names should never collide.
     *
     * Option (3) has been chosen as the normalization strategy as it provides the best compromise.
     *
     * Indexes
     * =======
     *
     * Indexing poses a different problem because indexes are persistent and rebuilding them is a relatively
     * expensive operation. If index keys depend on the schema, as is the case for the normalization algorithm (3)
     * above, then changes to the schema, e.g. the addition or removal of schema elements, may require an index
     * rebuild. In the above example, changing the primary name of one of the schema elements from "xxx" to "yyy"
     * invalidates any indexes. To avoid the need to rebuild indexes whenever the schema changes we use a simple
     * normalization algorithm - option (1) above - for generating index keys and employ a more complex algorithm for
     * querying the indexes during search operations:
     *
     * 1) compute all the possible aliases of the assertion value's OID using the current schema. In the earlier
     *    example, a filter of the form "(oids=xxx)" would yield the keys "xxx", "1.2.3", and "4.5.6", whereas the
     *    filter "(oids=1.2.3)" would only yield the keys "1.2.3" and "xxx"
     *
     * 2) perform an exact match index query against the index for each key
     *
     * 3) perform a union of the combined results.
     *
     * This algorithm may yield false positives in very rare cases when the schema contains many schema elements
     * sharing the same name.
     */

    ObjectIdentifierEqualityMatchingRuleImpl() {
        // Nothing to do.
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue) throws DecodeException {
        return getAssertion(schema, EMR_OID_NAME, assertionValue);
    }

    @Override
    public Collection<? extends Indexer> createIndexers(final IndexingOptions options) {
        return Collections.singleton(new Indexer() {
            @Override
            public String getIndexID() {
                return EMR_OID_NAME;
            }

            @Override
            public void createKeys(final Schema schema, final ByteSequence value, final Collection<ByteString> keys)
                    throws DecodeException {
                // TODO: optimize - avoid converting to/from string + validate syntax.
                final String oid = toLowerCase(value.toString()).trim();
                keys.add(ByteString.valueOfUtf8(oid));
            }

            @Override
            public String keyToHumanReadableString(final ByteSequence key) {
                return key.toByteString().toString();
            }
        });
    }

    static Assertion getAssertion(final Schema schema, final String indexId, final ByteSequence assertionValue) {
        return new Assertion() {
            // TODO: optimize.
            final String oid = toLowerCase(assertionValue.toString()).trim();
            final List<ByteString> candidates = getCandidates(schema, oid);

            @Override
            public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
                return ConditionResult.valueOf(candidates.contains(normalizedAttributeValue.toByteString()));
            }

            @Override
            public <T> T createIndexQuery(final IndexQueryFactory<T> factory) throws DecodeException {
                final List<T> subQueries = new ArrayList<>(candidates.size());
                for (final ByteString candidate : candidates) {
                    subQueries.add(factory.createExactMatchQuery(indexId, candidate));
                }
                return factory.createUnionQuery(subQueries);
            }
        };
    }

    private static List<ByteString> getCandidates(final Schema schema, final String oid) {
        // TODO: optimize - avoid double lookups.

        // The set of candidates is likely to be small, usually 2 or 3, so avoid the memory overhead of using a Set
        // and instead store the candidates in a small array.
        final List<ByteString> candidates = new ArrayList<>(3);
        candidates.add(ByteString.valueOfUtf8(oid));
        if (schema.hasObjectClass(oid)) { // Careful of placeholders
            final ObjectClass oc = schema.getObjectClass(oid);
            addCandidates(candidates, oc.getOID(), oc.getNames());
        }
        if (schema.hasAttributeType(oid)) { // Careful of placeholders
            final AttributeType at = schema.getAttributeType(oid);
            addCandidates(candidates, at.getOID(), at.getNames());
        }
        if (schema.hasSyntax(oid)) {
            final Syntax syntax = schema.getSyntax(oid);
            addCandidates(candidates, syntax.getOID(), Collections.<String>emptyList());
        }
        if (schema.hasMatchingRule(oid)) {
            final MatchingRule mr = schema.getMatchingRule(oid);
            addCandidates(candidates, mr.getOID(), mr.getNames());
        }
        if (schema.hasNameForm(oid)) {
            final NameForm nf = schema.getNameForm(oid);
            addCandidates(candidates, nf.getOID(), nf.getNames());
        }
        return candidates;
    }


    private static void addCandidates(final List<ByteString> candidates,
                                      final String numericOid,
                                      final List<String> names) {
        addIfNotPresent(candidates, ByteString.valueOfUtf8(numericOid));
        for (final String name : names) {
            addIfNotPresent(candidates, ByteString.valueOfUtf8(toLowerCase(name)));
        }
    }

    private static void addIfNotPresent(final List<ByteString> candidates, final ByteString candidate) {
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    @Override
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) throws DecodeException {
        // TODO: optimize - avoid converting to/from string + validate syntax + avoid double lookups.
        final String oid = toLowerCase(value.toString()).trim();
        if (schema.hasObjectClass(oid)) { // Careful of placeholders
            return ByteString.valueOfUtf8(toLowerCase(schema.getObjectClass(oid).getNameOrOID()));
        }
        if (schema.hasAttributeType(oid)) { // Careful of placeholders
            return ByteString.valueOfUtf8(toLowerCase(schema.getAttributeType(oid).getNameOrOID()));
        }
        if (schema.hasSyntax(oid)) {
            return ByteString.valueOfUtf8(toLowerCase(schema.getSyntax(oid).getOID()));
        }
        if (schema.hasMatchingRule(oid)) {
            return ByteString.valueOfUtf8(toLowerCase(schema.getMatchingRule(oid).getNameOrOID()));
        }
        if (schema.hasNameForm(oid)) {
            return ByteString.valueOfUtf8(toLowerCase(schema.getNameForm(oid).getNameOrOID()));
        }
        return ByteString.valueOfUtf8(oid);
    }
}
