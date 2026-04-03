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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.extensions;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.server.config.meta.AttributeValuePasswordValidatorCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.util.CollectionUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
  @BeforeClass
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
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-test-reversed-password: true",
         "",
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-test-reversed-password: true",
         "",
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-enabled: true",
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
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-test-reversed-password: false",
         "",
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-check-substrings: false",
         "ds-cfg-test-reversed-password: true",
         "ds-cfg-enabled: true");

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
    AttributeValuePasswordValidator validator = initializePasswordValidator(e);
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
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-test-reversed-password: invalid",
         "",
         // Invalid match attribute.
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-enabled: true",
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
    initializePasswordValidator(e);
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
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-enabled: true",
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
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-test-reversed-password: true"),
        "test",
        false
      },

      // Default configuration, with a password that matches the reverse of an
      // existing attribute value with reverse matching enabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-test-reversed-password: true"),
        "tset",
        false
      },

      // Default configuration, with a password that matches the reverse of an
      // existing attribute value with reverse matching disabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-enabled: true",
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
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-enabled: true",
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
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-match-attribute: cn",
             "ds-cfg-match-attribute: sn",
             "ds-cfg-match-attribute: givenName",
             "ds-cfg-test-reversed-password: true"),
        "test.user",
        true
      },

      // Default configuration, with a password that contains a substring
      // from one of the attributes in the entry.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Attribute Value,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-attribute-value-password-validator",
             "cn: Attribute Value",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "AttributeValuePasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-check-substrings: true",
             "ds-cfg-test-reversed-password: true"),
        "test.user99",
        false
      },
    };
  }



  /**
   * Retrieves test data for substring and reversed-password substring checks
   * using a user entry with uid=USN123.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "substringTestData")
  public Object[][] getSubstringTestData()
         throws Exception
  {
    Entry configEntry = TestCaseUtils.makeEntry(
         "dn: cn=Attribute Value,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-attribute-value-password-validator",
         "cn: Attribute Value",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "AttributeValuePasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-match-attribute: uid",
         "ds-cfg-check-substrings: true",
         "ds-cfg-min-substring-length: 3",
         "ds-cfg-test-reversed-password: true");

    return new Object[][]
    {
      // BLOCK: forward match "N12" in "USN123"
      new Object[] { configEntry, "USN123aa", false },
      // BLOCK: forward match "N12" in "USN123"
      new Object[] { configEntry, "aaUSN123", false },
      // BLOCK: forward match "123" in "USN123"
      new Object[] { configEntry, "U1sn123b", false },
      // BLOCK: reverse-password match "123" — reversed("NsU321ab")="ba123UsN" contains "123"
      new Object[] { configEntry, "NsU321ab", false },
      // BLOCK: forward match "N12" in "USN123"
      new Object[] { configEntry, "A9USN12z", false },
      // BLOCK: forward match "USN" in "USN123"
      new Object[] { configEntry, "xx123USN", false },
      // BLOCK: reverse-password match "USN" — reversed("NSU123xy")="yx321USN" contains "USN"
      new Object[] { configEntry, "NSU123xy", false },
      // BLOCK: forward match "N12" in "USN123"
      new Object[] { configEntry, "z9nUSN12", false },
      // BLOCK: reverse-password match "123" — reversed("usN321AA")="AA123Nsu" contains "123"
      new Object[] { configEntry, "usN321AA", false },
      // BLOCK: forward match "USN" in "USN123"
      new Object[] { configEntry, "1USN2abc", false },

      // PASS: no username substrings detected
      new Object[] { configEntry, "Sun3RiseA", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Rock7fall", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Tree9Bark", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Wave4Deep", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Glow5Star", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Rain8Drop", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Fire6Ash", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Mist2Hill", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Frog1Lake", true },
      // PASS: no username substrings detected
      new Object[] { configEntry, "Dust7Moon", true },
    };
  }



  /**
   * Tests substring and reversed-password substring checks against a user
   * entry with uid=USN123.
   *
   * @param  configEntry  The configuration entry to use for the password
   *                      validator.
   * @param  password     The password to test with the validator.
   * @param  acceptable   Indicates whether the provided password should be
   *                      considered acceptable.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "substringTestData")
  public void testSubstringPasswordIsAcceptable(Entry configEntry,
                                                String password,
                                                boolean acceptable)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=USN123,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: USN123",
         "givenName: USN",
         "sn: 123",
         "cn: USN 123",
         "userPassword: doesntmatter");

    AttributeValuePasswordValidator validator = initializePasswordValidator(configEntry);

    ByteString pwOS = ByteString.valueOfUtf8(password);
    ArrayList<Modification> mods = CollectionUtils.newArrayList(
        new Modification(ModificationType.REPLACE, Attributes.create("userpassword", password)));

    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(),
                             DN.valueOf("uid=USN123,o=test"), mods);

    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    assertEquals(validator.passwordIsAcceptable(pwOS,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
                 acceptable, invalidReason.toString());

    validator.finalizePasswordValidator();
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

    AttributeValuePasswordValidator validator = initializePasswordValidator(configEntry);

    ByteString pwOS = ByteString.valueOfUtf8(password);
    ArrayList<Modification> mods = CollectionUtils.newArrayList(
        new Modification(ModificationType.REPLACE, Attributes.create("userpassword", password)));

    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
                             new ArrayList<Control>(),
                             DN.valueOf("uid=test.user,o=test"), mods);

    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    assertEquals(validator.passwordIsAcceptable(pwOS,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
                 acceptable, invalidReason.toString());

    validator.finalizePasswordValidator();
  }

  private AttributeValuePasswordValidator initializePasswordValidator(Entry configEntry) throws Exception {
    return InitializationUtils.initializePasswordValidator(
        new AttributeValuePasswordValidator(), configEntry, AttributeValuePasswordValidatorCfgDefn.getInstance());
  }
}
