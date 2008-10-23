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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.ToolTipManager;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * A panel containing a label an a help icon.  A customized tool tip is used,
 * the tool tip is also displayed when the user clicks on the help icon.
 *
 */
public class LabelWithHelpIcon extends JPanel
{
  private static final long serialVersionUID = 4502977901538910797L;
  /**
   * The label with the text.
   */
  protected JLabel label = Utilities.createDefaultLabel();
  /**
   * The label with the icon.
   */
  protected JLabel iconLabel = new JLabel(icon);
  private static final ImageIcon icon =
    Utilities.createImageIcon("org/opends/quicksetup/images/help_small.gif");
  private static final ToolTipManager ttipManager =
    ToolTipManager.sharedInstance();

  private Popup tipWindow;
  private boolean isVisible;

  /**
   * The left inset of the help icon.
   */
  protected final int INSET_WITH_ICON= 3;

  /**
   * The constructor of this panel.
   * @param text the text of the panel.
   * @param tooltipIcon the tool tip of the help icon.
   */
  public LabelWithHelpIcon(Message text, Message tooltipIcon)
  {
    super(new GridBagLayout());
    setOpaque(false);
    label.setText(text.toString());
    label.setForeground(ColorAndFontConstants.foreground);
    if (tooltipIcon != null)
    {
      iconLabel.setToolTipText(tooltipIcon.toString());
    }
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.WEST;
    add(label, gbc);
    gbc.gridx ++;
    gbc.insets.left = INSET_WITH_ICON;
    add(iconLabel, gbc);
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(Box.createHorizontalGlue(), gbc);

    ttipManager.unregisterComponent(iconLabel);

    iconLabel.addMouseListener(new MouseAdapter()
    {
      /**
       * {@inheritDoc}
       */
      public void mouseExited(MouseEvent event)
      {
        hideToolTip(event);
      }

      /**
       * {@inheritDoc}
       */
      public void mousePressed(MouseEvent event)
      {
        if (isVisible)
        {
          hideToolTip(event);
        }
        else
        {
          displayToolTip(event);
        }
      }
    });
  }

  /**
   * Sets the text on the label.
   * @param text the text to be displayed.
   */
  public void setText(String text)
  {
    label.setText(text);
  }

  /**
   * Returns the text displayed on the panel.
   * @return the text displayed on the panel.
   */
  public String getText()
  {
    return label.getText();
  }

  /**
   * Sets the font to be used in this panel.
   * @param font the font.
   */
  public void setFont(Font font)
  {
    // This is call by the constructor of JPanel.
    if (label != null)
    {
      label.setFont(font);
    }
  }

  /**
   * Sets the tool tip to be used in the help icon.
   * @param tooltip the tool tip text.
   */
  public void setHelpTooltip(String tooltip)
  {
    iconLabel.setToolTipText(tooltip);
  }

  /**
   * Returns the tool tip to be used in the help icon.
   * @return the tool tip to be used in the help icon.
   */
  public String getHelpTooltip()
  {
    return iconLabel.getToolTipText();
  }

  /**
   * Sets whether the help icon is visible or not.
   * @param visible whether the help icon is visible or not.
   */
  public void setHelpIconVisible(boolean visible)
  {
    if (visible)
    {
      if (iconLabel.getIcon() != icon)
      {
        iconLabel.setIcon(icon);
      }
    }
    else if (iconLabel.getIcon() != null)
    {
      iconLabel.setIcon(null);
    }
  }

  /**
   * Sets the foreground color for the text in this panel.
   * @param color the foreground color for the text in this panel.
   */
  public void setForeground(Color color)
  {
    super.setForeground(color);
    if (label != null)
    {
      // This is called in the constructor of the object.
      label.setForeground(color);
    }
  }

  /**
   * Displays a tooltip depending on the MouseEvent received.
   * @param event the mouse event.
   */
  private void displayToolTip(MouseEvent event)
  {
    JComponent component = (JComponent)event.getSource();
    String toolTipText = component.getToolTipText();
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
    }
    isVisible = true;
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
    }
    isVisible = false;
  }
}
