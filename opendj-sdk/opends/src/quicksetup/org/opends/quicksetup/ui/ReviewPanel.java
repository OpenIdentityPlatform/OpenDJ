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

package org.opends.quicksetup.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Abstract class for rendering a review panel with fields and value
 * that the user can use to confirm an application's operation.
 */
public abstract class ReviewPanel extends QuickSetupStepPanel {

  private JCheckBox checkBox;

  /**
   * Creates an instance.
   * @param application GuiApplication this panel represents
   */
  public ReviewPanel(GuiApplication application) {
    super(application);
  }

  /**
   * Creates the panel containing field names and values.
   * @return JPanel containing fields and values
   */
  protected abstract JPanel createFieldsPanel();

  /**
   * {@inheritDoc}
   */
  final protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(createFieldsPanel(), gbc);

    addVerticalGlue(panel);

    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(getCheckBox(), gbc);

    return panel;
  }

  /**
   * Returns the start server check box.
   * If it does not exist creates the start server check box.
   * @return the start server check box.
   */
  protected JCheckBox getCheckBox()
  {
    if (checkBox == null)
    {
      checkBox =
          UIFactory.makeJCheckBox(getMsg("start-server-label"),
              getMsg("start-server-tooltip"), UIFactory.TextStyle.CHECKBOX);
      checkBox.setOpaque(false);
      checkBox.setSelected(getApplication().getUserData().getStartServer());
    }
    return checkBox;
  }
}
