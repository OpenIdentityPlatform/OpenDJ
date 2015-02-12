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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2014 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.SaltedSHA512PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.SaltedSHA512PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A set of test cases for the salted SHA-512 password storage scheme.
 */
@SuppressWarnings("javadoc")
public class SaltedSHA512PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /**
   * Creates a new instance of this storage scheme test case.
   */
  public SaltedSHA512PasswordStorageSchemeTestCase()
  {
    super("cn=Salted SHA-512,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  protected PasswordStorageScheme<?> getScheme() throws Exception
  {
    SaltedSHA512PasswordStorageScheme scheme =
         new SaltedSHA512PasswordStorageScheme();

    SaltedSHA512PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          SaltedSHA512PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }

  /**
   * Retrieves a set of passwords (plain and SSHA512 encrypted) that may
   * be used to test the compatibility of SSHA512 passwords.
   * The encrypted versions have been provided by external tools or
   * users
   *
   * @return  A set of couple (cleartext, encrypted) passwords that
   *          may be used to test the SSHA512 password storage scheme
   */

  @DataProvider(name = "testSSHA512Passwords")
  public Object[][] getTestSSHA512Passwords()
         throws Exception
  {
    return new Object[][]
    {
      new Object[] { "secret", "{SSHA512}8gRXO3lD2fGN3JIhbNJOsh31IRFKnWbDNl+cPH3HoJCkUpxZPG617TnN6Nvl2mVMSBLlzPu2eMpOhCDKoolNG6QCsYf2hppQTAVaqfx25PUJ1ngbuBiNDCpK6Xj5PYZiFwa+cpkY/Pzs77bLn3VMxmHhwa+vowfGhy5RRW+6npQ=" }
    };
  }

  @Test(dataProvider = "testSSHA512Passwords")
  public void testAuthSSHA512Passwords(
          String plaintextPassword,
          String encodedPassword) throws Exception
  {
    testAuthPasswords("TestSSHA512", plaintextPassword, encodedPassword);
  }

  /** {@inheritDoc} */
  @Override
  protected String encodeOffline(byte[] plaintextBytes) throws DirectoryException
  {
    return SaltedSHA512PasswordStorageScheme.encodeOffline(plaintextBytes);
  }
}
