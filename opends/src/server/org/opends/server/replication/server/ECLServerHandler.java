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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2014 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.protocol.ProtocolVersion.*;
import static org.opends.server.replication.protocol.StartECLSessionMsg
.ECLRequestType.*;
import static org.opends.server.replication.protocol.StartECLSessionMsg
.Persistent.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a server handler, which handles all interaction with a
 * peer replication server.
 */
public final class ECLServerHandler extends ServerHandler
{

  /**
   * Marks the end of the search on the External Change Log.
   */
  private static int UNDEFINED_PHASE = 0;
  /**
   * The External Change Log initialization phase currently reads the changes
   * from all the ReplicaDBs from oldest to newest and then:
   * <ul>
   * <li>Matches each ReplicaDBs change with the corresponding record in
   * {@link ChangeNumberIndexDB}, then assign its changeNumber in memory before
   * returning the change to the client</li>
   * <li>Once it reaches the end of the {@link ChangeNumberIndexDB}, it inserts
   * each ReplicaDBs change in the {@link ChangeNumberIndexDB} and gets back and
   * assign the changeNumber in memory to the ReplicaDBs change.</li>
   * </ul>
   * Once this phase is over the current search moves to the
   * {@link #UNDEFINED_PHASE} or the {@link #PERSISTENT_PHASE} depending on the
   * search type.
   *
   * @see #getSearchPhase()
   */
  private static int INIT_PHASE = 1;
  /**
   * The persistent phase is only used for persistent searches on the External
   * ChangeLog. It comes after the {@link #INIT_PHASE} and sends back to the
   * client newly added changes to the {@link ChangeNumberIndexDB}.
   */
  private static int PERSISTENT_PHASE = 2;

  private StartECLSessionMsg startECLSessionMsg;

  /** Cursor on the {@link ChangeNumberIndexDB}. */
  private DBCursor<ChangeNumberIndexRecord> cnIndexDBCursor;

  private boolean draftCompat = false;
  /**
   * Specifies whether the change number db has been read until its end.
   */
  private boolean isEndOfCNIndexDBReached = false;
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
   * The global list of contexts by domain for the search currently processed.
   */
  private Set<DomainContext> domainCtxts = Collections.emptySet();

  /**
   * Provides a string representation of this object.
   * @return the string representation.
   */
  private String dumpState()
  {
    return getClass().getSimpleName() +
           " [draftCompat=" + draftCompat +
           ", persistent=" + startECLSessionMsg.getPersistent() +
           ", startChangeNumber=" + startECLSessionMsg.getLastChangeNumber() +
           ", endOfCNIndexDBReached=" + isEndOfCNIndexDBReached +
           ", searchPhase=" + searchPhase +
           ", startCookie=" + startCookie +
           ", previousCookie=" + previousCookie +
           "]";
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
    private final ReplicationServerDomain rsDomain;

    /**
     * Active when there are still changes supposed eligible for the ECL. It is
     * active by default.
     */
    private boolean active = true;
    private UpdateMsg nextMsg;

    /**
     * the message handler from which are reading the changes for this domain.
     */
    private final MessageHandler mh;
    private final ServerState startState;
    private final ServerState currentState = new ServerState();
    private final ServerState stopState;
    private final long domainLatestTrimDate;

    public DomainContext(ReplicationServerDomain domain,
        ServerState startState, ServerState stopState, MessageHandler mh)
    {
      this.rsDomain = domain;
      this.startState = startState;
      this.stopState = stopState;
      this.mh = mh;
      this.domainLatestTrimDate = domain.getLatestDomainTrimDate();
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      StringBuilder buffer = new StringBuilder();
      toString(buffer);
      return buffer.toString();
    }

    private StringBuilder toString(StringBuilder buffer)
    {
      buffer.append(getClass().getSimpleName());
      buffer.append(" [");
      buffer.append(active ? "active" : "inactive");
      buffer.append(", baseDN=\"").append(rsDomain.getBaseDN()).append("\"");
      if (nextMsg != null)
      {
        buffer.append(", csn=").append(nextMsg.getCSN().toStringUI());
      }
      buffer.append(", nextMsg=[").append(nextMsg);
      buffer.append("]")
          .append(", startState=").append(startState)
          .append(", currentState=").append(currentState)
          .append(", stopState=").append(stopState)
          .append("]");
      return buffer;
    }

    /**
     * Computes the next available message for this domain context.
     */
    private void computeNextAvailableMessage()
    {
      nextMsg = getNextMessage();
      if (debugEnabled())
      {
        TRACER.debugInfo("In ECLServerHandler, for baseDN="
            + mh.getBaseDNString() + " computeNextAvailableMessage("
            + getOperationId() + ") : newMsg=[" + nextMsg + "] " + dumpState());
      }
    }

    private UpdateMsg getNextMessage()
    {
      while (true)
      {
        final UpdateMsg newMsg = mh.getNextMessage(false /* non blocking */);

        if (newMsg instanceof ReplicaOfflineMsg)
        {
          // and ReplicaOfflineMsg cannot be returned to a search on cn=changelog
          // proceed as if it was never returned
          continue;
        }
        else if (newMsg == null)
        { // in non blocking mode, null means no more messages
          return null;
        }
        else if (newMsg.getCSN().getTime() >= domainLatestTrimDate)
        {
          // when the replication changelog is trimmed, the newest change
          // is left in the DB (whatever its age), and we don't want this change
          // to be returned in the external changelog.
          // So let's check if the change time is older than the trim date
          return newMsg;
        }
      }
    }

    /**
     * Unregister the handler from the DomainContext ReplicationDomain.
     * @return Whether the handler has been unregistered with success.
     */
    private boolean unRegisterHandler()
    {
      return rsDomain.unRegisterHandler(mh);
    }

    /**
     * Stops the DomainContext handler.
     */
    private void stopServer()
    {
      rsDomain.stopServer(mh);
    }
  }

