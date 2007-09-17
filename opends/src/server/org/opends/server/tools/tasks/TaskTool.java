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

package org.opends.server.tools.tasks;

import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
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
import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.*;

import java.io.PrintStream;
import java.text.ParseException;
import java.util.Date;
import java.io.IOException;

/**
 * Base class for tools that are capable of operating either by running
 * local within this JVM or by scheduling a task to perform the same
 * action running within the directory server through the tasks interface.
 */
public abstract class TaskTool implements TaskScheduleInformation {

  LDAPConnectionArgumentParser argParser;

  StringArgument startArg;

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
    LDAPConnectionArgumentParser argParser = new LDAPConnectionArgumentParser(
            className, toolDescription, false);

    try {
      startArg =
           new StringArgument(
                   OPTION_LONG_START_DATETIME,
                   OPTION_SHORT_START_DATETIME,
                   OPTION_LONG_START_DATETIME, false, false,
                   true, OPTION_VALUE_START_DATETIME,
                   null, null,
                   INFO_DESCRIPTION_START_DATETIME.get());
      argParser.addArgument(startArg);
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
    if (startArg.isPresent()) {
      try {
        StaticUtils.parseDateTimeString(startArg.getValue());
      } catch (ParseException pe) {
        throw new ArgumentException(ERR_START_DATETIME_FORMAT.get());
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public Date getStartDateTime() {
    Date start = null;
    if (startArg != null && startArg.isPresent()) {
      try {
        start = StaticUtils.parseDateTimeString(startArg.getValue());
      } catch (ParseException pe) {
        // ignore; valiidated in validateTaskArgs()
      }
    }
    return start;
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

    if (argParser.isLdapOperation())
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
        ret = 0;
      } catch (LDAPConnectionException e) {
        Message message = ERR_LDAP_CONN_CANNOT_CONNECT.get(e.getMessage());
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

}
