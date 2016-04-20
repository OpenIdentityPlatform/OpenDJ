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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import org.opends.guitools.controlpanel.util.Utilities;

/** Class used to have components that provide a valid accessible name. */
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

  @Override
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
