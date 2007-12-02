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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;

import java.util.ArrayList;
import org.opends.messages.Message;

import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.admin.std.server.EntryCacheMonitorProviderCfg;
import org.opends.server.api.EntryCache;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.ConfigMessages.*;

/**
 * This class defines a Directory Server monitor provider that can be used to
 * obtain information about the entry cache state. Note that the information
 * reported is obtained with no locking, so it may not be entirely consistent.
 */
public class EntryCacheMonitorProvider
       extends MonitorProvider<EntryCacheMonitorProviderCfg>
{
  // The name for this monitor.
  private String monitorName;

  // The entry cache common name.
  private String entryCacheName;

  // The entry cache with which this monitor is associated.
  private EntryCache<? extends EntryCacheCfg> entryCache;

  // Global entry cache monitor configuration.
  private static EntryCacheMonitorProviderCfg monitorConfiguration;

  /**
   * Creates default instance of this monitor provider.
   */
  public EntryCacheMonitorProvider()
  {
    super("Entry Caches Monitor Provider");
    this.entryCacheName = "Entry Caches";
    this.entryCache = (EntryCache<? extends EntryCacheCfg>)
      DirectoryServer.getEntryCache();
  }

  /**
   * Creates implementation specific instance of this monitor provider.
   *
   * @param  entryCacheName  The name to use for this monitor provider.
   * @param  entryCache      The entry cache to associate this monitor
   *                         provider with.
   */
  public EntryCacheMonitorProvider(
    String entryCacheName,
    EntryCache<? extends EntryCacheCfg> entryCache)
  {
    super(entryCacheName + " Entry Cache Monitor Provider");
    this.entryCacheName = entryCacheName;
    this.entryCache = entryCache;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeMonitorProvider(
    EntryCacheMonitorProviderCfg configuration)
    throws ConfigException
  {
    monitorName = entryCacheName;

    if (configuration != null) {
      monitorConfiguration = configuration;
    }
    if (monitorConfiguration == null) {
      Message message =
        INFO_WARN_CONFIG_ENTRYCACHE_NO_MONITOR_CONFIG_ENTRY.get(
        ConfigConstants.DN_ENTRY_CACHE_MONITOR_CONFIG,
        monitorName);
      logError(message);
      throw new ConfigException(message);
    }
    if (!monitorConfiguration.isEnabled()) {
      Message message =
        INFO_WARN_CONFIG_ENTRYCACHE_MONITOR_CONFIG_DISABLED.get(
        ConfigConstants.DN_ENTRY_CACHE_MONITOR_CONFIG,
        monitorName);
      logError(message);
      throw new ConfigException(message);
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getMonitorInstanceName()
  {
    return monitorName;
  }

  /**
   * {@inheritDoc}
   */
  public long getUpdateInterval()
  {
    // This monitor does not need to run periodically.
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  public void updateMonitorData()
  {
    // This monitor does not need to run periodically.
    return;
  }

  /**
   * {@inheritDoc}
   */
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>();

    if ((entryCache != null) &&
        (monitorConfiguration != null) &&
        (monitorConfiguration.isEnabled())) {
      // Get monitor data from the cache.
      attrs = entryCache.getMonitorData();
    }

    return attrs;
  }
}
