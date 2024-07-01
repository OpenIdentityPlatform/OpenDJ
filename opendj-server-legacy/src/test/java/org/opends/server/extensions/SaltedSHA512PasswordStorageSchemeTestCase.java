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

import org.forgerock.opendj.server.config.meta.SaltedSHA512PasswordStorageSchemeCfgDefn;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the salted SHA-512 password storage scheme. */
@SuppressWarnings("javadoc")
public class SaltedSHA512PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /** Creates a new instance of this storage scheme test case. */
  public SaltedSHA512PasswordStorageSchemeTestCase()
  {
    super("cn=Salted SHA-512,cn=Password Storage Schemes,cn=config");
  }

  @Override
  protected PasswordStorageScheme<?> getScheme() throws Exception
  {
    return InitializationUtils.initializePasswordStorageScheme(
        new SaltedSHA512PasswordStorageScheme(), configEntry, SaltedSHA512PasswordStorageSchemeCfgDefn.getInstance());
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
      { "secret", "{SSHA512}8gRXO3lD2fGN3JIhbNJOsh31IRFKnWbDNl+cPH3HoJCkUpxZPG617TnN6Nvl2mVMSBLlzPu2eMpOhCD"
                  + "KoolNG6QCsYf2hppQTAVaqfx25PUJ1ngbuBiNDCpK6Xj5PYZiFwa+cpkY/Pzs77bLn3VMxmHhwa+vowfGhy5RRW+6npQ=" }
    };
  }

  @Test(dataProvider = "testSSHA512Passwords")
  public void testAuthSSHA512Passwords(
          String plaintextPassword,
          String encodedPassword) throws Exception
  {
    testAuthPasswords("TestSSHA512", plaintextPassword, encodedPassword);
  }

  @Override
  protected String encodeOffline(byte[] plaintextBytes) throws DirectoryException
  {
    return SaltedSHA512PasswordStorageScheme.encodeOffline(plaintextBytes);
  }
}
