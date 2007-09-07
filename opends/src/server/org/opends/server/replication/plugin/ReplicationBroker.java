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
import org.opends.messages.*;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.*;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
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
  private short protocolVersion;
  private long generationId = -1;
  private ReplSessionSecurity replSessionSecurity;

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
   * When the broker cannot connect to any replication server
   * it log an error and keeps continuing every second.
   * This boolean is set when the first failure happens and is used
   * to avoid repeating the error message for further failure to connect
   * and to know that it is necessary to print a new message when the broker
   * finally succeed to connect.
   */
  private boolean connectionError = false;

  private Object connectPhaseLock = new Object();

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
   *
   * @param generationId The generationId for the server associated to the
   * provided serverID and for the domain associated to the provided baseDN.
   * @param replSessionSecurity The session security configuration.
   */
  public ReplicationBroker(ServerState state, DN baseDn, short serverID,
      int maxReceiveQueue, int maxReceiveDelay, int maxSendQueue,
      int maxSendDelay, int window, long heartbeatInterval,
      long generationId, ReplSessionSecurity replSessionSecurity)
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
    this.protocolVersion = ProtocolVersion.currentVersion();
    this.generationId = generationId;
    this.replSessionSecurity = replSessionSecurity;
  }

  /**
   * Start the ReplicationBroker.
   *
   * @param servers list of servers used
   */
  public void start(Collection<String> servers)
  {
    /*
     * Open Socket to the ReplicationServer
     * Send the Start message
     */
    shutdown = false;
    this.servers = servers;
    if (servers.size() < 1)
    {
      Message message = NOTE_NEED_MORE_THAN_ONE_CHANGELOG_SERVER.get();
      logError(message);
    }

    this.rcvWindow = this.maxRcvWindow;
    this.connect();
  }


  /**
   * Connect to a ReplicationServer.
   *
   * @throws NumberFormatException address was invalid
   */
  private void connect()
  {
    ReplServerStartMessage replServerStartMsg;

    // Stop any existing heartbeat monitor from a previous session.
    if (heartbeatMonitor != null)
    {
      heartbeatMonitor.shutdown();
      heartbeatMonitor = null;
    }

    // checkState is true for the first loop on all replication servers
    // looking for one already up-to-date.
    // If we found some responding replication servers but none up-to-date
    // then we set check-state to false and do a second loop where the first
    // found will be the one elected and then we will update this replication
    // server.
    boolean checkState = true;
    boolean receivedResponse = true;

    // TODO: We are doing here 2 loops opening , closing , reopening session to
    // the same servers .. risk to have 'same server id' erros.
    // Would be better to do only one loop, keeping the best candidate while
    // traversing the list of replication servers to connect to.
    if (servers.size()==1)
    {
      checkState = false;
    }

    synchronized (connectPhaseLock)
    {
      while ((!connected) && (!shutdown) && (receivedResponse))
      {
        receivedResponse = false;
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
            session = replSessionSecurity.createClientSession(server, socket);
            boolean isSslEncryption =
                 replSessionSecurity.isSslEncryption(server);
            /*
             * Send our ServerStartMessage.
             */
            ServerStartMessage msg = new ServerStartMessage(serverID, baseDn,
                maxReceiveDelay, maxReceiveQueue, maxSendDelay, maxSendQueue,
                halfRcvWindow*2, heartbeatInterval, state,
                protocolVersion, generationId, isSslEncryption);
            session.publish(msg);


            /*
             * Read the ReplServerStartMessage that should come back.
             */
            session.setSoTimeout(1000);
            replServerStartMsg = (ReplServerStartMessage) session.receive();
            receivedResponse = true;

            /*
             * We have sent our own protocol version to the replication server.
             * The replication server will use the same one (or an older one
             * if it is an old replication server).
             */
            protocolVersion = ProtocolVersion.minWithCurrent(
                replServerStartMsg.getVersion());
            session.setSoTimeout(timeout);

            if (!isSslEncryption)
            {
              session.stopEncryption();
            }

            /*
             * We must not publish changes to a replicationServer that has not
             * seen all our previous changes because this could cause some
             * other ldap servers to miss those changes.
             * Check that the ReplicationServer has seen all our previous
             * changes.
             * If not, try another replicationServer.
             * If no other replicationServer has seen all our changes, recover
             * those changes and send them again to any replicationServer.
             */
            ChangeNumber replServerMaxChangeNumber =
              replServerStartMsg.getServerState().getMaxChangeNumber(serverID);
            if (replServerMaxChangeNumber == null)
              replServerMaxChangeNumber = new ChangeNumber(0, 0, serverID);
            ChangeNumber ourMaxChangeNumber =
              state.getMaxChangeNumber(serverID);
            if ((ourMaxChangeNumber == null) ||
                (ourMaxChangeNumber.olderOrEqual(replServerMaxChangeNumber)))
            {
              replicationServer = ServerAddr.toString();
              maxSendWindow = replServerStartMsg.getWindowSize();
              connected = true;
              startHeartBeat();
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
                Message message = NOTE_CHANGELOG_MISSING_CHANGES.get(server,
                    baseDn.toNormalizedString());
                logError(message);
              }
              else
              {
                replayOperations.clear();

                 // TODO: i18n
                logError(Message.raw("going to search for changes"));

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
                   * This server will start acepting again new updates but
                   * some inconsistencies will stay between servers.
                   * Log an error for the repair tool
                   * that will need to resynchronize the servers.
                   */
                  Message message = ERR_CANNOT_RECOVER_CHANGES.get(
                      baseDn.toNormalizedString());
                  logError(message);
                }
                else
                {
                  replicationServer = ServerAddr.toString();
                  maxSendWindow = replServerStartMsg.getWindowSize();
                  connected = true;
                  for (FakeOperation replayOp : replayOperations)
                  {
                    logError(Message.raw("sendingChange")); // TODO: i18n
                    session.publish(replayOp.generateMessage());
                  }
                  startHeartBeat();
                  logError(Message.raw("changes sent")); // TODO: i18n
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
            if (!connectionError )
            {
              // the error message is only logged once to avoid overflowing
              // the error log
              Message message = NOTE_NO_CHANGELOG_SERVER_LISTENING.get(server);
              logError(message);
            }
          }
          catch (Exception e)
          {
            Message message = ERR_EXCEPTION_STARTING_SESSION.get(
                baseDn.toNormalizedString(), server, e.getLocalizedMessage() +
                stackTraceToSingleLineString(e));
            logError(message);
          }
          finally
          {
            if (connected == false)
            {
              if (session != null)
              {
                try
                {
                  session.close();
                } catch (IOException e)
                {
                  // The session was already closed, just ignore.
                }
                session = null;
              }
            }
          }
        } // for servers

        // We have traversed all the replication servers

        if ((!connected) && (checkState == true) && receivedResponse)
        {
          /*
           * We could not find a replicationServer that has seen all the
           * changes that this server has already processed, start again
           * the loop looking for any replicationServer.
           */
          Message message = NOTE_COULD_NOT_FIND_CHANGELOG_WITH_MY_CHANGES.get(
              baseDn.toNormalizedString());
          logError(message);
          checkState = false;
        }
      }

      // We have traversed all the replication servers as many times as needed
      // to find one if one is up and running.

      if (connected)
      {
        // This server has connected correctly.
        // Log a message to let the administrator know that the failure was
        // resolved.
        // wakeup all the thread that were waiting on the window
        // on the previous connection.
        connectionError = false;
        if (sendWindow != null)
          sendWindow.release(Integer.MAX_VALUE);
        this.sendWindow = new Semaphore(maxSendWindow);
        connectPhaseLock.notify();
        Message message =
            NOTE_NOW_FOUND_CHANGELOG.get(replicationServer, baseDn.toString());
        logError(message);
      }
      else
      {
        /*
         * This server could not find any replicationServer
         * It's going to start in degraded mode.
         * Log a message
         */
        if (!connectionError)
        {
          checkState = false;
          connectionError = true;
          connectPhaseLock.notify();
          Message message =
              NOTE_COULD_NOT_FIND_CHANGELOG.get(baseDn.toString());
          logError(message);
        }
      }
    }
  }

  /**
   * Start the heartbeat monitor thread.
   */
  private void startHeartBeat()
  {
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
   * restart the ReplicationBroker.
   */
  public void reStart()
  {
    reStart(this.session);
  }

  /**
   * Restart the ReplicationServer broker after a failure.
   *
   * @param failingSession the socket which failed
   */
  public void reStart(ProtocolSession failingSession)
  {
    try
    {
      if (failingSession != null)
      {
        failingSession.close();
        numLostConnections++;
      }
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
        MessageBuilder mb = new MessageBuilder();
        mb.append(NOTE_EXCEPTION_RESTARTING_SESSION.get(
         baseDn.toNormalizedString(), e.getLocalizedMessage()));
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
      }
      if ((!connected) && (!shutdown))
      {
        try
        {
          Thread.sleep(500);
        } catch (InterruptedException e)
        {
          // ignore
        }
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

    while (!done)
    {
      if (connectionError)
      {
        // It was not possible to connect to any replication server.
        // Since the operation was already processed, we have no other
        // choice than to return without sending the ReplicationMessage
        // and relying on the resend procedure of the connect phase to
        // fix the problem when we finally connect.

        if (debugEnabled())
        {
          debugInfo("ReplicationBroker.publish() Publishing a " +
              " message is not possible due to existing connection error.");
        }

        return;
      }

      try
      {
        boolean credit;
        ProtocolSession current_session;
        Semaphore currentWindowSemaphore;

        // save the session at the time when we acquire the
        // sendwindow credit so that we can make sure later
        // that the session did not change in between.
        // This is necessary to make sure that we don't publish a message
        // on a session with a credit that was acquired from a previous
        // session.
        synchronized (connectPhaseLock)
        {
          current_session = session;
          currentWindowSemaphore = sendWindow;
        }

        if (msg instanceof UpdateMessage)
        {
          // Acquiring the window credit must be done outside of the
          // connectPhaseLock because it can be blocking and we don't
          // want to hold off reconnection in case the connection dropped.
          credit =
            currentWindowSemaphore.tryAcquire(
                (long) 500, TimeUnit.MILLISECONDS);
        }
        else
        {
          credit = true;
        }
        if (credit)
        {
          synchronized (connectPhaseLock)
          {
            // check the session. If it has changed, some
            // deconnection/reconnection happened and we need to restart from
            // scratch.
            if (session == current_session)
            {
              session.publish(msg);
              done = true;
            }
          }
        }
        if (!credit)
        {
          // the window is still closed.
          // Send a WindowProbe message to wakeup the receiver in case the
          // window update message was lost somehow...
          // then loop to check again if connection was closed.
          session.publish(new WindowProbe());
        }
      } catch (IOException e)
      {
        // The receive threads should handle reconnection or
        // mark this broker in error. Just retry.
        synchronized (connectPhaseLock)
        {
          try
          {
            connectPhaseLock.wait(100);
          } catch (InterruptedException e1)
          {
            // ignore
            if (debugEnabled())
            {
              debugInfo("ReplicationBroker.publish() " +
                  "IO exception raised : " + e.getLocalizedMessage());
            }
          }
        }
      }
      catch (InterruptedException e)
      {
        // just loop.
        if (debugEnabled())
        {
          debugInfo("ReplicationBroker.publish() " +
              "Interrupted exception raised." + e.getLocalizedMessage());
        }
      }
    }
  }


  /**
   * Receive a message.
   * This method is not multithread safe and should either always be
   * called in a single thread or protected by a locking mechanism
   * before being called.
   *
   * @return the received message
   * @throws SocketTimeoutException if the timeout set by setSoTimeout
   *         has expired
   */
  public ReplicationMessage receive() throws SocketTimeoutException
  {
    while (shutdown == false)
    {
      if (!connected)
      {
        reStart(null);
      }

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
          Message message =
              NOTE_DISCONNECTED_FROM_CHANGELOG.get(replicationServer);
          logError(message);

          debugInfo("ReplicationBroker.receive() " + baseDn +
              " Exception raised." + e + e.getLocalizedMessage());
          this.reStart(failingSession);
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
        debugInfo("ReplicationBroker is stopping. and will" +
          " close the connection");
      }

      if (session != null)
        session.close();
    } catch (IOException e)
    {}
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
    if (session != null)
    {
      session.setSoTimeout(timeout);
    }
  }

  /**
   * Set the value of the generationId for that broker. Normally the
   * generationId is set through the constructor but there are cases
   * where the value of the generationId must be changed while the broker
   * already exist for example after an on-line import.
   *
   * @param generationId The value of the generationId.
   *
   */
  public void setGenerationId(long generationId)
  {
    this.generationId = generationId;
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

  /**
   * Get the version of the replication protocol.
   * @return The version of the replication protocol.
   */
  public short getProtocolVersion()
  {
    return protocolVersion;
  }

  /**
   * Check if the broker is connected to a ReplicationServer and therefore
   * ready to received and send Replication Messages.
   *
   * @return true if the server is connected, false if not.
   */
  public boolean isConnected()
  {
    return !connectionError;
  }

  private boolean debugEnabled() { return true; }
  private static final void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    TRACER.debugInfo(s);
  }

  /**
   * Determine whether the connection to the replication server is encrypted.
   * @return true if the connection is encrypted, false otherwise.
   */
  public boolean isSessionEncrypted()
  {
    boolean isEncrypted = false;
    if (session != null)
    {
      return session.isEncrypted();
    }
    return isEncrypted;
  }
}
