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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installandupgrader.ui;

import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.event.BrowseActionListener;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.installandupgrader.InstallAndUpgradeUserData;
import org.opends.quicksetup.installandupgrader.InstallAndUpgrader;
import org.opends.server.util.DynamicConstants;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This panel is used to show a welcome message asking the user whether to
 * install a new instance or upgrade an existing instance.
 */
public class WelcomePanel extends QuickSetupStepPanel {

  private static final long serialVersionUID = 8696606861642491768L;

  private JLabel lblServerLocation;

  private JTextComponent tcServerLocation;

  private JRadioButton rbInstall;

  private JRadioButton rbUpgrade;

  private JButton butBrowse;

  /**
   * Default constructor.
   * @param application Upgrader application
   */
  public WelcomePanel(InstallAndUpgrader application) {
    super(application);
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData data) {
    super.beginDisplay(data);
    tcServerLocation.setText(data.getServerLocation());
    boolean isUpgrade = ((InstallAndUpgradeUserData)data).isUpgrade();
    rbInstall.setSelected(!isUpgrade);
    rbUpgrade.setSelected(isUpgrade);
    checkEnablingState();
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName) {
    Object v = null;
    if (FieldName.SERVER_TO_UPGRADE_LOCATION.equals(fieldName)) {
      v = tcServerLocation.getText();
    }
    else if (FieldName.IS_UPGRADE.equals(fieldName))
    {
      v = rbUpgrade.isSelected() ? Boolean.TRUE : Boolean.FALSE;
    }
    return v;
  }

  /**
   * {@inheritDoc}
   */
  public void displayFieldInvalid(FieldName fieldName, boolean invalid) {
    UIFactory.TextStyle style;
    if (invalid) {
      style = UIFactory.TextStyle.PRIMARY_FIELD_INVALID;
    } else {
      style = UIFactory.TextStyle.PRIMARY_FIELD_VALID;
    }
    if (FieldName.SERVER_TO_UPGRADE_LOCATION.equals(fieldName)) {
      UIFactory.setTextStyle(lblServerLocation, style);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle() {
    return getMsg("installandupgrade-welcome-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions() {
    /*
     * We can use org.opends.server.util.DynamicConstants without problems as it
     * has been added to quicksetup.jar during build time.
     */
    return getMsg("installandupgrade-welcome-panel-instructions",
            new String[] {
                    DynamicConstants.COMPACT_VERSION_STRING,
                    DynamicConstants.BUILD_ID});
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    JPanel p = UIFactory.makeJPanel();
    p.setLayout(new GridBagLayout());

    rbInstall = UIFactory.makeJRadioButton(
        getMsg("installandupgrader-rbinstall-label"),
        getMsg("installandupgrader-rbinstall-tooltip"),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    rbUpgrade = UIFactory.makeJRadioButton(
        getMsg("installandupgrader-rbupgrade-label"),
        getMsg("installandupgrader-rbupgrade-tooltip"),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    ButtonGroup group = new ButtonGroup();
    group.add(rbInstall);
    group.add(rbUpgrade);

    ActionListener l = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        checkEnablingState();
        ButtonEvent be = new ButtonEvent(ev.getSource(),
            ButtonName.INPUT_PANEL_BUTTON);
        notifyButtonListeners(be);
      }
    };
    rbInstall.addActionListener(l);
    rbUpgrade.addActionListener(l);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;

    p.add(rbInstall, gbc);
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    p.add(rbUpgrade, gbc);
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;

    p.add(createUpgraderPanel(), gbc);

    gbc.insets.top = 0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    p.add(Box.createVerticalGlue(), gbc);
    return p;
  }

  /**
   * Updates the state of the widgets in the panel depending on the radio button
   * that has been chosen.
   *
   */
  private void checkEnablingState()
  {
    boolean enable = rbUpgrade.isSelected();
    lblServerLocation.setEnabled(enable);
    tcServerLocation.setEnabled(enable);
    butBrowse.setEnabled(enable);
  }

  /**
   * Creates the panel containing the fields specific to the upgrader.
   * @return the panel containing the fields specific to the upgrader.
   */
  private Component createUpgraderPanel() {
    Component c;

    LabelFieldDescriptor serverLocationDescriptor =
            new LabelFieldDescriptor(getMsg("upgrade-location-label"),
                    getMsg("upgrade-location-tooltip"),
                    LabelFieldDescriptor.FieldType.TEXTFIELD,
                    LabelFieldDescriptor.LabelType.PRIMARY,
                    UIFactory.PATH_FIELD_SIZE);

    JPanel pnlBuildInfo = UIFactory.makeJPanel();
    pnlBuildInfo.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    UserData userData = getApplication().getUserData();

    lblServerLocation = UIFactory.makeJLabel(serverLocationDescriptor);

    tcServerLocation =
      UIFactory.makeJTextComponent(serverLocationDescriptor,
          userData.getServerLocation());

    butBrowse =
      UIFactory.makeJButton(getMsg("browse-button-label"),
          getMsg("browse-button-tooltip"));

    BrowseActionListener l =
      new BrowseActionListener(tcServerLocation,
          BrowseActionListener.BrowseType.LOCATION_DIRECTORY,
          getMainWindow());
    butBrowse.addActionListener(l);

    JPanel pnlBrowser = Utilities.createBrowseButtonPanel(
        lblServerLocation,
        tcServerLocation,
        butBrowse);
    pnlBrowser.setOpaque(false);

    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    pnlBuildInfo.add(pnlBrowser, gbc);

    gbc.gridy = 1;
    gbc.weighty = 1.0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.LINE_START;
    JPanel fill = UIFactory.makeJPanel();
    // fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    pnlBuildInfo.add(fill, gbc);

    c = pnlBuildInfo;

    return c;
  }
}

