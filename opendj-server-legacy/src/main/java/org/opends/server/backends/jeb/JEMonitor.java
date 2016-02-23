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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;

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
  public List<Attribute> getMonitorData()
  {
    try
    {
      List<Attribute> monitorAttrs = new ArrayList<>();

      monitorAttrs.add(Attributes.create("JEVersion", JEVersion.CURRENT_VERSION.getVersionString()));

      StatsConfig statsConfig = new StatsConfig();
      addAttributesForStatsObject(monitorAttrs, "Environment", env.getStats(statsConfig));
      addAttributesForStatsObject(monitorAttrs, "Transaction", env.getTransactionStats(statsConfig));

      return monitorAttrs;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return Collections.singletonList(Attributes.create("JEInfo", stackTraceToSingleLineString(e)));
    }
  }

  private void addAttributesForStatsObject(List<Attribute> monitorAttrs, String attrPrefix, Object stats)
  {
    for (Method method : stats.getClass().getMethods())
    {
      final Class<?> returnType = method.getReturnType();
      if (method.getName().startsWith("get")
          && (returnType.equals(int.class) || returnType.equals(long.class)))
      {
        addStatAttribute(monitorAttrs, attrPrefix, stats, method);
      }
    }
  }

  private void addStatAttribute(List<Attribute> monitorAttrs, String attrPrefix, Object stats, Method method)
  {
    final Syntax integerSyntax = DirectoryServer.getDefaultIntegerSyntax();
    try
    {
      // Remove the 'get' from the method name and add the prefix.
      String attrName = attrPrefix + method.getName().substring(3);

      AttributeType attrType = DirectoryServer.getAttributeType(attrName, integerSyntax);
      monitorAttrs.add(Attributes.create(attrType, String.valueOf(method.invoke(stats))));
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }
}
