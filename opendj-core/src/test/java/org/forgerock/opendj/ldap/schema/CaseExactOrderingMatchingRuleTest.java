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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_CASE_EXACT_OID;

import org.testng.annotations.DataProvider;

/** Test the CaseExactOrderingMatchingRule. */
public class CaseExactOrderingMatchingRuleTest extends OrderingMatchingRuleTest {
    @Override
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public Object[][] createOrderingMatchingRuleInvalidValues() {
        return new Object[][] {};
    }

    @Override
    @DataProvider(name = "Orderingmatchingrules")
    public Object[][] createOrderingMatchingRuleTestData() {
        return new Object[][] {
            { "12345678", "02345678", 1 },
            { "abcdef", "bcdefa", -1 },
            { "abcdef", "abcdef", 0 }, };
    }

    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_CASE_EXACT_OID);
    }
}
