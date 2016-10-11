/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import java.util.concurrent.atomic.AtomicReference;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.ReplicaId;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;

/**
 * {@link DBCursor} over a replica returning {@link UpdateMsg}s.
 * <p>
 * It decorates an existing {@link DBCursor} on a replicaDB and can possibly
 * return replica offline messages when the decorated DBCursor is exhausted and
 * the offline CSN is newer than the last returned update CSN.
 */
public class ReplicaCursor implements DBCursor<UpdateMsg>
{
  /** @NonNull */
  private final DBCursor<UpdateMsg> cursor;
  private final AtomicReference<ReplicaOfflineMsg> replicaOfflineMsg = new AtomicReference<>();
  private UpdateMsg currentRecord;

  private final ReplicaId replicaId;
  private final ReplicationDomainDB domainDB;

  /**
   * Creates a ReplicaCursor object with a cursor to decorate
   * and an offlineCSN to return as part of a ReplicaOfflineMsg.
   *
   * @param cursor
   *          the non-null underlying cursor that needs to be exhausted before
   *          we return a ReplicaOfflineMsg
   * @param offlineCSN
   *          the offline CSN from which to builder the
   *          {@link ReplicaOfflineMsg} to return
   * @param replicaId
   *          the replica identifier
   * @param domainDB
   *          the DB for the provided replication domain
   */
  public ReplicaCursor(DBCursor<UpdateMsg> cursor, CSN offlineCSN, ReplicaId replicaId, ReplicationDomainDB domainDB)
  {
    this.cursor = cursor;
    this.replicaId = replicaId;
    this.domainDB = domainDB;
    setOfflineCSN(offlineCSN);
  }

  /**
   * Sets the offline CSN to be returned by this cursor.
   *
   * @param offlineCSN
   *          The offline CSN to be returned by this cursor.
   *          If null, it will unset any previous offlineCSN and never return a ReplicaOfflineMsg
   */
  public void setOfflineCSN(CSN offlineCSN)
  {
    if (offlineCSN != null)
    {
      ReplicaOfflineMsg prevOfflineMsg = this.replicaOfflineMsg.get();
      if (prevOfflineMsg == null || prevOfflineMsg.getCSN().isOlderThan(offlineCSN))
      {
        // Do not spin if the the message for this replica has been changed. Either a newer
        // message has arrived or the next cursor iteration will pick it up.
        this.replicaOfflineMsg.compareAndSet(prevOfflineMsg, new ReplicaOfflineMsg(offlineCSN));
      }
    }
    else
    {
      this.replicaOfflineMsg.set(null);
    }
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return currentRecord;
  }

  /**
   * Returns the replica identifier that this cursor is associated to.
   *
   * @return the replica identifier that this cursor is associated to
   */
  public ReplicaId getReplicaId()
  {
    return replicaId;
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    final ReplicaOfflineMsg offlineMsg1 = replicaOfflineMsg.get();
    if (isReplicaOfflineMsgOutdated(offlineMsg1, currentRecord))
    {
      replicaOfflineMsg.compareAndSet(offlineMsg1, null);
    }

    // now verify if new changes have been added to the DB
    // (cursors are automatically restarted)
    final UpdateMsg lastUpdate = cursor.getRecord();
    final boolean hasNext = cursor.next();
    if (hasNext)
    {
      currentRecord = cursor.getRecord();
      return true;
    }

    // replicaDB just happened to be exhausted now
    final ReplicaOfflineMsg offlineMsg2 = replicaOfflineMsg.get();
    if (isReplicaOfflineMsgOutdated(offlineMsg2, lastUpdate))
    {
      replicaOfflineMsg.compareAndSet(offlineMsg2, null);
      currentRecord = null;
      return false;
    }
    currentRecord = offlineMsg2;
    return currentRecord != null;
  }

  /** It could also mean that the replica offline message has already been consumed. */
  private boolean isReplicaOfflineMsgOutdated(
      final ReplicaOfflineMsg offlineMsg, final UpdateMsg updateMsg)
  {
    return offlineMsg != null
        && updateMsg != null
        && offlineMsg.getCSN().isOlderThanOrEqualTo(updateMsg.getCSN());
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    cursor.close();
    domainDB.unregisterCursor(this);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    final ReplicaOfflineMsg msg = replicaOfflineMsg.get();
    return getClass().getSimpleName()
        + " currentRecord=" + currentRecord
        + " offlineCSN=" + (msg != null ? msg.getCSN().toStringUI() : null)
        + " cursor=" + cursor.toString().split("", 2)[1];
  }

}
