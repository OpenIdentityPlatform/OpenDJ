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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */

package org.opends.server.tools.upgrade;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;

import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ReturnCode;

import static org.opends.messages.ToolMessages.ERR_UPGRADE_INVALID_LOG_FILE;

/**
 * Creates a historical log about the upgrade.
 * <br>
 * If file does not exist an attempt will be made to create it.
 */
class UpgradeLog
{
  private static File logFile;
  private static final String UPGRADE_LOG_NAME = "upgrade.log";

  static void createLogFile()
  {
    if (logFile == null)
    {
      logFile = Paths.get(UpgradeUtils.getInstancePath(), Installation.LOGS_PATH_RELATIVE, UPGRADE_LOG_NAME).toFile();
    }
  }

  static PrintStream getPrintStream() throws ClientException
  {
    try
    {
      return new PrintStream(new FileOutputStream(logFile, true));
    }
    catch (FileNotFoundException e)
    {
      throw new ClientException(ReturnCode.ERROR_UNEXPECTED, ERR_UPGRADE_INVALID_LOG_FILE.get(e.getMessage()));
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
