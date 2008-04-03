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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.tools.tasks;

import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.ArgumentGroup;
import static org.opends.server.util.StaticUtils.wrapText;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import org.opends.server.util.StaticUtils;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPConnectionException;
import static org.opends.server.tools.ToolConstants.*;

import org.opends.server.types.LDAPException;
import org.opends.server.types.OpenDsException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.backends.task.TaskState;
import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.*;

import java.io.PrintStream;
import java.text.ParseException;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.EnumSet;
import java.util.Collections;
import java.io.IOException;

/**
 * Base class for tools that are capable of operating either by running
 * local within this JVM or by scheduling a task to perform the same
 * action running within the directory server through the tasks interface.
 */
public abstract class TaskTool implements TaskScheduleInformation {

  /**
   * Magic value used to indicate that the user would like to schedule
   * this operation to run immediately as a task as opposed to running
   * the operation in the local VM.
   */
  public static final String NOW = "0";

  private static final int RUN_OFFLINE = 51;
  private static final int RUN_ONLINE = 52;

  // Number of milliseconds this utility will wait before reloading
  // this task's entry in the directory while it is polling for status
  private static final int SYNCHRONOUS_TASK_POLL_INTERVAL = 1000;

  LDAPConnectionArgumentParser argParser;

  // Argument for describing the task's start time
  StringArgument startArg;

  // Argument for specifying completion notifications
  StringArgument completionNotificationArg;

  // Argument for specifying error notifications
  StringArgument errorNotificationArg;

  // Argument for specifying dependency
  StringArgument dependencyArg;

  // Argument for specifying a failed dependency action
  StringArgument failedDependencyActionArg;

  // Client for interacting with the task backend
  TaskClient taskClient;

  // Argument used to know whether we must test if we must run in offline
  // mode.
  BooleanArgument testIfOfflineArg;

  /**
   * Called when this utility should perform its actions locally in this
   * JVM.
   *
   * @param initializeServer indicates whether or not to initialize the
   *        directory server in the case of a local action
   * @param out stream to write messages; may be null
   * @param err stream to write messages; may be null
   * @return int indicating the result of this action
   */
  abstract protected int processLocal(boolean initializeServer,
                                      PrintStream out,
                                      PrintStream err);

