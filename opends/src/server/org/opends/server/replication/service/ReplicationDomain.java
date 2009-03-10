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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.service;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.replication.common.StatusMachine.*;

import org.opends.server.replication.common.ChangeNumberGenerator;

import java.io.BufferedOutputStream;

import org.opends.server.types.Attribute;

import org.opends.server.core.DirectoryServer;


import java.util.Set;

import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;

import java.util.HashMap;

import java.util.Map;

import org.opends.server.config.ConfigException;
import java.util.Collection;

import org.opends.server.replication.plugin.InitializeTargetTask;
import org.opends.server.replication.plugin.InitializeTask;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.DirectoryThread;
import org.opends.server.backends.task.Task;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachine;
import org.opends.server.replication.common.StatusMachineEvent;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.HeartbeatMsg;
import org.opends.server.replication.protocol.InitializeRequestMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ProtocolSession;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.RoutableMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;

/**
 * This class should be used as a base for Replication implementations.
 * <p>
 * It is intended that developer in need of a replication mechanism
 * subclass this class with their own implementation.
 * <p>
 *   The startup phase of the ReplicationDomain subclass,
 *   should read the list of replication servers from the configuration,
 *   instantiate a {@link ServerState} then start the publish service
 *   by calling
 *   {@link #startPublishService(Collection, int, long)}.
 *   At this point it can start calling the {@link #publish(UpdateMsg)}
 *   method if needed.
 * <p>
 *   When the startup phase reach the point when the subclass is ready
 *   to handle updates the Replication Domain implementation should call the
 *   {@link #startListenService()} method.
 *   At this point a Listener thread is created on the Replication Service
 *   and which can start receiving updates.
 * <p>
 *   When updates are received the Replication Service calls the
 *   {@link #processUpdate(UpdateMsg)} method.
 *   ReplicationDomain implementation should implement the appropriate code
 *   for replaying the update on the local repository.
 *   When fully done the subclass must call the
 *   {@link #processUpdateDone(UpdateMsg, String)} method.
 *   This allows to process the update asynchronously if necessary.
 *
 * <p>
 *   To propagate changes to other replica, a ReplicationDomain implementation
 *   must use the {@link #publish(UpdateMsg)} method.
 * <p>
 *   If the Full Initialization process is needed then implementation
 *   for {@link #importBackend(InputStream)} and
 *   {@link #exportBackend(OutputStream)} must be
 *   provided.
 * <p>
 *   Full Initialization of a replica can be triggered by LDAP clients
 *   by creating InitializeTasks or InitializeTargetTask.
 *   Full initialization can also by triggered from the ReplicationDomain
 *   implementation using methods {@link #initializeRemote(short)}
 *   or {@link #initializeFromRemote(short)}.
 * <p>
 *   At shutdown time, the {@link #stopDomain()} method should be called to
 *   cleanly stop the replication service.
 */
public abstract class ReplicationDomain
{
  /**
   * Current status for this replicated domain.
   */
  private ServerStatus status = ServerStatus.NOT_CONNECTED_STATUS;

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   *  An identifier for the Replication Service.
   *  All Replication Domain using this identifier will be connected
   *  through the Replication Service.
   */
  private final String serviceID;

  /**
   * The identifier of this Replication Domain inside the
   * Replication Service.
   * Each Domain must use a unique ServerID.
   */
  private final short serverID;

  /**
   * The ReplicationBroker that is used by this ReplicationDomain to
   * connect to the ReplicationService.
   */
  private ReplicationBroker broker = null;

  /**
   * This Map is used to store all outgoing assured messages in order
   * to be able to correlate all the coming back acks to the original
   * operation.
   */
  private final SortedMap<ChangeNumber, UpdateMsg> waitingAckMsgs =
    new TreeMap<ChangeNumber, UpdateMsg>();


  /**
   * The context related to an import or export being processed
   * Null when none is being processed.
   */
  private IEContext ieContext = null;

  /**
   * The Thread waiting for incoming update messages for this domain and pushing
   * them to the global incoming update message queue for later processing by
   * replay threads.
   */
  private ListenerThread listenerThread;

  /**
   * A Map used to store all the ReplicationDomains created on this server.
   */
  private static Map<String, ReplicationDomain> domains =
    new HashMap<String, ReplicationDomain>();

  /**
   * The Monitor in charge of replication monitoring.
   */
  private ReplicationMonitor monitor;

  /*
   * Assured mode properties
   */
  // Is assured mode enabled or not for this domain ?
  private boolean assured = false;
  // Assured sub mode (used when assured is true)
  private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
  // Safe Data level (used when assuredMode is SAFE_DATA)
  private byte assuredSdLevel = (byte)1;
  // The timeout in ms that should be used, when waiting for assured acks
  private long assuredTimeout = 2000;

  // Group id
  private byte groupId = (byte)1;
  // Referrals urls to be published to other servers of the topology
  // TODO: fill that with all currently opened urls if no urls configured
  private List<String> refUrls = new ArrayList<String>();

  /**
   * A set of counters used for Monitoring.
   */
  private AtomicInteger numProcessedUpdates = new AtomicInteger(0);
  private AtomicInteger numRcvdUpdates = new AtomicInteger(0);
  private AtomicInteger numSentUpdates = new AtomicInteger(0);

  /* Assured replication monitoring counters */

  // Number of updates sent in Assured Mode, Safe Read
  private AtomicInteger assuredSrSentUpdates = new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Read, that have been
  // successfully acknowledged
  private AtomicInteger assuredSrAcknowledgedUpdates = new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Read, that have not been
  // successfully acknowledged (either because of timeout, wrong status or error
  // at replay)
  private AtomicInteger assuredSrNotAcknowledgedUpdates =
    new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Read, that have not been
  // successfully acknowledged because of timeout
  private AtomicInteger assuredSrTimeoutUpdates = new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Read, that have not been
  // successfully acknowledged because of wrong status
  private AtomicInteger assuredSrWrongStatusUpdates = new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Read, that have not been
  // successfully acknowledged because of replay error
  private AtomicInteger assuredSrReplayErrorUpdates = new AtomicInteger(0);
  // Multiple values allowed: number of updates sent in Assured Mode, Safe Read,
  // that have not been successfully acknowledged (either because of timeout,
  // wrong status or error at replay) for a particular server (DS or RS). String
  // format: <server id>:<number of failed updates>
  private Map<Short,Integer> assuredSrServerNotAcknowledgedUpdates =
    new HashMap<Short,Integer>();
  // Number of updates received in Assured Mode, Safe Read request
  private AtomicInteger assuredSrReceivedUpdates = new AtomicInteger(0);
  // Number of updates received in Assured Mode, Safe Read request that we have
  // acked without errors
  private AtomicInteger assuredSrReceivedUpdatesAcked = new AtomicInteger(0);
  // Number of updates received in Assured Mode, Safe Read request that we have
  // acked with errors
  private AtomicInteger assuredSrReceivedUpdatesNotAcked = new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Data
  private AtomicInteger assuredSdSentUpdates = new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Data, that have been
  // successfully acknowledged
  private AtomicInteger assuredSdAcknowledgedUpdates = new AtomicInteger(0);
  // Number of updates sent in Assured Mode, Safe Data, that have not been
  // successfully acknowledged because of timeout
  private AtomicInteger assuredSdTimeoutUpdates = new AtomicInteger(0);
  // Multiple values allowed: number of updates sent in Assured Mode, Safe Data,
  // that have not been successfully acknowledged because of timeout for a
  // particular RS. String format: <server id>:<number of failed updates>
  private Map<Short,Integer> assuredSdServerTimeoutUpdates =
    new HashMap<Short,Integer>();

  /* Status related monitoring fields */

  // Indicates the date when the status changed. This may be used to indicate
  // the date the session with the current replication server started (when
  // status is NORMAL for instance). All the above assured monitoring fields
  // are also resetted each time the status is changed
  private Date lastStatusChangeDate = new Date();

  /**
   * The state maintained by the Concrete Class.
   */
  private final ServerState state;

  /**
   * The generator that will be used to generate {@link ChangeNumber}
   * for this domain.
   */
  private final ChangeNumberGenerator generator;

  /**
   * Returns the {@link ChangeNumberGenerator} that will be used to
   * generate {@link ChangeNumber} for this domain.
   *
   * @return The {@link ChangeNumberGenerator} that will be used to
   *         generate {@link ChangeNumber} for this domain.
   */
  public ChangeNumberGenerator getGenerator()
  {
    return generator;
  }

  /**
   * Creates a ReplicationDomain with the provided parameters.
   *
   * @param serviceID  The identifier of the Replication Domain to which
   *                   this object is participating.
   * @param serverID   The identifier of the server that is participating
   *                   to the Replication Domain.
   *                   This identifier should be different for each server that
   *                   is participating to a given Replication Domain.
   */
  public ReplicationDomain(String serviceID, short serverID)
  {
    this.serviceID = serviceID;
    this.serverID = serverID;
    this.state = new ServerState();
    this.generator = new ChangeNumberGenerator(serverID, state);

    domains.put(serviceID, this);
  }

