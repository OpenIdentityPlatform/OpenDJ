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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.CliApplication;

import org.opends.quicksetup.LicenseFile;
import static org.opends.quicksetup.Installation.*;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.admin.ads.ADSContext;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.WizardStep;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.BuildInformation;
import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.HistoricalRecord;

import org.opends.quicksetup.webstart.WebStartDownloader;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ZipExtractor;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.InProcessServerController;
import org.opends.quicksetup.util.ServerHealthChecker;
import org.opends.quicksetup.util.FileManager;

import org.opends.quicksetup.util.ExternalTools;
import org.opends.quicksetup.util.OperationOutput;
import org.opends.quicksetup.ui.FinishedPanel;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupDialog;
import org.opends.quicksetup.ui.ProgressPanel;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.QuickSetup;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.upgrader.ui.UpgraderReviewPanel;
import org.opends.quicksetup.upgrader.ui.WelcomePanel;
import org.opends.server.tools.JavaPropertiesTool;

import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * QuickSetup application of upgrading the bits of an installation of
 * OpenDS.
 */
public class Upgrader extends GuiApplication implements CliApplication {

  static private final Logger LOG = Logger.getLogger(Upgrader.class.getName());

  /**
   * Passed in from the shell script if the root is known at the time
   * of invocation.
   */
  static private final String SYS_PROP_INSTALL_ROOT =
          "org.opends.quicksetup.upgrader.Root";

  /**
   * Passed in from the shell script if the root is known at the time
   * of invocation.
   */
  static private final String SYS_PROP_INSTANCE_ROOT =
          "org.opends.quicksetup.upgrader.Instance";

  /**
   * If set to true, an error is introduced during the
   * upgrade process for testing.
   */
  static private final String SYS_PROP_CREATE_ERROR =
          "org.opends.quicksetup.upgrader.CreateError";

  /**
   * If set to true, an error is introduced during the
   * upgrade verification process.
   */
  static private final String SYS_PROP_CREATE_VERIFY_ERROR =
          "org.opends.quicksetup.upgrader.VerifyError";

  /**
   * If set to true, if the upgrader encounters an error
   * during upgrade, the abort method that backs out
   * changes is made a no-op leaving the server in the
   * erroneous state.
   */
  static final String SYS_PROP_NO_ABORT =
          "org.opends.quicksetup.upgrader.NoAbort";

  // Root files that will be ignored during backup
  static final String[] ROOT_FILES_TO_IGNORE_DURING_BACKUP = {
          CHANGELOG_PATH_RELATIVE, // changelogDb
          DATABASES_PATH_RELATIVE, // db
          LOGS_PATH_RELATIVE, // logs
          LOCKS_PATH_RELATIVE, // locks
          HISTORY_PATH_RELATIVE, // history
          TMP_PATH_RELATIVE, // tmp
          INSTANCE_LOCATION_PATH_RELATIVE //instance.loc
  };

  // Files that should be located into the install directory
  static final String[] ROOT_FILE_FOR_INSTALL_DIR= {
    "bin",
    "lib",
    "bat",
    "config" + File.separator + "schema",
    "setup",
    "setup.bat",
    "uninstall",
    "uninstall.bat",
    "install.html",
    "install.txt",
    "legal-notices",
    "opends_logo.png",
    "README",
    "upgrade",
    "upgrade.bat",
    "QuickSetup.app",
    "Uninstall.app",
    "tmpl_instance"
  };

  // Files that will be ignored during backup
  static final String[] FILES_TO_IGNORE_DURING_BACKUP = {
          TOOLS_PROPERTIES, // tools.properties
          RELATIVE_JAVA_PROPERTIES_FILE, // java.properties
          ADSContext.getAdminLDIFFile() // admin-backend.ldif
  };

  private ProgressStep currentProgressStep = UpgradeProgressStep.NOT_STARTED;

  /**
   * Assigned if an exception occurs during run().
   */
  private ApplicationException runError = null;

  /**
   * Assigned if a non-fatal error happened during the upgrade that the
   * user needs to be warned about during run().
   */
  private ApplicationException runWarning = null;

  /**
   * Directory where backup is kept in case the upgrade needs reversion.
   */
  private File backupDirectory = null;

  /**
   * ID that uniquely identifieds this invocation of the Upgrader in the
   * historical logs.
   */
  private Long historicalOperationId;

  /**
   * Information on the current build.
   */
  protected BuildInformation currentVersion = null;

  /**
   * Information on the current instance.
   */
  protected BuildInformation currentInstanceVersion = null;

  /**
   * Information on the staged build.
   */
  private BuildInformation stagedVersion = null;

  /**
   * New OpenDS bits.
   */
  private Installation stagedInstallation = null;

  // TODO: remove dead code
  private RemoteBuildManager remoteBuildManager = null;


  /**
   * Represents staged files for upgrade.
   */
  private Stage stage = null;


  /** Set to true if the user decides to close the window while running. */
  private boolean abort = false;

  /**
   * Creates a default instance.
   */
  public Upgrader() {

    // Initialize the logs if necessary
    try {
      if (!QuickSetupLog.isInitialized())
        QuickSetupLog.initLogFileHandler(
                File.createTempFile(
                        UpgradeLauncher.LOG_FILE_PREFIX,
                        QuickSetupLog.LOG_FILE_SUFFIX));
    } catch (IOException e) {
      System.err.println(INFO_ERROR_INITIALIZING_LOG.get());
      e.printStackTrace();
    }

    // Get started on downloading the web start jars
    if (Utils.isWebStart()) {
      initLoader();
    }

    final String installRootFromSystem =
            System.getProperty(SYS_PROP_INSTALL_ROOT);
    final String instanceRootFromSystem =
      System.getProperty(SYS_PROP_INSTANCE_ROOT);
    if (installRootFromSystem != null)
    {
      if (instanceRootFromSystem != null)
      {
        setInstallation(new Installation(installRootFromSystem,
            instanceRootFromSystem));
      } else
      {
        setInstallation(new Installation(installRootFromSystem,
            installRootFromSystem));
      }
    }

  }

  /**
   * {@inheritDoc}
   */
  public Message getFrameTitle() {
    return INFO_FRAME_UPGRADE_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFirstWizardStep() {
    return UpgradeWizardStep.WELCOME;
  }

  /**
   * {@inheritDoc}
   */
  public void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
    //  Set the default button for the frame
    if (step == UpgradeWizardStep.REVIEW) {
      dlg.setFocusOnButton(ButtonName.FINISH);
      dlg.setDefaultButton(ButtonName.FINISH);
    } else if (step == UpgradeWizardStep.WELCOME) {
      dlg.setDefaultButton(ButtonName.NEXT);
      dlg.setFocusOnButton(ButtonName.NEXT);
    } else if ((step == UpgradeWizardStep.PROGRESS) ||
        (step == UpgradeWizardStep.FINISHED)) {
      dlg.setDefaultButton(ButtonName.CLOSE);
      dlg.setFocusOnButton(ButtonName.CLOSE);
    }
  }

  /**
   * Gets a remote build manager that this class can use to find
   * out about and download builds for upgrading.
   *
   * @return RemoteBuildManager to use for builds
   */
  public RemoteBuildManager getRemoteBuildManager() {
    if (remoteBuildManager == null) {
      try {
        String listUrlString =
                System.getProperty("org.opends.quicksetup.upgrader.BuildList");
        if (listUrlString == null) {
          listUrlString = "http://www.opends.org/upgrade-builds";
        }
        URL buildRepo = new URL(listUrlString);

        // See if system properties dictate use of a proxy
        Proxy proxy = null;
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        if (proxyHost != null && proxyPort != null) {
          try {
            SocketAddress addr =
                    new InetSocketAddress(proxyHost, new Integer(proxyPort));
            proxy = new Proxy(Proxy.Type.HTTP, addr);
          } catch (NumberFormatException nfe) {
            LOG.log(Level.INFO, "Illegal proxy port number " + proxyPort);
          }
        }

        remoteBuildManager = new RemoteBuildManager(this, buildRepo, proxy);
      } catch (MalformedURLException e) {
        LOG.log(Level.INFO, "", e);
      }
    }
    return remoteBuildManager;
  }

