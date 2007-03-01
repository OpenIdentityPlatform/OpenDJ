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
package org.opends.server.loggers;



import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.ErrorLogger;
import org.opends.server.messages.MessageHandler;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;



/**
 * This class defines the wrapper that will invoke all registered error loggers
 * for each type of request received or response sent.
 */
public class Error
{
  // The set of error loggers that have been registered with the server.  It
  // will initially be empty.
  private static CopyOnWriteArrayList<ErrorLogger> errorLoggers =
       new CopyOnWriteArrayList<ErrorLogger>();

  // A mutex that will be used to provide threadsafe error to methods changing
  // the set of defined loggers.
  private static ReentrantLock loggerMutex = new ReentrantLock();



  /**
   * Adds a new error logger to which error messages should be sent.
   *
   * @param  logger  The error logger to which messages should be sent.
   */
  public static void addErrorLogger(ErrorLogger logger)
  {
    loggerMutex.lock();

    try
    {
      for (ErrorLogger l : errorLoggers)
      {
        if (l.equals(logger))
        {
          return;
        }
      }

      errorLoggers.add(logger);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Removes the provided error logger so it will no longer be sent any new
   * error messages.
   *
   * @param  logger  The error logger to remove from the set.
   */
  public static void removeErrorLogger(ErrorLogger logger)
  {
    loggerMutex.lock();

    try
    {
      errorLoggers.remove(logger);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Removes all active error loggers so that no error messages will be sent
   * anywhere.
   *
   * @param  closeLoggers  Indicates whether the loggers should be closed as
   *                       they are unregistered.
   */
  public static void removeAllErrorLoggers(boolean closeLoggers)
  {
    loggerMutex.lock();

    try
    {
      if (closeLoggers)
      {
        ErrorLogger[] loggers = new ErrorLogger[errorLoggers.size()];
        errorLoggers.toArray(loggers);

        errorLoggers.clear();

        for (ErrorLogger logger : loggers)
        {
          logger.closeErrorLogger();
        }
      }
      else
      {
        errorLoggers.clear();
      }
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine whether to
   *                   actually log this message.
   * @param  severity  The severity that may be used to determine whether to
   *                   actually log this message.
   * @param  errorID   The error ID that uniquely identifies the provided format
   *                   string.
   */
  public static void logError(ErrorLogCategory category,
                              ErrorLogSeverity severity, int errorID)
  {
    String message = MessageHandler.getMessage(errorID);

    for (ErrorLogger l : errorLoggers)
    {
      l.logError(category, severity, message, errorID);
    }
  }



  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine whether to
   *                   actually log this message.
   * @param  severity  The severity that may be used to determine whether to
   *                   actually log this message.
   * @param  errorID   The error ID that uniquely identifies the provided format
   *                   string.
   * @param  args      The set of arguments to use for the provided format
   *                   string.
   */
  public static void logError(ErrorLogCategory category,
                              ErrorLogSeverity severity, int errorID,
                              Object... args)
  {
    String message = MessageHandler.getMessage(errorID, args);

    for (ErrorLogger l : errorLoggers)
    {
      l.logError(category, severity, message, errorID);
    }
  }



  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine whether to
   *                   actually log this message.
   * @param  severity  The severity that may be used to determine whether to
   *                   actually log this message.
   * @param  message   The message to be logged.
   * @param  errorID   The error ID that uniquely identifies the format string
   *                   used to generate the provided message.
   */
  public static void logError(ErrorLogCategory category,
                              ErrorLogSeverity severity, String message,
                              int errorID)
  {
    for (ErrorLogger l : errorLoggers)
    {
      l.logError(category, severity, message, errorID);
    }
  }
}

