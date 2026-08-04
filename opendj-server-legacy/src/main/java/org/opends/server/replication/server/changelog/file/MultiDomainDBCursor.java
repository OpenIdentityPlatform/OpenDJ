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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server.changelog.file;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import net.jcip.annotations.NotThreadSafe;

import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.forgerock.opendj.ldap.DN;

/** Cursor iterating over a all the replication domain known to the changelog DB. */
@NotThreadSafe
public class MultiDomainDBCursor extends CompositeDBCursor<DN>
{
  private final ReplicationDomainDB domainDB;
  private final ConcurrentSkipListMap<DN, ServerState> newDomains = new ConcurrentSkipListMap<>();
  /**
   * The domains this cursor already iterates over, so that announcing a domain a second time is a
   * no-op: a second cursor over the same domain would either leak unclosed - the cursor tree of
   * {@link CompositeDBCursor} collapses cursors comparing equal - or deliver every change twice.
   * A domain may be announced again after the empty domainMap of a failed replica DB creation was
   * dropped, while this cursor still iterates over it.
   * <p>
   * Only ever mutated from the thread iterating this cursor - {@link #incorporateNewCursors()},
   * {@link #removeDomain(DN)} and {@link #close()} all run on it - and read by the threads
   * announcing domains.
   */
  private final ConcurrentSkipListSet<DN> incorporatedDomains = new ConcurrentSkipListSet<>();
  private final CursorOptions options;

  /**
   * Builds a MultiDomainDBCursor instance.
   *
   * @param domainDB
   *          the replication domain management DB
   * @param options The cursor options
   */
  public MultiDomainDBCursor(final ReplicationDomainDB domainDB, CursorOptions options)
  {
    this.domainDB = domainDB;
    this.options = options;
  }

  /**
   * Adds a replication domain for this cursor to iterate over. Added cursors
   * will be created and iterated over on the next call to {@link #next()}.
   * <p>
   * A no-op for a domain this cursor already iterates over: new replica DBs of such a domain
   * reach it through {@link DomainDBCursor#addReplicaDB(int, org.opends.server.replication.common.CSN)}.
   *
   * @param baseDN
   *          the replication domain's baseDN
   * @param startAfterState
   *          the {@link ServerState} after which to start iterating
   */
  public void addDomain(DN baseDN, ServerState startAfterState)
  {
    if (!incorporatedDomains.contains(baseDN))
    {
      newDomains.put(baseDN, startAfterState != null ? startAfterState : new ServerState());
    }
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
      // the check in addDomain() is only a fast path: an announcement racing an incorporation of
      // the same domain can still queue it a second time, so incorporation itself must ignore
      // domains this cursor already iterates over
      if (!incorporatedDomains.contains(baseDN))
      {
        final DBCursor<UpdateMsg> domainDBCursor = domainDB.getCursorFrom(baseDN, serverState, options);
        addCursor(domainDBCursor, baseDN);
        incorporatedDomains.add(baseDN);
      }
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
    removeCursor(baseDN);
    incorporatedDomains.remove(baseDN);
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    super.close();
    domainDB.unregisterCursor(this);
    newDomains.clear();
    incorporatedDomains.clear();
  }

}
