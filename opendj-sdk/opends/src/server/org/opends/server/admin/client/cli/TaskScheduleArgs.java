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
package org.opends.server.admin.client.cli;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.backends.task.RecurringTask;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.cli.CLIException;

/**
 * A class that contains all the arguments related to the task scheduling.
 *
 */
public class TaskScheduleArgs
{
  /**
   * Magic value used to indicate that the user would like to schedule
   * this operation to run immediately as a task as opposed to running
   * the operation in the local VM.
   */
  public static final String NOW = "0";
  /**
   *  Argument for describing the task's start time.
   */
  public StringArgument startArg;

  /**
   *  Argument to indicate a recurring task.
   */
  public StringArgument recurringArg;

  /**
   *  Argument for specifying completion notifications.
   */
  public StringArgument completionNotificationArg;

  /**
   *  Argument for specifying error notifications.
   */
  public StringArgument errorNotificationArg;

  /**
   *  Argument for specifying dependency.
   */
  public StringArgument dependencyArg;

  /**
   *  Argument for specifying a failed dependency action.
   */
  public StringArgument failedDependencyActionArg;

  /**
   * Default constructor.
   */
  public TaskScheduleArgs()
  {
    try
    {
     createTaskArguments();
    }
    catch (ArgumentException ae)
    {
      // This is a bug.
      throw new RuntimeException("Unexpected error: "+ae, ae);
    }
  }

  private void createTaskArguments() throws ArgumentException
  {
    startArg = new StringArgument(OPTION_LONG_START_DATETIME,
        OPTION_SHORT_START_DATETIME, OPTION_LONG_START_DATETIME, false, false,
        true, INFO_START_DATETIME_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_START_DATETIME.get());

    recurringArg = new StringArgument(OPTION_LONG_RECURRING_TASK,
        OPTION_SHORT_RECURRING_TASK, OPTION_LONG_RECURRING_TASK, false, false,
        true, INFO_RECURRING_TASK_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_RECURRING_TASK.get());

    completionNotificationArg = new StringArgument(
        OPTION_LONG_COMPLETION_NOTIFICATION_EMAIL,
        OPTION_SHORT_COMPLETION_NOTIFICATION_EMAIL,
        OPTION_LONG_COMPLETION_NOTIFICATION_EMAIL, false, true, true,
        INFO_EMAIL_ADDRESS_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TASK_COMPLETION_NOTIFICATION.get());

    errorNotificationArg = new StringArgument(
        OPTION_LONG_ERROR_NOTIFICATION_EMAIL,
        OPTION_SHORT_ERROR_NOTIFICATION_EMAIL,
        OPTION_LONG_ERROR_NOTIFICATION_EMAIL, false, true, true,
        INFO_EMAIL_ADDRESS_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TASK_ERROR_NOTIFICATION.get());

    dependencyArg = new StringArgument(OPTION_LONG_DEPENDENCY,
        OPTION_SHORT_DEPENDENCY, OPTION_LONG_DEPENDENCY, false, true, true,
        INFO_TASK_ID_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TASK_DEPENDENCY_ID.get());

    Set<FailedDependencyAction> fdaValSet =
      EnumSet.allOf(FailedDependencyAction.class);
    failedDependencyActionArg = new StringArgument(
        OPTION_LONG_FAILED_DEPENDENCY_ACTION,
        OPTION_SHORT_FAILED_DEPENDENCY_ACTION,
        OPTION_LONG_FAILED_DEPENDENCY_ACTION, false, true, true,
        INFO_ACTION_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TASK_FAILED_DEPENDENCY_ACTION.get(StaticUtils
            .collectionToString(fdaValSet, ","), FailedDependencyAction
            .defaultValue().name()));

    for (Argument arg : getArguments())
    {
      arg.setPropertyName(arg.getLongIdentifier());
    }
  }

  /**
   * Returns all the task schedule related arguments.
   * @return all the task schedule related arguments.
   */
  public Argument[] getArguments()
  {
    return new Argument[] {startArg, recurringArg, completionNotificationArg,
       errorNotificationArg, dependencyArg, failedDependencyActionArg};
  }

