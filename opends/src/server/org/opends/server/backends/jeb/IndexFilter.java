/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 *
 */
package org.opends.server.backends.jeb;

import org.opends.server.core.SearchOperation;
import org.opends.server.monitors.DatabaseEnvironmentMonitor;
import org.opends.server.types.AttributeType;
import org.opends.server.types.FilterType;
import org.opends.server.types.SearchFilter;

import static org.opends.messages.JebMessages.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    EntryIDSet candidates;
    switch (filter.getFilterType())
    {
      case AND:
        if (buffer != null)
        {
          buffer.append("(&");
        }
        candidates = evaluateLogicalAndFilter(filter);
        if (buffer != null)
        {
          buffer.append(")");
        }
        break;

      case OR:
        if (buffer != null)
        {
          buffer.append("(|");
        }
        candidates = evaluateLogicalOrFilter(filter);
        if (buffer != null)
        {
          buffer.append(")");
        }
        break;

      case EQUALITY:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        candidates = evaluateEqualityFilter(filter);
        break;

      case GREATER_OR_EQUAL:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        candidates = evaluateGreaterOrEqualFilter(filter);
        break;

      case SUBSTRING:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        candidates = evaluateSubstringFilter(filter);
        break;

      case LESS_OR_EQUAL:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        candidates = evaluateLessOrEqualFilter(filter);
        break;

      case PRESENT:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        candidates = evaluatePresenceFilter(filter);
        break;

      case APPROXIMATE_MATCH:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        candidates = evaluateApproximateFilter(filter);
        break;

      case EXTENSIBLE_MATCH:
         if (buffer!= null)
        {
          filter.toString(buffer);
        }
        candidates = evaluateExtensibleFilter(filter);
        break;

      case NOT:
      default:
        if (buffer != null)
        {
          filter.toString(buffer);
        }
        //NYI
        candidates = new EntryIDSet();
        break;
    }

    if (buffer != null)
    {
      candidates.toString(buffer);
    }
    return candidates;
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
    for (SearchFilter filter : fastComps)
    {
      EntryIDSet set = evaluateFilter(filter);

      if (retainAll(results, set))
      {
        return results;
      }
    }

    // Next, process the other (non-range) components.
    for (SearchFilter filter : otherComps)
    {
      EntryIDSet set = evaluateFilter(filter);

      if (retainAll(results, set))
      {
        return results;
      }
    }

    // Next, process range component pairs like (cn>=A)(cn<=B).

    if (rangeComps.isEmpty())
    {
      return results;
    }

    ArrayList<SearchFilter> remainComps = new ArrayList<SearchFilter>();
    for (Map.Entry<AttributeType, ArrayList<SearchFilter>> rangeEntry :
         rangeComps.entrySet())
    {
      ArrayList<SearchFilter> rangeList = rangeEntry.getValue();
      if (rangeList.size() == 2)
      {
        SearchFilter a = rangeList.get(0);
        SearchFilter b = rangeList.get(1);

        AttributeIndex attributeIndex =
             entryContainer.getAttributeIndex(rangeEntry.getKey());
        if (attributeIndex == null)
        {
          if(monitor.isFilterUseEnabled())
          {
            monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get("ordering",
                    rangeEntry.getKey().getNameOrOID()));
          }
          continue;
        }

        if (a.getFilterType() == FilterType.GREATER_OR_EQUAL &&
             b.getFilterType() == FilterType.LESS_OR_EQUAL)
        {
          // Like (cn>=A)(cn<=B).
          EntryIDSet set;
          set = attributeIndex.evaluateBoundedRange(a.getAssertionValue(),
                                                     b.getAssertionValue());

          if (buffer != null)
          {
            a.toString(buffer);
            b.toString(buffer);
            set.toString(buffer);
          }

          if(monitor.isFilterUseEnabled())
          {
            if(set.isDefined())
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  set.size());
            }
            else if(!attributeIndex.orderingIndex.isTrusted())
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  INFO_JEB_INDEX_FILTER_INDEX_NOT_TRUSTED.get(
                      attributeIndex.orderingIndex.getName()));
            }
            else if(attributeIndex.orderingIndex.isRebuildRunning())
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  INFO_JEB_INDEX_FILTER_INDEX_REBUILD_IN_PROGRESS.get(
                      attributeIndex.orderingIndex.getName()));
            }
            else
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  INFO_JEB_INDEX_FILTER_INDEX_LIMIT_EXCEEDED.get(
                      attributeIndex.orderingIndex.getName()));
            }
          }

          if (retainAll(results, set))
          {
            return results;
          }
          continue;
        }
        else if (a.getFilterType() == FilterType.LESS_OR_EQUAL &&
             b.getFilterType() == FilterType.GREATER_OR_EQUAL)
        {
          // Like (cn<=A)(cn>=B).
          EntryIDSet set;
          set = attributeIndex.evaluateBoundedRange(b.getAssertionValue(),
                                                     a.getAssertionValue());

          if (buffer != null)
          {
            a.toString(buffer);
            b.toString(buffer);
            set.toString(buffer);
          }

          if(monitor.isFilterUseEnabled())
          {
            if(set.isDefined())
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  set.size());
            }
            else if(!attributeIndex.orderingIndex.isTrusted())
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  INFO_JEB_INDEX_FILTER_INDEX_NOT_TRUSTED.get(
                      attributeIndex.orderingIndex.getName()));
            }
            else if(attributeIndex.orderingIndex.isRebuildRunning())
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  INFO_JEB_INDEX_FILTER_INDEX_REBUILD_IN_PROGRESS.get(
                      attributeIndex.orderingIndex.getName()));
            }
            else
            {
              monitor.updateStats(SearchFilter.createANDFilter(rangeList),
                  INFO_JEB_INDEX_FILTER_INDEX_LIMIT_EXCEEDED.get(
                      attributeIndex.orderingIndex.getName()));
            }
          }


          if (retainAll(results, set))
          {
            return results;
          }
          continue;
        }
      }

      // Add to the remaining range components to be processed.
      for (SearchFilter filter : rangeList)
      {
        remainComps.add(filter);
      }
    }

    // Finally, process the remaining slow range components.
    for (SearchFilter filter : remainComps)
    {
      EntryIDSet set = evaluateFilter(filter);
      if (retainAll(results, set))
      {
        return results;
      }
    }

    return results;
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
    // it is quicker to stop now and process the current small number of
    // candidates.
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

  /**
   * Evaluate an equality filter against the indexes.
   *
   * @param equalityFilter The equality filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateEqualityFilter(SearchFilter equalityFilter)
  {
    EntryIDSet candidates;
    AttributeIndex attributeIndex =
         entryContainer.getAttributeIndex(equalityFilter.getAttributeType());
    if (attributeIndex == null)
    {
      if(monitor.isFilterUseEnabled())
      {
        monitor.updateStats(equalityFilter,
            INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get("equality",
                equalityFilter.getAttributeType().getNameOrOID()));
      }
      candidates = new EntryIDSet();
    }
    else
    {
      candidates = attributeIndex.evaluateEqualityFilter(equalityFilter,
          buffer, monitor);
    }
    return candidates;
  }

  /**
   * Evaluate a presence filter against the indexes.
   *
   * @param filter The presence filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluatePresenceFilter(SearchFilter filter)
  {
    EntryIDSet candidates;
    AttributeIndex attributeIndex =
         entryContainer.getAttributeIndex(filter.getAttributeType());
    if (attributeIndex == null)
    {
      if(monitor.isFilterUseEnabled())
      {
        monitor.updateStats(filter,
            INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get("presence",
                filter.getAttributeType().getNameOrOID()));
      }
      candidates = new EntryIDSet();
    }
    else
    {
      candidates = attributeIndex.evaluatePresenceFilter(filter, buffer,
          monitor);
    }
    return candidates;
  }

  /**
   * Evaluate a greater-or-equal filter against the indexes.
   *
   * @param filter The greater-or-equal filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateGreaterOrEqualFilter(SearchFilter filter)
  {
    EntryIDSet candidates;
    AttributeIndex attributeIndex =
         entryContainer.getAttributeIndex(filter.getAttributeType());
    if (attributeIndex == null)
    {
      if(monitor.isFilterUseEnabled())
      {
        monitor.updateStats(filter,
            INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get("ordering",
                filter.getAttributeType().getNameOrOID()));
      }
      candidates = new EntryIDSet();
    }
    else
    {
      candidates = attributeIndex.evaluateGreaterOrEqualFilter(filter,
          buffer, monitor);
    }
    return candidates;
  }

  /**
   * Evaluate a less-or-equal filter against the indexes.
   *
   * @param filter The less-or-equal filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateLessOrEqualFilter(SearchFilter filter)
  {
    EntryIDSet candidates;
    AttributeIndex attributeIndex =
         entryContainer.getAttributeIndex(filter.getAttributeType());
    if (attributeIndex == null)
    {
      if(monitor.isFilterUseEnabled())
      {
        monitor.updateStats(filter,
            INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get("ordering",
                filter.getAttributeType().getNameOrOID()));
      }
      candidates = new EntryIDSet();
    }
    else
    {
      candidates = attributeIndex.evaluateLessOrEqualFilter(filter, buffer,
          monitor);
    }
    return candidates;
  }

  /**
   * Evaluate a substring filter against the indexes.
   *
   * @param filter The substring filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateSubstringFilter(SearchFilter filter)
  {
    EntryIDSet candidates;
    AttributeIndex attributeIndex =
         entryContainer.getAttributeIndex(filter.getAttributeType());
    if (attributeIndex == null)
    {
      if(monitor.isFilterUseEnabled())
      {
        monitor.updateStats(filter,
            INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get(
                "substring or equality",
                filter.getAttributeType().getNameOrOID()));
      }
      candidates = new EntryIDSet();
    }
    else
    {
      candidates = attributeIndex.evaluateSubstringFilter(filter,
          buffer, monitor);
    }
    return candidates;
  }

  /**
   * Evaluate an approximate filter against the indexes.
   *
   * @param approximateFilter The approximate filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateApproximateFilter(SearchFilter approximateFilter)
  {
    EntryIDSet candidates;
    AttributeIndex attributeIndex =
         entryContainer.getAttributeIndex(approximateFilter.getAttributeType());
    if (attributeIndex == null)
    {
      if(monitor.isFilterUseEnabled())
      {
        monitor.updateStats(approximateFilter,
            INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get("approximate",
                approximateFilter.getAttributeType().getNameOrOID()));
      }
      candidates = new EntryIDSet();
    }
    else
    {
      candidates = attributeIndex.evaluateApproximateFilter(approximateFilter,
          buffer, monitor);
    }
    return candidates;
  }

  /**
   * Evaluate an extensible filter against the indexes.
   *
   * @param extensibleFilter The extensible filter to be evaluated.
   * @return A set of entry IDs representing candidate entries.
   */
  private EntryIDSet evaluateExtensibleFilter(SearchFilter extensibleFilter)
  {
    EntryIDSet candidates;

    if (extensibleFilter.getDNAttributes())
    {
      // This will always be unindexed since the filter potentially matches
      // entries containing the specified attribute type as well as any entry
      // containing the attribute in its DN as part of a superior RDN.
      candidates = IndexQuery.createNullIndexQuery().evaluate(null);
    }
    else
    {
      AttributeIndex attributeIndex = entryContainer
          .getAttributeIndex(extensibleFilter.getAttributeType());
      if (attributeIndex == null)
      {
        candidates = IndexQuery.createNullIndexQuery().evaluate(null);
      }
      else
      {
        candidates = attributeIndex.evaluateExtensibleFilter(extensibleFilter,
            buffer, monitor);
      }
    }
    return candidates;
  }
}
