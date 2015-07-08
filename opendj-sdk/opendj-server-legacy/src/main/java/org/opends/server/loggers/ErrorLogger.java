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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.messages.Severity;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.std.meta.ErrorLogPublisherCfgDefn;
import org.opends.server.admin.std.server.ErrorLogPublisherCfg;
import org.opends.server.api.DirectoryThread;
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
      loggerStorage = new LoggerStorage<>();

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
  private ErrorLogger()
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
  protected Collection<ErrorLogPublisher<ErrorLogPublisherCfg>> getLogPublishers()
  {
    return loggerStorage.getLogPublishers();
  }

  /**
   * Writes a message to the error log using the provided information.
   * <p>
   * Category is defined using either short name (used for classes in well
   * defined packages) or fully qualified classname. Conversion to short name is
   * done automatically when loggers are created, see
   * {@code LoggingCategoryNames} for list of existing short names.
   *
   * @param category
   *          The category of the message, which is either a classname or a
   *          simple category name defined in {@code LoggingCategoryNames}
   *          class.
   * @param severity
   *          The severity of the message.
   * @param message
   *          The message to be logged.
   * @param exception
   *          The exception to be logged. May be {@code null}.
   */
  static void log(String category, Severity severity, LocalizableMessage message, Throwable exception)
  {
    for (ErrorLogPublisher<?> publisher : loggerStorage.getLogPublishers())
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
   *          The category of the logging event, which is either a classname or
   *          a simple category name defined in {@code LoggingCategoryNames}
   *          class.
   * @param severity
   *          The severity of logging event.
   * @return {@code true} if logger is enabled
   */
  static boolean isEnabledFor(String category, Severity severity)
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
    for (ErrorLogPublisher<ErrorLogPublisherCfg> publisher : loggerStorage.getLogPublishers())
    {
      if (publisher.isEnabledFor(category, severity))
      {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized void addLogPublisher(
      ErrorLogPublisher<ErrorLogPublisherCfg> publisher)
  {
    loggerStorage.addLogPublisher(publisher);
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized boolean removeLogPublisher(
      ErrorLogPublisher<ErrorLogPublisherCfg> publisher)
  {
    return loggerStorage.removeLogPublisher(publisher);
  }

  /** {@inheritDoc} */
  @Override
  public final synchronized void removeAllLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
  }

}
