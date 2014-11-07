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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_UUID_OID;

import org.testng.annotations.DataProvider;

/**
 * Test the UUIDOrderingMatchingRule.
 */
public class UUIDOrderingMatchingRuleTest extends OrderingMatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public Object[][] createOrderingMatchingRuleInvalidValues() {
        return new Object[][] {
            { "G2345678-9abc-def0-1234-1234567890ab" },
            { "g2345678-9abc-def0-1234-1234567890ab" },
            { "12345678/9abc/def0/1234/1234567890ab" },
            { "12345678-9abc-def0-1234-1234567890a" },
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "Orderingmatchingrules")
    public Object[][] createOrderingMatchingRuleTestData() {
        return new Object[][] {
            { "12345678-9ABC-DEF0-1234-1234567890ab", "12345678-9abc-def0-1234-1234567890ab", 0 },
            { "12345678-9abc-def0-1234-1234567890ab", "12345678-9abc-def0-1234-1234567890ab", 0 },
            { "02345678-9abc-def0-1234-1234567890ab", "12345678-9abc-def0-1234-1234567890ab", -1 },
            { "12345678-9abc-def0-1234-1234567890ab", "02345678-9abc-def0-1234-1234567890ab", 1 },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_UUID_OID);
    }
}
