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

import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.admin.std.server.EntryCacheMonitorProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.EntryCache;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class defines a Directory Server monitor provider that can be used to
 * obtain information about the entry cache state. Note that the information
 * reported is obtained with no locking, so it may not be entirely consistent.
 */
public class EntryCacheMonitorProvider
       extends MonitorProvider<EntryCacheMonitorProviderCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates an instance of this monitor provider.
   */
  public EntryCacheMonitorProvider()
  {
    super("Entry Cache Monitor Provider");

    // No initialization should be performed here.
  }

  /**
   * {@inheritDoc}
   */
  public void initializeMonitorProvider(
                   EntryCacheMonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }

  /**
   * {@inheritDoc}
   */
  public String getMonitorInstanceName()
  {
    return "Entry Cache";
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
    EntryCacheCfg configuration = null;

    // Get the root configuration object.
    ServerManagementContext managementContext =
      ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
      managementContext.getRootConfiguration();

    // Get the entry cache configuration.
    try {
      configuration = rootConfiguration.getEntryCache();
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return attrs;
    }

    // Get the entry cache.
    EntryCache<? extends EntryCacheCfg> cache =
      (EntryCache<? extends EntryCacheCfg>)
       DirectoryServer.getEntryCache();

    if ((cache != null) &&
        (configuration != null) &&
         configuration.isEnabled()) {
      // Get data from the cache.
      attrs = cache.getMonitorData();
    }

    return attrs;
  }
}
