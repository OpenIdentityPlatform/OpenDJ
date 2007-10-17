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

package org.opends.quicksetup;
import org.opends.messages.Message;

import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import java.util.HashSet;

/**
 * Delegate class for handling progress notification listeners and events.
 */
public class ProgressUpdateListenerDelegate {

  private HashSet<ProgressUpdateListener> listeners =
          new HashSet<ProgressUpdateListener>();

  /**
   * Creates a parameterized instance.
   */
  public ProgressUpdateListenerDelegate() {
  }

  /**
   * Adds a ProgressUpdateListener that will be notified of updates in
   * the install progress.
   *
   * @param l the ProgressUpdateListener to be added.
   */
  public void addProgressUpdateListener(ProgressUpdateListener l) {
    listeners.add(l);
  }

  /**
   * Removes a ProgressUpdateListener.
   *
   * @param l the ProgressUpdateListener to be removed.
   */
  public void removeProgressUpdateListener(ProgressUpdateListener l) {
    listeners.remove(l);
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an
   * update in the installation progress.
   *
   * @param current             progress step
   * @param ratio               the integer that specifies which percentage of
 *                            the whole installation has been completed.
   * @param currentPhaseSummary the localized summary message for the
*                            current installation progress in formatted form.
   * @param newLogDetail        the new log messages that we have for the
   */
  public void notifyListeners(ProgressStep current, Integer ratio,
                              Message currentPhaseSummary,
                              Message newLogDetail) {
    ProgressUpdateEvent ev =
            new ProgressUpdateEvent(current, ratio,
                    currentPhaseSummary, newLogDetail);
    for (ProgressUpdateListener l : listeners) {
      l.progressUpdate(ev);
    }
  }

  /**
   * Notify listeners about a change in log detail.
   * @param msg log detail
   */
  protected void notifyListeners(Message msg) {
    notifyListeners(null, null, null, msg);
  }

}
