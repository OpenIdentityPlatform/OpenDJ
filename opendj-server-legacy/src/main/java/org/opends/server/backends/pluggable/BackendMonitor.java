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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.TimeThread;

/**
 * A monitor provider for high level backend statistics, such as filter stats and search counters.
 */
class BackendMonitor extends MonitorProvider<MonitorProviderCfg>
{

  /** Represents the statistical information kept for each search filter. */
  private static class FilterStats implements Comparable<FilterStats>
  {
    private volatile LocalizableMessage failureReason = LocalizableMessage.EMPTY;
    private long maxMatchingEntries = -1;
    private final AtomicInteger hits = new AtomicInteger();

    @Override
    public int compareTo(FilterStats that) {
      return this.hits.get() - that.hits.get();
    }

    private void update(int hitCount, LocalizableMessage failureReason)
    {
      this.hits.getAndAdd(hitCount);
      this.failureReason = failureReason;
    }

    private void update(int hitCount, long matchingEntries)
    {
      this.hits.getAndAdd(hitCount);
      this.failureReason = LocalizableMessage.EMPTY;
      synchronized(this)
      {
        if(matchingEntries > maxMatchingEntries)
        {
          maxMatchingEntries = matchingEntries;
        }
      }
    }
  }

  /** The name of this monitor instance. */
  private final String name;

  /** The root container to be monitored. */
  private final RootContainer rootContainer;

  private int maxEntries = 1024;
  private boolean filterUseEnabled;
  private String startTimeStamp;
  private final HashMap<SearchFilter, FilterStats> filterToStats = new HashMap<>();
  private final AtomicInteger indexedSearchCount = new AtomicInteger();
  private final AtomicInteger unindexedSearchCount = new AtomicInteger();

  /**
   * Creates a new backend monitor.
   * @param name The monitor instance name.
   * @param rootContainer A root container handle for the backend to be
   * monitored.
   */
  BackendMonitor(String name, RootContainer rootContainer)
  {
    this.name = name;
    this.rootContainer = rootContainer;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
       throws ConfigException, InitializationException
  {
  }

  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    return name;
  }

  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return A set of attributes containing monitor data that should be
   *         returned to the client if the corresponding monitor entry is
   *         requested.
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> monitorAttrs = new ArrayList<Attribute>();

    AttributeBuilder needReindex = new AttributeBuilder("need-reindex");
    for(EntryContainer ec : rootContainer.getEntryContainers())
    {
      for(Tree tree : ec.listTrees())
      {
        if(tree instanceof Index && !((Index)tree).isTrusted())
        {
          needReindex.add(tree.getName().toString());
        }
      }
    }
    if(needReindex.size() > 0)
    {
      monitorAttrs.add(needReindex.toAttribute());
    }

    if(filterUseEnabled)
    {
      monitorAttrs.add(Attributes.create("filter-use-startTime",
          startTimeStamp));
      AttributeBuilder builder = new AttributeBuilder("filter-use");

      StringBuilder stringBuilder = new StringBuilder();
      synchronized(filterToStats)
      {
        for(Map.Entry<SearchFilter, FilterStats> entry :
            filterToStats.entrySet())
        {
          entry.getKey().toString(stringBuilder);
          stringBuilder.append(" hits:");
          stringBuilder.append(entry.getValue().hits.get());
          stringBuilder.append(" maxmatches:");
          stringBuilder.append(entry.getValue().maxMatchingEntries);
          stringBuilder.append(" message:");
          stringBuilder.append(entry.getValue().failureReason);
          builder.add(stringBuilder.toString());
          stringBuilder.setLength(0);
        }
      }
      monitorAttrs.add(builder.toAttribute());
      monitorAttrs.add(Attributes.create("filter-use-indexed",
          String.valueOf(indexedSearchCount.get())));
      monitorAttrs.add(Attributes.create("filter-use-unindexed",
          String.valueOf(unindexedSearchCount.get())));
    }

    return monitorAttrs;
  }


  /**
   * Updates the index filter statistics with this latest search filter
   * and the reason why an index was not used.
   *
   * @param searchFilter The search filter that was evaluated.
   * @param failureMessage The reason why an index was not used.
   */
  void updateStats(SearchFilter searchFilter, LocalizableMessage failureMessage)
  {
    if(!filterUseEnabled)
    {
      return;
    }

    FilterStats stats;
    synchronized(filterToStats)
    {
      stats = filterToStats.get(searchFilter);


      if(stats != null)
      {
        stats.update(1, failureMessage);
      }
      else
      {
        stats = new FilterStats();
        stats.update(1, failureMessage);
        removeLowestHit();
        filterToStats.put(searchFilter, stats);
      }
    }
  }

  /**
   * Updates the index filter statistics with this latest search filter
   * and the number of entries matched by the index lookup.
   *
   * @param searchFilter The search filter that was evaluated.
   * @param matchingEntries The number of entries matched by the successful
   *                        index lookup.
   */
  void updateStats(SearchFilter searchFilter, long matchingEntries)
  {
    if(!filterUseEnabled)
    {
      return;
    }

    FilterStats stats;
    synchronized(filterToStats)
    {
      stats = filterToStats.get(searchFilter);


      if(stats != null)
      {
        stats.update(1, matchingEntries);
      }
      else
      {
        stats = new FilterStats();
        stats.update(1, matchingEntries);
        removeLowestHit();
        filterToStats.put(searchFilter, stats);
      }
    }
  }

  /**
   * Enable or disable index filter statistics gathering.
   *
   * @param enabled <code>true></code> to enable index filter statics gathering.
   */
  void enableFilterUseStats(boolean enabled)
  {
    if(enabled && !filterUseEnabled)
    {
      startTimeStamp = TimeThread.getGMTTime();
      indexedSearchCount.set(0);
      unindexedSearchCount.set(0);
    }
    else if(!enabled)
    {
      filterToStats.clear();
    }
    filterUseEnabled = enabled;
  }

  /**
   * Indicates if index filter statistics gathering is enabled.
   *
   * @return <code>true</code> If index filter statistics gathering is enabled.
   */
  boolean isFilterUseEnabled()
  {
    return filterUseEnabled;
  }

  /**
   * Sets the maximum number of search filters statistics entries to keep
   * before ones with the least hits will be removed.
   *
   * @param maxEntries The maximum number of search filters statistics
   * entries to keep
   */
  void setMaxEntries(int maxEntries)
  {
    this.maxEntries = maxEntries;
  }

  /**
   * Updates the statistics counter to include an indexed search.
   */
  void updateIndexedSearchCount()
  {
    if (filterUseEnabled)
    {
      indexedSearchCount.getAndIncrement();
    }
  }

  /**
   * Updates the statistics counter to include an unindexed search.
   */
  void updateUnindexedSearchCount()
  {
    if (filterUseEnabled)
    {
      unindexedSearchCount.getAndIncrement();
    }
  }

  private void removeLowestHit()
  {
    while(!filterToStats.isEmpty() && filterToStats.size() > maxEntries)
    {
      Iterator<Map.Entry<SearchFilter, FilterStats>> i =
          filterToStats.entrySet().iterator();
      Map.Entry<SearchFilter, FilterStats> lowest = i.next();
      Map.Entry<SearchFilter, FilterStats> entry;
      while(lowest.getValue().hits.get() > 1 && i.hasNext())
      {
        entry = i.next();
        if(entry.getValue().hits.get() < lowest.getValue().hits.get())
        {
          lowest = entry;
        }
      }

      filterToStats.remove(lowest.getKey());
    }
  }
}
