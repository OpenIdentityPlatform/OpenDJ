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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.opends.messages.MessageBuilder;
import org.opends.server.config.ConfigException;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import static org.testng.Assert.*;
import org.opends.server.admin.std.meta.SimilarityBasedPasswordValidatorCfgDefn;
import org.opends.server.admin.std.server.SimilarityBasedPasswordValidatorCfg;
import org.opends.server.admin.server.AdminTestCaseUtils;



/**
 * A set of test cases for the Similarity-Based Password Validator.
 */
public class SimilarityBasedPasswordValidatorTestCase
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
    SimilarityBasedPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              SimilarityBasedPasswordValidatorCfgDefn.getInstance(),
              e);

    SimilarityBasedPasswordValidator validator = new SimilarityBasedPasswordValidator();
    validator.initializePasswordValidator(configuration);
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
    SimilarityBasedPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              SimilarityBasedPasswordValidatorCfgDefn.getInstance(),
              e);

    SimilarityBasedPasswordValidator validator = new SimilarityBasedPasswordValidator();
    validator.initializePasswordValidator(configuration);
  }



  /**
   * Tests the <CODE>passwordIsAcceptable</CODE> method with no constraints on
   * password difference.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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

    SimilarityBasedPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              SimilarityBasedPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    SimilarityBasedPasswordValidator validator =
         new SimilarityBasedPasswordValidator();
    validator.initializePasswordValidator(configuration);

    StringBuilder buffer = new StringBuilder();
    for (int i=0; i < 20; i++)
    {
      buffer.append('x');
      ASN1OctetString password = new ASN1OctetString(buffer.toString());

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
          Attributes.create("userpassword",
                                              buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperationBasis op =
           new ModifyOperationBasis(conn, conn.nextOperationID(),
                               conn.nextMessageID(), new ArrayList<Control>(),
                               DN.decode("cn=uid=test.user,o=test"), mods);

      MessageBuilder invalidReason = new MessageBuilder();
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
  @Test()
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

    SimilarityBasedPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              SimilarityBasedPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    SimilarityBasedPasswordValidator validator =
         new SimilarityBasedPasswordValidator();
    validator.initializePasswordValidator(configuration);

    StringBuilder buffer = new StringBuilder();
    HashSet<ByteString> currentPassword = new HashSet<ByteString>(3);
    currentPassword.add(ByteStringFactory.create("xxx"));
    for (int i=0; i < 7; i++)
    {
      buffer.append('x');
      ASN1OctetString password = new ASN1OctetString(buffer.toString());

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
          Attributes.create("userpassword",
                                              buffer.toString())));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperationBasis op =
           new ModifyOperationBasis(conn, conn.nextOperationID(),
                               conn.nextMessageID(), new ArrayList<Control>(),
                               DN.decode("cn=uid=test.user,o=test"), mods);

      MessageBuilder invalidReason = new MessageBuilder();
      assertEquals((buffer.length() >= 6),
                   validator.passwordIsAcceptable(password,
                                                  currentPassword,
                                                  op, userEntry,
                                                  invalidReason));
    }

    validator.finalizePasswordValidator();
  }
}

