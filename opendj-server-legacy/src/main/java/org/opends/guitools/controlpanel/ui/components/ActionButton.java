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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.Action;
import org.opends.guitools.controlpanel.datamodel.Category;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/**
 * A basic extension of a button that changes its rendering so that the looks
 * are more similar to a row in a list.  It is used in the actions on the left
 * of the main Control Center dialog (in actions like 'Manage Entries...',
 * 'Import from LDIF...' etc.
 */
public class ActionButton extends JButton
{
  private static final long serialVersionUID = -1898192406268037714L;

  private static final Border buttonBorder;
  private static final Border focusBorder;
  private final Action action;
  private boolean isBeingPressed;
  private boolean hasMouseOver;
  static
  {
    //Calculate border based on category settings
    Category cat = new Category();
    cat.setName(LocalizableMessage.EMPTY);
    CategoryButton b = new CategoryButton(cat);
    int n = b.getIconTextGap() + b.getIcon().getIconWidth() +
    b.getBorder().getBorderInsets(b).left;
    buttonBorder = new EmptyBorder(5, n, 5, 25);
    Border highlightBorder =
      UIManager.getBorder("List.focusCellHighlightBorder");
    // This is required (see issue
    // https://opends.dev.java.net/issues/show_bug.cgi?id=4400)
    // since in OpenJDK the CompoundBorder class does not handle properly
    // null insets.
    if (highlightBorder != null)
    {
      try
      {
        b.setBorder(BorderFactory.createCompoundBorder(
          highlightBorder, buttonBorder));
      }
      catch (Throwable t)
      {
        highlightBorder = null;
      }
    }
    if (highlightBorder == null)
    {
      highlightBorder =
        new javax.swing.plaf.BorderUIResource.LineBorderUIResource(
            ColorAndFontConstants.pressedForeground, 1);
    }
    focusBorder = BorderFactory.createCompoundBorder(
        highlightBorder, buttonBorder);
  }

  private static final Color defaultBackground =
    ColorAndFontConstants.background;

  private static final Color defaultForeground =
    ColorAndFontConstants.foreground;

  private static final Color mouseOverBackground =
    ColorAndFontConstants.mouseOverBackground;

  private static final Color mouseOverForeground =
    ColorAndFontConstants.mouseOverForeground;

  private static final Color pressedBackground =
    ColorAndFontConstants.pressedBackground;

  private static final Color pressedForeground =
    ColorAndFontConstants.pressedForeground;

  private static final Font actionFont = ColorAndFontConstants.defaultFont;


  /**
   * Creates a button associated with the provided action.
   * @param action the action.
   */
  public ActionButton(Action action) {
    super();
    this.action = action;
    setText(action.getName().toString());
    setIconTextGap(0);
    setHorizontalTextPosition(SwingConstants.TRAILING);
    setHorizontalAlignment(SwingConstants.LEADING);
    setOpaque(true);

    setBorder(buttonBorder);
    setFont(actionFont);

    setFocusPainted(true);
    setContentAreaFilled(false);
    setToolTipText(action.getName().toString());
    setRolloverEnabled(false);

    Dimension d1 = getPreferredSize();
    setBorder(focusBorder);
    Dimension d2 = getPreferredSize();
    setPreferredSize(new Dimension(Math.max(d1.width,d2.width),
        Math.max(d1.height, d2.height)));
    setBorder(buttonBorder);
  }

  /**
   * Callback when an action has been performed.
   *
   * @param ev
   *          the action event
   */
  public void actionPerformed(ActionEvent ev)
  {
    isBeingPressed = true;
    final boolean[] hadMouseOver = {hasMouseOver};
    hasMouseOver = true;
    repaint();
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        isBeingPressed = false;
        hasMouseOver = hadMouseOver[0];
        repaint();
      }
    });
  }

  /**
   * Callback when a mouse button has been pressed.
   *
   * @param e
   *          the mouse event
   */
  public void mousePressed(MouseEvent e)
  {
    isBeingPressed = true;
  }

  /**
   * Callback when a mouse button has been released.
   *
   * @param e
   *          the mouse event
   */
  public void mouseReleased(MouseEvent e)
  {
    isBeingPressed = false;
  }

  /**
   * Callback when mouse exited a component.
   *
   * @param e
   *          the mouse event
   */
  public void mouseExited(MouseEvent e)
  {
    hasMouseOver = false;
    repaint();
  }

  /**
   * Callback when mouse entered a component.
   *
   * @param e
   *          the mouse event
   */
  public void mouseEntered(MouseEvent e)
  {
    hasMouseOver = true;
    repaint();
  }

  @Override
  public void updateUI() {
      super.updateUI();
      // some look and feels replace our border, so take it back
      setBorder(buttonBorder);
  }

  @Override
  protected void paintComponent(Graphics g) {
    setBorder(hasFocus() ? focusBorder : buttonBorder);
    if (isBeingPressed && hasMouseOver)
    {
      setColors(g, pressedBackground, pressedForeground);
    }
    else if (hasMouseOver)
    {
      setColors(g, mouseOverBackground, mouseOverForeground);
    }
    else {
      setColors(g, defaultBackground, defaultForeground);
    }
    super.paintComponent(g);
  }

  private void setColors(Graphics g, Color backgroundColor, Color foregroundColor)
  {
    setBackground(backgroundColor);
    g.setColor(backgroundColor);
    Dimension size = getSize();
    g.fillRect(0, 0, size.width, size.height);
    setForeground(foregroundColor);
  }

  /**
   * Returns the action associated with this button.
   * @return the action associated with this button.
   */
  public Action getActionObject() {
      return action;
  }
}
