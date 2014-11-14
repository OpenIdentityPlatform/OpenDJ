/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_INTEGER_OID;

import org.testng.annotations.DataProvider;

/**
 * Test the IntegerOrderingMatchingRule.
 */
public class IntegerOrderingMatchingRuleTest extends OrderingMatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public Object[][] createOrderingMatchingRuleInvalidValues() {
        /*
         * The JDK8 BigInteger parser is quite tolerant and allows leading zeros
         * and + characters. It's ok if the matching rule is more tolerant than
         * the syntax itself (see commented data).
         */
        return new Object[][] {
            //{"01"},
            //{"00"},
            //{"-01"},
            { "1-2" },
            { "b2" },
            { "-" },
            { "" },
            {" 63 "},
            {"- 63"},
            //{"+63" },
            {"AB"  },
            {"0xAB"},
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "Orderingmatchingrules")
    public Object[][] createOrderingMatchingRuleTestData() {
        return new Object[][] {
            {"1",   "0",   1},
            {"1",   "1",   0},
            {"45",  "54", -1},
            {"-63", "63", -1},
            {"-63", "0",  -1},
            {"63",  "0",   1},
            {"0",   "-63", 1},
            // Values which are greater than 64 bits.
            { "-987654321987654321987654321", "-987654321987654321987654322", 1 },
            {"987654321987654321987654321", "987654321987654321987654322", -1},
            { "987654321987654321987654321", "987654321987654321987654321", 0 },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_INTEGER_OID);
    }
}
