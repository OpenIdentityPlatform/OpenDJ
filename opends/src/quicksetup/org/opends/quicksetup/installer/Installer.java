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
package org.opends.quicksetup.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.WindowEvent;

import javax.naming.AuthenticationException;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.quicksetup.ui.*;
import static org.opends.quicksetup.util.Utils.*;
import static org.opends.quicksetup.Step.*;
import org.opends.quicksetup.*;
import org.opends.server.util.CertificateManager;
import org.opends.quicksetup.installer.ui.DataOptionsPanel;
import org.opends.quicksetup.installer.ui.DataReplicationPanel;
import org.opends.quicksetup.installer.ui.GlobalAdministratorPanel;
import org.opends.quicksetup.installer.ui.InstallReviewPanel;
import org.opends.quicksetup.installer.ui.InstallWelcomePanel;
import org.opends.quicksetup.installer.ui.RemoteReplicationPortsPanel;
import org.opends.quicksetup.installer.ui.ServerSettingsPanel;
import org.opends.quicksetup.installer.ui.SuffixesToReplicatePanel;
import org.opends.server.util.SetupUtils;
import org.opends.server.types.OpenDsException;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import javax.naming.ldap.Rdn;
import javax.swing.*;


/**
 * This is an abstract class that is in charge of actually performing the
 * installation.
 *
 * It just takes a UserData object and based on that installs OpenDS.
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The
 * notification will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 *
 * Note that we can use freely the class org.opends.server.util.SetupUtils as
 * it is included in quicksetup.jar.
 *
 */
public abstract class Installer extends GuiApplication {
  private TopologyCache lastLoadedCache;

  /** Indicates that we've detected that there is something installed. */
  boolean forceToDisplaySetup = false;

  /** When true indicates that the user has canceled this operation. */
  protected boolean canceled = false;

  /** Map containing information about what has been configured remotely. */
  Map<ServerDescriptor, ConfiguredReplication> hmConfiguredRemoteReplication =
    new HashMap<ServerDescriptor, ConfiguredReplication>();

  // Constants used to do checks
  private static final int MIN_DIRECTORY_MANAGER_PWD = 1;

  private static final Logger LOG = Logger.getLogger(Installer.class.getName());

  /**
   * The minimum integer value that can be used for a port.
   */
  public static final int MIN_PORT_VALUE = 1;

  /**
   * The maximum integer value that can be used for a port.
   */
  public static final int MAX_PORT_VALUE = 65535;

  private static final int MIN_NUMBER_ENTRIES = 1;

  private static final int MAX_NUMBER_ENTRIES = 10000;

  /** Set of progress steps that have been completed. */
  protected Set<InstallProgressStep>
          completedProgress = new HashSet<InstallProgressStep>();

  private List<WizardStep> lstSteps = new ArrayList<WizardStep>();

  private final HashSet<WizardStep> SUBSTEPS = new HashSet<WizardStep>();
  {
    SUBSTEPS.add(Step.CREATE_GLOBAL_ADMINISTRATOR);
    SUBSTEPS.add(Step.SUFFIXES_OPTIONS);
    SUBSTEPS.add(Step.NEW_SUFFIX_OPTIONS);
    SUBSTEPS.add(Step.REMOTE_REPLICATION_PORTS);
  }

  private HashMap<WizardStep, WizardStep> hmPreviousSteps =
    new HashMap<WizardStep, WizardStep>();

  private char[] selfSignedCertPw = null;

  private boolean registeredNewServerOnRemote;
  private boolean createdAdministrator;
  private boolean createdRemoteAds;

  /**
   * An static String that contains the class name of ConfigFileHandler.
   */
  protected static final String CONFIG_CLASS_NAME =
      "org.opends.server.extensions.ConfigFileHandler";

  /** Alias of a self-signed certificate. */
  protected static final String SELF_SIGNED_CERT_ALIAS = "server-cert";

