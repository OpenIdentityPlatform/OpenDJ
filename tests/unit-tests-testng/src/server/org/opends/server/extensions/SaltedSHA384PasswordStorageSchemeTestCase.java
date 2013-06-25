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
 *      Portions Copyright 2010 ForgeRock AS.
 */
package org.opends.server.extensions;


import static org.testng.Assert.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.
            SaltedSHA384PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.SaltedSHA384PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.Entry;



/**
 * A set of test cases for the salted SHA-384 password storage scheme.
 */
public class SaltedSHA384PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /**
   * Creates a new instance of this storage scheme test case.
   */
  public SaltedSHA384PasswordStorageSchemeTestCase()
  {
    super("cn=Salted SHA-384,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  protected PasswordStorageScheme getScheme()
         throws Exception
  {
    SaltedSHA384PasswordStorageScheme scheme =
         new SaltedSHA384PasswordStorageScheme();

    SaltedSHA384PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          SaltedSHA384PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }

  /**
   * Retrieves a set of passwords (plain and SSHA384 encrypted) that may
   * be used to test the compatibility of SSHA384 passwords.
   * The encrypted versions have been provided by external tools or
   * users
   *
   * @return  A set of couple (cleartext, encrypted) passwords that
   *          may be used to test the SSHA384 password storage scheme
   */

  @DataProvider(name = "testSSHA384Passwords")
  public Object[][] getTestSSHA384Passwords()
         throws Exception
  {
    return new Object[][]
    {
      // Note that this test password has been generated with OpenDJ
      // Ideally, they should come from other projects, programs
      new Object[] { "secret", "{SSHA384}+Cw4SXSlJ9q++MCoOan5nWEcLEAMeRo4Y+1gmcZ8JinT9fz/5QG+npm8pQv2J2skOHy+FioGcig=" }
    };
}

  @Test(dataProvider = "testSSHA384Passwords")
  public void testAuthSSHA384Passwords(
          String plaintextPassword,
          String encodedPassword) throws Exception
  {
    // Start/clear-out the memory backend
    TestCaseUtils.initializeTestBackend(true);

    boolean allowPreencodedDefault = setAllowPreencodedPasswords(true);

    try {

      Entry userEntry = TestCaseUtils.makeEntry(
       "dn: uid=testSSHA384.user,o=test",
       "objectClass: top",
       "objectClass: person",
       "objectClass: organizationalPerson",
       "objectClass: inetOrgPerson",
       "uid: testSSHA384.user",
       "givenName: TestSSHA384",
       "sn: User",
       "cn: TestSSHA384 User",
       "userPassword: " + encodedPassword);


      // Add the entry
      TestCaseUtils.addEntry(userEntry);

      assertTrue(TestCaseUtils.canBind("uid=testSSHA384.user,o=test",
                  plaintextPassword),
               "Failed to bind when pre-encoded password = \"" +
               encodedPassword + "\" and " +
               "plaintext password = \"" +
               plaintextPassword + "\"" );
    } finally {
      setAllowPreencodedPasswords(allowPreencodedDefault);
    }
  }

}

