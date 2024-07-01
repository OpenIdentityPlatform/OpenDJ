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
 * Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Ordering matching rule tests.
 */
@SuppressWarnings("javadoc")
public abstract class OrderingMatchingRuleTest extends AbstractSchemaTestCase {
    /**
     * Create data for the OrderingMatchingRulesInvalidValues test.
     *
     * @return The data for the OrderingMatchingRulesInvalidValues test.
     */
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public abstract Object[][] createOrderingMatchingRuleInvalidValues();

    /**
     * Create data for the OrderingMatchingRules test.
     *
     * @return The data for the OrderingMatchingRules test.
     */
    @DataProvider(name = "Orderingmatchingrules")
    public abstract Object[][] createOrderingMatchingRuleTestData();

    /**
     * Test the comparison of valid values.
     */
    @Test(dataProvider = "Orderingmatchingrules")
    public void orderingMatchingRules(final String value1, final String value2, final int result)
            throws Exception {
        // Make sure that the specified class can be instantiated as a task.
        final MatchingRule ruleInstance = getRule();

        final ByteString normalizedValue1 =
                ruleInstance.normalizeAttributeValue(ByteString.valueOfUtf8(value1));
        final ByteString normalizedValue2 =
                ruleInstance.normalizeAttributeValue(ByteString.valueOfUtf8(value2));

        // Test the comparator
        final int comp = normalizedValue1.compareTo(normalizedValue2);
        if (comp == 0) {
            Assert.assertEquals(comp, result);
        } else if (comp > 0) {
            Assert.assertTrue(result > 0);
        } else if (comp < 0) {
            Assert.assertTrue(result < 0);
        }

        Assertion a = ruleInstance.getGreaterOrEqualAssertion(ByteString.valueOfUtf8(value2));
        Assert.assertEquals(a.matches(normalizedValue1), ConditionResult.valueOf(result >= 0));

        a = ruleInstance.getLessOrEqualAssertion(ByteString.valueOfUtf8(value2));
        Assert.assertEquals(a.matches(normalizedValue1), ConditionResult.valueOf(result <= 0));

        a = ruleInstance.getAssertion(ByteString.valueOfUtf8(value2));
        Assert.assertEquals(a.matches(normalizedValue1), ConditionResult.valueOf(result < 0));
    }

    /**
     * Test that invalid values are rejected.
     */
    @Test(expectedExceptions = DecodeException.class,
            dataProvider = "OrderingMatchingRuleInvalidValues")
    public void orderingMatchingRulesInvalidValues(final String value) throws Exception {
        // Make sure that the specified class can be instantiated as a task.
        final MatchingRule ruleInstance = getRule();

        // normalize the 2 provided values
        ruleInstance.normalizeAttributeValue(ByteString.valueOfUtf8(value));
    }

    /**
     * Get the Ordering matching Rules that is to be tested.
     *
     * @return The Ordering matching Rules that is to be tested.
     */
    protected abstract MatchingRule getRule();
}
