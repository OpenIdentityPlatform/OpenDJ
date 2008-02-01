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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
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
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.UpdateMessage;


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
    super(handler.toString() + " writer");

    this.serverId = serverId;
    this.session = session;
    this.handler = handler;
    this.replicationServerDomain = replicationServerDomain;
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
        UpdateMessage update = replicationServerDomain.take(this.handler);
        if (update == null)
          return;       /* this connection is closing */

        // Ignore update to be sent to a replica with a bad generation ID
        long referenceGenerationId = replicationServerDomain.getGenerationId();
        if ((referenceGenerationId != handler.getGenerationId())
            || (referenceGenerationId == -1)
            || (handler.getGenerationId() == -1))
        {
          logError(ERR_IGNORING_UPDATE_TO.get(
              update.getDn(),
              this.handler.getMonitorInstanceName()));
          continue;
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
        session.publish(update);
      }
    }
    catch (NoSuchElementException e)
    {
      /*
       * The remote host has disconnected and this particular Tree is going to
       * be removed, just ignore the exception and let the thread die as well
       */
      Message message = NOTE_SERVER_DISCONNECT.get(handler.toString());
      logError(message);
    }
    catch (SocketException e)
    {
      /*
       * The remote host has disconnected and this particular Tree is going to
       * be removed, just ignore the exception and let the thread die as well
       */
      Message message = NOTE_SERVER_DISCONNECT.get(handler.toString());
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
