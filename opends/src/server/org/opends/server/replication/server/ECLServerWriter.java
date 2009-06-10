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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.net.SocketException;

import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.ECLUpdateMsg;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.StartECLSessionMsg;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.workflowelement.externalchangelog.ECLSearchOperation;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;


/**
 * This class defines a server writer, which is used to send changes to a
 * directory server.
 */
public class ECLServerWriter extends ServerWriter
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private ProtocolSession session;
  private ECLServerHandler handler;
  private ReplicationServerDomain replicationServerDomain;
  private short protocolVersion = -1;
  private boolean suspended;
  private boolean shutdown;

  /**
   * Create a ServerWriter.
   *
   * @param session     the ProtocolSession that will be used to send updates.
   * @param handler     ECL handler for which the ServerWriter is created.
   * @param replicationServerDomain the ReplicationServerDomain of this
   *                    ServerWriter.
   */
  public ECLServerWriter(ProtocolSession session, ECLServerHandler handler,
      ReplicationServerDomain replicationServerDomain)
  {
    super(session, (short)-1, handler, replicationServerDomain);

    setName("Replication ECL Writer Thread for op:" +
        handler.getOperationId());

    this.session = session;
    this.handler = handler;
    this.replicationServerDomain = replicationServerDomain;
    // Keep protocol version locally for efficiency
    this.protocolVersion = handler.getProtocolVersion();
    this.suspended = false;
    this.shutdown = false;
  }

  /**
   * The writer will start suspended by the Handler for the CL
   * waiting for the startCLSessionMsg. Then it may be
   * suspended between 2 jobs, each job being a separate search.
   */
  public synchronized void suspendWriter()
  {
    synchronized(this)
    {
      suspended = true;
    }
  }

  /**
   * Resume the writer.
   */
  public synchronized void resumeWriter()
  {
    synchronized(this)
    {
      suspended = false;
    }
    notify();
  }

  /**
   * Run method for the ServerWriter.
   * Loops waiting for changes from the ReplicationServerDomain and
   * forward them to the other servers
   */
  public void run()
  {
    Message errMessage = null;
    try
    {
      while (true)
      {
        // wait to be resumed or shutdowned
        if ((suspended) && (!shutdown))
        {
          synchronized(this)
          {
            wait();
          }
        }

        if (shutdown)
        {
          return;
        }

        // Not suspended
        doIt();


        if (shutdown)
        {
          return;
        }
        suspendWriter();
      }
    }
    catch (SocketException e)
    {
      // Just ignore the exception and let the thread die as well
      errMessage = NOTE_SERVER_DISCONNECT.get(handler.toString(),
          "for operation " + handler.getOperationId());
      logError(errMessage);
    }
    catch (Exception e)
    {
      // An unexpected error happened.
      // Log an error and close the connection.
      errMessage = ERR_WRITER_UNEXPECTED_EXCEPTION.get(handler.toString() +
          " " +  stackTraceToSingleLineString(e));
      logError(errMessage);
    }
    finally
    {
      if (session!=null)
      {
        try
        {
          session.close();
        } catch (IOException e)
        {
          // Can't do much more : ignore
        }
      }
      replicationServerDomain.stopServer(handler);
    }
  }

  /**
   * Loop gettting changes from the domain and publishing them either to
   * the provided session or to the ECL session interface.
   * @throws IOException when raised (connection closure)
   * @throws InterruptedException when raised
   */
  public void doIt()
  throws IOException, InterruptedException
  {
    ECLUpdateMsg update = null;
    while (true)
    {
      if (shutdown)
      {
        return;
      }

      if (suspended)
      {
        return;
      }

      // TODO:ECL please document the details of the cases where update could
      // be not null here
      if (update == null)
      {
        try
        {
          update = handler.takeECLUpdate();
        }
        catch(DirectoryException de)
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
      }

      if (update == null)
      {
        if (handler.getSearchPhase() != 1)
        {
          if (session!=null)
          {
            // session is null in pusherOnly mode
            // Done is used to end phase 1
            session.publish(new DoneMsg(
                replicationServerDomain.getReplicationServer().getServerId(),
                handler.getServerId()), protocolVersion);
          }
        }

        if (handler.isPersistent() == StartECLSessionMsg.NON_PERSISTENT)
        {
          // publishing is normally stopped here
          break;
        }
        else
        {
          // except if we are in persistent search
          Thread.sleep(200);
          continue;
        }
      }
      else
      {
        // Publish the update to the remote server using a protocol version he
        // it supports
        publish(update, protocolVersion);
        update = null;
      }
    }
}

  /**
   * Shutdown the writer.
   */
  public synchronized void shutdownWriter()
  {
    shutdown = true;
    this.notify();
  }

  /**
   * Publish a change either on the protocol session or to a persistent search.
   */
  private void publish(ECLUpdateMsg msg, short reqProtocolVersion)
  throws IOException
  {
    if (debugEnabled())
      TRACER.debugInfo(this + " publishes msg=[" + msg.toString() + "]");

    if (session!=null)
    {
      session.publish(msg, protocolVersion);
    }
    else
    {
      ECLWorkflowElement wfe = (ECLWorkflowElement)
      DirectoryServer.getWorkflowElement(
          ECLWorkflowElement.ECL_WORKFLOW_ELEMENT);

      // Notify persistent searches.
      for (PersistentSearch psearch : wfe.getPersistentSearches())
      {
        try
        {
          Entry eclEntry = ECLSearchOperation.createEntryFromMsg(msg);
          psearch.processAdd(eclEntry, -1);
        }
        catch(Exception e)
        {
          Message errMessage =
            ERR_WRITER_UNEXPECTED_EXCEPTION.get(handler.toString() +
              " " +  stackTraceToSingleLineString(e));
          logError(errMessage);
        }
      }
    }
  }
}
