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
import org.opends.messages.Message;



import java.util.List;
import javax.net.ssl.KeyManager;

import org.opends.server.admin.std.server.KeyManagerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;



/**
 * This class defines an API that may be used to obtain a set of
 * {@code javax.net.ssl.KeyManager} objects for use when performing
 * SSL communication.
 *
 * @param <T>
 *          The type of key manager provider configuration handled by
 *          this key manager provider implementation.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=true)
public abstract class KeyManagerProvider
    <T extends KeyManagerCfg>
{
  /**
   * Initializes this key manager provider based on the information in
   * the provided key manager provider configuration.
   *
   * @param configuration
   *          The key manager provider configuration that contains the
   *          information to use to initialize this key manager
   *          provider.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public abstract void initializeKeyManagerProvider(T configuration)
      throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this key manager provider.  It should be possible to call this
   * method on an uninitialized key manager provider instance in order
   * to determine whether the key manager provider would be able to
   * use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The key manager provider
   *                              configuration for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this key manager provider, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      KeyManagerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by key manager provider
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any finalization that may be necessary for this key
   * manager provider.
   */
  public abstract void finalizeKeyManagerProvider();



  /**
   * Retrieves a set of {@code KeyManager} objects that may be used
   * for interactions requiring access to a key manager.
   *
   * @return  A set of {@code KeyManager} objects that may be used for
   *          interactions requiring access to a key manager.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to obtain the set of key managers.
   */
  public abstract KeyManager[] getKeyManagers()
         throws DirectoryException;
}

