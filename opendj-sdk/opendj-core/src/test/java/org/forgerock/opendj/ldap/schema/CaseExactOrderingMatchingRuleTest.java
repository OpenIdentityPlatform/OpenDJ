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
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_CASE_EXACT_OID;

import org.testng.annotations.DataProvider;

/**
 * Test the CaseExactOrderingMatchingRule.
 */
public class CaseExactOrderingMatchingRuleTest extends OrderingMatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public Object[][] createOrderingMatchingRuleInvalidValues() {
        return new Object[][] {};
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "Orderingmatchingrules")
    public Object[][] createOrderingMatchingRuleTestData() {
        return new Object[][] {
            { "12345678", "02345678", 1 },
            { "abcdef", "bcdefa", -1 },
            { "abcdef", "abcdef", 0 }, };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_CASE_EXACT_OID);
    }
}
