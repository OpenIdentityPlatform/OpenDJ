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
package org.opends.server.monitors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.StackTraceMonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.types.InitializationException;

/**
 * This class defines a Directory Server monitor provider that can be used to
 * obtain a stack trace from all server threads that are currently defined in
 * the JVM.
 */
public class StackTraceMonitorProvider
       extends MonitorProvider<StackTraceMonitorProviderCfg>
{
  @Override
  public void initializeMonitorProvider(
                   StackTraceMonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }

  @Override
  public String getMonitorInstanceName()
  {
    return "JVM Stack Trace";
  }

  @Override
  public MonitorData getMonitorData()
  {
    Map<Thread,StackTraceElement[]> threadStacks = Thread.getAllStackTraces();

    // Re-arrange all of the elements by thread ID so that there is some logical order.
    TreeMap<Long,Map.Entry<Thread,StackTraceElement[]>> orderedStacks = new TreeMap<>();
    for (Map.Entry<Thread,StackTraceElement[]> e : threadStacks.entrySet())
    {
      orderedStacks.put(e.getKey().getId(), e);
    }

    Collection<String> jvmThreads = new ArrayList<>();
    for (Map.Entry<Thread,StackTraceElement[]> e : orderedStacks.values())
    {
      Thread t                          = e.getKey();
      StackTraceElement[] stackElements = e.getValue();

      long tid = t.getId();
      jvmThreads.add("id=" + tid + " ---------- " + t.getName() + " ----------");

      // Create an attribute for the stack trace.
      if (stackElements != null)
      {
        for (int j = 0; j < stackElements.length; j++)
        {
          jvmThreads.add(toString(tid, j, stackElements[j]));
        }
      }
    }

    MonitorData result = new MonitorData(1);
    result.add("jvmThread", jvmThreads);
    return result;
  }

  private String toString(long tid, int frame, StackTraceElement stackElement)
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("id=").append(tid);
    buffer.append(" frame[").append(frame).append("]=");

    buffer.append(stackElement.getClassName());
    buffer.append(".");
    buffer.append(stackElement.getMethodName());
    buffer.append("(");
    buffer.append(stackElement.getFileName());
    buffer.append(":");
    if (stackElement.isNativeMethod())
    {
      buffer.append("native");
    }
    else
    {
      buffer.append(stackElement.getLineNumber());
    }
    buffer.append(")");
    return buffer.toString();
  }
}
