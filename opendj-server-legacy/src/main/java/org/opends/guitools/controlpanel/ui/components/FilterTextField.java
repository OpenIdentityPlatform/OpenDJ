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

package org.opends.guitools.controlpanel.ui.components;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.ui.UIFactory;

/**
 * A text field with an icon with 'X' shape on the right.  When the user clicks
 * on that icon, the contents of the text field are cleared.
 */
public class FilterTextField extends JTextField
{
  private static final long serialVersionUID = -2083433734204435457L;
  private boolean displayClearIcon;
  private ImageIcon clearIcon = Utilities.createImageIcon(IconPool.IMAGE_PATH+
      "/clear-filter.png");
  private ImageIcon clearIconPressed =
    Utilities.createImageIcon(IconPool.IMAGE_PATH+
  "/clear-filter-down.png");
  private ImageIcon refreshIcon =
    UIFactory.getImageIcon(UIFactory.IconType.WAIT_TINY);

  private boolean mousePressed;
  private boolean displayRefreshIcon;

  /** The time during which the refresh icon is displayed by default. */
  public static long DEFAULT_REFRESH_ICON_TIME = 750;

  private LinkedHashSet<ActionListener> listeners = new LinkedHashSet<>();
  private boolean constructorBorderSet;

  /** Default constructor. */
  public FilterTextField()
  {
    super(15);
    Border border = getBorder();
    if (border != null)
    {
      setBorder(BorderFactory.createCompoundBorder(border, new IconBorder()));
    }
    else
    {
      setBorder(new IconBorder());
    }
    constructorBorderSet = true;
    getDocument().addDocumentListener(new DocumentListener()
    {
      @Override
      public void changedUpdate(DocumentEvent e)
      {
        insertUpdate(e);
      }

      @Override
      public void insertUpdate(DocumentEvent e)
      {
        boolean displayIcon = getText().length() > 0;
        if (FilterTextField.this.displayClearIcon != displayIcon)
        {
          FilterTextField.this.displayClearIcon = displayIcon;
          repaint();
        }
      }
      @Override
      public void removeUpdate(DocumentEvent e)
      {
        insertUpdate(e);
      }
    });

    addMouseListener(new MouseAdapter()
    {
      @Override
      public void mousePressed(MouseEvent ev)
      {
        boolean p = getClearIconRectangle().contains(ev.getPoint());
        if (p != mousePressed)
        {
          mousePressed = p;
          repaint();
        }
      }

      @Override
      public void mouseReleased(MouseEvent ev)
      {
        if (mousePressed && getClearIconRectangle().contains(ev.getPoint()))
        {
          setText("");
          notifyListeners();
        }
        mousePressed = false;
      }
    });
  }

  @Override
  public void addActionListener(ActionListener listener)
  {
    listeners.add(listener);
  }

  @Override
  public void removeActionListener(ActionListener listener)
  {
    listeners.remove(listener);
  }

  @Override
  public void setBorder(Border border)
  {
    if (constructorBorderSet && border != null)
    {
      border = BorderFactory.createCompoundBorder(border, new IconBorder());
    }
    super.setBorder(border);
  }

  /**
   * Displays a refresh icon on the text field (this is used for instance in
   * the browsers that use this text field to specify a filter: the refresh
   * icon is displayed to show that the filter is being displayed).
   * @param display whether to display the refresh icon or not.
   */
  public void displayRefreshIcon(boolean display)
  {
    if (display != displayRefreshIcon)
    {
      displayRefreshIcon = display;
      repaint();
    }
  }

  /**
   * Returns <CODE>true</CODE> if the refresh icon is displayed and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the refresh icon is displayed and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isRefreshIconDisplayed()
  {
    return displayRefreshIcon;
  }

  /**
   * Displays a refresh icon on the text field (this is used for instance in
   * the browsers that use this text field to specify a filter: the refresh
   * icon is displayed to show that the filter is being displayed).
   * @param time the time (in milliseconds) that the icon will be displayed.
   */
  public void displayRefreshIcon(final long time)
  {
    displayRefreshIcon = true;
    repaint();
    Thread t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          Thread.sleep(time);
        }
        catch (Throwable t)
        {
        }
        finally
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              displayRefreshIcon = false;
              repaint();
            }
          });
        }
      }
    });
    t.start();
  }

  private static int id = 1;
  private void notifyListeners()
  {
    ActionEvent ev = new ActionEvent(this, id,
        "CLEAR_FILTER");
    id ++;
    for (ActionListener listener : listeners)
    {
      listener.actionPerformed(ev);
    }
  }

  private Rectangle getClearIconRectangle()
  {
    ImageIcon icon = getClearIcon();
    int margin = getMargin(this, icon);
    return new Rectangle(getWidth() - margin - icon.getIconWidth(),
        margin, icon.getIconWidth(), icon.getIconHeight());
  }

  /** The border of this filter text field. */
  private class IconBorder implements Border
  {
    @Override
    public Insets getBorderInsets(Component c)
    {
      ImageIcon icon = getClearIcon();
      int rightInsets = 0;
      if (displayClearIcon)
      {
        rightInsets += icon.getIconWidth() + getMargin(c, icon);
      }
      if (displayRefreshIcon)
      {
        rightInsets += refreshIcon.getIconWidth() + getMargin(c, refreshIcon);
      }
      return new Insets(0, 0, 0, rightInsets);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y,
        int width, int height)
    {
      if (displayClearIcon || displayRefreshIcon)
      {
        Graphics2D g2d = (Graphics2D) g.create();
        int leftSpaceOfClearIcon = 0;
        if (displayClearIcon)
        {
          ImageIcon icon = getClearIcon();
          int margin = (height - icon.getIconHeight()) / 2;
          icon.paintIcon(c,
              g2d, x + width - margin - icon.getIconWidth(),
              y + margin);
          leftSpaceOfClearIcon = margin + icon.getIconWidth();
        }
        if (displayRefreshIcon)
        {
          int margin = (height - refreshIcon.getIconHeight()) / 2;
          refreshIcon.paintIcon(c, g2d, x + width - margin -
              refreshIcon.getIconWidth() - leftSpaceOfClearIcon, y + margin);
        }
        g2d.dispose(); //clean up
      }
    }

    @Override
    public boolean isBorderOpaque()
    {
      return false;
    }
  }
  private int getMargin(Component c, ImageIcon icon)
  {
    return (c.getHeight() - icon.getIconHeight()) / 2;
  }

  private ImageIcon getClearIcon()
  {
    return mousePressed ? clearIconPressed : clearIcon;
  }
}
