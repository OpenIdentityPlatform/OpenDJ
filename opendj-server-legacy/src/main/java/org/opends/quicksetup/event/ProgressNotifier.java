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
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.opends.quicksetup.event;

import org.forgerock.i18n.LocalizableMessage;

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
  void notifyListeners(Integer ratio, LocalizableMessage currentPhaseSummary,
      LocalizableMessage newLogDetail);

}
