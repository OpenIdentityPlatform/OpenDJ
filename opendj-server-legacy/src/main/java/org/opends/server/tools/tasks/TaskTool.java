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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools.tasks;

import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.backends.task.FailedDependencyAction;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPConnectionException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.cli.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentGroup;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.StringArgument;

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
  public static final String NOW = TaskScheduleArgs.NOW;

  /**
   * The error code used by the mixed-script to know if the java
   * arguments for the off-line mode must be used.
   */
  private static final int RUN_OFFLINE = 51;
  /**
   * The error code used by the mixed-script to know if the java
   * arguments for the on-line mode must be used.
   */
  private static final int RUN_ONLINE = 52;

  /**
   * Number of milliseconds this utility will wait before reloading
   * this task's entry in the directory while it is polling for status.
   */
  private static final int SYNCHRONOUS_TASK_POLL_INTERVAL = 1000;

  private LDAPConnectionArgumentParser argParser;

  private TaskScheduleArgs taskScheduleArgs;

  /**
   * Argument used to know whether we must test if we must run in off-line mode.
   */
  private BooleanArgument testIfOfflineArg;

  /** This CLI is always using the administration connector with SSL. */
  private static final boolean alwaysSSL = true;

  /**
   * Called when this utility should perform its actions locally in this
   * JVM.
   *
   * @param initializeServer indicates whether to initialize the
   *        directory server in the case of a local action
   * @param out stream to write messages; may be null
   * @param err stream to write messages; may be null
   * @return int indicating the result of this action
   */
  protected abstract int processLocal(boolean initializeServer,
                                      PrintStream out,
                                      PrintStream err);

  /**
   * Cleanup task environment after offline run.
   * Default implementation does nothing.
   * Tasks which initialize some static part of the DirectoryServer instance (e.g admin data local backends) must
   * override this method to shutdown all needed component to prevent JVM environment alteration.
   */
  protected void cleanup()
  {
    // Do nothing by default.
  }

  /**
   * Creates an argument parser prepopulated with arguments for processing
   * input for scheduling tasks with the task backend.
   *
   * @param className of this tool
   * @param toolDescription of this tool
   * @return LDAPConnectionArgumentParser for processing CLI input
   */
  protected LDAPConnectionArgumentParser createArgParser(String className,
    LocalizableMessage toolDescription)
  {
    ArgumentGroup ldapGroup = new ArgumentGroup(
      INFO_DESCRIPTION_TASK_LDAP_ARGS.get(), 1001);

    argParser = new LDAPConnectionArgumentParser(className,
      toolDescription, false, ldapGroup, alwaysSSL);

    ArgumentGroup taskGroup = new ArgumentGroup(
      INFO_DESCRIPTION_TASK_TASK_ARGS.get(), 1000);

    try {
      StringArgument propertiesFileArgument =
          propertiesFileArgument();
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      BooleanArgument noPropertiesFileArgument =
          noPropertiesFileArgument();
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      taskScheduleArgs = new TaskScheduleArgs();

      for (Argument arg : taskScheduleArgs.getArguments())
      {
        argParser.addArgument(arg, taskGroup);
      }

      testIfOfflineArg =
              BooleanArgument.builder("testIfOffline")
                      .description(INFO_DESCRIPTION_TEST_IF_OFFLINE.get())
                      .hidden()
                      .buildAndAddToParser(argParser);
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
   * @throws ArgumentException if there is a problem with the arguments.
   * @throws ClientException if there is a problem with one of the values provided
   * by the user.
   */
  protected void validateTaskArgs() throws ArgumentException, ClientException
  {
    if (isRemoteTask())
    {
      taskScheduleArgs.validateArgs();
    }
    else
    {
      // server is offline => output logs to the console
      JDKLogging.enableConsoleLoggingForOpenDJTool();
      taskScheduleArgs.validateArgsIfOffline();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Date getStartDateTime() {
    return taskScheduleArgs.getStartDateTime();
  }

  /** {@inheritDoc} */
  @Override
  public String getRecurringDateTime() {
    return taskScheduleArgs.getRecurringDateTime();
  }

  /** {@inheritDoc} */
  @Override
  public List<String> getDependencyIds() {
    return taskScheduleArgs.getDependencyIds();
  }

  /** {@inheritDoc} */
  @Override
  public FailedDependencyAction getFailedDependencyAction() {
    return taskScheduleArgs.getFailedDependencyAction();
  }

  /** {@inheritDoc} */
  @Override
  public List<String> getNotifyUponCompletionEmailAddresses() {
    return taskScheduleArgs.getNotifyUponCompletionEmailAddresses();
  }

  /** {@inheritDoc} */
  @Override
  public List<String> getNotifyUponErrorEmailAddresses() {
    return taskScheduleArgs.getNotifyUponErrorEmailAddresses();
  }

  /**
   * Either invokes initiates this tool's local action or schedule this
   * tool using the tasks interface based on user input.
   *
   * @param argParser used to parse user arguments
   * @param initializeServer indicates whether to initialize the
   *        directory server in the case of a local action
   * @param out stream to write messages; may be null
   * @param err stream to write messages; may be null
   * @return int indicating the result of this action
   */
  protected int process(LDAPConnectionArgumentParser argParser,
                        boolean initializeServer,
                        PrintStream out, PrintStream err) {
    if (testIfOffline())
    {
      if (!isRemoteTask())
      {
        return RUN_OFFLINE;
      }
      else
      {
        return RUN_ONLINE;
      }
    }

    if (!isRemoteTask())
    {
      try
      {
        return processLocal(initializeServer, out, err);
      }
      finally
      {
        if (initializeServer)
        {
          cleanup();
        }
      }
    }

    if (initializeServer)
    {
      try
      {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_SERVER_BOOTSTRAP_ERROR.get(getExceptionMessage(e)));
        return 1;
      }
    }

    LDAPConnection conn = null;
    try {
      conn = argParser.connect(out, err);
      TaskClient tc = new TaskClient(conn);
      TaskEntry taskEntry = tc.schedule(this);
      LocalizableMessage startTime = taskEntry.getScheduledStartTime();
      if (taskEntry.getTaskState() == TaskState.RECURRING) {
        printWrappedText(out, INFO_TASK_TOOL_RECURRING_TASK_SCHEDULED.get(taskEntry.getType(), taskEntry.getId()));
      } else if (startTime == null || startTime.length() == 0) {
        printWrappedText(out, INFO_TASK_TOOL_TASK_SCHEDULED_NOW.get(taskEntry.getType(), taskEntry.getId()));
      } else {
        printWrappedText(out, INFO_TASK_TOOL_TASK_SCHEDULED_FUTURE.get(
            taskEntry.getType(), taskEntry.getId(), taskEntry.getScheduledStartTime()));
      }
      if (!taskScheduleArgs.startArg.isPresent()) {

        // Poll the task printing log messages until finished
        String taskId = taskEntry.getId();
        Set<LocalizableMessage> printedLogMessages = new HashSet<>();
        do {
          taskEntry = tc.getTaskEntry(taskId);
          List<LocalizableMessage> logs = taskEntry.getLogMessages();
          for (LocalizableMessage log : logs) {
            if (printedLogMessages.add(log)) {
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
          if (taskEntry.getTaskState() != TaskState.RECURRING) {
            printWrappedText(out, INFO_TASK_TOOL_TASK_SUCESSFULL.get(taskEntry.getType(), taskEntry.getId()));
          }
          return 0;
        } else {
          printWrappedText(out, INFO_TASK_TOOL_TASK_NOT_SUCESSFULL.get(taskEntry.getType(), taskEntry.getId()));
          return 1;
        }
      }
      return 0;
    } catch (LDAPConnectionException e) {
      if (isWrongPortException(e,
          Integer.valueOf(argParser.getArguments().getPort())))
      {
        printWrappedText(err, ERR_TASK_LDAP_FAILED_TO_CONNECT_WRONG_PORT.get(
            argParser.getArguments().getHostName(), argParser.getArguments().getPort()));
      } else {
        printWrappedText(err, ERR_TASK_TOOL_START_TIME_NO_LDAP.get(e.getMessage()));
      }
      return 1;
    } catch (DecodeException ae) {
      printWrappedText(err, ERR_TASK_TOOL_DECODE_ERROR.get(ae.getMessage()));
      return 1;
    } catch (IOException ioe) {
      printWrappedText(err, ERR_TASK_TOOL_IO_ERROR.get(ioe));
      return 1;
    } catch (LDAPException le) {
      printWrappedText(err, ERR_TASK_TOOL_LDAP_ERROR.get(le.getMessage()));
      return 1;
    } catch (OpenDsException e) {
      printWrappedText(err, e.getMessageObject());
      return 1;
    } catch (ArgumentException e) {
      argParser.displayMessageAndUsageReference(err, e.getMessageObject());
      return 1;
    }
    finally
    {
      if (conn != null)
      {
        try
        {
          conn.close(null);
        }
        catch (Throwable t)
        {
          // Ignore.
        }
      }
    }
  }

  private boolean isRemoteTask() {
    return argParser.connectionArgumentsPresent();
  }

  /**
   * Returns {@code true} if the provided exception was caused by trying to
   * connect to the wrong port and {@code false} otherwise.
   * @param t the exception to be analyzed.
   * @param port the port to which we tried to connect.
   * @return {@code true} if the provided exception was caused by trying to
   * connect to the wrong port and {@code false} otherwise.
   */
  private boolean isWrongPortException(Throwable t, int port)
  {
    boolean isWrongPortException = false;
    boolean isDefaultClearPort = (port - 389) % 1000 == 0;
    while (t != null && isDefaultClearPort)
    {
      isWrongPortException = t instanceof java.net.SocketTimeoutException;
      if (!isWrongPortException)
      {
        t = t.getCause();
      }
      else
      {
        break;
      }
    }
    return isWrongPortException;
  }


  /**
   * Indicates whether we must return if the command must be run in off-line
   * mode.
   * @return <CODE>true</CODE> if we must return if the command must be run in
   * off-line mode and <CODE>false</CODE> otherwise.
   */
  public boolean testIfOffline()
  {
    boolean returnValue = false;
    if (testIfOfflineArg != null)
    {
      returnValue = testIfOfflineArg.isPresent();
    }
    return returnValue;
  }

  /**
   * Checks that binary version and instance version are the same.
   *
   * @throws InitializationException
   *           If versions mismatch
   */
  protected void checkVersion() throws InitializationException
  {
    // FIXME Do not perform this check if the tool is use in remote mode (see OPENDJ-1166)
    BuildVersion.checkVersionMismatch();
  }
}
