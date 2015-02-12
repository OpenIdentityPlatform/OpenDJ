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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;



/**
 * This interface is the entry point for the changelog database which stores the
 * replication data on persistent storage. It allows to control the overall
 * database or access more specialized interfaces.
 */
public interface ChangelogDB
{

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
   * Sets whether the replication database must compute change numbers for
   * replicated changes. Change numbers are computed using a separate new
   * thread.
   *
   * @param computeChangeNumber
   *          whether to compute change numbers for replicated changes
   * @throws ChangelogException
   *           If a database problem happened
   */
  void setComputeChangeNumber(boolean computeChangeNumber)
      throws ChangelogException;

  /**
   * Shutdown the replication database.
   *
   * @throws ChangelogException
   *           If a database problem happened
   */
  void shutdownDB() throws ChangelogException;

  /**
   * Removes the changelog database directory.
   *
   * @throws ChangelogException
   *           If a database problem happened
   */
  void removeDB() throws ChangelogException;

  /**
   * Returns the {@link ChangeNumberIndexDB} object.
   *
   * @return the {@link ChangeNumberIndexDB} object
   */
  ChangeNumberIndexDB getChangeNumberIndexDB();

  /**
   * Returns the {@link ReplicationDomainDB} object.
   *
   * @return the {@link ReplicationDomainDB} object
   */
  ReplicationDomainDB getReplicationDomainDB();
}
