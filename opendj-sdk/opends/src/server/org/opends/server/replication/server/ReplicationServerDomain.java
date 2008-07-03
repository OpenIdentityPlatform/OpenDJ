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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.loggers.debug.DebugLogger.*;

import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.Iterator;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.AckMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.RoutableMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.replication.protocol.ReplServerInfoMessage;
import org.opends.server.replication.protocol.MonitorMessage;
import org.opends.server.replication.protocol.MonitorRequestMessage;
import org.opends.server.replication.protocol.ResetGenerationId;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;
import com.sleepycat.je.DatabaseException;

/**
 * This class define an in-memory cache that will be used to store
 * the messages that have been received from an LDAP server or
 * from another replication server and that should be forwarded to
 * other servers.
 *
 * The size of the cache is set by configuration.
 * If the cache becomes bigger than the configured size, the older messages
 * are removed and should they be needed again must be read from the backing
 * file
 *
 *
 * it runs a thread that is responsible for saving the messages
 * received to the disk and for trimming them
 * Decision to trim can be based on disk space or age of the message
 */
public class ReplicationServerDomain
{
  private final Object flowControlLock = new Object();
  private final DN baseDn;

  /*
   * The following map contains one balanced tree for each replica ID
   * to which we are currently publishing
   * the first update in the balanced tree is the next change that we
   * must push to this particular server
   *
   * We add new TreeSet in the HashMap when a new server register
   * to this replication server.
   *
   */
  private final Map<Short, ServerHandler> connectedServers =
    new ConcurrentHashMap<Short, ServerHandler>();

  /*
   * This map contains one ServerHandler for each replication servers
   * with which we are connected (so normally all the replication servers)
   * the first update in the balanced tree is the next change that we
   * must push to this particular server
   *
   * We add new TreeSet in the HashMap when a new replication server register
   * to this replication server.
   */

  private final Map<Short, ServerHandler> replicationServers =
    new ConcurrentHashMap<Short, ServerHandler>();

  /*
   * This map contains the List of updates received from each
   * LDAP server
   */
  private final Map<Short, DbHandler> sourceDbHandlers =
    new ConcurrentHashMap<Short, DbHandler>();
  private ReplicationServer replicationServer;

  /* GenerationId management */
  private long generationId = -1;
  private boolean generationIdSavedStatus = false;

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /* Monitor data management */

  // TODO: Remote monitor data cache lifetime is 500ms/should be configurable
  private long monitorDataLifeTime = 500;

  /* Search op on monitor data is processed by a worker thread.
   * Requests are sent to the other RS,and responses are received by the
   * listener threads.
   * The worker thread is awoke on this semaphore, or on timeout.
   */
  Semaphore remoteMonitorResponsesSemaphore;

  /**
   * The monitor data consolidated over the topology.
   */
  private  MonitorData monitorData = new MonitorData();
  private  MonitorData wrkMonitorData;

  /**
   * Creates a new ReplicationServerDomain associated to the DN baseDn.
   *
   * @param baseDn The baseDn associated to the ReplicationServerDomain.
   * @param replicationServer the ReplicationServer that created this
   *                          replicationServer cache.
   */
  public ReplicationServerDomain(DN baseDn, ReplicationServer replicationServer)
  {
    this.baseDn = baseDn;
    this.replicationServer = replicationServer;
  }

  /**
   * Add an update that has been received to the list of
   * updates that must be forwarded to all other servers.
   *
   * @param update  The update that has been received.
   * @param sourceHandler The ServerHandler for the server from which the
   *        update was received
   * @throws IOException When an IO exception happens during the update
   *         processing.
   */
  public void put(UpdateMessage update, ServerHandler sourceHandler)
              throws IOException
  {
    /*
     * TODO : In case that the source server is a LDAP server this method
     * should check that change did get pushed to at least one
     * other replication server before pushing it to the LDAP servers
     */

    short id  = update.getChangeNumber().getServerId();
    sourceHandler.updateServerState(update);
    sourceHandler.incrementInCount();

    if (update.isAssured())
    {
      int count = this.NumServers();
      if (count > 1)
      {
        if (sourceHandler.isReplicationServer())
          ServerHandler.addWaitingAck(update, sourceHandler.getServerId(),
                                      this, count - 1);
        else
          sourceHandler.addWaitingAck(update, count - 1);
      }
      else
      {
        sourceHandler.sendAck(update.getChangeNumber());
      }
    }

    if (generationId < 0)
    {
      generationId = sourceHandler.getGenerationId();
    }

    // look for the dbHandler that is responsible for the LDAP server which
    // generated the change.
    DbHandler dbHandler = null;
    synchronized (sourceDbHandlers)
    {
      dbHandler   = sourceDbHandlers.get(id);
      if (dbHandler == null)
      {
        try
        {
          dbHandler = replicationServer.newDbHandler(id, baseDn);
          generationIdSavedStatus = true;
        }
        catch (DatabaseException e)
        {
          /*
           * Because of database problem we can't save any more changes
           * from at least one LDAP server.
           * This replicationServer therefore can't do it's job properly anymore
           * and needs to close all its connections and shutdown itself.
           */
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_CHANGELOG_SHUTDOWN_DATABASE_ERROR.get());
          mb.append(stackTraceToSingleLineString(e));
          logError(mb.toMessage());
          replicationServer.shutdown();
          return;
        }
        sourceDbHandlers.put(id, dbHandler);
      }
    }

