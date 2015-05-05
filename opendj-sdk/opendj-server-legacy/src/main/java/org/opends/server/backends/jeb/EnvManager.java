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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;
import org.forgerock.i18n.LocalizableMessage;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.BackendMessages.*;

import java.io.File;
import java.io.FilenameFilter;

/**
 * A singleton class to manage the life-cycle of a JE database environment.
 */
public class EnvManager
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * A filename filter to match all kinds of JE files.
   */
  private static final FilenameFilter jeAllFilesFilter;

  static
  {
    // A filename filter to match all kinds of JE files.
    // JE has a com.sleepycat.je.log.JEFileFilter that would be useful
    // here but is not public.
    jeAllFilesFilter = new FilenameFilter()
    {
      public boolean accept(File d, String name)
      {
        return name.endsWith(".jdb") ||
               name.endsWith(".del") ||
               name.startsWith("je.");
      }
    };
  }

  /**
   * Creates the environment home directory, deleting any existing data files
   * if the directory already exists.
   * The environment must not be open.
   *
   * @param homeDir The backend home directory.
   * @throws JebException If an error occurs in the JE backend.
   */
  public static void createHomeDir(String homeDir)
       throws JebException
  {
    File dir = new File(homeDir);

    if (dir.exists())
    {
      if (!dir.isDirectory())
      {
        throw new JebException(ERR_DIRECTORY_INVALID.get(homeDir));
      }
      removeFiles(homeDir);
    }
    else
    {
      try
      {
        dir.mkdir();
      }
      catch (Exception e)
      {
        logger.traceException(e);
        throw new JebException(ERR_CREATE_FAIL.get(e.getMessage()), e);
      }
    }
  }

  /**
   * Deletes all the data files associated with the environment.
   * The environment must not be open.
   *
   * @param homeDir The backend home directory
   * @throws JebException If an error occurs in the JE backend or if the
   * specified home directory does not exist.
   */
  public static void removeFiles(String homeDir)
       throws JebException
  {
    File dir = new File(homeDir);
    if (!dir.exists())
    {
      LocalizableMessage message = ERR_DIRECTORY_DOES_NOT_EXIST.get(homeDir);
      throw new JebException(message);
    }
    if (!dir.isDirectory())
    {
      throw new JebException(ERR_DIRECTORY_INVALID.get(homeDir));
    }

    try
    {
      File[] jdbFiles = dir.listFiles(jeAllFilesFilter);
      for (File f : jdbFiles)
      {
        f.delete();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new JebException(ERR_REMOVE_FAIL.get(e.getMessage()), e);
    }
  }

}
