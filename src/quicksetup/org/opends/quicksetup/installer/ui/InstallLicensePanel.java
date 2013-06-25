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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.quicksetup.installer.ui;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.LicenseFile;
import org.opends.quicksetup.ButtonName;

/**
 * This panel is used to show a welcome message.
 *
 */
public class InstallLicensePanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 6209217138897900860L;

  /**
   * Default constructor.
   * @param app Application this panel represents
   */
  public InstallLicensePanel(GuiApplication app)
  {
    super(app);
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_LICENSE_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
  {
    return null;
  }

  private JCheckBox acceptCheck;

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    // No input in this panel
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    JLabel l =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            INFO_LICENSE_DETAILS_LABEL.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.bottom = 3;
    panel.add(l, gbc);

    JTextArea detailsTextArea = new JTextArea(10, 50);
    detailsTextArea.setBackground(
        UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    detailsTextArea.setFont(UIFactory.TEXTFIELD_FONT);
    detailsTextArea.setText(LicenseFile.getText());

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;
    panel.add(new JScrollPane(detailsTextArea), gbc);

    acceptCheck = UIFactory.makeJCheckBox(INFO_LICENSE_CLICK_LABEL.get(),
        null,
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    acceptCheck.setOpaque(false);
    acceptCheck.setSelected(false);

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 0.0;
    panel.add(acceptCheck, gbc);

    addActionListeners();

    return panel;
  }

  /**
   * {@inheritDoc}
   */
  protected boolean requiresScroll()
  {
    return false;
  }

  /**
   * Adds the required action listeners to the fields.
   */
  private void addActionListeners()
  {
    final ActionListener l = new ActionListener()
    {
      public void actionPerformed(ActionEvent e)
      {
        // Enable or disable Next button as user clicks approval button
        getQuickSetup().getDialog().setButtonEnabled(
            ButtonName.NEXT, acceptCheck.isSelected());

        // Save approval status for navigation
        LicenseFile.setApproval(acceptCheck.isSelected());
      }
    };

    acceptCheck.addActionListener(l);
  }
}
