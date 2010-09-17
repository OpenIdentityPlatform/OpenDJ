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
package org.opends.server.tools.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.cli.CommandBuilder;

/**
 * A generic data structure that contains the data that the user provided to
 * schedule a task.
 * <br>
 * The main difference with {@link TaskScheduleInformation} is that this class
 * is completely agnostic of the execution.
 */
public class TaskScheduleUserData
{
  private boolean startNow;
  private Date startDate;
  private String recurringDateTime;
  private final List<String> dependencyIds =
    new ArrayList<String>();
  private FailedDependencyAction failedDependencyAction;
  private final List<String> notifyUponCompletionEmailAddresses =
    new ArrayList<String>();
  private final List<String> notifyUponErrorEmailAddresses =
    new ArrayList<String>();

  /**
   * Whether the arguments provided by the user, indicate that the task should
   * be executed immediately.
   * @return {@code true} if the task must be executed immediately and
   * {@code false} otherwise.
   */
  public boolean isStartNow()
  {
    return startNow;
  }

  /**
   * Sets whether the arguments provided by the user, indicate that the task
   * should be executed immediately.
   * @param startNow {@code true} if the task must be executed immediately and
   * {@code false} otherwise.
   */
  public void setStartNow(boolean startNow)
  {
    this.startNow = startNow;
  }

  /**
   * Gets the date at which this task should be scheduled to start.
   *
   * @return date/time at which the task should be scheduled
   */
  public Date getStartDate()
  {
    return startDate;
  }

  /**
   * Sets the date at which this task should be scheduled to start.
   *
   * @param startDate the date/time at which the task should be scheduled
   */
  public void setStartDate(Date startDate)
  {
    this.startDate = startDate;
  }

  /**
   * Gets the date/time pattern for recurring task schedule.
   *
   * @return recurring date/time pattern at which the task
   *         should be scheduled.
   */
  public String getRecurringDateTime()
  {
    return recurringDateTime;
  }

  /**
   * Sets the date/time pattern for recurring task schedule.
   *
   * @param recurringDateTime recurring date/time pattern at which the task
   *         should be scheduled.
   */
  public void setRecurringDateTime(String recurringDateTime)
  {
    this.recurringDateTime = recurringDateTime;
  }

  /**
   * Gets a list of task IDs upon which this task is dependent.
   *
   * @return list of task IDs
   */
  public List<String> getDependencyIds()
  {
    return dependencyIds;
  }

  /**
   * Sets the list of task IDs upon which this task is dependent.
   *
   * @param dependencyIds list of task IDs
   */
  public void setDependencyIds(List<String> dependencyIds)
  {
    this.dependencyIds.clear();
    this.dependencyIds.addAll(dependencyIds);
  }

  /**
   * Gets the action to take should one of the dependent task fail.
   *
   * @return action to take
   */
  public FailedDependencyAction getFailedDependencyAction()
  {
    return failedDependencyAction;
  }

  /**
   * Sets the action to take should one of the dependent task fail.
   *
   * @param failedDependencyAction the action to take
   */
  public void setFailedDependencyAction(
      FailedDependencyAction failedDependencyAction)
  {
    this.failedDependencyAction = failedDependencyAction;
  }

  /**
   * Gets a list of email address to which an email will be sent when this
   * task completes.
   *
   * @return list of email addresses
   */
  public List<String> getNotifyUponCompletionEmailAddresses()
  {
    return notifyUponCompletionEmailAddresses;
  }

  /**
   * Sets the list of email address to which an email will be sent when this
   * task completes.
   *
   * @param notifyUponCompletionEmailAddresses the list of email addresses
   */
  public void setNotifyUponCompletionEmailAddresses(
      List<String> notifyUponCompletionEmailAddresses)
  {
    this.notifyUponCompletionEmailAddresses.clear();
    this.notifyUponCompletionEmailAddresses.addAll(
        notifyUponCompletionEmailAddresses);
  }

  /**
   * Gets the list of email address to which an email will be sent if this
   * task encounters an error during execution.
   *
   * @return list of email addresses
   */
  public List<String> getNotifyUponErrorEmailAddresses()
  {
    return notifyUponErrorEmailAddresses;
  }

  /**
   * Sets the list of email address to which an email will be sent if this
   * task encounters an error during execution.
   *
   * @param notifyUponErrorEmailAddresses the list of email addresses
   */
  public void setNotifyUponErrorEmailAddresses(
      List<String> notifyUponErrorEmailAddresses)
  {
    this.notifyUponErrorEmailAddresses.clear();
    this.notifyUponErrorEmailAddresses.addAll(notifyUponErrorEmailAddresses);
  }


  /**
   * An static utility method that can be used to update the object used to
   * display the equivalent command-line with the contents of a given
   * task schedule object.
   * @param commandBuilder the command builder.
   * @param taskSchedule the task schedule.
   */
  public static void updateCommandBuilderWithTaskSchedule(
      CommandBuilder commandBuilder,
      TaskScheduleUserData taskSchedule)
  {
    TaskScheduleArgs argsToClone = new TaskScheduleArgs();
    String sDate = null;
    String recurringDateTime = null;
    if (!taskSchedule.isStartNow())
    {
      Date date = taskSchedule.getStartDate();
      if (date != null)
      {
        sDate = StaticUtils.formatDateTimeString(date);
      }
      recurringDateTime = taskSchedule.getRecurringDateTime();
    }

    String sFailedDependencyAction = null;
    FailedDependencyAction fAction = taskSchedule.getFailedDependencyAction();
    if (fAction != null)
    {
      sFailedDependencyAction = fAction.name();
    }
    String[] sValues = {sDate, recurringDateTime, sFailedDependencyAction};
    StringArgument[] args = {argsToClone.startArg,
        argsToClone.recurringArg, argsToClone.failedDependencyActionArg};
    for (int i=0; i<sValues.length; i++)
    {
      if (sValues[i] != null)
      {
        commandBuilder.addArgument(getArgument(args[i],
            Collections.singleton(sValues[i])));
      }
    }

    List<?>[] values = {taskSchedule.getDependencyIds(),
        taskSchedule.getNotifyUponCompletionEmailAddresses(),
        taskSchedule.getNotifyUponErrorEmailAddresses()};
    args = new StringArgument[]{argsToClone.dependencyArg,
        argsToClone.completionNotificationArg,
        argsToClone.errorNotificationArg};

    for (int i=0; i<values.length; i++)
    {
      if (!values[i].isEmpty())
      {
        commandBuilder.addArgument(getArgument(args[i],
            values[i]));
      }
    }
  }

  private static StringArgument getArgument(
      StringArgument argToClone, Collection<?> values)
  {
    StringArgument arg;
    try
    {
      arg = new StringArgument(argToClone.getName(),
          argToClone.getShortIdentifier(), argToClone.getLongIdentifier(),
          argToClone.isRequired(), argToClone.isMultiValued(),
          argToClone.needsValue(),
          argToClone.getValuePlaceholder(),
          argToClone.getDefaultValue(),
          argToClone.getPropertyName(),
          argToClone.getDescription());
    }
    catch (ArgumentException e)
    {
      // This is a bug.
      throw new RuntimeException("Unexpected error: "+e, e);
    }
    for (Object v : values)
    {
      arg.addValue(String.valueOf(v));
    }
    return arg;
  }
}
