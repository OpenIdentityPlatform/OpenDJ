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
package org.opends.server.util.cli;



/**
 * An interactive console-based menu.
 *
 * @param <T>
 *          The type of success result value(s) returned by the
 *          call-back. Use <code>Void</code> if the call-backs do
 *          not return any values.
 */
public interface Menu<T> {

  /**
   * Displays the menu and waits for the user to select a valid
   * option. When the user selects an option, the call-back associated
   * with the option will be invoked and its result returned.
   *
   * @return Returns the result of invoking the chosen menu call-back.
   * @throws CLIException
   *           If an I/O exception occurred or if one of the menu
   *           option call-backs failed for some reason.
   */
  MenuResult<T> run() throws CLIException;
}
