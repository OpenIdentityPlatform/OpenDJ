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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 *
 */

package org.forgerock.opendj.ldap.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.ConditionResult.FALSE;
import static org.forgerock.opendj.ldap.ConditionResult.TRUE;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_OID_NAME;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_OID_OID;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class ObjectIdentifierEqualityMatchingRuleTest extends MatchingRuleTest {
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {};
    }

    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            // value, assertion, expected result

            // numeric OIDs match directly.
            { "2.5.4.3", "2.5.4.3", TRUE },
            { "2.5.4.3", "2.5.4.4", FALSE },   // OID is known
            { "2.5.4.3", "9.9.9.999", FALSE }, // OID is unknown
            { "9.9.9.999", "9.9.9.999", TRUE },
            { "9.9.9.999", "9.9.9.9999", FALSE },

            // OID 'descr' form where assertion values are known to the schema.
            { "2.5.4.3", "cn", TRUE },
            { "2.5.4.3", "commonName", TRUE },
            { "2.5.4.3", "l", FALSE },

            { "cn", "2.5.4.3", TRUE },
            { "commonName", "2.5.4.3", TRUE },
            { "l", "2.5.4.3", FALSE },

            { "cn", "cn", TRUE },
            { "commonName", "cn", TRUE },
            { "cn", "commonName", TRUE },
            { "commonName", "commonName", TRUE },

            { "l", "cn", FALSE },
            { "cn", "l", FALSE },

            /* THESE TESTS FAIL BECAUSE THE ACTUAL RESULT IS FALSE

            // These are undefined because assertion values are unknown to the server. See RFC 4517 4.2.26.
            { "2.5.4.3", "dummy", UNDEFINED },
            { "2.5.4.3", "xxx", UNDEFINED },
            { "9.9.9.999", "foo", UNDEFINED },

            */

            // Strictly speaking this should evaluate to UNDEFINED since 'DUMMY' is not recognized.
            { "dummy", "DUMMY", TRUE },

            // 2.5.4.3 is recognized (it's a numeric OID) so matching can be performed.
            { "dummy", "2.5.4.3", FALSE },
        };
    }

    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_OID_OID);
    }

    @Test
    public void indexingShouldNotBeSensitiveToSchemaChanges() throws Exception {
        // 1) keys should be stable and not impacted by schema changes
        // 2) index queries should work even when the schema element has been removed.

        final String customDefinition = "( 9.9.9.999 NAME ( 'foo' 'bar' ) SUP name )";
        final Schema customSchema = new SchemaBuilder(Schema.getCoreSchema())
                .addAttributeType(customDefinition, true)
                .toSchema();
        final List<ByteString> attributeValues = Arrays.asList(b("9.9.9.999"), b("FOO"), b("cn"));

        // Indexing should be insensitive to the schema.
        checkIndexContainsExpectedKeys(Schema.getCoreSchema(), attributeValues, b("9.9.9.999"), b("foo"), b("cn"));
        checkIndexContainsExpectedKeys(customSchema,           attributeValues, b("9.9.9.999"), b("foo"), b("cn"));

        // Index queries should take advantage of the schema by testing alternative schema element names.
        final ByteString assertionValue = b("bar");
        checkKeysUsedForIndexQuerying(Schema.getCoreSchema(), assertionValue, b("bar"));
        checkKeysUsedForIndexQuerying(customSchema,           assertionValue, b("bar"), b("9.9.9.999"), b("foo"));
    }

    private void checkIndexContainsExpectedKeys(final Schema schema,
                                                final List<ByteString> attributeValues,
                                                final ByteString... expectedKeys) throws DecodeException {
        final MatchingRule mr = schema.getMatchingRule(EMR_OID_OID);
        final Indexer indexer = mr.createIndexers(null).iterator().next();
        final ArrayList<ByteString> keys = new ArrayList<>();
        for (final ByteString value : attributeValues) {
            indexer.createKeys(schema, value, keys);
        }
        assertThat(keys).containsExactly(expectedKeys);
    }

    private void checkKeysUsedForIndexQuerying(final Schema schema,
                                               final ByteString assertionValue,
                                               final ByteString... expectedKeys) throws DecodeException {
        final MatchingRule mr = schema.getMatchingRule(EMR_OID_OID);
        final Assertion assertion = mr.getAssertion(assertionValue);
        final IndexQueryFactory indexQueryFactory = mock(IndexQueryFactory.class);
        assertion.createIndexQuery(indexQueryFactory);
        for (final ByteString key : expectedKeys) {
            verify(indexQueryFactory).createExactMatchQuery(EMR_OID_NAME, key);
        }
        verify(indexQueryFactory).createUnionQuery(anyCollection());
        verifyNoMoreInteractions(indexQueryFactory);
    }


    private ByteString b(final String value) {
        return ByteString.valueOfUtf8(value);
    }
}
