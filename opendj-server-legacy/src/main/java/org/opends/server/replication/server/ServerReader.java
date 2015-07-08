/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.net.SocketException;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.DirectoryThread;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.*;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.common.ServerStatus.*;
import static org.opends.server.util.StaticUtils.*;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private final Session session;
  private final ServerHandler handler;

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
        + ") reading from " + handler + " at "
        + session.getReadableRemoteAddress());
    this.session = session;
    this.handler = handler;
  }

  /**
   * Create a loop that reads changes and hands them off to be processed.
   */
  @Override
  public void run()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(getName() + " starting");
    }
    /*
     * wait on input stream
     * grab all incoming messages and publish them to the
     * replicationServerDomain
     */
    LocalizableMessage errMessage = null;
    try
    {
      while (true)
      {
        try
        {
          final ReplicationMsg msg = session.receive();

          if (logger.isTraceEnabled())
          {
            logger.trace("In " + getName() + " receives " + msg);
          }

          if (msg instanceof AckMsg)
          {
            handler.checkWindow();
            handler.processAck((AckMsg) msg);
          }
          else if (msg instanceof UpdateMsg)
          {
            final UpdateMsg updateMsg = (UpdateMsg) msg;
            if (!isUpdateMsgFiltered(updateMsg))
            {
              handler.put(updateMsg);
            }
          }
          else if (msg instanceof WindowMsg)
          {
            handler.updateWindow((WindowMsg) msg);
          }
          else if (msg instanceof MonitorRequestMsg)
          {
            handler.processMonitorRequestMsg((MonitorRequestMsg) msg);
          }
          else if (msg instanceof MonitorMsg)
          {
            handler.processMonitorMsg((MonitorMsg) msg);
          }
          else if (msg instanceof RoutableMsg)
          {
            /*
             * Note that we handle monitor messages separately since they in
             * fact never need "routing" and are instead sent directly between
             * connected peers. Doing so allows us to more clearly decouple
             * write IO from the reader thread (see OPENDJ-1354).
             */
            handler.process((RoutableMsg) msg);
          }
          else if (msg instanceof ResetGenerationIdMsg)
          {
            handler.processResetGenId((ResetGenerationIdMsg) msg);
          }
          else if (msg instanceof WindowProbeMsg)
          {
            handler.replyToWindowProbe();
          }
          else if (msg instanceof TopologyMsg)
          {
            ReplicationServerHandler rsh = (ReplicationServerHandler) handler;
            rsh.receiveTopoInfoFromRS((TopologyMsg) msg);
          }
          else if (msg instanceof ChangeStatusMsg)
          {
            ChangeStatusMsg csMsg = (ChangeStatusMsg) msg;
            try
            {
              DataServerHandler dsh = (DataServerHandler) handler;
              dsh.receiveNewStatus(csMsg);
            }
            catch (Exception e)
            {
              errMessage = ERR_RECEIVED_CHANGE_STATUS_NOT_FROM_DS.get(
                  handler.getBaseDN(), handler.getServerId(), csMsg);
              logger.error(errMessage);
            }
          }
          else if (msg instanceof ChangeTimeHeartbeatMsg)
          {
            handler.process((ChangeTimeHeartbeatMsg) msg);
          }
          else if (msg instanceof StopMsg)
          {
            /*
             * Peer server is properly disconnecting: go out of here to properly
             * close the server handler going to finally block.
             */
            if (logger.isTraceEnabled())
            {
              logger.trace(handler
                  + " has properly disconnected from this replication server "
                  + handler.getReplicationServerId());
            }
            return;
          }
          else if (msg == null)
          {
            /*
             * The remote server has sent an unknown message, close the
             * connection.
             */
            errMessage = NOTE_READER_NULL_MSG.get(handler);
            logger.info(errMessage);
            return;
          }
        }
        catch (NotSupportedOldVersionPDUException e)
        {
          /*
           * Received a V1 PDU we do not need to support: we just trash the
           * message and log the event for debug purpose, then continue
           * receiving messages.
           */
          logException(e);
        }
      }
    }
    catch (SocketException e)
    {
      /*
       * The connection has been broken Log a message and exit from this loop So
       * that this handler is stopped.
       */
      logException(e);
      if (!handler.shuttingDown())
      {
        errMessage = handler.getBadlyDisconnectedErrorMessage();
        logger.error(errMessage);
      }
    }
    catch (Exception e)
    {
      /*
       * The remote server has sent an unknown message, close the connection.
       */
      errMessage = NOTE_READER_EXCEPTION.get(handler,
          stackTraceToSingleLineString(e));
      logger.info(errMessage);
    }
    finally
    {
      /*
       * The thread only exits the loop above if some error condition happen.
       * Attempt to close the socket and stop the server handler.
       */
      if (logger.isTraceEnabled())
      {
        logger.trace("In " + getName() + " closing the session");
      }
      session.close();
      handler.doStop();
      if (logger.isTraceEnabled())
      {
        logger.trace(getName() + " stopped: " + errMessage);
      }
    }
  }

  /**
   * Returns whether the update message is filtered in one of those cases:
   * <ul>
   * <li>Ignore updates from DS in bad BAD_GENID_STATUS or FULL_UPDATE_STATUS</li>
   * <li>Ignore updates from RS with bad gen id</li>
   * </ul>
   */
  private boolean isUpdateMsgFiltered(UpdateMsg updateMsg)
  {
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
      final ServerStatus dsStatus = handler.getStatus();
      if (dsStatus == BAD_GEN_ID_STATUS)
      {
        logger.warn(WARN_IGNORING_UPDATE_FROM_DS_BADGENID,
            handler.getReplicationServerId(), updateMsg.getCSN(),
            handler.getBaseDN(), handler.getServerId(),
            session.getReadableRemoteAddress(),
            handler.getGenerationId(), handler.getReferenceGenId());
        return true;
      }
      else if (dsStatus == FULL_UPDATE_STATUS)
      {
        logger.warn(WARN_IGNORING_UPDATE_FROM_DS_FULLUP,
            handler.getReplicationServerId(), updateMsg.getCSN(),
            handler.getBaseDN(), handler.getServerId(),
            session.getReadableRemoteAddress());
        return true;
      }
    }
    else
    {
      /**
       * Ignore updates from RS with bad gen id
       * (no system managed status for a RS)
       */
      long referenceGenerationId = handler.getReferenceGenId();
      if (referenceGenerationId > 0
          && referenceGenerationId != handler.getGenerationId())
      {
        logger.error(WARN_IGNORING_UPDATE_FROM_RS,
            handler.getReplicationServerId(), updateMsg.getCSN(),
            handler.getBaseDN(), handler.getServerId(),
            session.getReadableRemoteAddress(),
            handler.getGenerationId(), referenceGenerationId);
        return true;
      }
    }
    return false;
  }

  private void logException(Exception e)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("In " + getName() + " " + stackTraceToSingleLineString(e));
    }
  }
}
