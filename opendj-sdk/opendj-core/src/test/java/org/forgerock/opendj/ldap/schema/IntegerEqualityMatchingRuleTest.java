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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_INTEGER_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/**
 * Test the IntegerEqualityMatchingRule.
 */
public class IntegerEqualityMatchingRuleTest extends MatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        /*
         * The JDK8 BigInteger parser is quite tolerant and allows leading zeros
         * and + characters. It's ok if the matching rule is more tolerant than
         * the syntax itself (see commented data).
         */
        return new Object[][] {
            //{"01"},
            //{"00"},
            //{"-01"},
            {"1-2"},
            {"b2"},
            {"-"},
            {""},
            {" 63 "},
            {"- 63"},
            //{"+63" },
            {"AB"  },
            {"0xAB"},
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            {"1234567890",  "1234567890",   ConditionResult.TRUE},
            {"-1",          "-1",           ConditionResult.TRUE},
            {"-9876543210", "-9876543210",  ConditionResult.TRUE},
            {"1",           "-1",           ConditionResult.FALSE},
            // Values which are greater than 64 bits.
            { "-987654321987654321987654321", "-987654321987654321987654322", ConditionResult.FALSE },
            {"987654321987654321987654321", "987654321987654321987654322", ConditionResult.FALSE },
            { "987654321987654321987654321", "987654321987654321987654321", ConditionResult.TRUE },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_INTEGER_OID);
    }

}
