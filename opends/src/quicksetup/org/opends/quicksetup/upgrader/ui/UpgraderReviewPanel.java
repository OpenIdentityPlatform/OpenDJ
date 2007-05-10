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

import org.opends.quicksetup.ui.ReviewPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.upgrader.Upgrader;
import org.opends.quicksetup.upgrader.UpgradeUserData;
import org.opends.quicksetup.upgrader.Build;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.BuildInformation;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Presents upgrade information to the user to confirm before starting the
 * actual upgrade.
 */
public class UpgraderReviewPanel extends ReviewPanel {

  static private final Logger LOG =
          Logger.getLogger(UpgraderReviewPanel.class.getName());

  private static final long serialVersionUID = 5942916658585976799L;

  JLabel tcServerLocation = null;
  JLabel tcOldBuild = null;
  JLabel tcNewBuild = null;
  private JCheckBox checkBox;

  /**
   * Creates an instance.
   * @param application Application represented by this panel
   */
  public UpgraderReviewPanel(Upgrader application) {
    super(application);
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData data) {
    tcServerLocation.setText(getServerToUpgrade());

    // Unfortunately these string are different.  The
    // old build string is the build ID (14 digit number)
    // and the new build is the build display name that
    // appears in the available builds information page.
    // It is currently not feasible to correlate these.
    tcOldBuild.setText(getOldBuildString());
    tcNewBuild.setText(getNewBuildString());

  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName) {
    Object value = null;
    if (fieldName == FieldName.SERVER_START) {
      value = getCheckBox().isSelected();
    }
    return value;
  }

  /**
   * {@inheritDoc}
   */
  public boolean blockingBeginDisplay() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle() {
    return getMsg("upgrade-review-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions() {
    return getMsg("upgrade-review-panel-instructions");
  }

  /**
   * {@inheritDoc}
   */
  protected JPanel createFieldsPanel() {
    JPanel p = UIFactory.makeJPanel();

    LabelFieldDescriptor serverDescriptor = new LabelFieldDescriptor(
      getMsg("upgrade-review-panel-server-label"),
      getMsg("upgrade-review-panel-server-tooltip"),
      LabelFieldDescriptor.FieldType.READ_ONLY,
      LabelFieldDescriptor.LabelType.PRIMARY,
      0
    );

    LabelFieldDescriptor oldVersionDescriptor = new LabelFieldDescriptor(
      getMsg("upgrade-review-panel-old-version-label"),
      getMsg("upgrade-review-panel-old-version-tooltip"),
      LabelFieldDescriptor.FieldType.READ_ONLY,
      LabelFieldDescriptor.LabelType.PRIMARY,
      0
    );

    LabelFieldDescriptor newVersionDescriptor = new LabelFieldDescriptor(
      getMsg("upgrade-review-panel-new-version-label"),
      getMsg("upgrade-review-panel-new-version-tooltip"),
      LabelFieldDescriptor.FieldType.READ_ONLY,
      LabelFieldDescriptor.LabelType.PRIMARY,
      0
    );

    // Here we use labels instead of calling UIFactory.makeJTextComponent.
    // which supplies us with a JEditorPane for read-only values.  We don't
    // know the values of these fields at this time.  If we use JEditorPanes
    // here when the panel is made visible there is an effect where the text
    // is seen racing left when first seen.
    tcServerLocation = UIFactory.makeJLabel(serverDescriptor);
    UIFactory.setTextStyle(tcServerLocation, UIFactory.TextStyle.READ_ONLY);
    tcOldBuild = UIFactory.makeJLabel(oldVersionDescriptor);
    UIFactory.setTextStyle(tcOldBuild, UIFactory.TextStyle.READ_ONLY);
    tcNewBuild = UIFactory.makeJLabel(newVersionDescriptor);
    UIFactory.setTextStyle(tcNewBuild, UIFactory.TextStyle.READ_ONLY);

    p.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.NONE;
    p.add(UIFactory.makeJLabel(serverDescriptor), gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(tcServerLocation, gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.NONE;
    p.add(UIFactory.makeJLabel(oldVersionDescriptor), gbc);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(tcOldBuild, gbc);

    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.fill = GridBagConstraints.NONE;
    p.add(UIFactory.makeJLabel(newVersionDescriptor), gbc);

    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(tcNewBuild, gbc);

    return p;
  }

  private String getServerToUpgrade() {
    return getUserData().getServerLocation();
  }

  private String getOldBuildString() {
    String oldVersion = null;
    try {
      BuildInformation bi = getApplication().getInstallation().
              getBuildInformation();
      if (bi != null) {
        oldVersion = bi.toString();
      }
    } catch (ApplicationException e) {
      LOG.log(Level.INFO, "error", e);
    }
    if (oldVersion == null) {
      oldVersion = getMsg("upgrade-build-id-unknown");
    }
    return oldVersion;
  }

  /**
   * Gets the string by which the new build is known in the
   * available builds page.
   * @return String indicating the new build
   */
  private String getNewBuildString() {
    String newVersion;
    UpgradeUserData uud = (UpgradeUserData)getUserData();
    Build build = uud.getInstallPackageToDownload();
    if (build != null) {
      newVersion = build.getDisplayName();
    } else {
      // TODO: figure out the build from the zip somehow
      newVersion = getMsg("upgrade-build-id-unknown");
    }
    return newVersion;
  }

  /**
   * {@inheritDoc}
   */
  protected JCheckBox getCheckBox()
  {
    if (checkBox == null)
    {
      checkBox =
          UIFactory.makeJCheckBox(getMsg("upgrade-review-panel-start-server"),
              getMsg("start-server-tooltip"), UIFactory.TextStyle.CHECKBOX);
      checkBox.setSelected(getApplication().getUserData().getStartServer());
    }
    return checkBox;
  }

//  public static void main(String[] args) {
//    final UserData ud = new UpgradeUserData();
//    ud.setServerLocation("XXX/XXXXX/XX/XXXXXXXXXXXX/XXXX");
//    Upgrader app = new Upgrader();
//    app.setUserData(ud);
//    final UpgraderReviewPanel p = new UpgraderReviewPanel(app);
//    p.initialize();
//    JFrame frame = new JFrame();
//    frame.getContentPane().add(p);
//    frame.addComponentListener(new ComponentAdapter() {
//      public void componentHidden(ComponentEvent componentEvent) {
//        System.exit(0);
//      }
//    });
//    frame.pack();
//    frame.setVisible(true);
//    new Thread(new Runnable() {
//      public void run() {
//        p.beginDisplay(ud);
//      }
//    }).start();
//
//  }
}
