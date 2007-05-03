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
package org.opends.server.replication.plugin;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import org.opends.server.api.DirectoryThread;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

/**
 * Thread that is used to get messages from the Replication servers
 * and replay them in the current server.
 */
public class ListenerThread extends DirectoryThread
{
  private ReplicationDomain listener;
  private boolean shutdown = false;

  /**
   * Constructor for the ListenerThread.
   *
   * @param listener the Plugin that created this thread
   */
  public ListenerThread(ReplicationDomain listener)
  {
     super("Replication Listener thread");
     this.listener = listener;
  }

  /**
   * Shutdown this listener thread.
   */
  public void shutdown()
  {
    shutdown = true;
  }

  /**
   * Run method for this class.
   */
  public void run()
  {
    UpdateMessage msg;

    try
    {
      while (((msg = listener.receive()) != null) && (shutdown == false))
      {
        listener.replay(msg);
      }
    } catch (Exception e)
    {
      /*
       * catch all exceptions happening in listener.receive and listener.replay
       * so that the thread never dies even in case of problems.
       */
      int msgID = MSGID_EXCEPTION_RECEIVING_REPLICATION_MESSAGE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SYNCHRONIZATION,
          ErrorLogSeverity.SEVERE_ERROR, message, msgID);
    }
  }
}
