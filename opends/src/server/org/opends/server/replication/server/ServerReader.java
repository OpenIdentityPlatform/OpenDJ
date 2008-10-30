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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import org.opends.messages.Message;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;


import java.io.IOException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.InitializeRequestMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.protocol.WindowMsg;
import org.opends.server.replication.protocol.WindowProbeMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.MonitorMsg;
import org.opends.server.replication.protocol.MonitorRequestMsg;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.
  NotSupportedOldVersionPDUException;

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
    super("Replication Reader for " + handler.toString() + " in RS " +
      replicationServerDomain.getReplicationServer().getServerId());
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
        (handler.isReplicationServer() ? " RS " : " LS") +
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
        try
        {
          ReplicationMsg msg = session.receive();

          /*
          if (debugEnabled())
          {
          TRACER.debugInfo(
          "In RS " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          (handler.isReplicationServer()?" From RS ":" From LS")+
          " with serverId=" + serverId + " receives " + msg);
          }
           */
          if (msg instanceof AckMsg)
          {
            AckMsg ack = (AckMsg) msg;
            handler.checkWindow();
            replicationServerDomain.ack(ack, serverId);
          } else if (msg instanceof UpdateMsg)
          {
            boolean filtered = false;
            /* Ignore updates in some cases */
            if (handler.isLDAPserver())
            {
              /**
               * Ignore updates from DS in bad BAD_GENID_STATUS or
               * FULL_UPDATE_STATUS
               *
               * The RSD lock should not be taken here as it is acceptable to
               * have a delay between the time the server has a wrong status and
               * the fact we detect it: the updates that succeed to pass during
               * this time will have no impact on remote server. But it is
               * interesting to not saturate uselessly the network if the
               * updates are not necessary so this check to stop sending updates
               * is interesting anyway. Not taking the RSD lock allows to have
               * better performances in normal mode (most of the time).
               */
              ServerStatus dsStatus = handler.getStatus();
              if ((dsStatus == ServerStatus.BAD_GEN_ID_STATUS) ||
                (dsStatus == ServerStatus.FULL_UPDATE_STATUS))
              {
                long referenceGenerationId =
                  replicationServerDomain.getGenerationId();
                if (dsStatus == ServerStatus.BAD_GEN_ID_STATUS)
                  logError(ERR_IGNORING_UPDATE_FROM_DS_BADGENID.get(
                    Short.toString(replicationServerDomain.
                    getReplicationServer().getServerId()),
                    replicationServerDomain.getBaseDn().toNormalizedString(),
                    ((UpdateMsg) msg).getChangeNumber().toString(),
                    Short.toString(handler.getServerId()),
                    Long.toString(referenceGenerationId),
                    Long.toString(handler.getGenerationId())));
                if (dsStatus == ServerStatus.FULL_UPDATE_STATUS)
                  logError(ERR_IGNORING_UPDATE_FROM_DS_FULLUP.get(
                    Short.toString(replicationServerDomain.
                    getReplicationServer().getServerId()),
                    replicationServerDomain.getBaseDn().toNormalizedString(),
                    ((UpdateMsg) msg).getChangeNumber().toString(),
                    Short.toString(handler.getServerId())));
                filtered = true;
              }
            } else
            {
              /**
               * Ignore updates from RS with bad gen id
               * (no system managed status for a RS)
               */
              long referenceGenerationId =
                replicationServerDomain.getGenerationId();
              if ((referenceGenerationId > 0) &&
                (referenceGenerationId != handler.getGenerationId()))
              {
                logError(ERR_IGNORING_UPDATE_FROM_RS.get(
                  Short.toString(replicationServerDomain.getReplicationServer().
                  getServerId()),
                  replicationServerDomain.getBaseDn().toNormalizedString(),
                  ((UpdateMsg) msg).getChangeNumber().toString(),
                  Short.toString(handler.getServerId()),
                  Long.toString(referenceGenerationId),
                  Long.toString(handler.getGenerationId())));
                filtered = true;
              }
            }

            if (!filtered)
            {
              UpdateMsg update = (UpdateMsg) msg;
              handler.decAndCheckWindow();
              replicationServerDomain.put(update, handler);
            }
          } else if (msg instanceof WindowMsg)
          {
            WindowMsg windowMsg = (WindowMsg) msg;
            handler.updateWindow(windowMsg);
          } else if (msg instanceof InitializeRequestMsg)
          {
            InitializeRequestMsg initializeMsg =
              (InitializeRequestMsg) msg;
            handler.process(initializeMsg);
          } else if (msg instanceof InitializeTargetMsg)
          {
            InitializeTargetMsg initializeMsg = (InitializeTargetMsg) msg;
            handler.process(initializeMsg);
          } else if (msg instanceof EntryMsg)
          {
            EntryMsg entryMsg = (EntryMsg) msg;
            handler.process(entryMsg);
          } else if (msg instanceof DoneMsg)
          {
            DoneMsg doneMsg = (DoneMsg) msg;
            handler.process(doneMsg);
          } else if (msg instanceof ErrorMsg)
          {
            ErrorMsg errorMsg = (ErrorMsg) msg;
            handler.process(errorMsg);
          } else if (msg instanceof ResetGenerationIdMsg)
          {
            ResetGenerationIdMsg genIdMsg = (ResetGenerationIdMsg) msg;
            replicationServerDomain.resetGenerationId(handler, genIdMsg);
          } else if (msg instanceof WindowProbeMsg)
          {
            WindowProbeMsg windowProbeMsg = (WindowProbeMsg) msg;
            handler.process(windowProbeMsg);
          } else if (msg instanceof TopologyMsg)
          {
            TopologyMsg topoMsg = (TopologyMsg) msg;
            replicationServerDomain.receiveTopoInfoFromRS(topoMsg,
              handler, true);
          } else if (msg instanceof ChangeStatusMsg)
          {
            ChangeStatusMsg csMsg = (ChangeStatusMsg) msg;
            replicationServerDomain.processNewStatus(handler, csMsg);
          } else if (msg instanceof MonitorRequestMsg)
          {
            MonitorRequestMsg replServerMonitorRequestMsg =
              (MonitorRequestMsg) msg;
            handler.process(replServerMonitorRequestMsg);
          } else if (msg instanceof MonitorMsg)
          {
            MonitorMsg replServerMonitorMsg = (MonitorMsg) msg;
            handler.process(replServerMonitorMsg);
          } else if (msg == null)
          {
            /*
             * The remote server has sent an unknown message,
             * close the conenction.
             */
            Message message = NOTE_READER_NULL_MSG.get(handler.toString());
            logError(message);
            return;
          }
        } catch (NotSupportedOldVersionPDUException e)
        {
          // Received a V1 PDU we do not need to support:
          // we just trash the message and log the event for debug purpose,
          // then continue receiving messages.
          if (debugEnabled())
            TRACER.debugInfo("In " + replicationServerDomain.
              getReplicationServer().
              getMonitorInstanceName() + ":" + e.getMessage());
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
          " reader IO EXCEPTION for serverID=" + serverId +
          stackTraceToSingleLineString(e) + " " + e.getLocalizedMessage());
      Message message = NOTE_SERVER_DISCONNECT.get(handler.toString(),
        Short.toString(replicationServerDomain.
        getReplicationServer().getServerId()));
      logError(message);
    } catch (ClassNotFoundException e)
    {
      if (debugEnabled())
        TRACER.debugInfo(
          "In RS <" + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() +
          " reader CNF EXCEPTION serverID=" + serverId +
          stackTraceToSingleLineString(e));
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
          " server reader EXCEPTION serverID=" + serverId +
          stackTraceToSingleLineString(e));
      /*
       * The remote server has sent an unknown message,
       * close the connection.
       */
      Message message = NOTE_READER_EXCEPTION.get(handler.toString());
      logError(message);
    } finally
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
        (handler.isReplicationServer() ? " RS" : " LDAP") +
        " server reader stopped for serverID=" + serverId);
  }
}
