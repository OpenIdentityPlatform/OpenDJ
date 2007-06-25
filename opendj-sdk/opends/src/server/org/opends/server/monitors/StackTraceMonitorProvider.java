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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;



import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.InitializationException;




/**
 * This class defines a Directory Server monitor provider that can be used to
 * obtain a stack trace from all server threads that are currently defined in
 * the JVM.
 */
public class StackTraceMonitorProvider
       extends MonitorProvider<MonitorProviderCfg>
{
  /**
   * Initializes this monitor provider.
   */
  public StackTraceMonitorProvider()
  {
    super("Stack Trace Monitor Provider");

    // No initialization should be performed here.
  }



  /**
   * {@inheritDoc}
   */
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  public String getMonitorInstanceName()
  {
    return "JVM Stack Trace";
  }



  /**
   * Retrieves the length of time in milliseconds that should elapse between
   * calls to the <CODE>updateMonitorData()</CODE> method.  A negative or zero
   * return value indicates that the <CODE>updateMonitorData()</CODE> method
   * should not be periodically invoked.
   *
   * @return  The length of time in milliseconds that should elapse between
   *          calls to the <CODE>updateMonitorData()</CODE> method.
   */
  public long getUpdateInterval()
  {
    // This monitor does not need to run periodically.
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
    // This monitor does not need to run periodically.
    return;
  }



  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  public ArrayList<Attribute> getMonitorData()
  {
    Map<Thread,StackTraceElement[]> threadStacks = Thread.getAllStackTraces();


    // Re-arrange all of the elements by thread ID so that there is some logical
    // order.
    TreeMap<Long,Map.Entry<Thread,StackTraceElement[]>> orderedStacks =
         new TreeMap<Long,Map.Entry<Thread,StackTraceElement[]>>();
    for (Map.Entry<Thread,StackTraceElement[]> e : threadStacks.entrySet())
    {
      orderedStacks.put(e.getKey().getId(), e);
    }


    AttributeType attrType =
         DirectoryServer.getDefaultAttributeType("jvmThread");
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();

    for (Map.Entry<Thread,StackTraceElement[]> e : orderedStacks.values())
    {
      Thread t                          = e.getKey();
      StackTraceElement[] stackElements = e.getValue();

      long id = t.getId();

      StringBuilder buffer = new StringBuilder();
      buffer.append("id=");
      buffer.append(id);
      buffer.append(" ---------- ");
      buffer.append(t.getName());
      buffer.append(" ----------");
      values.add(new AttributeValue(attrType, buffer.toString()));

      // Create an attribute for the stack trace.
      if (stackElements != null)
      {
        for (int j=0; j < stackElements.length; j++)
        {
          buffer = new StringBuilder();
          buffer.append("id=");
          buffer.append(id);
          buffer.append(" frame[");
          buffer.append(j);
          buffer.append("]=");

          buffer.append(stackElements[j].getClassName());
          buffer.append(".");
          buffer.append(stackElements[j].getMethodName());
          buffer.append("(");
          buffer.append(stackElements[j].getFileName());
          buffer.append(":");
          if (stackElements[j].isNativeMethod())
          {
            buffer.append("native");
          }
          else
          {
            buffer.append(stackElements[j].getLineNumber());
          }
          buffer.append(")");

          values.add(new AttributeValue(attrType, buffer.toString()));
        }
      }
    }

    ArrayList<Attribute> attrs = new ArrayList<Attribute>();
    attrs.add(new Attribute(attrType, "jvmThread", values));

    return attrs;
  }
}

