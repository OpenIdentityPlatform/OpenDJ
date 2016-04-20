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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.ArrayList;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.DirectoryThread;
import org.opends.server.types.Entry;

/**
 * This thread is spawned off at the time of file rotation to
 * execute specific actions such as compression, encryption,
 * and signing of the log files.
 */
public class RotationActionThread extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private ArrayList<ActionType> actions;
  private String filename;
  private Entry configEntry;

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
            Entry configEntry)
  {
    super("Logger Rotation Action Thread");

    this.filename = filename;
    this.actions = actions;
    this.configEntry = configEntry;
  }

  @Override
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
          case ENCRYPT:
            break;
          default:
            System.err.println("Invalid post rollover action:" + at);
            break;
        }
        if(action != null && !action.execute())
        {
          System.err.println("Post rotation action failed.");
        }
      }
    } catch(Exception e)
    {
      logger.traceException(e);
    }
  }
}
