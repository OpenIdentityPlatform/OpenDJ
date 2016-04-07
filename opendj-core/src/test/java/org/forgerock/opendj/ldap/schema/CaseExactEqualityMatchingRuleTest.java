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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_EXACT_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/** Test the CaseExactEqualityMatchingRule. */
public class CaseExactEqualityMatchingRuleTest extends MatchingRuleTest {
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {};
    }

    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            { "12345678", "12345678", ConditionResult.TRUE },
            { "12345678\u2163", "12345678\u2163", ConditionResult.TRUE },
            { "ABC45678", "ABC45678", ConditionResult.TRUE },
            { "  ABC45678  ", "ABC45678", ConditionResult.TRUE },
            { "ABC   45678", "ABC 45678", ConditionResult.TRUE },
            { "   ", " ", ConditionResult.TRUE },
            { "", "", ConditionResult.TRUE },
            { "ABC45678", "abc45678", ConditionResult.FALSE }, };
    }

    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_CASE_EXACT_OID);
    }
}
