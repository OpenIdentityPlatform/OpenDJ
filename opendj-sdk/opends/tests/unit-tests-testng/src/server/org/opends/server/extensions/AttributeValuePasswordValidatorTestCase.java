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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.AttributeValuePasswordValidatorCfgDefn;
import org.opends.server.admin.std.server.AttributeValuePasswordValidatorCfg;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of test cases for the attribute value password validator.
 */
public class AttributeValuePasswordValidatorTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Retrieves a set of valid configuration entries that may be used to
   * initialize the validator.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-test-reversed-password: true",
         "",
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-test-reversed-password: true",
         "",
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-match-attribute: cn",
         "ds-cfg-match-attribute: givenName",
         "ds-cfg-match-attribute: sn",
         "ds-cfg-test-reversed-password: true",
         "",
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-test-reversed-password: false");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with valid configurations.
   *
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs", groups= { "slow" })
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    AttributeValuePasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              AttributeValuePasswordValidatorCfgDefn.getInstance(), e);

    AttributeValuePasswordValidator validator =
         new AttributeValuePasswordValidator();
    validator.initializePasswordValidator(configuration);
    validator.finalizePasswordValidator();
  }



  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         // Invalid test-reversed-password
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-test-reversed-password: invalid",
         "",
         // Invalid match attribute.
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-match-attribute: nosuchattribute",
         "ds-cfg-test-reversed-password: true");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with invalid configurations.
   *
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    AttributeValuePasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              AttributeValuePasswordValidatorCfgDefn.getInstance(), e);

    AttributeValuePasswordValidator validator =
         new AttributeValuePasswordValidator();
    validator.initializePasswordValidator(configuration);
  }



  /**
   * Retrieves a set of data to use when testing a given password with a
   * provided configuration.  Each element of the returned array should be an
   * array of a configuration entry, a test password string, and an indication
   * as to whether the provided password should be acceptable.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "testData")
  public Object[][] getTestData()
         throws Exception
  {
    return new Object[][]
    {
      // Default configuration, with a password that does not match an existing
      // attribute value.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-password-validator-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-password-validator-enabled: true",
             "ds-cfg-test-reversed-password: true"),
        "password",
        true
      },

      // Default configuration, with a password that matches an existing
      // attribute value.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-password-validator-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-password-validator-enabled: true",
             "ds-cfg-test-reversed-password: true"),
        "test",
        false
      },

      // Default configuration, with a password that matches the reverse of an
      // existing attribute value with reverwse matching enabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-password-validator-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-password-validator-enabled: true",
             "ds-cfg-test-reversed-password: true"),
        "tset",
        false
      },

      // Default configuration, with a password that matches the reverse of an
      // existing attribute value with reverwse matching disabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-password-validator-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-password-validator-enabled: true",
             "ds-cfg-test-reversed-password: false"),
        "tset",
        true
      },

      // Default configuration, with a password that matches one of the values
      // of a specified set of attributes.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-password-validator-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-password-validator-enabled: true",
             "ds-cfg-match-attribute: cn",
             "ds-cfg-match-attribute: sn",
             "ds-cfg-match-attribute: givenName",
             "ds-cfg-test-reversed-password: true"),
        "test",
        false
      },

      // Default configuration, with a password that doesn't match any of the
      // values of a specified set of attributes but does match the value of
      // another attribute in the entry.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-password-validator-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-password-validator-enabled: true",
             "ds-cfg-match-attribute: cn",
             "ds-cfg-match-attribute: sn",
             "ds-cfg-match-attribute: givenName",
             "ds-cfg-test-reversed-password: true"),
        "test.user",
        true
      },
    };
  }



  /**
   * Tests the {@code passwordIsAcceptable} method using the provided
   * information.
   *
   * @param  configEntry  The configuration entry to use for the password
   *                      validator.
   * @param  password     The password to test with the validator.
   * @param  acceptable   Indicates whether the provided password should be
   *                      considered acceptable.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testData")
  public void testPasswordIsAcceptable(Entry configEntry, String password,
                                       boolean acceptable)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: doesntmatter");

    AttributeValuePasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              AttributeValuePasswordValidatorCfgDefn.getInstance(),
              configEntry);

    AttributeValuePasswordValidator validator =
         new AttributeValuePasswordValidator();
    validator.initializePasswordValidator(configuration);

    ASN1OctetString pwOS = new ASN1OctetString(password);
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("userpassword", password)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation =
         new ModifyOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    StringBuilder invalidReason = new StringBuilder();
    assertEquals(validator.passwordIsAcceptable(pwOS,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
                 acceptable, invalidReason.toString());

    validator.finalizePasswordValidator();
  }
}

