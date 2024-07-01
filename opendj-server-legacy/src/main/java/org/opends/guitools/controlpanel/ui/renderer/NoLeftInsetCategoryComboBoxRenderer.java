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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Component;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;

import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
/**
 *  This class is used simply to avoid an inset on the left for the
 *  elements of the combo box.
 *  Since this item is a CategorizedComboBoxElement of type
 *  CategorizedComboBoxElement.Type.REGULAR, it has by default an inset on
 *  the left.
 */
public class NoLeftInsetCategoryComboBoxRenderer extends CustomListCellRenderer
{
  /**
   * The constructor.
   * @param combo the combo box to be rendered.
   */
  public NoLeftInsetCategoryComboBoxRenderer(JComboBox combo)
  {
    super(combo);
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    Component comp = super.getListCellRendererComponent(list, value, index,
        isSelected, cellHasFocus);
    if (value instanceof CategorizedComboBoxElement)
    {
      CategorizedComboBoxElement element = (CategorizedComboBoxElement)value;
      String name = getStringValue(element);
      ((JLabel)comp).setText(name);
    }
    comp.setFont(defaultFont);
    return comp;
  }
}
