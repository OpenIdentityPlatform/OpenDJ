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
package org.opends.server.loggers;

import java.util.ArrayList;

import org.opends.server.api.DirectoryThread;
import org.opends.server.config.ConfigEntry;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;

/**
 * This thread is spawned off at the time of file rotation to
 * execute specific actions such as compression, encryption,
 * and signing of the log files.
 */
public class RotationActionThread extends DirectoryThread
{

  private ArrayList<ActionType> actions;
  private String filename;
  private ConfigEntry configEntry;

  /**
   * Create the logger thread along with the specified file name,
   * and the rotation actions.
   *
   * @param  filename     The name of the file to be rotated.
   * @param  actions      The set of actions that should be performed when the
   *                      file is rotated.
   * @param  configEntry  The entry that contains the rotation configuration.
   */
  public RotationActionThread(String filename,
            ArrayList<ActionType> actions,
            ConfigEntry configEntry)
  {
    super("Logger Rotation Action Thread");

    this.filename = filename;
    this.actions = actions;
    this.configEntry = configEntry;
  }

  /**
   * The run method of the thread.
   */
  public void run()
  {
    try
    {
      for(ActionType at : actions)
      {
        PostRotationAction action = null;
        switch(at)
        {
          case GZIP_COMPRESS:
            String gzipFile = filename + ".gz";
            action = new GZIPAction(filename, gzipFile, true);
            break;
          case ZIP_COMPRESS:
            String zipFile = filename + ".zip";
            action = new ZIPAction(filename, zipFile, true);
            break;
          case SIGN:
            String alias = RotationConfigUtil.getCertificateAlias(configEntry);
            action = new SignatureAction(filename, alias);
            break;
          case ENCRYPT:
            String encFile = filename + ".enc";
            String certAlias =
              RotationConfigUtil.getCertificateAlias(configEntry);
            // FIXME - make the encryption algorithm configurable.
            action = new EncryptAction(filename, encFile, false, certAlias,
                                       "RSA");
            break;
          default:
            System.err.println("Invalid post rollover action:" + at);
            break;
        }
        if(action != null)
        {
          boolean val = action.execute();
          if(val == false)
          {
            System.err.println("Post rotation action failed.");
          }
        }
      }
    } catch(Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }

}

