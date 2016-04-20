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

package org.opends.guitools.controlpanel.event;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.table.TableCellRenderer;

/**
 * This class listens to events and displays a tooltip when the user clicks on
 * the object that registered this listener.
 */
public class ClickTooltipDisplayer extends MouseAdapter
{
  private boolean isTooltipVisible;
  private Popup tipWindow;

  @Override
  public void mouseExited(MouseEvent event)
  {
    hideToolTip(event);
  }

  @Override
  public void mousePressed(MouseEvent event)
  {
    if (isTooltipVisible)
    {
      hideToolTip(event);
    }
    else
    {
      displayToolTip(event);
    }
  }

  /**
   * Displays a tooltip depending on the MouseEvent received.
   * @param event the mouse event.
   */
  private void displayToolTip(MouseEvent event)
  {
    JComponent component = (JComponent)event.getSource();
    String toolTipText;
    if (component instanceof JTable)
    {
      JTable table = (JTable)component;
      int row = table.rowAtPoint(event.getPoint());
      int column = table.columnAtPoint(event.getPoint());
      if (row != -1 && column != -1)
      {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component comp = renderer.getTableCellRendererComponent(table,
            table.getValueAt(row, column), true, true, row, column);
        if (comp instanceof JComponent)
        {
          // The coordinates must be translated.
          Rectangle rect = table.getCellRect(row, column, true);
          int x = event.getPoint().x - rect.x;
          int y = event.getPoint().y - rect.y;
          MouseEvent tEv = new MouseEvent(table, event.getID(),
              event.getWhen(), event.getModifiers(), x, y,
              event.getClickCount(), event.isPopupTrigger(), event.getButton());
          toolTipText = ((JComponent)comp).getToolTipText(tEv);
        }
        else
        {
          toolTipText = null;
        }
      }
      else
      {
        toolTipText = null;
      }
    }
    else
    {
      toolTipText = component.getToolTipText();
    }
    if (toolTipText != null)
    {
      Point preferredLocation = component.getToolTipLocation(event);
      Rectangle sBounds = component.getGraphicsConfiguration().
      getBounds();

      JToolTip tip = component.createToolTip();
      tip.setTipText(toolTipText);
      Dimension size = tip.getPreferredSize();
      Point location = new Point();

      Point screenLocation = component.getLocationOnScreen();
      if(preferredLocation != null)
      {
        location.x = screenLocation.x + preferredLocation.x;
        location.y = screenLocation.y + preferredLocation.y;
      }
      else
      {
        location.x = screenLocation.x + event.getX();
        location.y = screenLocation.y + event.getY() + 20;
      }

      if (location.x < sBounds.x) {
        location.x = sBounds.x;
      }
      else if (location.x - sBounds.x + size.width > sBounds.width) {
        location.x = sBounds.x + Math.max(0, sBounds.width - size.width);
      }
      if (location.y < sBounds.y) {
        location.y = sBounds.y;
      }
      else if (location.y - sBounds.y + size.height > sBounds.height) {
        location.y = sBounds.y + Math.max(0, sBounds.height - size.height);
      }

      PopupFactory popupFactory = PopupFactory.getSharedInstance();
      tipWindow = popupFactory.getPopup(component, tip, location.x, location.y);
      tipWindow.show();
      isTooltipVisible = true;
    }
  }

  /**
   * Hides the tooltip if we are displaying it.
   * @param event the mouse event.
   */
  private void hideToolTip(MouseEvent event)
  {
    if (tipWindow != null)
    {
      tipWindow.hide();
      tipWindow = null;
      isTooltipVisible = false;
    }
  }
}
