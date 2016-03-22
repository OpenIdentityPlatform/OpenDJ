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
import java.util.LinkedList;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.tools.tasks.TaskClient;
import org.opends.server.tools.tasks.TaskScheduleUserData;
import org.opends.server.types.HostPort;
import org.opends.server.types.RawAttribute;

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
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));

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

  /**
   * Commodity method that returns the list of basic task attributes required
   * to launch a task corresponding to the provided user data.
   * @param uData the user data describing the purge historical to be executed.
   * @return the list of basic task attributes required
   * to launch a task corresponding to the provided user data.
   */
  public static BasicAttributes getTaskAttributes(PurgeHistoricalUserData uData)
  {
    PurgeHistoricalScheduleInformation information =
      new PurgeHistoricalScheduleInformation(uData);
    return getAttributes(TaskClient.getTaskAttributes(information));
  }

  private static BasicAttributes getAttributes(ArrayList<RawAttribute> rawAttrs)
  {
    BasicAttributes attrs = new BasicAttributes();
    for (RawAttribute rawAttr : rawAttrs)
    {
      BasicAttribute attr = new BasicAttribute(rawAttr.getAttributeType());
      for (ByteString v : rawAttr.getValues())
      {
        attr.add(v.toString());
      }
      attrs.put(attr);
    }
    return attrs;
  }

  /**
   * Returns the DN of the task corresponding to the provided list of
   * attributes.  The code assumes that the attributes have been generated
   * calling the method {@link #getTaskAttributes(PurgeHistoricalUserData)}.
   * @param attrs the attributes of the task entry.
   * @return the DN of the task entry.
   */
  public static String getTaskDN(BasicAttributes attrs)
  {
    ArrayList<RawAttribute> rawAttrs = getRawAttributes(attrs);
    return TaskClient.getTaskDN(rawAttrs);
  }

  /**
   * Returns the ID of the task corresponding to the provided list of
   * attributes.  The code assumes that the attributes have been generated
   * calling the method {@link #getTaskAttributes(PurgeHistoricalUserData)}.
   * @param attrs the attributes of the task entry.
   * @return the ID of the task entry.
   */
  public static String getTaskID(BasicAttributes attrs)
  {
    ArrayList<RawAttribute> rawAttrs = getRawAttributes(attrs);
    return TaskClient.getTaskID(rawAttrs);
  }

  private static ArrayList<RawAttribute> getRawAttributes(BasicAttributes attrs)
  {
    try
    {
      ArrayList<RawAttribute> rawAttrs = new ArrayList<>();
      NamingEnumeration<Attribute> nAtt = attrs.getAll();
      while (nAtt.hasMore())
      {
        Attribute attr = nAtt.next();
        NamingEnumeration<?> values = attr.getAll();
        ArrayList<ByteString> rawValues = new ArrayList<>();
        while (values.hasMore())
        {
          Object v = values.next();
          rawValues.add(ByteString.valueOfUtf8(v.toString()));
        }
        RawAttribute rAttr = RawAttribute.create(attr.getID(), rawValues);
        rawAttrs.add(rAttr);
      }
      return rawAttrs;
    }
    catch (NamingException ne)
    {
      // This is a bug.
      throw new RuntimeException("Unexpected error: "+ne, ne);
    }
  }
}
