/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.loggers;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CsvFileAccessLogPublisherCfg;

/**
 * Common Audit publisher which publishes access events to CSV files.
 */
final class CsvFileAccessLogPublisher
  extends CommonAuditAccessLogPublisher<CsvFileAccessLogPublisherCfg>
  implements ConfigurationChangeListener<CsvFileAccessLogPublisherCfg>
{

  @Override
  boolean shouldLogControlOids()
  {
    return getConfig().isLogControlOids();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(final CsvFileAccessLogPublisherCfg config)
  {
    setConfig(config);
    return new ConfigChangeResult();
  }

  @Override
  public boolean isConfigurationChangeAcceptable(final CsvFileAccessLogPublisherCfg config,
      final List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

}
