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
 *
 */
package org.opends.server.backends.jeb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.opends.server.backends.jeb.AttributeIndex.IndexFilterType;
import org.opends.server.core.SearchOperation;
import org.opends.server.monitors.DatabaseEnvironmentMonitor;
import org.opends.server.types.AttributeType;
import org.opends.server.types.FilterType;
import org.opends.server.types.SearchFilter;

import static org.opends.messages.BackendMessages.*;

/**
 * An index filter is used to apply a search operation to a set of indexes
 * to generate a set of candidate entries.
 */
public class IndexFilter
{
  /**
   * Stop processing the filter against the indexes when the
   * number of candidates is smaller than this value.
   */
  public static final int FILTER_CANDIDATE_THRESHOLD = 10;

  /**
   * The entry entryContainer holding the attribute indexes.
   */
  private final EntryContainer entryContainer;

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

  private final DatabaseEnvironmentMonitor monitor;

  /**
   * Construct an index filter for a search operation.
   *
   * @param entryContainer The entry entryContainer.
   * @param searchOp       The search operation to be evaluated.
   * @param monitor        The monitor to gather filter usage stats.
   *
   * @param debugBuilder If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   */
  public IndexFilter(EntryContainer entryContainer,
                     SearchOperation searchOp,
                     StringBuilder debugBuilder,
                     DatabaseEnvironmentMonitor monitor)
  {
    this.entryContainer = entryContainer;
    this.searchOp = searchOp;
    this.buffer = debugBuilder;
    this.monitor = monitor;
  }

  /**
   * Evaluate the search operation against the indexes.
   *
   * @return A set of entry IDs representing candidate entries.
   */
  public EntryIDSet evaluate()
  {
    if (buffer != null)
    {
      buffer.append("filter=");
    }
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
    EntryIDSet candidates = evaluate(filter);
    if (buffer != null)
    {
      candidates.toString(buffer);
    }
    return candidates;
  }

  private EntryIDSet evaluate(SearchFilter filter)
  {
    switch (filter.getFilterType())
    {
      case AND:
        if (buffer != null)
        {
          buffer.append("(&");
        }
        final EntryIDSet res1 = evaluateLogicalAndFilter(filter);
        if (buffer != null)
        {
          buffer.append(")");
        }
        return res1;

      case OR:
        if (buffer != null)
        {
          buffer.append("(|");
        }
        final EntryIDSet res2 = evaluateLogicalOrFilter(filter);
        if (buffer != null)
        {
          buffer.append(")");
        }
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
        return new EntryIDSet();
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
    // Start off with an undefined set.
    EntryIDSet results = new EntryIDSet();

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

    // First, process the fast components.
    if (evaluateFilters(results, fastComps)
        // Next, process the other (non-range) components.
        || evaluateFilters(results, otherComps)
        // Are there any range component pairs like (cn>=A)(cn<=B) ?
        || rangeComps.isEmpty())
    {
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

        EntryIDSet set = attributeIndex.evaluateBoundedRange(filter1, filter2, buffer, monitor);
        if(monitor.isFilterUseEnabled() && set.isDefined())
        {
          monitor.updateStats(SearchFilter.createANDFilter(rangeList), set.size());
        }
        if (retainAll(results, set))
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
    evaluateFilters(results, remainComps);

    return results;
  }

  private boolean evaluateFilters(EntryIDSet results, ArrayList<SearchFilter> filters)
  {
    for (SearchFilter filter : filters)
    {
      final EntryIDSet filteredSet = evaluateFilter(filter);
      if (retainAll(results, filteredSet))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Retain all IDs in a given set that appear in a second set.
   *
   * @param a The set of entry IDs to be updated.
   * @param b Only those IDs that are in this set are retained.
   * @return true if the number of IDs in the updated set is now below
   *         the filter candidate threshold.
   */
  private boolean retainAll(EntryIDSet a, EntryIDSet b)
  {
    a.retainAll(b);

    // We may have reached the point of diminishing returns where
    // it is quicker to stop now and process the current small number of candidates.
    return a.isDefined() && a.size() <= FILTER_CANDIDATE_THRESHOLD;
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
    return EntryIDSet.unionOfSets(candidateSets, false);
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
      return attributeIndex.evaluateFilter(indexFilterType, filter, buffer, monitor);
    }

    if (monitor.isFilterUseEnabled())
    {
      monitor.updateStats(filter, INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(
          indexFilterType.toString(), filter.getAttributeType().getNameOrOID()));
    }
    return new EntryIDSet();
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
      return attributeIndex.evaluateExtensibleFilter(extensibleFilter, buffer, monitor);
    }
    return IndexQuery.createNullIndexQuery().evaluate(null);
  }
}
