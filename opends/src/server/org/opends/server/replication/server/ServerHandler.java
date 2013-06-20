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
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.ChangeTimeHeartbeatMsg;
import org.opends.server.replication.protocol.HeartbeatThread;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.RoutableMsg;
import org.opends.server.replication.protocol.Session;
import org.opends.server.replication.protocol.StartECLSessionMsg;
import org.opends.server.replication.protocol.StartMsg;
import org.opends.server.replication.protocol.StartSessionMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.protocol.WindowMsg;
import org.opends.server.replication.protocol.WindowProbeMsg;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

/**
 * This class defines a server handler  :
 * - that is a MessageHandler (see this class for more details)
 * - that handles all interaction with a peer server (RS or DS).
 */
public abstract class ServerHandler extends MessageHandler
{
  /**
   * Time during which the server will wait for existing thread to stop
   * during the shutdownWriter.
   */
  private static final int SHUTDOWN_JOIN_TIMEOUT = 30000;

  /**
   * Close the session and log the provided error message
   * Log nothing if message is null.
   * @param providedSession The provided closing session.
   * @param providedMsg     The provided error message.
   * @param handler         The handler that manages that session.
   */
  static protected void closeSession(Session providedSession,
      Message providedMsg, ServerHandler handler)
  {
    if (providedMsg != null)
    {
      if (debugEnabled())
        TRACER.debugInfo("In " +
          ((handler != null) ? handler.toString() : "Replication Server") +
          " closing session with err=" +
          providedMsg.toString());
      logError(providedMsg);
    }

    if (providedSession != null)
    {
      // This method is only called when aborting a failing handshake and
      // not StopMsg should be sent in such situation. StopMsg are only
      // expected when full handshake has been performed, or at end of
      // handshake phase 1, when DS was just gathering available RS info
      providedSession.close();
    }
  }

  /**
   * The serverId of the remote server.
   */
  protected int serverId;
  /**
   * The session opened with the remote server.
   */
  protected Session session;

  /**
   * The serverURL of the remote server.
   */
  protected String serverURL;
  /**
   * Number of updates received from the server in assured safe read mode.
   */
  protected int assuredSrReceivedUpdates = 0;
  /**
   * Number of updates received from the server in assured safe read mode that
   * timed out.
   */
  protected AtomicInteger assuredSrReceivedUpdatesTimeout = new AtomicInteger();
  /**
   * Number of updates sent to the server in assured safe read mode.
   */
  protected int assuredSrSentUpdates = 0;
  /**
   * Number of updates sent to the server in assured safe read mode that timed
   * out.
   */
  protected AtomicInteger assuredSrSentUpdatesTimeout = new AtomicInteger();
  /**
  // Number of updates received from the server in assured safe data mode.
   */
  protected int assuredSdReceivedUpdates = 0;
  /**
   * Number of updates received from the server in assured safe data mode that
   * timed out.
   */
  protected AtomicInteger assuredSdReceivedUpdatesTimeout = new AtomicInteger();
  /**
   * Number of updates sent to the server in assured safe data mode.
   */
  protected int assuredSdSentUpdates = 0;

  /**
   * Number of updates sent to the server in assured safe data mode that timed
   * out.
   */
  protected AtomicInteger assuredSdSentUpdatesTimeout = new AtomicInteger();

  /**
   * The associated ServerWriter that sends messages to the remote server.
   */
  protected ServerWriter writer = null;

  /**
   * The associated ServerReader that receives messages from the remote server.
   */
  protected ServerReader reader;

  // window
  private int rcvWindow;
  private int rcvWindowSizeHalf;

  /**
   * The size of the receiving window.
   */
  protected int maxRcvWindow;
  /**
   * Semaphore that the writer uses to control the flow to the remote server.
   */
  protected Semaphore sendWindow;
  /**
   * The initial size of the sending window.
   */
  int sendWindowSize;
  /**
   * remote generation id.
   */
  protected long generationId = -1;
  /**
   * The generation id of the hosting RS.
   */
  protected long localGenerationId = -1;
  /**
   * The generation id before processing a new start handshake.
   */
  protected long oldGenerationId = -1;
  /**
   * Group id of this remote server.
   */
  protected byte groupId = (byte) -1;
  /**
   * The SSL encryption after the negotiation with the peer.
   */
  protected boolean sslEncryption;
  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  protected long heartbeatInterval = 0;

