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
import static org.opends.server.util.StaticUtils.wrapText;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPConnectionException;
import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.ERR_LDAP_CONN_CANNOT_CONNECT;

import java.io.PrintStream;

/**
 * Base class for tools that are capable of operating either by running
 * local within this JVM or by scheduling a task to perform the same
 * action running within the directory server through the tasks interface.
 */
public abstract class TaskTool implements TaskScheduleInformation {

  /**
   * Either invokes initiates this tool's local action or schedule this
   * tool using the tasks interface based on user input.
   *
   * @param argParser used to parse user arguments
   * @param initializeServer indicates whether or not to initialize the
   *        directory server in the case of a local action
   * @param out stream to write messages
   * @param err stream to write messages
   * @return int indicating the result of this action
   */
  protected int process(LDAPConnectionArgumentParser argParser,
                        boolean initializeServer,
                        PrintStream out, PrintStream err) {
    int ret;
    if (argParser.isLdapOperation())
    {
      try {
        LDAPConnection conn = argParser.connect(out, err);
        TaskSchedulingClient tc = new TaskSchedulingClient(conn);
        ret = tc.schedule(this, out, err);
      } catch (LDAPConnectionException e) {
        Message message = ERR_LDAP_CONN_CANNOT_CONNECT.get(e.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        ret = LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
      } catch (ArgumentException e) {
        Message message = e.getMessageObject();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        ret = LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
      }
    }
    else
    {
      ret = processLocal(initializeServer, out, err);
    }
    return ret;
  }

  /**
   * Called when this utility should perform its actions locally in this
   * JVM.
   *
   * @param initializeServer indicates whether or not to initialize the
   *        directory server in the case of a local action
   * @param out stream to write messages
   * @param err stream to write messages
   * @return int indicating the result of this action
   */
  abstract protected int processLocal(boolean initializeServer,
                                      PrintStream out,
                                      PrintStream err);

}