  /**
   * Creates a ReplicationDomain with the provided parameters.
   * (for unit test purpose only)
   *
   * @param serviceID  The identifier of the Replication Domain to which
   *                   this object is participating.
   * @param serverID   The identifier of the server that is participating
   *                   to the Replication Domain.
   *                   This identifier should be different for each server that
   *                   is participating to a given Replication Domain.
   * @param serverState The serverState to use
   */
  public ReplicationDomain(String serviceID, short serverID,
    ServerState serverState)
  {
    this.serviceID = serviceID;
    this.serverID = serverID;
    this.state = serverState;
    this.generator = new ChangeNumberGenerator(serverID, state);

    domains.put(serviceID, this);
  }

  /**
   * Set the initial status of the domain and perform necessary initializations.
   * This method will be called by the Broker each time the ReplicationBroker
   * establish a new session to a Replication Server.
   *
   * Implementations may override this method when they need to perform
   * additional computing after session establishment.
   * The default implementation should be sufficient for ReplicationDomains
   * that don't need to perform additional computing.
   *
   * @param initStatus              The status to enter the state machine with.
   * @param replicationServerState  The ServerState of the ReplicationServer
   *                                with which the session was established.
   * @param generationID            The current generationID of the
   *                                ReplicationServer with which the session
   *                                was established.
   * @param session                 The ProtocolSession that is currently used.
   */
  public void sessionInitiated(
      ServerStatus initStatus,
      ServerState replicationServerState,
      long generationID,
      ProtocolSession session)
  {
    // Sanity check: is it a valid initial status?
    if (!isValidInitialStatus(initStatus))
    {
      Message msg = ERR_DS_INVALID_INIT_STATUS.get(initStatus.toString(),
        serviceID, Short.toString(serverID));
      logError(msg);
    } else
    {
      status = initStatus;
    }
  }

  /**
   * Processes an incoming ChangeStatusMsg. Compute new status according to
   * given order. Then update domain for being compliant with new status
   * definition.
   * @param csMsg The received status message
   */
  private void receiveChangeStatus(ChangeStatusMsg csMsg)
  {
    if (debugEnabled())
      TRACER.debugInfo("Replication domain " + serviceID +
        " received change status message:\n" + csMsg);

    ServerStatus reqStatus = csMsg.getRequestedStatus();

    // Translate requested status to a state machine event
    StatusMachineEvent event = StatusMachineEvent.statusToEvent(reqStatus);
    if (event == StatusMachineEvent.INVALID_EVENT)
    {
      Message msg = ERR_DS_INVALID_REQUESTED_STATUS.get(reqStatus.toString(),
        serviceID, Short.toString(serverID));
      logError(msg);
      return;
    }

    // Set the new status to the requested one
    setNewStatus(event);
  }

  /**
   * Called when first connection or disconnection detected.
   */
  void toNotConnectedStatus()
  {
    // Go into not connected status
    setNewStatus(StatusMachineEvent.TO_NOT_CONNECTED_STATUS_EVENT);
  }

  /**
   * Perform whatever actions are needed to apply properties for being
   * compliant with new status. Must be called in synchronized section for
   * status. The new status is already set in status variable.
   */
  private void updateDomainForNewStatus()
  {
    switch (status)
    {
      case NOT_CONNECTED_STATUS:
        break;
      case NORMAL_STATUS:
        break;
      case DEGRADED_STATUS:
        break;
      case FULL_UPDATE_STATUS:
        // Signal RS we just entered the full update status
        broker.signalStatusChange(status);
        break;
      case BAD_GEN_ID_STATUS:
        break;
      default:
        if (debugEnabled())
          TRACER.debugInfo("updateDomainForNewStatus: unexpected status: " +
            status);
    }
  }

  /**
   * Gets the status for this domain.
   * @return The status for this domain.
   */
  public ServerStatus getStatus()
  {
    return status;
  }

  /**
   * Gets the identifier of this domain.
   *
   * @return The identifier for this domain.
   */
  public String getServiceID()
  {
    return serviceID;
  }

  /**
   * Get the server ID.
   * @return The server ID.
   */
  public short getServerId()
  {
    return serverID;
  }

  /**
   * Tells if assured replication is enabled for this domain.
   * @return True if assured replication is enabled for this domain.
   */
  public boolean isAssured()
  {
    return assured;
  }

  /**
   * Gives the mode for the assured replication of the domain.
   * @return The mode for the assured replication of the domain.
   */
  public AssuredMode getAssuredMode()
  {
    return assuredMode;
  }

  /**
   * Gives the assured level of the replication of the domain.
   * @return The assured level of the replication of the domain.
   */
  public byte getAssuredSdLevel()
  {
    return assuredSdLevel;
  }

  /**
   * Gives the assured timeout of the replication of the domain (in ms).
   * @return The assured timeout of the replication of the domain.
   */
  public long getAssuredTimeout()
  {
    return assuredTimeout;
  }

  /**
   * Gets the group id for this domain.
   * @return The group id for this domain.
   */
  public byte getGroupId()
  {
    return groupId;
  }

  /**
   * Gets the referrals URLs this domain publishes.
   * @return The referrals URLs this domain publishes.
   */
  public List<String> getRefUrls()
  {
    return refUrls;
  }

  /**
   * Gets the info for DSs in the topology (except us).
   * @return The info for DSs in the topology (except us)
   */
  public List<DSInfo> getDsList()
  {
    return broker.getDsList();
  }

  /**
   * Gets the info for RSs in the topology (except the one we are connected
   * to).
   * @return The info for RSs in the topology (except the one we are connected
   * to)
   */
  public List<RSInfo> getRsList()
  {
    return broker.getRsList();
  }


  /**
   * Gets the server ID of the Replication Server to which the domain
   * is currently connected.
   *
   * @return The server ID of the Replication Server to which the domain
   *         is currently connected.
   */
  public short getRsServerId()
  {
    return broker.getRsServerId();
  }

  /**
   * Increment the number of processed updates.
   */
  private void incProcessedUpdates()
  {
    numProcessedUpdates.incrementAndGet();
  }

  /**
   * get the number of updates replayed by the replication.
   *
   * @return The number of updates replayed by the replication
   */
  int getNumProcessedUpdates()
  {
    if (numProcessedUpdates != null)
      return numProcessedUpdates.get();
    else
      return 0;
  }

  /**
   * get the number of updates received by the replication plugin.
   *
   * @return the number of updates received
   */
  int getNumRcvdUpdates()
  {
    if (numRcvdUpdates != null)
      return numRcvdUpdates.get();
    else
      return 0;
  }

  /**
   * Get the number of updates sent by the replication plugin.
   *
   * @return the number of updates sent
   */
  int getNumSentUpdates()
  {
    if (numSentUpdates != null)
      return numSentUpdates.get();
    else
      return 0;
  }

  /**
   * Set the list of Referrals that should be returned when an
   * operation needs to be redirected to this server.
   *
   * @param referralsUrl The list of referrals.
   */
  public void setURLs(Set<String> referralsUrl)
  {
    for (String url : referralsUrl)
      this.refUrls.add(url);
  }

  /**
   * Set the timeout of the assured replication.
   *
   * @param assuredTimeout the timeout of the assured replication.
   */
  public void setAssuredTimeout(long assuredTimeout)
  {
    this.assuredTimeout = assuredTimeout;
  }

  /**
   * Sets the groupID.
   *
   * @param groupId The groupID.
   */
  public void setGroupId(byte groupId)
  {
    this.groupId = groupId;
  }

  /**
   * Sets the level of assured replication.
   *
   * @param assuredSdLevel The level of assured replication.
   */
  public void setAssuredSdLevel(byte assuredSdLevel)
  {
    this.assuredSdLevel = assuredSdLevel;
  }

  /**
   * Sets the assured replication mode.
   *
   * @param dataMode The assured replication mode.
   */
  public void setAssuredMode(AssuredMode dataMode)
  {
    this.assuredMode = dataMode;
  }

  /**
   * Sets assured replication.
   *
   * @param assured A boolean indicating if assured replication should be used.
   */
  public void setAssured(boolean assured)
  {
    this.assured = assured;
  }