  /**
   * {@inheritDoc}
   */
  public int getExtraDialogHeight() {
    // Makes dialog height same as QuickSetup
    return 177;
  }

  /**
   * {@inheritDoc}
   */
  public String getInstallationPath() {
    // The upgrader runs from the bits extracted by BuildExtractor
    // in the staging directory.  However
    // we still want the Installation to point at the build being
    // upgraded so the install path reported in [installroot].
    String installationPath =  System.getProperty("INSTALL_ROOT");
    if (installationPath == null)
    {
      String path = Utils.getInstallPathFromClasspath();
      if (path != null)
      {
        File f = new File(path);
        if (f.getParentFile() != null
            && f.getParentFile().getParentFile() != null
            && new File(f.getParentFile().getParentFile(),
                Installation.LOCKS_PATH_RELATIVE).exists())
        {
          installationPath = Utils.getPath(f.getParentFile().getParentFile());
        } else
        {
          installationPath = path;
        }
      }
    }
    return installationPath;
  }

  /**
   * {@inheritDoc}
   */
  public String getInstancePath()
  {
    String installPath = getInstallationPath();
    if (installPath == null)
    {
      return null;
    }

    return Utils.getInstancePathFromClasspath(installPath);
  }

  /**
   * {@inheritDoc}
   */
  public ProgressStep getCurrentProgressStep() {
    return currentProgressStep;
  }

  /**
   * {@inheritDoc}
   */
  public Integer getRatio(ProgressStep step) {
    return ((UpgradeProgressStep) step).getProgress();
  }

  /**
   * {@inheritDoc}
   */
  public Message getSummary(ProgressStep step) {
    Message txt;
    if (step == UpgradeProgressStep.FINISHED) {
      txt = getFinalSuccessMessage();
    } else if (step == UpgradeProgressStep.FINISHED_CANCELED) {
      txt = getFinalCanceledMessage();
    } else if (step == UpgradeProgressStep.FINISHED_WITH_ERRORS) {
      txt = getFinalErrorMessage();
    } else if (step == UpgradeProgressStep.FINISHED_WITH_WARNINGS) {
      txt = getFinalWarningMessage();
    }
    else {
      txt = (((UpgradeProgressStep) step).getSummaryMessage());
    }
    return txt;
  }

  /**
   * {@inheritDoc}
   */
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {
    if ((dlg.getDisplayedStep() == UpgradeWizardStep.PROGRESS) ||
        (dlg.getDisplayedStep() == UpgradeWizardStep.FINISHED)) {
      // Simulate a close button event
      dlg.notifyButtonEvent(ButtonName.CLOSE);
    } else {
      // Simulate a quit button event
      dlg.notifyButtonEvent(ButtonName.QUIT);
    }
  }

  /**
   * Returns the progress message for a given progress step.
   * @param step the progress step.
   * @return the progress message for the provided step.
   */
  private Message getLogMsg(UpgradeProgressStep step) {
    Message txt;
    if (step == UpgradeProgressStep.FINISHED) {
      txt = getFinalSuccessMessage();
    } else if (step == UpgradeProgressStep.FINISHED_CANCELED) {
      txt = getFinalCanceledMessage();
    } else if (step == UpgradeProgressStep.FINISHED_WITH_ERRORS) {
      txt = getFinalErrorMessage();
    } else if (step == UpgradeProgressStep.FINISHED_WITH_WARNINGS) {
      txt = getFinalWarningMessage();
    }
    else
    {
      txt = step.getLogMsg(isVerbose());
    }
    return txt;
  }

