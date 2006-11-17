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

import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;

/**
 * This class contains the buttons in the bottom of the Install/Uninstall
 * dialog.  There is only one of this instances for the QuickSetupDialog.
 * The layout is updated calling setCurrentStep method.
 *
 */
class ButtonsPanel extends QuickSetupPanel
{
  private static final long serialVersionUID = -8460400337486357976L;

  private HashSet<ButtonActionListener> buttonListeners =
      new HashSet<ButtonActionListener>();

  private JButton nextButton;

  private JButton previousButton;

  private JButton quitButton;

  private JButton closeButton;

  private JButton finishButton;

  /**
   * Default constructor.
   *
   */
  public ButtonsPanel()
  {
    createButtons();
    layoutButtons();
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
   * Updates the layout of the panel so that it corresponds to the Step passed
   * as parameter.
   *
   * @param step the step in the wizard.
   */
  public void setDisplayedStep(Step step)
  {
    switch (step)
    {
    case WELCOME:

      previousButton.setVisible(false);
      nextButton.setVisible(true);
      finishButton.setVisible(false);
      quitButton.setVisible(true);
      closeButton.setVisible(false);

      break;

    case REVIEW:

      previousButton.setVisible(true);
      nextButton.setVisible(false);
      finishButton.setVisible(true);
      quitButton.setVisible(true);
      closeButton.setVisible(false);

      break;

    case PROGRESS:

      // TO COMPLETE: if there is an error we might want to change
      // this
      // like for instance coming back
      previousButton.setVisible(false);
      nextButton.setVisible(false);
      finishButton.setVisible(false);
      quitButton.setVisible(false);
      closeButton.setVisible(true);

      break;

    case CONFIRM_UNINSTALL:

      previousButton.setVisible(false);
      nextButton.setVisible(false);
      finishButton.setVisible(true);
      quitButton.setVisible(true);
      closeButton.setVisible(false);

      break;

    default:

      previousButton.setVisible(true);
      nextButton.setVisible(true);
      finishButton.setVisible(false);
      quitButton.setVisible(true);
      closeButton.setVisible(false);
    }
  }

  /**
   * Returns the button corresponding to the buttonName.
   * @param buttonName the ButtonName for which we want to get the button.
   * @return the button corresponding to the buttonName.
   */
  public JButton getButton(ButtonName buttonName)
  {
    JButton b = null;
    switch (buttonName)
    {
    case NEXT:
      b = nextButton;
      break;

    case PREVIOUS:
      b = previousButton;
      break;

    case QUIT:
      b = quitButton;
      break;

    case CLOSE:
      b = closeButton;
      break;

    case FINISH:
      b = finishButton;
      break;

    default:
      throw new IllegalArgumentException("Unknown button name: " +
          buttonName);
    }

    return b;
  }

  /*
   * Create the buttons.
   */
  private void createButtons()
  {
    nextButton =
        createButton("next-button-label", "next-button-tooltip",
            ButtonName.NEXT);

    previousButton =
        createButton("previous-button-label", "previous-button-tooltip",
            ButtonName.PREVIOUS);

    quitButton =
        createButton("quit-button-label", "quit-button-tooltip",
            ButtonName.QUIT);

    closeButton =
        createButton("close-button-label", "close-button-tooltip",
            ButtonName.CLOSE);

    finishButton =
        createButton("finish-button-label", "finish-button-tooltip",
            ButtonName.FINISH);
  }

  /**
   * Do the layout of the panel.
   *
   */
  private void layoutButtons()
  {
    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    GridBagConstraints gbcAux = new GridBagConstraints();
    gbcAux.gridwidth = GridBagConstraints.REMAINDER;
    gbcAux.fill = GridBagConstraints.HORIZONTAL;
    JPanel previousPanel = new JPanel(new GridBagLayout());
    // Set as opaque to inherit the background color of ButtonsPanel
    previousPanel.setOpaque(false);
    previousPanel.add(previousButton, gbcAux);
    int width = (int) previousButton.getPreferredSize().getWidth();
    previousPanel.add(Box.createHorizontalStrut(width), gbcAux);

    gbc.gridwidth = 4;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.insets.bottom = 0;
    gbc.insets.right = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    add(previousPanel, gbc);
    gbc.gridwidth--;

    JPanel nextFinishPanel = new JPanel(new GridBagLayout());
    // Set as opaque to inherit the background color of ButtonsPanel
    nextFinishPanel.setOpaque(false);
    nextFinishPanel.add(nextButton, gbcAux);
    nextFinishPanel.add(finishButton, gbcAux);
    width =
        (int) Math.max(nextButton.getPreferredSize().getWidth(), finishButton
            .getPreferredSize().getWidth());
    nextFinishPanel.add(Box.createHorizontalStrut(width), gbcAux);
    add(nextFinishPanel, gbc);

    gbc.gridwidth--;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.right = 0;
    add(Box.createHorizontalGlue(), gbc);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    JPanel quitClosePanel = new JPanel(new GridBagLayout());
    // Set as opaque to inherit the background color of ButtonsPanel
    quitClosePanel.setOpaque(false);
    quitClosePanel.add(quitButton, gbcAux);
    quitClosePanel.add(closeButton, gbcAux);
    width =
        (int) Math.max(quitButton.getPreferredSize().getWidth(), closeButton
            .getPreferredSize().getWidth());
    quitClosePanel.add(Box.createHorizontalStrut(width), gbcAux);
    add(quitClosePanel, gbc);
  }

  /**
   * Create a button.
   * @param labelKey the key in the properties file for the label.
   * @param tooltipKey the key in the properties file for the tooltip.
   * @param buttonName the ButtonName.
   * @return a new button with the specified parameters.
   */
  private JButton createButton(String labelKey, String tooltipKey,
      ButtonName buttonName)
  {
    JButton b = UIFactory.makeJButton(getMsg(labelKey), getMsg(tooltipKey));

    final ButtonName fButtonName = buttonName;

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

    b.addActionListener(actionListener);

    return b;
  }
}
