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

package org.opends.guitools.uninstaller;

import org.opends.quicksetup.*;

import static org.opends.quicksetup.Step.FINISHED;
import static org.opends.quicksetup.Step.PROGRESS;
import static org.opends.quicksetup.Step.REVIEW;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.i18n.ResourceProvider;
import org.opends.guitools.uninstaller.ui.ConfirmUninstallPanel;
import org.opends.guitools.uninstaller.ui.LoginDialog;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.ServerController;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.MultimasterDomainCfgClient;
import
org.opends.server.admin.std.client.MultimasterSynchronizationProviderCfgClient;
import org.opends.server.admin.std.client.ReplicationServerCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.ConfigureWindowsService;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.WindowEvent;

import javax.naming.Context;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * This class is in charge of performing the uninstallation of Open DS.
 */
public class Uninstaller extends GuiApplication implements CliApplication {

  private ProgressStep status = UninstallProgressStep.NOT_STARTED;
  private boolean runStarted;
  private boolean errorOnRemoteOccurred;

  private HashMap<ProgressStep, Integer> hmRatio =
          new HashMap<ProgressStep, Integer>();

  private HashMap<ProgressStep, String> hmSummary =
          new HashMap<ProgressStep, String>();

  private ApplicationException ue;

  private Boolean isWindowsServiceEnabled;

  private UninstallCliHelper cliHelper = new UninstallCliHelper();

  private static final Logger LOG =
    Logger.getLogger(Uninstaller.class.getName());

  private LoginDialog loginDialog;
  private ProgressDialog startProgressDlg;
  private StringBuffer startProgressDetails = new StringBuffer();
  private UninstallData conf;
  private String replicationServerHostPort;
  private TopologyCache lastLoadedCache;

  /**
   * Default constructor.
   */
  public Uninstaller()
  {
    super();

    /* Do some initialization required to use the administration framework
     * classes.  Note that this is not done in the installer code because
     * when the basic configuration of the server is performed (using
     * ConfigureDS) this initialization is done.
     */
    DirectoryServer.bootstrapClient();
    //  Bootstrap definition classes.
    try
    {
      ClassLoaderProvider.getInstance().enable();
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error enabling admin framework class loader: "+t,
          t);
    }

    // Switch off class name validation in client.
    ClassPropertyDefinition.setAllowClassValidation(false);

