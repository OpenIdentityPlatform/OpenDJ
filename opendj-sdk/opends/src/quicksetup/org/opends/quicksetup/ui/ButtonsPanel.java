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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.WizardStep;
import org.opends.quicksetup.upgrader.Upgrader;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.uninstaller.Uninstaller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;

/**
 * This class contains the buttons in the bottom of the Install/Uninstall
 * dialog.  There is only one of this instances for the QuickSetupDialog.
 * The layout is updated calling setCurrentStep method.
 *
 */
public class ButtonsPanel extends QuickSetupPanel
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
   * @param application Application running in QuickSetup
   *
   */
  public ButtonsPanel(GuiApplication application)
  {
    super(application);
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
  public void updateButtons(WizardStep step)
  {
    GuiApplication application = getApplication();
    previousButton.setVisible(application.canGoBack(step));
    if (application.canFinish(step)) {
      finishButton.setVisible(true);
      nextButton.setVisible(false);
    } else {
      finishButton.setVisible(false);
      nextButton.setVisible(application.canGoForward(step));
    }

    // The quit button appears on all the panels leading up
    // to the progress panel
    quitButton.setVisible(!step.isProgressStep());

    // The close button is only used on the progress panel and
    // is only enabled once progress has finished or cancelled.
    closeButton.setVisible(step.isProgressStep());
    closeButton.setEnabled(application.getCurrentProgressStep().isLast());
  }

  /**
   * Returns the button corresponding to the buttonName.
   * @param buttonName the ButtonName for which we want to get the button.
   * @return the button corresponding to the buttonName.
   */
  public JButton getButton(ButtonName buttonName)
  {
    JButton b = null;
    if (buttonName != null) {
      switch (buttonName) {
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

    String tooltip;

    GuiApplication application = getApplication();
    tooltip = application.getQuitButtonToolTipKey();
    quitButton =
        createButton("quit-button-label", tooltip, ButtonName.QUIT);



    tooltip = application.getCloseButtonToolTipKey();
    closeButton = createButton("close-button-label", tooltip, ButtonName.CLOSE);

    String label = application.getFinishButtonLabelKey();
    tooltip = application.getFinishButtonToolTipKey();
    finishButton = createButton(label, tooltip, ButtonName.FINISH);

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

    gbc.gridwidth = 5;
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

    // TODO: remove this hack
    if (getApplication() instanceof Installer ||
            getApplication() instanceof Upgrader) {
      nextFinishPanel.add(finishButton, gbcAux);
    }
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

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;

    // TODO: remove this hack
    if (getApplication() instanceof Uninstaller) {
      gbc.insets.right = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
      add(finishButton, gbc);
      gbc.insets.right = 0;
    }

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = 0;
    JPanel quitClosePanel = new JPanel(new GridBagLayout());
    // Set as opaque to inherit the background color of ButtonsPanel
    quitClosePanel.setOpaque(false);
    quitClosePanel.add(
        Box.createHorizontalStrut(UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS),
        gbcAux);
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
