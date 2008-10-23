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

package org.opends.guitools.controlpanel.ui.components;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

/**
 * This is a panel containing two labels with different fonts.  It is used
 * for instance in the index panel.  The label on the left contains the type
 * of object and the label on the right, details about the object.
 *
 */
public class TitlePanel extends JPanel
{
  private static final long serialVersionUID = -5164867192115208627L;
  private JLabel lTitle;
  private JLabel lDetails;

  private Message title;
  private Message details;

  /**
   * Constructor of the panel.
   * @param title the title of the panel.
   * @param details the details of the panel.
   */
  public TitlePanel(Message title, Message details)
  {
    super(new GridBagLayout());
    setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    MessageBuilder mb = new MessageBuilder();
    mb.append(title);
    mb.append(" - ");
    lTitle = Utilities.createTitleLabel(mb.toMessage());
    lDetails = Utilities.createDefaultLabel(details);
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.SOUTHWEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    add(lTitle, gbc);
    gbc.gridx ++;
    add(lDetails, gbc);
    this.title = title;
    this.details = details;
  }

  /**
   * Sets the title of this panel.
   * @param title the title of this panel.
   */
  public void setTitle(Message title)
  {
    lTitle.setText(title+" - ");
    this.title = title;
  }

  /**
   * Sets the details of this panel.
   * @param details the details of this panel.
   */
  public void setDetails(Message details)
  {
    lDetails.setText(details.toString());
    this.details = details;
  }

  /**
   * Returns the title of this panel.
   * @return the title of this panel.
   */
  public Message getTitle()
  {
    return title;
  }

  /**
   * Returns the details of this panel.
   * @return the details of this panel.
   */
  public Message getDetails()
  {
    return details;
  }
}
