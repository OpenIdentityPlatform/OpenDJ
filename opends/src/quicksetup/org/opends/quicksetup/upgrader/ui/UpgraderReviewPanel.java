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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader.ui;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.UserData;
import org.opends.quicksetup.BuildInformation;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.ReviewPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.upgrader.Upgrader;
import org.opends.quicksetup.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;
import java.util.logging.Level;

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

    // Unfortunately these string are different.
    // The new build is the build display name that
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
    if (fieldName == FieldName.SERVER_START_UPGRADER) {
      value = getBottomComponent().isSelected();
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
  protected Message getTitle() {
    return INFO_UPGRADE_REVIEW_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions() {
    return INFO_UPGRADE_REVIEW_PANEL_INSTRUCTIONS.get();
  }

  /**
   * {@inheritDoc}
   */
  protected JPanel createFieldsPanel() {
    JPanel p = UIFactory.makeJPanel();

    LabelFieldDescriptor serverDescriptor = new LabelFieldDescriptor(
      INFO_UPGRADE_REVIEW_PANEL_SERVER_LABEL.get(),
      INFO_UPGRADE_REVIEW_PANEL_SERVER_TOOLTIP.get(),
      LabelFieldDescriptor.FieldType.READ_ONLY,
      LabelFieldDescriptor.LabelType.PRIMARY,
      0
    );

    LabelFieldDescriptor oldVersionDescriptor = new LabelFieldDescriptor(
      INFO_UPGRADE_REVIEW_PANEL_OLD_VERSION_LABEL.get(),
      INFO_UPGRADE_REVIEW_PANEL_OLD_VERSION_TOOLTIP.get(),
      LabelFieldDescriptor.FieldType.READ_ONLY,
      LabelFieldDescriptor.LabelType.PRIMARY,
      0
    );

    LabelFieldDescriptor newVersionDescriptor = new LabelFieldDescriptor(
      INFO_UPGRADE_REVIEW_PANEL_NEW_VERSION_LABEL.get(),
      INFO_UPGRADE_REVIEW_PANEL_NEW_VERSION_TOOLTIP.get(),
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

    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    LabelFieldDescriptor[] descs = {serverDescriptor, oldVersionDescriptor,
        newVersionDescriptor};
    JLabel[] labels = {tcServerLocation, tcOldBuild, tcNewBuild};

    for (int i=0; i<descs.length; i++)
    {
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      if (i > 0)
      {
        gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      } else
      {
        gbc.insets.top = 0;
      }
      gbc.insets.left = 0;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      p.add(UIFactory.makeJLabel(descs[i]), gbc);
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;

      gbc.weightx = 1.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      p.add(labels[i], gbc);
    }

    return p;
  }

  private String getServerToUpgrade() {
    return getUserData().getServerLocation();
  }

  private String getOldBuildString() {
    return Utils.getBuildString(getApplication().getInstallation());
  }

  /**
   * Gets the string by which the new build is known in the
   * available builds page.
   * @return String indicating the new build
   */
  private String getNewBuildString() {
    String b = null;
    try {
      BuildInformation bi = BuildInformation.getCurrent();
      if (bi != null) {
        b = bi.toString();
      }
    } catch (ApplicationException e) {
      LOG.log(Level.INFO, "error trying to determine new build string", e);
    }
    if (b == null) {
      b = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
    }
    return b;
  }

  /**
   * {@inheritDoc}
   */
  protected JCheckBox getBottomComponent()
  {
    if (checkBox == null)
    {
      checkBox =
          UIFactory.makeJCheckBox(INFO_UPGRADE_REVIEW_PANEL_START_SERVER.get(),
              INFO_START_SERVER_TOOLTIP.get(), UIFactory.TextStyle.CHECKBOX);
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
