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
package org.opends.server.replication.server;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ReplicationMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;


import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.replication.protocol.AckMessage;
import org.opends.server.replication.protocol.DoneMessage;
import org.opends.server.replication.protocol.EntryMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.InitializeRequestMessage;
import org.opends.server.replication.protocol.InitializeTargetMessage;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.replication.protocol.WindowMessage;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.loggers.debug.DebugTracer;


/**
 * This class implement the part of the replicationServer that is reading
 * the connection from the LDAP servers to get all the updates that
 * were done on this replica and forward them to other servers.
 *
 * A single thread is dedicated to this work.
 * It waits in a blocking mode on the connection from the LDAP server
 * and upon receiving an update puts in into the replicationServer cache
 * from where the other servers will grab it.
 */
public class ServerReader extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private short serverId;
  private ProtocolSession session;
  private ServerHandler handler;
  private ReplicationCache replicationCache;

  /**
   * Constructor for the LDAP server reader part of the replicationServer.
   *
   * @param session The ProtocolSession from which to read the data.
   * @param serverId The server ID of the server from which we read changes.
   * @param handler The server handler for this server reader.
   * @param replicationCache The ReplicationCache for this server reader.
   */
  public ServerReader(ProtocolSession session, short serverId,
                      ServerHandler handler, ReplicationCache replicationCache)
  {
    super(handler.toString() + " reader");
    this.session = session;
    this.serverId = serverId;
    this.handler = handler;
    this.replicationCache = replicationCache;
  }

  /**
   * Create a loop that reads changes and hands them off to be processed.
   */
  public void run()
  {
    if (debugEnabled())
    {
      if (handler.isReplicationServer())
      {
        TRACER.debugInfo("Replication server reader starting " + serverId);
      }
      else
      {
        TRACER.debugInfo("LDAP server reader starting " + serverId);
      }
    }
    /*
     * wait on input stream
     * grab all incoming messages and publish them to the replicationCache
     */
    try
    {
      while (true)
      {
        ReplicationMessage msg = session.receive();

        if (msg instanceof AckMessage)
        {
          AckMessage ack = (AckMessage) msg;
          handler.checkWindow();
          replicationCache.ack(ack, serverId);
        }
        else if (msg instanceof UpdateMessage)
        {
          UpdateMessage update = (UpdateMessage) msg;
          handler.decAndCheckWindow();
          replicationCache.put(update, handler);
        }
        else if (msg instanceof WindowMessage)
        {
          WindowMessage windowMsg = (WindowMessage) msg;
          handler.updateWindow(windowMsg);
        }
        else if (msg instanceof InitializeRequestMessage)
        {
          InitializeRequestMessage initializeMsg =
            (InitializeRequestMessage) msg;
          handler.process(initializeMsg);
        }
        else if (msg instanceof InitializeTargetMessage)
        {
          InitializeTargetMessage initializeMsg = (InitializeTargetMessage) msg;
          handler.process(initializeMsg);
        }
        else if (msg instanceof EntryMessage)
        {
          EntryMessage entryMsg = (EntryMessage) msg;
          handler.process(entryMsg);
        }
        else if (msg instanceof DoneMessage)
        {
          DoneMessage doneMsg = (DoneMessage) msg;
          handler.process(doneMsg);
        }
        else if (msg instanceof ErrorMessage)
        {
          ErrorMessage errorMsg = (ErrorMessage) msg;
          handler.process(errorMsg);
        }
        else if (msg == null)
        {
          /*
           * The remote server has sent an unknown message,
           * close the conenction.
           */
          int    msgID   = MSGID_READER_NULL_MSG;
          String message = getMessage(msgID, handler.toString());
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return;
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
               message + e.getMessage(), msgID);
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
      /*
       * The remote server has sent an unknown message,
       * close the conenction.
       */
      int    msgID   = MSGID_READER_EXCEPTION;
      String message = getMessage(msgID, handler.toString());
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
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
      replicationCache.stopServer(handler);
    }
    if (debugEnabled())
    {
      if (handler.isReplicationServer())
      {
        TRACER.debugInfo("Replication server reader stopping " + serverId);
      }
      else
      {
        TRACER.debugInfo("LDAP server reader stopping " + serverId);
      }
    }
  }
}
