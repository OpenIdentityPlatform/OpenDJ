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
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_NUMERIC_STRING_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the NumericStringOrderingMatchingRule.
 */
@Test
public class NumericStringOrderingMatchingRuleTest extends OrderingMatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public Object[][] createOrderingMatchingRuleInvalidValues() {
        return new Object[][] {
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "Orderingmatchingrules")
    public Object[][] createOrderingMatchingRuleTestData() {
        return new Object[][] {
            // non-numeric characters are tolerated and treated as significant
            {"jfhslur", "JFH", 1},
            {"123AB", "2", -1},
            {"1", "999999999999999999999", -1},
            {"1", "9",  -1},
            {"10", "9",  -1},
            {"1 0", "9",  -1},
            {"1", " 1 ", 0},
            {"0", "1",  -1},
            {"1", "0",  1},
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_NUMERIC_STRING_OID);
    }
}
