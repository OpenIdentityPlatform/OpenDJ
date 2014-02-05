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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class IntegerPropertyDefinitionTest extends ConfigTestCase {

    @Test
    public void testCreateBuilder() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        assertNotNull(builder);
    }

    @DataProvider(name = "limitData")
    public Object[][] createlimitData() {
        return new Object[][] { { 1, 1 },
        // { null, 0 }
        };
    }

    @DataProvider(name = "illegalLimitData")
    public Object[][] createIllegalLimitData() {
        return new Object[][] {
             // lower, upper, is lower first ?
            { -1, 0, true },
            { 0, -1, false },
            { 2, 1, true },
            { 2, 1, false }
        };
    }

    @Test(dataProvider = "limitData")
    public void testLowerLimitWithInteger(int limit, int expectedValue) {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(limit);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        assert propertyDef.getLowerLimit() == expectedValue;
    }

    @Test(dataProvider = "limitData")
    public void testUpperLimitWithInteger(int limit, int expectedValue) {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setUpperLimit(limit);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        assert propertyDef.getUpperLimit().equals(expectedValue);
    }

    @Test(dataProvider = "illegalLimitData", expectedExceptions = IllegalArgumentException.class)
    public void testIllegalLimits(int lower, int upper, boolean isLowerFirst) {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        if (isLowerFirst) {
            builder.setLowerLimit(lower);
            builder.setUpperLimit(upper);
        } else {
            builder.setUpperLimit(upper);
            builder.setLowerLimit(lower);
        }
    }

    @Test
    public void testIsAllowUnlimitedTrue() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.decodeValue("unlimited");
    }

    @Test(expectedExceptions = PropertyException.class)
    public void testIsAllowUnlimitedFalse() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(false);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.decodeValue("unlimited");
    }

    @Test(expectedExceptions = PropertyException.class)
    public void testIsAllowUnlimitedInteger() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(false);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.validateValue(-1);
    }

    @DataProvider(name = "validateValueData")
    public Object[][] createvalidateValueData() {
        return new Object[][] {
            // low, high, allow unlimited ?, value to validate
            { 5, 10, false, 7 },
            { 5, null, true, -1 },
            { 5, 10, true, -1 },
        };
    }

    @Test(dataProvider = "validateValueData")
    public void testValidateValue(Integer low, Integer high, boolean allowUnlimited, Integer valueToValidate) {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(low);
        builder.setUpperLimit(high);
        builder.setAllowUnlimited(allowUnlimited);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.validateValue(valueToValidate);
    }

    @DataProvider(name = "illegalValidateValueData")
    public Object[][] createIllegalValidateValueData() {
        return new Object[][] {
            // low, high, allow unlimited ?, value to validate
            { 5, 10, false, null },
            { 5, 10, false, 1 },
            { 5, 10, false, 11 },
            { 5, 10, false, -1 },
            { 5, 10, true, 2 },
            { 5, 10, true, 11 }
        };
    }

    @Test(dataProvider = "illegalValidateValueData", expectedExceptions = { AssertionError.class,
            NullPointerException.class, PropertyException.class })
    public void testValidateValueIllegal(Integer low, Integer high, boolean allowUnlimited, Integer value) {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(low);
        builder.setUpperLimit(high);
        builder.setAllowUnlimited(allowUnlimited);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.validateValue(value);
    }

    @DataProvider(name = "encodeValueData")
    public Object[][] createEncodeValueData() {
        return new Object[][] {
            { -1, "unlimited" },
            { 1, "1" }, };
    }

    @Test(dataProvider = "encodeValueData")
    public void testEncodeValue(Integer value, String expectedValue) {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        assertEquals(propertyDef.encodeValue(value), expectedValue);
    }

    @Test
    public void testAccept() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        PropertyDefinitionVisitor<Boolean, Void> v = new PropertyDefinitionVisitor<Boolean, Void>() {
            public Boolean visitInteger(IntegerPropertyDefinition d, Void o) {
                return true;
            }

            @SuppressWarnings("unused")
            public Boolean visitUnknown(PropertyDefinition<?> d, Void o) throws PropertyException {
                return false;
            }
        };

        assertEquals((boolean) propertyDef.accept(v, null), true);
    }

    @Test
    public void testToString() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.toString();
    }

    @Test
    public void testCompare() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        IntegerPropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.compare(1, 2);
    }

    @Test
    public void testSetDefaultBehaviorProvider() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        builder.setDefaultBehaviorProvider(new DefaultBehaviorProvider<Integer>() {
            public <R, P> R accept(DefaultBehaviorProviderVisitor<Integer, R, P> v, P p) {
                return null;
            }
        });
    }

    @Test
    public void testSetOption() {
        IntegerPropertyDefinition.Builder builder = createTestBuilder();
        builder.setOption(PropertyOption.HIDDEN);
    }

    private IntegerPropertyDefinition.Builder createTestBuilder() {
        return IntegerPropertyDefinition.createBuilder(RootCfgDefn.getInstance(), "test-property-name");
    }

    private IntegerPropertyDefinition buildTestDefinition(IntegerPropertyDefinition.Builder builder) {
        builder.setDefaultBehaviorProvider(new DefinedDefaultBehaviorProvider<Integer>("0"));
        return builder.getInstance();
    }

}
