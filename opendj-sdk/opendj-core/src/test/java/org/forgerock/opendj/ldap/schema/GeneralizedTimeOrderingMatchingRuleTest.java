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
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_GENERALIZED_TIME_OID;

import org.testng.annotations.DataProvider;

/**
 * Test the GeneralizedTimeOrderingMatchingRule.
 */
public class GeneralizedTimeOrderingMatchingRuleTest extends OrderingMatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public Object[][] createOrderingMatchingRuleInvalidValues() {
        return new Object[][] {
            { "20060912180130"},
            {"2006123123595aZ"},
            {"200a1231235959Z"},
            {"2006j231235959Z"},
            {"20061231#35959Z"},
            {"20060912180a30Z"},
            {"20060912180030Z.01"},
            {"200609121800"},
            {"20060912180129.hhZ"},
            {"20060912180129.1hZ"},
            {"20060906135030+aa01"},
            {"2006"},
            {"20060906135030+3359"},
            {"20060906135030+2389"},
            {"20060906135030+2361"},
            {"20060906135030+"},
            {"20060906135030+0"},
            {"20060906135030+010"},
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "Orderingmatchingrules")
    public Object[][] createOrderingMatchingRuleTestData() {
        return new Object[][] {
            {"20060906135030+0101", "20060906135030+2359",  1},
            {"20060912180130Z",     "20060912180130Z",      0},
            {"20060912180130z",     "20060912180130Z",      0},
            {"20060912180130Z",     "20060912180129Z",      1},
            {"20060912180129Z",     "20060912180130Z",     -1},
            {"20060912180129.000Z", "20060912180130.001Z", -1},
            {"20060912180129.1Z",   "20060912180130.2Z",   -1},
            {"20060912180129.11Z",  "20060912180130.12Z",  -1},
            // OPENDJ-2397 - dates before 1970 have negative ms.
            {"19000101010203Z",     "20000101010203Z",     -1},
            {"20000101010203Z",     "19000101010203Z",      1},
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_GENERALIZED_TIME_OID);
    }
}
