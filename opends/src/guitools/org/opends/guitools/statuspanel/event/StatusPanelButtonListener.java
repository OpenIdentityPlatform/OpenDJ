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

package org.opends.guitools.statuspanel.event;

/**
 * Interface used to be notified of the button actions that can occur in the
 * StatusPanelDialog.
 *
 * In the current implementation StatusPanelController implements this
 * interface.
 *
 */
public interface StatusPanelButtonListener
{
  /**
   * Method called when user clicks on Authenticate button.
   */
  public void authenticateClicked();
  /**
   * Method called when user clicks on Start button.
   */
  public void startClicked();
  /**
   * Method called when user clicks on Restart button.
   */
  public void restartClicked();
  /**
   * Method called when user clicks on Stop button.
   */
  public void stopClicked();
  /**
   * Method called when user clicks on Quit button.
   */
  public void quitClicked();
}
