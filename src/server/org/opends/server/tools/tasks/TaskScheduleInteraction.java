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

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.opends.messages.Message;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.backends.task.RecurringTask;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;

/**
 * A class that is in charge of interacting with the user to ask about
 * scheduling options for a task.
 * <br>
 * It takes as argument an {@link TaskScheduleArgs} object with the arguments
 * provided by the user and updates the provided {@link TaskScheduleUserData}
 * with the information provided by the user.
 *
 */
public class TaskScheduleInteraction
{
  private boolean headerDisplayed;
  private final TaskScheduleUserData uData;
  private final TaskScheduleArgs args;
  private final ConsoleApplication app;
  private final Message taskName;
  private List<? extends TaskEntry> taskEntries =
    Collections.emptyList();
  private ProgressMessageFormatter formatter =
    new PlainTextProgressMessageFormatter();

  /**
   * The enumeration used by the menu displayed to ask the user about the
   * type of scheduling (if any) to be done.
   *
   */
  private enum ScheduleOption {
    RUN_NOW(INFO_RUN_TASK_NOW.get()),
    RUN_LATER(INFO_RUN_TASK_LATER.get()),
    SCHEDULE_TASK(INFO_SCHEDULE_TASK.get());

    private Message prompt;
    private ScheduleOption(Message prompt)
    {
      this.prompt = prompt;
    }
    Message getPrompt()
    {
      return prompt;
    }

    /**
     * The default option to be proposed to the user.
     * @return the default option to be proposed to the user.
     */
    public static ScheduleOption defaultValue()
    {
      return RUN_NOW;
    }
  };

  /**
   * Default constructor.
   * @param uData the task schedule user data.
   * @param args the object with the arguments provided by the user.  The code
   * assumes that the arguments have already been parsed.
   * @param app the console application object used to prompt for data.
   * @param taskName the name of the task to be used in the prompt messages.
   */
  public TaskScheduleInteraction(TaskScheduleUserData uData,
      TaskScheduleArgs args, ConsoleApplication app,
      Message taskName)
  {
    this.uData = uData;
    this.args = args;
    this.app = app;
    this.taskName = taskName;
  }

  /**
   * Executes the interaction with the user.
   * @throws CLIException if there is an error prompting the user.
   */
  public void run() throws CLIException
  {
    headerDisplayed = false;

    runStartNowOrSchedule();

    runCompletionNotification();

    runErrorNotification();

    runDependency();

    if (!uData.getDependencyIds().isEmpty())
    {
      runFailedDependencyAction();
    }
  }

  /**
   * Returns the task entries that are defined in the server.  These are
   * used to prompt the user about the task dependencies.
   * @return the task entries that are defined in the server.
   */
  public List<? extends TaskEntry> getTaskEntries()
  {
    return taskEntries;
  }

  /**
   * Sets the task entries that are defined in the server.  These are
   * used to prompt the user about the task dependencies.  If no task entries
   * are provided, the user will not be prompted for task dependencies.
   * @param taskEntries the task entries that are defined in the server.
   */
  public void setTaskEntries(List<? extends TaskEntry> taskEntries)
  {
    this.taskEntries = taskEntries;
  }

  /**
   * Returns the formatter that is used to generate messages.
   * @return the formatter that is used to generate messages.
   */
  public ProgressMessageFormatter getFormatter()
  {
    return formatter;
  }

  /**
   * Sets the formatter that is used to generate messages.
   * @param formatter the formatter that is used to generate messages.
   */
  public void setFormatter(ProgressMessageFormatter formatter)
  {
    this.formatter = formatter;
  }

  private void runFailedDependencyAction() throws CLIException
  {
    if (args.dependencyArg.isPresent())
    {
      uData.setFailedDependencyAction(args.getFailedDependencyAction());
    }
    else
    {
      askForFailedDependencyAction();
    }
  }

