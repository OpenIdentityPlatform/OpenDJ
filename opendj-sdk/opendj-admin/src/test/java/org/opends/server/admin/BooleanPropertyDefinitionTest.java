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
package org.opends.server.admin;

import static org.testng.Assert.*;

import org.forgerock.opendj.admin.meta.RootCfgDefn;
import org.forgerock.opendj.config.ConfigTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BooleanPropertyDefinitionTest extends ConfigTestCase {

    BooleanPropertyDefinition.Builder builder = null;

    @BeforeClass
    public void setUp() throws Exception {
        builder = BooleanPropertyDefinition.createBuilder(RootCfgDefn.getInstance(), "test-property");
    }

    @Test
    public void testValidateValue() {
        BooleanPropertyDefinition def = createPropertyDefinition();
        def.validateValue(Boolean.TRUE, PropertyDefinitionsOptions.NO_VALIDATION_OPTIONS);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testValidateValueIllegal() {
        BooleanPropertyDefinition def = createPropertyDefinition();
        def.validateValue(null, PropertyDefinitionsOptions.NO_VALIDATION_OPTIONS);
    }

    @DataProvider(name = "decodeValueData")
    public Object[][] createValidateValueData() {
        return new Object[][] { { "false", Boolean.FALSE }, { "true", Boolean.TRUE } };
    }

    @Test(dataProvider = "decodeValueData")
    public void testDecodeValue(String value, Boolean expected) {
        BooleanPropertyDefinition def = createPropertyDefinition();
        assertEquals(def.decodeValue(value, PropertyDefinitionsOptions.NO_VALIDATION_OPTIONS), expected);
    }

    @DataProvider(name = "decodeValueDataIllegal")
    public Object[][] createValidateValueDataIllegal() {
        return new Object[][] { { null }, { "abc" } };
    }

    @Test(dataProvider = "decodeValueDataIllegal", expectedExceptions = { NullPointerException.class,
            IllegalPropertyValueStringException.class })
    public void testDecodeValueIllegal(String value) {
        BooleanPropertyDefinition def = createPropertyDefinition();
        def.decodeValue(value, PropertyDefinitionsOptions.NO_VALIDATION_OPTIONS);
    }

    private BooleanPropertyDefinition createPropertyDefinition() {
        return builder.getInstance();
    }

}
