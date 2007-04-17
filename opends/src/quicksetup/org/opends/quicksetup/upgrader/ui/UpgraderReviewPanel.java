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
import org.opends.quicksetup.upgrader.Upgrader;

import javax.swing.*;
import java.awt.*;

/**
 * Presents upgrade information to the user to confirm before starting the
 * actual upgrade.
 */
public class UpgraderReviewPanel extends ReviewPanel {

  private static final long serialVersionUID = 5942916658585976799L;

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
    JPanel p = new JPanel();

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
    p.add(UIFactory.makeJTextComponent(serverDescriptor, "/xx/xx/xx"), gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.NONE;
    p.add(UIFactory.makeJLabel(oldVersionDescriptor), gbc);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(UIFactory.makeJTextComponent(oldVersionDescriptor, "abcdefg"), gbc);

    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.fill = GridBagConstraints.NONE;
    p.add(UIFactory.makeJLabel(newVersionDescriptor), gbc);

    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(UIFactory.makeJTextComponent(newVersionDescriptor, "1234567"), gbc);

    return p;
  }
}
