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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogDBIterator;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.replication.protocol.StartECLSessionMsg.*;

/**
 * This class defines a server handler, which handles all interaction with a
 * peer replication server.
 */
public final class ECLServerHandler extends ServerHandler
{

  /**
   * This is a string identifying the operation, provided by the client part of
   * the ECL, used to help interpretation of messages logged.
   */
  private String operationId;

  /** Iterator on the changelogDB database. */
  private ChangelogDBIterator changelogDBIter = null;

  private boolean draftCompat = false;
  /**
   * Specifies the last draft changer number (seqnum) requested.
   */
  private int lastDraftCN = 0;
  /**
   * Specifies whether the draft change number (seqnum) db has been read until
   * its end.
   */
  private boolean isEndOfDraftCNReached = false;
  /**
   * Specifies whether the current search has been requested to be persistent
   * or not.
   */
  private short isPersistent;
  /**
   * Specifies the current search phase : INIT or PERSISTENT.
   */
  private int searchPhase = INIT_PHASE;
  /**
   * Specifies the cookie contained in the request, specifying where
   * to start serving the ECL.
   */
  private String startCookie;
  /**
   * Specifies the value of the cookie before the change currently processed is
   * returned. It is updated with the CSN of the change currently processed
   * (thus becoming the "current" cookie just before the change is returned.
   */
  private MultiDomainServerState previousCookie = new MultiDomainServerState();
  /**
   * Specifies the excluded DNs (like cn=admin, ...).
   */
  private Set<String> excludedBaseDNs = new HashSet<String>();

  /**
   * Eligible CSN - only changes older or equal to eligibleCSN * are published
   * in the ECL.
   */
  private CSN eligibleCSN;

  /**
   * Provides a string representation of this object.
   * @return the string representation.
   */
  public String dumpState()
  {
    return getClass().getCanonicalName() +
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
    private ReplicationServerDomain rsd;

    /**
     * active when there are still changes supposed eligible for the ECL.
     */
    private boolean active;

    /**
     * the message handler from which are reading the changes for this domain.
     */
    private MessageHandler mh;
    private UpdateMsg nextMsg;
    private UpdateMsg nextNonEligibleMsg;
    private ServerState startState;
    private ServerState currentState;
    private ServerState stopState;
    private long domainLatestTrimDate;

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
      buffer.append("[ [active=").append(active)
          .append("] [rsd=").append(rsd)
          .append("] [nextMsg=").append(nextMsg).append("(")
          .append(nextMsg != null ?
          new Date(nextMsg.getCSN().getTime()).toString():"")
          .append(")")
          .append("] [nextNonEligibleMsg=").append(nextNonEligibleMsg)
          .append("] [startState=").append(startState)
          .append("] [stopState=").append(stopState)
          .append("] [currentState=").append(currentState)
          .append("]]");
    }