  /**
   * Creates a default instance.
   */
  public Installer() {
    lstSteps.add(WELCOME);
    lstSteps.add(SERVER_SETTINGS);
    lstSteps.add(REPLICATION_OPTIONS);
    lstSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    lstSteps.add(SUFFIXES_OPTIONS);
    lstSteps.add(REMOTE_REPLICATION_PORTS);
    lstSteps.add(NEW_SUFFIX_OPTIONS);
    lstSteps.add(REVIEW);
    lstSteps.add(PROGRESS);
    lstSteps.add(FINISHED);
    try {
      if (!QuickSetupLog.isInitialized())
        QuickSetupLog.initLogFileHandler(
                File.createTempFile(
                        InstallLauncher.LOG_FILE_PREFIX,
                        InstallLauncher.LOG_FILE_SUFFIX));
    } catch (IOException e) {
      System.err.println("Failed to initialize log");
    }
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
  public UserData createUserData() {
    UserData ud = new UserData();
    ud.setServerLocation(getDefaultServerLocation());
    return ud;
  }

  /**
   * {@inheritDoc}
   */
  public void forceToDisplay() {
    forceToDisplaySetup = true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoBack(WizardStep step) {
    return step != WELCOME &&
            step != PROGRESS &&
            step != FINISHED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoForward(WizardStep step) {
    return step != REVIEW &&
            step != PROGRESS &&
            step != FINISHED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canFinish(WizardStep step) {
    return step == REVIEW;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canQuit(WizardStep step) {
    return step != PROGRESS &&
    step != FINISHED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSubStep(WizardStep step)
  {
    return SUBSTEPS.contains(step);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVisible(WizardStep step, UserData userData)
  {
    boolean isVisible;
    if (step == CREATE_GLOBAL_ADMINISTRATOR)
    {
       isVisible = userData.mustCreateAdministrator();
    }
    else if (step == NEW_SUFFIX_OPTIONS)
    {
      SuffixesToReplicateOptions suf =
        userData.getSuffixesToReplicateOptions();
      if (suf != null)
      {
        isVisible = suf.getType() !=
          SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES;
      }
      else
      {
        isVisible = false;
      }
    }
    else if (step == SUFFIXES_OPTIONS)
    {
      DataReplicationOptions repl = userData.getReplicationOptions();
      if (repl != null)
      {
        isVisible =
          (repl.getType() != DataReplicationOptions.Type.STANDALONE) &&
          (repl.getType() != DataReplicationOptions.Type.FIRST_IN_TOPOLOGY);
      }
      else
      {
        isVisible = false;
      }
    }
    else if (step == REMOTE_REPLICATION_PORTS)
    {
      isVisible = isVisible(SUFFIXES_OPTIONS, userData) &&
      (userData.getRemoteWithNoReplicationPort().size() > 0) &&
      (userData.getSuffixesToReplicateOptions().getType() ==
        SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES);
    }
    else
    {
      isVisible = true;
    }
    return isVisible;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVisible(WizardStep step, QuickSetup qs)
  {
    return isVisible(step, getUserData());
  }

  /**
   * {@inheritDoc}
   */
  public boolean finishClicked(final WizardStep cStep, final QuickSetup qs) {
    if (cStep == Step.REVIEW) {
        updateUserDataForReviewPanel(qs);
        qs.launch();
        qs.setCurrentStep(Step.PROGRESS);
    } else {
        throw new IllegalStateException(
                "Cannot click on finish when we are not in the Review window");
    }
    // Installer responsible for updating the user data and launching
    return false;
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
              || qs.displayConfirmation(INFO_CONFIRM_CLOSE_INSTALL_MSG.get(),
              INFO_CONFIRM_CLOSE_INSTALL_TITLE.get())) {
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
   * {@inheritDoc}
   */
  public boolean isFinished()
  {
    return getCurrentProgressStep() == InstallProgressStep.FINISHED_SUCCESSFULLY
            || getCurrentProgressStep() == InstallProgressStep.FINISHED_CANCELED
        || getCurrentProgressStep() == InstallProgressStep.FINISHED_WITH_ERROR;
  }

  /**
   * {@inheritDoc}
   */
  public void cancel() {
    setCurrentProgressStep(InstallProgressStep.WAITING_TO_CANCEL);
    notifyListeners(null);
    this.canceled = true;
  }

  /**
   * {@inheritDoc}
   */
  public void quitClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == FINISHED)
    {
      qs.quit();
    }
    else if (cStep == PROGRESS) {
      throw new IllegalStateException(
              "Cannot click on quit from progress step");
    } else if (installStatus.isInstalled()) {
      qs.quit();
    } else if (qs.displayConfirmation(INFO_CONFIRM_QUIT_INSTALL_MSG.get(),
            INFO_CONFIRM_QUIT_INSTALL_TITLE.get())) {
      qs.quit();
    }
  }

  /**
   * {@inheritDoc}
   */
  public ButtonName getInitialFocusButtonName() {
    ButtonName name = null;
    if (!installStatus.isInstalled() || forceToDisplaySetup)
    {
      name = ButtonName.NEXT;
    } else
    {
      if (installStatus.canOverwriteCurrentInstall())
      {
        name = ButtonName.CONTINUE_INSTALL;
      }
      else
      {
        name = ButtonName.QUIT;
      }
    }
    return name;
  }

  /**
   * {@inheritDoc}
   */
  public JPanel createFramePanel(QuickSetupDialog dlg) {
    JPanel p;
    if (installStatus.isInstalled() && !forceToDisplaySetup) {
      p = dlg.getInstalledPanel();
    } else {
      p = super.createFramePanel(dlg);
    }
    return p;
  }

  /**
   * {@inheritDoc}
   */
  public Set<? extends WizardStep> getWizardSteps() {
    return Collections.unmodifiableSet(new HashSet<WizardStep>(lstSteps));
  }

  /**
   * {@inheritDoc}
   */
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step) {
    QuickSetupStepPanel p = null;
    if (step == WELCOME) {
        p = new InstallWelcomePanel(this);
    } else if (step == SERVER_SETTINGS) {
        p = new ServerSettingsPanel(this);
    } else if (step == REPLICATION_OPTIONS) {
      p = new DataReplicationPanel(this);
    } else if (step == CREATE_GLOBAL_ADMINISTRATOR) {
      p = new GlobalAdministratorPanel(this);
    } else if (step == SUFFIXES_OPTIONS) {
      p = new SuffixesToReplicatePanel(this);
    } else if (step == REMOTE_REPLICATION_PORTS) {
      p = new RemoteReplicationPortsPanel(this);
    } else if (step == NEW_SUFFIX_OPTIONS) {
        p = new DataOptionsPanel(this);
    } else if (step == REVIEW) {
        p = new InstallReviewPanel(this);
    } else if (step == PROGRESS) {
        p = new ProgressPanel(this);
    } else if (step == FINISHED) {
        p = new FinishedPanel(this);
    }
    return p;
  }

  /**
  * {@inheritDoc}
  */
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt) {

    if (installStatus.isInstalled() && forceToDisplaySetup) {
      // Simulate a close button event
      dlg.notifyButtonEvent(ButtonName.QUIT);
    } else {
      if (dlg.getDisplayedStep() == Step.PROGRESS) {
        // Simulate a close button event
        dlg.notifyButtonEvent(ButtonName.CLOSE);
      } else {
        // Simulate a quit button event
        dlg.notifyButtonEvent(ButtonName.QUIT);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getCloseButtonToolTip() {
    return INFO_CLOSE_BUTTON_INSTALL_TOOLTIP.get();
  }

  /**
   * {@inheritDoc}
   */
  public Message getQuitButtonToolTip() {
    return INFO_QUIT_BUTTON_INSTALL_TOOLTIP.get();
  }

  /**
   * {@inheritDoc}
   */
  public Message getFinishButtonToolTip() {
    return INFO_FINISH_BUTTON_INSTALL_TOOLTIP.get();
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
  public void previousClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == WELCOME) {
      throw new IllegalStateException(
          "Cannot click on previous from progress step");
    } else if (cStep == PROGRESS) {
      throw new IllegalStateException(
          "Cannot click on previous from progress step");
    } else if (cStep == FINISHED) {
      throw new IllegalStateException(
          "Cannot click on previous from finished step");
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getFrameTitle() {
    return INFO_FRAME_INSTALL_TITLE.get();
  }

  /** Indicates the current progress step. */
  private InstallProgressStep currentProgressStep =
          InstallProgressStep.NOT_STARTED;

  /**
   * {@inheritDoc}
   */
  public void setWizardDialogState(QuickSetupDialog dlg,
                                      UserData userData,
                                      WizardStep step) {
    if (!installStatus.isInstalled() || forceToDisplaySetup) {
      // Set the default button for the frame
      if (step == REVIEW) {
        dlg.setFocusOnButton(ButtonName.FINISH);
        dlg.setDefaultButton(ButtonName.FINISH);
      } else if (step == WELCOME) {
        dlg.setDefaultButton(ButtonName.NEXT);
        dlg.setFocusOnButton(ButtonName.NEXT);
      } else if ((step == PROGRESS) || (step == FINISHED)) {
        dlg.setDefaultButton(ButtonName.CLOSE);
        dlg.setFocusOnButton(ButtonName.CLOSE);
      } else {
        dlg.setDefaultButton(ButtonName.NEXT);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public ProgressStep getCurrentProgressStep()
  {
    return currentProgressStep;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFirstWizardStep() {
    return WELCOME;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getNextWizardStep(WizardStep step) {
    WizardStep next = null;
    if (step == Step.REPLICATION_OPTIONS)
    {
      if (getUserData().mustCreateAdministrator())
      {
        next = Step.CREATE_GLOBAL_ADMINISTRATOR;
      }
      else
      {
        switch (getUserData().getReplicationOptions().getType())
        {
        case FIRST_IN_TOPOLOGY:
          next = Step.NEW_SUFFIX_OPTIONS;
          break;
        case STANDALONE:
          next = Step.NEW_SUFFIX_OPTIONS;
          break;
        default:
          next = Step.SUFFIXES_OPTIONS;
        }
      }
    }
    else if (step == Step.SUFFIXES_OPTIONS)
    {
      switch (getUserData().getSuffixesToReplicateOptions().
          getType())
      {
      case REPLICATE_WITH_EXISTING_SUFFIXES:

        if (getUserData().getRemoteWithNoReplicationPort().size() > 0)
        {
          next = Step.REMOTE_REPLICATION_PORTS;
        }
        else
        {
          next = Step.REVIEW;
        }
        break;
      default:
        next = Step.NEW_SUFFIX_OPTIONS;
      }
    }
    else if (step == Step.REMOTE_REPLICATION_PORTS)
    {
      next = Step.REVIEW;
    }
    else
    {
      int i = lstSteps.indexOf(step);
      if (i != -1 && i + 1 < lstSteps.size()) {
        next = lstSteps.get(i + 1);
      }
    }
    if (next != null)
    {
      hmPreviousSteps.put(next, step);
    }
    return next;
  }

  /**
   * {@inheritDoc}
   */
  public LinkedHashSet<WizardStep> getOrderedSteps()
  {
    LinkedHashSet<WizardStep> orderedSteps = new LinkedHashSet<WizardStep>();
    orderedSteps.add(WELCOME);
    orderedSteps.add(SERVER_SETTINGS);
    orderedSteps.add(REPLICATION_OPTIONS);
    orderedSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    orderedSteps.add(SUFFIXES_OPTIONS);
    orderedSteps.add(REMOTE_REPLICATION_PORTS);
    orderedSteps.add(NEW_SUFFIX_OPTIONS);
    orderedSteps.add(REVIEW);
    orderedSteps.add(PROGRESS);
    orderedSteps.add(FINISHED);
    return orderedSteps;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getPreviousWizardStep(WizardStep step) {
    //  Try with the steps calculated in method getNextWizardStep.
    WizardStep prev = hmPreviousSteps.get(step);

    if (prev == null)
    {
      int i = lstSteps.indexOf(step);
      if (i != -1 && i > 0) {
        prev = lstSteps.get(i - 1);
      }
    }
    return prev;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getFinishedStep() {
    return Step.FINISHED;
  }

  /**
   * Uninstalls installed services.  This is to be used when the user
   * has elected to cancel an installation.
   */
  protected void uninstallServices() {
    if (completedProgress.contains(
            InstallProgressStep.ENABLING_WINDOWS_SERVICE)) {
      try {
        new InstallerHelper().disableWindowsService();
      } catch (ApplicationException ae) {
        LOG.log(Level.INFO, "Error disabling Windows service", ae);
      }
    }

    unconfigureRemote();
  }

  /**
   * Creates a template file based in the contents of the UserData object.
   * This template file is used to generate automatically data.  To generate
   * the template file the code will basically take into account the value of
   * the base dn and the number of entries to be generated.
   *
   * @return the file object pointing to the create template file.
   * @throws ApplicationException if an error occurs.
   */
  protected File createTemplateFile() throws ApplicationException {
    try
    {
      return SetupUtils.createTemplateFile(
                  getUserData().getNewSuffixOptions().getBaseDn(),
                  getUserData().getNewSuffixOptions().getNumberEntries());
    }
    catch (IOException ioe)
    {
      Message failedMsg = getThrowableMsg(
              INFO_ERROR_CREATING_TEMP_FILE.get(), ioe);
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
          failedMsg, ioe);
    }
  }

  /**
   * This methods configures the server based on the contents of the UserData
   * object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  protected void configureServer() throws ApplicationException {
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CONFIGURING.get()));

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-c");
    argList.add(getPath(getInstallation().getCurrentConfigurationFile()));
    argList.add("-p");
    argList.add(String.valueOf(getUserData().getServerPort()));

    SecurityOptions sec = getUserData().getSecurityOptions();
    // TODO: even if the user does not configure SSL maybe we should choose
    // a secure port that is not being used and that we can actually use.
    if (sec.getEnableSSL())
    {
      argList.add("-P");
      argList.add(String.valueOf(sec.getSslPort()));
    }

    if (sec.getEnableStartTLS())
    {
      argList.add("-q");
    }

    switch (sec.getCertificateType())
    {
    case SELF_SIGNED_CERTIFICATE:
      argList.add("-k");
      argList.add("cn=JKS,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      break;
    case JKS:
      argList.add("-k");
      argList.add("cn=JKS,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      argList.add("-m");
      argList.add(sec.getKeystorePath());
      argList.add("-a");
      argList.add(sec.getAliasToUse());
      break;
    case PKCS12:
      argList.add("-k");
      argList.add("cn=PKCS12,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      // We are going to import the PCKS12 certificate in a JKS truststore
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      argList.add("-m");
      argList.add(sec.getKeystorePath());
      argList.add("-a");
      argList.add(sec.getAliasToUse());
      break;
    case PKCS11:
      argList.add("-k");
      argList.add("cn=PKCS11,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      // We are going to import the PCKS11 certificate in a JKS truststore
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      argList.add("-a");
      argList.add(sec.getAliasToUse());
      break;
    case NO_CERTIFICATE:
      // Nothing to do.
      break;
    default:
      throw new IllegalStateException("Unknown certificate type: "+
          sec.getCertificateType());
    }

    // For the moment do not enable JMX
    /*
    argList.add("-x");
    argList.add(String.valueOf(getUserData().getServerJMXPort()));
     */
    argList.add("-D");
    argList.add(getUserData().getDirectoryManagerDn());

    argList.add("-w");
    argList.add(getUserData().getDirectoryManagerPwd());

    if (createNotReplicatedSuffix())
    {
      argList.add("-b");
      argList.add(getUserData().getNewSuffixOptions().getBaseDn());
    }
    else
    {
      Set<SuffixDescriptor> suffixesToReplicate =
        getUserData().getSuffixesToReplicateOptions().getSuffixes();

      for (SuffixDescriptor suffix: suffixesToReplicate)
      {
        argList.add("-b");
        argList.add(suffix.getDN());
      }
    }

    argList.add("-R");
    argList.add(getInstallation().getRootDirectory().getAbsolutePath());

    String[] args = new String[argList.size()];
    argList.toArray(args);
    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeConfigureServer(args);

      if (result != 0)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_ERROR_CONFIGURING.get(), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
          getThrowableMsg(INFO_ERROR_CONFIGURING.get(), t), t);
    }

    try
    {
      SecurityOptions.CertificateType certType = sec.getCertificateType();
      if (certType != SecurityOptions.CertificateType.NO_CERTIFICATE)
      {
        notifyListeners(getLineBreak());
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_UPDATING_CERTIFICATES.get()));
      }
      CertificateManager certManager;
      CertificateManager trustManager;
      File f;
      switch (certType)
      {
      case NO_CERTIFICATE:
        // Nothing to do
        break;
      case SELF_SIGNED_CERTIFICATE:
        String pwd = getSelfSignedCertificatePwd();
        certManager = new CertificateManager(
            getSelfSignedKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            pwd);
        certManager.generateSelfSignedCertificate(SELF_SIGNED_CERT_ALIAS,
            getSelfSignedCertificateSubjectDN(),
            getSelfSignedCertificateValidity());
        exportCertificate(certManager, SELF_SIGNED_CERT_ALIAS,
            getTemporaryCertificatePath());

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            pwd);
        trustManager.addCertificate(SELF_SIGNED_CERT_ALIAS,
            new File(getTemporaryCertificatePath()));
        createFile(getKeystorePinPath(), pwd);
        f = new File(getTemporaryCertificatePath());
        f.delete();

        break;
      case JKS:
        certManager = new CertificateManager(
            sec.getKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        exportCertificate(certManager, sec.getAliasToUse(),
            getTemporaryCertificatePath());

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        trustManager.addCertificate(sec.getAliasToUse(),
            new File(getTemporaryCertificatePath()));
        createFile(getKeystorePinPath(), sec.getKeystorePassword());
        f = new File(getTemporaryCertificatePath());
        f.delete();
        break;
      case PKCS12:
        certManager = new CertificateManager(
            sec.getKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_PKCS12,
            sec.getKeystorePassword());
        exportCertificate(certManager, sec.getAliasToUse(),
            getTemporaryCertificatePath());

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        trustManager.addCertificate(sec.getAliasToUse(),
            new File(getTemporaryCertificatePath()));
        createFile(getKeystorePinPath(), sec.getKeystorePassword());
        f = new File(getTemporaryCertificatePath());
        f.delete();
        break;
      case PKCS11:
        certManager = new CertificateManager(
            CertificateManager.KEY_STORE_PATH_PKCS11,
            CertificateManager.KEY_STORE_TYPE_PKCS11,
            sec.getKeystorePassword());
        exportCertificate(certManager, sec.getAliasToUse(),
            getTemporaryCertificatePath());

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            sec.getKeystorePassword());
        trustManager.addCertificate(sec.getAliasToUse(),
            new File(getTemporaryCertificatePath()));
        createFile(getKeystorePinPath(), sec.getKeystorePassword());
        break;
      default:
        throw new IllegalStateException("Unknown certificate type: "+certType);
      }
      if (certType != SecurityOptions.CertificateType.NO_CERTIFICATE)
      {
        notifyListeners(getFormattedDone());
      }
    }
    catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
          getThrowableMsg(INFO_ERROR_CONFIGURING_CERTIFICATE.get(),
                  t), t);
    }
  }

  /**
   * This methods creates the base entry for the suffix based on the contents of
   * the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void createBaseEntry() throws ApplicationException {
    notifyListeners(getFormattedWithPoints(
        INFO_PROGRESS_CREATING_BASE_ENTRY.get(
                getUserData().getNewSuffixOptions().getBaseDn())));

    InstallerHelper helper = new InstallerHelper();
    String baseDn = getUserData().getNewSuffixOptions().getBaseDn();
    File tempFile = helper.createBaseEntryTempFile(baseDn);

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(getPath(getInstallation().getCurrentConfigurationFile()));

    argList.add("-n");
    argList.add(getBackendName());

    argList.add("-l");
    argList.add(tempFile.getAbsolutePath());

    argList.add("-q");

    String[] args = new String[argList.size()];
    argList.toArray(args);

    try
    {
      int result = helper.invokeImportLDIF(args);

      if (result != 0)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_ERROR_CREATING_BASE_ENTRY.get(), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
          getThrowableMsg(INFO_ERROR_CREATING_BASE_ENTRY.get(), t), t);
    }

    notifyListeners(getFormattedDone());
  }

  /**
   * This methods imports the contents of an LDIF file based on the contents of
   * the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void importLDIF() throws ApplicationException {
    MessageBuilder mb = new MessageBuilder();
    mb.append(getFormattedProgress(INFO_PROGRESS_IMPORTING_LDIF.get(
            getUserData().getNewSuffixOptions().getLDIFPath())));
    mb.append(getLineBreak());
    notifyListeners(mb.toMessage());

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(getPath(getInstallation().getCurrentConfigurationFile()));
    argList.add("-n");
    argList.add(getBackendName());
    argList.add("-l");
    argList.add(getUserData().getNewSuffixOptions().getLDIFPath());

    String[] args = new String[argList.size()];
    argList.toArray(args);

    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeImportLDIF(args);

      if (result != 0)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_ERROR_IMPORTING_LDIF.get(), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
          getThrowableMsg(INFO_ERROR_IMPORTING_LDIF.get(), t), t);
    }
  }

  /**
   * This methods imports automatically generated data based on the contents
   * of the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void importAutomaticallyGenerated() throws ApplicationException {
    File templatePath = createTemplateFile();
    int nEntries = getUserData().getNewSuffixOptions().getNumberEntries();
    MessageBuilder mb = new MessageBuilder();
    mb.append(getFormattedProgress(
            INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED.get(
                    String.valueOf(nEntries))));
    mb.append(getLineBreak());
    notifyListeners(mb.toMessage());

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(getPath(getInstallation().getCurrentConfigurationFile()));
    argList.add("-n");
    argList.add(getBackendName());
    argList.add("-t");
    argList.add(templatePath.getAbsolutePath());
    argList.add("-s"); // seed
    argList.add("0");

    String[] args = new String[argList.size()];
    argList.toArray(args);

    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeImportLDIF(args);

      if (result != 0)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_ERROR_IMPORT_LDIF_TOOL_RETURN_CODE.get(
                    Integer.toString(result)), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
          getThrowableMsg(INFO_ERROR_IMPORT_AUTOMATICALLY_GENERATED.get(
                  listToString(argList, " "), t.getLocalizedMessage()), t), t);
    }
  }

  /**
   * This method undoes the modifications made in other servers in terms of
   * replication.  This method assumes that we are aborting the Installer and
   * that is why it does not call checkAbort.
   */
  private void unconfigureRemote()
  {
    InitialLdapContext ctx = null;
    if (registeredNewServerOnRemote || createdAdministrator ||
    createdRemoteAds)
    {
      // Try to connect
      DataReplicationOptions repl = getUserData().getReplicationOptions();
      AuthenticationData auth = repl.getAuthenticationData();
      String ldapUrl = getLdapUrl(auth);
      String dn = auth.getDn();
      String pwd = auth.getPwd();
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_UNCONFIGURING_ADS_ON_REMOTE.get(getHostDisplay(auth))));
      try
      {
        if (auth.useSecureConnection())
        {
          ApplicationTrustManager trustManager = getTrustManager();
          trustManager.setHost(auth.getHostName());
          ctx = createLdapsContext(ldapUrl, dn, pwd,
              getDefaultLDAPTimeout(), null, trustManager);
        }
        else
        {
          ctx = createLdapContext(ldapUrl, dn, pwd,
              getDefaultLDAPTimeout(), null);
        }

        ADSContext adsContext = new ADSContext(ctx);
        if (createdRemoteAds)
        {
          adsContext.removeAdminData();
        }
        else
        {
          if (registeredNewServerOnRemote)
          {
            try
            {
              adsContext.unregisterServer(getNewServerAdsProperties());
            }
            catch (ADSContextException ace)
            {
              if (ace.getError() !=
                ADSContextException.ErrorType.NOT_YET_REGISTERED)
              {
                throw ace;
              }
              else
              {
                // Nothing to do: this may occur if the new server has been
                // unregistered on another server and the modification has
                // been already propagated by replication.
              }
            }
          }
          if (createdAdministrator)
          {
            adsContext.deleteAdministrator(getAdministratorProperties());
          }
        }
        notifyListeners(getFormattedDone());
        notifyListeners(getLineBreak());
      }
      catch (Throwable t)
      {
        notifyListeners(getFormattedError(t, true));
      }
      finally
      {
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
    InstallerHelper helper = new InstallerHelper();
    for (ServerDescriptor server : hmConfiguredRemoteReplication.keySet())
    {
      notifyListeners(getFormattedWithPoints(
          INFO_PROGRESS_UNCONFIGURING_REPLICATION_REMOTE.get(
                  server.getHostPort(true))));
      try
      {
        ctx = getRemoteConnection(server, getTrustManager());
        helper.unconfigureReplication(ctx,
            hmConfiguredRemoteReplication.get(server),
            server.getHostPort(true));
      }
      catch (ApplicationException ae)
      {
        notifyListeners(getFormattedError(ae, true));
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
      notifyListeners(getFormattedDone());
      notifyListeners(getLineBreak());
    }
  }

  /**
   * This method creates the replication configuration for the suffixes on the
   * the local server (and eventually in the remote servers) to synchronize
   * things.
   * NOTE: this method assumes that the server is running.
   * @throws ApplicationException if something goes wrong.
   */
  protected void configureReplication() throws ApplicationException
  {
    notifyListeners(getFormattedWithPoints(
        INFO_PROGRESS_CONFIGURING_REPLICATION.get()));

    InstallerHelper helper = new InstallerHelper();
    Set<Integer> knownServerIds = new HashSet<Integer>();
    Set<Integer> knownReplicationServerIds = new HashSet<Integer>();
    if (lastLoadedCache != null)
    {
      for (SuffixDescriptor suffix : lastLoadedCache.getSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          knownServerIds.add(replica.getReplicationId());
        }
      }
      for (ServerDescriptor server : lastLoadedCache.getServers())
      {
        Object v = server.getServerProperties().get
        (ServerDescriptor.ServerProperty.REPLICATION_SERVER_ID);
        if (v != null)
        {
          knownReplicationServerIds.add((Integer)v);
        }
      }
    }
    else
    {
      /* There is no ADS anywhere.  Just use the SuffixDescriptors we found */
      for (SuffixDescriptor suffix :
        getUserData().getSuffixesToReplicateOptions().getAvailableSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          knownServerIds.add(replica.getReplicationId());
          Object v = replica.getServer().getServerProperties().get
          (ServerDescriptor.ServerProperty.REPLICATION_SERVER_ID);
          if (v != null)
          {
            knownReplicationServerIds.add((Integer)v);
          }
        }
      }
    }
    Set<String> dns = new HashSet<String>();
    DataReplicationOptions rep = getUserData().getReplicationOptions();
    String newReplicationServer = getLocalReplicationServer();

    Map<String, Set<String>> replicationServers =
      new HashMap<String, Set<String>>();

    if (rep.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY)
    {
      String dn = getUserData().getNewSuffixOptions().getBaseDn();
      dns.add(dn);
      HashSet<String> h = new HashSet<String>();
      h.add(newReplicationServer);
      replicationServers.put(dn, h);
    }
    else
    {
      Set<SuffixDescriptor> suffixes =
        getUserData().getSuffixesToReplicateOptions().getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        dns.add(suffix.getDN());
        HashSet<String> h = new HashSet<String>();
        h.addAll(suffix.getReplicationServers());
        h.add(newReplicationServer);
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          ServerDescriptor server = replica.getServer();
          Integer replicationPort = getUserData().
          getRemoteWithNoReplicationPort().get(server);
          if (replicationPort != null)
          {
            h.add(server.getHostName()+":"+replicationPort);
          }
        }
        replicationServers.put(suffix.getDN(), h);
      }
    }

    InitialLdapContext ctx = null;
    try
    {
      ctx = createLocalContext();
      helper.configureReplication(ctx, dns, replicationServers,
          getUserData().getReplicationOptions().getReplicationPort(),
          getLocalHostPort(),
          knownReplicationServerIds, knownServerIds);
    }
    catch (ApplicationException ae)
    {
      throw ae;
    }
    catch (NamingException ne)
    {
      Message failedMsg = getThrowableMsg(
              INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne);
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR, failedMsg, ne);
    }
    finally
    {
      try
      {
        if (ctx != null)
        {
          ctx.close();
        }
      }
      catch (Throwable t)
      {
      }
    }
    notifyListeners(getFormattedDone());
    notifyListeners(getLineBreak());
    checkAbort();

    if (rep.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      Map<ServerDescriptor, Set<ReplicaDescriptor>> hm =
        new HashMap<ServerDescriptor, Set<ReplicaDescriptor>>();
      for (SuffixDescriptor suffix :
        getUserData().getSuffixesToReplicateOptions().getSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          Set<ReplicaDescriptor> replicas = hm.get(replica.getServer());
          if (replicas == null)
          {
            replicas = new HashSet<ReplicaDescriptor>();
            hm.put(replica.getServer(), replicas);
          }
          replicas.add(replica);
        }
      }
      for (ServerDescriptor server : hm.keySet())
      {
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_CONFIGURING_REPLICATION_REMOTE.get(
                    server.getHostPort(true))));
        Integer v = (Integer)server.getServerProperties().get(
            ServerDescriptor.ServerProperty.REPLICATION_SERVER_PORT);
        int replicationPort;
        if (v != null)
        {
          replicationPort = v;
        }
        else
        {
          replicationPort =
            getUserData().getRemoteWithNoReplicationPort().get(server);
        }
        dns = new HashSet<String>();
        for (ReplicaDescriptor replica : hm.get(server))
        {
          dns.add(replica.getSuffix().getDN());
        }

        ctx = getRemoteConnection(server, getTrustManager());
        ConfiguredReplication repl =
          helper.configureReplication(ctx, dns, replicationServers,
              replicationPort, server.getHostPort(true),
              knownReplicationServerIds, knownServerIds);
        hmConfiguredRemoteReplication.put(server, repl);

        try
        {
          ctx.close();
        }
        catch (Throwable t)
        {
        }
        notifyListeners(getFormattedDone());
        notifyListeners(getLineBreak());
        checkAbort();
      }
    }
  }