    // Publish the messages to the source handler
    dbHandler.add(update);


    /*
     * Push the message to the replication servers
     */
    if (!sourceHandler.isReplicationServer())
    {
      for (ServerHandler handler : replicationServers.values())
      {
        handler.add(update, sourceHandler);
      }
    }

    /*
     * Push the message to the LDAP servers
     */
    for (ServerHandler handler : connectedServers.values())
    {
      // don't forward the change to the server that just sent it
      if (handler == sourceHandler)
      {
        continue;
      }

      handler.add(update, sourceHandler);
    }

  }

  /**
   * Wait a short while for ServerId disconnection.
   *
   * @param serverId the serverId to be checked.
   */
  public void waitDisconnection(short serverId)
  {
    if (connectedServers.containsKey(serverId))
    {
      // try again
      try
      {
        Thread.sleep(100);
      } catch (InterruptedException e)
      {
      }
    }
  }

  /**
   * Create initialize context necessary for finding the changes
   * that must be sent to a given LDAP or replication server.
   *
   * @param handler handler for the server that must be started
   * @throws Exception when method has failed
   * @return A boolean indicating if the start was successfull.
   */
  public boolean startServer(ServerHandler handler) throws Exception
  {
    /*
     * create the balanced tree that will be used to forward changes
     */
    synchronized (connectedServers)
    {
      ServerHandler oldHandler = connectedServers.get(handler.getServerId());

      if (connectedServers.containsKey(handler.getServerId()))
      {
        // looks like two LDAP servers have the same serverId
        // log an error message and drop this connection.
        Message message = ERR_DUPLICATE_SERVER_ID.get(
            oldHandler.toString(), handler.toString(), handler.getServerId());
        logError(message);
        return false;
      }
      connectedServers.put(handler.getServerId(), handler);

      // It can be that the server that connects here is the
      // first server connected for a domain.
      // In that case, we will establish the appriopriate connections
      // to the other repl servers for this domain and receive
      // their ReplServerInfo messages.
      // FIXME: Is it necessary to end this above processing BEFORE listening
      //        to incoming messages for that domain ? But the replica
      //        would raise Read Timeout for replica that connects.

      // Update the remote replication servers with our list
      // of connected LDAP servers
      sendReplServerInfo();

      return true;
    }
  }

  /**
   * Stop operations with a list of servers.
   *
   * @param replServers the replication servers for which
   * we want to stop operations
   */
  public void stopServers(Collection<String> replServers)
  {
    for (ServerHandler handler : replicationServers.values())
    {
      if (replServers.contains(handler.getServerAddressURL()))
        stopServer(handler);
    }
  }

  /**
   * Stop operations with a given server.
   *
   * @param handler the server for which we want to stop operations
   */
  public void stopServer(ServerHandler handler)
  {
    if (debugEnabled())
      TRACER.debugInfo(
        "In RS " + this.replicationServer.getMonitorInstanceName() +
        " for " + baseDn + " " +
        " stopServer " + handler.getMonitorInstanceName());


      if (handler.isReplicationServer())
      {
        if (replicationServers.containsValue(handler))
        {
          replicationServers.remove(handler.getServerId());
          handler.stopHandler();

          // Update the remote replication servers with our list
          // of connected LDAP servers
          sendReplServerInfo();
        }
      }
      else
      {
        if (connectedServers.containsValue(handler))
        {
          connectedServers.remove(handler.getServerId());
          handler.stopHandler();

          // Update the remote replication servers with our list
          // of connected LDAP servers
          sendReplServerInfo();
        }
      }
  }

  /**
   * Resets the generationId for this domain if there is no LDAP
   * server currently connected and if the generationId has never
   * been saved.
   */
  protected void mayResetGenerationId()
  {
    if (debugEnabled())
      TRACER.debugInfo(
        "In RS " + this.replicationServer.getMonitorInstanceName() +
        " for " + baseDn + " " +
        " mayResetGenerationId generationIdSavedStatus=" +
        generationIdSavedStatus);

    // If there is no more any LDAP server connected to this domain in the
    // topology and the generationId has never been saved, then we can reset
    // it and the next LDAP server to connect will become the new reference.
    boolean lDAPServersConnectedInTheTopology = false;
    if (connectedServers.isEmpty())
    {
      for (ServerHandler rsh : replicationServers.values())
      {
        if (generationId != rsh.getGenerationId())
        {
          if (debugEnabled())
            TRACER.debugInfo(
                "In RS " + this.replicationServer.getMonitorInstanceName() +
                " for " + baseDn + " " +
                " mayResetGenerationId skip RS" + rsh.getMonitorInstanceName() +
                " thas different genId");
        }
        else
        {
          if (rsh.hasRemoteLDAPServers())
          {
            lDAPServersConnectedInTheTopology = true;

            if (debugEnabled())
              TRACER.debugInfo(
                  "In RS " + this.replicationServer.getMonitorInstanceName() +
                  " for " + baseDn + " " +
                  " mayResetGenerationId RS" + rsh.getMonitorInstanceName() +
              " has servers connected to it - will not reset generationId");
          }
        }
      }
    }
    else
    {
      lDAPServersConnectedInTheTopology = true;
      if (debugEnabled())
        TRACER.debugInfo(
          "In RS " + this.replicationServer.getMonitorInstanceName() +
          " for " + baseDn + " " +
          " has servers connected to it - will not reset generationId");
    }

    if ((!lDAPServersConnectedInTheTopology) && (!this.generationIdSavedStatus)
        && (generationId != -1))
    {
      setGenerationId(-1, false);
    }
  }

  /**
   * Create initialize context necessary for finding the changes
   * that must be sent to a given replication server.
   *
   * @param handler the server ID to which we want to forward changes
   * @throws Exception in case of errors
   * @return A boolean indicating if the start was successfull.
   */
  public boolean startReplicationServer(ServerHandler handler) throws Exception
  {
    /*
     * create the balanced tree that will be used to forward changes
     */
    synchronized (replicationServers)
    {
      ServerHandler oldHandler = replicationServers.get(handler.getServerId());
      if ((oldHandler != null))
      {
        if (oldHandler.getServerAddressURL().equals(
            handler.getServerAddressURL()))
        {
          // this is the same server, this means that our ServerStart messages
          // have been sent at about the same time and 2 connections
          // have been established.
          // Silently drop this connection.
        }
        else
        {
          // looks like two replication servers have the same serverId
          // log an error message and drop this connection.
          Message message = ERR_DUPLICATE_REPLICATION_SERVER_ID.
              get(oldHandler.getServerAddressURL(),
                  handler.getServerAddressURL(), handler.getServerId());
          logError(message);
        }
        return false;
      }
      replicationServers.put(handler.getServerId(), handler);

      // Update this server with the list of LDAP servers
      // already connected
      handler.sendInfo(
          new ReplServerInfoMessage(getConnectedLDAPservers(),generationId));

      return true;
    }
  }

