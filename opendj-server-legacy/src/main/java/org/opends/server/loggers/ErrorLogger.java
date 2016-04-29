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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.messages.Severity;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.server.config.meta.ErrorLogPublisherCfgDefn;
import org.forgerock.opendj.server.config.server.ErrorLogPublisherCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.backends.task.Task;
import org.opends.server.core.ServerContext;

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

  /** The constructor for this class. */
  private ErrorLogger()
  {
    super((Class) ErrorLogPublisher.class,
        ERR_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS);
  }

  @Override
  protected ClassPropertyDefinition getJavaClassPropertyDefinition()
  {
    return ErrorLogPublisherCfgDefn.getInstance()
        .getJavaClassPropertyDefinition();
  }

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
  public static void log(String category, Severity severity, LocalizableMessage message, Throwable exception)
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
    for (ErrorLogPublisher<ErrorLogPublisherCfg> publisher : loggerStorage.getLogPublishers())
    {
      if (publisher.isEnabledFor(category, severity))
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public final synchronized void addLogPublisher(final ErrorLogPublisher<ErrorLogPublisherCfg> publisher)
  {
    loggerStorage.addLogPublisher(publisher);
    adjustJulLevel();
  }

  @Override
  public final synchronized boolean removeLogPublisher(final ErrorLogPublisher<ErrorLogPublisherCfg> publisher)
  {
    final boolean removed = loggerStorage.removeLogPublisher(publisher);
    adjustJulLevel();
    return removed;
  }

  @Override
  public final synchronized void removeAllLogPublishers()
  {
    loggerStorage.removeAllLogPublishers();
    adjustJulLevel();
  }

  private void adjustJulLevel()
  {
    final ServerContext serverContext = getServerContext();
    if (serverContext != null && serverContext.getLoggerConfigManager() != null)
    {
      serverContext.getLoggerConfigManager().adjustJulLevel();
    }
  }
}
