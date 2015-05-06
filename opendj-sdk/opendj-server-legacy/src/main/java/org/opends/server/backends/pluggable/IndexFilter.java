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
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.opends.server.backends.pluggable.AttributeIndex.IndexFilterType;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AttributeType;
import org.opends.server.types.FilterType;
import org.opends.server.types.SearchFilter;

/**
 * An index filter is used to apply a search operation to a set of indexes
 * to generate a set of candidate entries.
 */
class IndexFilter
{
  /**
   * Stop processing the filter against the indexes when the
   * number of candidates is smaller than this value.
   */
  private static final int FILTER_CANDIDATE_THRESHOLD = 10;

  /**
   * Limit on the number of entry IDs that may be retrieved by cursoring through an index.
   */
  static final int CURSOR_ENTRY_LIMIT = 100000;

  /** The entry container holding the attribute indexes. */
  private final EntryContainer entryContainer;
  private final ReadableTransaction txn;

  /**
   * The search operation provides the search base, scope and filter.
   * It can also be checked periodically for cancellation.
   */
  private final SearchOperation searchOp;

  /**
   * A string builder to hold a diagnostic string which helps determine
   * how the indexed contributed to the search operation.
   */
  private final StringBuilder buffer;
  private final BackendMonitor monitor;

  /**
   * Construct an index filter for a search operation.
   *
   * @param entryContainer The entry entryContainer.
   * @param txn a non null transaction
   * @param searchOp       The search operation to be evaluated.
   * @param monitor        The monitor to gather filter usage stats.
   * @param debugBuilder If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   */
  IndexFilter(EntryContainer entryContainer, ReadableTransaction txn, SearchOperation searchOp,
      StringBuilder debugBuilder, BackendMonitor monitor)
  {
    this.entryContainer = entryContainer;
    this.txn = txn;
    this.searchOp = searchOp;
    this.buffer = debugBuilder;
    this.monitor = monitor;
  }

  /**
   * Evaluate the search operation against the indexes.
   *
   * @return A set of entry IDs representing candidate entries.
   */
  EntryIDSet evaluate()
  {
    appendToDebugBuffer("filter=");
    return evaluateFilter(searchOp.getFilter());
  }

