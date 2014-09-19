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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.IOException;
import java.net.SocketException;

import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.ECLUpdateMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.workflowelement.externalchangelog.ECLSearchOperation;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a server writer, which is used to send changes to a
 * directory server.
 */
class ECLServerWriter extends ServerWriter
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final Session session;
  private final ECLServerHandler handler;
  private final ReplicationServerDomain replicationServerDomain;
  private boolean suspended;
  private volatile boolean shutdown;
  private final PersistentSearch mypsearch;

  /**
   * Create a ServerWriter.
   *
   * @param session     the Session that will be used to send updates.
   * @param handler     ECL handler for which the ServerWriter is created.
   * @param replicationServerDomain the ReplicationServerDomain of this
   *                    ServerWriter.
   */
  ECLServerWriter(Session session, ECLServerHandler handler,
      ReplicationServerDomain replicationServerDomain)
  {
    super(session, handler, replicationServerDomain, new DSRSShutdownSync());

    setName("Replication ECL Writer Thread for operation " +
        handler.getOperationId());

    this.session = session;
    this.handler = handler;
    this.replicationServerDomain = replicationServerDomain;
    this.suspended = false;
    this.shutdown = false;
    this.mypsearch = findPersistentSearch(handler);
  }

  /**
   * Look for the persistent search object related to this operation, the one
   * that will be notified with new entries to be returned.
   */
  private PersistentSearch findPersistentSearch(ECLServerHandler handler)
  {
    ECLWorkflowElement wfe = (ECLWorkflowElement)
        DirectoryServer.getWorkflowElement(ECLWorkflowElement.ECL_WORKFLOW_ELEMENT);
    for (PersistentSearch psearch : wfe.getPersistentSearches())
    {
      if (psearch.getSearchOperation().toString().equals(
          handler.getOperationId()))
      {
        return psearch;
      }
    }
    return null;
  }

  /**
   * The writer will start suspended by the Handler for the CL
   * waiting for the startCLSessionMsg. Then it may be
   * suspended between 2 jobs, each job being a separate search.
   */
  private synchronized void suspendWriter()
  {
    suspended = true;
  }

  /**
   * Resume the writer.
   */
  synchronized void resumeWriter()
  {
    suspended = false;
    notify();
  }

  /**
   * Run method for the ServerWriter.
   * Loops waiting for changes from the ReplicationServerDomain and
   * forward them to the other servers
   */
  @Override
  public void run()
  {
    try
    {
      while (true)
      {
        // wait to be resumed or shutdown
        if (suspended && !shutdown)
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
      // Just ignore the exception and let the thread die as well.
      // session is always null if a socket exception has occurred.
      if (session != null)
      {
        logger.error(handler.getBadlyDisconnectedErrorMessage());
      }
    }
    catch (Exception e)
    {
      // An unexpected error happened.
      // Log an error and close the connection.
      logger.error(ERR_WRITER_UNEXPECTED_EXCEPTION, handler + " " + stackTraceToSingleLineString(e));
    }
    finally
    {
      if (session != null)
      {
        session.close();
      }
      if (replicationServerDomain != null)
      {
        replicationServerDomain.stopServer(handler, false);
      }
    }
  }

  /**
   * Loop getting changes from the domain and publishing them either to
   * the provided session or to the ECL session interface.
   * @throws IOException when raised (connection closure)
   * @throws InterruptedException when raised
   */
  private void doIt() throws IOException, InterruptedException
  {
    while (!shutdown && !suspended)
    {
      final ECLUpdateMsg updateMsg = takeECLUpdate(handler);
      if (updateMsg == null)
      {
        if (session != null && handler.isInitPhaseDone())
        {
          // session is null in pusherOnly mode
          // Done is used to end phase 1
          session.publish(new DoneMsg(
              handler.getReplicationServerId(), handler.getServerId()));
        }

        if (handler.isNonPersistent())
        { // publishing is normally stopped here...
          break;
        }

        // ...except if we are in persistent search
        Thread.sleep(200);
      }
      else
      {
        // Publish the update to the remote server using a protocol version it supports
        publish(updateMsg);
      }
    }
  }

  private ECLUpdateMsg takeECLUpdate(ECLServerHandler handler)
  {
    try
    {
      return handler.takeECLUpdate();
    }
    catch(DirectoryException de)
    {
      logger.traceException(de);
      return null;
    }
  }

  /**
   * Shutdown the writer.
   */
  synchronized void shutdownWriter()
  {
    shutdown = true;
    notify();
  }

  /**
   * Publish a change either on the protocol session or to a persistent search.
   */
  private void publish(ECLUpdateMsg msg) throws IOException
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(getName() + " publishes msg=[" + msg + "]");
    }

    if (session != null)
    {
      session.publish(msg);
    }
    else if (mypsearch != null)
    {
      try
      {
        // Using processAdd() because all ECLUpdateMsgs are adds to the external changelog
        // (even though the underlying changes can be adds, deletes, modifies or modDNs)
        Entry eclEntry = ECLSearchOperation.createEntryFromMsg(msg);
        mypsearch.processAdd(eclEntry);
      }
      catch (Exception e)
      {
        logger.error(ERR_WRITER_UNEXPECTED_EXCEPTION, handler + " " + stackTraceToSingleLineString(e));
        mypsearch.cancel();
      }
    }
  }
}