  /**
   * The thread that will send heartbeats.
   */
  HeartbeatThread heartbeatThread = null;

  /**
   * Set when ServerWriter is stopping.
   */
  protected boolean shutdownWriter = false;

  /**
   * Set when ServerHandler is stopping.
   */
  private AtomicBoolean shuttingDown = new AtomicBoolean(false);

  /**
   * Weight of this remote server.
   */
  protected int weight = 1;

  /**
   * Creates a new server handler instance with the provided socket.
   *
   * @param session The Session used by the ServerHandler to
   *                 communicate with the remote entity.
   * @param queueSize The maximum number of update that will be kept
   *                  in memory by this ServerHandler.
   * @param replicationServerURL The URL of the hosting replication server.
   * @param replicationServerId The serverId of the hosting replication server.
   * @param replicationServer The hosting replication server.
   * @param rcvWindowSize The window size to receive from the remote server.
   */
  public ServerHandler(
      Session session,
      int queueSize,
      String replicationServerURL,
      int replicationServerId,
      ReplicationServer replicationServer,
      int rcvWindowSize)
  {
    super(queueSize, replicationServerURL,
        replicationServerId, replicationServer);
    this.session = session;
    this.rcvWindowSizeHalf = rcvWindowSize / 2;
    this.maxRcvWindow = rcvWindowSize;
    this.rcvWindow = rcvWindowSize;
  }

  /**
   * Abort a start procedure currently establishing.
   * @param reason The provided reason.
   */
  protected void abortStart(Message reason)
  {
    // We did not recognize the message, close session as what
    // can happen after is undetermined and we do not want the server to
    // be disturbed
    Session localSession = session;
    if (localSession != null)
    {
      closeSession(localSession, reason, this);
    }

    if ((replicationServerDomain != null) &&
        replicationServerDomain.hasLock())
      replicationServerDomain.release();

    // If generation id of domain was changed, set it back to old value
    // We may have changed it as it was -1 and we received a value >0 from
    // peer server and the last topo message sent may have failed being
    // sent: in that case retrieve old value of generation id for
    // replication server domain
    if (oldGenerationId != -100)
    {
      if (replicationServerDomain!=null)
        replicationServerDomain.changeGenerationId(oldGenerationId, false);
    }
  }

  /**
   * Check the protocol window and send WindowMsg if necessary.
   *
   * @throws IOException when the session becomes unavailable.
   */
  public synchronized void checkWindow() throws IOException
  {
    if (rcvWindow < rcvWindowSizeHalf)
    {
      WindowMsg msg = new WindowMsg(rcvWindowSizeHalf);
      session.publish(msg);
      rcvWindow += rcvWindowSizeHalf;
    }
  }

  /**
   * Decrement the protocol window, then check if it is necessary
   * to send a WindowMsg and send it.
   *
   * @throws IOException when the session becomes unavailable.
   */
  public synchronized void decAndCheckWindow() throws IOException
  {
    rcvWindow--;
    checkWindow();
  }

  /**
   * Set the shut down flag to true and returns the previous value of the flag.
   * @return The previous value of the shut down flag
   */
  public boolean engageShutdown()
  {
    // Use thread safe boolean
    return shuttingDown.getAndSet(true);
  }

  /**
   * Returns the shutdown flag.
   * @return The shutdown flag value.
   */
  public boolean shuttingDown()
  {
    return shuttingDown.get();
  }

