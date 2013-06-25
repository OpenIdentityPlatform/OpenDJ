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
import java.util.LinkedList;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.tools.tasks.TaskClient;
import org.opends.server.tools.tasks.TaskScheduleUserData;
import org.opends.server.types.ByteString;
import org.opends.server.types.RawAttribute;

/**
 * This class is used to store the information provided by the user to
 * purge historical data.
 *
 */
public class PurgeHistoricalUserData extends MonoServerReplicationUserData
{
  private int maximumDuration;
  private boolean online;
  private TaskScheduleUserData taskSchedule = new TaskScheduleUserData();

  /**
   * Default constructor.
   */
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
      String adminUid = getValue(argParser.getAdministratorUID(),
          argParser.getDefaultAdministratorUID());
      uData.setAdminUid(adminUid);
      String adminPwd = argParser.getBindPasswordAdmin();
      uData.setAdminPwd(adminPwd);

      String hostName = getValue(argParser.getHostNameToStatus(),
          argParser.getDefaultHostNameToStatus());
      uData.setHostName(hostName);
      int port = getValue(argParser.getPortToStatus(),
          argParser.getDefaultPortToStatus());
      uData.setPort(port);
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

    uData.setMaximumDuration(getValue(argParser.getMaximumDuration(),
        argParser.getDefaultMaximumDuration()));
  }


  /**
   * Commodity method that simply checks if a provided value is null or not,
   * if it is not <CODE>null</CODE> returns it and if it is <CODE>null</CODE>
   * returns the provided default value.
   * @param v the value to analyze.
   * @param defaultValue the default value.
   * @return if the provided value is not <CODE>null</CODE> returns it and if it
   * is <CODE>null</CODE> returns the provided default value.
   */
  private static String getValue(String v, String defaultValue)
  {
    if (v != null)
    {
      return v;
    }
    else
    {
      return defaultValue;
    }
  }

  /**
   * Commodity method that simply checks if a provided value is -1 or not,
   * if it is not -1 returns it and if it is -1 returns the provided default
   * value.
   * @param v the value to analyze.
   * @param defaultValue the default value.
   * @return if the provided value is not -1 returns it and if it is -1 returns
   * the provided default value.
   */
  private static int getValue(int v, int defaultValue)
  {
    if (v != -1)
    {
      return v;
    }
    else
    {
      return defaultValue;
    }
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
    ArrayList<RawAttribute> rawAttrs =
      TaskClient.getTaskAttributes(information);
    BasicAttributes attrs = getAttributes(rawAttrs);
    return attrs;
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
    ArrayList<RawAttribute> rawAttrs = new ArrayList<RawAttribute>();
    NamingEnumeration<Attribute> nAtt = attrs.getAll();

    try
    {
      while (nAtt.hasMore())
      {
        Attribute attr = nAtt.next();
        NamingEnumeration<?> values = attr.getAll();
        ArrayList<ByteString> rawValues = new ArrayList<ByteString>();
        while (values.hasMore())
        {
          Object v = values.next();
          rawValues.add(ByteString.valueOf(v.toString()));
        }
        RawAttribute rAttr = RawAttribute.create(attr.getID(), rawValues);
        rawAttrs.add(rAttr);
      }
    }
    catch (NamingException ne)
    {
      // This is a bug.
      throw new RuntimeException("Unexpected error: "+ne, ne);
    }
    return rawAttrs;
  }
}

