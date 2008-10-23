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



import static org.testng.Assert.*;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.protocols.asn1.ASN1OctetString;
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
 * A set of generic test cases for password storage schemes.
 */
public abstract class PasswordStorageSchemeTestCase
       extends ExtensionsTestCase
{
  // The configuration entry for this password storage scheme.
  protected ConfigEntry configEntry;

  // The string representation of the DN of the configuration entry for this
  // password storage scheme.
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
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    
    if (configDNString != null)
    {
      configEntry = DirectoryServer.getConfigEntry(DN.decode(configDNString));
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
    return new Object[][]
    {
      new Object[] { new ASN1OctetString() },
      new Object[] { new ASN1OctetString("") },
      new Object[] { new ASN1OctetString("\u0000") },
      new Object[] { new ASN1OctetString("\t") },
      new Object[] { new ASN1OctetString("\n") },
      new Object[] { new ASN1OctetString("\r\n") },
      new Object[] { new ASN1OctetString(" ") },
      new Object[] { new ASN1OctetString("Test1\tTest2\tTest3") },
      new Object[] { new ASN1OctetString("Test1\nTest2\nTest3") },
      new Object[] { new ASN1OctetString("Test1\r\nTest2\r\nTest3") },
      new Object[] { new ASN1OctetString("a") },
      new Object[] { new ASN1OctetString("ab") },
      new Object[] { new ASN1OctetString("abc") },
      new Object[] { new ASN1OctetString("abcd") },
      new Object[] { new ASN1OctetString("abcde") },
      new Object[] { new ASN1OctetString("abcdef") },
      new Object[] { new ASN1OctetString("abcdefg") },
      new Object[] { new ASN1OctetString("abcdefgh") },
      new Object[] { new ASN1OctetString("The Quick Brown Fox Jumps Over " +
                                         "The Lazy Dog") },
      new Object[] { new ASN1OctetString("\u00BFD\u00F3nde est\u00E1 el " +
                                         "ba\u00F1o?") }
    };
  }



  /**
   * Creates an instance of the password storage scheme, uses it to encode the
   * provided password, and ensures that the encoded value is correct.
   *
   * @param  plaintext  The plain-text version of the password to encode.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testPasswords")
  public void testStorageScheme(ByteString plaintext)
         throws Exception
  {
    PasswordStorageScheme scheme = getScheme();
    assertNotNull(scheme);
    assertNotNull(scheme.getStorageSchemeName());

    ByteString encodedPassword = scheme.encodePassword(plaintext);
    assertNotNull(encodedPassword);
    assertTrue(scheme.passwordMatches(plaintext, encodedPassword));
    assertFalse(scheme.passwordMatches(plaintext,
                                       new ASN1OctetString("garbage")));

    ByteString schemeEncodedPassword =
         scheme.encodePasswordWithScheme(plaintext);
    String[] pwComponents = UserPasswordSyntax.decodeUserPassword(
                                 schemeEncodedPassword.stringValue());
    assertNotNull(pwComponents);


    if (scheme.supportsAuthPasswordSyntax())
    {
      assertNotNull(scheme.getAuthPasswordSchemeName());
      ByteString encodedAuthPassword = scheme.encodeAuthPassword(plaintext);
      StringBuilder[] authPWComponents =
           AuthPasswordSyntax.decodeAuthPassword(
                encodedAuthPassword.stringValue());
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
      new Object[] { new ASN1OctetString("a") },
      new Object[] { new ASN1OctetString("abcdefgh") },
      new Object[] { new ASN1OctetString("abcdefghi") },
    };
  }

  /**
   * An end-to-end test that verifies that we can set a pre-encoded password
   * in a user entry, and then bind as that user using the cleartext password.
   */
  @Test(dataProvider = "passwordsForBinding")
  public void testSettingEncodedPassword(ASN1OctetString plainPassword) throws Exception
  {
    // Start/clear-out the memory backend
    TestCaseUtils.initializeTestBackend(true);

    boolean allowPreencodedDefault = setAllowPreencodedPasswords(true);

    try {
      PasswordStorageScheme scheme = getScheme();
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
           "userPassword: " + schemeEncodedPassword.stringValue());

      // Add the entry
      TestCaseUtils.addEntry(userEntry);

      assertTrue(TestCaseUtils.canBind("uid=test.user,o=test",
                 plainPassword.stringValue()),
                 "Failed to bind when pre-encoded password = \"" +
                         schemeEncodedPassword.stringValue() + "\" and " +
                         "plaintext password = \"" +
                         plainPassword.stringValue() + "\"");
    } finally {
      setAllowPreencodedPasswords(allowPreencodedDefault);
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
      PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
      previousValue = p.allowPreEncodedPasswords();

      String attr  = "ds-cfg-allow-pre-encoded-passwords";

      ArrayList<Modification> mods = new ArrayList<Modification>();
      mods.add(new Modification(ModificationType.REPLACE,
          Attributes.create(attr, String.valueOf(allowPreencoded))));

      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyOperation modifyOperation = conn.processModify(dn, mods);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

      p = DirectoryServer.getPasswordPolicy(dn);
      assertEquals(p.allowPreEncodedPasswords(), allowPreencoded);
    } catch (Exception e) {
      System.err.println("Failed to set ds-cfg-allow-pre-encoded-passwords " +
                         " to " + allowPreencoded);
      e.printStackTrace();
      throw e;
    }

    return previousValue;
  }

  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  protected abstract PasswordStorageScheme getScheme()
         throws Exception;
}

