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

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.tools.tasks.TaskScheduleUserData;
import org.opends.server.types.HostPort;

/** This class is used to store the information provided by the user to purge historical data. */
public class PurgeHistoricalUserData extends MonoServerReplicationUserData
{
  private int maximumDuration;
  private boolean online;
  private TaskScheduleUserData taskSchedule = new TaskScheduleUserData();

  /** Default constructor. */
  public PurgeHistoricalUserData()
  {
  }

  /**
   * Returns the maximum duration that the purge can take in seconds.
   * @return the maximum duration that the purge can take in seconds.
   */
  public int getMaximumDuration()
  {
    return maximumDuration;
  }

  /**
   * Sets the maximum duration that the purge can take in seconds.
   * @param maximumDuration the maximum duration that the purge can take in
   * seconds.
   */
  public void setMaximumDuration(int maximumDuration)
  {
    this.maximumDuration = maximumDuration;
  }

  /**
   * Whether the task will be executed on an online server (using an LDAP
   * connection and the tasks backend) or not.
   * @return {@code true} if the task will be executed on an online server
   * and {@code false} otherwise.
   */
  public boolean isOnline()
  {
    return online;
  }

  /**
   * Sets whether the task will be executed on an online server or not.
   * @param online {@code true} if the task will be executed on an online server
   * and {@code false} otherwise.
   */
  public void setOnline(boolean online)
  {
    this.online = online;
  }

  /**
   * Returns the object describing the schedule of the task.  If the operation
   * is not online, the value returned by this method should not be taken into
   * account.
   * @return the object describing the schedule of the task.
   */
  public TaskScheduleUserData getTaskSchedule()
  {
    return taskSchedule;
  }

  /**
   * Sets the object describing the schedule of the task.
   * @param taskSchedule the object describing the schedule of the task.
   */
  public void setTaskSchedule(TaskScheduleUserData taskSchedule)
  {
    this.taskSchedule = taskSchedule;
  }

  /**
   * Initializes the contents of the provided purge historical replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the purge historical replication user data object to be
   * initialized.
   * @param argParser the argument parser with the arguments provided by the
   * user.
   */
  public static  void initializeWithArgParser(PurgeHistoricalUserData uData,
      ReplicationCliArgumentParser argParser)
  {
    uData.setBaseDNs(toDNs(argParser.getBaseDNs()));

    if (argParser.connectionArgumentsPresent())
    {
      uData.setAdminUid(argParser.getAdministratorUIDOrDefault());
      uData.setAdminPwd(argParser.getBindPasswordAdmin());
      uData.setHostPort(new HostPort(
          argParser.getHostNameToStatusOrDefault(), argParser.getPortToStatusOrDefault()));
      uData.setOnline(true);
      TaskScheduleUserData taskSchedule = new TaskScheduleUserData();
      TaskScheduleArgs taskArgs = argParser.getTaskArgsList();
      taskSchedule.setStartNow(taskArgs.isStartNow());
      if (!taskSchedule.isStartNow())
      {
        taskSchedule.setStartDate(taskArgs.getStartDateTime());
        taskSchedule.setDependencyIds(taskArgs.getDependencyIds());
        taskSchedule.setFailedDependencyAction(
            taskArgs.getFailedDependencyAction());
        taskSchedule.setNotifyUponErrorEmailAddresses(
            taskArgs.getNotifyUponErrorEmailAddresses());
        taskSchedule.setNotifyUponCompletionEmailAddresses(
            taskArgs.getNotifyUponCompletionEmailAddresses());
        taskSchedule.setRecurringDateTime(
            taskArgs.getRecurringDateTime());
      }
      uData.setTaskSchedule(taskSchedule);
    }
    else
    {
      uData.setOnline(false);
    }

    uData.setMaximumDuration(argParser.getMaximumDurationOrDefault());
  }

  private static List<DN> toDNs(List<String> baseDNs)
  {
    final List<DN> results = new ArrayList<>(baseDNs.size());
    for (String dn : baseDNs)
    {
      results.add(DN.valueOf(dn));
    }
    return results;
  }
}
