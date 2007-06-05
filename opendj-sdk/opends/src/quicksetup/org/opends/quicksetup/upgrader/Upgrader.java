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

import org.opends.quicksetup.CliApplication;
import static org.opends.quicksetup.Installation.*;
import org.opends.quicksetup.WizardStep;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.BuildInformation;
import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.webstart.WebStartDownloader;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ZipExtractor;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.InProcessServerController;
import org.opends.quicksetup.util.ServerHealthChecker;
import org.opends.quicksetup.util.FileManager;

import org.opends.quicksetup.util.ExternalTools;
import org.opends.quicksetup.util.OperationOutput;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupDialog;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.ProgressPanel;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.QuickSetup;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.upgrader.ui.UpgraderReviewPanel;
import org.opends.quicksetup.upgrader.ui.WelcomePanel;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * QuickSetup application of ugrading the bits of an installation of
 * OpenDS.
 */
public class Upgrader extends GuiApplication implements CliApplication {

  /**
   * Steps in the Upgrade wizard.
   */
  enum UpgradeWizardStep implements WizardStep {

    WELCOME("welcome-step"),

    REVIEW("review-step"),

    PROGRESS("progress-step");

    private String msgKey;

    private UpgradeWizardStep(String msgKey) {
      this.msgKey = msgKey;
    }

    /**
     * {@inheritDoc}
     */
    public String getMessageKey() {
      return msgKey;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isProgressStep() {
      return this == PROGRESS;
    }

  }

  /**
   * Steps during the upgrade process.
   */
  enum UpgradeProgressStep implements ProgressStep {

    NOT_STARTED("summary-upgrade-not-started", 0),

    DOWNLOADING("summary-upgrade-downloading", 10),

    EXTRACTING("summary-upgrade-extracting", 20),

    INITIALIZING("summary-upgrade-initializing", 30),

    CHECK_SERVER_HEALTH("summary-upgrade-check-server-health", 35),

    CALCULATING_SCHEMA_CUSTOMIZATIONS(
            "summary-upgrade-calculating-schema-customization", 40),

    CALCULATING_CONFIGURATION_CUSTOMIZATIONS(
            "summary-upgrade-calculating-config-customization", 48),

    BACKING_UP_DATABASES("summary-upgrade-backing-up-db", 50),

    BACKING_UP_FILESYSTEM("summary-upgrade-backing-up-files",52),

    UPGRADING_COMPONENTS("summary-upgrade-upgrading-components", 60),

    PREPARING_CUSTOMIZATIONS("summary-upgrade-preparing-customizations", 65),

    APPLYING_SCHEMA_CUSTOMIZATIONS(
            "summary-upgrade-applying-schema-customization", 70),

    APPLYING_CONFIGURATION_CUSTOMIZATIONS(
            "summary-upgrade-applying-config-customization", 75),

    VERIFYING("summary-upgrade-verifying", 80),

    STARTING_SERVER("summary-starting", 90),

    STOPPING_SERVER("summary-stopping", 90),

    RECORDING_HISTORY("summary-upgrade-history", 97),

    CLEANUP("summary-upgrade-cleanup", 99),

    ABORT("summary-upgrade-abort", 99),

    FINISHED_WITH_ERRORS("summary-upgrade-finished-with-errors", 100),

    FINISHED_WITH_WARNINGS("summary-upgrade-finished-with-warnings", 100),

    FINISHED_CANCELED("summary-upgrade-finished-canceled", 100),

    FINISHED("summary-upgrade-finished-successfully", 100);

    private String summaryMsgKey;
    private int progress;

    private UpgradeProgressStep(String summaryMsgKey, int progress) {
      this.summaryMsgKey = summaryMsgKey;
      this.progress = progress;
    }

    /**
     * Return a key for access a summary message.
     *
     * @return String representing key for access summary in resource bundle
     */
    public String getSummaryMesssageKey() {
      return summaryMsgKey;
    }

    /**
     * Gets the amount of progress to show in the progress meter for this step.
     * @return int representing progress
     */
    public int getProgress() {
      return this.progress;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isLast() {
      return this == FINISHED ||
              this == FINISHED_WITH_ERRORS ||
              this == FINISHED_WITH_WARNINGS ||
              this == FINISHED_CANCELED;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isError() {
      return this == FINISHED_WITH_ERRORS;
    }
  }

  static private final Logger LOG = Logger.getLogger(Upgrader.class.getName());

  /**
   * Passed in from the shell script if the root is known at the time
   * of invocation.
   */
  static private final String SYS_PROP_INSTALL_ROOT =
          "org.opends.quicksetup.upgrader.Root";

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
  static private final String SYS_PROP_NO_ABORT =
          "org.opends.quicksetup.upgrader.NoAbort";

  // Root files that will be ignored during backup
  static private final String[] ROOT_FILES_TO_IGNORE_DURING_BACKUP = {
          CHANGELOG_PATH_RELATIVE, // changelogDb
          DATABASES_PATH_RELATIVE, // db
          LOGS_PATH_RELATIVE, // logs
          LOCKS_PATH_RELATIVE, // locks
          HISTORY_PATH_RELATIVE, // history
          TMP_PATH_RELATIVE // tmp
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
   * Helps with CLI specific tasks.
   */
  private UpgraderCliHelper cliHelper = null;

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
  private BuildInformation currentVersion = null;

  /**
   * Information on the staged build.
   */
  private BuildInformation stagedVersion = null;

  /**
   * New OpenDS bits.
   */
  private Installation stagedInstallation = null;

  private RemoteBuildManager remoteBuildManager = null;

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
                        UpgradeLauncher.LOG_FILE_SUFFIX));
    } catch (IOException e) {
      System.err.println("Failed to initialize log");
    }

