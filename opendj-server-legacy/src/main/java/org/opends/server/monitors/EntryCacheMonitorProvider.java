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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.monitors;

import static org.opends.messages.ConfigMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.EntryCacheCfg;
import org.forgerock.opendj.server.config.server.EntryCacheMonitorProviderCfg;
import org.opends.server.api.EntryCache;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;

/**
 * This class defines a Directory Server monitor provider that can be used to
 * obtain information about the entry cache state. Note that the information
 * reported is obtained with no locking, so it may not be entirely consistent.
 */
public class EntryCacheMonitorProvider
       extends MonitorProvider<EntryCacheMonitorProviderCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The name for this monitor. */
  private String monitorName;

  /** The entry cache common name. */
  private String entryCacheName;

  /** The entry cache with which this monitor is associated. */
  private EntryCache<? extends EntryCacheCfg> entryCache;

  /** Entry cache monitor configuration. */
  private EntryCacheMonitorProviderCfg monitorConfiguration;

  /**
   * Creates default instance of this monitor provider.
   */
  public EntryCacheMonitorProvider()
  {
    this.entryCacheName = "Entry Caches";
    this.entryCache = DirectoryServer.getEntryCache();
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
    this.entryCacheName = entryCacheName + " Entry Cache";
    this.entryCache = entryCache;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(
    EntryCacheMonitorProviderCfg configuration)
    throws ConfigException
  {
    monitorName = entryCacheName;

    if (configuration != null) {
      monitorConfiguration = configuration;
    }
    if (monitorConfiguration == null) {
      LocalizableMessage message =
          INFO_WARN_CONFIG_ENTRYCACHE_NO_MONITOR_CONFIG_ENTRY.get(
              ConfigConstants.DN_ENTRY_CACHE_MONITOR_CONFIG, monitorName);
      logger.debug(message);
      throw new ConfigException(message);
    }
    if (!monitorConfiguration.isEnabled()) {
      LocalizableMessage message =
          INFO_WARN_CONFIG_ENTRYCACHE_MONITOR_CONFIG_DISABLED.get(
              ConfigConstants.DN_ENTRY_CACHE_MONITOR_CONFIG, monitorName);
      logger.debug(message);
      throw new ConfigException(message);
    }
  }

  @Override
  public String getMonitorInstanceName()
  {
    return monitorName;
  }

  @Override
  public MonitorData getMonitorData()
  {
    if (entryCache != null &&
        monitorConfiguration != null &&
        monitorConfiguration.isEnabled()) {
      // Get monitor data from the cache.
      return entryCache.getMonitorData();
    }
    return new MonitorData(0);
  }
}
