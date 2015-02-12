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

import org.testng.annotations.Test;

import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.SaltedMD5PasswordStorageSchemeCfgDefn;
import org.opends.server.admin.std.server.SaltedMD5PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.forgerock.opendj.ldap.ByteString;

import static org.testng.Assert.*;

/**
 * A set of test cases for the salted MD5 password storage scheme.
 */
public class SaltedMD5PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /**
   * Creates a new instance of this storage scheme test case.
   */
  public SaltedMD5PasswordStorageSchemeTestCase()
  {
    super("cn=Salted MD5,cn=Password Storage Schemes,cn=config");
  }

  /**
   * Retrieves an initialized instance of this password storage scheme.
   *
   * @return  An initialized instance of this password storage scheme.
   */
  protected PasswordStorageScheme getScheme() throws Exception
  {
    SaltedMD5PasswordStorageScheme scheme =
         new SaltedMD5PasswordStorageScheme();

    SaltedMD5PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          SaltedMD5PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry.getEntry()
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }



  /**
   * Tests matching with a different salt size.
   */
  @Test
  public void testDifferentSaltSize() throws Exception {
    SaltedMD5PasswordStorageScheme scheme =
      new SaltedMD5PasswordStorageScheme();

    SaltedMD5PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
        SaltedMD5PasswordStorageSchemeCfgDefn.getInstance(),
        configEntry.getEntry()
      );

    scheme.initializePasswordStorageScheme(configuration);
    // The stored value has a 12 byte salt instead of the default 8
    assertTrue(scheme.passwordMatches(ByteString.valueOf("password"),
      ByteString.valueOf("so5s1vK3oEi4uL/oVY3bqs5LRlKjgMN+u4A4bw==")));
  }
}
