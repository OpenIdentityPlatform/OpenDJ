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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.PrintStream;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Utility class for java.util.logging support. */
public class JDKLogging
{
  /** Root packages that contains all OpenDJ related classes. */
  private static final String[] LOGGING_ROOTS = new String[] { "org.opends", "org.forgerock.opendj"};

  /** Disable java.util.logging. */
  public static void disableLogging()
  {
    LogManager.getLogManager().reset();
    Logger.getLogger("").setLevel(Level.OFF);
  }

  /**
   * Enable JDK logging to stderr at provided level for OpenDJ classes.
   * <p>
   * Error and warning messages will be printed on stderr, other messages will be printed on stdout.
   */
  public static void enableVerboseConsoleLoggingForOpenDJ()
  {
    enableConsoleLoggingForOpenDJ(Level.ALL, System.out, System.err);
  }

  /**
   * Enable JDK logging for OpenDJ tool.
   * <p>
   * Error and warning messages will be printed on stderr, other messages will be printed on stdout.
   * This method should only be used by external tool classes.
   */
  public static void enableConsoleLoggingForOpenDJTool()
  {
    enableConsoleLoggingForOpenDJ(Level.FINE, System.out, System.err);
  }

  /**
   * Enable JDK logging in provided {@link PrintStream} for OpenDJ tool.
   * <p>
   * All messages will be printed on the provided {@link PrintStream}.
   * This method should only be used by external tool classes.
   *
   * @param stream
   *          The stream to use to print messages.
   */
  public static void enableLoggingForOpenDJTool(final PrintStream stream)
  {
    enableConsoleLoggingForOpenDJ(Level.FINE, stream, stream);
  }

  /**
   * Enable JDK logging at provided {@link Level} in provided {@link PrintStream} for OpenDJ classes.
   *
   * @param level
   *          The level to log.
   * @param out
   *          The stream to use to print messages from {@link Level#FINEST} and {@link Level#INFO} included.
   * @param err
   *          The stream to use to print {@link Level#SEVERE} and {@link Level#WARNING} messages.
   */
  private static void enableConsoleLoggingForOpenDJ(final Level level, final PrintStream out, final PrintStream err)
  {
    LogManager.getLogManager().reset();
    Handler handler = new OpenDJHandler(out, err);
    handler.setFormatter(getFormatter());
    handler.setLevel(level);
    for (String loggingRoot : LOGGING_ROOTS)
    {
      Logger logger = Logger.getLogger(loggingRoot);
      logger.setLevel(level);
      logger.addHandler(handler);
    }
  }

  /** Custom handler to log to either stdout or stderr depending on the log level. */
  private static final class OpenDJHandler extends Handler
  {
    private final PrintStream out;
    private final PrintStream err;

    private OpenDJHandler(final PrintStream out, final PrintStream err)
    {
      this.out = out;
      this.err = err;
    }

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
          err.write(message.getBytes());
        }
        else
        {
          out.write(message.getBytes());
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
