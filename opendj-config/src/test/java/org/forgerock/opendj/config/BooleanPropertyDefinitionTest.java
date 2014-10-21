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

import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BooleanPropertyDefinitionTest extends ConfigTestCase {

    private BooleanPropertyDefinition.Builder builder;

    @BeforeClass
    public void setUp() throws Exception {
        builder = BooleanPropertyDefinition.createBuilder(RootCfgDefn.getInstance(), "test-property");
    }

    @Test
    public void testValidateValue() {
        BooleanPropertyDefinition def = createPropertyDefinition();
        def.validateValue(Boolean.TRUE);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testValidateValueIllegal() {
        BooleanPropertyDefinition def = createPropertyDefinition();
        def.validateValue(null);
    }

    @DataProvider(name = "decodeValueData")
    public Object[][] createValidateValueData() {
        return new Object[][] { { "false", Boolean.FALSE }, { "true", Boolean.TRUE } };
    }

    @Test(dataProvider = "decodeValueData")
    public void testDecodeValue(String value, Boolean expected) {
        BooleanPropertyDefinition def = createPropertyDefinition();
        assertEquals(def.decodeValue(value), expected);
    }

    @DataProvider(name = "decodeValueDataIllegal")
    public Object[][] createValidateValueDataIllegal() {
        return new Object[][] { { null }, { "abc" } };
    }

    @Test(dataProvider = "decodeValueDataIllegal", expectedExceptions = { NullPointerException.class,
            PropertyException.class })
    public void testDecodeValueIllegal(String value) {
        BooleanPropertyDefinition def = createPropertyDefinition();
        def.decodeValue(value);
    }

    private BooleanPropertyDefinition createPropertyDefinition() {
        return builder.getInstance();
    }

}
