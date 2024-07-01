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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.opendj.server.config.meta.BcryptPasswordStorageSchemeCfgDefn;
import org.opends.server.api.PasswordStorageScheme;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the Bcrypt password storage scheme. */
@SuppressWarnings("javadoc")
public class BcryptPasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /** Creates a new instance of this storage scheme test case. */
  public BcryptPasswordStorageSchemeTestCase()
  {
    super("cn=Bcrypt,cn=Password Storage Schemes,cn=config");
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
        new BcryptPasswordStorageScheme(), configEntry, BcryptPasswordStorageSchemeCfgDefn.getInstance());
  }

  /**
   * Retrieves a set of passwords (plain and bcrypt encrypted) that may
   * be used to test the compatibility of bcrypt passwords.
   * The encrypted versions have been provided by external tools or users
   *
   * @return  A set of couple (cleartext, encrypted) passwords that
   *          may be used to test the bcrypt password storage scheme
   */
  @DataProvider(name = "testBcryptPasswords")
  public Object[][] getTestBcryptPasswords() throws Exception
  {
    return new Object[][] {
      { "secret", "{BCRYPT}$2a$08$sxnezK9Dp9cQvU56LHRwIeI0RvfNn//fFzGnOgQ2l7TOZcZ1wbOVO" },
      { "5[g&f:\"U;#99]!_T", "{BCRYPT}$2a$08$Ttmg4fCbAcq2636pT83d1eM8weXLHbn8OFyVRanP2Tjej5hiZBnyu" },
      { "password", "{BCRYPT}$2a$05$bvIG6Nmid91Mu9RcmmWZfO5HJIMCT8riNW0hEp8f6/FuA2/mHZFpe"},
      { "Secret12!", "{BCRYPT}$2a$10$UOYhwLcHwGYdwWCYq1Xd2.66aPGYq8Q7HDzm8jzTRkdJyAjt/gfhO" },
      { "correctbatteryhorsestapler", "{BCRYPT}$2a$12$mACnM5lzNigHMaf7O1py1O3vlf6.BA8k8x3IoJ.Tq3IB/2e7g61Km"},
      { "TestingWith12%", "{BCRYPT}$2a$12$2nTgfUEOupc7Eb5PyGCnIOzoDG/VMEhIOTKTjIjY3UPjtTI..NoLO" }
    };
  }

  @Test(dataProvider = "testBcryptPasswords")
  public void testAuthBcryptPasswords(
      String plaintextPassword,
      String encodedPassword) throws Exception
  {
    testAuthPasswords("TestBCrypt", plaintextPassword, encodedPassword);
  }
}
