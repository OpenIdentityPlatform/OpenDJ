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
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.api.DirectoryThread;
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
import org.opends.server.util.TimeThread;

/**
 * This class defines a server handler, which handles all interaction with a
 * peer replication server.
 */
public class ECLServerHandler extends ServerHandler
{

  // Properties filled only if remote server is a RS
  private String serverAddressURL;

  String operationId;

  /**
   * CLDomainContext : contains the state properties for the search
   * currently being processed, by replication domain.
   */
  private class CLDomainContext
  {
    ReplicationServerDomain rsd; // the repl server domain
    boolean active;              // is the domain still active
    MessageHandler mh;           // the message handler associated
    UpdateMsg nextMsg;
    UpdateMsg nonElligiblemsg;
    ServerState startState;
    ServerState currentState;
    ServerState stopState;

    /**
     * Add to the provider buffer a string representation of this object.
     */
    public void toString(StringBuilder buffer, int i)
    {
      CLDomainContext xx = clDomCtxts[i];
      buffer.append(
          " clDomCtxts(" + i + ") [act=" + xx.active +
          " rsd=" + rsd +
          " nextMsg=" + nextMsg + "(" +
          (nextMsg != null?
              new Date(nextMsg.getChangeNumber().getTime()).toString():"")
              + ")" +
          " nextNonEligibleMsg="      + nonElligiblemsg +
          " startState=" + startState +
          " stopState= " + stopState +
          " currState= " + currentState + "]");
    }
  }

  // The list of contexts by domain for the current search
  CLDomainContext[] clDomCtxts = new CLDomainContext[0];

