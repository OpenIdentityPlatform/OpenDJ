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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.server.extensions;



import org.forgerock.opendj.config.server.AdminTestCaseUtils;
import org.forgerock.opendj.server.config.meta.SHA1PasswordStorageSchemeCfgDefn;
import org.forgerock.opendj.server.config.server.SHA1PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;



/**
 * A set of test cases for the SHA-1 password storage scheme.
 */
public class SHA1PasswordStorageSchemeTestCase
       extends PasswordStorageSchemeTestCase
{
  /**
   * Creates a new instance of this storage scheme test case.
   */
  public SHA1PasswordStorageSchemeTestCase()
  {
    super("cn=SHA-1,cn=Password Storage Schemes,cn=config");
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
    SHA1PasswordStorageScheme scheme = new SHA1PasswordStorageScheme();

    SHA1PasswordStorageSchemeCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          SHA1PasswordStorageSchemeCfgDefn.getInstance(),
          configEntry
          );

    scheme.initializePasswordStorageScheme(configuration);
    return scheme;
  }
}

