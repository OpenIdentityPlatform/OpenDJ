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

import java.util.ArrayList;
import java.util.List;

import org.forgerock.util.Pair;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.DN;

/**
 * Multi domain DB cursor that only returns updates for the domains which have
 * been enabled for the external changelog.
 */
public final class ECLMultiDomainDBCursor implements DBCursor<UpdateMsg>
{

  private final ECLEnabledDomainPredicate predicate;
  private final MultiDomainDBCursor cursor;

  /**
   * Builds an instance of this class filtering updates from the provided cursor.
   *
   * @param predicate
   *          tells whether a domain is enabled for the external changelog
   * @param cursor
   *          the cursor whose updates will be filtered
   */
  public ECLMultiDomainDBCursor(ECLEnabledDomainPredicate predicate, MultiDomainDBCursor cursor)
  {
    this.predicate = predicate;
    this.cursor = cursor;
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return cursor.getRecord();
  }

  /**
   * Returns the data associated to the cursor that returned the current record.
   *
   * @return the data associated to the cursor that returned the current record.
   */
  public DN getData()
  {
    return cursor.getData();
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
    cursor.removeDomain(baseDN);
  }

  /** {@inheritDoc} */
  @Override
  public boolean next() throws ChangelogException
  {
    // discard updates from non ECL enabled domains
    boolean hasNext;
    do
    {
      hasNext = cursor.next();
    }
    while (hasNext && !predicate.isECLEnabledDomain(cursor.getData()));
    return hasNext;
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
    return getClass().getSimpleName() + " cursor=[" + cursor + ']';
  }

  /**
   * Returns a snapshot of this cursor.
   *
   * @return a list of (DN, UpdateMsg) pairs, containing all base DNs enabled
   *         for the external changelog. The update message may be {@code null}.
   */
  List<Pair<DN, UpdateMsg>> getSnapshot()
  {
    final List<Pair<DN, UpdateMsg>> snapshot = cursor.getSnapshot();
    final List<Pair<DN, UpdateMsg>> eclSnapshot = new ArrayList<Pair<DN,UpdateMsg>>();
    for (Pair<DN, UpdateMsg> pair : snapshot)
    {
      DN baseDN = pair.getFirst();
      if (predicate.isECLEnabledDomain(baseDN))
      {
        eclSnapshot.add(pair);
      }
    }
    return eclSnapshot;
  }

  /**
   * Returns the cookie corresponding to the state of this cursor.
   *
   * @return a valid cookie taking into account only the base DNs enabled for
   *         the external changelog
   */
  public MultiDomainServerState toCookie()
  {
    List<Pair<DN, UpdateMsg>> snapshot = getSnapshot();
    MultiDomainServerState cookie = new MultiDomainServerState();
    for (Pair<DN, UpdateMsg> pair : snapshot)
    {
      // only put base DNs where a CSN is available in the cookie
      if (pair.getSecond() != null)
      {
        cookie.update(pair.getFirst(), pair.getSecond().getCSN());
      }
    }
    return cookie;
  }
}