  private void clDomCtxtsToString(String msg)
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append(msg+"\n");
    for (int i=0;i<clDomCtxts.length;i++)
    {
      clDomCtxts[i].toString(buffer, i);
      buffer.append("\n");
    }
    TRACER.debugInfo(
        "In " + this.getName() + " clDomCtxts: " + buffer.toString());
  }

  /**
   * Class that manages the state variables for the current search on the ECL.
   */
  private class CLTraverseCtxt
  {
    /**
     * Specifies the next changer number (seqnum), -1 when not.
     */
    public int nextSeqnum;
    /**
     * Specifies whether the current search has been requested to be persistent
     * or not.
     */
    public short isPersistent;
    /**
     * Specifies the last changer number (seqnum) requested.
     */
    public int stopSeqnum;
    /**
     * Specifies whether the change number (seqnum) db has been read until
     * its end.
     */
    public boolean endOfSeqnumdbReached = false;
    /**
     * Specifies the current search phase.
     * 1 = init
     * 2 = persistent
     */
    public int searchPhase = 1;
    /**
     * Specifies the cookie contained in the request, specifying where
     * to start serving the ECL.
     */
    public String generalizedStartState;
    /**
     * Specifies the current cookie value.
     */
    public MultiDomainServerState currentCookie =
      new MultiDomainServerState();
    /**
     * Specifies the excluded DNs.
     */
    public ArrayList<String> excludedServiceIDs = new ArrayList<String>();

    /**
     * Provides a string representation of this object.
     * @return the string representation.
     */
    public String toString()
    {
      return new String(
        this.getClass().getCanonicalName() +
        ":[" +
        " nextSeqnum=" + nextSeqnum +
        " persistent=" + isPersistent +
        " stopSeqnum" + stopSeqnum +
        " endOfSeqnumdbReached=" + endOfSeqnumdbReached +
        " searchPhase=" + searchPhase +
        " generalizedStartState=" + generalizedStartState +
        "]");
    }

  }

  // The context of the current search
  private CLTraverseCtxt cLSearchCtxt = new CLTraverseCtxt();

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
      protocolVersion = ProtocolVersion.minWithCurrent(
          inECLStartMsg.getVersion());
      generationId = inECLStartMsg.getGenerationId();
      serverURL = inECLStartMsg.getServerURL();
      int separator = serverURL.lastIndexOf(':');
      serverAddressURL =
        session.getRemoteAddress() + ":" + serverURL.substring(separator +
            1);
      setInitialServerState(inECLStartMsg.getServerState());
      setSendWindowSize(inECLStartMsg.getWindowSize());
      if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
      {
        // We support connection from a V1 RS
        // Only V2 protocol has the group id in repl server start message
        this.groupId = inECLStartMsg.getGroupId();
      }
      // FIXME:ECL Any generationID must be removed, it makes no sense here.
      oldGenerationId = -100;
    }
    catch(Exception e)
    {
      Message message = Message.raw(e.getLocalizedMessage());
      throw new DirectoryException(ResultCode.OTHER, message);
    }
    return inECLStartMsg.getSSLEncryption();
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
      ProtocolSession session,
      int queueSize,
      String replicationServerURL,
      short replicationServerId,
      ReplicationServer replicationServer,
      int rcvWindowSize)
  {
    super(session, queueSize, replicationServerURL, replicationServerId,
        replicationServer, rcvWindowSize);
    try
    {
      setServiceIdAndDomain("cn=changelog");
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
      short replicationServerId,
      ReplicationServer replicationServer,
      StartECLSessionMsg startECLSessionMsg)
  throws DirectoryException
  {
    // FIXME:ECL queueSize is hard coded to 1 else Handler hangs for some reason
    super(null, 1, replicationServerURL, replicationServerId,
        replicationServer, 0);
    try
    {
      setServiceIdAndDomain("cn=changelog");
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
      lockDomain(true);

      // send start to remote
      ReplServerStartMsg outReplServerStartMsg = sendStartToRemote((short)-1);

      // log
      logStartHandshakeRCVandSND(inECLStartMsg, outReplServerStartMsg);

      // until here session is encrypted then it depends on the negociation
      // The session initiator decides whether to use SSL.
      if (!sessionInitiatorSSLEncryption)
        session.stopEncryption();

      // wait and process StartSessionMsg from remote RS
      StartECLSessionMsg inStartECLSessionMsg =
        waitAndProcessStartSessionECLFromRemoteServer();

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
    ReplicationMsg msg = null;
    msg = session.receive();

    if (!(msg instanceof StartECLSessionMsg))
    {
      Message message = Message.raw(
          "Protocol error: StartECLSessionMsg required." + msg + " received.");
      abortStart(message);
    }

    // Process StartSessionMsg sent by remote DS
    StartECLSessionMsg startECLSessionMsg = (StartECLSessionMsg) msg;

    return startECLSessionMsg;
  }

  /**
   * Initialize the handler from a provided cookie value.
   * @param providedGeneralizedStartState The provided cookie value.
   * @throws DirectoryException When an error is raised.
   */
  public void initializeCLSearchFromGenState(
      String providedGeneralizedStartState)
  throws DirectoryException
  {
    this.cLSearchCtxt.nextSeqnum = -1; // will not generate seqnum
    initializeCLDomCtxts(providedGeneralizedStartState);
  }

  /**
   * Initialize the context for each domain.
   * @param providedGeneralizedStartState the provided generalized state
   * @throws DirectoryException When an error occurs.
   */
  public void initializeCLDomCtxts(String providedGeneralizedStartState)
  throws DirectoryException
  {
    HashMap<String,ServerState> startStates = new HashMap<String,ServerState>();

    ReplicationServer rs = replicationServerDomain.getReplicationServer();

    try
    {
      // Initialize start state for  all running domains with empty state
      Iterator<ReplicationServerDomain> rsdk = rs.getCacheIterator();
      if (rsdk != null)
      {
        while (rsdk.hasNext())
        {
          // process a domain
          ReplicationServerDomain rsd = rsdk.next();
          // skip the changelog domain
          if (rsd == this.replicationServerDomain)
            continue;
          startStates.put(rsd.getBaseDn(), new ServerState());
        }
      }

      // Overwrite start state from the cookie provided in the request
      if ((providedGeneralizedStartState != null) &&
          (providedGeneralizedStartState.length()>0))
      {
        String[] domains = providedGeneralizedStartState.split(";");
        for (String domainState : domains)
        {
          // Split baseDN and serverState
          String[] fields = domainState.split(":");

          // BaseDN - Check it
          String domainBaseDNReceived = fields[0];
          if (!startStates.containsKey(domainBaseDNReceived))
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                ERR_INVALID_COOKIE_FULL_RESYNC_REQUIRED.get(
                  "unknown " + domainBaseDNReceived));

          // ServerState
          ServerState domainServerState = new ServerState();
          if (fields.length>1)
          {
            String strState = fields[1];
            String[] strCN = strState.split(" ");
            for (String sr : strCN)
            {
              ChangeNumber fromChangeNumber = new ChangeNumber(sr);
              domainServerState.update(fromChangeNumber);
            }
          }
          startStates.put(domainBaseDNReceived, domainServerState);

          // FIXME: ECL first cookie value check
          // ECL For each of the provided state, it this state is older
          // than the older change stored in the replication changelog ....
          // then a purge occured since the time the cookie was published
          // it is recommended to do a full resync
          ReplicationServerDomain rsd =
            rs.getReplicationServerDomain(domainBaseDNReceived, false);
          ServerState domainStartState = rsd.getStartState();
          if (!domainServerState.cover(domainStartState))
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                ERR_INVALID_COOKIE_FULL_RESYNC_REQUIRED.get(
                  "too old cookie provided " + providedGeneralizedStartState
                  + " first acceptable change for " + rsd.getBaseDn()
                  + " is " + rsd.getStartState()));
          }
        }
      }
    }
    catch(DirectoryException de)
    {
      throw de;
    }
    catch(Exception e)
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_INVALID_COOKIE_FULL_RESYNC_REQUIRED.get(
            "Exception raised: " + e.getMessage()));
    }

    try
    {
      // Now traverse all domains and build the initial changelog context
      Iterator<ReplicationServerDomain> rsdi = rs.getCacheIterator();

      // Creates the table that will contain the real-time info by domain.
      clDomCtxts = new CLDomainContext[rs.getCacheSize()-1
                         -this.cLSearchCtxt.excludedServiceIDs.size()];
      int i =0;
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
          boolean excluded = false;
          for(String excludedServiceID : this.cLSearchCtxt.excludedServiceIDs)
          {
            if (excludedServiceID.equalsIgnoreCase(rsd.getBaseDn()))
            {
              excluded=true;
              break;
            }
          }
          if (excluded)
            continue;

          // Creates the context record
          CLDomainContext newContext = new CLDomainContext();
          newContext.active = true;
          newContext.rsd = rsd;

          if (this.cLSearchCtxt.isPersistent ==
            StartECLSessionMsg.PERSISTENT_CHANGES_ONLY)
          {
            newContext.startState = rsd.getCLElligibleState();
          }
          else
          {
            newContext.startState = startStates.get(rsd.getBaseDn());
            newContext.stopState = rsd.getCLElligibleState();
          }
          newContext.currentState = new ServerState();

          // Creates an unconnected SH
          MessageHandler mh = new MessageHandler(maxQueueSize,
              replicationServerURL, replicationServerId, replicationServer);
          // set initial state
          mh.setInitialServerState(newContext.startState);
          // set serviceID and domain
          mh.setServiceIdAndDomain(rsd.getBaseDn());
          // register into domain
          rsd.registerHandler(mh);
          newContext.mh = mh;

          // store the new context
          clDomCtxts[i] = newContext;
          i++;
        }
      }

      // the next record from the seqnumdb should be the one
      cLSearchCtxt.endOfSeqnumdbReached = false;
      cLSearchCtxt.generalizedStartState = providedGeneralizedStartState;

      // Initializes all domain with the next elligible message
      for (int j=0; j<clDomCtxts.length; j++)
      {
        this.getNextElligibleMessage(j);
        if (clDomCtxts[j].nextMsg == null)
          clDomCtxts[j].active = false;
      }
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      throw new DirectoryException(
          ResultCode.OPERATIONS_ERROR,
          Message.raw(Category.SYNC, Severity.INFORMATION,"Exception raised: " +
              e.getLocalizedMessage()),
          e);
    }
  }

  /**
   * Registers this handler into its related domain and notifies the domain.
   */
  private void registerIntoDomain()
  {
    replicationServerDomain.registerHandler(this);
  }

  /**
   * Shutdown this handler ServerHandler.
   */
  public void shutdown()
  {
    for (int i=0;i<clDomCtxts.length;i++)
    {
      if (!clDomCtxts[i].rsd.unRegisterHandler(clDomCtxts[i].mh))
      {
        TRACER.debugInfo(this +" shutdown() Internal error " +
                " when unregistering "+ clDomCtxts[i].mh);
      }
      clDomCtxts[i].rsd.stopServer(clDomCtxts[i].mh);
    }
    super.shutdown();
    clDomCtxts = null;
  }

  /**
   * Request to shutdown the associated writer.
   */
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
    ",cn=" + replicationServerDomain.getMonitorInstanceName();
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

    try
    {
      MonitorData md;
      md = replicationServerDomain.computeMonitorData();
      // FIXME:ECL No monitoring exist for ECL.
    }
    catch (Exception e)
    {
      Message message =
        ERR_ERROR_RETRIEVING_MONITOR_DATA.get(stackTraceToSingleLineString(e));
      // We failed retrieving the monitor data.
      attributes.add(Attributes.create("error", message.toString()));
    }
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
    if (this.cLSearchCtxt==null)
      localString += serverId + " " + serverURL + " " + getServiceId();
    else
      localString += this.getName();
    return localString;
  }
  /**
   * Gets the status of the connected DS.
   * @return The status of the connected DS.
   */
  public ServerStatus getStatus()
  {
    // FIXME:ECL Sould ECLServerHandler manage a ServerStatus ?
    return ServerStatus.INVALID_STATUS;
  }
  /**
   * Retrieves the Address URL for this server handler.
   *
   * @return  The Address URL for this server handler,
   *          in the form of an IP address and port separated by a colon.
   */
  public String getServerAddressURL()
  {
    return serverAddressURL;
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

    //
    this.following = false; // FIXME:ECL makes no sense for ECLServerHandler ?
    this.lateQueue.clear(); // FIXME:ECL makes no sense for ECLServerHandler ?
    this.setConsumerActive(true);
    this.cLSearchCtxt.searchPhase = 1;
    this.operationId = startECLSessionMsg.getOperationId();
    this.setName(this.getClass().getCanonicalName()+ " " + operationId);

    if (eligibleCNComputerThread==null)
      eligibleCNComputerThread = new ECLEligibleCNComputerThread();

    cLSearchCtxt.isPersistent  = startECLSessionMsg.isPersistent();
    cLSearchCtxt.stopSeqnum    = startECLSessionMsg.getLastDraftChangeNumber();
    cLSearchCtxt.searchPhase   = 1;
    cLSearchCtxt.currentCookie = new MultiDomainServerState(
        startECLSessionMsg.getCrossDomainServerState());
    cLSearchCtxt.excludedServiceIDs=startECLSessionMsg.getExcludedServiceIDs();

    //--
    if (startECLSessionMsg.getECLRequestType()==0)
    {
      initializeCLSearchFromGenState(
          startECLSessionMsg.getCrossDomainServerState());
    }

    if (session != null)
    {
      try
      {
        // Disable timeout for next communications
        session.setSoTimeout(0);
      }
      catch(Exception e) {}

      // sendWindow MUST be created before starting the writer
      sendWindow = new Semaphore(sendWindowSize);

      // create reader
      reader = new ServerReader(session, serverId,
          this, replicationServerDomain);
      reader.start();

      if (writer == null)
      {
        // create writer
        writer = new ECLServerWriter(session,this,replicationServerDomain);
        writer.start();
      }

      // Resume the writer
      ((ECLServerWriter)writer).resumeWriter();

      // FIXME:ECL Potential race condition if writer not yet resumed here
    }

    if (cLSearchCtxt.isPersistent == StartECLSessionMsg.PERSISTENT_CHANGES_ONLY)
    {
      closePhase1();
    }

    /* TODO: Good Draft Compat
    //--
    if (startCLMsg.getStartMode()==1)
    {
      initializeCLSearchFromProvidedSeqnum(startCLMsg.getSequenceNumber());
    }

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
      ChangeNumber crossDomainElligibleCN = computeCrossDomainElligibleCN();

      try
      {
        // to get the CL first and last
        // last rely on the crossDomainElligibleCN thhus must have been
        // computed before
        int[] limits = computeCLLimits(crossDomainElligibleCN);
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
    Good Draft Compat */

    // Store into domain
    registerIntoDomain();

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
    ECLUpdateMsg msg = getnextUpdate();

    // FIXME:ECL We should refactor so that a SH always have a session
    if (session == null)
      return msg;

    /*
     * When we remove a message from the queue we need to check if another
     * server is waiting in flow control because this queue was too long.
     * This check might cause a performance penalty an therefore it
     * is not done for every message removed but only every few messages.
     */
    /** FIXME:ECL checkAllSaturation makes no sense for ECLServerHandler ?
    if (++saturationCount > 10)
    {
      saturationCount = 0;
      try
      {
        replicationServerDomain.checkAllSaturation();
      } catch (IOException e)
      {
      }
    }
    */
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
   *
   * @param synchronous - not used - always non blocking.
   * @return the next message - null when none.
   */
  protected UpdateMsg getnextMessage(boolean synchronous)
  {
    UpdateMsg msg = null;
    try
    {
      ECLUpdateMsg eclMsg = getnextUpdate();
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
   * Get the next external changelog update.
   *
   * @return    The ECL update, null when none.
   * @exception DirectoryException when any problem occurs.
   */
  protected ECLUpdateMsg getnextUpdate()
  throws DirectoryException
  {
    return getGeneralizedNextECLUpdate(this.cLSearchCtxt);
  }

  /**
   * Computes the cross domain eligible message (non blocking).
   * Return null when search is covered
   */
  private ECLUpdateMsg getGeneralizedNextECLUpdate(CLTraverseCtxt cLSearchCtxt)
  throws DirectoryException
  {
    ECLUpdateMsg theOldestChange = null;
    try
    {
      TRACER.debugInfo("In " + replicationServerDomain.getReplicationServer().
          getMonitorInstanceName() + "," + this +
          " getGeneralizedNextECLUpdate starts with ctxt="
          + cLSearchCtxt);

      if ((cLSearchCtxt.nextSeqnum != -1) &&
          (!cLSearchCtxt.endOfSeqnumdbReached))
      {
        /* TODO:ECL G Good changelog draft compat.
        // First time , initialise the cursor to traverse the seqnumdb
        if (seqnumDbReadIterator == null)
        {
          try
          {
          seqnumDbReadIterator = replicationServerDomain.getReplicationServer().
            getSeqnumDbHandler().generateIterator(cLSearchCtxt.nextSeqnum);
            TRACER.debugInfo("getGeneralizedNextMessage(): "
                + " creates seqnumDbReadIterator from nextSeqnum="
                + cLSearchCtxt.nextSeqnum
                + " 1rst=" + seqnumDbReadIterator.getSeqnum()
                + " CN=" + seqnumDbReadIterator.getChangeNumber()
                + cLSearchCtxt);
          }
          catch(Exception e)
          {
            cLSearchCtxt.endOfSeqnumdbReached = true;
          }
        }
        */
      }


      // Search / no seqnum / not persistent
      // -----------------------------------
      //  init: all domain are candidate
      //    get one msg from each
      //       no (null) msg returned: should not happen since we go to a state
      //       that is computed/expected
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
      //  step 2: send DoneMsg

      // Persistent:
      // ----------
      //  step 1&2: same as non persistent
      //
      //  step 3: reinit all domain are candidate
      //    take the oldest
      //    if one domain has no msg, still is candidate

      int iDom = 0;
      boolean nextclchange = true;
      while ((nextclchange) && (cLSearchCtxt.searchPhase==1))
      {

        // Step 1 & 2
        if (cLSearchCtxt.searchPhase==1)
        {
          if (debugEnabled())
            clDomCtxtsToString("In getGeneralizedNextMessage : " +
              "looking for the generalized oldest change");

          // Retrieves the index in the subx table of the
          // generalizedOldestChange
          iDom = getGeneralizedOldestChange();

          // idomain != -1 means that we got one
          // generalized oldest change to process
          if (iDom==-1)
          {
            closePhase1();

            // signify end of phase 1 to the caller
            return null;
          }

          // idomain != -1 means that we got one
          // generalized oldest change to process
          String suffix = this.clDomCtxts[iDom].rsd.getBaseDn();
          theOldestChange = new ECLUpdateMsg(
              (LDAPUpdateMsg)clDomCtxts[iDom].nextMsg,
              null, // set later
              suffix);

          nextclchange = false;

          /* TODO:ECL G Good change log draft compat.
          if (cLSearchCtxt.nextSeqnum!=-1)
          {
            // Should either retrieve or generate a seqnum
            // we also need to check if the seqnumdb is acccurate reagrding
            // the changelogdb.
            // if not, 2 potential reasons
            // -1 : purge from the changelog .. let's traverse the seqnumdb
            // -2 : changelog is late .. let's traverse the changelog

            // replogcn : the oldest change from the changelog db
            ChangeNumber replogcn = theOldestChange.getChangeNumber();
            DN replogReplDomDN = clDomCtxts[iDom].rsd.getBaseDn();

            while (true)
            {
              if (!cLSearchCtxt.endOfSeqnumdbReached)
              {
                // we did not reach yet the end of the seqnumdb

                // seqnumcn : the next change from from the seqnum db
                ChangeNumber seqnumcn = seqnumDbReadIterator.getChangeNumber();

                // are replogcn and seqnumcn should be the same change ?
                int cmp = replogcn.compareTo(seqnumcn);
                DN seqnumReplDomDN=DN.decode(seqnumDbReadIterator.
                getDomainDN());

                TRACER.debugInfo("seqnumgen: comparing the 2 db "
                    + " changelogdb:" + replogReplDomDN + "=" + replogcn
                    + " ts=" +
                    new Date(replogcn.getTime()).toString()
                    + "## seqnumdb:" + seqnumReplDomDN + "=" + seqnumcn
                    + " ts=" +
                    new Date(seqnumcn.getTime()).toString()
                    + " sn older=" + seqnumcn.older(replogcn));

                if ((replogReplDomDN.compareTo(seqnumReplDomDN)==0) && (cmp==0))
                {
                  // same domain and same CN => same change

                  // assign the seqnum from the seqnumdb
                  // to the change from the changelogdb

                  TRACER.debugInfo("seqnumgen: assigning seqnum="
                      + seqnumDbReadIterator.getSeqnum()
                      + " to change=" + theOldestChange);
                  theOldestChange.setSeqnum(seqnumDbReadIterator.getSeqnum());

                  // prepare the next seqnum for the potential next change added
                  // to the seqnumDb
                  cLSearchCtxt.nextSeqnum = seqnumDbReadIterator.getSeqnum()
                  + 1;
                  break;
                }
                else
                {
                  // replogcn and seqnumcn are NOT the same change
                  if (seqnumcn.older(replogcn))
                  {
                    // the change from the seqnumDb is older
                    // that means that the change has been purged from the
                    // changelog
                    try
                    {
                      // let's traverse the seqnumdb searching for the change
                      // found in the changelogDb.
                      TRACER.debugInfo("seqnumgen: will skip "
                          + seqnumcn  + " and next from  the seqnum");
                      cLSearchCtxt.endOfSeqnumdbReached =
                        (seqnumDbReadIterator.next()==false);
                      TRACER.debugInfo("seqnumgen: has nexted cr to "
                          + " sn=" + seqnumDbReadIterator.getSeqnum()
                          + " cn=" + seqnumDbReadIterator.getChangeNumber()
                          + " and reached end "
                          + " of seqnumdb:"+cLSearchCtxt.endOfSeqnumdbReached);
                      if (cLSearchCtxt.endOfSeqnumdbReached)
                      {
                        // we are at the end of the seqnumdb in the append mode
                        // store in seqnumdb the pair
                        // seqnum of the cur change,state before this change)
                        replicationServerDomain.addSeqnum(
                            cLSearchCtxt.nextSeqnum,
                            getGenState(),
                      clDomCtxts[iDom].rsd.getBaseDn().toNormalizedString(),
                            theOldestChange.getChangeNumber());
                        theOldestChange.setSeqnum(cLSearchCtxt.nextSeqnum);
                        cLSearchCtxt.nextSeqnum++;
                        break;
                      }
                      else
                      {
                        // next change from seqnumdb
                        cLSearchCtxt.nextSeqnum =
                          seqnumDbReadIterator.getSeqnum() + 1;
                        continue;
                      }
                    }
                    catch(Exception e)
                    {
                    }
                  }
                  else
                  {
                    // the change from the changelogDb is older
                    // it should have been stored lately
                    // let's continue to traverse the changelogdb
                    TRACER.debugInfo("seqnumgen: will skip "
                        + replogcn  + " and next from  the CL");
                    nextclchange = true;
                    break; // TO BE CHECKED
                  }
                }
              }
              else
              {
                // we are at the end of the seqnumdb in the append mode
                // store in seqnumdb the pair
                // (seqnum of the current change, state before this change)
                replicationServerDomain.addSeqnum(
                    cLSearchCtxt.nextSeqnum,
                    getGenState(),
                    clDomCtxts[iDom].rsd.getBaseDn().toNormalizedString(),
                    theOldestChange.getChangeNumber());
                theOldestChange.setSeqnum(cLSearchCtxt.nextSeqnum);
                cLSearchCtxt.nextSeqnum++;
                break;
              }
            } // while seqnum
          } // nextseqnum !- -1
          */

          // here we have the right oldest change and in the seqnum case we
          // have its seqnum

          // Set and test the domain of the oldestChange see if we reached
          // the end of the phase for this domain
          clDomCtxts[iDom].currentState.update(
              theOldestChange.getUpdateMsg().getChangeNumber());

          if (clDomCtxts[iDom].currentState.cover(clDomCtxts[iDom].stopState))
          {
            clDomCtxts[iDom].active = false;
          }

          // Test the seqnum of the oldestChange see if we reached
          // the end of operation
          /* TODO:ECL G Good changelog draft compat. Not yet implemented
          if ((cLSearchCtxt.stopSeqnum>0) &&
              (theOldestChange.getSeqnum()>=cLSearchCtxt.stopSeqnum))
          {
            closePhase1();

            // means end of phase 1 to the calling writer
            return null;
          }
          */

          if (clDomCtxts[iDom].active)
          {
            // populates the table with the next eligible msg from idomain
            // in non blocking mode, return null when no more eligible msg
            getNextElligibleMessage(iDom);
          }
        } // phase ==1
      } // while (nextclchange)

      if (cLSearchCtxt.searchPhase==2)
      {
        clDomCtxtsToString("In getGeneralizedNextMessage (persistent): " +
        "looking for the generalized oldest change");

        for (int ido=0; ido<clDomCtxts.length; ido++)
        {
          // get next msg
          getNextElligibleMessage(ido);
        }

        // take the oldest one
        iDom = getGeneralizedOldestChange();

        if (iDom != -1)
        {
          String suffix = this.clDomCtxts[iDom].rsd.getBaseDn();

          theOldestChange = new ECLUpdateMsg(
              (LDAPUpdateMsg)clDomCtxts[iDom].nextMsg,
              null, // set later
              suffix);

          clDomCtxts[iDom].currentState.update(
              theOldestChange.getUpdateMsg().getChangeNumber());

          /* TODO:ECL G Good changelog draft compat.
          if (cLSearchCtxt.nextSeqnum!=-1)
          {
            // should generate seqnum

            // store in seqnumdb the pair
            // (seqnum of the current change, state before this change)
            replicationServerDomain.addSeqnum(
                cLSearchCtxt.nextSeqnum,
                getGenState(),
                clDomCtxts[iDom].rsd.getBaseDn().toNormalizedString(),
                theOldestChange.getChangeNumber());
            theOldestChange.setSeqnum(cLSearchCtxt.nextSeqnum);
            cLSearchCtxt.nextSeqnum++;
          }
          */
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

    if (theOldestChange != null)
    {
      // Update the current state
      this.cLSearchCtxt.currentCookie.update(
          theOldestChange.getServiceId(),
          theOldestChange.getUpdateMsg().getChangeNumber());

      // Set the current value of global state in the returned message
      theOldestChange.setCookie(this.cLSearchCtxt.currentCookie);
    }
    return theOldestChange;
  }

  /**
   * Terminates the first (non persistent) phase of the search on the ECL.
   */
  private void closePhase1()
  {
    // starvation of changelog messages
    // all domain have been unactived means are covered
    if (debugEnabled())
      TRACER.debugInfo("In " + replicationServerDomain.getReplicationServer().
        getMonitorInstanceName() + "," + this + " closePhase1()"
        + " searchCtxt=" + cLSearchCtxt);

    // go to persistent phase if one
    for (int i=0; i<clDomCtxts.length; i++)
      clDomCtxts[i].active = true;

    if (this.cLSearchCtxt.isPersistent != StartECLSessionMsg.NON_PERSISTENT)
    {
      // phase = 1 done AND persistent search => goto phase 2
      cLSearchCtxt.searchPhase=2;

      if (writer ==null)
      {
        writer = new ECLServerWriter(session,this,replicationServerDomain);
        writer.start();  // start suspended
      }

    }
    else
    {
      // phase = 1 done AND !persistent search => reinit to phase 0
      cLSearchCtxt.searchPhase=0;
    }

    /* TODO:ECL G Good changelog draft compat.
    if (seqnumDbReadIterator!=null)
    {
      // End of phase 1 => always release the seqnum iterator
      seqnumDbReadIterator.releaseCursor();
      seqnumDbReadIterator = null;
    }
     */
  }

  /**
   * Get the oldest change contained in the subx table.
   * The subx table should be populated before
   * @return the oldest change.
   */
  private int getGeneralizedOldestChange()
  {
    int oldest = -1;
    for (int i=0; i<clDomCtxts.length; i++)
    {
      if ((clDomCtxts[i].active))
      {
        // on the first loop, oldest==-1
        // .msg is null when the previous (non blocking) nextMessage did
        // not have any eligible msg to return
        if (clDomCtxts[i].nextMsg != null)
        {
          if ((oldest==-1) ||
              (clDomCtxts[i].nextMsg.compareTo(clDomCtxts[oldest].nextMsg)<0))
          {
            oldest = i;
          }
        }
      }
    }
    if (debugEnabled())
      TRACER.debugInfo("In " + replicationServerDomain.getReplicationServer().
        getMonitorInstanceName()
        + "," + this + " getGeneralizedOldestChange() " +
        " returns " +
        ((oldest!=-1)?clDomCtxts[oldest].nextMsg:""));

    return oldest;
  }

  /**
   * Get from the provided domain, the next message elligible regarding
   * the crossDomain elligible CN. Put it in the context table.
   * @param idomain the provided domain.
   */
  private void getNextElligibleMessage(int idomain)
  {
    ChangeNumber crossDomainElligibleCN = computeCrossDomainElligibleCN();
    try
    {
      if (clDomCtxts[idomain].nonElligiblemsg != null)
      {
        TRACER.debugInfo("getNextElligibleMessage tests if the already " +
            " stored nonElligibleMsg has becoem elligible regarding " +
            " the crossDomainElligibleCN ("+crossDomainElligibleCN +
            " ) " +
            clDomCtxts[idomain].nonElligiblemsg.getChangeNumber().
            older(crossDomainElligibleCN));
        // we already got the oldest msg and it was not elligible
        if (clDomCtxts[idomain].nonElligiblemsg.getChangeNumber().
            older(crossDomainElligibleCN))
        {
          // it is now elligible
          clDomCtxts[idomain].nextMsg = clDomCtxts[idomain].nonElligiblemsg;
          clDomCtxts[idomain].nonElligiblemsg = null;
        }
        else
        {
          // the oldest is still not elligible - let's wait next
        }
      }
      else
      {
        // non blocking
        UpdateMsg newMsg = clDomCtxts[idomain].mh.getnextMessage(false);
        if (debugEnabled())
          TRACER.debugInfo(this +
            " getNextElligibleMessage got the next changelogmsg "
            + " from " + clDomCtxts[idomain].mh.getServiceId()
            + " newCLMsg=" + newMsg);
        clDomCtxts[idomain].nextMsg =
          clDomCtxts[idomain].nonElligiblemsg = null;
        // in non blocking mode, return null when no more msg
        if (newMsg != null)
        {
          /* TODO:ECL Take into account eligibility.
          TRACER.debugInfo("getNextElligibleMessage is "
              + newMsg.getChangeNumber()
              + new Date(newMsg.getChangeNumber().getTime()).toString()
              + " elligible "
              + newMsg.getChangeNumber().older(crossDomainElligibleCN));
          if (newMsg.getChangeNumber().older(crossDomainElligibleCN))
          {
            // is elligible
            clDomCtxts[idomain].nextMsg = newMsg;
          }
          else
          {
            // is not elligible
            clDomCtxts[idomain].nonElligiblemsg = newMsg;
          }
          */
          clDomCtxts[idomain].nextMsg = newMsg;
        }
      }
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
  }

  /*
   */
  ECLEligibleCNComputerThread eligibleCNComputerThread = null;
  ChangeNumber liveecn;
  private ChangeNumber computeCrossDomainElligibleCN()
  {
    return liveecn;
  }


  /**
   * This class specifies the thread that computes periodically
   * the cross domain eligible CN.
   */
  private final class ECLEligibleCNComputerThread
    extends DirectoryThread
  {
    /**
     * The tracer object for the debug logger.
     */
    private boolean shutdown = false;

    private ECLEligibleCNComputerThread()
    {
      super("ECL eligible CN computer thread");
      start();
    }

    public void run()
    {
      while (shutdown == false)
      {
        try {
          synchronized (this)
          {
            liveecn = computeNewCrossDomainElligibleCN();
            try
            {
              this.wait(1000);
            } catch (InterruptedException e)
            { }
          }
        } catch (Exception end)
        {
          break;
        }
      }
    }

    private ChangeNumber computeNewCrossDomainElligibleCN()
    {
      ChangeNumber computedCrossDomainElligibleCN = null;
      String s = "=> ";

      ReplicationServer rs = replicationServerDomain.getReplicationServer();

      if (debugEnabled())
        TRACER.debugInfo("ECLSH.computeNewCrossDomainElligibleCN() "
          + " periodic starts rs="+rs);

      Iterator<ReplicationServerDomain> rsdi = rs.getCacheIterator();
      if (rsdi != null)
      {
        while (rsdi.hasNext())
        {
          ReplicationServerDomain domain = rsdi.next();
          if (domain.getBaseDn().equalsIgnoreCase("cn=changelog"))
            continue;

          ChangeNumber domainElligibleCN = computeEligibleCN(domain);
          if (domainElligibleCN==null)
            continue;
          if ((computedCrossDomainElligibleCN == null) ||
              (domainElligibleCN.older(computedCrossDomainElligibleCN)))
          {
            computedCrossDomainElligibleCN = domainElligibleCN;
          }
          s += "\n DN:" + domain.getBaseDn()
          + "\t\t domainElligibleCN :" + domainElligibleCN
          + "/" +
          new Date(domainElligibleCN.getTime()).toString();
        }
      }

      if (debugEnabled())
        TRACER.debugInfo("SH.computeNewCrossDomainElligibleCN() periodic " +
          " ends with " +
          " the following domainElligibleCN for each domain :" + s +
          "\n thus CrossDomainElligibleCN=" + computedCrossDomainElligibleCN +
          "  ts=" +
          new Date(computedCrossDomainElligibleCN.getTime()).toString());

      return computedCrossDomainElligibleCN;
    }
  }

  /**
   * Compute the eligible CN.
   * @param rsd The provided replication server domain for which we want
   * to retrieve the eligible date.
   * @return null if the domain does not play in eligibility.
   */
  public ChangeNumber computeEligibleCN(ReplicationServerDomain rsd)
  {
    ChangeNumber elligibleCN = null;
    ServerState heartbeatState = rsd.getHeartbeatState();
    if (heartbeatState==null)
      return null;

    // compute elligible CN
    ServerState hbState = heartbeatState.duplicate();

    Iterator<Short> it = hbState.iterator();
    while (it.hasNext())
    {
      short sid = it.next();
      ChangeNumber storedCN = hbState.getMaxChangeNumber(sid);

      // If the most recent UpdateMsg or CLHeartbeatMsg received is very old
      // then the server is considered down and not considered for eligibility
      if (TimeThread.getTime()-storedCN.getTime()>2000)
      {
        if (debugEnabled())
          TRACER.debugInfo(
            "For RSD." + rsd.getBaseDn() + " Server " + sid
            + " is not considered for eligibility ... potentially down");
        continue;
      }

      if ((elligibleCN == null) || (storedCN.older(elligibleCN)))
      {
        elligibleCN = storedCN;
      }
    }

    if (debugEnabled())
      TRACER.debugInfo(
        "For RSD." + rsd.getBaseDn() + " ElligibleCN()=" + elligibleCN);
    return elligibleCN;
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
    return this.cLSearchCtxt.isPersistent;
  }

  /**
   * Getter for the current search phase (INIT or PERSISTENT).
   * @return Whether the current search is persistent or not.
   */
  public int getSearchPhase() {
    return this.cLSearchCtxt.searchPhase;
  }

}
