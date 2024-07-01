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
 * Portions Copyright 2010-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.Entry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

/** A set of generic test cases for password storage schemes. */
@SuppressWarnings("javadoc")
public abstract class PasswordStorageSchemeTestCase
       extends ExtensionsTestCase
{
  /** The configuration entry for this password storage scheme. */
  protected Entry configEntry;

  /**
   * The string representation of the DN of the configuration entry for this
   * password storage scheme.
   */
  private String configDNString;



  /**
   * Creates a new instance of this password storage scheme test case with the
   * provided information.
   *
   * @param  configDNString  The string representation of the DN of the
   *                         configuration entry, or <CODE>null</CODE> if there
   *                         is none.
   */
  protected PasswordStorageSchemeTestCase(String configDNString)
  {
    super();

    this.configDNString = configDNString;
    this.configEntry    = null;
  }



  /**
   * Ensures that the Directory Server is started before running any of these
   * tests.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();

    if (configDNString != null)
    {
      configEntry = DirectoryServer.getEntry(DN.valueOf(configDNString));
    }
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
    return getTestPasswordsStatic();
  }

  static Object[][] getTestPasswordsStatic()
  {
    return new Object[][]
    {
      new Object[] { ByteString.empty() },
      new Object[] { ByteString.valueOfUtf8("") },
      new Object[] { ByteString.valueOfUtf8("\u0000") },
      new Object[] { ByteString.valueOfUtf8("\t") },
      new Object[] { ByteString.valueOfUtf8("\n") },
      new Object[] { ByteString.valueOfUtf8("\r\n") },
      new Object[] { ByteString.valueOfUtf8(" ") },
      new Object[] { ByteString.valueOfUtf8("Test1\tTest2\tTest3") },
      new Object[] { ByteString.valueOfUtf8("Test1\nTest2\nTest3") },
      new Object[] { ByteString.valueOfUtf8("Test1\r\nTest2\r\nTest3") },
      new Object[] { ByteString.valueOfUtf8("a") },
      new Object[] { ByteString.valueOfUtf8("ab") },
      new Object[] { ByteString.valueOfUtf8("abc") },
      new Object[] { ByteString.valueOfUtf8("abcd") },
      new Object[] { ByteString.valueOfUtf8("abcde") },
      new Object[] { ByteString.valueOfUtf8("abcdef") },
      new Object[] { ByteString.valueOfUtf8("abcdefg") },
      new Object[] { ByteString.valueOfUtf8("abcdefgh") },
      new Object[] { ByteString.valueOfUtf8("The Quick Brown Fox Jumps Over " +
                                         "The Lazy Dog") },
      new Object[] { ByteString.valueOfUtf8("\u00BFD\u00F3nde est\u00E1 el " +
                                         "ba\u00F1o?") }
    };
  }



  /**
   * Creates an instance of the password storage scheme, uses it to encode the
   * provided password, and ensures that the encoded value is correct.
   *
   * @param  plaintext  The plain-text version of the password to encode.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testStorageScheme(ByteString plaintext)
         throws Exception
  {
    testStorageScheme(plaintext, getScheme());
  }

  static void testStorageScheme(ByteString plaintext,
      PasswordStorageScheme<?> scheme) throws Exception
  {
    assertNotNull(scheme);
    assertNotNull(scheme.getStorageSchemeName());

    ByteString encodedPassword = scheme.encodePassword(plaintext);
    assertNotNull(encodedPassword);
    assertTrue(scheme.passwordMatches(plaintext, encodedPassword));
    assertFalse(scheme.passwordMatches(plaintext,
                                       ByteString.valueOfUtf8("garbage")));

    ByteString schemeEncodedPassword =
         scheme.encodePasswordWithScheme(plaintext);
    String[] pwComponents = UserPasswordSyntax.decodeUserPassword(
                                 schemeEncodedPassword.toString());
    assertNotNull(pwComponents);


    if (scheme.supportsAuthPasswordSyntax())
    {
      assertNotNull(scheme.getAuthPasswordSchemeName());
      ByteString encodedAuthPassword = scheme.encodeAuthPassword(plaintext);
      String[] authPWComponents = AuthPasswordSyntax.decodeAuthPassword(encodedAuthPassword.toString());
      assertTrue(scheme.authPasswordMatches(plaintext, authPWComponents[1], authPWComponents[2]));
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


  @DataProvider
  public static Object[][] passwordsForBinding()
  {
    return new Object[][]
    {
      // In the case of a clear-text password, these values will be shoved
      // un-excaped into an LDIF file, so make sure they don't include \n
      // or other characters that will cause LDIF parsing errors.
      // We really don't need many test cases here, since that functionality
      // is tested above.
      new Object[] { ByteString.valueOfUtf8("a") },
      new Object[] { ByteString.valueOfUtf8("abcdefgh") },
      new Object[] { ByteString.valueOfUtf8("abcdefghi") },
    };
  }

  /**
   * An end-to-end test that verifies that we can set a pre-encoded password
   * in a user entry, and then bind as that user using the cleartext password.
   */
  @Test(dataProvider = "passwordsForBinding")
  public void testSettingEncodedPassword(ByteString plainPassword) throws Exception
  {
    testSettingEncodedPassword(plainPassword, getScheme());
  }

  static void testSettingEncodedPassword(ByteString plainPassword,
      PasswordStorageScheme<?> scheme) throws Exception, DirectoryException
  {
    // Start/clear-out the memory backend
    TestCaseUtils.initializeTestBackend(true);

    boolean allowPreencodedDefault = setAllowPreencodedPasswords(true);

    try {
      ByteString schemeEncodedPassword =
           scheme.encodePasswordWithScheme(plainPassword);

      // This code creates a user with the encoded password,
      // and then verifies that they can bind with the raw password.
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
           "userPassword: " + schemeEncodedPassword);

      TestCaseUtils.addEntry(userEntry);

      assertTrue(TestCaseUtils.canBind("uid=test.user,o=test",
                 plainPassword.toString()),
                 "Failed to bind when pre-encoded password = \"" +
                         schemeEncodedPassword + "\" and " +
                         "plaintext password = \"" +
                         plainPassword + "\"");
    } finally {
      setAllowPreencodedPasswords(allowPreencodedDefault);
    }
  }


  /**
   * Sets whether to allow pre-encoded password values for the
   * current password storage scheme and returns the previous value so that
   * it can be restored.
   *
   * @param allowPreencoded whether to allow pre-encoded passwords
   * @return the previous value for the allow preencoded passwords
   */
  protected static boolean setAllowPreencodedPasswords(boolean allowPreencoded)
          throws Exception
  {
    // This code was borrowed from
    // PasswordPolicyTestCase.testAllowPreEncodedPasswordsAuth
    try {
      DN dn = DN.valueOf("cn=Default Password Policy,cn=Password Policies,cn=config");
      PasswordPolicy p = (PasswordPolicy) DirectoryServer.getAuthenticationPolicy(dn);
      final boolean previousValue = p.isAllowPreEncodedPasswords();

      ModifyRequest modifyRequest = newModifyRequest("cn=Default Password Policy,cn=Password Policies,cn=config")
          .addModification(REPLACE, "ds-cfg-allow-pre-encoded-passwords", allowPreencoded);
      ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

      p = (PasswordPolicy) DirectoryServer.getAuthenticationPolicy(dn);
      assertEquals(p.isAllowPreEncodedPasswords(), allowPreencoded);
      return previousValue;
    } catch (Exception e) {
      System.err.println("Failed to set ds-cfg-allow-pre-encoded-passwords " +
                         " to " + allowPreencoded);
      e.printStackTrace();
      throw e;
    }
  }

  protected static void testAuthPasswords(final String upperName,
      String plaintextPassword, String encodedPassword) throws Exception
  {
    // Start/clear-out the memory backend
    TestCaseUtils.initializeTestBackend(true);

    boolean allowPreencodedDefault = setAllowPreencodedPasswords(true);

    try
    {
      final String lowerName =
          Character.toLowerCase(upperName.charAt(0)) + upperName.substring(1);

      Entry userEntry = TestCaseUtils.makeEntry(
          "dn: uid=" + lowerName + ".user,o=test",
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: " + lowerName + ".user",
          "givenName: " + upperName,
          "sn: User",
          "cn: " + upperName + " User",
          "userPassword: " + encodedPassword);

      TestCaseUtils.addEntry(userEntry);

      assertTrue(TestCaseUtils.canBind(
          "uid=" + lowerName + ".user,o=test", plaintextPassword),
          "Failed to bind when pre-encoded password = \"" + encodedPassword
          + "\" and " + "plaintext password = \"" + plaintextPassword + "\"");
    }
    finally
    {
      setAllowPreencodedPasswords(allowPreencodedDefault);
    }
  }

  /**
   * Tests the <CODE>encodeOffline</CODE> method.
   *
   * @param plaintext
   *          The plaintext password to use for the test.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testEncodeOffline(ByteString plaintext) throws Exception
  {
    PasswordStorageScheme<?> scheme = getScheme();
    String passwordString = encodeOffline(plaintext.toByteArray());
    if (passwordString != null)
    {
      String[] pwComps = UserPasswordSyntax.decodeUserPassword(passwordString);
      ByteString encodedPassword = ByteString.valueOfUtf8(pwComps[1]);

      assertTrue(scheme.passwordMatches(plaintext, encodedPassword));
    }
  }

  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return An initialized instance of this password storage scheme.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  protected abstract PasswordStorageScheme<?> getScheme() throws Exception;

  /**
   * Encodes the provided plaintext password while offline.
   *
   * @param plaintextBytes
   *          The plaintext password in bytes to use for the test.
   * @throws DirectoryException
   *           If an unexpected problem occurs.
   */
  protected String encodeOffline(byte[] plaintextBytes) throws DirectoryException
  {
    return null;
  }
}

