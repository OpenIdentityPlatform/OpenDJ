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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.tools.tasks.TaskClient;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDAPException;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.LDAPConnectionArgumentParser;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuCallback;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TextTablePrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.opends.messages.ToolMessages.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

/** Tool for getting information and managing tasks in the Directory Server. */
public class ManageTasks extends ConsoleApplication {
  /** This CLI is always using the administration connector with SSL. */
  private static final boolean alwaysSSL = true;

  /**
   * The main method for TaskInfo tool.
   *
   * @param args The command-line arguments provided to this program.
   */
  public static void main(String[] args) {
    int retCode = mainTaskInfo(args, System.out, System.err);
    if (retCode != 0) {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param args             The command-line arguments provided to this
   * @param out              The output stream to use for standard output, or
   *                         {@code null} if standard output is not needed.
   * @param err              The output stream to use for standard error, or
   *                         {@code null} if standard error is not needed.
   * @param initializeServer Indicates whether to initialize the server.
   * @return int return code
   */
  public static int mainTaskInfo(String[] args,
                                 OutputStream out,
                                 OutputStream err,
                                 boolean initializeServer) {
    ManageTasks tool = new ManageTasks(out, err);
    return tool.process(args, initializeServer);
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param args             The command-line arguments provided to this
   * @param out              The output stream to use for standard output, or
   *                         {@code null} if standard output is not needed.
   * @param err              The output stream to use for standard error, or
   *                         {@code null} if standard error is not needed.
   * @return int return code
   */
  private static int mainTaskInfo(String[] args,
                                 OutputStream out,
                                 OutputStream err) {
    return mainTaskInfo(args, out, err, true);
  }

  private static final int INDENT = 2;

  /** ID of task for which to display details and exit. */
  private StringArgument task;
  /** Indicates print summary and exit. */
  private BooleanArgument summary;
  /** ID of task to cancel. */
  private StringArgument cancel;
  /** Argument used to request non-interactive behavior. */
  private BooleanArgument noPrompt;

  /** Accesses the directory's task backend. */
  private TaskClient taskClient;

  /**
   * Constructs a parameterized instance.
   * @param out              The output stream to use for standard output, or
   *                         {@code null} if standard output is not needed.
   * @param err              The output stream to use for standard error, or
   *                         {@code null} if standard error is not needed.
   */
  private ManageTasks(OutputStream out, OutputStream err)
  {
    super(new PrintStream(out), new PrintStream(err));
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param args       The command-line arguments provided to this
   *                   program.
   * @param initializeServer  Indicates whether to initialize the server.
   * @return The error code.
   */
  private int process(String[] args, boolean initializeServer)
  {
    if (initializeServer)
    {
      DirectoryServer.bootstrapClient();
    }
    JDKLogging.disableLogging();

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser = new LDAPConnectionArgumentParser(
            "org.opends.server.tools.TaskInfo",
            INFO_TASKINFO_TOOL_DESCRIPTION.get(),
            false, null, alwaysSSL);
    argParser.setShortToolDescription(REF_SHORT_DESC_MANAGE_TASKS.get());

    // Initialize all the command-line argument types and register them with the parser
    try {
      StringArgument propertiesFileArgument =
              StringArgument.builder(OPTION_LONG_PROP_FILE_PATH)
                      .description(INFO_DESCRIPTION_PROP_FILE_PATH.get())
                      .valuePlaceholder(INFO_PROP_FILE_PATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      BooleanArgument noPropertiesFileArgument =
              BooleanArgument.builder(OPTION_LONG_NO_PROP_FILE)
                      .description(INFO_DESCRIPTION_NO_PROP_FILE.get())
                      .buildAndAddToParser(argParser);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      task =
              StringArgument.builder("info")
                      .shortIdentifier('i')
                      .description(INFO_TASKINFO_TASK_ARG_DESCRIPTION.get())
                      .valuePlaceholder(INFO_TASK_ID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      cancel =
              StringArgument.builder("cancel")
                      .shortIdentifier('c')
                      .description(INFO_TASKINFO_TASK_ARG_CANCEL.get())
                      .valuePlaceholder(INFO_TASK_ID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      summary =
              BooleanArgument.builder("summary")
                      .shortIdentifier('s')
                      .description(INFO_TASKINFO_SUMMARY_ARG_DESCRIPTION.get())
                      .buildAndAddToParser(argParser);

      noPrompt = noPromptArgument();
      argParser.addArgument(noPrompt);

      BooleanArgument displayUsage = showUsageArgument();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae) {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return 1;
    }

    argParser.getArguments().initArgumentsWithConfiguration(argParser);
    // Parse the command-line arguments provided to this program.
    try {
      argParser.parseArguments(args);
      StaticUtils.checkOnlyOneArgPresent(task, summary, cancel);
    }
    catch (ArgumentException ae) {
      argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }

    if (!argParser.usageOrVersionDisplayed()) {
      // Checks the version - if upgrade required, the tool is unusable
      try
      {
        BuildVersion.checkVersionMismatch();
      }
      catch (InitializationException e)
      {
        println(e.getMessageObject());
        return 1;
      }

      try {
        LDAPConnectionConsoleInteraction ui =
                new LDAPConnectionConsoleInteraction(
                        this, argParser.getArguments());

        taskClient = new TaskClient(argParser.connect(ui,
                getOutputStream(), getErrorStream()));

        if (isMenuDrivenMode()) {
          // Keep prompting the user until they specify quit of there is a fatal exception
          while (true) {
            getOutputStream().println();
            Menu<Void> menu = getSummaryMenu();
            MenuResult<Void> result = menu.run();
            if (result.isQuit()) {
              return 0;
            }
          }
        } else if (task.isPresent()) {
          getOutputStream().println();
          MenuResult<TaskEntry> r = new PrintTaskInfo(task.getValue()).invoke(this);
          if (r.isAgain())
          {
            return 1;
          }
        } else if (summary.isPresent()) {
          getOutputStream().println();
          printSummaryTable();
        } else if (cancel.isPresent()) {
          MenuResult<TaskEntry> r = new CancelTask(cancel.getValue()).invoke(this);
          if (r.isAgain())
          {
            return 1;
          }
        } else if (!isInteractive()) {
           // no-prompt option
           getOutputStream().println();
           printSummaryTable();
           return 0;
        }
      } catch (LDAPConnectionException lce) {
        println(INFO_TASKINFO_LDAP_EXCEPTION.get(lce.getMessageObject()));
        return 1;
      } catch (Exception e) {
        println(LocalizableMessage.raw(StaticUtils.getExceptionMessage(e)));
        return 1;
      }
    }
    return 0;
  }

  @Override
  public boolean isAdvancedMode() {
    return false;
  }

  @Override
  public boolean isInteractive() {
    return !noPrompt.isPresent();
  }

  @Override
  public boolean isMenuDrivenMode() {
    return !task.isPresent() && !cancel.isPresent() && !summary.isPresent() && !noPrompt.isPresent();
  }

  @Override
  public boolean isQuiet() {
    return false;
  }

  @Override
  public boolean isScriptFriendly() {
    return false;
  }

  @Override
  public boolean isVerbose() {
    return false;
  }

  /**
   * Creates the summary table.
   *
   * @throws IOException if there is a problem with screen I/O
   * @throws LDAPException if there is a problem getting information
   *         out to the directory
   * @throws DecodeException if there is a problem with the encoding
   */
  private void printSummaryTable()
          throws LDAPException, IOException, DecodeException {
    List<TaskEntry> entries = taskClient.getTaskEntries();
    if (!entries.isEmpty()) {
      TableBuilder table = new TableBuilder();
      Map<String, TaskEntry> mapIdToEntry = new TreeMap<>();
      for (TaskEntry entry : entries) {
        String taskId = entry.getId();
        if (taskId != null) {
          mapIdToEntry.put(taskId, entry);
        }
      }

      table.appendHeading(INFO_TASKINFO_FIELD_ID.get());
      table.appendHeading(INFO_TASKINFO_FIELD_TYPE.get());
      table.appendHeading(INFO_TASKINFO_FIELD_STATUS.get());
      for (String taskId : mapIdToEntry.keySet()) {
        TaskEntry entryWrapper = mapIdToEntry.get(taskId);
        table.startRow();
        table.appendCell(taskId);
        table.appendCell(entryWrapper.getType());
        table.appendCell(entryWrapper.getState());
      }
      StringWriter sw = new StringWriter();
      TextTablePrinter tablePrinter = new TextTablePrinter(sw);
      tablePrinter.setIndentWidth(INDENT);
      tablePrinter.setTotalWidth(80);
      table.print(tablePrinter);
      getOutputStream().println(LocalizableMessage.raw(sw.getBuffer()));
    } else {
      getOutputStream().println(INFO_TASKINFO_NO_TASKS.get());
      getOutputStream().println();
    }
  }

  /**
   * Creates the summary table.
   *
   * @return list of strings of IDs of all the tasks in the table in order
   *         of the indexes printed in the table
   * @throws IOException if there is a problem with screen I/O
   * @throws LDAPException if there is a problem getting information
   *         out to the directory
   * @throws DecodeException if there is a problem with the encoding
   */
  private Menu<Void> getSummaryMenu()
          throws LDAPException, IOException, DecodeException {
    List<String> taskIds = new ArrayList<>();
    List<Integer> cancelableIndices = new ArrayList<>();
    List<TaskEntry> entries = taskClient.getTaskEntries();
    MenuBuilder<Void> menuBuilder = new MenuBuilder<>(this);
    if (!entries.isEmpty()) {
      Map<String, TaskEntry> mapIdToEntry = new TreeMap<>();
      for (TaskEntry entry : entries) {
        String taskId = entry.getId();
        if (taskId != null) {
          mapIdToEntry.put(taskId, entry);
        }
      }

      menuBuilder.setColumnHeadings(
              INFO_TASKINFO_FIELD_ID.get(),
              INFO_TASKINFO_FIELD_TYPE.get(),
              INFO_TASKINFO_FIELD_STATUS.get());
      menuBuilder.setColumnWidths(null, null, 0);
      int index = 0;
      for (final String taskId : mapIdToEntry.keySet()) {
        taskIds.add(taskId);
        final TaskEntry taskEntry = mapIdToEntry.get(taskId);
        menuBuilder.addNumberedOption(
                LocalizableMessage.raw(taskEntry.getId()),
                new TaskDrilldownMenu(taskId),
                taskEntry.getType(), taskEntry.getState());
        index++;
        if (taskEntry.isCancelable()) {
          cancelableIndices.add(index);
        }
      }
    } else {
      getOutputStream().println(INFO_TASKINFO_NO_TASKS.get());
      getOutputStream().println();
    }

    menuBuilder.addCharOption(
            INFO_TASKINFO_CMD_REFRESH_CHAR.get(),
            INFO_TASKINFO_CMD_REFRESH.get(),
            new PrintSummaryTop());

    if (!cancelableIndices.isEmpty()) {
      menuBuilder.addCharOption(
              INFO_TASKINFO_CMD_CANCEL_CHAR.get(),
              INFO_TASKINFO_CMD_CANCEL.get(),
              new CancelTaskTop(taskIds, cancelableIndices));
    }
    menuBuilder.addQuitOption();

    return menuBuilder.toMenu();
  }

  /**
   * Gets the client that can be used to interact with the task backend.
   *
   * @return TaskClient for interacting with the task backend.
   */
  public TaskClient getTaskClient() {
    return this.taskClient;
  }

  private static void printTable(TableBuilder table, int column, int width, StringWriter sw)
  {
    TextTablePrinter tablePrinter = new TextTablePrinter(sw);
    tablePrinter.setTotalWidth(80);
    tablePrinter.setIndentWidth(INDENT);
    tablePrinter.setColumnWidth(column, width);
    table.print(tablePrinter);
  }

  /** Base for callbacks that implement top level menu items. */
  private static abstract class TopMenuCallback
          implements MenuCallback<Void> {
    @Override
    public MenuResult<Void> invoke(ConsoleApplication app) throws ClientException {
      return invoke((ManageTasks)app);
    }

    /**
     * Called upon task invocation.
     *
     * @param app this console application
     * @return MessageResult result of task
     * @throws ClientException if there is a problem
     */
    protected abstract MenuResult<Void> invoke(ManageTasks app) throws ClientException;
  }

  /** Base for callbacks that manage task entries. */
  private static abstract class TaskOperationCallback
          implements MenuCallback<TaskEntry> {
    /** ID of the task to manage. */
    protected String taskId;

    /**
     * Constructs a parameterized instance.
     *
     * @param taskId if the task to examine
     */
    public TaskOperationCallback(String taskId) {
      this.taskId = taskId;
    }

    @Override
    public MenuResult<TaskEntry> invoke(ConsoleApplication app) throws ClientException
    {
      return invoke((ManageTasks)app);
    }

    /**
     * Invokes the task.
     *
     * @param app
     *          the current application running
     * @return how the application should proceed next
     * @throws ClientException
     *           if any problem occurred
     */
    protected abstract MenuResult<TaskEntry> invoke(ManageTasks app) throws ClientException;
  }

  /** Executable for printing a task summary table. */
  private static class PrintSummaryTop extends TopMenuCallback {
    @Override
    public MenuResult<Void> invoke(ManageTasks app) throws ClientException
    {
      // Since the summary table is reprinted every time,
      // the user enters the top level this task just returns 'success'
      return MenuResult.success();
    }
  }

  /** Executable for printing a particular task's details. */
  private static class TaskDrilldownMenu extends TopMenuCallback {
    private String taskId;

    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task for which information will be displayed
     */
    public TaskDrilldownMenu(String taskId) {
      this.taskId = taskId;
    }

    @Override
    public MenuResult<Void> invoke(ManageTasks app) throws ClientException {
      MenuResult<TaskEntry> res = new PrintTaskInfo(taskId).invoke(app);
      TaskEntry taskEntry = res.getValue();
      if (taskEntry != null) {
        while (true) {
          try {
            taskEntry = app.getTaskClient().getTaskEntry(taskId);

            // Show the menu
            MenuBuilder<TaskEntry> menuBuilder = new MenuBuilder<>(app);
            menuBuilder.addBackOption(true);
            menuBuilder.addCharOption(
                    INFO_TASKINFO_CMD_REFRESH_CHAR.get(),
                    INFO_TASKINFO_CMD_REFRESH.get(),
                    new PrintTaskInfo(taskId));
            List<LocalizableMessage> logs = taskEntry.getLogMessages();
            if (logs != null && !logs.isEmpty()) {
              menuBuilder.addCharOption(
                      INFO_TASKINFO_CMD_VIEW_LOGS_CHAR.get(),
                      INFO_TASKINFO_CMD_VIEW_LOGS.get(),
                      new ViewTaskLogs(taskId));
            }
            if (taskEntry.isCancelable() && !taskEntry.isDone()) {
              menuBuilder.addCharOption(
                      INFO_TASKINFO_CMD_CANCEL_CHAR.get(),
                      INFO_TASKINFO_CMD_CANCEL.get(),
                      new CancelTask(taskId));
            }
            menuBuilder.addQuitOption();
            Menu<TaskEntry> menu = menuBuilder.toMenu();
            MenuResult<TaskEntry> result = menu.run();
            if (result.isCancel()) {
              break;
            } else if (result.isQuit()) {
              System.exit(0);
            }
          } catch (Exception e) {
            app.println(LocalizableMessage.raw(StaticUtils.getExceptionMessage(e)));
          }
        }
      } else {
        app.println(ERR_TASKINFO_UNKNOWN_TASK_ENTRY.get(taskId));
      }
      return MenuResult.success();
    }
  }

  /** Executable for printing a particular task's details. */
  private static class PrintTaskInfo extends TaskOperationCallback {
    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task for which information will be printed
     */
    public PrintTaskInfo(String taskId) {
      super(taskId);
    }

    @Override
    public MenuResult<TaskEntry> invoke(ManageTasks app) throws ClientException
    {
      try {
        TaskEntry taskEntry = app.getTaskClient().getTaskEntry(taskId);

        TableBuilder table = new TableBuilder();
        table.appendHeading(INFO_TASKINFO_DETAILS.get());

        table.startRow();
        table.appendCell(INFO_TASKINFO_FIELD_ID.get());
        table.appendCell(taskEntry.getId());

        table.startRow();
        table.appendCell(INFO_TASKINFO_FIELD_TYPE.get());
        table.appendCell(taskEntry.getType());

        table.startRow();
        table.appendCell(INFO_TASKINFO_FIELD_STATUS.get());
        table.appendCell(taskEntry.getState());

        table.startRow();
        table.appendCell(INFO_TASKINFO_FIELD_SCHEDULED_START.get());

        if (TaskState.isRecurring(taskEntry.getTaskState())) {
          LocalizableMessage m = taskEntry.getScheduleTab();
          table.appendCell(m);
        } else {
          LocalizableMessage m = taskEntry.getScheduledStartTime();
          if (m == null || m.equals(LocalizableMessage.EMPTY)) {
            table.appendCell(INFO_TASKINFO_IMMEDIATE_EXECUTION.get());
          } else {
            table.appendCell(m);
          }

          table.startRow();
          table.appendCell(INFO_TASKINFO_FIELD_ACTUAL_START.get());
          table.appendCell(taskEntry.getActualStartTime());

          table.startRow();
          table.appendCell(INFO_TASKINFO_FIELD_COMPLETION_TIME.get());
          table.appendCell(taskEntry.getCompletionTime());
        }

        writeMultiValueCells(
                table,
                INFO_TASKINFO_FIELD_DEPENDENCY.get(),
                taskEntry.getDependencyIds());

        table.startRow();
        table.appendCell(INFO_TASKINFO_FIELD_FAILED_DEPENDENCY_ACTION.get());
        LocalizableMessage m = taskEntry.getFailedDependencyAction();
        table.appendCell(m != null ? m : INFO_TASKINFO_NONE.get());

        writeMultiValueCells(
                table,
                INFO_TASKINFO_FIELD_NOTIFY_ON_COMPLETION.get(),
                taskEntry.getCompletionNotificationEmailAddresses(),
                INFO_TASKINFO_NONE_SPECIFIED.get());

        writeMultiValueCells(
                table,
                INFO_TASKINFO_FIELD_NOTIFY_ON_ERROR.get(),
                taskEntry.getErrorNotificationEmailAddresses(),
                INFO_TASKINFO_NONE_SPECIFIED.get());

        StringWriter sw = new StringWriter();
        printTable(table, 1, 0, sw);
        app.getOutputStream().println();
        app.getOutputStream().println(LocalizableMessage.raw(sw.getBuffer().toString()));

        // Create a table for the task options
        table = new TableBuilder();
        table.appendHeading(INFO_TASKINFO_OPTIONS.get(taskEntry.getType()));
        Map<LocalizableMessage,List<String>> taskSpecificAttrs =
                taskEntry.getTaskSpecificAttributeValuePairs();
        for (LocalizableMessage attrName : taskSpecificAttrs.keySet()) {
          table.startRow();
          table.appendCell(attrName);
          List<String> values = taskSpecificAttrs.get(attrName);
          if (!values.isEmpty()) {
            table.appendCell(values.get(0));
          }
          if (values.size() > 1) {
            for (int i = 1; i < values.size(); i++) {
              table.startRow();
              table.appendCell();
              table.appendCell(values.get(i));
            }
          }
        }
        sw = new StringWriter();
        printTable(table, 1, 0, sw);
        app.getOutputStream().println(LocalizableMessage.raw(sw.getBuffer().toString()));

        // Print the last log message if any
        List<LocalizableMessage> logs = taskEntry.getLogMessages();
        if (logs != null && !logs.isEmpty()) {
          // Create a table for the last log entry
          table = new TableBuilder();
          table.appendHeading(INFO_TASKINFO_FIELD_LAST_LOG.get());
          table.startRow();
          table.appendCell(logs.get(logs.size() - 1));

          sw = new StringWriter();
          printTable(table, 0, 0, sw);
          app.getOutputStream().println(LocalizableMessage.raw(sw.getBuffer().toString()));
        }

        app.getOutputStream().println();
        return MenuResult.success(taskEntry);
      } catch (Exception e) {
        app.errPrintln(ERR_TASKINFO_RETRIEVING_TASK_ENTRY.get(taskId, e.getMessage()));
        return MenuResult.again();
      }
    }

    /**
     * Writes an attribute and associated values to the table.
     * @param table of task details
     * @param fieldLabel of attribute
     * @param values of the attribute
     */
    private void writeMultiValueCells(TableBuilder table,
                                      LocalizableMessage fieldLabel,
                                      List<?> values) {
      writeMultiValueCells(table, fieldLabel, values, INFO_TASKINFO_NONE.get());
    }

    /**
     * Writes an attribute and associated values to the table.
     *
     * @param table of task details
     * @param fieldLabel of attribute
     * @param values of the attribute
     * @param noneLabel label for the value column when there are no values
     */
    private void writeMultiValueCells(TableBuilder table,
                                      LocalizableMessage fieldLabel,
                                      List<?> values,
                                      LocalizableMessage noneLabel) {
      table.startRow();
      table.appendCell(fieldLabel);
      if (values.isEmpty()) {
        table.appendCell(noneLabel);
      } else {
        table.appendCell(values.get(0));
      }
      if (values.size() > 1) {
        for (int i = 1; i < values.size(); i++) {
          table.startRow();
          table.appendCell();
          table.appendCell(values.get(i));
        }
      }
    }
  }

  /** Executable for printing a particular task's details. */
  private static class ViewTaskLogs extends TaskOperationCallback {
    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task for which log records will be printed
     */
    public ViewTaskLogs(String taskId) {
      super(taskId);
    }

    @Override
    protected MenuResult<TaskEntry> invoke(ManageTasks app) throws ClientException
    {
      TaskEntry taskEntry = null;
      try {
        taskEntry = app.getTaskClient().getTaskEntry(taskId);
        List<LocalizableMessage> logs = taskEntry.getLogMessages();
        app.getOutputStream().println();

        // Create a table for the last log entry
        TableBuilder table = new TableBuilder();
        table.appendHeading(INFO_TASKINFO_FIELD_LOG.get());
        if (logs != null && !logs.isEmpty()) {
          for (LocalizableMessage log : logs) {
            table.startRow();
            table.appendCell(log);
          }
        } else {
          table.startRow();
          table.appendCell(INFO_TASKINFO_NONE.get());
        }
        StringWriter sw = new StringWriter();
        printTable(table, 0, 0, sw);
        app.getOutputStream().println(LocalizableMessage.raw(sw.getBuffer().toString()));
        app.getOutputStream().println();
      } catch (Exception e) {
        app.println(ERR_TASKINFO_ACCESSING_LOGS.get(taskId, e.getMessage()));
      }
      return MenuResult.success(taskEntry);
    }
  }

  /** Executable for canceling a particular task. */
  private static class CancelTaskTop extends TopMenuCallback {
    private List<String> taskIds;
    private List<Integer> cancelableIndices;

    /**
     * Constructs a parameterized instance.
     *
     * @param taskIds of all known tasks
     * @param cancelableIndices list of integers whose elements represent
     *        the indices of <code>taskIds</code> that are cancelable
     */
    public CancelTaskTop(List<String> taskIds, List<Integer> cancelableIndices)
    {
      this.taskIds = taskIds;
      this.cancelableIndices = cancelableIndices;
    }

    @Override
    public MenuResult<Void> invoke(ManageTasks app) throws ClientException
    {
      if (taskIds == null || taskIds.isEmpty()) {
        app.println(INFO_TASKINFO_NO_TASKS.get());
        return MenuResult.cancel();
      }
      if (cancelableIndices == null || cancelableIndices.isEmpty()) {
        app.println(INFO_TASKINFO_NO_CANCELABLE_TASKS.get());
        return MenuResult.cancel();
      }

      // Prompt for the task number
      Integer index = null;
      String line = app.readLineOfInput(INFO_TASKINFO_CMD_CANCEL_NUMBER_PROMPT.get(cancelableIndices.get(0)));
      if (line.length() == 0) {
        line = String.valueOf(cancelableIndices.get(0));
      }

      try {
        int i = Integer.parseInt(line);
        if (!cancelableIndices.contains(i)) {
          app.println(ERR_TASKINFO_NOT_CANCELABLE_TASK_INDEX.get(i));
        } else {
          index = i - 1;
        }
      } catch (NumberFormatException ignored) {}

      if (index == null) {
        app.errPrintln(ERR_TASKINFO_INVALID_MENU_KEY.get(line));
        return MenuResult.again();
      }

      String taskId = taskIds.get(index);
      try {
        CancelTask ct = new CancelTask(taskId);
        MenuResult<TaskEntry> result = ct.invoke(app);
        return result.isSuccess() ? MenuResult.<Void> success() : MenuResult.<Void> again();
      } catch (Exception e) {
        app.errPrintln(ERR_TASKINFO_CANCELING_TASK.get(taskId, e.getMessage()));
        return MenuResult.again();
      }
    }
  }

  /** Executable for canceling a particular task. */
  private static class CancelTask extends TaskOperationCallback {
    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task to cancel
     */
    public CancelTask(String taskId) {
      super(taskId);
    }

    @Override
    public MenuResult<TaskEntry> invoke(ManageTasks app) throws ClientException
    {
      try {
        TaskEntry entry = app.getTaskClient().getTaskEntry(taskId);
        if (!entry.isCancelable()) {
          app.errPrintln(ERR_TASKINFO_TASK_NOT_CANCELABLE_TASK.get(taskId));
          return MenuResult.again();
        }

        app.getTaskClient().cancelTask(taskId);
        app.println(INFO_TASKINFO_CMD_CANCEL_SUCCESS.get(taskId));
        return MenuResult.success(entry);
      } catch (Exception e) {
        app.errPrintln(ERR_TASKINFO_CANCELING_TASK.get(taskId, e.getMessage()));
        return MenuResult.again();
      }
    }
  }
}
