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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

/**
 * {@link IndexQueryFactory} implementation which evaluates queries instead of creating them.
 * The queries are evaluated against a provided {@code NavigableMap<ByteString, Set>} (SetMultimap)
 * which acts as an index.
 *
 * @param <T> type of the values
 */
final class FakeIndexQueryFactory<T> implements IndexQueryFactory<Set<T>>
{
  private final NavigableMap<ByteString, Set<T>> index;

  FakeIndexQueryFactory(NavigableMap<ByteString, Set<T>> index)
  {
    this.index = index;
  }

  @Override
  public Set<T> createExactMatchQuery(String indexID, ByteSequence key)
  {
    Set<T> results = index.get(key);
    return results != null ? new HashSet<>(results) : Collections.<T> emptySet();
  }

  @Override
  public Set<T> createMatchAllQuery()
  {
    return flatten(index.values());
  }

  @Override
  public Set<T> createRangeMatchQuery(String indexID, ByteSequence lower, ByteSequence upper,
      boolean lowerIncluded, boolean upperIncluded)
  {
    NavigableMap<ByteString, Set<T>> map = index;
    if (lower.length() > 0)
    {
      map = map.tailMap(lower.toByteString(), lowerIncluded);
    }
    if (upper.length() > 0)
    {
      map = map.headMap(upper.toByteString(), upperIncluded);
    }
    return flatten(map.values());
  }

  private Set<T> flatten(Collection<? extends Collection<T>> values)
  {
    Set<T> results = new HashSet<>();
    for (Collection<T> entries : values)
    {
      results.addAll(entries);
    }
    return results;
  }

  @Override
  public Set<T> createIntersectionQuery(Collection<Set<T>> subResults)
  {
    Iterator<Set<T>> it = subResults.iterator();
    if (!it.hasNext())
    {
      return Collections.emptySet();
    }
    Set<T> results = new HashSet<>(it.next());
    while (it.hasNext())
    {
      results.retainAll(it.next());
    }
    return results;
  }

  @Override
  public Set<T> createUnionQuery(Collection<Set<T>> subResults)
  {
    Set<T> results = new HashSet<>();
    for (Collection<T> entries : subResults)
    {
      results.addAll(entries);
    }
    return results;
  }

  @Override
  public IndexingOptions getIndexingOptions()
  {
    return null;
  }
}