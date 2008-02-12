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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AlertHandlerCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.messages.Message;


/**
 * This class implements a Directory Server alert handler that only provides a
 * mechanism to determine the number of times it is invoked.
 */
public class DummyAlertHandler
       implements AlertHandler<AlertHandlerCfg>,
                  ConfigurationChangeListener<AlertHandlerCfg>
{
  // The current configuration for this alert handler.
  private AlertHandlerCfg currentConfig;

  // The number of times this alert handler has been invoked.
  private static AtomicInteger alertCount = new AtomicInteger(0);


  /**
   * Creates a new instance of this SMTP alert handler.
   */
  public DummyAlertHandler()
  {
    super();

    // All initialization should be done in the initializeAlertHandler method.
  }



  /**
   * {@inheritDoc}
   */
  public void initializeAlertHandler(AlertHandlerCfg configuration)
       throws ConfigException, InitializationException
  {
    configuration.addChangeListener(this);
    currentConfig = configuration;
  }



  /**
   * {@inheritDoc}
   */
  public AlertHandlerCfg getAlertHandlerConfiguration()
  {
    return currentConfig;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAcceptable(AlertHandlerCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeAlertHandler()
  {
    // No action is required.
  }



  /**
   * {@inheritDoc}
   */
  public void sendAlertNotification(AlertGenerator generator, String alertType,
                                    Message alertMessage)
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



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(AlertHandlerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 AlertHandlerCfg configuration)
  {
    currentConfig = configuration;

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

