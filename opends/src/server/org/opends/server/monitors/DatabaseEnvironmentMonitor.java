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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;



import static org.opends.server.loggers.debug.DebugLogger.*;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.MonitorProvider;
import org.opends.server.backends.jeb.RootContainer;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.TransactionStats;



/**
 * A monitor provider for a Berkeley DB JE environment.
 * It uses reflection on the environment statistics object
 * so that we don't need to keep a list of all the stats.
 */
public class DatabaseEnvironmentMonitor
       extends MonitorProvider<MonitorProviderCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The name of this monitor instance.
   */
  private String name;

  /**
   * The root container to be monitored.
   */
  private RootContainer rootContainer;

  /**
   * Creates a new database environment monitor.
   * @param name The monitor instance name.
   * @param rootContainer A root container handle for the database to be
   * monitored.
   */
  public DatabaseEnvironmentMonitor(String name, RootContainer rootContainer)
  {
    super(name + " Monitor Provider");


    this.name = name;
    this.rootContainer = rootContainer;
  }



  /**
   * {@inheritDoc}
   */
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
  public String getMonitorInstanceName()
  {
    return name;
  }

  /**
   * Retrieves the length of time in milliseconds that should elapse between
   * calls to the <CODE>updateMonitorData()</CODE> method.  A negative or zero
   * return value indicates that the <CODE>updateMonitorData()</CODE> method
   * should not be periodically invoked.
   *
   * @return The length of time in milliseconds that should elapse between
   *         calls to the <CODE>updateMonitorData()</CODE> method.
   */
  public long getUpdateInterval()
  {
    return 0;
  }

  /**
   * Performs any processing periodic processing that may be desired to update
   * the information associated with this monitor.  Note that best-effort
   * attempts will be made to ensure that calls to this method come
   * <CODE>getUpdateInterval()</CODE> milliseconds apart, but no guarantees will
   * be made.
   */
  public void updateMonitorData()
  {
  }

  /**
   * Creates monitor attribute values for a given JE statistics object,
   * using reflection to call all the getter methods of the statistics object.
   * The attribute type names of the created attribute values are derived from
   * the names of the getter methods.
   * @param monitorAttrs The monitor attribute values are inserted into this
   * attribute list.
   * @param stats The JE statistics object.
   * @param attrPrefix A common prefix for the attribute type names of the
   * monitor attribute values, to distinguish the attributes of one
   * type of statistical object from another, and to avoid attribute name
   * collisions.
   */
  private void addAttributesForStatsObject(ArrayList<Attribute> monitorAttrs,
                                           Object stats, String attrPrefix)
  {
    Class<?> c = stats.getClass();
    Method[] methods = c.getMethods();

    // Iterate through all the statistic class methods.
    for (Method method : methods)
    {
      // Invoke all the getters returning integer values.
      if (method.getName().startsWith("get"))
      {
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(int.class) || returnType.equals(long.class))
        {
          AttributeSyntax<?> integerSyntax =
               DirectoryServer.getDefaultIntegerSyntax();

          // Remove the 'get' from the method name and add the prefix.
          String attrName = attrPrefix + method.getName().substring(3);

          try
          {
            // Read the statistic.
            Object statValue = method.invoke(stats);

            // Create an attribute from the statistic.
            AttributeType attrType =
                 DirectoryServer.getDefaultAttributeType(attrName,
                                                         integerSyntax);
            monitorAttrs.add(Attributes.create(attrType, String
                .valueOf(statValue)));

          } catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }
    }
  }

  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return A set of attributes containing monitor data that should be
   *         returned to the client if the corresponding monitor entry is
   *         requested.
   */
  public ArrayList<Attribute> getMonitorData()
  {
    EnvironmentStats environmentStats = null;
    LockStats lockStats = null;
    TransactionStats transactionStats = null;
    StatsConfig statsConfig = new StatsConfig();

    try
    {
      environmentStats = rootContainer.getEnvironmentStats(statsConfig);
      lockStats = rootContainer.getEnvironmentLockStats(statsConfig);
      transactionStats =
          rootContainer.getEnvironmentTransactionStats(statsConfig);
    } catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return null;
    }

    ArrayList<Attribute> monitorAttrs = new ArrayList<Attribute>();

    String jeVersion = JEVersion.CURRENT_VERSION.getVersionString();
    AttributeType versionType =
         DirectoryServer.getDefaultAttributeType("JEVersion");
    monitorAttrs.add(Attributes.create(versionType, jeVersion));

    addAttributesForStatsObject(monitorAttrs, environmentStats, "Environment");
    addAttributesForStatsObject(monitorAttrs, lockStats, "Lock");
    addAttributesForStatsObject(monitorAttrs, transactionStats, "Transaction");

    return monitorAttrs;
  }
}
