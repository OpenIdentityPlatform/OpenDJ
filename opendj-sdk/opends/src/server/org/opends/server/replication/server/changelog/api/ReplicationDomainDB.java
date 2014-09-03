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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;

import java.util.Set;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.replication.server.changelog.je.MultiDomainDBCursor;
import org.opends.server.types.DN;

/**
 * This interface allows to query or control the replication domain database(s)
 * (composed of one or more ReplicaDBs) and query/update each ReplicaDB.
 */
public interface ReplicationDomainDB
{

  /**
   * Returns the newest {@link CSN}s from the replicaDBs for each serverId in
   * the specified replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return a new ServerState object holding the {serverId => newest CSN} Map.
   *         If a replica DB is empty or closed, the newest CSN will be null for
   *         that replica. The caller owns the generated ServerState.
   */
  ServerState getDomainNewestCSNs(DN baseDN);

  /**
   * Removes all the data relating to the specified replication domain and
   * shutdown all its replica databases. In particular, it will:
   * <ol>
   * <li>remove all the changes from the replica databases</li>
   * <li>remove all knowledge of the serverIds in this domain</li>
   * <li>remove any knowledge of the current generationId for this domain</li>
   * </ol>
   *
   * @param baseDN
   *          the replication domain baseDN
   * @throws ChangelogException
   *           If a database problem happened
   */
  void removeDomain(DN baseDN) throws ChangelogException;

  /**
   * Generates a {@link DBCursor} across all the domains starting at or after the
   * provided {@link MultiDomainServerState} for each domain.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link DBCursor#close()} method to free the resources and locks used by the
   * cursor.
   *
   * @param startState
   *          Starting point for each domain cursor. If any {@link ServerState}
   *          for a domain is null, then start from the oldest CSN for each
   *          replicaDBs
   * @param positionStrategy
   *          Cursor position strategy, which allow to indicates at which
   *          exact position the cursor must start
   * @return a non null {@link DBCursor}
   * @throws ChangelogException
   *           If a database problem happened
   * @see #getCursorFrom(DN, ServerState, PositionStrategy)
   */
  public MultiDomainDBCursor getCursorFrom(MultiDomainServerState startState, PositionStrategy positionStrategy)
      throws ChangelogException;

  /**
   * Generates a {@link DBCursor} across all the domains starting at or after
   * the provided {@link MultiDomainServerState} for each domain, excluding a
   * provided set of domain DNs.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link DBCursor#close()} method to free the resources and locks used by the
   * cursor.
   *
   * @param startState
   *          Starting point for each domain cursor. If any {@link ServerState}
   *          for a domain is null, then start from the oldest CSN for each
   *          replicaDBs
   * @param positionStrategy
   *          Cursor position strategy, which allow to indicates at which exact
   *          position the cursor must start
   * @param excludedDomainDns
   *          Every domain appearing in this set is excluded from the cursor
   * @return a non null {@link DBCursor}
   * @throws ChangelogException
   *           If a database problem happened
   * @see #getCursorFrom(DN, ServerState, PositionStrategy)
   */
  public MultiDomainDBCursor getCursorFrom(MultiDomainServerState startState, PositionStrategy positionStrategy,
      Set<DN> excludedDomainDns) throws ChangelogException;

  // serverId methods

  /**
   * Generates a {@link DBCursor} across all the replicaDBs for the specified
   * replication domain starting at or after the provided {@link ServerState} for each
   * replicaDBs.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link DBCursor#close()} method to free the resources and locks used by the
   * cursor.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param startState
   *          Starting point for each ReplicaDB cursor. If any CSN for a
   *          replicaDB is null, then start from the oldest CSN for this
   *          replicaDB
   * @param positionStrategy
   *          Cursor position strategy, which allow to indicates at which
   *          exact position the cursor must start
   * @return a non null {@link DBCursor}
   * @throws ChangelogException
   *           If a database problem happened
   * @see #getCursorFrom(DN, int, CSN, PositionStrategy)
   */
  DBCursor<UpdateMsg> getCursorFrom(DN baseDN, ServerState startState, PositionStrategy positionStrategy)
      throws ChangelogException;

  /**
   * Generates a {@link DBCursor} for one replicaDB for the specified
   * replication domain and serverId starting at or after the provided {@link CSN}.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link DBCursor#close()} method to free the resources and locks used by the
   * cursor.
   *
   * @param baseDN
   *          the replication domain baseDN of the replicaDB
   * @param serverId
   *          the serverId of the replicaDB
   * @param startCSN
   *          Starting point for the ReplicaDB cursor. If the CSN is null, then
   *          start from the oldest CSN for this replicaDB
   * @param positionStrategy
   *          Cursor position strategy, which allow to indicates at which
   *          exact position the cursor must start
   * @return a non null {@link DBCursor}
   * @throws ChangelogException
   *           If a database problem happened
   */
  DBCursor<UpdateMsg> getCursorFrom(DN baseDN, int serverId, CSN startCSN, PositionStrategy positionStrategy)
      throws ChangelogException;

  /**
   * Unregisters the provided cursor from this replication domain.
   *
   * @param cursor
   *          the cursor to unregister.
   */
  void unregisterCursor(DBCursor<?> cursor);

  /**
   * Publishes the provided change to the changelog DB for the specified
   * serverId and replication domain. After a change has been successfully
   * published, it becomes available to be returned by the External ChangeLog.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param updateMsg
   *          the update message to publish to the replicaDB
   * @return true if a db had to be created to publish this message
   * @throws ChangelogException
   *           If a database problem happened
   */
  boolean publishUpdateMsg(DN baseDN, UpdateMsg updateMsg)
      throws ChangelogException;

  /**
   * Let the DB know this replica is alive.
   * <p>
   * This method allows the medium consistency point to move forward in case
   * this replica did not publish new changes.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param heartbeatCSN
   *          The CSN heartbeat sent by this replica (contains the serverId and
   *          timestamp of the heartbeat)
   * @throws ChangelogException
   *            If a database problem happened
   */
  void replicaHeartbeat(DN baseDN, CSN heartbeatCSN) throws ChangelogException;

  /**
   * Let the DB know this replica is going down.
   * <p>
   * This method allows to let the medium consistency point move forward while
   * this replica is offline.
   * <p>
   * Note: This method must not be called to let the DB know the replica is not
   * sending heartbeats anymore, i.e. it must not be used in case of suspected
   * network partition.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param offlineCSN
   *          The CSN (serverId and timestamp) for the replica's going offline
   * @throws ChangelogException
   *           If a database problem happened
   */
  void notifyReplicaOffline(DN baseDN, CSN offlineCSN) throws ChangelogException;
}
