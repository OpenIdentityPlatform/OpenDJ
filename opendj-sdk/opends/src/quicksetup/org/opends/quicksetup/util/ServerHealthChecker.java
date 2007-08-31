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

package org.opends.quicksetup.util;

import org.opends.messages.Message;
import org.opends.messages.Severity;

import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ApplicationException;

import static org.opends.messages.QuickSetupMessages.*;

import java.util.List;

/**
 * Checks a server health by starting a server and looking through its
 * log messages for signs that things may not be well.
 */
public class ServerHealthChecker {

  private Installation installation = null;

  private List<Message> errors = null;

  /**
   * Regular expression used to determine whether or not a server
   * is healthy enough for upgrade by looking through its startup
   * logs.
   */
  static private final String UNHEALTHY_SERVER_LOG_REGEX =
    new StringBuilder()
    .append(".*(")
    .append(Severity.FATAL_ERROR.name())
    .append("|")
    .append(Severity.SEVERE_ERROR.name())
    .append(")+.*").toString();


  /**
   * Creates an instance.
   * @param installation of the server to check
   */
  public ServerHealthChecker(Installation installation) {
    this.installation = installation;
  }

  /**
   * Diagnoses the server's health by stopping (if necessary) and starting
   * the server in process and analyzing the resulting log messages.
   * Following the call to this method the server is left in a stopped state.
   * @throws ApplicationException if things go wrong
   */
  public void checkServer() throws ApplicationException {
    InProcessServerController control = null;
    try {
      control = new InProcessServerController(installation);
      if (installation.getStatus().isServerRunning()) {
        new ServerController(installation).stopServer(true);
      }
      OperationOutput op = control.startServer();
      errors = op.getErrorMessages(UNHEALTHY_SERVER_LOG_REGEX);
    } catch (Exception e) {
      if (e instanceof ApplicationException) {
        throw (ApplicationException)e;
      } else {
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                INFO_ERROR_SERVER_HEALTH_CHECK_FAILURE.get(), e);
      }
    } finally {
      if (control != null) {
        control.stopServer();
      }
    }
  }

  /**
   * Gets a list of Strings representing error messages that resulted
   * from calling <code>checkServer</code>; null if <code>checkServer</code>
   * has not been called.
   * @return List of strings containing error messages
   */
  public List<Message> getProblemMessages() {
    return errors;
  }

}
