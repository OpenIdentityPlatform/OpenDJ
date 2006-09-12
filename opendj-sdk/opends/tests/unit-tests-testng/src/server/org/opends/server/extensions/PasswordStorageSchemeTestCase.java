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



import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;

import static org.testng.Assert.*;



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
      assertEquals(plaintext, scheme.getPlaintextValue(encodedPassword));
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



  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @param  configEntry  The configuration entry for the password storage
   *                      scheme, or <CODE>null</CODE> if none is available.
   *
   * @return  An initialized instance of this password storage scheme.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public abstract PasswordStorageScheme getScheme()
         throws Exception;
}

