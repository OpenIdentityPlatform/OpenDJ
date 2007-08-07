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

package org.opends.quicksetup.installandupgrader;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.WizardStep;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.installandupgrader.ui.WelcomePanel;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.offline.OfflineInstaller;
import org.opends.quicksetup.installer.webstart.WebStartInstaller;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetup;
import org.opends.quicksetup.ui.QuickSetupDialog;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.upgrader.UpgradeLauncher;
import org.opends.quicksetup.upgrader.UpgradeWizardStep;
import org.opends.quicksetup.upgrader.Upgrader;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

/**
 * Application that allows to upgrade or install OpenDS.
 */
public class InstallAndUpgrader extends GuiApplication
{
  static private final Logger LOG =
    Logger.getLogger(InstallAndUpgrader.class.getName());
  private Installer installer;
  private Upgrader upgrader;
  private InstallAndUpgradeUserData userData;

  /**
   * Creates a default instance.
   */
  public InstallAndUpgrader() {

    // Initialize the logs if necessary
    try {
      if (!QuickSetupLog.isInitialized())
        QuickSetupLog.initLogFileHandler(
                File.createTempFile(
                        UpgradeLauncher.LOG_FILE_PREFIX,
                        QuickSetupLog.LOG_FILE_SUFFIX));
    } catch (IOException e) {
      System.err.println(
              ResourceProvider.getInstance().getMsg("error-initializing-log"));
      e.printStackTrace();
    }
    if (Utils.isWebStart())
    {
      installer = new WebStartInstaller();
    }
    else
    {
      installer = new OfflineInstaller();
    }
    upgrader = new Upgrader();
  }



  /**
   * {@inheritDoc}
   */
  public void setProgressMessageFormatter(ProgressMessageFormatter formatter) {
    super.setProgressMessageFormatter(formatter);
    installer.setProgressMessageFormatter(formatter);
    upgrader.setProgressMessageFormatter(formatter);
  }

  /**
   * {@inheritDoc}
   */
  public void setCurrentInstallStatus(CurrentInstallStatus installStatus) {
    super.setCurrentInstallStatus(installStatus);
    installer.setCurrentInstallStatus(installStatus);
    upgrader.setCurrentInstallStatus(installStatus);
  }

  /**
   * {@inheritDoc}
   */
  public UserData getUserData()
  {
    return getDelegateApplication().getUserData();
  }

