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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.replication.protocol.ProtocolVersion.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.ServerConstants;

/**
 * This class defines a server handler, which handles all interaction with a
 * peer replication server.
 */
public final class ECLServerHandler extends ServerHandler
{

  // This is a string identifying the operation, provided by the client part
  // of the ECL, used to help interpretation of messages logged.
  String operationId;

  // Iterator on the draftCN database.
  private DraftCNDbIterator draftCNDbIter = null;

  boolean draftCompat = false;
  /**
   * Specifies the last draft changer number (seqnum) requested.
   */
  public int lastDraftCN = 0;
  /**
   * Specifies whether the draft change number (seqnum) db has been read until
   * its end.
   */
  public boolean isEndOfDraftCNReached = false;
  /**
   * Specifies whether the current search has been requested to be persistent
   * or not.
   */
  public short isPersistent;
  /**
   * Specifies the current search phase : INIT or PERSISTENT.
   */
  public int searchPhase = INIT_PHASE;
  /**
   * Specifies the cookie contained in the request, specifying where
   * to start serving the ECL.
   */
  public String startCookie;
  /**
   * Specifies the value of the cookie before the change currently processed
   * is returned. It is updated with the change number of the change
   * currently processed (thus becoming the "current" cookie just
   * before the change is returned.
   */
  public MultiDomainServerState previousCookie =
    new MultiDomainServerState();
  /**
   * Specifies the excluded DNs (like cn=admin, ...).
   */
  public ArrayList<String> excludedServiceIDs = new ArrayList<String>();
  //HashSet<String> excludedServiceIDs = new HashSet<String>();

  /**
   * Eligible changeNumber - only changes older or equal to eligibleCN
   * are published in the ECL.
   */
  public ChangeNumber eligibleCN = null;

  /**
   * Provides a string representation of this object.
   * @return the string representation.
   */
  public String dumpState()
  {
    return this.getClass().getCanonicalName() +
           "[" +
           "[draftCompat=" + draftCompat +
           "] [persistent=" + isPersistent +
           "] [lastDraftCN=" + lastDraftCN +
           "] [isEndOfDraftCNReached=" + isEndOfDraftCNReached +
           "] [searchPhase=" + searchPhase +
           "] [startCookie=" + startCookie +
           "] [previousCookie=" + previousCookie +
       "]]";
  }

  /**
   * Class that manages the 'by domain' state variables for the search being
   * currently processed on the ECL.
   * For example :
   * if search on 'cn=changelog' is being processed when 2 replicated domains
   * dc=us and dc=europe are configured, then there will be 2 DomainContext
   * used, one for ds=us, and one for dc=europe.
   */
  private class DomainContext
  {
    ReplicationServerDomain rsd;

    boolean active;              // active when there are still changes
    // supposed eligible for the ECL.

    MessageHandler mh;           // the message handler from which are read
    // the changes for this domain
    private UpdateMsg nextMsg;
    private UpdateMsg nextNonEligibleMsg;
    ServerState startState;
    ServerState currentState;
    ServerState stopState;
    long domainLatestTrimDate;

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      StringBuilder buffer = new StringBuilder();
      toString(buffer);
      return buffer.toString();
    }
    /**
     * Provide a string representation of this object for debug purpose..
     * @param buffer Append to this buffer.
     */
    public void toString(StringBuilder buffer)
    {
      buffer.append("[ [active=").append(active).append("] [rsd=")
          .append(rsd).append("] [nextMsg=").append(nextMsg).append("(")
          .append(nextMsg != null ?
          new Date(nextMsg.getChangeNumber().getTime()).toString():"")
          .append(")" + "] [nextNonEligibleMsg=").append(nextNonEligibleMsg)
          .append("] [startState=").append(startState).append("] [stopState=")
          .append(stopState).append("] [currentState=").append(currentState)
          .append("]]");
    }

    /**
     * Get the next message eligible regarding
     * the crossDomain eligible CN. Put it in the context table.
     * @param opid The operation id.
     */
    private void getNextEligibleMessageForDomain(String opid)
    {
      if (debugEnabled())
        TRACER.debugInfo(" In ECLServerHandler, for " + mh.getServiceId() +
          " getNextEligibleMessageForDomain(" + opid+ ") "
          + "ctxt=" + toString());

      assert(nextMsg == null);
      try
      {
        // Before get a new message from the domain, evaluate in priority
        // a message that has not been published to the ECL because it was
        // not eligible
        if (nextNonEligibleMsg != null)
        {
          boolean hasBecomeEligible =
            (nextNonEligibleMsg.getChangeNumber().getTime()
                <= eligibleCN.getTime());

          if (debugEnabled())
            TRACER.debugInfo(" In ECLServerHandler, for " + mh.getServiceId() +
                " getNextEligibleMessageForDomain(" + opid+ ") "
              + " stored nonEligibleMsg " + nextNonEligibleMsg
              + " has now become eligible regarding "
              + " the eligibleCN ("+ eligibleCN
              + " ):" + hasBecomeEligible);

          if (hasBecomeEligible)
          {
            // it is now eligible
            nextMsg = nextNonEligibleMsg;
            nextNonEligibleMsg = null;
          }
          // else the oldest is still not eligible - let's wait next
        }
        else
        {
          // Here comes a new message !!!
          // non blocking
          UpdateMsg newMsg;
          do {
            newMsg = mh.getNextMessage(false);
            // when the replication changelog is trimmed, the last (latest) chg
            // is left in the db (whatever its age), and we don't want this chg
            // to be returned in the external changelog.
            // So let's check if the chg time is older than the trim date
          } while ((newMsg!=null) &&
              (newMsg.getChangeNumber().getTime() < domainLatestTrimDate));

          if (debugEnabled())
            TRACER.debugInfo(" In ECLServerHandler, for " + mh.getServiceId() +
                " getNextEligibleMessageForDomain(" + opid+ ") "
                + " got new message : "
                +  " serviceId=[" + mh.getServiceId()
                + "] [newMsg=" + newMsg + "]" + dumpState());

          // in non blocking mode, return null when no more msg
          if (newMsg != null)
          {
            boolean isEligible = (newMsg.getChangeNumber().getTime()
                <= eligibleCN.getTime());

          if (debugEnabled())
              TRACER.debugInfo(" In ECLServerHandler, for " + mh.getServiceId()
                + " getNextEligibleMessageForDomain(" + opid+ ") "
                + "newMsg isEligible=" + isEligible + " since "
                + "newMsg=[" + newMsg.getChangeNumber()
                + " " + new Date(newMsg.getChangeNumber().getTime()).toString()
                + "] eligibleCN=[" + eligibleCN
                + " " + new Date(eligibleCN.getTime()).toString()+"]"
                + dumpState());

            if (isEligible)
            {
              nextMsg = newMsg;
            }
            else
            {
              nextNonEligibleMsg = newMsg;
            }
          }
        }
      }
      catch(Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    /**
     * Unregister the handler from the DomainContext ReplicationDomain.
     * @return Whether the handler has been unregistered with success.
     */
    private boolean unRegisterHandler()
    {
      return rsd.unRegisterHandler(mh);
    }

    /**
     * Stops the DomainContext handler.
     */
    private void stopServer()
    {
      rsd.stopServer(mh);
    }
  }

  // The global list of contexts by domain for the search currently processed.
  DomainContext[] domainCtxts = new DomainContext[0];

  private String clDomCtxtsToString(String msg)
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append(msg);
    buffer.append("\n");
    for (DomainContext domainCtxt : domainCtxts) {
      domainCtxt.toString(buffer);
      buffer.append("\n");
    }
    return buffer.toString();
  }

