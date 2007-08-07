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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import java.util.HashMap;
import java.util.LinkedHashSet;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opends.quicksetup.UserData;
import org.opends.quicksetup.WizardStep;

/**
 * This class displays the different steps of the wizard.  It appears on the
 * left of the dialog.
 *
 * The current step is highlighted using a different label style and an icon.
 * The current displayed step can be changed calling the method setCurrentStep.
 *
 */
public class StepsPanel extends QuickSetupPanel
{
  private static final long serialVersionUID = -2003945907121690657L;

  HashMap<WizardStep, JLabel> hmLabels = new HashMap<WizardStep, JLabel>();

  HashMap<WizardStep, JLabel> hmIcons = new HashMap<WizardStep, JLabel>();

  HashMap<WizardStep, JPanel> hmSubPanels = new HashMap<WizardStep, JPanel>();

  /**
   * Creates a StepsPanel.
   * @param app Application whose steps this class represents
   */
  public StepsPanel(GuiApplication app)
  {
    super(app);
    createLayout(app);
  }

  /**
   * Updates the layout of the panel so that it corresponds to the Step passed
   * as parameter.
   *
   * @param step the step in the wizard.
   * @param userData the data provided by the user.
   */
  public void setDisplayedStep(WizardStep step, UserData userData)
  {
    for (WizardStep s : getApplication().getWizardSteps())
    {
      if (s.equals(step))
      {
        getIcon(s).setVisible(true);
        UIFactory.setTextStyle(getLabel(s), UIFactory.TextStyle.CURRENT_STEP);
      }
      else
      {
        if (getIcon(s) != null)
        {
          getIcon(s).setVisible(false);
        }
        if (getLabel(s) != null)
        {
          UIFactory.setTextStyle(getLabel(s),
              UIFactory.TextStyle.NOT_CURRENT_STEP);
        }
      }
      setStepVisible(s, getApplication().isVisible(s, userData));
    }
  }

  /**
   * Updates the visiblitiy of the steps depending on the current contents
   * of the panels (uses the QuickSetup to know what is displayed in the
   * panels).
   *
   * @param qs the QuickSetup object.
   */
  public void updateStepVisibility(QuickSetup qs)
  {
    for (WizardStep s : getApplication().getWizardSteps())
    {
      setStepVisible(s, getApplication().isVisible(s, qs));
    }
  }

  /**
   * Creates the layout of the panel.
   * @param app Application whose steps this class represents
   */
  private void createLayout(GuiApplication app)
  {
    setLayout(new GridBagLayout());

    JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;

    HashMap<WizardStep, String> hmText = new HashMap<WizardStep, String>();
    LinkedHashSet<WizardStep> orderedSteps = app.getOrderedSteps();
    boolean first = true;
    for (WizardStep s : orderedSteps)
    {
      hmText.put(s, app.getI18n().getMsg(s.getMessageKey()));

      JPanel subPanel = new JPanel(new GridBagLayout());
      subPanel.setOpaque(false);
      if (!first)
      {
        gbc.insets.top = UIFactory.TOP_INSET_STEP;
      }

      GridBagConstraints gbcAux = new GridBagConstraints();
      gbcAux.gridwidth = GridBagConstraints.REMAINDER;
      gbcAux.fill = GridBagConstraints.HORIZONTAL;
      JPanel auxPanel = new JPanel(new GridBagLayout());
      auxPanel.setOpaque(false);
      JLabel iconLabel =
          UIFactory.makeJLabel(UIFactory.IconType.CURRENT_STEP, null,
              UIFactory.TextStyle.NO_STYLE);
      gbcAux.insets.left = 0;

      auxPanel.add(iconLabel, gbcAux);
      int width = (int) iconLabel.getPreferredSize().getWidth();
      if (getApplication().isSubStep(s))
      {
        width += UIFactory.LEFT_INSET_SUBSTEP;
      }
      gbcAux.insets.left = 0;
      auxPanel.add(Box.createHorizontalStrut(width), gbcAux);

      hmIcons.put(s, iconLabel);

      gbc.gridwidth = 3;
      gbc.weightx = 0.0;
      subPanel.add(auxPanel, gbc);

      JLabel stepLabel =
          UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, hmText.get(s),
              UIFactory.TextStyle.CURRENT_STEP);
      hmLabels.put(s, stepLabel);
      gbc.insets.left = UIFactory.LEFT_INSET_STEP;
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      subPanel.add(stepLabel, gbc);
      gbc.insets = UIFactory.getEmptyInsets();
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1.0;
      subPanel.add(Box.createHorizontalGlue(), gbc);

      mainPanel.add(subPanel, gbc);
      hmSubPanels.put(s, subPanel);

      stepLabel.setLabelFor(this);
      iconLabel.setLabelFor(stepLabel);

      first = false;
    }

    gbc.insets.left = 0;
    gbc.insets.top = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    add(mainPanel, gbc);
    int mainWidth = (int) mainPanel.getPreferredSize().getWidth();

    // We are creating all the labels with the style
    // UIFactory.LabelStyle.CURRENT_STEP which is the one
    // that takes more space. But once we display the dialog only one
    // of the labels will have that style and the other will have
    // UIFactory.LabelStyle.NOT_CURRENT_STEP. Adding the strut guarantees
    // that the width of the panel will always be enough to display the
    // longest label using UIFactory.LabelStyle.CURRENT_STEP.
    add(Box.createHorizontalStrut(mainWidth), gbc);

    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.weighty = 1.0;
    add(Box.createVerticalGlue(), gbc);
  }

  /**
   * Returns the label associated with the given step.
   * @param step the step for which we want to retrieve the JLabel.
   * @return the label associated with the given step.
   */
  private JLabel getLabel(WizardStep step)
  {
    return hmLabels.get(step);
  }

  /**
   * Returns the icon associated with the given step.
   * @param step the step for which we want to retrieve the Icon.
   * @return the icon associated with the given step.
   */
  private JLabel getIcon(WizardStep step)
  {
    return hmIcons.get(step);
  }

  /**
   * Returns the sub-panel associated with the given step.
   * @param step the step for which we want to retrieve the sub-panel.
   * @return the sub-panel associated with the given step.
   */
  private JPanel getSubPanel(WizardStep step)
  {
    return hmSubPanels.get(step);
  }

  private void setStepVisible(WizardStep step, boolean visible)
  {
    JPanel subPanel = getSubPanel(step);
    // Check done to minimize possible flickering.
    if (visible != subPanel.isVisible())
    {
      subPanel.setVisible(visible);
    }
  }
}
