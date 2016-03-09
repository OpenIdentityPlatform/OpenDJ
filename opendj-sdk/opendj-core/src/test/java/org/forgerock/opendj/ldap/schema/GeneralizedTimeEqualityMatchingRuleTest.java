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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_GENERALIZED_TIME_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/** Test the GeneralizedTimeEqualityMatchingRule. */
public class GeneralizedTimeEqualityMatchingRuleTest extends MatchingRuleTest {
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {
            {"2006september061Z"},
            {"2006"},
            {"200609061Z"},
            {"20060906135Z"},
            {"200609061350G"},
            {"2006090613mmZ"},
            {"20060906135030.011"},
            {"20060906135030Zx"},
            {"20060906135030.Z"},
            {"20060906135030.aZ"},
            {"20060906135030"},
            {"20060906135030.123"},
            {"20060906135030-2500"},
            {"20060906135030-2070"},
            // Following values do not pass - they passed in server
            //{"20060931135030Z"},
            //{"20060229135030Z"},
            //{"20060230135030Z"},
        };
    }

    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            {"2006090613Z",             "20060906130000.000Z", ConditionResult.TRUE },
            {"200609061350Z",           "20060906135000.000Z", ConditionResult.TRUE },
            {"200609061351Z",           "20060906135000.000Z", ConditionResult.FALSE },
            {"20060906135030Z",         "20060906135030.000Z", ConditionResult.TRUE },
            {"20060906135030.3Z",       "20060906135030.300Z", ConditionResult.TRUE },
            {"20060906135030.30Z",      "20060906135030.300Z", ConditionResult.TRUE },
            {"20060906135030Z",         "20060906135030.000Z", ConditionResult.TRUE },
            {"20060906135030.0Z",       "20060906135030.000Z", ConditionResult.TRUE },
            {"20060906135030.0118Z",    "20060906135030.012Z", ConditionResult.TRUE },
            {"20060906135030+01",       "20060906125030.000Z", ConditionResult.TRUE },
            {"20060906135030+0101",     "20060906124930.000Z", ConditionResult.TRUE },
            {"20070417055812.318-0500", "20070417105812.318Z", ConditionResult.TRUE },
            // Following values do not pass - they passed in server
            //{"2007041705.5Z",           "20070417053000.000Z", ConditionResult.TRUE },
            //{"200704170558.5Z",         "20070417055830.000Z", ConditionResult.TRUE },
        };
    }

    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_GENERALIZED_TIME_OID);
    }
}
