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

package org.opends.guitools.controlpanel.ui.components;

import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.ui.renderer.TreeCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The tree that is used in different places in the Control Panel (schema
 * browser, index browser or the LDAP entries browser).  It renders in a
 * different manner than the default tree (selection takes the whole width
 * of the tree, in a similar manner as happens with trees in Mac OS).
 *
 */
public class CustomTree extends JTree
{
  private static final long serialVersionUID = -8351107707374485555L;
  private Set<MouseListener> mouseListeners;
  private JPopupMenu popupMenu;
  private final int MAX_ICON_HEIGHT = 18;

  /**
   * Internal enumeration used to translate mouse events.
   *
   */
  private enum NewEventType
  {
    MOUSE_PRESSED, MOUSE_CLICKED, MOUSE_RELEASED
  };

  /**
   * {@inheritDoc}
   */
  public void paintComponent(Graphics g)
  {
    int[] selectedRows = this.getSelectionRows();
    if (selectedRows == null)
    {
      selectedRows = new int[] {};
    }
    Insets insets = getInsets();
    int w = getWidth( )  - insets.left - insets.right;
    int h = getHeight( ) - insets.top  - insets.bottom;
    int x = insets.left;
    int y = insets.top;
    int nRows = getRowCount();
    for ( int i = 0; i < nRows; i++)
    {
      int rowHeight = getRowBounds( i ).height;
      boolean isSelected = false;
      for (int j=0; j<selectedRows.length; j++)
      {
        if (selectedRows[j] == i)
        {
          isSelected = true;
          break;
        }
      }
      if (isSelected)
      {
        g.setColor(TreeCellRenderer.selectionBackground);
      }
      else
      {
        g.setColor(TreeCellRenderer.nonselectionBackground);
      }
      g.fillRect( x, y, w, rowHeight );
      y += rowHeight;
    }
    final int remainder = insets.top + h - y;
    if ( remainder > 0 )
    {
      g.setColor(TreeCellRenderer.nonselectionBackground);
      g.fillRect(x, y, w, remainder);
    }

    boolean isOpaque = isOpaque();
    setOpaque(false);
    super.paintComponent(g);
    setOpaque(isOpaque);
  }

  /**
   * Sets a popup menu that will be displayed when the user clicks on the tree.
   * @param popMenu the popup menu.
   */
  public void setPopupMenu(JPopupMenu popMenu)
  {
    this.popupMenu = popMenu;
  }

