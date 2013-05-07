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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */

package org.opends.quicksetup;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Date;
import java.text.DateFormat;

/**
 * Utilities for setting up QuickSetup application log.
 */
public class QuickSetupLog {

  static private File logFile = null;
  static private FileHandler fileHandler = null;

  /**
   * Creates a new file handler for writing log messages to the file indicated
   * by <code>file</code>.
   * @param file log file to which log messages will be written
   * @throws IOException if something goes wrong
   */
  static public void initLogFileHandler(File file) throws IOException {
    if (!isInitialized()) {
      logFile = file;
      fileHandler = new FileHandler(logFile.getCanonicalPath());
      fileHandler.setFormatter(new SimpleFormatter());
      Logger logger = Logger.getLogger("org.opends");
      logger.addHandler(fileHandler);
      disableConsoleLogging();
      logger = Logger.getLogger("org.opends.quicksetup");
      logger.log(Level.INFO, getInitialLogRecord());
    }
  }

  /**
   * Creates a new file handler for writing log messages of a given package
   * to the file indicated by <code>file</code>.
   * @param file log file to which log messages will be written.
   * @param packageName the name of the package of the classes that generate
   * log messages.
   * @throws IOException if something goes wrong
   */
  static public void initLogFileHandler(File file, String packageName)
  throws IOException {
    initLogFileHandler(file);
    Logger logger = Logger.getLogger(packageName);
    logger.addHandler(fileHandler);
    if (disableLoggingToConsole())
    {
      logger.setUseParentHandlers(false); // disable logging to console
    }
  }

  /**
   * Prevents messages written to loggers from appearing in the console
   * output.
   */
  static public void disableConsoleLogging() {
    if (disableLoggingToConsole())
    {
      Logger logger = Logger.getLogger("org.opends");
      logger.setUseParentHandlers(false);
    }
  }

  /**
   * Gets the name of the log file.
   * @return File representing the log file
   */
  static public File getLogFile() {
    return logFile;
  }

  /**
   * Indicates whether or not the log file has been initialized.
   * @return true when the log file has been initialized
   */
  static public boolean isInitialized() {
    return logFile != null;
  }

  static private String getInitialLogRecord() {
    // Note; currently the logs are not internationalized.
    return "QuickSetup application launched " +
        DateFormat.getDateTimeInstance(DateFormat.LONG,
            DateFormat.LONG).format(new Date());
  }

  private static boolean disableLoggingToConsole()
  {
    return !"true".equals(System.getenv("OPENDJ_LOG_TO_STDOUT"));
  }
}
