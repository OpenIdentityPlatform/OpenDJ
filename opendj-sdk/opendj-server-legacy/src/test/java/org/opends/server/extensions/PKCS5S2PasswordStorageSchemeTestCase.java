/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.PKCS5S2PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.PKCS5S2PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

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
    PKCS5S2PasswordStorageScheme scheme =
         new PKCS5S2PasswordStorageScheme();

    PKCS5S2PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
              PKCS5S2PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
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

}
