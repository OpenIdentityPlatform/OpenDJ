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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;
import org.forgerock.opendj.server.config.meta.SimilarityBasedPasswordValidatorCfgDefn;

/**
 * A set of test cases for the Similarity-Based Password Reject.
 */
public class SimilarityBasedPasswordValidatorTestCase
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
         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-min-password-difference: 6",
         "",
         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-min-password-difference: 3",
         "",
         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-min-password-difference: 0"
         );

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
    initializePasswordValidator(e);
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
         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-min-password-difference: -1",
         "",

         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         // "ds-cfg-min-password-difference: -1", // error here
         "",

         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-min-password-difference: notNumeric");

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
   * Tests the <CODE>passwordIsAcceptable</CODE> method with no constraints on
   * password difference.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptableNoConstraints()
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
         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-min-password-difference: 0"
         );

    SimilarityBasedPasswordValidator validator = initializePasswordValidator(validatorEntry);

    StringBuilder buffer = new StringBuilder();
    for (int i=0; i < 20; i++)
    {
      buffer.append('x');
      ByteString password = ByteString.valueOfUtf8(buffer.toString());

      ArrayList<Modification> mods = newArrayList(
          new Modification(REPLACE, Attributes.create("userpassword", buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperationBasis op =
           new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), new ArrayList<Control>(),
                               DN.valueOf("cn=uid=test.user,o=test"), mods);

      LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
      assertTrue(validator.passwordIsAcceptable(password,
                                                new HashSet<ByteString>(0),
                                                op, userEntry, invalidReason));
    }

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the <CODE>passwordIsAcceptable</CODE> method with a constraint on the
   * minimum password difference.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testPasswordIsAcceptableMinDifferenceConstraint()
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
         "dn: cn=Similarity-Based Password Validator,cn=Password Validators," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-similarity-based-password-validator",
         "cn: Similarity-Based Password Validator",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "SimilarityBasedPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-min-password-difference: 3"
         );

    SimilarityBasedPasswordValidator validator = initializePasswordValidator(validatorEntry);

    StringBuilder buffer = new StringBuilder();
    HashSet<ByteString> currentPassword = new HashSet<>(3);
    currentPassword.add(ByteString.valueOfUtf8("xxx"));
    for (int i=0; i < 7; i++)
    {
      buffer.append('x');
      ByteString password = ByteString.valueOfUtf8(buffer.toString());

      ArrayList<Modification> mods = newArrayList(
          new Modification(REPLACE, Attributes.create("userpassword", buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperationBasis op =
           new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), new ArrayList<Control>(),
                               DN.valueOf("cn=uid=test.user,o=test"), mods);

      LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
      boolean actual = validator.passwordIsAcceptable(
          password, currentPassword, op, userEntry, invalidReason);
      assertEquals(buffer.length() >= 6, actual);
    }

    validator.finalizePasswordValidator();
  }

  private SimilarityBasedPasswordValidator initializePasswordValidator(Entry cfgEntry) throws Exception {
    return InitializationUtils.initializePasswordValidator(
        new SimilarityBasedPasswordValidator(), cfgEntry, SimilarityBasedPasswordValidatorCfgDefn.getInstance());
  }
}
