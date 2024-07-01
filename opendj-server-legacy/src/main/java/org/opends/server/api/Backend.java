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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.api;

import java.util.List;
import java.util.Set;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.core.ServerContext;
import org.opends.server.types.InitializationException;

/**
 * Represents a directory server backend, which can be either local or remote (proxy).
 * A backend is a LDAP endpoint.
 *
 * @param <C>
 *          the type of the BackendCfg for the current backend
 */
public abstract class Backend<C extends Configuration>
  //implements ReactiveHandler<LdapClientConnection, Request, Response>
{

  /** The unique identifier for this backend. */
  private String backendID;

  /**
   * Performs the provided request on this backend.
   *
   * @return a stream of response
   */
  //public abstract Stream<Response> handle(final LDAPClientConnection clientContext, final Request request)
  //   throws Exception;

  /**
   * Configure this backend based on the information in the provided configuration.
   * When the method returns, the backend will have been configured (ready to be opened) but still unable
   * to process operations.
   *
   * @param  cfg          The configuration of this backend.
   * @param  serverContext The server context for this instance
   * @throws  ConfigException
   *                      If there is an error in the configuration.
   */
  public abstract void configureBackend(C cfg, ServerContext serverContext) throws ConfigException;

  /**
   * Opens this backend based on the information provided when the backend was configured.
   * It also should open any underlying storage and register all suffixes with the server.
   *
   * @see #configureBackend
   *
   * @throws  ConfigException  If an unrecoverable problem arises while opening the backend.
   *
   * @throws  InitializationException  If a problem occurs during opening that is not
   *                                   related to the server configuration.
   */
  public abstract void openBackend() throws ConfigException, InitializationException;

  /**
   * Performs any necessary work to finalize this backend. The backend must be an opened backend,
   * so do not use this method on backends where only <code>configureBackend()</code> has been called.
   * This may be called during the Directory Server shutdown process or if a backend is disabled
   * with the server online.
   * It must not return until the backend is closed.
   * <p>
   * This method may not throw any exceptions. If any problems are encountered,
   * then they may be logged but the closure should progress as completely as
   * possible.
   * <p>
   */
  public abstract void finalizeBackend();

  /**
   * Retrieves the unique identifier for this backend.
   *
   * @return  The unique identifier for this backend.
   */
  public final String getBackendID()
  {
    return backendID;
  }

  /**
   * Retrieves the set of base-level DNs that may be used within this
   * backend.
   *
   * @return  The set of base-level DNs that may be used within this
   *          backend.
   */
  public abstract Set<DN> getBaseDNs();

  /**
   * Indicates whether this backend should be considered a default (wild-card) route.
   *
   * @return {@code true} if the backend should be considered as the default route, {@code false} otherwise
   */
  public abstract boolean isDefaultRoute();

  /**
   * Retrieves the OIDs of the controls that may be supported by this
   * backend.
   *
   * @return  The OIDs of the controls that may be supported by this
   *          backend.
   */
  public abstract Set<String> getSupportedControls();

  /**
   * Retrieves the OIDs of the features that may be supported by this
   * backend.
   *
   * @return  The OIDs of the features that may be supported by this
   *          backend.
   */
  public abstract Set<String> getSupportedFeatures();

  /**
   * Indicates whether the provided configuration is acceptable for
   * this backend.  It should be possible to call this method on an
   * uninitialized backend instance in order to determine whether the
   * backend would be able to use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The backend configuration for which
   *                              to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   * @param serverContext         this Directory Server instance's server context
   * @return  {@code true} if the provided configuration is acceptable
   *          for this backend, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      C configuration,
                      List<LocalizableMessage> unacceptableReasons, ServerContext serverContext)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by backend implementations
    // that wish to perform more detailed validation.
    return true;
  }

  /**
   * Indicates whether this backend holds private data or user data.
   *
   * @return  {@code true} if this backend holds private data, or
   *          {@code false} if it holds user data.
   */
  public abstract boolean isPrivateBackend();

  /**
   * Specifies the unique identifier for this backend.
   *
   * @param  backendID  The unique identifier for this backend.
   */
  public final void setBackendID(String backendID)
  {
    this.backendID = backendID;
  }

}
