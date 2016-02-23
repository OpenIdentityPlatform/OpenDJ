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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_CASE_IGNORE_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/**
 * Test the CaseIgnoreEqualityMatchingRule.
 */
public class CaseIgnoreEqualityMatchingRuleTest extends MatchingRuleTest {
    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {};
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            { "12345678", "12345678", ConditionResult.TRUE },
            { "ABC45678", "abc45678", ConditionResult.TRUE },
            { " string ", "string", ConditionResult.TRUE },
            { "string ", "string", ConditionResult.TRUE },
            { " string", "string", ConditionResult.TRUE },
            { "    ", " ", ConditionResult.TRUE },
            { "Z", "z", ConditionResult.TRUE },
            { "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890", "abcdefghijklmnopqrstuvwxyz1234567890",
                ConditionResult.TRUE },
            { "foo\u0020bar\u0020\u0020", "foo bar", ConditionResult.TRUE },
            { "test\u00AD\u200D", "test", ConditionResult.TRUE },
            { "foo\u070Fbar", "foobar", ConditionResult.TRUE },
            // Case-folding data below.
            { "foo\u0149bar", "foo\u02BC\u006Ebar", ConditionResult.TRUE },
            { "foo\u017Bbar", "foo\u017Cbar", ConditionResult.TRUE },
            { "foo\u017BBAR", "foo\u017Cbar", ConditionResult.TRUE },


        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_CASE_IGNORE_OID);
    }
}
