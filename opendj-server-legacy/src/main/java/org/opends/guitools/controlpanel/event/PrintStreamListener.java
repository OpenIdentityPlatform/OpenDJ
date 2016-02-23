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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.event;

/**
 * This is a listener used by the progress dialogs and the tasks to be notified
 * when something is written to a PrintStream.  This is used for instance to
 * be able to redirect the output logs of operations launched in a separate
 * process (like start-ds, import-ldif, etc.).  It is used mainly in the
 * progress dialog and in the task.
 *
 */
public interface PrintStreamListener
{
  /**
   * Notification that a new line has been written in a PrintStream.
   * @param line the new line.
   */
  void newLine(String line);
}
