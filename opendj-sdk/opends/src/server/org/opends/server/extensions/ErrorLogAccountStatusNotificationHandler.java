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



import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an account status notification handler that will write
 * information about status notifications using the Directory Server's error
 * logging facility.
 */
public class ErrorLogAccountStatusNotificationHandler
       extends AccountStatusNotificationHandler
       implements ConfigurableComponent
{



  /**
   * The set of names for the account status notification types that may be
   * logged by this notification handler.
   */
  private static final HashSet<String> NOTIFICATION_TYPE_NAMES =
       new HashSet<String>();



  // The DN of the configuration entry for this notification handler.
  private DN configEntryDN;

  // The set of notification types that should generate log messages.
  private HashSet<AccountStatusNotificationType> notificationTypes;




  static
  {
    for (AccountStatusNotificationType t :
         AccountStatusNotificationType.values())
    {
      NOTIFICATION_TYPE_NAMES.add(t.getNotificationTypeName());
    }
  }



  /**
   * Initializes this account status notification handler based on the
   * information in the provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this account status notification
   *                      handler.
   *
   * @throws  ConfigException  If the provided entry does not contain a valid
   *                           configuration for this account status
   *                           notification handler.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeStatusNotificationHandler(ConfigEntry configEntry)
       throws ConfigException, InitializationException
  {
    configEntryDN = configEntry.getDN();


    // Initialize the set of notification types that should generate log
    // messages.
    int msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_DESCRIPTION_NOTIFICATION_TYPES;
    MultiChoiceConfigAttribute typesStub =
         new MultiChoiceConfigAttribute(ATTR_ACCT_NOTIFICATION_TYPE,
                                        getMessage(msgID), true, true, false,
                                        NOTIFICATION_TYPE_NAMES);
    try
    {
      MultiChoiceConfigAttribute typesAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(typesStub);
      notificationTypes = new HashSet<AccountStatusNotificationType>();
      for (String s : typesAttr.activeValues())
      {
        AccountStatusNotificationType t =
             AccountStatusNotificationType.typeForName(s);
        if (t == null)
        {
          msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_INVALID_TYPE;
          String message = getMessage(msgID, String.valueOf(configEntryDN), s);
          throw new ConfigException(msgID, message);
        }
        else
        {
          notificationTypes.add(t);
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_CANNOT_GET_NOTIFICATION_TYPES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }


    DirectoryServer.registerConfigurableComponent(this);
    DirectoryServer.registerAccountStatusNotificationHandler(configEntryDN,
                                                             this);
  }



  /**
   * Performs any processing that may be necessary in conjunction with the
   * provided account status notification type.
   *
   * @param  notificationType  The type for this account status notification.
   * @param  userDN            The DN of the user entry to which this
   *                           notification applies.
   * @param  messageID         The unique ID for this notification.
   * @param  message           The human-readable message for this notification.
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
   * Indicates whether the provided configuration entry has an
   * acceptable configuration for this component.  If it does not,
   * then detailed information about the problem(s) should be added to
   * the provided list.
   *
   * @param  configEntry          The configuration entry for which to
   *                              make the determination.
   * @param  unacceptableReasons  A list that can be used to hold
   *                              messages about why the provided
   *                              entry does not have an acceptable
   *                              configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an
   *          acceptable configuration for this component, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                      List<String> unacceptableReasons)
  {
    // Initialize the set of notification types that should generate log
    // messages.
    int msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_DESCRIPTION_NOTIFICATION_TYPES;
    MultiChoiceConfigAttribute typesStub =
         new MultiChoiceConfigAttribute(ATTR_ACCT_NOTIFICATION_TYPE,
                                        getMessage(msgID), true, true, false,
                                        NOTIFICATION_TYPE_NAMES);
    try
    {
      MultiChoiceConfigAttribute typesAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(typesStub);
      HashSet<AccountStatusNotificationType> types =
           new HashSet<AccountStatusNotificationType>();
      for (String s : typesAttr.activeValues())
      {
        AccountStatusNotificationType t =
             AccountStatusNotificationType.typeForName(s);
        if (t == null)
        {
          msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_INVALID_TYPE;
          String message = getMessage(msgID, String.valueOf(configEntryDN), s);
          unacceptableReasons.add(message);
          return false;
        }
        else
        {
          types.add(t);
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_CANNOT_GET_NOTIFICATION_TYPES;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  getExceptionMessage(e));
      unacceptableReasons.add(message);
      return false;
    }


    // If we've gotten here, then everything is OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained
   * in the provided entry.  Information about the result of this
   * processing should be added to the provided message list.
   * Information should always be added to this list if a
   * configuration change could not be applied.  If detailed results
   * are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not
   * changed) should also be included.
   *
   * @param  configEntry      The entry containing the new
   *                          configuration to apply for this
   *                          component.
   * @param  detailedResults  Indicates whether detailed information
   *                          about the processing should be added to
   *                          the list.
   *
   * @return  Information about the result of the configuration
   *          update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();


    // Initialize the set of notification types that should generate log
    // messages.
    HashSet<AccountStatusNotificationType> types =
         new HashSet<AccountStatusNotificationType>();
    int msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_DESCRIPTION_NOTIFICATION_TYPES;
    MultiChoiceConfigAttribute typesStub =
         new MultiChoiceConfigAttribute(ATTR_ACCT_NOTIFICATION_TYPE,
                                        getMessage(msgID), true, true, false,
                                        NOTIFICATION_TYPE_NAMES);
    try
    {
      MultiChoiceConfigAttribute typesAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(typesStub);
      for (String s : typesAttr.activeValues())
      {
        AccountStatusNotificationType t =
             AccountStatusNotificationType.typeForName(s);
        if (t == null)
        {
          resultCode = ResultCode.UNWILLING_TO_PERFORM;

          msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_INVALID_TYPE;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN), s));
        }
        else
        {
          types.add(t);
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      resultCode = DirectoryServer.getServerErrorResultCode();

      msgID = MSGID_ERRORLOG_ACCTNOTHANDLER_CANNOT_GET_NOTIFICATION_TYPES;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              getExceptionMessage(e)));
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      this.notificationTypes = types;
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

