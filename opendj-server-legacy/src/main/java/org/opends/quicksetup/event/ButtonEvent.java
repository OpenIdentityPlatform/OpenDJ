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
 */

package org.opends.quicksetup.event;

import java.util.EventObject;

import org.opends.quicksetup.ButtonName;

/**
 * The event that is generated when the user clicks in one of the wizard buttons
 * specified in org.opends.quicksetup.ButtonName.
 *
 */
public class ButtonEvent extends EventObject
{
  private static final long serialVersionUID = -4411929136433332009L;

  private ButtonName buttonName;

  /**
   * Constructor.
   * @param source the button that generated the event
   * @param buttonName the button name.
   */
  public ButtonEvent(Object source, ButtonName buttonName)
  {
    super(source);
    this.buttonName = buttonName;
  }

  /**
   * Gets the ButtonName of the button that generated the event.
   * @return the ButtonName of the button that generated the event.
   */
  public ButtonName getButtonName()
  {
    return buttonName;
  }
}
