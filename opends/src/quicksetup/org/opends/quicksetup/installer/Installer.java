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
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.*;
import java.util.logging.Logger;
import java.awt.event.WindowEvent;

import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.*;
import org.opends.server.util.CertificateManager;
import org.opends.quicksetup.installer.ui.DataOptionsPanel;
import org.opends.quicksetup.installer.ui.DataReplicationPanel;
import org.opends.quicksetup.installer.ui.GlobalAdministratorPanel;
import org.opends.quicksetup.installer.ui.InstallReviewPanel;
import org.opends.quicksetup.installer.ui.InstallWelcomePanel;
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
//  TODO: remove this comment once the replication code is in place.
    SUBSTEPS.add(Step.SUFFIXES_OPTIONS);
    //SUBSTEPS.add(Step.NEW_SUFFIX_OPTIONS);
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
    /*
    lstSteps.add(REPLICATION_OPTIONS);
    lstSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    lstSteps.add(SUFFIXES_OPTIONS);
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
  public boolean canClose(WizardStep step) {
    return step == PROGRESS;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canCancel(WizardStep step) {
    return false;
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
    else
    {
      isVisible = true;
    }
    // TODO: to delete the following line once the replication code works.
    isVisible = true;
    return isVisible;
  }

  /**
   * {@inheritDoc}
   */
  public void finishClicked(final WizardStep cStep, final QuickSetup qs) {
    if (cStep == Step.REVIEW) {
        updateUserDataForReviewPanel(qs);
        qs.launch();
        qs.setCurrentStep(Step.PROGRESS);
    } else {
        throw new IllegalStateException(
                "Cannot click on finish when we are not in the Review window");
    }
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
  public void cancelClicked(WizardStep cStep, QuickSetup qs) {
    // do nothing;
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
  public String getCloseButtonToolTip() {
    return "close-button-install-tooltip";
  }

  /**
   * {@inheritDoc}
   */
  public String getFinishButtonToolTip() {
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
      } else if (step == PROGRESS) {
        dlg.setDefaultButton(ButtonName.CLOSE);
      } else if (step == WELCOME) {
        dlg.setDefaultButton(ButtonName.NEXT);
        dlg.setFocusOnButton(ButtonName.NEXT);
      } else if (step == REVIEW) {
        dlg.setDefaultButton(ButtonName.NEXT);
      } else if (step == PROGRESS) {
        dlg.setFocusOnButton(ButtonName.CLOSE);
        dlg.setButtonEnabled(ButtonName.CLOSE, false);
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
        next = Step.REVIEW;
        break;
      default:
        next = Step.NEW_SUFFIX_OPTIONS;
      }
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
    // TODO: remove this comment once the replication code is in place.
    /*
    orderedSteps.add(REPLICATION_OPTIONS);
    orderedSteps.add(CREATE_GLOBAL_ADMINISTRATOR);
    orderedSteps.add(SUFFIXES_OPTIONS);
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

    argList.add("-x");
    argList.add(String.valueOf(getUserData().getServerJMXPort()));

    argList.add("-D");
    argList.add(getUserData().getDirectoryManagerDn());

    argList.add("-w");
    argList.add(getUserData().getDirectoryManagerPwd());

    if (createNotReplicatedSuffix())
    {
      argList.add("-b");
      argList.add(getUserData().getNewSuffixOptions().getBaseDn());
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
            getSelfSignedCertificateSubjectDN(sec),
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
  protected void createBaseEntry() throws ApplicationException {
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
  protected void importLDIF() throws ApplicationException {
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
  protected void importAutomaticallyGenerated() throws ApplicationException {
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
    hmSummary.put(InstallProgressStep.STARTING_SERVER,
        getFormattedSummary(getMsg("summary-starting")));
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
        getFormattedError(getMsg("summary-install-finished-with-error")));
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
    else
    {
      /* We must replicate some suffixes: for the moment just create them. */
      /* TODO: replicate them. */
      Set<SuffixDescriptor> suffixesToReplicate =
        getUserData().getSuffixesToReplicateOptions().getAvailableSuffixes();
      boolean startedServer = false;
      if (suffixesToReplicate.size() > 0)
      {
        startServerWithoutConnectionHandlers();
        startedServer = true;
      }
      for (SuffixDescriptor suffix: suffixesToReplicate)
      {
        // TODO: localize
        notifyListeners(getFormattedWithPoints("Creating Suffix"));

        ArrayList<String> argList = new ArrayList<String>();
        argList.add("-C");
        argList.add(CONFIG_CLASS_NAME);

        argList.add("-c");
        argList.add(getInstallation().getCurrentConfigurationFile().toString());

        argList.add("-b");
        argList.add(suffix.getDN());

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
        notifyListeners(getFormattedDone());

        // TODO: localize
        notifyListeners(
            getFormattedProgress("One day we will replicate the suffixes!"));
      }
      if (startedServer)
      {
        getServerController().stopServerInProcess();
      }
    }
  }

  /**
   * This methods updates the ADS contents (and creates the according suffixes).
   * @throws ApplicationException if something goes wrong.
   */
  protected void updateADS() throws ApplicationException
  {
    if (true) return;
    /* First check if the remote server contains an ADS: if it is the case the
     * best is to update its contents with the new data and then configure the
     * local server to be replicated with the remote server.
     */
    DataReplicationOptions repl =
      getUserData().getReplicationOptions();
    boolean remoteServer =
      repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
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
        ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
            Utils.getDefaultLDAPTimeout(), null);

        ADSContext adsContext = new ADSContext(ctx);
        if (adsContext.hasAdminData())
        {
          /* Add global administrator if the user specified one. */
          if (getUserData().mustCreateAdministrator())
          {
            try
            {
              String[] arg = {getHostDisplay(auth)};
              notifyListeners(getFormattedWithPoints(
                  getMsg("creating-administrator", arg)));
              adsContext.createAdministrator(getAdministratorProperties());
              notifyListeners(getFormattedDone());
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

          Map<ADSContext.ServerProperty, Object> serverProperties =
            getNewServerProperties();

          /* Register new server data. */
          adsContext.registerServer(serverProperties);

          /* Configure local server to have an ADS and replicate it */
          // TODO
          notifyListeners(getFormattedProgress(
              "Here we create the new server ADS and we replicate it.\n"));
        }
        else
        {
          /* TODO: We need to integrate in remote framework to make this work.
           */
          /*
          adsContext.createAdminData();
          adsContext.createAdministrator(getAdministratorProperties());
          adsContext.registerServer(
              getRemoteServerProperties(adsContext.getDirContext()));
          adsContext.registerServer(getNewServerProperties());
          */
          notifyListeners(getFormattedProgress(
              "Here we update the server in "+getHostDisplay(auth)+"\n"));

          /* Configure local server to have an ADS and replicate it */
          // TODO
          notifyListeners(getFormattedProgress(
              "Here we create the new server ADS and we replicate it.\n"));

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
      }
    }
    else
    {
      notifyListeners(getFormattedWithPoints(getMsg("creating-ads")));
      Map<ADSContext.ServerProperty, Object> serverProperties =
        getNewServerProperties();
      try
      {
        ADSContext.createOfflineAdminData(serverProperties,
            getUserData().getServerLocation(), getBackendName());
      }
      catch (ADSContextException ace)
      {
        throw new ApplicationException(
            ApplicationException.Type.CONFIGURATION_ERROR,
            getMsg("local-ads-exception"), ace);
      }
      notifyListeners(getFormattedDone());
    }
  }

  /**
   * Tells whether we must create a suffix that we are not going to replicate
   * with other servers or not.
   * @return <CODE>true</CODE> if we must create a new suffix and
   * <CODE>false</CODE> otherwise.
   */
  private boolean createNotReplicatedSuffix()
  {
    boolean createSuffix;

    DataReplicationOptions repl =
      getUserData().getReplicationOptions();

    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();

    createSuffix =
      (repl.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY) ||
      (repl.getType() == DataReplicationOptions.Type.STANDALONE) ||
      (suf.getType() ==
        SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY);

    return createSuffix;
  }

  private String getLdapUrl(AuthenticationData auth)
  {
    return "ldap://"+auth.getHostName()+":"+auth.getPort();
  }

  private String getHostDisplay(AuthenticationData auth)
  {
    return auth.getHostName()+":"+auth.getPort();
  }

  private Map<ADSContext.ServerProperty, Object> getNewServerProperties()
  {
    Map<ADSContext.ServerProperty, Object> serverProperties =
      new HashMap<ADSContext.ServerProperty, Object>();
    // TODO: this might not work
    try
    {
      serverProperties.put(ADSContext.ServerProperty.HOSTNAME,
          java.net.InetAddress.getLocalHost().getHostName());
    }
    catch (Throwable t)
    {
      t.printStackTrace();
    }
    serverProperties.put(ADSContext.ServerProperty.PORT,
        String.valueOf(getUserData().getServerPort()));
    serverProperties.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");

    // TODO: even if the user does not configure SSL maybe we should choose
    // a secure port that is not being used and that we can actually use.
    serverProperties.put(ADSContext.ServerProperty.SECURE_PORT, "636");
    serverProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "false");

    serverProperties.put(ADSContext.ServerProperty.JMX_PORT,
        String.valueOf(getUserData().getServerJMXPort()));
    serverProperties.put(ADSContext.ServerProperty.JMX_ENABLED, "true");

    serverProperties.put(ADSContext.ServerProperty.INSTANCE_PATH,
        getUserData().getServerLocation());

    String serverID = serverProperties.get(ADSContext.ServerProperty.HOSTNAME)+
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
    adminProperties.put(ADSContext.AdministratorProperty.UID,
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

    int defaultJMXPort =
      UserData.getDefaultJMXPort(new int[] {port, securePort});
    if (defaultJMXPort != -1)
    {
      getUserData().setServerJMXPort(defaultJMXPort);
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
    String host = null;
    Integer port = null;
    String dn = null;
    String pwd = null;
    ArrayList<String> errorMsgs = new ArrayList<String>();

    DataReplicationOptions.Type type = (DataReplicationOptions.Type)
      qs.getFieldValue(FieldName.REPLICATION_OPTIONS);
    host = qs.getFieldStringValue(FieldName.REMOTE_SERVER_HOST);
    dn = qs.getFieldStringValue(FieldName.REMOTE_SERVER_DN);
    pwd = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PWD);

    switch (type)
    {
    case IN_EXISTING_TOPOLOGY:
    {
      // Check host name
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
      String sPort = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PORT);
      try
      {
        port = Integer.parseInt(sPort);
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

      if (errorMsgs.size() == 0)
      {
        // Try to connect
        String ldapUrl = "ldap://"+host+":"+port;
        InitialLdapContext ctx = null;
        try
        {
          try
          {
            ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
                Utils.getDefaultLDAPTimeout(), null);
          }
          catch (Throwable t)
          {
            // Try using a global administrator
            dn = ADSContext.getAdministratorDN(dn);
            ctx = Utils.createLdapContext(ldapUrl, dn, pwd,
                Utils.getDefaultLDAPTimeout(), null);
          }

          ADSContext adsContext = new ADSContext(ctx);
          if (adsContext.hasAdminData())
          {
            /* Check if there are already global administrators */
            Set administrators = adsContext.readAdministratorRegistry();
            if (administrators.size() > 0)
            {
              hasGlobalAdministrators = true;
            }
            updateUserDataWithSuffixesInADS(adsContext);
          }
          else
          {
            getUserData().setSuffixesToReplicateOptions(
                new SuffixesToReplicateOptions(
                    SuffixesToReplicateOptions.Type.
                    REPLICATE_WITH_EXISTING_SUFFIXES,
                    staticSuffixes(),
                    staticSuffixes()));
          }
        }
        catch (NoPermissionException x)
        {
          String[] arg = {host+":"+port};
          errorMsgs.add(getMsg("cannot-connect-to-remote-permissions", arg));
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
        }
        catch (NamingException ne)
        {
          String[] arg = {host+":"+port};
          errorMsgs.add(getMsg("cannot-connect-to-remote-generic", arg));
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
        }
        catch (ADSContextException ace)
        {
          String[] args = {host+":"+port, ace.toString()};
          errorMsgs.add(getMsg("remote-ads-exception", args));
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
      break;
    }
    case STANDALONE:
    {
      Set<SuffixDescriptor> available;
      SuffixesToReplicateOptions repl =
        getUserData().getSuffixesToReplicateOptions();

      if (repl != null)
      {
        available = repl.getAvailableSuffixes();
      }
      else
      {
        available = new HashSet<SuffixDescriptor>();
      }

      Set<SuffixDescriptor> chosen;
      if (repl != null)
      {
        chosen = repl.getSuffixes();
      }
      else
      {
        chosen = new HashSet<SuffixDescriptor>();
      }

      getUserData().setSuffixesToReplicateOptions(
          new SuffixesToReplicateOptions(
              SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE,
              available,
              chosen));
      break;
    }
    case FIRST_IN_TOPOLOGY:
    {
      Set<SuffixDescriptor> available;
      SuffixesToReplicateOptions repl =
        getUserData().getSuffixesToReplicateOptions();

      if (repl != null)
      {
        available = repl.getAvailableSuffixes();
      }
      else
      {
        available = new HashSet<SuffixDescriptor>();
      }

      Set<SuffixDescriptor> chosen;
      if (repl != null)
      {
        chosen = repl.getSuffixes();
      }
      else
      {
        chosen = new HashSet<SuffixDescriptor>();
      }
      getUserData().setSuffixesToReplicateOptions(
          new SuffixesToReplicateOptions(
              SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY,
              available,
              chosen));
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

      DataReplicationOptions repl = new DataReplicationOptions(type,
          auth);
      getUserData().setReplicationOptions(repl);

      getUserData().createAdministrator(!hasGlobalAdministrators &&
      type == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY);

    }
    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.REPLICATION_OPTIONS,
          Utils.getStringFromCollection(errorMsgs, "\n"));
    }
  }

  /**
   * Validate the data provided by the user in the create global administrator
   * panel and update the UserInstallData object according to that content.
   *
   * @throws an
   *           UserInstallDataException if the data provided by the user is not
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
      getUserData().setDirectoryManagerPwd(pwd1);
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
   * @throws an
   *           UserInstallDataException if the data provided by the user is not
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
   * Validate the data provided by the user in the new suffix data options panel
   * and update the UserInstallData object according to that content.
   *
   * @throws an
   *           UserInstallDataException if the data provided by the user is not
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
      InitialLdapContext ctx) throws NamingException
  {
    // TODO: use administration framework.
    return new HashMap<ADSContext.ServerProperty, Object>();
  }

  /**
   * Update the UserInstallData object according to the content of the review
   * panel.
   */
  private void updateUserDataWithSuffixesInADS(ADSContext adsContext)
  {
    SuffixesToReplicateOptions suf =
      getUserData().getSuffixesToReplicateOptions();
    SuffixesToReplicateOptions.Type type;
    Set<SuffixDescriptor> suffixes = null;
    if (suf == null)
    {
      type = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
    }
    else
    {
      type = suf.getType();
    }
    // TODO: get the suffixes using the adsContext.
    if (suffixes == null)
    {
      suffixes = new HashSet<SuffixDescriptor>();
    }
    suffixes = staticSuffixes();
    getUserData().setSuffixesToReplicateOptions(
        new SuffixesToReplicateOptions(type, suffixes, suffixes));
  }

  private Set<SuffixDescriptor> staticSuffixes()
  {
    ServerDescriptor server1 = new ServerDescriptor();
    Map<ADSContext.ServerProperty, Object> serverProp1 =
      new HashMap<ADSContext.ServerProperty, Object>();
    serverProp1.put(ADSContext.ServerProperty.HOSTNAME, "potato.france");
    serverProp1.put(ADSContext.ServerProperty.PORT, "389");
    serverProp1.put(ADSContext.ServerProperty.SECURE_PORT, "689");
    serverProp1.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");
    serverProp1.put(ADSContext.ServerProperty.LDAPS_ENABLED, "false");
    serverProp1.put(ADSContext.ServerProperty.INSTANCE_PATH, "/tmp/jvl1");
    server1.setAdsProperties(serverProp1);

    ServerDescriptor server2 = new ServerDescriptor();
    Map<ADSContext.ServerProperty, Object> serverProp2 =
      new HashMap<ADSContext.ServerProperty, Object>();
    serverProp2.put(ADSContext.ServerProperty.HOSTNAME, "skalariak.france");
    serverProp2.put(ADSContext.ServerProperty.PORT, "389");
    serverProp2.put(ADSContext.ServerProperty.SECURE_PORT, "689");
    serverProp2.put(ADSContext.ServerProperty.LDAP_ENABLED, "false");
    serverProp2.put(ADSContext.ServerProperty.LDAPS_ENABLED, "true");
    serverProp2.put(ADSContext.ServerProperty.INSTANCE_PATH, "/tmp/jvl2");
    server2.setAdsProperties(serverProp2);

    SuffixDescriptor suffix1 = new SuffixDescriptor();
    suffix1.setDN("dc=example,dc=com");
    Set<ReplicaDescriptor> replicas1 = new HashSet<ReplicaDescriptor>();

    SuffixDescriptor suffix2 = new SuffixDescriptor();
    suffix2.setDN("dc=for real,dc=com");
    Set<ReplicaDescriptor> replicas2 = new HashSet<ReplicaDescriptor>();

    SuffixDescriptor suffix3 = new SuffixDescriptor();
    suffix3.setDN("dc=s3,dc=com");
    Set<ReplicaDescriptor> replicas3 = new HashSet<ReplicaDescriptor>();

    SuffixDescriptor suffix4 = new SuffixDescriptor();
    suffix4.setDN("dc=s4,dc=com");
    Set<ReplicaDescriptor> replicas4 = new HashSet<ReplicaDescriptor>();


    ReplicaDescriptor replica1 = new ReplicaDescriptor();
    replica1.setSuffix(suffix1);
    replica1.setServer(server1);
    replica1.setEntries(1002);
    replicas1.add(replica1);

    ReplicaDescriptor replica2 = new ReplicaDescriptor();
    replica2.setSuffix(suffix1);
    replica2.setServer(server2);
    replica2.setEntries(1003);
    replicas1.add(replica2);

    suffix1.setReplicas(replicas1);

    ReplicaDescriptor replica3 = new ReplicaDescriptor();
    replica3.setSuffix(suffix2);
    replica3.setServer(server2);
    replicas2.add(replica3);

    suffix2.setReplicas(replicas2);

    ReplicaDescriptor replica5 = new ReplicaDescriptor();
    replica5.setSuffix(suffix3);
    replica5.setServer(server1);
    replica5.setEntries(1003);
    replicas3.add(replica5);

    ReplicaDescriptor replica6 = new ReplicaDescriptor();
    replica6.setSuffix(suffix3);
    replica6.setServer(server2);
    replica6.setEntries(1003);
    replicas3.add(replica6);

    suffix3.setReplicas(replicas3);

    ReplicaDescriptor replica7 = new ReplicaDescriptor();
    replica7.setSuffix(suffix4);
    replica7.setServer(server1);
    replica7.setEntries(1003);
    replicas4.add(replica7);

    ReplicaDescriptor replica8 = new ReplicaDescriptor();
    replica8.setSuffix(suffix3);
    replica8.setServer(server2);
    replica8.setEntries(1003);
    replicas4.add(replica8);

    suffix4.setReplicas(replicas4);

    Set<SuffixDescriptor> suffixes = new HashSet<SuffixDescriptor>();
    suffixes.add(suffix1);
    suffixes.add(suffix2);
    suffixes.add(suffix3);
    suffixes.add(suffix4);

    //suffixes.clear();

    return suffixes;
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
  private String getSelfSignedCertificateSubjectDN(SecurityOptions sec)
  {
    return "cn="+Rdn.escapeValue(sec.getSelfSignedCertificateName())+
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

  private static int getRandomInt(Random random,int modulo)
  {
    int value = 0;
    value = (random.nextInt() & modulo);
    return value;
  }
}