  /**
   * {@inheritDoc}
   */
  public String getFrameTitle() {
    return getMsg("frame-install-title");
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFirstWizardStep() {
    return Step.WELCOME;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFinishedStep() {
    return getDelegateApplication().getFinishedStep();
  }

  /**
   * {@inheritDoc}
   */
  public void run()
  {
    getDelegateApplication().run();
  }

  /**
   * {@inheritDoc}
   */
  public void addProgressUpdateListener(ProgressUpdateListener l)
  {
    installer.addProgressUpdateListener(l);
    upgrader.addProgressUpdateListener(l);
  }

  /**
   * {@inheritDoc}
   */
  public void removeProgressUpdateListener(ProgressUpdateListener l)
  {
    installer.removeProgressUpdateListener(l);
    upgrader.removeProgressUpdateListener(l);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isCancellable()
  {
    return getDelegateApplication().isCancellable();
  }

  /**
   * {@inheritDoc}
   */
  public void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
    if ((getDelegateApplication() == upgrader) &&
        (step == Step.WELCOME))
    {
      step = UpgradeWizardStep.WELCOME;
    }
    getDelegateApplication().setWizardDialogState(dlg, userData, step);
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getNextWizardStep(WizardStep step) {
    if ((getDelegateApplication() == upgrader) &&
        (step == Step.WELCOME))
    {
      step = UpgradeWizardStep.WELCOME;
    }
    return getDelegateApplication().getNextWizardStep(step);
  }

  /**
   * {@inheritDoc}
   */
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step) {
    QuickSetupStepPanel p = null;
    if (step == Step.WELCOME) {
      p = new WelcomePanel(this);
    }
    if (p == null)
    {
      p = upgrader.createWizardStepPanel(step);
    }
    if (p == null)
    {
      p = installer.createWizardStepPanel(step);
    }
    return p;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getPreviousWizardStep(WizardStep step) {
    WizardStep s = getDelegateApplication().getPreviousWizardStep(step);
    if (s == UpgradeWizardStep.WELCOME)
    {
      s = Step.WELCOME;
    }
    return s;
  }

  /**
   * {@inheritDoc}
   */
  public LinkedHashSet<WizardStep> getOrderedSteps()
  {
    LinkedHashSet<WizardStep> set = new LinkedHashSet<WizardStep>();
    set.addAll(installer.getOrderedSteps());
    set.addAll(upgrader.getOrderedSteps());
    set.remove(UpgradeWizardStep.WELCOME);
    return set;
  }

  /**
   * {@inheritDoc}
   */
  public ProgressStep getCurrentProgressStep() {
    return getDelegateApplication().getCurrentProgressStep();
  }

  /**
   * {@inheritDoc}
   */
  public Integer getRatio(ProgressStep step) {
    return getDelegateApplication().getRatio(step);
  }

  /**
   * {@inheritDoc}
   */
  public String getSummary(ProgressStep step) {
    return getDelegateApplication().getSummary(step);
  }

  /**
   * {@inheritDoc}
   */
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {
    getDelegateApplication().windowClosing(dlg, evt);
  }

  /**
   * {@inheritDoc}
   */
  public ButtonName getInitialFocusButtonName() {
    return getDelegateApplication().getInitialFocusButtonName();
  }

  /**
   * {@inheritDoc}
   */
  public Set<? extends WizardStep> getWizardSteps() {
    Set<WizardStep> set = new HashSet<WizardStep>();
    set.addAll(installer.getWizardSteps());
    set.addAll(upgrader.getWizardSteps());
    set.remove(UpgradeWizardStep.WELCOME);
    return set;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVisible(WizardStep step, UserData userData)
  {
    boolean isVisible;
    if (step == Step.WELCOME)
    {
      isVisible = true;
    }
    else
    {
      isVisible = getDelegateApplication().isVisible(step,
          getDelegateApplication().getUserData()) &&
      getDelegateApplication().getWizardSteps().contains(step);
    }
    return isVisible;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVisible(WizardStep step, QuickSetup qs)
  {
    boolean isVisible;
    if (step == Step.WELCOME)
    {
      isVisible = true;
    }
    else
    {
      GuiApplication appl;
      Boolean isUpgrade = (Boolean)qs.getFieldValue(FieldName.IS_UPGRADE);
      if (Boolean.TRUE.equals(isUpgrade))
      {
        appl = upgrader;
      }
      else
      {
        appl = installer;
      }
      isVisible = appl.isVisible(step, qs) &&
      appl.getWizardSteps().contains(step);
    }
    return isVisible;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSubStep(WizardStep step)
  {
    boolean isSubStep;
    if (step == Step.WELCOME)
    {
      isSubStep = false;
    }
    else
    {
      isSubStep = getDelegateApplication().isSubStep(step);
    }
    return isSubStep;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoBack(WizardStep step) {
    boolean canGoBack;
    if (step == Step.WELCOME)
    {
      canGoBack = false;
    }
    else
    {
      canGoBack = getDelegateApplication().canGoBack(step);
    }
    return canGoBack;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoForward(WizardStep step) {
    boolean canGoForward;
    if (step == Step.WELCOME)
    {
      canGoForward = true;
    }
    else
    {
      canGoForward = getDelegateApplication().canGoForward(step);
    }
    return canGoForward;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canFinish(WizardStep step) {
    return getDelegateApplication().canFinish(step);
  }

  /**
   * {@inheritDoc}
   */
  public boolean canQuit(WizardStep step) {
    return getDelegateApplication().canQuit(step);
  }

  /**
   * {@inheritDoc}
   */
  public void previousClicked(WizardStep cStep, QuickSetup qs)
  {
    getDelegateApplication().previousClicked(cStep, qs);
  }

  /**
   * {@inheritDoc}
   */
  public void nextClicked(WizardStep cStep, QuickSetup qs)
  {
    getDelegateApplication().nextClicked(cStep, qs);
  }

  /**
   * {@inheritDoc}
   */
  public boolean finishClicked(WizardStep cStep, QuickSetup qs)
  {
    return getDelegateApplication().finishClicked(cStep, qs);
  }

  /**
   * {@inheritDoc}
   */
  public void closeClicked(WizardStep cStep, QuickSetup qs)
  {
    getDelegateApplication().closeClicked(cStep, qs);
  }

  /**
   * {@inheritDoc}
   */
  public void quitClicked(WizardStep cStep, QuickSetup qs)
  {
    getDelegateApplication().quitClicked(cStep, qs);
  }

  /**
   * {@inheritDoc}
   */
  public void updateUserData(WizardStep cStep, QuickSetup qs)
  throws UserDataException
  {
    if (cStep == Step.WELCOME)
    {
      Boolean isUpgrade = (Boolean)qs.getFieldValue(FieldName.IS_UPGRADE);
      getInstallAndUpgradeUserData().setUpgrade(isUpgrade);
      if (isUpgrade)
      {
        upgrader.updateUserData(UpgradeWizardStep.WELCOME, qs);
        getUserData().setServerLocation(
            upgrader.getUserData().getServerLocation());
      }
      else
      {
        installer.updateUserData(cStep, qs);
      }
    }
    else
    {
      getDelegateApplication().updateUserData(cStep, qs);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void cancel()
  {
    getDelegateApplication().cancel();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isFinished()
  {
    return getDelegateApplication().isFinished();
  }

  /**
   * {@inheritDoc}
   */
  public String getInstallationPath()
  {
    return getDelegateApplication().getInstallationPath();
  }

  /**
   * {@inheritDoc}
   */
  public void setQuickSetupDialog(QuickSetupDialog dialog) {
    installer.setQuickSetupDialog(dialog);
    upgrader.setQuickSetupDialog(dialog);
  }

  private GuiApplication getDelegateApplication()
  {
    GuiApplication application;
    if (getInstallAndUpgradeUserData().isUpgrade())
    {
      application = upgrader;
    }
    else
    {
      application = installer;
    }
    return application;
  }

  /**
   * Returns the install and upgrader specific user data.
   * @return the install and upgrader specific user data.
   */
  public InstallAndUpgradeUserData getInstallAndUpgradeUserData()
  {
    if (userData == null) {
      userData = new InstallAndUpgradeUserData();
    }
    return userData;
  }
}
