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

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.components.LabelWithHelpIcon;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * Class used to render the tables.
 */
public class CustomCellRenderer extends LabelWithHelpIcon
implements TableCellRenderer
{
  private static final long serialVersionUID = -8604332267021523835L;
  /**
   * The border of the first column.
   */
  protected final static Border column0Border =
    BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 1, 0, 0,
          ColorAndFontConstants.gridColor),
          BorderFactory.createEmptyBorder(4, 4, 4, 4));
  /**
   * The default border.
   */
  public final static Border defaultBorder =
    BorderFactory.createEmptyBorder(4, 4, 4, 4);
  private static Border defaultFocusBorder;

  /**
   * Default constructor.
   */
  public CustomCellRenderer()
  {
    super(Message.EMPTY, null);
    setHelpIconVisible(false);
    setFont(ColorAndFontConstants.tableFont);
    setOpaque(true);
    setBackground(ColorAndFontConstants.treeBackground);
    setForeground(ColorAndFontConstants.treeForeground);
  }

  /**
   * {@inheritDoc}
   */
  public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column) {
    if (value instanceof String)
    {
      String s = (String)value;
      if (s.indexOf("<html>") == 0)
      {
        value = "<html>"+
        Utilities.applyFont(s.substring(6), ColorAndFontConstants.tableFont);
      }
      setText((String)value);
    }
    else
    {
      setText(String.valueOf(value));
    }

    if (hasFocus)
    {
      setBorder(getDefaultFocusBorder(table, value, isSelected, row, column));
    }
    else if (column == 0)
    {
      setBorder(column0Border);
    }
    else
    {
      setBorder(defaultBorder);
    }
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public String getToolTipText(MouseEvent ev)
  {
    Rectangle r = new Rectangle();
    r.x = label.getPreferredSize().width + INSET_WITH_ICON;
    r.y = 0;
    r.width = iconLabel.getPreferredSize().width;
    r.height = iconLabel.getPreferredSize().height;

    if (r.contains(ev.getPoint()))
    {
      return getHelpTooltip();
    }
    else
    {
      return null;
    }
  }

  /**
   * Returns the border to be used for a given cell in a table.
   * @param table the table.
   * @param value the value to be rendered.
   * @param isSelected whether the row is selected or not.
   * @param row the row number of the cell.
   * @param column the column number of the cell.
   * @return the border to be used for a given cell in a table.
   */
  public static Border getDefaultFocusBorder(JTable table, Object value,
      boolean isSelected, int row, int column)
  {
    if (defaultFocusBorder == null)
    {
      DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
      JComponent comp = (JComponent)
      renderer.getTableCellRendererComponent(table, value, isSelected,
          true, row, column);
      Border border = comp.getBorder();
      if (border != null)
      {
        defaultFocusBorder = border;
      }
      else
      {
        defaultFocusBorder = defaultBorder;
      }
    }
    return defaultFocusBorder;
  }
}
