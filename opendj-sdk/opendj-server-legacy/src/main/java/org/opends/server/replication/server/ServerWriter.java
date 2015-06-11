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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.net.SocketException;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.DirectoryThread;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.DSRSShutdownSync;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.common.ServerStatus.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a server writer, which is used to send changes to a
 * directory server.
 */
public class ServerWriter extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final Session session;
  private final ServerHandler handler;
  private final ReplicationServerDomain replicationServerDomain;
  private final DSRSShutdownSync dsrsShutdownSync;

  /**
   * Create a ServerWriter. Then ServerWriter then waits on the ServerHandler
   * for new updates and forward them to the server
   *
   * @param session
   *          the Session that will be used to send updates.
   * @param handler
   *          handler for which the ServerWriter is created.
   * @param replicationServerDomain
   *          The ReplicationServerDomain of this ServerWriter.
   * @param dsrsShutdownSync Synchronization object for shutdown of combined DS/RS instances.
   */
  public ServerWriter(Session session, ServerHandler handler,
      ReplicationServerDomain replicationServerDomain,
      DSRSShutdownSync dsrsShutdownSync)
  {
    // Session may be null for ECLServerWriter.
    super("Replication server RS(" + handler.getReplicationServerId()
        + ") writing to " + handler + " at "
        + (session != null ? session.getReadableRemoteAddress() : "unknown"));

    this.session = session;
    this.handler = handler;
    this.replicationServerDomain = replicationServerDomain;
    this.dsrsShutdownSync = dsrsShutdownSync;
  }

  /**
   * Run method for the ServerWriter.
   * Loops waiting for changes from the ReplicationServerDomain and forward them
   * to the other servers
   */
  @Override
  public void run()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(getName() + " starting");
    }

    LocalizableMessage errMessage = null;
    try
    {
      boolean shutdown = false;
      while (!shutdown
          || !dsrsShutdownSync.canShutdown(replicationServerDomain.getBaseDN()))
      {
        final UpdateMsg updateMsg = this.handler.take();
        if (updateMsg == null)
        {
          // this connection is closing
          errMessage = LocalizableMessage.raw(
           "Connection closure: null update returned by domain.");
          shutdown = true;
        }
        else if (!isUpdateMsgFiltered(updateMsg))
        {
          // Publish the update to the remote server using a protocol version it supports
          session.publish(updateMsg);
          if (updateMsg instanceof ReplicaOfflineMsg)
          {
            dsrsShutdownSync.replicaOfflineMsgForwarded(replicationServerDomain.getBaseDN());
          }
        }
      }
    }
    catch (SocketException e)
    {
      /*
       * The remote host has disconnected and this particular Tree is going to
       * be removed, just ignore the exception and let the thread die as well
       */
      errMessage = handler.getBadlyDisconnectedErrorMessage();
      logger.error(errMessage);
    }
    catch (Exception e)
    {
      /*
       * An unexpected error happened.
       * Log an error and close the connection.
       */
      errMessage = ERR_WRITER_UNEXPECTED_EXCEPTION.get(handler +
                        " " +  stackTraceToSingleLineString(e));
      logger.error(errMessage);
    }
    finally {
      session.close();
      replicationServerDomain.stopServer(handler, false);
      if (logger.isTraceEnabled())
      {
        logger.trace(getName() + " stopped " + errMessage);
      }
    }
  }

  private boolean isUpdateMsgFiltered(UpdateMsg updateMsg)
  {
    if (handler.isDataServer())
    {
      /**
       * Ignore updates to DS in bad BAD_GENID_STATUS or FULL_UPDATE_STATUS
       *
       * The RSD lock should not be taken here as it is acceptable to have a delay
       * between the time the server has a wrong status and the fact we detect it:
       * the updates that succeed to pass during this time will have no impact on remote server.
       * But it is interesting to not saturate uselessly the network
       * if the updates are not necessary so this check to stop sending updates is interesting anyway.
       * Not taking the RSD lock allows to have better performances in normal mode (most of the time).
       */
      final ServerStatus dsStatus = handler.getStatus();
      if (dsStatus == BAD_GEN_ID_STATUS)
      {
        logger.warn(WARN_IGNORING_UPDATE_TO_DS_BADGENID, handler.getReplicationServerId(),
            updateMsg.getCSN(), handler.getBaseDN(), handler.getServerId(),
            session.getReadableRemoteAddress(),
            handler.getGenerationId(),
            replicationServerDomain.getGenerationId());
        return true;
      }
      else if (dsStatus == FULL_UPDATE_STATUS)
      {
        logger.warn(WARN_IGNORING_UPDATE_TO_DS_FULLUP, handler.getReplicationServerId(),
            updateMsg.getCSN(), handler.getBaseDN(), handler.getServerId(),
            session.getReadableRemoteAddress());
        return true;
      }
    }
    else
    {
      /**
       * Ignore updates to RS with bad gen id
       * (no system managed status for a RS)
       */
      final long referenceGenerationId = replicationServerDomain.getGenerationId();
      if (referenceGenerationId != handler.getGenerationId()
          || referenceGenerationId == -1 || handler.getGenerationId() == -1)
      {
        logger.error(WARN_IGNORING_UPDATE_TO_RS,
            handler.getReplicationServerId(),
            updateMsg.getCSN(), handler.getBaseDN(), handler.getServerId(),
            session.getReadableRemoteAddress(),
            handler.getGenerationId(),
            referenceGenerationId);
        return true;
      }
    }
    return false;
  }
}