  static int UNDEFINED_PHASE = 0;
  static int INIT_PHASE = 1;
  static int PERSISTENT_PHASE = 2;

  /**
   * Starts this handler based on a start message received from remote server.
   * @param inECLStartMsg The start msg provided by the remote server.
   * @return Whether the remote server requires encryption or not.
   * @throws DirectoryException When a problem occurs.
   */
  public boolean processStartFromRemote(ServerStartECLMsg inECLStartMsg)
  throws DirectoryException
  {
    try
    {
      session.setProtocolVersion(getCompatibleVersion(inECLStartMsg
          .getVersion()));
      serverURL = inECLStartMsg.getServerURL();
      setInitialServerState(inECLStartMsg.getServerState());
      setSendWindowSize(inECLStartMsg.getWindowSize());
      if (getProtocolVersion() > ProtocolVersion.REPLICATION_PROTOCOL_V1)
      {
        // We support connection from a V1 RS
        // Only V2 protocol has the group id in repl server start message
        this.groupId = inECLStartMsg.getGroupId();
      }
    }
    catch(Exception e)
    {
      Message message = Message.raw(e.getLocalizedMessage());
      throw new DirectoryException(ResultCode.OTHER, message);
    }
    return inECLStartMsg.getSSLEncryption();
  }

  /**
   * Sends a start message to the remote ECL server.
   *
   * @return The StartMsg sent.
   * @throws IOException
   *           When an exception occurs.
   */
  private StartMsg sendStartToRemote() throws IOException
  {
    final StartMsg startMsg;

    // Before V4 protocol, we sent a ReplServerStartMsg
    if (getProtocolVersion() < ProtocolVersion.REPLICATION_PROTOCOL_V4)
    {
      // Peer DS uses protocol < V4 : send it a ReplServerStartMsg
      startMsg = new ReplServerStartMsg(replicationServerId,
          replicationServerURL, getServiceId(), maxRcvWindow,
          replicationServerDomain.getDbServerState(),
          localGenerationId, sslEncryption, getLocalGroupId(),
          replicationServerDomain.getReplicationServer()
              .getDegradedStatusThreshold());
    }
    else
    {
      // Peer DS uses protocol V4 : send it a ReplServerStartDSMsg
      startMsg = new ReplServerStartDSMsg(replicationServerId,
          replicationServerURL, getServiceId(), maxRcvWindow,
          new ServerState(), localGenerationId, sslEncryption,
          getLocalGroupId(), 0, replicationServer.getWeight(), 0);
    }

    send(startMsg);
    return startMsg;
  }

  /**
   * Creates a new handler object to a remote replication server.
   * @param session The session with the remote RS.
   * @param queueSize The queue size to manage updates to that RS.
   * @param replicationServerURL The hosting local RS URL.
   * @param replicationServerId The hosting local RS serverId.
   * @param replicationServer The hosting local RS object.
   * @param rcvWindowSize The receiving window size.
   */
  public ECLServerHandler(
      Session session,
      int queueSize,
      String replicationServerURL,
      int replicationServerId,
      ReplicationServer replicationServer,
      int rcvWindowSize)
  {
    super(session, queueSize, replicationServerURL, replicationServerId,
        replicationServer, rcvWindowSize);
    try
    {
      setServiceIdAndDomain(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT, true);
    }
    catch(DirectoryException de)
    {
      // no chance to have a bad domain set here
    }


  }

  /**
   * Creates a new handler object to a remote replication server.
   * @param replicationServerURL The hosting local RS URL.
   * @param replicationServerId The hosting local RS serverId.
   * @param replicationServer The hosting local RS object.
   * @param startECLSessionMsg the start parameters.
   * @throws DirectoryException when an errors occurs.
   */
  public ECLServerHandler(
      String replicationServerURL,
      int replicationServerId,
      ReplicationServer replicationServer,
      StartECLSessionMsg startECLSessionMsg)
  throws DirectoryException
  {
    // queueSize is hard coded to 1 else super class hangs for some reason
    super(null, 1, replicationServerURL, replicationServerId,
        replicationServer, 0);
    try
    {
      setServiceIdAndDomain(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT, true);
    }
    catch(DirectoryException de)
    {
      // no chance to have a bad domain set here
    }
    this.initialize(startECLSessionMsg);
  }

