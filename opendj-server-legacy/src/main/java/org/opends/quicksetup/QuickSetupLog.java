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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.quicksetup;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.Date;
import java.text.DateFormat;

import org.opends.server.loggers.JDKLogging;

/** Utilities for setting up QuickSetup application log. */
public class QuickSetupLog
{
  private static final String OPENDS_LOGGER_NAME = "org.opends";

  private static File LOG_FILE;
  private static FileHandler FILE_HANDLER;

  /**
   * Creates a new file handler for writing log messages to the file indicated
   * by <code>file</code>.
   *
   * @param file
   *          log file to which log messages will be written
   * @throws IOException
   *           if something goes wrong
   */
  public static void initLogFileHandler(File file) throws IOException
  {
    if (!isInitialized())
    {
      LOG_FILE = file;
      FILE_HANDLER = new FileHandler(LOG_FILE.getCanonicalPath());
      FILE_HANDLER.setFormatter(JDKLogging.getFormatter());
      Logger logger = Logger.getLogger(OPENDS_LOGGER_NAME);
      logger.addHandler(FILE_HANDLER);
      disableConsoleLogging(logger);
      logger = Logger.getLogger(OPENDS_LOGGER_NAME + ".quicksetup");
      logger.info(getInitialLogRecord());
    }
  }

  /**
   * Creates a new file handler for writing log messages of a given package to
   * the file indicated by <code>file</code>.
   *
   * @param file
   *          log file to which log messages will be written.
   * @param packageName
   *          the name of the package of the classes that generate log messages.
   * @throws IOException
   *           if something goes wrong
   */
  public static void initLogFileHandler(File file, String packageName) throws IOException
  {
    initLogFileHandler(file);
    final Logger logger = Logger.getLogger(packageName);
    logger.addHandler(FILE_HANDLER);
    disableConsoleLogging(logger);
  }

  /** Prevents messages written to loggers from appearing in the console output. */
  private static void disableConsoleLogging(final Logger logger)
  {
    if (!"true".equals(System.getenv("OPENDJ_LOG_TO_STDOUT")))
    {
      logger.setUseParentHandlers(false);
    }
  }

  /**
   * Gets the name of the log file.
   *
   * @return File representing the log file
   */
  public static File getLogFile()
  {
    return LOG_FILE;
  }

  /**
   * Indicates whether or not the log file has been initialized.
   *
   * @return true when the log file has been initialized
   */
  public static boolean isInitialized()
  {
    return LOG_FILE != null;
  }

  private static String getInitialLogRecord()
  {
    // Note; currently the logs are not internationalized.
    return "QuickSetup application launched "
        + DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date());
  }
}
