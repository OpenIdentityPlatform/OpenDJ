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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.Date;
import java.text.DateFormat;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.loggers.JDKLogging;

/** This class represents a temporary log file which should be usually deleted if linked operation succeeded. */
public class TempLogFile
{
  private static final LocalizedLogger localizedLogger = LocalizedLogger.getLoggerForThisClass();

  private static final String OPENDS_LOGGER_NAME = "org.opends";

  /**
   * Creates a new temporary log file.
   * <p>
   * Log file will be generated in the OS temporary directory and its name will have
   * the following pattern: prefix-[RANDOM_NUMBER_STRING].log
   *
   * @param prefix
   *          log file prefix to which log messages will be written.
   * @return a new temporary log file.
   */
  public static TempLogFile newTempLogFile(final String prefix)
  {
    try
    {
      return new TempLogFile(File.createTempFile(prefix, ".log"));
    }
    catch (final IOException e)
    {
      localizedLogger.error(LocalizableMessage.raw("Unable to create temp log file because: " + e.getMessage()), e);
      return new TempLogFile();
    }
  }

  /** Prevents messages written to loggers from appearing in the console output. */
  private static void disableConsoleLogging(final Logger logger)
  {
    if (!"true".equalsIgnoreCase(System.getenv("OPENDJ_LOG_TO_STDOUT")))
    {
      logger.setUseParentHandlers(false);
    }
  }

  private final File logFile;
  private final FileHandler fileHandler;

  private TempLogFile()
  {
    this.logFile = null;
    this.fileHandler = null;
  }

  private TempLogFile(final File file) throws IOException
  {
    logFile = file;
    fileHandler = new FileHandler(logFile.getCanonicalPath());
    fileHandler.setFormatter(JDKLogging.getFormatter());
    final Logger parentLogger = Logger.getLogger(OPENDS_LOGGER_NAME);
    parentLogger.addHandler(fileHandler);
    disableConsoleLogging(parentLogger);
    final Logger logger = Logger.getLogger(getClass().getPackage().getName());
    logger.info("QuickSetup application launched " + DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
          .format(new Date()));
  }

  /**
   * Gets the name of the log file.
   *
   * @return File representing the log file
   */
  public File getLogFile()
  {
    return logFile;
  }

  /** Closes the log file handler and delete the temp log file . */
  public void deleteLogFileAfterSuccess()
  {
    if (isEnabled())
    {
      fileHandler.close();
      logFile.delete();
    }
  }

  /**
   * Return {@code true} if a temp log file has been created and could be used to log messages.
   * @return {@code true} if a temp log file has been created and could be used to log messages.
   */
  public boolean isEnabled()
  {
    return logFile != null;
  }

  /**
   * Return the absolute path of the temp log file.
   * @return the absolute path of the temp log file.
   */
  public String getPath()
  {
    return logFile.getAbsolutePath();
  }
}
