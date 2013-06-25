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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

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

    Utilities.addClickTooltipListener(iconLabel);

    updateAccessibleContext();
  }

  /**
   * Set the component this is labeling. Can be {@code null} if this does not
   * label a {@code Component}.
   * @param comp the {@code Component} to be labeled.
   */
  public void setLabelFor(Component comp)
  {
    label.setLabelFor(comp);
  }

  /**
   * Sets the text on the label.
   * @param text the text to be displayed.
   */
  public void setText(String text)
  {
    label.setText(text);
    updateAccessibleContext();
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
    updateAccessibleContext();
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
   * {@inheritDoc}
   */
  public String getToolTipText(MouseEvent ev)
  {
    int x = ev.getPoint().x;
    boolean display = x > label.getPreferredSize().width - 10;

    if (display)
    {
      return getHelpTooltip();
    }
    else
    {
      return null;
    }
  }

  private void updateAccessibleContext()
  {
    StringBuilder sb = new StringBuilder();
    String s = label.getText();
    if (s != null)
    {
      sb.append(s);
    }
    if (iconLabel.getIcon() != null)
    {
      String toolTip = iconLabel.getToolTipText();
      toolTip = Utilities.stripHtmlToSingleLine(toolTip);
      if (toolTip != null)
      {
        sb.append(" - "+toolTip);
      }
    }
    getAccessibleContext().setAccessibleName(sb.toString());
  }
}
