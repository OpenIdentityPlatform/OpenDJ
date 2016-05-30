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

import static org.forgerock.opendj.ldap.ConditionResult.FALSE;
import static org.forgerock.opendj.ldap.ConditionResult.TRUE;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_OID_FIRST_COMPONENT_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class ObjectIdentifierFirstComponentEqualityMatchingRuleTest extends MatchingRuleTest {
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {};
    }

    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        // This is defined in the core schema so the names 'cn' and 'commonName' will be recognized. However 'dummy'
        // is not part of the core schema definition so it won't be recognized by the server.
        final String cnDefinition = "( 2.5.4.3 NAME ( 'cn' 'commonName' 'dummy' ) SUP name )";

        // Matching should be supported against the numeric OID, but the names are not in the core schema so cannot
        // be used in assertion values.
        final String customDefinition = "( 9.9.9.999 NAME ( 'foo' 'bar' ) SUP name )";

        return new Object[][] {
            // numeric OIDs match directly.
            { cnDefinition, "2.5.4.3", TRUE },
            { cnDefinition, "2.5.4.4", FALSE },   // OID is known
            { cnDefinition, "9.9.9.999", FALSE }, // OID is unknown, but it is numeric
            { customDefinition, "9.9.9.999", TRUE },
            { customDefinition, "9.9.9.9999", FALSE },

            // OID 'descr' form where assertion values are known to the schema.
            { cnDefinition, "cn", TRUE },
            { cnDefinition, "commonName", TRUE },
            { cnDefinition, "l", FALSE },
            { customDefinition, "cn", FALSE },

            /* THESE TESTS FAIL BECAUSE THE ACTUAL RESULT IS FALSE

            // These are undefined because the assertion values are unknown to the server. See RFC 4517 4.2.26.
            { cnDefinition, "dummy", UNDEFINED },
            { cnDefinition, "xxx", UNDEFINED },
            { customDefinition, "foo", UNDEFINED }

            */
        };
    }

    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_OID_FIRST_COMPONENT_OID);
    }
}
