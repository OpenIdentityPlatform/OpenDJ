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

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.types.DN;

/**
 * Cursor iterating over a all the replication domain known to the changelog DB.
 *
 * \@NotThreadSafe
 */
public class MultiDomainDBCursor extends CompositeDBCursor<DN>
{
  private final ReplicationDomainDB domainDB;

  private final ConcurrentSkipListMap<DN, ServerState> newDomains =
      new ConcurrentSkipListMap<DN, ServerState>();
  private final ConcurrentSkipListSet<DN> removeDomains =
      new ConcurrentSkipListSet<DN>();

  private final PositionStrategy positionStrategy;

  /**
   * Builds a MultiDomainDBCursor instance.
   *
   * @param domainDB
   *          the replication domain management DB
   * @param positionStrategy
   *          Cursor position strategy, which allow to indicates at which
   *          exact position the cursor must start
   */
  public MultiDomainDBCursor(ReplicationDomainDB domainDB, PositionStrategy positionStrategy)
  {
    this.domainDB = domainDB;
    this.positionStrategy = positionStrategy;
  }

  /**
   * Adds a replication domain for this cursor to iterate over. Added cursors
   * will be created and iterated over on the next call to {@link #next()}.
   *
   * @param baseDN
   *          the replication domain's baseDN
   * @param startAfterState
   *          the {@link ServerState} after which to start iterating
   */
  public void addDomain(DN baseDN, ServerState startAfterState)
  {
    newDomains.put(baseDN,
        startAfterState != null ? startAfterState : new ServerState());
  }

  /** {@inheritDoc} */
  @Override
  protected void incorporateNewCursors() throws ChangelogException
  {
    for (Iterator<Entry<DN, ServerState>> iter = newDomains.entrySet().iterator();
         iter.hasNext();)
    {
      final Entry<DN, ServerState> entry = iter.next();
      final DN baseDN = entry.getKey();
      final ServerState serverState = entry.getValue();
      final DBCursor<UpdateMsg> domainDBCursor = domainDB.getCursorFrom(baseDN, serverState, positionStrategy);
      addCursor(domainDBCursor, baseDN);
      iter.remove();
    }
  }

  /**
   * Removes a replication domain from this cursor and stops iterating over it.
   * Removed cursors will be effectively removed on the next call to
   * {@link #next()}.
   *
   * @param baseDN
   *          the replication domain's baseDN
   */
  public void removeDomain(DN baseDN)
  {
    removeDomains.add(baseDN);
  }

  /** {@inheritDoc} */
  @Override
  protected Iterator<DN> removedCursorsIterator()
  {
    return removeDomains.iterator();
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    super.close();
    domainDB.unregisterCursor(this);
    newDomains.clear();
    removeDomains.clear();
  }

}
