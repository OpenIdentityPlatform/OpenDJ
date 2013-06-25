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

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * A simple panel containing a message.
 *
 */
public class NoItemSelectedPanel extends JPanel
{
  private JLabel l;
  private Message msg;
  private static final long serialVersionUID = -8288525745479095426L;

  /**
   * Default constructor.
   *
   */
  public NoItemSelectedPanel()
  {
    super(new GridBagLayout());
    setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    msg = INFO_CTRL_PANEL_NO_ITEM_SELECTED_LABEL.get();
    l = Utilities.createPrimaryLabel(msg);
    add(l, gbc);
  }

  /**
   * Sets the message to be displayed.
   * @param text the message to be displayed.
   */
  public void setMessage(Message text)
  {
    msg = text;
    l.setText(text.toString());
  }

  /**
   * Returns the displayed message.
   * @return the displayed message.
   */
  public Message getMessage()
  {
    return msg;
  }
}
