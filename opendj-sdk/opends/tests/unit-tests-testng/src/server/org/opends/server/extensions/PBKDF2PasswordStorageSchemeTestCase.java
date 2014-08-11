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
import org.opends.server.admin.std.meta.PBKDF2PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.PBKDF2PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;

/**
 * A set of test cases for the PBKDF2 password storage scheme.
 */
@SuppressWarnings("javadoc")
public class PBKDF2PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /** Creates a new instance of this storage scheme test case.   */
  public PBKDF2PasswordStorageSchemeTestCase()
  {
    super("cn=PBKDF2,cn=Password Storage Schemes,cn=config");
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
    PBKDF2PasswordStorageScheme scheme =
         new PBKDF2PasswordStorageScheme();

    PBKDF2PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
              PBKDF2PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }

  @Override
  protected String encodeOffline(final byte[] plaintextBytes) throws DirectoryException
  {
    return PBKDF2PasswordStorageScheme.encodeOffline(plaintextBytes);
  }
}
