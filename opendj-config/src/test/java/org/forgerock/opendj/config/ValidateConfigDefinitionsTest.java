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
 * Portions copyright 2011-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
@Test(singleThreaded = true)
public class ValidateConfigDefinitionsTest extends ConfigTestCase {

    private static final String EOL = System.getProperty("line.separator");

    @BeforeClass
    public void setup() throws Exception {
        TestCfg.setUp();
    }

    @AfterClass
    public void tearDown() {
        TestCfg.cleanup();
    }

    @DataProvider
    Object[][] enumerateManageObjectDefns() throws Exception {
        TopCfgDefn topCfgDefn = TopCfgDefn.getInstance();
        List<AbstractManagedObjectDefinition<?, ?>> allCfgDefns = new ArrayList<>(topCfgDefn.getAllChildren());

        Object[][] params = new Object[allCfgDefns.size()][];
        for (int i = 0; i < params.length; i++) {
            params[i] = new Object[] { allCfgDefns.get(i) };
        }
        System.out.println(params.length);
        return params;
    }

    /** Exceptions to config objects having a different objectclass. */
    private static final List<String> CLASS_OBJECT_CLASS_EXCEPTIONS = Arrays.asList(new String[] {
        "org.forgerock.opendj.config.std.meta.RootCfgDefn", "org.forgerock.opendj.config.std.meta.GlobalCfgDefn", });

    /** TODO : does not work because can't retrieve object class objects */
    @Test(enabled = false, dataProvider = "enumerateManageObjectDefns")
    public void validateConfigObjectDefinitions(AbstractManagedObjectDefinition<?, ?> objectDef) {
        String objName = objectDef.getName();
        StringBuilder errors = new StringBuilder();
        Collection<PropertyDefinition<?>> allPropertyDefs = objectDef.getAllPropertyDefinitions();

        LDAPProfile ldapProfile = LDAPProfile.getInstance();
        String ldapObjectclassName = ldapProfile.getObjectClass(objectDef);
        if (ldapObjectclassName == null) {
            errors.append("There is no objectclass definition for configuration object " + objName);
        } else {
            String expectedObjectClass = "ds-cfg-" + objName;
            if (!ldapObjectclassName.equals(expectedObjectClass)
                && !CLASS_OBJECT_CLASS_EXCEPTIONS.contains(objectDef.getClass().getName())) {
                errors.append(
                    "For config object " + objName + ", the LDAP objectclass must be " + expectedObjectClass
                        + " instead of " + ldapObjectclassName).append(EOL + EOL);
            }
        }
        ObjectClass configObjectClass =
            Schema.getDefaultSchema().asNonStrictSchema().getObjectClass(ldapObjectclassName.toLowerCase());

        for (PropertyDefinition<?> propDef : allPropertyDefs) {
            validatePropertyDefinition(objectDef, configObjectClass, propDef, errors);
        }

        assertTrue(errors.length() != 0, "The configuration definition for " + objectDef.getName()
               + " has the following problems: " + EOL + errors);
    }

    /** Exceptions to properties ending in -class being exactly 'java-class'. */
    private static final List<String> CLASS_PROPERTY_EXCEPTIONS = Arrays.asList(new String[] {
    // e.g. "prop-name-ending-with-class"
    });

    /** Exceptions to properties ending in -enabled being exactly 'enabled'. */
    private static final List<String> ENABLED_PROPERTY_EXCEPTIONS = Arrays.asList(new String[] {
        "index-filter-analyzer-enabled", "subordinate-indexes-enabled"
    // e.g. "prop-name-ending-with-enabled"
    });

    /** Exceptions to properties not starting with the name of their config object. */
    private static final List<String> OBJECT_PREFIX_PROPERTY_EXCEPTIONS = Arrays.asList(new String[] { "backend-id",
        "plugin-type", "replication-server-id", "network-group-id", "workflow-id", "workflow-element-id",
        "workflow-element"
    // e.g. "prop-name-starting-with-object-prefix"
    });

