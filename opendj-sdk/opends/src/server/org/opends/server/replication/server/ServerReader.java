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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;

import org.opends.messages.Message;
import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.*;

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
  private final Session session;
  private final ServerHandler handler;
  private final String remoteAddress;



  /**
   * Constructor for the LDAP server reader part of the replicationServer.
   *
   * @param session
   *          The Session from which to read the data.
   * @param handler
   *          The server handler for this server reader.
   */
  public ServerReader(Session session, ServerHandler handler)
  {
    super("Replication server RS(" + handler.getReplicationServerId()
        + ") reading from " + handler.toString() + " at "
        + session.getReadableRemoteAddress());
    this.session = session;
    this.handler = handler;
    this.remoteAddress = session.getReadableRemoteAddress();
  }

  /**
   * Create a loop that reads changes and hands them off to be processed.
   */
  public void run()
  {
    Message errMessage = null;
    if (debugEnabled())
    {
      TRACER.debugInfo(this.getName() + " starting");
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

          if (debugEnabled())
            TRACER.debugInfo("In " + getName() + " receives " + msg);

          if (msg instanceof AckMsg)
          {
            AckMsg ack = (AckMsg) msg;
            handler.checkWindow();
            handler.processAck(ack);
          } else if (msg instanceof UpdateMsg)
          {
            boolean filtered = false;
            /* Ignore updates in some cases */
            if (handler.isDataServer())
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
                long referenceGenerationId = handler.getReferenceGenId();
                if (dsStatus == ServerStatus.BAD_GEN_ID_STATUS)
                  logError(WARN_IGNORING_UPDATE_FROM_DS_BADGENID.get(
                      handler.getReplicationServerId(),
                      ((UpdateMsg) msg).getChangeNumber().toString(),
                      handler.getServiceId(), handler.getServerId(),
                      session.getReadableRemoteAddress(),
                      handler.getGenerationId(),
                      referenceGenerationId));
                if (dsStatus == ServerStatus.FULL_UPDATE_STATUS)
                  logError(WARN_IGNORING_UPDATE_FROM_DS_FULLUP.get(
                      handler.getReplicationServerId(),
                      ((UpdateMsg) msg).getChangeNumber().toString(),
                      handler.getServiceId(), handler.getServerId(),
                      session.getReadableRemoteAddress()));
                filtered = true;
              }
            } else
            {
              /**
               * Ignore updates from RS with bad gen id
               * (no system managed status for a RS)
               */
              long referenceGenerationId =handler.getReferenceGenId();
              if ((referenceGenerationId > 0) &&
                (referenceGenerationId != handler.getGenerationId()))
              {
                logError(
                    WARN_IGNORING_UPDATE_FROM_RS.get(
                        handler.getReplicationServerId(),
                        ((UpdateMsg) msg).getChangeNumber().toString(),
                        handler.getServiceId(),
                        handler.getServerId(),
                        session.getReadableRemoteAddress(),
                        handler.getGenerationId(),
                        referenceGenerationId));
                filtered = true;
              }
            }

            if (!filtered)
            {
              UpdateMsg update = (UpdateMsg) msg;
              handler.decAndCheckWindow();
              handler.put(update);
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
          } else if (msg instanceof InitializeRcvAckMsg)
          {
            InitializeRcvAckMsg initializeRcvAckMsg =
              (InitializeRcvAckMsg) msg;
            handler.process(initializeRcvAckMsg);
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
            handler.processResetGenId(genIdMsg);
          } else if (msg instanceof WindowProbeMsg)
          {
            WindowProbeMsg windowProbeMsg = (WindowProbeMsg) msg;
            handler.process(windowProbeMsg);
          } else if (msg instanceof TopologyMsg)
          {
            TopologyMsg topoMsg = (TopologyMsg) msg;
            ReplicationServerHandler rsh = (ReplicationServerHandler)handler;
            rsh.receiveTopoInfoFromRS(topoMsg);
          } else if (msg instanceof ChangeStatusMsg)
          {
            ChangeStatusMsg csMsg = (ChangeStatusMsg) msg;
            try
            {
              DataServerHandler dsh = (DataServerHandler)handler;
              dsh.receiveNewStatus(csMsg);
            }
            catch(Exception e)
            {
              errMessage =
                ERR_RECEIVED_CHANGE_STATUS_NOT_FROM_DS.get(
                    handler.getServiceId(),
                    Integer.toString(handler.getServerId()),
                    csMsg.toString());
              logError(errMessage);
            }
          } else if (msg instanceof MonitorRequestMsg)
          {
            MonitorRequestMsg replServerMonitorRequestMsg =
              (MonitorRequestMsg) msg;
            handler.process(replServerMonitorRequestMsg);
          } else if (msg instanceof MonitorMsg)
          {
            MonitorMsg replServerMonitorMsg = (MonitorMsg) msg;
            handler.process(replServerMonitorMsg);
          } else if (msg instanceof ChangeTimeHeartbeatMsg)
          {
            ChangeTimeHeartbeatMsg cthbMsg = (ChangeTimeHeartbeatMsg) msg;
            handler.process(cthbMsg);
          } else if (msg instanceof StopMsg)
          {
            // Peer server is properly disconnecting: go out of here to
            // properly close the server handler going to finally block.
            if (debugEnabled())
            {
              TRACER.debugInfo(handler.toString() + " has properly " +
                "disconnected from this replication server " +
                Integer.toString(handler.getReplicationServerId()));
            }
            return;
          } else if (msg == null)
          {
            /*
             * The remote server has sent an unknown message,
             * close the conenction.
             */
            errMessage = NOTE_READER_NULL_MSG.get(handler.toString());
            logError(errMessage);
            return;
          }
        } catch (NotSupportedOldVersionPDUException e)
        {
          // Received a V1 PDU we do not need to support:
          // we just trash the message and log the event for debug purpose,
          // then continue receiving messages.
          if (debugEnabled())
            TRACER.debugInfo(
                "In " + this.getName() + " " + stackTraceToSingleLineString(e));
        }
      }
    }
    catch (IOException e)
    {
      /*
       * The connection has been broken
       * Log a message and exit from this loop
       * So that this handler is stopped.
       */
      if (debugEnabled())
        TRACER.debugInfo(
            "In " + this.getName() + " " + stackTraceToSingleLineString(e));
      if (!handler.shuttingDown())
      {
        if (handler.isDataServer())
        {
          errMessage = ERR_DS_BADLY_DISCONNECTED.get(
              handler.getReplicationServerId(), handler.getServerId(),
              remoteAddress, handler.getServiceId());
        }
        else
        {
          errMessage = ERR_RS_BADLY_DISCONNECTED.get(
              handler.getReplicationServerId(), handler.getServerId(),
              remoteAddress, handler.getServiceId());
        }
        logError(errMessage);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
        TRACER.debugInfo(
          "In " + this.getName() + " " + stackTraceToSingleLineString(e));
      /*
       * The remote server has sent an unknown message,
       * close the connection.
       */
      errMessage = NOTE_READER_EXCEPTION.get(handler.toString());
      logError(errMessage);
    }
    finally
    {
      /*
       * The thread only exits the loop above if some error condition happen.
       * Attempt to close the socket and stop the server handler.
       */
      if (debugEnabled())
      {
        TRACER.debugInfo("In " + this.getName()
            + " closing the session");
      }
      session.close();
      handler.doStop();
      if (debugEnabled())
      {
        TRACER.debugInfo(this.getName() + " stopped " + errMessage);
      }
    }
  }
}
