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
 *      Portions Copyright 2010-2013 ForgeRock AS.
 *      Portions Copyright 2012 Dariusz Janny <dariusz.janny@gmail.com>
 */
package org.opends.server.extensions;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.CryptPasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.CryptPasswordStorageSchemeCfg;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * A set of test cases for the crypt password storage scheme.
 */
public class CryptPasswordStorageSchemeTestCase
       extends ExtensionsTestCase
{
  // The configuration entry for this password storage scheme.
  private ConfigEntry configEntry;

  // The string representation of the DN of the configuration entry for this
  // password storage scheme.
  private static final String configDNString =
          "cn=Crypt,cn=Password Storage Schemes,cn=config";

  // Names of all the crypt algorithms we want to test.
  private static final String[] names = { "unix", "md5", "sha256", "sha512" };

  /**
   * Creates a new instance of this crypt password storage scheme test
   * case with the provided information.
   */
  public CryptPasswordStorageSchemeTestCase()
  {
    super();
    this.configEntry    = null;
  }


  /**
   * Ensures that the Directory Server is started before running any of these
   * tests.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    configEntry = DirectoryServer.getConfigEntry(DN.decode(configDNString));
  }



  /**
   * Retrieves a set of passwords that may be used to test the password storage
   * scheme.
   *
   * @return  A set of passwords that may be used to test the password storage
   *          scheme.
   */
  @DataProvider(name = "testPasswords")
  public Object[][] getTestPasswords()
  {
    return new Object[][]
    {
      new Object[] { ByteString.empty() },
      new Object[] { ByteString.valueOf("") },
      new Object[] { ByteString.valueOf("\u0000") },
      new Object[] { ByteString.valueOf("\t") },
      new Object[] { ByteString.valueOf("\n") },
      new Object[] { ByteString.valueOf("\r\n") },
      new Object[] { ByteString.valueOf(" ") },
      new Object[] { ByteString.valueOf("Test1\tTest2\tTest3") },
      new Object[] { ByteString.valueOf("Test1\nTest2\nTest3") },
      new Object[] { ByteString.valueOf("Test1\r\nTest2\r\nTest3") },
      new Object[] { ByteString.valueOf("a") },
      new Object[] { ByteString.valueOf("ab") },
      new Object[] { ByteString.valueOf("abc") },
      new Object[] { ByteString.valueOf("abcd") },
      new Object[] { ByteString.valueOf("abcde") },
      new Object[] { ByteString.valueOf("abcdef") },
      new Object[] { ByteString.valueOf("abcdefg") },
      new Object[] { ByteString.valueOf("abcdefgh") },
      new Object[] { ByteString.valueOf("The Quick Brown Fox Jumps Over " +
                                         "The Lazy Dog") },
      new Object[] { ByteString.valueOf("\u00BFD\u00F3nde est\u00E1 el " +
                                         "ba\u00F1o?") }
    };
  }


  /**
   * Creates an instance of each password storage scheme, uses it to encode the
   * provided password, and ensures that the encoded value is correct.
   *
   * @param  plaintext  The plain-text version of the password to encode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testUnixStorageSchemes(ByteString plaintext)
         throws Exception
  {
    for (String name : names)
    {
      CryptPasswordStorageScheme scheme = getScheme(name);
      assertNotNull(scheme);
      assertNotNull(scheme.getStorageSchemeName());

      ByteString encodedPassword = scheme.encodePassword(plaintext);
      assertNotNull(encodedPassword);
      assertTrue(scheme.passwordMatches(plaintext, encodedPassword));
      assertFalse(scheme.passwordMatches(plaintext,
                                         ByteString.valueOf("garbage")));

      ByteString schemeEncodedPassword =
           scheme.encodePasswordWithScheme(plaintext);
      String[] pwComponents = UserPasswordSyntax.decodeUserPassword(
                                   schemeEncodedPassword.toString());
      assertNotNull(pwComponents);


      if (scheme.supportsAuthPasswordSyntax())
      {
        assertNotNull(scheme.getAuthPasswordSchemeName());
        ByteString encodedAuthPassword = scheme.encodeAuthPassword(plaintext);
        StringBuilder[] authPWComponents =
             AuthPasswordSyntax.decodeAuthPassword(
                  encodedAuthPassword.toString());
        assertTrue(scheme.authPasswordMatches(plaintext,
                                              authPWComponents[1].toString(),
                                              authPWComponents[2].toString()));
        assertFalse(scheme.authPasswordMatches(plaintext, ",", "foo"));
        assertFalse(scheme.authPasswordMatches(plaintext, "foo", ","));
      }
      else
      {
        try
        {
          scheme.encodeAuthPassword(plaintext);
          throw new Exception("Expected encodedAuthPassword to fail for scheme " +
                              scheme.getStorageSchemeName() +
                              " because it doesn't support auth passwords.");
        }
        catch (DirectoryException de)
        {
          // This was expected.
        }

        assertFalse(scheme.authPasswordMatches(plaintext, "foo", "bar"));
      }


      if (scheme.isReversible())
      {
        assertEquals(scheme.getPlaintextValue(encodedPassword), plaintext);
      }
      else
      {
        try
        {
          scheme.getPlaintextValue(encodedPassword);
          throw new Exception("Expected getPlaintextValue to fail for scheme " +
                              scheme.getStorageSchemeName() +
                              " because it is not reversible.");
        }
        catch (DirectoryException de)
        {
          // This was expected.
        }
      }

      scheme.isStorageSchemeSecure();
      
    }
  }



  @DataProvider
  public Object[][] passwordsForBinding()
  {
    return new Object[][]
    {
      // In the case of a clear-text password, these values will be shoved
      // un-excaped into an LDIF file, so make sure they don't include \n
      // or other characters that will cause LDIF parsing errors.
      // We really don't need many test cases here, since that functionality
      // is tested above.
      new Object[] { ByteString.valueOf("a") },
      new Object[] { ByteString.valueOf("abcdefgh") },
      new Object[] { ByteString.valueOf("abcdefghi") },
    };
  }



  /**
   * An end-to-end test that verifies that we can set a pre-encoded password
   * in a user entry, and then bind as that user using the cleartext password.
   */
  @Test(dataProvider = "passwordsForBinding")
  public void testSettingUnixEncodedPassword(ByteString plainPassword)
          throws Exception
  {
    for (String name: names)
    {
      // Start/clear-out the memory backend
      TestCaseUtils.initializeTestBackend(true);

      setAllowPreencodedPasswords(true);

      CryptPasswordStorageScheme scheme = getScheme(name);
      ByteString schemeEncodedPassword =
          scheme.encodePasswordWithScheme(plainPassword);

      //
      // This code creates a user with the encoded password,
      // and then verifies that they can bind with the raw password.
      //

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
          "ds-privilege-name: bypass-acl",
          "userPassword: " + schemeEncodedPassword.toString());

      // Add the entry
      TestCaseUtils.addEntry(userEntry);

      assertTrue(TestCaseUtils.canBind("uid=test.user,o=test",
          plainPassword.toString()),
          "Failed to bind when pre-encoded password = \"" +
              schemeEncodedPassword.toString() + "\" and " +
              "plaintext password = \"" +
              plainPassword.toString() + "\"");
    }
  }


  /**
   * Sets whether or not to allow pre-encoded password values for the
   * current password storage scheme and returns the previous value so that
   * it can be restored.
   *
   * @param allowPreencoded whether or not to allow pre-encoded passwords
   * @return the previous value for the allow preencoded passwords
   */
  private boolean setAllowPreencodedPasswords(boolean allowPreencoded)
          throws Exception
  {
    // This code was borrowed from
    // PasswordPolicyTestCase.testAllowPreEncodedPasswordsAuth
    boolean previousValue = false;
    try {
      DN dn = DN.decode("cn=Default Password Policy,cn=Password Policies,cn=config");
      PasswordPolicy p = (PasswordPolicy) DirectoryServer.getAuthenticationPolicy(dn);
      previousValue = p.isAllowPreEncodedPasswords();

      String attr  = "ds-cfg-allow-pre-encoded-passwords";

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
          Attributes.create(attr, String.valueOf(allowPreencoded))));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation modifyOperation = conn.processModify(dn, mods);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

      p = (PasswordPolicy) DirectoryServer.getAuthenticationPolicy(dn);
      assertEquals(p.isAllowPreEncodedPasswords(), allowPreencoded);
    } catch (Exception e) {
      System.err.println("Failed to set ds-cfg-allow-pre-encoded-passwords " +
                         " to " + allowPreencoded);
      e.printStackTrace();
      throw e;
    }

    return previousValue;
  }

  /**
   * Retrieves a set of passwords (plain and variously hashed) that may
   * be used to test the different Unix "crypt" algorithms used by the Crypt
   * Password Storage scheme.
   *
   * The encrypted versions have been generated by the openssl passwd -1
   * command on MacOS X.
   *
   * @return  A set of couple (cleartext, hashed) passwords that
   *          may be used to test the different algorithms used by the Crypt
   *          password storage scheme.
   */

  @DataProvider(name = "testCryptPasswords")
  public Object[][] getTestCryptPasswords()
         throws Exception
  {
    return new Object[][]
    {
      new Object[] { "secret12", "{CRYPT}$1$X40CcMaA$dd3ndknBLcpkED4/RciyD1" },
      new Object[] { "#1 Strong Password!", "{CRYPT}$1$7jHbWKyy$gAmpOSdaYVap55MwsQnK5/" },
      new Object[] { "foo", "{CRYPT}$1$ac/Z7Q3s$5kTVLqMSq9KMqUVyEBfiw0" },
      new Object[] { "secret12", "{CRYPT}$5$miWe9yahchas7aiy$b/6oTh5QF3bqbdIDWmjtdOxD8df75426zTHwF.MJuyB" },
      new Object[] { "foo", "{CRYPT}$5$aZoothaeDai0nooG$5LDMuhK6gWtH6/mrrqZbRc5aIRROfrKri4Tvl/D6Z.0"},
      new Object[] { "#1 Strong Password!", "{CRYPT}$5$aZoothaeDai0nooG$6o0Sbx/RtTA4K/A8uflMsSCid3i7TYktcwWxIp5NFy2"},
      new Object[] { "secret12", "{CRYPT}$6$miWe9yahchas7aiy$RQASn5qZMCu2FDsR69RHk1RoLVi3skFUhS0qGNCo.MymgkYoWAedMji09UzxMFzOj8fW2GnzsXT4RVn9gcNmf0" },
      new Object[] { "#1 Strong Password!", "{CRYPT}$6$p0NJY6r4$VV2JfNtRaTmy8hBtVpdgeIUYQIAUyfdLyhiH6VxzsDIw.28oCsVeMQ5ARiL/PoOambM9dAU3vk4ll8uEB/nnx0"},
      new Object[] { "foo", "{CRYPT}$6$aZoothaeDai0nooG$1K9ePro8ujsqRy/Ag77OVuev8Y8hyN1Jp10S2t9S.1RMtkKn/SbxQbl2MezoL0UJFYjrEzL0zVdO8PcfT3yXS."}
    };
  }

  @Test(dataProvider = "testCryptPasswords")
  public void testAuthCryptPasswords(
          String plaintextPassword,
          String encodedPassword) throws Exception
  {
      // Start/clear-out the memory backend
    TestCaseUtils.initializeTestBackend(true);

    boolean allowPreencodedDefault = setAllowPreencodedPasswords(true);

    try {

      Entry userEntry = TestCaseUtils.makeEntry(
       "dn: uid=testCrypt.user,o=test",
       "objectClass: top",
       "objectClass: person",
       "objectClass: organizationalPerson",
       "objectClass: inetOrgPerson",
       "uid: testCrypt.user",
       "givenName: TestCrypt",
       "sn: User",
       "cn: TestCrypt User",
       "userPassword: " + encodedPassword);


      // Add the entry
      TestCaseUtils.addEntry(userEntry);

      assertTrue(TestCaseUtils.canBind("uid=testCrypt.user,o=test",
                  plaintextPassword),
               "Failed to bind when pre-encoded password = \"" +
               encodedPassword + "\" and " +
               "plaintext password = \"" +
               plaintextPassword + "\"" );
    } finally {
      setAllowPreencodedPasswords(allowPreencodedDefault);
    }
  }

  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private CryptPasswordStorageScheme getScheme(String algo)
         throws Exception
  {
    CryptPasswordStorageScheme scheme =
         new CryptPasswordStorageScheme();
    Entry e = TestCaseUtils.makeEntry(
      "dn: cn=CRYPT,cn=Password Storage Schemes,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-password-storage-scheme",
      "objectClass: ds-cfg-crypt-password-storage-scheme",
      "cn: CRYPT",
      "ds-cfg-java-class: org.opends.server.extensions.CryptPasswordStorageScheme",
      "ds-cfg-enabled: true",
      "ds-cfg-crypt-password-storage-encryption-algrithm: " + algo
);
    CryptPasswordStorageSchemeCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              CryptPasswordStorageSchemeCfgDefn.getInstance(),
              e);

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }
}

