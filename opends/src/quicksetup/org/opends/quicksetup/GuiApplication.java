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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.opends.quicksetup.ui.FramePanel;
import org.opends.quicksetup.ui.QuickSetupDialog;
import org.opends.quicksetup.ui.QuickSetupStepPanel;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This class represents an application with a wizard GUI that can be run in the
 * context of QuickSetup.  Examples of applications might be 'installer',
 * 'uninstaller' and 'upgrader'.
 */
public abstract class GuiApplication extends Application {

  static private final Logger LOG =
          Logger.getLogger(GuiApplication.class.getName());

  /** The currently displayed wizard step. */
  private WizardStep displayedStep;

  /**
   * Constructs an instance of an application.  Subclasses
   * of this application must have a default constructor.
   */
  public GuiApplication() {
    this.displayedStep = getFirstWizardStep();
  }

  /**
   * Gets the frame title of the GUI application that will be used
   * in some operating systems.
   * @return internationalized String representing the frame title
   */
  abstract public String getFrameTitle();

  /**
   * Returns whether the installer has finished or not.
   * @return <CODE>true</CODE> if the install is finished or <CODE>false
   * </CODE> if not.
   */
  public boolean isFinished()
  {
    return getCurrentProgressStep().isLast();
  }

  /**
   * Returns the initial wizard step.
   * @return Step representing the first step to show in the wizard
   */
  public abstract WizardStep getFirstWizardStep();

  /**
   * Called by the quicksetup controller when the user advances to
   * a new step in the wizard.  Applications are expected to manipulate
   * the QuickSetupDialog to reflect the current step.
   *
   * @param step     Step indicating the new current step
   * @param userData UserData representing the data specified by the user
   * @param dlg      QuickSetupDialog hosting the wizard
   */
  protected void setDisplayedWizardStep(WizardStep step,
                                        UserData userData,
                                        QuickSetupDialog dlg) {
    this.displayedStep = step;

    // First call the panels to do the required updates on their layout
    dlg.setDisplayedStep(step, userData);
    setWizardDialogState(dlg, userData, step);
  }

  /**
   * Called when the user advances to new step in the wizard.  Applications
   * are expected to manipulate the QuickSetupDialog to reflect the current
   * step.
   * @param dlg QuickSetupDialog hosting the wizard
   * @param userData UserData representing the data specified by the user
   * @param step Step indicating the new current step
   */
  protected abstract void setWizardDialogState(QuickSetupDialog dlg,
                                               UserData userData,
                                               WizardStep step);

  /**
   * Returns the tab formatted.
   * @return the tab formatted.
   */
  protected String getTab()
  {
    return formatter.getTab();
  }

  /**
   * Called by the controller when the window is closing.  The application
   * can take application specific actions here.
   * @param dlg QuickSetupDialog that will be closing
   * @param evt The event from the Window indicating closing
   */
  abstract public void windowClosing(QuickSetupDialog dlg, WindowEvent evt);

  /**
   * This method is called when we detected that there is something installed
   * we inform of this to the user and the user wants to proceed with the
   * installation destroying the contents of the data and the configuration
   * in the current installation.
   */
  public void forceToDisplay() {
    // This is really only appropriate for Installer.
    // The default implementation is to do nothing.
    // The Installer application overrides this with
    // whatever it needs.
  }

  /**
   * Get the name of the button that will receive initial focus.
   * @return ButtonName of the button to receive initial focus
   */
  abstract public ButtonName getInitialFocusButtonName();

  /**
   * Creates the main panel for the wizard dialog.
   * @param dlg QuickSetupDialog used
   * @return JPanel frame panel
   */
  public JPanel createFramePanel(QuickSetupDialog dlg) {
    return new FramePanel(dlg.getStepsPanel(),
            dlg.getCurrentStepPanel(),
            dlg.getButtonsPanel());
  }

  /**
   * Returns the set of wizard steps used in this application's wizard.
   * @return Set of Step objects representing wizard steps
   */
  abstract public Set<? extends WizardStep> getWizardSteps();