/**
 * Get the next update that need to be sent to a given LDAP server.
 * This call is blocking when no update is available or when dependencies
 * do not allow to send the next available change
 *
 * @param  handler  The server handler for the target directory server.
 *
 * @return the update that must be forwarded
 */
  public UpdateMessage take(ServerHandler handler)
  {
    UpdateMessage msg;
    /*
     * Get the balanced tree that we use to sort the changes to be
     * sent to the replica from the cookie
     *
     * The next change to send is always the first one in the tree
     * So this methods simply need to check that dependencies are OK
     * and update this replicaId RUV
     *
     *  TODO : dependency  :
     *  before forwarding change, we should check that the dependency
     *  that is indicated in this change is OK (change already in the RUV)
     */
    msg = handler.take();
    synchronized (flowControlLock)
    {
      if (handler.restartAfterSaturation(null))
        flowControlLock.notifyAll();
    }
    return msg;
  }

  /**
   * Return a Set of String containing the lists of Replication servers
   * connected to this server.
   * @return the set of connected servers
   */
  public Set<String> getChangelogs()
  {
    LinkedHashSet<String> mySet = new LinkedHashSet<String>();

    for (ServerHandler handler : replicationServers.values())
    {
      mySet.add(handler.getServerAddressURL());
    }

    return mySet;
  }


  /**
   * Return a Set containing the servers known by this replicationServer.
   * @return a set containing the servers known by this replicationServer.
   */
  public Set<Short> getServers()
  {
    return sourceDbHandlers.keySet();
  }

  /**
   * Returns as a set of String the list of LDAP servers connected to us.
   * Each string is the serverID of a connected LDAP server.
   *
   * @return The set of connected LDAP servers
   */
  public List<String> getConnectedLDAPservers()
  {
    List<String> mySet = new ArrayList<String>(0);

    for (ServerHandler handler : connectedServers.values())
    {
      mySet.add(String.valueOf(handler.getServerId()));
    }
    return mySet;
  }

  /**
   * Creates and returns an iterator.
   * When the iterator is not used anymore, the caller MUST call the
   * ReplicationIterator.releaseCursor() method to free the ressources
   * and locks used by the ReplicationIterator.
   *
   * @param serverId Identifier of the server for which the iterator is created.
   * @param changeNumber Starting point for the iterator.
   * @return the created ReplicationIterator. Null when no DB is available
   * for the provided server Id.
   */
  public ReplicationIterator getChangelogIterator(short serverId,
                    ChangeNumber changeNumber)
  {
    DbHandler handler = sourceDbHandlers.get(serverId);
    if (handler == null)
      return null;

    try
    {
      return handler.generateIterator(changeNumber);
    }
    catch (Exception e)
    {
     return null;
    }
  }

  /**
   * Returns the change count for that ReplicationServerDomain.
   *
   * @return the change count.
   */
  public long getChangesCount()
  {
    long entryCount = 0;
    for (DbHandler dbHandler : sourceDbHandlers.values())
    {
      entryCount += dbHandler.getChangesCount();
    }
    return entryCount;
  }

  /**
   * Get the baseDn.
   * @return Returns the baseDn.
   */
  public DN getBaseDn()
  {
    return baseDn;
  }

  /**
   * Sets the provided DbHandler associated to the provided serverId.
   *
   * @param serverId  the serverId for the server to which is
   *                  associated the Dbhandler.
   * @param dbHandler the dbHandler associated to the serverId.
   *
   * @throws DatabaseException If a database error happened.
   */
  public void setDbHandler(short serverId, DbHandler dbHandler)
  throws DatabaseException
  {
    synchronized (sourceDbHandlers)
    {
      sourceDbHandlers.put(serverId , dbHandler);
    }
  }

  /**
   * Get the number of currently connected servers.
   *
   * @return the number of currently connected servers.
   */
  private int NumServers()
  {
    return replicationServers.size() + connectedServers.size();
  }


  /**
   * Add an ack to the list of ack received for a given change.
   *
   * @param message The ack message received.
   * @param fromServerId The identifier of the server that sent the ack.
   */
  public void ack(AckMessage message, short fromServerId)
  {
    /*
     * there are 2 possible cases here :
     *  - the message that was acked comes from a server to which
     *    we are directly connected.
     *    In this case, we can find the handler from the connectedServers map
     *  - the message that was acked comes from a server to which we are not
     *    connected.
     *    In this case we need to find the replication server that forwarded
     *    the change and send back the ack to this server.
     */
    ServerHandler handler = connectedServers.get(
                                       message.getChangeNumber().getServerId());
    if (handler != null)
      handler.ack(message, fromServerId);
    else
    {
      ServerHandler.ackChangelog(message, fromServerId);
    }
  }

  /**
   * Retrieves the destination handlers for a routable message.
   *
   * @param msg The message to route.
   * @param senderHandler The handler of the server that published this message.
   * @return The list of destination handlers.
   */
  protected List<ServerHandler> getDestinationServers(RoutableMessage msg,
      ServerHandler senderHandler)
  {
    List<ServerHandler> servers =
      new ArrayList<ServerHandler>();

    if (msg.getDestination() == RoutableMessage.THE_CLOSEST_SERVER)
    {
      // TODO Import from the "closest server" to be implemented
    }
    else if (msg.getDestination() == RoutableMessage.ALL_SERVERS)
    {
      if (!senderHandler.isReplicationServer())
      {
        // Send to all replication servers with a least one remote
        // server connected
        for (ServerHandler rsh : replicationServers.values())
        {
          if (rsh.hasRemoteLDAPServers())
          {
            servers.add(rsh);
          }
        }
      }

      // Sends to all connected LDAP servers
      for (ServerHandler destinationHandler : connectedServers.values())
      {
        // Don't loop on the sender
        if (destinationHandler == senderHandler)
          continue;
        servers.add(destinationHandler);
      }
    }
    else
    {
      // Destination is one server
      ServerHandler destinationHandler =
        connectedServers.get(msg.getDestination());
      if (destinationHandler != null)
      {
        servers.add(destinationHandler);
      }
      else
      {
        // the targeted server is NOT connected
        // Let's search for THE changelog server that MAY
        // have the targeted server connected.
        if (senderHandler.isLDAPserver())
        {
          for (ServerHandler h : replicationServers.values())
          {
            // Send to all replication servers with a least one remote
            // server connected
            if (h.isRemoteLDAPServer(msg.getDestination()))
            {
              servers.add(h);
            }
          }
        }
      }
    }
    return servers;
  }

  /**
   * Processes a message coming from one server in the topology
   * and potentially forwards it to one or all other servers.
   *
   * @param msg The message received and to be processed.
   * @param senderHandler The server handler of the server that emitted
   * the message.
   */
  public void process(RoutableMessage msg, ServerHandler senderHandler)
  {

    // Test the message for which a ReplicationServer is expected
    // to be the destination
    if (msg.getDestination() == this.replicationServer.getServerId())
    {
      if (msg instanceof ErrorMessage)
      {
        ErrorMessage errorMsg = (ErrorMessage)msg;
        logError(ERR_ERROR_MSG_RECEIVED.get(
            errorMsg.getDetails()));
      }
      else if (msg instanceof MonitorRequestMessage)
      {
        MonitorRequestMessage replServerMonitorRequestMsg =
          (MonitorRequestMessage) msg;

        MonitorMessage monitorMsg =
          new MonitorMessage(
              replServerMonitorRequestMsg.getDestination(),
              replServerMonitorRequestMsg.getsenderID());

        // Populate for each connected LDAP Server
        // from the states stored in the serverHandler.
        // - the server state
        // - the older missing change
        for (ServerHandler lsh : this.connectedServers.values())
        {
          monitorMsg.setServerState(
              lsh.getServerId(),
              lsh.getServerState(),
              lsh.getApproxFirstMissingDate(),
              true);
        }

        // Same for the connected RS
        for (ServerHandler rsh : this.replicationServers.values())
        {
          monitorMsg.setServerState(
              rsh.getServerId(),
              rsh.getServerState(),
              rsh.getApproxFirstMissingDate(),
              false);
        }

        // Populate the RS state in the msg from the DbState
        monitorMsg.setReplServerDbState(this.getDbServerState());


        try
        {
          senderHandler.send(monitorMsg);
        }
        catch(Exception e)
        {
          // We log the error. The requestor will detect a timeout or
          // any other failure on the connection.
          logError(ERR_CHANGELOG_ERROR_SENDING_MSG.get(
              Short.toString((msg.getDestination()))));
        }
      }
      else if (msg instanceof MonitorMessage)
      {
        MonitorMessage monitorMsg =
          (MonitorMessage) msg;

        receivesMonitorDataResponse(monitorMsg);
      }
      else
      {
        logError(NOTE_ERR_ROUTING_TO_SERVER.get(
            msg.getClass().getCanonicalName()));
      }
      return;
    }

    List<ServerHandler> servers = getDestinationServers(msg, senderHandler);

    if (servers.isEmpty())
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get());
      mb.append(" In Replication Server=" + this.replicationServer.
          getMonitorInstanceName());
      mb.append(" domain =" + this.baseDn);
      mb.append(" unroutable message =" + msg.toString());
      mb.append(" routing table is empty");
      ErrorMessage errMsg = new ErrorMessage(
          this.replicationServer.getServerId(),
          msg.getsenderID(),
          mb.toMessage());
      logError(mb.toMessage());
      try
      {
        senderHandler.send(errMsg);
      }
      catch(IOException ioe)
      {
        // TODO Handle error properly (sender timeout in addition)
        /*
         * An error happened trying to send an error msg to this server.
         * Log an error and close the connection to this server.
         */
        MessageBuilder mb2 = new MessageBuilder();
        mb2.append(ERR_CHANGELOG_ERROR_SENDING_ERROR.get(this.toString()));
        mb2.append(stackTraceToSingleLineString(ioe));
        logError(mb2.toMessage());
        senderHandler.shutdown();
      }
    }
    else
    {
      for (ServerHandler targetHandler : servers)
      {
        try
        {
          targetHandler.send(msg);
        }
        catch(IOException ioe)
        {
          /*
           * An error happened trying the send a routabled message
           * to its destination server.
           * Send back an error to the originator of the message.
           */
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_CHANGELOG_ERROR_SENDING_MSG.get(this.toString()));
          mb.append(stackTraceToSingleLineString(ioe));
          mb.append(" ");
          mb.append(msg.getClass().getCanonicalName());
          logError(mb.toMessage());

          MessageBuilder mb1 = new MessageBuilder();
          mb1.append(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.get());
          mb1.append("serverID:" + msg.getDestination());
          ErrorMessage errMsg = new ErrorMessage(
              msg.getsenderID(), mb1.toMessage());
          try
          {
            senderHandler.send(errMsg);
          }
          catch(IOException ioe1)
          {
            // an error happened on the sender session trying to recover
            // from an error on the receiver session.
            // We don't have much solution left beside closing the sessions.
            senderHandler.shutdown();
            targetHandler.shutdown();
          }
          // TODO Handle error properly (sender timeout in addition)
        }
      }
    }

  }

    /**
     * Send back an ack to the server that sent the change.
     *
     * @param changeNumber The ChangeNumber of the change that must be acked.
     * @param isLDAPserver This boolean indicates if the server that sent the
     *                     change was an LDAP server or a ReplicationServer.
     */
    public void sendAck(ChangeNumber changeNumber, boolean isLDAPserver)
    {
      short serverId = changeNumber.getServerId();
      sendAck(changeNumber, isLDAPserver, serverId);
    }

    /**
     *
     * Send back an ack to a server that sent the change.
     *
     * @param changeNumber The ChangeNumber of the change that must be acked.
     * @param isLDAPserver This boolean indicates if the server that sent the
     *                     change was an LDAP server or a ReplicationServer.
     * @param serverId     The identifier of the server from which we
     *                     received the change..
     */
    public void sendAck(ChangeNumber changeNumber, boolean isLDAPserver,
        short serverId)
    {
      ServerHandler handler;
      if (isLDAPserver)
        handler = connectedServers.get(serverId);
      else
        handler = replicationServers.get(serverId);

      // TODO : check for null handler and log error
      try
      {
        handler.sendAck(changeNumber);
      } catch (IOException e)
      {
        /*
         * An error happened trying the send back an ack to this server.
         * Log an error and close the connection to this server.
         */
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CHANGELOG_ERROR_SENDING_ACK.get(this.toString()));
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
        handler.shutdown();
      }
    }

    /**
     * Shutdown this ReplicationServerDomain.
     */
    public void shutdown()
    {
      // Close session with other changelogs
      for (ServerHandler serverHandler : replicationServers.values())
      {
        serverHandler.shutdown();
      }

      // Close session with other LDAP servers
      for (ServerHandler serverHandler : connectedServers.values())
      {
        serverHandler.shutdown();
      }

      // Shutdown the dbHandlers
      synchronized (sourceDbHandlers)
      {
        for (DbHandler dbHandler : sourceDbHandlers.values())
        {
          dbHandler.shutdown();
        }
        sourceDbHandlers.clear();
      }
    }

    /**
     * Returns the ServerState describing the last change from this replica.
     *
     * @return The ServerState describing the last change from this replica.
     */
    public ServerState getDbServerState()
    {
      ServerState serverState = new ServerState();
      for (DbHandler db : sourceDbHandlers.values())
      {
        serverState.update(db.getLastChange());
      }
      return serverState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return "ReplicationServerDomain " + baseDn;
    }

    /**
     * Check if some server Handler should be removed from flow control state.
     * @throws IOException If an error happened.
     */
    public void checkAllSaturation() throws IOException
    {
      for (ServerHandler handler : replicationServers.values())
      {
        handler.checkWindow();
      }

      for (ServerHandler handler : connectedServers.values())
      {
        handler.checkWindow();
      }
    }

    /**
     * Check if a server that was in flow control can now restart
     * sending updates.
     * @param sourceHandler The server that must be checked.
     * @return true if the server can restart sending changes.
     *         false if the server can't restart sending changes.
     */
    public boolean restartAfterSaturation(ServerHandler sourceHandler)
    {
      for (ServerHandler handler : replicationServers.values())
      {
        if (!handler.restartAfterSaturation(sourceHandler))
          return false;
      }

      for (ServerHandler handler : connectedServers.values())
      {
        if (!handler.restartAfterSaturation(sourceHandler))
          return false;
      }
      return true;
    }

    /**
     * Send a ReplServerInfoMessage to all the connected replication servers
     * in order to let them know our connected LDAP servers.
     */
    private void sendReplServerInfo()
    {
      ReplServerInfoMessage info =
        new ReplServerInfoMessage(getConnectedLDAPservers(), generationId);
      for (ServerHandler handler : replicationServers.values())
      {
        try
        {
          handler.sendInfo(info);
        }
        catch (IOException e)
        {
          /*
           * An error happened trying the send back an ack to this server.
           * Log an error and close the connection to this server.
           */
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_CHANGELOG_ERROR_SENDING_INFO.get(this.toString()));
          mb.append(stackTraceToSingleLineString(e));
          logError(mb.toMessage());
          handler.shutdown();
        }
      }
    }

    /**
     * Get the generationId associated to this domain.
     *
     * @return The generationId
     */
    public long getGenerationId()
    {
      return generationId;
    }

    /**
     * Get the generationId saved status.
     *
     * @return The generationId saved status.
     */
    public boolean getGenerationIdSavedStatus()
    {
      return generationIdSavedStatus;
    }

    /**
     * Sets the provided value as the new in memory generationId.
     *
     * @param generationId The new value of generationId.
     * @param savedStatus  The saved status of the generationId.
     */
    synchronized public void setGenerationId(long generationId,
        boolean savedStatus)
    {
      if (debugEnabled())
        TRACER.debugInfo(
          "In " + this.replicationServer.getMonitorInstanceName() +
          " baseDN=" + baseDn +
          " RCache.set GenerationId=" + generationId);

      if (this.generationId != generationId)
      {
        // we are changing of genId
        clearDbs();

        this.generationId = generationId;
        this.generationIdSavedStatus = savedStatus;

        // they have a generationId different from the reference one
        for (ServerHandler handler : connectedServers.values())
        {
          if (generationId != handler.getGenerationId())
          {
            // Notify our remote LS that from now on have a different genID
            handler.warnBadGenerationId();
          }
        }
      }
    }

    /**
     * Resets the generationID.
     *
     * @param senderHandler The handler associated to the server
     *        that requested to reset the generationId.
     * @param genIdMsg The reset generation ID msg received.
     */
    public void resetGenerationId(ServerHandler senderHandler,
        ResetGenerationId genIdMsg)
    {
      long newGenId = genIdMsg.getGenerationId();

      if (newGenId != this.generationId)
      {
        this.setGenerationId(newGenId, false);
      }

      // If we are the first replication server warned,
      // then forwards the reset message to the remote replication servers
      for (ServerHandler rsHandler : replicationServers.values())
      {
        try
        {
          // After we'll have send the mssage , the remote RS will adopt
          // the new genId
          rsHandler.setGenerationId(newGenId);
          if (senderHandler.isLDAPserver())
          {
            rsHandler.forwardGenerationIdToRS(genIdMsg);
          }
        }
        catch (IOException e)
        {
          logError(ERR_CHANGELOG_ERROR_SENDING_INFO.
              get(rsHandler.getMonitorInstanceName()));
        }
      }
    }

    /**
     * Clears the Db associated with that cache.
     */
    public void clearDbs()
    {
      // Reset the localchange and state db for the current domain
      synchronized (sourceDbHandlers)
      {
        for (DbHandler dbHandler : sourceDbHandlers.values())
        {
          try
          {
            dbHandler.clear();
          }
          catch (Exception e)
          {
            // TODO: i18n
            MessageBuilder mb = new MessageBuilder();
            mb.append(ERR_ERROR_CLEARING_DB.get(dbHandler.toString(),
                e.getMessage() + " " +
                stackTraceToSingleLineString(e)));
            logError(mb.toMessage());
          }
        }
        sourceDbHandlers.clear();

        if (debugEnabled())
          TRACER.debugInfo(
              "In " + this.replicationServer.getMonitorInstanceName() +
              " baseDN=" + baseDn +
          " The source db handler has been cleared");
      }
      try
      {
        replicationServer.clearGenerationId(baseDn);
      }
      catch (Exception e)
      {
        // TODO: i18n
        logError(Message.raw(
            "Exception caught while clearing generationId:" +
            e.getLocalizedMessage()));
      }
    }

    /**
     * Returns whether the provided server is in degraded
     * state due to the fact that the peer server has an invalid
     * generationId for this domain.
     *
     * @param serverId The serverId for which we want to know the
     *                 the state.
     * @return Whether it is degraded or not.
     */

    public boolean isDegradedDueToGenerationId(short serverId)
    {
      if (debugEnabled())
        TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
            " baseDN=" + baseDn +
            " isDegraded serverId=" + serverId +
            " given local generation Id=" + this.generationId);

      ServerHandler handler = replicationServers.get(serverId);
      if (handler == null)
      {
        handler = connectedServers.get(serverId);
        if (handler == null)
        {
          return false;
        }
      }

      if (debugEnabled())
        TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
            " baseDN=" + baseDn +
            " Compute degradation of serverId=" + serverId +
            " LS server generation Id=" + handler.getGenerationId());
      return (handler.getGenerationId() != this.generationId);
    }

    /**
     * Return the associated replication server.
     * @return The replication server.
     */
    public ReplicationServer getReplicationServer()
    {
      return replicationServer;
    }

    /**
     * Process reception of a ReplServerInfoMessage.
     *
     * @param infoMsg The received message.
     * @param handler The handler that received the message.
     * @throws IOException when raised by the underlying session.
     */
    public void receiveReplServerInfo(
        ReplServerInfoMessage infoMsg, ServerHandler handler) throws IOException
    {
      if (debugEnabled())
      {
        if (handler.isReplicationServer())
          TRACER.debugInfo(
           "In RS " + getReplicationServer().getServerId() +
           " Receiving replServerInfo from " + handler.getServerId() +
           " baseDn=" + baseDn +
           " genId=" + infoMsg.getGenerationId());
      }

      mayResetGenerationId();
      if (generationId < 0)
        generationId = handler.getGenerationId();
      if (generationId > 0 && (generationId != infoMsg.getGenerationId()))
      {
        Message message = NOTE_BAD_GENERATION_ID.get(
            baseDn.toNormalizedString(),
            Short.toString(handler.getServerId()),
            Long.toString(infoMsg.getGenerationId()),
            Long.toString(generationId));

        ErrorMessage errorMsg = new ErrorMessage(
            getReplicationServer().getServerId(),
            handler.getServerId(),
            message);
        handler.sendError(errorMsg);
      }
    }

    /* =======================
     * Monitor Data generation
     * =======================
     */

    /**
     * Retrieves the global monitor data.
     * @return The monitor data.
     * @throws DirectoryException When an error occurs.
     */
    synchronized protected MonitorData getMonitorData()
      throws DirectoryException
    {
      if (monitorData.getBuildDate() + monitorDataLifeTime
          > TimeThread.getTime())
      {
        if (debugEnabled())
          TRACER.debugInfo(
          "In " + this.replicationServer.getMonitorInstanceName() +
          " baseDn=" + baseDn + " getRemoteMonitorData in cache");
        // The current data are still valid. No need to renew them.
        return monitorData;
      }

      wrkMonitorData = new MonitorData();
      synchronized(wrkMonitorData)
      {
        if (debugEnabled())
          TRACER.debugInfo(
          "In " + this.replicationServer.getMonitorInstanceName() +
          " baseDn=" + baseDn + " Computing monitor data ");

        // Let's process our directly connected LSes
        // - in the ServerHandler for a given LS1, the stored state contains :
        //   - the max CN produced by LS1
        //   - the last CN consumed by LS1 from LS2..n
        // - in the RSdomain/dbHandler, the built-in state contains :
        //   - the max CN produced by each server
        // So for a given LS connected we can take the state and the max from
        // the LS/state.

        for (ServerHandler directlsh : connectedServers.values())
        {
          short serverID = directlsh.getServerId();

          // the state comes from the state stored in the SH
          ServerState directlshState = directlsh.getServerState().duplicate();

          // the max CN sent by that LS also comes from the SH
          ChangeNumber maxcn = directlshState.getMaxChangeNumber(serverID);
          if (maxcn == null)
          {
            // This directly connected LS has never produced any change
            maxcn = new ChangeNumber(0, 0 , serverID);
          }
          wrkMonitorData.setMaxCN(serverID, maxcn);
          wrkMonitorData.setLDAPServerState(serverID, directlshState);
          wrkMonitorData.setFirstMissingDate(serverID, directlsh.
                                             getApproxFirstMissingDate());
        }

        // Then initialize the max CN for the LS that produced something
        // - from our own local db state
        // - whatever they are directly or undirectly connected
        ServerState dbServerState = getDbServerState();
        Iterator<Short> it = dbServerState.iterator();
        while (it.hasNext())
        {
          short sid = it.next();
          ChangeNumber storedCN = dbServerState.getMaxChangeNumber(sid);
          wrkMonitorData.setMaxCN(sid, storedCN);
        }

        // Now we have used all available local informations
        // and we need the remote ones.
        if (debugEnabled())
          TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
            " baseDn=" + baseDn + " Local monitor data: " +
            wrkMonitorData.toString());
      }

      // Send Request to the other Replication Servers
      if (remoteMonitorResponsesSemaphore == null)
      {
        remoteMonitorResponsesSemaphore = new Semaphore(0);
        short requestCnt = sendMonitorDataRequest();
        // Wait reponses from them or timeout
        waitMonitorDataResponses(requestCnt);
      }
      else
      {
        // The processing of renewing the monitor cache is already running
        // We'll make it sleeping until the end
        // TODO: unit test for this case.
        while (remoteMonitorResponsesSemaphore!=null)
        {
          waitMonitorDataResponses(1);
        }
      }

      wrkMonitorData.completeComputing();

      // Store the new computed data as the reference
      synchronized(monitorData)
      {
        // Now we have the expected answers or an error occured
        monitorData = wrkMonitorData;
        wrkMonitorData = null;
        if (debugEnabled())
          TRACER.debugInfo(
          "In " + this.replicationServer.getMonitorInstanceName() +
          " baseDn=" + baseDn + " *** Computed MonitorData: " +
          monitorData.toString());
      }
      return monitorData;
    }


    /**
     * Sends a MonitorRequest message to all connected RS.
     * @return the number of requests sent.
     * @throws DirectoryException when a problem occurs.
     */
    protected short sendMonitorDataRequest()
      throws DirectoryException
    {
      short sent=0;
      try
      {
        for (ServerHandler rs : replicationServers.values())
        {
          MonitorRequestMessage msg = new
            MonitorRequestMessage(this.replicationServer.getServerId(),
              rs.getServerId());
          rs.send(msg);
          sent++;
        }
      }
      catch(Exception e)
      {
        Message message = ERR_SENDING_REMOTE_MONITOR_DATA_REQUEST.get();
        logError(message);
        throw new DirectoryException(ResultCode.OTHER,
            message, e);
      }
      return sent;
    }

    /**
     * Wait for the expected count of received MonitorMessage.
     * @param expectedResponses The number of expected answers.
     * @throws DirectoryException When an error occurs.
     */
    protected void waitMonitorDataResponses(int expectedResponses)
      throws DirectoryException
    {
      try
      {
        if (debugEnabled())
          TRACER.debugInfo(
          "In " + this.replicationServer.getMonitorInstanceName() +
          " baseDn=" + baseDn +
          " waiting for " + expectedResponses
          + " expected monitor messages");

        boolean allPermitsAcquired =
          remoteMonitorResponsesSemaphore.tryAcquire(
              expectedResponses,
              (long) 5000, TimeUnit.MILLISECONDS);

        if (!allPermitsAcquired)
        {
          logError(ERR_MISSING_REMOTE_MONITOR_DATA.get());
          // let's go on in best effort even with limited data received.
        }
        else
        {
          if (debugEnabled())
            TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
            " baseDn=" + baseDn +
            " Successfully received all " + expectedResponses
            + " expected monitor messages");
        }
      }
      catch(Exception e)
      {
        logError(ERR_PROCESSING_REMOTE_MONITOR_DATA.get(e.getMessage()));
      }
      finally
      {
        remoteMonitorResponsesSemaphore = null;
      }
    }

    /**
     * Processes a Monitor message receives from a remote Replication Server
     * and stores the data received.
     *
     * @param msg The message to be processed.
     */
    public void receivesMonitorDataResponse(MonitorMessage msg)
    {
      if (debugEnabled())
        TRACER.debugInfo(
        "In " + this.replicationServer.getMonitorInstanceName() +
        "Receiving " + msg + " from " + msg.getsenderID() +
        remoteMonitorResponsesSemaphore);

      if (remoteMonitorResponsesSemaphore == null)
      {
        // Let's ignore the remote monitor data just received
        // since the computing processing has been ended.
        // An error - probably a timemout - occured that was already logged
        logError(NOTE_IGNORING_REMOTE_MONITOR_DATA.get(
            Short.toString(msg.getsenderID())));
        return;
      }

      try
      {
        synchronized(wrkMonitorData)
        {
          // Here is the RS state : list <serverID, lastChangeNumber>
          // For each LDAP Server, we keep the max CN accross the RSes
          ServerState replServerState = msg.getReplServerDbState();
          wrkMonitorData.setMaxCNs(replServerState);

          // Store the remote LDAP servers states
          Iterator<Short> lsidIterator = msg.ldapIterator();
          while (lsidIterator.hasNext())
          {
            short sid = lsidIterator.next();
            wrkMonitorData.setLDAPServerState(sid,
                msg.getLDAPServerState(sid).duplicate());
            wrkMonitorData.setFirstMissingDate(sid,
                msg.getLDAPApproxFirstMissingDate(sid));
          }

          // Process the latency reported by the remote RSi on its connections
          // to the other RSes
          Iterator<Short> rsidIterator = msg.rsIterator();
          while (rsidIterator.hasNext())
          {
            short rsid = rsidIterator.next();
            if (rsid == replicationServer.getServerId())
            {
              // this is the latency of the remote RSi regarding the current RS
              // let's update the fmd of my connected LS
              for (ServerHandler connectedlsh : connectedServers.values())
              {
                short connectedlsid = connectedlsh.getServerId();
                Long newfmd = msg.getRSApproxFirstMissingDate(rsid);
                wrkMonitorData.setFirstMissingDate(connectedlsid, newfmd);
              }
            }
            else
            {
              // this is the latency of the remote RSi regarding another RSj
              // let's update the latency of the LSes connected to RSj
              ServerHandler rsjHdr = replicationServers.get(rsid);
              if (rsjHdr != null)
              {
                for(short remotelsid : rsjHdr.getConnectedServerIds())
                {
                  Long newfmd = msg.getRSApproxFirstMissingDate(rsid);
                  wrkMonitorData.setFirstMissingDate(remotelsid, newfmd);
                }
              }
            }
          }
          if (debugEnabled())
          {
            if (debugEnabled())
              TRACER.debugInfo(
              "In " + this.replicationServer.getMonitorInstanceName() +
              " baseDn=" + baseDn +
              " Processed msg from " + msg.getsenderID() +
              " New monitor data: " + wrkMonitorData.toString());
          }
        }

        // Decreases the number of expected responses and potentially
        // wakes up the waiting requestor thread.
        remoteMonitorResponsesSemaphore.release();

      }
      catch (Exception e)
      {
        logError(ERR_PROCESSING_REMOTE_MONITOR_DATA.get(e.getMessage() +
            stackTraceToSingleLineString(e)));

        // If an exception occurs while processing one of the expected message,
        // the processing is aborted and the waiting thread is awoke.
        remoteMonitorResponsesSemaphore.notifyAll();
      }
    }

    /**
     * Set the purge delay on all the db Handlers for this Domain
     * of Replicaiton.
     *
     * @param delay The new purge delay to use.
     */
    void setPurgeDelay(long delay)
    {
      for (DbHandler handler : sourceDbHandlers.values())
      {
        handler.setPurgeDelay(delay);
      }
    }
}
