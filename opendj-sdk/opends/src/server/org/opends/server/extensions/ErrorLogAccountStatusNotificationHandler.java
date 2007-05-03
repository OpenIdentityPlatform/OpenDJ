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
package org.opends.server.extensions;



import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.
       ErrorLogAccountStatusNotificationHandlerCfgDefn;
import org.opends.server.admin.std.server.
       ErrorLogAccountStatusNotificationHandlerCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigException;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;



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
  /**
   * The set of names for the account status notification types that may be
   * logged by this notification handler.
   */
  private static final HashSet<String> NOTIFICATION_TYPE_NAMES =
       new HashSet<String>();

  static
  {
    for (AccountStatusNotificationType t :
         AccountStatusNotificationType.values())
    {
      NOTIFICATION_TYPE_NAMES.add(t.getNotificationTypeName());
    }
  }


  // The DN of the configuration entry for this notification handler.
  private DN configEntryDN;

  // The set of notification types that should generate log messages.
  private HashSet<AccountStatusNotificationType> notificationTypes;



  /**
   * {@inheritDoc}
   */
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



  /**
   * {@inheritDoc}
   */
  public void handleStatusNotification(AccountStatusNotificationType
                                            notificationType,
                                       DN userDN, int messageID, String message)
  {
    if (notificationTypes.contains(notificationType))
    {
      int msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_NOTIFICATION;
      logError(ErrorLogCategory.PASSWORD_POLICY, ErrorLogSeverity.NOTICE,
               msgID, notificationType.getNotificationTypeName(),
               String.valueOf(userDN), messageID, message);
    }
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    LinkedList<String> typeNames = new LinkedList<String>();
    for (AccountStatusNotificationType t : notificationTypes)
    {
      typeNames.add(t.getNotificationTypeName());
    }

    int msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_DESCRIPTION_NOTIFICATION_TYPES;
    attrList.add(new MultiChoiceConfigAttribute(ATTR_ACCT_NOTIFICATION_TYPE,
                                                getMessage(msgID), true, true,
                                                false, NOTIFICATION_TYPE_NAMES,
                                                typeNames));

    return attrList;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      ErrorLogAccountStatusNotificationHandlerCfg configuration,
      List<String> unacceptableReasons
      )
  {
    // Make sure that we can process the defined notification handler.
    // If so, then we'll accept the new configuration.
    boolean applyChanges = false;
    boolean isAcceptable = processNotificationHandlerConfig (
        configuration, applyChanges
        );

    return isAcceptable;
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
      boolean detailedResults
      )
  {
    ConfigChangeResult changeResult = applyConfigurationChange (configuration);
    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange (
      ErrorLogAccountStatusNotificationHandlerCfg configuration
      )
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();
    ConfigChangeResult changeResult = new ConfigChangeResult(
        resultCode, adminActionRequired, messages
        );

    // Initialize the set of notification types that should generate log
    // messages.
    boolean applyChanges = false;
    processNotificationHandlerConfig (
        configuration, applyChanges
        );

    return changeResult;
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
    HashSet<AccountStatusNotificationType> newNotificationTypes =
        new HashSet<AccountStatusNotificationType>();

    // Initialize the set of notification types that should generate log
    // messages.
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
   * @param  notificationType  The configuration notification type for which
   *                           to retrieve the OpenDS notification type.
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

