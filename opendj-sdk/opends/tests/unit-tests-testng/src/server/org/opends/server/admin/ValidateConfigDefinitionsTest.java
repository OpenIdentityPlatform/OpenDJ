/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;

import static org.opends.server.util.ServerConstants.*;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


public class ValidateConfigDefinitionsTest extends DirectoryServerTestCase {

  @BeforeClass
  public void startServer() throws Exception {
    TestCaseUtils.startServer();
  }

  /**
   * Returns all AbstractManagedObjectDefinition objects that are
   * defined in
   */
  @DataProvider
  public Object[][] enumrateManageObjectDefns() throws Exception {
    TopCfgDefn topCfgDefn = TopCfgDefn.getInstance();
    List<AbstractManagedObjectDefinition<?,?>> allCfgDefns =
            new ArrayList<AbstractManagedObjectDefinition<?,?>>(topCfgDefn.getAllChildren());

    Object[][] params = new Object[allCfgDefns.size()][];
    for (int i = 0; i < params.length; i++) {
      params[i] = new Object[]{allCfgDefns.get(i)};
    }

    return params;
  }

  // Exceptions to config objects having a differnt objectclass
  private static final List<String> CLASS_OBJECT_CLASS_EXCEPTIONS =
          Arrays.asList(new String[]{
                  "org.opends.server.admin.std.meta.RootCfgDefn",
                  "org.opends.server.admin.std.meta.GlobalCfgDefn",
          });


  @Test(dataProvider="enumrateManageObjectDefns")
  public void validateConfigObjectDefinitions(AbstractManagedObjectDefinition<?, ?> objectDef) {
    String objName = objectDef.getName();
    StringBuilder errors = new StringBuilder(); 
    Collection<PropertyDefinition<?>> allDefinitions =
            objectDef.getAllPropertyDefinitions();

    LDAPProfile ldapProfile = LDAPProfile.getInstance();
    String ldapObjectclassName = ldapProfile.getObjectClass(objectDef);
    if (ldapObjectclassName == null) {
      errors.append("There is no objectclass definition for configuration object " + objName);
    } else {
      String expectedObjectClass = "ds-cfg-" + objName;
      if (!ldapObjectclassName.equals(expectedObjectClass) &&
          !CLASS_OBJECT_CLASS_EXCEPTIONS.contains(objectDef.getClass().getName())) {
        errors.append("For config object " + objName +
                ", the LDAP objectclass must be " + expectedObjectClass +
                " instead of " + ldapObjectclassName).append(EOL + EOL);
      }
    }
    ObjectClass configObjectClass = DirectoryServer.getSchema().getObjectClass(ldapObjectclassName.toLowerCase());;

    for (PropertyDefinition<?> propDef: allDefinitions) {     
      validatePropertyDefinition(objectDef, configObjectClass, propDef, errors);
    }

    if (errors.length() > 0) {
      Assert.fail("The configuration definition for " + objectDef.getName() + " has the following problems: " + EOL +
                  errors.toString());
    }
  }

  // Exceptions to properties ending in -class being exactly 'java-class'.
  private static final List<String> CLASS_PROPERTY_EXCEPTIONS =
          Arrays.asList(new String[]{
                  // e.g. "prop-name-ending-with-class"
          });

  // Exceptions to properties ending in -enabled being exactly 'enabled'.
  private static final List<String> ENABLED_PROPERTY_EXCEPTIONS =
          Arrays.asList(new String[]{
                  // e.g. "prop-name-ending-with-enabled"
          });

  // Exceptions to properties not starting with the name of their config object
  private static final List<String> OBJECT_PREFIX_PROPERTY_EXCEPTIONS =
          Arrays.asList(new String[]{
                  "backend-id",
                  "plugin-type",
                  "replication-server-id",
                  "network-group-id",
                  "workflow-id",
                  "workflow-element-id",
                  "workflow-element"
                  // e.g. "prop-name-starting-with-object-prefix"
          });


