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

import org.opends.guitools.controlpanel.datamodel.Action;
import org.opends.guitools.controlpanel.datamodel.Category;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.messages.Message;

/**
 * A basic extension of a button that changes its rendering so that the looks
 * are more similar to a row in a list.  It is used in the actions on the left
 * of the main Control Center dialog (in actions like 'Manage Entries...',
 * 'Import from LDIF...' etc.
 *
 */
public class ActionButton extends JButton
{
  private static final long serialVersionUID = -1898192406268037714L;

  private Action action;
  private boolean isBeingPressed;
  private boolean hasMouseOver;
  private static Border buttonBorder;
  private static Border focusBorder;
  static
  {
    //Calculate border based on category settings
    Category cat = new Category();
    cat.setName(Message.EMPTY);
    CategoryButton b = new CategoryButton(cat);
    int n = b.getIconTextGap() + b.getIcon().getIconWidth() +
    b.getBorder().getBorderInsets(b).left;
    buttonBorder = new EmptyBorder(5, n, 5, 25);
    focusBorder = BorderFactory.createCompoundBorder(
        UIManager.getBorder("List.focusCellHighlightBorder"), buttonBorder);
  };

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

  static final Font actionFont = ColorAndFontConstants.defaultFont;


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
   * {@inheritDoc}
   */
  public void actionPerformed(ActionEvent ev)
  {
    isBeingPressed = true;
    final boolean[] hadMouseOver = {hasMouseOver};
    hasMouseOver = true;
    repaint();
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        isBeingPressed = false;
        hasMouseOver = hadMouseOver[0];
        repaint();
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public void mousePressed(MouseEvent e)
  {
    isBeingPressed = true;
  }

  /**
   * {@inheritDoc}
   */
  public void mouseReleased(MouseEvent e)
  {
    isBeingPressed = false;
  }

  /**
   * {@inheritDoc}
   */
  public void mouseExited(MouseEvent e)
  {
    hasMouseOver = false;
    repaint();
  }

  /**
   * {@inheritDoc}
   */
  public void mouseEntered(MouseEvent e)
  {
    hasMouseOver = true;
    repaint();
  }

  /**
   * {@inheritDoc}
   */
  public void updateUI() {
      super.updateUI();
      // some look and feels replace our border, so take it back
      setBorder(buttonBorder);
  }

  /**
   * {@inheritDoc}
   */
  protected void paintComponent(Graphics g) {
    setBorder(hasFocus() ? focusBorder : buttonBorder);
    if (isBeingPressed && hasMouseOver)
    {
      setBackground(pressedBackground);
      g.setColor(pressedBackground);
      Dimension size = getSize();
      g.fillRect(0, 0, size.width, size.height);
      setForeground(pressedForeground);
    }
    else if (hasMouseOver)
    {
      setBackground(mouseOverBackground);
      g.setColor(mouseOverBackground);
      Dimension size = getSize();
      g.fillRect(0, 0, size.width, size.height);
      setForeground(mouseOverForeground);
    }
    else {
      setBackground(defaultBackground);
      g.setColor(defaultBackground);
      Dimension size = getSize();
      g.fillRect(0, 0, size.width, size.height);
      setForeground(defaultForeground);
    }
    super.paintComponent(g);
  }

  /**
   * Returns the action associated with this button.
   * @return the action associated with this button.
   */
  public Action getActionObject() {
      return action;
  }
}
