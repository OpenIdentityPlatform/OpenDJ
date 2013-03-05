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
 * Copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.ldap.Filter.alwaysFalse;
import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.adapt;
import static org.forgerock.opendj.rest2ldap.Utils.transform;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_WRITE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

/**
 * An attribute mapper which provides a mapping from a JSON value to a single DN
 * valued LDAP attribute.
 */
public final class ReferenceAttributeMapper extends AttributeMapper {

    private boolean isRequired = false;
    private boolean isSingleValued = false;
    private final AttributeDescription ldapAttributeName;
    private final AttributeMapper mapper;
    private WritabilityPolicy writabilityPolicy = READ_WRITE;

    ReferenceAttributeMapper(final AttributeDescription ldapAttributeName,
            final AttributeMapper mapper) {
        this.ldapAttributeName = ldapAttributeName;
        this.mapper = mapper;
    }

    /**
     * Indicates that the LDAP attribute is mandatory and must be provided
     * during create requests.
     *
     * @return This attribute mapper.
     */
    public ReferenceAttributeMapper isRequired() {
        this.isRequired = true;
        return this;
    }

    /**
     * Indicates that multi-valued LDAP attribute should be represented as a
     * single-valued JSON value, rather than an array of values.
     *
     * @return This attribute mapper.
     */
    public ReferenceAttributeMapper isSingleValued() {
        this.isSingleValued = true;
        return this;
    }

    /**
     * Indicates whether or not the LDAP attribute supports updates. The default
     * is {@link WritabilityPolicy#READ_WRITE}.
     *
     * @param policy
     *            The writability policy.
     * @return This attribute mapper.
     */
    public ReferenceAttributeMapper writability(final WritabilityPolicy policy) {
        this.writabilityPolicy = policy;
        return this;
    }

    @Override
    void getLDAPAttributes(final Context c, final JsonPointer jsonAttribute,
            final Set<String> ldapAttributes) {
        ldapAttributes.add(ldapAttributeName.toString());
    }

    @Override
    void getLDAPFilter(final Context c, final FilterType type, final JsonPointer jsonAttribute,
            final String operator, final Object valueAssertion, final ResultHandler<Filter> h) {
        // TODO: only presence and equality matching will be supported. Equality matching will
        // only work for the primary key (whatever that is) by performing a reverse look up to
        // convert the primary key to a DN.
        h.handleResult(alwaysFalse());
    }

    @Override
    void toJSON(final Context c, final Entry e, final ResultHandler<JsonValue> h) {
        final Attribute attribute = e.getAttribute(ldapAttributeName);
        if (attribute == null) {
            h.handleResult(null);
        } else if (attributeIsSingleValued()) {
            final DN dn = attribute.parse().usingSchema(c.getConfig().schema()).asDN();
            readEntry(c, dn, h);
        } else {
            final Set<DN> dns = attribute.parse().usingSchema(c.getConfig().schema()).asSetOfDN();
            final ResultHandler<JsonValue> handler =
                    accumulate(dns.size(), transform(
                            new Function<List<JsonValue>, JsonValue, Void>() {
                                @Override
                                public JsonValue apply(final List<JsonValue> value, final Void p) {
                                    if (value.isEmpty()) {
                                        // No values, so omit the entire JSON object from the resource.
                                        return null;
                                    } else {
                                        // Combine values into a single JSON array.
                                        final List<Object> result =
                                                new ArrayList<Object>(value.size());
                                        for (final JsonValue e : value) {
                                            result.add(e.getObject());
                                        }
                                        return new JsonValue(result);
                                    }
                                }
                            }, h));
            for (final DN dn : dns) {
                readEntry(c, dn, handler);
            }
        }
    }

    @Override
    void toLDAP(final Context c, final JsonValue v, final ResultHandler<List<Attribute>> h) {
        // TODO:
        h.handleResult(Collections.<Attribute> emptyList());
    }

    private boolean attributeIsRequired() {
        return isRequired;
    }

    private boolean attributeIsSingleValued() {
        return isSingleValued || ldapAttributeName.getAttributeType().isSingleValue();
    }

    private void readEntry(final Context c, final DN dn, final ResultHandler<JsonValue> handler) {
        final Set<String> requestedLDAPAttributes = new LinkedHashSet<String>();
        mapper.getLDAPAttributes(c, new JsonPointer(), requestedLDAPAttributes);
        c.getConnection().readEntryAsync(dn, requestedLDAPAttributes,
                new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {

                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        handler.handleError(adapt(error));
                    }

                    @Override
                    public void handleResult(final SearchResultEntry result) {
                        mapper.toJSON(c, result, handler);
                    }
                });
    }

}
