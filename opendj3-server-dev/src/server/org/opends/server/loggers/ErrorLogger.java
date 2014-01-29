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
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.messages.Severity;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.std.meta.ErrorLogPublisherCfgDefn;
import org.opends.server.admin.std.server.ErrorLogPublisherCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.backends.task.Task;

/**
 * This class defines the wrapper that will invoke all registered error loggers
 * for each type of request received or response sent. If no error log
 * publishers are registered, messages will be directed to standard out.
 */
public class ErrorLogger extends AbstractLogger
    <ErrorLogPublisher<ErrorLogPublisherCfg>, ErrorLogPublisherCfg>
{

  private static LoggerStorage
      <ErrorLogPublisher<ErrorLogPublisherCfg>, ErrorLogPublisherCfg>
      loggerStorage = new LoggerStorage
      <ErrorLogPublisher<ErrorLogPublisherCfg>, ErrorLogPublisherCfg>();

  /** The singleton instance of this class for configuration purposes. */
  private static final ErrorLogger instance = new ErrorLogger();

  /**
   * Retrieve the singleton instance of this class.
   *
   * @return The singleton instance of this logger.
   */
  public static ErrorLogger getInstance()
  {
    return instance;
  }

  /**
   * The constructor for this class.
   */
  public ErrorLogger()
  {
    super((Class) ErrorLogPublisher.class,
        ERR_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS);
  }

  /** {@inheritDoc} */
  @Override
  protected ClassPropertyDefinition getJavaClassPropertyDefinition()
  {
    return ErrorLogPublisherCfgDefn.getInstance()
        .getJavaClassPropertyDefinition();
  }

  /** {@inheritDoc} */
  @Override
  protected LoggerStorage<ErrorLogPublisher<ErrorLogPublisherCfg>,
      ErrorLogPublisherCfg> getStorage()
  {
    return loggerStorage;
  }

  /**
   * Add an error log publisher to the error logger.
   *
   * @param publisher The error log publisher to add.
   */
  public synchronized static void addErrorLogPublisher(
      ErrorLogPublisher publisher)
  {
    loggerStorage.addLogPublisher(publisher);
  }

  /**
   * Remove an error log publisher from the error logger.
   *
   * @param publisher The error log publisher to remove.
   * @return True if the error log publisher is removed or false otherwise.
   */
  public synchronized static boolean removeErrorLogPublisher(
      ErrorLogPublisher publisher)
  {
    return loggerStorage.removeLogPublisher(publisher);
  }

  /**
   * Removes all existing error log publishers from the logger.
   */
  public synchronized static void removeAllErrorLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
  }

  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  message   The message to be logged.
   */
  // TODO : remove
  public static void logError(LocalizableMessage message)
  {
    log("category", Severity.SEVERE_ERROR, message, null);
  }

  /**
   * Writes a message to the error log using the provided information.
   *
   * @param category
   *          The category of the message.
   * @param severity
   *          The severity of the message.
   * @param message
   *          The message to be logged.
   * @param exception
   *          The exception to be logged. May be {@code null}.
   */
  public static void log(String category, Severity severity, LocalizableMessage message, Throwable exception)
  {
    for (ErrorLogPublisher publisher : loggerStorage.getLogPublishers())
    {
      publisher.log(category, severity, message, exception);
    }

    if (Thread.currentThread() instanceof DirectoryThread)
    {
      DirectoryThread thread = (DirectoryThread) Thread.currentThread();
      Task task = thread.getAssociatedTask();
      if (task != null)
      {
        task.addLogMessage(severity, message, exception);
      }
    }
  }

  /**
   * Check if logging is enabled for the provided category and severity.
   *
   * @param category
   *            The category of logging event.
   * @param severity
   *            The severity of logging event.
   * @return {@code true} if logger is enabled
   */
  public static boolean isEnabledFor(String category, Severity severity)
  {
    if (Thread.currentThread() instanceof DirectoryThread)
    {
      DirectoryThread thread = (DirectoryThread) Thread.currentThread();
      Task task = thread.getAssociatedTask();
      if (task != null)
      {
        return true;
      }
    }
    for (ErrorLogPublisher publisher : loggerStorage.getLogPublishers())
    {
      if (publisher.isEnabledFor(category, severity))
      {
        return true;
      }
    }
    return false;
  }

}
