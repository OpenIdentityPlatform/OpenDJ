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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.changelog;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.synchronization.common.LogMessages.*;

import java.io.IOException;
import java.net.SocketException;
import java.util.NoSuchElementException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.synchronization.protocol.ProtocolSession;
import org.opends.server.synchronization.protocol.UpdateMessage;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;


/**
 * This class defines a server writer, which is used to send changes to a
 * directory server.
 */
public class ServerWriter extends DirectoryThread
{
  private ProtocolSession session;
  private ServerHandler handler;
  private ChangelogCache changelogCache;

  /**
   * Create a ServerWriter.
   * Then ServerWriter then waits on the ServerHandler for new updates
   * and forward them to the server
   *
   * @param session the ProtocolSession that will be used to send updates.
   * @param serverId the Identifier of the server.
   * @param handler handler for which the ServerWriter is created.
   * @param changelogCache The ChangelogCache of this ServerWriter.
   */
  public ServerWriter(ProtocolSession session, short serverId,
                      ServerHandler handler, ChangelogCache changelogCache)
  {
    super(handler.toString() + " writer");

    this.session = session;
    this.handler = handler;
    this.changelogCache = changelogCache;
  }

  /**
   * Run method for the ServerWriter.
   * Loops waiting for changes from the ChangelogCache and forward them
   * to the other servers
   */
  public void run()
  {
    try {
      while (true)
      {
        UpdateMessage update = changelogCache.take(this.handler);
        if (update == null)
          return;       /* this connection is closing */
        session.publish(update);
      }
    }
    catch (NoSuchElementException e)
    {
      /*
       * The remote host has disconnected and this particular Tree is going to
       * be removed, just ignore the exception and let the thread die as well
       */
      int    msgID   = MSGID_SERVER_DISCONNECT;
      String message = getMessage(msgID, handler.toString());
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.NOTICE,
               message, msgID);
    }
    catch (SocketException e)
    {
      /*
       * The remote host has disconnected and this particular Tree is going to
       * be removed, just ignore the exception and let the thread die as well
       */
      int    msgID   = MSGID_SERVER_DISCONNECT;
      String message = getMessage(msgID, handler.toString());
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.NOTICE,
               message, msgID);
    }
    catch (Exception e)
    {
      /*
       * An unexpected error happened.
       * Log an error and close the connection.
       */
      int    msgID   = MSGID_WRITER_UNEXPECTED_EXCEPTION;
      String message = getMessage(msgID, handler.toString());
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }
    finally {
      try
      {
        session.close();
      } catch (IOException e)
      {
       // Can't do much more : ignore
      }
      changelogCache.stopServer(handler);
    }
  }
}
