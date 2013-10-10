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


/**
 * This class stores an index of all the changes seen by this server in the form
 * of {@link CNIndexRecord}s. The records are sorted by a global ordering as
 * defined in the CSN class. The index links a <code>changeNumber</code> to the
 * corresponding CSN. The CSN then links to a corresponding change in one of the
 * ReplicaDBs.
 *
 * @see <a href=
 * "https://wikis.forgerock.org/confluence/display/OPENDJ/OpenDJ+Domain+Names"
 * >OpenDJ Domain Names</a> for more information about the changelog.
 * @see <a href= "http://tools.ietf.org/html/draft-good-ldap-changelog-04"
 * >OpenDJ Domain Names</a> for more information about the changeNumber.
 */
public interface ChangeNumberIndexDB
{

  /**
   * Returns the last generated change number.
   *
   * @return the last generated change number
   */
  long getLastGeneratedChangeNumber();

  /**
   * Get the record associated to a provided change number.
   *
   * @param changeNumber
   *          the provided change number.
   * @return the {@link CNIndexRecord}, null when none.
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  CNIndexRecord getRecord(long changeNumber) throws ChangelogException;

  /**
   * Get the oldest record stored in this DB.
   *
   * @return Returns the oldest {@link CNIndexRecord} in this DB, null when the
   *         DB is empty or closed
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  CNIndexRecord getOldestRecord() throws ChangelogException;

  /**
   * Get the newest record stored in this DB.
   *
   * @return Returns the newest {@link CNIndexRecord} in this DB, null when the
   *         DB is empty or closed
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  CNIndexRecord getNewestRecord() throws ChangelogException;

  /**
   * Add an update to the list of messages that must be saved to this DB managed
   * by this DB and return the changeNumber associated to this record.
   * <p>
   * Note: this method disregards the changeNumber in the provided record.
   * <p>
   * FIXME will be removed when ECLServerHandler will not be responsible anymore
   * for lazily building the ChangeNumberIndexDB.
   *
   * @param record
   *          The {@link CNIndexRecord} to add to this DB.
   * @return the change number associated to this record on adding to this DB
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  long addRecord(CNIndexRecord record) throws ChangelogException;

  /**
   * Generate a new {@link DBCursor} that allows to browse the db managed by
   * this object and starting at the position defined by a given changeNumber.
   *
   * @param startChangeNumber
   *          The position where the iterator must start.
   * @return a new ReplicationIterator that allows to browse this DB managed by
   *         this object and starting at the position defined by a given
   *         changeNumber.
   * @throws ChangelogException
   *           if a database problem occurs.
   */
  DBCursor<CNIndexRecord> getCursorFrom(long startChangeNumber)
      throws ChangelogException;

}
