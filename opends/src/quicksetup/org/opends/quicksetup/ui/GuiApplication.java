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

package org.opends.quicksetup.ui;

import org.opends.quicksetup.*;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.InProcessServerController;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.webstart.WebStartDownloader;
import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
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

  /** Downloads .jar files for webstart application. */
  protected WebStartDownloader loader;

  /** The QuickSetupDialog in control. */
  private QuickSetupDialog qs;

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
  abstract public Message getFrameTitle();

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
  public void setDisplayedWizardStep(WizardStep step,
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
  public abstract void setWizardDialogState(QuickSetupDialog dlg,
                                               UserData userData,
                                               WizardStep step);

  /**
   * Returns the tab formatted.
   * @return the tab formatted.
   */
  protected Message getTab()
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
   * Called before the application cancels its operation, giving the
   * user a chance to confirm the cancellation action.
   * @param qs QuickSetup that can be used for confirming
   * @return boolean where true indicates that the user answered
   * affirmatively to the cancelation confirmation
   */
  public boolean confirmCancel(QuickSetup qs) {
    return qs.displayConfirmation(
          INFO_CONFIRM_CANCEL_PROMPT.get(),
          INFO_CONFIRM_CANCEL_TITLE.get());
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
   * Gets the finished step in the wizard.
   * @return Step the finished step
   */
  abstract public WizardStep getFinishedStep();

  /**
   * Gets the currently displayed wizard step.
   * @return WizardStep being displayed.
   */
  public WizardStep getCurrentWizardStep() {
    return displayedStep;
  }

  /**
   * Returns the QuickSetupDialog in control.
   * @return the QuickSetupDialog in control.
   */
  protected QuickSetupDialog getQuickSetupDialog()
  {
    return qs;
  }

  /**
   * Indicates whether the provided <code>step</code> is a sub step or not.
   * @param step WizardStep for which the return value indicates whether
   * or not is a sub step.
   * @return boolean where true indicates the provided <code>step</code> is a
   * substep.
   */
  public boolean isSubStep(WizardStep step)
  {
    return false;
  }

  /**
   * Indicates whether the provided <code>step</code> is visible or not
   * depending on the contents of the UserData object that is provided.
   * @param step WizardStep for which the return value indicates whether
   * or not is visible.
   * @param userData the UserData to be used to determine if the step is
   * visible or not.
   * @return boolean where true indicates the provided <code>step</code> is
   * visible.
   */
  public boolean isVisible(WizardStep step, UserData userData)
  {
    return true;
  }

  /**
   * Indicates whether the provided <code>step</code> is visible or not
   * depending on the contents of the QuickSetup object that is provided.
   * @param step WizardStep for which the return value indicates whether
   * or not is visible.
   * @param qs the QuickSetup to be used to determine if the step is
   * visible or not.
   * @return boolean where true indicates the provided <code>step</code> is
   * visible.
   */
  public boolean isVisible(WizardStep step, QuickSetup qs)
  {
    return true;
  }

  /**
   * Returns the list of all the steps in an ordered manner.  This is required
   * because in the case of an application with substeps the user of the other
   * interfaces is not enough.  This is a default implementation that uses
   * the getNextWizardStep method to calculate the list that work for
   * applications with no substeps.
   * @return a list containing ALL the steps (including substeps) in an ordered
   * manner.
   */
  public LinkedHashSet<WizardStep> getOrderedSteps()
  {
    LinkedHashSet<WizardStep> orderedSteps = new LinkedHashSet<WizardStep>();
    WizardStep step = getFirstWizardStep();
    orderedSteps.add(step);
    while (null != (step = getNextWizardStep(step))) {
      orderedSteps.add(step);
    }
    return orderedSteps;
  }

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
    return !step.isProgressStep() && getNextWizardStep(step) != null;
  }

  /**
   * Indicates whether or not the user is allowed to finish the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can finish the wizard
   * @return boolean where true indicates the user can finish the wizard
   */
  public abstract boolean canFinish(WizardStep step);

  /**
   * Indicates whether or not the user is allowed to quit the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can quit the wizard
   * @return boolean where true indicates the user can quit the wizard
   */
  public boolean canQuit(WizardStep step) {
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
   * @return boolean that the application uses to indicate the the
   * application should be launched.  If false, the application is
   * responsible for updating the user data for the final screen and
   * launching the application if this is the desired behavior.
   */
  public abstract boolean finishClicked(final WizardStep cStep,
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
  public void closeClicked(WizardStep cStep, QuickSetup qs) {
    qs.quit();
  }

  /**
   * Called when the user has clicked the 'quit' button.
   * @param step WizardStep at which the user clicked the quit button
   * @param qs QuickSetup controller
   */
  public void quitClicked(WizardStep step, QuickSetup qs) {
    qs.quit();
  }

  /**
   * Called whenever this application should update its user data from
   * values found in QuickSetup.
   * @param cStep current wizard step
   * @param qs QuickSetup controller
   * @throws org.opends.quicksetup.UserDataException if there is a problem with
   *  the data
   */
  public abstract void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException;

  /**
   * Gets the key for the close button's tool tip text.
   * @return String key of the text in the resource bundle
   */
  public Message getCloseButtonToolTip() {
    return INFO_CLOSE_BUTTON_TOOLTIP.get();
  }

  /**
   * Gets the key for the quit button's tool tip text.
   * @return String key of the text in the resource bundle
   */
  public Message getQuitButtonToolTip() {
    return INFO_QUIT_BUTTON_INSTALL_TOOLTIP.get();
  }

  /**
   * Gets the key for the finish button's tool tip text.
   * @return String key of the text in the resource bundle
   */
  public Message getFinishButtonToolTip() {
    return INFO_FINISH_BUTTON_TOOLTIP.get();
  }

  /**
   * Gets the key for the finish button's label.
   * @return String key of the text in the resource bundle
   */
  public Message getFinishButtonLabel() {
    return INFO_FINISH_BUTTON_LABEL.get();
  }

  /**
   * Indicates whether the finish button must be placed on the left (close to
   * "Next" button) or on the right (close to "Quit" button).
   * @return <CODE>true</CODE> if the finish button must be placed on the left
   * and <CODE>false</CODE> otherwise.
   */
  public boolean finishOnLeft()
  {
    return true;
  }

  /**
   * Updates the list of certificates accepted by the user in the trust manager
   * based on the information stored in the UserDataCertificateException we got
   * when trying to connect in secure mode.
   * @param ce the UserDataCertificateException that contains the information to
   * be used.
   */
  protected void acceptCertificateForException(UserDataCertificateException ce)
  {
    X509Certificate[] chain = ce.getChain();
    String authType = ce.getAuthType();
    String host = ce.getHost();

    if ((chain != null) && (authType != null) && (host != null))
    {
      LOG.log(Level.INFO, "Accepting certificate presented by host "+host);
      getTrustManager().acceptCertificate(chain, authType, host);
    }
    else
    {
      if (chain == null)
      {
        LOG.log(Level.WARNING,
            "The chain is null for the UserDataCertificateException");
      }
      if (authType == null)
      {
        LOG.log(Level.WARNING,
            "The auth type is null for the UserDataCertificateException");
      }
      if (host == null)
      {
        LOG.log(Level.WARNING,
            "The host is null for the UserDataCertificateException");
      }
    }
  }

  /**
   * Begins downloading webstart jars in another thread
   * for WebStart applications only.
   */
  protected void initLoader() {
    loader = new WebStartDownloader();
    loader.start(false);
  }

  /**
   * Waits for the loader to be finished.  Every time we have an update in the
   * percentage that is downloaded we notify the listeners of this.
   *
   * @param maxRatio is the integer value that tells us which is the max ratio
   * that corresponds to the download.  It is used to calculate how the global
   * installation ratio changes when the download ratio increases.  For instance
   * if we suppose that the download takes 25 % of the total installation
   * process, then maxRatio will be 25.  When the download is complete this
   * method will send a notification to the ProgressUpdateListeners with a ratio
   * of 25 %.
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong
   *
   */
  protected void waitForLoader(Integer maxRatio) throws ApplicationException {
    int lastPercentage = -1;
    WebStartDownloader.Status lastStatus =
      WebStartDownloader.Status.DOWNLOADING;
    while (!loader.isFinished() && (loader.getException() == null))
    {
      // Pool until is over
      int perc = loader.getDownloadPercentage();
      WebStartDownloader.Status downloadStatus = loader.getStatus();
      if ((perc != lastPercentage) || (downloadStatus != lastStatus))
      {
        lastPercentage = perc;
        int ratio = (perc * maxRatio) / 100;
        Message summary;
        switch (downloadStatus)
        {
        case VALIDATING:
          summary = INFO_VALIDATING_RATIO.get(
                  String.valueOf(perc),
                  String.valueOf(loader.getCurrentValidatingPercentage()));
          break;
        case UPGRADING:
          summary = INFO_UPGRADING_RATIO.get(
                  String.valueOf(perc),
                  String.valueOf(loader.getCurrentUpgradingPercentage()));
          break;
        default:
          summary = INFO_DOWNLOADING_RATIO.get(String.valueOf(perc));
        }
        loader.setSummary(summary);
        notifyListeners(ratio, summary, null);

        try
        {
          Thread.sleep(300);
        } catch (Exception ex)
        {
          // do nothing;
        }
      }
    }

    if (loader.getException() != null)
    {
      throw loader.getException();
    }
  }

  /**
   * Gets the amount of addition pixels added to the height
   * of the tallest panel in order to size the wizard for
   * asthetic reasons.
   * @return int height to add
   */
  public int getExtraDialogHeight() {
    return 0;
  }

  /**
   * Starts the server to be able to update its configuration but not allowing
   * it to listen to external connections.
   * @throws ApplicationException if the server could not be started.
   */
  protected void startServerWithoutConnectionHandlers()
  throws ApplicationException {
    try {
      ServerController control = new ServerController(getInstallation());
      if (getInstallation().getStatus().isServerRunning()) {
        control.stopServer(true);
      }
      InProcessServerController ipsc =
              new InProcessServerController(getInstallation());
      InProcessServerController.disableConnectionHandlers(true);
      ipsc.startServer();
    } catch (Throwable t) {
      Message msg = INFO_ERROR_STARTING_SERVER_WITH_NO_CONNECTION_HANDLERS.get(
              (t.getMessage() == null) ? t.toString() : t.getMessage());
      LOG.log(Level.INFO, msg.toString(), t);
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.IMPORT_ERROR, msg, t);
    }
  }

  /**
   * {@inheritDoc}
   */
  public UserInteraction userInteraction() {
    UserInteraction ui = null;
    if (!getUserData().isQuiet()) {
      if (Utils.isCli()) {
        ui = new CliUserInteraction();
      } else {
        ui = new GuiUserInteraction(qs.getFrame());
      }
    }
    return ui;
  }

  /**
   * Sets the QuickSetupDialog driving this application.
   * @param dialog QuickSetupDialog driving this application
   */
  public void setQuickSetupDialog(QuickSetupDialog dialog) {
    this.qs = dialog;
  }
}
