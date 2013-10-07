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

import java.util.Set;

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
   * Returns the serverIds for the servers that are or have been part of the
   * provided replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return an unmodifiable set of integers holding the serverIds
   */
  Set<Integer> getDomainServerIds(DN baseDN);

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
   * Generates a {@link ReplicaDBCursor} for the specified serverId and
   * replication domain starting after the provided CSN.
   * <p>
   * The cursor is already advanced to the record after startAfterCSN.
   * <p>
   * When the cursor is not used anymore, client code MUST call the
   * {@link ReplicaDBCursor#close()} method to free the resources and locks used
   * by the cursor.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param serverId
   *          Identifier of the server for which the cursor is created
   * @param startAfterCSN
   *          Starting point for the cursor. If null, start from the oldest CSN
   * @return a non null {@link ReplicaDBCursor}
   */
  ReplicaDBCursor getCursorFrom(DN baseDN, int serverId, CSN startAfterCSN);

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

}
