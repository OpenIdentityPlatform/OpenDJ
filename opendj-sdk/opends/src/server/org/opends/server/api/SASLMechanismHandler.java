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



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.types.InitializationException;




/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements the
 * functionality required for one or more SASL mechanisms.
 */
public abstract class SASLMechanismHandler
{



  /**
   * Initializes this SASL mechanism handler based on the information
   * in the provided configuration entry.  It should also register
   * itself with the Directory Server for the particular kinds of SASL
   * mechanisms that it will process.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this SASL
   *                      mechanism handler.
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
  public abstract void initializeSASLMechanismHandler(
                            ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Performs any finalization that may be necessary for this SASL
   * mechanism handler.  By default, no finalization is performed.
   */
  public void finalizeSASLMechanismHandler()
  {

    // No implementation is required by default.
  }



  /**
   * Processes the SASL bind operation.  SASL mechanism
   * implementations must ensure that the following actions are taken
   * during the processing of this method:
   * <UL>
   *   <LI>The {@code BindOperation.setResultCode} method must be used
   *       to set the appropriate result code.</LI>
   *   <LI>If the SASL processing gets far enough to be able to map
   *       the request to a user entry (regardless of whether the
   *       authentication is ultimately successful), then this method
   *       must call the {@code BindOperation.setSASLAuthUserEntry}
   *       method to provide it with the entry for the user that
   *       attempted to authenticate.</LI>
   *   <LI>If the bind processing was successful, then the
   *       {@code BindOperation.setAuthenticationInfo} method must be
   *       used to set the authentication info for the bind
   *       operation.</LI>
   *   <LI>If the bind processing was not successful, then the
   *       {@code BindOperation.setAuthFailureReason} method should be
   *       used to provide a message explaining why the authentication
   *       failed.</LI>
   * </UL>
   *
   * @param  bindOperation  The SASL bind operation to be processed.
   */
  public abstract void processSASLBind(BindOperation bindOperation);



  /**
   * Indicates whether the specified SASL mechanism is password-based
   * or uses some other form of credentials (e.g., an SSL client
   * certificate or Kerberos ticket).
   *
   * @param  mechanism  The name of the mechanism for which to make
   *                    the determination.  This will only be invoked
   *                    with names of mechanisms for which this
   *                    handler has previously registered.
   *
   * @return  <CODE>true</CODE> if this SASL mechanism is
   *          password-based, or <CODE>false</CODE> if it uses some
   *          other form of credentials.
   */
  public abstract boolean isPasswordBased(String mechanism);



  /**
   * Indicates whether the specified SASL mechanism should be
   * considered secure (i.e., it does not expose the authentication
   * credentials in a manner that is useful to a third-party observer,
   * and other aspects of the authentication are generally secure).
   *
   * @param  mechanism  The name of the mechanism for which to make
   *                    the determination.  This will only be invoked
   *                    with names of mechanisms for which this
   *                    handler has previously registered.
   *
   * @return  <CODE>true</CODE> if this SASL mechanism should be
   *          considered secure, or <CODE>false</CODE> if not.
   */
  public abstract boolean isSecure(String mechanism);
}

