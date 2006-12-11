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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;

/**
 * This class is a panel that contains an error message and a quit button.
 * It is used for instance when we try to install Open DS but it is already
 * installed.
 *
 */
public class QuickSetupErrorPanel extends QuickSetupPanel
{
  private static final long serialVersionUID = 1765037717593522233L;

  private HashSet<ButtonActionListener> buttonListeners =
      new HashSet<ButtonActionListener>();

  private JButton quitButton;

  /**
   * Constructor of the QuickSetupErrorPanel.
   *
   * @param htmlMsg the error message to be displayed.
   */
  public QuickSetupErrorPanel(String htmlMsg)
  {
    JPanel p1 = new JPanel(new GridBagLayout());
    p1.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    p1.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    p1.add(UIFactory.makeJLabel(UIFactory.IconType.WARNING_LARGE, null,
        UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets.left = 0;
    JTextComponent tf =
        UIFactory.makeHtmlPane(htmlMsg, UIFactory.INSTRUCTIONS_FONT);
    tf.setOpaque(false);
    p1.add(tf, gbc);

    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    p1.add(Box.createVerticalGlue(), gbc);

    JPanel p2 = new JPanel(new GridBagLayout());
    p2.setOpaque(false);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    p2.add(Box.createHorizontalGlue(), gbc);
    quitButton =
        UIFactory.makeJButton(getMsg("quit-button-label"),
            getMsg("quit-button-install-tooltip"));

    final ButtonName fButtonName = ButtonName.QUIT;

    ActionListener actionListener = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        ButtonEvent be = new ButtonEvent(ev.getSource(), fButtonName);
        for (ButtonActionListener li : buttonListeners)
        {
          li.buttonActionPerformed(be);
        }
      }
    };
    quitButton.addActionListener(actionListener);

    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    p2.add(quitButton, gbc);

    setLayout(new GridBagLayout());
    setBackground(UIFactory.DEFAULT_BACKGROUND);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    add(p1, gbc);
    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    add(p2, gbc);
  }

  /**
   * Adds a button listener.  All the button listeners will be notified when
   * the buttons are clicked (by the user or programatically).
   * @param l the ButtonActionListener to be added.
   */
  public void addButtonActionListener(ButtonActionListener l)
  {
    buttonListeners.add(l);
  }

  /**
   * Removes a button listener.
   * @param l the ButtonActionListener to be removed.
   */
  public void removeButtonActionListener(ButtonActionListener l)
  {
    buttonListeners.remove(l);
  }

  /**
   * Returns the quit button.
   * @return the quit button.
   */
  public JButton getQuitButton()
  {
    return quitButton;
  }
}
