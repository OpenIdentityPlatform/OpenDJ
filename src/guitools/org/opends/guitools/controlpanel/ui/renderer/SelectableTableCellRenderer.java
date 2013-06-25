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
 *
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
      /**
       * {@inheritDoc}
       */
      public void mousePressed(MouseEvent ev)
      {
        isBeingPressed = true;
        table.repaint();
      }

      /**
       * {@inheritDoc}
       */
      public void mouseReleased(MouseEvent ev)
      {
        isBeingPressed = false;
      }

      /**
       * {@inheritDoc}
       */
      public void mouseExited(MouseEvent ev)
      {
        hasMouseOver = false;
        lastRowMouseOver = -1;
        table.repaint();
      }

      /**
       * {@inheritDoc}
       */
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
      /**
       * {@inheritDoc}
       */
      public void mouseMoved(MouseEvent ev)
      {
        lastRowMouseOver = table.rowAtPoint(ev.getPoint());
        table.repaint();
      }

      /**
       * {@inheritDoc}
       */
      public void mouseDragged(MouseEvent ev)
      {
        lastRowMouseOver = -1;
        table.repaint();
      }
    };
    table.addMouseListener(mouseListener);
    table.addMouseMotionListener(mouseMotionAdapter);
  }

  /**
   * {@inheritDoc}
   */
  public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column)
  {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
        column);
    updateComponent(this, table, row, column, isSelected);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  protected void updateComponent(Component comp, JTable table, int row,
      int column, boolean isSelected)
  {
    if (table.isCellEditable(row, column) && !isSelected)
    {
      comp.setBackground(ColorAndFontConstants.treeBackground);
      comp.setForeground(ColorAndFontConstants.treeForeground);
    }
    else if (isBeingPressed && hasMouseOver && (row == lastRowMouseOver))
    {
      comp.setBackground(pressedBackground);
      comp.setForeground(pressedForeground);
    }
    else if ((hasMouseOver && (row == lastRowMouseOver)) || isSelected)
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
