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
 *      Copyright 2008 Sun Microsystems, Inc.
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
  public void newLine(String line);
}
