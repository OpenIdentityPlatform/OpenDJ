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
