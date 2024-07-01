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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.opends.quicksetup;

/**
 * This enumeration defines the logical names of the buttons that appear on the
 * the wizard dialog.
 */
public enum ButtonName
{
  /** The Next button. */
  NEXT,
  /** The Previous button. */
  PREVIOUS,
  /** The Quit button. */
  QUIT,
  /** The Continue with install button. */
  CONTINUE_INSTALL,
  /** The Close button. */
  CLOSE,
  /** The Finish button. */
  FINISH,
  /** The Launch Status Panel button. */
  LAUNCH_STATUS_PANEL,
  /**
   * Input panel button.  This is used to identify generic buttons inside
   * the panels and the notifications are used for instance to update the
   * visibility of the steps on the right.
   */
  INPUT_PANEL_BUTTON
}
