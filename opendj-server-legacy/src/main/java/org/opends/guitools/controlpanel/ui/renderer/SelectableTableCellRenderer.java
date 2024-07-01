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

package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JTable;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/**
 * A table cell renderer that updates the rendering of the cells when the user
 * moves the mouse over the table.  This is done to provide a visual hint that
 * the table can be selected.
 */
public class SelectableTableCellRenderer extends CustomCellRenderer
{
  private static final long serialVersionUID = 6855042914121526677L;
  private boolean hasMouseOver;
  private boolean isBeingPressed;
  private int lastRowMouseOver;

  private static final Color pressedBackground =
    ColorAndFontConstants.pressedBackground;

  private static final Color pressedForeground =
    ColorAndFontConstants.pressedForeground;

  private static final Color mouseOverBackground =
    ColorAndFontConstants.mouseOverBackground;

  private static final Color mouseOverForeground =
    ColorAndFontConstants.mouseOverForeground;

  /**
   * Sets the table that will be rendered by this renderer.
   * @param table the table to be rendered.
   */
  public void setTable(final JTable table)
  {
    MouseAdapter mouseListener = new MouseAdapter()
    {
      @Override
      public void mousePressed(MouseEvent ev)
      {
        isBeingPressed = true;
        table.repaint();
      }

      @Override
      public void mouseReleased(MouseEvent ev)
      {
        isBeingPressed = false;
      }

      @Override
      public void mouseExited(MouseEvent ev)
      {
        hasMouseOver = false;
        lastRowMouseOver = -1;
        table.repaint();
      }

      @Override
      public void mouseEntered(MouseEvent ev)
      {
        if (ev.getSource() == table)
        {
          hasMouseOver = true;
          lastRowMouseOver = table.rowAtPoint(ev.getPoint());
        }
        else
        {
          mouseExited(ev);
        }
      }
    };
    MouseMotionAdapter mouseMotionAdapter = new MouseMotionAdapter()
    {
      @Override
      public void mouseMoved(MouseEvent ev)
      {
        lastRowMouseOver = table.rowAtPoint(ev.getPoint());
        table.repaint();
      }

      @Override
      public void mouseDragged(MouseEvent ev)
      {
        lastRowMouseOver = -1;
        table.repaint();
      }
    };
    table.addMouseListener(mouseListener);
    table.addMouseMotionListener(mouseMotionAdapter);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column)
  {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
        column);
    updateComponent(this, table, row, column, isSelected);
    return this;
  }

  void updateComponent(Component comp, JTable table, int row,
      int column, boolean isSelected)
  {
    if (table.isCellEditable(row, column) && !isSelected)
    {
      comp.setBackground(ColorAndFontConstants.treeBackground);
      comp.setForeground(ColorAndFontConstants.treeForeground);
    }
    else if (isBeingPressed && hasMouseOver && row == lastRowMouseOver)
    {
      comp.setBackground(pressedBackground);
      comp.setForeground(pressedForeground);
    }
    else if ((hasMouseOver && row == lastRowMouseOver) || isSelected)
    {
      comp.setBackground(mouseOverBackground);
      comp.setForeground(mouseOverForeground);
    }
    else
    {
      comp.setBackground(ColorAndFontConstants.treeBackground);
      comp.setForeground(ColorAndFontConstants.treeForeground);
    }
  }
}
