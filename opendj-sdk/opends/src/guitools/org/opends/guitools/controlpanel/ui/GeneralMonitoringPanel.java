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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.messages.Message;

/**
 * Abstract class used to refactor some code among the panels that display the
 * contents of the global monitoring.
 *
 */
public abstract class GeneralMonitoringPanel extends StatusGenericPanel
{
  /**
   * The empty border shared by all the panels.
   */
  protected Border PANEL_BORDER = new EmptyBorder(10, 10, 10, 10);

  /**
   * The message to express that the value was not found.
   */
  protected static Message NO_VALUE_SET =
    INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return Message.EMPTY;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
  }
}
