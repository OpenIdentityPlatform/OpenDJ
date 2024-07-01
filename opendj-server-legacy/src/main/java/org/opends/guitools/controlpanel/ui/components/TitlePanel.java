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

package org.opends.guitools.controlpanel.ui.components;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

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

  private LocalizableMessage title;
  private LocalizableMessage details;

  /**
   * Constructor of the panel.
   * @param title the title of the panel.
   * @param details the details of the panel.
   */
  public TitlePanel(LocalizableMessage title, LocalizableMessage details)
  {
    super(new GridBagLayout());
    setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
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
  public void setTitle(LocalizableMessage title)
  {
    lTitle.setText(title+" - ");
    this.title = title;
  }

  /**
   * Sets the details of this panel.
   * @param details the details of this panel.
   */
  public void setDetails(LocalizableMessage details)
  {
    lDetails.setText(details.toString());
    this.details = details;
  }

  /**
   * Returns the title of this panel.
   * @return the title of this panel.
   */
  public LocalizableMessage getTitle()
  {
    return title;
  }

  /**
   * Returns the details of this panel.
   * @return the details of this panel.
   */
  public LocalizableMessage getDetails()
  {
    return details;
  }
}
