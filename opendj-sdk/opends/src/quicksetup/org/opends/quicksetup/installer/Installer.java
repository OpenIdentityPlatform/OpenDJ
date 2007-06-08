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

import static org.opends.quicksetup.Step.*;

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
import org.opends.admin.ads.util.ServerLoader;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.Utils;
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

  /* Indicates that we've detected that there is something installed */
  boolean forceToDisplaySetup = false;

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

  private List<WizardStep> lstSteps = new ArrayList<WizardStep>();

  private final HashSet<WizardStep> SUBSTEPS = new HashSet<WizardStep>();
  {
    SUBSTEPS.add(Step.CREATE_GLOBAL_ADMINISTRATOR);
    SUBSTEPS.add(Step.SUFFIXES_OPTIONS);
    // TODO: remove this comment once we want to display the replication options
    // in setup.
    //SUBSTEPS.add(Step.NEW_SUFFIX_OPTIONS);
    SUBSTEPS.add(Step.REMOTE_REPLICATION_PORTS);
  }

  private HashMap<WizardStep, WizardStep> hmPreviousSteps =
    new HashMap<WizardStep, WizardStep>();

  /**
   * An static String that contains the class name of ConfigFileHandler.
   */
  protected static final String CONFIG_CLASS_NAME =
      "org.opends.server.extensions.ConfigFileHandler";

  /**
   * Creates a default instance.
   */
  public Installer() {
    lstSteps.add(WELCOME);
    lstSteps.add(SERVER_SETTINGS);
    // TODO: remove this comment once we want to display the replication options
    // in setup.
    /*
    lstSteps.add(REPLICATION_OPTIONS);
    lstSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    lstSteps.add(SUFFIXES_OPTIONS);
    lstSteps.add(REMOTE_REPLICATION_PORTS);
    */
    lstSteps.add(NEW_SUFFIX_OPTIONS);
    lstSteps.add(REVIEW);
    lstSteps.add(PROGRESS);
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
    return false; // TODO: have installer delete installed files upon cancel
  }

  /**
   * {@inheritDoc}
   */
  public UserData createUserData() {
    UserData ud = new UserData();
    ud.setServerLocation(Utils.getDefaultServerLocation());
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
            step != PROGRESS;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canGoForward(WizardStep step) {
    return step != REVIEW &&
            step != PROGRESS;
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
    return step != PROGRESS;
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
  public boolean isVisible(WizardStep step)
  {
    boolean isVisible;
    if (step == CREATE_GLOBAL_ADMINISTRATOR)
    {
       isVisible = getUserData().mustCreateAdministrator();
    }
    else if (step == NEW_SUFFIX_OPTIONS)
    {
      SuffixesToReplicateOptions suf =
        getUserData().getSuffixesToReplicateOptions();
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
      DataReplicationOptions repl =
        getUserData().getReplicationOptions();
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
      isVisible = isVisible(SUFFIXES_OPTIONS) &&
      (getUserData().getRemoteWithNoReplicationPort().size() > 0) &&
      (getUserData().getSuffixesToReplicateOptions().getType() ==
        SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES);
    }
    else
    {
      isVisible = true;
    }
    // TODO: remove this line once we want to display the replication options
    // in setup.
    isVisible = true;
    return isVisible;
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
    }
  }

  /**
   * {@inheritDoc}
   */
  public void closeClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
      if (isFinished()
              || qs.displayConfirmation(getMsg("confirm-close-install-msg"),
              getMsg("confirm-close-install-title"))) {
        qs.quit();
      }
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
        || getCurrentProgressStep() == InstallProgressStep.FINISHED_WITH_ERROR;
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
  public void quitClicked(WizardStep cStep, QuickSetup qs) {
    if (cStep == PROGRESS) {
      throw new IllegalStateException(
              "Cannot click on quit from progress step");
    } else if (installStatus.isInstalled()) {
      qs.quit();
    } else if (qs.displayConfirmation(getMsg("confirm-quit-install-msg"),
            getMsg("confirm-quit-install-title"))) {
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
  public String getCloseButtonToolTipKey() {
    return "close-button-install-tooltip";
  }

  /**
   * {@inheritDoc}
   */
  public String getQuitButtonToolTipKey() {
    return "quit-button-install-tooltip";
  }

  /**
   * {@inheritDoc}
   */
  public String getFinishButtonToolTipKey() {
    return "finish-button-install-tooltip";
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
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getFrameTitle() {
    return getMsg("frame-install-title");
  }

  /** Indicates the current progress step. */
  private InstallProgressStep status =
          InstallProgressStep.NOT_STARTED;

  /**
   * {@inheritDoc}
   */
  protected void setWizardDialogState(QuickSetupDialog dlg,
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
      } else if (step == REVIEW) {
        dlg.setDefaultButton(ButtonName.NEXT);
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
    return status;
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
    // TODO: remove this comment once we want to display the replication options
    // in setup.
    /*
    orderedSteps.add(REPLICATION_OPTIONS);
    orderedSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    orderedSteps.add(SUFFIXES_OPTIONS);
    orderedSteps.add(REMOTE_REPLICATION_PORTS);
    */
    orderedSteps.add(NEW_SUFFIX_OPTIONS);
    orderedSteps.add(REVIEW);
    orderedSteps.add(PROGRESS);
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
      String failedMsg = getThrowableMsg("error-creating-temp-file", null, ioe);
      throw new ApplicationException(
          ApplicationException.Type.FILE_SYSTEM_ERROR, failedMsg, ioe);
    }
  }

  /**
   * This methods configures the server based on the contents of the UserData
   * object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  protected void configureServer() throws ApplicationException {
    notifyListeners(getFormattedWithPoints(getMsg("progress-configuring")));

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-c");
    argList.add(Utils.getPath(getInstallation().getCurrentConfigurationFile()));
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

    String[] args = new String[argList.size()];
    argList.toArray(args);
    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeConfigureServer(args);

      if (result != 0)
      {
        throw new ApplicationException(
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("error-configuring"), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-configuring", null, t), t);
    }

    try
    {
      SecurityOptions.CertificateType certType = sec.getCertificateType();
      if (certType != SecurityOptions.CertificateType.NO_CERTIFICATE)
      {
        notifyListeners(getLineBreak());
        notifyListeners(getFormattedWithPoints(
            getMsg("progress-updating-certificates")));
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
        certManager.generateSelfSignedCertificate("server-cert",
            getSelfSignedCertificateSubjectDN(),
            getSelfSignedCertificateValidity());
        exportCertificate(certManager, "server-cert",
            getTemporaryCertificatePath());

        trustManager = new CertificateManager(
            getTrustManagerPath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            pwd);
        trustManager.addCertificate("server-cert",
            new File(getTemporaryCertificatePath()));
        Utils.createFile(getKeystorePinPath(), pwd);
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
        Utils.createFile(getKeystorePinPath(), sec.getKeystorePassword());
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
        Utils.createFile(getKeystorePinPath(), sec.getKeystorePassword());
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
        Utils.createFile(getKeystorePinPath(), sec.getKeystorePassword());
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
          ApplicationException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-configuring-certificate", null, t), t);
    }
  }

  /**
   * This methods creates the base entry for the suffix based on the contents of
   * the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void createBaseEntry() throws ApplicationException {
    String[] arg =
      { getUserData().getNewSuffixOptions().getBaseDn() };
    notifyListeners(getFormattedWithPoints(
        getMsg("progress-creating-base-entry", arg)));

    InstallerHelper helper = new InstallerHelper();
    String baseDn = getUserData().getNewSuffixOptions().getBaseDn();
    File tempFile = helper.createBaseEntryTempFile(baseDn);

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(Utils.getPath(getInstallation().getCurrentConfigurationFile()));

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
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("error-creating-base-entry"), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-creating-base-entry", null, t), t);
    }

    notifyListeners(getFormattedDone());
  }

  /**
   * This methods imports the contents of an LDIF file based on the contents of
   * the UserData object provided in the constructor.
   * @throws ApplicationException if something goes wrong.
   */
  private void importLDIF() throws ApplicationException {
    String[] arg =
      { getUserData().getNewSuffixOptions().getLDIFPath() };
    notifyListeners(getFormattedProgress(getMsg("progress-importing-ldif", arg))
        + getLineBreak());

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(Utils.getPath(getInstallation().getCurrentConfigurationFile()));
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
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("error-importing-ldif"), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-importing-ldif", null, t), t);
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
    String[] arg =
      { String.valueOf(nEntries) };
    notifyListeners(getFormattedProgress(getMsg(
        "progress-import-automatically-generated", arg))
        + getLineBreak());

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(Utils.getPath(getInstallation().getCurrentConfigurationFile()));
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
        String[] msgArgs = { Utils.stringArrayToString(args, " ") };
        throw new ApplicationException(
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("error-import-automatically-generated", msgArgs), null);
      }
    } catch (Throwable t)
    {
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-import-automatically-generated", null, t), t);
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
        getMsg("progress-configuring-replication")));

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
      String failedMsg = getThrowableMsg("error-connecting-to-local", null, ne);
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR, failedMsg, ne);
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
        notifyListeners(getLineBreak());
        notifyListeners(getFormattedWithPoints(
            getMsg("progress-configuring-replication-remote",
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
        helper.configureReplication(ctx, dns, replicationServers,
            replicationPort, server.getHostPort(true),
            knownReplicationServerIds, knownServerIds);

        try
        {
          ctx.close();
        }
        catch (Throwable t)
        {
        }
        notifyListeners(getFormattedDone());
      }
    }
  }

  /**
   * This methods enables this server as a Windows service.
   * @throws ApplicationException if something goes wrong.
   */
  protected void enableWindowsService() throws ApplicationException {
      notifyListeners(getFormattedProgress(
        getMsg("progress-enabling-windows-service")));
      InstallerHelper helper = new InstallerHelper();
      helper.enableWindowsService();
  }

  /**
   * Updates the contents of the provided map with the localized summary
   * strings.
   * @param hmSummary the Map to be updated.
   */
  protected void initSummaryMap(
      Map<InstallProgressStep, String> hmSummary)
  {
    hmSummary.put(InstallProgressStep.NOT_STARTED,
        getFormattedSummary(getMsg("summary-install-not-started")));
    hmSummary.put(InstallProgressStep.DOWNLOADING,
        getFormattedSummary(getMsg("summary-downloading")));
    hmSummary.put(InstallProgressStep.EXTRACTING,
        getFormattedSummary(getMsg("summary-extracting")));
    hmSummary.put(InstallProgressStep.CONFIGURING_SERVER,
        getFormattedSummary(getMsg("summary-configuring")));
    hmSummary.put(InstallProgressStep.CREATING_BASE_ENTRY,
        getFormattedSummary(getMsg("summary-creating-base-entry")));
    hmSummary.put(InstallProgressStep.IMPORTING_LDIF,
        getFormattedSummary(getMsg("summary-importing-ldif")));
    hmSummary.put(
        InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        getFormattedSummary(
            getMsg("summary-importing-automatically-generated")));
    hmSummary.put(InstallProgressStep.CONFIGURING_REPLICATION,
        getFormattedSummary(getMsg("summary-configuring-replication")));
    hmSummary.put(InstallProgressStep.STARTING_SERVER,
        getFormattedSummary(getMsg("summary-starting")));
    hmSummary.put(InstallProgressStep.STOPPING_SERVER,
        getFormattedSummary(getMsg("summary-stopping")));
    hmSummary.put(InstallProgressStep.CONFIGURING_ADS,
        getFormattedSummary(getMsg("summary-configuring-ads")));
    hmSummary.put(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES,
        getFormattedSummary(getMsg("summary-initialize-replicated-suffixes")));
    hmSummary.put(InstallProgressStep.ENABLING_WINDOWS_SERVICE,
        getFormattedSummary(getMsg("summary-enabling-windows-service")));

    Installation installation = getInstallation();
    String cmd = Utils.getPath(installation.getStatusPanelCommandFile());
    cmd = UIFactory.applyFontToHtml(cmd, UIFactory.INSTRUCTIONS_MONOSPACE_FONT);
    String[] args = {formatter.getFormattedText(getInstallationPath()), cmd};
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY,
        getFormattedSuccess(
            getMsg("summary-install-finished-successfully", args)));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR,
        getFormattedError(getMsg("summary-install-finished-with-error", args)));
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
   * Sets the current status of the installation process.
   * @param status the current status of the installation process.
   */
  protected void setStatus(InstallProgressStep status)
  {
    this.status = status;
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
        status = InstallProgressStep.CREATING_BASE_ENTRY;
        notifyListeners(getTaskSeparator());
        createBaseEntry();
        break;
      case IMPORT_FROM_LDIF_FILE:
        status = InstallProgressStep.IMPORTING_LDIF;
        notifyListeners(getTaskSeparator());
        importLDIF();
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        status = InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED;
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
      String failedMsg = getThrowableMsg("error-connecting-to-local", null, t);
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
          ApplicationException.Type.CONFIGURATION_ERROR, failedMsg, t);
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
        getMsg("progress-initializing-suffix", dn, hostPort)));
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
              if (Utils.areDnsEqual(r.getSuffix().getDN(), dn))
              {
                replicationId = r.getReplicationId();
              }
            }
          }
          catch (NamingException ne)
          {
            String[] arg = {server.getHostPort(true)};
            throw new ApplicationException(
                ApplicationException.Type.CONFIGURATION_ERROR,
                getMsg("cannot-connect-to-remote-generic", arg), ne);
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
        int nTries = 4;
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
                  ApplicationException.Type.APPLICATION,
                  pnfe.getLocalizedMessage(), null);
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
          ctx = Utils.createLdapsContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, trustManager);
        }
        else
        {
          ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null);
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
              String[] arg = {getHostDisplay(auth)};
              notifyListeners(getFormattedWithPoints(
                  getMsg("progress-creating-administrator", arg)));
              adsContext.createAdministrator(getAdministratorProperties());
              notifyListeners(getFormattedDone());
              notifyListeners(getLineBreak());
            }
            catch (ADSContextException ade)
            {
              if (ade.getError() ==
                ADSContextException.ErrorType.ALREADY_REGISTERED)
              {
                notifyListeners(getFormattedWarning(
                    getMsg("administrator-already-registered")));
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
              getMsg("progress-creating-ads-on-remote", getHostDisplay(auth))));

          adsContext.createAdminData();
          adsContext.createAdministrator(getAdministratorProperties());
          adsContext.registerServer(
              getRemoteServerProperties(auth.getHostName(),
                  adsContext.getDirContext()));

          notifyListeners(getFormattedDone());
          notifyListeners(getLineBreak());
        }
        /* Configure local server to have an ADS */
        notifyListeners(getFormattedWithPoints(
            getMsg("progress-creating-ads")));
        try
        {
          localCtx = createLocalContext();
        }
        catch (Throwable t)
        {
          String failedMsg = getThrowableMsg("error-connecting-to-local", null,
              t);
          throw new ApplicationException(
              ApplicationException.Type.CONFIGURATION_ERROR, failedMsg, t);
        }
        createLocalAds(localCtx);
        notifyListeners(getFormattedDone());
        notifyListeners(getLineBreak());

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
          if (Utils.areDnsEqual(suffix.getDN(),
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
        }
        catch (ADSContextException adse)
        {
          if (adse.getError() ==
            ADSContextException.ErrorType.ALREADY_REGISTERED)
          {
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
          if (Utils.areDnsEqual(replica.getSuffix().getDN(),
              ADSContext.getAdministrationSuffixDN()))
          {
            notifyListeners(getFormattedWithPoints(
                getMsg("progress-initializing-ads")));

            int replicationId = replica.getReplicationId();
            if (replicationId == -1)
            {
              /**
               * This occurs if the remote server had not replication
               * configured.
               */
              InitialLdapContext rCtx = null;
              try
              {
                rCtx = getRemoteConnection(server, getTrustManager());
                ServerDescriptor s = ServerDescriptor.createStandalone(rCtx);
                for (ReplicaDescriptor r : s.getReplicas())
                {
                  if (Utils.areDnsEqual(r.getSuffix().getDN(), dn))
                  {
                    replicationId = r.getReplicationId();
                  }
                }
              }
              catch (NamingException ne)
              {
                String[] arg = {server.getHostPort(true)};
                throw new ApplicationException(
                    ApplicationException.Type.CONFIGURATION_ERROR,
                    getMsg("cannot-connect-to-remote-generic", arg), ne);
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
            int nTries = 4;
            boolean initDone = false;
            while (!initDone)
            {
              try
              {
                initializeSuffix(localCtx, replica.getReplicationId(),
                    ADSContext.getAdministrationSuffixDN(),
                    true, server.getHostPort(true));
                initDone = true;
              }
              catch (PeerNotFoundException pnfe)
              {
                LOG.log(Level.INFO, "Peer could not be found");
                if (nTries == 1)
                {
                  throw new ApplicationException(
                      ApplicationException.Type.APPLICATION,
                      pnfe.getLocalizedMessage(), null);
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
            break;
          }
        }
      }
      catch (NoPermissionException x)
      {
        String[] arg = {getHostDisplay(auth)};
        throw new ApplicationException(
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("cannot-connect-to-remote-permissions", arg), x);
      }
      catch (NamingException ne)
      {
        String[] arg = {getHostDisplay(auth)};
        throw new ApplicationException(
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("cannot-connect-to-remote-generic", arg), ne);
      }
      catch (TopologyCacheException tpe)
      {
        LOG.log(Level.WARNING, "Error reloading topology cache to "+
            "configure ADS replication.", tpe);
        throw new ApplicationException(
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("bug-msg"), tpe);
      }
      catch (ADSContextException ace)
      {
        String[] args = {getHostDisplay(auth), ace.toString()};
        throw new ApplicationException(
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("remote-ads-exception", args), ace);
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
            getMsg("progress-creating-ads")));
        try
        {
          localCtx = createLocalContext();
        }
        catch (Throwable t)
        {
          String failedMsg = getThrowableMsg("error-connecting-to-local", null,
              t);
          throw new ApplicationException(
              ApplicationException.Type.CONFIGURATION_ERROR, failedMsg, t);
        }
        createLocalAds(localCtx);
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
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("ads-exception", ace.toString()), ace);
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

    serverProperties.put(ADSContext.ServerProperty.JMX_PORT, "1689");
    serverProperties.put(ADSContext.ServerProperty.JMX_ENABLED, "false");

    String path;
    if (Utils.isWebStart())
    {
      path = getUserData().getServerLocation();
    }
    else
    {
      path = Utils.getInstallPathFromClasspath();
    }
    serverProperties.put(ADSContext.ServerProperty.INSTANCE_PATH, path);

    String serverID = serverProperties.get(ADSContext.ServerProperty.HOST_NAME)+
    ":"+getUserData().getServerPort();

    /* TODO: do we want to ask this specifically to the user? */
    serverProperties.put(ADSContext.ServerProperty.ID, serverID);

    serverProperties.put(ADSContext.ServerProperty.HOST_OS,
        Utils.getOSString());
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
        getMsg("global-administrator-description"));
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
    ArrayList<String> errorMsgs = new ArrayList<String>();
    String confirmationMsg = null;

    if (Utils.isWebStart())
    {
      // Check the server location
      String serverLocation = qs.getFieldStringValue(FieldName.SERVER_LOCATION);

      if ((serverLocation == null) || ("".equals(serverLocation.trim())))
      {
        errorMsgs.add(getMsg("empty-server-location"));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (!Utils.parentDirectoryExists(serverLocation))
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
          String[] arg =
            { serverLocation };
          errorMsgs.add(getMsg("parent-directory-could-not-be-found", arg));
          qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
        }
        else
        {
          if (!Utils.canWrite(existingParentDirectory))
          {
            String[] arg =
              { existingParentDirectory };
            errorMsgs.add(getMsg("directory-not-writable", arg));
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
          }
          else if (!Utils.hasEnoughSpace(existingParentDirectory,
              getRequiredInstallSpace()))
          {
            long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
            String[] args =
              { existingParentDirectory, String.valueOf(requiredInMb) };
            errorMsgs.add(getMsg("not-enough-disk-space", args));
            qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
          }
          else
          {
            String[] arg =
            { serverLocation };
            confirmationMsg =
              getMsg("parent-directory-does-not-exist-confirmation", arg);
            getUserData().setServerLocation(serverLocation);
          }
        }
      } else if (Utils.fileExists(serverLocation))
      {
        String[] arg =
          { serverLocation };
        errorMsgs.add(getMsg("file-exists", arg));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else if (Utils.directoryExistsAndIsNotEmpty(serverLocation))
      {
        String[] arg =
          { serverLocation };
        errorMsgs.add(getMsg("directory-exists-not-empty", arg));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else if (!Utils.canWrite(serverLocation))
      {
        String[] arg =
          { serverLocation };
        errorMsgs.add(getMsg("directory-not-writable", arg));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);

      } else if (!Utils.hasEnoughSpace(serverLocation,
          getRequiredInstallSpace()))
      {
        long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
        String[] args =
          { serverLocation, String.valueOf(requiredInMb) };
        errorMsgs.add(getMsg("not-enough-disk-space", args));
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
    if ((hostName == null) || hostName.trim().isEmpty())
    {
      errorMsgs.add(getMsg("empty-host-name"));
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
        String[] args =
          { String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE) };
        errorMsgs.add(getMsg("invalid-port-value-range", args));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      } else if (!Utils.canUseAsPort(port))
      {
        if (Utils.isPriviledgedPort(port))
        {
          errorMsgs.add(getMsg("cannot-bind-priviledged-port", new String[]
            { String.valueOf(port) }));
        } else
        {
          errorMsgs.add(getMsg("cannot-bind-port", new String[]
            { String.valueOf(port) }));
        }
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);

      } else
      {
        getUserData().setServerPort(port);
        qs.displayFieldInvalid(FieldName.SERVER_PORT, false);
      }

    } catch (NumberFormatException nfe)
    {
      String[] args =
        { String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE) };
      errorMsgs.add(getMsg("invalid-port-value-range", args));
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
        String[] args =
          { String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE) };
        errorMsgs.add(getMsg("invalid-secure-port-value-range", args));
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
      } else if (!Utils.canUseAsPort(securePort))
      {
        if (Utils.isPriviledgedPort(securePort))
        {
          errorMsgs.add(getMsg("cannot-bind-priviledged-port", new String[]
            { String.valueOf(securePort) }));
        } else
        {
          errorMsgs.add(getMsg("cannot-bind-port", new String[]
            { String.valueOf(securePort) }));
        }
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);

      }
      else if (port == securePort)
      {
        errorMsgs.add(getMsg("equal-ports",
            new String[] { String.valueOf(securePort) }));
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
      errorMsgs.add(getMsg("empty-directory-manager-dn"));
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else if (!Utils.isDn(dmDn))
    {
      errorMsgs.add(getMsg("not-a-directory-manager-dn"));
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    } else if (Utils.isConfigurationDn(dmDn))
    {
      errorMsgs.add(getMsg("directory-manager-dn-is-config-dn"));
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
      errorMsgs.add(getMsg("not-equal-pwd"));
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM, true);
      pwdValid = false;

    }
    if (pwd1.length() < MIN_DIRECTORY_MANAGER_PWD)
    {
      errorMsgs.add(getMsg(("pwd-too-short"), new String[]
        { String.valueOf(MIN_DIRECTORY_MANAGER_PWD) }));
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
          Utils.getStringFromCollection(errorMsgs, "\n"));
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
    ArrayList<String> errorMsgs = new ArrayList<String>();

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
          Utils.getStringFromCollection(errorMsgs, "\n"));
    }
    if (confirmEx != null)
    {
      throw confirmEx;
    }
  }

  private int checkReplicationPort(QuickSetup qs, ArrayList<String> errorMsgs)
  {
    int replicationPort = -1;
    String sPort = qs.getFieldStringValue(FieldName.REPLICATION_PORT);
    try
    {
      replicationPort = Integer.parseInt(sPort);
      if ((replicationPort < MIN_PORT_VALUE) ||
          (replicationPort > MAX_PORT_VALUE))
      {
        String[] args =
        { String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE) };
        errorMsgs.add(getMsg("invalid-replication-port-value-range", args));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      } else if (!Utils.canUseAsPort(replicationPort))
      {
        if (Utils.isPriviledgedPort(replicationPort))
        {
          errorMsgs.add(getMsg("cannot-bind-priviledged-port",
              new String[] { String.valueOf(replicationPort) }));
        } else
        {
          errorMsgs.add(getMsg("cannot-bind-port",
              new String[] { String.valueOf(replicationPort) }));
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
              getMsg("replication-port-already-chosen-for-other-protocol"));
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
        }
        else
        {
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, false);
        }
      }

    } catch (NumberFormatException nfe)
    {
      String[] args =
      { String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE) };
      errorMsgs.add(getMsg("invalid-replication-port-value-range", args));
      qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
    }
    return replicationPort;
  }

  private void checkRemoteHostPortDnAndPwd(String host, String sPort, String dn,
      String pwd, QuickSetup qs, ArrayList<String> errorMsgs)
  {
    // Check host
    if ((host == null) || (host.length() == 0))
    {
      errorMsgs.add(getMsg("empty-remote-host"));
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
      errorMsgs.add(getMsg("invalid-remote-port"));
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
    }

    // Check dn
    if ((dn == null) || (dn.length() == 0))
    {
      errorMsgs.add(getMsg("empty-remote-dn"));
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, false);
    }

    // Check password
    if ((pwd == null) || (pwd.length() == 0))
    {
      errorMsgs.add(getMsg("empty-remote-pwd"));
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, false);
    }
  }

  private void updateUserDataWithADS(String host, int port, String dn,
      String pwd, QuickSetup qs, ArrayList<String> errorMsgs,
      boolean[] hasGlobalAdministrators,
      String[] effectiveDn) throws UserDataException
  {
    String ldapUrl;
    host = Utils.getHostNameForLdapUrl(host);
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
      try
      {
        if (isSecure)
        {
          ctx = Utils.createLdapsContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null, trustManager);
        }
        else
        {
          ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
              Utils.getDefaultLDAPTimeout(), null);
        }
      }
      catch (Throwable t)
      {
        if (!isCertificateException(t))
        {
          // Try using a global administrator
          dn = ADSContext.getAdministratorDN(dn);
          if (isSecure)
          {
            ctx = Utils.createLdapsContext(ldapUrl, dn, pwd,
                Utils.getDefaultLDAPTimeout(), null, trustManager);
          }
          else
          {
            ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
                Utils.getDefaultLDAPTimeout(), null);
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
            String errorMsg = getMsg("not-global-administrator-provided");
            throw new UserDataException(Step.REPLICATION_OPTIONS, errorMsg);
          case GENERIC_CREATING_CONNECTION:
            if ((e.getCause() != null) &&
                isCertificateException(e.getCause()))
            {
              UserDataCertificateException.Type excType;
              ApplicationTrustManager.Cause cause =
                trustManager.getLastRefusedCause();
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
                  h = getMsg("not-available-label");
                  p = -1;
                }
                throw new UserDataCertificateException(Step.REPLICATION_OPTIONS,
                    getMsg("certificate-exception", h, String.valueOf(p)),
                    e.getCause(), h, p,
                    e.getTrustManager().getLastRefusedChain(),
                    trustManager.getLastRefusedAuthType(), excType);
              }
            }
          }
          exceptionMsgs.add(getStringRepresentation(e));
        }
        if (exceptionMsgs.size() > 0)
        {
          String confirmationMsg =
            getMsg("error-reading-registered-servers-confirm",
              Utils.getStringFromCollection(exceptionMsgs, "\n"));
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
              getMsg("certificate-exception", host, String.valueOf(port)), t,
              host, port, trustManager.getLastRefusedChain(),
              trustManager.getLastRefusedAuthType(), excType);
        }
        else
        {
          String[] arg = {host+":"+port, t.toString()};
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
          errorMsgs.add(getMsg("cannot-connect-to-remote-generic", arg));
        }
      }
      else if (t instanceof AuthenticationException)
      {
        String[] arg = {host+":"+port};
        errorMsgs.add(getMsg("cannot-connect-to-remote-authentication", arg));
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
      }
      else if (t instanceof NoPermissionException)
      {
        String[] arg = {host+":"+port};
        errorMsgs.add(getMsg("cannot-connect-to-remote-permissions", arg));
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
      }
      else if (t instanceof NamingException)
      {
        String[] arg = {host+":"+port, t.toString()};
        errorMsgs.add(getMsg("cannot-connect-to-remote-generic", arg));
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
      }
      else if (t instanceof ADSContextException)
      {
        String[] args = {host+":"+port, t.toString()};
        errorMsgs.add(getMsg("remote-ads-exception", args));
      }
      else
      {
        throw new UserDataException(Step.REPLICATION_OPTIONS,
            getThrowableMsg("bug-msg", null, t));
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
    effectiveDn[0] = dn;
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
    ArrayList<String> errorMsgs = new ArrayList<String>();

    // Check the Global Administrator UID
    String uid = qs.getFieldStringValue(FieldName.GLOBAL_ADMINISTRATOR_UID);

    if ((uid == null) || (uid.trim().length() == 0))
    {
      errorMsgs.add(getMsg("empty-administrator-uid"));
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
      errorMsgs.add(getMsg("not-equal-pwd"));
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM, true);
      pwdValid = false;

    }
    if (pwd1.length() < MIN_DIRECTORY_MANAGER_PWD)
    {
      errorMsgs.add(getMsg(("pwd-too-short"), new String[]
        { String.valueOf(MIN_DIRECTORY_MANAGER_PWD) }));
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
          Utils.getStringFromCollection(errorMsgs, "\n"));
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
    ArrayList<String> errorMsgs = new ArrayList<String>();
    if (qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE_OPTIONS) ==
      SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES)
    {
      Set s = (Set)qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE);
      if (s.size() == 0)
      {
        errorMsgs.add(getMsg("no-suffixes-chosen-to-replicate"));
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
          Utils.getStringFromCollection(errorMsgs, "\n"));
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
    ArrayList<String> errorMsgs = new ArrayList<String>();
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
          String[] args = { server.getHostPort(true),
              String.valueOf(MIN_PORT_VALUE), String.valueOf(MAX_PORT_VALUE)};
          errorMsgs.add(getMsg("invalid-remote-replication-port-value-range",
              args));
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
            errorMsgs.add(getMsg(
                    "remote-replication-port-already-chosen-for-other-protocol",
                    server.getHostPort(true)));
          }
        }
        servers.put(server, replicationPort);
      } catch (NumberFormatException nfe)
      {
        String[] args = { hostName, String.valueOf(MIN_PORT_VALUE),
            String.valueOf(MAX_PORT_VALUE)};
        errorMsgs.add(getMsg("invalid-remote-replication-port-value-range",
            args));
      }
    }

    if (errorMsgs.size() > 0)
    {
      qs.displayFieldInvalid(FieldName.REMOTE_REPLICATION_PORT, true);
      throw new UserDataException(Step.REMOTE_REPLICATION_PORTS,
          Utils.getStringFromCollection(errorMsgs, "\n"));
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
    ArrayList<String> errorMsgs = new ArrayList<String>();

    NewSuffixOptions dataOptions = null;

    // Check the base dn
    boolean validBaseDn = false;
    String baseDn = qs.getFieldStringValue(FieldName.DIRECTORY_BASE_DN);
    if ((baseDn == null) || (baseDn.trim().length() == 0))
    {
      errorMsgs.add(getMsg("empty-base-dn"));
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, true);
    } else if (!Utils.isDn(baseDn))
    {
      errorMsgs.add(getMsg("not-a-base-dn"));
      qs.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, true);
    } else if (Utils.isConfigurationDn(baseDn))
    {
      errorMsgs.add(getMsg("base-dn-is-configuration-dn"));
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
        errorMsgs.add(getMsg("no-ldif-path"));
        qs.displayFieldInvalid(FieldName.LDIF_PATH, true);
      } else if (!Utils.fileExists(ldifPath))
      {
        errorMsgs.add(getMsg("ldif-file-does-not-exist"));
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
        errorMsgs.add(getMsg("no-number-entries"));
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
          String[] args =
                { String.valueOf(MIN_NUMBER_ENTRIES),
                    String.valueOf(MAX_NUMBER_ENTRIES) };
          errorMsgs.add(getMsg("invalid-number-entries-range", args));
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
          Utils.getStringFromCollection(errorMsgs, "\n"));
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
    getUserData().setStartServer(b.booleanValue());
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
  private String getSelfSignedKeystorePath()
  {
    String parentFile = Utils.getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (Utils.getPath(parentFile, "keystore"));
  }

  /**
   * Returns the trustmanager path to be used for generating a self-signed
   * certificate.
   * @return the trustmanager path to be used for generating a self-signed
   * certificate.
   */
  private String getTrustManagerPath()
  {
    String parentFile = Utils.getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (Utils.getPath(parentFile, "truststore"));
  }

  /**
   * Returns the path of the self-signed that we export to be able to create
   * a truststore.
   * @return the path of the self-signed that is exported.
   */
  private String getTemporaryCertificatePath()
  {
    String parentFile = Utils.getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (Utils.getPath(parentFile, "server-cert.txt"));
  }

  /**
   * Returns the path to be used to store the password of the keystore.
   * @return the path to be used to store the password of the keystore.
   */
  private String getKeystorePinPath()
  {
    String parentFile = Utils.getPath(getInstallationPath(),
        Installation.CONFIG_PATH_RELATIVE);
    return (Utils.getPath(parentFile, "keystore.pin"));
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
   * Returns a randomly generated password for the self-signed certificate
   * keystore.
   * @return a randomly generated password for the self-signed certificate
   * keystore.
   */
  private String getSelfSignedCertificatePwd()
  {
    int pwdLength = 50;
    char[] pwd = new char[pwdLength];
    Random random = new Random();
    for (int pos=0; pos < pwdLength; pos++) {
        int type = getRandomInt(random,3);
        char nextChar = getRandomChar(random,type);
        pwd[pos] = nextChar;
    }

    String pwdString = new String(pwd);
    return pwdString;
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

  private boolean isCertificateException(Throwable t)
  {
    return Utils.isCertificateException(t);
  }

  private String getStringRepresentation(TopologyCacheException e)
  {
    StringBuilder buf = new StringBuilder();

    String ldapUrl = e.getLdapUrl();
    if (ldapUrl != null)
    {
      String hostName = ldapUrl.substring(ldapUrl.indexOf("://") + 3);
      buf.append(getMsg("server-error", hostName) + " ");
    }
    if (e.getCause() instanceof NamingException)
    {
      buf.append(getThrowableMsg("bug-msg", null, e.getCause()));
    }
    else
    {
      // This is unexpected.
      buf.append(getThrowableMsg("bug-msg", null, e.getCause()));
    }
    return buf.toString();
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
    String ldapUrl = "ldap://"+getUserData().getHostName()+":"+
    getUserData().getServerPort();
    String dn = getUserData().getDirectoryManagerDn();
    String pwd = getUserData().getDirectoryManagerPwd();
    return Utils.createLdapContext(ldapUrl, dn, pwd,
        Utils.getDefaultLDAPTimeout(), null);
  }
  private void createLocalAds(InitialLdapContext ctx)
  throws ApplicationException, ADSContextException
  {
    try
    {
      ADSContext adsContext = new ADSContext(ctx);
      adsContext.createAdminData();
      adsContext.registerServer(getNewServerAdsProperties());
      if (getUserData().mustCreateAdministrator())
      {
        adsContext.createAdministrator(getAdministratorProperties());
      }
    }
    catch (ADSContextException ace)
    {
      throw ace;
    }
    catch (Throwable t)
    {
      String failedMsg = getThrowableMsg("bug-msg", null, t);
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR, failedMsg, t);
    }
  }

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
    }
    else
    {
      adsProperties = server.getAdsProperties();
    }

    ServerLoader loader = new ServerLoader(adsProperties, auth.getDn(),
        auth.getPwd(), trustManager);

    InitialLdapContext ctx = null;
    try
    {
      ctx = loader.createContext();
    }
    catch (NamingException ne)
    {
      System.out.println("dn: "+auth.getDn());
      System.out.println("dn: "+auth.getDn());

      String errorMessage = getMsg("cannot-connect-to-remote-generic",
          server.getHostPort(true), ne.toString(true));
      throw new ApplicationException(
          ApplicationException.Type.CONFIGURATION_ERROR, errorMessage, ne);
    }
    return ctx;
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
        String[] arg = {sourceServerDisplay};
        throw new ApplicationException(ApplicationException.Type.APPLICATION,
            getThrowableMsg("error-launching-initialization", arg, ne), ne);
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
    String lastDisplayedMsg = null;
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
          String msg;
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
              msg = getMsg("initialize-progress-with-percentage", sProcessed,
                  String.valueOf(perc));
            }
            else
            {
              //msg = getMsg("no-entries-to-initialize");
              msg = null;
            }
          }
          else if (processed != -1)
          {
            msg = getMsg("initialize-progress-with-processed", sProcessed);
          }
          else if (unprocessed != -1)
          {
            msg = getMsg("initialize-progress-with-unprocessed",
                sUnprocessed);
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
          String errorMsg;
          if (lastLogMsg == null)
          {
            errorMsg = getMsg("error-during-initialization-no-log",
                sourceServerDisplay);
          }
          else
          {
            errorMsg = getMsg("error-during-initialization-log",
                sourceServerDisplay, lastLogMsg);
          }

          if (helper.isCompletedWithErrors(state))
          {
            notifyListeners(getFormattedWarning(errorMsg));
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            ApplicationException ae = new ApplicationException(
                ApplicationException.Type.APPLICATION, errorMsg, null);
            if ((lastLogMsg != null) &&
                helper.isPeersNotFoundError(lastLogMsg))
            {
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
                getMsg("suffix-initialized-successfully")));
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
        notifyListeners(getFormattedProgress(
            getMsg("suffix-initialized-successfully")));
      }
      catch (NamingException ne)
      {
        String[] arg = {sourceServerDisplay};
        throw new ApplicationException(ApplicationException.Type.APPLICATION,
            getThrowableMsg("error-pooling-initialization", arg, ne), ne);
      }
    }
  }

  private String getFirstValue(SearchResult entry, String attrName)
  throws NamingException
  {
    return Utils.getFirstValue(entry, attrName);
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
class PeerNotFoundException extends Exception
{
  private static final long serialVersionUID = -362726764261560341L;

  /**
   * The constructor for the exception.
   * @param localizedMsg the localized message.
   */
  PeerNotFoundException(String localizedMsg)
  {
    super(localizedMsg);
  }
}