    // Get started on downloading the web start jars
    if (Utils.isWebStart()) {
      initLoader();
    }

    final String instanceRootFromSystem =
            System.getProperty(SYS_PROP_INSTALL_ROOT);
    if (instanceRootFromSystem != null) {
      setInstallation(new Installation(instanceRootFromSystem));
    }

  }

  /**
   * {@inheritDoc}
   */
  public String getFrameTitle() {
    return getMsg("frame-upgrade-title");
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
  protected void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
    // TODO
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
    return UIFactory.EXTRA_DIALOG_HEIGHT;
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstallationPath() {
    // The upgrader runs from the bits extracted by BuildExtractor
    // in the staging directory.  So 'stagePath' below will point
    // to the staging directory [installroot]/tmp/upgrade.  However
    // we still want the Installation to point at the build being
    // upgraded so the install path reported in [installroot].

    String installationPath = null;
    String path = Utils.getInstallPathFromClasspath();
    if (path != null) {
      File f = new File(path);
      if (f.getParentFile() != null &&
              f.getParentFile().getParentFile() != null &&
              new File(f.getParentFile().getParentFile(),
                      Installation.LOCKS_PATH_RELATIVE).exists()) {
        installationPath = Utils.getPath(f.getParentFile().getParentFile());
      } else {
        installationPath = path;
      }
    }
    return installationPath;
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
  public String getSummary(ProgressStep step) {
    String txt = null;
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
      txt = getMsg(((UpgradeProgressStep) step).getSummaryMesssageKey());
    }
    return txt;
  }

  /**
   * {@inheritDoc}
   */
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {
    // TODO
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
    }
    return next;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getPreviousWizardStep(WizardStep step) {
    WizardStep prev = null;
    if (UpgradeWizardStep.PROGRESS.equals(step)) {
      prev = UpgradeWizardStep.REVIEW;
    } else if (UpgradeWizardStep.REVIEW.equals(step)) {
      prev = UpgradeWizardStep.WELCOME;
    }
    return prev;
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
  public String getFinishButtonToolTipKey() {
    return "finish-button-upgrade-tooltip";
  }

  /**
   * {@inheritDoc}
   */
  public String getQuitButtonToolTipKey() {
    return "quit-button-upgrade-tooltip";
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
            getMsg("confirm-cancel-upgrade-msg"),
            getMsg("confirm-cancel-upgrade-title"));
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
    } else if (qs.displayConfirmation(getMsg("confirm-quit-upgrade-msg"),
            getMsg("confirm-quit-upgrade-title"))) {
      qs.quit();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException {
    List<String> errorMsgs = new ArrayList<String>();
    UpgradeUserData uud = getUpgradeUserData();
    if (cStep == UpgradeWizardStep.WELCOME) {

      // User can only select the installation to upgrade
      // in the webstart version of this tool.  Otherwise
      // the fields are readonly.
      if (Utils.isWebStart()) {
        String serverLocationString =
                qs.getFieldStringValue(FieldName.SERVER_LOCATION);
        if ((serverLocationString == null) ||
                ("".equals(serverLocationString.trim()))) {
          errorMsgs.add(getMsg("empty-server-location"));
          qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
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
              Installation installation = new Installation(serverLocation);
              setInstallation(installation);
            }

            uud.setServerLocation(serverLocationString);
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, false);
          } catch (IllegalArgumentException iae) {
            LOG.log(Level.INFO,
                    "illegal OpenDS installation directory selected", iae);
            errorMsgs.add(getMsg("error-invalid-server-location",
                    serverLocationString));
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
          }
        }
      } else {
        // do nothing; all fields are read-only
      }

    } else if (cStep == UpgradeWizardStep.REVIEW) {
      Boolean startServer =
              (Boolean) qs.getFieldValue(FieldName.SERVER_START);
      uud.setStartServer(startServer);
    }

