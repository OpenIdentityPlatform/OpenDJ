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

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;

import net.jcip.annotations.NotThreadSafe;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.forgerock.opendj.ldap.DN;

/** Cursor iterating over a replication domain's replica DBs. */
@NotThreadSafe
public class DomainDBCursor extends CompositeDBCursor<Void>
{
  /** Replaces null CSNs in ConcurrentSkipListMap that does not support null values. */
  private static final CSN NULL_CSN = new CSN(0, 0, 0);

  private final DN baseDN;
  private final ReplicationDomainDB domainDB;
  private final ConcurrentSkipListMap<Integer, CSN> newReplicas = new ConcurrentSkipListMap<>();
  private final CursorOptions options;

  /**
   * Builds a DomainDBCursor instance.
   *
   * @param baseDN
   *          the replication domain baseDN of this cursor
   * @param domainDB
   *          the DB for the provided replication domain
   * @param options The cursor options
   */
  public DomainDBCursor(final DN baseDN, final ReplicationDomainDB domainDB, CursorOptions options)
  {
    this.baseDN = baseDN;
    this.domainDB = domainDB;
    this.options = options;
  }

  /**
   * Returns the replication domain baseDN of this cursor.
   *
   * @return the replication domain baseDN of this cursor.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Adds a replicaDB for this cursor to iterate over. Added cursors will be
   * created and iterated over on the next call to {@link #next()}.
   *
   * @param serverId
   *          the serverId of the replica
   * @param startCSN
   *          the CSN to use as a starting point
   */
  public void addReplicaDB(int serverId, CSN startCSN)
  {
    // only keep the oldest CSN that will be the new cursor's starting point
    newReplicas.putIfAbsent(serverId, startCSN != null ? startCSN : NULL_CSN);
  }

  /** {@inheritDoc} */
  @Override
  protected void incorporateNewCursors() throws ChangelogException
  {
    for (Iterator<Entry<Integer, CSN>> iter = newReplicas.entrySet().iterator(); iter.hasNext();)
    {
      final Entry<Integer, CSN> pair = iter.next();
      final int serverId = pair.getKey();
      final CSN csn = pair.getValue();
      final CSN startCSN = !NULL_CSN.equals(csn) ? csn : null;
      final DBCursor<UpdateMsg> cursor = domainDB.getCursorFrom(baseDN, serverId, startCSN, options);
      addCursor(cursor, null);
      iter.remove();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    super.close();
    domainDB.unregisterCursor(this);
    newReplicas.clear();
  }

}