  /**
   * This methods enables this server as a Windows service.
   * @throws ApplicationException if something goes wrong.
   */
  protected void enableWindowsService() throws ApplicationException {
      notifyListeners(getFormattedProgress(
        INFO_PROGRESS_ENABLING_WINDOWS_SERVICE.get()));
      InstallerHelper helper = new InstallerHelper();
      helper.enableWindowsService();
  }

  /**
   * Updates the contents of the provided map with the localized summary
   * strings.
   * @param hmSummary the Map to be updated.
   */
  protected void initSummaryMap(
      Map<InstallProgressStep, Message> hmSummary)
  {
    hmSummary.put(InstallProgressStep.NOT_STARTED,
        getFormattedSummary(INFO_SUMMARY_INSTALL_NOT_STARTED.get()));
    hmSummary.put(InstallProgressStep.DOWNLOADING,
        getFormattedSummary(INFO_SUMMARY_DOWNLOADING.get()));
    hmSummary.put(InstallProgressStep.EXTRACTING,
        getFormattedSummary(INFO_SUMMARY_EXTRACTING.get()));
    hmSummary.put(InstallProgressStep.CONFIGURING_SERVER,
        getFormattedSummary(INFO_SUMMARY_CONFIGURING.get()));
    hmSummary.put(InstallProgressStep.CREATING_BASE_ENTRY,
        getFormattedSummary(INFO_SUMMARY_CREATING_BASE_ENTRY.get()));
    hmSummary.put(InstallProgressStep.IMPORTING_LDIF,
        getFormattedSummary(INFO_SUMMARY_IMPORTING_LDIF.get()));
    hmSummary.put(
        InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        getFormattedSummary(
            INFO_SUMMARY_IMPORTING_AUTOMATICALLY_GENERATED.get()));
    hmSummary.put(InstallProgressStep.CONFIGURING_REPLICATION,
        getFormattedSummary(INFO_SUMMARY_CONFIGURING_REPLICATION.get()));
    hmSummary.put(InstallProgressStep.STARTING_SERVER,
        getFormattedSummary(INFO_SUMMARY_STARTING.get()));
    hmSummary.put(InstallProgressStep.STOPPING_SERVER,
        getFormattedSummary(INFO_SUMMARY_STOPPING.get()));
    hmSummary.put(InstallProgressStep.CONFIGURING_ADS,
        getFormattedSummary(INFO_SUMMARY_CONFIGURING_ADS.get()));
    hmSummary.put(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES,
        getFormattedSummary(INFO_SUMMARY_INITIALIZE_REPLICATED_SUFFIXES.get()));
    hmSummary.put(InstallProgressStep.ENABLING_WINDOWS_SERVICE,
        getFormattedSummary(INFO_SUMMARY_ENABLING_WINDOWS_SERVICE.get()));
    hmSummary.put(InstallProgressStep.WAITING_TO_CANCEL,
        getFormattedSummary(INFO_SUMMARY_WAITING_TO_CANCEL.get()));
    hmSummary.put(InstallProgressStep.CANCELING,
        getFormattedSummary(INFO_SUMMARY_CANCELING.get()));

    Installation installation = getInstallation();
    String cmd = getPath(installation.getStatusPanelCommandFile());
    cmd = UIFactory.applyFontToHtml(cmd, UIFactory.INSTRUCTIONS_MONOSPACE_FONT);
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY,
            getFormattedSuccess(
                    INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY.get(
                            formatter.getFormattedText(
                                    Message.raw(getInstallationPath())),
                            INFO_GENERAL_SERVER_STOPPED.get(),
                            cmd)));
    hmSummary.put(InstallProgressStep.FINISHED_CANCELED,
            getFormattedSuccess(INFO_SUMMARY_INSTALL_FINISHED_CANCELED.get()));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR,
            getFormattedError(INFO_SUMMARY_INSTALL_FINISHED_WITH_ERROR.get(
                    INFO_GENERAL_SERVER_STOPPED.get(),
                    cmd)));
  }

  /**
   * Updates the messages in the summary with the state of the server.
   * @param hmSummary the Map containing the messages.
   */
  protected void updateSummaryWithServerState(
      Map<InstallProgressStep, Message> hmSummary)
  {
   Installation installation = getInstallation();
   String cmd = getPath(installation.getStatusPanelCommandFile());
   cmd = UIFactory.applyFontToHtml(cmd, UIFactory.INSTRUCTIONS_MONOSPACE_FONT);
   Message status;
   if (installation.getStatus().isServerRunning())
   {
     status = INFO_GENERAL_SERVER_STARTED.get();
   }
   else
   {
     status = INFO_GENERAL_SERVER_STOPPED.get();
   }
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY,
            getFormattedSuccess(
                    INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY.get(
                            formatter.getFormattedText(
                                    Message.raw(getInstallationPath())),
                            status,
                            cmd)));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR,
            getFormattedError(INFO_SUMMARY_INSTALL_FINISHED_WITH_ERROR.get(
                    status,
                    cmd)));
  }
  /**
   * Checks the value of <code>canceled</code> field and throws an
   * ApplicationException if true.  This indicates that the user has
   * canceled this operation and the process of aborting should begin
   * as soon as possible.
   *
   * @throws ApplicationException thrown if <code>canceled</code>
   */
  protected void checkAbort() throws ApplicationException {
    if (canceled) {
      setCurrentProgressStep(InstallProgressStep.CANCELING);
      notifyListeners(null);
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CANCELLED,
            INFO_UPGRADE_CANCELED.get(), null);
    }
  }

  /**
   * Writes the java home that we are using for the setup in a file.
   * This way we can use this java home even if the user has not set JAVA_HOME
   * when running the different scripts.
   *
   */
  protected void writeJavaHome()
  {
    try
    {
      // This isn't likely to happen, and it's not a serious problem even if
      // it does.
      SetupUtils.writeSetJavaHome(getInstallationPath());
    } catch (Exception e) {}
  }

  /**
   * These methods validate the data provided by the user in the panels and
   * update the userData object according to that content.
   *
   * @param cStep
   *          the current step of the wizard
   * @param qs QuickStart controller
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  public void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException
  {
    if (cStep == SERVER_SETTINGS)
    {
      updateUserDataForServerSettingsPanel(qs);
    }
    else if (cStep == REPLICATION_OPTIONS)
    {
      updateUserDataForReplicationOptionsPanel(qs);
    }
    else if (cStep == CREATE_GLOBAL_ADMINISTRATOR)
    {
      updateUserDataForCreateAdministratorPanel(qs);
    }
    else if (cStep == SUFFIXES_OPTIONS)
    {
      updateUserDataForSuffixesOptionsPanel(qs);
    }
    else if (cStep == REMOTE_REPLICATION_PORTS)
    {
      updateUserDataForRemoteReplicationPorts(qs);
    }
    else if (cStep == NEW_SUFFIX_OPTIONS)
    {
      updateUserDataForNewSuffixOptionsPanel(qs);
    }
    else if (cStep ==  REVIEW)
    {
      updateUserDataForReviewPanel(qs);
    }
  }

  /**
   * Returns the default backend name (the one that will be created).
   * @return the default backend name (the one that will be created).
   */
  protected String getBackendName()
  {
    return "userRoot";
  }

  /**
   * Sets the current progress step of the installation process.
   * @param currentProgressStep the current progress step of the installation
   * process.
   */
  protected void setCurrentProgressStep(InstallProgressStep currentProgressStep)
  {
    if (currentProgressStep != null) {
      this.completedProgress.add(currentProgressStep);
    }
    this.currentProgressStep = currentProgressStep;
  }

  /**
   * This methods updates the data on the server based on the contents of the
   * UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  protected void createData() throws ApplicationException
  {
    if (createNotReplicatedSuffix())
    {
      switch (getUserData().getNewSuffixOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        currentProgressStep = InstallProgressStep.CREATING_BASE_ENTRY;
        notifyListeners(getTaskSeparator());
        createBaseEntry();
        break;
      case IMPORT_FROM_LDIF_FILE:
        currentProgressStep = InstallProgressStep.IMPORTING_LDIF;
        notifyListeners(getTaskSeparator());
        importLDIF();
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        currentProgressStep =
          InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED;
        notifyListeners(getTaskSeparator());
        importAutomaticallyGenerated();
        break;
      }
    }
  }

  /**
   * This method initialize the contents of the synchronized servers with the
   * contents of the first server we find.
   * @throws ApplicationException if something goes wrong.
   */
  protected void initializeSuffixes() throws ApplicationException
  {
    InitialLdapContext ctx = null;
    try
    {
      ctx = createLocalContext();
    }
    catch (Throwable t)
    {
      Message failedMsg =
              getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), t);
      try
      {
        if (ctx != null)
        {
          ctx.close();
        }
      }
      catch (Throwable t1)
      {
      }
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR, failedMsg, t);
    }

    Set<SuffixDescriptor> suffixes =
      getUserData().getSuffixesToReplicateOptions().getSuffixes();
    int i = 0;
    for (SuffixDescriptor suffix : suffixes)
    {
      String dn = suffix.getDN();

      ReplicaDescriptor replica = suffix.getReplicas().iterator().next();
      ServerDescriptor server = replica.getServer();
      String hostPort = server.getHostPort(true);

      notifyListeners(getFormattedProgress(
        INFO_PROGRESS_INITIALIZING_SUFFIX.get(dn, hostPort)));
      notifyListeners(getLineBreak());
      try
      {
        int replicationId = replica.getReplicationId();
        if (replicationId == -1)
        {
          /**
           * This occurs if the remote server had not replication configured.
           */
          InitialLdapContext rCtx = null;
          try
          {
            rCtx = getRemoteConnection(server, getTrustManager());
            ServerDescriptor s = ServerDescriptor.createStandalone(rCtx);
            for (ReplicaDescriptor r : s.getReplicas())
            {
              if (areDnsEqual(r.getSuffix().getDN(), dn))
              {
                replicationId = r.getReplicationId();
              }
            }
          }
          catch (NamingException ne)
          {
            throw new ApplicationException(
                ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
                INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
                        server.getHostPort(true),
                        ne.getLocalizedMessage()), ne);
          }
          finally
          {
            try
            {
              rCtx.close();
            }
            catch (Throwable t)
            {
            }
          }
        }
        try
        {
          Thread.sleep(3000);
        }
        catch (Throwable t) {}
        int nTries = 5;
        boolean initDone = false;
        while (!initDone)
        {
          try
          {
            initializeSuffix(ctx, replicationId, dn, true, hostPort);
            initDone = true;
          }
          catch (PeerNotFoundException pnfe)
          {
            LOG.log(Level.INFO, "Peer could not be found");
            if (nTries == 1)
            {
              throw new ApplicationException(
                  ApplicationReturnCode.ReturnCode.APPLICATION_ERROR,
                  pnfe.getMessageObject(), null);
            }
            try
            {
              Thread.sleep((5 - nTries) * 3000);
            }
            catch (Throwable t)
            {
            }
          }
          nTries--;
        }
      }
      catch (ApplicationException ae)
      {
        try
        {
          if (ctx != null)
          {
            ctx.close();
          }
        }
        catch (Throwable t1)
        {
        }
        throw ae;
      }
      if (i > 0)
      {
        notifyListeners(getLineBreak());
      }
      i++;
      checkAbort();
    }
  }

  /**
   * This method updates the ADS contents (and creates the according suffixes).
   * NOTE: this method assumes that the server is running.
   * @throws ApplicationException if something goes wrong.
   */
  protected void updateADS() throws ApplicationException
  {
    /*
     * First check if the remote server contains an ADS: if it is the case the
     * best is to update its contents with the new data and then configure the
     * local server to be replicated with the remote server.
     */
    DataReplicationOptions repl = getUserData().getReplicationOptions();
    boolean remoteServer =
      repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
    InitialLdapContext localCtx = null;
    if (remoteServer)
    {
      // Try to connect
      AuthenticationData auth = repl.getAuthenticationData();
      String ldapUrl = getLdapUrl(auth);
      String dn = auth.getDn();
      String pwd = auth.getPwd();
      InitialLdapContext ctx = null;
      try
      {
        if (auth.useSecureConnection())
        {
          ApplicationTrustManager trustManager = getTrustManager();
          trustManager.setHost(auth.getHostName());
          ctx = createLdapsContext(ldapUrl, dn, pwd,
              getDefaultLDAPTimeout(), null, trustManager);
        }
        else
        {
          ctx = createLdapContext(ldapUrl, dn, pwd,
              getDefaultLDAPTimeout(), null);
        }

        ADSContext adsContext = new ADSContext(ctx);
        boolean hasAdminData = adsContext.hasAdminData();
        if (hasAdminData)
        {
          /* Add global administrator if the user specified one. */
          if (getUserData().mustCreateAdministrator())
          {
            try
            {
              notifyListeners(getFormattedWithPoints(
                  INFO_PROGRESS_CREATING_ADMINISTRATOR.get()));
              adsContext.createAdministrator(getAdministratorProperties());
              createdAdministrator = true;
              notifyListeners(getFormattedDone());
              notifyListeners(getLineBreak());
              checkAbort();
            }
            catch (ADSContextException ade)
            {
              if (ade.getError() ==
                ADSContextException.ErrorType.ALREADY_REGISTERED)
              {
                notifyListeners(getFormattedWarning(
                    INFO_ADMINISTRATOR_ALREADY_REGISTERED.get()));
              }
              else
              {
                throw ade;
              }
            }
          }
        }
        else
        {
          notifyListeners(getFormattedWithPoints(
              INFO_PROGRESS_CREATING_ADS_ON_REMOTE.get(getHostDisplay(auth))));

          adsContext.createAdminData(null);
          adsContext.createAdministrator(getAdministratorProperties());
          adsContext.registerServer(
              getRemoteServerProperties(auth.getHostName(),
                  adsContext.getDirContext()));
          createdRemoteAds = true;
          notifyListeners(getFormattedDone());
          notifyListeners(getLineBreak());
          checkAbort();
        }
        /* Configure local server to have an ADS */
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_CREATING_ADS.get()));
        try
        {
          localCtx = createLocalContext();
        }
        catch (Throwable t)
        {
          Message failedMsg = getThrowableMsg(
                  INFO_ERROR_CONNECTING_TO_LOCAL.get(), t);
          throw new ApplicationException(
              ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
              failedMsg, t);
        }
        createLocalAds(localCtx, false);
        notifyListeners(getFormattedDone());
        notifyListeners(getLineBreak());
        checkAbort();

        lastLoadedCache = new TopologyCache(adsContext, getTrustManager());
        lastLoadedCache.reloadTopology();
        Set<Integer> knownServerIds = new HashSet<Integer>();
        Set<Integer> knownReplicationServerIds = new HashSet<Integer>();
        Set<String> replicationServers = new HashSet<String>();
        replicationServers.add(getLocalReplicationServer());
        Set<ServerDescriptor> remoteWithAds = new HashSet<ServerDescriptor>();

        for (SuffixDescriptor suffix : lastLoadedCache.getSuffixes())
        {
          for (ReplicaDescriptor replica : suffix.getReplicas())
          {
            knownServerIds.add(replica.getReplicationId());
          }
          if (areDnsEqual(suffix.getDN(),
              ADSContext.getAdministrationSuffixDN()))
          {
            replicationServers.addAll(suffix.getReplicationServers());
            for (ReplicaDescriptor replica : suffix.getReplicas())
            {
              ServerDescriptor server = replica.getServer();
              Object e = server.getServerProperties().get
              (ServerDescriptor.ServerProperty.IS_REPLICATION_SERVER);
              if (Boolean.TRUE.equals(e))
              {
                replicationServers.add(server.getHostName()+":"+
                    server.getServerProperties().get
                    (ServerDescriptor.ServerProperty.REPLICATION_SERVER_PORT));
              }
              remoteWithAds.add(server);
            }
          }
        }
        for (ServerDescriptor server : lastLoadedCache.getServers())
        {
          Object v = server.getServerProperties().get
          (ServerDescriptor.ServerProperty.REPLICATION_SERVER_ID);
          if (v != null)
          {
            knownReplicationServerIds.add((Integer)v);
          }
        }
        InstallerHelper helper = new InstallerHelper();
        Set<String> dns = new HashSet<String>();
        dns.add(ADSContext.getAdministrationSuffixDN());
        Map <String, Set<String>>hmRepServers =
          new HashMap<String, Set<String>>();
        hmRepServers.put(ADSContext.getAdministrationSuffixDN(),
            replicationServers);
        for (ServerDescriptor server : remoteWithAds)
        {
          Integer replicationPort = (Integer)server.getServerProperties().get
          (ServerDescriptor.ServerProperty.REPLICATION_SERVER_PORT);
          if (replicationPort != null)
          {
            InitialLdapContext rCtx = getRemoteConnection(server,
                getTrustManager());
            helper.configureReplication(rCtx, dns, hmRepServers,
                replicationPort, server.getHostPort(true),
                knownReplicationServerIds, knownServerIds);
            try
            {
              rCtx.close();
            }
            catch (Throwable t)
            {
            }
          }
        }
        /* Register new server data. */
        try
        {
          adsContext.registerServer(getNewServerAdsProperties());
          registeredNewServerOnRemote = true;
        }
        catch (ADSContextException adse)
        {
          if (adse.getError() ==
            ADSContextException.ErrorType.ALREADY_REGISTERED)
          {
            LOG.log(Level.WARNING, "Server already registered. Unregistering "+
                "and registering server");
            /* This might occur after registering and unregistering a server */
            adsContext.unregisterServer(getNewServerAdsProperties());
            adsContext.registerServer(getNewServerAdsProperties());
          }
          else
          {
            throw adse;
          }
        }

        /* Configure replication on local server */
        helper.configureReplication(localCtx, dns, hmRepServers,
            getUserData().getReplicationOptions().getReplicationPort(),
            getLocalHostPort(), knownReplicationServerIds, knownServerIds);

        /* Initialize local ADS contents. */
        ServerDescriptor server = ServerDescriptor.createStandalone(ctx);
        for (ReplicaDescriptor replica : server.getReplicas())
        {
          if (areDnsEqual(replica.getSuffix().getDN(),
              ADSContext.getAdministrationSuffixDN()))
          {
            notifyListeners(getFormattedWithPoints(
                INFO_PROGRESS_INITIALIZING_ADS.get()));

            int replicationId = replica.getReplicationId();
            int nTries = 5;
            boolean initDone = false;
            while (!initDone)
            {
              try
              {
                initializeSuffix(localCtx, replicationId,
                    ADSContext.getAdministrationSuffixDN(),
                    false, server.getHostPort(true));
                initDone = true;
              }
              catch (PeerNotFoundException pnfe)
              {
                LOG.log(Level.INFO, "Peer could not be found");
                if (nTries == 1)
                {
                  throw new ApplicationException(
                      ApplicationReturnCode.ReturnCode.APPLICATION_ERROR,
                      pnfe.getMessageObject(), null);
                }
                try
                {
                  Thread.sleep((5 - nTries) * 3000);
                }
                catch (Throwable t)
                {
                }
              }
              nTries--;
            }
            notifyListeners(getFormattedDone());
            notifyListeners(getLineBreak());
            checkAbort();
            break;
          }
        }
      }
      catch (NoPermissionException x)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_CANNOT_CONNECT_TO_REMOTE_PERMISSIONS.get(
                    getHostDisplay(auth)), x);
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
                    getHostDisplay(auth), ne.getLocalizedMessage()), ne);
      }
      catch (TopologyCacheException tpe)
      {
        LOG.log(Level.WARNING, "Error reloading topology cache to "+
            "configure ADS replication.", tpe);
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_BUG_MSG.get(), tpe);
      }
      catch (ADSContextException ace)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_REMOTE_ADS_EXCEPTION.get(
                    getHostDisplay(auth), ace.getReason()), ace);
      }
      finally
      {
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
        if (localCtx != null)
        {
          try
          {
            localCtx.close();
          }
          catch (Throwable t)
          {
          }
        }
      }
    }
    else
    {
      try
      {
        /* Configure local server to have an ADS */
        notifyListeners(getFormattedWithPoints(
            INFO_PROGRESS_CREATING_ADS.get()));
        try
        {
          localCtx = createLocalContext();
        }
        catch (Throwable t)
        {
          Message failedMsg = getThrowableMsg(
                  INFO_ERROR_CONNECTING_TO_LOCAL.get(),
                  t);
          throw new ApplicationException(
              ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
              failedMsg, t);
        }
        createLocalAds(localCtx, true);
        int replicationPort =
          getUserData().getReplicationOptions().getReplicationPort();
        Set<String> dns = new HashSet<String>();
        dns.add(ADSContext.getAdministrationSuffixDN());
        Map<String, Set<String>> hmReplicationServers =
          new HashMap<String, Set<String>>();
        HashSet<String> replicationServers = new HashSet<String>();
        String newReplicationServer = getLocalReplicationServer();
        replicationServers.add(newReplicationServer);
        hmReplicationServers.put(ADSContext.getAdministrationSuffixDN(),
            replicationServers);
        InstallerHelper helper = new InstallerHelper();

        helper.configureReplication(localCtx, dns, hmReplicationServers,
            replicationPort, getLocalHostPort(),
            new HashSet<Integer>(), new HashSet<Integer>());
        notifyListeners(getFormattedDone());
        notifyListeners(getLineBreak());
      }
      catch (ADSContextException ace)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
            INFO_ADS_EXCEPTION.get(ace.toString()), ace);
      }
      finally
      {
        if (localCtx != null)
        {
          try
          {
            localCtx.close();
          }
          catch (Throwable t)
          {
          }
        }
      }
    }
  }

  /**
   * Tells whether we must create a suffix that we are not going to replicate
   * with other servers or not.
   * @return <CODE>true</CODE> if we must create a new suffix and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean createNotReplicatedSuffix()
  {
    boolean createSuffix;

    DataReplicationOptions repl =
      getUserData().getReplicationOptions();

    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();

    createSuffix =
      (repl.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY) ||
      (repl.getType() == DataReplicationOptions.Type.STANDALONE) ||
      (suf.getType() == SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY);

    return createSuffix;
  }

  /**
   * Returns <CODE>true</CODE> if we must configure replication and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must configure replication and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustConfigureReplication()
  {
    return getUserData().getReplicationOptions().getType() !=
      DataReplicationOptions.Type.STANDALONE;
  }

  /**
   * Returns <CODE>true</CODE> if we must create the ADS and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must create the ADS and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustCreateAds()
  {
    return getUserData().getReplicationOptions().getType() !=
      DataReplicationOptions.Type.STANDALONE;
  }

  /**
   * Returns <CODE>true</CODE> if we must start the server and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must start the server and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustStart()
  {
    return getUserData().getStartServer() || mustCreateAds();
  }

  /**
   * Returns <CODE>true</CODE> if we must stop the server and
   * <CODE>false</CODE> otherwise.
   * The server might be stopped if the user asked not to start it at the
   * end of the installation and it was started temporarily to update its
   * configuration.
   * @return <CODE>true</CODE> if we must stop the server and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustStop()
  {
    return !getUserData().getStartServer() && mustCreateAds();
  }

  /**
   * Returns <CODE>true</CODE> if we must initialize suffixes and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we must initialize suffixes and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean mustInitializeSuffixes()
  {
    return getUserData().getReplicationOptions().getType() ==
      DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
  }

  private String getLdapUrl(AuthenticationData auth)
  {
    String ldapUrl;
    if (auth.useSecureConnection())
    {
      ldapUrl = "ldaps://"+auth.getHostName()+":"+auth.getPort();
    }
    else
    {
      ldapUrl = "ldap://"+auth.getHostName()+":"+auth.getPort();
    }
    return ldapUrl;
  }

  private String getHostDisplay(AuthenticationData auth)
  {
    return auth.getHostName()+":"+auth.getPort();
  }

  private Map<ADSContext.ServerProperty, Object> getNewServerAdsProperties()
  {
    Map<ADSContext.ServerProperty, Object> serverProperties =
      new HashMap<ADSContext.ServerProperty, Object>();
    serverProperties.put(ADSContext.ServerProperty.HOST_NAME,
          getUserData().getHostName());
    serverProperties.put(ADSContext.ServerProperty.LDAP_PORT,
        String.valueOf(getUserData().getServerPort()));
    serverProperties.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");

    // TODO: even if the user does not configure SSL maybe we should choose
    // a secure port that is not being used and that we can actually use.
    SecurityOptions sec = getUserData().getSecurityOptions();
    if (sec.getEnableSSL())
    {
      serverProperties.put(ADSContext.ServerProperty.LDAPS_PORT,
          String.valueOf(sec.getSslPort()));
      serverProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "true");
    }
    else
    {
      serverProperties.put(ADSContext.ServerProperty.LDAPS_PORT, "636");
      serverProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "false");
    }

    if (sec.getEnableStartTLS())
    {
      serverProperties.put(ADSContext.ServerProperty.STARTTLS_ENABLED, "true");
    }
    else
    {
      serverProperties.put(ADSContext.ServerProperty.STARTTLS_ENABLED, "false");
    }

    serverProperties.put(ADSContext.ServerProperty.JMX_PORT, "1689");
    serverProperties.put(ADSContext.ServerProperty.JMX_ENABLED, "false");

    String path;
    if (isWebStart())
    {
      path = getUserData().getServerLocation();
    }
    else
    {
      path = getInstallPathFromClasspath();
    }
    serverProperties.put(ADSContext.ServerProperty.INSTANCE_PATH, path);

    String serverID = serverProperties.get(ADSContext.ServerProperty.HOST_NAME)+
    ":"+getUserData().getServerPort();

    /* TODO: do we want to ask this specifically to the user? */
    serverProperties.put(ADSContext.ServerProperty.ID, serverID);

    serverProperties.put(ADSContext.ServerProperty.HOST_OS,
        getOSString());
    return serverProperties;
  }

  private Map<ADSContext.AdministratorProperty, Object>
  getAdministratorProperties()
  {
    Map<ADSContext.AdministratorProperty, Object> adminProperties =
      new HashMap<ADSContext.AdministratorProperty, Object>();
    adminProperties.put(ADSContext.AdministratorProperty.UID,
        getUserData().getGlobalAdministratorUID());
    adminProperties.put(ADSContext.AdministratorProperty.PASSWORD,
        getUserData().getGlobalAdministratorPassword());
    adminProperties.put(ADSContext.AdministratorProperty.DESCRIPTION,
        INFO_GLOBAL_ADMINISTRATOR_DESCRIPTION.get().toString());
    return adminProperties;
  }

  /**
   * Validate the data provided by the user in the server settings panel and
   * update the userData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForServerSettingsPanel(QuickSetup qs)
      throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();
    Message confirmationMsg = null;

    if (isWebStart())
    {
      // Check the server location
      String serverLocation = qs.getFieldStringValue(FieldName.SERVER_LOCATION);

      if ((serverLocation == null) || ("".equals(serverLocation.trim())))
      {
        errorMsgs.add(INFO_EMPTY_SERVER_LOCATION.get());
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (!parentDirectoryExists(serverLocation))
      {
        String existingParentDirectory = null;
        File f = new File(serverLocation);
        while ((existingParentDirectory == null) && (f != null))
        {
          f = f.getParentFile();
          if ((f != null) && f.exists())
          {
            if (f.isDirectory())
            {
              existingParentDirectory = f.getAbsolutePath();
            }
            else
            {
              // The parent path is a file!
              f = null;
            }
          }
        }
        if (existingParentDirectory == null)
        {
          errorMsgs.add(INFO_PARENT_DIRECTORY_COULD_NOT_BE_FOUND.get(
                  serverLocation));
          qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
        }
        else
        {
          if (!canWrite(existingParentDirectory))
          {
            errorMsgs.add(INFO_DIRECTORY_NOT_WRITABLE.get(
                    existingParentDirectory));
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
          }
          else if (!hasEnoughSpace(existingParentDirectory,
              getRequiredInstallSpace()))
          {
            long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
            errorMsgs.add(INFO_NOT_ENOUGH_DISK_SPACE.get(
                    existingParentDirectory, String.valueOf(requiredInMb)));
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
          }
          else
          {
            confirmationMsg =
              INFO_PARENT_DIRECTORY_DOES_NOT_EXIST_CONFIRMATION.get(
                      serverLocation);
            getUserData().setServerLocation(serverLocation);
          }
        }
      } else if (fileExists(serverLocation))
      {
        errorMsgs.add(INFO_FILE_EXISTS.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else if (directoryExistsAndIsNotEmpty(serverLocation))
      {
        errorMsgs.add(INFO_DIRECTORY_EXISTS_NOT_EMPTY.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else if (!canWrite(serverLocation))
      {
        errorMsgs.add(INFO_DIRECTORY_NOT_WRITABLE.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);

      } else if (!hasEnoughSpace(serverLocation,
          getRequiredInstallSpace()))
      {
        long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
        errorMsgs.add(INFO_NOT_ENOUGH_DISK_SPACE.get(
                serverLocation, String.valueOf(requiredInMb)));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);

      } else if (isWindows() && (serverLocation.indexOf("%") != -1))
      {
        errorMsgs.add(INFO_INVALID_CHAR_IN_PATH.get("%"));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else
      {
        getUserData().setServerLocation(serverLocation);
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, false);
      }
    }

    // Check the host is not empty.
    // TODO: check that the host name is valid...
    String hostName = qs.getFieldStringValue(FieldName.HOST_NAME);
    if ((hostName == null) || hostName.trim().length() == 0)
    {
      errorMsgs.add(INFO_EMPTY_HOST_NAME.get());
      qs.displayFieldInvalid(FieldName.HOST_NAME, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.HOST_NAME, false);
      getUserData().setHostName(hostName);
    }

    // Check the port
    String sPort = qs.getFieldStringValue(FieldName.SERVER_PORT);
    int port = -1;
    try
    {
      port = Integer.parseInt(sPort);
      if ((port < MIN_PORT_VALUE) || (port > MAX_PORT_VALUE))
      {
        errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(
                String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      } else if (!canUseAsPort(port))
      {
        if (isPriviledgedPort(port))
        {
          errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
            String.valueOf(port)));
        } else
        {
          errorMsgs.add(INFO_CANNOT_BIND_PORT.get(String.valueOf(port)));
        }
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);

      } else
      {
        getUserData().setServerPort(port);
        qs.displayFieldInvalid(FieldName.SERVER_PORT, false);
      }

    } catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(
              String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE)));
      qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
    }

    // Check the secure port
    SecurityOptions sec =
      (SecurityOptions)qs.getFieldValue(FieldName.SECURITY_OPTIONS);
    int securePort = sec.getSslPort();
    if (sec.getEnableSSL())
    {
      if ((securePort < MIN_PORT_VALUE) || (securePort > MAX_PORT_VALUE))
      {
        errorMsgs.add(INFO_INVALID_SECURE_PORT_VALUE_RANGE.get(
                String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
      } else if (!canUseAsPort(securePort))
      {
        if (isPriviledgedPort(securePort))
        {
          errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
            String.valueOf(securePort)));
        } else
        {
          errorMsgs.add(INFO_CANNOT_BIND_PORT.get(String.valueOf(securePort)));
        }
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);

      }
      else if (port == securePort)
      {
        errorMsgs.add(INFO_EQUAL_PORTS.get());
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      }
      else
      {
        getUserData().setSecurityOptions(sec);
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, false);
      }
    }
    else
    {
      getUserData().setSecurityOptions(sec);
      qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, false);
    }


    // Check the Directory Manager DN
    String dmDn = qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_DN);

    if ((dmDn == null) || (dmDn.trim().length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_DIRECTORY_MANAGER_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else if (!isDn(dmDn))
    {
      errorMsgs.add(INFO_NOT_A_DIRECTORY_MANAGER_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else if (isConfigurationDn(dmDn))
    {
      errorMsgs.add(INFO_DIRECTORY_MANAGER_DN_IS_CONFIG_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else
    {
      getUserData().setDirectoryManagerDn(dmDn);
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, false);
    }

    // Check the provided passwords
    String pwd1 = qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_PWD);
    String pwd2 =
            qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM);
    if (pwd1 == null)
    {
      pwd1 = "";
    }

    boolean pwdValid = true;
    if (!pwd1.equals(pwd2))
    {
      errorMsgs.add(INFO_NOT_EQUAL_PWD.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM, true);
      pwdValid = false;

    }
    if (pwd1.length() < MIN_DIRECTORY_MANAGER_PWD)
    {
      errorMsgs.add(INFO_PWD_TOO_SHORT.get(
              String.valueOf(MIN_DIRECTORY_MANAGER_PWD)));
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD, true);
      if ((pwd2 == null) || (pwd2.length() < MIN_DIRECTORY_MANAGER_PWD))
      {
        qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM, true);
      }
      pwdValid = false;
    }

    if (pwdValid)
    {
      getUserData().setDirectoryManagerPwd(pwd1);
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD, false);
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM, false);
    }

    // For the moment do not enable JMX
    int defaultJMXPort =
      UserData.getDefaultJMXPort(new int[] {port, securePort});
    if (defaultJMXPort != -1)
    {
      //getUserData().setServerJMXPort(defaultJMXPort);
      getUserData().setServerJMXPort(-1);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.SERVER_SETTINGS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
    if (confirmationMsg != null)
    {
      throw new UserDataConfirmationException(Step.SERVER_SETTINGS,
          confirmationMsg);
    }
  }

  /**
   * Validate the data provided by the user in the data options panel and update
   * the userData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForReplicationOptionsPanel(QuickSetup qs)
      throws UserDataException {
    boolean hasGlobalAdministrators = false;
    Integer replicationPort = -1;
    String host = null;
    Integer port = null;
    String dn = null;
    String pwd = null;
    boolean isSecure = Boolean.TRUE.equals(qs.getFieldValue(
        FieldName.REMOTE_SERVER_IS_SECURE_PORT));
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    DataReplicationOptions.Type type = (DataReplicationOptions.Type)
      qs.getFieldValue(FieldName.REPLICATION_OPTIONS);
    host = qs.getFieldStringValue(FieldName.REMOTE_SERVER_HOST);
    dn = qs.getFieldStringValue(FieldName.REMOTE_SERVER_DN);
    pwd = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PWD);

    if (type != DataReplicationOptions.Type.STANDALONE)
    {
      // Check replication port
      replicationPort = checkReplicationPort(qs, errorMsgs);
    }

    UserDataConfirmationException confirmEx = null;
    switch (type)
    {
    case IN_EXISTING_TOPOLOGY:
    {
      String sPort = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PORT);
      checkRemoteHostPortDnAndPwd(host, sPort, dn, pwd, qs, errorMsgs);

      if (errorMsgs.size() == 0)
      {
        port = Integer.parseInt(sPort);
        // Try to connect
        boolean[] globalAdmin = {hasGlobalAdministrators};
        String[] effectiveDn = {dn};
        try
        {
          updateUserDataWithADS(host, port, dn, pwd, qs, errorMsgs,
              globalAdmin, effectiveDn);
        }
        catch (UserDataConfirmationException e)
        {
          confirmEx = e;
        }
        hasGlobalAdministrators = globalAdmin[0];
        dn = effectiveDn[0];
      }
      break;
    }
    case STANDALONE:
    {
      getUserData().setSuffixesToReplicateOptions(
          new SuffixesToReplicateOptions(
              SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE,
              new HashSet<SuffixDescriptor>(),
              new HashSet<SuffixDescriptor>()));
      break;
    }
    case FIRST_IN_TOPOLOGY:
    {
      getUserData().setSuffixesToReplicateOptions(
          new SuffixesToReplicateOptions(
              SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY,
              new HashSet<SuffixDescriptor>(),
              new HashSet<SuffixDescriptor>()));
      break;
    }
    default:
      throw new IllegalStateException("Do not know what to do with type: "+
          type);
    }

    if (errorMsgs.size() == 0)
    {
      AuthenticationData auth = new AuthenticationData();
      auth.setHostName(host);
      if (port != null)
      {
        auth.setPort(port);
      }
      auth.setDn(dn);
      auth.setPwd(pwd);
      auth.setUseSecureConnection(isSecure);

      DataReplicationOptions repl = new DataReplicationOptions(type,
          auth, replicationPort);
      getUserData().setReplicationOptions(repl);

      getUserData().createAdministrator(!hasGlobalAdministrators &&
      type == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY);
    }
    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.REPLICATION_OPTIONS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
    if (confirmEx != null)
    {
      throw confirmEx;
    }
  }

  private int checkReplicationPort(QuickSetup qs, ArrayList<Message> errorMsgs)
  {
    int replicationPort = -1;
    String sPort = qs.getFieldStringValue(FieldName.REPLICATION_PORT);
    try
    {
      replicationPort = Integer.parseInt(sPort);
      if ((replicationPort < MIN_PORT_VALUE) ||
          (replicationPort > MAX_PORT_VALUE))
      {
        errorMsgs.add(INFO_INVALID_REPLICATION_PORT_VALUE_RANGE.get(
                String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      } else if (!canUseAsPort(replicationPort))
      {
        if (isPriviledgedPort(replicationPort))
        {
          errorMsgs.add(INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(
                  String.valueOf(replicationPort)));
        } else
        {
          errorMsgs.add(INFO_CANNOT_BIND_PORT.get(
                  String.valueOf(replicationPort)));
        }
        qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);

      } else
      {
        /* Check that we did not chose this port for another protocol */
        SecurityOptions sec = getUserData().getSecurityOptions();
        if ((replicationPort == getUserData().getServerPort()) ||
            (replicationPort == getUserData().getServerJMXPort()) ||
            ((replicationPort == sec.getSslPort()) && sec.getEnableSSL()))
        {
          errorMsgs.add(
              INFO_REPLICATION_PORT_ALREADY_CHOSEN_FOR_OTHER_PROTOCOL.get());
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
        }
        else
        {
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, false);
        }
      }

    } catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_REPLICATION_PORT_VALUE_RANGE.get(
              String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE)));
      qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
    }
    return replicationPort;
  }

  private void checkRemoteHostPortDnAndPwd(String host, String sPort, String dn,
      String pwd, QuickSetup qs, ArrayList<Message> errorMsgs)
  {
    // Check host
    if ((host == null) || (host.length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_HOST.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, false);
    }

    // Check port
    try
    {
      Integer.parseInt(sPort);
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, false);
    }
    catch (Throwable t)
    {
      errorMsgs.add(INFO_INVALID_REMOTE_PORT.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
    }

    // Check dn
    if ((dn == null) || (dn.length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_DN.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, false);
    }

    // Check password
    if ((pwd == null) || (pwd.length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_PWD.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, false);
    }
  }

  private void updateUserDataWithADS(String host, int port, String dn,
      String pwd, QuickSetup qs, ArrayList<Message> errorMsgs,
      boolean[] hasGlobalAdministrators,
      String[] effectiveDn) throws UserDataException
  {
    String ldapUrl;
    host = getHostNameForLdapUrl(host);
    boolean isSecure = Boolean.TRUE.equals(qs.getFieldValue(
        FieldName.REMOTE_SERVER_IS_SECURE_PORT));
    if (isSecure)
    {
      ldapUrl = "ldaps://"+host+":"+port;
    }
    else
    {
      ldapUrl = "ldap://"+host+":"+port;
    }
    InitialLdapContext ctx = null;

    ApplicationTrustManager trustManager = getTrustManager();
    trustManager.setHost(host);
    trustManager.resetLastRefusedItems();
    try
    {
      effectiveDn[0] = dn;
      try
      {
        if (isSecure)
        {
          ctx = createLdapsContext(ldapUrl, dn, pwd,
              getDefaultLDAPTimeout(), null, trustManager);
        }
        else
        {
          ctx = createLdapContext(ldapUrl, dn, pwd,
              getDefaultLDAPTimeout(), null);
        }
      }
      catch (Throwable t)
      {
        if (!isCertificateException(t))
        {
          // Try using a global administrator
          dn = ADSContext.getAdministratorDN(dn);
          effectiveDn[0] = dn;
          if (isSecure)
          {
            ctx = createLdapsContext(ldapUrl, dn, pwd,
                getDefaultLDAPTimeout(), null, trustManager);
          }
          else
          {
            ctx = createLdapContext(ldapUrl, dn, pwd,
                getDefaultLDAPTimeout(), null);
          }
        }
        else
        {
          throw t;
        }
      }

      ADSContext adsContext = new ADSContext(ctx);
      if (adsContext.hasAdminData())
      {
        /* Check if there are already global administrators */
        Set administrators = adsContext.readAdministratorRegistry();
        if (administrators.size() > 0)
        {
          hasGlobalAdministrators[0] = true;
        }
        else
        {
          hasGlobalAdministrators[0] = false;
        }
        Set<TopologyCacheException> exceptions =
        updateUserDataWithSuffixesInADS(adsContext, trustManager);
        Set<String> exceptionMsgs = new LinkedHashSet<String>();
        /* Check the exceptions and see if we throw them or not. */
        for (TopologyCacheException e : exceptions)
        {
          switch (e.getType())
          {
          case NOT_GLOBAL_ADMINISTRATOR:
            Message errorMsg = INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get();
            throw new UserDataException(Step.REPLICATION_OPTIONS, errorMsg);
          case GENERIC_CREATING_CONNECTION:
            if ((e.getCause() != null) &&
                isCertificateException(e.getCause()))
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
              else if (cause ==
                ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
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
                  h = INFO_NOT_AVAILABLE_LABEL.get().toString();
                  p = -1;
                }
                throw new UserDataCertificateException(
                        Step.REPLICATION_OPTIONS,
                        INFO_CERTIFICATE_EXCEPTION.get(
                                h, String.valueOf(p)),
                        e.getCause(), h, p,
                        e.getTrustManager().getLastRefusedChain(),
                        e.getTrustManager().getLastRefusedAuthType(), excType);
              }
            }
          }
          exceptionMsgs.add(getStringRepresentation(e));
        }
        if (exceptionMsgs.size() > 0)
        {
          Message confirmationMsg =
            INFO_ERROR_READING_REGISTERED_SERVERS_CONFIRM.get(
                    getStringFromCollection(exceptionMsgs, "n"));
          throw new UserDataConfirmationException(Step.REPLICATION_OPTIONS,
              confirmationMsg);
        }
      }
      else
      {
        updateUserDataWithSuffixesInServer(ctx);
      }
    }
    catch (UserDataException ude)
    {
      throw ude;
    }
    catch (Throwable t)
    {
      LOG.log(Level.INFO, "Error connecting to remote server.", t);
      if (isCertificateException(t))
      {
        UserDataCertificateException.Type excType;
        ApplicationTrustManager.Cause cause =
          trustManager.getLastRefusedCause();
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
          throw new UserDataCertificateException(Step.REPLICATION_OPTIONS,
              INFO_CERTIFICATE_EXCEPTION.get(host, String.valueOf(port)), t,
              host, port, trustManager.getLastRefusedChain(),
              trustManager.getLastRefusedAuthType(), excType);
        }
        else
        {
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
          errorMsgs.add(INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
                  host+":"+port, t.toString()));
        }
      }
      else if (t instanceof AuthenticationException)
      {
        errorMsgs.add(INFO_CANNOT_CONNECT_TO_REMOTE_AUTHENTICATION.get());
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
      }
      else if (t instanceof NoPermissionException)
      {
        errorMsgs.add(INFO_CANNOT_CONNECT_TO_REMOTE_PERMISSIONS.get(
                  host+":"+port));
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
      }
      else if (t instanceof NamingException)
      {
        errorMsgs.add(INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
                host+":"+port, t.toString()));
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
      }
      else if (t instanceof ADSContextException)
      {
        String[] args = {host+":"+port, t.toString()};
        errorMsgs.add(INFO_REMOTE_ADS_EXCEPTION.get(
                host+":"+port, t.toString()));
      }
      else
      {
        throw new UserDataException(Step.REPLICATION_OPTIONS,
            getThrowableMsg(INFO_BUG_MSG.get(), t));
      }
    }
    finally
    {
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
   * Validate the data provided by the user in the create global administrator
   * panel and update the UserInstallData object according to that content.
   *
   * @throws
   *           UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForCreateAdministratorPanel(QuickSetup qs)
  throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    // Check the Global Administrator UID
    String uid = qs.getFieldStringValue(FieldName.GLOBAL_ADMINISTRATOR_UID);

    if ((uid == null) || (uid.trim().length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_ADMINISTRATOR_UID.get());
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_UID, true);
    }
    else
    {
      getUserData().setGlobalAdministratorUID(uid);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_UID, false);
    }

    // Check the provided passwords
    String pwd1 = qs.getFieldStringValue(FieldName.GLOBAL_ADMINISTRATOR_PWD);
    String pwd2 = qs.getFieldStringValue(
        FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM);
    if (pwd1 == null)
    {
      pwd1 = "";
    }

    boolean pwdValid = true;
    if (!pwd1.equals(pwd2))
    {
      errorMsgs.add(INFO_NOT_EQUAL_PWD.get());
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM, true);
      pwdValid = false;

    }
    if (pwd1.length() < MIN_DIRECTORY_MANAGER_PWD)
    {
      errorMsgs.add(INFO_PWD_TOO_SHORT.get(
              String.valueOf(MIN_DIRECTORY_MANAGER_PWD)));
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD, true);
      if ((pwd2 == null) || (pwd2.length() < MIN_DIRECTORY_MANAGER_PWD))
      {
        qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM,
            true);
      }
      pwdValid = false;
    }

    if (pwdValid)
    {
      getUserData().setGlobalAdministratorPassword(pwd1);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD, false);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM, false);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.CREATE_GLOBAL_ADMINISTRATOR,
          getMessageFromCollection(errorMsgs, "\n"));
    }
  }

  /**
   * Validate the data provided by the user in the replicate suffixes options
   * panel and update the UserInstallData object according to that content.
   *
   * @throws
   *           UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForSuffixesOptionsPanel(QuickSetup qs)
  throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();
    if (qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE_OPTIONS) ==
      SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES)
    {
      Set s = (Set)qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE);
      if (s.size() == 0)
      {
        errorMsgs.add(INFO_NO_SUFFIXES_CHOSEN_TO_REPLICATE.get());
        qs.displayFieldInvalid(FieldName.SUFFIXES_TO_REPLICATE, true);
      }
      else
      {
        Set<SuffixDescriptor> chosen = new HashSet<SuffixDescriptor>();
        for (Object o: s)
        {
          chosen.add((SuffixDescriptor)o);
        }
        qs.displayFieldInvalid(FieldName.SUFFIXES_TO_REPLICATE, false);
        Set<SuffixDescriptor> available = getUserData().
        getSuffixesToReplicateOptions().getAvailableSuffixes();

        SuffixesToReplicateOptions options =
          new SuffixesToReplicateOptions(
          SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES,
              available,
              chosen);
        getUserData().setSuffixesToReplicateOptions(options);
      }
      getUserData().setRemoteWithNoReplicationPort(
          getRemoteWithNoReplicationPort(getUserData()));
    }
    else
    {
      Set<SuffixDescriptor> available = getUserData().
      getSuffixesToReplicateOptions().getAvailableSuffixes();
      Set<SuffixDescriptor> chosen = getUserData().
      getSuffixesToReplicateOptions().getSuffixes();
      SuffixesToReplicateOptions options =
        new SuffixesToReplicateOptions(
            SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY,
            available,
            chosen);
      getUserData().setSuffixesToReplicateOptions(options);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.SUFFIXES_OPTIONS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
  }

  /**
   * Validate the data provided by the user in the remote server replication
   * port panel and update the userData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   */
  private void updateUserDataForRemoteReplicationPorts(QuickSetup qs)
      throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();
    Map<ServerDescriptor, Integer> servers =
      getUserData().getRemoteWithNoReplicationPort();
    Map hm = (Map) qs.getFieldValue(FieldName.REMOTE_REPLICATION_PORT);
    for (ServerDescriptor server : servers.keySet())
    {
      String hostName = server.getHostName();
      int replicationPort = -1;
      String sPort = (String)hm.get(server.getId());
      try
      {
        replicationPort = Integer.parseInt(sPort);
        if ((replicationPort < MIN_PORT_VALUE) ||
            (replicationPort > MAX_PORT_VALUE))
        {
          errorMsgs.add(INFO_INVALID_REMOTE_REPLICATION_PORT_VALUE_RANGE.get(
                  server.getHostPort(true),
                  String.valueOf(MIN_PORT_VALUE),
                  String.valueOf(MAX_PORT_VALUE)));
        }
        if (hostName.equalsIgnoreCase(getUserData().getHostName()))
        {
          int securePort = -1;
          if (getUserData().getSecurityOptions().getEnableSSL())
          {
            securePort = getUserData().getSecurityOptions().getSslPort();
          }
          if ((replicationPort == getUserData().getServerPort()) ||
              (replicationPort == getUserData().getServerJMXPort()) ||
              (replicationPort ==
                getUserData().getReplicationOptions().getReplicationPort()) ||
              (replicationPort == securePort))
          {
            errorMsgs.add(
                  INFO_REMOTE_REPLICATION_PORT_ALREADY_CHOSEN_FOR_OTHER_PROTOCOL
                          .get(server.getHostPort(true)));
          }
        }
        servers.put(server, replicationPort);
      } catch (NumberFormatException nfe)
      {
        errorMsgs.add(INFO_INVALID_REMOTE_REPLICATION_PORT_VALUE_RANGE.get(
                hostName, String.valueOf(MIN_PORT_VALUE),
                String.valueOf(MAX_PORT_VALUE)));
      }
    }

    if (errorMsgs.size() > 0)
    {
      qs.displayFieldInvalid(FieldName.REMOTE_REPLICATION_PORT, true);
      throw new UserDataException(Step.REMOTE_REPLICATION_PORTS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_REPLICATION_PORT, false);
      getUserData().setRemoteWithNoReplicationPort(servers);
    }
  }

  /**
   * Validate the data provided by the user in the new suffix data options panel
   * and update the UserInstallData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForNewSuffixOptionsPanel(QuickSetup qs)
      throws UserDataException
  {
    ArrayList<Message> errorMsgs = new ArrayList<Message>();

    NewSuffixOptions dataOptions = null;

    // Check the base dn
    boolean validBaseDn = false;
    String baseDn = qs.getFieldStringValue(FieldName.DIRECTORY_BASE_DN);
    if ((baseDn == null) || (baseDn.trim().length() == 0))
    {
      errorMsgs.add(INFO_EMPTY_BASE_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, true);
    } else if (!isDn(baseDn))
    {
      errorMsgs.add(INFO_NOT_A_BASE_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, true);
    } else if (isConfigurationDn(baseDn))
    {
      errorMsgs.add(INFO_BASE_DN_IS_CONFIGURATION_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, true);
    } else
    {
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, false);
      validBaseDn = true;
    }

    // Check the data options
    NewSuffixOptions.Type type =
        (NewSuffixOptions.Type) qs.getFieldValue(FieldName.DATA_OPTIONS);

    switch (type)
    {
    case IMPORT_FROM_LDIF_FILE:
      String ldifPath = qs.getFieldStringValue(FieldName.LDIF_PATH);
      if ((ldifPath == null) || (ldifPath.trim().equals("")))
      {
        errorMsgs.add(INFO_NO_LDIF_PATH.get());
        qs.displayFieldInvalid(FieldName.LDIF_PATH, true);
      } else if (!fileExists(ldifPath))
      {
        errorMsgs.add(INFO_LDIF_FILE_DOES_NOT_EXIST.get());
        qs.displayFieldInvalid(FieldName.LDIF_PATH, true);
      } else if (validBaseDn)
      {
        dataOptions = new NewSuffixOptions(type, baseDn, ldifPath);
        qs.displayFieldInvalid(FieldName.LDIF_PATH, false);
      }
      break;

    case IMPORT_AUTOMATICALLY_GENERATED_DATA:
      // variable used to know if everything went ok during these
      // checks
      int startErrors = errorMsgs.size();

      // Check the number of entries
      String nEntries = qs.getFieldStringValue(FieldName.NUMBER_ENTRIES);
      if ((nEntries == null) || (nEntries.trim().equals("")))
      {
        errorMsgs.add(INFO_NO_NUMBER_ENTRIES.get());
        qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, true);
      } else
      {
        boolean nEntriesValid = false;
        try
        {
          int n = Integer.parseInt(nEntries);

          nEntriesValid = n >= MIN_NUMBER_ENTRIES && n <= MAX_NUMBER_ENTRIES;
        } catch (NumberFormatException nfe)
        {
        }

        if (!nEntriesValid)
        {
          errorMsgs.add(INFO_INVALID_NUMBER_ENTRIES_RANGE.get(
                  String.valueOf(MIN_NUMBER_ENTRIES),
                    String.valueOf(MAX_NUMBER_ENTRIES)));
          qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, true);
        } else
        {
          qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, false);
        }
      }
      if (startErrors == errorMsgs.size() && validBaseDn)
      {
        // No validation errors
        dataOptions = new NewSuffixOptions(type, baseDn, new Integer(nEntries));
      }
      break;

    default:
      qs.displayFieldInvalid(FieldName.LDIF_PATH, false);
      qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, false);
      if (validBaseDn)
      {
        dataOptions = new NewSuffixOptions(type, baseDn);
      }
    }

    if (dataOptions != null)
    {
      getUserData().setNewSuffixOptions(dataOptions);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.NEW_SUFFIX_OPTIONS,
          getMessageFromCollection(errorMsgs, "\n"));
    }
  }


  /**
   * Update the userData object according to the content of the review
   * panel.
   *
   */
  private void updateUserDataForReviewPanel(QuickSetup qs)
  {
    Boolean b = (Boolean) qs.getFieldValue(FieldName.SERVER_START);
    getUserData().setStartServer(b);
    b = (Boolean) qs.getFieldValue(FieldName.ENABLE_WINDOWS_SERVICE);
    getUserData().setEnableWindowsService(b);
  }

  /**
   * Returns the number of free disk space in bytes required to install Open DS
   *
   * For the moment we just return 15 Megabytes. TODO we might want to have
   * something dynamic to calculate the required free disk space for the
   * installation.
   *
   * @return the number of free disk space required to install Open DS.
   */
  private long getRequiredInstallSpace()
  {
    return 15 * 1024 * 1024;
  }

  private Map<ADSContext.ServerProperty, Object> getRemoteServerProperties(
      String hostName, InitialLdapContext ctx) throws NamingException
  {
    ServerDescriptor server = ServerDescriptor.createStandalone(ctx);
    Map<ADSContext.ServerProperty, Object> serverProperties =
      new HashMap<ADSContext.ServerProperty, Object>();
    serverProperties.put(ADSContext.ServerProperty.HOST_NAME, hostName);
    ADSContext.ServerProperty[][] adsProperties =
    {
        {ADSContext.ServerProperty.LDAP_PORT,
        ADSContext.ServerProperty.LDAP_ENABLED},
        {ADSContext.ServerProperty.LDAPS_PORT,
        ADSContext.ServerProperty.LDAPS_ENABLED},
        {ADSContext.ServerProperty.JMX_PORT,
        ADSContext.ServerProperty.JMX_ENABLED},
        {ADSContext.ServerProperty.JMXS_PORT,
        ADSContext.ServerProperty.JMXS_ENABLED}

    };
    ServerDescriptor.ServerProperty[][] properties =
    {
        {ServerDescriptor.ServerProperty.LDAP_PORT,
         ServerDescriptor.ServerProperty.LDAP_ENABLED},
        {ServerDescriptor.ServerProperty.LDAPS_PORT,
         ServerDescriptor.ServerProperty.LDAPS_ENABLED},
        {ServerDescriptor.ServerProperty.JMX_PORT,
         ServerDescriptor.ServerProperty.JMX_ENABLED},
        {ServerDescriptor.ServerProperty.JMXS_PORT,
         ServerDescriptor.ServerProperty.JMXS_ENABLED}
    };
    for (int i=0; i<properties.length; i++)
    {
      ArrayList portNumbers =
        (ArrayList)server.getServerProperties().get(properties[i][0]);
      if (portNumbers != null)
      {
        ArrayList enabled =
          (ArrayList)server.getServerProperties().get(properties[i][1]);
        boolean enabledFound = false;
        for (int j=0; j<enabled.size() && !enabledFound; j++)
        {
          if (Boolean.TRUE.equals(enabled.get(j)))
          {
            enabledFound = true;
            serverProperties.put(adsProperties[i][0],
                String.valueOf(portNumbers.get(j)));
          }
        }
        if (!enabledFound && (portNumbers.size() > 0))
        {
          serverProperties.put(adsProperties[i][0],
              String.valueOf(portNumbers.get(0)));
        }
        serverProperties.put(adsProperties[i][1], enabledFound?"true":"false");
      }
    }

    serverProperties.put(ADSContext.ServerProperty.ID,
        server.getHostPort(true));

    return serverProperties;
  }

  /**
   * Update the UserInstallData with the contents we discover in the ADS.
   */
  private Set<TopologyCacheException> updateUserDataWithSuffixesInADS(
      ADSContext adsContext, ApplicationTrustManager trustManager)
  throws TopologyCacheException
  {
    Set<TopologyCacheException> exceptions =
      new HashSet<TopologyCacheException>();
    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();
    SuffixesToReplicateOptions.Type type;

    if ((suf == null) || (suf.getType() ==
      SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE))
    {
      type = suf.getType();
    }
    else
    {
      type = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
    }
    lastLoadedCache = new TopologyCache(adsContext, trustManager);
    lastLoadedCache.reloadTopology();
    Set<SuffixDescriptor> suffixes = lastLoadedCache.getSuffixes();

    getUserData().setSuffixesToReplicateOptions(
        new SuffixesToReplicateOptions(type, suffixes, suf.getSuffixes()));

    /* Analyze if we had any exception while loading servers.  For the moment
     * only throw the exception found if the user did not provide the
     * Administrator DN and this caused a problem authenticating in one server
     * or if there is a certificate problem.
     */
    Set<ServerDescriptor> servers = lastLoadedCache.getServers();
    for (ServerDescriptor server : servers)
    {
      TopologyCacheException e = server.getLastException();
      if (e != null)
      {
        exceptions.add(e);
      }
    }
    return exceptions;
  }

  /**
   * Update the UserInstallData object with the contents of the server to which
   * we are connected with the provided InitialLdapContext.
   */
  private void updateUserDataWithSuffixesInServer(InitialLdapContext ctx)
  throws NamingException
  {
    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();
    SuffixesToReplicateOptions.Type type;
    Set<SuffixDescriptor> suffixes = new HashSet<SuffixDescriptor>();
    if (suf == null)
    {
      type = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
    }
    else
    {
      type = suf.getType();
    }
    ServerDescriptor s = ServerDescriptor.createStandalone(ctx);
    Set<ReplicaDescriptor> replicas = s.getReplicas();
    for (ReplicaDescriptor replica : replicas)
    {
      suffixes.add(replica.getSuffix());
    }
    getUserData().setSuffixesToReplicateOptions(
        new SuffixesToReplicateOptions(type, suffixes, suf.getSuffixes()));
  }

  /**
   * Returns the keystore path to be used for generating a self-signed
   * certificate.
   * @return the keystore path to be used for generating a self-signed
   * certificate.
   */
  protected String getSelfSignedKeystorePath()
  {
    String parentFile = getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "keystore"));
  }

  /**
   * Returns the trustmanager path to be used for generating a self-signed
   * certificate.
   * @return the trustmanager path to be used for generating a self-signed
   * certificate.
   */
  private String getTrustManagerPath()
  {
    String parentFile = getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "truststore"));
  }

  /**
   * Returns the path of the self-signed that we export to be able to create
   * a truststore.
   * @return the path of the self-signed that is exported.
   */
  private String getTemporaryCertificatePath()
  {
    String parentFile = getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "server-cert.txt"));
  }

  /**
   * Returns the path to be used to store the password of the keystore.
   * @return the path to be used to store the password of the keystore.
   */
  private String getKeystorePinPath()
  {
    String parentFile = getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (getPath(parentFile, "keystore.pin"));
  }


  /**
   * Returns the validity period to be used to generate the self-signed
   * certificate.
   * @return the validity period to be used to generate the self-signed
   * certificate.
   */
  private int getSelfSignedCertificateValidity()
  {
    return 2 * 365;
  }

  /**
   * Returns the Subject DN to be used to generate the self-signed certificate.
   * @return the Subject DN to be used to generate the self-signed certificate.
   */
  private String getSelfSignedCertificateSubjectDN()
  {
    return "cn="+Rdn.escapeValue(getUserData().getHostName())+
    ",O=OpenDS Self-Signed Certificate";
  }

  /**
   * Returns the self-signed certificate password used for this session.  This
   * method calls <code>createSelfSignedCertificatePwd()</code> the first time
   * this method is called.
   * @return the self-signed certificate password used for this session.
   */
  protected String getSelfSignedCertificatePwd()
  {
    if (selfSignedCertPw == null) {
      selfSignedCertPw = createSelfSignedCertificatePwd();
    }
    return new String(selfSignedCertPw);
  }

  /**
   * Returns a randomly generated password for the self-signed certificate
   * keystore.
   * @return a randomly generated password for the self-signed certificate
   * keystore.
   */
  private char[] createSelfSignedCertificatePwd() {
    int pwdLength = 50;
    char[] pwd = new char[pwdLength];
    Random random = new Random();
    for (int pos=0; pos < pwdLength; pos++) {
        int type = getRandomInt(random,3);
        char nextChar = getRandomChar(random,type);
        pwd[pos] = nextChar;
    }
    return pwd;
  }

  private void exportCertificate(CertificateManager certManager, String alias,
      String path) throws CertificateEncodingException, IOException,
      KeyStoreException
  {
    Certificate certificate = certManager.getCertificate(alias);

    byte[] certificateBytes = certificate.getEncoded();

    FileOutputStream outputStream = new FileOutputStream(path, false);
    outputStream.write(certificateBytes);
    outputStream.close();
  }

  /* The next two methods are used to generate the random password for the
   * self-signed certificate. */
  private char getRandomChar(Random random, int type)
  {
    char generatedChar;
    int next = random.nextInt();
    int d;

    switch (type)
    {
    case 0:
      // Will return a figure
      d = next % 10;
      if (d < 0)
      {
        d = d * (-1);
      }
      generatedChar = (char) (d+48);
      break;
    case 1:
      // Will return a lower case letter
      d = next % 26;
      if (d < 0)
      {
        d = d * (-1);
      }
      generatedChar =  (char) (d + 97);
      break;
    default:
      // Will return a capital letter
      d = (next % 26);
      if (d < 0)
      {
        d = d * (-1);
      }
      generatedChar = (char) (d + 65) ;
    }

    return generatedChar;
  }

  private Map<ServerDescriptor, Integer> getRemoteWithNoReplicationPort(
      UserData userData)
  {
    Map<ServerDescriptor, Integer> servers =
      new HashMap<ServerDescriptor, Integer>();
    Set<SuffixDescriptor> suffixes =
      userData.getSuffixesToReplicateOptions().getSuffixes();
    for (SuffixDescriptor suffix : suffixes)
    {
      for (ReplicaDescriptor replica : suffix.getReplicas())
      {
        ServerDescriptor server = replica.getServer();
        Object v = server.getServerProperties().get(
            ServerDescriptor.ServerProperty.IS_REPLICATION_SERVER);
        if (!Boolean.TRUE.equals(v))
        {
          servers.put(server, 8989);
        }
      }
    }
    return servers;
  }

  private InitialLdapContext createLocalContext() throws NamingException
  {
    String ldapUrl = "ldap://"+
    getHostNameForLdapUrl(getUserData().getHostName())+":"+
    getUserData().getServerPort();
    String dn = getUserData().getDirectoryManagerDn();
    String pwd = getUserData().getDirectoryManagerPwd();
    return createLdapContext(ldapUrl, dn, pwd,
        getDefaultLDAPTimeout(), null);
  }
  private void createLocalAds(InitialLdapContext ctx, boolean addData)
  throws ApplicationException, ADSContextException
  {
    try
    {
      ADSContext adsContext = new ADSContext(ctx);
      if (addData)
      {
        adsContext.createAdminData(null);
        adsContext.registerServer(getNewServerAdsProperties());
        if (getUserData().mustCreateAdministrator())
        {
          adsContext.createAdministrator(getAdministratorProperties());
        }
      }
      else
      {
        adsContext.createAdministrationSuffix(null);
      }
    }
    catch (ADSContextException ace)
    {
      throw ace;
    }
    catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.CONFIGURATION_ERROR,
              getThrowableMsg(INFO_BUG_MSG.get(), t), t);
    }
  }


  /**
   * Gets an InitialLdapContext based on the information that appears on the
   * provided ServerDescriptor.
   * @param server the object describing the server.
   * @param trustManager the trust manager to be used to establish the
   * connection.
   * @return the InitialLdapContext to the remote server.
   * @throws ApplicationException if something goes wrong.
   */
  private InitialLdapContext getRemoteConnection(ServerDescriptor server,
      ApplicationTrustManager trustManager) throws ApplicationException
  {
    Map<ADSContext.ServerProperty, Object> adsProperties;
    AuthenticationData auth =
      getUserData().getReplicationOptions().getAuthenticationData();
    if (!server.isRegistered())
    {
      /* Create adsProperties to be able to use the class ServerLoader to
       * get the connection.  Just update the connection parameters with what
       * the user chose in the Topology Options panel (i.e. even if SSL
       * is enabled on the remote server, use standard LDAP to connect to the
       * server if the user specified the LDAP port: this avoids having an
       * issue with the certificate if it has not been accepted previously
       * by the user).
       */
      adsProperties = new HashMap<ADSContext.ServerProperty, Object>();
      adsProperties.put(ADSContext.ServerProperty.HOST_NAME,
          server.getHostName());
      if (auth.useSecureConnection())
      {
        adsProperties.put(ADSContext.ServerProperty.LDAPS_PORT,
            String.valueOf(auth.getPort()));
        adsProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "true");
      }
      else
      {
        adsProperties.put(ADSContext.ServerProperty.LDAP_PORT,
            String.valueOf(auth.getPort()));
        adsProperties.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");
      }
      server.setAdsProperties(adsProperties);
    }
    return  getRemoteConnection(server, auth.getDn(), auth.getPwd(),
        trustManager);
  }

  private void initializeSuffix(InitialLdapContext ctx, int replicaId,
      String suffixDn, boolean displayProgress, String sourceServerDisplay)
  throws ApplicationException, PeerNotFoundException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-initialize-from-remote-replica");
    attrs.put(oc);
    attrs.put("ds-task-class-name", "org.opends.server.tasks.InitializeTask");
    attrs.put("ds-task-initialize-domain-dn", suffixDn);
    attrs.put("ds-task-initialize-replica-server-id",
        String.valueOf(replicaId));
    while (!taskCreated)
    {
      String id = "quicksetup-initialize"+i;
      dn = "ds-task-id="+id+",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_LAUNCHING_INITIALIZATION.get(
                        sourceServerDisplay
                ), ne), ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setCountLimit(1);
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-unprocessed-entry-count",
            "ds-task-processed-entry-count",
            "ds-task-log-message",
            "ds-task-state"
        });
    Message lastDisplayedMsg = null;
    String lastLogMsg = null;
    long lastTimeMsgDisplayed = -1;
    int totalEntries = 0;
    while (!isOver)
    {
      try
      {
        Thread.sleep(500);
      }
      catch (Throwable t)
      {
      }
      try
      {
        NamingEnumeration res = ctx.search(dn, filter, searchControls);
        SearchResult sr = (SearchResult)res.next();
        if (displayProgress)
        {
          // Display the number of entries that have been handled and
          // a percentage...
          Message msg;
          String sProcessed = getFirstValue(sr,
          "ds-task-processed-entry-count");
          String sUnprocessed = getFirstValue(sr,
          "ds-task-unprocessed-entry-count");
          int processed = -1;
          int unprocessed = -1;
          if (sProcessed != null)
          {
            processed = Integer.parseInt(sProcessed);
          }
          if (sUnprocessed != null)
          {
            unprocessed = Integer.parseInt(sUnprocessed);
          }
          totalEntries = Math.max(totalEntries, processed+unprocessed);

          if ((processed != -1) && (unprocessed != -1))
          {
            if (processed + unprocessed > 0)
            {
              int perc = (100 * processed) / (processed + unprocessed);
              msg = INFO_INITIALIZE_PROGRESS_WITH_PERCENTAGE.get(sProcessed,
                  String.valueOf(perc));
            }
            else
            {
              //msg = INFO_NO_ENTRIES_TO_INITIALIZE.get();
              msg = null;
            }
          }
          else if (processed != -1)
          {
            msg = INFO_INITIALIZE_PROGRESS_WITH_PROCESSED.get(sProcessed);
          }
          else if (unprocessed != -1)
          {
            msg = INFO_INITIALIZE_PROGRESS_WITH_UNPROCESSED.get(sUnprocessed);
          }
          else
          {
            msg = lastDisplayedMsg;
          }

          if (msg != null)
          {
            long currentTime = System.currentTimeMillis();
            /* Refresh period: to avoid having too many lines in the log */
            long minRefreshPeriod;
            if (totalEntries < 100)
            {
              minRefreshPeriod = 0;
            }
            else if (totalEntries < 1000)
            {
              minRefreshPeriod = 1000;
            }
            else if (totalEntries < 10000)
            {
              minRefreshPeriod = 5000;
            }
            else
            {
              minRefreshPeriod = 10000;
            }
            if (!msg.equals(lastDisplayedMsg) &&
            ((currentTime - minRefreshPeriod) > lastTimeMsgDisplayed))
            if (!msg.equals(lastDisplayedMsg))
            {
              notifyListeners(getFormattedProgress(msg));
              lastDisplayedMsg = msg;
              notifyListeners(getLineBreak());
              lastTimeMsgDisplayed = currentTime;
            }
          }
        }
        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          if (lastLogMsg == null)
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(
                    sourceServerDisplay, state, sourceServerDisplay);
          }
          else
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_LOG.get(
                    sourceServerDisplay, lastLogMsg, state,
                    sourceServerDisplay);
          }

          if (helper.isCompletedWithErrors(state))
          {
            notifyListeners(getFormattedWarning(errorMsg));
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            ApplicationException ae = new ApplicationException(
                ApplicationReturnCode.ReturnCode.APPLICATION_ERROR, errorMsg,
                null);
            if ((lastLogMsg == null) ||
                helper.isPeersNotFoundError(lastLogMsg))
            {
              // Assume that this is a peer not found error.
              throw new PeerNotFoundException(errorMsg);
            }
            else
            {
              throw ae;
            }
          }
          else if (displayProgress)
          {
            notifyListeners(getFormattedProgress(
                INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get()));
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
        notifyListeners(getFormattedProgress(
            INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get()));
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION.get(
                        sourceServerDisplay),
                        ne), ne);
      }
    }
  }

  private String getLocalReplicationServer()
  {
    return getUserData().getHostName()+":"+
    getUserData().getReplicationOptions().getReplicationPort();
  }

  private String getLocalHostPort()
  {
    return getUserData().getHostName()+":"+getUserData().getServerPort();
  }

  private static int getRandomInt(Random random,int modulo)
  {
    int value = 0;
    value = (random.nextInt() & modulo);
    return value;
  }
}

/**
 * The exception that is thrown during initialization if the peer specified
 * could not be found.
 *
 */
class PeerNotFoundException extends OpenDsException {

  private static final long serialVersionUID = -362726764261560341L;

  /**
   * The constructor for the exception.
   * @param message the localized message.
   */
  PeerNotFoundException(Message message)
  {
    super(message);
  }
}
