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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;



import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
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
  /**
   * The name to use for the monitor attribute that provides the current request
   * backlog.
   */
  public static final String ATTR_CURRENT_BACKLOG = "currentRequestBacklog";



  /**
   * The name to use for the monitor attribute that provides the average request
   * backlog.
   */
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



  // The maximum backlog observed by polling the queue.
  private int maxBacklog;

  // The total number of times the backlog has been polled.
  private long numPolls;

  // The total backlog observed from periodic polling.
  private long totalBacklog;

  // The parallel work queue instance with which this monitor is associated.
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



  /**
   * {@inheritDoc}
   */
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
  public String getMonitorInstanceName()
  {
    return "Work Queue";
  }


  /**
   * {@inheritDoc}
   */
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

    ArrayList<Attribute> monitorAttrs = new ArrayList<Attribute>();
    AttributeSyntax<?> integerSyntax = DirectoryServer
        .getDefaultIntegerSyntax();

    // The current backlog.
    AttributeType attrType = DirectoryServer.getDefaultAttributeType(
        ATTR_CURRENT_BACKLOG, integerSyntax);
    monitorAttrs
        .add(Attributes.create(attrType, String.valueOf(backlog)));

    // The average backlog.
    attrType = DirectoryServer.getDefaultAttributeType(ATTR_AVERAGE_BACKLOG,
        integerSyntax);
    monitorAttrs.add(Attributes.create(attrType, String
        .valueOf(averageBacklog)));

    // The maximum backlog.
    attrType = DirectoryServer.getDefaultAttributeType(ATTR_MAX_BACKLOG,
        integerSyntax);
    monitorAttrs.add(Attributes.create(attrType, String
        .valueOf(maxBacklog)));

    // The total number of operations submitted.
    attrType = DirectoryServer.getDefaultAttributeType(ATTR_OPS_SUBMITTED,
        integerSyntax);
    monitorAttrs.add(Attributes.create(attrType, String
        .valueOf(opsSubmitted)));

    return monitorAttrs;
  }
}