  /**
   * Creates an argument parser prepopulated with arguments for processing
   * input for scheduling tasks with the task backend.
   *
   * @param className of this tool
   * @param toolDescription of this tool
   * @return LDAPConnectionArgumentParser for processing CLI input
   */
  protected LDAPConnectionArgumentParser createArgParser(String className,
      Message toolDescription)
    {
    ArgumentGroup ldapGroup = new ArgumentGroup(
            INFO_DESCRIPTION_TASK_LDAP_ARGS.get(), 1001);

    argParser = new LDAPConnectionArgumentParser(className,
            toolDescription, false, ldapGroup);

    ArgumentGroup taskGroup = new ArgumentGroup(
            INFO_DESCRIPTION_TASK_TASK_ARGS.get(), 1000);

    try {
      StringArgument propertiesFileArgument = new StringArgument(
          "propertiesFilePath",
          null, OPTION_LONG_PROP_FILE_PATH,
          false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

     BooleanArgument noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
     argParser.addArgument(noPropertiesFileArgument);
     argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      startArg = new StringArgument(
              OPTION_LONG_START_DATETIME,
              OPTION_SHORT_START_DATETIME,
              OPTION_LONG_START_DATETIME, false, false,
              true, INFO_START_DATETIME_PLACEHOLDER.get(),
              null, null,
              INFO_DESCRIPTION_START_DATETIME.get());
      argParser.addArgument(startArg, taskGroup);

      completionNotificationArg = new StringArgument(
              OPTION_LONG_COMPLETION_NOTIFICATION_EMAIL,
              OPTION_SHORT_COMPLETION_NOTIFICATION_EMAIL,
              OPTION_LONG_COMPLETION_NOTIFICATION_EMAIL,
              false, true, true, INFO_EMAIL_ADDRESS_PLACEHOLDER.get(),
              null, null, INFO_DESCRIPTION_TASK_COMPLETION_NOTIFICATION.get());
      argParser.addArgument(completionNotificationArg, taskGroup);

      errorNotificationArg = new StringArgument(
              OPTION_LONG_ERROR_NOTIFICATION_EMAIL,
              OPTION_SHORT_ERROR_NOTIFICATION_EMAIL,
              OPTION_LONG_ERROR_NOTIFICATION_EMAIL,
              false, true, true, INFO_EMAIL_ADDRESS_PLACEHOLDER.get(),
              null, null, INFO_DESCRIPTION_TASK_ERROR_NOTIFICATION.get());
      argParser.addArgument(errorNotificationArg, taskGroup);

      dependencyArg = new StringArgument(
              OPTION_LONG_DEPENDENCY,
              OPTION_SHORT_DEPENDENCY,
              OPTION_LONG_DEPENDENCY,
              false, true, true, INFO_TASK_ID_PLACEHOLDER.get(),
              null, null, INFO_DESCRIPTION_TASK_DEPENDENCY_ID.get());
      argParser.addArgument(dependencyArg, taskGroup);

      Set fdaValSet = EnumSet.allOf(FailedDependencyAction.class);
      failedDependencyActionArg = new StringArgument(
              OPTION_LONG_FAILED_DEPENDENCY_ACTION,
              OPTION_SHORT_FAILED_DEPENDENCY_ACTION,
              OPTION_LONG_FAILED_DEPENDENCY_ACTION,
              false, true, true, INFO_ACTION_PLACEHOLDER.get(),
              null, null, INFO_DESCRIPTION_TASK_FAILED_DEPENDENCY_ACTION.get(
                StaticUtils.collectionToString(fdaValSet, ","),
                FailedDependencyAction.defaultValue().name()));
      argParser.addArgument(failedDependencyActionArg, taskGroup);

      testIfOfflineArg = new BooleanArgument(
          "testIfOffline", null, "testIfOffline",
          INFO_DESCRIPTION_TEST_IF_OFFLINE.get());
      testIfOfflineArg.setHidden(true);
      argParser.addArgument(testIfOfflineArg);

    } catch (ArgumentException e) {
      // should never happen
    }

    return argParser;
  }

  /**
   * Validates arguments related to task scheduling.  This should be
   * called after the <code>ArgumentParser.parseArguments</code> has
   * been called.
   *
   * @throws ArgumentException if there is a problem with the arguments
   */
  protected void validateTaskArgs() throws ArgumentException {
    if (startArg.isPresent() && !NOW.equals(startArg.getValue())) {
      try {
        StaticUtils.parseDateTimeString(startArg.getValue());
      } catch (ParseException pe) {
        throw new ArgumentException(ERR_START_DATETIME_FORMAT.get());
      }
    }

    if (!processAsTask() && completionNotificationArg.isPresent()) {
      throw new ArgumentException(ERR_TASKTOOL_OPTIONS_FOR_TASK_ONLY.get(
              completionNotificationArg.getLongIdentifier()));
    }

    if (!processAsTask() && errorNotificationArg.isPresent()) {
      throw new ArgumentException(ERR_TASKTOOL_OPTIONS_FOR_TASK_ONLY.get(
              errorNotificationArg.getLongIdentifier()));
    }

    if (!processAsTask() && dependencyArg.isPresent()) {
      throw new ArgumentException(ERR_TASKTOOL_OPTIONS_FOR_TASK_ONLY.get(
              dependencyArg.getLongIdentifier()));
    }

    if (!processAsTask() && failedDependencyActionArg.isPresent()) {
      throw new ArgumentException(ERR_TASKTOOL_OPTIONS_FOR_TASK_ONLY.get(
              failedDependencyActionArg.getLongIdentifier()));
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
        Set fdaValSet = EnumSet.allOf(FailedDependencyAction.class);
        throw new ArgumentException(ERR_TASKTOOL_INVALID_FDA.get(fda,
                        StaticUtils.collectionToString(fdaValSet, ",")));
      }
    }
  }

