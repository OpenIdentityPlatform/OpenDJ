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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

/**
 * This enumeration defines the logical names of the buttons that appear on the
 * the wizard dialog.
 */
public enum ButtonName
{
  /**
   * The Next button.
   */
  NEXT,
  /**
   * The Previous button.
   */
  PREVIOUS,
  /**
   * The Quit button.
   */
  QUIT,
  /**
   * The Continue with install button.
   */
  CONTINUE_INSTALL,
  /**
   * The Close button.
   */
  CLOSE,
  /**
   * The Finish button.
   */
  FINISH,
  /**
   * The Launch Status Panel button.
   */
  LAUNCH_STATUS_PANEL,
  /**
   * Input panel button.  This is used to identify generic buttons inside
   * the panels and the notifications are used for instance to update the
   * visibility of the steps on the right.
   */
  INPUT_PANEL_BUTTON
}
