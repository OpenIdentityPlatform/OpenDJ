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

import org.forgerock.opendj.server.config.server.AlertHandlerCfg;
import org.forgerock.opendj.config.server.ConfigException;
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
  void initializeAlertHandler(T configuration)
       throws ConfigException, InitializationException;



  /**
   * Retrieves the current configuration for this alert handler.
   *
   * @return  The current configuration for this alert handler.
   */
  AlertHandlerCfg getAlertHandlerConfiguration();



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
  boolean isConfigurationAcceptable(
                      AlertHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons);



  /**
   * Performs any necessary cleanup that may be necessary when this
   * alert handler is finalized.
   */
  void finalizeAlertHandler();



  /**
   * Sends an alert notification based on the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertMessage  A message (possibly {@code null}) that can
   *                       provide more information about this alert.
   */
  void sendAlertNotification(AlertGenerator generator,
                                    String alertType,
                                    LocalizableMessage alertMessage);
}

