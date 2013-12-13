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
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AttributeTypePropertyDefinitionTest extends ConfigTestCase {

    @BeforeClass
    public void setUp() throws Exception {
        disableClassValidationForProperties();
    }

    @Test
    public void testValidateValue() {
        AttributeTypePropertyDefinition.setCheckSchema(true);
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        propertyDef.validateValue(Schema.getDefaultSchema().getAttributeType("cn"));
    }

    @DataProvider(name = "valueLegalData")
    public Object[][] createValidateValueLegalData() {
        return new Object[][] { { "cn" }, { "o" }, { "ou" } };
    }

    @Test(dataProvider = "valueLegalData")
    public void testDecodeValue(String value) {
        AttributeTypePropertyDefinition.setCheckSchema(true);
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        AttributeType expected = Schema.getDefaultSchema().getAttributeType(value);
        assertEquals(propertyDef.decodeValue(value), expected);
    }

    @Test(dataProvider = "valueLegalData")
    public void testEncodeValue(String value) {
        AttributeTypePropertyDefinition.setCheckSchema(true);
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        assertEquals(propertyDef.encodeValue(propertyDef.decodeValue(value)), value);
    }

    @DataProvider(name = "valueIllegalData")
    public Object[][] createValidateValueIllegalData() {
        return new Object[][] { { "dummy-type-xxx" } };
    }

    @Test(dataProvider = "valueIllegalData", expectedExceptions = { IllegalPropertyValueStringException.class })
    public void testDecodeValueIllegal(String value) {
        AttributeTypePropertyDefinition.setCheckSchema(true);
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        propertyDef.decodeValue(value);
    }

    @Test(dataProvider = "valueIllegalData")
    public void testDecodeValueIllegalNoSchemaCheck(String value) {
        AttributeTypePropertyDefinition.setCheckSchema(false);
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        AttributeType type = propertyDef.decodeValue(value);
        assertEquals(type.getNameOrOID(), value);

        // Make sure to turn schema checking back on
        // so that other tests which depend on it don't fail.
        AttributeTypePropertyDefinition.setCheckSchema(true);
    }

    // Create a new definition.
    private AttributeTypePropertyDefinition createPropertyDefinition() {
        AttributeTypePropertyDefinition.Builder builder = AttributeTypePropertyDefinition.createBuilder(
                RootCfgDefn.getInstance(), "test-property");
        return builder.getInstance();
    }

}