  /**
   * Evaluate a search filter against the indexes.
   *
   * @param filter The search filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateFilter(SearchFilter filter)
  {
    EntryIDSet candidates = evaluateFilter0(filter);
    if (buffer != null)
    {
      candidates.toString(buffer);
    }
    return candidates;
  }

  private EntryIDSet evaluateFilter0(SearchFilter filter)
  {
    switch (filter.getFilterType())
    {
      case AND:
        appendToDebugBuffer("(&");
        final EntryIDSet res1 = evaluateLogicalAndFilter(filter);
        appendToDebugBuffer(")");
        return res1;

      case OR:
        appendToDebugBuffer("(|");
        final EntryIDSet res2 = evaluateLogicalOrFilter(filter);
        appendToDebugBuffer(")");
        return res2;

      case EQUALITY:
        return evaluateFilterWithDiagnostic(IndexFilterType.EQUALITY, filter);

      case GREATER_OR_EQUAL:
        return evaluateFilterWithDiagnostic(IndexFilterType.GREATER_OR_EQUAL, filter);

      case SUBSTRING:
        return evaluateFilterWithDiagnostic(IndexFilterType.SUBSTRING, filter);

      case LESS_OR_EQUAL:
        return evaluateFilterWithDiagnostic(IndexFilterType.LESS_OR_EQUAL, filter);

      case PRESENT:
        return evaluateFilterWithDiagnostic(IndexFilterType.PRESENCE, filter);

      case APPROXIMATE_MATCH:
        return evaluateFilterWithDiagnostic(IndexFilterType.APPROXIMATE, filter);

      case EXTENSIBLE_MATCH:
        if (buffer!= null)
        {
          filter.toString(buffer);
        }
        return evaluateExtensibleFilter(filter);

      case NOT:
      default:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        //NYI
        return newUndefinedSet();
    }
  }

  /**
   * Evaluate a logical AND search filter against the indexes.
   *
   * @param andFilter The AND search filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateLogicalAndFilter(SearchFilter andFilter)
  {
    // Put the slow range filters (greater-or-equal, less-or-equal)
    // into a hash map, the faster components (equality, presence, approx)
    // into one list and the remainder into another list.

    ArrayList<SearchFilter> fastComps = new ArrayList<SearchFilter>();
    ArrayList<SearchFilter> otherComps = new ArrayList<SearchFilter>();
    HashMap<AttributeType, ArrayList<SearchFilter>> rangeComps =
         new HashMap<AttributeType, ArrayList<SearchFilter>>();

    for (SearchFilter filter : andFilter.getFilterComponents())
    {
      FilterType filterType = filter.getFilterType();
      if (filterType == FilterType.GREATER_OR_EQUAL ||
           filterType == FilterType.LESS_OR_EQUAL)
      {
        ArrayList<SearchFilter> rangeList;
        rangeList = rangeComps.get(filter.getAttributeType());
        if (rangeList == null)
        {
          rangeList = new ArrayList<SearchFilter>();
          rangeComps.put(filter.getAttributeType(), rangeList);
        }
        rangeList.add(filter);
      }
      else if (filterType == FilterType.EQUALITY ||
           filterType == FilterType.PRESENT ||
           filterType == FilterType.APPROXIMATE_MATCH)
      {
        fastComps.add(filter);
      }
      else
      {
        otherComps.add(filter);
      }
    }

    EntryIDSet results = newUndefinedSet();
    // First, process the fast components.
    results = applyFiltersUntilThreshold(results, fastComps);
    // Next, process the other (non-range) components.
    results = applyFiltersUntilThreshold(results, otherComps);

    if ( isBelowFilterThreshold(results) || rangeComps.isEmpty() ) {
      return results;
    }

    // Next, process range component pairs like (cn>=A)(cn<=B).
    ArrayList<SearchFilter> remainComps = new ArrayList<SearchFilter>();
    for (Map.Entry<AttributeType, ArrayList<SearchFilter>> rangeEntry : rangeComps.entrySet())
    {
      ArrayList<SearchFilter> rangeList = rangeEntry.getValue();
      if (rangeList.size() == 2)
      {
        SearchFilter filter1 = rangeList.get(0);
        SearchFilter filter2 = rangeList.get(1);

        AttributeIndex attributeIndex = entryContainer.getAttributeIndex(rangeEntry.getKey());
        if (attributeIndex == null)
        {
          if(monitor.isFilterUseEnabled())
          {
            monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get("ordering", rangeEntry.getKey().getNameOrOID()));
          }
          continue;
        }

        final IndexQueryFactoryImpl indexQueryFactory = new IndexQueryFactoryImpl(txn, attributeIndex);
        EntryIDSet set = attributeIndex.evaluateBoundedRange(indexQueryFactory, filter1, filter2, buffer, monitor);
        if(monitor.isFilterUseEnabled() && set.isDefined())
        {
          monitor.updateStats(SearchFilter.createANDFilter(rangeList), set.size());
        }
        results.retainAll(set);
        if (isBelowFilterThreshold(results))
        {
          return results;
        }
      }
      else
      {
        // Add to the remaining range components to be processed.
        remainComps.addAll(rangeList);
      }
    }

    // Finally, process the remaining slow range components.
    return applyFiltersUntilThreshold(results, remainComps);
  }

  private EntryIDSet applyFiltersUntilThreshold(EntryIDSet results, ArrayList<SearchFilter> filters)
  {
    for(SearchFilter filter : filters) {
      if (isBelowFilterThreshold(results)) {
        return results;
      }
      results.retainAll(evaluateFilter(filter));
    }
    return results;
  }

  static boolean isBelowFilterThreshold(EntryIDSet set)
  {
    return set.isDefined() && set.size() <= FILTER_CANDIDATE_THRESHOLD;
  }

  /**
   * Evaluate a logical OR search filter against the indexes.
   *
   * @param orFilter The OR search filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateLogicalOrFilter(SearchFilter orFilter)
  {
    ArrayList<EntryIDSet> candidateSets = new ArrayList<EntryIDSet>(
         orFilter.getFilterComponents().size());

    for (SearchFilter filter : orFilter.getFilterComponents())
    {
      EntryIDSet set = evaluateFilter(filter);
      if (!set.isDefined())
      {
        // There is no point continuing.
        return set;
      }
      candidateSets.add(set);
    }
    return newSetFromUnion(candidateSets);
  }

  private EntryIDSet evaluateFilterWithDiagnostic(IndexFilterType indexFilterType, SearchFilter filter)
  {
    if (buffer != null)
    {
      filter.toString(buffer);
    }
    return evaluateFilter(indexFilterType, filter);
  }

  private EntryIDSet evaluateFilter(IndexFilterType indexFilterType, SearchFilter filter)
  {
    AttributeIndex attributeIndex = entryContainer.getAttributeIndex(filter.getAttributeType());
    if (attributeIndex != null)
    {
      final IndexQueryFactoryImpl indexQueryFactory = new IndexQueryFactoryImpl(txn, attributeIndex);
      return attributeIndex.evaluateFilter(indexQueryFactory, indexFilterType, filter, buffer, monitor);
    }

    if (monitor.isFilterUseEnabled())
    {
      monitor.updateStats(filter, INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(
          indexFilterType.toString(), filter.getAttributeType().getNameOrOID()));
    }
    return newUndefinedSet();
  }

  /**
   * Evaluate an extensible filter against the indexes.
   *
   * @param extensibleFilter The extensible filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateExtensibleFilter(SearchFilter extensibleFilter)
  {
    if (extensibleFilter.getDNAttributes())
    {
      // This will always be unindexed since the filter potentially matches
      // entries containing the specified attribute type as well as any entry
      // containing the attribute in its DN as part of a superior RDN.
      return IndexQuery.createNullIndexQuery().evaluate(null);
    }

    AttributeIndex attributeIndex = entryContainer.getAttributeIndex(extensibleFilter.getAttributeType());
    if (attributeIndex != null)
    {
      final IndexQueryFactoryImpl indexQueryFactory = new IndexQueryFactoryImpl(txn, attributeIndex);
      return attributeIndex.evaluateExtensibleFilter(indexQueryFactory, extensibleFilter, buffer, monitor);
    }
    return IndexQuery.createNullIndexQuery().evaluate(null);
  }

  private void appendToDebugBuffer(String content)
  {
    if (buffer != null)
    {
      buffer.append(content);
    }
  }
}
