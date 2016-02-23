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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.EMR_UNIQUE_MEMBER_OID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the UniqueMemberEqualityMatchingRule.
 */
@Test
public class UniqueMemberEqualityMatchingRuleTest extends MatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] {
            {"1.3.6.1.4.1.1466.01"}
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            // non-bit string content on optional uid is tolerated
            { "1.3.6.1.4.1.1466.0=#04024869,O=Test,C=GB#'123'B",
              "1.3.6.1.4.1.1466.0=#04024869,O=Test,C=GB#'123'B", ConditionResult.TRUE },
            { "1.3.6.1.4.1.1466.0=#04024869,O=Test,C=GB#'0101'B",
              "1.3.6.1.4.1.1466.0=#04024869,O=Test,C=GB#'0101'B", ConditionResult.TRUE },
            { "1.3.6.1.4.1.1466.0=#04024869,O=Test,C=GB#'0101'B",
              "1.3.6.1.4.1.1466.0=#04024869,o=Test,C=GB#'0101'B", ConditionResult.TRUE },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(EMR_UNIQUE_MEMBER_OID);
    }

}
