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
import org.opends.messages.Message;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;


import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.replication.protocol.AckMessage;
import org.opends.server.replication.protocol.DoneMessage;
import org.opends.server.replication.protocol.EntryMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.ResetGenerationId;
import org.opends.server.replication.protocol.InitializeRequestMessage;
import org.opends.server.replication.protocol.InitializeTargetMessage;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.replication.protocol.WindowMessage;
import org.opends.server.replication.protocol.WindowProbe;
import org.opends.server.replication.protocol.ReplServerInfoMessage;
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
  private ReplicationServerDomain replicationServerDomain;

  /**
   * Constructor for the LDAP server reader part of the replicationServer.
   *
   * @param session The ProtocolSession from which to read the data.
   * @param serverId The server ID of the server from which we read messages.
   * @param handler The server handler for this server reader.
   * @param replicationServerDomain The ReplicationServerDomain for this server
   *        reader.
   */
  public ServerReader(ProtocolSession session, short serverId,
                      ServerHandler handler,
                      ReplicationServerDomain replicationServerDomain)
  {
    super(handler.toString() + " reader");
    this.session = session;
    this.serverId = serverId;
    this.handler = handler;
    this.replicationServerDomain = replicationServerDomain;
  }

  /**
   * Create a loop that reads changes and hands them off to be processed.
   */
  public void run()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo(
          "In RS " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          (handler.isReplicationServer()?" RS ":" LS")+
          " reader starting for serverId=" + serverId);
    }
    /*
     * wait on input stream
     * grab all incoming messages and publish them to the
     * replicationServerDomain
     */
    try
    {
      while (true)
      {
        ReplicationMessage msg = session.receive();

        if (debugEnabled())
        {
          TRACER.debugInfo(
              "In RS " + replicationServerDomain.getReplicationServer().
              getMonitorInstanceName() +
              (handler.isReplicationServer()?" From RS ":" From LS")+
              " with serverId=" + serverId + " receives " + msg);
        }
        if (msg instanceof AckMessage)
        {
          AckMessage ack = (AckMessage) msg;
          handler.checkWindow();
          replicationServerDomain.ack(ack, serverId);
        }
        else if (msg instanceof UpdateMessage)
        {
          // Ignore update received from a replica with
          // a bad generation ID
          long referenceGenerationId =
                  replicationServerDomain.getGenerationId();
          if ((referenceGenerationId>0) &&
              (referenceGenerationId != handler.getGenerationId()))
          {
            logError(ERR_IGNORING_UPDATE_FROM.get(
                msg.toString(),
                handler.getMonitorInstanceName()));
          }
          else
          {
            UpdateMessage update = (UpdateMessage) msg;
            handler.decAndCheckWindow();
            replicationServerDomain.put(update, handler);
          }
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
        else if (msg instanceof ResetGenerationId)
        {
          ResetGenerationId genIdMsg = (ResetGenerationId) msg;
          replicationServerDomain.resetGenerationId(this.handler, genIdMsg);
        }
        else if (msg instanceof WindowProbe)
        {
          WindowProbe windowProbeMsg = (WindowProbe) msg;
          handler.process(windowProbeMsg);
        }
        else if (msg instanceof ReplServerInfoMessage)
        {
          ReplServerInfoMessage infoMsg = (ReplServerInfoMessage)msg;
          handler.receiveReplServerInfo(infoMsg);

          if (debugEnabled())
          {
            if (handler.isReplicationServer())
              TRACER.debugInfo(
               "In RS " + replicationServerDomain.getReplicationServer().
               getServerId() +
               " Receiving replServerInfo from " + handler.getServerId() +
               " baseDn=" + replicationServerDomain.getBaseDn() +
               " genId=" + infoMsg.getGenerationId());
          }

          if (replicationServerDomain.getGenerationId()<0)
          {
            // Here is the case where a ReplicationServer receives from
            // another ReplicationServer the generationId for a domain
            // for which the generation ID has never been set.
            replicationServerDomain.
                    setGenerationId(infoMsg.getGenerationId(),false);
          }
          else
          {
            if (infoMsg.getGenerationId()<0)
            {
              // Here is the case where another ReplicationServer
              // signals that it has no generationId set for the domain.
              // If we have generationId set locally and no server currently
              // connected for that domain in the topology then we may also
              // reset the generationId localy.
              replicationServerDomain.mayResetGenerationId();
            }

            if (replicationServerDomain.getGenerationId() !=
                    infoMsg.getGenerationId())
            {
              Message message = NOTE_BAD_GENERATION_ID.get(
                  replicationServerDomain.getBaseDn().toNormalizedString(),
                  Short.toString(handler.getServerId()),
                  Long.toString(infoMsg.getGenerationId()),
                  Long.toString(replicationServerDomain.getGenerationId()));

              ErrorMessage errorMsg = new ErrorMessage(
                  replicationServerDomain.getReplicationServer().getServerId(),
                  handler.getServerId(),
                  message);
              session.publish(errorMsg);
            }
          }
        }
        else if (msg == null)
        {
          /*
           * The remote server has sent an unknown message,
           * close the conenction.
           */
          Message message = NOTE_READER_NULL_MSG.get(handler.toString());
          logError(message);
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
      if (debugEnabled())
        TRACER.debugInfo(
          "In RS " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          " reader IO EXCEPTION for serverID=" + serverId
          + stackTraceToSingleLineString(e) + " " + e.getLocalizedMessage());
      Message message = NOTE_SERVER_DISCONNECT.get(handler.toString());
      logError(message);
    } catch (ClassNotFoundException e)
    {
      if (debugEnabled())
        TRACER.debugInfo(
          "In RS <" + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          " reader CNF EXCEPTION serverID=" + serverId
          + stackTraceToSingleLineString(e));
      /*
       * The remote server has sent an unknown message,
       * close the connection.
       */
      Message message = ERR_UNKNOWN_MESSAGE.get(handler.toString());
      logError(message);
    } catch (Exception e)
    {
      if (debugEnabled())
        TRACER.debugInfo(
          "In RS <" + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          " server reader EXCEPTION serverID=" + serverId
          + stackTraceToSingleLineString(e));
      /*
       * The remote server has sent an unknown message,
       * close the connection.
       */
      Message message = NOTE_READER_EXCEPTION.get(handler.toString());
      logError(message);
    }
    finally
    {
      /*
       * The thread only exit the loop above is some error condition
       * happen.
       * Attempt to close the socket and stop the server handler.
       */
      if (debugEnabled())
        TRACER.debugInfo(
          "In RS " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          " server reader for serverID=" + serverId +
          " is closing the session");
      try
      {
        session.close();
      } catch (IOException e)
      {
       // ignore
      }
      replicationServerDomain.stopServer(handler);
    }
    if (debugEnabled())
      TRACER.debugInfo(
          "In RS " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          (handler.isReplicationServer()?" RS":" LDAP") +
          " server reader stopped for serverID=" + serverId);
  }
}