  private void validatePropertyDefinition(AbstractManagedObjectDefinition<?, ?> objectDef,
                                          ObjectClass configObjectClass,
                                          PropertyDefinition<?> propDef,
                                          StringBuilder errors) {
    String objName = objectDef.getName();
    String propName = propDef.getName();

    // We want class properties to be exactly java-class
    if (propName.endsWith("-class") &&
        !propName.equals("java-class") &&
        !CLASS_PROPERTY_EXCEPTIONS.contains(propName))
    {
      errors.append("The " + propName + " property on config object " + objName +
              " should probably be java-class.  If not, then add " +
              propName + " to the CLASS_PROPERTY_EXCEPTIONS array in " +
              ValidateConfigDefinitionsTest.class.getName() + " to suppress" +
              " this warning.");
    }

    // We want enabled properties to be exactly enabled
    if (propName.endsWith("-enabled") && !ENABLED_PROPERTY_EXCEPTIONS.contains(propName))
    {
      errors.append("The " + propName + " property on config object " + objName +
              " should probably be just 'enabled'.  If not, then add " +
              propName + " to the ENABLED_PROPERTY_EXCEPTIONS array in " +
              ValidateConfigDefinitionsTest.class.getName() + " to suppress" +
              " this warning.");
    }

    // It's redundant for properties to be prefixed with the name of their objecty
    if (propName.startsWith(objName) && !propName.equals(objName) && 
            !OBJECT_PREFIX_PROPERTY_EXCEPTIONS.contains(propName))
    {
      errors.append("The " + propName + " property on config object " + objName +
              " should not be prefixed with the name of the config object because" +
              " this is redundant.  If you disagree, then add " +
              propName + " to the OBJECT_PREFIX_PROPERTY_EXCEPTIONS array in " +
              ValidateConfigDefinitionsTest.class.getName() + " to suppress" +
              " this warning.");
    }


    LDAPProfile ldapProfile = LDAPProfile.getInstance();
    String ldapAttrName = ldapProfile.getAttributeName(objectDef, propDef);

    // LDAP attribute name is consistent with the property name
    String expectedLdapAttr = "ds-cfg-" + propName;
    if (!ldapAttrName.equals(expectedLdapAttr)) {
      errors.append("For the " + propName + " property on config object " + objName +
              ", the LDAP attribute must be " + expectedLdapAttr + " instead of " + ldapAttrName).append(EOL + EOL);
    }


    Schema schema = DirectoryServer.getSchema();
    AttributeType attrType = schema.getAttributeType(ldapAttrName.toLowerCase());

    // LDAP attribute exists
    if (attrType == null) {
      errors.append(propName + " property on config object " + objName + " is declared" +
               " to use ldap attribute " + ldapAttrName + ", but this attribute is not in the schema ").append(EOL + EOL);
    } else {

      // LDAP attribute is multivalued if the property is multivalued
      if (propDef.hasOption(PropertyOption.MULTI_VALUED) && attrType.isSingleValue()) {
        errors.append(propName + " property on config object " + objName + " is declared" +
                 " as multi-valued, but the corresponding ldap attribute " + ldapAttrName +
                 " is declared as single-valued.").append(EOL + EOL);
      }

      if (configObjectClass != null) {
        // If it's mandatory in the schema, it must be mandatory on the config property
        Set<AttributeType> mandatoryAttributes = configObjectClass.getRequiredAttributeChain();
        if (mandatoryAttributes.contains(attrType) && !propDef.hasOption(PropertyOption.MANDATORY)) {
          errors.append(propName + " property on config object " + objName + " is not declared" +
                   " as mandatory even though the corresponding ldap attribute " + ldapAttrName +
                   " is declared as mandatory in the schema.").append(EOL + EOL);
        }

        Set<AttributeType> allowedAttributes = new HashSet<AttributeType>(mandatoryAttributes);
        allowedAttributes.addAll(configObjectClass.getOptionalAttributeChain());
        if (!allowedAttributes.contains(attrType)) {
          errors.append(propName + " property on config object " + objName + " has" +
                   " the corresponding ldap attribute " + ldapAttrName +
                   ", but this attribute is not an allowed attribute on the configuration " +
                   " object's objectclass " + configObjectClass.getNameOrOID()).append(EOL + EOL);
        }
      }
    }
  }



}