  /**
   * {@inheritDoc}
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
   * {@inheritDoc}
   */
  public List<String> getDependencyIds() {
    if (dependencyArg.isPresent()) {
      return dependencyArg.getValues();
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * {@inheritDoc}
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
   * {@inheritDoc}
   */
  public List<String> getNotifyUponCompletionEmailAddresses() {
    if (completionNotificationArg.isPresent()) {
      return completionNotificationArg.getValues();
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * {@inheritDoc}
   */
  public List<String> getNotifyUponErrorEmailAddresses() {
    if (errorNotificationArg.isPresent()) {
      return errorNotificationArg.getValues();
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Either invokes initiates this tool's local action or schedule this
   * tool using the tasks interface based on user input.
   *
   * @param argParser used to parse user arguments
   * @param initializeServer indicates whether or not to initialize the
   *        directory server in the case of a local action
   * @param out stream to write messages; may be null
   * @param err stream to write messages; may be null
   * @return int indicating the result of this action
   */
  protected int process(LDAPConnectionArgumentParser argParser,
                        boolean initializeServer,
                        PrintStream out, PrintStream err) {
    int ret;

    if (testIfOffline())
    {
      if (!processAsTask())
      {
        return RUN_OFFLINE;
      }
      else
      {
        return RUN_ONLINE;
      }
    }

    if (processAsTask())
    {
      if (initializeServer)
      {
        try
        {
          DirectoryServer.bootstrapClient();
          DirectoryServer.initializeJMX();
        }
        catch (Exception e)
        {
          Message message = ERR_SERVER_BOOTSTRAP_ERROR.get(
                  getExceptionMessage(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      try {
        LDAPConnection conn = argParser.connect(out, err);
        TaskClient tc = new TaskClient(conn);
        TaskEntry taskEntry = tc.schedule(this);
        Message startTime = taskEntry.getScheduledStartTime();
        if (startTime == null || startTime.length() == 0) {
          out.println(
                  wrapText(INFO_TASK_TOOL_TASK_SCHEDULED_NOW.get(
                          taskEntry.getType(),
                          taskEntry.getId()),
                  MAX_LINE_WIDTH));

        } else {
          out.println(
                  wrapText(INFO_TASK_TOOL_TASK_SCHEDULED_FUTURE.get(
                          taskEntry.getType(),
                          taskEntry.getId(),
                          taskEntry.getScheduledStartTime()),
                  MAX_LINE_WIDTH));
        }
        if (!startArg.isPresent()) {

          // Poll the task printing log messages until finished
          String taskId = taskEntry.getId();
          Set<Message> printedLogMessages = new HashSet<Message>();
          do {
            taskEntry = tc.getTaskEntry(taskId);
            List<Message> logs = taskEntry.getLogMessages();
            for (Message log : logs) {
              if (!printedLogMessages.contains(log)) {
                printedLogMessages.add(log);
                out.println(log);
              }
            }

            try {
              Thread.sleep(SYNCHRONOUS_TASK_POLL_INTERVAL);
            } catch (InterruptedException e) {
              // ignore
            }

          } while (!taskEntry.isDone());
          if (TaskState.isSuccessful(taskEntry.getTaskState())) {
            out.println(
                wrapText(INFO_TASK_TOOL_TASK_SUCESSFULL.get(
                        taskEntry.getType(),
                        taskEntry.getId()),
                MAX_LINE_WIDTH));

            return 0;
          } else {
            out.println(
                wrapText(INFO_TASK_TOOL_TASK_NOT_SUCESSFULL.get(
                        taskEntry.getType(),
                        taskEntry.getId()),
                MAX_LINE_WIDTH));
            return 1;
          }
        }
        ret = 0;
      } catch (LDAPConnectionException e) {
        Message message = ERR_TASK_TOOL_START_TIME_NO_LDAP.get(e.getMessage());
        if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
        ret = 1;
      } catch (IOException ioe) {
        Message message = ERR_TASK_TOOL_IO_ERROR.get(String.valueOf(ioe));
        if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
        ret = 1;
      } catch (ASN1Exception ae) {
        Message message = ERR_TASK_TOOL_DECODE_ERROR.get(ae.getMessage());
        if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
        ret = 1;
      } catch (LDAPException le) {
        Message message = ERR_TASK_TOOL_DECODE_ERROR.get(le.getMessage());
        if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
        ret = 1;
      } catch (OpenDsException e) {
        Message message = e.getMessageObject();
        if (err != null) err.println(wrapText(message, MAX_LINE_WIDTH));
        ret = 1;
      }
    } else {
      ret = processLocal(initializeServer, out, err);
    }
    return ret;
  }

  private boolean processAsTask() {
    return argParser.connectionArgumentsPresent();
  }

  /**
   * Indicates whether we must return if the command must be run in offline
   * mode.
   * @return <CODE>true</CODE> if we must return if the command must be run in
   * offline mode and <CODE>false</CODE> otherwise.
   */
  private boolean testIfOffline()
  {
    boolean returnValue = false;
    if (testIfOfflineArg != null)
    {
      returnValue = testIfOfflineArg.isPresent();
    }
    return returnValue;
  }
}
