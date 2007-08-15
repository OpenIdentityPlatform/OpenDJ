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

import static org.opends.messages.QuickSetupMessages.*;

import java.awt.*;

import java.util.HashMap;
import java.util.Set;

import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.*;

import javax.swing.*;

/**
 * This is the class that contains the panel on the right-top part of the
 * QuickSetupDialog).  It uses a CardLayout that contains all
 * the panels that are specific to each step (WelcomePanel, ReviewPanel, etc.).
 *
 * To specify which is the panel to be displayed the method setCurrentStep
 * method is called.
 *
 * There is only one instance of this class for a given QuickSetupDialog (and
 * there are only 1 instance of each of the panels that are contained in its
 * CardLayout).
 *
 */
public class CurrentStepPanel extends QuickSetupPanel
{
  private static final long serialVersionUID = 5474803491510999334L;

  private static final String LOADING_PANEL = "loading";

  private HashMap<WizardStep, QuickSetupStepPanel> hmPanels =
      new HashMap<WizardStep, QuickSetupStepPanel>();

  /**
   * The constructor of this class.
   * @param app Application used to create panels for populating the layout
   * @param qs QuickSetup acting as controller
   */
  public CurrentStepPanel(GuiApplication app, QuickSetup qs)
  {
    super(app);
    setQuickSetup(qs);
    createLayout(app);
  }

  /**
   * Returns the value corresponding to the provided FieldName.
   * @param fieldName the FieldName for which we want to obtain the value.
   * @return the value corresponding to the provided FieldName.
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;
    for (WizardStep s : hmPanels.keySet())
    {
      value = getPanel(s).getFieldValue(fieldName);
      if (value != null)
      {
        break;
      }
    }
    return value;
  }

  /**
   * Marks as invalid (or valid depending on the value of the invalid parameter)
   * a field corresponding to FieldName.  This basically implies udpating the
   * style of the JLabel associated with fieldName (the association is done
   * using the LabelFieldDescriptor class).
   * @param fieldName the FieldName to be marked as valid or invalid.
   * @param invalid whether to mark the field as valid or invalid.
   */
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    for (WizardStep s : hmPanels.keySet())
    {
      getPanel(s).displayFieldInvalid(fieldName, invalid);
    }
  }

  /**
   * Returns the panel we use to display the progress.  This method is used
   * to be able to retrieve the message formatter.
   * @return the panel we use to display the progress.
   */
  ProgressPanel getProgressPanel()
  {
    return (ProgressPanel)hmPanels.get(Step.PROGRESS);
  }

  /**
   * Create the layout of the panel.
   * @param app Application used to create panels for populating the layout
   */
  private void createLayout(GuiApplication app)
  {
    Set<? extends WizardStep> steps = app.getWizardSteps();
    if (steps != null) {
      for (WizardStep step : steps) {
        QuickSetupStepPanel panel = app.createWizardStepPanel(step);
        if (panel != null) {
          panel.setQuickSetup(getQuickSetup());
          panel.initialize();
          hmPanels.put(step, panel);
        }
      }
    }

    int minWidth = 0;
    int minHeight = 0;
    setLayout(new CardLayout());
    for (WizardStep s : hmPanels.keySet())
    {
      minWidth = Math.max(minWidth, getPanel(s).getMinimumWidth());
      minHeight = Math.max(minHeight, getPanel(s).getMinimumHeight());
      add(getPanel(s), s.toString());
    }

    // Add a special panel to display while panels are
    // initializing themselves
    JPanel loadingPanel = UIFactory.makeJPanel();
    loadingPanel.setLayout(new GridBagLayout());
    loadingPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            INFO_GENERAL_LOADING.get(),
            UIFactory.TextStyle.PRIMARY_FIELD_VALID),
            new GridBagConstraints());
    add(loadingPanel, LOADING_PANEL);

    // For aesthetical reasons we add a little bit of height
    minHeight += getApplication().getExtraDialogHeight();

    setPreferredSize(new Dimension(minWidth, minHeight));
    setMinimumSize(new Dimension(minWidth, minHeight));
  }

  /**
   * Adds a button listener.  All the button listeners will be notified when
   * the buttons are clicked (by the user or programatically).
   * @param l the ButtonActionListener to be added.
   */
  public void addButtonActionListener(ButtonActionListener l)
  {
    for (WizardStep s : hmPanels.keySet())
    {
      getPanel(s).addButtonActionListener(l);
    }
  }

  /**
   * Removes a button listener.
   * @param l the ButtonActionListener to be removed.
   */
  public void removeButtonActionListener(ButtonActionListener l)
  {
    for (WizardStep s : hmPanels.keySet())
    {
      getPanel(s).removeButtonActionListener(l);
    }
  }

  /**
   * Displays the panel corresponding to the provided step.  The panel contents
   * are updated with the contents of the UserData object.
   * @param step the step that we want to display.
   * @param userData the UserData object that must be used to populate
   * the panels.
   */
  public void setDisplayedStep(final WizardStep step, final UserData userData)
  {
    final CardLayout cl = (CardLayout) (getLayout());

    if (getPanel(step).blockingBeginDisplay())
    {
      // Show the 'loading...' panel and invoke begin
      // display in another thread in case the panel
      // taske a while to initialize.
      cl.show(this, LOADING_PANEL);
      new Thread(new Runnable() {
        public void run() {
          getPanel(step).beginDisplay(userData);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              cl.show(CurrentStepPanel.this, step.toString());
              getPanel(step).endDisplay();
            }
          });
        }
      },"panel begin display thread").start();
    }
    else
    {
      getPanel(step).beginDisplay(userData);
      cl.show(CurrentStepPanel.this, step.toString());
      getPanel(step).endDisplay();
    }
  }

  /**
   * Forwards the different panels the ProgressDescriptor so that they
   * can update their contents accordingly.
   * @param descriptor the descriptor of the Application progress.
   */
  public void displayProgress(ProgressDescriptor descriptor)
  {
    for (WizardStep s : hmPanels.keySet())
    {
      getPanel(s).displayProgress(descriptor);
    }
  }

  /**
   * This method sets up an icon on the bottom left side of the dialog.
   * Generally this method is called with an animated gif that is passed to
   * display progress.
   * @param iconType the icon type to be set.
   */
  public void setIcon(UIFactory.IconType iconType)
  {
    for (WizardStep s : hmPanels.keySet())
    {
      getPanel(s).setIcon(iconType);
    }
  }

  /**
   * Retrieves the panel for the provided step.
   * @param step the step for which we want to get the panel.
   * @return the panel for the provided step.
   */
  private QuickSetupStepPanel getPanel(WizardStep step)
  {
    return hmPanels.get(step);
  }
}
