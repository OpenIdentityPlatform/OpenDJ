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

package org.opends.quicksetup.upgrader.ui;

import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.ui.GuiApplication;

import javax.swing.*;
import java.awt.*;

/**
 * This panel allows the user to select a remote or local build for upgrade.
 */
public class ChooseVersionPanel extends QuickSetupStepPanel {

  private static final long serialVersionUID = -6941309163077121917L;

  /**
   * Creates an instance.
   * @param application this panel represents.
   */
  public ChooseVersionPanel(GuiApplication application) {
    super(application);
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel() {
    Component c;

    JPanel p = new JPanel();

    JRadioButton rbRemote = UIFactory.makeJRadioButton(
            getMsg("upgrade-choose-version-remote-label"),
            getMsg("upgrade-choose-version-remote-tooltip"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    JRadioButton rbLocal = UIFactory.makeJRadioButton(
            getMsg("upgrade-choose-version-local-label"),
            getMsg("upgrade-choose-version-local-tooltip"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    JComboBox cboBuild = UIFactory.makeJComboBox();
    cboBuild.setModel(createBuildComboBoxModel());

    // TODO: use UIFactory
    JTextField tfBuild = new JTextField();
    tfBuild.setColumns(20);

    JPanel pnlBrowse = Utilities.createBrowseButtonPanel(
            UIFactory.makeJLabel(null,
                    getMsg("upgrade-choose-version-local-path"),
                    UIFactory.TextStyle.SECONDARY_FIELD_VALID),
            tfBuild,
            UIFactory.makeJButton(getMsg("browse-button-label"),
                    getMsg("browse-button-tooltip")));

    p.setLayout(new GridBagLayout());
    // p.setBorder(BorderFactory.createLineBorder(Color.RED));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = 15; // non-standard but looks better
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    p.add(rbRemote, gbc);

    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 2;
    p.add(cboBuild, gbc);

    gbc.gridy = 1;
    gbc.gridx = 1;
    gbc.weightx = 1.5;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getEmptyInsets();
    JPanel fill = new JPanel();
    // fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    p.add(fill, gbc);

    gbc.gridy = 2;
    gbc.gridx = 0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = 15; // UIFactory.TOP_INSET_RADIOBUTTON;
    p.add(rbLocal, gbc);

    gbc.gridy = 3;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    p.add(pnlBrowse, gbc);

    gbc.gridy = 4;
    gbc.weighty = 1.0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.LINE_START;
    JPanel fill2 = new JPanel();
    //fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    p.add(fill2, gbc);


    c = p;
    return c;
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle() {
    return getMsg("upgrade-choose-version-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions() {
    return getMsg("upgrade-choose-version-panel-instructions");
  }

  private ComboBoxModel createBuildComboBoxModel() {
    // TODO:  populate a list model with builds.
    ComboBoxModel cbm = new DefaultComboBoxModel(new String[] {"xx","YY","ZZ"});
    return cbm;
  }

}
