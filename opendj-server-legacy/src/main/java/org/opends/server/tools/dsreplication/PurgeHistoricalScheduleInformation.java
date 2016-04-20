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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

import java.util.Date;
import java.util.List;

import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.config.ConfigConstants;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tools.tasks.TaskScheduleInformation;
import org.opends.server.tools.tasks.TaskScheduleUserData;
import org.opends.server.types.RawAttribute;

/**
 * This is a simple adaptor to create a task schedule information object
 * using the data provided by the user.  It is used to be able to share some
 * code with the {@link org.opends.server.tools.tasks.TaskTool} class.
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

  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    attributes.add(new LDAPAttribute(
        ConfigConstants.ATTR_TASK_CONFLICTS_HIST_PURGE_DOMAIN_DN, uData.getBaseDNs()));
    attributes.add(new LDAPAttribute(
        ConfigConstants.ATTR_TASK_CONFLICTS_HIST_PURGE_MAX_DURATION,
        Long.toString(uData.getMaximumDuration())));
  }

  @Override
  public List<String> getDependencyIds()
  {
    return taskSchedule.getDependencyIds();
  }

  @Override
  public FailedDependencyAction getFailedDependencyAction()
  {
    return taskSchedule.getFailedDependencyAction();
  }

  @Override
  public List<String> getNotifyUponCompletionEmailAddresses()
  {
    return taskSchedule.getNotifyUponCompletionEmailAddresses();
  }

  @Override
  public List<String> getNotifyUponErrorEmailAddresses()
  {
    return taskSchedule.getNotifyUponErrorEmailAddresses();
  }

  @Override
  public String getRecurringDateTime()
  {
    return taskSchedule.getRecurringDateTime();
  }

  @Override
  public Date getStartDateTime()
  {
    return taskSchedule.getStartDate();
  }

  @Override
  public Class<?> getTaskClass()
  {
    return org.opends.server.tasks.PurgeConflictsHistoricalTask.class;
  }

  @Override
  public String getTaskId()
  {
    return null;
  }

  @Override
  public String getTaskObjectclass()
  {
    return "ds-task-purge-conflicts-historical";
  }
}
