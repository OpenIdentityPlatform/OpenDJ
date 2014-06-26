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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;

/**
 * Implementation of a DBCursor that decorates an existing DBCursor
 * and returns a ReplicaOfflineMsg when the decorated DBCursor is exhausted
 * and the offline CSN is newer than the last returned update CSN.
 */
public class ReplicaOfflineCursor implements DBCursor<UpdateMsg>
{
  /** @NonNull */
  private final DBCursor<UpdateMsg> cursor;
  private ReplicaOfflineMsg replicaOfflineMsg;
  /**
   * Whether calls to {@link #getRecord()} must return the {@link ReplicaOfflineMsg}
   */
  private boolean returnReplicaOfflineMsg;

  /**
   * Creates a ReplicaOfflineCursor object with a cursor to decorate
   * and an offlineCSN to return as part of a ReplicaOfflineMsg.
   *
   * @param cursor
   *          the non-null underlying cursor that needs to be exhausted before
   *          we return a ReplicaOfflineMsg
   * @param offlineCSN
   *          The offline CSN from which to builder the
   *          {@link ReplicaOfflineMsg} to return
   */
  public ReplicaOfflineCursor(DBCursor<UpdateMsg> cursor, CSN offlineCSN)
  {
    this.replicaOfflineMsg =
        offlineCSN != null ? new ReplicaOfflineMsg(offlineCSN) : null;
    this.cursor = cursor;
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return returnReplicaOfflineMsg ? replicaOfflineMsg : cursor.getRecord();
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    if (returnReplicaOfflineMsg)
    {
      // already consumed, never return it again...
      replicaOfflineMsg = null;
      returnReplicaOfflineMsg = false;
      // ...and verify if new changes have been added to the DB
      // (cursors are automatically restarted)
    }
    final UpdateMsg lastUpdate = cursor.getRecord();
    final boolean hasNext = cursor.next();
    if (hasNext)
    {
      return true;
    }
    if (replicaOfflineMsg == null)
    { // no ReplicaOfflineMsg to return
      return false;
    }

    // replicaDB just happened to be exhausted now
    if (lastUpdate != null
        && replicaOfflineMsg.getCSN().isOlderThanOrEqualTo(lastUpdate.getCSN()))
    {
      // offlineCSN is outdated, never return it
      replicaOfflineMsg = null;
      return false;
    }
    returnReplicaOfflineMsg = true;
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    cursor.close();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + " returnReplicaOfflineMsg=" + returnReplicaOfflineMsg
        + " offlineCSN="
        + (replicaOfflineMsg != null ? replicaOfflineMsg.getCSN().toStringUI() : null)
        + " cursor=" + cursor.toString().split("", 2)[1];
  }

}