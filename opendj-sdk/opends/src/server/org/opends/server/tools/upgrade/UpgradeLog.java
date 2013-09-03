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
 *      Copyright 2013 ForgeRock AS
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
import org.opends.server.tools.ClientException;

import static org.opends.messages.ToolMessages.ERR_UPGRADE_INVALID_LOG_FILE;

/**
 * Creates a historical log about the upgrade. If file does not exist an attempt
 * will be made to create it.
 */
class UpgradeLog
{
  static private File logFile = null;
  static private FileHandler fileHandler = null;
  final static String UPGRADELOGNAME = "upgrade.log";

  /**
   * Creates a new file handler for writing log messages into
   * {@value #UPGRADELOGNAME} file.
   */
  static void initLogFileHandler()
  {
    final Logger logger = Logger.getLogger(UpgradeCli.class.getName());

    final String SPACE = " ";

    if (logFile == null)
    {
      logFile =
          new File(new StringBuilder(UpgradeUtils.getInstallationPath())
              .append(File.separator).append(UPGRADELOGNAME).toString());
    }
    try
    {
      fileHandler = new FileHandler(logFile.getCanonicalPath(), true);
    }
    catch (IOException e)
    {
      logger.log(Level.SEVERE, e.getMessage());
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
            .append(SPACE).append(record.getSourceMethodName()).append("\n");
        sb.append(SPACE).append("msg=").append(record.getMessage())
            .append("\n");
        return sb.toString();
      }
    });
    logger.setLevel(Level.CONFIG);
    logger.addHandler(fileHandler);

    logger.setUseParentHandlers(false);
    // Log Config info.
    logger.log(Level.CONFIG, "**** Upgrade of OpenDJ started ****");
    logger.log(Level.CONFIG, RuntimeMessages.NOTE_INSTALL_DIRECTORY.get(
        UpgradeUtils.getInstallationPath()).toString());
    logger.log(Level.CONFIG, RuntimeMessages.NOTE_INSTANCE_DIRECTORY.get(
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
      throw new ClientException(1, ERR_UPGRADE_INVALID_LOG_FILE.get(e
          .getMessage()));
    }
  }
}
