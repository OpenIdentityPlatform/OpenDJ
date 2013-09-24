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

import java.util.Map;
import java.util.Set;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.DN;

/**
 * The changelogDB stores the replication data on persistent storage.
 * <p>
 * This interface allows to:
 * <ul>
 * <li>set the storage directory and the purge interval</li>
 * <li>get access to the {@link ChangeNumberIndexDB}</li>
 * <li>query or control the replication domain database(s) (composed of one or
 * more ReplicaDBs)</li>
 * <li>query/update each ReplicaDB</li>
 * </ul>
 */
public interface ChangelogDB
{

  // DB control methods

  /**
   * Get the replication server database directory. This is used by tests to do
   * some cleanup.
   *
   * @return the database directory name
   */
  String getDBDirectoryName();

  /**
   * Initializes the replication database by reading its previous state and
   * building the relevant ReplicaDBs according to the previous state. This
   * method must be called once before using the ChangelogDB.
   */
  void initializeDB();

  /**
   * Sets the purge delay for the replication database. Can be called while the
   * database is running.
   * <p>
   * Purging happens on a best effort basis, i.e. the purge delay is used by the
   * replication database to know which data can be purged, but there are no
   * guarantees on when the purging will actually happen.
   *
   * @param delayInMillis
   *          the purge delay in milliseconds
   */
  void setPurgeDelay(long delayInMillis);

  /**
   * Shutdown the replication database.
   */
  void shutdownDB();

  /**
   * Returns a new {@link ChangeNumberIndexDB} object.
   *
   * @return a new {@link ChangeNumberIndexDB} object
   * @throws ChangelogException
   *           If a database problem happened
   */
  ChangeNumberIndexDB newChangeNumberIndexDB() throws ChangelogException;

  // Domain methods

  /**
   * Returns the serverIds for the servers that are or have been part of the
   * provided replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return a set of integers holding the serverIds
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
   * @return a {serverId => oldest CSN} Map. If a replica DB is empty or closed,
   *         the oldest CSN will be null for that replica.
   */
  Map<Integer, CSN> getDomainOldestCSNs(DN baseDN);

  /**
   * Returns the newest {@link CSN}s of each serverId for the specified
   * replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return a {serverId => newest CSN} Map. If a replica DB is empty or closed,
   *         the newest CSN will be null for that replica.
   */
  Map<Integer, CSN> getDomainNewestCSNs(DN baseDN);

  /**
   * Retrieves the latest trim date for the specified replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @return the domain latest trim date
   */
  long getDomainLatestTrimDate(DN baseDN);

  /**
   * Shutdown the specified replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   */
  void shutdownDomain(DN baseDN);

  /**
   * Clear DB and shutdown for the specified replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   */
  void clearDomain(DN baseDN);

  // serverId methods

  /**
   * Return the number of changes between 2 provided {@link CSN}s for the
   * specified serverId and replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param serverId
   *          the serverId on which to act
   * @param from
   *          The lower (older) CSN
   * @param to
   *          The upper (newer) CSN
   * @return The computed number of changes
   */
  long getCount(DN baseDN, int serverId, CSN from, CSN to);

  /**
   * Returns the {@link CSN} situated immediately after the specified
   * {@link CSN} for the specified serverId and replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param serverId
   *          the serverId for which we want the information
   * @param startAfterCSN
   *          The position where the iterator must start
   * @return a new ReplicationIterator that allows to browse the db managed by
   *         this dbHandler and starting at the position defined by a given CSN.
   */
  CSN getCSNAfter(DN baseDN, int serverId, CSN startAfterCSN);

  /**
   * Generates a non empty {@link ReplicaDBCursor} for the specified serverId
   * and replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param serverId
   *          the serverId on which to act
   * @param startAfterCSN
   *          The position where the iterator must start
   * @return a {@link ReplicaDBCursor} if the ReplicaDB is not empty, null
   *         otherwise
   */
  ReplicaDBCursor getCursorFrom(DN baseDN, int serverId, CSN startAfterCSN);

  /**
   * for the specified serverId and replication domain.
   *
   * @param baseDN
   *          the replication domain baseDN
   * @param serverId
   *          the serverId on which to act
   * @param updateMsg
   *          the update message to publish to the replicaDB
   * @return true if a db had to be created to publish this message
   * @throws ChangelogException
   *           If a database problem happened
   */
  boolean publishUpdateMsg(DN baseDN, int serverId, UpdateMsg updateMsg)
      throws ChangelogException;

}
