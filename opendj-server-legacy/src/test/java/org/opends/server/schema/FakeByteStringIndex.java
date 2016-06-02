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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilterType;

class FakeByteStringIndex
{
  private MatchingRule matchingRule;
  private Indexer indexer;
  private final NavigableMap<ByteString, Set<ByteString>> index = new TreeMap<>();

  FakeByteStringIndex(String mrName) throws DecodeException
  {
    matchingRule = DirectoryServer.getSchema().getMatchingRule(mrName);
    IndexingOptions options = mock(IndexingOptions.class);
    indexer = matchingRule.createIndexers(options).iterator().next();
  }

  void addAll(List<ByteString> attrValues) throws DecodeException
  {
    for (ByteString attrValue : attrValues)
    {
      add(attrValue);
    }
  }

  void add(ByteString attrValue) throws DecodeException
  {
    for (ByteString key : index(attrValue))
    {
      Set<ByteString> entries = index.get(key);
      if (entries == null)
      {
        entries = new HashSet<>();
        index.put(key, entries);
      }
      entries.add(attrValue);
    }
  }

  private Collection<ByteString> index(ByteString attrValue) throws DecodeException
  {
    Collection<ByteString> keys = new TreeSet<>();
    indexer.createKeys(Schema.getDefaultSchema(), attrValue, keys);
    return keys;
  }

  public Set<ByteString> evaluateAssertionValue(ByteString assertionValue, FilterType filterType)
      throws DirectoryException, DecodeException
  {
    Assertion assertion = getAssertion(assertionValue, filterType);
    return assertion.createIndexQuery(new FakeIndexQueryFactory<ByteString>(index));
  }

  private Assertion getAssertion(ByteString assertionValue, FilterType filterType) throws DecodeException
  {
    switch (filterType)
    {
    case EQUALITY:
    case EXTENSIBLE_MATCH:
      return matchingRule.getAssertion(assertionValue);

    case LESS_OR_EQUAL:
      return matchingRule.getLessOrEqualAssertion(assertionValue);

    case GREATER_OR_EQUAL:
      return matchingRule.getGreaterOrEqualAssertion(assertionValue);

    default:
      throw new RuntimeException("Not implemented for filter type " + filterType);
    }
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<ByteString, Set<ByteString>> mapEntry : index.entrySet())
    {
      String key = mapEntry.getKey().toHexString();
      Set<ByteString> value = mapEntry.getValue();
      Iterator<ByteString> it = value.iterator();
      if (!it.hasNext())
      {
        continue;
      }
      sb.append(key).append("\t").append(firstLine(it.next())).append("\n");
      while (it.hasNext())
      {
        sb.append(emptyString(key.length())).append("\t").append(firstLine(it.next())).append("\n");
      }
    }
    return sb.toString();
  }

  private String firstLine(ByteString attrValue)
  {
    return attrValue.toString().split("\\n")[0] + " ...";
  }

  private String emptyString(int length)
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++)
    {
      sb.append(" ");
    }
    return sb.toString();
  }
}
