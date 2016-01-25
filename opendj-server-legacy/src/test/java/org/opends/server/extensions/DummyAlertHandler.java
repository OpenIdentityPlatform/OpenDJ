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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.AlertHandlerCfg;
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
