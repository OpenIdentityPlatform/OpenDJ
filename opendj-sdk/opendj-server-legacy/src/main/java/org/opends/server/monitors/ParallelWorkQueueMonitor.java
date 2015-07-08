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
package org.opends.server.monitors;

import static org.opends.server.core.DirectoryServer.*;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.extensions.ParallelWorkQueue;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;

/**
 * This class defines a Directory Server monitor that can be used to provide
 * information about the state of the work queue.
 */
public class ParallelWorkQueueMonitor
       extends MonitorProvider<MonitorProviderCfg>
       implements Runnable
{
  /** The name to use for the monitor attribute that provides the current request backlog. */
  public static final String ATTR_CURRENT_BACKLOG = "currentRequestBacklog";
  /** The name to use for the monitor attribute that provides the average request backlog. */
  public static final String ATTR_AVERAGE_BACKLOG = "averageRequestBacklog";
  /**
   * The name to use for the monitor attribute that provides the maximum
   * observed request backlog.
   */
  public static final String ATTR_MAX_BACKLOG = "maxRequestBacklog";
  /**
   * The name to use for the monitor attribute that provides the total number of
   * operations submitted.
   */
  public static final String ATTR_OPS_SUBMITTED = "requestsSubmitted";


  /** The maximum backlog observed by polling the queue. */
  private int maxBacklog;

  /** The total number of times the backlog has been polled. */
  private long numPolls;

  /** The total backlog observed from periodic polling. */
  private long totalBacklog;

  /** The parallel work queue instance with which this monitor is associated. */
  private ParallelWorkQueue workQueue;

  /**
   * Initializes this monitor provider.  Note that no initialization should be
   * done here, since it should be performed in the
   * <CODE>initializeMonitorProvider</CODE> class.
   *
   * @param  workQueue  The work queue with which this monitor is associated.
   */
  public ParallelWorkQueueMonitor(ParallelWorkQueue workQueue)
  {
    this.workQueue = workQueue;
  }



  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    maxBacklog   = 0;
    totalBacklog = 0;
    numPolls     = 0;
    scheduleUpdate(this, 0, 10, TimeUnit.SECONDS);
  }



  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    return "Work Queue";
  }


  /** {@inheritDoc} */
  @Override
  public void run()
  {
    int backlog = workQueue.size();
    totalBacklog += backlog;
    numPolls++;

    if (backlog > maxBacklog)
    {
      maxBacklog = backlog;
    }
  }



  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    int backlog = workQueue.size();
    totalBacklog += backlog;
    numPolls++;
    if (backlog > maxBacklog)
    {
      maxBacklog = backlog;
    }

    long averageBacklog = (long) (1.0 * totalBacklog / numPolls);
    long opsSubmitted = workQueue.getOpsSubmitted();

    ArrayList<Attribute> monitorAttrs = new ArrayList<>();
    putAttribute(monitorAttrs, ATTR_CURRENT_BACKLOG, backlog);
    putAttribute(monitorAttrs, ATTR_AVERAGE_BACKLOG, averageBacklog);
    putAttribute(monitorAttrs, ATTR_MAX_BACKLOG, maxBacklog);
    // The total number of operations submitted.
    putAttribute(monitorAttrs, ATTR_OPS_SUBMITTED, opsSubmitted);

    return monitorAttrs;
  }

  private void putAttribute(ArrayList<Attribute> monitorAttrs, String attrName, Object value)
  {
    AttributeType attrType = getDefaultAttributeType(attrName, getDefaultIntegerSyntax());
    monitorAttrs.add(Attributes.create(attrType, String.valueOf(value)));
  }
}
