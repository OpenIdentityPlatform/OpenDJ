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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.opends.server.api;



import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.server.AuthenticationPolicyCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ServerContext;
import org.opends.server.types.InitializationException;



/**
 * A factory for creating configurable authentication policies.
 * <p>
 * All implementations must have a default constructor, i.e. one that does not
 * require and arguments.
 *
 * @param <T>
 *          The type of authentication policy configuration handled by this
 *          factory.
 */
public interface AuthenticationPolicyFactory<T extends AuthenticationPolicyCfg>
{
  /**
   * Creates a new authentication policy using the provided configuration.
   *
   * @param configuration
   *          The configuration.
   * @return The new authentication policy configured using the provided
   *         configuration.
   * @throws ConfigException
   *           If an unrecoverable problem arises during initialization of the
   *           authentication policy as a result of the server configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization of the authentication
   *           policy.
   */
  AuthenticationPolicy createAuthenticationPolicy(T configuration)
      throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided authentication policy configuration is
   * acceptable.
   *
   * @param configuration
   *          The authentication policy configuration.
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the provided
   *          configuration is not acceptable.
   * @return Returns <code>true</code> if the provided authentication policy
   *         configuration is acceptable, or <code>false</code> if it is not.
   */
  boolean isConfigurationAcceptable(T configuration,
      List<LocalizableMessage> unacceptableReasons);


  /**
   * Sets the server context.
   *
   * @param serverContext
   *            The server context.
   */
  void setServerContext(ServerContext serverContext);

}