  /**
   * {@inheritDoc}
   */
  public ButtonName getInitialFocusButtonName() {
    // TODO
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public Set<? extends WizardStep> getWizardSteps() {
    return Collections.unmodifiableSet(EnumSet.allOf(UpgradeWizardStep.class));
  }

  /**
   * {@inheritDoc}
   */
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step) {
    QuickSetupStepPanel pnl = null;
    if (UpgradeWizardStep.WELCOME.equals(step)) {
      pnl = new WelcomePanel(this);
    } else if (UpgradeWizardStep.REVIEW.equals(step)) {
      pnl = new UpgraderReviewPanel(this);
    } else if (UpgradeWizardStep.PROGRESS.equals(step)) {
      pnl = new ProgressPanel(this);
    } else if (UpgradeWizardStep.FINISHED.equals(step)) {
      pnl = new FinishedPanel(this);
    }
    return pnl;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getNextWizardStep(WizardStep step) {
    WizardStep next = null;
    if (UpgradeWizardStep.WELCOME.equals(step)) {
      next = UpgradeWizardStep.REVIEW;
    } else if (UpgradeWizardStep.REVIEW.equals(step)) {
      next = UpgradeWizardStep.PROGRESS;
    } else if (UpgradeWizardStep.PROGRESS.equals(step)) {
      next = UpgradeWizardStep.FINISHED;
    }
    return next;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getPreviousWizardStep(WizardStep step) {
    WizardStep prev = null;
    if (UpgradeWizardStep.FINISHED.equals(step)) {
      prev = UpgradeWizardStep.PROGRESS;
    } else if (UpgradeWizardStep.PROGRESS.equals(step)) {
      prev = UpgradeWizardStep.REVIEW;
    } else if (UpgradeWizardStep.REVIEW.equals(step)) {
      prev = UpgradeWizardStep.WELCOME;
    }
    return prev;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFinishedStep() {
    return UpgradeWizardStep.FINISHED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canQuit(WizardStep step) {
    return UpgradeWizardStep.WELCOME == step ||
            UpgradeWizardStep.REVIEW == step;
  }

  /**
   * {@inheritDoc}
   */
  public Message getFinishButtonToolTip() {
    return INFO_FINISH_BUTTON_UPGRADE_TOOLTIP.get();
  }

  /**
   * {@inheritDoc}
   */
  public Message getQuitButtonToolTip() {
    return INFO_QUIT_BUTTON_UPGRADE_TOOLTIP.get();
  }

  /**
   * {@inheritDoc}
   */
  public void cancel() {

    // The run() method checks that status of this variable
    // occasionally and aborts the operation if it discovers
    // a 'true' value.
    abort = true;

  }


  /**
   * {@inheritDoc}
   */
  public boolean confirmCancel(QuickSetup qs) {
    return qs.displayConfirmation(
            INFO_CONFIRM_CANCEL_UPGRADE_MSG.get(),
            INFO_CONFIRM_CANCEL_UPGRADE_TITLE.get());
  }

  /**
   * {@inheritDoc}
   */
  public boolean isFinished() {
    return getCurrentProgressStep() ==
            UpgradeProgressStep.FINISHED
            || getCurrentProgressStep() ==
            UpgradeProgressStep.FINISHED_WITH_ERRORS
            || getCurrentProgressStep() ==
            UpgradeProgressStep.FINISHED_WITH_WARNINGS
            || getCurrentProgressStep() ==
            UpgradeProgressStep.FINISHED_CANCELED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isCancellable() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void quitClicked(WizardStep cStep, final QuickSetup qs) {
    if (cStep == UpgradeWizardStep.PROGRESS) {
      throw new IllegalStateException(
              "Cannot click on quit from progress step");
    } else if (isFinished()) {
      qs.quit();
    } else if (qs.displayConfirmation(
            INFO_CONFIRM_QUIT_UPGRADE_MSG.get(),
            INFO_CONFIRM_QUIT_UPGRADE_TITLE.get())) {
      qs.quit();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException {
    List<Message> errorMsgs = new ArrayList<Message>();
    UpgradeUserData uud = getUpgradeUserData();
    if (cStep == UpgradeWizardStep.WELCOME) {

      // User can only select the installation to upgrade
      // in the webstart version of this tool.  Otherwise
      // the fields are readonly.
      if (Utils.isWebStart()) {
        String serverLocationString =
                qs.getFieldStringValue(FieldName.SERVER_TO_UPGRADE_LOCATION);
        if ((serverLocationString == null) ||
                ("".equals(serverLocationString.trim()))) {
          errorMsgs.add(INFO_EMPTY_SERVER_LOCATION.get());
          qs.displayFieldInvalid(FieldName.SERVER_TO_UPGRADE_LOCATION, true);
        } else {
          try {
            File serverLocation = new File(serverLocationString);
            Installation.validateRootDirectory(serverLocation);

            // If we get here the value is acceptable and not null

            Installation currentInstallation = getInstallation();
            if (currentInstallation == null ||
                !serverLocation.equals(getInstallation().getRootDirectory())) {
              LOG.log(Level.INFO,
                      "user changed server root from " +
                      (currentInstallation == null ?
                              "'null'" :
                              currentInstallation.getRootDirectory()) +
                      " to " + serverLocation);
              Installation installation = new Installation(serverLocation,
                  serverLocation);
              setInstallation(installation);
              try
              {
                // Try to see if there is a problem with the build information,
                // we might be trying to do the upgrade with a JVM that is not
                // compatible with the java arguments used by the server
                // scripts (see issue ).
                installation.getBuildInformation(true);
              }
              catch (ApplicationException ae)
              {
                if (ae.getMessageObject().getDescriptor().equals(
                    INFO_ERROR_CREATING_BUILD_INFO_MSG))
                {
                  // This is the message thrown when there was a problem with
                  // the binary.  The details content is on the scripts and not
                  // localized, we can assume that if there is a mention to
                  // OPENDS_JAVA_HOME in the message there is an error with the
                  // script.
                  if (ae.getMessageObject().toString().indexOf(
                      "OPENDS_JAVA_HOME") != -1)
                  {
                    String javaBin = System.getProperty("java.home")+
                    File.separator+
                    "bin"+File.separator+"java";
                    String setJavaHome =
                      installation.getSetJavaHomeFile().getAbsolutePath();
                    String dsJavaProperties =
                      installation.getJavaPropertiesCommandFile().
                      getAbsolutePath();
                    errorMsgs.add(ERR_INVALID_JAVA_ARGS.get(
                        serverLocationString,
                        javaBin,
                        setJavaHome,
                        dsJavaProperties));
                  }
                }
              }
            }

            uud.setServerLocation(serverLocationString);
            qs.displayFieldInvalid(FieldName.SERVER_TO_UPGRADE_LOCATION, false);
          } catch (IllegalArgumentException iae) {
            LOG.log(Level.INFO,
                    "illegal OpenDS installation directory selected", iae);
            errorMsgs.add(INFO_ERROR_INVALID_SERVER_LOCATION.get(
                    serverLocationString));
            qs.displayFieldInvalid(FieldName.SERVER_TO_UPGRADE_LOCATION, true);
          }
        }
      } else {
        // do nothing; all fields are read-only
      }

    } else if (cStep == UpgradeWizardStep.REVIEW) {
      Boolean startServer =
              (Boolean) qs.getFieldValue(FieldName.SERVER_START_UPGRADER);
      uud.setStartServer(startServer);
    }

    if (errorMsgs.size() > 0) {
      throw new UserDataException(UpgradeWizardStep.WELCOME,
              Utils.getMessageFromCollection(errorMsgs, "\n"));
    }

  }

  /**
   * {@inheritDoc}
   */
  public void previousClicked(WizardStep cStep, QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  public boolean finishClicked(final WizardStep cStep, final QuickSetup qs) {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void nextClicked(WizardStep cStep, QuickSetup qs) {
  }

  /**
   * {@inheritDoc}
   */
  public boolean canFinish(WizardStep step) {
    return UpgradeWizardStep.REVIEW.equals(step);
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoBack(WizardStep step) {
    return super.canGoBack(step) && !step.equals(UpgradeWizardStep.PROGRESS)
    && !step.equals(UpgradeWizardStep.FINISHED);
  }

  /**
   * {@inheritDoc}
   */
  public void run() {
    // Reset exception just in case this application is rerun
    // for some reason
    runError = null;

    try {

      if (Utils.isWebStart()) {
        ZipExtractor extractor;
        setCurrentProgressStep(UpgradeProgressStep.DOWNLOADING);
        try {
          LOG.log(Level.INFO, "Waiting for Java Web Start jar download");
          waitForLoader(UpgradeProgressStep.EXTRACTING.getProgress());
          LOG.log(Level.INFO, "Downloaded build file");
          String zipName = WebStartDownloader.getZipFileName();
          InputStream in =
                  Upgrader.class.getClassLoader().getResourceAsStream(zipName);
          extractor = new ZipExtractor(in,
              UpgradeProgressStep.EXTRACTING.getProgress(),
              UpgradeProgressStep.INITIALIZING.getProgress(),
              Utils.getNumberZipEntries(), zipName, this);

        } catch (ApplicationException e) {
          LOG.log(Level.SEVERE, "Error downloading Web Start jars", e);
          throw e;
        }
        notifyListeners(getFormattedDoneWithLineBreak());

        checkAbort();

        try {
          setCurrentProgressStep(UpgradeProgressStep.EXTRACTING);
          if (isVerbose())
          {
            notifyListeners(getLineBreak());
          }
          extractor.extract(getStageDirectory());
          if (!isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
          else
          {
            notifyListeners(getLineBreak());
          }
          LOG.log(Level.INFO, "extraction finished");
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Error extracting build file", e);
          throw e;
        }
      }

      checkAbort();

      // Check license
      if (!LicenseFile.isAlreadyApproved())
      {
        String installRootFromSystem = System.getProperty("INSTALL_ROOT");
        System.setProperty("INSTALL_ROOT", installRootFromSystem
            + File.separator + "tmp" + File.separator + "upgrade");
        if (LicenseFile.exists())
        {
          String licenseString = LicenseFile.getText();
          System.out.println(licenseString);
          if (getUserData().isInteractive())
          {
            // If the user asks for no-prompt. We just display the license text.
            // User doesn't asks for no-prompt. We just display the license text
            // and force to accept it.
            String yes = INFO_LICENSE_CLI_ACCEPT_YES.get().toString();
            String no = INFO_LICENSE_CLI_ACCEPT_NO.get().toString();
            System.out.println(INFO_LICENSE_DETAILS_LABEL.get().toString());

            BufferedReader in = new BufferedReader(new InputStreamReader(
                System.in));
            while (true)
            {
              System.out.print(INFO_LICENSE_CLI_ACCEPT_QUESTION
                  .get(yes, no, no).toString());
              try
              {
                String response = in.readLine();
                if ((response == null)
                    || (response.toLowerCase().equals(no.toLowerCase()))
                    || (response.length() == 0))
                {
                  System.exit(ReturnCode.CANCELLED.getReturnCode());
                }
                else if (response.toLowerCase().equals(yes.toLowerCase()))
                {
                  // create the file
                  LicenseFile.setApproval(true);
                  LicenseFile.createFileLicenseApproved();
                  break;
                }
                else
                {
                  System.out.println(INFO_LICENSE_CLI_ACCEPT_INVALID_RESPONSE
                      .get().toString());
                }
              }
              catch (IOException e)
              {
                System.out.println(INFO_LICENSE_CLI_ACCEPT_INVALID_RESPONSE
                    .get().toString());
              }
            }
          }
        }
        System.setProperty("INSTALL_ROOT", installRootFromSystem);
      }

      if (!Utils.isWebStart())
      {
        // The command-line upgrade has not the option of leaving the server
        // started or stopped, so we must leave it as it was at the beginning of
        // the upgrade.
        getUserData().setStartServer(
          getInstallation().getStatus().isServerRunning());
      }

      try {
        LOG.log(Level.INFO, "initializing upgrade");
        setCurrentProgressStep(UpgradeProgressStep.INITIALIZING);
        initialize();
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "initialization finished");
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "Error initializing upgrader", e);
        throw e;
      }

      checkAbort();

      MigrationManager migration =
              new MigrationManager(getInstallation(),
                      getUpgradeBackupDirectory(),
                      userInteraction());
      try {
        LOG.log(Level.INFO, "checking for schema customizations");
        setCurrentProgressStep(
                UpgradeProgressStep.CALCULATING_SCHEMA_CUSTOMIZATIONS);
        migration.calculateSchemaCustomizations();
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "check for schema customizations finished");
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "Error calculating schema customizations", e);
        throw e;
      }

      checkAbort();

      try {
        LOG.log(Level.INFO, "checking for config customizations");
        setCurrentProgressStep(
                UpgradeProgressStep.CALCULATING_CONFIGURATION_CUSTOMIZATIONS);
        migration.calculateConfigCustomizations();
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "check for config customizations finished");
      } catch (ApplicationException e) {
        LOG.log(Level.INFO,
                "Error calculating config customizations", e);
        throw e;
      }

      checkAbort();

      if (getUpgradeUserData().getPerformDatabaseBackup()) {
        try {
          LOG.log(Level.INFO, "backing up databases");
          setCurrentProgressStep(UpgradeProgressStep.BACKING_UP_DATABASES);
          backupDatabases();
          notifyListeners(getFormattedDoneWithLineBreak());
          LOG.log(Level.INFO, "database backup finished");
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Error backing up databases", e);
          throw e;
        }
      }

      checkAbort();

      try {
        LOG.log(Level.INFO, "backing up filesystem");
        setCurrentProgressStep(UpgradeProgressStep.BACKING_UP_FILESYSTEM);
        backupFilesystem();
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "filesystem backup finished");
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "Error backing up files", e);
        throw e;
      }

      checkAbort();

      try {
        LOG.log(Level.INFO, "upgrading components");
        setCurrentProgressStep(
                UpgradeProgressStep.UPGRADING_COMPONENTS);
        upgradeComponents();
        updateConfigDirectory();
        updateExtensionsDirectory();
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "component upgrade finished");
      } catch (ApplicationException e) {
        LOG.log(Level.INFO,
                "Error upgrading components", e);
        throw e;
      }

      checkAbort();

      //********************************************
      //*  The two steps following this step require
      //*  the server to be started 'in process'.
      // *******************************************
      LOG.log(Level.INFO, "schema customization " +
              (migration.isSchemaCustomized() ? "":"not ") + "present");
      LOG.log(Level.INFO, "config customization " +
              (migration.isConfigurationCustomized() ? "":"not ") + "present");
      if (migration.isSchemaCustomized() ||
              migration.isConfigurationCustomized()) {
        try {
          LOG.log(Level.INFO, "starting server");
          setCurrentProgressStep(
                  UpgradeProgressStep.PREPARING_CUSTOMIZATIONS);
          InProcessServerController ipsc =
                  new InProcessServerController(getInstallation());
          InProcessServerController.disableConnectionHandlers(true);
          InProcessServerController.disableAdminDataSynchronization(true);
          InProcessServerController.disableSynchronization(true);
          ipsc.startServer();
          LOG.log(Level.INFO, "start server finished");
          notifyListeners(getFormattedDoneWithLineBreak());
        } catch (Exception e) {
          LOG.log(Level.INFO,
                  "Error starting server in order to apply custom" +
                          "schema and/or configuration", e);
          throw new ApplicationException(
              ReturnCode.APPLICATION_ERROR,
              INFO_ERROR_STARTING_SERVER.get(), e);
        }

        checkAbort();

        if (migration.isSchemaCustomized()) {
          try {
            LOG.log(Level.INFO, "Applying schema customizations");
            setCurrentProgressStep(
                    UpgradeProgressStep.APPLYING_SCHEMA_CUSTOMIZATIONS);
            migration.migrateSchema();
            notifyListeners(getFormattedDoneWithLineBreak());
            LOG.log(Level.INFO, "custom schema application finished");
          } catch (ApplicationException e) {
            LOG.log(Level.INFO,
                    "Error applying schema customizations", e);
            throw e;
          }
        }

        checkAbort();

        if (migration.isConfigurationCustomized()) {
          try {
            LOG.log(Level.INFO, "Applying config customizations");
            setCurrentProgressStep(
                    UpgradeProgressStep.APPLYING_CONFIGURATION_CUSTOMIZATIONS);
            migration.migrateConfiguration();
            notifyListeners(getFormattedDoneWithLineBreak());
            LOG.log(Level.INFO, "custom config application finished");
          } catch (ApplicationException e) {
            LOG.log(Level.INFO,
                    "Error applying configuration customizations", e);
            throw e;
          }
        }

        checkAbort();

        if (migration.mustMigrateADS())
        {
          try {
            LOG.log(Level.INFO, "Applying registration changes");
            if (isVerbose())
            {
              setCurrentProgressStep(
                  UpgradeProgressStep.APPLYING_ADS_CUSTOMIZATIONS);
            }
            migration.migrateADS(
                getStagedInstallation().getADSBackendFile());
            if (isVerbose())
            {
              notifyListeners(getFormattedDone());
            }
            LOG.log(Level.INFO, "custom registration application finished");
          } catch (ApplicationException e) {
            LOG.log(Level.INFO,
                "Error applying registration customizations", e);
            throw e;
          }
        }

        checkAbort();

        if (migration.mustMigrateToolProperties())
        {
          try {
            LOG.log(Level.INFO, "Applying tools properties");
            migration.migrateToolPropertiesFile(
                getStagedInstallation().getToolsPropertiesFile());
            LOG.log(Level.INFO, "tools properties application finished");
          } catch (ApplicationException e) {
            LOG.log(Level.INFO,
                "Error applying tools properties changes", e);
            throw e;
          }
        }

        if (migration.mustRunDSJavaProperties())
        {
          try {
            LOG.log(Level.INFO, "Upgrading script with java properties");
//          Launch the script
            String propertiesFile = new File(
                getInstallation().getConfigurationDirectory(),
                Installation.DEFAULT_JAVA_PROPERTIES_FILE).getAbsolutePath();
            String setJavaFile =
              getInstallation().getSetJavaHomeFile().getAbsolutePath();
            String[] args =
            {
                "--propertiesFile", propertiesFile,
                "--destinationFile", setJavaFile,
                "--quiet"
            };

            int returnValue = JavaPropertiesTool.mainCLI(args);

            if ((returnValue !=
              JavaPropertiesTool.ErrorReturnCode.SUCCESSFUL.getReturnCode()) &&
              returnValue !=
              JavaPropertiesTool.ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode())
            {
              throw new ApplicationException(ReturnCode.APPLICATION_ERROR,
              ERR_ERROR_CREATING_JAVA_HOME_SCRIPTS.get(returnValue), null);
            }
            LOG.log(Level.INFO, "scripts successfully upgraded");
          } catch (ApplicationException e) {
            LOG.log(Level.INFO,
                "Error upgrading scripts", e);
            throw e;
          }
        }

        checkAbort();

        try {
          LOG.log(Level.INFO, "stopping server");
          // This class imports classes from the server
          if (isVerbose())
          {
            notifyListeners(INFO_PROGRESS_UPGRADE_INTERNAL_STOP.get());
          }
          new InProcessServerController(
                  getInstallation()).stopServer();
          InProcessServerController.disableConnectionHandlers(false);
          if (isVerbose())
          {
            notifyListeners(getFormattedDone());
          }
          LOG.log(Level.INFO, "server stopped");
        } catch (Throwable t) {
          LOG.log(Level.INFO, "Error stopping server", t);
          throw new ApplicationException(ReturnCode.BUG,
                  INFO_ERROR_STOPPING_SERVER.get(), t);
        }
      }

      checkAbort();

      // This allows you to test whether or not he upgrader can successfully
      // abort an upgrade once changes have been made to the installation
      // path's filesystem.
      if ("true".equals(
              System.getProperty(SYS_PROP_CREATE_ERROR))) {
        LOG.log(Level.WARNING, "creating artificial error");
        throw new ApplicationException(
                null, INFO_ERROR_ARTIFICIAL.get(), null);
      }

      List<Message> errors;
      try {
        LOG.log(Level.INFO, "verifying upgrade");
        setCurrentProgressStep(UpgradeProgressStep.VERIFYING);
        Installation installation = getInstallation();
        ServerHealthChecker healthChecker =
                new ServerHealthChecker(installation);
        healthChecker.checkServer();
        errors = healthChecker.getProblemMessages();
      } catch (Exception e) {
        LOG.log(Level.INFO, "error performing server health check", e);
        throw e;
      }

      // For testing
      if ("true".equals(
              System.getProperty(SYS_PROP_CREATE_VERIFY_ERROR))) {
        LOG.log(Level.WARNING, "creating artificial verification error");
        if (errors == null || errors.size() == 0) {
          errors = new ArrayList<Message>();
          errors.add(Message.raw("Artificial verification error for testing"));
        }
      }

      if (errors != null && errors.size() > 0) {
        notifyListeners(getFormattedErrorWithLineBreak());
        Message formattedDetails =
                Utils.listToMessage(errors,
                        Constants.LINE_SEPARATOR, /*bullet=*/"\u2022 ", "");
        ApplicationException ae = new ApplicationException(
                ReturnCode.APPLICATION_ERROR,
                INFO_ERROR_UPGRADED_SERVER_STARTS_WITH_ERRORS.get(
                        Constants.LINE_SEPARATOR + formattedDetails), null);
        UserInteraction ui = userInteraction();
        if (ui != null) {

          // We are about to present the problems with the upgrade to the
          // user and ask if they would like to continue.  Regardless of
          // whether or not they continue at this point, since they will
          // have seen the errors we consider the errors as warnings.
          runWarning = ae;

          // Ask the user if they would like to continue.
          Message cancel = INFO_UPGRADE_VERIFICATION_FAILURE_CANCEL.get();
          if (cancel.equals(ui.confirm(
                  INFO_UPGRADE_VERIFICATION_FAILURE_TITLE.get(),
                  INFO_UPGRADE_VERIFICATION_FAILURE_PROMPT.get(),
                  formattedDetails,
                  INFO_UPGRADE_VERIFICATION_FAILURE_TITLE.get(),
                  UserInteraction.MessageType.ERROR,
                  new Message[]{INFO_CONTINUE_BUTTON_LABEL.get(), cancel},
                  cancel,
                  INFO_UPGRADE_VERIFICATION_FAILURE_VIEW_DETAILS.get()))) {
            // User indicated cancel
            cancel();
            checkAbort();
          } else {
            // User wants to continue;  nothing to do
          }
        } else {
          // We can't ask the user if they want to continue so we
          // just bail on the upgrade by throwing an exception which
          // will cause upgrader to exit unsuccessfully
          throw ae;
        }
      } else {
        notifyListeners(getFormattedDoneWithLineBreak());
      }
      LOG.log(Level.INFO, "upgrade verification complete");

      // Leave the server in the state requested by the user via the
      // checkbox on the review panel.  The upgrade has already been
      // verified at this point to in the unlikely event of an error,
      // we call this a warning instead of an error.
      try {
        ServerController control = new ServerController(getInstallation());
        boolean serverRunning = getInstallation().getStatus().isServerRunning();
        boolean userRequestsStart = getUserData().getStartServer();
        if (userRequestsStart && !serverRunning) {
          try {
            LOG.log(Level.INFO, "starting server");
            setCurrentProgressStep(UpgradeProgressStep.STARTING_SERVER);
            if (isVerbose())
            {
              notifyListeners(getTaskSeparator());
            }
            else
            {
              notifyListeners(getFormattedWithPoints(
                  INFO_PROGRESS_STARTING_NON_VERBOSE.get()));
            }
            int port = getInstallation().getCurrentConfiguration().getPort();
            if (port != -1 && !Utils.canUseAsPort(port)) {
              throw new ApplicationException(
                  ReturnCode.APPLICATION_ERROR,
                      INFO_ERROR_PORT_IN_USE.get(Integer.toString(port)),
                      null);
            }
            control.startServer(!isVerbose());
            if (!isVerbose())
            {
              notifyListeners(getFormattedDoneWithLineBreak());
            }
            else
            {
              notifyListeners(getLineBreak());
            }
          } catch (ApplicationException e) {
            if (isVerbose())
            {
              notifyListeners(getLineBreak());
            }
            notifyListeners(getFormattedErrorWithLineBreak());
            LOG.log(Level.INFO, "error starting server");
            this.runWarning = e;
          }
        } else if (!userRequestsStart && serverRunning) {
          try {
            LOG.log(Level.INFO, "stopping server");
            if (isVerbose())
            {
              notifyListeners(getTaskSeparator());
            }
            else
            {
              notifyListeners(getFormattedWithPoints(
                  INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
            }
            setCurrentProgressStep(UpgradeProgressStep.STOPPING_SERVER);
            control.stopServer(!isVerbose());
            if (!isVerbose())
            {
              notifyListeners(getFormattedDoneWithLineBreak());
            }
            else
            {
              notifyListeners(getLineBreak());
            }
          } catch (ApplicationException e) {
            if (isVerbose())
            {
              notifyListeners(getLineBreak());
            }
            notifyListeners(getFormattedErrorWithLineBreak());
            LOG.log(Level.INFO, "error stopping server");
            this.runWarning = e;
          }
        }
      } catch (IOException ioe) {
        LOG.log(Level.INFO, "error determining if server running");
        this.runWarning = new ApplicationException(
            ReturnCode.TOOL_ERROR,
                INFO_ERROR_SERVER_STATUS.get(), ioe);
      }

    } catch (ApplicationException ae) {

      // We don't consider a  user cancelation exception
      // to be an error.
      if (ae.getType() != ReturnCode.CANCELLED) {
        this.runError = ae;
      } else {
        this.abort = true;
      }

    } catch (Throwable t) {
      this.runError =
              new ApplicationException(ReturnCode.BUG,
                      INFO_BUG_MSG.get(), t);
    } finally {
      try {
        HistoricalRecord.Status status;
        String note = null;
        if (runError == null && !abort) {
          status = HistoricalRecord.Status.SUCCESS;
          backupWindowsUpgradeFile();
        } else {
          if (abort) {
            status = HistoricalRecord.Status.CANCEL;
          } else {
            status = HistoricalRecord.Status.FAILURE;
            note = runError.getLocalizedMessage();
          }

          if (runError != null)
          {
            notifyListeners(getFormattedErrorWithLineBreak());
            Message msg;

            if (runError.getCause() != null)
            {
              msg = getFormattedError(
                  Utils.getThrowableMsg(runError.getMessageObject(),
                      runError.getCause()), true);
            }
            else
            {
              msg = getFormattedError(runError, true);
            }
            notifyListeners(msg);
            if (Utils.isCli())
            {
              notifyListeners(getLineBreak());
              notifyListeners(getLineBreak());
            }
          }
          // Abort the upgrade and put things back like we found it
          LOG.log(Level.INFO, "canceling upgrade");
          ProgressStep lastProgressStep = getCurrentProgressStep();
          setCurrentProgressStep(UpgradeProgressStep.ABORT);
          abort(lastProgressStep);
          notifyListeners(getFormattedDoneWithLineBreak());
          LOG.log(Level.INFO, "cancelation complete");
        }

        LOG.log(Level.INFO, "cleaning up after upgrade");
        setCurrentProgressStep(UpgradeProgressStep.CLEANUP);
        cleanup();
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "clean up complete");


        // Write a record in the log file indicating success/failure
        LOG.log(Level.INFO, "recording upgrade history");
        setCurrentProgressStep(UpgradeProgressStep.RECORDING_HISTORY);
        writeHistoricalRecord(historicalOperationId,
                getCurrentBuildInformation(),
                getStagedBuildInformation(),
                status,
                note);
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "history recorded");
        notifyListeners(
                new MessageBuilder().append(
                  INFO_GENERAL_SEE_FOR_HISTORY.get(
                    Utils.getPath(getInstallation().getHistoryLogFile())))
                .append(formatter.getLineBreak())
                .toMessage());

        try {
          Stage stage = getStage();
          List<Message> stageMessages = stage.getMessages();
          for (Message m : stageMessages) {
            notifyListeners(m);
          }
        } catch (IOException e) {
          LOG.log(Level.INFO, "failed to access stage", e);
        }

      } catch (ApplicationException e) {
        notifyListeners(getFormattedErrorWithLineBreak());
        Message msg;
        if (e.getCause() != null)
        {
          msg = getFormattedError(
              Utils.getThrowableMsg(e.getMessageObject(), e.getCause()), true);
        }
        else
        {
          msg = getFormattedError(e, true);
        }
        notifyListeners(msg);
        if (Utils.isCli())
        {
          notifyListeners(getLineBreak());
        }
        LOG.log(Level.INFO, "Error cleaning up after upgrade.", e);
      }

      // Decide final status based on presence of error

      // WARNING: change this code at your own risk!  The ordering
      // of these statements is important.  There are differences
      // in how the CLI and GUI application's processes exit.
      // Changing the ordering here may result in messages being
      // skipped because the process has already exited by the time
      // processing messages has finished.  Need to resolve these
      // issues.
      if (abort) {
        LOG.log(Level.INFO, "upgrade canceled by user");
        if (!Utils.isCli()) {
          notifyListenersOfLog();
          this.currentProgressStep = UpgradeProgressStep.FINISHED_CANCELED;
          notifyListeners(null);
        } else {
          setCurrentProgressStep(UpgradeProgressStep.FINISHED_CANCELED);
          notifyListeners(getLineBreak());
        }
      } else if (runError != null) {
        LOG.log(Level.INFO, "upgrade completed with errors", runError);
        if (!Utils.isCli()) {
          notifyListenersOfLog();
          this.currentProgressStep = UpgradeProgressStep.FINISHED_WITH_ERRORS;
        } else {
          setCurrentProgressStep(UpgradeProgressStep.FINISHED_WITH_ERRORS);
          notifyListeners(getLineBreak());
        }
      } else if (runWarning != null) {
        LOG.log(Level.INFO, "upgrade completed with warnings");
        Message warningText = runWarning.getMessageObject();

        // By design, the warnings are written as errors to the details section
        // as errors.  Warning markup is used surrounding the main message
        // at the end of progress.
        if (!Utils.isCli()) {
          notifyListenersOfLog();
          this.currentProgressStep = UpgradeProgressStep.FINISHED_WITH_WARNINGS;
          notifyListeners(getFormattedError(warningText, true));
        } else {
          notifyListeners(getFormattedErrorWithLineBreak(warningText, true));
          notifyListeners(getLineBreak());
          setCurrentProgressStep(UpgradeProgressStep.FINISHED_WITH_WARNINGS);
          notifyListeners(getLineBreak());
        }

      } else {
        LOG.log(Level.INFO, "upgrade completed successfully");
        if (!Utils.isCli()) {
          notifyListenersOfLog();
          this.currentProgressStep = UpgradeProgressStep.FINISHED;
          notifyListeners(null);
        } else {
          setCurrentProgressStep(UpgradeProgressStep.FINISHED);
          notifyListeners(getLineBreak());
        }
      }
    }
  }

  /**
   * Checks the value of <code>aborted</code> field and throws an
   * ApplicationException if true.  This indicates that the user has
   * aborted this operation and the process of aborting should begin
   * as soon as possible.
   *
   * @throws ApplicationException thrown if <code>aborted</code>
   */
  public void checkAbort() throws ApplicationException {
    if (abort) throw new ApplicationException(
        ReturnCode.CANCELLED,
            INFO_UPGRADE_CANCELED.get(), null);
  }

  /**
   * Abort this upgrade and repair the installation.
   *
   * @param lastStep ProgressStep indicating how much work we will have to
   *                 do to get the installation back like we left it
   * @throws ApplicationException of something goes wrong
   */
  private void abort(ProgressStep lastStep) throws ApplicationException {

    // This can be used to bypass the aborted upgrade cleanup
    // process so that an autopsy can be performed on the
    // crippled server.
    if ("true".equals(System.getProperty(SYS_PROP_NO_ABORT))) {
      return;
    }

    UpgradeProgressStep lastUpgradeStep = (UpgradeProgressStep) lastStep;
    EnumSet<UpgradeProgressStep> stepsStarted =
            EnumSet.range(UpgradeProgressStep.NOT_STARTED, lastUpgradeStep);

    if (stepsStarted.contains(UpgradeProgressStep.BACKING_UP_FILESYSTEM)) {

      // Files were copied from the stage directory to the current
      // directory.  Repair things by overwriting file in the
      // root with those that were copied to the backup directory
      // during backupFiles()

      File root = getInstallation().getRootDirectory();
      File backupDirectory;
      try {
        backupDirectory = getFilesInstallBackupDirectory();
        FileManager fm = new FileManager();
        boolean restoreError = false;
        for (String fileName : backupDirectory.list()) {
          File f = new File(backupDirectory, fileName);

          // Do our best to restore the filesystem like
          // we found it.  Just report potential problems
          // to the user.
          try {
            fm.move(f, root, null);
          } catch (Throwable t) {
            restoreError = true;
            notifyListeners(INFO_ERROR_RESTORING_FILE.get(Utils.getPath(f),
                    Utils.getPath(root)));
          }
        }
        if (!restoreError) {
          fm.deleteRecursively(backupDirectory);
        }

        if (! instanceAndInstallInSameDir())
        {
          root = getInstallation().getInstanceDirectory();
          backupDirectory = getFilesInstanceBackupDirectory();
          fm = new FileManager();
          for (String fileName : backupDirectory.list()) {
            File f = new File(backupDirectory, fileName);

            // Do our best to restore the filesystem like
            // we found it.  Just report potential problems
            // to the user.
            try {
              fm.move(f, root, null);
            } catch (Throwable t) {
              restoreError = true;
              notifyListeners(INFO_ERROR_RESTORING_FILE.get(Utils.getPath(f),
                      Utils.getPath(root)));
            }
          }
          if (!restoreError) {
            fm.deleteRecursively(backupDirectory);
          }
        }

        // Restart the server after putting the files
        // back like we found them.
        ServerController sc = new ServerController(getInstallation());
        sc.stopServer(true);
        sc.startServer(true);

      } catch (IOException e) {
        LOG.log(Level.INFO, "Error getting backup directory", e);
      }
    }


  }

  /**
   * Returns the directory used to stage files for upgrade or reversion.
   * @return the directory used to stage files for upgrade or reversion
   * @throws IOException if errors occurs while accessing stage files
   * @throws org.opends.quicksetup.ApplicationException
   *     if retrieval of stage files path fails
   */
  protected Stage getStage() throws IOException, ApplicationException {
    if (this.stage == null) {
      this.stage = new Stage(getStageDirectory());
    }
    return this.stage;
  }

  /**
   * Upgrade components.
   * @throws ApplicationException if upgrade fails
   */
  protected void upgradeComponents() throws ApplicationException {
    try {
      Stage stage = getStage();
      Installation installation = getInstallation();
      File root = installation.getRootDirectory();

      if (instanceAndInstallInSameDir())
      {
       stage.move(root, new UpgradeFileFilter(getStageDirectory()));
      }
      else
      {
         stage.move(root, new UpgradeFileFilter(getStageDirectory(),true));
        root = installation.getInstanceDirectory();
        stage.move(root, new UpgradeFileFilter(getStageDirectory(),false));
      }

      // Check if instance.loc exits
      File instanceFile = new File
         (installation.getRootDirectory(),
            Installation.INSTANCE_LOCATION_PATH_RELATIVE);
      if (! instanceFile.exists())
      {
        BufferedWriter instanceLoc =
          new BufferedWriter(new FileWriter(instanceFile));
        instanceLoc.append(
            installation.getInstanceDirectory().getAbsolutePath());
        instanceLoc.close();
      }

      // The bits should now be of the new version.  Have
      // the installation update the build information so
      // that it is correct.
      LOG.log(Level.INFO, "upgraded bits to " +
              installation.getBuildInformation(false));

    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_UPGRADING_COMPONENTS.get(), e);
    }
  }

  private void updateConfigDirectory()
          throws IOException,ApplicationException
  {
    // The config directory may contain files that are needed
    // by the new installation (e.g. SSL config files and tasks)
    File oldInstallConfigDir =
            new File(getFilesInstallBackupDirectory(),
                     Installation.CONFIG_PATH_RELATIVE);
    File oldInstanceConfigDir =
            new File(getFilesInstanceBackupDirectory(),
                     Installation.CONFIG_PATH_RELATIVE);
    File newInstallConfigDir =
            getInstallation().getInstallConfigurationDirectory();
    File newInstanceConfigDir =
      getInstallation().getConfigurationDirectory();
    FileManager fm = new FileManager();

    // Define a filter for files that we don't want to copy over
    // from the old config directory.
    {
      final File oldConfigUpgradeDir = new File(oldInstallConfigDir, "upgrade");
      final File oldConfigSchemaDir = new File(oldInstallConfigDir, "schema");
      FileFilter filter = new FileFilter()
      {
        public boolean accept(File f)
        {
          return !Utils.isDescendant(f, oldConfigUpgradeDir)
              && !Utils.isDescendant(f, oldConfigSchemaDir);
        }
      };

      fm.synchronize(oldInstallConfigDir, newInstallConfigDir, filter);
    }

    {
      final File oldConfigUpgradeDir =
        new File(oldInstanceConfigDir, "upgrade");
      FileFilter filter = new FileFilter()
      {
        public boolean accept(File f)
        {
          return !Utils.isDescendant(f, oldConfigUpgradeDir);
        }
      };

      fm.synchronize(oldInstanceConfigDir, newInstanceConfigDir, filter);
    }
  }

  private void updateExtensionsDirectory()
          throws IOException,ApplicationException
  {
    // Get extensions back
    File savedDir =
            new File(getFilesInstanceBackupDirectory(),
                     Installation.LIBRARIES_PATH_RELATIVE);
    File destDir = getInstallation().getInstanceDirectory();

    FileManager fm = new FileManager();
    fm.copyRecursively(savedDir, destDir);

    // Get classes back
    savedDir =
            new File(getFilesInstanceBackupDirectory(),
                     Installation.CLASSES_PATH_RELATIVE);
    destDir = getInstallation().getInstanceDirectory();

    fm.copyRecursively(savedDir, destDir);

  }

  /**
   * Backup files to be able to revert if upgrdae fails.
   * @throws ApplicationException if backup fails
   */
  protected void backupFilesystem() throws ApplicationException {
    try {
      // Backup first install (potentially everything if install and instance
      //  are in the same dir
      File filesBackupDirectory = getFilesInstallBackupDirectory();
      FileManager fm = new FileManager();
      File root = getInstallation().getRootDirectory();
      FileFilter filter = new UpgradeFileFilter(root);
      for (String fileName : root.list()) {
        File f = new File(root, fileName);

        // Replacing a Windows bat file while it is running with a different
        // version leads to unpredictable behavior so we make a special case
        // here and during the upgrade components step.  This file will only
        // be backed up at the end of the process if everything went fine.
        if (Utils.isWindows() &&
                fileName.equals(Installation.WINDOWS_UPGRADE_FILE_NAME)) {
          continue;
        }

        fm.move(f, filesBackupDirectory, filter);
      }
      if (!instanceAndInstallInSameDir())
      {
        filesBackupDirectory = getFilesInstanceBackupDirectory();
        root = getInstallation().getInstanceDirectory();
        filter = new UpgradeFileFilter(root);
        for (String fileName : root.list())
        {
          File f = new File(root, fileName);

          // Replacing a Windows bat file while it is running with a different
          // version leads to unpredictable behavior so we make a special case
          // here and during the upgrade components step. This file will only
          // be backed up at the end of the process if everything went fine.
          if (Utils.isWindows()
              && fileName.equals(Installation.WINDOWS_UPGRADE_FILE_NAME))
          {
            continue;
          }
          fm.move(f, filesBackupDirectory, filter);
        }
      }
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_BACKUP_FILESYSTEM.get(),
              e);
    }
  }

  /**
   * This method is called at the end of the upgrade process if everything went
   * fine since the reverter requires to have the upgrade file to properly
   * complete (see issue 2784).
   * @throws ApplicationException if there was an error backing up the upgrade
   * file.
   */
  private void backupWindowsUpgradeFile() throws ApplicationException {
    try
    {
      if (Utils.isWindows())
      {
        File filesBackupDirectory = getFilesInstallBackupDirectory();
        FileManager fm = new FileManager();
        File root = getInstallation().getRootDirectory();
        File f = new File(root, Installation.WINDOWS_UPGRADE_FILE_NAME);
        fm.copy(f, filesBackupDirectory);
      }
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_BACKUP_FILESYSTEM.get(),
              e);
    }
  }