  /**
   * Receives an update message from the replicationServer.
   * also responsible for updating the list of pending changes
   * @return the received message - null if none
   */
  UpdateMsg receive()
  {
    UpdateMsg update = null;

    while (update == null)
    {
      InitializeRequestMsg initMsg = null;
      ReplicationMsg msg;
      try
      {
        msg = broker.receive();
        if (msg == null)
        {
          // The server is in the shutdown process
          return null;
        }

        if (debugEnabled())
          if (!(msg instanceof HeartbeatMsg))
            TRACER.debugVerbose("Message received <" + msg + ">");

        if (msg instanceof AckMsg)
        {
          AckMsg ack = (AckMsg) msg;
          receiveAck(ack);
        }
        else if (msg instanceof InitializeRequestMsg)
        {
          // Another server requests us to provide entries
          // for a total update
          initMsg = (InitializeRequestMsg)msg;
        }
        else if (msg instanceof InitializeTargetMsg)
        {
          // Another server is exporting its entries to us
          InitializeTargetMsg importMsg = (InitializeTargetMsg) msg;

          try
          {
            // This must be done while we are still holding the
            // broker lock because we are now going to receive a
            // bunch of entries from the remote server and we
            // want the import thread to catch them and
            // not the ListenerThread.
            initialize(importMsg);
          }
          catch(DirectoryException de)
          {
            // Returns an error message to notify the sender
            ErrorMsg errorMsg =
              new ErrorMsg(importMsg.getsenderID(),
                  de.getMessageObject());
            MessageBuilder mb = new MessageBuilder();
            mb.append(de.getMessageObject());
            TRACER.debugInfo(Message.toString(mb.toMessage()));
            broker.publish(errorMsg);
          }
        }
        else if (msg instanceof ErrorMsg)
        {
          if (ieContext != null)
          {
            // This is an error termination for the 2 following cases :
            // - either during an export
            // - or before an import really started
            //   For example, when we publish a request and the
            //  replicationServer did not find any import source.
            abandonImportExport((ErrorMsg)msg);
          }
          else
          {
            /*
             * Log error message
             */
            ErrorMsg errorMsg = (ErrorMsg)msg;
            logError(ERR_ERROR_MSG_RECEIVED.get(
                errorMsg.getDetails()));
          }
        }
        else if (msg instanceof ChangeStatusMsg)
        {
          ChangeStatusMsg csMsg = (ChangeStatusMsg)msg;
          receiveChangeStatus(csMsg);
        }
        else if (msg instanceof UpdateMsg)
        {
          generator.adjust(((UpdateMsg) msg).getChangeNumber());
          update = (UpdateMsg) msg;
          generator.adjust(update.getChangeNumber());
        }
      }
      catch (SocketTimeoutException e)
      {
        // just retry
      }
      // Test if we have received and export request message and
      // if that's the case handle it now.
      // This must be done outside of the portion of code protected
      // by the broker lock so that we keep receiveing update
      // when we are doing and export and so that a possible
      // closure of the socket happening when we are publishing the
      // entries to the remote can be handled by the other
      // replay thread when they call this method and therefore the
      // broker.receive() method.
      if (initMsg != null)
      {
        // Do this work in a thread to allow replay thread continue working
        ExportThread exportThread = new ExportThread(initMsg.getsenderID());
        exportThread.start();
      }
    }

    numRcvdUpdates.incrementAndGet();
     byte rsGroupId = broker.getRsGroupId();
    if ( update.isAssured() && (update.getAssuredMode() ==
      AssuredMode.SAFE_READ_MODE) && (rsGroupId == groupId) )
    {
      assuredSrReceivedUpdates.incrementAndGet();
    }
    return update;
  }

  /**
   * Updates the passed monitoring list of errors received for assured messages
   * (safe data or safe read, depending of the passed list to update) for a
   * particular server in the list. This increments the counter of error for the
   * passed server, or creates an initial value of 1 error for it if the server
   * is not yet present in the map.
   * @param errorList
   * @param serverId
   */
  private void updateAssuredErrorsByServer(Map<Short,Integer> errorsByServer,
    Short serverId)
  {
    synchronized (errorsByServer)
    {
      Integer serverErrCount = errorsByServer.get(serverId);
      if (serverErrCount == null)
      {
        // Server not present in list, create an entry with an
        // initial number of errors set to 1
        errorsByServer.put(serverId, 1);
      } else
      {
        // Server already present in list, just increment number of
        // errors for the server
        int val = serverErrCount.intValue();
        val++;
        errorsByServer.put(serverId, val);
      }
    }
  }

  /**
   * Do the necessary processing when an AckMsg is received.
   *
   * @param ack The AckMsg that was received.
   */
  private void receiveAck(AckMsg ack)
  {
    UpdateMsg update;
    ChangeNumber changeNumber = ack.getChangeNumber();

    // Remove the message for pending ack list (this may already make the thread
    // that is waiting for the ack be aware of its reception)
    synchronized (waitingAckMsgs)
    {
      update = waitingAckMsgs.remove(changeNumber);
    }

    // Signal waiting thread ack has been received
    if (update != null)
    {
      synchronized (update)
      {
        update.notify();
      }

      // Analyze status of embedded in the ack to see if everything went well
      boolean hasTimeout = ack.hasTimeout();
      boolean hasReplayErrors = ack.hasReplayError();
      boolean hasWrongStatus = ack.hasWrongStatus();

      AssuredMode updateAssuredMode = update.getAssuredMode();

      if ( hasTimeout || hasReplayErrors || hasWrongStatus)
      {
        // Some problems detected: message not correclty reached every requested
        // servers. Log problem
        Message errorMsg = NOTE_DS_RECEIVED_ACK_ERROR.get(serviceID,
          Short.toString(serverID), update.toString(), ack.errorsToString());
        logError(errorMsg);

        List<Short> failedServers = ack.getFailedServers();

        // Increment assured replication monitoring counters
        switch (updateAssuredMode)
        {
          case SAFE_READ_MODE:
            assuredSrNotAcknowledgedUpdates.incrementAndGet();
            if (hasTimeout)
              assuredSrTimeoutUpdates.incrementAndGet();
            if (hasReplayErrors)
              assuredSrReplayErrorUpdates.incrementAndGet();
            if (hasWrongStatus)
              assuredSrWrongStatusUpdates.incrementAndGet();
            if (failedServers != null) // This should always be the case !
            {
              for(Short sid : failedServers)
              {
                updateAssuredErrorsByServer(
                  assuredSrServerNotAcknowledgedUpdates, sid);
              }
            }
            break;
          case SAFE_DATA_MODE:
            // The only possible cause of ack error in safe data mode is timeout
            if (hasTimeout) // So should always be the case
              assuredSdTimeoutUpdates.incrementAndGet();
            if (failedServers != null) // This should always be the case !
            {
              for(Short sid : failedServers)
              {
                updateAssuredErrorsByServer(
                  assuredSdServerTimeoutUpdates, sid);
              }
            }
            break;
          default:
          // Should not happen
        }
      } else
      {
        // Update has been acknowledged without errors
        // Increment assured replication monitoring counters
        switch (updateAssuredMode)
        {
          case SAFE_READ_MODE:
            assuredSrAcknowledgedUpdates.incrementAndGet();
            break;
          case SAFE_DATA_MODE:
            assuredSdAcknowledgedUpdates.incrementAndGet();
            break;
          default:
          // Should not happen
        }
      }
    }
  }

  /**
   * Retrieves a replication domain based on the baseDn.
   *
   * @param serviceID           The identifier of the domain to retrieve.
   *
   * @return                    The domain retrieved.
   *
   * @throws DirectoryException When an error occurred or no domain
   *                            match the provided baseDn.
   */
  static ReplicationDomain retrievesReplicationDomain(String serviceID)
  throws DirectoryException
  {
    ReplicationDomain replicationDomain = domains.get(serviceID);
    if (replicationDomain == null)
    {
      throw new DirectoryException(ResultCode.OTHER,
          ERR_NO_MATCHING_DOMAIN.get(serviceID));
    }
    return replicationDomain;
  }

  /*
   * After this point the code is related to the Total Update.
   */

  /**
   * This thread is launched when we want to export data to another server that
   * has requested to be initialized with the data of our backend.
   */
  private class ExportThread extends DirectoryThread
  {
    // Id of server that will receive updates
    private short target;

    /**
     * Constructor for the ExportThread.
     *
     * @param target Id of server that will receive updates
     */
    public ExportThread(short target)
    {
      super("Export thread " + serverID);
      this.target = target;
    }

    /**
     * Run method for this class.
     */
    @Override
    public void run()
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Export thread starting.");
      }

