/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.guitools.uninstaller;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.guitools.uninstaller.ui.ConfirmUninstallPanel;
import org.opends.guitools.uninstaller.ui.LoginDialog;
import org.opends.quicksetup.*;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.ReplicationDomainCfgClient;
import org.opends.server.admin.std.client.ReplicationServerCfgClient;
import org.opends.server.admin.std.client.ReplicationSynchronizationProviderCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.ClientException;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.quicksetup.Step.*;
import static org.opends.quicksetup.util.Utils.*;
import static org.opends.server.tools.ConfigureWindowsService.*;

/**
 * This class is in charge of performing the uninstallation of Open DS.
 */
public class Uninstaller extends GuiApplication implements CliApplication {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private ProgressStep status = UninstallProgressStep.NOT_STARTED;
  private boolean runStarted;
  private boolean errorOnRemoteOccurred;
  private boolean errorDeletingOccurred;

  private UninstallerArgumentParser parser;

  private Map<ProgressStep, Integer> hmRatio = new HashMap<>();
  private Map<ProgressStep, LocalizableMessage> hmSummary = new HashMap<>();

  private ApplicationException ue;
  private Boolean isWindowsServiceEnabled;
  private UninstallCliHelper cliHelper = new UninstallCliHelper();

  private LoginDialog loginDialog;
  private ProgressDialog startProgressDlg;
  private LocalizableMessageBuilder startProgressDetails = new LocalizableMessageBuilder();
  private UninstallData conf;

  /** Default constructor. */
  public Uninstaller()
  {
    super();

    /* Do some initialization required to use the administration framework
     * classes.  Note that this is not done in the uninstaller code because
     * when the basic configuration of the server is performed (using
     * ConfigureDS) this initialization is done.
     */
    DirectoryServer.bootstrapClient();
    //  Bootstrap definition classes.
    try
    {
      if (!ClassLoaderProvider.getInstance().isEnabled())
      {
        ClassLoaderProvider.getInstance().enable();
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error enabling admin framework class loader: "+t,
          t));
    }

    // Switch off class name validation in client.
    ClassPropertyDefinition.setAllowClassValidation(false);

    // Switch off attribute type name validation in client.
    AttributeTypePropertyDefinition.setCheckSchema(false);

