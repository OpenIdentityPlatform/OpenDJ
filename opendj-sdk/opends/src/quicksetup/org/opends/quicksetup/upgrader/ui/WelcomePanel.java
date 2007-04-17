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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader.ui;

import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.event.BrowseActionListener;
import org.opends.quicksetup.upgrader.Upgrader;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;

/**
 * This panel is used to show a welcome message.
 */
public class WelcomePanel extends QuickSetupStepPanel {

  private static final long serialVersionUID = 8695606871542491768L;

  /**
   * Default constructor.
   * @param application Upgrader application
   */
  public WelcomePanel(Upgrader application) {
    super(application);
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle() {
    return getMsg("upgrade-welcome-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions() {
    /*
     * We can use org.opends.server.util.DynamicConstants without problems as it
     * has been added to quicksetup.jar during build time.
     */
    java.util.List<String> args = new ArrayList<String>();
    String msgKey;
    if (Utils.isWebStart()) {
      msgKey = "upgrade-welcome-panel-webstart-instructions";
      String cmd = Utils.isWindows() ? Installation.WINDOWS_UPGRADE_FILE_NAME :
              Installation.UNIX_UPGRADE_FILE_NAME;
      args.add(UIFactory.applyFontToHtml(cmd,
              UIFactory.INSTRUCTIONS_MONOSPACE_FONT));
    } else {
      msgKey = "upgrade-welcome-panel-offline-instructions";
    }
    return getMsg(msgKey, args.toArray(new String[0]));
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel() {
    Component c;

    JPanel pnlBuildInfo = new JPanel();
    pnlBuildInfo.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    // The WebStart version of this tool allows the user to
    // select a build to upgrade.  Running the tool from the
    // command line implies a build.
    if (!Utils.isWebStart()) {

      LabelFieldDescriptor serverLocationDescriptor =
              new LabelFieldDescriptor(getMsg("upgrade-location-label"),
                      getMsg("upgrade-location-tooltip"),
                      LabelFieldDescriptor.FieldType.TEXTFIELD,
                      LabelFieldDescriptor.LabelType.PRIMARY,
                      UIFactory.PATH_FIELD_SIZE);

      JTextComponent tfBuild =
              UIFactory.makeJTextComponent(serverLocationDescriptor, null);

      JButton butBrowse =
              UIFactory.makeJButton(getMsg("browse-button-label"),
                      getMsg("browse-button-tooltip"));

      BrowseActionListener l =
              new BrowseActionListener(tfBuild,
                      BrowseActionListener.BrowseType.LOCATION_DIRECTORY,
                      getMainWindow());
      butBrowse.addActionListener(l);

      JPanel pnlBrowser = Utilities.createBrowseButtonPanel(
              UIFactory.makeJLabel(serverLocationDescriptor),
              tfBuild,
              butBrowse);
      // pnlBrowser.setBorder(BorderFactory.createLineBorder(Color.GREEN));
      gbc.insets.top = 15; // non-standard but looks better
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.anchor = GridBagConstraints.FIRST_LINE_START;
      pnlBuildInfo.add(pnlBrowser, gbc);

      gbc.gridy = 1;
      gbc.weighty = 1.0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.anchor = GridBagConstraints.LINE_START;
      JPanel fill = new JPanel();
      // fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
      pnlBuildInfo.add(fill, gbc);

    } else {

      LabelFieldDescriptor serverLocationDescriptorRO =
              new LabelFieldDescriptor(getMsg("upgrade-location-label"),
                      getMsg("upgrade-location-tooltip"),
                      LabelFieldDescriptor.FieldType.READ_ONLY,
                      LabelFieldDescriptor.LabelType.PRIMARY,
                      UIFactory.PATH_FIELD_SIZE);

      LabelFieldDescriptor serverBuildDescriptorRO =
              new LabelFieldDescriptor(getMsg("upgrade-build-id-label"),
                      getMsg("upgrade-build-id-tooltip"),
                      LabelFieldDescriptor.FieldType.READ_ONLY,
                      LabelFieldDescriptor.LabelType.PRIMARY,
                      UIFactory.PATH_FIELD_SIZE);

      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.anchor = GridBagConstraints.FIRST_LINE_START;
      gbc.insets.top = 15; // non-standard but looks better
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      pnlBuildInfo.add(UIFactory.makeJLabel(serverLocationDescriptorRO), gbc);

      gbc.gridx = 1;
      gbc.gridy = 0;
      gbc.weightx = 1.0;
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.anchor = GridBagConstraints.PAGE_START;
      String installLocation = Utils.getPath(
              getApplication().getInstallation().getRootDirectory());
      pnlBuildInfo.add(
              UIFactory.makeJTextComponent(
                      serverLocationDescriptorRO, installLocation), gbc);

      gbc.gridx = 0;
      gbc.gridy = 1;
      gbc.anchor = GridBagConstraints.LINE_START;
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      pnlBuildInfo.add(UIFactory.makeJLabel(serverBuildDescriptorRO), gbc);

      gbc.gridx = 1;
      gbc.gridy = 1;
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      pnlBuildInfo.add(UIFactory.makeJTextComponent(
              serverBuildDescriptorRO,
              org.opends.server.util.DynamicConstants.BUILD_ID), gbc);

      gbc.gridy = 2;
      gbc.weighty = 1.0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.anchor = GridBagConstraints.LINE_START;
      JPanel fill = new JPanel();
      //fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
      pnlBuildInfo.add(fill, gbc);
    }

    c = pnlBuildInfo;

    return c;
  }


}