  private void backupDatabases() throws ApplicationException {
    try {
      ExternalTools et = new ExternalTools(getInstallation());
      OperationOutput output = et.backup(getUpgradeBackupDirectory());
      int ret = output.getReturnCode();
      if (ret != 0) {
        throw new ApplicationException(
            ReturnCode.TOOL_ERROR,
                INFO_ERROR_BACKUP_DB_TOOL_RETURN_CODE.get(
                        Integer.toString(ret)),
                null);

      }
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      throw new ApplicationException(
          ReturnCode.TOOL_ERROR,
              INFO_ERROR_BACKUP_DB.get(), e);
    }
  }

  /**
   * Cleanup to done executed once upgrade is done.
   * @throws org.opends.quicksetup.ApplicationException
   *    if cleanup fails
   */
  protected void cleanup() throws ApplicationException {
    deleteStagingDirectory();
  }

  private void deleteStagingDirectory() throws ApplicationException {
    File stagingDir = null;
    try {
      stagingDir = getStageDirectory();
      FileManager fm = new FileManager();

      // On Linux at least the deleteOnExit seems not to work very well
      // for directories that contain files, even if they have been marked
      // for deletion upon exit as well.  Note that on Windows there are
      // file locking issues so we mark files for deletion after this JVM exits.
      if (stagingDir.exists()) {
        fm.deleteRecursively(stagingDir, null,
                FileManager.DeletionPolicy.DELETE_ON_EXIT_IF_UNSUCCESSFUL);
      }

    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_DELETING_STAGE_DIRECTORY.get(
                      Utils.getPath(stagingDir)), e);
    }
  }

  /**
   * Implements the initialization phase of the upgrade.
   * @throws ApplicationException if upgrade is not possible
   */
  protected void initialize() throws ApplicationException {
    try {
      BuildInformation fromVersion = getCurrentInstanceBuildInformation();
      BuildInformation toVersion = getStagedBuildInformation();
      if (fromVersion.equals(toVersion))
      {
        throw new ApplicationException(ReturnCode.APPLICATION_ERROR,
            INFO_UPGRADE_ORACLE_SAME_VERSION.get(
                toVersion.toString()), null);
      }
      if (getInstallation().getStatus().isServerRunning()) {
        new ServerController(getInstallation()).stopServer(true);
      }

      this.historicalOperationId =
              writeInitialHistoricalRecord(fromVersion, toVersion);

      insureUpgradability();
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_INITIALIZING_UPGRADE.get(), e);
    }
  }

  /**
   * Given the current information, determines whether or not
   * an upgrade from the current version to the next version
   * is possible.  Upgrading may not be possible due to 'flag
   * day' types of changes to the codebase.
   * @throws org.opends.quicksetup.ApplicationException if upgradability
   *         cannot be insured.
   */
  private void insureUpgradability() throws ApplicationException {
    BuildInformation newVersion;
    currentVersion = getCurrentInstanceBuildInformation();

    try {
      newVersion = getStagedInstallation().getBuildInformation();
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      LOG.log(Level.INFO, "error getting build information for " +
              "staged installation", e);
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_DETERMINING_UPGRADE_BUILD.get(), e);
    }

    UpgradeIssueNotifier uo = new UpgradeIssueNotifier(
            userInteraction(), currentVersion, newVersion);
    uo.notifyUser();
    if (uo.noServerStartFollowingOperation()) {
      // Some issue dictates that we don't try and restart the server
      // after this operation.  It may be that the databases are no
      // longer readable after the upgrade or something equally earth
      // shattering.
      getUserData().setStartServer(false);
    }
  }

  /**
   * Returns the path of the new OpenDS bits.
   * @return the path of the new OpenDS bits.
   * @throws java.io.IOException if an error occurs while accessing the
   *         new bits
   * @throws org.opends.quicksetup.ApplicationException if upgradability
   *         cannot be insured.
   */
  protected Installation getStagedInstallation()
          throws IOException, ApplicationException {
    if (stagedInstallation == null) {
      File stageDir = getStageDirectory();
      try {
        Installation.validateRootDirectory(stageDir);
        stagedInstallation = new Installation(getStageDirectory(),
            getStageDirectory());
      } catch (IllegalArgumentException e) {
        Message msg = INFO_ERROR_BAD_STAGE_DIRECTORY.get(
                Utils.getPath(getStageDirectory()));
        throw ApplicationException.createFileSystemException(msg, e);
      }
    }
    return stagedInstallation;
  }

  /**
   * {@inheritDoc}
   */
  public UserData createUserData() {
    UpgradeUserData uud = new UpgradeUserData();
    String instanceRootFromSystem = System.getProperty(SYS_PROP_INSTALL_ROOT);
    if (instanceRootFromSystem != null) {
      uud.setServerLocation(instanceRootFromSystem);
    }
    return uud;
  }

  /**
   * {@inheritDoc}
   */
  public UserData createUserData(Launcher launcher)
          throws UserDataException {
    return new UpgraderCliHelper((UpgradeLauncher) launcher).
            createUserData(launcher.getArguments());
  }

  /**
   * {@inheritDoc}
   */
  public ApplicationException getRunError() {
    return runError;
  }

  /**
   * {@inheritDoc}
   */
  public ReturnCode getReturnCode() {
    return null;
  }

  private void setCurrentProgressStep(UpgradeProgressStep step) {
    this.currentProgressStep = step;
    int progress = step.getProgress();
    Message msg = getSummary(step);
    Message log = getLogMsg(step);
    if (step.logRequiresPoints(isVerbose()) && (log != null))
    {
      log = getFormattedWithPoints(log);
    }
    notifyListeners(progress, msg, log);
  }

  private Message getFinalSuccessMessage() {
    Message txt;
    String installPath = Utils.getPath(getInstallation().getRootDirectory());
    String newVersion;
    try {
      BuildInformation bi = getInstallation().getBuildInformation();
      if (bi != null) {
        newVersion = bi.toString();
      } else {
        newVersion = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
      }
    } catch (ApplicationException e) {
      newVersion = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
    }
    if (Utils.isCli()) {
      txt = INFO_SUMMARY_UPGRADE_FINISHED_SUCCESSFULLY_CLI.get(
              formatter.getFormattedText(Message.raw(installPath)),
              newVersion);
    } else {
      txt = getFormattedSuccess(
              INFO_SUMMARY_UPGRADE_FINISHED_SUCCESSFULLY.get(
                      formatter.getFormattedText(Message.raw(installPath)),
                      newVersion));
    }
    return txt;
  }

  private Message getFinalCanceledMessage() {
    Message txt;
    if (Utils.isCli()) {
      txt = INFO_SUMMARY_UPGRADE_FINISHED_CANCELED_CLI.get();
    } else {
      txt = getFormattedSuccess(
              INFO_SUMMARY_UPGRADE_FINISHED_CANCELED.get());
    }
    return txt;
  }

  private Message getFinalErrorMessage() {
    Message txt;
    if (Utils.isCli()) {
      txt = INFO_SUMMARY_UPGRADE_FINISHED_WITH_ERRORS_CLI.get();
    } else {
      txt = getFormattedError(
              INFO_SUMMARY_UPGRADE_FINISHED_WITH_ERRORS.get());
    }
    return txt;
  }

  private Message getFinalWarningMessage() {
    Message txt;
    if (Utils.isCli()) {
      txt = INFO_SUMMARY_UPGRADE_FINISHED_WITH_WARNINGS_CLI.get();
    } else {
      txt = getFormattedWarning(
              INFO_SUMMARY_UPGRADE_FINISHED_WITH_WARNINGS.get());
    }
    return txt;
  }

  /**
   * Returns the path of the new OpenDS bits.
   * @return the path of the new OpenDS bits.
   * @throws org.opends.quicksetup.ApplicationException
   *         if retrieval of stage files path fails
   * @throws java.io.IOException if errors occurs while accessing stage files
   */
  protected File getStageDirectory()
          throws ApplicationException, IOException {
    return getInstallation().getTemporaryUpgradeDirectory();
  }

  private UpgradeUserData getUpgradeUserData() {
    return (UpgradeUserData) getUserData();
  }

  private boolean instanceAndInstallInSameDir()
  {
    Installation installation = getInstallation() ;
    File installDir  = installation.getRootDirectory();
    try
    {
      installDir = installDir.getCanonicalFile();
    }
    catch (Exception e) {
      installDir  = installation.getRootDirectory();
    }
    File instanceDir = installation.getInstanceDirectory();
    try
    {
      instanceDir = instanceDir.getCanonicalFile();
    }
    catch (Exception e) {
      instanceDir = installation.getInstanceDirectory();
    }
    return installDir.getAbsolutePath().equals(instanceDir.getAbsolutePath());
  }

  /**
   * Returns the path where to backup instance files.
   * @return the path where to backup instance files.
   * @throws java.io.IOException if retrieval of backup files fails
   */
  protected File getFilesInstanceBackupDirectory() throws IOException
  {
    if (instanceAndInstallInSameDir())
    {
      return getFilesBackupDirectory();
    } else
    {
      return new File(getFilesBackupDirectory(),
          Installation.HISTORY_BACKUP_FILES_DIR_INSTANCE);
    }
  }

  private File getFilesInstallBackupDirectory() throws IOException
  {
    if (instanceAndInstallInSameDir())
    {
      return getFilesBackupDirectory();
    } else
    {
      return new File(getFilesBackupDirectory(),
          Installation.HISTORY_BACKUP_FILES_DIR_INSTALL);
    }
  }

  private File getFilesBackupDirectory() throws IOException {
    File files = new File(getUpgradeBackupDirectory(),
            Installation.HISTORY_BACKUP_FILES_DIR_NAME);
    if (!files.exists()) {
      if (!files.mkdirs()) {
        throw new IOException("error creating files backup directory");
      }
    }

    // Check if instance and instance are in the same dir
    if ( ! instanceAndInstallInSameDir())
    {
      File install = new File(files,
          Installation.HISTORY_BACKUP_FILES_DIR_INSTALL);
      if (!install.exists())
      {
        if (!install.mkdirs())
        {
          throw new IOException("error creating files backup directory");
        }
      }
      File instance = new File(files,
          Installation.HISTORY_BACKUP_FILES_DIR_INSTANCE);
      if (!instance.exists())
      {
        if (!instance.mkdirs())
        {
          throw new IOException("error creating files backup directory");
        }
      }
    }
    return files;
  }

  private File getUpgradeBackupDirectory() throws IOException {
    if (backupDirectory == null) {
      backupDirectory = getInstallation().createHistoryBackupDirectory();
    }
    return backupDirectory;
  }

  /**
   * Returns the BuildInformation of the current OpenDS bits.
   * @return the BuildInformation of the current OpenDS bits.
   */
  private BuildInformation getCurrentBuildInformation() {
    if (this.currentVersion == null) {
      try {
        currentVersion = getInstallation().getBuildInformation();
      } catch (Exception e) {
        LOG.log(Level.INFO, "error trying to determine current version", e);
      }
    }
    return currentVersion;
  }

 /**
   * Returns the BuildInformation of the OpenDS instance.
   * @return the BuildInformation of the OpenDS instance.
   */
  private BuildInformation getCurrentInstanceBuildInformation() {
    if (this.currentInstanceVersion == null) {
      currentInstanceVersion = getInstallation().getInstanceBuildInformation();
    }

    return currentInstanceVersion;
  }


  private BuildInformation getStagedBuildInformation() {
    if (stagedVersion == null) {
      try {
          stagedVersion = getStagedInstallation().getBuildInformation();
      } catch (Exception e) {
        LOG.log(Level.INFO, "error getting build info for staged installation",
                e);
      }
    }
    return stagedVersion;
  }

  }
