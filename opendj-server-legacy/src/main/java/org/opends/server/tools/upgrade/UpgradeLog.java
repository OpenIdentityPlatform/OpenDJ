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
 * Portions Copyright 2013-2015 ForgeRock AS.
 */

package org.opends.server.tools.upgrade;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.opends.messages.RuntimeMessages;

import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ReturnCode;

import static org.opends.messages.ToolMessages.ERR_UPGRADE_INVALID_LOG_FILE;
import static com.forgerock.opendj.cli.Utils.LINE_SEPARATOR;

/**
 * Creates a historical log about the upgrade. If file does not exist an attempt
 * will be made to create it.
 */
class UpgradeLog
{
  private static File logFile;
  private static FileHandler fileHandler;
  static final String UPGRADELOGNAME = "upgrade.log";
  static final String LOGDIR = "logs";

  /**
   * Creates a new file handler for writing log messages into
   * {@value #UPGRADELOGNAME} file.
   */
  static void initLogFileHandler()
  {
    final Logger logger = Logger.getLogger(UpgradeLog.class.getPackage().getName());

    final String SPACE = " ";

    if (logFile == null)
    {
      logFile = new File(UpgradeUtils.getInstancePath() + File.separator + Installation.LOGS_PATH_RELATIVE
          + File.separator + UPGRADELOGNAME);
    }
    try
    {
      fileHandler = new FileHandler(logFile.getCanonicalPath(), true);
    }
    catch (IOException e)
    {
      logger.severe(e.getMessage());
    }
    fileHandler.setFormatter(new Formatter()
    {
      /** {@inheritDoc} */
      @Override
      public String format(LogRecord record)
      {
        // Format the log ~like the errors logger.
        StringBuilder sb = new StringBuilder();
        final SimpleDateFormat dateFormat =
            new SimpleDateFormat("[dd/MMM/yyyy:HH:mm:ss Z]");
        sb.append(dateFormat.format(record.getMillis())).append(SPACE);
        sb.append("category=UPGRADE").append(SPACE).append("sq=").append(
            record.getSequenceNumber()).append(SPACE).append("severity=")
            .append(record.getLevel().toString().toUpperCase());
        sb.append(SPACE).append("src=").append(record.getSourceClassName())
            .append(SPACE).append(record.getSourceMethodName()).append(LINE_SEPARATOR);
        sb.append(SPACE).append("msg=").append(record.getMessage())
            .append(LINE_SEPARATOR);
        return sb.toString();
      }
    });
    logger.setLevel(Level.ALL);
    logger.addHandler(fileHandler);

    logger.setUseParentHandlers(false);
    // Log Config info.
    logger.info("**** Upgrade of OpenDJ started ****");
    logger.info(RuntimeMessages.NOTE_INSTALL_DIRECTORY.get(
        UpgradeUtils.getInstallationPath()).toString());
    logger.info(RuntimeMessages.NOTE_INSTANCE_DIRECTORY.get(
        UpgradeUtils.getInstancePath()).toString());
  }

  /**
   * Returns the print stream of the current logger.
   *
   * @return the print stream of the current logger.
   * @throws ClientException
   *           If the file defined by the logger is not found or invalid.
   */
  static PrintStream getPrintStream() throws ClientException
  {
    try
    {
      return new PrintStream(new FileOutputStream(logFile, true));
    }
    catch (FileNotFoundException e)
    {
      throw new ClientException(ReturnCode.ERROR_UNEXPECTED, ERR_UPGRADE_INVALID_LOG_FILE.get(e
          .getMessage()));
    }
  }

  static String getLogFilePath()
  {
    try
    {
      return logFile.getCanonicalPath();
    }
    catch (IOException e)
    {
      return logFile.getPath();
    }
  }
}
