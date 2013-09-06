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

import org.opends.server.config.ConfigException;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;

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
   * Set the directory to be used by the replication database.
   *
   * @param dbDirName
   *          the directory for use by the replication database
   * @throws ConfigException
   *           if a problem occurs opening the directory
   */
  void setReplicationDBDirectory(String dbDirName) throws ConfigException;

  /**
   * Get the replication server database directory. This is used by tests to do
   * some cleanup.
   *
   * @return the database directory name
   */
  String getDBDirName();

  /**
   * Initializes the replication database.
   */
  void initializeDB();

  /**
   * Sets the purge delay for the replication database. This purge delay is a
   * best effort.
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
   * @param baseDn
   *          the replication domain baseDn
   * @return a set of integers holding the serverIds
   */
  Set<Integer> getDomainServerIds(String baseDn);

  /**
   * Get the number of changes for the specified replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @return the number of changes.
   */
  long getDomainChangesCount(String baseDn);

  /**
   * Returns the FIRST {@link CSN}s of each serverId for the specified
   * replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @return a {serverId => FIRST CSN} Map
   */
  Map<Integer, CSN> getDomainFirstCSNs(String baseDn);

  /**
   * Returns the LAST {@link CSN}s of each serverId for the specified
   * replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @return a {serverId => LAST CSN} Map
   */
  Map<Integer, CSN> getDomainLastCSNs(String baseDn);

  /**
   * Retrieves the latest trim date for the specified replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @return the domain latest trim date
   */
  long getDomainLatestTrimDate(String baseDn);

  /**
   * Shutdown the specified replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   */
  void shutdownDomain(String baseDn);

  /**
   * Clear DB and shutdown for the specified replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   */
  void clearDomain(String baseDn);

  // serverId methods

  /**
   * Return the number of changes between 2 provided {@link CSN}s for the
   * specified serverId and replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @param serverId
   *          the serverId on which to act
   * @param from
   *          The lower (older) CSN
   * @param to
   *          The upper (newer) CSN
   * @return The computed number of changes
   */
  long getCount(String baseDn, int serverId, CSN from, CSN to);

  /**
   * Returns the {@link CSN} situated immediately after the specified
   * {@link CSN} for the specified serverId and replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @param serverId
   *          the serverId for which we want the information
   * @param startAfterCSN
   *          The position where the iterator must start
   * @return a new ReplicationIterator that allows to browse the db managed by
   *         this dbHandler and starting at the position defined by a given CSN.
   */
  CSN getCSNAfter(String baseDn, int serverId, CSN startAfterCSN);

  /**
   * Generates a non empty {@link ReplicaDBCursor} for the specified serverId
   * and replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @param serverId
   *          the serverId on which to act
   * @param startAfterCSN
   *          The position where the iterator must start
   * @return a {@link ReplicaDBCursor} if the ReplicaDB is not empty, null
   *         otherwise
   */
  ReplicaDBCursor getCursorFrom(String baseDn, int serverId, CSN startAfterCSN);

  /**
   * for the specified serverId and replication domain.
   *
   * @param baseDn
   *          the replication domain baseDn
   * @param serverId
   *          the serverId on which to act
   * @param updateMsg
   *          the update message to publish to the replicaDB
   * @return true if a db had to be created to publish this message
   * @throws ChangelogException
   *           If a database problem happened
   */
  boolean publishUpdateMsg(String baseDn, int serverId, UpdateMsg updateMsg)
      throws ChangelogException;

}