  private void askForFailedDependencyAction() throws CLIException
  {
    checkHeaderDisplay();

    MenuBuilder<FailedDependencyAction> builder =
      new MenuBuilder<FailedDependencyAction>(app);
    builder.setPrompt(INFO_TASK_FAILED_DEPENDENCY_ACTION_PROMPT.get());
    builder.addCancelOption(false);
    for (FailedDependencyAction choice : FailedDependencyAction.values())
    {
      MenuResult<FailedDependencyAction> result = MenuResult.success(choice);

      builder.addNumberedOption(choice.getDisplayName(), result);

      if (choice.equals(FailedDependencyAction.defaultValue()))
      {
        builder.setDefault(choice.getDisplayName(), result);
      }
    }
    MenuResult<FailedDependencyAction> m = builder.toMenu().run();
    if (m.isSuccess())
    {
      uData.setFailedDependencyAction(m.getValue());
    }
    else
    {
      throw new CLIException(Message.EMPTY);
    }

  }

  private void runDependency() throws CLIException
  {
    if (args.dependencyArg.isPresent())
    {
      uData.setDependencyIds(args.getDependencyIds());
    }
    else if (!taskEntries.isEmpty())
    {
      askForDependency();
    }
  }

  private void askForDependency() throws CLIException
  {
    checkHeaderDisplay();

    boolean hasDependencies =
      app.confirmAction(INFO_TASK_HAS_DEPENDENCIES_PROMPT.get(), false);
    if (hasDependencies)
    {
      printAvailableDependencyTaskMessage();
      HashSet<String> dependencies = new HashSet<String>();
      while (true)
      {
        String dependencyID =
          app.readLineOfInput(INFO_TASK_DEPENDENCIES_PROMPT.get());
        if (dependencyID != null && !dependencyID.isEmpty())
        {
          if (isTaskIDDefined(dependencyID))
          {
            dependencies.add(dependencyID);
          }
          else
          {
            printTaskIDNotDefinedMessage(dependencyID);
          }
        }
        else
        {
          break;
        }
      }
      uData.setDependencyIds(new ArrayList<String>(dependencies));
    }
    else
    {
      List<String> empty = Collections.emptyList();
      uData.setDependencyIds(empty);
    }
  }

  private void printAvailableDependencyTaskMessage()
  {
    StringBuilder sb = new StringBuilder();
    String separator = formatter.getLineBreak().toString() +
    formatter.getTab().toString();
    for (TaskEntry entry : taskEntries)
    {
      sb.append(separator);
      sb.append(entry.getId());
    }
    app.printlnProgress();
    app.printProgress(INFO_AVAILABLE_DEFINED_TASKS.get(sb.toString()));
    app.printlnProgress();
    app.printlnProgress();

  }

  private void printTaskIDNotDefinedMessage(String dependencyID)
  {
    app.println();
    app.println(ERR_DEPENDENCY_TASK_NOT_DEFINED.get(dependencyID));
  }

  private boolean isTaskIDDefined(String dependencyID)
  {
    boolean taskIDDefined = false;
    for (TaskEntry entry : taskEntries)
    {
      if (dependencyID.equalsIgnoreCase(entry.getId()))
      {
        taskIDDefined = true;
        break;
      }
    }
    return taskIDDefined;
  }

  private void runErrorNotification() throws CLIException
  {
    if (args.errorNotificationArg.isPresent())
    {
      uData.setNotifyUponErrorEmailAddresses(
          args.getNotifyUponErrorEmailAddresses());
    }
    else
    {
      askForErrorNotification();
    }
  }

  private void askForErrorNotification() throws CLIException
  {
    List<String> addresses =
      askForEmailNotification(INFO_HAS_ERROR_NOTIFICATION_PROMPT.get(),
          INFO_ERROR_NOTIFICATION_PROMPT.get());
    uData.setNotifyUponErrorEmailAddresses(addresses);
  }

  private List<String> askForEmailNotification(Message hasNotificationPrompt,
      Message emailAddressPrompt) throws CLIException
  {
    checkHeaderDisplay();

    List<String> addresses = new ArrayList<String>();
    boolean hasNotification =
      app.confirmAction(hasNotificationPrompt, false);
    if (hasNotification)
    {
      HashSet<String> set = new HashSet<String>();
      while (true)
      {
        String address =
          app.readLineOfInput(emailAddressPrompt);
        if (address != null && !address.isEmpty())
        {
          if (!StaticUtils.isEmailAddress(address)) {
            app.println(ERR_INVALID_EMAIL_ADDRESS.get(address));
          }
          else
          {
            set.add(address);
          }
        }
        else
        {
          break;
        }
      }
      addresses.addAll(set);
    }
    return addresses;
  }