  /**
   * Default constructor.
   *
   */
  public CustomTree()
  {
    putClientProperty("JTree.lineStyle", "Angled");
    // This mouse listener is used so that when the user clicks on a row,
    // the items are selected (is not required to click directly on the label).
    // This code tries to have a similar behavior as in Mac OS).
    MouseListener mouseListener = new MouseAdapter()
    {
      private boolean ignoreEvents;
      /**
       * {@inheritDoc}
       */
      public void mousePressed(MouseEvent ev)
      {
        if (ignoreEvents)
        {
          return;
        }
        MouseEvent newEvent = getTranslatedEvent(ev);

        if (Utilities.isMacOS() && ev.isPopupTrigger() &&
            (ev.getButton() != MouseEvent.BUTTON1))
        {
          MouseEvent baseEvent = ev;
          if (newEvent != null)
          {
            baseEvent = newEvent;
          }
          int mods = baseEvent.getModifiersEx();
          mods &= (InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK |
              InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK);
          mods |=  InputEvent.BUTTON1_DOWN_MASK;
          final MouseEvent  macEvent = new MouseEvent(
              baseEvent.getComponent(),
              baseEvent.getID(),
                System.currentTimeMillis(),
                mods,
                baseEvent.getX(),
                baseEvent.getY(),
                baseEvent.getClickCount(),
                false,
                MouseEvent.BUTTON1);
          // This is done to select the node when the user does a right
          // click on Mac OS.
          notifyNewEvent(macEvent, NewEventType.MOUSE_PRESSED);
        }

        if (ev.isPopupTrigger() && (popupMenu != null)) {
          if ((getPathForLocation(ev.getPoint().x, ev.getPoint().y) != null) ||
              (newEvent != null))
          {
            popupMenu.show(ev.getComponent(), ev.getX(), ev.getY());
          }
        }
        if (newEvent != null)
        {
          notifyNewEvent(newEvent, NewEventType.MOUSE_PRESSED);
        }
      }

      /**
       * {@inheritDoc}
       */
      public void mouseReleased(MouseEvent ev)
      {
        if (ignoreEvents)
        {
          return;
        }
        MouseEvent newEvent = getTranslatedEvent(ev);
        if (ev.isPopupTrigger() && (popupMenu != null) &&
            !popupMenu.isVisible()) {
          if ((getPathForLocation(ev.getPoint().x, ev.getPoint().y) != null) ||
              (newEvent != null))
          {
            popupMenu.show(ev.getComponent(), ev.getX(), ev.getY());
          }
        }

        if (newEvent != null)
        {
          notifyNewEvent(newEvent, NewEventType.MOUSE_RELEASED);
        }
      }

      /**
       * {@inheritDoc}
       */
      public void mouseClicked(MouseEvent ev)
      {
        if (ignoreEvents)
        {
          return;
        }
        MouseEvent newEvent = getTranslatedEvent(ev);
        if (newEvent != null)
        {
          notifyNewEvent(newEvent, NewEventType.MOUSE_CLICKED);
        }
      }

      private void notifyNewEvent(MouseEvent newEvent, NewEventType type)
      {
        ignoreEvents = true;
        // New ArrayList to avoid concurrent modifications (the listeners
        // could be unregistering themselves).
        for (MouseListener mouseListener :
          new ArrayList<MouseListener>(mouseListeners))
        {
          if (mouseListener != this)
          {
            switch (type)
            {
            case MOUSE_RELEASED:
              mouseListener.mouseReleased(newEvent);
              break;
            case MOUSE_CLICKED:
              mouseListener.mouseClicked(newEvent);
              break;
            default:
              mouseListener.mousePressed(newEvent);
            }
          }
        }
        ignoreEvents = false;
      }

      private MouseEvent getTranslatedEvent(MouseEvent ev)
      {
        MouseEvent newEvent = null;
        int x = ev.getPoint().x;
        int y = ev.getPoint().y;
        if (getPathForLocation(x, y) == null)
        {
          TreePath path = getWidePathForLocation(x, y);
          if (path != null)
          {
            Rectangle r = getPathBounds(path);
            if (r != null)
            {
              int newX = r.x + (r.width / 2);
              int newY = r.y + (r.height / 2);
              // Simulate an event
              newEvent = new MouseEvent(
                  ev.getComponent(),
                  ev.getID(),
                  ev.getWhen(),
                  ev.getModifiersEx(),
                  newX,
                  newY,
                  ev.getClickCount(),
                  ev.isPopupTrigger(),
                  ev.getButton());
            }
          }
        }
        return newEvent;
      }
    };
    addMouseListener(mouseListener);
    if (getRowHeight() <= MAX_ICON_HEIGHT)
    {
      setRowHeight(MAX_ICON_HEIGHT + 1);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void addMouseListener(MouseListener mouseListener)
  {
    super.addMouseListener(mouseListener);
    if (mouseListeners == null)
    {
      mouseListeners = new HashSet<MouseListener>();
    }
    mouseListeners.add(mouseListener);
  }

  /**
   * {@inheritDoc}
   */
  public void removeMouseListener(MouseListener mouseListener)
  {
    super.removeMouseListener(mouseListener);
    mouseListeners.remove(mouseListener);
  }

  private TreePath getWidePathForLocation(int x, int y)
  {
    TreePath path = null;
    TreePath closestPath = getClosestPathForLocation(x, y);
    if (closestPath != null)
    {
      Rectangle pathBounds = getPathBounds(closestPath);
      if (pathBounds != null &&
         x >= pathBounds.x && x < (getX() + getWidth()) &&
         y >= pathBounds.y && y < (pathBounds.y + pathBounds.height))
      {
        path = closestPath;
      }
    }
    return path;
  }
}
