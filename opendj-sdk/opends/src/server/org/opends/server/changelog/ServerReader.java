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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.changelog;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.synchronization.SynchMessages.*;

import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.synchronization.AckMessage;
import org.opends.server.synchronization.SynchronizationMessage;
import org.opends.server.synchronization.UpdateMessage;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;


/**
 * This class implement the part of the changelog that is reading
 * the connection from the LDAP servers to get all the updates that
 * were done on this replica and forward them to other servers.
 *
 * A single thread is dedicated to this work.
 * It waits in a blocking mode on the connection from the LDAP server
 * and upon receiving an update puts in into the changelog cache
 * from where the other servers will grab it.
 */
public class ServerReader extends DirectoryThread
{
  private short serverId;
  private ProtocolSession session;
  private ServerHandler handler;
  private ChangelogCache changelogCache;

  /**
   * Constructor for the LDAP server reader part of the changelog.
   *
   * @param session The ProtocolSession from which to read the data.
   * @param serverId The server ID of the server from which we read changes.
   * @param handler The server handler for this server reader.
   * @param changelogCache The ChangelogCache for this server reader.
   */
  public ServerReader(ProtocolSession session, short serverId,
                      ServerHandler handler, ChangelogCache changelogCache)
  {
    super(handler.toString() + " reader");
    this.session = session;
    this.serverId = serverId;
    this.handler = handler;
    this.changelogCache = changelogCache;
  }

  /**
   * Create a loop that reads changes and hands them off to be processed.
   */
  public void run()
  {
    /*
     * TODO : catch exceptions in case of bugs
     * wait on input stream
     * grab all incoming messages and publish them to the changelogCache
     */
    try
    {
      while (true)
      {
        SynchronizationMessage msg = session.receive();

        if (msg == null)
        {
          // TODO : generate error in the log
          // make sure that connection is closed
          return;
        }
        if (msg instanceof AckMessage)
        {
          AckMessage ack = (AckMessage) msg;
          changelogCache.ack(ack, serverId);
        }
        else if (msg instanceof UpdateMessage)
        {
          UpdateMessage update = (UpdateMessage) msg;
          changelogCache.put(update, handler);
        }
      }
    } catch (IOException e)
    {
      /*
       * The connection has been broken
       * Log a message and exit from this loop
       * So that this handler is stopped.
       */
      int    msgID   = MSGID_SERVER_DISCONNECT;
      String message = getMessage(msgID, handler.toString());
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.NOTICE,
               message, msgID);
    } catch (ClassNotFoundException e)
    {
      /*
       * The remote server has sent an unknown message,
       * close the conenction.
       */
      int    msgID   = MSGID_UNKNOWN_MESSAGE;
      String message = getMessage(msgID, handler.toString());
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.NOTICE,
               message, msgID);
    } catch (Exception e)
    {

    }
    finally
    {
      /*
       * The thread only exit the loop above is some error condition
       * happen.
       * Attempt to close the socket and stop the server handler.
       */
      try
      {
        session.close();
      } catch (IOException e)
      {
       // ignore
      }
      changelogCache.stopServer(handler);
    }
  }
}
