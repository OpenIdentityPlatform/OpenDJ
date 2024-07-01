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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.RepeatedCharactersPasswordValidatorCfgDefn;
import org.forgerock.opendj.server.config.server.RepeatedCharactersPasswordValidatorCfg;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A set of test cases for the repeated characters password validator.
 */
public class RepeatedCharactersPasswordValidatorTestCase
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
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: false",
         "",
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: true",
         "",
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 0",
         "ds-cfg-case-sensitive-validation: false");

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
    RepeatedCharactersPasswordValidator validator = initializePasswordValidator(e);
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
         // Missing maximum consecutive length
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-case-sensitive-validation: false",
         "",
         // Missing case-sensitive validation
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "",
         // Non-numeric maximum consecutive length
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: non-numeric",
         "ds-cfg-case-sensitive-validation: false",
         "",
         // Non-boolean case-sensitive validation
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: non-boolean",
         "",
         // Maximum consecutive length out of range.
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: -1",
         "ds-cfg-case-sensitive-validation: false");

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

  private RepeatedCharactersPasswordValidator initializePasswordValidator(Entry e)
        throws ConfigException, InitializationException {
    return InitializationUtils.initializePasswordValidator(
        new RepeatedCharactersPasswordValidator(), e, RepeatedCharactersPasswordValidatorCfgDefn.getInstance());
  }

  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * within the constraints of the password validator.  Case-sensitivity will
   * not be an issue.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptable2Consecutive()
         throws Exception
  {
    assertAcceptable("password", 2, false, true);
  }

  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * outside of the constraints of the password validator.  Case-sensitivity
   * will not be an issue.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptable3Consecutive()
         throws Exception
  {
    assertAcceptable("passsword", 2, false, false);
  }

  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * within the constraints of the password validator only because it is
   * configured to operate in a case-sensitive manner.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptableCaseSensitive()
         throws Exception
  {
    assertAcceptable("passSword", 2, true, true);
  }



  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * outside of the constraints of the password validator because it is
   * configured to operate in a case-insensitive manner.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptableCaseInsensitive()
         throws Exception
  {
    assertAcceptable("passSword", 2, false, false);
  }



  /**
   * Tests the {@code passwordIsAcceptable} method when the validator is
   * configured to accept any number of repeated characters.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptableUnlimitedRepeats()
         throws Exception
  {
    assertAcceptable("aaaaaaaa", 0, true, true);
  }

  /**
   * Tests the ability of the password validator to change its behavior when
   * the configuration is updated.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptableConfigurationChange()
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

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 0",
         "ds-cfg-case-sensitive-validation: true");

    RepeatedCharactersPasswordValidator validator = initializePasswordValidator(validatorEntry);

    String value = "aaaaaaaa";
    assertAcceptable(validator, value, userEntry, true);

    Entry updatedValidatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: true");

    RepeatedCharactersPasswordValidatorCfg updatedConfiguration = InitializationUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              updatedValidatorEntry);

    ArrayList<LocalizableMessage> unacceptableReasons = new ArrayList<>();
    assertTrue(validator.isConfigurationChangeAcceptable(updatedConfiguration,
                                                         unacceptableReasons),
               String.valueOf(unacceptableReasons));

    ConfigChangeResult changeResult =
         validator.applyConfigurationChange(updatedConfiguration);
    assertEquals(changeResult.getResultCode(), ResultCode.SUCCESS);

    assertAcceptable(validator, value, userEntry, false);

    validator.finalizePasswordValidator();
  }

  private void assertAcceptable(String value, int maxConsecutiveLength, boolean caseSensitiveValidation,
      boolean expectedResult) throws Exception, ConfigException, DirectoryException
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

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions.RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: " + maxConsecutiveLength,
         "ds-cfg-case-sensitive-validation: " + caseSensitiveValidation);

    RepeatedCharactersPasswordValidator validator = initializePasswordValidator(validatorEntry);
    assertAcceptable(validator, value, userEntry, expectedResult);
    validator.finalizePasswordValidator();
  }

  private void assertAcceptable(RepeatedCharactersPasswordValidator validator, String value, Entry userEntry,
      boolean expectedResult) throws DirectoryException
  {
    ByteString password = ByteString.valueOfUtf8(value);
    ModifyOperationBasis modifyOperation = replaceUserPasswordAttribute(value);

    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    boolean acceptable =
        validator.passwordIsAcceptable(password, new HashSet<ByteString>(0), modifyOperation, userEntry, invalidReason);
    assertEquals(acceptable, expectedResult, invalidReason.toString());
  }

  private ModifyOperationBasis replaceUserPasswordAttribute(String newValue) throws DirectoryException
  {
    ArrayList<Modification> mods = newArrayList(
        new Modification(REPLACE, Attributes.create("userpassword", newValue)));

    return new ModifyOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        new ArrayList<Control>(), DN.valueOf("uid=test.user,o=test"), mods);
  }
}
