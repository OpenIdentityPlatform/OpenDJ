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
 *      Portions Copyright 2013-2015 ForgeRock AS
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
      logFile = new File(
          UpgradeUtils.getInstallationPath() + File.separator + UPGRADELOGNAME);
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
        StringBuffer sb = new StringBuffer();
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
}
