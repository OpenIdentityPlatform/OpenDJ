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

import org.opends.quicksetup.util.Utils;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * A set of utilities specific to GUI QuickSetup applications.
 */
public class Utilities {

  /**
   * Creates a panel with a field and a browse button.
   * @param lbl JLabel for the field
   * @param tf JTextField for holding the browsed data
   * @param but JButton for invoking browse action
   * @return the created panel.
   */
  static public JPanel createBrowseButtonPanel(JLabel lbl,
                                         JTextComponent tf,
                                         JButton but)
  {
    GridBagConstraints gbc = new GridBagConstraints();

    JPanel panel = UIFactory.makeJPanel();
    panel.setLayout(new GridBagLayout());

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = 4;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(lbl, gbc);

    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    gbc.gridwidth--;
    gbc.weightx = 0.1;
    panel.add(tf, gbc);

    gbc.insets.left = UIFactory.LEFT_INSET_BROWSE;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    panel.add(but, gbc);

    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  /**
   * Sets a frames image icon to the standard OpenDS icon appropriate
   * for the running platform.
   *
   * @param frame for which the icon will be set
   */
  static public void setFrameIcon(JFrame frame) {
    UIFactory.IconType ic;
    if (Utils.isMacOS()) {
      ic = UIFactory.IconType.MINIMIZED_MAC;
    } else {
      ic = UIFactory.IconType.MINIMIZED;
    }
    frame.setIconImage(UIFactory.getImageIcon(ic).getImage());
  }

}