  /**
   * Finalize the initialization, create reader, writer, heartbeat system
   * and monitoring system.
   * @throws DirectoryException When an exception is raised.
   */
  protected void finalizeStart()
  throws DirectoryException
  {
    // FIXME:ECL We should refactor so that a SH always have a session
    if (session != null)
    {
      try
      {
        // Disable timeout for next communications
        session.setSoTimeout(0);
      }
      catch(Exception e)
      { /* do nothing */
      }

      // sendWindow MUST be created before starting the writer
      sendWindow = new Semaphore(sendWindowSize);

      writer = new ServerWriter(session, this,
          replicationServerDomain);
      reader = new ServerReader(session, this);

      session.setName("Replication server RS("
          + this.getReplicationServerId()
          + ") session thread to " + this.toString() + " at "
          + session.getReadableRemoteAddress());
      session.start();
      try
      {
        session.waitForStartup();
      }
      catch (InterruptedException e)
      {
        final Message message =
            ERR_SESSION_STARTUP_INTERRUPTED.get(session.getName());
        throw new DirectoryException(ResultCode.OTHER,
            message, e);
      }
      reader.start();
      writer.start();

      // Create a thread to send heartbeat messages.
      if (heartbeatInterval > 0)
      {
        String threadName = "Replication server RS("
            + this.getReplicationServerId()
            + ") heartbeat publisher to " + this.toString() + " at "
            + session.getReadableRemoteAddress();
        heartbeatThread = new HeartbeatThread(threadName, session,
            heartbeatInterval / 3);
        heartbeatThread.start();
      }
    }

    DirectoryServer.deregisterMonitorProvider(this);
    DirectoryServer.registerMonitorProvider(this);
  }

  /**
   * Sends a message.
   *
   * @param msg
   *          The message to be sent.
   * @throws IOException
   *           When it occurs while sending the message,
   */
  public void send(ReplicationMsg msg) throws IOException
  {
    /*
     * Some unit tests include a null domain, so avoid logging anything in that
     * case.
     */
    if (debugEnabled() && replicationServerDomain != null)
    {
      TRACER.debugInfo("In "
          + replicationServerDomain.getReplicationServer()
              .getMonitorInstanceName() + this + " publishes message:\n" + msg);
    }
    session.publish(msg);
  }

  /**
   * Get the age of the older change that has not yet been replicated
   * to the server handled by this ServerHandler.
   * @return The age if the older change has not yet been replicated
   *         to the server handled by this ServerHandler.
   */
  public Long getApproxFirstMissingDate()
  {
    Long result = (long) 0;

    // Get the older CN received
    ChangeNumber olderUpdateCN = getOlderUpdateCN();
    if (olderUpdateCN != null)
    {
      // If not present in the local RS db,
      // then approximate with the older update time
      result = olderUpdateCN.getTime();
    }
    return result;
  }

  /**
   * Get the number of updates received from the server in assured safe data
   * mode.
   * @return The number of updates received from the server in assured safe data
   * mode
   */
  public int getAssuredSdReceivedUpdates()
  {
    return assuredSdReceivedUpdates;
  }

  /**
   * Get the number of updates received from the server in assured safe data
   * mode that timed out.
   * @return The number of updates received from the server in assured safe data
   * mode that timed out.
   */
  public AtomicInteger getAssuredSdReceivedUpdatesTimeout()
  {
    return assuredSdReceivedUpdatesTimeout;
  }

  /**
   * Get the number of updates sent to the server in assured safe data mode.
   * @return The number of updates sent to the server in assured safe data mode
   */
  public int getAssuredSdSentUpdates()
  {
    return assuredSdSentUpdates;
  }

  /**
   * Get the number of updates sent to the server in assured safe data mode that
   * timed out.
   * @return The number of updates sent to the server in assured safe data mode
   * that timed out.
   */
  public AtomicInteger getAssuredSdSentUpdatesTimeout()
  {
    return assuredSdSentUpdatesTimeout;
  }

  /**
   * Get the number of updates received from the server in assured safe read
   * mode.
   * @return The number of updates received from the server in assured safe read
   * mode
   */
  public int getAssuredSrReceivedUpdates()
  {
    return assuredSrReceivedUpdates;
  }

  /**
   * Get the number of updates received from the server in assured safe read
   * mode that timed out.
   * @return The number of updates received from the server in assured safe read
   * mode that timed out.
   */
  public AtomicInteger getAssuredSrReceivedUpdatesTimeout()
  {
    return assuredSrReceivedUpdatesTimeout;
  }

  /**
   * Get the number of updates sent to the server in assured safe read mode.
   * @return The number of updates sent to the server in assured safe read mode
   */
  public int getAssuredSrSentUpdates()
  {
    return assuredSrSentUpdates;
  }

