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
 * Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.extensions;

import java.security.SecureRandom;
import java.util.concurrent.Callable;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.server.config.meta.PKCS5S2PasswordStorageSchemeCfgDefn;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.withoutJceService;
import static org.testng.Assert.*;

/**
 * A set of test cases for the PKCS5S2 password storage scheme.
 */
@SuppressWarnings("javadoc")
public class PKCS5S2PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /**
   * Creates a new instance of this storage scheme test case.
   */
  public PKCS5S2PasswordStorageSchemeTestCase()
  {
    super("cn=PKCS5S2,cn=Password Storage Schemes,cn=config");
  }

  /**
   * Retrieves a set of passwords that may be used to test the password storage scheme.
   *
   * @return  A set of passwords that may be used to test the password storage scheme.
   */
  @Override
  @DataProvider(name = "testPasswords")
  public Object[][] getTestPasswords()
  {
    final Object[][] testPasswords = super.getTestPasswords();

    // JDK Bug 6879540. Empty passwords are not accepted when generating PBESpecKey.
    // The bug is present in Java 6 and some version of Java 7.
    final int newLength = testPasswords.length - 2;
    final Object[][] results = new Object[newLength][];
    System.arraycopy(testPasswords, 2, results, 0, newLength);
    return results;
  }


  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   */
  @Override
  protected PasswordStorageScheme<?> getScheme() throws Exception
  {
    return InitializationUtils.initializePasswordStorageScheme(
        new PKCS5S2PasswordStorageScheme(), configEntry, PKCS5S2PasswordStorageSchemeCfgDefn.getInstance());
  }

  /**
   * Retrieves a set of passwords (plain and PKCS5S2 encrypted) that may
   * be used to test the compatibility of PKCS5S2 passwords.
   * The encrypted versions have been provided by external tools or
   * users
   *
   * @return  A set of couple (cleartext, encrypted) passwords that
   *          may be used to test the PKCS5S2 password storage scheme
   */
  @DataProvider(name = "testPKCS5S2Passwords")
  public Object[][] getTestPKCS5S2Passwords() throws Exception
  {
    return new Object[][]
    {
      // Sample from public forum...
      new Object[] { "admin", "{PKCS5S2}siTdcDkChqeSDGVnIMILINUGSzhublIyp1KDvI0CJQ3HuQurEHyN7itWI6rpIzN4" },
      // Sample from Crowd support forums
      new Object[] { "admin", "{PKCS5S2}4PCXluhV1YoY3yGgp77MfHjoFoS7GwNxif4gQLpwIfqLs9n/3seRLlECMu2CWGtm" },
      // Sample from Apache DS implementation test
      new Object[] {"tempo", "{PKCS5S2}ggkzUKrzLIxti+aFlhPbfXFiIZbw9TGm/Pru/eVqMgWupaxbIt70xqWXpqS9Q9XZ" },
      // Sample from passlib  library http://pythonhosted.org/passlib/lib/passlib.hash.atlassian_pbkdf2_sha1.html
      new Object[] { "password", "{PKCS5S2}DQIXJU038u4P7FdsuFTY/+35bm41kfjZa57UrdxHp2Mu3qF2uy+ooD+jF5t1tb8J" },
      // Samples from https://eikonal.wordpress.com/tag/magic-string/
      new Object[] { "password", "{PKCS5S2}1Nq7N2YM4ZyTstZaSynlnGGh2rgAG+b7SB+9xreszUhrE39BnfwNg2RGm6tqvDg2" },
      new Object[] { "password", "{PKCS5S2}fU8ppRTCuJeS8n7PGYOQMhVqZ4hUidTIiWI4K8R8IBOXm/lYywaouSLtvlTeTr3V" },
      new Object[] { "password", "{PKCS5S2}+X+PMcYYAwBAKIWwFsJY639EipU1NXJfc1jKC5VYHZV7zoDI4zTEpKO4xZQoegg1" },
      new Object[] { "password", "{PKCS5S2}bu1dK0WotXYuBaB0bo2RslxMAp4JawLofUFw4S5fZdAtfsm3Ats6kO6j5NaHZCdt" },
      new Object[] { "password", "{PKCS5S2}z/mfc47xvjcm5Ny7dw7BeExB68Oc4XiTJvUS5HRAadKr4/Aomn1WOMMrMWtikUPK" },
      // Sample from Sage platform JIRA - PLFM-2205
      new Object[] { "password", "{PKCS5S2}cnDeuXJkUW+sQwdTw4YlBaV0PMYvZQKc69lHAamznecCeEX9IPqpp7TjhEdJlNkV" },
      // Samples from Emidio Stani, contributor of original PKCS5S2 extension for OpenDJ
      new Object[] { "test2", "{PKCS5S2}A0o7i4Typ0wVnME334K2Od2oyFUNBCwryGBa6g/5s2NDFc+E4ewNiV22KaTDKOqB" },
      new Object[] { "test1", "{PKCS5S2}999tlQor9kNRXuIiHv2MhiL3zlReDlfWS9nOzO1Le/HeawYuhYuL/2SOug67T+Aq" },
      // Sample from bitbucket cwdapache pull request
      new Object[] { "password", "{PKCS5S2}aCE+yLkHgdZ7DQxM37/5nY3NFFYhQfDrkNUoEE6eUItQJoS4Z+jKFj+2OkySTboT" },
      // Sample from Atlassian JIRA test suite
      // https://github.com/atlassian/jira-suite-utilities/blob/master/src/test/xml/test1.xml
      new Object[] { "developer", "{PKCS5S2}IcisOH+L07K8RAgqQJsp7IGXLUL0jRhCOSVrvAq8sprymJvEcNHT/LMaL+6ZOcCh" }
    };
  }

  @Test(dataProvider = "testPKCS5S2Passwords")
  public void testAuthPKCS5S2Passwords(
          String plaintextPassword,
          String encodedPassword) throws Exception
  {
    testAuthPasswords("TestPKCS5S2", plaintextPassword, encodedPassword);
  }

  @Override
  protected String encodeOffline(final byte[] plaintextBytes) throws DirectoryException
  {
    return PKCS5S2PasswordStorageScheme.encodeOffline(plaintextBytes);
  }

  /**
   * A FIPS-restricted JCE (SunPKCS11-NSS-FIPS, BC-FIPS) registers no {@code SHA1PRNG}: the
   * scheme has to take the provider's default random source, as the other PBKDF2 schemes do,
   * instead of failing to initialize and taking the server start down with it.
   */
  @Test
  public void testInitializesAndEncodesWithoutSha1Prng() throws Exception
  {
    withoutSha1Prng(() ->
    {
      final PasswordStorageScheme<?> scheme = getScheme();
      final ByteString plaintext = ByteString.valueOfUtf8("correct horse battery staple");
      assertTrue(scheme.passwordMatches(plaintext, scheme.encodePassword(plaintext)));
      return null;
    });
  }

  /** Same for the offline encoder, which is what encode-password and the initial root password use. */
  @Test
  public void testEncodesOfflineWithoutSha1Prng() throws Exception
  {
    withoutSha1Prng(() ->
    {
      final ByteString plaintext = ByteString.valueOfUtf8("correct horse battery staple");
      final String encoded = PKCS5S2PasswordStorageScheme.encodeOffline(plaintext.toByteArray());
      final String prefix = "{" + getScheme().getStorageSchemeName() + "}";
      assertTrue(encoded.startsWith(prefix), encoded);
      assertTrue(getScheme().passwordMatches(plaintext, ByteString.valueOfUtf8(encoded.substring(prefix.length()))));
      return null;
    });
  }

  /**
   * When the derivation itself is unavailable, the failure has to name the algorithm: a
   * message-less InitializationException leaves the administrator with a server which does
   * not start and no word on why.
   */
  @Test
  public void testInitializationFailureNamesTheMissingAlgorithm() throws Exception
  {
    withoutJceService("SecretKeyFactory", "PBKDF2WithHmacSHA1", () ->
    {
      try
      {
        getScheme();
        fail("initialization succeeded without PBKDF2WithHmacSHA1");
      }
      catch (InitializationException e)
      {
        assertNotNull(e.getMessageObject(), "the failure carries no message");
        assertTrue(e.getMessage().contains("PBKDF2WithHmacSHA1"), e.getMessage());
      }
      return null;
    });
  }

  /**
   * Withdraws every provider registering {@code SHA1PRNG} (the SUN provider on a stock JDK),
   * with BC-FIPS standing in for the digests the derivation still needs from it.
   */
  private static void withoutSha1Prng(final Callable<Void> action) throws Exception
  {
    final BouncyCastleFipsProvider bcFips = new BouncyCastleFipsProvider();
    // Seed the provider's DRBG while the JDK's own random source is still installed.
    SecureRandom.getInstance("DEFAULT", bcFips).nextBytes(new byte[8]);
    withoutJceService("SecureRandom", "SHA1PRNG", action, bcFips);
  }
}
