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
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.messages.ReplicationMessages.*;

import java.io.IOException;
import java.net.SocketException;
import java.util.NoSuchElementException;

import org.opends.server.api.DirectoryThread;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.UpdateMsg;


/**
 * This class defines a server writer, which is used to send changes to a
 * directory server.
 */
public class ServerWriter extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private ProtocolSession session;
  private ServerHandler handler;
  private ReplicationServerDomain replicationServerDomain;
  private short serverId;
  private short protocolVersion = -1;

  /**
   * Create a ServerWriter.
   * Then ServerWriter then waits on the ServerHandler for new updates
   * and forward them to the server
   *
   * @param session the ProtocolSession that will be used to send updates.
   * @param serverId the Identifier of the server.
   * @param handler handler for which the ServerWriter is created.
   * @param replicationServerDomain The ReplicationServerDomain of this
   *        ServerWriter.
   */
  public ServerWriter(ProtocolSession session, short serverId,
                      ServerHandler handler,
                      ReplicationServerDomain replicationServerDomain)
  {
    super("Replication Writer for " + handler.toString() + " in RS " +
      replicationServerDomain.getReplicationServer().getServerId());

    this.serverId = serverId;
    this.session = session;
    this.handler = handler;
    this.replicationServerDomain = replicationServerDomain;
    // Keep protocol version locally for efficiency
    this.protocolVersion = handler.getProtocolVersion();
  }

  /**
   * Run method for the ServerWriter.
   * Loops waiting for changes from the ReplicationServerDomain and forward them
   * to the other servers
   */
  public void run()
  {
    if (debugEnabled())
    {
      if (handler.isReplicationServer())
      {
        TRACER.debugInfo("Replication server writer starting " + serverId);
      }
      else
      {
        TRACER.debugInfo("LDAP server writer starting " + serverId);
      }
    }
    try
    {
      while (true)
      {
        UpdateMsg update = replicationServerDomain.take(this.handler);
        if (update == null)
          return;       /* this connection is closing */

        /* Ignore updates in some cases */
        if (handler.isLDAPserver())
        {
          /**
           * Ignore updates to DS in bad BAD_GENID_STATUS or FULL_UPDATE_STATUS
           *
           * The RSD lock should not be taken here as it is acceptable to have a
           * delay between the time the server has a wrong status and the fact
           * we detect it: the updates that succeed to pass during this time
           * will have no impact on remote server. But it is interesting to not
           * saturate uselessly the network if the updates are not necessary so
           * this check to stop sending updates is interesting anyway. Not
           * taking the RSD lock allows to have better performances in normal
           * mode (most of the time).
           */
          ServerStatus dsStatus = handler.getStatus();
          if ((dsStatus == ServerStatus.BAD_GEN_ID_STATUS) ||
            (dsStatus == ServerStatus.FULL_UPDATE_STATUS))
          {
            long referenceGenerationId =
              replicationServerDomain.getGenerationId();
            if (dsStatus == ServerStatus.BAD_GEN_ID_STATUS)
              logError(ERR_IGNORING_UPDATE_TO_DS_BADGENID.get(
                Short.toString(replicationServerDomain.getReplicationServer().
                getServerId()),
                replicationServerDomain.getBaseDn(),
                update.getChangeNumber().toString(),
                Short.toString(handler.getServerId()),
                Long.toString(handler.getGenerationId()),
                Long.toString(referenceGenerationId)));
            if (dsStatus == ServerStatus.FULL_UPDATE_STATUS)
              logError(ERR_IGNORING_UPDATE_TO_DS_FULLUP.get(
                Short.toString(replicationServerDomain.getReplicationServer().
                getServerId()),
                replicationServerDomain.getBaseDn(),
                update.getChangeNumber().toString(),
                Short.toString(handler.getServerId())));
            continue;
          }
        } else
        {
          /**
           * Ignore updates to RS with bad gen id
           * (no system managed status for a RS)
           */
          long referenceGenerationId =
            replicationServerDomain.getGenerationId();
          if ((referenceGenerationId != handler.getGenerationId()) ||
            (referenceGenerationId == -1) || (handler.getGenerationId() == -1))
          {
            logError(ERR_IGNORING_UPDATE_TO_RS.get(
              Short.toString(replicationServerDomain.getReplicationServer().
              getServerId()),
              replicationServerDomain.getBaseDn(),
              update.getChangeNumber().toString(),
              Short.toString(handler.getServerId()),
              Long.toString(handler.getGenerationId()),
              Long.toString(referenceGenerationId)));
            continue;
          }
        }

        /*
        if (debugEnabled())
        {
          TRACER.debugInfo(
            "In " + replicationServerDomain.getReplicationServer().
              getMonitorInstanceName() +
            ", writer to " + this.handler.getMonitorInstanceName() +
            " publishes msg=[" + update.toString() + "]"+
            " refgenId=" + referenceGenerationId +
            " server=" + handler.getServerId() +
            " generationId=" + handler.getGenerationId());
        }
        */

        // Publish the update to the remote server using a protocol version he
        // it supports
        session.publish(update, protocolVersion);
      }
    }
    catch (NoSuchElementException e)
    {
      /*
       * The remote host has disconnected and this particular Tree is going to
       * be removed, just ignore the exception and let the thread die as well
       */
      Message message = NOTE_SERVER_DISCONNECT.get(handler.toString(),
        Short.toString(replicationServerDomain.
        getReplicationServer().getServerId()));
      logError(message);
    }
    catch (SocketException e)
    {
      /*
       * The remote host has disconnected and this particular Tree is going to
       * be removed, just ignore the exception and let the thread die as well
       */
      Message message = NOTE_SERVER_DISCONNECT.get(handler.toString(),
        Short.toString(replicationServerDomain.
        getReplicationServer().getServerId()));
      logError(message);
    }
    catch (Exception e)
    {
      /*
       * An unexpected error happened.
       * Log an error and close the connection.
       */
      Message message = ERR_WRITER_UNEXPECTED_EXCEPTION.get(handler.toString() +
                        " " +  stackTraceToSingleLineString(e));
      logError(message);
    }
    finally {
      try
      {
        session.close();
      } catch (IOException e)
      {
       // Can't do much more : ignore
      }
      replicationServerDomain.stopServer(handler);

      if (debugEnabled())
      {
        if (handler.isReplicationServer())
        {
          TRACER.debugInfo("Replication server writer stopping " + serverId);
        }
        else
        {
          TRACER.debugInfo("LDAP server writer stopping " + serverId);
        }
      }
    }
  }
}
