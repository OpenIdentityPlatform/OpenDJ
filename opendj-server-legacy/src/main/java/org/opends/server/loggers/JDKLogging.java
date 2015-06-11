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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Utility class for java.util.logging support.
 */
public class JDKLogging
{
  /** Root packages that contains all OpenDJ related classes. */
  private static final String[] LOGGING_ROOTS = new String[] { "org.opends", "org.forgerock.opendj"};

  /**
   * Disable java.util.logging.
   */
  public static void disableLogging()
  {
    LogManager.getLogManager().reset();
    Logger.getLogger("").setLevel(Level.OFF);
  }

  /**
   * Enable JDK logging to stderr at provided level for OpenDJ classes.
   *
   * @param level
   *          The level to log.
   */
  public static void enableConsoleLoggingForOpenDJ(Level level)
  {
    LogManager.getLogManager().reset();
    Handler handler = new OpenDJHandler();
    handler.setFormatter(getFormatter());
    handler.setLevel(level);
    for (String loggingRoot : LOGGING_ROOTS)
    {
      Logger logger = Logger.getLogger(loggingRoot);
      logger.setLevel(level);
      logger.addHandler(handler);
    }
  }

  private static final class OpenDJHandler extends Handler
  {
    @Override
    public void publish(LogRecord record)
    {
      if (getFormatter() == null)
      {
        setFormatter(new SimpleFormatter());
      }

      try
      {
        String message = getFormatter().format(record);
        if (record.getLevel().intValue() >= Level.WARNING.intValue())
        {
          System.err.write(message.getBytes());
        }
        else
        {
          System.out.write(message.getBytes());
        }
      }
      catch (Exception exception)
      {
        reportError(null, exception, ErrorManager.FORMAT_FAILURE);
        return;
      }
    }

    @Override
    public void close() throws SecurityException
    {
    }

    @Override
    public void flush()
    {
      System.out.flush();
      System.err.flush();
    }
  }

  /**
   * Get a formatter.
   *
   * @return a formatter for loggers
   */
  public static Formatter getFormatter()
  {
    return new JDKLoggingFormater();
  }

  /**
   * Returns the packages to be used as root for logging.
   * <p>
   * This package covers all OpenDJ classes.
   *
   * @return the root packages to log
   */
  public static String[] getOpendDJLoggingRoots() {
    return LOGGING_ROOTS;
  }

}