  /**
   * Creates a wizard panel given a specific step.
   * @param step for which a panel representation should be created
   * @return QuickSetupStepPanel for representing the <code>step</code>
   */
  abstract public QuickSetupStepPanel createWizardStepPanel(WizardStep step);

  /**
   * Gets the next step in the wizard given a current step.
   * @param step Step the current step
   * @return Step the next step
   */
  abstract public WizardStep getNextWizardStep(WizardStep step);

  /**
   * Gets the previous step in the wizard given a current step.
   * @param step Step the current step
   * @return Step the previous step
   */
  abstract public WizardStep getPreviousWizardStep(WizardStep step);

  /**
   * Indicates whether or not the user is allowed to return to a previous
   * step from <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can return to a previous step
   * @return boolean where true indicates the user can return to a previous
   * step from <code>step</code>
   */
  public boolean canGoBack(WizardStep step) {
    return !getFirstWizardStep().equals(step);
  }

  /**
   * Indicates whether or not the user is allowed to move to a new
   * step from <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can move to a new step
   * @return boolean where true indicates the user can move to a new
   * step from <code>step</code>
   */
  public boolean canGoForward(WizardStep step) {
    return getNextWizardStep(step) != null;
  }

  /**
   * Inidicates whether or not the user is allowed to finish the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can finish the wizard
   * @return boolean where true indicates the user can finish the wizard
   */
  public boolean canFinish(WizardStep step) {
    return getNextWizardStep(step) != null;
  }

  /**
   * Inidicates whether or not the user is allowed to quit the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can quit the wizard
   * @return boolean where true indicates the user can quit the wizard
   */
  public boolean canQuit(WizardStep step) {
    return false;
  }

  /**
   * Inidicates whether or not the user is allowed to close the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can close the wizard
   * @return boolean where true indicates the user can close the wizard
   */
  public boolean canClose(WizardStep step) {
    return false;
  }

  /**
   * Inidicates whether or not the user is allowed to cancel the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can cancel the wizard
   * @return boolean where true indicates the user can cancel the wizard
   */
  public boolean canCancel(WizardStep step) {
    return false;
  }

  /**
   * Called when the user has clicked the 'previous' button.
   * @param cStep WizardStep at which the user clicked the previous button
   * @param qs QuickSetup controller
   */
  public abstract void previousClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'finish' button.
   * @param cStep WizardStep at which the user clicked the previous button
   * @param qs QuickSetup controller
   */
  public abstract void finishClicked(final WizardStep cStep,
                                     final QuickSetup qs);

  /**
   * Called when the user has clicked the 'next' button.
   * @param cStep WizardStep at which the user clicked the next button
   * @param qs QuickSetup controller
   */
  public abstract void nextClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'close' button.
   * @param cStep WizardStep at which the user clicked the close button
   * @param qs QuickSetup controller
   */
  public abstract void closeClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'cancel' button.
   * @param cStep WizardStep at which the user clicked the cancel button
   * @param qs QuickSetup controller
   */
  public abstract void cancelClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'quit' button.
   * @param step WizardStep at which the user clicked the quit button
   * @param qs QuickSetup controller
   */
  abstract public void quitClicked(WizardStep step, QuickSetup qs);

  /**
   * Called whenever this application should update its user data from
   * values found in QuickSetup.
   * @param cStep current wizard step
   * @param qs QuickSetup controller
   * @throws UserDataException if there is a problem with the data
   */
  abstract protected void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException;

  /**
   * Gets the key for the close button's tool tip text.
   * @return String key of the text in the resource bundle
   */
  public String getCloseButtonToolTip() {
    return "close-button-tooltip";
  }

  /**
   * Gets the key for the finish button's tool tip text.
   * @return String key of the text in the resource bundle
   */
  public String getFinishButtonToolTip() {
    return "finish-button-tooltip";
  }

  /**
   * Gets the key for the finish button's label.
   * @return String key of the text in the resource bundle
   */
  public String getFinishButtonLabel() {
    return "finish-button-label";
  }

}
