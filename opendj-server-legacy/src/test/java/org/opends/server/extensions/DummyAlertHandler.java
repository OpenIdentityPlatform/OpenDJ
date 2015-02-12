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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AlertHandlerCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.InitializationException;

/**
 * This class implements a Directory Server alert handler that only provides a
 * mechanism to determine the number of times it is invoked.
 */
public class DummyAlertHandler
       implements AlertHandler<AlertHandlerCfg>,
                  ConfigurationChangeListener<AlertHandlerCfg>
{
  /** The current configuration for this alert handler. */
  private AlertHandlerCfg currentConfig;

  /** The number of times this alert handler has been invoked. */
  private static AtomicInteger alertCount = new AtomicInteger(0);

  /** Creates a new instance of this SMTP alert handler. */
  public DummyAlertHandler()
  {
    super();
    // All initialization should be done in the initializeAlertHandler method.
  }

  /** {@inheritDoc} */
  @Override
  public void initializeAlertHandler(AlertHandlerCfg configuration)
       throws ConfigException, InitializationException
  {
    configuration.addChangeListener(this);
    currentConfig = configuration;
  }

  /** {@inheritDoc} */
  @Override
  public AlertHandlerCfg getAlertHandlerConfiguration()
  {
    return currentConfig;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(AlertHandlerCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeAlertHandler()
  {
    // No action is required.
  }

  /** {@inheritDoc} */
  @Override
  public void sendAlertNotification(AlertGenerator generator, String alertType,
                                    LocalizableMessage alertMessage)
  {
    alertCount.incrementAndGet();
  }

  /**
   * Retrieves the number of times that this alert handler has been invoked.
   *
   * @return  The number of times that this alert handler has been invoked.
   */
  public static int getAlertCount()
  {
    return alertCount.get();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(AlertHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 AlertHandlerCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
