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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SizePropertyDefinitionTest extends ConfigTestCase {

    @Test
    public void testCreateBuilder() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        assertNotNull(builder);
    }

    @Test
    public void testLowerLimit() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(1);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        assert propertyDef.getLowerLimit() == 1;
    }

    @DataProvider(name = "stringLimitData")
    public Object[][] createStringLimitData() {
        return new Object[][] {
            { "1 b", 1L },
        };
    }

    @DataProvider(name = "illegalLimitData")
    public Object[][] createIllegalLimitData() {
        return new Object[][] {
            // lower, upper, is lower first
            { -1L, 0L, true },
            { 0L, -1L, false },
            { 2L, 1L, true },
            { 2L, 1L, false } };
    }

    @Test(dataProvider = "stringLimitData")
    public void testLowerLimitString(String unitLimit, Long expectedValue) {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(unitLimit);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        assert propertyDef.getLowerLimit() == expectedValue;
    }

    @Test(dataProvider = "stringLimitData")
    public void testUpperLimitString(String limit, long expectedValue) {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setUpperLimit(limit);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        assert propertyDef.getUpperLimit().equals(expectedValue);
    }

    @Test(dataProvider = "illegalLimitData", expectedExceptions = IllegalArgumentException.class)
    public void testIllegalLimits(long lower, long upper, boolean lowerFirst) {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        if (lowerFirst) {
            builder.setLowerLimit(lower);
            builder.setUpperLimit(upper);
        } else {
            builder.setUpperLimit(upper);
            builder.setLowerLimit(lower);
        }
    }

    @Test
    public void testIsAllowUnlimitedTrue() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.decodeValue("unlimited");
    }

    @Test(expectedExceptions = PropertyException.class)
    public void testIsAllowUnlimitedFalse() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(false);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.decodeValue("unlimited");
    }

    @Test(expectedExceptions = PropertyException.class)
    public void testIsAllowUnlimitedNumeric() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(false);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.validateValue(-1L);
    }

    @DataProvider(name = "validateValueData")
    public Object[][] createvalidateValueData() {
        return new Object[][] {
            // low, high, is allow unlimited, value
            { 5L, 10L, false, 7L },
            { 5L, null, true, -1L },
            { 5L, 10L, true, -1L },
        };
    }

    @Test(dataProvider = "validateValueData")
    public void testValidateValue(Long low, Long high, boolean isAllowUnlimited, Long valueToValidate) {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(low);
        builder.setUpperLimit(high);
        builder.setAllowUnlimited(isAllowUnlimited);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.validateValue(valueToValidate);
    }

    @DataProvider(name = "illegalValidateValueData")
    public Object[][] createIllegalValidateValueData() {
        return new Object[][] {
             // low, high, is allow unlimited, value
            { 5L, 10L, false, null },
            { 5L, 10L, false, 1L },
            { 5L, 10L, false, 11L },
            { 5L, 10L, false, -1L },
            { 5L, 10L, true, 2L },
            { 5L, 10L, true, 11L }
        };
    }

    @Test(dataProvider = "illegalValidateValueData", expectedExceptions = { AssertionError.class,
            NullPointerException.class, PropertyException.class })
    public void testValidateValueIllegal(Long low, Long high, boolean allowUnlimited, Long valueToValidate) {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(low);
        builder.setUpperLimit(high);
        builder.setAllowUnlimited(allowUnlimited);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.validateValue(valueToValidate);
    }

    @DataProvider(name = "encodeValueData")
    public Object[][] createEncodeValueData() {
        return new Object[][] {
            { -1L, "unlimited" },
            { 0L, "0 b" },
            { 1L, "1 b" },
            { 2L, "2 b" },
            { 999L, "999 b" },
            { 1000L, "1 kb" },
            { 1001L, "1001 b" },
            { 1023L, "1023 b" },
            { 1024L, "1 kib" },
            { 1025L, "1025 b" },
            { 1000L * 1000L, "1 mb" },
            { 1000L * 1000L * 1000L, "1 gb" },
            { 1024L * 1024L * 1024L, "1 gib" },
            { 1000L * 1000L * 1000L * 1000L, "1 tb" }
        };
    }

    @Test(dataProvider = "encodeValueData")
    public void testEncodeValue(Long value, String expectedValue) {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        assertEquals(propertyDef.encodeValue(value), expectedValue);
    }

    @Test
    public void testAccept() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        PropertyDefinitionVisitor<Boolean, Void> v = new PropertyDefinitionVisitor<Boolean, Void>() {
            @Override
            public Boolean visitSize(SizePropertyDefinition d, Void o) {
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
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        propertyDef.toString();
    }

    @Test
    public void testCompare() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        SizePropertyDefinition propertyDef = buildTestDefinition(builder);
        assertEquals(propertyDef.compare(1L, 2L), -1);
    }

    @Test
    public void testSetDefaultBehaviorProvider() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        builder.setDefaultBehaviorProvider(new DefaultBehaviorProvider<Long>() {
            @Override
            public <R, P> R accept(DefaultBehaviorProviderVisitor<Long, R, P> v, P p) {
                return null;
            }
        });
    }

    @Test
    public void testSetOption() {
        SizePropertyDefinition.Builder builder = createTestBuilder();
        builder.setOption(PropertyOption.HIDDEN);
    }

    private SizePropertyDefinition.Builder createTestBuilder() {
        return SizePropertyDefinition.createBuilder(RootCfgDefn.getInstance(), "test-property-name");
    }

    private SizePropertyDefinition buildTestDefinition(SizePropertyDefinition.Builder builder) {
        builder.setDefaultBehaviorProvider(new DefinedDefaultBehaviorProvider<Long>("0"));
        return builder.getInstance();
    }

}
