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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;

import static org.testng.Assert.*;



/**
 * A set of test cases for the length-based password validator.
 */
public class LengthBasedPasswordValidatorTestCase
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
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 6",
         "ds-cfg-maximum-password-length: 0",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 6",
         "ds-cfg-maximum-password-length: 10",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 0",
         "ds-cfg-maximum-password-length: 0",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 6",
         "ds-cfg-maximum-password-length: 6",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 6",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 0",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-maximum-password-length: 10",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true");

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
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    DN parentDN = DN.decode("cn=Password Validators,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    LengthBasedPasswordValidator validator =
         new LengthBasedPasswordValidator();
    validator.initializePasswordValidator(configEntry);
    validator.finalizePasswordValidator();
  }



  /**
   * Retrieves a set of invvalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: -1",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: notNumeric",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-maximum-password-length: -1",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-maximum-password-length: notNumeric",
         "",
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 6",
         "ds-cfg-maximum-password-length: 5");

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
    DN parentDN = DN.decode("cn=Password Validators,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    LengthBasedPasswordValidator validator =
         new LengthBasedPasswordValidator();
    validator.initializePasswordValidator(configEntry);
  }



  /**
   * Tests the <CODE>passwordIsValid</CODE> method with no constraints on
   * password length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsValidNoConstraints()
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
         "userPassword: password");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 0",
         "ds-cfg-maximum-password-length: 0");

    DN parentDN = DN.decode("cn=Password Validators,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(validatorEntry, parentEntry);

    LengthBasedPasswordValidator validator =
         new LengthBasedPasswordValidator();
    validator.initializePasswordValidator(configEntry);

    StringBuilder buffer = new StringBuilder();
    for (int i=0; i < 20; i++)
    {
      buffer.append('x');
      ASN1OctetString password = new ASN1OctetString(buffer.toString());

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
                                new Attribute("userPassword",
                                              buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation op =
           new ModifyOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), new ArrayList<Control>(),
                               DN.decode("cn=uid=test.user,o=test"), mods);

      StringBuilder invalidReason = new StringBuilder();
      assertTrue(validator.passwordIsValid(password, op, userEntry,
                                           invalidReason));
    }

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the <CODE>passwordIsValid</CODE> method with a constraint on the
   * minimum password length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsValidMinLengthConstraint()
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
         "userPassword: password");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 10",
         "ds-cfg-maximum-password-length: 0");

    DN parentDN = DN.decode("cn=Password Validators,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(validatorEntry, parentEntry);

    LengthBasedPasswordValidator validator =
         new LengthBasedPasswordValidator();
    validator.initializePasswordValidator(configEntry);

    StringBuilder buffer = new StringBuilder();
    for (int i=0; i < 20; i++)
    {
      buffer.append('x');
      ASN1OctetString password = new ASN1OctetString(buffer.toString());

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
                                new Attribute("userPassword",
                                              buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation op =
           new ModifyOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), new ArrayList<Control>(),
                               DN.decode("cn=uid=test.user,o=test"), mods);

      StringBuilder invalidReason = new StringBuilder();
      assertEquals((buffer.length() >= 10),
                   validator.passwordIsValid(password, op, userEntry,
                                             invalidReason));
    }

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the <CODE>passwordIsValid</CODE> method with a constraint on the
   * maximum password length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsValidMaxLengthConstraint()
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
         "userPassword: password");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 0",
         "ds-cfg-maximum-password-length: 10");

    DN parentDN = DN.decode("cn=Password Validators,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(validatorEntry, parentEntry);

    LengthBasedPasswordValidator validator =
         new LengthBasedPasswordValidator();
    validator.initializePasswordValidator(configEntry);

    StringBuilder buffer = new StringBuilder();
    for (int i=0; i < 20; i++)
    {
      buffer.append('x');
      ASN1OctetString password = new ASN1OctetString(buffer.toString());

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
                                new Attribute("userPassword",
                                              buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation op =
           new ModifyOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), new ArrayList<Control>(),
                               DN.decode("cn=uid=test.user,o=test"), mods);

      StringBuilder invalidReason = new StringBuilder();
      assertEquals((buffer.length() <= 10),
                   validator.passwordIsValid(password, op, userEntry,
                                             invalidReason));
    }

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the <CODE>passwordIsValid</CODE> method with constraints on both the
   * minimum and maximum password length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsValidMinAndMaxLengthConstraints()
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
         "userPassword: password");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Length-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-length-based-password-validator",
         "cn: Length-Based Password Validator",
         "ds-cfg-password-validator-class: org.opends.server.extensions." +
              "LengthBasedPasswordValidator",
         "ds-cfg-password-validator-enabled: true",
         "ds-cfg-minimum-password-length: 6",
         "ds-cfg-maximum-password-length: 10");

    DN parentDN = DN.decode("cn=Password Validators,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(validatorEntry, parentEntry);

    LengthBasedPasswordValidator validator =
         new LengthBasedPasswordValidator();
    validator.initializePasswordValidator(configEntry);

    StringBuilder buffer = new StringBuilder();
    for (int i=0; i < 20; i++)
    {
      buffer.append('x');
      ASN1OctetString password = new ASN1OctetString(buffer.toString());

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
                                new Attribute("userPassword",
                                              buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation op =
           new ModifyOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), new ArrayList<Control>(),
                               DN.decode("cn=uid=test.user,o=test"), mods);

      StringBuilder invalidReason = new StringBuilder();
      assertEquals(((buffer.length() >= 6) && (buffer.length() <= 10)),
                   validator.passwordIsValid(password, op, userEntry,
                                             invalidReason));
    }

    validator.finalizePasswordValidator();
  }
}

