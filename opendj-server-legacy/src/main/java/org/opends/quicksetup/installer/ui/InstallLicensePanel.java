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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import org.forgerock.i18n.LocalizableMessage;
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

/** This panel is used to show a welcome message. */
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

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_LICENSE_PANEL_TITLE.get();
  }

  @Override
  protected LocalizableMessage getInstructions()
  {
    return null;
  }

  private JCheckBox acceptCheck;

  @Override
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

  @Override
  protected boolean requiresScroll()
  {
    return false;
  }

  /** Adds the required action listeners to the fields. */
  private void addActionListeners()
  {
    final ActionListener l = new ActionListener()
    {
      @Override
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
