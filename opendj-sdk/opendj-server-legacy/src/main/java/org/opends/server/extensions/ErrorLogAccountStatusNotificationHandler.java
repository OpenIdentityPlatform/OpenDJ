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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.util.HashSet;
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.
       ErrorLogAccountStatusNotificationHandlerCfgDefn;
import org.opends.server.admin.std.server.AccountStatusNotificationHandlerCfg;
import org.opends.server.admin.std.server.
       ErrorLogAccountStatusNotificationHandlerCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

/**
 * This class defines an account status notification handler that will write
 * information about status notifications using the Directory Server's error
 * logging facility.
 */
public class ErrorLogAccountStatusNotificationHandler
       extends
          AccountStatusNotificationHandler
          <ErrorLogAccountStatusNotificationHandlerCfg>
       implements
          ConfigurationChangeListener
          <ErrorLogAccountStatusNotificationHandlerCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The set of names for the account status notification types that may be
   * logged by this notification handler.
   */
  private static final HashSet<String> NOTIFICATION_TYPE_NAMES = new HashSet<>();
  static
  {
    for (AccountStatusNotificationType t : AccountStatusNotificationType.values())
    {
      NOTIFICATION_TYPE_NAMES.add(t.getName());
    }
  }


  /** The DN of the configuration entry for this notification handler. */
  private DN configEntryDN;

  /** The set of notification types that should generate log messages. */
  private HashSet<AccountStatusNotificationType> notificationTypes;



  /** {@inheritDoc} */
  public void initializeStatusNotificationHandler(
      ErrorLogAccountStatusNotificationHandlerCfg configuration
      )
      throws ConfigException, InitializationException
  {
    configuration.addErrorLogChangeListener (this);
    configEntryDN = configuration.dn();

    // Read configuration and apply changes.
    boolean applyChanges = true;
    processNotificationHandlerConfig (configuration, applyChanges);
  }



  /** {@inheritDoc} */
  public void handleStatusNotification(
                   AccountStatusNotification notification)
  {
    logger.info(NOTE_ERRORLOG_ACCTNOTHANDLER_NOTIFICATION,
                  notification.getNotificationType().getName(),
                  notification.getUserDN(),
                  notification.getMessage().ordinal(),
                  notification.getMessage());
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(
                      AccountStatusNotificationHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    ErrorLogAccountStatusNotificationHandlerCfg config =
         (ErrorLogAccountStatusNotificationHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
      ErrorLogAccountStatusNotificationHandlerCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // Make sure that we can process the defined notification handler.
    // If so, then we'll accept the new configuration.
    boolean applyChanges = false;
    return processNotificationHandlerConfig (
        configuration, applyChanges);
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configuration    The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyConfigurationChange (
      ErrorLogAccountStatusNotificationHandlerCfg configuration,
      boolean detailedResults)
  {
    return applyConfigurationChange(configuration);
  }



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange (
      ErrorLogAccountStatusNotificationHandlerCfg configuration
      )
  {
    // Initialize the set of notification types that should generate log messages.
    boolean applyChanges =false;
    processNotificationHandlerConfig (configuration, applyChanges);

    return new ConfigChangeResult();
  }


  /**
   * Parses the provided configuration and configure the notification handler.
   *
   * @param configuration  The new configuration containing the changes.
   * @param applyChanges   If true then take into account the new configuration.
   *
   * @return  The mapping between strings of character set values and the
   *          minimum number of characters required from those sets.
   */
  public boolean processNotificationHandlerConfig(
      ErrorLogAccountStatusNotificationHandlerCfg configuration,
      boolean                                     applyChanges
      )
  {
    // false if the configuration is not acceptable
    boolean isAcceptable = true;

    // The set of notification types that should generate log messages.
    HashSet<AccountStatusNotificationType> newNotificationTypes = new HashSet<>();

    // Initialize the set of notification types that should generate log messages.
    for (ErrorLogAccountStatusNotificationHandlerCfgDefn.
         AccountStatusNotificationType configNotificationType:
         configuration.getAccountStatusNotificationType())
    {
      newNotificationTypes.add (getNotificationType (configNotificationType));
    }

    if (applyChanges && isAcceptable)
    {
      notificationTypes = newNotificationTypes;
    }

    return isAcceptable;
  }


  /**
   * Gets the OpenDS notification type object that corresponds to the
   * configuration counterpart.
   *
   * @param  configNotificationType  The configuration notification type for
   *                                 which  to retrieve the OpenDS notification
   *                                 type.
   */
  private AccountStatusNotificationType getNotificationType(
      ErrorLogAccountStatusNotificationHandlerCfgDefn.
         AccountStatusNotificationType configNotificationType
      )
  {
    AccountStatusNotificationType nt = null;

    switch (configNotificationType)
    {
    case ACCOUNT_TEMPORARILY_LOCKED:
         nt = AccountStatusNotificationType.ACCOUNT_TEMPORARILY_LOCKED;
         break;
    case ACCOUNT_PERMANENTLY_LOCKED:
         nt = AccountStatusNotificationType.ACCOUNT_PERMANENTLY_LOCKED;
         break;
    case ACCOUNT_UNLOCKED:
         nt = AccountStatusNotificationType.ACCOUNT_UNLOCKED;
         break;
    case ACCOUNT_IDLE_LOCKED:
         nt = AccountStatusNotificationType.ACCOUNT_IDLE_LOCKED;
         break;
    case ACCOUNT_RESET_LOCKED:
         nt = AccountStatusNotificationType.ACCOUNT_RESET_LOCKED;
         break;
    case ACCOUNT_DISABLED:
         nt = AccountStatusNotificationType.ACCOUNT_DISABLED;
         break;
    case ACCOUNT_ENABLED:
         nt = AccountStatusNotificationType.ACCOUNT_ENABLED;
         break;
    case ACCOUNT_EXPIRED:
         nt = AccountStatusNotificationType.ACCOUNT_EXPIRED;
         break;
    case PASSWORD_EXPIRED:
         nt = AccountStatusNotificationType.PASSWORD_EXPIRED;
         break;
    case PASSWORD_EXPIRING:
         nt = AccountStatusNotificationType.PASSWORD_EXPIRING;
         break;
    case PASSWORD_RESET:
         nt = AccountStatusNotificationType.PASSWORD_RESET;
         break;
    case PASSWORD_CHANGED:
         nt = AccountStatusNotificationType.PASSWORD_CHANGED;
         break;
    }

    return nt;
  }

}