    private void validatePropertyDefinition(AbstractManagedObjectDefinition<?, ?> objectDef,
        ObjectClass configObjectClass, PropertyDefinition<?> propDef, StringBuilder errors) {
        String objName = objectDef.getName();
        String propName = propDef.getName();

        // We want class properties to be exactly java-class
        if (propName.endsWith("-class") && !propName.equals("java-class")
            && !CLASS_PROPERTY_EXCEPTIONS.contains(propName)) {
            errors.append("The " + propName + " property on config object " + objName
                + " should probably be java-class.  If not, then add " + propName
                + " to the CLASS_PROPERTY_EXCEPTIONS array in " + ValidateConfigDefinitionsTest.class.getName()
                + " to suppress" + " this warning.");
        }

        // We want enabled properties to be exactly enabled
        if (propName.endsWith("-enabled") && !ENABLED_PROPERTY_EXCEPTIONS.contains(propName)) {
            errors.append("The " + propName + " property on config object " + objName
                + " should probably be just 'enabled'.  If not, then add " + propName
                + " to the ENABLED_PROPERTY_EXCEPTIONS array in " + ValidateConfigDefinitionsTest.class.getName()
                + " to suppress" + " this warning.");
        }

        // It's redundant for properties to be prefixed with the name of their
        // objecty
        if (propName.startsWith(objName) && !propName.equals(objName)
            && !OBJECT_PREFIX_PROPERTY_EXCEPTIONS.contains(propName)) {
            errors.append("The " + propName + " property on config object " + objName
                + " should not be prefixed with the name of the config object because"
                + " this is redundant.  If you disagree, then add " + propName
                + " to the OBJECT_PREFIX_PROPERTY_EXCEPTIONS array in "
                + ValidateConfigDefinitionsTest.class.getName() + " to suppress" + " this warning.");
        }

        LDAPProfile ldapProfile = LDAPProfile.getInstance();
        String ldapAttrName = ldapProfile.getAttributeName(objectDef, propDef);

        // LDAP attribute name is consistent with the property name
        String expectedLdapAttr = "ds-cfg-" + propName;
        if (!ldapAttrName.equals(expectedLdapAttr)) {
            errors.append(
                "For the " + propName + " property on config object " + objName + ", the LDAP attribute must be "
                    + expectedLdapAttr + " instead of " + ldapAttrName).append(EOL + EOL);
        }

        Schema schema = Schema.getDefaultSchema();
        AttributeType attrType = schema.getAttributeType(ldapAttrName);

        // LDAP attribute exists
        if (attrType == null) {
            errors.append(
                propName + " property on config object " + objName + " is declared" + " to use ldap attribute "
                    + ldapAttrName + ", but this attribute is not in the schema ").append(EOL + EOL);
        } else {

            // LDAP attribute is multivalued if the property is multivalued
            if (propDef.hasOption(PropertyOption.MULTI_VALUED) && attrType.isSingleValue()) {
                errors.append(
                    propName + " property on config object " + objName + " is declared"
                        + " as multi-valued, but the corresponding ldap attribute " + ldapAttrName
                        + " is declared as single-valued.").append(EOL + EOL);
            }

            if (configObjectClass != null) {
                // If it's mandatory in the schema, it must be mandatory on the
                // config property
                Set<AttributeType> mandatoryAttributes = configObjectClass.getRequiredAttributes();
                if (mandatoryAttributes.contains(attrType) && !propDef.hasOption(PropertyOption.MANDATORY)) {
                    errors.append(
                        propName + " property on config object " + objName + " is not declared"
                            + " as mandatory even though the corresponding ldap attribute " + ldapAttrName
                            + " is declared as mandatory in the schema.").append(EOL + EOL);
                }

                Set<AttributeType> allowedAttributes = new HashSet<>(mandatoryAttributes);
                allowedAttributes.addAll(configObjectClass.getOptionalAttributes());
                if (!allowedAttributes.contains(attrType)) {
                    errors.append(
                        propName + " property on config object " + objName + " has"
                            + " the corresponding ldap attribute " + ldapAttrName
                            + ", but this attribute is not an allowed attribute on the configuration "
                            + " object's objectclass " + configObjectClass.getNameOrOID()).append(EOL + EOL);
                }
            }
        }
    }

}
