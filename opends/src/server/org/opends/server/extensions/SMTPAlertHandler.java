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
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AlertHandlerCfg;
import org.opends.server.admin.std.server.SMTPAlertHandlerCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.EMailMessage;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements a Directory Server alert handler that may be used to
 * send administrative alerts via SMTP.
 */
public class SMTPAlertHandler
       implements AlertHandler<SMTPAlertHandlerCfg>,
                  ConfigurationChangeListener<SMTPAlertHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The current configuration for this alert handler.
  private SMTPAlertHandlerCfg currentConfig;



  /**
   * Creates a new instance of this SMTP alert handler.
   */
  public SMTPAlertHandler()
  {
    super();

    // All initialization should be done in the initializeAlertHandler method.
  }



  /**
   * {@inheritDoc}
   */
  public void initializeAlertHandler(SMTPAlertHandlerCfg configuration)
       throws ConfigException, InitializationException
  {
    // Make sure that the Directory Server is configured with information about
    // at least one SMTP server.
    if ((DirectoryServer.getMailServerPropertySets() == null) ||
        DirectoryServer.getMailServerPropertySets().isEmpty())
    {
      int    msgID   = MSGID_SMTPALERTHANDLER_NO_SMTP_SERVERS;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }

    configuration.addSMTPChangeListener(this);
    currentConfig = configuration;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAcceptable(AlertHandlerCfg configuration,
                                           List<String> unacceptableReasons)
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
                                    int alertID, String alertMessage)
  {
    SMTPAlertHandlerCfg cfg = currentConfig;

    ArrayList<String> recipients =
         new ArrayList<String>(cfg.getRecipientAddress());

    String alertIDStr = String.valueOf(alertID);
    String subject = replaceTokens(cfg.getMessageSubject(), alertType,
                                   alertIDStr, alertMessage);

    String body = replaceTokens(cfg.getMessageBody(), alertType, alertIDStr,
                                alertMessage);

    EMailMessage message = new EMailMessage(cfg.getSenderAddress(), recipients,
                                            subject);

    message.setBody(wrapText(body, 75));

    try
    {
      message.send();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_SMTPALERTHANDLER_ERROR_SENDING_MESSAGE;
      String msg = getMessage(msgID, alertType, alertMessage,
                              stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_WARNING,
               msg, msgID);
    }
  }



  /**
   * Replaces any occurrences of special tokens in the given string with the
   * appropriate value.  Tokens supported include:
   * <UL>
   *   <LI>%%alert-type%% -- Will be replaced with the alert type string</LI>
   *   <LI>%%alert-id%% -- Will be replaced with the alert ID value</LI>
   *   <LI>%%alert-message%% -- Will be replaced with the alert message</LI>
   *   <LI>\n -- Will be replaced with an end-of-line character.
   * </UL>
   *
   * @param  s             The string to be processed.
   * @param  alertType     The string to use to replace the "%%alert-type%%"
   *                       token.
   * @param  alertID       The string to use to replace the "%%alert-id%%"
   *                       token.
   * @param  alertMessage  The string to use to replace the "%%alert-message%%"
   *                       token.
   *
   * @return  A processed version of the provided string.
   */
  private String replaceTokens(String s, String alertType, String alertID,
                               String alertMessage)
  {
    return s.replace("%%alert-type%%", alertType).
             replace("%%alert-id%%", alertID).
             replace("%%alert-message%%", alertMessage).
             replace("\\n", "\r\n");
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      SMTPAlertHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 SMTPAlertHandlerCfg configuration)
  {
    currentConfig = configuration;

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }
}

