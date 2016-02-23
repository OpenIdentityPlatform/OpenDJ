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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_UUID_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;

/**
 * Test the UUIDEqualityMatchingRule.
 */
public class UUIDEqualityMatchingRuleTest extends MatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {
            {"G2345678-9abc-def0-1234-1234567890ab"},
            {"g2345678-9abc-def0-1234-1234567890ab"},
            {"12345678/9abc/def0/1234/1234567890ab"},
            {"12345678-9abc-def0-1234-1234567890a"},
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            { "12345678-9ABC-DEF0-1234-1234567890ab",
              "12345678-9abc-def0-1234-1234567890ab", ConditionResult.TRUE },
            { "12345678-9abc-def0-1234-1234567890ab",
              "12345678-9abc-def0-1234-1234567890ab", ConditionResult.TRUE },
            { "02345678-9abc-def0-1234-1234567890ab",
              "12345678-9abc-def0-1234-1234567890ab", ConditionResult.FALSE },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_UUID_OID);
    }

}
