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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.quicksetup.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Abstract class for rendering a review panel with fields and value
 * that the user can use to confirm an application's operation.
 */
public abstract class ReviewPanel extends QuickSetupStepPanel {

  private static final long serialVersionUID = 509534079919269557L;

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

  @Override
  protected Component createInputPanel()
  {
    JPanel panel = UIFactory.makeJPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(createFieldsPanel(), gbc);

    addVerticalGlue(panel);

    JComponent chk = getBottomComponent();
    if (chk != null) {
      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.weighty = 0.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      panel.add(chk, gbc);
    }

    return panel;
  }

  /**
   * Returns the component that will placed at the bottom of the panel.
   * In the case of the installer and the uninstaller this is basically the
   * start server check box.
   * If it does not exist creates the component.
   * @return the component that will placed at the bottom of the panel.
   */
  protected abstract JComponent getBottomComponent();
}
