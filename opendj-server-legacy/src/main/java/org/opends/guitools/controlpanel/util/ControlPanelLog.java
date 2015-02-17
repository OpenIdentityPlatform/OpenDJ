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
package org.opends.guitools.controlpanel.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.Date;
import java.text.DateFormat;

import org.opends.server.loggers.JDKLogging;

/**
 * Utilities for setting up Control Panel application log.
 */
public class ControlPanelLog
{
  private static String[] packages = {
    "org.opends"
  };
  private static File logFile;
  private static FileHandler fileHandler;

  /**
   * Creates a new file handler for writing log messages to the file indicated
   * by <code>file</code>.
   * @param file log file to which log messages will be written
   * @throws IOException if something goes wrong
   */
  public static void initLogFileHandler(File file) throws IOException {
    if (!isInitialized())
    {
      logFile = file;
      fileHandler = new FileHandler(logFile.getCanonicalPath());
      fileHandler.setFormatter(JDKLogging.getFormatter());
      boolean initialLogDone = false;
      for (String root : JDKLogging.getOpendDJLoggingRoots())
      {
        Logger logger = Logger.getLogger(root);
        if (disableLoggingToConsole())
        {
          logger.setUseParentHandlers(false); // disable logging to console
        }
        logger.addHandler(fileHandler);
        if (!initialLogDone) {
          logger.info(getInitialLogRecord());
          initialLogDone = true;
        }
      }
    }
  }

  /**
   * Writes messages under a given package in the file handler defined when
   * calling initLogFileHandler.  Note that initLogFileHandler should be called
   * before calling this method.
   * @param packageName the package name.
   * @throws IOException if something goes wrong
   */
  public static void initPackage(String packageName) throws IOException {
    Logger logger = Logger.getLogger(packageName);
    if (disableLoggingToConsole())
    {
      logger.setUseParentHandlers(false); // disable logging to console
    }
    logger.addHandler(fileHandler);
    logger.info(getInitialLogRecord());
  }

  /**
   * Gets the name of the log file.
   * @return File representing the log file
   */
  public static File getLogFile() {
    return logFile;
  }

  /**
   * Indicates whether or not the log file has been initialized.
   * @return true when the log file has been initialized
   */
  public static boolean isInitialized() {
    return logFile != null;
  }

  private static String getInitialLogRecord() {
    StringBuilder sb = new StringBuilder()
            .append("Application launched " +
                    DateFormat.getDateTimeInstance(DateFormat.LONG,
                                                   DateFormat.LONG).
                            format(new Date()));
    return sb.toString();
  }

  private static boolean disableLoggingToConsole()
  {
    return !"true".equals(System.getenv("OPENDJ_LOG_TO_STDOUT"));
  }
}