  private String domaimCtxtsToString(String msg)
  {
    final StringBuilder buffer = new StringBuilder();
    buffer.append(msg).append("\n");
    for (DomainContext domainCtxt : domainCtxts) {
      domainCtxt.toString(buffer).append("\n");
    }
    return buffer.toString();
  }

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
      DN baseDN = DN.decode(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
      setBaseDNAndDomain(baseDN, true);
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
      {
        session.stopEncryption();
      }

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
   *
   * @return the startSessionMsg received
   * @throws Exception
   */
  private StartECLSessionMsg waitAndProcessStartSessionECLFromRemoteServer()
      throws Exception
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
      Message message = Message.raw(
          "Protocol error: StartECLSessionMsg required." + msg + " received.");
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
   *
   * @param providedCookie
   *          The provided cookie value.
   * @throws DirectoryException
   *           When an error is raised.
   */
  private void initializeCLSearchFromCookie(String providedCookie)
      throws DirectoryException
  {
    this.draftCompat = false;

    initializeChangelogDomainCtxts(providedCookie, false);
  }

  /**
   * Initialize the handler from a provided start change number.
   *
   * @param startChangeNumber
   *          The provided start change number.
   * @throws DirectoryException
   *           When an error is raised.
   */
  private void initializeCLSearchFromChangeNumber(long startChangeNumber)
      throws DirectoryException
  {
    try
    {
      this.draftCompat = true;

      final String providedCookie = findCookie(startChangeNumber);
      initializeChangelogDomainCtxts(providedCookie, true);
    }
    catch(DirectoryException de)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, de);
      releaseCursor();
      throw de;
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      releaseCursor();
      throw new DirectoryException(
          ResultCode.OPERATIONS_ERROR,
          Message.raw(Category.SYNC,
              Severity.FATAL_ERROR,e.getLocalizedMessage()));
    }
  }

  /**
   * Finds in the {@link ChangeNumberIndexDB} the cookie corresponding to the
   * passed in startChangeNumber.
   *
   * @param startChangeNumber
   *          the start change number coming from the request filter.
   * @return the cookie corresponding to the passed in startChangeNumber.
   * @throws ChangelogException
   *           if a database problem occurred
   * @throws DirectoryException
   *           if a database problem occurred
   */
  private String findCookie(final long startChangeNumber)
      throws ChangelogException, DirectoryException
  {
    final ChangeNumberIndexDB cnIndexDB =
        replicationServer.getChangeNumberIndexDB();

    if (startChangeNumber <= 1)
    {
      // Request filter DOES NOT contain any start change number
      // So we'll generate from the oldest change number in the CNIndexDB
      final ChangeNumberIndexRecord oldestRecord = cnIndexDB.getOldestRecord();
      if (oldestRecord == null)
      { // DB is empty or closed
        isEndOfCNIndexDBReached = true;
        return null;
      }

      cnIndexDBCursor =
          getCursorFrom(cnIndexDB, oldestRecord.getChangeNumber());
      return oldestRecord.getPreviousCookie();
    }

    // Request filter DOES contain a startChangeNumber

    // Read the CNIndexDB to see whether it contains startChangeNumber
    DBCursor<ChangeNumberIndexRecord> cursor =
        cnIndexDB.getCursorFrom(startChangeNumber);
    final ChangeNumberIndexRecord startRecord = cursor.getRecord();
    if (startRecord != null)
    {
      // found the provided startChangeNumber, let's return it
      cnIndexDBCursor = cursor;
      return startRecord.getPreviousCookie();
    }
    close(cursor);

    // startChangeNumber provided in the request IS NOT in the CNIndexDB

    /*
     * Get the changeNumberLimits (from the eligibleCSN obtained at the start of
     * this method) in order to have the oldest and newest change numbers.
     */
    final long[] limits = replicationServer.getECLChangeNumberLimits();
    final long oldestChangeNumber = limits[0];
    final long newestChangeNumber = limits[1];

    // If the startChangeNumber provided is lower than the oldestChangeNumber in
    // the DB, let's use the lower limit.
    if (startChangeNumber < oldestChangeNumber)
    {
      cursor = cnIndexDB.getCursorFrom(oldestChangeNumber);
      final ChangeNumberIndexRecord oldestRecord = cursor.getRecord();
      if (oldestRecord == null)
      {
        // This should not happen
        close(cursor);
        isEndOfCNIndexDBReached = true;
        return null;
      }

      cnIndexDBCursor = cursor;
      return oldestRecord.getPreviousCookie();
    }
    else if (startChangeNumber <= newestChangeNumber)
    {
      // startChangeNumber is between oldest and potential newest and has never
      // been returned yet
      final ChangeNumberIndexRecord newestRecord = cnIndexDB.getNewestRecord();
      if (newestRecord == null)
      {
        isEndOfCNIndexDBReached = true;
        return null;
      }

      cnIndexDBCursor =
          getCursorFrom(cnIndexDB, newestRecord.getChangeNumber());
      return newestRecord.getPreviousCookie();

      // TODO:ECL ... ok we'll start from the end of the CNIndexDB BUT ...
      // this may be very long. Work on perf improvement here.
    }

    // startChangeNumber is greater than the potential newest change number
    throw new DirectoryException(ResultCode.SUCCESS, Message.raw(""));
  }

  private DBCursor<ChangeNumberIndexRecord> getCursorFrom(
      ChangeNumberIndexDB cnIndexDB, long startChangeNumber)
      throws ChangelogException
  {
    DBCursor<ChangeNumberIndexRecord> cursor =
        cnIndexDB.getCursorFrom(startChangeNumber);
    if (cursor.getRecord() == null)
    {
      close(cursor);
      throw new ChangelogException(Message.raw("Change Number "
          + startChangeNumber + " is not available in the Changelog"));
    }
    return cursor;
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
    try
    {
      domainCtxts = buildDomainContexts(providedCookie, allowUnknownDomains);
      startCookie = providedCookie;

      // Initializes each and every domain with the next(first) eligible message
      // from the domain.
      for (DomainContext domainCtxt : domainCtxts) {
        domainCtxt.computeNextAvailableMessage();

        if (domainCtxt.nextMsg == null)
        {
          domainCtxt.active = false;
        }
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
    {
      TRACER.debugInfo("initializeChangelogDomainCtxts() ends with "
          + dumpState());
    }
  }

  private Set<DomainContext> buildDomainContexts(String providedCookie,
      boolean allowUnknownDomains) throws DirectoryException
  {
    final Set<DomainContext> results = new HashSet<DomainContext>();
    final ReplicationServer rs = this.replicationServer;

    /*
    This map is initialized from the providedCookie.
    Below, it will be traversed and each domain configured with ECL will be
    checked and removed from the map.
    At the end, normally the map should be empty.
    Depending on allowUnknownDomains provided flag, a non empty map will
    be considered as an error when allowUnknownDomains is false.
    */
    final Map<DN, ServerState> startStatesFromProvidedCookie =
        MultiDomainServerState.splitGenStateToServerStates(providedCookie);

    final StringBuilder missingDomains = new StringBuilder();
    for (ReplicationServerDomain domain : toIterable(rs.getDomainIterator()))
    {
      // skip the 'unreal' changelog domain
      if (domain == this.replicationServerDomain)
      {
        continue;
      }

      // skip the excluded domains
      Set<String> excludedBaseDNs = startECLSessionMsg.getExcludedBaseDNs();
      if (excludedBaseDNs.contains(domain.getBaseDN().toNormalizedString()))
      {
        // this is an excluded domain
        if (allowUnknownDomains)
        {
          startStatesFromProvidedCookie.remove(domain.getBaseDN());
        }
        continue;
      }

      // skip unused domains
      final ServerState latestState = domain.getLatestServerState();
      if (latestState.isEmpty())
      {
        continue;
      }

      // Creates the new domain context
      final DomainContext newDomainCtxt;
      final ServerState domainStartState =
          startStatesFromProvidedCookie.remove(domain.getBaseDN());
      if (startECLSessionMsg.getPersistent() == PERSISTENT_CHANGES_ONLY)
      {
        newDomainCtxt = newDomainContext(domain, null, latestState);
      }
      else
      {
        // let's take the start state for this domain from the provided cookie
        ServerState startState = domainStartState;
        if (providedCookie == null || providedCookie.length() == 0
            || allowUnknownDomains)
        {
          // when there is no cookie provided in the request,
          // let's start traversing this domain from the beginning of
          // what we have in the replication changelog
          if (startState == null)
          {
            startState =
                domain.getOldestState().duplicateOnlyOlderThan(
                    domain.getLatestDomainTrimDate());
          }
        }
        else
        {
          // when there is a cookie provided in the request,
          if (startState == null)
          {
            missingDomains.append(domain.getBaseDN()).append(":;");
            continue;
          }
          else if (!startState.isEmpty()
              && hasCookieBeenTrimmedFromDB(domain, startState))
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                ERR_RESYNC_REQUIRED_TOO_OLD_DOMAIN_IN_PROVIDED_COOKIE.get(
                    domain.getBaseDN().toNormalizedString()));
          }
        }
        newDomainCtxt = newDomainContext(domain, startState, latestState);
      }

      previousCookie.replace(newDomainCtxt.rsDomain.getBaseDN(),
                             newDomainCtxt.startState.duplicate());

      results.add(newDomainCtxt);
    }

    if (missingDomains.length() > 0)
    {
      // If there are domain missing in the provided cookie,
      // the request is rejected and a full resync is required.
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_RESYNC_REQUIRED_MISSING_DOMAIN_IN_PROVIDED_COOKIE.get(
              missingDomains, "<" + providedCookie + missingDomains + ">"));
    }

    /*
    When it is valid to have the provided cookie containing unknown domains
    (allowUnknownDomains is true), 2 cases must be considered :
    - if the cookie contains a domain that is replicated but where
    ECL is disabled, then this case is considered above
    - if the cookie contains a domain that is even not replicated
    then this case need to be considered here in another loop.
    */
    if (!startStatesFromProvidedCookie.isEmpty() && allowUnknownDomains)
    {
      final Set<DN> providedDomains = startStatesFromProvidedCookie.keySet();
      for (Iterator<DN> iter = providedDomains.iterator(); iter.hasNext();)
      {
        DN providedDomain = iter.next();
        if (rs.getReplicationServerDomain(providedDomain) == null)
        {
          // the domain provided in the cookie is not replicated
          iter.remove();
        }
      }
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
      final StringBuilder sb = new StringBuilder();
      for (DomainContext domainCtxt : results) {
        sb.append(domainCtxt.rsDomain.getBaseDN()).append(":")
          .append(domainCtxt.startState).append(";");
      }
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_RESYNC_REQUIRED_UNKNOWN_DOMAIN_IN_PROVIDED_COOKIE.get(
              startStatesFromProvidedCookie.toString(), sb.toString()));
    }

    return results;
  }

  private DomainContext newDomainContext(ReplicationServerDomain domain,
      ServerState startState, ServerState stopState) throws DirectoryException
  {
    // Create an unconnected MessageHandler for the domain
    MessageHandler mh = new MessageHandler(maxQueueSize, replicationServer);
    mh.setInitialServerState(startState);
    mh.setBaseDNAndDomain(domain.getBaseDN(), false);
    // register the unconnected into the domain
    domain.registerHandler(mh);

    return new DomainContext(domain, startState, stopState, mh);
  }

  private boolean hasCookieBeenTrimmedFromDB(ReplicationServerDomain rsDomain,
      ServerState cookie)
  {
    /*
    when the provided startState is older than the replication changelogdb
    oldestState, it means that the replication changelog db has been trimmed and
    the cookie is not valid anymore.
    */
    for (CSN dbOldestChange : rsDomain.getOldestState())
    {
      CSN providedChange = cookie.getCSN(dbOldestChange.getServerId());
      if (providedChange != null && providedChange.isOlderThan(dbOldestChange))
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
    replicationServerDomain.registerHandler(this);
  }

  /**
   * Shutdown this handler.
   */
  @Override
  public void shutdown()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo(this + " shutdown()");
    }
    releaseCursor();
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

  private void releaseCursor()
  {
    if (this.cnIndexDBCursor != null)
    {
      this.cnIndexDBCursor.close();
      this.cnIndexDBCursor = null;
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
      return eclServer + serverId + " " + serverURL + " "
          + getBaseDNString() + " " + getOperationId();
    }
    return eclServer + getClass().getCanonicalName() + " " + getOperationId();
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
    this.startECLSessionMsg = startECLSessionMsg;

    searchPhase   = INIT_PHASE;
    final String cookie = startECLSessionMsg.getCrossDomainServerState();
    try
    {
      previousCookie = new MultiDomainServerState(cookie);
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
          ERR_INVALID_COOKIE_SYNTAX.get(cookie));
    }

    initializeChangelogSearch(startECLSessionMsg);

    if (session != null)
    {
      try
      {
        // Disable timeout for next communications
        // FIXME: why? and where is it reset?
        session.setSoTimeout(0);
      }
      catch(Exception e) { /* do nothing */ }

      // sendWindow MUST be created before starting the writer
      sendWindow = new Semaphore(sendWindowSize);

      reader = new ServerReader(session, this);
      reader.start();

      if (writer == null)
      {
        writer = new ECLServerWriter(session,this,replicationServerDomain);
        writer.start();
      }

      ((ECLServerWriter)writer).resumeWriter();

      // TODO:ECL Potential race condition if writer not yet resumed here
    }

    if (startECLSessionMsg.getPersistent() == PERSISTENT_CHANGES_ONLY)
    {
      closeInitPhase();
    }

    registerIntoDomain();

    if (debugEnabled())
    {
      TRACER.debugInfo(getClass().getCanonicalName() + " " + getOperationId()
          + " initialized: " + " " + dumpState() + domaimCtxtsToString(""));
    }
  }

  private void initializeChangelogSearch(StartECLSessionMsg msg)
      throws DirectoryException
  {
    if (msg.getECLRequestType() == REQUEST_TYPE_FROM_COOKIE)
    {
      initializeCLSearchFromCookie(msg.getCrossDomainServerState());
    }
    else if (msg.getECLRequestType() == REQUEST_TYPE_FROM_CHANGE_NUMBER)
    {
      initializeCLSearchFromChangeNumber(msg.getFirstChangeNumber());
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
  public ECLUpdateMsg takeECLUpdate() throws DirectoryException
  {
    ECLUpdateMsg msg = getNextECLUpdate();

    // TODO:ECL We should refactor so that a SH always have a session
    if (session == null)
    {
      return msg;
    }

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
    try
    {
      ECLUpdateMsg eclMsg = getNextECLUpdate();
      if (eclMsg != null)
      {
        return eclMsg.getUpdateMsg();
      }
    }
    catch(DirectoryException de)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, de);
    }
    return null;
  }

  /**
   * Returns the next update message for the External Changelog (ECL).
   * @return the ECL update message, null when there aren't anymore.
   * @throws DirectoryException when an error occurs.
   */
  public ECLUpdateMsg getNextECLUpdate() throws DirectoryException
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("In cn=changelog " + this +
          " getNextECLUpdate starts: " + dumpState());
    }

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
        final DomainContext oldestContext = findDomainCtxtWithOldestChange();
        if (oldestContext == null)
        { // there is no oldest change to process
          closeInitPhase();

          // signals end of phase 1 to the caller
          return null;
        }

        final ECLUpdateMsg change = newECLUpdateMsg(oldestContext);

        // Default is not to loop, with one exception
        continueLooping = false;
        if (draftCompat)
        {
          continueLooping = !assignChangeNumber(change);
          // if we encounter a change that has been trimmed from the replicaDBs,
          // we will skip it and loop to the next oldest change from the
          // replicaDBs
        }

        // here we have the right oldest change
        // and in the draft case, we have its change number

        // Set and test the domain of the oldestChange see if we reached
        // the end of the phase for this domain
        oldestContext.currentState.update(change.getUpdateMsg().getCSN());

        if (oldestContext.currentState.cover(oldestContext.stopState)
            || isBeyondLastRequestedChangeNumber(change))
        {
          oldestContext.active = false;
        }
        if (oldestContext.active)
        {
          oldestContext.computeNextAvailableMessage();
        }
        oldestChange = change;
      }

      if (searchPhase == PERSISTENT_PHASE)
      {
        if (debugEnabled())
          TRACER.debugInfo(domaimCtxtsToString(
              "In getNextECLUpdate (persistent): "
                  + "looking for the generalized oldest change"));

        for (DomainContext domainCtxt : domainCtxts)
        {
          domainCtxt.computeNextAvailableMessage();
        }

        final DomainContext oldestContext = findDomainCtxtWithOldestChange();
        if (oldestContext != null)
        {
          oldestChange = newECLUpdateMsg(oldestContext);
          oldestContext.currentState.update(
              oldestChange.getUpdateMsg().getCSN());
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
      final CSN csn = oldestChange.getUpdateMsg().getCSN();
      if (debugEnabled())
      {
        TRACER.debugInfo("getNextECLUpdate updates previousCookie:" + csn);
      }

      previousCookie.update(oldestChange.getBaseDN(), csn);
      oldestChange.setCookie(previousCookie);

      if (debugEnabled())
      {
        TRACER.debugInfo("getNextECLUpdate returns result oldestChange="
            + oldestChange);
      }
    }
    return oldestChange;
  }

  private boolean isBeyondLastRequestedChangeNumber(final ECLUpdateMsg change)
  {
    final long lastChangeNumber = startECLSessionMsg.getLastChangeNumber();
    return draftCompat
        && 0 < lastChangeNumber
        && lastChangeNumber < change.getChangeNumber();
  }

  private ECLUpdateMsg newECLUpdateMsg(DomainContext ctx)
  {
    // cookie will be set later AND changeNumber may be set later
    final ECLUpdateMsg change = new ECLUpdateMsg(
        (LDAPUpdateMsg) ctx.nextMsg, null, ctx.rsDomain.getBaseDN(), 0);
    ctx.nextMsg = null; // clean after use
    return change;
  }

  /**
   * Either retrieves a change number from the DB, or assign a new change number
   * and store in the DB.
   *
   * @param replicaDBChange
   *          the replica DB change to find in the {@link ChangeNumberIndexDB}
   *          where to assign the change number
   * @return <code>true</code> if a change number has been assigned to the
   *         provided replicaDBChange, <code>false</code> otherwise
   * @throws DirectoryException
   *           if any problem occur
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  private boolean assignChangeNumber(final ECLUpdateMsg replicaDBChange)
      throws ChangelogException, DirectoryException
  {
    // We also need to check if the CNIndexDB is consistent with the replicaDBs.
    // If not, 2 potential reasons:
    // a/ replicaDBs have been purged (trim) let's traverse the CNIndexDB
    // b/ changelog is late ... let's traverse the changelogDb
    // The following loop allows to loop until being on the same cn in the 2 dbs

    CSN csnFromReplicaDB = replicaDBChange.getUpdateMsg().getCSN();
    DN baseDNFromReplicaDB = replicaDBChange.getBaseDN();

    while (!isEndOfCNIndexDBReached)
    {
      final ChangeNumberIndexRecord currentRecord = cnIndexDBCursor.getRecord();
      final CSN csnFromCNIndexDB = currentRecord.getCSN();
      final DN baseDNFromCNIndexDB = currentRecord.getBaseDN();

      if (debugEnabled())
      {
        TRACER.debugInfo("assignChangeNumber() comparing the replicaDB's and"
            + " CNIndexDB's baseDNs :" + baseDNFromReplicaDB + "?="
            + baseDNFromCNIndexDB + " timestamps:" + asDate(csnFromReplicaDB)
            + " ?older" + asDate(csnFromCNIndexDB));
      }

      if (areSameChange(csnFromReplicaDB, baseDNFromReplicaDB,
          csnFromCNIndexDB, baseDNFromCNIndexDB))
      {
        // We matched the ReplicaDB change with a record in the CNIndexDB
        // => set the changeNumber in memory and return the change to the client
        if (debugEnabled())
          TRACER.debugInfo("assignChangeNumber() assigning changeNumber="
              + currentRecord.getChangeNumber() + " to change="
              + replicaDBChange);

        previousCookie.update(
            new MultiDomainServerState(currentRecord.getPreviousCookie()));
        replicaDBChange.setCookie(previousCookie);
        replicaDBChange.setChangeNumber(currentRecord.getChangeNumber());
        return true;
      }

      if (!csnFromCNIndexDB.isOlderThan(csnFromReplicaDB))
      {
        // the change from the replicaDB is older
        // it should have been stored lately
        // let's continue to traverse the replicaDBs
        if (debugEnabled())
          TRACER.debugInfo("assignChangeNumber() will skip " + csnFromReplicaDB
              + " and read next change from the regular changelog.");
        return false; // TO BE CHECKED
      }

      // The change from the CNIndexDB is older.
      // It means that the CNIndexDB change has been purged from the replicaDB
      // and CNIndexDB has not been trimmed yet.
      try
      {
        // keep traversing the CNIndexDB searching for the replicaDB change
        if (debugEnabled())
          TRACER.debugInfo("assignChangeNumber() will skip " + csnFromCNIndexDB
              + " and read next change from the CNIndexDB.");

        isEndOfCNIndexDBReached = !cnIndexDBCursor.next();

        if (debugEnabled())
          TRACER.debugInfo("assignChangeNumber() has skipped to changeNumber="
              + currentRecord.getChangeNumber() + " csn="
              + currentRecord.getCSN() + " End of CNIndexDB ?"
              + isEndOfCNIndexDBReached);
      }
      catch (ChangelogException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        // FIXME There is an opportunity for an infinite loop here if the DB
        // continuously throws ChangelogExceptions
      }
    }
    return false;
  }

  private Date asDate(CSN csn)
  {
    return new Date(csn.getTime());
  }

  private boolean areSameChange(CSN csn1, DN baseDN1, CSN csn2, DN baseDN2)
  {
    boolean sameDN = baseDN1.compareTo(baseDN2) == 0;
    boolean sameCSN = csn1.compareTo(csn2) == 0;
    return sameDN && sameCSN;
  }

  /**
   * Terminates the first (non persistent) phase of the search on the ECL.
   */
  private void closeInitPhase()
  {
    // starvation of changelog messages
    // all domain have been unactived means are covered
    if (debugEnabled())
    {
      TRACER.debugInfo("In cn=changelog" + "," + this + " closeInitPhase(): "
          + dumpState());
    }

    // go to persistent phase if one
    for (DomainContext domainCtxt : domainCtxts) domainCtxt.active = true;

    if (startECLSessionMsg.getPersistent() != NON_PERSISTENT)
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

    // End of INIT_PHASE => always release the cursor
    releaseCursor();
  }

  /**
   * Find the domainCtxt of the domain with the oldest change.
   *
   * @return the domainCtxt of the domain with the oldest change, null when
   *         none.
   */
  private DomainContext findDomainCtxtWithOldestChange()
  {
    DomainContext oldestCtxt = null;
    for (DomainContext domainCtxt : domainCtxts)
    {
      if (domainCtxt.active
          // .nextMsg is null when the previous (non blocking) nextMessage did
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
          + " findDomainCtxtWithOldestChange() returns "
          + ((oldestCtxt != null) ? oldestCtxt.nextMsg : "-1"));

    return oldestCtxt;
  }

  /**
   * Returns the client operation id.
   * @return The client operation id.
   */
  public String getOperationId()
  {
    if (startECLSessionMsg != null)
    {
      return startECLSessionMsg.getOperationId();
    }
    return "";
  }

  /**
   * Returns whether the current search is a persistent search .
   *
   * @return true if the current search is a persistent search, false otherwise
   */
  boolean isNonPersistent()
  {
    return startECLSessionMsg.getPersistent() == NON_PERSISTENT;
  }

  /**
   * Returns whether the initialization phase has completed.
   *
   * @return true the initialization phase has completed, false otherwise
   */
  boolean isInitPhaseDone()
  {
    return this.searchPhase != INIT_PHASE;
  }

}