  /**
   * Get the number of updates sent to the server in assured safe read mode that
   * timed out.
   * @return The number of updates sent to the server in assured safe read mode
   * that timed out.
   */
  public AtomicInteger getAssuredSrSentUpdatesTimeout()
  {
    return assuredSrSentUpdatesTimeout;
  }

  /**
   * Returns the Replication Server Domain to which belongs this server handler.
   *
   * @return The replication server domain.
   */
  public ReplicationServerDomain getDomain()
  {
    return this.replicationServerDomain;
  }

  /**
   * Returns the value of generationId for that handler.
   * @return The value of the generationId.
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * Gets the group id of the server represented by this object.
   * @return The group id of the server represented by this object.
   */
  public byte getGroupId()
  {
    return groupId;
  }

  /**
   * Get our heartbeat interval.
   * @return Our heartbeat interval.
   */
  public long getHeartbeatInterval()
  {
    return heartbeatInterval;
  }

  /**
   * Get the count of updates received from the server.
   * @return the count of update received from the server.
   */
  public int getInCount()
  {
    return inCount;
  }

  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    // Get the generic ones
    ArrayList<Attribute> attributes = super.getMonitorData();

    attributes.add(Attributes.create("server-id", String.valueOf(serverId)));
    attributes.add(Attributes.create("domain-name", getServiceId()));

    // Deprecated
    attributes.add(Attributes.create("max-waiting-changes", String
        .valueOf(maxQueueSize)));
    attributes.add(Attributes.create("sent-updates", String
        .valueOf(getOutCount())));
    attributes.add(Attributes.create("received-updates", String
        .valueOf(getInCount())));

    // Assured counters
    attributes.add(Attributes.create("assured-sr-received-updates", String
        .valueOf(getAssuredSrReceivedUpdates())));
    attributes.add(Attributes.create("assured-sr-received-updates-timeout",
        String .valueOf(getAssuredSrReceivedUpdatesTimeout())));
    attributes.add(Attributes.create("assured-sr-sent-updates", String
        .valueOf(getAssuredSrSentUpdates())));
    attributes.add(Attributes.create("assured-sr-sent-updates-timeout", String
        .valueOf(getAssuredSrSentUpdatesTimeout())));
    attributes.add(Attributes.create("assured-sd-received-updates", String
        .valueOf(getAssuredSdReceivedUpdates())));
    if (!isDataServer())
    {
      attributes.add(Attributes.create("assured-sd-sent-updates",
          String.valueOf(getAssuredSdSentUpdates())));
      attributes.add(Attributes.create("assured-sd-sent-updates-timeout",
          String.valueOf(getAssuredSdSentUpdatesTimeout())));
    } else
    {
      attributes.add(Attributes.create("assured-sd-received-updates-timeout",
          String.valueOf(getAssuredSdReceivedUpdatesTimeout())));
    }

    // Window stats
    attributes.add(Attributes.create("max-send-window", String
        .valueOf(sendWindowSize)));
    attributes.add(Attributes.create("current-send-window", String
        .valueOf(sendWindow.availablePermits())));
    attributes.add(Attributes.create("max-rcv-window", String
        .valueOf(maxRcvWindow)));
    attributes.add(Attributes.create("current-rcv-window", String
        .valueOf(rcvWindow)));

    // Encryption
    attributes.add(Attributes.create("ssl-encryption", String
        .valueOf(session.isEncrypted())));

    // Data generation
    attributes.add(Attributes.create("generation-id", String
        .valueOf(generationId)));

