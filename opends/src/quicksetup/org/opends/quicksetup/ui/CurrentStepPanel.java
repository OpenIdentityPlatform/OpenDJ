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

import java.awt.CardLayout;
import java.awt.Dimension;

import java.util.HashMap;

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.installer.FieldName;
import org.opends.quicksetup.ProgressDescriptor;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.Utils;

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
class CurrentStepPanel extends QuickSetupPanel
{
  private UserData defaultUserData;

  private CurrentInstallStatus installStatus;

  private static final long serialVersionUID = 5474803491510999334L;

  private HashMap<Step, QuickSetupStepPanel> hmPanels =
      new HashMap<Step, QuickSetupStepPanel>();

  /**
   * The constructor of this class.
   * @param defaultUserData the default data that is used to initialize the
   * contents of the panels (the proposed values).
   * @param installStatus the object describing the current installation status.
   * @param isUninstall boolean telling whether we are uninstalling or not.
   */
  public CurrentStepPanel(UserData defaultUserData,
      CurrentInstallStatus installStatus, boolean isUninstall)
  {
    this.defaultUserData = defaultUserData;
    this.installStatus = installStatus;
    createLayout(isUninstall);
  }

  /**
   * Returns the value corresponding to the provided FieldName.
   * @param fieldName the FieldName for which we want to obtain the value.
   * @return the value corresponding to the provided FieldName.
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;
    for (Step s : hmPanels.keySet())
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
    for (Step s : hmPanels.keySet())
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
   * @param isUninstall whether this is an install or uninstall panel.
   */
  private void createLayout(boolean isUninstall)
  {
    if (isUninstall)
    {
      hmPanels.put(Step.CONFIRM_UNINSTALL,
          new ConfirmUninstallPanel(installStatus));
      hmPanels.put(Step.PROGRESS, new ProgressPanel());
    } else
    {
      hmPanels.put(Step.WELCOME, new InstallWelcomePanel());
      hmPanels.put(Step.SERVER_SETTINGS, new ServerSettingsPanel(
          defaultUserData));
      hmPanels.put(Step.DATA_OPTIONS,
          new DataOptionsPanel(defaultUserData));
      hmPanels.put(Step.REVIEW, new ReviewPanel(defaultUserData));
      hmPanels.put(Step.PROGRESS, new ProgressPanel());
    }

    int minWidth = 0;
    int minHeight = 0;
    setLayout(new CardLayout());
    for (Step s : hmPanels.keySet())
    {
      minWidth = Math.max(minWidth, getPanel(s).getMinimumWidth());
      minHeight = Math.max(minHeight, getPanel(s).getMinimumHeight());
      add(getPanel(s), s.toString());
    }

    // For aesthetical reasons we add a little bit of height
    if (!Utils.isUninstall())
    {
      minHeight += UIFactory.EXTRA_DIALOG_HEIGHT;
    }

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
    for (Step s : hmPanels.keySet())
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
    for (Step s : hmPanels.keySet())
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
  public void setDisplayedStep(Step step, UserData userData)
  {
    CardLayout cl = (CardLayout) (getLayout());
    getPanel(step).beginDisplay(userData);
    cl.show(this, step.toString());
    getPanel(step).endDisplay();
  }

  /**
   * Forwards the different panels the ProgressDescriptor so that they
   * can update their contents accordingly.
   * @param descriptor the descriptor of the Uninstallation progress.
   */
  public void displayProgress(ProgressDescriptor descriptor)
  {
    for (Step s : hmPanels.keySet())
    {
      getPanel(s).displayProgress(descriptor);
    }
  }

  /**
   * Retrieves the panel for the provided step.
   * @param step the step for which we want to get the panel.
   * @return the panel for the provided step.
   */
  private QuickSetupStepPanel getPanel(Step step)
  {
    return hmPanels.get(step);
  }
}
