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
 * Portions Copyright 2015 ForgeRock AS.
 */

package org.opends.quicksetup.event;

/**
 * Interface used for the actions that are done in the wizard buttons (those
 * specified in org.opends.quicksetup.ButtonName).
 */
public interface ButtonActionListener
{
  /**
   * Method called when the user clicks on one of the buttons specified in
   * org.opends.quicksetup.ButtonName.
   * @param event the button event
   */
  void buttonActionPerformed(ButtonEvent event);
}
