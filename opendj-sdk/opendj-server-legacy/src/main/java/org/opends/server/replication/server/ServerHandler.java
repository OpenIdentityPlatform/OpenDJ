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

import static org.opends.messages.ReplicationMessages.*;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class defines a server handler  :
 * - that is a MessageHandler (see this class for more details)
 * - that handles all interaction with a peer server (RS or DS).
 */
public abstract class ServerHandler extends MessageHandler
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Time during which the server will wait for existing thread to stop
   * during the shutdownWriter.
   */
  private static final int SHUTDOWN_JOIN_TIMEOUT = 30000;

  /**
   * The serverId of the remote server.
   */
  protected int serverId;
  /**
   * The session opened with the remote server.
   */
  protected final Session session;

  /**
   * The serverURL of the remote server.
   */
  protected String serverURL;
  /**
   * Number of updates received from the server in assured safe read mode.
   */
  private int assuredSrReceivedUpdates;
  /**
   * Number of updates received from the server in assured safe read mode that
   * timed out.
   */
  private final AtomicInteger assuredSrReceivedUpdatesTimeout = new AtomicInteger();
  /**
   * Number of updates sent to the server in assured safe read mode.
   */
  private int assuredSrSentUpdates;
  /**
   * Number of updates sent to the server in assured safe read mode that timed
   * out.
   */
  private final AtomicInteger assuredSrSentUpdatesTimeout = new AtomicInteger();
  /**
   * Number of updates received from the server in assured safe data mode.
   */
  private int assuredSdReceivedUpdates;
  /**
   * Number of updates received from the server in assured safe data mode that
   * timed out.
   */
  private final AtomicInteger assuredSdReceivedUpdatesTimeout = new AtomicInteger();
  /**
   * Number of updates sent to the server in assured safe data mode.
   */
  private int assuredSdSentUpdates;

  /**
   * Number of updates sent to the server in assured safe data mode that timed out.
   */
  private final AtomicInteger assuredSdSentUpdatesTimeout = new AtomicInteger();

  /**
   * The associated ServerWriter that sends messages to the remote server.
   */
  private ServerWriter writer;

  /**
   * The associated ServerReader that receives messages from the remote server.
   */
  private ServerReader reader;

  /** Window. */
  private int rcvWindow;
  private final int rcvWindowSizeHalf;

  /** The size of the receiving window. */
  protected final int maxRcvWindow;
  /** Semaphore that the writer uses to control the flow to the remote server. */
  private Semaphore sendWindow;
  /** The initial size of the sending window. */
  private int sendWindowSize;
  /** Remote generation id. */
  protected long generationId = -1;
  /** The generation id of the hosting RS. */
  protected long localGenerationId = -1;
  /** The generation id before processing a new start handshake. */
  protected long oldGenerationId = -1;
  /** Group id of this remote server. */
  protected byte groupId = -1;
  /** The SSL encryption after the negotiation with the peer. */
  protected boolean sslEncryption;
  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  protected long heartbeatInterval;

  /** The thread that will send heartbeats. */
  private HeartbeatThread heartbeatThread;

  /** Set when ServerWriter is stopping. */
  private volatile boolean shutdownWriter;

  /** Weight of this remote server. */
  protected int weight = 1;

  /**
   * Creates a new server handler instance with the provided socket.
   *
   * @param session The Session used by the ServerHandler to
   *                 communicate with the remote entity.
   * @param queueSize The maximum number of update that will be kept
   *                  in memory by this ServerHandler.
   * @param replicationServer The hosting replication server.
   * @param rcvWindowSize The window size to receive from the remote server.
   */
  public ServerHandler(
      Session session,
      int queueSize,
      ReplicationServer replicationServer,
      int rcvWindowSize)
  {
    super(queueSize, replicationServer);
    this.session = session;
    this.rcvWindowSizeHalf = rcvWindowSize / 2;
    this.maxRcvWindow = rcvWindowSize;
    this.rcvWindow = rcvWindowSize;
  }

  /**
   * Abort a start procedure currently establishing.
   * @param reason The provided reason.
   */
  protected void abortStart(LocalizableMessage reason)
  {
    // We did not recognize the message, close session as what can happen after
    // is undetermined and we do not want the server to be disturbed
    Session localSession = session;
    if (localSession != null)
    {
      if (reason != null)
      {
        if (logger.isTraceEnabled())
        {
         logger.trace("In " + this + " closing session with err=" + reason);
        }
        logger.error(reason);
      }

      // This method is only called when aborting a failing handshake and
      // not StopMsg should be sent in such situation. StopMsg are only
      // expected when full handshake has been performed, or at end of
      // handshake phase 1, when DS was just gathering available RS info
      localSession.close();
    }

    releaseDomainLock();

    // If generation id of domain was changed, set it back to old value
    // We may have changed it as it was -1 and we received a value >0 from peer
    // server and the last topo message sent may have failed being sent: in that
    // case retrieve old value of generation id for replication server domain
    if (oldGenerationId != -100)
    {
      replicationServerDomain.changeGenerationId(oldGenerationId);
    }
  }

  /**
   * Releases the lock on the replication server domain if it was held.
   */
  protected void releaseDomainLock()
  {
    if (replicationServerDomain.hasLock())
    {
      replicationServerDomain.release();
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
  private synchronized void decAndCheckWindow() throws IOException
  {
    rcvWindow--;
    checkWindow();
  }

  /**
   * Finalize the initialization, create reader, writer, heartbeat system
   * and monitoring system.
   * @throws DirectoryException When an exception is raised.
   */
  protected void finalizeStart() throws DirectoryException
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

      writer = new ServerWriter(session, this, replicationServerDomain,
          replicationServer.getDSRSShutdownSync());
      reader = new ServerReader(session, this);

      session.setName("Replication server RS(" + getReplicationServerId()
          + ") session thread to " + this + " at "
          + session.getReadableRemoteAddress());
      session.start();
      try
      {
        session.waitForStartup();
      }
      catch (InterruptedException e)
      {
        final LocalizableMessage message =
            ERR_SESSION_STARTUP_INTERRUPTED.get(session.getName());
        throw new DirectoryException(ResultCode.OTHER, message, e);
      }
      reader.start();
      writer.start();

      // Create a thread to send heartbeat messages.
      if (heartbeatInterval > 0)
      {
        String threadName = "Replication server RS(" + getReplicationServerId()
            + ") heartbeat publisher to " + this + " at "
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
    // avoid logging anything for unit tests that include a null domain.
    if (logger.isTraceEnabled())
    {
      logger.trace("In "
          + replicationServerDomain.getLocalRSMonitorInstanceName() + " "
          + this + " publishes message:\n" + msg);
    }
    session.publish(msg);
  }

  /**
   * Get the age of the older change that has not yet been replicated
   * to the server handled by this ServerHandler.
   * @return The age if the older change has not yet been replicated
   *         to the server handled by this ServerHandler.
   */
  public long getApproxFirstMissingDate()
  {
    // Get the older CSN received
    CSN olderUpdateCSN = getOlderUpdateCSN();
    if (olderUpdateCSN != null)
    {
      // If not present in the local RS db,
      // then approximate with the older update time
      return olderUpdateCSN.getTime();
    }
    return 0;
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
    return replicationServerDomain;
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

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    // Get the generic ones
    List<Attribute> attributes = super.getMonitorData();

    attributes.add(Attributes.create("server-id", String.valueOf(serverId)));
    attributes.add(Attributes.create("domain-name", String.valueOf(getBaseDN())));

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
    attributes.add(Attributes.create("max-send-window", String.valueOf(sendWindowSize)));
    attributes.add(Attributes.create("current-send-window", String.valueOf(sendWindow.availablePermits())));
    attributes.add(Attributes.create("max-rcv-window", String.valueOf(maxRcvWindow)));
    attributes.add(Attributes.create("current-rcv-window", String.valueOf(rcvWindow)));

    // Encryption
    attributes.add(Attributes.create("ssl-encryption", String.valueOf(session.isEncrypted())));

    // Data generation
    attributes.add(Attributes.create("generation-id", String.valueOf(generationId)));

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
   * Gets the protocol version used with this remote server.
   * @return The protocol version used with this remote server.
   */
  public short getProtocolVersion()
  {
    return session.getProtocolVersion();
  }

  /**
   * Get the Server Id.
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
   * mode that timed out.
   */
  public void incrementAssuredSrSentUpdatesTimeout()
  {
    assuredSrSentUpdatesTimeout.incrementAndGet();
  }

  /** {@inheritDoc} */
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
    return !isDataServer();
  }

  // The handshake phase must be done by blocking any access to structures
  // keeping info on connected servers, so that one can safely check for
  // pre-existence of a server, send a coherent snapshot of known topology to
  // peers, update the local view of the topology...
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

  /**
   * Lock the domain without a timeout.
   * <p>
   * If domain already exists, lock it until handshake is finished otherwise it
   * will be created and locked later in the method
   *
   * @throws DirectoryException
   *           When an exception occurs.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting for the lock.
   */
  public void lockDomainNoTimeout() throws DirectoryException,
      InterruptedException
  {
    if (!replicationServerDomain.hasLock())
    {
      replicationServerDomain.lock();
    }
  }

  /**
   * Lock the domain with a timeout.
   * <p>
   * Take the lock on the domain. WARNING: Here we try to acquire the lock with
   * a timeout. This is for preventing a deadlock that may happen if there are
   * cross connection attempts (for same domain) from this replication server
   * and from a peer one.
   * <p>
   * Here is the scenario:
   * <ol>
   * <li>RS1 connect thread takes the domain lock and starts connection to RS2
   * </li>
   * <li>at the same time RS2 connect thread takes his domain lock and start
   * connection to RS2</li>
   * <li>RS2 listen thread starts processing received ReplServerStartMsg from
   * RS1 and wants to acquire the lock on the domain (here) but cannot as RS2
   * connect thread already has it</li>
   * <li>RS1 listen thread starts processing received ReplServerStartMsg from
   * RS2 and wants to acquire the lock on the domain (here) but cannot as RS1
   * connect thread already has it</li>
   * </ol>
   * => Deadlock: 4 threads are locked.
   * <p>
   * To prevent threads locking in such situation, the listen threads here will
   * both timeout trying to acquire the lock. The random time for the timeout
   * should allow on connection attempt to be aborted whereas the other one
   * should have time to finish in the same time.
   * <p>
   * Warning: the minimum time (3s) should be big enough to allow normal
   * situation connections to terminate. The added random time should represent
   * a big enough range so that the chance to have one listen thread timing out
   * a lot before the peer one is great. When the first listen thread times out,
   * the remote connect thread should release the lock and allow the peer listen
   * thread to take the lock it was waiting for and process the connection
   * attempt.
   *
   * @throws DirectoryException
   *           When an exception occurs.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting for the lock.
   */
  public void lockDomainWithTimeout() throws DirectoryException,
      InterruptedException
  {
    final Random random = new Random();
    final int randomTime = random.nextInt(6); // Random from 0 to 5
    // Wait at least 3 seconds + (0 to 5 seconds)
    final long timeout = 3000 + (randomTime * 1000);
    final boolean lockAcquired = replicationServerDomain.tryLock(timeout);
    if (!lockAcquired)
    {
      LocalizableMessage message = WARN_TIMEOUT_WHEN_CROSS_CONNECTION.get(
          getBaseDN(), serverId, session.getReadableRemoteAddress(), getReplicationServerId());
      throw new DirectoryException(ResultCode.OTHER, message);
    }
  }

  /**
   * Processes a routable message.
   *
   * @param msg The message to be processed.
   */
  void process(RoutableMsg msg)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("In "
          + replicationServerDomain.getLocalRSMonitorInstanceName() + " "
          + this + " processes routable msg received:" + msg);
    }
    replicationServerDomain.process(msg, this);
  }

  /**
   * Responds to a monitor request message.
   *
   * @param msg
   *          The monitor request message.
   */
  void processMonitorRequestMsg(MonitorRequestMsg msg)
  {
    replicationServerDomain.processMonitorRequestMsg(msg, this);
  }

  /**
   * Responds to a monitor message.
   *
   * @param msg
   *          The monitor message.
   */
  void processMonitorMsg(MonitorMsg msg)
  {
    replicationServerDomain.processMonitorMsg(msg, this);
  }

  /**
   * Processes a change time heartbeat msg.
   *
   * @param msg
   *          The message to be processed.
   * @throws DirectoryException
   *           When an exception is raised.
   */
  void process(ChangeTimeHeartbeatMsg msg) throws DirectoryException
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("In "
          + replicationServerDomain.getLocalRSMonitorInstanceName() + " "
          + this + " processes received msg:\n" + msg);
    }
    replicationServerDomain.processChangeTimeHeartbeatMsg(this, msg);
  }

  /**
   * Process the reception of a WindowProbeMsg message.
   *
   * @throws IOException
   *           When the session becomes unavailable.
   */
  public void replyToWindowProbe() throws IOException
  {
    if (rcvWindow > 0)
    {
      // The LDAP server believes that its window is closed while it is not,
      // this means that some problem happened in the window exchange procedure!
      // lets update the LDAP server with out current window size and hope
      // that everything will work better in the future.
      // TODO also log an error message.
      session.publish(new WindowMsg(rcvWindow));
    }
    else
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
   * Sets the window size when used when sending to the remote.
   * @param size The provided window size.
   */
  protected void setSendWindowSize(int size)
  {
    this.sendWindowSize = size;
  }

  /**
   * Shutdown This ServerHandler.
   */
  @Override
  public void shutdown()
  {
    shutdownWriter = true;
    setConsumerActive(false);
    super.shutdown();

    if (session != null)
    {
      session.close();
    }
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
      if (writer != null && !Thread.currentThread().equals(writer))
      {
        writer.join(SHUTDOWN_JOIN_TIMEOUT);
      }
      if (reader != null && !Thread.currentThread().equals(reader))
      {
        reader.join(SHUTDOWN_JOIN_TIMEOUT);
      }
    } catch (InterruptedException e)
    {
      // don't try anymore to join and return.
    }
    if (logger.isTraceEnabled())
    {
      logger.trace("SH.shutdowned(" + this + ")");
    }
  }

  /**
   * Select the next update that must be sent to the server managed by this
   * ServerHandler.
   *
   * @return the next update that must be sent to the server managed by this
   *         ServerHandler.
   * @throws ChangelogException
   *            If a problem occurs when reading the changelog
   */
  public UpdateMsg take() throws ChangelogException
  {
    final UpdateMsg msg = getNextMessage();

    acquirePermitInSendWindow();

    if (msg != null)
    {
      incrementOutCount();
      if (msg.isAssured())
      {
        incrementAssuredStats(msg);
      }
      return msg;
    }
    return null;
  }

  private void acquirePermitInSendWindow()
  {
    boolean acquired = false;
    boolean interrupted = true;
    do
    {
      try
      {
        acquired = sendWindow.tryAcquire(500, TimeUnit.MILLISECONDS);
        interrupted = false;
      } catch (InterruptedException e)
      {
        // loop until not interrupted
      }
    } while ((interrupted || !acquired) && !shutdownWriter);
  }

  private void incrementAssuredStats(final UpdateMsg msg)
  {
    if (msg.getAssuredMode() == AssuredMode.SAFE_READ_MODE)
    {
      assuredSrSentUpdates++;
    }
    else if (!isDataServer())
    {
      assuredSdSentUpdates++;
    }
  }

  /**
   * Creates a RSInfo structure representing this remote RS.
   * @return The RSInfo structure representing this remote RS
   */
  public RSInfo toRSInfo()
  {
    return new RSInfo(serverId, serverURL, generationId, groupId, weight);
  }

  /**
   * Update the send window size based on the credit specified in the
   * given window message.
   *
   * @param windowMsg The Window LocalizableMessage containing the information
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
    if (logger.isTraceEnabled())
    {
      logger.trace("In " + this.replicationServer.getMonitorInstanceName()
          + ", " + getClass().getSimpleName() + " " + this + ":"
          + "\nSH START HANDSHAKE RECEIVED:\n" + inStartMsg
          + "\nAND REPLIED:\n" + outStartMsg);
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
    if (logger.isTraceEnabled())
    {
      logger.trace("In " + this.replicationServer.getMonitorInstanceName()
          + ", " + getClass().getSimpleName() + " " + this + ":"
          + "\nSH START HANDSHAKE SENT:\n" + outStartMsg + "\nAND RECEIVED:\n"
          + inStartMsg);
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
    if (logger.isTraceEnabled())
    {
      logger.trace("In " + this.replicationServer.getMonitorInstanceName()
          + ", " + getClass().getSimpleName() + " " + this + ":"
          + "\nSH TOPO HANDSHAKE RECEIVED:\n" + inTopoMsg + "\nAND REPLIED:\n"
          + outTopoMsg);
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
    if (logger.isTraceEnabled())
    {
      logger.trace("In " + this.replicationServer.getMonitorInstanceName()
          + ", " + getClass().getSimpleName() + " " + this + ":"
          + "\nSH TOPO HANDSHAKE SENT:\n" + outTopoMsg + "\nAND RECEIVED:\n"
          + inTopoMsg);
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
    if (logger.isTraceEnabled())
    {
      logger.trace("In " + this.replicationServer.getMonitorInstanceName()
          + ", " + getClass().getSimpleName() + " " + this + " :"
          + "\nSH SESSION HANDSHAKE RECEIVED:\n" + inStartSessionMsg
          + "\nAND REPLIED:\n" + outTopoMsg);
    }
  }

  /**
   * Log stop message has been received.
   */
  protected void logStopReceived()
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("In " + this.replicationServer.getMonitorInstanceName()
          + ", " + getClass().getSimpleName() + " " + this + " :"
          + "\nSH SESSION HANDSHAKE RECEIVED A STOP MESSAGE");
    }
  }

  /**
   * Process a Ack message received.
   * @param ack the message received.
   */
  void processAck(AckMsg ack)
  {
    replicationServerDomain.processAck(ack, this);
  }

  /**
   * Get the reference generation id (associated with the changes in the db).
   * @return the reference generation id.
   */
  public long getReferenceGenId()
  {
    return replicationServerDomain.getGenerationId();
  }

  /**
   * Process a ResetGenerationIdMsg message received.
   * @param msg the message received.
   */
  void processResetGenId(ResetGenerationIdMsg msg)
  {
    replicationServerDomain.resetGenerationId(this, msg);
  }

  /**
   * Put a new update message received.
   * @param update the update message received.
   * @throws IOException when it occurs.
   */
  public void put(UpdateMsg update) throws IOException
  {
    decAndCheckWindow();
    replicationServerDomain.put(update, this);
  }

  /**
   * Stop this handler.
   */
  public void doStop()
  {
    replicationServerDomain.stopServer(this, false);
  }

  /**
   * Creates a ReplServerStartMsg for the current ServerHandler.
   *
   * @return a new ReplServerStartMsg for the current ServerHandler.
   */
  protected ReplServerStartMsg createReplServerStartMsg()
  {
    return new ReplServerStartMsg(getReplicationServerId(),
        getReplicationServerURL(), getBaseDN(), maxRcvWindow,
        replicationServerDomain.getLatestServerState(), localGenerationId,
        sslEncryption, getLocalGroupId(),
        replicationServer.getDegradedStatusThreshold());
  }

  /**
   * Returns a "badly disconnected" error message for this server handler.
   *
   * @return a "badly disconnected" error message for this server handler
   */
  public LocalizableMessage getBadlyDisconnectedErrorMessage()
  {
    if (isDataServer())
    {
      return ERR_DS_BADLY_DISCONNECTED.get(getReplicationServerId(),
          getServerId(), session.getReadableRemoteAddress(), getBaseDN());
    }
    return ERR_RS_BADLY_DISCONNECTED.get(getReplicationServerId(),
        getServerId(), session.getReadableRemoteAddress(), getBaseDN());
  }
}
