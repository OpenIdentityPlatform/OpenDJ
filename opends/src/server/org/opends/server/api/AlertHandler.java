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

import org.opends.server.admin.std.server.AlertHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;


/**
 * This interface defines the set of methods that must be implemented
 * for a Directory Server alert handler.  Alert handlers are used to
 * present alert notifications in various forms like JMX, e-mail, or
 * paging.
 *
 * @param  <T>  The type of configuration handled by this alert
 *              handler.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface AlertHandler<T extends AlertHandlerCfg>
{
  /**
   * Initializes this alert handler based on the information in the
   * provided configuration entry.
   *
   * @param  configuration  The configuration to use to initialize
   *                        this alert handler.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid configuration for this alert
   *                           handler.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public void initializeAlertHandler(T configuration)
       throws ConfigException, InitializationException;



  /**
   * Retrieves the current configuration for this alert handler.
   *
   * @return  The current configuration for this alert handler.
   */
  public AlertHandlerCfg getAlertHandlerConfiguration();



  /**
   * Indicates whether the provided configuration is acceptable for
   * this alert handler.
   *
   * @param  configuration        The configuration for which to make
   *                              tje determination.
   * @param  unacceptableReasons  A list to which human-readable
   *                              reasons may be added to explain why
   *                              the configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is
   *          acceptable, or {@code false} if it is not.
   */
  public boolean isConfigurationAcceptable(
                      AlertHandlerCfg configuration,
                      List<Message> unacceptableReasons);



  /**
   * Performs any necessary cleanup that may be necessary when this
   * alert handler is finalized.
   */
  public void finalizeAlertHandler();



  /**
   * Sends an alert notification based on the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertMessage  A message (possibly {@code null}) that can
   *                       provide more information about this alert.
   */
  public void sendAlertNotification(AlertGenerator generator,
                                    String alertType,
                                    Message alertMessage);
}

