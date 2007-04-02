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

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.*;
import org.opends.quicksetup.ui.QuickSetupDialog;
import org.opends.quicksetup.ui.QuickSetupStepPanel;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.util.Set;

/**
 * QuickSetup application of ugrading the bits of an installation of
 * OpenDS.
 */
public class Upgrader extends Application implements CliApplication {

  /**
   * {@inheritDoc}
   */
  public String getFrameTitle() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFirstWizardStep() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstallationPath() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected String getBinariesPath() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public ProgressStep getStatus() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public Integer getRatio(ProgressStep step) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public String getSummary(ProgressStep step) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {
  }

  /**
   * {@inheritDoc}
   */
  public ButtonName getInitialFocusButtonName() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public JPanel createFramePanel(QuickSetupDialog dlg) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public Set<WizardStep> getWizardSteps() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public Step getNextWizardStep(WizardStep step) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getPreviousWizardStep(WizardStep step) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void quitClicked(WizardStep step, QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  protected void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException
  {
  }

  /**
   * {@inheritDoc}
   */
  public void previousClicked(WizardStep cStep, QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  public void finishClicked(final WizardStep cStep, final QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  public void nextClicked(WizardStep cStep, QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  public void closeClicked(WizardStep cStep, QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked(WizardStep cStep, QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  public void run() {
  }

  /**
   * {@inheritDoc}
   */
  public UserData createUserData(String[] args,
                                 CurrentInstallStatus status)
          throws UserDataException
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public ApplicationException getException() {
    return null;
  }

}
