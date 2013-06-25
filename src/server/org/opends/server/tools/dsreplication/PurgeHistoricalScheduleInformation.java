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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsreplication;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.config.ConfigConstants;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tools.tasks.TaskScheduleInformation;
import org.opends.server.tools.tasks.TaskScheduleUserData;
import org.opends.server.types.ByteString;
import org.opends.server.types.RawAttribute;

/**
 * This is a simple adaptor to create a task schedule information object
 * using the data provided by the user.  It is used to be able to share some
 * code with the {@link TaskTool} class.
 *
 */
public class PurgeHistoricalScheduleInformation
implements TaskScheduleInformation
{
  private final PurgeHistoricalUserData uData;
  private TaskScheduleUserData taskSchedule;

  /**
   * Default constructor.
   * @param uData the data provided by the user to do the purge historical.
   */
  public PurgeHistoricalScheduleInformation(
      PurgeHistoricalUserData uData)
  {
    this.uData = uData;
    this.taskSchedule = uData.getTaskSchedule();
    if (taskSchedule == null)
    {
      taskSchedule = new TaskScheduleUserData();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    ArrayList<ByteString> baseDNs = new ArrayList<ByteString>();
    for (String baseDN : uData.getBaseDNs())
    {
      baseDNs.add(ByteString.valueOf(baseDN));
    }
    attributes.add(new LDAPAttribute(
        ConfigConstants.ATTR_TASK_CONFLICTS_HIST_PURGE_DOMAIN_DN, baseDNs));
    attributes.add(new LDAPAttribute(
        ConfigConstants.ATTR_TASK_CONFLICTS_HIST_PURGE_MAX_DURATION,
        Long.toString(uData.getMaximumDuration())));

  }

  /**
   * {@inheritDoc}
   */
  public List<String> getDependencyIds()
  {
    return taskSchedule.getDependencyIds();
  }

  /**
   * {@inheritDoc}
   */
  public FailedDependencyAction getFailedDependencyAction()
  {
    return taskSchedule.getFailedDependencyAction();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getNotifyUponCompletionEmailAddresses()
  {
    return taskSchedule.getNotifyUponCompletionEmailAddresses();
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getNotifyUponErrorEmailAddresses()
  {
    return taskSchedule.getNotifyUponErrorEmailAddresses();
  }

  /**
   * {@inheritDoc}
   */
  public String getRecurringDateTime()
  {
    return taskSchedule.getRecurringDateTime();
  }

  /**
   * {@inheritDoc}
   */
  public Date getStartDateTime()
  {
    return taskSchedule.getStartDate();
  }

  /**
   * {@inheritDoc}
   */
  public Class<?> getTaskClass()
  {
    return org.opends.server.tasks.PurgeConflictsHistoricalTask.class;
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskId()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskObjectclass()
  {
    return "ds-task-purge-conflicts-historical";
  }
}
