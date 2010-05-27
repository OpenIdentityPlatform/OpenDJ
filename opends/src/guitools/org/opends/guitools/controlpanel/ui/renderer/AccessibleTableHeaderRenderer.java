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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import org.opends.guitools.controlpanel.util.Utilities;

/**
 * Class used to have components that provide a valid accessible name.
 */
public class AccessibleTableHeaderRenderer implements TableCellRenderer
{
  private TableCellRenderer renderer;
  /**
   * Constructor of the renderer.
   * @param renderer the renderer to be used as base.
   */
  public AccessibleTableHeaderRenderer(TableCellRenderer renderer)
  {
    this.renderer = renderer;
  }
  /**
   * {@inheritDoc}
   */
  public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column)
  {
    Component comp = renderer.getTableCellRendererComponent(table, value,
        isSelected, hasFocus, row, column);
    comp.getAccessibleContext().setAccessibleName(
        Utilities.stripHtmlToSingleLine(String.valueOf(value)));
    return comp;
  }

}
