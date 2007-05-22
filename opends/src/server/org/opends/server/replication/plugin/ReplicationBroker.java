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
package org.opends.server.replication.plugin;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.ReplServerStartMessage;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ServerStartMessage;
import org.opends.server.replication.protocol.SocketSession;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.replication.protocol.WindowMessage;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;


/**
 * The broker for Multi-master Replication.
 */
public class ReplicationBroker implements InternalSearchListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private boolean shutdown = false;
  private Collection<String> servers;
  private boolean connected = false;
  private final Object lock = new Object();
  private String replicationServer = "Not connected";
  private TreeSet<FakeOperation> replayOperations;
  private ProtocolSession session = null;
  private final ServerState state;
  private final DN baseDn;
  private final short serverID;
  private int maxSendDelay;
  private int maxReceiveDelay;
  private int maxSendQueue;
  private int maxReceiveQueue;
  private Semaphore sendWindow;
  private int maxSendWindow;
  private int rcvWindow;
  private int halfRcvWindow;
  private int maxRcvWindow;
  private int timeout = 0;

  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval = 0;


  /**
   * A thread to monitor heartbeats on the session.
   */
  private HeartbeatMonitor heartbeatMonitor = null;

  /**
   * The number of times the connection was lost.
   */
  private int numLostConnections = 0;


  /**
   * Creates a new ReplicationServer Broker for a particular ReplicationDomain.
   *
   * @param state The ServerState that should be used by this broker
   *              when negociating the session with the replicationServer.
   * @param baseDn The base DN that should be used by this broker
   *              when negociating the session with the replicationServer.
   * @param serverID The server ID that should be used by this broker
   *              when negociating the session with the replicationServer.
   * @param maxReceiveQueue The maximum size of the receive queue to use on
   *                         the replicationServer.
   * @param maxReceiveDelay The maximum replication delay to use on the
   *                        replicationServer.
   * @param maxSendQueue The maximum size of the send queue to use on
   *                     the replicationServer.
   * @param maxSendDelay The maximum send delay to use on the replicationServer.
   * @param window The size of the send and receive window to use.
   * @param heartbeatInterval The interval between heartbeats requested of the
   * replicationServer, or zero if no heartbeats are requested.
   */
  public ReplicationBroker(ServerState state, DN baseDn, short serverID,
      int maxReceiveQueue, int maxReceiveDelay, int maxSendQueue,
      int maxSendDelay, int window, long heartbeatInterval)
  {
    this.baseDn = baseDn;
    this.serverID = serverID;
    this.maxReceiveDelay = maxReceiveDelay;
    this.maxSendDelay = maxSendDelay;
    this.maxReceiveQueue = maxReceiveQueue;
    this.maxSendQueue = maxSendQueue;
    this.state = state;
    replayOperations =
      new TreeSet<FakeOperation>(new FakeOperationComparator());
    this.rcvWindow = window;
    this.maxRcvWindow = window;
    this.halfRcvWindow = window/2;
    this.heartbeatInterval = heartbeatInterval;
  }

  /**
   * Start the ReplicationBroker.
   *
   * @param servers list of servers used
   * @throws Exception : in case of errors
   */
  public void start(Collection<String> servers)
                    throws Exception
  {
    /*
     * Open Socket to the ReplicationServer
     * Send the Start message
     */
    shutdown = false;
    this.servers = servers;
    if (servers.size() < 1)
    {
      int    msgID   = MSGID_NEED_MORE_THAN_ONE_CHANGELOG_SERVER;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.NOTICE,
               message, msgID);
    }

    this.rcvWindow = this.maxRcvWindow;
    this.connect();
  }


  /**
   * Connect to a ReplicationServer.
   *
   * @throws NumberFormatException address was invalid
   * @throws IOException error during connection phase
   */
  private void connect() throws NumberFormatException, IOException
  {
    ReplServerStartMessage startMsg;

    // Stop any existing heartbeat monitor from a previous session.
    if (heartbeatMonitor != null)
    {
      heartbeatMonitor.shutdown();
      heartbeatMonitor = null;
    }

    boolean checkState = true;
    while( !connected)
    {
      for (String server : servers)
      {
        int separator = server.lastIndexOf(':');
        String port = server.substring(separator + 1);
        String hostname = server.substring(0, separator);

        try
        {
          /*
           * Open a socket connection to the next candidate.
           */
          InetSocketAddress ServerAddr = new InetSocketAddress(
              InetAddress.getByName(hostname), Integer.parseInt(port));
          Socket socket = new Socket();
          socket.setReceiveBufferSize(1000000);
          socket.setTcpNoDelay(true);
          socket.connect(ServerAddr, 500);
          session = new SocketSession(socket);

          /*
           * Send our ServerStartMessage.
           */
          ServerStartMessage msg = new ServerStartMessage(serverID, baseDn,
              maxReceiveDelay, maxReceiveQueue, maxSendDelay, maxSendQueue,
              halfRcvWindow*2, heartbeatInterval, state);
          session.publish(msg);


          /*
           * Read the ReplServerStartMessage that should come back.
           */
          session.setSoTimeout(1000);
          startMsg = (ReplServerStartMessage) session.receive();
          session.setSoTimeout(timeout);

          /*
           * We must not publish changes to a replicationServer that has not
           * seen all our previous changes because this could cause some
           * other ldap servers to miss those changes.
           * Check that the ReplicationServer has seen all our previous changes.
           * If not, try another replicationServer.
           * If no other replicationServer has seen all our changes, recover
           * those changes and send them again to any replicationServer.
           */
          ChangeNumber replServerMaxChangeNumber =
            startMsg.getServerState().getMaxChangeNumber(serverID);
          if (replServerMaxChangeNumber == null)
            replServerMaxChangeNumber = new ChangeNumber(0, 0, serverID);
          ChangeNumber ourMaxChangeNumber =  state.getMaxChangeNumber(serverID);
          if ((ourMaxChangeNumber == null) ||
              (ourMaxChangeNumber.olderOrEqual(replServerMaxChangeNumber)))
          {
            replicationServer = ServerAddr.toString();
            maxSendWindow = startMsg.getWindowSize();
            this.sendWindow = new Semaphore(maxSendWindow);
            connected = true;
            break;
          }
          else
          {
            if (checkState == true)
            {
              /* This replicationServer is missing some
               * of our changes, we are going to try another server
               * but before log a notice message
               */
              int    msgID   = MSGID_CHANGELOG_MISSING_CHANGES;
              String message = getMessage(msgID, server);
              logError(ErrorLogCategory.SYNCHRONIZATION,
                       ErrorLogSeverity.NOTICE,
                       message, msgID);
            }
            else
            {
              replayOperations.clear();
              /*
               * Get all the changes that have not been seen by this
               * replicationServer and update it
               */
              InternalClientConnection conn =
                  InternalClientConnection.getRootConnection();
              LDAPFilter filter = LDAPFilter.decode(
                  "("+ Historical.HISTORICALATTRIBUTENAME +
                  ">=dummy:" + replServerMaxChangeNumber + ")");
              LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
              attrs.add(Historical.HISTORICALATTRIBUTENAME);
              InternalSearchOperation op = conn.processSearch(
                  new ASN1OctetString(baseDn.toString()),
                  SearchScope.WHOLE_SUBTREE,
                  DereferencePolicy.NEVER_DEREF_ALIASES,
                  0, 0, false, filter,
                  attrs, this);
              if (op.getResultCode() != ResultCode.SUCCESS)
              {
                /*
                 * An error happened trying to search for the updates
                 * This server therefore can't start acepting new updates.
                 * TODO : should stop the LDAP server (how to ?)
                 */
                int    msgID   = MSGID_CANNOT_RECOVER_CHANGES;
                String message = getMessage(msgID);
                logError(ErrorLogCategory.SYNCHRONIZATION,
                         ErrorLogSeverity.FATAL_ERROR,
                         message, msgID);
              }
              else
              {
                replicationServer = ServerAddr.toString();
                maxSendWindow = startMsg.getWindowSize();
                this.sendWindow = new Semaphore(maxSendWindow);
                connected = true;
                for (FakeOperation replayOp : replayOperations)
                {
                  publish(replayOp.generateMessage());
                }
                break;
              }
            }
          }
        }
        catch (ConnectException e)
        {
          /*
           * There was no server waiting on this host:port
           * Log a notice and try the next replicationServer in the list
           */
          int    msgID   = MSGID_NO_CHANGELOG_SERVER_LISTENING;
          String message = getMessage(msgID, server);
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.NOTICE,
                   message, msgID);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_EXCEPTION_STARTING_SESSION;
          String message = getMessage(msgID)  + stackTraceToSingleLineString(e);
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
        finally
        {
          if (connected == false)
          {
            if (session != null)
            {
              logError(ErrorLogCategory.SYNCHRONIZATION,
                  ErrorLogSeverity.NOTICE,
                  "Broker : connect closing session" , 1);

              session.close();
              session = null;
            }
          }
        }
      }

      if (!connected)
      {
        if (checkState == true)
        {
          /*
           * We could not find a replicationServer that has seen all the
           * changes that this server has already processed, start again
           * the loop looking for any replicationServer.
           */
          try
          {
            Thread.sleep(500);
          } catch (InterruptedException e)
          {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          checkState = false;
          int    msgID   = MSGID_COULD_NOT_FIND_CHANGELOG_WITH_MY_CHANGES;
          String message = getMessage(msgID);
          logError(ErrorLogCategory.SYNCHRONIZATION,
              ErrorLogSeverity.NOTICE,
              message, msgID);
        }
        else
        {
          /*
           * This server could not find any replicationServer
           * Let's wait a little and try again.
           */
          synchronized (this)
          {
            checkState = false;
            int    msgID   = MSGID_COULD_NOT_FIND_CHANGELOG;
            String message = getMessage(msgID);
            logError(ErrorLogCategory.SYNCHRONIZATION,
                ErrorLogSeverity.NOTICE,
                message, msgID);
            try
            {
              this.wait(1000);
            } catch (InterruptedException e)
            {
            }
          }
        }
      }
    }

    // Start a heartbeat monitor thread.
    if (heartbeatInterval > 0)
    {
      heartbeatMonitor =
           new HeartbeatMonitor("Replication Heartbeat Monitor", session,
                                heartbeatInterval);
      heartbeatMonitor.start();
    }
  }


  /**
   * Restart the ReplicationServer broker after a failure.
   *
   * @param failingSession the socket which failed
   */
  private void reStart(ProtocolSession failingSession)
  {
    numLostConnections++;

    try
    {
      failingSession.close();
    } catch (IOException e1)
    {
      // ignore
    }

    if (failingSession == session)
    {
      this.connected = false;
    }
    while (!this.connected && (!this.shutdown))
    {
      try
      {
        this.connect();
      } catch (Exception e)
      {
        int    msgID   = MSGID_EXCEPTION_STARTING_SESSION;
        String message = getMessage(msgID) + stackTraceToSingleLineString(e);
        logError(ErrorLogCategory.SYNCHRONIZATION,
                 ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
      }
    }
  }


  /**
   * Publish a message to the other servers.
   * @param msg the message to publish
   */
  public void publish(ReplicationMessage msg)
  {
    boolean done = false;
    ProtocolSession failingSession = session;

    while (!done)
    {
      synchronized (lock)
      {
        try
        {
          if (this.connected == false)
            this.reStart(failingSession);
          if (msg instanceof UpdateMessage)
            sendWindow.acquire();
          session.publish(msg);
          done = true;
        } catch (IOException e)
        {
          this.reStart(failingSession);
        }
        catch (InterruptedException e)
        {
          this.reStart(failingSession);
        }
      }
    }
  }


  /**
   * Receive a message.
   * @return the received message
   * @throws SocketTimeoutException if the timeout set by setSoTimeout
   *         has expired
   */
  public ReplicationMessage receive() throws SocketTimeoutException
  {
    while (shutdown == false)
    {
      ProtocolSession failingSession = session;
      try
      {
        ReplicationMessage msg = session.receive();
        if (msg instanceof WindowMessage)
        {
          WindowMessage windowMsg = (WindowMessage) msg;
          sendWindow.release(windowMsg.getNumAck());
        }
        else
        {
          if (msg instanceof UpdateMessage)
          {
            rcvWindow--;
            if (rcvWindow < halfRcvWindow)
            {
              session.publish(new WindowMessage(halfRcvWindow));
              rcvWindow += halfRcvWindow;
            }
          }
          return msg;
        }
      } catch (SocketTimeoutException e)
      {
        throw e;
      } catch (Exception e)
      {
        if (shutdown == false)
        {
          synchronized (lock)
          {
            this.reStart(failingSession);
          }
        }
      }
    }
    return null;
  }


  /**
   * stop the server.
   */
  public void stop()
  {
    replicationServer = "stopped";
    shutdown = true;
    connected = false;
    try
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("ReplicationBroker Stop Closing session");
      }

      if (session != null)
        session.close();
    } catch (IOException e)
    {}
  }

  /**
   * restart the server after a suspension.
   * @throws Exception in case of errors.
   */
  public void restartReceive() throws Exception
  {
    // TODO Auto-generated method stub

  }

  /**
   * Suspend message reception.
   * @throws Exception in case of errors.
   */
  public void suspendReceive() throws Exception
  {
    // TODO Auto-generated method stub

  }

  /**
   * Set a timeout value.
   * With this option set to a non-zero value, calls to the receive() method
   * block for only this amount of time after which a
   * java.net.SocketTimeoutException is raised.
   * The Broker is valid and useable even after such an Exception is raised.
   *
   * @param timeout the specified timeout, in milliseconds.
   * @throws SocketException if there is an error in the underlying protocol,
   *         such as a TCP error.
   */
  public void setSoTimeout(int timeout) throws SocketException
  {
    this.timeout = timeout;
    session.setSoTimeout(timeout);
  }

  /**
   * Get the name of the replicationServer to which this broker is currently
   * connected.
   *
   * @return the name of the replicationServer to which this domain
   *         is currently connected.
   */
  public String getReplicationServer()
  {
    return replicationServer;
  }
  /**
   * {@inheritDoc}
   */
  public void handleInternalSearchEntry(
      InternalSearchOperation searchOperation,
      SearchResultEntry searchEntry)
  {
    /*
     * Only deal with modify operation so far
     * TODO : implement code for ADD, DEL, MODDN operation
     *
     * Parse all ds-sync-hist attribute values
     *   - for each Changenumber > replication server MaxChangeNumber :
     *          build an attribute mod
     *
     */
    Iterable<FakeOperation> updates =
      Historical.generateFakeOperations(searchEntry);
    for (FakeOperation op : updates)
    {
      replayOperations.add(op);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleInternalSearchReference(
      InternalSearchOperation searchOperation,
      SearchResultReference searchReference)
  {
    // TODO to be implemented
  }

  /**
   * Get the maximum receive window size.
   *
   * @return The maximum receive window size.
   */
  public int getMaxRcvWindow()
  {
    return maxRcvWindow;
  }

  /**
   * Get the current receive window size.
   *
   * @return The current receive window size.
   */
  public int getCurrentRcvWindow()
  {
    return rcvWindow;
  }

  /**
   * Get the maximum send window size.
   *
   * @return The maximum send window size.
   */
  public int getMaxSendWindow()
  {
    return maxSendWindow;
  }

  /**
   * Get the current send window size.
   *
   * @return The current send window size.
   */
  public int getCurrentSendWindow()
  {
    if (connected)
      return sendWindow.availablePermits();
    else
      return 0;
  }

  /**
   * Get the number of times the connection was lost.
   * @return The number of times the connection was lost.
   */
  public int getNumLostConnections()
  {
    return numLostConnections;
  }


  /**
   * Change some config parameters.
   *
   * @param replicationServers    The new list of replication servers.
   * @param maxReceiveQueue     The max size of receive queue.
   * @param maxReceiveDelay     The max receive delay.
   * @param maxSendQueue        The max send queue.
   * @param maxSendDelay        The max Send Delay.
   * @param window              The max window size.
   * @param heartbeatInterval   The heartbeat interval.
   */
  public void changeConfig(Collection<String> replicationServers,
      int maxReceiveQueue, int maxReceiveDelay, int maxSendQueue,
      int maxSendDelay, int window, long heartbeatInterval)
  {
    this.servers = replicationServers;
    this.maxRcvWindow = window;
    this.heartbeatInterval = heartbeatInterval;
    this.maxReceiveDelay = maxReceiveDelay;
    this.maxReceiveQueue = maxReceiveQueue;
    this.maxSendDelay = maxSendDelay;
    this.maxSendQueue = maxSendQueue;
    // TODO : Changing those parameters requires to either restart a new
    // session with the replicationServer or renegociate the parameters that
    // were sent in the ServerStart message
  }
}
