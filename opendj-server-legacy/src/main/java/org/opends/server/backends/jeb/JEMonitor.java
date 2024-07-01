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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.jeb;

import static org.opends.server.util.StaticUtils.*;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;

import com.sleepycat.je.Environment;
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.StatsConfig;

/** Monitoring class for JE, populating cn=monitor statistics using reflection on objects methods. */
final class JEMonitor extends MonitorProvider<MonitorProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The name of this monitor instance. */
  private final String name;
  /** The environment to be monitored. */
  private final Environment env;

  JEMonitor(String name, Environment env)
  {
    this.name = name;
    this.env = env;
  }

  @Override
  public String getMonitorInstanceName()
  {
    return name;
  }

  @Override
  public MonitorData getMonitorData()
  {
    try
    {
      final StatsConfig statsConfig = new StatsConfig();

      final MonitorData monitorAttrs = new MonitorData();
      monitorAttrs.add("JEVersion", JEVersion.CURRENT_VERSION.getVersionString());
      monitorAttrs.addBean(env.getStats(statsConfig), "Environment");
      monitorAttrs.addBean(env.getTransactionStats(statsConfig), "Transaction");
      return monitorAttrs;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      MonitorData monitorAttrs = new MonitorData(1);
      monitorAttrs.add("JEInfo", stackTraceToSingleLineString(e));
      return monitorAttrs;
    }
  }
}
