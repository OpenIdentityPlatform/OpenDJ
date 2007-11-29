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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.tools;

import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.*;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.core.DirectoryServer;
import static org.opends.server.loggers.ErrorLogger.removeErrorLogPublisher;
import org.opends.server.protocols.asn1.ASN1Exception;
import static org.opends.server.tools.ToolConstants.*;
import org.opends.server.tools.tasks.TaskClient;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.types.LDAPException;
import org.opends.server.util.StaticUtils;
import static org.opends.server.util.StaticUtils.filterExitCode;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuCallback;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tool for getting information and managing tasks in the Directory Server.
 */
public class ManageTasks extends ConsoleApplication {

  private static ErrorLogPublisher errorLogPublisher = null;

  /**
   * The main method for TaskInfo tool.
   *
   * @param args The command-line arguments provided to this program.
   */
  public static void main(String[] args) {
    int retCode = mainTaskInfo(args, System.in, System.out, System.err);

    if (errorLogPublisher != null) {
      removeErrorLogPublisher(errorLogPublisher);
    }

    if (retCode != 0) {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the process for
   * displaying task information.
   *
   * @param args The command-line arguments provided to this program.
   * @return int return code
   */
  public static int mainTaskInfo(String[] args) {
    return mainTaskInfo(args, System.in, System.out, System.err);
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param args             The command-line arguments provided to this
   * @param in               Input stream from which to solicit user input.
   * @param out              The output stream to use for standard output, or
   *                         {@code null} if standard output is not needed.
   * @param err              The output stream to use for standard error, or
   *                         {@code null} if standard error is not needed.

   * @return int return code
   */
  public static int mainTaskInfo(String[] args,
                                 InputStream in,
                                 OutputStream out,
                                 OutputStream err) {
    ManageTasks tool = new ManageTasks(in, out, err);
    return tool.process(args);
  }

  private static final int INDENT = 2;

  /**
   * ID of task for which to display details and exit.
   */
  private StringArgument task = null;

  /**
   * Indicates print summary and exit.
   */
  private BooleanArgument summary = null;

  /**
   * ID of task to cancel.
   */
  private StringArgument cancel = null;

  /**
   * Argument used to request non-interactive behavior.
   */
  private BooleanArgument noPrompt = null;

  /**
   * Accesses the directory's task backend.
   */
  private TaskClient taskClient;

  /**
   * Constructs a parameterized instance.
   *
   * @param in               Input stream from which to solicit user input.
   * @param out              The output stream to use for standard output, or
   *                         {@code null} if standard output is not needed.
   * @param err              The output stream to use for standard error, or
   *                         {@code null} if standard error is not needed.
   */
  public ManageTasks(InputStream in, OutputStream out, OutputStream err) {
    super(in, out, err);
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param args       The command-line arguments provided to this
   *                   program.
   * @return The error code.
   */
  public int process(String[] args) {

    DirectoryServer.bootstrapClient();

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser = new LDAPConnectionArgumentParser(
            "org.opends.server.tools.TaskInfo",
            INFO_TASKINFO_TOOL_DESCRIPTION.get(),
            false, null);

    // Initialize all the command-line argument types and register them with the
    // parser.
    try {

       StringArgument propertiesFileArgument = new StringArgument(
          "propertiesFilePath", null, OPTION_LONG_PROP_FILE_PATH, false, false,
          true, OPTION_VALUE_PROP_FILE_PATH, null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      BooleanArgument noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      task = new StringArgument(
              "info", 'i', "info",
              false, true, "{taskID}",
              INFO_TASKINFO_TASK_ARG_DESCRIPTION.get());
      argParser.addArgument(task);

      cancel = new StringArgument(
              "cancel", 'c', "cancel",
              false, true, "{taskID}",
              INFO_TASKINFO_TASK_ARG_CANCEL.get());
      argParser.addArgument(cancel);

      summary = new BooleanArgument(
              "summary", 's', "summary",
              INFO_TASKINFO_SUMMARY_ARG_DESCRIPTION.get());
      argParser.addArgument(summary);

      noPrompt = new BooleanArgument(
              OPTION_LONG_NO_PROMPT,
              OPTION_SHORT_NO_PROMPT,
              OPTION_LONG_NO_PROMPT,
              INFO_DESCRIPTION_NO_PROMPT.get());
      argParser.addArgument(noPrompt);

      BooleanArgument displayUsage = new BooleanArgument(
              "help", OPTION_SHORT_HELP,
              OPTION_LONG_HELP,
              INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae) {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try {
      argParser.parseArguments(args);
      StaticUtils.checkOnlyOneArgPresent(task, summary, cancel);
    }
    catch (ArgumentException ae) {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      println(message);
      println(argParser.getUsageMessage());
      return 1;
    }

    if (!argParser.usageOrVersionDisplayed()) {
      try {
        LDAPConnectionConsoleInteraction ui =
                new LDAPConnectionConsoleInteraction(
                        this, argParser.getArguments());

        taskClient = new TaskClient(argParser.connect(ui,
                getOutputStream(), getErrorStream()));

        if (isMenuDrivenMode()) {

          // Keep prompting the user until they specify quit of
          // there is a fatal exception
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
          MenuResult<TaskEntry> r =
                  new PrintTaskInfo(task.getValue()).invoke(this);
          if (r.isAgain()) return 1;
        } else if (summary.isPresent()) {
          getOutputStream().println();
          printSummaryTable();
        } else if (cancel.isPresent()) {
          MenuResult<TaskEntry> r =
                  new CancelTask(cancel.getValue()).invoke(this);
          if (r.isAgain()) return 1;
        }

      } catch (LDAPConnectionException lce) {
        println(INFO_TASKINFO_LDAP_EXCEPTION.get(lce.getMessageObject()));
        return 1;
      } catch (Exception e) {
        println(Message.raw(e.getMessage()));
        return 1;
      }
    }
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAdvancedMode() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isInteractive() {
    return !noPrompt.isPresent();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isMenuDrivenMode() {
    return !task.isPresent() && !cancel.isPresent() && !summary.isPresent();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isQuiet() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return false;
  }

  /**
   * Creates the summary table.
   *
   * @throws IOException if there is a problem with screen I/O
   * @throws LDAPException if there is a problem getting information
   *         out to the directory
   * @throws ASN1Exception if there is a problem with the encoding
   */
  private void printSummaryTable()
          throws LDAPException, IOException, ASN1Exception {
    List<TaskEntry> entries = taskClient.getTaskEntries();
    if (entries.size() > 0) {
      TableBuilder table = new TableBuilder();
      Map<String, TaskEntry> mapIdToEntry =
              new TreeMap<String, TaskEntry>();
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
      getOutputStream().println(Message.raw(sw.getBuffer()));
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
   * @throws ASN1Exception if there is a problem with the encoding
   */
  private Menu<Void> getSummaryMenu()
          throws LDAPException, IOException, ASN1Exception {
    List<String> taskIds = new ArrayList<String>();
    List<Integer> cancelableIndices = new ArrayList<Integer>();
    List<TaskEntry> entries = taskClient.getTaskEntries();
    MenuBuilder<Void> menuBuilder = new MenuBuilder<Void>(this);
    if (entries.size() > 0) {
      Map<String, TaskEntry> mapIdToEntry =
              new TreeMap<String, TaskEntry>();
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
                Message.raw(taskEntry.getId()),
                new TaskDrilldownMenu(taskId),
                taskEntry.getType(), taskEntry.getState());
        index++;
        if (taskEntry.isCancelable() && !taskEntry.isDone()) {
          cancelableIndices.add(index);
        }
      }
    } else {
      // println();
      getOutputStream().println(INFO_TASKINFO_NO_TASKS.get());
      getOutputStream().println();
    }

    menuBuilder.addCharOption(
            Message.raw("r"),
            INFO_TASKINFO_CMD_REFRESH.get(),
            new PrintSummaryTop());

    if (cancelableIndices.size() > 0) {
      menuBuilder.addCharOption(
              Message.raw("c"),
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

  /**
   * Base for callbacks that implement top level menu items.
   */
  static abstract private class TopMenuCallback
          implements MenuCallback<Void> {

    /**
     * {@inheritDoc}
     */
    public MenuResult<Void> invoke(ConsoleApplication app) throws CLIException {
      return invoke((ManageTasks)app);
    }

    /**
     * Called upon task invocation.
     *
     * @param app this console application
     * @return MessageResult result of task
     * @throws CLIException if there is a problem
     */
    protected abstract MenuResult<Void> invoke(ManageTasks app)
            throws CLIException;

  }

  /**
   * Base for callbacks that manage task entries.
   */
  static abstract private class TaskOperationCallback
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

    /**
     * {@inheritDoc}
     */
    public MenuResult<TaskEntry> invoke(ConsoleApplication app)
            throws CLIException
    {
      return invoke((ManageTasks)app);
    }

    /**
     * {@inheritDoc}
     */
    protected abstract MenuResult<TaskEntry> invoke(ManageTasks app)
            throws CLIException;

  }

  /**
   * Executable for printing a task summary table.
   */
  static private class PrintSummaryTop extends TopMenuCallback {

    public MenuResult<Void> invoke(ManageTasks app)
            throws CLIException
    {
      // Since the summary table is reprinted every time the
      // user enters the top level this task just returns
      // 'success'
      return MenuResult.success();
    }
  }

  /**
   * Exectuable for printing a particular task's details.
   */
  static private class TaskDrilldownMenu extends TopMenuCallback {

    private String taskId;

    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task for which information will be displayed
     */
    public TaskDrilldownMenu(String taskId) {
      this.taskId = taskId;
    }

    /**
     * {@inheritDoc}
     */
    public MenuResult<Void> invoke(ManageTasks app) throws CLIException {
      MenuResult<TaskEntry> res = new PrintTaskInfo(taskId).invoke(app);
      TaskEntry taskEntry = res.getValue();
      if (taskEntry != null) {
        while (true) {
          try {
            taskEntry = app.getTaskClient().getTaskEntry(taskId);

            // Show the menu
            MenuBuilder<TaskEntry> menuBuilder =
                    new MenuBuilder<TaskEntry>(app);
            menuBuilder.addBackOption(true);
            menuBuilder.addCharOption(
                    Message.raw("r"),
                    INFO_TASKINFO_CMD_REFRESH.get(),
                    new PrintTaskInfo(taskId));
            List<Message> logs = taskEntry.getLogMessages();
            if (logs != null && logs.size() > 0) {
              menuBuilder.addCharOption(
                      Message.raw("l"),
                      INFO_TASKINFO_CMD_VIEW_LOGS.get(),
                      new ViewTaskLogs(taskId));
            }
            if (taskEntry.isCancelable() && !taskEntry.isDone()) {
              menuBuilder.addCharOption(
                      Message.raw("c"),
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
            app.println(Message.raw(e.getMessage()));
          }
        }
      } else {
        app.println(ERR_TASKINFO_UNKNOWN_TASK_ENTRY.get(taskId));
      }
      return MenuResult.success();
    }

  }

  /**
   * Exectuable for printing a particular task's details.
   */
  static private class PrintTaskInfo extends TaskOperationCallback {

    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task for which information will be printed
     */
    public PrintTaskInfo(String taskId) {
      super(taskId);
    }

    /**
     * {@inheritDoc}
     */
    public MenuResult<TaskEntry> invoke(ManageTasks app)
            throws CLIException
    {
      TaskEntry taskEntry = null;
      try {
        taskEntry = app.getTaskClient().getTaskEntry(taskId);

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
        Message m = taskEntry.getScheduledStartTime();
        if (m == null || m.equals(Message.EMPTY)) {
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

        writeMultiValueCells(
                table,
                INFO_TASKINFO_FIELD_DEPENDENCY.get(),
                taskEntry.getDependencyIds());

        table.startRow();
        table.appendCell(INFO_TASKINFO_FIELD_FAILED_DEPENDENCY_ACTION.get());
        m = taskEntry.getFailedDependencyAction();
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
        TextTablePrinter tablePrinter = new TextTablePrinter(sw);
        tablePrinter.setTotalWidth(80);
        tablePrinter.setIndentWidth(INDENT);
        tablePrinter.setColumnWidth(1, 0);
        table.print(tablePrinter);
        app.getOutputStream().println();
        app.getOutputStream().println(Message.raw(sw.getBuffer().toString()));

        // Create a table for the task options
        table = new TableBuilder();
        table.appendHeading(INFO_TASKINFO_OPTIONS.get(taskEntry.getType()));
        Map<Message,List<String>> taskSpecificAttrs =
                taskEntry.getTaskSpecificAttributeValuePairs();
        for (Message attrName : taskSpecificAttrs.keySet()) {
          table.startRow();
          table.appendCell(attrName);
          List<String> values = taskSpecificAttrs.get(attrName);
          if (values.size() > 0) {
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
        tablePrinter = new TextTablePrinter(sw);
        tablePrinter.setTotalWidth(80);
        tablePrinter.setIndentWidth(INDENT);
        tablePrinter.setColumnWidth(1, 0);
        table.print(tablePrinter);
        app.getOutputStream().println(Message.raw(sw.getBuffer().toString()));

        // Print the last log message if any
        List<Message> logs = taskEntry.getLogMessages();
        if (logs != null && logs.size() > 0) {

          // Create a table for the last log entry
          table = new TableBuilder();
          table.appendHeading(INFO_TASKINFO_FIELD_LAST_LOG.get());
          table.startRow();
          table.appendCell(logs.get(logs.size() - 1));

          sw = new StringWriter();
          tablePrinter = new TextTablePrinter(sw);
          tablePrinter.setTotalWidth(80);
          tablePrinter.setIndentWidth(INDENT);
          tablePrinter.setColumnWidth(0, 0);
          table.print(tablePrinter);
          app.getOutputStream().println(Message.raw(sw.getBuffer().toString()));
        }

        app.getOutputStream().println();
      } catch (Exception e) {
        app.println(ERR_TASKINFO_RETRIEVING_TASK_ENTRY.get(
                    taskId, e.getMessage()));
        return MenuResult.again();
      }
      return MenuResult.success(taskEntry);
    }

    /**
     * Writes an attribute and associated values to the table.
     * @param table of task details
     * @param fieldLabel of attribute
     * @param values of the attribute
     */
    private void writeMultiValueCells(TableBuilder table,
                                      Message fieldLabel,
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
                                      Message fieldLabel,
                                      List<?> values,
                                      Message noneLabel) {
      table.startRow();
      table.appendCell(fieldLabel);
      if (values.size() == 0) {
        table.appendCell(noneLabel);
      } else if (values.size() > 0) {
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

  /**
   * Exectuable for printing a particular task's details.
   */
  static private class ViewTaskLogs extends TaskOperationCallback {

    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task for which log records will be printed
     */
    public ViewTaskLogs(String taskId) {
      super(taskId);
    }

    /**
     * {@inheritDoc}
     */
    protected MenuResult<TaskEntry> invoke(ManageTasks app)
            throws CLIException
    {
      TaskEntry taskEntry = null;
      try {
        taskEntry = app.getTaskClient().getTaskEntry(taskId);
        List<Message> logs = taskEntry.getLogMessages();
        app.getOutputStream().println();

        // Create a table for the last log entry
        TableBuilder table = new TableBuilder();
        table.appendHeading(INFO_TASKINFO_FIELD_LOG.get());
        if (logs != null && logs.size() > 0) {
          for (Message log : logs) {
            table.startRow();
            table.appendCell(log);
          }
        } else {
          table.startRow();
          table.appendCell(INFO_TASKINFO_NONE.get());
        }
        StringWriter sw = new StringWriter();
        TextTablePrinter tablePrinter = new TextTablePrinter(sw);
        tablePrinter.setTotalWidth(80);
        tablePrinter.setIndentWidth(INDENT);
        tablePrinter.setColumnWidth(0, 0);
        table.print(tablePrinter);
        app.getOutputStream().println(Message.raw(sw.getBuffer().toString()));
        app.getOutputStream().println();
      } catch (Exception e) {
        app.println(ERR_TASKINFO_ACCESSING_LOGS.get(taskId, e.getMessage()));
      }
      return MenuResult.success(taskEntry);
    }
  }

  /**
   * Executable for canceling a particular task.
   */
  static private class CancelTaskTop extends TopMenuCallback {

    private List<String> taskIds;
    private List<Integer> cancelableIndices;

    /**
     * Constructs a parameterized instance.
     *
     * @param taskIds of all known tasks
     * @param cancelableIndices list of integers whose elements represent
     *        the indices of <code>taskIds</code> that are cancelable
     */
    public CancelTaskTop(List<String> taskIds,
                         List<Integer> cancelableIndices) {
      this.taskIds = taskIds;
      this.cancelableIndices = cancelableIndices;
    }

    /**
     * {@inheritDoc}
     */
    public MenuResult<Void> invoke(ManageTasks app)
            throws CLIException
    {
      if (taskIds != null && taskIds.size() > 0) {
        if (cancelableIndices != null && cancelableIndices.size() > 0) {

          // Prompt for the task number
          Integer index = null;
          String line = app.readLineOfInput(
                  INFO_TASKINFO_CMD_CANCEL_NUMBER_PROMPT.get(
                          cancelableIndices.get(0)));
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
          } catch (NumberFormatException nfe) {
            // ignore;
          }
          if (index != null) {
            String taskId = taskIds.get(index);
            try {
              CancelTask ct = new CancelTask(taskId);
              MenuResult<TaskEntry> result = ct.invoke(app);
              if (result.isSuccess()) {
                return MenuResult.success();
              } else {
                return MenuResult.again();
              }
            } catch (Exception e) {
              app.println(ERR_TASKINFO_CANCELING_TASK.get(
                          taskId, e.getMessage()));
              return MenuResult.again();
            }
          } else {
            app.println(ERR_TASKINFO_INVALID_MENU_KEY.get(line));
            return MenuResult.again();
          }
        } else {
          app.println(INFO_TASKINFO_NO_CANCELABLE_TASKS.get());
          return MenuResult.cancel();
        }
      } else {
        app.println(INFO_TASKINFO_NO_TASKS.get());
        return MenuResult.cancel();
      }
    }

  }

  /**
   * Executable for canceling a particular task.
   */
  static private class CancelTask extends TaskOperationCallback {

    /**
     * Constructs a parameterized instance.
     *
     * @param taskId of the task to cancel
     */
    public CancelTask(String taskId) {
      super(taskId);
    }

    /**
     * {@inheritDoc}
     */
    public MenuResult<TaskEntry> invoke(ManageTasks app)
            throws CLIException
    {
      try {
        TaskEntry entry = app.getTaskClient().getTaskEntry(taskId);
        if (entry.isCancelable()) {
          app.getTaskClient().cancelTask(taskId);
          app.println(INFO_TASKINFO_CMD_CANCEL_SUCCESS.get(taskId));
          return MenuResult.success(entry);
        } else {
          app.println(ERR_TASKINFO_TASK_NOT_CANCELABLE_TASK.get(taskId));
          return MenuResult.again();
        }
      } catch (Exception e) {
        app.println(ERR_TASKINFO_CANCELING_TASK.get(
                taskId, e.getMessage()));
        return MenuResult.again();
      }
    }

  }

}
