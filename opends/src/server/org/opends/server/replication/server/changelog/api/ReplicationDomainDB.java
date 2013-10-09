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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.DN;

/**
 * This interface allows to query or control the replication domain database(s)
 * (composed of one or more ReplicaDBs) and query/update each ReplicaDB.
 */
public interface ReplicationDomainDB
{

  /**
   * Get the number of changes for the specified replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return the number of changes.
   */
  long getDomainChangesCount(DN baseDN);

  /**
   * Returns the oldest {@link CSN}s of each serverId for the specified
   * replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return a new ServerState object holding the {serverId => oldest CSN}
   *         mapping. If a replica DB is empty or closed, the oldest CSN will be
   *         null for that replica. The caller owns the generated ServerState.
   */
  ServerState getDomainOldestCSNs(DN baseDN);

  /**
   * Returns the newest {@link CSN}s of each serverId for the specified
   * replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return a new ServerState object holding the {serverId => newest CSN} Map.
   *         If a replica DB is empty or closed, the newest CSN will be null for
   *         that replica. The caller owns the generated ServerState.
   */
  ServerState getDomainNewestCSNs(DN baseDN);

  /**
   * Retrieves the latest trim date for the specified replication domain.
   * <p>
   * FIXME will be removed when ECLServerHandler will not be responsible anymore
   * for lazily building the ChangeNumberIndexDB.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return the domain latest trim date
   */
  long getDomainLatestTrimDate(DN baseDN);

  /**
   * Shutdown all the replica databases for the specified replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   */
  void shutdownDomain(DN baseDN);

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

  // serverId methods

  /**
   * Return the number of changes inclusive between 2 provided {@link CSN}s for
   * the specified serverId and replication domain. i.e. the <code>from</code>
   * and <code>to</code> CSNs are included in the count.
   * <p>
   * Note that:
   * <ol>
   * <li>If <code>from</code> is null, the count starts at the oldest CSN in the
   * database.</li>
   * <li>If <code>to</code> is null, the count is 0.</li>
   * <li>If both from and to are present, then the count includes them both
   * <code>to</code> is null, the count ends at the newest CSN in the database.
   * </li>
   * <li>incidentally, if both <code>from</code> and <code>to</code> are null,
   * the total count of entries in the replica database is returned.</li>
   * </ol>
   * <h6>Example</h6>
   * <p>
   * Given the following replica database for baseDN "dc=example,dc=com" and
   * serverId 1:
   *
   * <pre>
   * CSN1  <=  Oldest
   * CSN2
   * CSN3
   * CSN4
   * CSN5  <=  Newest
   * </pre>
   *
   * Then:
   *
   * <pre>
   * assertEquals(getCount(&quot;dc=example,dc=com&quot;, 1, CSN1, CSN1), 1);
   * assertEquals(getCount(&quot;dc=example,dc=com&quot;, 1, CSN1, CSN2), 2);
   * assertEquals(getCount(&quot;dc=example,dc=com&quot;, 1, CSN1, CSN5), 5);
   * assertEquals(getCount(&quot;dc=example,dc=com&quot;, 1, null, CSN5), 5);
   * assertEquals(getCount(&quot;dc=example,dc=com&quot;, 1, CSN1, null), 0);
   * assertEquals(getCount(&quot;dc=example,dc=com&quot;, 1, null, null), 5);
   * </pre>
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param serverId
   *          the serverId on which to act
   * @param from
   *          The older CSN where to start the count
   * @param to
   *          The newer CSN where to end the count
   * @return The computed number of changes
   */
  long getCount(DN baseDN, int serverId, CSN from, CSN to);

  /**
   * Generates a {@link ReplicaDBCursor} across all the replicaDBs for the
   * specified replication domain, with all cursors starting after the provided
   * CSN.
   * <p>
   * The cursor is already advanced to the record after startAfterCSN.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link ReplicaDBCursor#close()} method to free the resources and locks used
   * by the cursor.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param startAfterCSN
   *          Starting point for each ReplicaDB cursor. If null, start from the
   *          oldest CSN for each ReplicaDB cursor.
   * @return a non null {@link ReplicaDBCursor}
   * @see #getCursorFrom(DN, ServerState)
   */
  ReplicaDBCursor getCursorFrom(DN baseDN, CSN startAfterCSN);

  /**
   * Generates a {@link ReplicaDBCursor} across all the replicaDBs for the
   * specified replication domain starting after the provided
   * {@link ServerState} for each replicaDBs.
   * <p>
   * The cursor is already advanced to the records after the serverState.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link ReplicaDBCursor#close()} method to free the resources and locks used
   * by the cursor.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param startAfterServerState
   *          Starting point for each ReplicaDB cursor. If any CSN for a
   *          replicaDB is null, then start from the oldest CSN for this
   *          replicaDB
   * @return a non null {@link ReplicaDBCursor}
   * @see #getCursorFrom(DN, CSN)
   */
  ReplicaDBCursor getCursorFrom(DN baseDN, ServerState startAfterServerState);

  /**
   * for the specified serverId and replication domain.
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
   * @param csn
   *          The CSN heartbeat sent by this replica (contains the serverId and
   *          timestamp of the heartbeat)
   */
  void replicaHeartbeat(DN baseDN, CSN csn);

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
   * @param serverId
   *          The replica's serverId going offline
   */
  void replicaOffline(DN baseDN, int serverId);
}