    logger.info(LocalizableMessage.raw("Uninstaller is created."));
  }
  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getFrameTitle() {
    LocalizableMessage defaultVal = INFO_FRAME_UNINSTALL_TITLE.get(DynamicConstants.PRODUCT_NAME);
    return Utils.getCustomizedObject("INFO_FRAME_UNINSTALL_TITLE", defaultVal, LocalizableMessage.class);
  }

  /** {@inheritDoc} */
  @Override
  public UserData createUserData() {
    UninstallUserData data = new UninstallUserData();
    data.setTrustManager(super.getTrustManager());
    return data;
  }

  /** {@inheritDoc} */
  @Override
  public WizardStep getFirstWizardStep() {
    return Step.CONFIRM_UNINSTALL;
  }

  /** {@inheritDoc} */
  @Override
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

  /** {@inheritDoc} */
  @Override
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

  /** {@inheritDoc} */
  @Override
  public WizardStep getFinishedStep() {
    return Step.FINISHED;
  }

  /** {@inheritDoc} */
  @Override
  public boolean finishOnLeft()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean canGoBack(WizardStep step) {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean canGoForward(WizardStep step) {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean canFinish(WizardStep step) {
    return step == Step.CONFIRM_UNINSTALL;
  }

  /**
   * Whether the provided wizard step allow to quit.
   *
   * @param step the wizard step
   * @return true if the provided wizard step allow to quit, false otherwise
   */
  public boolean canQuit(WizardStep step) {
    return step == Step.CONFIRM_UNINSTALL;
  }

  /** {@inheritDoc} */
  @Override
  public void nextClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
      throw new IllegalStateException("Cannot click on next from progress step");
    } else if (cStep == REVIEW) {
      throw new IllegalStateException("Cannot click on next from review step");
    } else if (cStep == FINISHED) {
      throw new IllegalStateException("Cannot click on next from finished step");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void closeClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
        if (isFinished()
            || qs.displayConfirmation(INFO_CONFIRM_CLOSE_UNINSTALL_MSG.get(),
                INFO_CONFIRM_CLOSE_UNINSTALL_TITLE.get()))
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

    Set<String> dbs = new HashSet<>();
    Set<?> s = (Set<?>) qs.getFieldValue(FieldName.EXTERNAL_DB_DIRECTORIES);
    for (Object v : s) {
      dbs.add((String) v);
    }

    Set<String> logs = new HashSet<>();
    s = (Set<?>) qs.getFieldValue(FieldName.EXTERNAL_LOG_FILES);
    for (Object v : s) {
      logs.add((String) v);
    }

    uud.setExternalDbsToRemove(dbs);
    uud.setExternalLogsToRemove(logs);

    if (dbs.isEmpty() &&
            logs.isEmpty() &&
            !uud.getRemoveLibrariesAndTools() &&
            !uud.getRemoveDatabases() &&
            !uud.getRemoveConfigurationAndSchema() &&
            !uud.getRemoveBackups() &&
            !uud.getRemoveLDIFs() &&
            !uud.getRemoveLogs()) {
      throw new UserDataException(Step.CONFIRM_UNINSTALL,
              INFO_NOTHING_SELECTED_TO_UNINSTALL.get());
    }
  }


  /** {@inheritDoc} */
  @Override
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

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getCloseButtonToolTip() {
    return INFO_CLOSE_BUTTON_UNINSTALL_TOOLTIP.get();
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getFinishButtonToolTip() {
    return INFO_FINISH_BUTTON_UNINSTALL_TOOLTIP.get();
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getFinishButtonLabel() {
    return INFO_FINISH_BUTTON_UNINSTALL_LABEL.get();
  }

  /** {@inheritDoc} */
  @Override
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

  /** {@inheritDoc} */
  @Override
  public void notifyListeners(Integer ratio, LocalizableMessage currentPhaseSummary,
      final LocalizableMessage newLogDetail)
  {
    if (runStarted)
    {
      super.notifyListeners(ratio, currentPhaseSummary, newLogDetail);
    }
    else
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          if (startProgressDlg != null && newLogDetail != null)
          {
            startProgressDetails.append(newLogDetail);
            startProgressDlg.setDetails(startProgressDetails.toMessage());
          }
        }
      });
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean finishClicked(final WizardStep cStep, final QuickSetup qs) {
    if (cStep == Step.CONFIRM_UNINSTALL) {
      BackgroundTask<UninstallData> worker =
        new BackgroundTask<UninstallData>() {
        @Override
        public UninstallData processBackgroundTask() throws UserDataException {
          try {
            updateUserUninstallDataForConfirmUninstallPanel(qs);
            return new UninstallData(Installation.getLocal());
          }
          catch (UserDataException uude) {
            throw uude;
          } catch (Throwable t) {
            logger.warn(LocalizableMessage.raw("Error processing task: "+t, t));
            throw new UserDataException(Step.CONFIRM_UNINSTALL,
                    getThrowableMsg(INFO_BUG_MSG.get(), t));
          }
        }

        @Override
        public void backgroundTaskCompleted(UninstallData returnValue,
                                            Throwable throwable) {
          qs.getDialog().workerFinished();
          if (throwable != null) {
            if (throwable instanceof UserDataException)
            {
              qs.displayError(LocalizableMessage.raw(throwable.getLocalizedMessage()),
                    INFO_ERROR_TITLE.get());
            }
            else
            {
              logger.warn(LocalizableMessage.raw("Error processing task: "+throwable,
                  throwable));
              qs.displayError(LocalizableMessage.raw(throwable.toString()),
                      INFO_ERROR_TITLE.get());
            }
          } else {
            conf = returnValue;
            if (conf.isADS() && conf.isReplicationServer())
            {
              if (conf.isServerRunning())
              {
                if (qs.displayConfirmation(
                    INFO_CONFIRM_UNINSTALL_REPLICATION_SERVER_RUNNING_MSG.get(),
                    INFO_CONFIRM_UNINSTALL_REPLICATION_SERVER_RUNNING_TITLE
                            .get()))
                {
                  askForAuthenticationAndLaunch(qs);
                }
                else if (qs.displayConfirmation(
                        INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_MSG.get(),
                        INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_TITLE.get()))
                {
                  getUserData().setStopServer(true);
                  qs.launch();
                  qs.setCurrentStep(getNextWizardStep(Step.CONFIRM_UNINSTALL));
                } else {
                  getUserData().setStopServer(false);
                }
              }
              else if (qs.displayConfirmation(
                  INFO_CONFIRM_UNINSTALL_REPLICATION_SERVER_NOT_RUNNING_MSG.get(),
                  INFO_CONFIRM_UNINSTALL_REPLICATION_SERVER_NOT_RUNNING_TITLE.get()))
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
                      INFO_CONFIRM_UNINSTALL_SERVER_NOT_RUNNING_MSG.get(),
                      INFO_CONFIRM_UNINSTALL_SERVER_NOT_RUNNING_TITLE.get()))
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
                    INFO_CONFIRM_UNINSTALL_SERVER_NOT_RUNNING_MSG.get(),
                    INFO_CONFIRM_UNINSTALL_SERVER_NOT_RUNNING_TITLE.get()))
                {
                  qs.launch();
                  qs.setCurrentStep(
                      getNextWizardStep(Step.CONFIRM_UNINSTALL));
                }
              }
            }
            else if (!conf.isServerRunning())
            {
              getUserData().setStopServer(false);
              if (qs.displayConfirmation(
                      INFO_CONFIRM_UNINSTALL_SERVER_NOT_RUNNING_MSG.get(),
                      INFO_CONFIRM_UNINSTALL_SERVER_NOT_RUNNING_TITLE.get()))
              {
                qs.launch();
                qs.setCurrentStep(getNextWizardStep(Step.CONFIRM_UNINSTALL));
              }
            }
            else if (qs.displayConfirmation(
                    INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_MSG.get(),
                    INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_TITLE.get())) {
              getUserData().setStopServer(true);
              qs.launch();
              qs.setCurrentStep(getNextWizardStep(Step.CONFIRM_UNINSTALL));
            } else {
              getUserData().setStopServer(false);
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

  /** {@inheritDoc} */
  @Override
  public void updateUserData(WizardStep step, QuickSetup qs) {
    // do nothing;
  }

  /** {@inheritDoc} */
  @Override
  public void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
    if (step == Step.CONFIRM_UNINSTALL) {
      dlg.setDefaultButton(ButtonName.FINISH);
      dlg.setFocusOnButton(ButtonName.FINISH);
    } else if (step == PROGRESS || step == FINISHED) {
      dlg.setDefaultButton(ButtonName.CLOSE);
      dlg.setFocusOnButton(ButtonName.CLOSE);
      dlg.setButtonEnabled(ButtonName.CLOSE, false);
    }
  }

  /** {@inheritDoc} */
  @Override
  public UserData createUserData(Launcher launcher) throws UserDataException,
      ApplicationException, ClientException
  {
    parser = (UninstallerArgumentParser) launcher.getArgumentParser();
    return cliHelper.createUserData(parser, launcher.getArguments());
  }

  /** {@inheritDoc} */
  @Override
  public String getInstallationPath() {
    return getInstallPathFromClasspath();
  }

  /** {@inheritDoc} */
  @Override
  public String getInstancePath() {
    return getInstancePathFromInstallPath(getInstallPathFromClasspath());
  }

  /**
   * Returns the ApplicationException that might occur during installation or
   * <CODE>null</CODE> if no exception occurred.
   *
   * @return the ApplicationException that might occur during installation or
   *         <CODE>null</CODE> if no exception occurred.
   */
  @Override
  public ApplicationException getRunError() {
    return ue;
  }

  /** {@inheritDoc} */
  @Override
  public ReturnCode getReturnCode() {
    return null;
  }

  /**
   * Initialize the different map used in this class.
   */
  private void initMaps() {
    hmSummary.put(UninstallProgressStep.NOT_STARTED,
            getFormattedSummary(INFO_SUMMARY_UNINSTALL_NOT_STARTED.get()));
    hmSummary.put(UninstallProgressStep.STOPPING_SERVER,
            getFormattedSummary(INFO_SUMMARY_STOPPING.get()));
    hmSummary.put(UninstallProgressStep.UNCONFIGURING_REPLICATION,
            getFormattedSummary(INFO_SUMMARY_UNCONFIGURING_REPLICATION.get()));
    hmSummary.put(UninstallProgressStep.DISABLING_WINDOWS_SERVICE,
            getFormattedSummary(INFO_SUMMARY_DISABLING_WINDOWS_SERVICE.get()));
    hmSummary.put(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES,
            getFormattedSummary(INFO_SUMMARY_DELETING_EXTERNAL_DB_FILES.get()));
    hmSummary.put(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES,
            getFormattedSummary(
                    INFO_SUMMARY_DELETING_EXTERNAL_LOG_FILES.get()));
    hmSummary.put(UninstallProgressStep.REMOVING_EXTERNAL_REFERENCES,
            getFormattedSummary(
                    INFO_SUMMARY_DELETING_EXTERNAL_REFERENCES.get()));
    hmSummary.put(UninstallProgressStep.DELETING_INSTALLATION_FILES,
            getFormattedSummary(
                    INFO_SUMMARY_DELETING_INSTALLATION_FILES.get()));

    LocalizableMessage successMsg;
    Installation installation = getInstallation();
    String libPath = getPath(installation.getLibrariesDirectory());
    String resourcesPath = getPath(installation.getResourcesDirectory());
    String classesPath = getPath(installation.getClassesDirectory());
    boolean resourcesDefined =
     Utils.directoryExistsAndIsNotEmpty(resourcesPath);
    boolean classesDefined =
      Utils.directoryExistsAndIsNotEmpty(classesPath);
    ArrayList<String> paths = new ArrayList<>();
    paths.add(libPath);
    if (resourcesDefined)
    {
      paths.add(resourcesPath);
    }
    if (classesDefined)
    {
      paths.add(classesPath);
    }
    if (isCli()) {
      if (getUninstallUserData().getRemoveLibrariesAndTools()) {
        String arg;
        if (isWindows()) {
          arg = installation.getUninstallBatFile() + getLineBreak().toString() +
                  getTab() + joinAsString(getLineBreak().toString(), paths);
        } else {
          arg = joinAsString(getLineBreak().toString(), paths);
        }
        successMsg = INFO_SUMMARY_UNINSTALL_FINISHED_SUCCESSFULLY_REMOVE_JARFILES_CLI.get(arg);
      } else {
        successMsg = INFO_SUMMARY_UNINSTALL_FINISHED_SUCCESSFULLY_CLI.get();
      }
    } else if (getUninstallUserData().getRemoveLibrariesAndTools()) {
      String formattedPath =
          addWordBreaks(joinAsString(getLineBreak().toString(), paths), 60, 5);
      successMsg = INFO_SUMMARY_UNINSTALL_FINISHED_SUCCESSFULLY_REMOVE_JARFILES.get(formattedPath);
    } else {
      successMsg = INFO_SUMMARY_UNINSTALL_FINISHED_SUCCESSFULLY.get();
    }
    hmSummary.put(UninstallProgressStep.FINISHED_SUCCESSFULLY,
            getFormattedSuccess(successMsg));

    LocalizableMessage nonCriticalMsg;
    if (!isCli())
    {
      nonCriticalMsg =
        INFO_SUMMARY_UNINSTALL_FINISHED_WITH_ERROR_ON_REMOTE.get();
    }
    else
    {
      nonCriticalMsg =
        INFO_SUMMARY_UNINSTALL_FINISHED_WITH_ERROR_ON_REMOTE_CLI.get();
    }
    hmSummary.put(UninstallProgressStep.FINISHED_WITH_ERROR_ON_REMOTE,
            getFormattedWarning(nonCriticalMsg));
    if (!isCli())
    {
      nonCriticalMsg =
        INFO_SUMMARY_UNINSTALL_FINISHED_WITH_ERROR_DELETING.get();
    }
    else
    {
      nonCriticalMsg =
        INFO_SUMMARY_UNINSTALL_FINISHED_WITH_ERROR_DELETING_CLI.get();
    }
    hmSummary.put(UninstallProgressStep.FINISHED_WITH_ERROR_DELETING,
        getFormattedWarning(nonCriticalMsg));
    hmSummary.put(UninstallProgressStep.FINISHED_WITH_ERROR,
            getFormattedError(
                    INFO_SUMMARY_UNINSTALL_FINISHED_WITH_ERROR.get()));

    /*
    * hmTime contains the relative time that takes for each task to be
    * accomplished. For instance if stopping takes twice the time of
    * deleting files, the value for downloading will be the double of the
    * value for extracting.
    */
    Map<UninstallProgressStep, Integer> hmTime = new HashMap<>();
    hmTime.put(UninstallProgressStep.UNCONFIGURING_REPLICATION, 5);
    hmTime.put(UninstallProgressStep.STOPPING_SERVER, 15);
    hmTime.put(UninstallProgressStep.DISABLING_WINDOWS_SERVICE, 5);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES, 30);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES, 5);
    hmTime.put(UninstallProgressStep.REMOVING_EXTERNAL_REFERENCES, 5);
    hmTime.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, 10);

    int totalTime = 0;
    List<UninstallProgressStep> steps = new ArrayList<>();
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
  @Override
  public void run() {
    runStarted = true;
    logger.info(LocalizableMessage.raw("run of the Uninstaller started"));

    initMaps();
    PrintStream origErr = System.err;
    PrintStream origOut = System.out;
    try {
      PrintStream err = new ErrorPrintStream();
      PrintStream out = new OutputPrintStream();
      if (!isCli()) {
        System.setErr(err);
        System.setOut(out);
      }

      boolean displaySeparator = false;

      logger.info(LocalizableMessage.raw("Update remote replication? "+
          getUninstallUserData().getUpdateRemoteReplication()));
      if (getUninstallUserData().getUpdateRemoteReplication())
      {
        status = UninstallProgressStep.UNCONFIGURING_REPLICATION;
        removeRemoteServerReferences();
        displaySeparator = true;
      }

      logger.info(LocalizableMessage.raw("Stop server? "+getUserData().getStopServer()));
      if (getUserData().getStopServer()) {
        status = UninstallProgressStep.STOPPING_SERVER;
        if (displaySeparator && isVerbose()) {
          notifyListeners(getTaskSeparator());
        }
        if (!isVerbose())
        {
          notifyListeners(getFormattedWithPoints(
              INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
        }
        // In case of uninstall, the server stop has to run locally.
        // In order to bypass the tools.properties mechanism, if any,
        // we systematically add the --noPropertiesFile flag
        // when we run the stop-ds command. This is done
        // by setting the parameter "noPropertiesFile" to 'true'
        // in the following call.
        new ServerController(this).stopServer(!isVerbose(),true);
        if (!isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
        displaySeparator = true;
      }
      logger.info(LocalizableMessage.raw("Is Windows Service Enabled? "+
          isWindowsServiceEnabled()));
      if (isWindowsServiceEnabled()) {
        status = UninstallProgressStep.DISABLING_WINDOWS_SERVICE;
        if (displaySeparator && isVerbose()) {
          notifyListeners(getTaskSeparator());
        }
        disableWindowsService();
        displaySeparator = true;
      }

      Set<String> dbsToDelete = getUninstallUserData().getExternalDbsToRemove();
      if (!dbsToDelete.isEmpty()) {
        status = UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES;
        if (displaySeparator && isVerbose()) {
          notifyListeners(getTaskSeparator());
        }

        try
        {
          deleteExternalDatabaseFiles(dbsToDelete);
          displaySeparator = true;
        }
        catch (ApplicationException ae)
        {
          if (ae.getType() == ReturnCode.FILE_SYSTEM_ACCESS_ERROR)
          {
            errorDeletingOccurred = true;
            LocalizableMessage msg = getFormattedWarning(ae.getMessageObject());
            notifyListeners(msg);
          }
          else
          {
            throw ae;
          }
        }
      }

      Set<String> logsToDelete = getUninstallUserData().getExternalLogsToRemove();
      if (!logsToDelete.isEmpty()) {
        status = UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES;

        if (displaySeparator && isVerbose()) {
          notifyListeners(getTaskSeparator());
        }

        try
        {
          deleteExternalLogFiles(logsToDelete);
          displaySeparator = true;
        }
        catch (ApplicationException ae)
        {
          if (ae.getType() == ReturnCode.FILE_SYSTEM_ACCESS_ERROR)
          {
            errorDeletingOccurred = true;
            LocalizableMessage msg = getFormattedWarning(ae.getMessageObject());
            notifyListeners(msg);
          }
          else
          {
            throw ae;
          }
        }
      }

      UninstallUserData userData = getUninstallUserData();
      boolean somethingToDelete = userData.getRemoveBackups() ||
              userData.getRemoveConfigurationAndSchema() ||
              userData.getRemoveDatabases() ||
              userData.getRemoveLDIFs() ||
              userData.getRemoveLibrariesAndTools() ||
              userData.getRemoveLogs();
      if (displaySeparator && somethingToDelete && isVerbose()) {
        notifyListeners(getTaskSeparator());
      }

      if (somethingToDelete) {
        status = UninstallProgressStep.DELETING_INSTALLATION_FILES;
        try
        {
          deleteInstallationFiles(getRatio(status),
                getRatio(UninstallProgressStep.FINISHED_SUCCESSFULLY));
        }
        catch (ApplicationException ae)
        {
          if (ae.getType() == ReturnCode.FILE_SYSTEM_ACCESS_ERROR)
          {
            errorDeletingOccurred = true;
            LocalizableMessage msg = getFormattedWarning(ae.getMessageObject());
            notifyListeners(msg);
          }
          else
          {
            throw ae;
          }
        }
      }
      if (errorOnRemoteOccurred)
      {
        status = UninstallProgressStep.FINISHED_WITH_ERROR_ON_REMOTE;
      }
      else if (errorDeletingOccurred)
      {
        status = UninstallProgressStep.FINISHED_WITH_ERROR_DELETING;
      }
      else
      {
        status = UninstallProgressStep.FINISHED_SUCCESSFULLY;
      }
      if (isCli()) {
        notifyListeners(new LocalizableMessageBuilder(getLineBreak())
                .append(getLineBreak()).append(getSummary(status))
                .toMessage());
      } else {
        notifyListeners(null);
      }

    } catch (ApplicationException ex) {
      logger.error(LocalizableMessage.raw("Error: "+ex, ex));
      ue = ex;
      status = UninstallProgressStep.FINISHED_WITH_ERROR;
      LocalizableMessage msg = getFormattedError(ex, true);
      notifyListeners(msg);
    }
    catch (Throwable t) {
      logger.error(LocalizableMessage.raw("Error: "+t, t));
      ue = new ApplicationException(
              ReturnCode.BUG,
              getThrowableMsg(INFO_BUG_MSG.get(), t), t);
      status = UninstallProgressStep.FINISHED_WITH_ERROR;
      LocalizableMessage msg = getFormattedError(ue, true);
      notifyListeners(msg);
    }
    if (!isCli()) {
      System.setErr(origErr);
      System.setOut(origOut);
    }
  }

  /** {@inheritDoc} */
  @Override
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
  @Override
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
  @Override
  public LocalizableMessage getSummary(ProgressStep step) {
    return hmSummary.get(step);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isFinished() {
    return getCurrentProgressStep() ==
            UninstallProgressStep.FINISHED_SUCCESSFULLY
    || getCurrentProgressStep() ==
            UninstallProgressStep.FINISHED_WITH_ERROR
    || getCurrentProgressStep() ==
            UninstallProgressStep.FINISHED_WITH_ERROR_ON_REMOTE
    || getCurrentProgressStep() ==
            UninstallProgressStep.FINISHED_WITH_ERROR_DELETING;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCancellable() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void cancel() {
    // do nothing; not cancellable
  }

  /** {@inheritDoc} */
  @Override
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {
    if (dlg.getDisplayedStep() == PROGRESS ||
        dlg.getDisplayedStep() == FINISHED) {
      // Simulate a close button event
      dlg.notifyButtonEvent(ButtonName.CLOSE);
    } else {
      // Simulate a quit button event
      dlg.notifyButtonEvent(ButtonName.QUIT);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ButtonName getInitialFocusButtonName() {
    return ButtonName.FINISH;
  }

  /** {@inheritDoc} */
  @Override
  public Set<? extends WizardStep> getWizardSteps() {
    Set<WizardStep> setSteps = new HashSet<>();
    setSteps.add(Step.CONFIRM_UNINSTALL);
    setSteps.add(Step.PROGRESS);
    setSteps.add(Step.FINISHED);
    return Collections.unmodifiableSet(setSteps);
  }

  /** {@inheritDoc} */
  @Override
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step) {
    if (step == Step.CONFIRM_UNINSTALL) {
      return new ConfirmUninstallPanel(this, installStatus);
    } else if (step == Step.PROGRESS) {
      return new ProgressPanel(this);
    } else if (step == Step.FINISHED) {
      return new FinishedPanel(this);
    }
    return null;
  }

  /**
   * Deletes the external database files specified in the provided Set.
   *
   * @param dbFiles the database directories to be deleted.
   * @throws ApplicationException if something goes wrong.
   */
  private void deleteExternalDatabaseFiles(Set<String> dbFiles)
          throws ApplicationException {
    if (isVerbose())
    {
      notifyListeners(getFormattedProgressWithLineBreak(
            INFO_PROGRESS_DELETING_EXTERNAL_DB_FILES.get()));
    }
    else
    {
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_DELETING_EXTERNAL_DB_FILES_NON_VERBOSE.get()));
    }
    for (String path : dbFiles) {
      deleteRecursively(new File(path));
    }
    if (!isVerbose())
    {
      notifyListeners(getFormattedDoneWithLineBreak());
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
    if (isVerbose())
    {
      notifyListeners(getFormattedProgressWithLineBreak(
          INFO_PROGRESS_DELETING_EXTERNAL_LOG_FILES.get()));
    }
    else
    {
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_DELETING_EXTERNAL_LOG_FILES_NON_VERBOSE.get()));
    }
    for (String path : logFiles) {
      deleteRecursively(new File(path));
    }
    if (!isVerbose())
    {
      notifyListeners(getFormattedDoneWithLineBreak());
    }
  }

  /**
   * Deletes the files under the installation path.
   *
   * @throws ApplicationException if something goes wrong.
   */
  private void deleteInstallationFiles(int minRatio, int maxRatio)
          throws ApplicationException {
    if (isVerbose())
    {
      notifyListeners(getFormattedProgressWithLineBreak(
          INFO_PROGRESS_DELETING_INSTALLATION_FILES.get()));
    }
    else
    {
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_DELETING_INSTALLATION_FILES_NON_VERBOSE.get()));
    }

    String installPath = getInstallPathFromClasspath();
    File installFile = new File(installPath);
    try
    {
      installPath = installFile.getCanonicalPath();
    }
    catch(Exception e)
    {
      installPath = getInstallPathFromClasspath();
    }

    String instancePath =
      Utils.getInstancePathFromInstallPath(installFile.getAbsolutePath());
    File instanceFile = new File(instancePath);
    try
    {
      instancePath = instanceFile.getCanonicalPath();
    } catch (Exception e)
    {
      instancePath =
        Utils.getInstancePathFromInstallPath(installFile.getAbsolutePath());
    }

    InstallationFilesToDeleteFilter filter =
            new InstallationFilesToDeleteFilter();

    File[] installFiles  = installFile.listFiles();
    File[] instanceFiles  = null ;
    if (! installPath.equals(instancePath))
    {
      instanceFiles = new File(instancePath).listFiles();
    }

    File[] rootFiles = null;

    if (installFiles == null)
    {
      rootFiles = new File(instancePath).listFiles();
    }
    else
    if (instanceFiles == null)
    {
      rootFiles = installFiles;
    }
    else
    {
      // both installFiles and instanceFiles are not null
      rootFiles = new File[installFiles.length + instanceFiles.length];
      System.arraycopy(installFiles,  0, rootFiles, 0, installFiles.length);
      System.arraycopy(instanceFiles, 0, rootFiles, installFiles.length,
          instanceFiles.length);
    }

    if (rootFiles != null) {
      /* The following is done to have a moving progress bar when we delete
       * the installation files.
       */
      int totalRatio = 0;
      ArrayList<Integer> cumulatedRatio = new ArrayList<>();
      for (File f : rootFiles) {
        if (filter.accept(f)) {
          Installation installation = getInstallation();
          int relativeRatio;
          if (equalsOrDescendant(f, installation.getLibrariesDirectory())) {
            relativeRatio = 10;
          } else
          if (equalsOrDescendant(f, installation.getBinariesDirectory())) {
            relativeRatio = 5;
          } else
          if (equalsOrDescendant(f, installation.getConfigurationDirectory())) {
            relativeRatio = 5;
          } else
          if (equalsOrDescendant(f, installation.getBackupDirectory())) {
            relativeRatio = 20;
          } else
          if (equalsOrDescendant(f, installation.getLdifDirectory())) {
            relativeRatio = 20;
          } else if (equalsOrDescendant(f, installation.getDatabasesDirectory())) {
            relativeRatio = 50;
          } else
          if (equalsOrDescendant(f, installation.getLogsDirectory())) {
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
      for (File rootFile : rootFiles)
      {
        int beforeRatio = minRatio +
                (it.next() * (maxRatio - minRatio)) / totalRatio;
        hmRatio.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, beforeRatio);
        deleteRecursively(rootFile, filter);
      }
      hmRatio.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, maxRatio);
    }
    if (!isVerbose())
    {
      notifyListeners(getFormattedDone());
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
    File cfile ;
    try
    {
      cfile = file.getCanonicalFile();
    }
    catch (Exception e)
    {
      cfile = file ;
    }
    if (cfile.exists()) {
      if (cfile.isFile()) {
        if (filter != null) {
          if (filter.accept(cfile)) {
            delete(cfile);
          }
        } else {
          delete(cfile);
        }
      } else {
        File[] children = cfile.listFiles();
        if (children != null) {
          for (File element : children)
          {
            deleteRecursively(element, filter);
          }
        }
        if (filter != null) {
          if (filter.accept(cfile)) {
            delete(cfile);
          }
        } else {
          delete(cfile);
        }
      }
    } else {
      // Just tell that the file/directory does not exist.
      notifyListeners(getFormattedWarning(
          INFO_PROGRESS_DELETING_FILE_DOES_NOT_EXIST.get(cfile)));
    }
  }

  /**
   * Deletes the specified file.
   *
   * @param file the file to be deleted.
   * @throws ApplicationException if something goes wrong.
   */
  private void delete(File file) throws ApplicationException {
    boolean isFile = file.isFile();

    if (isVerbose())
    {
      if (isFile) {
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_DELETING_FILE.get(file.getAbsolutePath())));
      } else {
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_DELETING_DIRECTORY.get(file.getAbsolutePath())));
      }
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
      LocalizableMessage errMsg;
      if (isFile) {
        errMsg = INFO_ERROR_DELETING_FILE.get(file.getAbsolutePath());
      } else {
        errMsg = INFO_ERROR_DELETING_DIRECTORY.get(file.getAbsolutePath());
      }
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
          errMsg, null);
    }

    if (isVerbose())
    {
      notifyListeners(getFormattedDoneWithLineBreak());
    }
  }

  private boolean equalsOrDescendant(File file, File directory) {
    return file.equals(directory) || isDescendant(file, directory);
  }

  /**
   * This class is used to get the files that are not binaries.  This is
   * required to know which are the files that can be deleted directly and which
   * not.
   */
  private class InstallationFilesToDeleteFilter implements FileFilter {
    private Installation installation = getInstallation();
    private File quicksetupFile = installation.getQuicksetupJarFile();
    private File openDSFile = installation.getOpenDSJarFile();
    private File librariesFile = installation.getLibrariesDirectory();
    private File resourcesDir = installation.getResourcesDirectory();
    private File classesDir = installation.getClassesDirectory();
    private File uninstallBatFile = installation.getUninstallBatFile();

    private boolean canDeleteResourcesDir =
      !Utils.directoryExistsAndIsNotEmpty(resourcesDir.getAbsolutePath());
    private boolean canDeleteClassesDir =
      !Utils.directoryExistsAndIsNotEmpty(classesDir.getAbsolutePath());


    private File installationPath = installation.getRootDirectory();

    /** {@inheritDoc} */
    @Override
    public boolean accept(File file) {
      UninstallUserData userData = getUninstallUserData();
      boolean[] uData = {
              userData.getRemoveLibrariesAndTools(),
              userData.getRemoveLibrariesAndTools(),
              userData.getRemoveLibrariesAndTools(),
              userData.getRemoveLibrariesAndTools(),
              userData.getRemoveDatabases(),
              userData.getRemoveLogs(),
              userData.getRemoveConfigurationAndSchema(),
              userData.getRemoveBackups(),
              userData.getRemoveLDIFs()
      };

      Installation installation = getInstallation();
      File[] parentFiles  ;
      try {
        File[] tmp  = {
              installation.getLibrariesDirectory().getCanonicalFile(),
              installation.getBinariesDirectory().getCanonicalFile(),
              installation.getResourcesDirectory().getCanonicalFile(),
              installation.getClassesDirectory().getCanonicalFile(),
              installation.getDatabasesDirectory().getCanonicalFile(),
              installation.getLogsDirectory().getCanonicalFile(),
              installation.getConfigurationDirectory().getCanonicalFile(),
              installation.getBackupDirectory().getCanonicalFile(),
              installation.getLdifDirectory().getCanonicalFile()
      };
        parentFiles = tmp ;
      }
      catch (Exception e)
      {
        return true;
      }

      boolean accept =
        !installationPath.equals(file)
        && !equalsOrDescendant(file, librariesFile)
        && (canDeleteClassesDir  || !equalsOrDescendant(file, classesDir))
        && (canDeleteResourcesDir || !equalsOrDescendant(file, resourcesDir))
        && !quicksetupFile.equals(file)
        && !openDSFile.equals(file);

      if (accept && isWindows() && isCli()) {
        accept = !uninstallBatFile.equals(file);
      }

      for (int i = 0; i < uData.length && accept; i++) {
        File parent = parentFiles[i];
        accept &= uData[i] ||
                !equalsOrDescendant(file, parent);
      }

      logger.info(LocalizableMessage.raw("accept for :"+file+" is: "+accept));
      return accept;
    }
  }

  private boolean isWindowsServiceEnabled() {
    if (isWindowsServiceEnabled == null) {
      isWindowsServiceEnabled = serviceState() == SERVICE_STATE_ENABLED;
    }
    return isWindowsServiceEnabled.booleanValue();
  }

  /** {@inheritDoc} */
  @Override
  public ApplicationTrustManager getTrustManager()
  {
    return getUninstallUserData().getTrustManager();
  }

  /**
   * This methods disables this server as a Windows service.
   *
   * @throws ApplicationException if something goes wrong.
   */
  protected void disableWindowsService() throws ApplicationException {
    notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_DISABLING_WINDOWS_SERVICE.get()));
    int code = disableService(System.out, System.err);

    LocalizableMessage errorMessage = INFO_ERROR_DISABLING_WINDOWS_SERVICE.get(
            getInstallationPath());

    switch (code) {
      case SERVICE_DISABLE_SUCCESS:
        break;
      case SERVICE_ALREADY_DISABLED:
        break;
      default:
        throw new ApplicationException(ReturnCode.WINDOWS_SERVICE_ERROR, errorMessage, null);
    }
    notifyListeners(getLineBreak());
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
    startProgressDetails = new LocalizableMessageBuilder();
    startProgressDlg = new ProgressDialog(frame);
    startProgressDlg.setSummary(
        getFormattedSummary(INFO_SUMMARY_STARTING.get()));
    startProgressDlg.setDetails(LocalizableMessage.EMPTY);
    startProgressDlg.setCloseButtonEnabled(false);
    final Boolean[] returnValue = new Boolean[] {Boolean.FALSE};
    Thread t = new Thread(new Runnable()
    {
      @Override
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
            @Override
            public void run()
            {
              if (isServerRunning)
              {
                startProgressDlg.setSummary(getFormattedSuccess(
                    INFO_SUMMARY_START_SUCCESS.get()));
              }
              else
              {
               startProgressDlg.setSummary(getFormattedError(
                       INFO_SUMMARY_START_ERROR.get()));
              }
              startProgressDlg.setCloseButtonEnabled(true);
            }
          });
        }
        catch (Throwable t)
        {
          LocalizableMessage msg = getFormattedError(t, true);
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
          getTrustManager(), getConnectTimeout());
      loginDialog.pack();
    }
    Utilities.centerOnComponent(loginDialog, qs.getDialog().getFrame());
    loginDialog.setModal(true);
    loginDialog.setVisible(true);
    if (!loginDialog.isCanceled())
    {
      getUninstallUserData().setAdminUID(loginDialog.getAdministratorUid());
      getUninstallUserData().setAdminPwd(loginDialog.getAdministratorPwd());
      final InitialLdapContext ctx = loginDialog.getContext();
      try
      {
        getUninstallUserData().setLocalServerUrl(
            (String)ctx.getEnvironment().get(Context.PROVIDER_URL));
      }
      catch (NamingException ne)
      {
        logger.warn(LocalizableMessage.raw("Could not find local server: "+ne, ne));
        getUninstallUserData().setLocalServerUrl("ldap://localhost:389");
      }
      getUninstallUserData().setReplicationServer(
          loginDialog.getHostName() + ":" +
          conf.getReplicationServerPort());
      getUninstallUserData().setReferencedHostName(loginDialog.getHostName());

      BackgroundTask<TopologyCache> worker = new BackgroundTask<TopologyCache>()
      {
        @Override
        public TopologyCache processBackgroundTask() throws Throwable
        {
          logger.info(LocalizableMessage.raw("Loading Topology Cache in askForAuthentication"));
          ADSContext adsContext = new ADSContext(ctx);
          TopologyCache cache = new TopologyCache(adsContext,
              getTrustManager(), getConnectTimeout());
          cache.getFilter().setSearchMonitoringInformation(false);
          cache.reloadTopology();
          return cache;
        }
        @Override
        public void backgroundTaskCompleted(TopologyCache returnValue,
            Throwable throwable) {
          qs.getDialog().workerFinished();
          if (throwable != null)
          {
            logger.warn(LocalizableMessage.raw("Throwable: "+throwable, throwable));
            if (throwable instanceof TopologyCacheException)
            {
              qs.displayError(
                      getMessage(
                              (TopologyCacheException)throwable),
                      INFO_ERROR_TITLE.get());
            }
            else
            {
              qs.displayError(
                  getThrowableMsg(INFO_BUG_MSG.get(), throwable),
                  INFO_ERROR_TITLE.get());
            }
            logger.info(LocalizableMessage.raw("Error was displayed"));
          }
          else
          {
            TopologyCache cache = returnValue;
            handleTopologyCache(qs, cache);
          }
        }
      };

      qs.getDialog().workerStarted();
      worker.startBackgroundTask();
    }
    else if (qs.displayConfirmation(
        INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_MSG.get(),
        INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_TITLE.get()))
    {
      getUserData().setStopServer(true);
      qs.launch();
      qs.setCurrentStep(getNextWizardStep(Step.CONFIRM_UNINSTALL));
    } else {
      getUserData().setStopServer(false);
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
    logger.info(LocalizableMessage.raw("Handling TopologyCache"));
    boolean stopProcessing = false;
    Set<TopologyCacheException> exceptions = new HashSet<>();
    /* Analyze if we had any exception while loading servers.  For the moment
     * only throw the exception found if the user did not provide the
     * Administrator DN and this caused a problem authenticating in one server
     * or if there is a certificate problem.
     */
    for (ServerDescriptor server : cache.getServers())
    {
      TopologyCacheException e = server.getLastException();
      if (e != null)
      {
        exceptions.add(e);
      }
    }
    Set<LocalizableMessage> exceptionMsgs = new LinkedHashSet<>();
    /* Check the exceptions and see if we throw them or not. */
    for (TopologyCacheException e : exceptions)
    {
      logger.info(LocalizableMessage.raw("Analyzing exception: "+e, e));
      if (stopProcessing)
      {
        break;
      }
      switch (e.getType())
      {
      case NOT_GLOBAL_ADMINISTRATOR:
        LocalizableMessage errorMsg = INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get();
        qs.displayError(errorMsg, INFO_ERROR_TITLE.get());
        stopProcessing = true;
        break;
      case GENERIC_CREATING_CONNECTION:
        if (isCertificateException(e.getCause()))
        {
          ApplicationTrustManager.Cause cause = null;
          if (e.getTrustManager() != null)
          {
            cause = e.getTrustManager().getLastRefusedCause();
          }
          logger.info(LocalizableMessage.raw("Certificate exception cause: "+cause));
          UserDataCertificateException.Type excType = getCertificateExceptionType(cause);
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
              logger.warn(LocalizableMessage.raw(
                  "Error parsing ldap url of TopologyCacheException.", t));
              h = INFO_NOT_AVAILABLE_LABEL.get().toString();
              p = -1;
            }
            UserDataCertificateException exc =
              new UserDataCertificateException(Step.REPLICATION_OPTIONS,
                INFO_CERTIFICATE_EXCEPTION.get(h, p),
                e.getCause(), h, p,
                e.getTrustManager().getLastRefusedChain(),
                e.getTrustManager().getLastRefusedAuthType(), excType);
            handleCertificateException(qs, exc, cache);
            stopProcessing = true;
          }
        }
      }
      exceptionMsgs.add(getMessage(e));
    }
    if (!stopProcessing && !exceptionMsgs.isEmpty())
    {
      LocalizableMessage confirmationMsg =
        ERR_UNINSTALL_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.get(
                getMessageFromCollection(exceptionMsgs, "\n"));
      stopProcessing = !qs.displayConfirmation(confirmationMsg,
          INFO_CONFIRMATION_TITLE.get());
    }
    if (!stopProcessing)
    {
      stopProcessing = !qs.displayConfirmation(
          INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_MSG.get(),
          INFO_CONFIRM_UNINSTALL_SERVER_RUNNING_TITLE.get());
    }
    if (!stopProcessing)
    {
      // Launch everything
      getUninstallUserData().setUpdateRemoteReplication(true);
      getUninstallUserData().setRemoteServers(cache.getServers());
      getUserData().setStopServer(true);
      qs.launch();
      qs.setCurrentStep(getNextWizardStep(Step.CONFIRM_UNINSTALL));
    }
  }

  private UserDataCertificateException.Type getCertificateExceptionType(ApplicationTrustManager.Cause cause)
  {
    if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
    {
      return UserDataCertificateException.Type.NOT_TRUSTED;
    }
    else if (cause == ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
    {
      return UserDataCertificateException.Type.HOST_NAME_MISMATCH;
    }
    else
    {
      return null;
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
    if (dlg.getUserAnswer() != CertificateDialog.ReturnType.NOT_ACCEPTED)
    {
      X509Certificate[] chain = ce.getChain();
      String authType = ce.getAuthType();
      String host = ce.getHost();

      if (chain != null && authType != null && host != null)
      {
        logger.info(LocalizableMessage.raw("Accepting certificate presented by host "+host));
        getTrustManager().acceptCertificate(chain, authType, host);
        BackgroundTask<TopologyCache> worker =
          new BackgroundTask<TopologyCache>()
        {
          @Override
          public TopologyCache processBackgroundTask() throws Throwable
          {
            logger.info(LocalizableMessage.raw("Reloading topology"));
            cache.getFilter().setSearchMonitoringInformation(false);
            cache.reloadTopology();
            return cache;
          }
          @Override
          public void backgroundTaskCompleted(TopologyCache returnValue,
              Throwable throwable) {
            qs.getDialog().workerFinished();
            if (throwable != null)
            {
              if (throwable instanceof TopologyCacheException)
              {
                qs.displayError(getMessage((TopologyCacheException)throwable),
                    INFO_ERROR_TITLE.get());
              }
              else
              {
                qs.displayError(
                    getThrowableMsg(INFO_BUG_MSG.get(), throwable),
                    INFO_ERROR_TITLE.get());
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
          logger.warn(LocalizableMessage.raw(
              "The chain is null for the UserDataCertificateException"));
        }
        if (authType == null)
        {
          logger.warn(LocalizableMessage.raw(
              "The auth type is null for the UserDataCertificateException"));
        }
        if (host == null)
        {
          logger.warn(LocalizableMessage.raw(
              "The host is null for the UserDataCertificateException"));
        }
      }
    }
    if (dlg.getUserAnswer() ==
      CertificateDialog.ReturnType.ACCEPTED_PERMANENTLY)
    {
      X509Certificate[] chain = ce.getChain();
      if (chain != null)
      {
        try
        {
          UIKeyStore.acceptCertificate(chain);
        }
        catch (Throwable t)
        {
          logger.warn(LocalizableMessage.raw("Error accepting certificate: "+t, t));
        }
      }
    }
  }

  /**
   * This method updates the replication in the remote servers.  It does
   * throw ApplicationException if we are working on the force on error mode.
   * It also tries to delete the server registration entry from the remote ADS
   * servers.
   * @throws ApplicationException if we are not working on force on error mode
   * and there is an error.
   */
  private void removeRemoteServerReferences() throws ApplicationException
  {
    Set<ServerDescriptor> servers = getUninstallUserData().getRemoteServers();
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
      logger.warn(LocalizableMessage.raw("The server ADS properties for the server to "+
          "uninstall could not be found."));
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
   * a given ServerProperty object.
   * It also tries to delete the server registration entry from the remote ADS
   * servers if the serverADSProperties object passed is not null.
   * @param server the ServerDescriptor object representing the server where
   * we want to remove references to the server that we are trying to uninstall.
   * @param serverADSProperties the Map with the ADS properties of the server
   * that we are trying to uninstall.
   * @throws ApplicationException if we are not working on force on error mode
   * and there is an error.
   */
  private void removeReferences(ServerDescriptor server,
      Map<ADSContext.ServerProperty, Object> serverADSProperties)
  throws ApplicationException
  {
    /* First check if the server must be updated based in the contents of the
     * ServerDescriptor object. */
    boolean hasReferences = false;

    Object v = server.getServerProperties().get(
        ServerDescriptor.ServerProperty.IS_REPLICATION_SERVER);
    if (Boolean.TRUE.equals(v))
    {
      Set<?> replicationServers = (Set<?>)server.getServerProperties().get(
          ServerDescriptor.ServerProperty.EXTERNAL_REPLICATION_SERVERS);
      if (replicationServers != null)
      {
        for (Object o : replicationServers)
        {
          if (getUninstallUserData().getReplicationServer().equalsIgnoreCase(
              (String)o))
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
            if (getUninstallUserData().getReplicationServer().equalsIgnoreCase(
                (String)o))
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

    if (!hasReferences)
    {
      logger.info(LocalizableMessage.raw("No references in: "+ server.getHostPort(true)));
    }
    if (hasReferences)
    {
      logger.info(LocalizableMessage.raw("Updating references in: "+ server.getHostPort(true)));
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_REMOVING_REFERENCES.get(server.getHostPort(true))));
      InitialLdapContext ctx = null;
      try
      {
        String dn = ADSContext.getAdministratorDN(
            getUninstallUserData().getAdminUID());
        String pwd = getUninstallUserData().getAdminPwd();
        ctx = getRemoteConnection(server, dn, pwd, getTrustManager(),
            getConnectTimeout(),
            new LinkedHashSet<PreferredConnection>());

        // Update replication servers and domains.  If the domain
        // is an ADS, then remove it from there.
        removeReferences(ctx, server.getHostPort(true), serverADSProperties);

        notifyListeners(getFormattedDoneWithLineBreak());
      }
      catch (ApplicationException ae)
      {
        errorOnRemoteOccurred = true;
        logger.info(LocalizableMessage.raw("Error updating replication references in: "+
            server.getHostPort(true), ae));

        if (!getUninstallUserData().isForceOnError())
        {
          LocalizableMessage msg =
            ERR_UNINSTALL_ERROR_UPDATING_REMOTE_NO_FORCE.get(
              "--"+
              parser.getSecureArgsList().adminUidArg.getLongIdentifier(),
              "--"+OPTION_LONG_BINDPWD,
              "--"+OPTION_LONG_BINDPWD_FILE,
              "--"+parser.forceOnErrorArg.getLongIdentifier(),
              ae.getMessageObject());
          throw new ApplicationException(ae.getType(), msg, ae);
        }
        else
        {
          LocalizableMessage html = getFormattedError(ae, true);
          notifyListeners(html);
        }
      }
      finally
      {
        StaticUtils.close(ctx);
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
      ReplicationSynchronizationProviderCfgClient sync =
        (ReplicationSynchronizationProviderCfgClient)
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
            if (getUninstallUserData().getReplicationServer().equalsIgnoreCase(
                o))
            {
              replServer = o;
              break;
            }
          }
          if (replServer != null)
          {
            logger.info(LocalizableMessage.raw("Updating references in replication server on "+
                serverDisplay+"."));
            replServers.remove(replServer);
            if (!replServers.isEmpty())
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
      String[] domainNames = sync.listReplicationDomains();
      if (domainNames != null)
      {
        for (String domainName : domainNames)
        {
          ReplicationDomainCfgClient domain =
            sync.getReplicationDomain(domainName);
          Set<String> replServers = domain.getReplicationServer();
          if (replServers != null)
          {
            String replServer = null;
            for (String o : replServers)
            {
              if (getUninstallUserData().getReplicationServer().
                  equalsIgnoreCase(o))
              {
                replServer = o;
                break;
              }
            }
            if (replServer != null)
            {
              logger.info(LocalizableMessage.raw("Updating references in domain " +
                  domain.getBaseDN()+" on " + serverDisplay + "."));
              replServers.remove(replServer);
              if (!replServers.isEmpty())
              {
                domain.setReplicationServer(replServers);
                domain.commit();
              }
              else
              {
                sync.removeReplicationDomain(domainName);
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
      logger.info(LocalizableMessage.raw("No synchronization found on "+ serverDisplay+".",
          monfe));
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw(
          "Error removing references in replication server on "+
          serverDisplay+": "+t, t));
      LocalizableMessage errorMessage = INFO_ERROR_CONFIGURING_REMOTE_GENERIC.get(
              serverDisplay, t);
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR, errorMessage, t);
    }
    ADSContext adsContext = new ADSContext(ctx);

    try
    {
      if (adsContext.hasAdminData() && serverADSProperties != null)
      {
        logger.info(LocalizableMessage.raw("Unregistering server on ADS of server "+
            ConnectionUtils.getHostPort(ctx)+".  Properties: "+
            serverADSProperties));
        adsContext.unregisterServer(serverADSProperties);
      }
    }
    catch (ADSContextException ace)
    {
      if (ace.getError() !=
        ADSContextException.ErrorType.NOT_YET_REGISTERED)
      {
        throw new ApplicationException(
            ReturnCode.CONFIGURATION_ERROR,
            INFO_REMOTE_ADS_EXCEPTION.get(serverDisplay, ace),
            ace);
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
        String usedUrl = getUninstallUserData().getLocalServerUrl();
        boolean isSecure = usedUrl.toLowerCase().startsWith("ldaps");
        URI uri = new URI(usedUrl);
        int port = uri.getPort();
        ServerDescriptor.ServerProperty property;
        if (isSecure)
        {
          property = ServerDescriptor.ServerProperty.ADMIN_PORT;
        }
        else
        {
          property = ServerDescriptor.ServerProperty.LDAP_PORT;
        }
        ArrayList<?> ports =
          (ArrayList<?>)server.getServerProperties().get(property);
        if (ports != null)
        {
          isServerToUninstall = ports.contains(port);
        }
        else
        {
          // This occurs if the instance could not be loaded.
          ADSContext.ServerProperty adsProperty;
          if (isSecure)
          {
            adsProperty = ADSContext.ServerProperty.ADMIN_PORT;
          }
          else
          {
            adsProperty = ADSContext.ServerProperty.LDAP_PORT;
          }
          String v = (String)server.getAdsProperties().get(adsProperty);
          if (v != null)
          {
            isServerToUninstall = v.equals(String.valueOf(port));
          }
        }
      }
      catch (Throwable t)
      {
        logger.warn(LocalizableMessage.raw("Failing checking the port: "+t, t));
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
      boolean hostNameEquals =
        getUninstallUserData().getReferencedHostName().equals(hostName);
      try
      {
        InetAddress localAddress = InetAddress.getLocalHost();
        InetAddress[] addresses = InetAddress.getAllByName(hostName);
        for (int i=0; i<addresses.length && !hostNameEquals; i++)
        {
          hostNameEquals = localAddress.equals(addresses[i]);
        }
        if (!hostNameEquals)
        {
          hostNameEquals =
            localAddress.getHostName().equalsIgnoreCase(hostName) ||
            localAddress.getCanonicalHostName().equalsIgnoreCase(hostName);
        }
      }
      catch (Throwable t)
      {
        logger.warn(LocalizableMessage.raw("Failing checking host names: "+t, t));
      }
      isServerToUninstall = hostNameEquals;
    }
    return isServerToUninstall;
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.
   * @return the timeout to be used to connect in milliseconds.  Returns
   * {@code 0} if there is no timeout.
   */
  private int getConnectTimeout()
  {
    return getUserData().getConnectTimeout();
  }
}

