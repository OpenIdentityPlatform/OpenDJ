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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.api;
import org.forgerock.i18n.LocalizableMessage;



import java.util.List;
import javax.net.ssl.TrustManager;

import org.forgerock.opendj.server.config.server.TrustManagerProviderCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;



/**
 * This class defines an API that may be used to obtain a set of
 * {@code javax.net.ssl.TrustManager} objects for use when performing
 * SSL/StartTLS negotiation.
 *
 * @param  <T>  The type of trust manager provider configuration
 *              handled by this trust manager provider implementation.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=true)
public abstract class TrustManagerProvider<T extends
        TrustManagerProviderCfg>
{
  /**
   * Initializes this trust manager provider based on the information
   * in the provided configuration entry.
   *
   * @param  configuration  The configuration to use for this trust
   *                        manager provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization as a result of the
   *                           server configuration.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeTrustManagerProvider(
                            T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this trust manager provider.  It should be possible to call this
   * method on an uninitialized trust manager provider instance in
   * order to determine whether the trust manager provider would be
   * able to use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The trust manager provider
   *                              configuration for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this trust manager provider, or {@code false} if
   *          not.
   */
  public boolean isConfigurationAcceptable(
                      TrustManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by trust manager provider
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any finalization that may be necessary for this trust
   * manager provider.
   */
  public abstract void finalizeTrustManagerProvider();



  /**
   * Retrieves a set of {@code TrustManager} objects that may be used
   * for interactions requiring access to a trust manager.
   *
   * @return  A set of {@code TrustManager} objects that may be used
   *          for interactions requiring access to a trust manager.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to obtain the set of trust managers.
   */
  public abstract TrustManager[] getTrustManagers()
         throws DirectoryException;
}

