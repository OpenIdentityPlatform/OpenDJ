/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 ForgeRock AS.
 */

package com.forgerock.openidm.pwsync;

import org.opends.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.IOException;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AccountStatusNotificationHandlerCfg;
import com.forgerock.openidm.pwsync.server.OpenidmAccountStatusNotificationHandlerCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.config.ConfigException;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.types.AccountStatusNotificationProperty.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an account status notification handler that captures
 * information about account status notifications and forward them
 * to OpenIDM. The 2 events of interest are password reset and password
 * change, which will convey the new clear-text password.
 *  */
public class OpenidmAccountStatusNotificationHandler
       extends
          AccountStatusNotificationHandler
          <OpenidmAccountStatusNotificationHandlerCfg>
       implements
          ConfigurationChangeListener
          <OpenidmAccountStatusNotificationHandlerCfg>
{
  // The current configuration for this account status notification handler.
  private OpenidmAccountStatusNotificationHandlerCfg currentConfig;

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private static final byte PWD_CHANGED = 1;
  private static final byte PWD_RESET = 2;
  
    //The name of the logfile that the update thread uses to process change
  //records. Defaults to "logs/referint", but can be changed in the
  //configuration.
  private String logFileName = null;

  //The File class that logfile corresponds to.
  private File logFile;

  /**
   * {@inheritDoc}
   */
  public void initializeStatusNotificationHandler(
      OpenidmAccountStatusNotificationHandlerCfg configuration
      )
      throws ConfigException, InitializationException
  {
    currentConfig = configuration;
    currentConfig.addOpenidmChangeListener(this);

    // Read configuration, check and initialize things here.
    logFileName = configuration.getLogFile();
    setUpLogFile(logFileName);
  }



  /**
   * {@inheritDoc}
   */
  public void handleStatusNotification(
                   AccountStatusNotification notification)
  {
    OpenidmAccountStatusNotificationHandlerCfg config = currentConfig;
    List<String> newPasswords = null;
    
    HashMap<String, List<String>> returnedData = new HashMap<String, List<String>>();
    Byte passwordEvent = 0;
    
    String userDN = String.valueOf(notification.getUserDN());
    Entry userEntry = notification.getUserEntry();
    Set<AttributeType> notificationAttrs = config.getAttributeType();
    for (AttributeType t : notificationAttrs)
    {
      List<Attribute> attrList = userEntry.getAttribute(t);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          ArrayList<String> attrVals = new ArrayList<String>();
          for (AttributeValue v : a)
          {
            if (debugEnabled())
            {
              TRACER.debugInfo("Adding end user attribute value " +
                               v.getValue().toString() + " from attr " +
                               a.getNameWithOptions() + "to notification");
            }

            // Add the value of this attribute to the Notif message
            attrVals.add(v.getValue().toString());
          }
          returnedData.put(a.getName().toString(), attrVals);
        }
      }
    }

    switch (notification.getNotificationType())
    {
      case PASSWORD_CHANGED:
        // Build the password changed message
        newPasswords = 
            notification.getNotificationProperties().get(NEW_PASSWORD);
        passwordEvent = PWD_CHANGED;
    
        break;
      case PASSWORD_RESET:
        // Build the password reset message
        newPasswords = 
            notification.getNotificationProperties().get(NEW_PASSWORD);
        passwordEvent = PWD_RESET;
        break;
      default:
        // We are not interest by other events, just return
        return;     
    }

    // Process the notification
    ProcessOpenIDMNotification(passwordEvent, userDN,
        newPasswords, returnedData);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(
                      AccountStatusNotificationHandlerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    OpenidmAccountStatusNotificationHandlerCfg config =
         (OpenidmAccountStatusNotificationHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      OpenidmAccountStatusNotificationHandlerCfg configuration,
      List<Message> unacceptableReasons
      )
  {
    boolean isAcceptable = true;
    
    // If additional parameters are added to the config, they should be
    // checked here.
    
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
      OpenidmAccountStatusNotificationHandlerCfg configuration,
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
      OpenidmAccountStatusNotificationHandlerCfg configuration
      )
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    Boolean adminActionRequired = false;
    //User is not allowed to change the logfile name, append a message that the
    //server needs restarting for change to take effect.
    String newLogFileName=configuration.getLogFile();
    if(!logFileName.equals(newLogFileName))
    {
      adminActionRequired=true;
      messages.add(
           OpenidmAccountStatusNotificationHandlerMessages.
          INFO_OPENIDM_PWSYNC_LOGFILE_CHANGE_REQUIRES_RESTART.get(logFileName,
                newLogFileName));
    }
   

    currentConfig = configuration;
    return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
        messages);
  }
  
  /**
   *
   * @return the current configuration of the plugin 
   */
  public OpenidmAccountStatusNotificationHandlerCfg getCurrentConfiguration()
  {
     return currentConfig;
  }
  
  /**
   * Process a password change notification and sends it to OpenIDM.
   * 
   * @param passwordEvent A byte indicating if it's a change or reset.
   * 
   * @param userDN The user distinguished name as a string.
   * 
   * @param newPasswords the list of new passwords (there may be more than 1).
   * 
   * @param returnedData the additional attributes and values of the user
   *                     entry.
   * 
   */
  void ProcessOpenIDMNotification(byte passwordEvent,
      String userDN,
      List<String>newPasswords,
      Map<String, List<String>>returnedData)
  {
     System.out.println("User " + userDN + " 's password " + 
         (passwordEvent == PWD_CHANGED ? "changed" : "reset") + " to : " +
         newPasswords.toString() + " Additional data: " +
         returnedData.toString());
         
    // For now do nothing
  }
  
 
  /**
   * Sets up the log file that the plugin can write update records to and
   * the background thread can use to read update records from. The specifed
   * log file name is the name to use for the file. If the file exists from
   * a previous run, use it.
   *
   * @param logFileName The name of the file to use, may be absolute.
   *
   * @throws ConfigException If a new file cannot be created if needed.
   *
   */
  private void setUpLogFile(String logFileName)
          throws ConfigException
  {
    this.logFileName=logFileName;
    logFile=getFileForPath(logFileName);

    try
    {
      if(!logFile.exists())
      {
        logFile.createNewFile();
      }
    }
    catch (IOException io)
    {
      throw new ConfigException(
          OpenidmAccountStatusNotificationHandlerMessages.
          ERR_OPENIDM_PWSYNC_CREATE_LOGFILE.get(
                                     io.getMessage()), io);
    }
  }

}