  /**
   * Starts the handler from a remote start message received from
   * the remote server.
   * @param inECLStartMsg The provided ReplServerStart message received.
   */
  public void startFromRemoteServer(ServerStartECLMsg inECLStartMsg)
  {
    try
    {
      // Process start from remote
      boolean sessionInitiatorSSLEncryption =
        processStartFromRemote(inECLStartMsg);

      // lock with timeout
      if (replicationServerDomain != null)
      {
        lockDomain(true);
      }

      localGenerationId = -1;

      // send start to remote
      StartMsg outStartMsg = sendStartToRemote();

      // log
      logStartHandshakeRCVandSND(inECLStartMsg, outStartMsg);

      // until here session is encrypted then it depends on the negotiation
      // The session initiator decides whether to use SSL.
      if (!sessionInitiatorSSLEncryption)
        session.stopEncryption();

      // wait and process StartSessionMsg from remote RS
      StartECLSessionMsg inStartECLSessionMsg =
        waitAndProcessStartSessionECLFromRemoteServer();
      if (inStartECLSessionMsg == null)
      {
        // client wants to properly close the connection (client sent a
        // StopMsg)
        logStopReceived();
        abortStart(null);
        return;
      }

      logStartECLSessionHandshake(inStartECLSessionMsg);

      // initialization
      initialize(inStartECLSessionMsg);
    }
    catch(DirectoryException de)
    {
      abortStart(de.getMessageObject());
    }
    catch(Exception e)
    {
      abortStart(Message.raw(e.getLocalizedMessage()));
    }
    finally
    {
      if ((replicationServerDomain != null) &&
          replicationServerDomain.hasLock())
      {
        replicationServerDomain.release();
      }
    }
  }

  /**
   * Wait receiving the StartSessionMsg from the remote DS and process it.
   * @return the startSessionMsg received
   * @throws DirectoryException
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws DataFormatException
   * @throws NotSupportedOldVersionPDUException
   */
  private StartECLSessionMsg waitAndProcessStartSessionECLFromRemoteServer()
  throws DirectoryException, IOException, ClassNotFoundException,
  DataFormatException,
  NotSupportedOldVersionPDUException
  {
    ReplicationMsg msg = session.receive();

    if (msg instanceof StopMsg)
    {
      // client wants to stop handshake (was just for handshake phase one for RS
      // choice). Return null to make the session be terminated.
      return null;
    }
    else if (!(msg instanceof StartECLSessionMsg))
    {
      Message message = Message
          .raw("Protocol error: StartECLSessionMsg required." + msg
              + " received.");
      abortStart(message);
      return null;
    }
    else
    {
      // Process StartSessionMsg sent by remote DS
      return (StartECLSessionMsg) msg;
    }
  }

  /**
   * Initialize the handler from a provided cookie value.
   * @param crossDomainStartState The provided cookie value.
   * @throws DirectoryException When an error is raised.
   */
  public void initializeCLSearchFromGenState(String crossDomainStartState)
  throws DirectoryException
  {
    initializeCLDomCtxts(crossDomainStartState, false);
  }

