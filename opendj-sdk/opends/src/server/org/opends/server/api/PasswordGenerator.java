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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.admin.std.server.PasswordGeneratorCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;



/**
 * This class defines a set of methods and structures that must be
 * implemented by a Directory Server module that may be used to
 * generate user passwords. The password generator is included as part
 * of a password policy, and is used by the password modify extended
 * operation to construct a new password for the user if that option
 * is chosen.
 *
 * @param  <T>  The type of configuration handled by this password
 *              generator.
 */
public abstract class PasswordGenerator
       <T extends PasswordGeneratorCfg>
{
  /**
   * Initializes this password generator based on the information in
   * the provided configuration entry.
   *
   * @param  configuration  The configuration to use to initialize
   *                        this password validator.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializePasswordGenerator(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Performs any finalization work that may be necessary when this
   * password generator is taken out of service.
   */
  public void finalizePasswordGenerator()
  {
    // No action is performed by default.
  }



  /**
   * Generates a password for the user whose account is contained in
   * the specified entry.
   *
   * @param  userEntry  The entry for the user for whom the password
   *                    is to be generated.
   *
   * @return  The password that has been generated for the user.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to generate the password.
   */
  public abstract ByteString generatePassword(Entry userEntry)
         throws DirectoryException;
}

