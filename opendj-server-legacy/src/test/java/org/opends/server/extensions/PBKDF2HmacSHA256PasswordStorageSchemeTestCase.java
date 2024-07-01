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
 */
package org.opends.server.extensions;

import org.forgerock.opendj.server.config.meta.PBKDF2PasswordStorageSchemeCfgDefn;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.DataProvider;

/**
 * A set of test cases for the PBKDF2-HMAC-SHA256 password storage scheme.
 */
@SuppressWarnings("javadoc")
public class PBKDF2HmacSHA256PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /** Creates a new instance of this storage scheme test case.   */
  public PBKDF2HmacSHA256PasswordStorageSchemeTestCase()
  {
    super("cn=PBKDF2-HMAC-SHA256,cn=Password Storage Schemes,cn=config");
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
        new PBKDF2HmacSHA256PasswordStorageScheme(), configEntry, PBKDF2PasswordStorageSchemeCfgDefn.getInstance());
  }

  @Override
  protected String encodeOffline(final byte[] plaintextBytes) throws DirectoryException
  {
    return PBKDF2HmacSHA256PasswordStorageScheme.encodeOffline(plaintextBytes);
  }
}
