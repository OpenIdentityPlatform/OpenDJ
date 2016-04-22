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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;

/**
 * Abstract class used to refactor some code among the panels that display the
 * contents of the global monitoring.
 */
abstract class GeneralMonitoringPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2840755228290832143L;

  /** The empty border shared by all the panels. */
  protected final Border PANEL_BORDER = new EmptyBorder(10, 10, 10, 10);

  /** The message to express that the value was not found. */
  protected final static LocalizableMessage NO_VALUE_SET =
    INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    // no-op
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return LocalizableMessage.EMPTY;
  }

  @Override
  public void okClicked()
  {
    // no-op
  }

  /** Updates the contents of the panel. */
  public abstract void updateContents();
}
