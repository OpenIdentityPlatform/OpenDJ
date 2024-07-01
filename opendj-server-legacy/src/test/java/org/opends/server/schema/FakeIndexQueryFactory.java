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
 * Copyright 2015 ForgeRock AS.
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
