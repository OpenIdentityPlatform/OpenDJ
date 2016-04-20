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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.quicksetup.ui;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JPanel;

/**
 * This class is the panel that is displayed in the QuickSetupDialog.  It
 * contains 3 panels that are passed in the constructor: the steps panel,
 * the buttons panel and the current step panel (the main panel of the three).
 * <p>
 * The only remarkable thing of this class is that is responsible for
 * implementing the background.  The three subpanels are transparent and
 * this class sets a background (with the OpenDJ logo) and uses some basic
 * transparency effects.
 */
public class FramePanel extends JPanel
{
  private static final long serialVersionUID = 7733658951410876078L;

  private Icon backgroundIcon;
  private Component stepsPanel;
  private Component buttonsPanel;
  private int buttonsPanelVerticalInsets;
  private int stepsPanelHorizontalInsets;

  /**
   * The constructor of the FramePanel.
   * @param stepsPanel the steps panel that on the top-left side of the
   * QuickSetupDialog.
   * @param currentStepPanel the current step panel (the panel that displays
   * what is associated to the current step).  Is the panel that contains all
   * the input fields and is located on the top-right side of the
   * QuickSetupDialog.
   * @param buttonsPanel the buttons panel that appears on the bottom of the
   * QuickSetupDialog.
   */
  public FramePanel(Component stepsPanel, Component currentStepPanel,
      Component buttonsPanel)
  {
    super(new GridBagLayout());
    setBackground(UIFactory.DEFAULT_BACKGROUND);
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets = UIFactory.getStepsPanelInsets();
    add(stepsPanel, gbc);

    stepsPanelHorizontalInsets = gbc.insets.left + gbc.insets.right;

    JPanel currentPanelContainer = new JPanel(new GridBagLayout());
    currentPanelContainer.setBorder(UIFactory.CURRENT_STEP_PANEL_BORDER);
    currentPanelContainer
        .setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    currentPanelContainer.setOpaque(false);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    currentPanelContainer.add(currentStepPanel, gbc);
    gbc.insets = UIFactory.getEmptyInsets();
    add(currentPanelContainer, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    add(Box.createHorizontalGlue(), gbc);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    add(buttonsPanel, gbc);

    buttonsPanelVerticalInsets = gbc.insets.top + gbc.insets.bottom;

    backgroundIcon =
      UIFactory.getImageIcon(UIFactory.IconType.BACKGROUND);

    int backGroundIconWidth = 0;
    int backGroundIconHeight = 0;
    if (backgroundIcon != null)
    {
      backGroundIconWidth = backgroundIcon.getIconWidth();
      backGroundIconHeight = backgroundIcon.getIconHeight();
    }

    this.buttonsPanel = buttonsPanel;
    this.stepsPanel = stepsPanel;
    int width =
        Math.max((int) getPreferredSize().getWidth(), backGroundIconWidth
            + UIFactory.LEFT_INSET_BACKGROUND
            + UIFactory.RIGHT_INSET_BACKGROUND);
    int height =
        Math.max((int) getPreferredSize().getHeight(), backGroundIconHeight
            + UIFactory.TOP_INSET_BACKGROUND
            + UIFactory.BOTTOM_INSET_BACKGROUND);
    setPreferredSize(new Dimension(width, height));
  }

  /**
   * {@inheritDoc}
   *
   * This method has been overwritten to be able to have a transparency effect
   * with the OpenDJ logo background.
   */
  @Override
  protected void paintComponent(Graphics g)
  {
      // paint background
      g.setColor(UIFactory.DEFAULT_BACKGROUND);
      int width = getWidth();
      int height = getHeight();
      int buttonsTotalHeight = buttonsPanel.getHeight()
      + buttonsPanelVerticalInsets;
      int stepsPanelTotalWidth = stepsPanel.getWidth()
      + stepsPanelHorizontalInsets;

      g.fillRect(0, 0, stepsPanelTotalWidth, height);
      g.fillRect(stepsPanelTotalWidth, height - buttonsTotalHeight, width,
              height);
      g.setColor(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
      g.fillRect(stepsPanelTotalWidth, 0, width, height - buttonsTotalHeight);

      if (backgroundIcon != null)
      {
          // Draw the icon over and over, right aligned.
          // Copy the Graphics object, which is actually
          // a Graphics2D object. Cast it so we can
          // set alpha composite.
          Graphics2D g2d = (Graphics2D) g.create();

          g2d.setComposite(AlphaComposite.getInstance(
                  AlphaComposite.SRC_OVER, 0.1f));

          backgroundIcon.paintIcon(this, g2d,
                  UIFactory.LEFT_INSET_BACKGROUND,
                  UIFactory.TOP_INSET_BACKGROUND);

          g2d.dispose(); //clean up
      }
  }
}