    return attributes;
  }

  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  @Override
  public abstract String getMonitorInstanceName();

  /**
   * Get the count of updates sent to this server.
   * @return  The count of update sent to this server.
   */
  public int getOutCount()
  {
    return outCount;
  }

  /**
   * Gets the protocol version used with this remote server.
   * @return The protocol version used with this remote server.
   */
  public short getProtocolVersion()
  {
    return session.getProtocolVersion();
  }

  /**
   * get the Server Id.
   *
   * @return the ID of the server to which this object is linked
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * Retrieves the URL for this server handler.
   *
   * @return  The URL for this server handler, in the form of an address and
   *          port separated by a colon.
   */
  public String getServerURL()
  {
    return serverURL;
  }

  /**
   * Return the ServerStatus.
   * @return The server status.
   */
  protected abstract ServerStatus getStatus();

  /**
   * Increment the number of updates received from the server in assured safe
   * data mode.
   */
  public void incrementAssuredSdReceivedUpdates()
  {
    assuredSdReceivedUpdates++;
  }

  /**
   * Increment the number of updates received from the server in assured safe
   * data mode that timed out.
   */
  public void incrementAssuredSdReceivedUpdatesTimeout()
  {
    assuredSdReceivedUpdatesTimeout.incrementAndGet();
  }

  /**
   * Increment the number of updates sent to the server in assured safe data
   * mode.
   */
  public void incrementAssuredSdSentUpdates()
  {
    assuredSdSentUpdates++;
  }

  /**
   * Increment the number of updates sent to the server in assured safe data
   * mode that timed out.
   */
  public void incrementAssuredSdSentUpdatesTimeout()
  {
    assuredSdSentUpdatesTimeout.incrementAndGet();
  }

  /**
   * Increment the number of updates received from the server in assured safe
   * read mode.
   */
  public void incrementAssuredSrReceivedUpdates()
  {
    assuredSrReceivedUpdates++;
  }

  /**
   * Increment the number of updates received from the server in assured safe
   * read mode that timed out.
   */
  public void incrementAssuredSrReceivedUpdatesTimeout()
  {
    assuredSrReceivedUpdatesTimeout.incrementAndGet();
  }

  /**
   * Increment the number of updates sent to the server in assured safe read
   * mode.
   */
  public void incrementAssuredSrSentUpdates()
  {
    assuredSrSentUpdates++;
  }

  /**
   * Increment the number of updates sent to the server in assured safe read
   * mode that timed out.
   */
  public void incrementAssuredSrSentUpdatesTimeout()
  {
    assuredSrSentUpdatesTimeout.incrementAndGet();
  }

  /**
   * Increase the counter of update received from the server.
   */
  public void incrementInCount()
  {
    inCount++;
  }

  /**
   * Increase the counter of updates sent to the server.
   */
  public void incrementOutCount()
  {
    outCount++;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
  throws ConfigException, InitializationException
  {
    // Nothing to do for now
  }

  /**
   * Check if the server associated to this ServerHandler is a data server
   * in the topology.
   * @return true if the server is a data server.
   */
  public abstract boolean isDataServer();

  /**
   * Check if the server associated to this ServerHandler is a replication
   * server.
   * @return true if the server is a replication server.
   */
  public boolean isReplicationServer()
  {
    return (!this.isDataServer());
  }



  /**
   * Lock the domain potentially with a timeout.
   *
   * @param timedout
   *          The provided timeout.
   * @throws DirectoryException
   *           When an exception occurs.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting for the lock.
   */
  protected void lockDomain(boolean timedout)
    throws DirectoryException, InterruptedException
  {
    // The handshake phase must be done by blocking any access to structures
    // keeping info on connected servers, so that one can safely check for
    // pre-existence of a server, send a coherent snapshot of known topology
    // to peers, update the local view of the topology...
    //
    // For instance a kind of problem could be that while we connect with a
    // peer RS, a DS is connecting at the same time and we could publish the
    // connected DSs to the peer RS forgetting this last DS in the TopologyMsg.
    //
    // This method and every others that need to read/make changes to the
    // structures holding topology for the domain should:
    // - call ReplicationServerDomain.lock()
    // - read/modify structures
    // - call ReplicationServerDomain.release()
    //
    // More information is provided in comment of ReplicationServerDomain.lock()

    // If domain already exists, lock it until handshake is finished otherwise
    // it will be created and locked later in the method
    if (!timedout)
    {
      // !timedout
      if (!replicationServerDomain.hasLock())
        replicationServerDomain.lock();
    }
    else
    {
      // timedout
      /**
       * Take the lock on the domain.
       * WARNING: Here we try to acquire the lock with a timeout. This
       * is for preventing a deadlock that may happen if there are cross
       * connection attempts (for same domain) from this replication
       * server and from a peer one:
       * Here is the scenario:
       * - RS1 connect thread takes the domain lock and starts
       * connection to RS2
       * - at the same time RS2 connect thread takes his domain lock and
       * start connection to RS2
       * - RS2 listen thread starts processing received
       * ReplServerStartMsg from RS1 and wants to acquire the lock on
       * the domain (here) but cannot as RS2 connect thread already has
       * it
       * - RS1 listen thread starts processing received
       * ReplServerStartMsg from RS2 and wants to acquire the lock on
       * the domain (here) but cannot as RS1 connect thread already has
       * it
       * => Deadlock: 4 threads are locked.
       * So to prevent that in such situation, the listen threads here
       * will both timeout trying to acquire the lock. The random time
       * for the timeout should allow on connection attempt to be
       * aborted whereas the other one should have time to finish in the
       * same time.
       * Warning: the minimum time (3s) should be big enough to allow
       * normal situation connections to terminate. The added random
       * time should represent a big enough range so that the chance to
       * have one listen thread timing out a lot before the peer one is
       * great. When the first listen thread times out, the remote
       * connect thread should release the lock and allow the peer
       * listen thread to take the lock it was waiting for and process
       * the connection attempt.
       */
      Random random = new Random();
      int randomTime = random.nextInt(6); // Random from 0 to 5
      // Wait at least 3 seconds + (0 to 5 seconds)
      long timeout = (long) (3000 + ( randomTime * 1000 ) );
      boolean noTimeout = replicationServerDomain.tryLock(timeout);
      if (!noTimeout)
      {
        // Timeout
        Message message = WARN_TIMEOUT_WHEN_CROSS_CONNECTION.get(
            getServiceId(),
            serverId,
            session.getReadableRemoteAddress(),
            replicationServerId);
        throw new DirectoryException(ResultCode.OTHER, message);
      }
    }
  }

  /**
   * Processes a routable message.
   *
   * @param msg The message to be processed.
   */
  public void process(RoutableMsg msg)
  {
    if (debugEnabled())
      TRACER.debugInfo("In " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() + this +
          " processes routable msg received:" + msg);
    replicationServerDomain.process(msg, this);
  }

  /**
   * Processes a change time heartbeat msg.
   *
   * @param msg The message to be processed.
   */
  public void process(ChangeTimeHeartbeatMsg msg)
  {
    if (debugEnabled())
      TRACER.debugInfo("In " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() + this +
          " processes received msg:\n" + msg);
    replicationServerDomain.processChangeTimeHeartbeatMsg(this, msg);
  }

  /**
   * Process the reception of a WindowProbeMsg message.
   *
   * @param  windowProbeMsg The message to process.
   *
   * @throws IOException    When the session becomes unavailable.
   */
  public void process(WindowProbeMsg windowProbeMsg) throws IOException
  {
    if (rcvWindow > 0)
    {
      // The LDAP server believes that its window is closed
      // while it is not, this means that some problem happened in the
      // window exchange procedure !
      // lets update the LDAP server with out current window size and hope
      // that everything will work better in the futur.
      // TODO also log an error message.
      WindowMsg msg = new WindowMsg(rcvWindow);
      session.publish(msg);
    } else
    {
      // Both the LDAP server and the replication server believes that the
      // window is closed. Lets check the flowcontrol in case we
      // can now resume operations and send a windowMessage if necessary.
      checkWindow();
    }
  }

  /**
   * Sends the provided TopologyMsg to the peer server.
   *
   * @param topoMsg
   *          The TopologyMsg message to be sent.
   * @throws IOException
   *           When it occurs while sending the message,
   */
  public void sendTopoInfo(TopologyMsg topoMsg) throws IOException
  {
    // V1 Rs do not support the TopologyMsg
    if (getProtocolVersion() > ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      send(topoMsg);
    }
  }

  /**
   * Set a new generation ID.
   *
   * @param generationId The new generation ID
   *
   */
  public void setGenerationId(long generationId)
  {
    this.generationId = generationId;
  }

  /**
   * Sets the replication server domain associated.
   * @param rsd The provided replication server domain.
   */
  protected void setReplicationServerDomain(ReplicationServerDomain rsd)
  {
    this.replicationServerDomain = rsd;
  }

  /**
   * Sets the window size when used when sending to the remote.
   * @param size The provided window size.
   */
  protected void setSendWindowSize(int size)
  {
    this.sendWindowSize = size;
  }

  /**
   * Requests to shutdown the writer.
   */
  protected void shutdownWriter()
  {
    shutdownWriter = true;
  }

  /**
   * Shutdown This ServerHandler.
   */
  public void shutdown()
  {
    shutdownWriter();
    setConsumerActive(false);
    super.shutdown();

    if (session != null)
    {
      session.close();
    }

    /*
     * Stop the heartbeat thread.
     */
    if (heartbeatThread != null)
    {
      heartbeatThread.shutdown();
    }

    DirectoryServer.deregisterMonitorProvider(this);

    /*
     * Be sure to wait for ServerWriter and ServerReader death
     * It does not matter if we try to stop a thread which is us (reader
     * or writer), but we must not wait for our own thread death.
     */
    try
    {
      if ((writer != null) && (!(Thread.currentThread().equals(writer))))
      {

        writer.join(SHUTDOWN_JOIN_TIMEOUT);
      }
      if ((reader != null) && (!(Thread.currentThread().equals(reader))))
      {
        reader.join(SHUTDOWN_JOIN_TIMEOUT);
      }
    } catch (InterruptedException e)
    {
      // don't try anymore to join and return.
    }
    if (debugEnabled())
      TRACER.debugInfo("SH.shutdowned(" + this + ")");
  }

  /**
   * Select the next update that must be sent to the server managed by this
   * ServerHandler.
   *
   * @return the next update that must be sent to the server managed by this
   *         ServerHandler.
   */
  public UpdateMsg take()
  {
    boolean interrupted = true;
    UpdateMsg msg = getNextMessage(true); // synchronous:block until msg

    boolean acquired = false;
    do
    {
      try
      {
        acquired = sendWindow.tryAcquire((long) 500, TimeUnit.MILLISECONDS);
        interrupted = false;
      } catch (InterruptedException e)
      {
        // loop until not interrupted
      }
    } while (((interrupted) || (!acquired)) && (!shutdownWriter));
    if (msg != null)
    {
      incrementOutCount();

      if (msg.isAssured())
      {
        if (msg.getAssuredMode() == AssuredMode.SAFE_READ_MODE)
        {
          incrementAssuredSrSentUpdates();
        } else
        {
          if (!isDataServer())
            incrementAssuredSdSentUpdates();
        }
      }
    }
    return msg;
  }

  /**
   * Creates a RSInfo structure representing this remote RS.
   * @return The RSInfo structure representing this remote RS
   */
  public RSInfo toRSInfo()
  {

    return new RSInfo(serverId, serverURL, generationId, groupId,
      weight);
  }

  /**
   * Starts the monitoring publisher for the domain if not already started.
   */
  protected void createMonitoringPublisher()
  {
    if (!replicationServerDomain.isRunningMonitoringPublisher())
    {
      replicationServerDomain.startMonitoringPublisher();
    }
  }
  /**
   * Update the send window size based on the credit specified in the
   * given window message.
   *
   * @param windowMsg The Window Message containing the information
   *                  necessary for updating the window size.
   */
  public void updateWindow(WindowMsg windowMsg)
  {
    sendWindow.release(windowMsg.getNumAck());
  }

  /**
   * Log the messages involved in the start handshake.
   * @param inStartMsg The message received first.
   * @param outStartMsg The message sent in response.
   */
  protected void logStartHandshakeRCVandSND(
      StartMsg inStartMsg,
      StartMsg outStartMsg)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " +
        this.replicationServer.getMonitorInstanceName() + ", " +
        this.getClass().getSimpleName() + " " + this + ":" +
        "\nSH START HANDSHAKE RECEIVED:\n" + inStartMsg.toString()+
        "\nAND REPLIED:\n" + outStartMsg.toString());
    }
  }

  /**
   * Log the messages involved in the start handshake.
   * @param outStartMsg The message sent first.
   * @param inStartMsg The message received in response.
   */
  protected void logStartHandshakeSNDandRCV(
      StartMsg outStartMsg,
      StartMsg inStartMsg)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " +
        this.replicationServer.getMonitorInstanceName() + ", " +
        this.getClass().getSimpleName() + " " + this + ":" +
        "\nSH START HANDSHAKE SENT("+ this +
        "):\n" + outStartMsg.toString()+
        "\nAND RECEIVED:\n" + inStartMsg.toString());
    }
  }

  /**
   * Log the messages involved in the Topology handshake.
   * @param inTopoMsg The message received first.
   * @param outTopoMsg The message sent in response.
   */
  protected void logTopoHandshakeRCVandSND(
      TopologyMsg inTopoMsg,
      TopologyMsg outTopoMsg)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " +
          this.replicationServer.getMonitorInstanceName() + ", " +
          this.getClass().getSimpleName() + " " + this + ":" +
          "\nSH TOPO HANDSHAKE RECEIVED:\n" + inTopoMsg.toString() +
          "\nAND REPLIED:\n" + outTopoMsg.toString());
    }
  }

  /**
   * Log the messages involved in the Topology handshake.
   * @param outTopoMsg The message sent first.
   * @param inTopoMsg The message received in response.
   */
  protected void logTopoHandshakeSNDandRCV(
      TopologyMsg outTopoMsg,
      TopologyMsg inTopoMsg)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " +
          this.replicationServer.getMonitorInstanceName() + ", " +
          this.getClass().getSimpleName() + " " + this + ":" +
          "\nSH TOPO HANDSHAKE SENT:\n" + outTopoMsg.toString() +
          "\nAND RECEIVED:\n" + inTopoMsg.toString());
    }
  }

  /**
   * Log the messages involved in the Topology/StartSession handshake.
   * @param inStartSessionMsg The message received first.
   * @param outTopoMsg The message sent in response.
   */
  protected void logStartSessionHandshake(
      StartSessionMsg inStartSessionMsg,
      TopologyMsg outTopoMsg)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " +
          this.replicationServer.getMonitorInstanceName() + ", " +
          this.getClass().getSimpleName() + " " + this + " :" +
          "\nSH SESSION HANDSHAKE RECEIVED:\n" + inStartSessionMsg.toString() +
          "\nAND REPLIED:\n" + outTopoMsg.toString());
    }
  }

  /**
   * Log stop message has been received.
   */
  protected void logStopReceived()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " +
          this.replicationServer.getMonitorInstanceName() + ", " +
          this.getClass().getSimpleName() + " " + this + " :" +
          "\nSH SESSION HANDSHAKE RECEIVED A STOP MESSAGE");
    }
  }

  /**
   * Log the messages involved in the Topology/StartSession handshake.
   * @param inStartECLSessionMsg The message received first.
   */
  protected void logStartECLSessionHandshake(
      StartECLSessionMsg inStartECLSessionMsg)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In " +
          this.replicationServer.getMonitorInstanceName() + ", " +
          this.getClass().getSimpleName() + " " + this + " :" +
          "\nSH SESSION HANDSHAKE RECEIVED:\n" +
          inStartECLSessionMsg.toString());
    }
  }

  /**
   * Process a Ack message received.
   * @param ack the message received.
   */
  public void processAck(AckMsg ack)
  {
    if (replicationServerDomain!=null)
      replicationServerDomain.processAck(ack, this);
  }

  /**
   * Get the reference generation id (associated with the changes in the db).
   * @return the reference generation id.
   */
  public long getReferenceGenId()
  {
    long refgenid = -1;
    if (replicationServerDomain!=null)
      refgenid = replicationServerDomain.getGenerationId();
    return refgenid;
  }

  /**
   * Process a ResetGenerationIdMsg message received.
   * @param msg the message received.
   */
  public void processResetGenId(ResetGenerationIdMsg msg)
  {
    if (replicationServerDomain!=null)
      replicationServerDomain.resetGenerationId(this, msg);
  }

  /**
   * Put a new update message received.
   * @param update the update message received.
   * @throws IOException when it occurs.
   */
  public void put(UpdateMsg update)
  throws IOException
  {
    if (replicationServerDomain!=null)
      replicationServerDomain.put(update, this);
  }

  /**
   * Stop this handler.
   */
  public void doStop()
  {
    if (replicationServerDomain!=null)
      replicationServerDomain.stopServer(this, false);
  }
}
