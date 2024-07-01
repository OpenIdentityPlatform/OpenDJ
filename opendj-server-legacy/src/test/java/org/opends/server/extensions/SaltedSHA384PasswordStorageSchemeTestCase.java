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

import org.forgerock.opendj.server.config.meta.SaltedSHA384PasswordStorageSchemeCfgDefn;
import org.opends.server.api.PasswordStorageScheme;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the salted SHA-384 password storage scheme. */
@SuppressWarnings("javadoc")
public class SaltedSHA384PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /** Creates a new instance of this storage scheme test case. */
  public SaltedSHA384PasswordStorageSchemeTestCase()
  {
    super("cn=Salted SHA-384,cn=Password Storage Schemes,cn=config");
  }

  @Override
  protected PasswordStorageScheme<?> getScheme() throws Exception
  {
    return InitializationUtils.initializePasswordStorageScheme(
        new SaltedSHA384PasswordStorageScheme(), configEntry, SaltedSHA384PasswordStorageSchemeCfgDefn.getInstance());
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
  public Object[][] getTestSSHA384Passwords() throws Exception
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
    testAuthPasswords("TestSSHA384", plaintextPassword, encodedPassword);
  }

}

