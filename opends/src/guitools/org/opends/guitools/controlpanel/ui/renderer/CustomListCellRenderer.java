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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.ui.StatusGenericPanel;

/**
 * A renderer used in the Control Panel that deals with
 * CategorizedComboBoxElement elements.  It can be used to render JList and
 * JComboBoxes.
 *
 */
public class CustomListCellRenderer implements ListCellRenderer
{
  private ListCellRenderer defaultRenderer;

  /**
   * The separator used to render a non-selectable separator in the combo box.
   */
  protected Component separator;

  /**
   * The default font.
   */
  protected Font defaultFont;

  /**
   * The category font.
   */
  protected Font categoryFont;

  /**
   * Constructor of a renderer to be used with a combo box.
   * @param combo the combo box containing the elements to be rendered.
   */
  public CustomListCellRenderer(JComboBox combo)
  {
    this(combo.getRenderer());
  }

  /**
   * Constructor of a renderer to be used with a list.
   * @param list the list to be rendered.
   */
  public CustomListCellRenderer(JList list)
  {
    this(list.getCellRenderer());
  }

  private CustomListCellRenderer(ListCellRenderer defaultRenderer)
  {
    this.defaultRenderer = defaultRenderer;
    JSeparator sep = new JSeparator();
    separator = new JPanel(new BorderLayout());
    ((JPanel)separator).setOpaque(false);
    ((JPanel)separator).add(sep, BorderLayout.CENTER);
    ((JPanel)separator).setBorder(new EmptyBorder(5, 3, 5, 3));
  }

  /**
   * {@inheritDoc}
   */
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    Component comp;
    if (StatusGenericPanel.COMBO_SEPARATOR.equals(value))
    {
      return separator;
    }
    else if (value instanceof CategorizedComboBoxElement)
    {
      CategorizedComboBoxElement element = (CategorizedComboBoxElement)value;
      String name = getStringValue(element);
      boolean isRegular =
        element.getType() == CategorizedComboBoxElement.Type.REGULAR;
      if (isRegular)
      {
        name = "    "+name;
      }
      comp = defaultRenderer.getListCellRendererComponent(list, name, index,
          isSelected && isRegular, cellHasFocus);
      if (defaultFont == null)
      {
        defaultFont = comp.getFont();
        categoryFont = defaultFont.deriveFont(Font.BOLD | Font.ITALIC);
      }
      if (element.getType() == CategorizedComboBoxElement.Type.REGULAR)
      {
        comp.setFont(defaultFont);
      }
      else
      {
        comp.setFont(categoryFont);
      }
    }
    else
    {
      comp = defaultRenderer.getListCellRendererComponent(list, value, index,
          isSelected, cellHasFocus);
      if (defaultFont == null)
      {
        defaultFont = comp.getFont();
        categoryFont = defaultFont.deriveFont(Font.BOLD | Font.ITALIC);
      }
      comp.setFont(defaultFont);
    }
    return comp;
  }

  /**
   * Returns the String value for a given CategorizedComboBoxElement.
   * @param desc the combo box element.
   * @return the String value for a given CategorizedComboBoxElement.
   */
  protected String getStringValue(CategorizedComboBoxElement desc)
  {
    return String.valueOf(desc.getValue());
  }
}
