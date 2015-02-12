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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.extensions;

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.SaltedSHA1PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.SaltedSHA1PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.types.DirectoryException;

/**
 * A set of test cases for the salted SHA-1 password storage scheme.
 */
public class SaltedSHA1PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /**
   * Creates a new instance of this storage scheme test case.
   */
  public SaltedSHA1PasswordStorageSchemeTestCase()
  {
    super("cn=Salted SHA-1,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  protected PasswordStorageScheme<?> getScheme() throws Exception
  {
    SaltedSHA1PasswordStorageScheme scheme =
         new SaltedSHA1PasswordStorageScheme();

    SaltedSHA1PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          SaltedSHA1PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }

  /** {@inheritDoc} */
  @Override
  protected String encodeOffline(final byte[] plaintextBytes) throws DirectoryException
  {
    return SaltedSHA1PasswordStorageScheme.encodeOffline(plaintextBytes);
  }
}

