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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;

class FakeEntryIndex
{
  private AttributeType attrType;
  private MatchingRule matchingRule;
  private Indexer indexer;
  private final NavigableMap<ByteString, Set<Entry>> index = new TreeMap<>();

  FakeEntryIndex(String attrName) throws DecodeException
  {
    attrType = DirectoryServer.getSchema().getAttributeType(attrName);
    if (attrType == null)
    {
      throw new IllegalArgumentException("Cannot find attribute with name \"" + attrName + "\"");
    }
    matchingRule = attrType.getEqualityMatchingRule();
    IndexingOptions options = mock(IndexingOptions.class);
    indexer = matchingRule.createIndexers(options).iterator().next();
  }

  void addAll(List<Entry> entries) throws DecodeException
  {
    for (Entry entry : entries)
    {
      add(entry);
    }
  }

  void add(Entry entry) throws DecodeException
  {
    Attribute attribute = entry.getExactAttribute(AttributeDescription.create(attrType));
    for (ByteString key : index(attribute))
    {
      Set<Entry> entries = index.get(key);
      if (entries == null)
      {
        entries = new HashSet<>();
        index.put(key, entries);
      }
      entries.add(entry);
    }
  }

  private Collection<ByteString> index(Attribute attribute) throws DecodeException
  {
    if (attribute == null)
    {
      return Collections.emptySet();
    }
    Collection<ByteString> keys = new TreeSet<>();
    for (ByteString attrValue : attribute)
    {
      indexer.createKeys(Schema.getDefaultSchema(), attrValue, keys);
    }
    return keys;
  }

  public Set<Entry> evaluateFilter(String filterString) throws DirectoryException, DecodeException
  {
    SearchFilter filter = SearchFilter.createFilterFromString(filterString);
    if (!attrType.equals(filter.getAttributeType()))
    {
      throw new IllegalArgumentException("The search filter \"" + filterString
          + "\" should target the same attribute type as this index \"" + attrType.getNameOrOID() + "\"");
    }
    Assertion assertion = getAssertion(filter);
    return assertion.createIndexQuery(new FakeIndexQueryFactory<Entry>(index));
  }

  private Assertion getAssertion(SearchFilter filter) throws DecodeException
  {
    switch (filter.getFilterType())
    {
    case EQUALITY:
      return matchingRule.getAssertion(filter.getAssertionValue());

    case LESS_OR_EQUAL:
      return matchingRule.getLessOrEqualAssertion(filter.getAssertionValue());

    case GREATER_OR_EQUAL:
      return matchingRule.getGreaterOrEqualAssertion(filter.getAssertionValue());

    case EXTENSIBLE_MATCH:
      MatchingRule rule = DirectoryServer.getSchema().getMatchingRule(filter.getMatchingRuleID());
      return rule.getAssertion(filter.getAssertionValue());

    default:
      throw new RuntimeException("Not implemented for search filter type " + filter.getFilterType());
    }
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<ByteString, Set<Entry>> mapEntry : index.entrySet())
    {
      String key = mapEntry.getKey().toHexString();
      Set<Entry> value = mapEntry.getValue();
      Iterator<Entry> it = value.iterator();
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

  private String firstLine(Entry entry)
  {
    return entry.toString().split("\\n")[0] + " ...";
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
