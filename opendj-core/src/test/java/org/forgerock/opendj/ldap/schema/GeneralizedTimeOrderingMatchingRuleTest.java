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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_GENERALIZED_TIME_OID;

import org.testng.annotations.DataProvider;

/** Test the GeneralizedTimeOrderingMatchingRule. */
public class GeneralizedTimeOrderingMatchingRuleTest extends OrderingMatchingRuleTest {
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

    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_GENERALIZED_TIME_OID);
    }
}
