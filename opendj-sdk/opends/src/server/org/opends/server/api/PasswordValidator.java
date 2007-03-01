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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.Set;

import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.Operation;
import org.opends.server.types.ByteString;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;




/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that may be used to
 * determine whether a proposed password is acceptable for a user.
 */
public abstract class PasswordValidator
{



  /**
   * Initializes this password validator based on the information in
   * the provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this
   *                      password validator.
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
  public abstract void initializePasswordValidator(
                            ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Performs any finalization that might be required when this
   * password validator is unloaded.  No action is taken in the
   * default implementation.
   */
  public void finalizePasswordValidator()
  {


    // No action is required by default.
  }



  /**
   * Indicates whether the provided password is acceptable for use by
   * the specified user.  If the password is determined to be
   * unacceptable, then a human-readable explanation should be
   * appended to the provided buffer.
   *
   * @param  newPassword       The proposed clear-text password that
   *                           should be validated.
   * @param  currentPasswords  The set of clear-text current passwords
   *                           for the user (if available).  Note that
   *                           the current passwords may not always be
   *                           available, and this may not comprise
   *                           entire set of passwords currently
   *                           for the user.
   * @param  operation         The operation that is being used to set
   *                           the password.  It may be an add, a
   *                           modify, or a password modify operation.
   * @param  userEntry         The entry for the user whose password
   *                           is being changed.
   * @param  invalidReason     The buffer to which the human-readable
   *                           explanation should be appended if it is
   *                           determined that the password is not
   *                           acceptable.
   *
   * @return  <CODE>true</CODE> if the password is acceptable, or
   *          <CODE>false</CODE> if not.
   */
  public abstract boolean passwordIsAcceptable(ByteString newPassword,
                               Set<ByteString> currentPasswords,
                               Operation operation,
                               Entry userEntry,
                               StringBuilder invalidReason);
}

