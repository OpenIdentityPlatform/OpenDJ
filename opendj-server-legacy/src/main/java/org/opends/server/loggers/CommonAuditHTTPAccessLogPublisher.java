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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.HTTPAccessLogPublisherCfg;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.ldap.DN;

/**
 * Publishes HTTP access events to the common audit service.
 * <p>
 * This class actually does nothing because HTTP access logging is managed by a CHF filter.
 * (See LdapHttpApplication)
 *
 * @param <T> the type of configuration
 */
class CommonAuditHTTPAccessLogPublisher<T extends HTTPAccessLogPublisherCfg>
  extends HTTPAccessLogPublisher<T>
  implements CommonAuditLogPublisher, ConfigurationChangeListener<T>
{
  /** Current configuration for this publisher. */
  private T config;

  @Override
  public void setRequestHandler(RequestHandler handler)
  {
    // nothing to do
  }

  T getConfig()
  {
    return config;
  }

  @Override
  public void initializeLogPublisher(final T cfg, ServerContext serverContext)
  {
    config = cfg;
  }

  @Override
  public final DN getDN()
  {
    return config != null ? config.dn() : null;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(final T config)
  {
    this.config = config;
    return new ConfigChangeResult();
  }

  @Override
  public boolean isConfigurationAcceptable(final T configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(final T config, final List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public void logRequestInfo(HTTPRequestInfo requestInfo)
  {
    // nothing to do
  }

  @Override
  public void close()
  {
    // nothing to do
  }
}
