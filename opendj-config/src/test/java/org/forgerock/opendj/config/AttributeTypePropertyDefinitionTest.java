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

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AttributeTypePropertyDefinitionTest extends ConfigTestCase {

    @Test
    public void testValidateValue() {
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        propertyDef.validateValue(Schema.getDefaultSchema().getAttributeType("cn"));
    }

    @DataProvider(name = "valueLegalData")
    public Object[][] createValidateValueLegalData() {
        return new Object[][] { { "cn" }, { "o" }, { "ou" } };
    }

    @Test(dataProvider = "valueLegalData")
    public void testDecodeValue(String value) {
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        AttributeType expected = Schema.getDefaultSchema().getAttributeType(value);
        assertEquals(propertyDef.decodeValue(value), expected);
    }

    @Test(dataProvider = "valueLegalData")
    public void testEncodeValue(String value) {
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        assertEquals(propertyDef.encodeValue(propertyDef.decodeValue(value)), value);
    }

    @DataProvider(name = "valueIllegalData")
    public Object[][] createValidateValueIllegalData() {
        return new Object[][] { { "dummy-type-xxx" } };
    }

    @Test(dataProvider = "valueIllegalData", expectedExceptions = { PropertyException.class })
    public void testDecodeValueIllegal(String value) {
        ConfigurationFramework.getInstance().setIsClient(false);
        try {
            AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
            propertyDef.decodeValue(value);
        } finally {
            ConfigurationFramework.getInstance().setIsClient(true);
        }
    }

    @Test(dataProvider = "valueIllegalData")
    public void testDecodeValueIllegalNoSchemaCheck(String value) {
        AttributeTypePropertyDefinition propertyDef = createPropertyDefinition();
        AttributeType type = propertyDef.decodeValue(value);
        assertEquals(type.getNameOrOID(), value);
    }

    /** Create a new definition. */
    private AttributeTypePropertyDefinition createPropertyDefinition() {
        AttributeTypePropertyDefinition.Builder builder = AttributeTypePropertyDefinition.createBuilder(
                RootCfgDefn.getInstance(), "test-property");
        return builder.getInstance();
    }

}
