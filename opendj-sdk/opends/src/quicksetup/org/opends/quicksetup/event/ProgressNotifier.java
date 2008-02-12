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

package org.opends.quicksetup.event;

import org.opends.messages.Message;

/**
 * Inteface for applications that advertise status to interested
 * listeners.
 */
public interface ProgressNotifier {

  /**
   * Adds a listener to the list of those that are
   * notified about the progress state change of
   * an application.
   * @param l ProgressUpdateListener
   */
  void addProgressUpdateListener(ProgressUpdateListener l);

  /**
   * Removes a listener from this list of those that
   * are notified about a progress state change.
   * @param l ProgressUpdateListener
   */
  void removeProgressUpdateListener(ProgressUpdateListener l);

  /**
   * Notifies all registered listeners about a change
   * in progress state.
   * @param ratio Integer specifying the percentage of the whole
   *        process that has been completed
   * @param currentPhaseSummary localized summary message for
 *        the current installation progress in formatted
 *        form
   * @param newLogDetail new log messages in formatted form
   */
  void notifyListeners(Integer ratio, Message currentPhaseSummary,
      Message newLogDetail);

}
