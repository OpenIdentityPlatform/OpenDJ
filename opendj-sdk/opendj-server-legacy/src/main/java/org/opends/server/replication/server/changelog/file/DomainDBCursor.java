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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.types.DN;

/**
 * Cursor iterating over a replication domain's replica DBs.
 *
 * \@NotThreadSafe
 */
public class DomainDBCursor extends CompositeDBCursor<Void>
{

  private final DN baseDN;
  private final ReplicationDomainDB domainDB;

  private final ConcurrentSkipListMap<Integer, CSN> newReplicas = new ConcurrentSkipListMap<Integer, CSN>();
  /**
   * Replaces null CSNs in ConcurrentSkipListMap that does not support null values.
   */
  private static final CSN NULL_CSN = new CSN(0, 0, 0);

  private final PositionStrategy positionStrategy;
  private final KeyMatchingStrategy matchingStrategy;

  /**
   * Builds a DomainDBCursor instance.
   *
   * @param baseDN
   *          the replication domain baseDN of this cursor
   * @param domainDB
   *          the DB for the provided replication domain
   * @param matchingStrategy
   *          Cursor key matching strategy, which allow to indicates how key is
   *          matched
   * @param positionStrategy
   *          Cursor position strategy, which allow to indicates at which exact
   *          position the cursor must start
   */
  public DomainDBCursor(final DN baseDN, final ReplicationDomainDB domainDB, final KeyMatchingStrategy matchingStrategy,
      final PositionStrategy positionStrategy)
  {
    this.baseDN = baseDN;
    this.domainDB = domainDB;
    this.matchingStrategy = matchingStrategy;
    this.positionStrategy = positionStrategy;
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
      final DBCursor<UpdateMsg> cursor =
          domainDB.getCursorFrom(baseDN, serverId, startCSN, matchingStrategy, positionStrategy);
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
