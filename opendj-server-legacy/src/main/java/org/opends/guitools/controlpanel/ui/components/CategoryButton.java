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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JCheckBox;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.Category;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.border.SelectedCategoryBorder;
import org.opends.guitools.controlpanel.util.Utilities;


/**
 * A component that acts as a checkbox but uses some customized buttons to
 * indicate the selected and unselected states.  This component is used in the
 * 'Import LDIF' and 'Export LDIF' panels to hide parts of the dialog.
 */
class CategoryButton extends JCheckBox
{
  private static final long serialVersionUID = 6191857253411571940L;
  private Border buttonSelectedBorder;
  private Border buttonUnselectedBorder;
  private static final Color backgroundColor =
    ColorAndFontConstants.greyBackground;

  /**
   * Constructor of the category button.
   * @param category the category associated with this button.
   */
  public CategoryButton(Category category)
  {
    super();
    setText(category.getName().toString());
    setHorizontalTextPosition(SwingConstants.TRAILING);
    setHorizontalAlignment(SwingConstants.LEADING);
    setFocusPainted(true);
    setRolloverEnabled(false);
    setContentAreaFilled(false);
    setOpaque(true);
    setBorderPainted(true);
    setSelectedIcon(Utilities.createImageIcon(
        IconPool.IMAGE_PATH+"/downarrow.png"));
    setIcon(Utilities.createImageIcon(IconPool.IMAGE_PATH+"/rightarrow.png"));
    setRolloverIcon(getIcon());
    setRolloverSelectedIcon(getSelectedIcon());
    setPressedIcon(getSelectedIcon());
    buttonSelectedBorder = new SelectedCategoryBorder();
    buttonUnselectedBorder = new EmptyBorder(5, 5, 5, 5);
    setBorder(isSelected() ? buttonSelectedBorder : buttonUnselectedBorder);
    addChangeListener(new ChangeListener()
    {
      @Override
      public void stateChanged(ChangeEvent e)
      {
        setBorder(isSelected() ? buttonSelectedBorder : buttonUnselectedBorder);
      }
    });
    setBackground(backgroundColor);
    setForeground(ColorAndFontConstants.categoryForeground);
    setFont(ColorAndFontConstants.categoryFont);
  }

  @Override
  public void updateUI()
  {
    super.updateUI();
    // some look and feels replace our border, so take it back
    setBorder(isSelected() ? buttonSelectedBorder : buttonUnselectedBorder);
  }

  @Override
  protected void paintComponent(Graphics g) {
    setBackground(backgroundColor);
    g.setColor(backgroundColor);
    g.setFont(ColorAndFontConstants.categoryFont);
    Dimension size = getSize();
    g.fillRect(0, 0, size.width, size.height);
    setBorder(isSelected() ? buttonSelectedBorder : buttonUnselectedBorder);
    super.paintComponent(g);
  }
}
