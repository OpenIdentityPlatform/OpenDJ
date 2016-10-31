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
package org.opends.server.loggers;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.JsonFileAccessLogPublisherCfg;

import java.util.List;

/** Common Audit publisher which publishes access events to Json files. */
final class JsonFileAccessLogPublisher
  extends CommonAuditAccessLogPublisher<JsonFileAccessLogPublisherCfg>
  implements ConfigurationChangeListener<JsonFileAccessLogPublisherCfg>
{
  @Override
  boolean shouldLogControlOids()
  {
    return getConfig().isLogControlOids();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(final JsonFileAccessLogPublisherCfg config)
  {
    setConfig(config);
    return new ConfigChangeResult();
  }

  @Override
  public boolean isConfigurationChangeAcceptable(final JsonFileAccessLogPublisherCfg config,
                                                 final List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }
}
