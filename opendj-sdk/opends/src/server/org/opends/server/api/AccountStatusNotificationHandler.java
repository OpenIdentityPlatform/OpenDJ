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

import org.opends.server.admin.std.server.
       AccountStatusNotificationHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;



/**
 * This class defines the set of methods that must be implemented for
 * an account status notification handler.  This handler will be
 * invoked whenever certain types of events occur that could change
 * the status of a user account.  The account status notification
 * handler may be used to notify the user and/or administrators of the
 * change.
 *
 * @param  <T>  The type of configuration handled by this notification
 *              handler.
 */
public abstract class
       AccountStatusNotificationHandler
       <T extends AccountStatusNotificationHandlerCfg>
{
  /**
   * Initializes this account status notification handler based on the
   * information in the provided configuration entry.
   *
   * @param  configuration  The configuration entry that contains the
   *                        information to use to initialize this
   *                        account status notification handler.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid configuration for this account
   *                           status notification handler.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeStatusNotificationHandler(
         T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this account status notification handler.  It should be possible
   * to call this method on an uninitialized account status
   * notification handler instance in order to determine whether the
   * handler would be able to use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The account status notification
   *                              handler configuration for which to
   *                              make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this account status notification handler, or
   *          {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      AccountStatusNotificationHandlerCfg
                           configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by account status
    // notification implementations that wish to perform more detailed
    // validation.
    return true;
  }



  /**
   * Performs any finalization that may be necessary when this status
   * notification handler is taken out of service.
   */
  public void finalizeStatusNotificationHandler()
  {
    // No action is required by default.
  }



  /**
   * Performs any processing that may be necessary in conjunction with
   * the provided account status notification type.
   *
   * @param  notificationType  The type for this account status
   *                           notification.
   * @param  userDN            The DN of the user entry to which this
   *                           notification applies.
   * @param  message           The human-readable message for this
   *                           notification.
   */
  public abstract void
       handleStatusNotification(
          AccountStatusNotificationType notificationType,
          DN userDN, Message message);



  /**
   * Performs any processing that may be necessary in conjunction with
   * the provided account status notification.
   *
   * @param  notification  The account status notification to be
   *                       processed.
   */
  public void handleStatusNotification(
                   AccountStatusNotification notification)
  {
    handleStatusNotification(notification.getNotificationType(),
                             notification.getUserDN(),
                             notification.getMessage());
  }
}

