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
package org.opends.server.replication.server;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.AckMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.InitializeRequestMessage;
import org.opends.server.replication.protocol.RoutableMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

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
public class ReplicationCache
{
  private Object flowControlLock = new Object();
  private DN baseDn = null;

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
  private Map<Short, ServerHandler> connectedServers =
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
  private Map<Short, ServerHandler> replicationServers =
    new ConcurrentHashMap<Short, ServerHandler>();

  /*
   * This map contains the List of updates received from each
   * LDAP server
   */
  private Map<Short, DbHandler> sourceDbHandlers =
    new ConcurrentHashMap<Short, DbHandler>();
  private ReplicationServer replicationServer;

  /**
   * Creates a new ReplicationCache associated to the DN baseDn.
   *
   * @param baseDn The baseDn associated to the ReplicationCache.
   * @param replicationServer the ReplicationServer that created this
   *                          replicationServer cache.
   */
  public ReplicationCache(DN baseDn, ReplicationServer replicationServer)
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

    // look for the dbHandler that is responsible for the master server which
    // generated the change.
    DbHandler dbHandler = null;
    synchronized (sourceDbHandlers)
    {
      short id  = update.getChangeNumber().getServerId();
      dbHandler   = sourceDbHandlers.get(id);
      if (dbHandler == null)
      {
        try
        {
          dbHandler = replicationServer.newDbHandler(id, baseDn);
        } catch (DatabaseException e)
        {
          /*
           * Because of database problem we can't save any more changes
           * from at least one LDAP server.
           * This replicationServer therefore can't do it's job properly anymore
           * and needs to close all its connections and shutdown itself.
           */
          int    msgID   = MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR;
          String message = getMessage(msgID) + stackTraceToSingleLineString(e);
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
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
   * Create initialize context necessary for finding the changes
   * that must be sent to a given LDAP or replication server.
   *
   * @param handler handler for the server that must be started
   * @throws Exception when method has failed
   */
  public void startServer(ServerHandler handler) throws Exception
  {
    /*
     * create the balanced tree that will be used to forward changes
     */
    synchronized (connectedServers)
    {
      if (connectedServers.containsKey(handler.getServerId()))
      {
        /* TODO : handle error properly */
        throw new Exception("serverId already registered");
      }
      connectedServers.put(handler.getServerId(), handler);
    }
  }

  /**
   * Stop operations with a given server.
   *
   * @param handler the server for which we want to stop operations
   */
  public void stopServer(ServerHandler handler)
  {
    handler.stopHandler();

    if (handler.isReplicationServer())
      replicationServers.remove(handler.getServerId());
    else
      connectedServers.remove(handler.getServerId());
  }

  /**
   * Create initialize context necessary for finding the changes
   * that must be sent to a given replication server.
   *
   * @param handler the server ID to which we want to forward changes
   * @throws Exception in case of errors
   */
  public void startReplicationServer(ServerHandler handler) throws Exception
  {
    /*
     * create the balanced tree that will be used to forward changes
     * TODO throw proper exception
     */
    synchronized (replicationServers)
    {
      if (replicationServers.containsKey(handler.getServerId()))
      {
        throw new Exception("Replication Server Id already registered");
      }
      replicationServers.put(handler.getServerId(), handler);
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
   * Creates and returns an iterator.
   *
   * @param serverId Identifier of the server for which the iterator is created.
   * @param changeNumber Starting point for the iterator.
   * @return the created ReplicationIterator.
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
    catch (Exception e) {
     return null;
    }
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
   * creates a new ReplicationDB with specified identifier.
   * @param id the identifier of the new ReplicationDB.
   * @param db the new db.
   *
   * @throws DatabaseException If a database error happened.
   */
  public void newDb(short id, DbHandler db) throws DatabaseException
  {
    synchronized (sourceDbHandlers)
    {
      sourceDbHandlers.put(id , db);
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

    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.SEVERE_ERROR,
        "getDestinationServers"
        + " msgDest:" + msg.getDestination() , 1);

    if (msg.getDestination() == RoutableMessage.THE_CLOSEST_SERVER)
    {
      // TODO Import from the "closest server" to be implemented
    }
    else if (msg.getDestination() == RoutableMessage.ALL_SERVERS)
    {
      if (!senderHandler.isReplicationServer())
      {
        // Send to all replicationServers
        for (ServerHandler destinationHandler : replicationServers.values())
        {
          servers.add(destinationHandler);
        }
      }

      // Send to all connected LDAP servers
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
        if (senderHandler.isLDAPserver())
        {
          // let's forward to the other changelogs
          servers.addAll(replicationServers.values());
        }
      }
    }

    return servers;
  }

  /**
   * Process an InitializeRequestMessage.
   *
   * @param msg The message received and to be processed.
   * @param senderHandler The server handler of the server that emitted
   * the message.
   */
  public void process(RoutableMessage msg, ServerHandler senderHandler)
  {

    List<ServerHandler> servers = getDestinationServers(msg, senderHandler);

    if (servers.isEmpty())
    {
      if (!(msg instanceof InitializeRequestMessage))
      {
        // TODO A more elaborated policy is probably needed
      }
      else
      {
        ErrorMessage errMsg = new ErrorMessage(
            msg.getsenderID(), MSGID_NO_REACHABLE_PEER_IN_THE_DOMAIN,
            "serverID:" + msg.getDestination());

        try
        {
          senderHandler.send(errMsg);
        }
        catch(IOException ioe)
        {
          // TODO Handle error properly (sender timeout in addition)
        }
      }
      return;
    }

    for (ServerHandler targetHandler : servers)
    {
      try
      {
        targetHandler.send(msg);
      }
      catch(IOException ioe)
      {
        // TODO Handle error properly (sender timeout in addition)
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
        int    msgID   = MSGID_CHANGELOG_ERROR_SENDING_ACK;
        String message = getMessage(msgID, this.toString())
        + stackTraceToSingleLineString(e);
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.SEVERE_ERROR,
            message, msgID);
        handler.shutdown();
      }
    }

    /**
     * Shutdown this ReplicationCache.
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
      return "ReplicationCache " + baseDn;
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
}