    if (errorMsgs.size() > 0) {
      throw new UserDataException(Step.SERVER_SETTINGS,
              Utils.getStringFromCollection(errorMsgs, "\n"));
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
    boolean cf = UpgradeWizardStep.REVIEW.equals(step);
    return cf;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoBack(WizardStep step) {
    return super.canGoBack(step) && !step.equals(UpgradeWizardStep.PROGRESS);
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
        ZipExtractor extractor = null;
        try {
          LOG.log(Level.INFO, "Waiting for Java Web Start jar download");
          waitForLoader(15); // TODO: ratio
          LOG.log(Level.INFO, "Downloaded build file");
          String zipName = WebStartDownloader.getZipFileName();
          InputStream in =
                  Upgrader.class.getClassLoader().getResourceAsStream(zipName);
          extractor = new ZipExtractor(in, zipName);

        } catch (ApplicationException e) {
          LOG.log(Level.SEVERE, "Error downloading Web Start jars", e);
          throw e;
        }

        checkAbort();

        try {
          setCurrentProgressStep(UpgradeProgressStep.EXTRACTING);
          extractor.extract(getStageDirectory());
          notifyListeners(formatter.getFormattedDone() +
                  formatter.getLineBreak());
          LOG.log(Level.INFO, "extraction finished");
        } catch (ApplicationException e) {
          notifyListeners(formatter.getFormattedError() +
                  formatter.getLineBreak());
          LOG.log(Level.INFO, "Error extracting build file", e);
          throw e;
        }

      }

      checkAbort();

      try {
        LOG.log(Level.INFO, "initializing upgrade");
        setCurrentProgressStep(UpgradeProgressStep.INITIALIZING);
        initialize();
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "initialization finished");
      } catch (ApplicationException e) {
        notifyListeners(formatter.getFormattedError() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "Error initializing upgrader", e);
        throw e;
      }

      checkAbort();

