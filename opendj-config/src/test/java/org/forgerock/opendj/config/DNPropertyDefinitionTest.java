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

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DNPropertyDefinitionTest extends ConfigTestCase {

    @DataProvider(name = "baseDN")
    public Object[][] createBuilderSetBaseDN() {
        return new Object[][] {
            { null },
            { "cn=key manager providers, cn=config" } };
    }


    @Test(dataProvider = "baseDN")
    public void testBuilderSetBaseDN(String baseDN) {
        DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.setBaseDN(baseDN);
        DNPropertyDefinition propertyDef = localBuilder.getInstance();

        DN actual = propertyDef.getBaseDN();
        DN expected = baseDN == null ? null : DN.valueOf(baseDN);

        assertEquals(actual, expected);
    }

    @DataProvider(name = "legalValues")
    public Object[][] createLegalValues() {
        return new Object[][] {
            // base DN, value to validate
            { null, "cn=config" },
            { null, "dc=example,dc=com" },
            { "", "cn=config" },
            { "cn=config", "cn=key manager providers, cn=config" },
            { "cn=key manager providers, cn=config", "cn=my provider, cn=key manager providers, cn=config" },
        };
    }

    @DataProvider(name = "illegalValues")
    public Object[][] createIllegalValues() {
        return new Object[][] {
            // Above base DN.
            { "cn=config", "" },

            // Same as base DN.
            { "cn=config", "cn=config" },

            // Same as base DN.
            { "cn=key manager providers, cn=config", "cn=key manager providers, cn=config" },

            // Too far beneath base DN.
            { "cn=config", "cn=my provider, cn=key manager providers, cn=config" },

            // Unrelated to base DN.
            { "cn=config", "dc=example, dc=com" }, };
    }

    @Test(dataProvider = "legalValues")
    public void testValidateLegalValues(String baseDN, String valueToValidate) {
        DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.setBaseDN(baseDN);
        DNPropertyDefinition propertyDef = localBuilder.getInstance();
        propertyDef.validateValue(DN.valueOf(valueToValidate));
    }

    @Test(dataProvider = "illegalValues", expectedExceptions = PropertyException.class)
    public void testValidateIllegalValues(String baseDN, String valueToValidate) {
        DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.setBaseDN(baseDN);
        DNPropertyDefinition propertyDef = localBuilder.getInstance();
        propertyDef.validateValue(DN.valueOf(valueToValidate));
    }

    @Test(dataProvider = "legalValues")
    public void testDecodeLegalValues(String baseDN, String valueToValidate) {
        DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.setBaseDN(baseDN);
        DNPropertyDefinition propertyDef = localBuilder.getInstance();
        propertyDef.decodeValue(valueToValidate);
    }

    @Test(dataProvider = "illegalValues", expectedExceptions = PropertyException.class)
    public void testDecodeIllegalValues(String baseDN, String valueToValidate) {
        DNPropertyDefinition.Builder localBuilder = DNPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        localBuilder.setBaseDN(baseDN);
        DNPropertyDefinition propertyDef = localBuilder.getInstance();
        propertyDef.decodeValue(valueToValidate);
    }
}