      try
      {
        initializeRemote(target, target, null);
      } catch (DirectoryException de)
      {
      // An error message has been sent to the peer
      // Nothing more to do locally
      }
      if (debugEnabled())
      {
        TRACER.debugInfo("Export thread stopping.");
      }
    }
  }

  /**
   * This class contain the context related to an import or export
   * launched on the domain.
   */
  private class IEContext
  {
    // The task that initiated the operation.
    Task initializeTask;
    // The target in the case of an export
    short exportTarget = RoutableMsg.UNKNOWN_SERVER;
    // The source in the case of an import
    short importSource = RoutableMsg.UNKNOWN_SERVER;

    // The total entry count expected to be processed
    long entryCount = 0;
    // The count for the entry not yet processed
    long entryLeftCount = 0;

    // The exception raised when any
    DirectoryException exception = null;

    // A boolean indicating if the context is related to an
    // import or an export.
    boolean importInProgress;

    /**
     * Creates a new IEContext.
     *
     * @param importInProgress true if the IEContext will be used
     *                         for and import, false if the IEContext
     *                         will be used for and export.
     */
    public IEContext(boolean importInProgress)
    {
      this.importInProgress = importInProgress;
    }

    /**
     * Initializes the import/export counters with the provider value.
     * @param total
     * @param left
     * @throws DirectoryException
     */
    public void setCounters(long total, long left)
      throws DirectoryException
    {
      entryCount = total;
      entryLeftCount = left;

      if (initializeTask != null)
      {
        if (initializeTask instanceof InitializeTask)
        {
          ((InitializeTask)initializeTask).setTotal(entryCount);
          ((InitializeTask)initializeTask).setLeft(entryCount);
        }
        else if (initializeTask instanceof InitializeTargetTask)
        {
          ((InitializeTargetTask)initializeTask).setTotal(entryCount);
          ((InitializeTargetTask)initializeTask).setLeft(entryCount);
        }
      }
    }

    /**
     * Update the counters of the task for each entry processed during
     * an import or export.
     * @throws DirectoryException
     */
    public void updateCounters()
      throws DirectoryException
    {
      entryLeftCount--;

      if (initializeTask != null)
      {
        if (initializeTask instanceof InitializeTask)
        {
          ((InitializeTask)initializeTask).setLeft(entryLeftCount);
        }
        else if (initializeTask instanceof InitializeTargetTask)
        {
          ((InitializeTargetTask)initializeTask).setLeft(entryLeftCount);
        }
      }
    }

    /**
     * Update the counters of the task for each entry processed during
     * an import or export.
     *
     * @param  entriesDone The number of entries that were processed
     *                     since the last time this method was called.
     *
     * @throws DirectoryException
     */
    public void updateCounters(int entriesDone)
      throws DirectoryException
    {
      entryLeftCount -= entriesDone;

      if (initializeTask != null)
      {
        if (initializeTask instanceof InitializeTask)
        {
          ((InitializeTask)initializeTask).setLeft(entryLeftCount);
        }
        else if (initializeTask instanceof InitializeTargetTask)
        {
          ((InitializeTargetTask)initializeTask).setLeft(entryLeftCount);
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return new String("[ Entry count=" + this.entryCount +
                        ", Entry left count=" + this.entryLeftCount + "]");
    }
  }
  /**
   * Verifies that the given string represents a valid source
   * from which this server can be initialized.
   *
   * @param targetString The string representing the source
   * @return The source as a short value
   * @throws DirectoryException if the string is not valid
   */
  public short decodeTarget(String targetString)
  throws DirectoryException
  {
    short  target = 0;
    Throwable cause;
    if (targetString.equalsIgnoreCase("all"))
    {
      return RoutableMsg.ALL_SERVERS;
    }

    // So should be a serverID
    try
    {
      target = Integer.decode(targetString).shortValue();
      if (target >= 0)
      {
        // FIXME Could we check now that it is a know server in the domain ?
      }
      return target;
    }
    catch(Exception e)
    {
      cause = e;
    }
    ResultCode resultCode = ResultCode.OTHER;
    Message message = ERR_INVALID_EXPORT_TARGET.get();

    if (cause != null)
      throw new DirectoryException(
          resultCode, message, cause);
    else
      throw new DirectoryException(
          resultCode, message);

  }

  /**
   * Initializes a remote server from this server.
   * <p>
   * The {@link #exportBackend(OutputStream)} will therefore be called
   * on this server, and the {@link #importBackend(InputStream)}
   * will be called on the remote server.
   * <p>
   * The InputStream and OutpuStream given as a parameter to those
   * methods will be connected through the replication protocol.
   *
   * @param target   The server-id of the server that should be initialized.
   *                 The target can be discovered using the
   *                 {@link #getDsList()} method.
   * @param initTask The task that triggers this initialization and that should
   *                 be updated with its progress.
   *
   * @throws DirectoryException If it was not possible to publish the
   *                            Initialization message to the Topology.
   */
  public void initializeRemote(short target, Task initTask)
  throws DirectoryException
  {
    initializeRemote(target, serverID, initTask);
  }

  /**
   * Process the initialization of some other server or servers in the topology
   * specified by the target argument when this initialization specifying the
   * server that requests the initialization.
   *
   * @param target The target that should be initialized.
   * @param requestorID The server that initiated the export.
   * @param initTask The task that triggers this initialization and that should
   *  be updated with its progress.
   *
   * @exception DirectoryException When an error occurs.
   */
  void initializeRemote(short target, short requestorID, Task initTask)
  throws DirectoryException
  {
    Message msg = NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_START.get(
      Short.toString(serverID),
      serviceID,
      Short.toString(requestorID));
    logError(msg);

    boolean contextAcquired=false;

    acquireIEContext(false);
    contextAcquired = true;
    ieContext.exportTarget = target;

    if (initTask != null)
    {
      ieContext.initializeTask = initTask;
    }

    // The number of entries to be exported is the number of entries under
    // the base DN entry and the base entry itself.
    long entryCount = this.countEntries();


    ieContext.setCounters(entryCount, entryCount);

    // Send start message to the peer
    InitializeTargetMsg initializeMessage = new InitializeTargetMsg(
        serviceID, serverID, target, requestorID, entryCount);

    broker.publish(initializeMessage);

    try
    {
      exportBackend(new BufferedOutputStream(new ReplOutputStream(this)));

      // Notify the peer of the success
      DoneMsg doneMsg = new DoneMsg(serverID,
          initializeMessage.getDestination());
      broker.publish(doneMsg);

      releaseIEContext();
    }
    catch(DirectoryException de)
    {
      // Notify the peer of the failure
      ErrorMsg errorMsg =
        new ErrorMsg(target,
                         de.getMessageObject());
      broker.publish(errorMsg);

      if (contextAcquired)
        releaseIEContext();

      throw(de);
    }

    msg = NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_END.get(
      Short.toString(serverID),
      serviceID,
      Short.toString(requestorID));
    logError(msg);
  }

  /**
   * Get the ServerState maintained by the Concrete class.
   *
   * @return the ServerState maintained by the Concrete class.
   */
  public ServerState getServerState()
  {
    return state;
  }


  private synchronized void acquireIEContext(boolean importInProgress)
  throws DirectoryException
  {
    if (ieContext != null)
    {
      // Rejects 2 simultaneous exports
      Message message = ERR_SIMULTANEOUS_IMPORT_EXPORT_REJECTED.get();
      throw new DirectoryException(ResultCode.OTHER,
          message);
    }

    ieContext = new IEContext(importInProgress);
  }

  private synchronized void releaseIEContext()
  {
    ieContext = null;
  }

  /**
   * Processes an error message received while an import/export is
   * on going.
   * @param errorMsg The error message received.
   */
  void abandonImportExport(ErrorMsg errorMsg)
  {
    // FIXME TBD Treat the case where the error happens while entries
    // are being exported

    if (debugEnabled())
      TRACER.debugVerbose(
          " abandonImportExport:" + this.serverID +
          " serviceID: " + this.serviceID +
          " Error Msg received: " + errorMsg);

    if (ieContext != null)
    {
      ieContext.exception = new DirectoryException(ResultCode.OTHER,
          errorMsg.getDetails());

      if (ieContext.initializeTask instanceof InitializeTask)
      {
        // Update the task that initiated the import
        ((InitializeTask)ieContext.initializeTask).
        updateTaskCompletionState(ieContext.exception);

        releaseIEContext();
      }
    }
  }

  /**
   * Receives bytes related to an entry in the context of an import to
   * initialize the domain (called by ReplLDIFInputStream).
   *
   * @return The bytes. Null when the Done or Err message has been received
   */
  byte[] receiveEntryBytes()
  {
    ReplicationMsg msg;
    while (true)
    {
      try
      {
        msg = broker.receive();

        if (debugEnabled())
          TRACER.debugVerbose(
              " sid:" + serverID +
              " base DN:" + serviceID +
              " Import EntryBytes received " + msg);
        if (msg == null)
        {
          // The server is in the shutdown process
          return null;
        }

        if (msg instanceof EntryMsg)
        {
          EntryMsg entryMsg = (EntryMsg)msg;
          byte[] entryBytes = entryMsg.getEntryBytes();
          ieContext.updateCounters(countEntryLimits(entryBytes));
          return entryBytes;
        }
        else if (msg instanceof DoneMsg)
        {
          // This is the normal termination of the import
          // No error is stored and the import is ended
          // by returning null
          return null;
        }
        else if (msg instanceof ErrorMsg)
        {
          // This is an error termination during the import
          // The error is stored and the import is ended
          // by returning null
          ErrorMsg errorMsg = (ErrorMsg)msg;
          ieContext.exception = new DirectoryException(
                                      ResultCode.OTHER,
                                      errorMsg.getDetails());
          return null;
        }
        else
        {
          // Other messages received during an import are trashed
        }
      }
      catch(Exception e)
      {
        // TODO: i18n
        ieContext.exception = new DirectoryException(ResultCode.OTHER,
            Message.raw("received an unexpected message type" +
                e.getLocalizedMessage()));
      }
    }
  }

  /**
   * Count the number of entries in the provided byte[].
   * This is based on the hypothesis that the entries are separated
   * by a "\n\n" String.
   *
   * @param   entryBytes
   * @return  The number of entries in the provided byte[].
   */
  private int countEntryLimits(byte[] entryBytes)
  {
    return countEntryLimits(entryBytes, 0, entryBytes.length);
  }

  /**
   * Count the number of entries in the provided byte[].
   * This is based on the hypothesis that the entries are separated
   * by a "\n\n" String.
   *
   * @param   entryBytes
   * @return  The number of entries in the provided byte[].
   */
  private int countEntryLimits(byte[] entryBytes, int pos, int length)
  {
    int entryCount = 0;
    int count = 0;
    while (count<=length-2)
    {
      if ((entryBytes[pos+count] == '\n') && (entryBytes[pos+count+1] == '\n'))
      {
        entryCount++;
        count++;
      }
      count++;
    }
    return entryCount;
  }

  /**
   * Exports an entry in LDIF format.
   *
   * @param lDIFEntry The entry to be exported in byte[] form.
   * @param pos       The starting Position in the array.
   * @param length    Number of array elements to be copied.
   *
   * @throws IOException when an error occurred.
   */
  void exportLDIFEntry(byte[] lDIFEntry, int pos, int length) throws IOException
  {
    // If an error was raised - like receiving an ErrorMsg
    // we just let down the export.
    if (ieContext.exception != null)
    {
      IOException ioe = new IOException(ieContext.exception.getMessage());
      ieContext = null;
      throw ioe;
    }

    EntryMsg entryMessage = new EntryMsg(
        serverID, ieContext.exportTarget, lDIFEntry, pos, length);
    broker.publish(entryMessage);

    try
    {
      ieContext.updateCounters(countEntryLimits(lDIFEntry, pos, length));
    }
    catch (DirectoryException de)
    {
      throw new IOException(de.getMessage());
    }
  }

  /**
   * Initializes this domain from another source server.
   * <p>
   * When this method is called, a request for initialization will
   * be sent to the source server asking for initialization.
   * <p>
   * The {@link #exportBackend(OutputStream)} will therefore be called
   * on the source server, and the {@link #importBackend(InputStream)}
   * will be called on his server.
   * <p>
   * The InputStream and OutpuStream given as a parameter to those
   * methods will be connected through the replication protocol.
   *
   * @param source   The server-id of the source from which to initialize.
   *                 The source can be discovered using the
   *                 {@link #getDsList()} method.
   *
   * @throws DirectoryException If it was not possible to publish the
   *                            Initialization message to the Topology.
   */
  public void initializeFromRemote(short source)
  throws DirectoryException
  {
    initializeFromRemote(source, null);
  }

  /**
   * Initializes a remote server from this server.
   * <p>
   * The {@link #exportBackend(OutputStream)} will therefore be called
   * on this server, and the {@link #importBackend(InputStream)}
   * will be called on the remote server.
   * <p>
   * The InputStream and OutpuStream given as a parameter to those
   * methods will be connected through the replication protocol.
   *
   * @param target   The server-id of the server that should be initialized.
   *                 The target can be discovered using the
   *                 {@link #getDsList()} method.
   *
   * @throws DirectoryException If it was not possible to publish the
   *                            Initialization message to the Topology.
   */
  public void initializeRemote(short target) throws DirectoryException
  {
    initializeRemote(target, null);
  }

  /**
   * Initializes this domain from another source server.
   * <p>
   * When this method is called, a request for initialization will
   * be sent to the source server asking for initialization.
   * <p>
   * The {@link #exportBackend(OutputStream)} will therefore be called
   * on the source server, and the {@link #importBackend(InputStream)}
   * will be called on his server.
   * <p>
   * The InputStream and OutpuStream given as a parameter to those
   * methods will be connected through the replication protocol.
   *
   * @param source   The server-id of the source from which to initialize.
   *                 The source can be discovered using the
   *                 {@link #getDsList()} method.
   * @param initTask The task that launched the initialization
   *                 and should be updated of its progress.
   *
   * @throws DirectoryException If it was not possible to publish the
   *                            Initialization message to the Topology.
   */
  public void initializeFromRemote(short source, Task initTask)
  throws DirectoryException
  {
    if (debugEnabled())
      TRACER.debugInfo("Entering initializeFromRemote");

    acquireIEContext(true);
    ieContext.initializeTask = initTask;

    InitializeRequestMsg initializeMsg = new InitializeRequestMsg(
        serviceID, serverID, source);

    // Publish Init request msg
    broker.publish(initializeMsg);

    // .. we expect to receive entries or err after that
  }

  /**
   * Initializes the domain's backend with received entries.
   * @param initializeMessage The message that initiated the import.
   * @exception DirectoryException Thrown when an error occurs.
   */
  void initialize(InitializeTargetMsg initializeMessage)
  throws DirectoryException
  {
    DirectoryException de = null;

    Message msg = NOTE_FULL_UPDATE_ENGAGED_FROM_REMOTE_START.get(
      Short.toString(serverID),
      serviceID,
      Long.toString(initializeMessage.getRequestorID()));
    logError(msg);

    // Go into full update status
    setNewStatus(StatusMachineEvent.TO_FULL_UPDATE_STATUS_EVENT);

    if (initializeMessage.getRequestorID() == serverID)
    {
      // The import responds to a request we did so the IEContext
      // is already acquired
    }
    else
    {
      acquireIEContext(true);
    }

    ieContext.importSource = initializeMessage.getsenderID();
    ieContext.entryLeftCount = initializeMessage.getEntryCount();
    ieContext.setCounters(
        initializeMessage.getEntryCount(),
        initializeMessage.getEntryCount());

    try
    {
      importBackend(new ReplInputStream(this));
      broker.reStart();
    }
    catch (DirectoryException e)
    {
      de = e;
    }
    finally
    {
      if ((ieContext != null)  && (ieContext.exception != null))
        de = ieContext.exception;

      // Update the task that initiated the import
      if ((ieContext != null ) && (ieContext.initializeTask != null))
      {
        ((InitializeTask)ieContext.initializeTask).
        updateTaskCompletionState(de);
      }
      releaseIEContext();
    }

    // Sends up the root error.
    if (de != null)
    {
      throw de;
    }

    msg = NOTE_FULL_UPDATE_ENGAGED_FROM_REMOTE_END.get(
      Short.toString(serverID),
      serviceID,
      Long.toString(initializeMessage.getRequestorID()));
    logError(msg);
  }

  /**
   * Sets the status to a new value depending of the passed status machine
   * event.
   * @param event The event that may make the status be changed
   */
  private void setNewStatus(StatusMachineEvent event)
  {
    ServerStatus newStatus =
      StatusMachine.computeNewStatus(status, event);

    if (newStatus == ServerStatus.INVALID_STATUS)
    {
      Message msg = ERR_DS_CANNOT_CHANGE_STATUS.get(serviceID,
        Short.toString(serverID), status.toString(), event.toString());
      logError(msg);
      return;
    }

    if (newStatus != status)
    {
      // Reset status date
      lastStatusChangeDate = new Date();
      // Reset monitoring counters if reconnection
      if (newStatus == ServerStatus.NOT_CONNECTED_STATUS)
        resetMonitoringCounters();

      // Store new status
      status = newStatus;

      if (debugEnabled())
        TRACER.debugInfo("Replication domain " + serviceID +
          " new status is: " + status);

      // Perform whatever actions are needed to apply properties for being
      // compliant with new status
      updateDomainForNewStatus();
    }
  }

  /**
   * Returns a boolean indicating if an import or export is currently
   * processed.
   *
   * @return The status
   */
  public boolean ieRunning()
  {
    return (ieContext != null);
  }

  /**
   * Check the value of the Replication Servers generation ID.
   *
   * @param generationID        The expected value of the generation ID.
   *
   * @throws DirectoryException When the generation ID of the Replication
   *                            Servers is not the expected value.
   */
  private void checkGenerationID(long generationID) throws DirectoryException
  {
    boolean flag = false;

    for (int i = 0; i< 10; i++)
    {
      for (RSInfo rsInfo : getRsList())
      {
        if (rsInfo.getGenerationId() == generationID)
        {
          flag = true;
          break;
        }
        else
        {
          try
          {
            Thread.sleep(i*100);
          } catch (InterruptedException e)
          {
          }
        }
      }
      if (flag)
      {
        break;
      }
    }

    if (!flag)
    {
      ResultCode resultCode = ResultCode.OTHER;
      Message message = ERR_RESET_GENERATION_ID_FAILED.get(serviceID);
      throw new DirectoryException(
          resultCode, message);
    }
  }

  /**
   * Reset the Replication Log.
   * Calling this method will remove all the Replication information that
   * was kept on all the Replication Servers currently connected in the
   * topology.
   *
   * @throws DirectoryException If this ReplicationDomain is not currently
   *                           connected to a Replication Server or it
   *                           was not possible to contact it.
   */
  public void resetReplicationLog() throws DirectoryException
  {
    // Reset the Generation ID to -1 to clean the ReplicationServers.
    resetGenerationId((long)-1);

    // check that at least one ReplicationServer did change its generation-id
    checkGenerationID(-1);

    // Reconnect to the Replication Server so that it adopt our
    // GenerationID.
    disableService();
    enableService();

    resetGenerationId(getGenerationID());

    // check that at least one ReplicationServer did change its generation-id
    checkGenerationID(getGenerationID());
  }

  /**
   * Reset the generationId of this domain in the whole topology.
   * A message is sent to the Replication Servers for them to reset
   * their change dbs.
   *
   * @param generationIdNewValue  The new value of the generation Id.
   * @throws DirectoryException   When an error occurs
   */
  public void resetGenerationId(Long generationIdNewValue)
  throws DirectoryException
  {
    if (debugEnabled())
      TRACER.debugInfo(
          "Server id " + serverID + " and domain " + serviceID
          + "resetGenerationId" + generationIdNewValue);

    if (!isConnected())
    {
      ResultCode resultCode = ResultCode.OTHER;
      Message message = ERR_RESET_GENERATION_CONN_ERR_ID.get(serviceID);
      throw new DirectoryException(
         resultCode, message);
    }

    ResetGenerationIdMsg genIdMessage = null;

    if (generationIdNewValue == null)
    {
      genIdMessage = new ResetGenerationIdMsg(this.getGenerationID());
    }
    else
    {
      genIdMessage = new ResetGenerationIdMsg(generationIdNewValue);
    }
    broker.publish(genIdMessage);
  }


  /*
   ******** End of The total Update code *********
   */

  /*
   ******* Start of Monitoring Code **********
   */

  /**
   * Get the maximum receive window size.
   *
   * @return The maximum receive window size.
   */
  int getMaxRcvWindow()
  {
    if (broker != null)
      return broker.getMaxRcvWindow();
    else
      return 0;
  }

  /**
   * Get the current receive window size.
   *
   * @return The current receive window size.
   */
  int getCurrentRcvWindow()
  {
    if (broker != null)
      return broker.getCurrentRcvWindow();
    else
      return 0;
  }

  /**
   * Get the maximum send window size.
   *
   * @return The maximum send window size.
   */
  int getMaxSendWindow()
  {
    if (broker != null)
      return broker.getMaxSendWindow();
    else
      return 0;
  }

  /**
   * Get the current send window size.
   *
   * @return The current send window size.
   */
  int getCurrentSendWindow()
  {
    if (broker != null)
      return broker.getCurrentSendWindow();
    else
      return 0;
  }

  /**
   * Get the number of times the replication connection was lost.
   * @return The number of times the replication connection was lost.
   */
  int getNumLostConnections()
  {
    if (broker != null)
      return broker.getNumLostConnections();
    else
      return 0;
  }

  /**
   * Determine whether the connection to the replication server is encrypted.
   * @return true if the connection is encrypted, false otherwise.
   */
  boolean isSessionEncrypted()
  {
    if (broker != null)
      return broker.isSessionEncrypted();
    else
      return false;
  }

  /**
   * This method is called when the ReplicationDomain has completed the
   * processing of a received update synchronously.
   * In such cases the processUpdateDone () is called and the state
   * is updated automatically.
   *
   * @param msg The UpdateMessage that was processed.
   */
  void processUpdateDoneSynchronous(UpdateMsg msg)
  {
    // Warning: in synchronous mode, no way to tell the replay of an update went
    // wrong Just put null in processUpdateDone so that if assured replication
    // is used the ack is sent without error at replay flag.
    processUpdateDone(msg, null);
    state.update(msg.getChangeNumber());
  }

  /**
   * Check if the domain is connected to a ReplicationServer.
   *
   * @return true if the server is connected, false if not.
   */
  public boolean isConnected()
  {
    if (broker != null)
      return broker.isConnected();
    else
      return false;
  }

  /**
   * Get the name of the replicationServer to which this domain is currently
   * connected.
   *
   * @return the name of the replicationServer to which this domain
   *         is currently connected.
   */
  public String getReplicationServer()
  {
    if (broker != null)
      return broker.getReplicationServer();
    else
      return "Not connected";
  }

  /**
   * Gets the number of updates sent in assured safe read mode.
   * @return The number of updates sent in assured safe read mode.
   */
  public int getAssuredSrSentUpdates()
  {
    return assuredSrSentUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have been
   * acknowledged without errors.
   * @return The number of updates sent in assured safe read mode that have been
   * acknowledged without errors.
   */
  public int getAssuredSrAcknowledgedUpdates()
  {
    return assuredSrAcknowledgedUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged.
   */
  public int getAssuredSrNotAcknowledgedUpdates()
  {
    return assuredSrNotAcknowledgedUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged due to timeout error.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged due to timeout error.
   */
  public int getAssuredSrTimeoutUpdates()
  {
    return assuredSrTimeoutUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged due to wrong status error.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged due to wrong status error.
   */
  public int getAssuredSrWrongStatusUpdates()
  {
    return assuredSrWrongStatusUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged due to replay error.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged due to replay error.
   */
  public int getAssuredSrReplayErrorUpdates()
  {
    return assuredSrReplayErrorUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe read mode that have not
   * been acknowledged per server.
   * @return The number of updates sent in assured safe read mode that have not
   * been acknowledged per server.
   */
  public Map<Short, Integer> getAssuredSrServerNotAcknowledgedUpdates()
  {
    // Clone a snapshot with synchronized section to have a consistent view in
    // monitoring
    Map<Short, Integer> snapshot = new HashMap<Short, Integer>();
    synchronized(assuredSrServerNotAcknowledgedUpdates)
    {
      Set<Short> keySet = assuredSrServerNotAcknowledgedUpdates.keySet();
      for (Short serverId : keySet)
      {
        Integer i = assuredSrServerNotAcknowledgedUpdates.get(serverId);
        snapshot.put(serverId, i);
      }
    }
    return snapshot;
  }

  /**
   * Gets the number of updates received in assured safe read mode request.
   * @return The number of updates received in assured safe read mode request.
   */
  public int getAssuredSrReceivedUpdates()
  {
    return assuredSrReceivedUpdates.get();
  }

  /**
   * Gets the number of updates received in assured safe read mode that we acked
   * without error (no replay error).
   * @return The number of updates received in assured safe read mode that we
   * acked without error (no replay error).
   */
  public int getAssuredSrReceivedUpdatesAcked()
  {
    return this.assuredSrReceivedUpdatesAcked.get();
  }

  /**
   * Gets the number of updates received in assured safe read mode that we did
   * not ack due to error (replay error).
   * @return The number of updates received in assured safe read mode that we
   * did not ack due to error (replay error).
   */
  public int getAssuredSrReceivedUpdatesNotAcked()
  {
    return this.assuredSrReceivedUpdatesNotAcked.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode.
   * @return The number of updates sent in assured safe data mode.
   */
  public int getAssuredSdSentUpdates()
  {
    return assuredSdSentUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode that have been
   * acknowledged without errors.
   * @return The number of updates sent in assured safe data mode that have been
   * acknowledged without errors.
   */
  public int getAssuredSdAcknowledgedUpdates()
  {
    return assuredSdAcknowledgedUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode that have not
   * been acknowledged due to timeout error.
   * @return The number of updates sent in assured safe data mode that have not
   * been acknowledged due to timeout error.
   */
  public int getAssuredSdTimeoutUpdates()
  {
    return assuredSdTimeoutUpdates.get();
  }

  /**
   * Gets the number of updates sent in assured safe data mode that have not
   * been acknowledged due to timeout error per server.
   * @return The number of updates sent in assured safe data mode that have not
   * been acknowledged due to timeout error per server.
   */
  public Map<Short, Integer> getAssuredSdServerTimeoutUpdates()
  {
    // Clone a snapshot with synchronized section to have a consistent view in
    // monitoring
    Map<Short, Integer> snapshot = new HashMap<Short, Integer>();
    synchronized(assuredSdServerTimeoutUpdates)
    {
      Set<Short> keySet = assuredSdServerTimeoutUpdates.keySet();
      for (Short serverId : keySet)
      {
        Integer i = assuredSdServerTimeoutUpdates.get(serverId);
        snapshot.put(serverId, i);
      }
    }
    return snapshot;
  }

  /**
   * Gets the date of the last status change.
   * @return The date of the last status change.
   */
  public Date getLastStatusChangeDate()
  {
    return lastStatusChangeDate;
  }

  /**
   * Resets the values of the monitoring counters.
   */
  private void resetMonitoringCounters()
  {
    numProcessedUpdates = new AtomicInteger(0);
    numRcvdUpdates = new AtomicInteger(0);
    numSentUpdates = new AtomicInteger(0);

    assuredSrSentUpdates = new AtomicInteger(0);
    assuredSrAcknowledgedUpdates = new AtomicInteger(0);
    assuredSrNotAcknowledgedUpdates = new AtomicInteger(0);
    assuredSrTimeoutUpdates = new AtomicInteger(0);
    assuredSrWrongStatusUpdates = new AtomicInteger(0);
    assuredSrReplayErrorUpdates = new AtomicInteger(0);
    assuredSrServerNotAcknowledgedUpdates = new HashMap<Short,Integer>();
    assuredSrReceivedUpdates = new AtomicInteger(0);
    assuredSrReceivedUpdatesAcked = new AtomicInteger(0);
    assuredSrReceivedUpdatesNotAcked = new AtomicInteger(0);
    assuredSdSentUpdates = new AtomicInteger(0);
    assuredSdAcknowledgedUpdates = new AtomicInteger(0);
    assuredSdTimeoutUpdates = new AtomicInteger(0);
    assuredSdServerTimeoutUpdates = new HashMap<Short,Integer>();
  }

  /*
   ********** End of Monitoring Code **************
   */

  /**
   * Start the publish mechanism of the Replication Service.
   * After this method has been called, the publish service can be used
   * by calling the {@link #publish(UpdateMsg)} method.
   *
   * @param replicationServers   The replication servers that should be used.
   * @param window               The window size of this replication domain.
   * @param heartbeatInterval    The heartbeatInterval that should be used
   *                             to check the availability of the replication
   *                             servers.
   *
   * @throws ConfigException     If the DirectoryServer configuration was
   *                             incorrect.
   */
  public void startPublishService(
      Collection<String> replicationServers, int window,
      long heartbeatInterval) throws ConfigException
  {
    if (broker == null)
    {
      /*
       * create the broker object used to publish and receive changes
       */
      broker = new ReplicationBroker(
          this, state, serviceID,
          serverID, window,
          getGenerationID(),
          heartbeatInterval,
          new ReplSessionSecurity(),
          getGroupId());

      broker.start(replicationServers);

      /*
       * Create a replication monitor object responsible for publishing
       * monitoring information below cn=monitor.
       */
      monitor = new ReplicationMonitor(this);

      DirectoryServer.registerMonitorProvider(monitor);
    }
  }

  /**
   * Starts the receiver side of the Replication Service.
   * <p>
   * After this method has been called, the Replication Service will start
   * calling the {@link #processUpdate(UpdateMsg)}.
   * <p>
   * This method must be called once and must be called after the
   * {@link #startPublishService(Collection, int, long)}.
   *
   */
  public void startListenService()
  {
    //
    // Create the listener thread
    listenerThread = new ListenerThread(this);
    listenerThread.start();
  }

  /**
   * Temporarily disable the Replication Service.
   * The Replication Service can be enabled again using
   * {@link #enableService()}.
   * <p>
   * It can be useful to disable the Replication Service when the
   * repository where the replicated information is stored becomes
   * temporarily unavailable and replicated updates can therefore not
   * be replayed during a while.
   */
  public void disableService()
  {
    // Stop the listener thread
    if (listenerThread != null)
    {
      listenerThread.shutdown();
    }

    if (broker != null)
    {
      broker.stop();
    }

    // Wait for the listener thread to stop
    if (listenerThread != null)
      listenerThread.waitForShutdown();
  }

  /**
   * Restart the Replication service after a {@link #disableService()}.
   * <p>
   * The Replication Service will restart from the point indicated by the
   * {@link ServerState} that was given as a parameter to the
   * {@link #startPublishService(Collection, int, long)}
   * at startup time.
   * If some data have changed in the repository during the period of time when
   * the Replication Service was disabled, this {@link ServerState} should
   * therefore be updated by the Replication Domain subclass before calling
   * this method.
   */
  public void enableService()
  {
    broker.start();

    // Create the listener thread
    listenerThread = new ListenerThread(this);
    listenerThread.start();
  }

  /**
   * Definitively stops the Replication Service.
   */
  public void stopDomain()
  {
    DirectoryServer.deregisterMonitorProvider(monitor.getMonitorInstanceName());

    disableService();
    domains.remove(serviceID);
  }

  /**
   * Change the ReplicationDomain parameters.
   *
   * @param replicationServers  The new list of Replication Servers that this
   *                           domain should now use.
   * @param windowSize         The window size that this domain should use.
   * @param heartbeatInterval  The heartbeatInterval that this domain should
   *                           use.
   * @param groupId            The new group id to use
   */
  public void changeConfig(
      Collection<String> replicationServers,
      int windowSize,
      long heartbeatInterval,
      byte groupId)
  {
    this.groupId = groupId;

    if (broker != null)
    {
      if (broker.changeConfig(
          replicationServers, windowSize, heartbeatInterval, groupId))
      {
        disableService();
        enableService();
      }
    }
  }


  /**
   * This method should trigger an export of the replicated data.
   * to the provided outputStream.
   * When finished the outputStream should be flushed and closed.
   *
   * @param output               The OutputStream where the export should
   *                             be produced.
   * @throws DirectoryException  When needed.
   */
  abstract protected void exportBackend(OutputStream output)
           throws DirectoryException;

  /**
   * This method should trigger an import of the replicated data.
   *
   * @param input                The InputStream from which
   *                             the import should be reading entries.
   *
   * @throws DirectoryException  When needed.
   */
  abstract protected void importBackend(InputStream input)
           throws DirectoryException;

  /**
   * This method should return the total number of objects in the
   * replicated domain.
   * This count will be used for reporting.
   *
   * @throws DirectoryException when needed.
   *
   * @return The number of objects in the replication domain.
   */
  public abstract long countEntries() throws DirectoryException;

  /**
   * This method should handle the processing of {@link UpdateMsg} receive
   * from remote replication entities.
   * <p>
   * This method will be called by a single thread and should therefore
   * should not be blocking.
   *
   * @param updateMsg The {@link UpdateMsg} that was received.
   *
   * @return A boolean indicating if the processing is completed at return
   *                   time.
   *                   If <code> true </code> is returned, no further
   *                   processing is necessary.
   *
   *                   If <code> false </code> is returned, the subclass should
   *                   call the method
   *                   {@link #processUpdateDone(UpdateMsg, String)}
   *                   and update the ServerState
   *                   When this processing is complete.
   *
   */
  public abstract boolean processUpdate(UpdateMsg updateMsg);

  /**
   * This method must be called after each call to
   * {@link #processUpdate(UpdateMsg)} when the processing of the update is
   * completed.
   * <p>
   * It is useful for implementation needing to process the update in an
   * asynchronous way or using several threads, but must be called even
   * by implementation doing it in a synchronous, single-threaded way.
   *
   * @param  msg The UpdateMsg whose processing was completed.
   * @param replayErrorMsg if not null, this means an error occurred during the
   * replay of this update, and this is the matching human readable message
   * describing the problem.
   */
  public void processUpdateDone(UpdateMsg msg, String replayErrorMsg)
  {
    broker.updateWindowAfterReplay();

    // Send an ack if it was requested and the group id is the same of the RS
    // one. Only Safe Read mode makes sense in DS for returning an ack.
    byte rsGroupId = broker.getRsGroupId();
    if (msg.isAssured())
    {
      // Assured feature is supported starting from replication protocol V2
      if (broker.getProtocolVersion() >=
        ProtocolVersion.REPLICATION_PROTOCOL_V2)
      {
        AssuredMode msgAssuredMode = msg.getAssuredMode();
        if (msgAssuredMode == AssuredMode.SAFE_READ_MODE)
        {
          if (rsGroupId == groupId)
          {
            // Send the ack
            AckMsg ackMsg = new AckMsg(msg.getChangeNumber());
            if (replayErrorMsg != null)
            {
              // Mark the error in the ack
              //   -> replay error occured
              ackMsg.setHasReplayError(true);
              //   -> replay error occured in our server
              List<Short> idList = new ArrayList<Short>();
              idList.add(serverID);
              ackMsg.setFailedServers(idList);
            }
            broker.publish(ackMsg);
            if (replayErrorMsg != null)
            {
              assuredSrReceivedUpdatesNotAcked.incrementAndGet();
            } else
            {
              assuredSrReceivedUpdatesAcked.incrementAndGet();
            }
          }
        } else if (assuredMode != AssuredMode.SAFE_DATA_MODE)
        {
          Message errorMsg = ERR_DS_UNKNOWN_ASSURED_MODE.get(
            Short.toString(serverID), msgAssuredMode.toString(), serviceID,
            msg.toString());
          logError(errorMsg);
        } else
        {
          // In safe data mode assured update that comes up to a DS requires no
          // ack from a destinator DS. Safe data mode is based on RS acks only
        }
      }
    }

    incProcessedUpdates();
  }

  /**
   * Prepare a message if it is to be sent in assured mode.
   * If the assured mode is enabled, this method should be called before
   * publish(UpdateMsg msg) method. This will configure the update accordingly
   * before it is sent and will prepare the mechanism that will block until the
   * matching ack is received. To wait for the ack after publish call, use
   * the waitForAckIfAssuredEnabled() method.
   * The expected typical usage in a service inheriting from this class is
   * the following sequence:
   * UpdateMsg msg = xxx;
   * prepareWaitForAckIfAssuredEnabled(msg);
   * publish(msg);
   * waitForAckIfAssuredEnabled(msg);
   *
   * Note: prepareWaitForAckIfAssuredEnabled and waitForAckIfAssuredEnabled have
   * no effect if assured replication is disabled.
   * Note: this mechanism should not be used if using publish(byte[] msg)
   * version as usage of these methods is already hidden inside.
   *
   * @param msg The update message to be sent soon.
   */
  protected void prepareWaitForAckIfAssuredEnabled(UpdateMsg msg)
  {
    byte rsGroupId = broker.getRsGroupId();
    /*
     * If assured configured, set message accordingly to request an ack in the
     * right assured mode.
     * No ack requested for a RS with a different group id. Assured
     * replication suported for the same locality, i.e: a topology working in
     * the same
     * geographical location). If we are connected to a RS which is not in our
     * locality, no need to ask for an ack.
     */
    if (assured && (rsGroupId == groupId))
    {
      msg.setAssured(true);
      msg.setAssuredMode(assuredMode);
      if (assuredMode == AssuredMode.SAFE_DATA_MODE)
        msg.setSafeDataLevel(assuredSdLevel);

      // Add the assured message to the list of update that are
      // waiting for acks
      synchronized (waitingAckMsgs)
      {
        waitingAckMsgs.put(msg.getChangeNumber(), msg);
      }
    }
  }

  /**
   * Wait for the processing of an assured message after it has been sent, if
   * assured replication is configured, otherwise, do nothing.
   * The prepareWaitForAckIfAssuredEnabled method should have been called
   * before, see its comment for the full picture.
   *
   * @param msg The UpdateMsg for which we are waiting for an ack.
   * @throws TimeoutException When the configured timeout occurs waiting for the
   * ack.
   */
  protected void waitForAckIfAssuredEnabled(UpdateMsg msg)
    throws TimeoutException
  {
    byte rsGroupId = broker.getRsGroupId();

    // If assured mode configured, wait for acknowledgement for the just sent
    // message
    if (assured && (rsGroupId == groupId))
    {
      // Increment assured replication monitoring counters
      switch (assuredMode)
      {
        case SAFE_READ_MODE:
          assuredSrSentUpdates.incrementAndGet();
          break;
        case SAFE_DATA_MODE:
          assuredSdSentUpdates.incrementAndGet();
          break;
        default:
        // Should not happen
      }
    } else
    {
      // Not assured or bad group id, return immediately
      return;
    }

    // Wait for the ack to be received, timing out if necessary
    long startTime = System.currentTimeMillis();
    synchronized (msg)
    {
      ChangeNumber cn = msg.getChangeNumber();
      while (waitingAckMsgs.containsKey(cn))
      {
        try
        {
          // WARNING: this timeout may be difficult to optimize: too low, it
          // may use too much CPU, too high, it may penalize performance...
          msg.wait(10);
        } catch (InterruptedException e)
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("waitForAck method interrupted for replication " +
              "serviceID: " + serviceID);
          }
          break;
        }
        // Timeout ?
        if ( (System.currentTimeMillis() - startTime) >= assuredTimeout )
        {
          // Timeout occured, be sure that ack is not being received and if so,
          // remove the update from the wait list, log the timeout error and
          // also update assured monitoring counters
          UpdateMsg update;
          synchronized (waitingAckMsgs)
          {
            update = waitingAckMsgs.remove(cn);
          }

          if (update != null)
          {
            // No luck, this is a real timeout
            // Increment assured replication monitoring counters
            switch (msg.getAssuredMode())
            {
              case SAFE_READ_MODE:
                assuredSrNotAcknowledgedUpdates.incrementAndGet();
                assuredSrTimeoutUpdates.incrementAndGet();
                // Increment number of errors for our RS
                updateAssuredErrorsByServer(
                  assuredSrServerNotAcknowledgedUpdates,
                  broker.getRsServerId());
                break;
              case SAFE_DATA_MODE:
                assuredSdTimeoutUpdates.incrementAndGet();
                // Increment number of errors for our RS
                updateAssuredErrorsByServer(assuredSdServerTimeoutUpdates,
                  broker.getRsServerId());
                break;
              default:
              // Should not happen
            }

            throw new TimeoutException("No ack received for message cn: " + cn +
              " and replication servceID: " + serviceID + " after " +
              assuredTimeout + " ms.");
          } else
          {
            // Ack received just before timeout limit: we can exit
            break;
          }
        }
      }
    }
  }

  /**
   * Publish an {@link UpdateMsg} to the Replication Service.
   * <p>
   * The Replication Service will handle the delivery of this {@link UpdateMsg}
   * to all the participants of this Replication Domain.
   * These members will be receive this {@link UpdateMsg} through a call
   * of the {@link #processUpdate(UpdateMsg)} message.
   *
   * @param msg The UpdateMsg that should be pushed.
   */
  public void publish(UpdateMsg msg)
  {
    // Publish the update
    broker.publish(msg);
    state.update(msg.getChangeNumber());
    numSentUpdates.incrementAndGet();
  }

  /**
   * Publish informations to the Replication Service (not assured mode).
   *
   * @param msg  The byte array containing the informations that should
   *             be sent to the remote entities.
   */
  public void publish(byte[] msg)
  {
    UpdateMsg update;
    synchronized (this)
    {
      update = new UpdateMsg(generator.newChangeNumber(), msg);

      // If assured replication is configured, this will prepare blocking
      // mechanism. If assured replication is disabled, this returns
      // immediately
      prepareWaitForAckIfAssuredEnabled(update);

      publish(update);
    }

    try
    {
      // If assured replication is enabled, this will wait for the matching
      // ack or time out. If assured replication is disabled, this returns
      // immediately
      waitForAckIfAssuredEnabled(update);
    } catch (TimeoutException ex)
    {
      // This exception may only be raised if assured replication is
      // enabled
      Message errorMsg = NOTE_DS_ACK_TIMEOUT.get(serviceID, Long.toString(
        assuredTimeout), msg.toString());
      logError(errorMsg);
    }
  }

  /**
   * This method should return the generationID to use for this
   * ReplicationDomain.
   * This method can be called at any time after the ReplicationDomain
   * has been started.
   *
   * @return The GenerationID.
   */
  public abstract long getGenerationID();

  /**
   * Subclasses should use this method to add additional monitoring
   * information in the ReplicationDomain.
   *
   * @return Additional monitoring attributes that will be added in the
   *         ReplicationDomain monitoring entry.
   */
  public Collection<Attribute> getAdditionalMonitoring()
  {
    return new ArrayList<Attribute>();
  }

  /**
   * Returns a boolean indicating if a total update import is currently
   * in Progress.
   *
   * @return A boolean indicating if a total update import is currently
   *         in Progress.
   */
  boolean importInProgress()
  {
    if (ieContext == null)
      return false;
    else
      return ieContext.importInProgress;
  }

  /**
   * Returns a boolean indicating if a total update export is currently
   * in Progress.
   *
   * @return A boolean indicating if a total update export is currently
   *         in Progress.
   */
  boolean exportInProgress()
  {
    if (ieContext == null)
      return false;
    else
      return !ieContext.importInProgress;
  }

  /**
   * Returns the number of entries still to be processed when a total update
   * is in progress.
   *
   * @return The number of entries still to be processed when a total update
   *         is in progress.
   */
  long getLeftEntryCount()
  {
    if (ieContext != null)
      return ieContext.entryLeftCount;
    else
      return 0;
  }

  /**
   * Returns the total number of entries to be processed when a total update
   * is in progress.
   *
   * @return The total number of entries to be processed when a total update
   *         is in progress.
   */
  long getTotalEntryCount()
  {
    if (ieContext != null)
      return ieContext.entryCount;
    else
      return 0;
  }
}
