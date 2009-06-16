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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/**
 * Class used to render the task tables.
 */
public class TaskCellRenderer extends DefaultTableCellRenderer
{
  private static final long serialVersionUID = -84332267021523835L;
  /**
   * The border of the first column.
   * TODO: modify CustomCellRenderer to make this public.
   */
  protected final static Border column0Border =
    BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 1, 0, 0,
          ColorAndFontConstants.gridColor),
          BorderFactory.createEmptyBorder(4, 4, 4, 4));
  /**
   * The default border.
   */
  public final static Border defaultBorder = CustomCellRenderer.defaultBorder;

  /**
   * Default constructor.
   */
  public TaskCellRenderer()
  {
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
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
        row, column);

    if (hasFocus)
    {
      setBorder(
          CustomCellRenderer.getDefaultFocusBorder(table, value, isSelected,
              row, column));
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
}