  /**
   * Initialize the handler from a provided draft first change number.
   * @param startDraftCN The provided draft first change number.
   * @throws DirectoryException When an error is raised.
   */
  public void initializeCLSearchFromDraftCN(int startDraftCN)
  throws DirectoryException
  {
    String crossDomainStartState;
    try
    {
      draftCompat = true;

      DraftCNDbHandler draftCNDb = replicationServer.getDraftCNDbHandler();

      // Any possible optimization on draft CN in the request filter ?
      if (startDraftCN <= 1)
      {
        // Request filter DOES NOT contain any firstDraftCN
        // So we'll generate from the beginning of what we have stored here.

        // Get starting state from first DraftCN from DraftCNdb
        if (draftCNDb.count() == 0)
        {
        // DraftCNdb IS EMPTY hence start from what we have in the changelog db.
          isEndOfDraftCNReached = true;
          crossDomainStartState = null;
        }
        else
        {
          // DraftCNdb IS NOT EMPTY hence start from
          // the generalizedServerState related to the start of the draftDb
          crossDomainStartState = draftCNDb.getValue(draftCNDb.getFirstKey());

          // And get an iterator to traverse the draftCNDb
          draftCNDbIter =
              draftCNDb.generateIterator(draftCNDb.getFirstKey());
        }
      }
      else
      {
        // Request filter DOES contain a startDraftCN

        // Read the draftCNDb to see whether it contains startDraftCN
        crossDomainStartState = draftCNDb.getValue(startDraftCN);

        if (crossDomainStartState != null)
        {
          // startDraftCN (from the request filter) is present in the draftCnDb
          // Get an iterator to traverse the draftCNDb
          draftCNDbIter =
            draftCNDb.generateIterator(startDraftCN);
        }
        else
        {
          // startDraftCN provided in the request IS NOT in the DraftCNDb

          // Get the draftLimits (from the eligibleCN got at the beginning of
          // the operation) in order to have the first and possible last
          // DraftCN.
          int[] limits = replicationServer.getECLDraftCNLimits(
              eligibleCN, excludedServiceIDs);

          // If the startDraftCN provided is lower than the first Draft CN in
          // the DB, let's use the lower limit.
          if (startDraftCN < limits[0])
          {
            crossDomainStartState = draftCNDb.getValue(limits[0]);

            if (crossDomainStartState != null)
            {
              // startDraftCN (from the request filter) is present in the
              // draftCnDb.
              // Get an iterator to traverse it.
              draftCNDbIter =
                draftCNDb.generateIterator(limits[0]);
            }
            else
            {
              // This shouldn't happen
              // Let's start from what we have in the changelog db.
              isEndOfDraftCNReached = true;
              crossDomainStartState = null;
            }
          }
          else if (startDraftCN<=limits[1])
          {
            // startDraftCN is between first and potential last and has never
            // been returned yet
            if (draftCNDb.count() == 0)
            {
              // db is empty
              isEndOfDraftCNReached = true;
              crossDomainStartState = null;
            }
            else
            {
             crossDomainStartState = draftCNDb.getValue(draftCNDb.getLastKey());
              draftCNDbIter =
                  draftCNDb.generateIterator(draftCNDb.getLastKey());
            }
            // TODO:ECL ... ok we'll start from the end of the draftCNDb BUT ...
            // this may be very long. Work on perf improvement here.
          }
          else
          {
            // startDraftCN is > the potential last DraftCN
            throw new DirectoryException(ResultCode.SUCCESS, Message.raw(""));
          }
        }
      }
      this.draftCompat = true;

      initializeCLDomCtxts(crossDomainStartState, true);
    }
    catch(DirectoryException de)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, de);
      if (draftCNDbIter != null)
        draftCNDbIter.releaseCursor();
      throw(de);
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      if (draftCNDbIter != null)
        draftCNDbIter.releaseCursor();
      throw new DirectoryException(
          ResultCode.OPERATIONS_ERROR,
          Message.raw(Category.SYNC,
              Severity.FATAL_ERROR,e.getLocalizedMessage()));
    }
  }

  /**
   * Initialize the context for each domain.
   * @param  providedCookie the provided generalized state
   * @param  allowUnknownDomains Provides all changes for domains not included
   *           in the provided cookie.
   * @throws DirectoryException When an error occurs.
   */
  public void initializeCLDomCtxts(String providedCookie,
      boolean allowUnknownDomains)
  throws DirectoryException
  {
    /*
    This map is initialized from the providedCookie.
    Below, it will be traversed and each domain configured with ECL will be
    checked and removed from the map.
    At the end, normally the map should be empty.
    Depending on allowUnknownDomains provided flag, a non empty map will
    be considered as an error when allowUnknownDomains is false.
    */
    Map<String,ServerState> startStatesFromProvidedCookie =
      new HashMap<String,ServerState>();

    ReplicationServer rs = this.replicationServer;

    // Parse the provided cookie and overwrite startState from it.
    if ((providedCookie != null) && (providedCookie.length()!=0))
      startStatesFromProvidedCookie =
        MultiDomainServerState.splitGenStateToServerStates(providedCookie);

    try
    {
      Iterator<ReplicationServerDomain> rsdi = rs.getDomainIterator();

      // Creates the table that will contain the real-time info for each
      // and every domain.
      HashSet<DomainContext> tmpSet = new HashSet<DomainContext>();
      String missingDomains = "";
      if (rsdi != null)
      {
        while (rsdi.hasNext())
        {
          // process a domain
          ReplicationServerDomain rsd = rsdi.next();

          // skip the 'unreal' changelog domain
          if (rsd == this.replicationServerDomain)
            continue;

          // skip the excluded domains
          if (excludedServiceIDs.contains(rsd.getBaseDn()))
          {
            // this is an excluded domain
            if (allowUnknownDomains)
              startStatesFromProvidedCookie.remove(rsd.getBaseDn());
            continue;
          }

          // skip unused domains
          if (rsd.getDbServerState().isEmpty())
            continue;

          // Creates the new domain context
          DomainContext newDomainCtxt = new DomainContext();
          newDomainCtxt.active = true;
          newDomainCtxt.rsd = rsd;
          newDomainCtxt.domainLatestTrimDate = rsd.getLatestDomainTrimDate();

          // Assign the start state for the domain
          if (isPersistent ==
            StartECLSessionMsg.PERSISTENT_CHANGES_ONLY)
          {
            newDomainCtxt.startState = rsd.getEligibleState(eligibleCN);
            startStatesFromProvidedCookie.remove(rsd.getBaseDn());
          }
          else
          {
            // let's take the start state for this domain from the provided
            // cookie
            newDomainCtxt.startState =
              startStatesFromProvidedCookie.remove(rsd.getBaseDn());

            if ((providedCookie==null)||(providedCookie.length()==0)
                ||allowUnknownDomains)
            {
              // when there is no cookie provided in the request,
              // let's start traversing this domain from the beginning of
              // what we have in the replication changelog
              if (newDomainCtxt.startState == null)
              {
                ChangeNumber latestTrimCN =
                    new ChangeNumber(newDomainCtxt.domainLatestTrimDate, 0,0);
                newDomainCtxt.startState = rsd.getStartState()
                        .duplicateOnlyOlderThan(latestTrimCN);
              }
            }
            else
            {
              // when there is a cookie provided in the request,
              if (newDomainCtxt.startState == null)
              {
                missingDomains += (rsd.getBaseDn() + ":;");
                continue;
              }
              else if (!newDomainCtxt.startState.isEmpty())
              {
                /*
                when the provided startState is older than the replication
                changelogdb startState, it means that the replication
                changelog db has been trimmed and the cookie is not valid
                anymore.
                */
                boolean cookieTooOld = false;
                for (int aServerId : rsd.getStartState())
                {
                  ChangeNumber dbOldestChange =
                    rsd.getStartState().getMaxChangeNumber(aServerId);
                  ChangeNumber providedChange =
                    newDomainCtxt.startState.getMaxChangeNumber(aServerId);
                  if ((providedChange != null)
                      && (providedChange.older(dbOldestChange)))
                  {
                    cookieTooOld=true;
                  }
                }

                if (cookieTooOld)
                {
                  // the provided start
                  throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                      ERR_RESYNC_REQUIRED_TOO_OLD_DOMAIN_IN_PROVIDED_COOKIE.get(
                          newDomainCtxt.rsd.getBaseDn()));
                }
              }
            }

            // Set the stop state for the domain from the eligibleCN
            newDomainCtxt.stopState = rsd.getEligibleState(eligibleCN);
          }
          newDomainCtxt.currentState = new ServerState();

          // Creates an unconnected SH for the domain
          MessageHandler mh = new MessageHandler(maxQueueSize,
              replicationServerURL, replicationServerId, replicationServer);
          // set initial state
          mh.setInitialServerState(newDomainCtxt.startState);
          // set serviceID and domain
          mh.setServiceIdAndDomain(rsd.getBaseDn(), false);
          // register the unconnected into the domain
          rsd.registerHandler(mh);
          newDomainCtxt.mh = mh;

          previousCookie.update(newDomainCtxt.rsd.getBaseDn(),
                                newDomainCtxt.startState);

          // store the new context
          tmpSet.add(newDomainCtxt);
        }
      }

      if (missingDomains.length()>0)
      {
        // If there are domain missing in the provided cookie,
        // the request is rejected and a full resync is required.
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_RESYNC_REQUIRED_MISSING_DOMAIN_IN_PROVIDED_COOKIE.get(
              missingDomains,
              "<" + (providedCookie + missingDomains)+ ">"));
      }

      domainCtxts = tmpSet.toArray(new DomainContext[tmpSet.size()]);

      /*
      When it is valid to have the provided cookie containing unknown domains
      (allowUnknownDomains is true), 2 cases must be considered :
      - if the cookie contains a domain that is replicated but where
      ECL is disabled, then this case is considered above
      - if the cookie contains a domain that is even not replicated
      then this case need to be considered here in another loop.
      */
      if (!startStatesFromProvidedCookie.isEmpty())
      {
        if (allowUnknownDomains)
          for (String providedDomain : startStatesFromProvidedCookie.keySet())
            if (rs.getReplicationServerDomain(providedDomain, false) == null)
              // the domain provided in the cookie is not replicated
              startStatesFromProvidedCookie.remove(providedDomain);
      }

      // Now do the final checking
      if (!startStatesFromProvidedCookie.isEmpty())
      {
        /*
        After reading all the known domains from the provided cookie, there
        is one (or several) domain that are not currently configured.
        This domain has probably been removed or replication disabled on it.
        The request is rejected and full resync is required.
        */
        StringBuilder sb = new StringBuilder();
        for (DomainContext domainCtxt : domainCtxts) {
          sb.append(domainCtxt.rsd.getBaseDn()).append(":")
              .append(domainCtxt.startState).append(";");
        }
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_RESYNC_REQUIRED_UNKNOWN_DOMAIN_IN_PROVIDED_COOKIE.get(
                startStatesFromProvidedCookie.toString() ,sb.toString()));
      }

      // the next record from the DraftCNdb should be the one
      startCookie = providedCookie;

      // Initializes each and every domain with the next(first) eligible message
      // from the domain.
      for (DomainContext domainCtxt : domainCtxts) {
        domainCtxt.getNextEligibleMessageForDomain(operationId);

        if (domainCtxt.nextMsg == null)
          domainCtxt.active = false;
      }
    }
    catch(DirectoryException de)
    {
      throw de;
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      // FIXME:ECL do not publish internal exception plumb to the client
      throw new DirectoryException(
          ResultCode.OPERATIONS_ERROR,
          Message.raw(Category.SYNC, Severity.INFORMATION,"Exception raised: " +
              e),
              e);
    }
    if (debugEnabled())
      TRACER.debugInfo(
        " initializeCLDomCtxts ends with " + " " + dumpState());
  }

  /**
   * Registers this handler into its related domain and notifies the domain.
   */
  private void registerIntoDomain()
  {
    if (replicationServerDomain!=null)
      replicationServerDomain.registerHandler(this);
  }

  /**
   * Shutdown this handler.
   */
  @Override
  public void shutdown()
  {
    if (debugEnabled())
      TRACER.debugInfo(this + " shutdown()" + draftCNDbIter);
    if (this.draftCNDbIter != null)
    {
      draftCNDbIter.releaseCursor();
      draftCNDbIter = null;
    }
    for (DomainContext domainCtxt : domainCtxts) {
      if (!domainCtxt.unRegisterHandler()) {
        logError(Message.raw(Category.SYNC, Severity.NOTICE,
            this + " shutdown() - error when unregistering handler "
                + domainCtxt.mh));
      }
      domainCtxt.stopServer();
    }
    super.shutdown();
    domainCtxts = null;
  }

  /**
   * Request to shutdown the associated writer.
   */
  @Override
  protected void shutdownWriter()
  {
    shutdownWriter = true;
    if (writer!=null)
    {
      ECLServerWriter eclWriter = (ECLServerWriter)this.writer;
      eclWriter.shutdownWriter();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getMonitorInstanceName()
  {
    String str = serverURL + " " + String.valueOf(serverId);

    return "Connected External Changelog Server " + str +
    ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT;
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

    // Add the specific RS ones
    attributes.add(Attributes.create("External-Changelog-Server",
        serverURL));

    // TODO:ECL No monitoring exist for ECL.
    return attributes;
  }
  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    String localString;
    localString = "External changelog Server ";
    if (this.serverId != 0)
      localString += serverId + " " + serverURL + " " + getServiceId()
       + " " + this.getOperationId();
    else
      localString += this.getClass().getCanonicalName()+ " " + operationId;
    return localString;
  }
  /**
   * Gets the status of the connected DS.
   * @return The status of the connected DS.
   */
  @Override
  public ServerStatus getStatus()
  {
    // There is no other status possible for the ECL Server Handler to
    // be normally connected.
    return ServerStatus.NORMAL_STATUS;
  }
  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDataServer()
  {
    return true;
  }

  /**
   * Initialize the handler.
   * @param startECLSessionMsg The provided starting state.
   * @throws DirectoryException when a problem occurs.
   */
  public void initialize(StartECLSessionMsg startECLSessionMsg)
  throws DirectoryException
  {

    this.operationId = startECLSessionMsg.getOperationId();

    isPersistent  = startECLSessionMsg.isPersistent();
    lastDraftCN   = startECLSessionMsg.getLastDraftChangeNumber();
    searchPhase   = INIT_PHASE;
    try
    {
      previousCookie = new MultiDomainServerState(
        startECLSessionMsg.getCrossDomainServerState());
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      throw new DirectoryException(
          ResultCode.PROTOCOL_ERROR,
          ERR_INVALID_COOKIE_SYNTAX.get());
    }

    excludedServiceIDs = startECLSessionMsg.getExcludedServiceIDs();
    replicationServer.disableEligibility(excludedServiceIDs);
    eligibleCN = replicationServer.getEligibleCN();

    if (startECLSessionMsg.getECLRequestType()==
      StartECLSessionMsg.REQUEST_TYPE_FROM_COOKIE)
    {
      initializeCLSearchFromGenState(
          startECLSessionMsg.getCrossDomainServerState());
    }
    else if (startECLSessionMsg.getECLRequestType()==
      StartECLSessionMsg.REQUEST_TYPE_FROM_DRAFT_CHANGE_NUMBER)
    {
      initializeCLSearchFromDraftCN(
          startECLSessionMsg.getFirstDraftChangeNumber());
    }

    if (session != null)
    {
      try
      {
        // Disable timeout for next communications
        session.setSoTimeout(0);
      }
      catch(Exception e) { /* do nothing */ }

      // sendWindow MUST be created before starting the writer
      sendWindow = new Semaphore(sendWindowSize);

      // create reader
      reader = new ServerReader(session, this);
      reader.start();

      if (writer == null)
      {
        // create writer
        writer = new ECLServerWriter(session,this,replicationServerDomain);
        writer.start();
      }

      // Resume the writer
      ((ECLServerWriter)writer).resumeWriter();

      // TODO:ECL Potential race condition if writer not yet resumed here
    }

    if (isPersistent == StartECLSessionMsg.PERSISTENT_CHANGES_ONLY)
    {
      closeInitPhase();
    }

    /* TODO: From replication changenumber
    //--
    if (startCLMsg.getStartMode()==2)
    {
      if (CLSearchFromProvidedExactCN(startCLMsg.getChangeNumber()))
        return;
    }

    //--
    if (startCLMsg.getStartMode()==4)
    {
      // to get the CL first and last
      initializeCLDomCtxts(null); // from start
      ChangeNumber crossDomainEligibleCN = computeCrossDomainEligibleCN();

      try
      {
        // to get the CL first and last
        // last rely on the crossDomainEligibleCN thus must have been
        // computed before
        int[] limits = computeCLLimits(crossDomainEligibleCN);
        // Send the response
        CLLimitsMsg msg = new CLLimitsMsg(limits[0], limits[1]);
        session.publish(msg);
      }
      catch(Exception e)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
        try
        {
          session.publish(
            new ErrorMsg(
             replicationServerDomain.getReplicationServer().getServerId(),
             serverId,
             Message.raw(Category.SYNC, Severity.INFORMATION,
                 "Exception raised: " + e.getMessage())));
        }
        catch(IOException ioe)
        {
          // FIXME: close conn ?
        }
      }
      return;
    }
     */

    // Store into domain
    registerIntoDomain();

    if (debugEnabled())
      TRACER.debugInfo(
          this.getClass().getCanonicalName()+ " " + operationId +
          " initialized: " +
          " " + dumpState() + " " +
          " " + clDomCtxtsToString(""));

  }

  /**
   * Select the next update that must be sent to the server managed by this
   * ServerHandler.
   *
   * @return the next update that must be sent to the server managed by this
   *         ServerHandler.
   * @exception DirectoryException when an error occurs.
   */
  public ECLUpdateMsg takeECLUpdate()
  throws DirectoryException
  {
    boolean interrupted = true;
    ECLUpdateMsg msg = getNextECLUpdate();

    // TODO:ECL We should refactor so that a SH always have a session
    if (session == null)
      return msg;

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
    }
    return msg;
  }

  /**
   * Get the next message - non blocking - null when none.
   * This method is currently not used but we don't want to keep the mother
   * class method since it make no sense for ECLServerHandler.
   * @param synchronous - not used
   * @return the next message
   */
  @Override
  protected UpdateMsg getNextMessage(boolean synchronous)
  {
    UpdateMsg msg = null;
    try
    {
      ECLUpdateMsg eclMsg = getNextECLUpdate();
      if (eclMsg!=null)
        msg = eclMsg.getUpdateMsg();
    }
    catch(DirectoryException de)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, de);
    }
    return msg;
  }

  /**
   * Returns the next update message for the External Changelog (ECL).
   * @return the ECL update message, null when there aren't anymore.
   * @throws DirectoryException when an error occurs.
   */
  public ECLUpdateMsg getNextECLUpdate()
  throws DirectoryException
  {
    ECLUpdateMsg oldestChange = null;

    if (debugEnabled())
      TRACER.debugInfo("In cn=changelog" + this +
          " getNextECLUpdate starts: " + dumpState());

    try
    {

      //  getMessage:
      //    get the oldest msg:
      //    after:
      //     if stopState of domain is covered then domain is out candidate
      //       until no domain candidate mean generalized stopState
      //       has been reached
      //     else
      //       get one msg from that domain
      //       no (null) msg returned: should not happen since we go to a state
      //       that is computed/expected

      // Persistent:
      // ----------
      //  step 1&2: same as non persistent
      //
      //  step 3: reinit all domain are candidate
      //    take the oldest
      //    if one domain has no msg, still is candidate

      int iDom;
      boolean continueLooping = true;
      while ((continueLooping) && (searchPhase == INIT_PHASE))
      {
        // Step 1 & 2
        if (searchPhase == INIT_PHASE)
        {
          // Default is not to loop, with one exception
          continueLooping = false;

          iDom = getOldestChangeFromDomainCtxts();

          // iDom == -1 means that there is no oldest change to process
          if (iDom == -1)
          {
            closeInitPhase();

            // signals end of phase 1 to the caller
            return null;
          }

          // Build the ECLUpdateMsg to be returned
          oldestChange = new ECLUpdateMsg(
              (LDAPUpdateMsg)domainCtxts[iDom].nextMsg,
              null, // cookie will be set later
              domainCtxts[iDom].rsd.getBaseDn(),
              0); //  draftChangeNumber may be set later
          domainCtxts[iDom].nextMsg = null;

          if (draftCompat)
          {
            // either retrieve a draftCN from the draftCNDb
            // or assign a new draftCN and store in the db

            DraftCNDbHandler draftCNDb=replicationServer.getDraftCNDbHandler();

            // We also need to check if the draftCNdb is consistent with
            // the changelogdb.
            // if not, 2 potential reasons
            // a/ : changelog has been purged (trim)let's traverse the draftCNDb
            // b/ : changelog is late .. let's traverse the changelogDb
            // The following loop allows to loop until being on the same cn
            // in the 2 dbs

            // replogcn : the oldest change from the changelog db
            ChangeNumber cnFromChangelogDb =
              oldestChange.getUpdateMsg().getChangeNumber();
            String dnFromChangelogDb = domainCtxts[iDom].rsd.getBaseDn();

            while (true)
            {
              if (!isEndOfDraftCNReached)
              {
                // we did not reach yet the end of the DraftCNdb

                // the next change from the DraftCN db
                ChangeNumber cnFromDraftCNDb = draftCNDbIter.getChangeNumber();
                String dnFromDraftCNDb = draftCNDbIter.getServiceID();

                // are replogcn and DraftCNcn should be the same change ?
                int areCNEqual = cnFromChangelogDb.compareTo(cnFromDraftCNDb);
                int areDNEqual = dnFromChangelogDb.compareTo(dnFromDraftCNDb);

                if (debugEnabled())
                  TRACER.debugInfo("getNextECLUpdate generating draftCN "
                    + " comparing the 2 db DNs :"
                    + dnFromChangelogDb + "?=" + cnFromChangelogDb
                    + " timestamps:" + new Date(cnFromChangelogDb.getTime())
                    + " ?older" +   new Date(cnFromDraftCNDb.getTime()));

                if ((areDNEqual==0) && (areCNEqual==0))
                {
                  // same domain and same CN => same change

                  // assign the DraftCN found to the change from the changelogdb
                  if (debugEnabled())
                    TRACER.debugInfo("getNextECLUpdate generating draftCN "
                      + " assigning draftCN=" + draftCNDbIter.getDraftCN()
                      + " to change=" + oldestChange);

                  oldestChange.setDraftChangeNumber(
                      draftCNDbIter.getDraftCN());

                  break;
                }
                else
                {
                  // replogcn and DraftCNcn are NOT on the same change
                  if (cnFromDraftCNDb.older(cnFromChangelogDb))
                  {
                    // the change from the DraftCNDb is older
                    // that means that the change has been purged from the
                    // changelogDb (and DraftCNdb not yet been trimmed)

                    try
                    {
                      // let's traverse the DraftCNdb searching for the change
                      // found in the changelogDb.
                      if (debugEnabled())
                      TRACER.debugInfo("getNextECLUpdate generating draftCN "
                          + " will skip " + cnFromDraftCNDb
                          + " and read next change from the DraftCNDb.");

                      isEndOfDraftCNReached = !draftCNDbIter.next();

                      if (debugEnabled())
                      TRACER.debugInfo("getNextECLUpdate generating draftCN "
                          + " has skiped to "
                          + " sn=" + draftCNDbIter.getDraftCN()
                          + " cn=" + draftCNDbIter.getChangeNumber()
                          + " End of draftCNDb ?"+isEndOfDraftCNReached);

                      if (isEndOfDraftCNReached)
                      {
                        // we are at the end of the DraftCNdb in the append mode

                        // generate a new draftCN and assign to this change
                        oldestChange.setDraftChangeNumber(
                            replicationServer.getNewDraftCN());

                        // store in DraftCNdb the pair
                        // (draftCN_of_the_cur_change, state_before_this_change)
                        draftCNDb.add(
                            oldestChange.getDraftChangeNumber(),
                            previousCookie.toString(),
                            oldestChange.getServiceId(),
                            oldestChange.getUpdateMsg().getChangeNumber());

                        break;
                      }
                    }
                    catch(Exception e)
                    {
                      // TODO: At least log a warning
                    }
                  }
                  else
                  {
                    // the change from the changelogDb is older
                    // it should have been stored lately
                    // let's continue to traverse the changelogdb
                    if (debugEnabled())
                    TRACER.debugInfo("getNextECLUpdate: will skip "
                        + cnFromChangelogDb
                        + " and read next from the regular changelog.");
                    continueLooping = true;
                    break; // TO BE CHECKED
                  }
                }
              }
              else
              {
                // we are at the end of the DraftCNdb in the append mode
                // store in DraftCNdb the pair
                // (DraftCN of the current change, state before this change)
                oldestChange.setDraftChangeNumber(
                    replicationServer.getNewDraftCN());

                draftCNDb.add(
                    oldestChange.getDraftChangeNumber(),
                    this.previousCookie.toString(),
                    domainCtxts[iDom].rsd.getBaseDn(),
                    oldestChange.getUpdateMsg().getChangeNumber());

                break;
              }
            } // while DraftCN
          } // if draftCompat

          // here we have the right oldest change
          // and in the draft case, we have its draft changenumber

          // Set and test the domain of the oldestChange see if we reached
          // the end of the phase for this domain
          domainCtxts[iDom].currentState.update(
              oldestChange.getUpdateMsg().getChangeNumber());

          if (domainCtxts[iDom].currentState.cover(domainCtxts[iDom].stopState))
          {
            domainCtxts[iDom].active = false;
          }
          if (draftCompat && (lastDraftCN>0) &&
              (oldestChange.getDraftChangeNumber()>lastDraftCN))
          {
            domainCtxts[iDom].active = false;
          }
          if (domainCtxts[iDom].active)
          {
            // populates the table with the next eligible msg from iDom
            // in non blocking mode, return null when no more eligible msg
            domainCtxts[iDom].getNextEligibleMessageForDomain(operationId);
          }
        } // phase == INIT_PHASE
      } // while (...)

      if (searchPhase == PERSISTENT_PHASE)
      {
        if (debugEnabled())
          clDomCtxtsToString("In getNextECLUpdate (persistent): " +
          "looking for the generalized oldest change");

        for (DomainContext domainCtxt : domainCtxts) {
          // get next msg
          domainCtxt.getNextEligibleMessageForDomain(operationId);
        }

        // take the oldest one
        iDom = getOldestChangeFromDomainCtxts();

        if (iDom != -1)
        {
          String suffix = this.domainCtxts[iDom].rsd.getBaseDn();

          oldestChange = new ECLUpdateMsg(
              (LDAPUpdateMsg)domainCtxts[iDom].nextMsg,
              null, // set later
              suffix, 0);
          domainCtxts[iDom].nextMsg = null; // clean

          domainCtxts[iDom].currentState.update(
              oldestChange.getUpdateMsg().getChangeNumber());

          if (draftCompat)
          {
            // should generate DraftCN
            DraftCNDbHandler draftCNDb =replicationServer.getDraftCNDbHandler();

            oldestChange.setDraftChangeNumber(
                replicationServer.getNewDraftCN());

            // store in DraftCNdb the pair
            // (DraftCN of the current change, state before this change)
            draftCNDb.add(
                oldestChange.getDraftChangeNumber(),
                this.previousCookie.toString(),
                domainCtxts[iDom].rsd.getBaseDn(),
                oldestChange.getUpdateMsg().getChangeNumber());
          }
        }
      }
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      throw new DirectoryException(
          ResultCode.OPERATIONS_ERROR,
          Message.raw(Category.SYNC, Severity.INFORMATION,"Exception raised: "),
          e);
    }

    if (oldestChange != null)
    {
      if (debugEnabled())
        TRACER.debugInfo("getNextECLUpdate updates previousCookie:"
          + oldestChange.getUpdateMsg().getChangeNumber());

      // Update the current state
      previousCookie.update(
          oldestChange.getServiceId(),
          oldestChange.getUpdateMsg().getChangeNumber());

      // Set the current value of global state in the returned message
      oldestChange.setCookie(previousCookie);

      if (debugEnabled())
        TRACER.debugInfo("getNextECLUpdate returns result oldest change =" +
                oldestChange);

    }
    return oldestChange;
  }

  /**
   * Terminates the first (non persistent) phase of the search on the ECL.
   */
  private void closeInitPhase()
  {
    // starvation of changelog messages
    // all domain have been unactived means are covered
    if (debugEnabled())
      TRACER.debugInfo("In cn=changelog" + "," + this + " closeInitPhase(): "
          + dumpState());

    // go to persistent phase if one
    for (DomainContext domainCtxt : domainCtxts) domainCtxt.active = true;

    if (this.isPersistent != StartECLSessionMsg.NON_PERSISTENT)
    {
      // INIT_PHASE is done AND search is persistent => goto PERSISTENT_PHASE
      searchPhase = PERSISTENT_PHASE;

      if (writer ==null)
      {
        writer = new ECLServerWriter(session,this,replicationServerDomain);
        writer.start();  // start suspended
      }

    }
    else
    {
      // INIT_PHASE is done AND search is not persistent => re-init
      searchPhase = UNDEFINED_PHASE;
    }

    if (draftCNDbIter!=null)
    {
      // End of INIT_PHASE => always release the iterator
      draftCNDbIter.releaseCursor();
      draftCNDbIter = null;
    }
  }

  /**
   * Get the index in the domainCtxt table of the domain with the oldest change.
   * @return the index of the domain with the oldest change, -1 when none.
   */
  private int getOldestChangeFromDomainCtxts()
  {
    int oldest = -1;
    for (int i=0; i<domainCtxts.length; i++)
    {
      if ((domainCtxts[i].active))
      {
        // on the first loop, oldest==-1
        // .msg is null when the previous (non blocking) nextMessage did
        // not have any eligible msg to return
        if (domainCtxts[i].nextMsg != null)
        {
          if ((oldest==-1) ||
              (domainCtxts[i].nextMsg.compareTo(domainCtxts[oldest].nextMsg)<0))
          {
            oldest = i;
          }
        }
      }
    }

    if (debugEnabled())
      TRACER.debugInfo("In cn=changelog"
          + "," + this + " getOldestChangeFromDomainCtxts() returns " +
          ((oldest!=-1)?domainCtxts[oldest].nextMsg:"-1"));

    return oldest;
  }

  /**
   * Returns the client operation id.
   * @return The client operation id.
   */
  public String getOperationId()
  {
    return operationId;
  }

  /**
   * Getter for the persistent property of the current search.
   * @return Whether the current search is persistent or not.
   */
  public short isPersistent() {
    return this.isPersistent;
  }

  /**
   * Getter for the current search phase (INIT or PERSISTENT).
   * @return Whether the current search is persistent or not.
   */
  public int getSearchPhase() {
    return this.searchPhase;
  }

  /**
   * Refresh the eligibleCN by requesting the replication server.
   */
  public void refreshEligibleCN()
  {
    eligibleCN = replicationServer.getEligibleCN();
  }

}
