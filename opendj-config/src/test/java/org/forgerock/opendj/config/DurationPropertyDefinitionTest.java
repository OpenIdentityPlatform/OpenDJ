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

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DurationPropertyDefinitionTest extends ConfigTestCase {

    @Test
    public void testCreateBuilder() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        assertNotNull(builder);
    }

    /**
     * Creates data for testing string-based limit values.
     *
     * @return data
     */
    @DataProvider(name = "longLimitData")
    Object[][] createLongLimitData() {
        return new Object[][] {
            { 1L, 1L },
        };
    }

    /**
     * Creates data for testing limit values.
     *
     * @return data
     */
    @DataProvider(name = "illegalLongLimitData")
    Object[][] createIllegalLongLimitData() {
        return new Object[][] {
            // lower, upper, lower first
            { -1L, 0L, true },
            { 0L, -1L, false },
            { 2L, 1L, true },
            { 2L, 1L, false } };
    }

    @DataProvider(name = "stringLimitData")
    Object[][] createStringLimitData() {
        return new Object[][] {
            // unit, limit, expected value
            { "ms", "123", 123 },
            { "ms", "123s", 123000 },
            { "s", "123", 123000 },
            { "s", "123s", 123000 },
            { "m", "10", 600000 },
            { "m", "10s", 10000 } };
    }

    @Test(dataProvider = "longLimitData")
    public void testLowerLimitWithLong(long lowerLimit, long expectedValue) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(lowerLimit);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        assertEquals(def.getLowerLimit(), expectedValue);
    }

    @Test(dataProvider = "stringLimitData")
    public void testLowerLimitWithString(String unit, String limitValue, long expected) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setBaseUnit(DurationUnit.getUnit(unit));
        builder.setLowerLimit(limitValue);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        assertEquals(def.getLowerLimit(), expected);
    }

    @Test(dataProvider = "longLimitData")
    public void testUpperLimitWithLong(long upperLimit, long expectedValue) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setUpperLimit(upperLimit);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        assertEquals((long) def.getUpperLimit(), expectedValue);
    }

    @Test(dataProvider = "illegalLongLimitData", expectedExceptions = IllegalArgumentException.class)
    public void testIllegalLimitsWithLong(long lowerLimit, long upperLimit, boolean isLowerFirst) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        if (isLowerFirst) {
            builder.setLowerLimit(lowerLimit);
            builder.setUpperLimit(upperLimit);
        } else {
            builder.setUpperLimit(upperLimit);
            builder.setLowerLimit(lowerLimit);
        }
    }

    @Test
    public void testAllowUnlimitedIsTrue() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.decodeValue("unlimited");
    }

    @Test(expectedExceptions = PropertyException.class)
    public void testAllowUnlimitedIsFalse() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(false);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.decodeValue("unlimited");
    }

    @Test(expectedExceptions = PropertyException.class)
    public void testAllowUnlimitedIsFalseNumValue() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(false);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.validateValue(-1L);
    }

    @DataProvider(name = "validateValueData")
    Object[][] createValidateValueData() {
        return new Object[][] {
            // low in ms, high in ms, allow unlimited, value in seconds
            { 5000L, 10000L, false, 7L },
            { 5000L, null, true, -1L },
            { 5000L, 10000L, false, 5L },
            { 5000L, 10000L, false, 10L },
            { 5000L, null, false, 10000L }
        };
    }

    @Test(dataProvider = "validateValueData")
    public void testValidateValue(Long lowerLimitInMillis, Long higherLimitInMillis,
            boolean isAllowUnlimited, Long valueInSeconds) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(lowerLimitInMillis);
        builder.setUpperLimit(higherLimitInMillis);
        builder.setAllowUnlimited(isAllowUnlimited);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.validateValue(valueInSeconds);
    }

    @DataProvider(name = "illegalValidateValueData")
    Object[][] createIllegalValidateValueData() {
        return new Object[][] {
             // low in ms, high in ms, allow unlimited, value in seconds
            { 5000L, 10000L, false, null },
            { 5000L, 10000L, false, 1L },
            { 5000L, 10000L, false, 11L },
            { 5000L, 10000L, false, -1L } };
    }

    @Test(dataProvider = "illegalValidateValueData", expectedExceptions = { AssertionError.class,
            NullPointerException.class, PropertyException.class })
    public void testValidateValueIllegal(Long lowLimitInMillis, Long highLimitInMillis,
            boolean isAllowUnlimited, Long valueInSeconds) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setLowerLimit(lowLimitInMillis);
        builder.setUpperLimit(highLimitInMillis);
        builder.setAllowUnlimited(isAllowUnlimited);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.validateValue(valueInSeconds);
    }

    @DataProvider(name = "encodeValueData")
    Object[][] createEncodeValueData() {
        return new Object[][] {
            { -1L, "unlimited" },
            { 0L, "0 s" },
            { 1L, "1 s" },
            { 2L, "2 s" },
            { 999L, "999 s" },
            { 1000L, "1000 s" },
            { 1001L, "1001 s" },
            { 1023L, "1023 s" },
            { 1024L, "1024 s" },
            { 1025L, "1025 s" },
            { 1000L * 1000L, "1000000 s" },
        };
    }

    @Test(dataProvider = "encodeValueData")
    public void testEncodeValue(Long valueToEncode, String expectedValue) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        assertEquals(def.encodeValue(valueToEncode), expectedValue);
    }

    /** Test that accept doesn't throw and exception. */
    @Test
    public void testAccept() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        PropertyDefinitionVisitor<Boolean, Void> v = new PropertyDefinitionVisitor<Boolean, Void>() {
            @Override
            public Boolean visitDuration(DurationPropertyDefinition d, Void o) {
                return true;
            }
            @SuppressWarnings("unused")
            public Boolean visitUnknown(PropertyDefinition<?> d, Void o) throws PropertyException {
                return false;
            }
        };

        assertEquals((boolean) def.accept(v, null), true);
    }

    /** Make sure toString doesn't barf. */
    @Test
    public void testToString() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.toString();
    }

    /** Make sure toString doesn't barf. */
    @Test
    public void testToString2() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setUpperLimit(10L);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.toString();
    }

    @Test
    public void testCompare() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.compare(1L, 2L);
    }

    @Test
    public void testSetDefaultBehaviorProvider() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        builder.setDefaultBehaviorProvider(new DefaultBehaviorProvider<Long>() {
            @Override
            public <R, P> R accept(DefaultBehaviorProviderVisitor<Long, R, P> v, P p) {
                return null;
            }
        });
    }

    @Test
    public void testSetPropertyOption() {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setOption(PropertyOption.HIDDEN);
    }

    @DataProvider(name = "decodeValueData")
    Object[][] createDecodeValueData() {
        return new Object[][] {
            // syntax tests
            { "unlimited", -1L },
            { "0h", 0L },
            { "0.0h", 0L },
            { "0.00h", 0L },
            { "0 h", 0L },
            { "0.00 h", 0L },
            { "1h", 1L },
            { "1 h", 1L },
            { "0ms", 0L },
            { "1h60m", 2L },
            { "1d10h", 34L },
            { "4d600m", 106L },

            // conversion tests
            { "1 d", 24L }, { "2 d", 48L }, { "0.5 d", 12L } };
    }

    @Test(dataProvider = "decodeValueData")
    public void testDecodeValue(String valueToDecode, Long expectedValue) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(true);
        builder.setBaseUnit(DurationUnit.HOURS);
        builder.setMaximumUnit(DurationUnit.DAYS);
        DurationPropertyDefinition def = buildTestDefinition(builder);

        assertThat(def.decodeValue(valueToDecode)).
            isEqualTo(expectedValue);
    }

    @DataProvider(name = "decodeValueDataIllegal")
    Object[][] createDecodeValueDataIllegal() {
        return new Object[][] { { "" }, { "0" }, // no unit
            { "123" }, // no unit
            { "a s" },
            { "1 x" },
            { "0.h" },
            { "0. h" },
            { "1.h" },
            { "1. h" },
            { "1.1 h" }, // too granular
            { "30 m" }, // unit too small violation
            { "60 m" }, // unit too small violation
            { "1 w" }, // unit too big violation
            { "7 w" }, // unit too big violation
            { "1 x" }, { "1 d" }, // upper limit violation
            { "2 h" }, // lower limit violation
            { "-1 h" } // unlimited violation
        };
    }

    @Test(dataProvider = "decodeValueDataIllegal", expectedExceptions = { PropertyException.class })
    public void testDecodeValue(String valueToDecode) {
        DurationPropertyDefinition.Builder builder = createTestBuilder();
        builder.setAllowUnlimited(false);
        builder.setBaseUnit(DurationUnit.HOURS);
        builder.setMaximumUnit(DurationUnit.DAYS);
        builder.setLowerLimit(5L);
        builder.setUpperLimit(10L);
        DurationPropertyDefinition def = buildTestDefinition(builder);
        def.decodeValue(valueToDecode);
    }

    private DurationPropertyDefinition.Builder createTestBuilder() {
        return DurationPropertyDefinition.createBuilder(RootCfgDefn.getInstance(), "test-property-name");
    }

    private DurationPropertyDefinition buildTestDefinition(DurationPropertyDefinition.Builder builder) {
        builder.setDefaultBehaviorProvider(new DefinedDefaultBehaviorProvider<Long>("0"));
        return builder.getInstance();
    }

}
