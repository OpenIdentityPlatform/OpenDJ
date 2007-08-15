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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.messages.JebMessages.ERR_JEB_CREATE_FAIL;
import static org.opends.messages.JebMessages.
     ERR_JEB_DIRECTORY_INVALID;
import static org.opends.messages.JebMessages.ERR_JEB_REMOVE_FAIL;

import java.io.File;
import java.io.FilenameFilter;

/**
 * A singleton class to manage the life-cycle of a JE database environment.
 */
public class EnvManager
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


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
               name.equals("je.lck");
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
        Message message = ERR_JEB_DIRECTORY_INVALID.get(homeDir);
        throw new JebException(message);
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        Message message = ERR_JEB_CREATE_FAIL.get(e.getMessage());
        throw new JebException(message, e);
      }
    }
  }

  /**
   * Deletes all the data files associated with the environment.
   * The environment must not be open.
   *
   * @param homeDir The backend home directory
   * @throws JebException If an error occurs in the JE backend.
   */
  public static void removeFiles(String homeDir)
       throws JebException
  {
    File dir = new File(homeDir);
    if (!dir.isDirectory())
    {
      Message message = ERR_JEB_DIRECTORY_INVALID.get(homeDir);
      throw new JebException(message);
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_JEB_REMOVE_FAIL.get(e.getMessage());
      throw new JebException(message, e);
    }
  }

}
