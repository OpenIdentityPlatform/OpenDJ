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

package org.opends.quicksetup.uninstaller;

import org.opends.quicksetup.*;

import static org.opends.quicksetup.Step.FINISHED;
import static org.opends.quicksetup.Step.PROGRESS;
import static org.opends.quicksetup.Step.REVIEW;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.ServerController;
import org.opends.server.tools.ConfigureWindowsService;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.WindowEvent;

/**
 * This class is in charge of performing the uninstallation of Open DS.
 */
public class Uninstaller extends GuiApplication implements CliApplication {

  private ProgressStep status = UninstallProgressStep.NOT_STARTED;

  private HashMap<ProgressStep, Integer> hmRatio =
          new HashMap<ProgressStep, Integer>();

  private HashMap<ProgressStep, String> hmSummary =
          new HashMap<ProgressStep, String>();

  private ApplicationException ue;

  private Boolean isWindowsServiceEnabled;

  private UninstallCliHelper cliHelper = new UninstallCliHelper();

  private static final Logger LOG =
    Logger.getLogger(Uninstaller.class.getName());

  /**
   * {@inheritDoc}
   */
  public String getFrameTitle() {
    return getMsg("frame-uninstall-title");
  }

  /**
   * {@inheritDoc}
   */
  public UserData createUserData() {
    return new UninstallUserData();
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFirstWizardStep() {
    return Step.CONFIRM_UNINSTALL;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getNextWizardStep(WizardStep step) {
    Step nextStep = null;
    if (step != null && step.equals(Step.CONFIRM_UNINSTALL)) {
      nextStep = Step.PROGRESS;
    }
    else if (Step.PROGRESS.equals(step))
    {
      nextStep = Step.FINISHED;
    }
    return nextStep;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getPreviousWizardStep(WizardStep step) {
    Step prevStep = null;
    if (step != null && step.equals(Step.PROGRESS)) {
      prevStep = Step.CONFIRM_UNINSTALL;
    }
    else if (Step.FINISHED.equals(step))
    {
      prevStep = Step.PROGRESS;
    }
    return prevStep;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFinishedStep() {
    return Step.FINISHED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean finishOnLeft()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoBack(WizardStep step) {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoForward(WizardStep step) {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canFinish(WizardStep step) {
    return step == Step.CONFIRM_UNINSTALL;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canQuit(WizardStep step) {
    return step == Step.CONFIRM_UNINSTALL;
  }

  /**
   * {@inheritDoc}
   */
  public void nextClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
      throw new IllegalStateException(
          "Cannot click on next from progress step");
    } else if (cStep == REVIEW) {
      throw new IllegalStateException("Cannot click on next from review step");
    } else if (cStep == FINISHED) {
      throw new IllegalStateException(
          "Cannot click on next from finished step");
    }
  }

  /**
   * {@inheritDoc}
   */
  public void closeClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
        if (isFinished()
            || qs.displayConfirmation(getMsg("confirm-close-uninstall-msg"),
                getMsg("confirm-close-uninstall-title")))
        {
          qs.quit();
        }
    }
    else if (cStep == FINISHED)
    {
      qs.quit();
    } else {
      throw new IllegalStateException(
          "Close only can be clicked on PROGRESS step");
    }
  }

  /**
   * Update the UserData object according to the content of the review
   * panel.
   */
  private void updateUserUninstallDataForConfirmUninstallPanel(QuickSetup qs)
          throws UserDataException {
    UninstallUserData uud = (UninstallUserData) getUserData();
    uud.setRemoveLibrariesAndTools(
            (Boolean) qs.getFieldValue(FieldName.REMOVE_LIBRARIES_AND_TOOLS));
    uud.setRemoveDatabases(
            (Boolean) qs.getFieldValue(FieldName.REMOVE_DATABASES));
    uud.setRemoveConfigurationAndSchema(
            (Boolean) qs.getFieldValue(
                    FieldName.REMOVE_CONFIGURATION_AND_SCHEMA));
    uud.setRemoveBackups(
            (Boolean) qs.getFieldValue(FieldName.REMOVE_BACKUPS));
    uud.setRemoveLDIFs(
            (Boolean) qs.getFieldValue(FieldName.REMOVE_LDIFS));
    uud.setRemoveLogs(
            (Boolean) qs.getFieldValue(FieldName.REMOVE_LOGS));

    Set<String> dbs = new HashSet<String>();
    Set s = (Set) qs.getFieldValue(FieldName.EXTERNAL_DB_DIRECTORIES);
    for (Object v : s) {
      dbs.add((String) v);
    }

    Set<String> logs = new HashSet<String>();
    s = (Set) qs.getFieldValue(FieldName.EXTERNAL_LOG_FILES);
    for (Object v : s) {
      logs.add((String) v);
    }

    uud.setExternalDbsToRemove(dbs);
    uud.setExternalLogsToRemove(logs);

    if ((dbs.size() == 0) &&
            (logs.size() == 0) &&
            !uud.getRemoveLibrariesAndTools() &&
            !uud.getRemoveDatabases() &&
            !uud.getRemoveConfigurationAndSchema() &&
            !uud.getRemoveBackups() &&
            !uud.getRemoveLDIFs() &&
            !uud.getRemoveLogs()) {
      throw new UserDataException(Step.CONFIRM_UNINSTALL,
              getMsg("nothing-selected-to-uninstall"));
    }
  }


  /**
   * {@inheritDoc}
   */
  public void quitClicked(WizardStep step, QuickSetup qs) {
    if (step == Step.PROGRESS) {
      throw new IllegalStateException(
              "Cannot click on quit from progress step");
    }
    else if (step == Step.FINISHED) {
      throw new IllegalStateException(
      "Cannot click on quit from finished step");
    }
    qs.quit();
  }

  /**
   * {@inheritDoc}
   */
  public String getCloseButtonToolTipKey() {
    return "close-button-uninstall-tooltip";
  }

  /**
   * {@inheritDoc}
   */
  public String getFinishButtonToolTipKey() {
    return "finish-button-uninstall-tooltip";
  }

  /**
   * {@inheritDoc}
   */
  public String getFinishButtonLabelKey() {
    return "finish-button-uninstall-label";
  }

  /**
   * {@inheritDoc}
   */
  public void previousClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
      throw new IllegalStateException(
              "Cannot click on previous from progress step");
    }
    else if (cStep == FINISHED) {
      throw new IllegalStateException(
      "Cannot click on previous from finished step");
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean finishClicked(final WizardStep cStep, final QuickSetup qs) {
    if (cStep == Step.CONFIRM_UNINSTALL) {
      BackgroundTask worker = new BackgroundTask() {
        public Object processBackgroundTask() throws UserDataException {
          try {
            updateUserUninstallDataForConfirmUninstallPanel(qs);
          }
          catch (UserDataException uude) {
            throw uude;
          } catch (Throwable t) {
            LOG.log(Level.WARNING, "Error processing task: "+t, t);
            throw new UserDataException(cStep,
                    getThrowableMsg("bug-msg", t));
          }
          Status status = Installation.getLocal().getStatus();
          return status.isServerRunning();
        }

        public void backgroundTaskCompleted(Object returnValue,
                                            Throwable throwable) {
          qs.getDialog().workerFinished();
          if (throwable != null) {
            if (throwable instanceof UserDataException)
            {
              qs.displayError(throwable.getLocalizedMessage(),
                    getMsg("error-title"));
            }
            else
            {
              LOG.log(Level.WARNING, "Error processing task: "+throwable,
                  throwable);
              qs.displayError(throwable.toString(), getMsg("error-title"));
            }
          } else {
            boolean serverRunning = (Boolean) returnValue;
            if (!serverRunning) {
              getUserData().setStopServer(false);
              if (qs.displayConfirmation(
                      getMsg("confirm-uninstall-server-not-running-msg"),
                      getMsg("confirm-uninstall-server-not-running-title"))) {
                qs.launch();
                qs.setCurrentStep(getNextWizardStep(cStep));
              }
            } else {
              if (qs.displayConfirmation(
                      getMsg("confirm-uninstall-server-running-msg"),
                      getMsg("confirm-uninstall-server-running-title"))) {
                getUserData().setStopServer(true);
                qs.launch();
                qs.setCurrentStep(getNextWizardStep(cStep));
              } else {
                getUserData().setStopServer(false);
              }
            }
          }
        }
      };
      qs.getDialog().workerStarted();
      worker.startBackgroundTask();
    }
    // Uninstaller is responsible for updating user data and launching
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void updateUserData(WizardStep step, QuickSetup qs) {
    // do nothing;
  }

  /**
   * {@inheritDoc}
   */
  public void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
    if (step == Step.CONFIRM_UNINSTALL) {
      dlg.setDefaultButton(ButtonName.FINISH);
      dlg.setFocusOnButton(ButtonName.FINISH);
    } else if ((step == PROGRESS) || (step == FINISHED)) {
      dlg.setDefaultButton(ButtonName.CLOSE);
      dlg.setFocusOnButton(ButtonName.CLOSE);
      dlg.setButtonEnabled(ButtonName.CLOSE, false);
    }
  }

  /**
   * {@inheritDoc}
   * @param launcher
   */
  public UserData createUserData(Launcher launcher)
          throws UserDataException {
    return cliHelper.createUserData(launcher.getArguments());
  }

  /**
   * {@inheritDoc}
   */
  public String getInstallationPath() {
    return Utils.getInstallPathFromClasspath();
  }

  /**
   * Returns the ApplicationException that might occur during installation or
   * <CODE>null</CODE> if no exception occurred.
   *
   * @return the ApplicationException that might occur during installation or
   *         <CODE>null</CODE> if no exception occurred.
   */
  public ApplicationException getRunError() {
    return ue;
  }

  /**
   * Initialize the different map used in this class.
   */
  private void initMaps() {
    hmSummary.put(UninstallProgressStep.NOT_STARTED,
            getFormattedSummary(getMsg("summary-uninstall-not-started")));
    hmSummary.put(UninstallProgressStep.STOPPING_SERVER,
            getFormattedSummary(getMsg("summary-stopping")));
    hmSummary.put(UninstallProgressStep.DISABLING_WINDOWS_SERVICE,
            getFormattedSummary(getMsg("summary-disabling-windows-service")));
    hmSummary.put(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES,
            getFormattedSummary(getMsg("summary-deleting-external-db-files")));
    hmSummary.put(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES,
            getFormattedSummary(getMsg("summary-deleting-external-log-files")));
    hmSummary.put(UninstallProgressStep.REMOVING_EXTERNAL_REFERENCES,
            getFormattedSummary(
                    getMsg("summary-deleting-external-references")));
    hmSummary.put(UninstallProgressStep.DELETING_INSTALLATION_FILES,
            getFormattedSummary(getMsg("summary-deleting-installation-files")));

    String successMsg;
    Installation installation = getInstallation();
    String libPath = Utils.getPath(installation.getLibrariesDirectory());
    if (Utils.isCli()) {
      if (getUninstallUserData().getRemoveLibrariesAndTools()) {
        String[] arg = new String[1];
        if (Utils.isWindows()) {
          arg[0] = installation.getUninstallBatFile() + getLineBreak() +
                  getTab() + libPath;
        } else {
          arg[0] = libPath;
        }
        successMsg = getMsg(
                "summary-uninstall-finished-successfully-remove-jarfiles-cli",
                arg);
      } else {
        successMsg = getMsg("summary-uninstall-finished-successfully-cli");
      }
    } else {
      if (getUninstallUserData().getRemoveLibrariesAndTools()) {
        String[] arg = {libPath};
        successMsg = getMsg(
                "summary-uninstall-finished-successfully-remove-jarfiles", arg);
      } else {
        successMsg = getMsg("summary-uninstall-finished-successfully");
      }
    }
    hmSummary.put(UninstallProgressStep.FINISHED_SUCCESSFULLY,
            getFormattedSuccess(successMsg));
    hmSummary.put(UninstallProgressStep.FINISHED_WITH_ERROR,
            getFormattedError(getMsg("summary-uninstall-finished-with-error")));

    /*
    * hmTime contains the relative time that takes for each task to be
    * accomplished. For instance if stopping takes twice the time of
    * deleting files, the value for downloading will be the double of the
    * value for extracting.
    */
    HashMap<UninstallProgressStep, Integer> hmTime =
            new HashMap<UninstallProgressStep, Integer>();
    hmTime.put(UninstallProgressStep.STOPPING_SERVER, 15);
    hmTime.put(UninstallProgressStep.DISABLING_WINDOWS_SERVICE, 5);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES, 30);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES, 5);
    hmTime.put(UninstallProgressStep.REMOVING_EXTERNAL_REFERENCES, 5);
    hmTime.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, 10);

    int totalTime = 0;
    ArrayList<UninstallProgressStep> steps =
            new ArrayList<UninstallProgressStep>();
    if (getUserData().getStopServer()) {
      totalTime += hmTime.get(UninstallProgressStep.STOPPING_SERVER);
      steps.add(UninstallProgressStep.STOPPING_SERVER);
    }
    if (isWindowsServiceEnabled()) {
      totalTime += hmTime.get(UninstallProgressStep.DISABLING_WINDOWS_SERVICE);
      steps.add(UninstallProgressStep.DISABLING_WINDOWS_SERVICE);
    }
    totalTime += hmTime.get(UninstallProgressStep.DELETING_INSTALLATION_FILES);
    steps.add(UninstallProgressStep.DELETING_INSTALLATION_FILES);

    if (getUninstallUserData().getExternalDbsToRemove().size() > 0) {
      totalTime += hmTime.get(
              UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES);
      steps.add(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES);
    }

    if (getUninstallUserData().getExternalLogsToRemove().size() > 0) {
      totalTime += hmTime.get(
              UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES);
      steps.add(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES);
    }

    int cumulatedTime = 0;
    for (UninstallProgressStep s : steps) {
      Integer statusTime = hmTime.get(s);
      hmRatio.put(s, (100 * cumulatedTime) / totalTime);
      if (statusTime != null) {
        cumulatedTime += statusTime;
      }
    }

    hmRatio.put(UninstallProgressStep.FINISHED_SUCCESSFULLY, 100);
    hmRatio.put(UninstallProgressStep.FINISHED_WITH_ERROR, 100);
  }

  /**
   * Actually performs the uninstall in this thread.  The thread is blocked.
   */
  public void run() {
    initMaps();
    PrintStream origErr = System.err;
    PrintStream origOut = System.out;
    try {
      PrintStream err = new ErrorPrintStream();
      PrintStream out = new OutputPrintStream();
      if (!Utils.isCli()) {
        System.setErr(err);
        System.setOut(out);
      }

      boolean displaySeparator = false;
      if (getUserData().getStopServer()) {
        status = UninstallProgressStep.STOPPING_SERVER;
        new ServerController(this).stopServer();
        displaySeparator = true;
      }
      if (isWindowsServiceEnabled()) {
        status = UninstallProgressStep.DISABLING_WINDOWS_SERVICE;
        if (displaySeparator) {
          notifyListeners(getTaskSeparator());
        }
        disableWindowsService();
        displaySeparator = true;
      }

      Set<String> dbsToDelete = getUninstallUserData().getExternalDbsToRemove();
      if (dbsToDelete.size() > 0) {
        status = UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES;
        if (displaySeparator) {
          notifyListeners(getTaskSeparator());
        }

        deleteExternalDatabaseFiles(dbsToDelete);
        displaySeparator = true;
      }

      Set<String> logsToDelete =
              getUninstallUserData().getExternalLogsToRemove();
      if (logsToDelete.size() > 0) {
        status = UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES;

        if (displaySeparator) {
          notifyListeners(getTaskSeparator());
        }

        deleteExternalLogFiles(logsToDelete);
        displaySeparator = true;
      }

      UninstallUserData userData = getUninstallUserData();
      boolean somethingToDelete = userData.getRemoveBackups() ||
              userData.getRemoveConfigurationAndSchema() ||
              userData.getRemoveDatabases() ||
              userData.getRemoveLDIFs() ||
              userData.getRemoveLibrariesAndTools() ||
              userData.getRemoveLogs();
      if (displaySeparator && somethingToDelete) {
        notifyListeners(getTaskSeparator());
      }

      if (somethingToDelete) {
        status = UninstallProgressStep.DELETING_INSTALLATION_FILES;
        deleteInstallationFiles(getRatio(status),
                getRatio(UninstallProgressStep.FINISHED_SUCCESSFULLY));
      }
      status = UninstallProgressStep.FINISHED_SUCCESSFULLY;
      if (Utils.isCli()) {
        notifyListeners(getLineBreak() + getLineBreak() + getSummary(status));
      } else {
        notifyListeners(null);
      }

    } catch (ApplicationException ex) {
      ue = ex;
      status = UninstallProgressStep.FINISHED_WITH_ERROR;
      String msg = getFormattedError(ex, true);
      notifyListeners(msg);
    }
    catch (Throwable t) {
      ue = new ApplicationException(
              ApplicationException.Type.BUG,
              getThrowableMsg("bug-msg", t), t);
      status = UninstallProgressStep.FINISHED_WITH_ERROR;
      String msg = getFormattedError(ue, true);
      notifyListeners(msg);
    }
    if (!Utils.isCli()) {
      System.setErr(origErr);
      System.setOut(origOut);
    }
  }

  /**
   * {@inheritDoc}
   */
  public ProgressStep getCurrentProgressStep() {
    return status;
  }

  /**
   * Returns an integer that specifies which percentage of the whole
   * installation has been completed.
   *
   * @param step the UninstallProgressStep for which we want to get the ratio.
   * @return an integer that specifies which percentage of the whole
   *         uninstallation has been completed.
   */
  public Integer getRatio(ProgressStep step) {
    return hmRatio.get(step);
  }

  /**
   * Returns an formatted representation of the summary for the specified
   * UninstallProgressStep.
   *
   * @param step the UninstallProgressStep for which we want to get the summary.
   * @return an formatted representation of the summary for the specified
   *         UninstallProgressStep.
   */
  public String getSummary(ProgressStep step) {
    return hmSummary.get(step);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isFinished() {
    return getCurrentProgressStep() ==
            UninstallProgressStep.FINISHED_SUCCESSFULLY
    || getCurrentProgressStep() ==
            UninstallProgressStep.FINISHED_WITH_ERROR;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isCancellable() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void cancel() {
    // do nothing; not cancellable
  }

  /**
   * {@inheritDoc}
   */
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {
    if ((dlg.getDisplayedStep() == PROGRESS) ||
        (dlg.getDisplayedStep() == FINISHED)) {
      // Simulate a close button event
      dlg.notifyButtonEvent(ButtonName.CLOSE);
    } else {
      // Simulate a quit button event
      dlg.notifyButtonEvent(ButtonName.QUIT);
    }
  }

  /**
   * {@inheritDoc}
   */
  public ButtonName getInitialFocusButtonName() {
    return ButtonName.FINISH;
  }

  /**
   * {@inheritDoc}
   */
  public Set<? extends WizardStep> getWizardSteps() {
    Set<WizardStep> setSteps = new HashSet<WizardStep>();
    setSteps.add(Step.CONFIRM_UNINSTALL);
    setSteps.add(Step.PROGRESS);
    setSteps.add(Step.FINISHED);
    return Collections.unmodifiableSet(setSteps);
  }

  /**
   * {@inheritDoc}
   */
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step) {
    QuickSetupStepPanel p = null;
    if (step == Step.CONFIRM_UNINSTALL) {
      p = new ConfirmUninstallPanel(this, installStatus);
    } else if (step == Step.PROGRESS) {
      p = new ProgressPanel(this);
    } else if (step == Step.FINISHED) {
      p = new FinishedPanel(this);
    }
    return p;
  }

  /**
   * Deletes the external database files specified in the provided Set.
   *
   * @param dbFiles the database directories to be deleted.
   * @throws ApplicationException if something goes wrong.
   */
  private void deleteExternalDatabaseFiles(Set<String> dbFiles)
          throws ApplicationException {
    notifyListeners(getFormattedProgress(
            getMsg("progress-deleting-external-db-files")) +
            getLineBreak());
    for (String path : dbFiles) {
      deleteRecursively(new File(path));
    }
  }

  /**
   * Deletes the external database files specified in the provided Set.
   *
   * @param logFiles the log files to be deleted.
   * @throws ApplicationException if something goes wrong.
   */
  private void deleteExternalLogFiles(Set<String> logFiles)
          throws ApplicationException {
    notifyListeners(getFormattedProgress(
            getMsg("progress-deleting-external-log-files")) +
            getLineBreak());
    for (String path : logFiles) {
      deleteRecursively(new File(path));
    }
  }

  /**
   * Deletes the files under the installation path.
   *
   * @throws ApplicationException if something goes wrong.
   */
  private void deleteInstallationFiles(int minRatio, int maxRatio)
          throws ApplicationException {
    notifyListeners(getFormattedProgress(
            getMsg("progress-deleting-installation-files")) +
            getLineBreak());
    File f = new File(Utils.getInstallPathFromClasspath());
    InstallationFilesToDeleteFilter filter =
            new InstallationFilesToDeleteFilter();
    File[] rootFiles = f.listFiles();
    if (rootFiles != null) {
      /* The following is done to have a moving progress bar when we delete
       * the installation files.
       */
      int totalRatio = 0;
      ArrayList<Integer> cumulatedRatio = new ArrayList<Integer>();
      for (int i = 0; i < rootFiles.length; i++) {
        if (filter.accept(rootFiles[i])) {
          Installation installation = getInstallation();
          int relativeRatio;
          if (equalsOrDescendant(rootFiles[i],
                  installation.getLibrariesDirectory())) {
            relativeRatio = 10;
          } else
          if (equalsOrDescendant(rootFiles[i],
                  installation.getBinariesDirectory())) {
            relativeRatio = 5;
          } else
          if (equalsOrDescendant(rootFiles[i],
                  installation.getConfigurationDirectory())) {
            relativeRatio = 5;
          } else
          if (equalsOrDescendant(rootFiles[i],
                  installation.getBackupDirectory())) {
            relativeRatio = 20;
          } else
          if (equalsOrDescendant(rootFiles[i],
                  installation.getLdifDirectory())) {
            relativeRatio = 20;
          } else if (equalsOrDescendant(rootFiles[i],
                  installation.getDatabasesDirectory())) {
            relativeRatio = 50;
          } else
          if (equalsOrDescendant(rootFiles[i],
                  installation.getLogsDirectory())) {
            relativeRatio = 30;
          } else {
            relativeRatio = 2;
          }
          cumulatedRatio.add(totalRatio);
          totalRatio += relativeRatio;
        } else {
          cumulatedRatio.add(totalRatio);
        }
      }
      Iterator<Integer> it = cumulatedRatio.iterator();
      for (int i = 0; i < rootFiles.length; i++) {
        int beforeRatio = minRatio +
                ((it.next() * (maxRatio - minRatio)) / totalRatio);
        hmRatio.put(UninstallProgressStep.DELETING_INSTALLATION_FILES,
                beforeRatio);
        deleteRecursively(rootFiles[i], filter);
      }
      hmRatio.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, maxRatio);
    }
  }

  /**
   * Deletes everything below the specified file.
   *
   * @param file the path to be deleted.
   * @throws ApplicationException if something goes wrong.
   */
  private void deleteRecursively(File file) throws ApplicationException {
    deleteRecursively(file, null);
  }

  /**
   * Deletes everything below the specified file.
   *
   * @param file   the path to be deleted.
   * @param filter the filter of the files to know if the file can be deleted
   *               directly or not.
   * @throws ApplicationException if something goes wrong.
   */
  private void deleteRecursively(File file, FileFilter filter)
          throws ApplicationException {
    if (file.exists()) {
      if (file.isFile()) {
        if (filter != null) {
          if (filter.accept(file)) {
            delete(file);
          }
        } else {
          delete(file);
        }
      } else {
        File[] children = file.listFiles();
        if (children != null) {
          for (int i = 0; i < children.length; i++) {
            deleteRecursively(children[i], filter);
          }
        }
        if (filter != null) {
          if (filter.accept(file)) {
            delete(file);
          }
        } else {
          delete(file);
        }
      }
    } else {
      // Just tell that the file/directory does not exist.
      String[] arg = {file.toString()};
      notifyListeners(getFormattedWarning(
              getMsg("progress-deleting-file-does-not-exist", arg)));
    }
  }

  /**
   * Deletes the specified file.
   *
   * @param file the file to be deleted.
   * @throws ApplicationException if something goes wrong.
   */
  private void delete(File file) throws ApplicationException {
    String[] arg = {file.getAbsolutePath()};
    boolean isFile = file.isFile();

    if (isFile) {
      notifyListeners(getFormattedWithPoints(
              getMsg("progress-deleting-file", arg)));
    } else {
      notifyListeners(getFormattedWithPoints(
              getMsg("progress-deleting-directory", arg)));
    }

    boolean delete = false;
    /*
     * Sometimes the server keeps some locks on the files.
     * TODO: remove this code once stop-ds returns properly when server
     * is stopped.
     */
    int nTries = 5;
    for (int i = 0; i < nTries && !delete; i++) {
      delete = file.delete();
      if (!delete) {
        try {
          Thread.sleep(1000);
        }
        catch (Exception ex) {
        }
      }
    }

    if (!delete) {
      String errMsg;
      if (isFile) {
        errMsg = getMsg("error-deleting-file", arg);
      } else {
        errMsg = getMsg("error-deleting-directory", arg);
      }
      throw new ApplicationException(
              ApplicationException.Type.FILE_SYSTEM_ERROR, errMsg, null);
    }

    notifyListeners(getFormattedDone() + getLineBreak());
  }

  private boolean equalsOrDescendant(File file, File directory) {
    return file.equals(directory) || Utils.isDescendant(file, directory);
  }

  /**
   * This class is used to get the files that are not binaries.  This is
   * required to know which are the files that can be deleted directly and which
   * not.
   */
  class InstallationFilesToDeleteFilter implements FileFilter {
    Installation installation = getInstallation();
    File quicksetupFile = installation.getQuicksetupJarFile();
    File openDSFile = installation.getOpenDSJarFile();
    File librariesFile = installation.getLibrariesDirectory();
    File activationFile = new File(librariesFile, "activation.jar");
    File aspectRuntimeFile = new File(librariesFile, "aspectjrt.jar");
    File uninstallBatFile = installation.getUninstallBatFile();

    File installationPath = installation.getRootDirectory();

    /**
     * {@inheritDoc}
     */
    public boolean accept(File file) {
      UninstallUserData userData = getUninstallUserData();
      boolean[] uData = {
              userData.getRemoveLibrariesAndTools(),
              userData.getRemoveLibrariesAndTools(),
              userData.getRemoveDatabases(),
              userData.getRemoveLogs(),
              userData.getRemoveConfigurationAndSchema(),
              userData.getRemoveBackups(),
              userData.getRemoveLDIFs()
      };

      Installation installation = getInstallation();
      File[] parentFiles = {
              installation.getLibrariesDirectory(),
              installation.getBinariesDirectory(),
              installation.getDatabasesDirectory(),
              installation.getLogsDirectory(),
              installation.getConfigurationDirectory(),
              installation.getBackupDirectory(),
              installation.getLdifDirectory()
      };

      boolean accept =
              !installationPath.equals(file)
                      && !equalsOrDescendant(file, librariesFile)
                      && !quicksetupFile.equals(file)
                      && !openDSFile.equals(file);

      if (accept && Utils.isWindows() && Utils.isCli()) {
        accept = !uninstallBatFile.equals(file);
      }

      for (int i = 0; i < uData.length && accept; i++) {
        accept &= uData[i] ||
                !equalsOrDescendant(file, parentFiles[i]);
      }

      LOG.log(Level.INFO, "accept for :"+file+" is: "+accept);
      return accept;
    }
  }

  private boolean isWindowsServiceEnabled() {
    if (isWindowsServiceEnabled == null) {
      if (ConfigureWindowsService.serviceState(null, null) ==
              ConfigureWindowsService.SERVICE_STATE_ENABLED) {
        isWindowsServiceEnabled = Boolean.TRUE;
      } else {
        isWindowsServiceEnabled = Boolean.FALSE;
      }
    }
    return isWindowsServiceEnabled.booleanValue();
  }

  /**
   * This methods disables this server as a Windows service.
   *
   * @throws ApplicationException if something goes wrong.
   */
  protected void disableWindowsService() throws ApplicationException {
    notifyListeners(getFormattedProgress(
            getMsg("progress-disabling-windows-service")));
    int code = ConfigureWindowsService.disableService(System.out, System.err);

    String errorMessage = getMsg("error-disabling-windows-service",
        getInstallationPath());

    switch (code) {
      case ConfigureWindowsService.SERVICE_DISABLE_SUCCESS:
        break;
      case ConfigureWindowsService.SERVICE_ALREADY_DISABLED:
        break;
      default:
        throw new ApplicationException(
                ApplicationException.Type.WINDOWS_SERVICE_ERROR,
                errorMessage, null);
    }
  }

  private UninstallUserData getUninstallUserData() {
    return (UninstallUserData) getUserData();
  }

}