    /**
     * Get the next message eligible regarding
     * the crossDomain eligible CSN. Put it in the context table.
     * @param opid The operation id.
     */
    private void getNextEligibleMessageForDomain(String opid)
    {
      if (debugEnabled())
        TRACER.debugInfo(" In ECLServerHandler, for " + mh.getBaseDN() +
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
            (nextNonEligibleMsg.getCSN().getTime()
                <= eligibleCSN.getTime());

          if (debugEnabled())
            TRACER.debugInfo(" In ECLServerHandler, for " + mh.getBaseDN() +
                " getNextEligibleMessageForDomain(" + opid+ ") "
              + " stored nonEligibleMsg " + nextNonEligibleMsg
              + " has now become eligible regarding "
              + " the eligibleCSN ("+ eligibleCSN
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
              (newMsg.getCSN().getTime() < domainLatestTrimDate));

          if (debugEnabled())
            TRACER.debugInfo(" In ECLServerHandler, for " + mh.getBaseDN() +
                " getNextEligibleMessageForDomain(" + opid+ ") "
                + " got new message : "
                +  " baseDN=[" + mh.getBaseDN()
                + "] [newMsg=" + newMsg + "]" + dumpState());

          // in non blocking mode, return null when no more msg
          if (newMsg != null)
          {
            boolean isEligible = (newMsg.getCSN().getTime()
                <= eligibleCSN.getTime());

          if (debugEnabled())
              TRACER.debugInfo(" In ECLServerHandler, for " + mh.getBaseDN()
                + " getNextEligibleMessageForDomain(" + opid+ ") "
                + "newMsg isEligible=" + isEligible + " since "
                + "newMsg=[" + newMsg.getCSN()
                + " " + new Date(newMsg.getCSN().getTime())
                + "] eligibleCSN=[" + eligibleCSN
                + " " + new Date(eligibleCSN.getTime())+"]"
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

  /**
   * The global list of contexts by domain for the search currently processed.
   */
  private DomainContext[] domainCtxts = new DomainContext[0];

  private String clDomCtxtsToString(String msg)
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append(msg).append("\n");
    for (DomainContext domainCtxt : domainCtxts) {
      domainCtxt.toString(buffer);
      buffer.append("\n");
    }
    return buffer.toString();
  }

  private static int UNDEFINED_PHASE = 0;
  private static int INIT_PHASE = 1;
  private static int PERSISTENT_PHASE = 2;

  /**
   * Starts this handler based on a start message received from remote server.
   * @param inECLStartMsg The start msg provided by the remote server.
   * @return Whether the remote server requires encryption or not.
   * @throws DirectoryException When a problem occurs.
   */
  private boolean processStartFromRemote(ServerStartECLMsg inECLStartMsg)
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
      startMsg = createReplServerStartMsg();
    }
    else
    {
      // Peer DS uses protocol V4 : send it a ReplServerStartDSMsg
       startMsg = new ReplServerStartDSMsg(getReplicationServerId(),
           getReplicationServerURL(), getBaseDN(), maxRcvWindow,
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
   * @param replicationServer The hosting local RS object.
   * @param rcvWindowSize The receiving window size.
   */
  public ECLServerHandler(
      Session session,
      int queueSize,
      ReplicationServer replicationServer,
      int rcvWindowSize)
  {
    super(session, queueSize, replicationServer, rcvWindowSize);
    try
    {
      setBaseDNAndDomain(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT, true);
    }
    catch(DirectoryException de)
    {
      // no chance to have a bad domain set here
    }
  }

  /**
   * Creates a new handler object to a remote replication server.
   * @param replicationServer The hosting local RS object.
   * @param startECLSessionMsg the start parameters.
   * @throws DirectoryException when an errors occurs.
   */
  public ECLServerHandler(ReplicationServer replicationServer,
      StartECLSessionMsg startECLSessionMsg) throws DirectoryException
  {
    // queueSize is hard coded to 1 else super class hangs for some reason
    this(null, 1, replicationServer, 0);
    initialize(startECLSessionMsg);
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
      boolean sessionInitiatorSSLEncryption =
        processStartFromRemote(inECLStartMsg);

      lockDomainWithTimeout();

      localGenerationId = -1;

      StartMsg outStartMsg = sendStartToRemote();
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
        // client wants to properly close the connection (client sent a StopMsg)
        logStopReceived();
        abortStart(null);
        return;
      }

      logStartECLSessionHandshake(inStartECLSessionMsg);

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
      releaseDomainLock();
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
  private void initializeCLSearchFromGenState(String crossDomainStartState)
      throws DirectoryException
  {
    initializeChangelogDomainCtxts(crossDomainStartState, false);
  }

  /**
   * Initialize the handler from a provided draft first change number.
   * @param startDraftCN The provided draft first change number.
   * @throws DirectoryException When an error is raised.
   */
  private void initializeCLSearchFromDraftCN(int startDraftCN)
      throws DirectoryException
  {
    try
    {
      this.draftCompat = true;

      final String providedCookie = findCookie(startDraftCN);
      initializeChangelogDomainCtxts(providedCookie, true);
    }
    catch(DirectoryException de)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, de);
      releaseIterator();
      throw de;
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      releaseIterator();
      throw new DirectoryException(
          ResultCode.OPERATIONS_ERROR,
          Message.raw(Category.SYNC,
              Severity.FATAL_ERROR,e.getLocalizedMessage()));
    }
  }

  /**
   * Finds in the draft changelog DB the cookie corresponding to the passed in
   * startDraftCN.
   *
   * @param startDraftCN
   *          the start draftCN coming from the request filter.
   * @return the cookie corresponding to the passed in startDraftCN.
   * @throws Exception
   *           if a database problem occurred
   * @throws DirectoryException
   *           if a database problem occurred
   */
  private String findCookie(final int startDraftCN) throws ChangelogException,
      DirectoryException
  {
    final ChangelogDB changelogDB = replicationServer.getChangelogDB();

    if (startDraftCN <= 1)
    {
      // Request filter DOES NOT contain any firstDraftCN
      // So we'll generate from the first DraftCN in the DraftCNdb
      if (changelogDB.isEmpty())
      {
        // FIXME JNR if we find a way to make draftCNDb.isEmpty() a non costly
        // operation, then I think we can move this check to the top of this
        // method
        isEndOfDraftCNReached = true;
        return null;
      }

      final int firstDraftCN = changelogDB.getFirstDraftCN();
      final String crossDomainStartState =
          changelogDB.getPreviousCookie(firstDraftCN);
      changelogDBIter = changelogDB.generateIterator(firstDraftCN);
      return crossDomainStartState;
    }

    // Request filter DOES contain a startDraftCN

    // Read the draftCNDb to see whether it contains startDraftCN
    String crossDomainStartState = changelogDB.getPreviousCookie(startDraftCN);
    if (crossDomainStartState != null)
    {
      // found the provided startDraftCN, let's return it
      changelogDBIter = changelogDB.generateIterator(startDraftCN);
      return crossDomainStartState;
    }

    // startDraftCN provided in the request IS NOT in the DraftCNDb

    /*
     * Get the draftLimits (from the eligibleCSN got at the beginning of the
     * operation) in order to have the first and possible last DraftCN.
     */
    final int[] limits =
        replicationServer.getECLDraftCNLimits(eligibleCSN, excludedBaseDNs);
    final int firstDraftCN = limits[0];
    final int lastDraftCN = limits[1];

    // If the startDraftCN provided is lower than the first Draft CN in
    // the DB, let's use the lower limit.
    if (startDraftCN < firstDraftCN)
    {
      crossDomainStartState = changelogDB.getPreviousCookie(firstDraftCN);
      if (crossDomainStartState != null)
      {
        changelogDBIter = changelogDB.generateIterator(firstDraftCN);
        return crossDomainStartState;
      }

      // This should not happen
      isEndOfDraftCNReached = true;
      return null;
    }
    else if (startDraftCN <= lastDraftCN)
    {
      // startDraftCN is between first and potential last and has never
      // been returned yet
      if (changelogDB.isEmpty())
      {
        isEndOfDraftCNReached = true;
        return null;
      }

      final int lastKey = changelogDB.getLastDraftCN();
      crossDomainStartState = changelogDB.getPreviousCookie(lastKey);
      changelogDBIter = changelogDB.generateIterator(lastKey);
      return crossDomainStartState;

      // TODO:ECL ... ok we'll start from the end of the draftCNDb BUT ...
      // this may be very long. Work on perf improvement here.
    }

    // startDraftCN is greater than the potential last DraftCN
    throw new DirectoryException(ResultCode.SUCCESS, Message.raw(""));
  }

  /**
   * Initialize the context for each domain.
   * @param  providedCookie the provided generalized state
   * @param  allowUnknownDomains Provides all changes for domains not included
   *           in the provided cookie.
   * @throws DirectoryException When an error occurs.
   */
  private void initializeChangelogDomainCtxts(String providedCookie,
      boolean allowUnknownDomains) throws DirectoryException
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
      // Creates the table that will contain the real-time info for each
      // and every domain.
      Set<DomainContext> tmpSet = new HashSet<DomainContext>();
      String missingDomains = "";
      for (Iterator<ReplicationServerDomain> iter = rs.getDomainIterator();
           iter.hasNext();)
      {
        ReplicationServerDomain rsd = iter.next();

        // skip the 'unreal' changelog domain
        if (rsd == this.replicationServerDomain)
          continue;

        // skip the excluded domains
        if (excludedBaseDNs.contains(rsd.getBaseDn()))
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
        if (isPersistent == PERSISTENT_CHANGES_ONLY)
        {
          newDomainCtxt.startState = rsd.getEligibleState(eligibleCSN);
          startStatesFromProvidedCookie.remove(rsd.getBaseDn());
        }
        else
        {
          // let's take the start state for this domain from the provided
          // cookie
          newDomainCtxt.startState =
              startStatesFromProvidedCookie.remove(rsd.getBaseDn());

          if (providedCookie == null
              || providedCookie.length() == 0
              || allowUnknownDomains)
          {
            // when there is no cookie provided in the request,
            // let's start traversing this domain from the beginning of
            // what we have in the replication changelog
            if (newDomainCtxt.startState == null)
            {
              CSN latestTrimCSN =
                  new CSN(newDomainCtxt.domainLatestTrimDate, 0, 0);
              newDomainCtxt.startState =
                  rsd.getStartState().duplicateOnlyOlderThan(latestTrimCSN);
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
              if (hasCookieBeenTrimmedFromDB(rsd, newDomainCtxt.startState))
              {
                throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                    ERR_RESYNC_REQUIRED_TOO_OLD_DOMAIN_IN_PROVIDED_COOKIE
                        .get(newDomainCtxt.rsd.getBaseDn()));
              }
            }
          }

          // Set the stop state for the domain from the eligibleCSN
          newDomainCtxt.stopState = rsd.getEligibleState(eligibleCSN);
        }
        newDomainCtxt.currentState = new ServerState();

        // Creates an unconnected SH for the domain
        MessageHandler mh = new MessageHandler(maxQueueSize, replicationServer);
        mh.setInitialServerState(newDomainCtxt.startState);
        mh.setBaseDNAndDomain(rsd.getBaseDn(), false);
        // register the unconnected into the domain
        rsd.registerHandler(mh);
        newDomainCtxt.mh = mh;

        previousCookie.update(newDomainCtxt.rsd.getBaseDn(),
                              newDomainCtxt.startState);

        // store the new context
        tmpSet.add(newDomainCtxt);
      }

      if (missingDomains.length()>0)
      {
        // If there are domain missing in the provided cookie,
        // the request is rejected and a full resync is required.
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_RESYNC_REQUIRED_MISSING_DOMAIN_IN_PROVIDED_COOKIE.get(
              missingDomains,
              "<" + providedCookie + missingDomains + ">"));
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
            if (rs.getReplicationServerDomain(providedDomain) == null)
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

  private boolean hasCookieBeenTrimmedFromDB(ReplicationServerDomain rsDomain,
      ServerState cookie)
  {
    /*
    when the provided startState is older than the replication
    changelogdb startState, it means that the replication
    changelog db has been trimmed and the cookie is not valid
    anymore.
    */
    for (int serverId : rsDomain.getStartState())
    {
      CSN dbOldestChange =
          rsDomain.getStartState().getCSN(serverId);
      CSN providedChange = cookie.getCSN(serverId);
      if (providedChange != null
          && providedChange.older(dbOldestChange))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Registers this handler into its related domain and notifies the domain.
   */
  private void registerIntoDomain()
  {
    if (replicationServerDomain != null)
      replicationServerDomain.registerHandler(this);
  }

  /**
   * Shutdown this handler.
   */
  @Override
  public void shutdown()
  {
    if (debugEnabled())
      TRACER.debugInfo(this + " shutdown()");
    releaseIterator();
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

  private void releaseIterator()
  {
    if (this.changelogDBIter != null)
    {
      this.changelogDBIter.close();
      this.changelogDBIter = null;
    }
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
    return "Connected External Changelog Server " + serverURL + " " + serverId
        + ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT;
  }

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    // Get the generic ones
    List<Attribute> attributes = super.getMonitorData();

    // Add the specific RS ones
    attributes.add(Attributes.create("External-Changelog-Server", serverURL));

    // TODO:ECL No monitoring exist for ECL.
    return attributes;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final String eclServer = "External changelog Server ";
    if (this.serverId != 0)
    {
      return eclServer + serverId + " " + serverURL + " " + getBaseDN() + " "
          + operationId;
    }
    return eclServer + getClass().getCanonicalName() + " " + operationId;
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
  private void initialize(StartECLSessionMsg startECLSessionMsg)
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

    excludedBaseDNs = startECLSessionMsg.getExcludedBaseDNs();
    replicationServer.disableEligibility(excludedBaseDNs);
    eligibleCSN = replicationServer.getEligibleCSN();

    initializeChangelogSearch(startECLSessionMsg);

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

    if (isPersistent == PERSISTENT_CHANGES_ONLY)
    {
      closeInitPhase();
    }

    /* TODO: From replication CSN
    //--
    if (startCLMsg.getStartMode()==2)
    {
      if (CLSearchFromProvidedExactCSN(startCLMsg.getCSN()))
        return;
    }

    //--
    if (startCLMsg.getStartMode()==4)
    {
      // to get the CL first and last
      initializeCLDomCtxts(null); // from start
      CSN crossDomainEligibleCSN = computeCrossDomainEligibleCSN();

      try
      {
        // to get the CL first and last
        // last rely on the crossDomainEligibleCSN thus must have been
        // computed before
        int[] limits = computeCLLimits(crossDomainEligibleCSN);
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
             replicationServer.getServerId(),
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
      TRACER.debugInfo(getClass().getCanonicalName() + " " + operationId
          + " initialized: " + " " + dumpState() + " " + " "
          + clDomCtxtsToString(""));
  }

  private void initializeChangelogSearch(StartECLSessionMsg msg)
      throws DirectoryException
  {
    short requestType = msg.getECLRequestType();
    if (requestType == REQUEST_TYPE_FROM_COOKIE)
    {
      initializeCLSearchFromGenState(msg.getCrossDomainServerState());
    }
    else if (requestType == REQUEST_TYPE_FROM_DRAFT_CHANGE_NUMBER)
    {
      initializeCLSearchFromDraftCN(msg.getFirstDraftChangeNumber());
    }
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
    ECLUpdateMsg msg = getNextECLUpdate();

    // TODO:ECL We should refactor so that a SH always have a session
    if (session == null)
      return msg;

    boolean interrupted = true;
    boolean acquired = false;
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
  public ECLUpdateMsg getNextECLUpdate() throws DirectoryException
  {
    if (debugEnabled())
      TRACER.debugInfo("In cn=changelog" + this +
          " getNextECLUpdate starts: " + dumpState());

    ECLUpdateMsg oldestChange = null;
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

      boolean continueLooping = true;
      while (continueLooping && searchPhase == INIT_PHASE)
      {
        // Step 1 & 2
        DomainContext oldestContext = findOldestChangeFromDomainCtxts();
        if (oldestContext == null)
        { // there is no oldest change to process
          closeInitPhase();

          // signals end of phase 1 to the caller
          return null;
        }

        // Build the ECLUpdateMsg to be returned
        final ECLUpdateMsg change = new ECLUpdateMsg(
            (LDAPUpdateMsg) oldestContext.nextMsg,
            null, // cookie will be set later
            oldestContext.rsd.getBaseDn(),
            0); //  draftChangeNumber may be set later
        oldestContext.nextMsg = null;

        // Default is not to loop, with one exception
        continueLooping = false;
        if (draftCompat)
        {
          continueLooping = !assignDraftCN(change);
        }

        // here we have the right oldest change
        // and in the draft case, we have its draft changenumber

        // Set and test the domain of the oldestChange see if we reached
        // the end of the phase for this domain
        oldestContext.currentState.update(
            change.getUpdateMsg().getCSN());

        if (oldestContext.currentState.cover(oldestContext.stopState))
        {
          oldestContext.active = false;
        }
        if (draftCompat && (lastDraftCN>0) &&
            (change.getDraftChangeNumber()>lastDraftCN))
        {
          oldestContext.active = false;
        }
        if (oldestContext.active)
        {
          // populates the table with the next eligible msg from iDom
          // in non blocking mode, return null when no more eligible msg
          oldestContext.getNextEligibleMessageForDomain(operationId);
        }
        oldestChange = change;
      }

      if (searchPhase == PERSISTENT_PHASE)
      {
        if (debugEnabled())
          clDomCtxtsToString("In getNextECLUpdate (persistent): " +
          "looking for the generalized oldest change");

        for (DomainContext domainCtxt : domainCtxts) {
          domainCtxt.getNextEligibleMessageForDomain(operationId);
        }

        DomainContext oldestContext = findOldestChangeFromDomainCtxts();
        if (oldestContext != null)
        {
          final ECLUpdateMsg change = new ECLUpdateMsg(
              (LDAPUpdateMsg) oldestContext.nextMsg,
              null, // set later
              oldestContext.rsd.getBaseDn(),
              0);
          oldestContext.nextMsg = null; // clean

          oldestContext.currentState.update(
              change.getUpdateMsg().getCSN());

          if (draftCompat)
          {
            assignNewDraftCNAndStore(change);
          }
          oldestChange = change;
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
          + oldestChange.getUpdateMsg().getCSN());

      // Update the current state
      previousCookie.update(
          oldestChange.getBaseDN(),
          oldestChange.getUpdateMsg().getCSN());

      // Set the current value of global state in the returned message
      oldestChange.setCookie(previousCookie);

      if (debugEnabled())
        TRACER.debugInfo("getNextECLUpdate returns result oldest change =" +
                oldestChange);

    }
    return oldestChange;
  }

  /**
   * Either retrieves a draftCN from the draftCNDb, or assign a new draftCN and
   * store in the db.
   *
   * @param oldestChange
   *          the oldestChange where to assign the draftCN
   * @return <code>true</code> if a draftCN has been assigned to the provided
   *         oldestChange, <code>false</code> otherwise
   * @throws DirectoryException
   *           if any problem occur
   */
  private boolean assignDraftCN(final ECLUpdateMsg oldestChange)
      throws DirectoryException
  {
    // We also need to check if the draftCNdb is consistent with
    // the changelogdb.
    // if not, 2 potential reasons
    // a/ : changelog has been purged (trim)let's traverse the draftCNDb
    // b/ : changelog is late .. let's traverse the changelogDb
    // The following loop allows to loop until being on the same cn
    // in the 2 dbs

    // replogCSN : the oldest change from the changelog db
    CSN csnFromChangelogDb = oldestChange.getUpdateMsg().getCSN();
    String dnFromChangelogDb = oldestChange.getBaseDN();

    while (true)
    {
      if (isEndOfDraftCNReached)
      {
        // we are at the end of the DraftCNdb in the append mode
        assignNewDraftCNAndStore(oldestChange);
        return true;
      }


      // the next change from the DraftCN db
      CSN csnFromDraftCNDb = changelogDBIter.getCSN();
      String dnFromDraftCNDb = changelogDBIter.getBaseDN();

      if (debugEnabled())
        TRACER.debugInfo("getNextECLUpdate generating draftCN "
            + " comparing the 2 db DNs :" + dnFromChangelogDb + "?="
            + csnFromChangelogDb + " timestamps:"
            + new Date(csnFromChangelogDb.getTime()) + " ?older"
            + new Date(csnFromDraftCNDb.getTime()));


      if (areSameChange(csnFromChangelogDb, dnFromChangelogDb,
          csnFromDraftCNDb, dnFromDraftCNDb))
      {
        if (debugEnabled())
          TRACER.debugInfo("getNextECLUpdate generating draftCN "
              + " assigning draftCN=" + changelogDBIter.getDraftCN()
              + " to change=" + oldestChange);

        oldestChange.setDraftChangeNumber(changelogDBIter.getDraftCN());
        return true;
      }


      if (!csnFromDraftCNDb.older(csnFromChangelogDb))
      {
        // the change from the changelogDb is older
        // it should have been stored lately
        // let's continue to traverse the changelogdb
        if (debugEnabled())
          TRACER.debugInfo("getNextECLUpdate: will skip " + csnFromChangelogDb
              + " and read next from the regular changelog.");
        return false; // TO BE CHECKED
      }


      // the change from the DraftCNDb is older
      // that means that the change has been purged from the
      // changelogDb (and DraftCNdb not yet been trimmed)
      try
      {
        // let's traverse the DraftCNdb searching for the change
        // found in the changelogDb.
        if (debugEnabled())
          TRACER.debugInfo("getNextECLUpdate generating draftCN "
              + " will skip " + csnFromDraftCNDb
              + " and read next change from the DraftCNDb.");

        isEndOfDraftCNReached = !changelogDBIter.next();

        if (debugEnabled())
          TRACER.debugInfo("getNextECLUpdate generating draftCN "
              + " has skipped to " + " sn=" + changelogDBIter.getDraftCN()
              + " csn=" + changelogDBIter.getCSN()
              + " End of draftCNDb ?" + isEndOfDraftCNReached);
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        // FIXME There is an opportunity for an infinite loop here if the DB
        // continuously throws DatabaseExceptions
      }
    }
  }

  private boolean areSameChange(CSN csn1, String dn1, CSN csn2, String dn2)
  {
    boolean sameDN = dn1.compareTo(dn2) == 0;
    boolean sameCSN = csn1.compareTo(csn2) == 0;
    return sameDN && sameCSN;
  }

  private void assignNewDraftCNAndStore(ECLUpdateMsg change)
      throws DirectoryException
  {
    // generate a new draftCN and assign to this change
    change.setDraftChangeNumber(replicationServer.getNewDraftCN());

    // store in changelogDB the pair
    // (DraftCN of the current change, state before this change)
    ChangelogDB changelogDB = replicationServer.getChangelogDB();
    changelogDB.add(
        change.getDraftChangeNumber(),
        previousCookie.toString(),
        change.getBaseDN(),
        change.getUpdateMsg().getCSN());
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

    if (this.isPersistent != NON_PERSISTENT)
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

    // End of INIT_PHASE => always release the iterator
    releaseIterator();
  }

  /**
   * Find the domainCtxt of the domain with the oldest change.
   *
   * @return the domainCtxt of the domain with the oldest change, null when
   *         none.
   */
  private DomainContext findOldestChangeFromDomainCtxts()
  {
    DomainContext oldestCtxt = null;
    for (DomainContext domainCtxt : domainCtxts)
    {
      if (domainCtxt.active
          // .msg is null when the previous (non blocking) nextMessage did
          // not have any eligible msg to return
          && domainCtxt.nextMsg != null
          && (oldestCtxt == null
              || domainCtxt.nextMsg.compareTo(oldestCtxt.nextMsg) < 0))
      {
        oldestCtxt = domainCtxt;
      }
    }

    if (debugEnabled())
      TRACER.debugInfo("In cn=changelog," + this
          + " getOldestChangeFromDomainCtxts() returns "
          + ((oldestCtxt != null) ? oldestCtxt.nextMsg : "-1"));

    return oldestCtxt;
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
   * Refresh the eligibleCSN by requesting the replication server.
   */
  public void refreshEligibleCSN()
  {
    eligibleCSN = replicationServer.getEligibleCSN();
  }

}
