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

import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.extensions.ParallelWorkQueue;
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

  @Override
  public MonitorData getMonitorData()
  {
    int backlog = workQueue.size();
    totalBacklog += backlog;
    numPolls++;
    if (backlog > maxBacklog)
    {
      maxBacklog = backlog;
    }

    long averageBacklog = (long) (1.0 * totalBacklog / numPolls);

    final MonitorData monitorAttrs = new MonitorData(4);
    monitorAttrs.add(ATTR_CURRENT_BACKLOG, backlog);
    monitorAttrs.add(ATTR_AVERAGE_BACKLOG, averageBacklog);
    monitorAttrs.add(ATTR_MAX_BACKLOG, maxBacklog);
    monitorAttrs.add(ATTR_OPS_SUBMITTED, workQueue.getOpsSubmitted());
    return monitorAttrs;
  }
}