  private void runCompletionNotification() throws CLIException
  {
    if (args.completionNotificationArg.isPresent())
    {
      uData.setNotifyUponCompletionEmailAddresses(
          args.getNotifyUponCompletionEmailAddresses());
    }
    else
    {
      askForCompletionNotification();
    }
  }

  private void askForCompletionNotification() throws CLIException
  {
    List<String> addresses =
      askForEmailNotification(INFO_HAS_COMPLETION_NOTIFICATION_PROMPT.get(),
          INFO_COMPLETION_NOTIFICATION_PROMPT.get());
    uData.setNotifyUponCompletionEmailAddresses(addresses);
  }

  private void runStartNowOrSchedule() throws CLIException
  {
    if (args.startArg.isPresent())
    {
      uData.setStartDate(args.getStartDateTime());
      uData.setStartNow(args.isStartNow());
    }
    if (args.recurringArg.isPresent())
    {
      uData.setRecurringDateTime(args.getRecurringDateTime());
      uData.setStartNow(false);
    }
    if (!args.startArg.isPresent() &&
        !args.recurringArg.isPresent())
    {
      askToStartNowOrSchedule();
    }
  }

  private void askToStartNowOrSchedule() throws CLIException
  {
    checkHeaderDisplay();

    MenuBuilder<ScheduleOption> builder = new MenuBuilder<ScheduleOption>(app);
    builder.setPrompt(INFO_TASK_SCHEDULE_PROMPT.get(taskName));
    builder.addCancelOption(false);
    for (ScheduleOption choice : ScheduleOption.values())
    {
      MenuResult<ScheduleOption> result = MenuResult.success(choice);
      if (choice == ScheduleOption.defaultValue())
      {
        builder.setDefault(choice.getPrompt(), result);
      }
      builder.addNumberedOption(choice.getPrompt(), result);
    }
    MenuResult<ScheduleOption> m = builder.toMenu().run();
    if (m.isSuccess())
    {
      switch (m.getValue())
      {
        case RUN_NOW:
          uData.setStartNow(true);
          break;
        case RUN_LATER:
          uData.setStartNow(false);
          askForStartDate();
          break;
        case SCHEDULE_TASK:
          uData.setStartNow(false);
          askForTaskSchedule();
          break;
      }
    }
    else
    {
      throw new CLIException(Message.EMPTY);
    }
  }

  private void askForStartDate() throws CLIException
  {
    checkHeaderDisplay();

    Date startDate = null;
    while (startDate == null)
    {
      String sDate = app.readInput(INFO_TASK_START_DATE_PROMPT.get(), null);
      try {
        startDate = StaticUtils.parseDateTimeString(sDate);
        // Check that the provided date is not previous to the current date.
        Date currentDate = new Date(System.currentTimeMillis());
        if (currentDate.after(startDate))
        {
          app.printProgress(ERR_START_DATETIME_ALREADY_PASSED.get(sDate));
          app.printlnProgress();
          app.printlnProgress();
          startDate = null;
        }
      } catch (ParseException pe) {
        app.println(ERR_START_DATETIME_FORMAT.get());
        app.println();
      }
    }
    uData.setStartDate(startDate);
  }

  private void askForTaskSchedule() throws CLIException
  {
    checkHeaderDisplay();

    String schedule = null;

    while (schedule == null)
    {
      schedule = app.readInput(INFO_TASK_RECURRING_SCHEDULE_PROMPT.get(),
          null);
      try
      {
        RecurringTask.parseTaskTab(schedule);
        app.printlnProgress();
      }
      catch (DirectoryException de)
      {
        schedule = null;
        app.println(ERR_RECURRING_SCHEDULE_FORMAT_ERROR.get(
            de.getMessageObject()));
        app.println();
      }
    }
    uData.setRecurringDateTime(schedule);
  }

  private void checkHeaderDisplay()
  {
    if (!headerDisplayed)
    {
      app.printlnProgress();
      app.printProgress(INFO_TASK_SCHEDULE_PROMPT_HEADER.get());
      app.printlnProgress();
      headerDisplayed = true;
    }
    app.printlnProgress();

  }
}