      boolean schemaCustomizationPresent = false;
      try {
        LOG.log(Level.INFO, "checking for schema customizations");
        setCurrentProgressStep(
                UpgradeProgressStep.CALCULATING_SCHEMA_CUSTOMIZATIONS);
        schemaCustomizationPresent = calculateSchemaCustomizations();
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "check for schema customizations finished");
      } catch (ApplicationException e) {
        notifyListeners(formatter.getFormattedError() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "Error calculating schema customizations", e);
        throw e;
      }

      checkAbort();

      boolean configCustimizationPresent = false;
      try {
        LOG.log(Level.INFO, "checking for config customizations");
        setCurrentProgressStep(
                UpgradeProgressStep.CALCULATING_CONFIGURATION_CUSTOMIZATIONS);
        configCustimizationPresent = calculateConfigCustomizations();
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "check for config customizations finished");
      } catch (ApplicationException e) {
        notifyListeners(formatter.getFormattedError() +
                formatter.getLineBreak());
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
          notifyListeners(formatter.getFormattedDone() +
                  formatter.getLineBreak());
          LOG.log(Level.INFO, "database backup finished");
        } catch (ApplicationException e) {
          notifyListeners(formatter.getFormattedError() +
                  formatter.getLineBreak());
          LOG.log(Level.INFO, "Error backing up databases", e);
          throw e;
        }
      }

      checkAbort();

      try {
        LOG.log(Level.INFO, "backing up filesystem");
        setCurrentProgressStep(UpgradeProgressStep.BACKING_UP_FILESYSTEM);
        backupFilesytem();
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "filesystem backup finished");
      } catch (ApplicationException e) {
        notifyListeners(formatter.getFormattedError() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "Error backing up files", e);
        throw e;
      }

      checkAbort();

      try {
        LOG.log(Level.INFO, "upgrading components");
        setCurrentProgressStep(
                UpgradeProgressStep.UPGRADING_COMPONENTS);
        upgradeComponents();
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "componnet upgrade finished");
      } catch (ApplicationException e) {
        notifyListeners(formatter.getFormattedError() +
                formatter.getLineBreak());
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
              (schemaCustomizationPresent ? "":"not ") + "present");
      LOG.log(Level.INFO, "config customization " +
              (configCustimizationPresent ? "":"not ") + "present");
      if (schemaCustomizationPresent || configCustimizationPresent) {
        try {
          LOG.log(Level.INFO, "starting server");
          setCurrentProgressStep(
                  UpgradeProgressStep.PREPARING_CUSTOMIZATIONS);
          InProcessServerController ipsc =
                  new InProcessServerController(getInstallation());
          InProcessServerController.disableConnectionHandlers(true);
          ipsc.startServer();
          LOG.log(Level.INFO, "start server finished");
          notifyListeners(formatter.getFormattedDone() +
                  formatter.getLineBreak());
        } catch (Exception e) {
          notifyListeners(formatter.getFormattedError() +
                  formatter.getLineBreak());
          LOG.log(Level.INFO,
                  "Error starting server in order to apply custom" +
                          "schema and/or configuration", e);
          throw new ApplicationException(ApplicationException.Type.APPLICATION,
                  "Error starting server:" + e.getLocalizedMessage(), e);
        }

        checkAbort();

        if (schemaCustomizationPresent) {
          try {
            LOG.log(Level.INFO, "applying schema customizatoin");
            setCurrentProgressStep(
                    UpgradeProgressStep.APPLYING_SCHEMA_CUSTOMIZATIONS);
            applySchemaCustomizations();
            notifyListeners(formatter.getFormattedDone() +
                    formatter.getLineBreak());
            LOG.log(Level.INFO, "custom schema application finished");
          } catch (ApplicationException e) {
            notifyListeners(formatter.getFormattedError() +
                    formatter.getLineBreak());
            LOG.log(Level.INFO,
                    "Error applying schema customizations", e);
            throw e;
          }
        }

        checkAbort();

        if (configCustimizationPresent) {
          try {
            LOG.log(Level.INFO, "applying config customizatoin");
            setCurrentProgressStep(
                    UpgradeProgressStep.APPLYING_CONFIGURATION_CUSTOMIZATIONS);
            applyConfigurationCustomizations();
            notifyListeners(formatter.getFormattedDone() +
                    formatter.getLineBreak());
            LOG.log(Level.INFO, "custom config application finished");
          } catch (ApplicationException e) {
            notifyListeners(formatter.getFormattedError() +
                    formatter.getLineBreak());
            LOG.log(Level.INFO,
                    "Error applying configuration customizations", e);
            throw e;
          }
        }

        checkAbort();

        try {
          LOG.log(Level.INFO, "stopping server");
          // This class imports classes from the server
          new InProcessServerController(
                  getInstallation()).stopServer();
          InProcessServerController.disableConnectionHandlers(false);
          LOG.log(Level.INFO, "server stopped");
        } catch (Throwable t) {
          LOG.log(Level.INFO, "Error stopping server", t);
          throw new ApplicationException(ApplicationException.Type.BUG,
                  "Error stopping server in process", t);
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
                null, "ARTIFICIAL ERROR FOR TESTING ABORT PROCESS", null);
      }

      LOG.log(Level.INFO, "verifying upgrade");
      setCurrentProgressStep(UpgradeProgressStep.VERIFYING);
      Installation installation = getInstallation();
      ServerHealthChecker healthChecker = new ServerHealthChecker(installation);
      healthChecker.checkServer();
      List<String> errors = healthChecker.getProblemMessages();

      // For testing
      if ("true".equals(
              System.getProperty(SYS_PROP_CREATE_VERIFY_ERROR))) {
        LOG.log(Level.WARNING, "creating artificial verification error");
        if (errors == null || errors.size() == 0) {
          errors = new ArrayList<String>();
          errors.add("Artificial verification error for testing");
        }
      }

      if (errors != null && errors.size() > 0) {
        notifyListeners(formatter.getFormattedError() +
                formatter.getLineBreak());
        String sep = System.getProperty("line.separator");
        String formattedDetails =
                Utils.listToString(errors, sep, /*bullet=*/"\u2022 ", "");
        runWarning = new ApplicationException(
                ApplicationException.Type.APPLICATION,
              "Upgraded server failed verification test by signaling " +
                      "errors during startup:" + sep +
                      formattedDetails, null);
        String cancel = "Cancel Upgrade";
        UserInteraction ui = userInteraction();
        if (ui == null || cancel.equals(ui.confirm(
                  "Upgrade Verification Failed",
                  "The upgraded server returned errors on startup.  Would " +
                          "you like to cancel the upgrade?  If you cancel, " +
                          "any changes made to the server by this upgrade " +
                          "will be backed out.",
                  formattedDetails,
                  "Upgrade Error",
                  UserInteraction.MessageType.ERROR,
                  new String[] { "Continue", cancel },
                  cancel, "View Error Details"))) {
            cancel();
            throw new ApplicationException(
              ApplicationException.Type.APPLICATION,
              "Upgrade canceled", null);
        }
      } else {
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
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
            int port = getInstallation().getCurrentConfiguration().getPort();
            if (port != -1 && !Utils.canUseAsPort(port)) {
              throw new ApplicationException(
                      ApplicationException.Type.APPLICATION,
                      "The server can not be started as another application " +
                              "is using port " + port + ".  Check that you " +
                              "have access to this port before restarting " +
                              "the server.", null);
            }
            control.startServer(true);
            notifyListeners(formatter.getFormattedDone() +
                    formatter.getLineBreak());
          } catch (ApplicationException e) {
            notifyListeners(formatter.getFormattedError() +
                    formatter.getLineBreak());
            LOG.log(Level.INFO, "error starting server");
            this.runWarning = e;
          }
        } else if (!userRequestsStart && serverRunning) {
          try {
            LOG.log(Level.INFO, "stopping server");
            setCurrentProgressStep(UpgradeProgressStep.STOPPING_SERVER);
            control.stopServer(true);
            notifyListeners(formatter.getFormattedDone() +
                    formatter.getLineBreak());
          } catch (ApplicationException e) {
            notifyListeners(formatter.getFormattedError() +
                    formatter.getLineBreak());
            LOG.log(Level.INFO, "error stopping server");
            this.runWarning = e;
          }
        }
      } catch (IOException ioe) {
        LOG.log(Level.INFO, "error determining if server running");
        this.runWarning = new ApplicationException(
                ApplicationException.Type.TOOL_ERROR,
                "Error determining whether or not server running", ioe);
      }

    } catch (ApplicationException ae) {
      this.runError = ae;
    } catch (Throwable t) {
      this.runError =
              new ApplicationException(ApplicationException.Type.BUG,
                      "Unexpected error: " + t.getLocalizedMessage(),
                      t);
    } finally {
      try {
        HistoricalRecord.Status status;
        String note = null;
        if (runError == null) {
          status = HistoricalRecord.Status.SUCCESS;
        } else {
          status = HistoricalRecord.Status.FAILURE;
          note = runError.getLocalizedMessage();

          // Abort the upgrade and put things back like we found it
          LOG.log(Level.INFO, "canceling upgrade");
          ProgressStep lastProgressStep = getCurrentProgressStep();
          setCurrentProgressStep(UpgradeProgressStep.ABORT);
          LOG.log(Level.INFO, "abort");
          abort(lastProgressStep);
          notifyListeners(formatter.getFormattedDone() +
                  formatter.getLineBreak());
          LOG.log(Level.INFO, "cancelation complete");
        }

        LOG.log(Level.INFO, "cleaning up after upgrade");
        setCurrentProgressStep(UpgradeProgressStep.CLEANUP);
        cleanup();
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "clean up complete");


        // Write a record in the log file indicating success/failure
        LOG.log(Level.INFO, "recording upgrade history");
        setCurrentProgressStep(UpgradeProgressStep.RECORDING_HISTORY);
        writeHistoricalRecord(historicalOperationId,
                getCurrentBuildInformation(),
                getStagedBuildInformation(),
                status,
                note);
        notifyListeners(formatter.getFormattedDone() +
                formatter.getLineBreak());
        LOG.log(Level.INFO, "history recorded");
        notifyListeners("See '" +
                Utils.getPath(getInstallation().getHistoryLogFile()) +
                " for upgrade history" + formatter.getLineBreak());
      } catch (ApplicationException e) {
        notifyListeners(formatter.getFormattedError() +
                formatter.getLineBreak());
        System.err.print("Error cleaning up after upgrade: " +
                e.getLocalizedMessage());
      }

      // Decide final status based on presense of error

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
        }
      } else if (runError != null) {
        LOG.log(Level.INFO, "upgrade completed with errors", runError);
        if (!Utils.isCli()) {
          notifyListenersOfLog();
          this.currentProgressStep = UpgradeProgressStep.FINISHED_WITH_ERRORS;
          notifyListeners(formatter.getFormattedError(runError, true));
        } else {
          notifyListeners(formatter.getFormattedError(runError, true) +
                          formatter.getLineBreak());
          notifyListeners(formatter.getLineBreak());
          setCurrentProgressStep(UpgradeProgressStep.FINISHED_WITH_ERRORS);
          notifyListeners(formatter.getLineBreak());
        }
      } else if (runWarning != null) {
        LOG.log(Level.INFO, "upgrade completed with warnings");
        String warningText = runWarning.getLocalizedMessage();

        // By design, the warnings are written as errors to the details section
        // as errors.  Warning markup is used surrounding the main message
        // at the end of progress.
        if (!Utils.isCli()) {
          notifyListenersOfLog();
          this.currentProgressStep = UpgradeProgressStep.FINISHED_WITH_WARNINGS;
          notifyListeners(formatter.getFormattedError(warningText, true));
        } else {
          notifyListeners(formatter.getFormattedError(warningText, true) +
                          formatter.getLineBreak());
          notifyListeners(formatter.getLineBreak());
          setCurrentProgressStep(UpgradeProgressStep.FINISHED_WITH_WARNINGS);
          notifyListeners(formatter.getLineBreak());
        }

      } else {
        LOG.log(Level.INFO, "upgrade completed successfully");
        if (!Utils.isCli()) {
          notifyListenersOfLog();
          this.currentProgressStep = UpgradeProgressStep.FINISHED;
          notifyListeners(null);
        } else {
          setCurrentProgressStep(UpgradeProgressStep.FINISHED);
        }
      }
    }

  }

  private void checkAbort() throws ApplicationException {
    if (abort) throw new ApplicationException(
            ApplicationException.Type.APPLICATION,
            "Upgrade canceled", null);
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
        backupDirectory = getFilesBackupDirectory();
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
            notifyListeners("The following could not be restored after the" +
                    "failed upgrade attempt.  You should restore this " +
                    "file/directory manually: '" + f + "' to '" + root + "'");
          }
        }
        if (!restoreError) {
          fm.deleteRecursively(backupDirectory);
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

  private void applyConfigurationCustomizations() throws ApplicationException {
    try {
      File configDiff = getCustomConfigDiffFile();
      if (configDiff.exists()) {
        new InProcessServerController(
                getInstallation()).modify(configDiff);

      }
    } catch (IOException e) {
      String msg = "I/O Error applying configuration customization: " +
              e.getLocalizedMessage();
      LOG.log(Level.INFO, msg, e);
      throw new ApplicationException(ApplicationException.Type.IMPORT_ERROR,
              msg, e);
    } catch (Exception e) {
      String msg = "Error applying configuration customization: " +
              e.getLocalizedMessage();
      LOG.log(Level.INFO, msg, e);
      throw new ApplicationException(ApplicationException.Type.IMPORT_ERROR,
              msg, e);
    }
  }

  private void applySchemaCustomizations() throws ApplicationException {
    try {
      File schemaDiff = getCustomSchemaDiffFile();
      if (schemaDiff.exists()) {
        new InProcessServerController(
                getInstallation()).modify(schemaDiff);
      }
    } catch (IOException e) {
      String msg = "I/O Error applying schema customization: " +
              e.getLocalizedMessage();
      LOG.log(Level.INFO, msg, e);
      throw new ApplicationException(ApplicationException.Type.IMPORT_ERROR,
              msg, e);
    } catch (Exception e) {
      String msg = "Error applying schema customization: " +
              e.getLocalizedMessage();
      LOG.log(Level.INFO, msg, e);
      throw new ApplicationException(ApplicationException.Type.IMPORT_ERROR,
              msg, e);
    }
  }

  private Long writeInitialHistoricalRecord(
          BuildInformation fromVersion,
          BuildInformation toVersion)
          throws ApplicationException {
    Long id;
    try {
      HistoricalLog log =
              new HistoricalLog(getInstallation().getHistoryLogFile());
      id = log.append(fromVersion, toVersion,
              HistoricalRecord.Status.STARTED, null);
    } catch (IOException e) {
      String msg = "I/O Error logging operation: " + e.getLocalizedMessage();
      throw ApplicationException.createFileSystemException(
              msg, e);
    }
    return id;
  }

  private void writeHistoricalRecord(
          Long id,
          BuildInformation from,
          BuildInformation to,
          HistoricalRecord.Status status,
          String note)
          throws ApplicationException {
    try {
      HistoricalLog log =
              new HistoricalLog(getInstallation().getHistoryLogFile());
      log.append(id, from, to, status, note);
    } catch (IOException e) {
      String msg = "Error logging operation: " + e.getLocalizedMessage();
      throw ApplicationException.createFileSystemException(msg, e);
    }
  }

  private void upgradeComponents() throws ApplicationException {
    try {
      File stageDir = getStageDirectory();
      Installation installation = getInstallation();
      File root = installation.getRootDirectory();
      FileManager fm = new FileManager();
      for (String fileName : stageDir.list()) {
        File f = new File(stageDir, fileName);
        fm.copyRecursively(f, root,
                new UpgradeFileFilter(stageDir),
                /*overwrite=*/true);
      }

      // The bits should now be of the new version.  Have
      // the installation update the build information so
      // that it is correct.
      LOG.log(Level.INFO, "upgraded bits to " +
              installation.getBuildInformation(false));

    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              "I/0 error upgrading components: " + e.getLocalizedMessage(), e);
    }
  }

  private boolean calculateConfigCustomizations() throws ApplicationException {
    boolean isCustom = false;
    try {
      if (getInstallation().getCurrentConfiguration().hasBeenModified()) {
        isCustom = true;
        LOG.log(Level.INFO, "Configuration contains customizations that will " +
                "be migrated");
        try {
          ldifDiff(getInstallation().getBaseConfigurationFile(),
                  getInstallation().getCurrentConfigurationFile(),
                  getCustomConfigDiffFile());
        } catch (Exception e) {
          throw ApplicationException.createFileSystemException(
                  "Error determining configuration customizations: "
                          + e.getLocalizedMessage(), e);
        }
      } else {
        LOG.log(Level.INFO, "No configuration customizations to migrate");
      }
    } catch (IOException e) {
      // TODO i18n
      throw ApplicationException.createFileSystemException(
              "Could not determine configuration modifications: " +
                      e.getLocalizedMessage(), e);
    }
    return isCustom;
  }

  private void ldifDiff(File source, File target, File output)
          throws ApplicationException {
    ExternalTools et = new ExternalTools(getInstallation());
    try {
      String[] args = new String[] {
              "-o", Utils.getPath(output),
              "-O",
      };
      OperationOutput oo = et.ldifDiff(source, target, args);
      int ret = oo.getReturnCode();
      if (ret != 0) {
        throw new ApplicationException(
                ApplicationException.Type.TOOL_ERROR,
                "ldif-diff tool returned error code " + ret,
                null);
      }
    } catch (Exception e) {
      throw new ApplicationException(
              ApplicationException.Type.TOOL_ERROR,
              "Error performing determining customizations", e);
    }
  }

  private boolean calculateSchemaCustomizations() throws ApplicationException {
    boolean isCustom = false;
    if (getInstallation().getStatus().schemaHasBeenModified()) {
      isCustom = true;
      LOG.log(Level.INFO, "Schema contains customizations that will " +
              "be migrated");
      try {
        ldifDiff(getInstallation().getBaseSchemaFile(),
                getInstallation().getSchemaConcatFile(),
                getCustomSchemaDiffFile());
      } catch (Exception e) {
        throw ApplicationException.createFileSystemException(
                "Error determining schema customizations: " +
                        e.getLocalizedMessage(), e);
      }
    } else {
      LOG.log(Level.INFO, "No schema customizations to migrate");
    }
    return isCustom;
  }

  private void backupFilesytem() throws ApplicationException {
    try {
      File filesBackupDirectory = getFilesBackupDirectory();
      FileManager fm = new FileManager();
      File root = getInstallation().getRootDirectory();
      FileFilter filter = new UpgradeFileFilter(root);
      for (String fileName : root.list()) {
        File f = new File(root, fileName);
        //fm.copyRecursively(f, filesBackupDirectory,
        fm.move(f, filesBackupDirectory, filter);
      }
    } catch (Exception e) {
      throw new ApplicationException(
              ApplicationException.Type.FILE_SYSTEM_ERROR,
              e.getLocalizedMessage(),
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
                ApplicationException.Type.TOOL_ERROR,
                "backup tool returned error code " + ret,
                null);

      }
    } catch (Exception e) {
      throw new ApplicationException(
              ApplicationException.Type.TOOL_ERROR,
              "Error backing up databases", e);
    }
  }

  private void cleanup() throws ApplicationException {
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
      // TODO i18n
      throw ApplicationException.createFileSystemException(
              "Error attempting to clean up tmp directory " +
                      stagingDir != null ? stagingDir.getName() : "null" +
                      ": " + e.getLocalizedMessage(),
              e);
    }
  }

  private void initialize() throws ApplicationException {
    try {
      if (getInstallation().getStatus().isServerRunning()) {
        new ServerController(getInstallation()).stopServer(true);
      }

      BuildInformation fromVersion = getCurrentBuildInformation();
      BuildInformation toVersion = getStagedBuildInformation();
      this.historicalOperationId =
              writeInitialHistoricalRecord(fromVersion, toVersion);

      insureUpgradability();

    } catch (Exception e) {
      throw new ApplicationException(
              ApplicationException.Type.FILE_SYSTEM_ERROR,
              e.getMessage(), e);
    }
  }

  /**
   * Given the current information, determines whether or not
   * an upgrade from the current version to the next version
   * is possible.  Upgrading may not be possible due to 'flag
   * day' types of changes to the codebase.
   */
  private void insureUpgradability() throws ApplicationException {
    BuildInformation currentVersion;
    BuildInformation newVersion;

    try {
      currentVersion = getInstallation().getBuildInformation();
    } catch (ApplicationException e) {
      LOG.log(Level.INFO, "error", e);
      throw ApplicationException.createFileSystemException(
              "Could not determine current build information: " +
                      e.getLocalizedMessage(), e);
    }

    try {
      newVersion = getStagedInstallation().getBuildInformation();
    } catch (Exception e) {
      LOG.log(Level.INFO, "error", e);
      throw ApplicationException.createFileSystemException(
              "Could not determine upgrade build information: " +
                      e.getLocalizedMessage(), e);
    }

    UpgradeOracle uo = new UpgradeOracle(currentVersion, newVersion);
    if (!uo.isSupported()) {
      throw new ApplicationException(ApplicationException.Type.APPLICATION,
              uo.getSummaryMessage(), null);
    }

  }

  private Installation getStagedInstallation()
          throws IOException, ApplicationException {
    if (stagedInstallation == null) {
      File stageDir = getStageDirectory();
      try {
        Installation.validateRootDirectory(stageDir);
        stagedInstallation = new Installation(getStageDirectory());
      } catch (IllegalArgumentException e) {
        throw ApplicationException.createFileSystemException(
                "Directory '" + getStageDirectory() +
                        "' does not contain a staged installation of OpenDS" +
                        " as was expected.  Verify that the new installation" +
                        " package (.zip) is an OpenDS installation file and" +
                        " that you have write access permission for this " +
                        " directory.", null);
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
  public UserData createUserData(String[] args, CurrentInstallStatus cis)
          throws UserDataException {
    return getCliHelper().createUserData(args, cis);
  }

  /**
   * {@inheritDoc}
   */
  public ApplicationException getException() {
    return runError;
  }

  private void setCurrentProgressStep(UpgradeProgressStep step) {
    this.currentProgressStep = step;
    int progress = step.getProgress();
    String msg = getSummary(step);
    notifyListeners(progress, msg, getFormattedProgress(msg));
  }

  private UpgraderCliHelper getCliHelper() {
    if (cliHelper == null) {
      cliHelper = new UpgraderCliHelper();
    }
    return cliHelper;
  }

  private String getFinalSuccessMessage() {
    String txt;
    String installPath = Utils.getPath(getInstallation().getRootDirectory());
    String newVersion = null;
    try {
      BuildInformation bi = getInstallation().getBuildInformation();
      if (bi != null) {
        newVersion = bi.toString();
      } else {
        newVersion = getMsg("upgrade-build-id-unknown");
      }
    } catch (ApplicationException e) {
      newVersion = getMsg("upgrade-build-id-unknown");
    }
    String[] args = {
            formatter.getFormattedText(installPath),
            newVersion};
    if (Utils.isCli()) {
      txt = getMsg("summary-upgrade-finished-successfully-cli", args);
    } else {
      txt = getFormattedSuccess(
              getMsg("summary-upgrade-finished-successfully",
                      args));
    }
    return txt;
  }

  private String getFinalCanceledMessage() {
    String txt;
    if (Utils.isCli()) {
      txt = getMsg("summary-upgrade-finished-canceled-cli");
    } else {
      txt = getFormattedSuccess(
              getMsg("summary-upgrade-finished-canceled"));
    }
    return txt;
  }

  private String getFinalErrorMessage() {
    String txt;
    if (Utils.isCli()) {
      txt = getMsg("summary-upgrade-finished-with-errors-cli");
    } else {
      txt = getFormattedError(
              getMsg("summary-upgrade-finished-with-errors"));
    }
    return txt;
  }

  private String getFinalWarningMessage() {
    String txt;
    if (Utils.isCli()) {
      txt = getMsg("summary-upgrade-finished-with-warnings-cli");
    } else {
      txt = getFormattedWarning(
              getMsg("summary-upgrade-finished-with-warnings"));
    }
    return txt;
  }

  private File getStageDirectory()
          throws ApplicationException, IOException {
    return getInstallation().getTemporaryUpgradeDirectory();
  }

  private UpgradeUserData getUpgradeUserData() {
    return (UpgradeUserData) getUserData();
  }

  private File getFilesBackupDirectory() throws IOException {
    File files = new File(getUpgradeBackupDirectory(), "files");
    if (!files.exists()) {
      if (!files.mkdirs()) {
        throw new IOException("error creating files backup directory");
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

  private File getCustomConfigDiffFile() throws IOException {
    return new File(getUpgradeBackupDirectory(), "config.custom.diff");
  }

  private File getCustomSchemaDiffFile() throws IOException {
    return new File(getUpgradeBackupDirectory(), "schema.custom.diff");
  }

  private BuildInformation getCurrentBuildInformation() {
    if (this.currentVersion == null) {
      try {
        currentVersion = getInstallation().getBuildInformation();
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "error trying to determine current version", e);
      }
    }
    return currentVersion;
  }

  private BuildInformation getStagedBuildInformation() {
    if (stagedVersion == null) {
      try {
        stagedVersion = getStagedInstallation().getBuildInformation();
      } catch (Exception e) {
        LOG.log(Level.INFO, "error", e);
      }
    }
    return stagedVersion;
  }

  /**
   * Filter defining files we want to manage in the upgrade
   * process.
   */
  private class UpgradeFileFilter implements FileFilter {

    Set<File> filesToIgnore;

    public UpgradeFileFilter(File root) throws IOException {
      this.filesToIgnore = new HashSet<File>();
      for (String rootFileNamesToIgnore : ROOT_FILES_TO_IGNORE_DURING_BACKUP) {
        filesToIgnore.add(new File(root, rootFileNamesToIgnore));
      }

      // Definitely want to not back this up since it would create
      // infinite recursion.  This may not be necessary if we are
      // ignoring the entire history directory but its added here for
      // safe measure.
      filesToIgnore.add(getUpgradeBackupDirectory());
    }

    public boolean accept(File file) {
      boolean accept = true;
      for (File ignoreFile : filesToIgnore) {
        if (ignoreFile.equals(file) ||
                Utils.isParentOf(ignoreFile, file)) {
          accept = false;
          break;
        }
      }
      return accept;
    }
  }

}