  /**
   * Validates arguments related to task scheduling.  This should be
   * called after the <code>ArgumentParser.parseArguments</code> has
   * been called.
   * <br>
   * Note that this method does only validation that is not dependent on whether
   * the operation will be launched as a task or not.  If the operation is not
   * to be launched as a task, the method {@link #validateArgsIfOffline()}
   * should be called instead of this method.
   * @throws ArgumentException if there is a problem with the arguments.
   * @throws CLIException if there is a problem with one of the values provided
   * by the user.
   */
  public void validateArgs()
  throws ArgumentException, CLIException {
    if (startArg.isPresent() && !NOW.equals(startArg.getValue())) {
      try {
        Date date = StaticUtils.parseDateTimeString(startArg.getValue());
        // Check that the provided date is not previous to the current date.
        Date currentDate = new Date(System.currentTimeMillis());
        if (currentDate.after(date))
        {
          throw new CLIException(ERR_START_DATETIME_ALREADY_PASSED.get(
              startArg.getValue()));
        }
      } catch (ParseException pe) {
        throw new ArgumentException(ERR_START_DATETIME_FORMAT.get());
      }
    }

    if (recurringArg.isPresent())
    {
      try
      {
        RecurringTask.parseTaskTab(recurringArg.getValue());
      }
      catch (DirectoryException de)
      {
        throw new ArgumentException(ERR_RECURRING_SCHEDULE_FORMAT_ERROR.get(
            de.getMessageObject()), de);
      }
    }

    if (completionNotificationArg.isPresent()) {
      LinkedList<String> addrs = completionNotificationArg.getValues();
      for (String addr : addrs) {
        if (!StaticUtils.isEmailAddress(addr)) {
          throw new ArgumentException(ERR_TASKTOOL_INVALID_EMAIL_ADDRESS.get(
                  addr, completionNotificationArg.getLongIdentifier()));
        }
      }
    }

    if (errorNotificationArg.isPresent()) {
      LinkedList<String> addrs = errorNotificationArg.getValues();
      for (String addr : addrs) {
        if (!StaticUtils.isEmailAddress(addr)) {
          throw new ArgumentException(ERR_TASKTOOL_INVALID_EMAIL_ADDRESS.get(
                  addr, errorNotificationArg.getLongIdentifier()));
        }
      }
    }

    if (failedDependencyActionArg.isPresent()) {

      if (!dependencyArg.isPresent()) {
        throw new ArgumentException(ERR_TASKTOOL_FDA_WITH_NO_DEPENDENCY.get());
      }

      String fda = failedDependencyActionArg.getValue();
      if (null == FailedDependencyAction.fromString(fda)) {
        Set<FailedDependencyAction> fdaValSet =
          EnumSet.allOf(FailedDependencyAction.class);
        throw new ArgumentException(ERR_TASKTOOL_INVALID_FDA.get(fda,
                        StaticUtils.collectionToString(fdaValSet, ",")));
      }
    }
  }

  /**
   * Validates arguments related to task scheduling.  This should be
   * called after the <code>ArgumentParser.parseArguments</code> has
   * been called.
   * <br>
   * This method assumes that the operation is not to be launched as a task.
   * This method covers all the checks done by {@link #validateArgs()}, so it
   * is not necessary to call that method if this method is being called.
   * @throws ArgumentException if there is a problem with the arguments.
   * @throws CLIException if there is a problem with one of the values provided
   * by the user.
   */
  public void validateArgsIfOffline()
  throws ArgumentException, CLIException
  {
    Argument[] incompatibleArgs = {startArg, recurringArg,
        completionNotificationArg,
        errorNotificationArg, dependencyArg, failedDependencyActionArg};
    for (Argument arg : incompatibleArgs)
    {
      if (arg.isPresent()) {
        throw new ArgumentException(ERR_TASKTOOL_OPTIONS_FOR_TASK_ONLY.get(
                arg.getLongIdentifier()));
      }
    }
    validateArgs();
  }

  /**
   * Gets the date at which the associated task should be scheduled to start.
   *
   * @return date/time at which the task should be scheduled
   */
  public Date getStartDateTime() {
    Date start = null;

    // If the start time arg is present parse its value
    if (startArg != null && startArg.isPresent()) {
      if (NOW.equals(startArg.getValue())) {
        start = new Date();
      } else {
        try {
          start = StaticUtils.parseDateTimeString(startArg.getValue());
        } catch (ParseException pe) {
          // ignore; validated in validateTaskArgs()
        }
      }
    }
    return start;
  }

  /**
   * Whether the arguments provided by the user, indicate that the task should
   * be executed immediately.
   * <br>
   * This method assumes that the arguments have already been parsed and
   * validated.
   * @return {@code true} if the task must be executed immediately and
   * {@code false} otherwise.
   */
  public boolean isStartNow()
  {
    boolean isStartNow = true;
    if (startArg != null && startArg.isPresent()) {
      isStartNow = NOW.equals(startArg.getValue());
    }
    return isStartNow;
  }

  /**
   * Gets the date/time pattern for recurring task schedule.
   *
   * @return recurring date/time pattern at which the task
   *         should be scheduled.
   */
  public String getRecurringDateTime() {
    String pattern = null;

    // If the recurring task arg is present parse its value
    if (recurringArg != null && recurringArg.isPresent()) {
      pattern = recurringArg.getValue();
    }
    return pattern;
  }

  /**
   * Gets a list of task IDs upon which the associated task is dependent.
   *
   * @return list of task IDs
   */
  public List<String> getDependencyIds() {
    if (dependencyArg.isPresent()) {
      return dependencyArg.getValues();
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Gets the action to take should one of the dependent task fail.
   *
   * @return action to take
   */
  public FailedDependencyAction getFailedDependencyAction() {
    FailedDependencyAction fda = null;
    if (failedDependencyActionArg.isPresent()) {
      String fdaString = failedDependencyActionArg.getValue();
      fda = FailedDependencyAction.fromString(fdaString);
    }
    return fda;
  }

  /**
   * Gets a list of email address to which an email will be sent when this
   * task completes.
   *
   * @return list of email addresses
   */
  public List<String> getNotifyUponCompletionEmailAddresses() {
    if (completionNotificationArg.isPresent()) {
      return completionNotificationArg.getValues();
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Gets a list of email address to which an email will be sent if this
   * task encounters an error during execution.
   *
   * @return list of email addresses
   */
  public List<String> getNotifyUponErrorEmailAddresses() {
    if (errorNotificationArg.isPresent()) {
      return errorNotificationArg.getValues();
    } else {
      return Collections.emptyList();
    }
  }
}