    // Switch off attribute type name validation in client.
    AttributeTypePropertyDefinition.setCheckSchema(false);
  }
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
    UninstallUserData uud = getUninstallUserData();
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
    // This is updated on the method handleTopologyCache
    uud.setUpdateRemoteReplication(false);

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
  public void notifyListeners(Integer ratio, String currentPhaseSummary,
      final String newLogDetail)
  {
    if (runStarted)
    {
      super.notifyListeners(ratio, currentPhaseSummary, newLogDetail);
    }
    else
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          if (startProgressDlg != null)
          {
            if (newLogDetail != null)
            {
              startProgressDetails.append(newLogDetail);
              startProgressDlg.setDetails(startProgressDetails.toString());
            }
          }
        }
      });
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
            return new UninstallData(Installation.getLocal());
          }
          catch (UserDataException uude) {
            throw uude;
          } catch (Throwable t) {
            LOG.log(Level.WARNING, "Error processing task: "+t, t);
            throw new UserDataException(Step.CONFIRM_UNINSTALL,
                    getThrowableMsg("bug-msg", t));
          }
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
            conf = (UninstallData)returnValue;
            if (conf.isADS() && conf.isReplicationServer())
            {
              if (conf.isServerRunning())
              {
                if (qs.displayConfirmation(
                    getMsg("confirm-uninstall-replication-server-running-msg"),
                    getMsg(
                        "confirm-uninstall-replication-server-running-title")))
                {
                  askForAuthenticationAndLaunch(qs);
                }
                else
                {
                  if (qs.displayConfirmation(
                          getMsg("confirm-uninstall-server-running-msg"),
                          getMsg("confirm-uninstall-server-running-title")))
                  {
                    getUserData().setStopServer(true);
                    qs.launch();
                    qs.setCurrentStep(
                        getNextWizardStep(Step.CONFIRM_UNINSTALL));
                  } else {
                    getUserData().setStopServer(false);
                  }
                }
              }
              else
              {
                if (qs.displayConfirmation(
                    getMsg(
                        "confirm-uninstall-replication-server-not-running-msg"),
                    getMsg(
                        "confirm-uninstall-replication-server-not-running-title"
                        )))
                {
                  boolean startWorked = startServer(qs.getDialog().getFrame());
                  if (startWorked)
                  {
                    askForAuthenticationAndLaunch(qs);
                  }
                  else
                  {
                    getUserData().setStopServer(false);
                    if (qs.displayConfirmation(
                        getMsg("confirm-uninstall-server-not-running-msg"),
                        getMsg("confirm-uninstall-server-not-running-title")))
                    {
                      qs.launch();
                      qs.setCurrentStep(
                          getNextWizardStep(Step.CONFIRM_UNINSTALL));
                    }
                  }
                }
                else
                {
                  getUserData().setStopServer(false);
                  if (qs.displayConfirmation(
                      getMsg("confirm-uninstall-server-not-running-msg"),
                      getMsg("confirm-uninstall-server-not-running-title")))
                  {
                    qs.launch();
                    qs.setCurrentStep(
                        getNextWizardStep(Step.CONFIRM_UNINSTALL));
                  }
                }
              }
            }
            else if (!conf.isServerRunning())
            {
              getUserData().setStopServer(false);
              if (qs.displayConfirmation(
                      getMsg("confirm-uninstall-server-not-running-msg"),
                      getMsg("confirm-uninstall-server-not-running-title")))
              {
                qs.launch();
                qs.setCurrentStep(getNextWizardStep(
                    Step.CONFIRM_UNINSTALL));
              }
            } else {
              if (qs.displayConfirmation(
                      getMsg("confirm-uninstall-server-running-msg"),
                      getMsg("confirm-uninstall-server-running-title"))) {
                getUserData().setStopServer(true);
                qs.launch();
                qs.setCurrentStep(getNextWizardStep(
                    Step.CONFIRM_UNINSTALL));
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
    return cliHelper.createUserData(launcher.getArgumentParser(),
        launcher.getArguments(), getTrustManager());
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
    hmSummary.put(UninstallProgressStep.UNCONFIGURING_REPLICATION,
            getFormattedSummary(getMsg("summary-unconfiguring-replication")));
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

    String nonCriticalMsg;
    if (Utils.isCli())
    {
      nonCriticalMsg =
        getMsg("summary-uninstall-finished-with-error-on-remote");
    }
    else
    {
      nonCriticalMsg =
        getMsg("summary-uninstall-finished-with-error-on-remote-cli");
    }
    hmSummary.put(UninstallProgressStep.FINISHED_WITH_ERROR_ON_REMOTE,
            getFormattedWarning(nonCriticalMsg));
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
    hmTime.put(UninstallProgressStep.UNCONFIGURING_REPLICATION, 5);
    hmTime.put(UninstallProgressStep.STOPPING_SERVER, 15);
    hmTime.put(UninstallProgressStep.DISABLING_WINDOWS_SERVICE, 5);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES, 30);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES, 5);
    hmTime.put(UninstallProgressStep.REMOVING_EXTERNAL_REFERENCES, 5);
    hmTime.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, 10);

    int totalTime = 0;
    ArrayList<UninstallProgressStep> steps =
            new ArrayList<UninstallProgressStep>();
    if (getUninstallUserData().getUpdateRemoteReplication()) {
      totalTime += hmTime.get(UninstallProgressStep.UNCONFIGURING_REPLICATION);
      steps.add(UninstallProgressStep.UNCONFIGURING_REPLICATION);
    }
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
    hmRatio.put(UninstallProgressStep.FINISHED_WITH_ERROR_ON_REMOTE, 100);
    hmRatio.put(UninstallProgressStep.FINISHED_WITH_ERROR, 100);
  }

  /**
   * Actually performs the uninstall in this thread.  The thread is blocked.
   */
  public void run() {
    runStarted = true;
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

      if (getUninstallUserData().getUpdateRemoteReplication())
      {
        status = UninstallProgressStep.UNCONFIGURING_REPLICATION;
        removeRemoteServerReferences();
        displaySeparator = true;
      }

      if (getUserData().getStopServer()) {
        status = UninstallProgressStep.STOPPING_SERVER;
        if (displaySeparator) {
          notifyListeners(getTaskSeparator());
        }
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
      if (errorOnRemoteOccurred)
      {
        status = UninstallProgressStep.FINISHED_WITH_ERROR_ON_REMOTE;
      }
      else
      {
        status = UninstallProgressStep.FINISHED_SUCCESSFULLY;
      }
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
              ApplicationReturnCode.ReturnCode.BUG,
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
            UninstallProgressStep.FINISHED_WITH_ERROR
    || getCurrentProgressStep() ==
            UninstallProgressStep.FINISHED_WITH_ERROR_ON_REMOTE;
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
     * This is dependent on the OS so there is no much we can do here.
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
          ApplicationReturnCode.ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
          errMsg, null);
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
   * Returns a ResourceProvider instance.
   * @return a ResourceProvider instance.
   */
  public ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
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
            ApplicationReturnCode.ReturnCode.WINDOWS_SERVICE_ERROR,
                errorMessage, null);
    }
  }

  private UninstallUserData getUninstallUserData() {
    return (UninstallUserData) getUserData();
  }

  /**
   * Tries to start the server and launches a progress dialog.  This method
   * assumes that is being called from the event thread.
   * @return <CODE>true</CODE> if the server could be started and <CODE>
   * false</CODE> otherwise.
   * @param frame the JFrame to be used as parent of the progress dialog.
   */
  private boolean startServer(JFrame frame)
  {
    startProgressDetails = new StringBuffer();
    startProgressDlg = new ProgressDialog(frame);
    startProgressDlg.setSummary(
        getFormattedSummary(getMsg("summary-starting")));
    startProgressDlg.setDetails("");
    startProgressDlg.setCloseButtonEnabled(false);
    final Boolean[] returnValue = new Boolean[] {Boolean.FALSE};
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          new ServerController(Uninstaller.this).startServer();
          final boolean isServerRunning =
            Installation.getLocal().getStatus().isServerRunning();
          returnValue[0] = isServerRunning;
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              if (isServerRunning)
              {
                startProgressDlg.setSummary(getFormattedSuccess(
                    getMsg("summary-start-success")));
              }
              else
              {
               startProgressDlg.setSummary(getFormattedError(
                       getMsg("summary-start-error")));
              }
              startProgressDlg.setCloseButtonEnabled(true);
            }
          });
        }
        catch (Throwable t)
        {
          String msg = getFormattedError(t, true);
          notifyListeners(msg);
        }
      }
    });
    t.start();
    startProgressDlg.pack();
    Utilities.centerOnComponent(startProgressDlg, frame);
    startProgressDlg.setModal(true);
    startProgressDlg.setVisible(true);
    startProgressDlg = null;
    return returnValue[0];
  }

  /**
   * This method displays a login dialog message, asking the user to provide
   * authentication to retrieve information from the ADS and update the
   * remote servers.  Then it tries to connect to the remote servers.
   *
   * @param qs the QuickSetup object.
   */
  private void askForAuthenticationAndLaunch(final QuickSetup qs)
  {
    if (loginDialog == null)
    {
      loginDialog = new LoginDialog(qs.getDialog().getFrame(),
          getTrustManager());
      loginDialog.pack();
    }
    Utilities.centerOnComponent(loginDialog, qs.getDialog().getFrame());
    loginDialog.setModal(true);
    loginDialog.setVisible(true);
    if (!loginDialog.isCancelled())
    {
      final InitialLdapContext ctx = loginDialog.getContext();

      replicationServerHostPort = loginDialog.getHostName() + ":" +
      conf.getReplicationServerPort();

      BackgroundTask worker = new BackgroundTask()
      {
        public Object processBackgroundTask()throws TopologyCacheException
        {
          ADSContext adsContext = new ADSContext(ctx);
          TopologyCache cache = new TopologyCache(adsContext,
              getTrustManager());
          cache.reloadTopology();
          return cache;
        }
        public void backgroundTaskCompleted(Object returnValue,
            Throwable throwable) {
          qs.getDialog().workerFinished();
          if (throwable != null)
          {
            if (throwable instanceof TopologyCacheException)
            {
              qs.displayError(
                  getStringRepresentation((TopologyCacheException)throwable),
                  getMsg("error-title"));
            }
            else
            {
              qs.displayError(
                  getThrowableMsg("bug-msg", null, throwable),
                  getMsg("error-title"));
            }
          }
          else
          {
            TopologyCache cache = (TopologyCache)returnValue;
            handleTopologyCache(qs, cache);
          }
        }
      };

      qs.getDialog().workerStarted();
      worker.startBackgroundTask();
    }
    else
    {
      if (qs.displayConfirmation(
          getMsg("confirm-uninstall-server-running-msg"),
          getMsg("confirm-uninstall-server-running-title")))
      {
        getUserData().setStopServer(true);
        qs.launch();
        qs.setCurrentStep(getNextWizardStep(Step.CONFIRM_UNINSTALL));
      } else {
        getUserData().setStopServer(false);
      }
    }
  }

  /**
   * Method that interacts with the user depending on what errors where
   * encountered in the TopologyCache object.  This method assumes that the
   * TopologyCache has been reloaded.
   * Note: this method assumes that is being called from the event thread.
   * @param qs the QuickSetup object for the application.
   * @param cache the TopologyCache.
   */
  private void handleTopologyCache(QuickSetup qs, TopologyCache cache)
  {
    boolean stopProcessing = false;
    Set<TopologyCacheException> exceptions =
      new HashSet<TopologyCacheException>();
    /* Analyze if we had any exception while loading servers.  For the moment
     * only throw the exception found if the user did not provide the
     * Administrator DN and this caused a problem authenticating in one server
     * or if there is a certificate problem.
     */
    Set<ServerDescriptor> servers = cache.getServers();
    for (ServerDescriptor server : servers)
    {
      TopologyCacheException e = server.getLastException();
      if (e != null)
      {
        exceptions.add(e);
      }
    }
    Set<String> exceptionMsgs = new LinkedHashSet<String>();
    /* Check the exceptions and see if we throw them or not. */
    for (TopologyCacheException e : exceptions)
    {
      if (stopProcessing)
      {
        break;
      }
      switch (e.getType())
      {
      case NOT_GLOBAL_ADMINISTRATOR:
        String errorMsg = getMsg("not-global-administrator-provided");
        qs.displayError(errorMsg, getMsg("error-title"));
        stopProcessing = true;
        break;
      case GENERIC_CREATING_CONNECTION:
        if ((e.getCause() != null) &&
            Utils.isCertificateException(e.getCause()))
        {
          UserDataCertificateException.Type excType;
          ApplicationTrustManager.Cause cause = null;
          if (e.getTrustManager() != null)
          {
            cause = e.getTrustManager().getLastRefusedCause();
          }
          LOG.log(Level.INFO, "Certificate exception cause: "+cause);
          if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
          {
            excType = UserDataCertificateException.Type.NOT_TRUSTED;
          }
          else if (cause == ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
          {
            excType = UserDataCertificateException.Type.HOST_NAME_MISMATCH;
          }
          else
          {
            excType = null;
          }
          if (excType != null)
          {
            String h;
            int p;
            try
            {
              URI uri = new URI(e.getLdapUrl());
              h = uri.getHost();
              p = uri.getPort();
            }
            catch (Throwable t)
            {
              LOG.log(Level.WARNING,
                  "Error parsing ldap url of TopologyCacheException.", t);
              h = getMsg("not-available-label");
              p = -1;
            }
            UserDataCertificateException exc =
              new UserDataCertificateException(Step.REPLICATION_OPTIONS,
                getMsg("certificate-exception", h, String.valueOf(p)),
                e.getCause(), h, p,
                e.getTrustManager().getLastRefusedChain(),
                e.getTrustManager().getLastRefusedAuthType(), excType);
            handleCertificateException(qs, exc, cache);
            stopProcessing = true;
          }
        }
      }
      exceptionMsgs.add(getStringRepresentation(e));
    }
    if (!stopProcessing && (exceptionMsgs.size() > 0))
    {
      String confirmationMsg =
        getMsg("error-reading-registered-servers-confirm",
            Utils.getStringFromCollection(exceptionMsgs, "\n"));
      stopProcessing = !qs.displayConfirmation(confirmationMsg,
          getMsg("confirmation-title"));
    }
    if (!stopProcessing)
    {
      stopProcessing = !qs.displayConfirmation(
          getMsg("confirm-uninstall-server-running-msg"),
          getMsg("confirm-uninstall-server-running-title"));
    }
    if (!stopProcessing)
    {
      // Launch everything
      lastLoadedCache = cache;
      getUninstallUserData().setUpdateRemoteReplication(true);
      getUserData().setStopServer(true);
      qs.launch();
      qs.setCurrentStep(getNextWizardStep(Step.CONFIRM_UNINSTALL));
    }
  }

  /**
   * Displays a dialog asking the user to accept a certificate if the user
   * accepts it, we update the trust manager and call again to the method that
   * handles the action of clicking on "Finish".
   * This method assumes that we are being called from the event thread.
   */
  private void handleCertificateException(final QuickSetup qs,
      UserDataCertificateException ce, final TopologyCache cache)
  {
    CertificateDialog dlg =
      new CertificateDialog(qs.getDialog().getFrame(), ce);
    dlg.pack();
    dlg.setVisible(true);
    if (dlg.isAccepted())
    {
      X509Certificate[] chain = ce.getChain();
      String authType = ce.getAuthType();
      String host = ce.getHost();

      if ((chain != null) && (authType != null) && (host != null))
      {
        LOG.log(Level.INFO, "Accepting certificate presented by host "+host);
        getTrustManager().acceptCertificate(chain, authType, host);
        BackgroundTask worker = new BackgroundTask()
        {
          public Object processBackgroundTask()throws TopologyCacheException
          {
            cache.reloadTopology();
            return cache;
          }
          public void backgroundTaskCompleted(Object returnValue,
              Throwable throwable) {
            qs.getDialog().workerFinished();
            if (throwable != null)
            {
              if (throwable instanceof TopologyCacheException)
              {
                qs.displayError(
                    getStringRepresentation((TopologyCacheException)throwable),
                    getMsg("error-title"));
              }
              else
              {
                qs.displayError(
                    getThrowableMsg("bug-msg", null, throwable),
                    getMsg("error-title"));
              }
            }
            else
            {
              handleTopologyCache(qs, cache);
            }
          }
        };

        qs.getDialog().workerStarted();
        worker.startBackgroundTask();
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
  }

  /**
   * This method updates the replication in the remote servers.  It does not
   * thrown any exception and works in a best effort mode.
   * It also tries to delete the server registration entry from the remote ADS
   * servers.
   */
  private void removeRemoteServerReferences()
  {
    Set<ServerDescriptor> servers = lastLoadedCache.getServers();
    Map<ADSContext.ServerProperty, Object> serverADSProperties = null;
    for (ServerDescriptor server : servers)
    {
      if (isServerToUninstall(server))
      {
        serverADSProperties = server.getAdsProperties();
        break;
      }
    }
    if (serverADSProperties == null)
    {
      LOG.log(Level.WARNING, "The server ADS properties for the server to "+
          "uninstall could not be found.");
    }
    for (ServerDescriptor server : servers)
    {
      if (server.getAdsProperties() != serverADSProperties)
      {
        removeReferences(server, serverADSProperties);
      }
    }
  }

  /**
   * This method updates the replication in the remote server represented by
   * a given ServerProperty object.  It does not thrown any exception and works
   * in a best effort mode.
   * It also tries to delete the server registration entry from the remote ADS
   * servers if the serverADSProperties object passed is not null.
   * @param server the ServerDescriptor object representing the server where
   * we want to remove references to the server that we are trying to uninstall.
   * @param serverADSProperties the Map with the ADS properties of the server
   * that we are trying to uninstall.
   */
  private void removeReferences(ServerDescriptor server,
      Map<ADSContext.ServerProperty, Object> serverADSProperties)
  {
    /* First check if the server must be updated based in the contents of the
     * ServerDescriptor object. */
    boolean hasReferences = false;

    Object v = server.getServerProperties().get(
        ServerDescriptor.ServerProperty.IS_REPLICATION_SERVER);
    if (Boolean.TRUE.equals(v))
    {
      Set replicationServers = (Set<?>)server.getServerProperties().get(
          ServerDescriptor.ServerProperty.EXTERNAL_REPLICATION_SERVERS);
      if (replicationServers != null)
      {
        for (Object o : replicationServers)
        {
          if (replicationServerHostPort.equalsIgnoreCase((String)o))
          {
            hasReferences = true;
            break;
          }
        }
      }
    }

    if (!hasReferences)
    {
      for (ReplicaDescriptor replica : server.getReplicas())
      {
        if (replica.isReplicated())
        {
          for (Object o : replica.getReplicationServers())
          {
            if (replicationServerHostPort.equalsIgnoreCase((String)o))
            {
              hasReferences = true;
              break;
            }
          }
        }
        if (hasReferences)
        {
          break;
        }
      }
    }

    if (hasReferences)
    {
      LOG.log(Level.INFO, "Updating references in: "+ server.getHostPort(true));
      notifyListeners(getFormattedWithPoints(
          getMsg("progress-removing-references", server.getHostPort(true))));
      InitialLdapContext ctx = null;
      try
      {
        String dn =
          ADSContext.getAdministratorDN(loginDialog.getAdministratorUid());
        String pwd = loginDialog.getAdministratorPwd();
        ctx = getRemoteConnection(server, dn, pwd, getTrustManager());
        try
        {
          /*
           * Search for the config to check that it is the directory manager.
           */
          SearchControls searchControls = new SearchControls();
          searchControls.setCountLimit(1);
          searchControls.setSearchScope(
          SearchControls. OBJECT_SCOPE);
          searchControls.setReturningAttributes(new String[] {"*"});
          ctx.search("cn=config", "objectclass=*", searchControls);
        }
        catch (Throwable t)
        {
          System.out.println("FUCK: "+t);
          t.printStackTrace();
        }
        // Update replication servers and domains.  If the domain
        // is an ADS, then remove it from there.
        removeReferences(ctx, server.getHostPort(true), serverADSProperties);

        notifyListeners(getFormattedDone() + getLineBreak());
      }
      catch (ApplicationException ae)
      {
        errorOnRemoteOccurred = true;
        String html = getFormattedError(ae, true);
        notifyListeners(html);
        LOG.log(Level.INFO, "Error updating replication references in: "+
            server.getHostPort(true), ae);
      }
      if (ctx != null)
      {
        try
        {
          ctx.close();
        }
        catch (Throwable t)
        {
        }
      }
    }
  }

  /**
   * This method updates the replication in the remote server using the
   * provided InitialLdapContext.
   * It also tries to delete the server registration entry from the remote ADS
   * servers if the serverADSProperties object passed is not null.
   * @param ctx the connection to the remote server where we want to remove
   * references to the server that we are trying to uninstall.
   * @param serverDisplay an String representation that is used to identify
   * the remote server in the log messages we present to the user.
   * @param serverADSProperties the Map with the ADS properties of the server
   * that we are trying to uninstall.
   * @throws ApplicationException if an error occurs while updating the remote
   * OpenDS server configuration.
   */
  private void removeReferences(InitialLdapContext ctx, String serverDisplay,
      Map<ADSContext.ServerProperty, Object> serverADSProperties)
  throws ApplicationException
  {
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      MultimasterSynchronizationProviderCfgClient sync =
        (MultimasterSynchronizationProviderCfgClient)
        root.getSynchronizationProvider("Multimaster Synchronization");
      if (sync.hasReplicationServer())
      {
        ReplicationServerCfgClient replicationServer =
          sync.getReplicationServer();
        Set<String> replServers = replicationServer.getReplicationServer();
        if (replServers != null)
        {
          String replServer = null;
          for (String o : replServers)
          {
            if (replicationServerHostPort.equalsIgnoreCase(o))
            {
              replServer = o;
              break;
            }
          }
          if (replServer != null)
          {
            LOG.log(Level.INFO, "Updating references in replication server on "+
                serverDisplay+".");
            replServers.remove(replServer);
            if (replServers.size() > 0)
            {
              replicationServer.setReplicationServer(replServers);
              replicationServer.commit();
            }
            else
            {
              sync.removeReplicationServer();
              sync.commit();
            }
          }
        }
      }
      String[] domainNames = sync.listMultimasterDomains();
      if (domainNames != null)
      {
        for (int i=0; i<domainNames.length; i++)
        {
          MultimasterDomainCfgClient domain =
            sync.getMultimasterDomain(domainNames[i]);
          Set<String> replServers = domain.getReplicationServer();
          if (replServers != null)
          {
            String replServer = null;
            for (String o : replServers)
            {
              if (replicationServerHostPort.equalsIgnoreCase(o))
              {
                replServer = o;
                break;
              }
            }
            if (replServer != null)
            {
              LOG.log(Level.INFO, "Updating references in domain " +
                  domain.getReplicationDN()+" on " + serverDisplay + ".");
              replServers.remove(replServer);
              if (replServers.size() > 0)
              {
                domain.setReplicationServer(replServers);
                domain.commit();
              }
              else
              {
                sync.removeMultimasterDomain(domainNames[i]);
                sync.commit();
              }
            }
          }
        }
      }
    }
    catch (ManagedObjectNotFoundException monfe)
    {
      // It does not exist.
      LOG.log(Level.INFO, "No synchronization found on "+ serverDisplay+".",
          monfe);
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Error removing references in replication server on "+
          serverDisplay+": "+t, t);
      String errorMessage = getMsg("error-configuring-remote-generic",
          serverDisplay, t.toString());
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR, errorMessage,
          t);
    }
    ADSContext adsContext = new ADSContext(ctx);

    try
    {
      if (adsContext.hasAdminData() && (serverADSProperties != null))
      {
        adsContext.unregisterServer(serverADSProperties);
      }
    }
    catch (ADSContextException ace)
    {
      if (ace.getError() !=
        ADSContextException.ErrorType.NOT_YET_REGISTERED)
      {
        String[] args = {serverDisplay, ace.toString()};
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            getMsg("remote-ads-exception", args), ace);
      }
      else
      {
        // Nothing to do: this may occur if the new server has been
        // unregistered on another server and the modification has
        // been already propagated by replication.
      }
    }
  }

  /**
   * Tells whether this ServerDescriptor object represents the server that we
   * are trying to uninstall or not.
   * @param server the ServerDescriptor object to analyze.
   * @return <CODE>true</CODE> if the ServerDescriptor object represents the
   * server that we are trying to uninstall and <CODE>false</CODE> otherwise.
   */
  private boolean isServerToUninstall(ServerDescriptor server)
  {
    boolean isServerToUninstall = false;
    String path = (String)server.getAdsProperties().get(
        ADSContext.ServerProperty.INSTANCE_PATH);
    if (path == null)
    {
      // Compare the port of the URL we used.
      try
      {
        String usedUrl = (String)
        loginDialog.getContext().getEnvironment().get(Context.PROVIDER_URL);
        boolean isSecure = usedUrl.toLowerCase().startsWith("ldaps");
        URI uri = new URI(usedUrl);
        int port = uri.getPort();
        ServerDescriptor.ServerProperty property;
        if (isSecure)
        {
          property = ServerDescriptor.ServerProperty.LDAPS_PORT;
        }
        else
        {
          property = ServerDescriptor.ServerProperty.LDAP_PORT;
        }
        ArrayList ports = (ArrayList)server.getServerProperties().get(property);
        isServerToUninstall = ports.contains(port);
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Failing checking the port: "+t, t);
      }
    }
    else
    {
      File f = new File(path);
      isServerToUninstall =
        f.equals(Installation.getLocal().getRootDirectory());
    }

    if (isServerToUninstall)
    {
      // TODO: the host name comparison made here does not necessarily work in
      // all environments...
      String hostName = server.getHostName();
      boolean hostNameEquals = false;
      try
      {
        InetAddress localAddress = InetAddress.getLocalHost();
        InetAddress[] addresses = InetAddress.getAllByName(hostName);
        for (int i=0; i<addresses.length && !hostNameEquals; i++)
        {
          hostNameEquals = localAddress.equals(addresses[i]);
        }
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Failing checking host names: "+t, t);
      }
      isServerToUninstall = hostNameEquals;
    }
    return isServerToUninstall;
  }
}

