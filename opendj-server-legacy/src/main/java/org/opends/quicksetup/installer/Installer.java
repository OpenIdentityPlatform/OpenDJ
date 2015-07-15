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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.quicksetup.installer;

import static org.forgerock.util.Utils.*;
import static org.opends.admin.ads.ServerDescriptor.*;
import static org.opends.admin.ads.ServerDescriptor.ServerProperty.*;
import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.quicksetup.Step.*;
import static org.opends.quicksetup.installer.DataReplicationOptions.Type.*;
import static org.opends.quicksetup.installer.InstallProgressStep.*;
import static org.opends.quicksetup.util.Utils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NamingSecurityException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.Rdn;
import javax.swing.JPanel;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg0;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.TopologyCacheFilter;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.JavaArguments;
import org.opends.quicksetup.LicenseFile;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.QuickSetupLog;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.UserDataConfirmationException;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.WizardStep;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.installer.ui.DataOptionsPanel;
import org.opends.quicksetup.installer.ui.DataReplicationPanel;
import org.opends.quicksetup.installer.ui.GlobalAdministratorPanel;
import org.opends.quicksetup.installer.ui.InstallLicensePanel;
import org.opends.quicksetup.installer.ui.InstallReviewPanel;
import org.opends.quicksetup.installer.ui.InstallWelcomePanel;
import org.opends.quicksetup.installer.ui.RemoteReplicationPortsPanel;
import org.opends.quicksetup.installer.ui.RuntimeOptionsPanel;
import org.opends.quicksetup.installer.ui.ServerSettingsPanel;
import org.opends.quicksetup.installer.ui.SuffixesToReplicatePanel;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.FinishedPanel;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.ProgressPanel;
import org.opends.quicksetup.ui.QuickSetup;
import org.opends.quicksetup.ui.QuickSetupDialog;
import org.opends.quicksetup.ui.QuickSetupErrorPanel;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.FileManager;
import org.opends.quicksetup.util.IncompatibleVersionException;
import org.opends.quicksetup.util.Utils;
import org.opends.server.tools.BackendTypeHelper;
import org.opends.server.tools.BackendTypeHelper.BackendTypeUIAdapter;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.util.OperatingSystem;

/**
 * This is an abstract class that is in charge of actually performing the
 * installation.
 *
 * It just takes a UserData object and based on that installs OpenDJ.
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The
 * notification will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 *
 * Note that we can use freely the class org.opends.server.util.SetupUtils as
 * it is included in quicksetup.jar.
 */
public abstract class Installer extends GuiApplication
{
  /** The minimum integer value that can be used for a port. */
  public static final int MIN_PORT_VALUE = 1;
  /** The maximum integer value that can be used for a port. */
  public static final int MAX_PORT_VALUE = 65535;

  /** The name of the backend created on setup. */
  public static final String ROOT_BACKEND_NAME = "userRoot";

  /** Constants used to do checks. */
  private static final int MIN_DIRECTORY_MANAGER_PWD = 1;

  private static final int MIN_NUMBER_ENTRIES = 1;
  private static final int MAX_NUMBER_ENTRIES = 10000000;

  /**
   * If the user decides to import more than this number of entries, the import
   * process of automatically generated data will be verbose.
   */
  private static final int THRESHOLD_AUTOMATIC_DATA_VERBOSE = 20000;

  /**
   * If the user decides to import a number of entries higher than this
   * threshold, the start process will be verbose.
   */
  private static final int THRESHOLD_VERBOSE_START = 100000;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private TopologyCache lastLoadedCache;

  /** Indicates that we've detected that there is something installed. */
  boolean forceToDisplaySetup;

  /** When true indicates that the user has canceled this operation. */
  protected boolean canceled;

  private boolean javaVersionCheckFailed;

  /** Map containing information about what has been configured remotely. */
  private final Map<ServerDescriptor, ConfiguredReplication> hmConfiguredRemoteReplication = new HashMap<>();

  /** Set of progress steps that have been completed. */
  protected Set<InstallProgressStep> completedProgress = new HashSet<>();

  private final List<WizardStep> lstSteps = new ArrayList<>();

  private final Set<WizardStep> SUBSTEPS = new HashSet<>();
  {
    SUBSTEPS.add(Step.CREATE_GLOBAL_ADMINISTRATOR);
    SUBSTEPS.add(Step.SUFFIXES_OPTIONS);
    SUBSTEPS.add(Step.NEW_SUFFIX_OPTIONS);
    SUBSTEPS.add(Step.REMOTE_REPLICATION_PORTS);
  }

  private final Map<WizardStep, WizardStep> hmPreviousSteps = new HashMap<>();

  private char[] selfSignedCertPw;

  private boolean registeredNewServerOnRemote;
  private boolean createdAdministrator;
  private boolean createdRemoteAds;
  private String lastImportProgress;

  /** A static String that contains the class name of ConfigFileHandler. */
  protected static final String DEFAULT_CONFIG_CLASS_NAME = "org.opends.server.extensions.ConfigFileHandler";

  /** Alias of a self-signed certificate. */
  protected static final String SELF_SIGNED_CERT_ALIAS = SecurityOptions.SELF_SIGNED_CERT_ALIAS;

  /**
   * The threshold in minutes used to know whether we must display a warning
   * informing that there is a server clock difference between two servers whose
   * contents are being replicated.
   */
  public static final int THRESHOLD_CLOCK_DIFFERENCE_WARNING = 5;

  /** Creates a default instance. */
  public Installer()
  {
    addStepsInOrder(lstSteps, LicenseFile.exists());
    try
    {
      if (!QuickSetupLog.isInitialized())
      {
        QuickSetupLog.initLogFileHandler(File.createTempFile(Constants.LOG_FILE_PREFIX, Constants.LOG_FILE_SUFFIX));
      }
    }
    catch (IOException e)
    {
      System.err.println("Failed to initialize log");
    }
  }

  @Override
  public boolean isCancellable()
  {
    return true;
  }

  @Override
  public UserData createUserData()
  {
    UserData ud = new UserData();
    ud.setServerLocation(getDefaultServerLocation());
    initializeUserDataWithUserArguments(ud, getUserArguments());
    return ud;
  }

  private void initializeUserDataWithUserArguments(UserData ud, String[] userArguments)
  {
    for (int i = 0; i < userArguments.length; i++)
    {
      if ("--connectTimeout".equalsIgnoreCase(userArguments[i]))
      {
        if (i < userArguments.length - 1)
        {
          String sTimeout = userArguments[i + 1];
          try
          {
            ud.setConnectTimeout(Integer.valueOf(sTimeout));
          }
          catch (Throwable t)
          {
            logger.warn(LocalizableMessage.raw("Error getting connect timeout: " + t, t));
          }
        }
        break;
      }
    }
  }

  @Override
  public void forceToDisplay()
  {
    forceToDisplaySetup = true;
  }

  @Override
  public boolean canGoBack(WizardStep step)
  {
    return step != WELCOME && step != PROGRESS && step != FINISHED;
  }

  @Override
  public boolean canGoForward(WizardStep step)
  {
    return step != REVIEW && step != PROGRESS && step != FINISHED;
  }

  @Override
  public boolean canFinish(WizardStep step)
  {
    return step == REVIEW;
  }

    @Override
  public boolean isSubStep(WizardStep step)
  {
    return SUBSTEPS.contains(step);
  }

  @Override
  public boolean isVisible(WizardStep step, UserData userData)
  {
    if (step == CREATE_GLOBAL_ADMINISTRATOR)
    {
      return userData.mustCreateAdministrator();
    }
    else if (step == NEW_SUFFIX_OPTIONS)
    {
      SuffixesToReplicateOptions suf = userData.getSuffixesToReplicateOptions();
      return suf != null && suf.getType() != SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES;
    }
    else if (step == SUFFIXES_OPTIONS)
    {
      DataReplicationOptions repl = userData.getReplicationOptions();
      return repl != null && repl.getType() != DataReplicationOptions.Type.STANDALONE
          && repl.getType() != DataReplicationOptions.Type.FIRST_IN_TOPOLOGY;
    }
    else if (step == REMOTE_REPLICATION_PORTS)
    {
      return isVisible(SUFFIXES_OPTIONS, userData)
          && !userData.getRemoteWithNoReplicationPort().isEmpty()
          && userData.getSuffixesToReplicateOptions().getType() ==
              SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES;
    }
    return true;
  }

  @Override
  public boolean isVisible(WizardStep step, QuickSetup qs)
  {
    return isVisible(step, getUserData());
  }

  @Override
  public boolean finishClicked(final WizardStep cStep, final QuickSetup qs)
  {
    if (cStep != Step.REVIEW)
    {
      throw new IllegalStateException("Cannot click on finish when we are not in the Review window");
    }

    updateUserDataForReviewPanel(qs);
    qs.launch();
    qs.setCurrentStep(Step.PROGRESS);
    // Installer responsible for updating the user data and launching
    return false;
  }

    @Override
  public void nextClicked(WizardStep cStep, QuickSetup qs)
  {
    if (cStep == PROGRESS)
    {
      throw new IllegalStateException("Cannot click on next from progress step");
    }
    else if (cStep == REVIEW)
    {
      throw new IllegalStateException("Cannot click on next from review step");
    }
    else if (cStep == FINISHED)
    {
      throw new IllegalStateException("Cannot click on next from finished step");
    }
  }

  @Override
  public void closeClicked(WizardStep cStep, QuickSetup qs)
  {
    if (cStep == PROGRESS)
    {
      if (isFinished()
          || qs.displayConfirmation(INFO_CONFIRM_CLOSE_INSTALL_MSG.get(), INFO_CONFIRM_CLOSE_INSTALL_TITLE.get()))
      {
        qs.quit();
      }
    }
    else if (cStep == FINISHED)
    {
      qs.quit();
    }
    else
    {
      throw new IllegalStateException("Close only can be clicked on PROGRESS step");
    }
  }

  @Override
  public boolean isFinished()
  {
    return getCurrentProgressStep() == InstallProgressStep.FINISHED_SUCCESSFULLY
        || getCurrentProgressStep() == InstallProgressStep.FINISHED_CANCELED
        || getCurrentProgressStep() == InstallProgressStep.FINISHED_WITH_ERROR;
  }

  @Override
  public void cancel()
  {
    setCurrentProgressStep(InstallProgressStep.WAITING_TO_CANCEL);
    notifyListeners(null);
    this.canceled = true;
  }

  @Override
  public void quitClicked(WizardStep cStep, QuickSetup qs)
  {
    if (cStep == FINISHED)
    {
      qs.quit();
    }
    else if (cStep == PROGRESS)
    {
      throw new IllegalStateException("Cannot click on quit from progress step");
    }
    else if (installStatus.isInstalled())
    {
      qs.quit();
    }
    else if (javaVersionCheckFailed)
    {
      qs.quit();
    }
    else if (qs.displayConfirmation(INFO_CONFIRM_QUIT_INSTALL_MSG.get(), INFO_CONFIRM_QUIT_INSTALL_TITLE.get()))
    {
      qs.quit();
    }
  }

  @Override
  public ButtonName getInitialFocusButtonName()
  {
    if (!installStatus.isInstalled() || forceToDisplaySetup)
    {
      return ButtonName.NEXT;
    }
    else if (installStatus.canOverwriteCurrentInstall())
    {
      return ButtonName.CONTINUE_INSTALL;
    }
    else
    {
      return ButtonName.QUIT;
    }
  }

  @Override
  public JPanel createFramePanel(QuickSetupDialog dlg)
  {
    JPanel p;
    javaVersionCheckFailed = true;
    try
    {
      Utils.checkJavaVersion();
      javaVersionCheckFailed = false;
      if (installStatus.isInstalled() && !forceToDisplaySetup)
      {
        p = dlg.getInstalledPanel();
      }
      else
      {
        p = super.createFramePanel(dlg);
      }
    }
    catch (IncompatibleVersionException ijv)
    {
      LocalizableMessageBuilder sb = new LocalizableMessageBuilder();
      sb.append(Utils.breakHtmlString(Utils.getHtml(ijv.getMessageObject().toString()),
          Constants.MAX_CHARS_PER_LINE_IN_DIALOG));
      QuickSetupErrorPanel errPanel = new QuickSetupErrorPanel(this, sb.toMessage());
      final QuickSetupDialog fDlg = dlg;
      errPanel.addButtonActionListener(new ButtonActionListener()
      {
        /**
         * ButtonActionListener implementation. It assumes that we are called in
         * the event thread.
         *
         * @param ev
         *          the ButtonEvent we receive.
         */
        @Override
        public void buttonActionPerformed(ButtonEvent ev)
        {
          // Simulate a close button event
          fDlg.notifyButtonEvent(ButtonName.QUIT);
        }
      });
      p = errPanel;
    }
    return p;
  }

  @Override
  public Set<? extends WizardStep> getWizardSteps()
  {
    return Collections.unmodifiableSet(new HashSet<WizardStep>(lstSteps));
  }

  @Override
  public QuickSetupStepPanel createWizardStepPanel(WizardStep step)
  {
    if (step instanceof Step)
    {
      switch ((Step) step)
      {
      case WELCOME:
        return new InstallWelcomePanel(this);
      case LICENSE:
        return new InstallLicensePanel(this);
      case SERVER_SETTINGS:
        return new ServerSettingsPanel(this);
      case REPLICATION_OPTIONS:
        return new DataReplicationPanel(this);
      case CREATE_GLOBAL_ADMINISTRATOR:
        return new GlobalAdministratorPanel(this);
      case SUFFIXES_OPTIONS:
        return new SuffixesToReplicatePanel(this);
      case REMOTE_REPLICATION_PORTS:
        return new RemoteReplicationPortsPanel(this);
      case NEW_SUFFIX_OPTIONS:
        return new DataOptionsPanel(this);
      case RUNTIME_OPTIONS:
        return new RuntimeOptionsPanel(this);
      case REVIEW:
        return new InstallReviewPanel(this);
      case PROGRESS:
        return new ProgressPanel(this);
      case FINISHED:
        return new FinishedPanel(this);
      }
    }
    return null;
  }

  @Override
  public void windowClosing(QuickSetupDialog dlg, WindowEvent evt)
  {
    if (installStatus.isInstalled() && forceToDisplaySetup)
    {
      // Simulate a close button event
      dlg.notifyButtonEvent(ButtonName.QUIT);
    }
    else if (dlg.getDisplayedStep() == Step.PROGRESS)
    {
      // Simulate a close button event
      dlg.notifyButtonEvent(ButtonName.CLOSE);
    }
    else
    {
      // Simulate a quit button event
      dlg.notifyButtonEvent(ButtonName.QUIT);
    }
  }

  @Override
  public LocalizableMessage getCloseButtonToolTip()
  {
    return INFO_CLOSE_BUTTON_INSTALL_TOOLTIP.get();
  }

  @Override
  public LocalizableMessage getQuitButtonToolTip()
  {
    return INFO_QUIT_BUTTON_INSTALL_TOOLTIP.get();
  }

  @Override
  public LocalizableMessage getFinishButtonToolTip()
  {
    return INFO_FINISH_BUTTON_INSTALL_TOOLTIP.get();
  }

  @Override
  public int getExtraDialogHeight()
  {
    return UIFactory.EXTRA_DIALOG_HEIGHT;
  }

  @Override
  public void previousClicked(WizardStep cStep, QuickSetup qs)
  {
    if (cStep == WELCOME)
    {
      throw new IllegalStateException("Cannot click on previous from progress step");
    }
    else if (cStep == PROGRESS)
    {
      throw new IllegalStateException("Cannot click on previous from progress step");
    }
    else if (cStep == FINISHED)
    {
      throw new IllegalStateException("Cannot click on previous from finished step");
    }
  }

  @Override
  public LocalizableMessage getFrameTitle()
  {
    return Utils.getCustomizedObject("INFO_FRAME_INSTALL_TITLE", INFO_FRAME_INSTALL_TITLE
        .get(DynamicConstants.PRODUCT_NAME), LocalizableMessage.class);
  }

  /** Indicates the current progress step. */
  private InstallProgressStep currentProgressStep = InstallProgressStep.NOT_STARTED;

  @Override
  public void setWizardDialogState(QuickSetupDialog dlg, UserData userData, WizardStep step)
  {
    if (!installStatus.isInstalled() || forceToDisplaySetup)
    {
      // Set the default button for the frame
      if (step == REVIEW)
      {
        dlg.setFocusOnButton(ButtonName.FINISH);
        dlg.setDefaultButton(ButtonName.FINISH);
      }
      else if (step == WELCOME)
      {
        dlg.setDefaultButton(ButtonName.NEXT);
        dlg.setFocusOnButton(ButtonName.NEXT);
      }
      else if (step == PROGRESS || step == FINISHED)
      {
        dlg.setDefaultButton(ButtonName.CLOSE);
        dlg.setFocusOnButton(ButtonName.CLOSE);
      }
      else
      {
        dlg.setDefaultButton(ButtonName.NEXT);
      }
    }
  }

  @Override
  public ProgressStep getCurrentProgressStep()
  {
    return currentProgressStep;
  }

  @Override
  public WizardStep getFirstWizardStep()
  {
    return WELCOME;
  }

  @Override
  public WizardStep getNextWizardStep(WizardStep step)
  {
    WizardStep next = getNextWizardStep0(step);
    if (next != null)
    {
      hmPreviousSteps.put(next, step);
    }
    return next;
  }

  private WizardStep getNextWizardStep0(WizardStep step)
  {
    if (step == Step.REPLICATION_OPTIONS)
    {
      if (getUserData().mustCreateAdministrator())
      {
        return Step.CREATE_GLOBAL_ADMINISTRATOR;
      }

      switch (getUserData().getReplicationOptions().getType())
      {
      case FIRST_IN_TOPOLOGY:
      case STANDALONE:
        return Step.NEW_SUFFIX_OPTIONS;
      default:
        return Step.SUFFIXES_OPTIONS;
      }
    }
    else if (step == Step.SUFFIXES_OPTIONS)
    {
      switch (getUserData().getSuffixesToReplicateOptions().getType())
      {
      case REPLICATE_WITH_EXISTING_SUFFIXES:
        if (!getUserData().getRemoteWithNoReplicationPort().isEmpty())
        {
          return Step.REMOTE_REPLICATION_PORTS;
        }
        return Step.RUNTIME_OPTIONS;
      default:
        return Step.NEW_SUFFIX_OPTIONS;
      }
    }
    else if (step == Step.REMOTE_REPLICATION_PORTS)
    {
      return Step.RUNTIME_OPTIONS;
    }
    else
    {
      int i = lstSteps.indexOf(step);
      if (i != -1 && i + 1 < lstSteps.size())
      {
        return lstSteps.get(i + 1);
      }
    }
    return null;
  }

  @Override
  public LinkedHashSet<WizardStep> getOrderedSteps()
  {
    LinkedHashSet<WizardStep> orderedSteps = new LinkedHashSet<>();
    addStepsInOrder(orderedSteps, lstSteps.contains(LICENSE));
    return orderedSteps;
  }

  private void addStepsInOrder(Collection<WizardStep> steps, boolean licenseExists)
  {
    steps.add(WELCOME);
    if (licenseExists)
    {
      steps.add(LICENSE);
    }
    steps.add(SERVER_SETTINGS);
    steps.add(REPLICATION_OPTIONS);
    steps.add(CREATE_GLOBAL_ADMINISTRATOR);
    steps.add(SUFFIXES_OPTIONS);
    steps.add(REMOTE_REPLICATION_PORTS);
    steps.add(NEW_SUFFIX_OPTIONS);
    steps.add(RUNTIME_OPTIONS);
    steps.add(REVIEW);
    steps.add(PROGRESS);
    steps.add(FINISHED);
  }

  @Override
  public WizardStep getPreviousWizardStep(WizardStep step)
  {
    //  Try with the steps calculated in method getNextWizardStep.
    WizardStep prev = hmPreviousSteps.get(step);

    if (prev == null)
    {
      int i = lstSteps.indexOf(step);
      if (i != -1 && i > 0)
      {
        prev = lstSteps.get(i - 1);
      }
    }
    return prev;
  }

  @Override
  public WizardStep getFinishedStep()
  {
    return Step.FINISHED;
  }

  /**
   * Uninstalls installed services. This is to be used when the user has elected
   * to cancel an installation.
   */
  protected void uninstallServices()
  {
    if (completedProgress.contains(InstallProgressStep.ENABLING_WINDOWS_SERVICE))
    {
      try
      {
        new InstallerHelper().disableWindowsService();
      }
      catch (ApplicationException ae)
      {
        logger.info(LocalizableMessage.raw("Error disabling Windows service", ae));
      }
    }

    unconfigureRemote();
  }

  /**
   * Creates the template files based in the contents of the UserData object.
   * These templates files are used to generate automatically data. To generate
   * the template file the code will basically take into account the value of
   * the base dn and the number of entries to be generated.
   *
   * @return a list of file objects pointing to the create template files.
   * @throws ApplicationException
   *           if an error occurs.
   */
  private File createTemplateFile() throws ApplicationException
  {
    try
    {
      Set<String> baseDNs = new LinkedHashSet<>(getUserData().getNewSuffixOptions().getBaseDns());
      int nEntries = getUserData().getNewSuffixOptions().getNumberEntries();
      return SetupUtils.createTemplateFile(baseDNs, nEntries);
    }
    catch (IOException ioe)
    {
      LocalizableMessage failedMsg = getThrowableMsg(INFO_ERROR_CREATING_TEMP_FILE.get(), ioe);
      throw new ApplicationException(ReturnCode.FILE_SYSTEM_ACCESS_ERROR, failedMsg, ioe);
    }
  }

  /**
   * This methods configures the server based on the contents of the UserData
   * object provided in the constructor.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  protected void configureServer() throws ApplicationException
  {
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CONFIGURING.get()));

    if (Utils.isWebStart())
    {
      String installDir = getUserData().getServerLocation();
      setInstallation(new Installation(installDir, installDir));
    }

    copyTemplateInstance();
    writeOpenDSJavaHome();
    writeHostName();
    checkAbort();

    List<String> argList = new ArrayList<>();
    argList.add("-C");
    argList.add(getConfigurationClassName());

    argList.add("-c");
    argList.add(getConfigurationFile());
    argList.add("-h");
    argList.add(getUserData().getHostName());
    argList.add("-p");
    argList.add(String.valueOf(getUserData().getServerPort()));
    argList.add("--adminConnectorPort");
    argList.add(String.valueOf(getUserData().getAdminConnectorPort()));

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

    addCertificateArguments(sec, argList);
    // For the moment do not enable JMX
    if (getUserData().getServerJMXPort() > 0)
    {
      argList.add("-x");
      argList.add(String.valueOf(getUserData().getServerJMXPort()));
    }

    argList.add("-D");
    argList.add(getUserData().getDirectoryManagerDn());

    argList.add("-w");
    argList.add(getUserData().getDirectoryManagerPwd());

    final ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType =
        getUserData().getBackendType();
    if (backendType != null)
    {
      argList.add("--" + OPTION_LONG_BACKEND_TYPE);
      argList.add(BackendTypeHelper.filterSchemaBackendName(backendType.getName()));
    }

    if (createNotReplicatedSuffix())
    {
      for (String baseDn : getUserData().getNewSuffixOptions().getBaseDns())
      {
        argList.add("-b");
        argList.add(baseDn);
      }
    }

    argList.add("-R");
    argList.add(getInstallation().getRootDirectory().getAbsolutePath());

    final String[] args = new String[argList.size()];
    argList.toArray(args);
    StringBuilder cmd = new StringBuilder();
    boolean nextPassword = false;
    for (String s : argList)
    {
      if (cmd.length() > 0)
      {
        cmd.append(" ");
      }
      if (nextPassword)
      {
        cmd.append("{rootUserPassword}");
      }
      else
      {
        cmd.append(s);
      }
      nextPassword = "-w".equals(s);
    }
    logger.info(LocalizableMessage.raw("configure DS cmd: " + cmd));
    final InstallerHelper helper = new InstallerHelper();
    setNotifyListeners(false);
    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          if (helper.invokeConfigureServer(args) != 0)
          {
            ae = new ApplicationException(ReturnCode.CONFIGURATION_ERROR, INFO_ERROR_CONFIGURING.get(), null);
          }
          else if (getUserData().getNewSuffixOptions().getBaseDns().isEmpty())
          {
            helper.deleteBackend(ROOT_BACKEND_NAME);
          }
        }
        catch (ApplicationException aex)
        {
          ae = aex;
        }
        catch (Throwable t)
        {
          ae = new ApplicationException(
              ReturnCode.CONFIGURATION_ERROR, getThrowableMsg(INFO_ERROR_CONFIGURING.get(), t), t);
        }
        finally
        {
          setNotifyListeners(true);
        }
        isOver = true;
      }

      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    invokeLongOperation(thread);
    notifyListeners(getFormattedDoneWithLineBreak());
    checkAbort();
    configureCertificate(sec);
  }

  private void configureCertificate(SecurityOptions sec) throws ApplicationException
  {
    try
    {
      SecurityOptions.CertificateType certType = sec.getCertificateType();
      if (certType != SecurityOptions.CertificateType.NO_CERTIFICATE)
      {
        notifyListeners(getFormattedWithPoints(INFO_PROGRESS_UPDATING_CERTIFICATES.get()));
      }

      switch (certType)
      {
      case NO_CERTIFICATE:
        // Nothing to do
        break;
      case SELF_SIGNED_CERTIFICATE:
        String pwd = getSelfSignedCertificatePwd();
        final CertificateManager certManager =
            new CertificateManager(getSelfSignedKeystorePath(), CertificateManager.KEY_STORE_TYPE_JKS, pwd);
        certManager.generateSelfSignedCertificate(SELF_SIGNED_CERT_ALIAS, getSelfSignedCertificateSubjectDN(),
            getSelfSignedCertificateValidity());
        SetupUtils.exportCertificate(certManager, SELF_SIGNED_CERT_ALIAS, getTemporaryCertificatePath());
        configureTrustStore(CertificateManager.KEY_STORE_TYPE_JKS, SELF_SIGNED_CERT_ALIAS, pwd);
        break;

      case JKS:
        configureKeyAndTrustStore(sec.getKeystorePath(), CertificateManager.KEY_STORE_TYPE_JKS,
            CertificateManager.KEY_STORE_TYPE_JKS, sec);
        break;

      case JCEKS:
        configureKeyAndTrustStore(sec.getKeystorePath(), CertificateManager.KEY_STORE_TYPE_JCEKS,
            CertificateManager.KEY_STORE_TYPE_JCEKS, sec);
        break;

      case PKCS12:
        configureKeyAndTrustStore(sec.getKeystorePath(), CertificateManager.KEY_STORE_TYPE_PKCS12,
            CertificateManager.KEY_STORE_TYPE_JKS, sec);
        break;

      case PKCS11:
        configureKeyAndTrustStore(CertificateManager.KEY_STORE_PATH_PKCS11, CertificateManager.KEY_STORE_TYPE_PKCS11,
            CertificateManager.KEY_STORE_TYPE_JKS, sec);
        break;

      default:
        throw new IllegalStateException("Unknown certificate type: " + certType);
      }

      if (certType != SecurityOptions.CertificateType.NO_CERTIFICATE)
      {
        notifyListeners(getFormattedDoneWithLineBreak());
      }
    }
    catch (Throwable t)
    {
      logger.error(LocalizableMessage.raw("Error configuring certificate: " + t, t));
      throw new ApplicationException(
          ReturnCode.CONFIGURATION_ERROR, getThrowableMsg(INFO_ERROR_CONFIGURING_CERTIFICATE.get(), t), t);
    }
  }

  private void configureKeyAndTrustStore(final String keyStorePath, final String keyStoreType,
      final String trustStoreType, final SecurityOptions sec) throws Exception
  {
    final String keystorePassword = sec.getKeystorePassword();
    final String keyStoreAlias = sec.getAliasToUse();

    CertificateManager certManager = new CertificateManager(keyStorePath, keyStoreType, keystorePassword);
    SetupUtils.exportCertificate(certManager, keyStoreAlias, getTemporaryCertificatePath());
    configureTrustStore(trustStoreType, keyStoreAlias, keystorePassword);
  }

  private void configureTrustStore(final String type, final String keyStoreAlias, final String password)
      throws Exception
  {
    final String alias = keyStoreAlias != null ? keyStoreAlias : SELF_SIGNED_CERT_ALIAS;
    final CertificateManager trustMgr = new CertificateManager(getTrustManagerPath(), type, password);
    trustMgr.addCertificate(alias, new File(getTemporaryCertificatePath()));

    createProtectedFile(getKeystorePinPath(), password);
    final File f = new File(getTemporaryCertificatePath());
    f.delete();
  }

  private void addCertificateArguments(SecurityOptions sec, List<String> argList)
  {
    final String aliasInKeyStore = sec.getAliasToUse();

    switch (sec.getCertificateType())
    {
    case SELF_SIGNED_CERTIFICATE:
      argList.add("-k");
      argList.add("cn=JKS,cn=Key Manager Providers,cn=config");
      argList.add("-t");
      argList.add("cn=JKS,cn=Trust Manager Providers,cn=config");
      break;
    case JKS:
      addCertificateArguments(argList, sec, aliasInKeyStore, "cn=JKS,cn=Key Manager Providers,cn=config",
          "cn=JKS,cn=Trust Manager Providers,cn=config");
      break;
    case JCEKS:
      addCertificateArguments(argList, sec, aliasInKeyStore, "cn=JCEKS,cn=Key Manager Providers,cn=config",
          "cn=JCEKS,cn=Trust Manager Providers,cn=config");
      break;
    case PKCS12:
      addCertificateArguments(argList, sec, aliasInKeyStore, "cn=PKCS12,cn=Key Manager Providers,cn=config",
          "cn=JKS,cn=Trust Manager Providers,cn=config");
      break;
    case PKCS11:
      addCertificateArguments(argList, null, aliasInKeyStore, "cn=PKCS11,cn=Key Manager Providers,cn=config",
          "cn=JKS,cn=Trust Manager Providers,cn=config");
      break;
    case NO_CERTIFICATE:
      // Nothing to do.
      break;
    default:
      throw new IllegalStateException("Unknown certificate type: " + sec.getCertificateType());
    }
  }

  private void addCertificateArguments(List<String> argList, SecurityOptions sec, String aliasInKeyStore,
      String keyStoreDN, String trustStoreDN)
  {
    argList.add("-k");
    argList.add(keyStoreDN);
    argList.add("-t");
    argList.add(trustStoreDN);
    if (sec != null)
    {
      argList.add("-m");
      argList.add(sec.getKeystorePath());
    }
    if (aliasInKeyStore != null)
    {
      argList.add("-a");
      argList.add(aliasInKeyStore);
    }
  }

  /**
   * This methods creates the base entry for the suffix based on the contents of
   * the UserData object provided in the constructor.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  private void createBaseEntry() throws ApplicationException
  {
    LinkedList<String> baseDns = getUserData().getNewSuffixOptions().getBaseDns();
    if (baseDns.size() == 1)
    {
      notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CREATING_BASE_ENTRY.get(baseDns.getFirst())));
    }
    else
    {
      notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CREATING_BASE_ENTRIES.get()));
    }

    final InstallerHelper helper = new InstallerHelper();

    LinkedList<File> ldifFiles = new LinkedList<>();

    for (String baseDn : baseDns)
    {
      ldifFiles.add(helper.createBaseEntryTempFile(baseDn));
    }
    checkAbort();

    List<String> argList = new ArrayList<>();
    argList.add("-n");
    argList.add(ROOT_BACKEND_NAME);
    for (File f : ldifFiles)
    {
      argList.add("-l");
      argList.add(f.getAbsolutePath());
    }
    argList.add("-F");
    argList.add("-Q");
    argList.add("--noPropertiesFile");

    final String[] args = new String[argList.size()];
    argList.toArray(args);

    setNotifyListeners(false);

    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          int result = helper.invokeImportLDIF(Installer.this, args);

          if (result != 0)
          {
            ae = new ApplicationException(ReturnCode.IMPORT_ERROR, INFO_ERROR_CREATING_BASE_ENTRY.get(), null);
          }
        }
        catch (Throwable t)
        {
          ae =
              new ApplicationException(ReturnCode.IMPORT_ERROR,
                  getThrowableMsg(INFO_ERROR_CREATING_BASE_ENTRY.get(), t), t);
        }
        finally
        {
          setNotifyListeners(true);
        }
        isOver = true;
      }

      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    invokeLongOperation(thread);
    notifyListeners(getFormattedDoneWithLineBreak());
  }

  /**
   * This methods imports the contents of an LDIF file based on the contents of
   * the UserData object provided in the constructor.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  private void importLDIF() throws ApplicationException
  {
    LinkedList<String> ldifPaths = getUserData().getNewSuffixOptions().getLDIFPaths();
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    if (ldifPaths.size() > 1)
    {
      if (isVerbose())
      {
        mb.append(getFormattedProgress(INFO_PROGRESS_IMPORTING_LDIFS.get(joinAsString(", ", ldifPaths))));
        mb.append(getLineBreak());
      }
      else
      {
        mb.append(getFormattedProgress(INFO_PROGRESS_IMPORTING_LDIFS_NON_VERBOSE.get(joinAsString(", ", ldifPaths))));
      }
    }
    else if (isVerbose())
    {
      mb.append(getFormattedProgress(INFO_PROGRESS_IMPORTING_LDIF.get(ldifPaths.getFirst())));
      mb.append(getLineBreak());
    }
    else
    {
      mb.append(getFormattedProgress(INFO_PROGRESS_IMPORTING_LDIF_NON_VERBOSE.get(ldifPaths.getFirst())));
    }
    notifyListeners(mb.toMessage());

    final PointAdder pointAdder = new PointAdder();

    if (!isVerbose())
    {
      setNotifyListeners(false);
      pointAdder.start();
    }

    List<String> argList = new ArrayList<>();
    argList.add("-n");
    argList.add(ROOT_BACKEND_NAME);
    for (String ldifPath : ldifPaths)
    {
      argList.add("-l");
      argList.add(ldifPath);
    }
    argList.add("-F");
    String rejectedFile = getUserData().getNewSuffixOptions().getRejectedFile();
    if (rejectedFile != null)
    {
      argList.add("-R");
      argList.add(rejectedFile);
    }
    String skippedFile = getUserData().getNewSuffixOptions().getSkippedFile();
    if (skippedFile != null)
    {
      argList.add("--skipFile");
      argList.add(skippedFile);
    }

    argList.add("--noPropertiesFile");

    final String[] args = new String[argList.size()];
    argList.toArray(args);

    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          InstallerHelper helper = new InstallerHelper();
          int result = helper.invokeImportLDIF(Installer.this, args);

          if (result != 0)
          {
            ae = new ApplicationException(ReturnCode.IMPORT_ERROR, INFO_ERROR_IMPORTING_LDIF.get(), null);
          }
        }
        catch (Throwable t)
        {
          ae = new ApplicationException(
              ReturnCode.IMPORT_ERROR, getThrowableMsg(INFO_ERROR_IMPORTING_LDIF.get(), t), t);
        }
        finally
        {
          if (!isVerbose())
          {
            setNotifyListeners(true);
            pointAdder.stop();
          }
        }
        isOver = true;
      }

      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    try
    {
      invokeLongOperation(thread);
    }
    catch (ApplicationException ae)
    {
      if (!isVerbose() && lastImportProgress != null)
      {
        notifyListeners(getFormattedProgress(LocalizableMessage.raw(lastImportProgress)));
        notifyListeners(getLineBreak());
      }
      throw ae;
    }
    if (!isVerbose())
    {
      if (lastImportProgress == null)
      {
        notifyListeners(getFormattedDoneWithLineBreak());
      }
      else
      {
        notifyListeners(getFormattedProgress(LocalizableMessage.raw(lastImportProgress)));
        notifyListeners(getLineBreak());
      }
    }
  }

  /**
   * This methods imports automatically generated data based on the contents of
   * the UserData object provided in the constructor.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  private void importAutomaticallyGenerated() throws ApplicationException
  {
    File templatePath = createTemplateFile();
    int nEntries = getUserData().getNewSuffixOptions().getNumberEntries();
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    if (isVerbose() || nEntries > THRESHOLD_AUTOMATIC_DATA_VERBOSE)
    {
      mb.append(getFormattedProgress(INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED.get(nEntries)));
      mb.append(getLineBreak());
    }
    else
    {
      mb.append(getFormattedProgress(INFO_PROGRESS_IMPORT_AUTOMATICALLY_GENERATED_NON_VERBOSE.get(nEntries)));
    }
    notifyListeners(mb.toMessage());

    final PointAdder pointAdder = new PointAdder();
    if (!isVerbose())
    {
      pointAdder.start();
    }

    if (!isVerbose())
    {
      setNotifyListeners(false);
    }
    final List<String> argList = new ArrayList<>();
    argList.add("-n");
    argList.add(ROOT_BACKEND_NAME);
    argList.add("-A");
    argList.add(templatePath.getAbsolutePath());
    argList.add("-s"); // seed
    argList.add("0");
    argList.add("-F");
    argList.add("--noPropertiesFile");

    final String[] args = new String[argList.size()];
    argList.toArray(args);

    InvokeThread thread = new InvokeThread()
    {
      @Override
      public void run()
      {
        try
        {
          InstallerHelper helper = new InstallerHelper();
          int result = helper.invokeImportLDIF(Installer.this, args);

          if (result != 0)
          {
            ae = new ApplicationException(
                ReturnCode.IMPORT_ERROR, INFO_ERROR_IMPORT_LDIF_TOOL_RETURN_CODE.get(result), null);
          }
        }
        catch (Throwable t)
        {
          ae = new ApplicationException(ReturnCode.IMPORT_ERROR, getThrowableMsg(
                      INFO_ERROR_IMPORT_AUTOMATICALLY_GENERATED.get(joinAsString(" ", argList),
                      t.getLocalizedMessage()), t), t);
        }
        finally
        {
          if (!isVerbose())
          {
            setNotifyListeners(true);
            if (ae != null)
            {
              pointAdder.stop();
            }
          }
        }
        isOver = true;
      }

      @Override
      public void abort()
      {
        // TODO: implement the abort
      }
    };
    invokeLongOperation(thread);
    if (!isVerbose())
    {
      pointAdder.stop();
      notifyListeners(getFormattedDoneWithLineBreak());
    }
  }

  /**
   * This method undoes the modifications made in other servers in terms of
   * replication. This method assumes that we are aborting the Installer and
   * that is why it does not call checkAbort.
   */
  private void unconfigureRemote()
  {
    InitialLdapContext ctx = null;
    if (registeredNewServerOnRemote || createdAdministrator || createdRemoteAds)
    {
      // Try to connect
      DataReplicationOptions repl = getUserData().getReplicationOptions();
      AuthenticationData auth = repl.getAuthenticationData();
      if (isVerbose())
      {
        notifyListeners(getFormattedWithPoints(INFO_PROGRESS_UNCONFIGURING_ADS_ON_REMOTE.get(getHostDisplay(auth))));
      }
      try
      {
        ctx = createInitialLdapContext(auth);

        ADSContext adsContext = new ADSContext(ctx);
        if (createdRemoteAds)
        {
          adsContext.removeAdminData(true);
        }
        else
        {
          if (registeredNewServerOnRemote)
          {
            try
            {
              adsContext.unregisterServer(getNewServerAdsProperties(getUserData()));
            }
            catch (ADSContextException ace)
            {
              if (ace.getError() != ADSContextException.ErrorType.NOT_YET_REGISTERED)
              {
                throw ace;
              }
              // Else, nothing to do: this may occur if the new server has been
              // unregistered on another server and the modification has been
              // already propagated by replication.
            }
          }
          if (createdAdministrator)
          {
            adsContext.deleteAdministrator(getAdministratorProperties(getUserData()));
          }
        }
        if (isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
      }
      catch (Throwable t)
      {
        notifyListeners(getFormattedError(t, true));
      }
      finally
      {
        StaticUtils.close(ctx);
      }
    }
    InstallerHelper helper = new InstallerHelper();
    for (ServerDescriptor server : hmConfiguredRemoteReplication.keySet())
    {
      notifyListeners(getFormattedWithPoints(INFO_PROGRESS_UNCONFIGURING_REPLICATION_REMOTE.get(getHostPort(server))));
      try
      {
        ctx = getRemoteConnection(server, getTrustManager(), getPreferredConnections());
        helper.unconfigureReplication(ctx, hmConfiguredRemoteReplication.get(server), ConnectionUtils.getHostPort(ctx));
      }
      catch (ApplicationException ae)
      {
        notifyListeners(getFormattedError(ae, true));
      }
      finally
      {
        StaticUtils.close(ctx);
      }
      notifyListeners(getFormattedDoneWithLineBreak());
    }
  }

  /**
   * This method configures the backends and suffixes that must be replicated.
   * The setup uses the same backend names as in the remote servers. If userRoot
   * is not one of the backends defined in the remote servers, it deletes it
   * from the configuration. NOTE: this method assumes that the server is
   * running.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  protected void createReplicatedBackendsIfRequired() throws ApplicationException
  {
    if (FIRST_IN_TOPOLOGY == getUserData().getReplicationOptions().getType())
    {
      return;
    }
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CREATING_REPLICATED_BACKENDS.get()));

    // The keys are the backend IDs and the values the list of base DNs.
    final Map<String, Set<String>> hmBackendSuffix = new HashMap<>();
    final SuffixesToReplicateOptions suffixData = getUserData().getSuffixesToReplicateOptions();
    populateBackendsToCreate(hmBackendSuffix, suffixData.getSuffixes());
    createReplicatedBackends(hmBackendSuffix, suffixData.getSuffixBackendTypes());
    notifyListeners(getFormattedDoneWithLineBreak());
    checkAbort();
  }

  /**
   * The criteria to choose the name of the backend is to try to have the
   * configuration of the other server. The algorithm consists on putting the
   * remote servers in a list and pick the backend as they appear on the list.
   */
  private void populateBackendsToCreate(Map<String, Set<String>> hmBackendSuffix, Set<SuffixDescriptor> suffixes)
  {
    Set<ServerDescriptor> serverList = getServerListFromSuffixes(suffixes);
    for (SuffixDescriptor suffix : suffixes)
    {
      final ReplicaDescriptor replica = retrieveReplicaForSuffix(serverList, suffix);
      if (replica != null)
      {
        final String backendNameKey = getOrAddBackend(hmBackendSuffix, replica.getBackendName());
        hmBackendSuffix.get(backendNameKey).add(suffix.getDN());
      }
    }
  }

  private Set<ServerDescriptor> getServerListFromSuffixes(Set<SuffixDescriptor> suffixes)
  {
    Set<ServerDescriptor> serverList = new LinkedHashSet<>();
    for (SuffixDescriptor suffix : suffixes)
    {
      for (ReplicaDescriptor replica : suffix.getReplicas())
      {
        serverList.add(replica.getServer());
      }
    }
    return serverList;
  }

  private ReplicaDescriptor retrieveReplicaForSuffix(Set<ServerDescriptor> serverList, SuffixDescriptor suffix)
  {
    for (ServerDescriptor server : serverList)
    {
      for (ReplicaDescriptor replica : suffix.getReplicas())
      {
        if (replica.getServer() == server)
        {
          return replica;
        }
      }
    }
    return null;
  }

  private String getOrAddBackend(Map<String, Set<String>> hmBackendSuffix, String backendName)
  {
    for (String storedBackend : hmBackendSuffix.keySet())
    {
      if (storedBackend.equalsIgnoreCase(backendName))
      {
        return storedBackend;
      }
    }
    hmBackendSuffix.put(backendName, new HashSet<String>());
    return backendName;
  }

  private void createReplicatedBackends(final Map<String, Set<String>> hmBackendSuffix,
      final Map<String, BackendTypeUIAdapter> backendTypes) throws ApplicationException
  {
    InitialLdapContext ctx = null;
    try
    {
      ctx = createLocalContext();
      final InstallerHelper helper = new InstallerHelper();
      for (String backendName : hmBackendSuffix.keySet())
      {
        helper.createBackend(ctx, backendName, hmBackendSuffix.get(backendName), ConnectionUtils.getHostPort(ctx),
            backendTypes.get(backendName).getLegacyConfigurationFrameworkBackend());
      }
    }
    catch (NamingException ne)
    {
      LocalizableMessage failedMsg = getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne);
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, failedMsg, ne);
    }
    finally
    {
      StaticUtils.close(ctx);
    }
  }

  /**
   * This method creates the replication configuration for the suffixes on the
   * the local server (and eventually in the remote servers) to synchronize
   * things. NOTE: this method assumes that the server is running.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  protected void configureReplication() throws ApplicationException
  {
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CONFIGURING_REPLICATION.get()));

    InstallerHelper helper = new InstallerHelper();
    Set<Integer> knownServerIds = new HashSet<>();
    Set<Integer> knownReplicationServerIds = new HashSet<>();
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
        Object v = server.getServerProperties().get(REPLICATION_SERVER_ID);
        if (v != null)
        {
          knownReplicationServerIds.add((Integer) v);
        }
      }
    }
    else
    {
      /* There is no ADS anywhere. Just use the SuffixDescriptors we found */
      for (SuffixDescriptor suffix : getUserData().getSuffixesToReplicateOptions().getAvailableSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          knownServerIds.add(replica.getReplicationId());
          Object v = replica.getServer().getServerProperties().get(REPLICATION_SERVER_ID);
          if (v != null)
          {
            knownReplicationServerIds.add((Integer) v);
          }
        }
      }
    }

    /*
     * For each suffix specified by the user, create a map from the suffix DN to
     * the set of replication servers. The initial instance in a topology is a
     * degenerate case. Also, collect a set of all observed replication servers
     * as the set of ADS suffix replicas (all instances hosting the replication
     * server also replicate ADS).
     */
    Map<String, Set<String>> replicationServers = new HashMap<>();
    Set<String> adsServers = new HashSet<>();

    if (getUserData().getReplicationOptions().getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY)
    {
      List<String> baseDns = getUserData().getNewSuffixOptions().getBaseDns();
      Set<String> h = new HashSet<>();
      h.add(getLocalReplicationServer());
      adsServers.add(getLocalReplicationServer());
      for (String dn : baseDns)
      {
        replicationServers.put(dn, new HashSet<String>(h));
      }
    }
    else
    {
      Set<SuffixDescriptor> suffixes = getUserData().getSuffixesToReplicateOptions().getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        Set<String> h = new HashSet<>(suffix.getReplicationServers());
        adsServers.addAll(suffix.getReplicationServers());
        h.add(getLocalReplicationServer());
        adsServers.add(getLocalReplicationServer());
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          ServerDescriptor server = replica.getServer();
          AuthenticationData repPort = getUserData().getRemoteWithNoReplicationPort().get(server);
          if (repPort != null)
          {
            h.add(server.getHostName() + ":" + repPort.getPort());
            adsServers.add(server.getHostName() + ":" + repPort.getPort());
          }
        }
        replicationServers.put(suffix.getDN(), h);
      }
    }
    replicationServers.put(ADSContext.getAdministrationSuffixDN(), adsServers);
    replicationServers.put(Constants.SCHEMA_DN, new HashSet<String>(adsServers));

    InitialLdapContext ctx = null;
    long localTime = -1;
    long localTimeMeasureTime = -1;
    String localServerDisplay = null;
    try
    {
      ctx = createLocalContext();
      helper.configureReplication(ctx, replicationServers,
          getUserData().getReplicationOptions().getReplicationPort(),
          getUserData().getReplicationOptions().useSecureReplication(),
          getLocalHostPort(),
          knownReplicationServerIds, knownServerIds);
      localTimeMeasureTime = System.currentTimeMillis();
      localTime = Utils.getServerClock(ctx);
      localServerDisplay = ConnectionUtils.getHostPort(ctx);
    }
    catch (NamingException ne)
    {
      LocalizableMessage failedMsg = getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), ne);
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, failedMsg, ne);
    }
    finally
    {
      StaticUtils.close(ctx);
    }
    notifyListeners(getFormattedDoneWithLineBreak());
    checkAbort();

    if (getUserData().getReplicationOptions().getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      Map<ServerDescriptor, Set<ReplicaDescriptor>> hm = new HashMap<>();
      for (SuffixDescriptor suffix : getUserData().getSuffixesToReplicateOptions().getSuffixes())
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          Set<ReplicaDescriptor> replicas = hm.get(replica.getServer());
          if (replicas == null)
          {
            replicas = new HashSet<>();
            hm.put(replica.getServer(), replicas);
          }
          replicas.add(replica);
        }
      }
      for (ServerDescriptor server : hm.keySet())
      {
        notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CONFIGURING_REPLICATION_REMOTE.get(getHostPort(server))));
        Integer v = (Integer) server.getServerProperties().get(REPLICATION_SERVER_PORT);
        int replicationPort;
        boolean enableSecureReplication;
        if (v != null)
        {
          replicationPort = v;
          enableSecureReplication = false;
        }
        else
        {
          AuthenticationData authData = getUserData().getRemoteWithNoReplicationPort().get(server);
          if (authData != null)
          {
            replicationPort = authData.getPort();
            enableSecureReplication = authData.useSecureConnection();
          }
          else
          {
            replicationPort = Constants.DEFAULT_REPLICATION_PORT;
            enableSecureReplication = false;
            logger.warn(LocalizableMessage.raw("Could not find replication port for: " + getHostPort(server)));
          }
        }
        Set<String> dns = new HashSet<>();
        for (ReplicaDescriptor replica : hm.get(server))
        {
          dns.add(replica.getSuffix().getDN());
        }
        dns.add(ADSContext.getAdministrationSuffixDN());
        dns.add(Constants.SCHEMA_DN);
        Map<String, Set<String>> remoteReplicationServers = new HashMap<>();
        for (String dn : dns)
        {
          Set<String> repServer = replicationServers.get(dn);
          if (repServer == null)
          {
            // Do the comparison manually
            for (String dn1 : replicationServers.keySet())
            {
              if (Utils.areDnsEqual(dn, dn1))
              {
                repServer = replicationServers.get(dn1);
                dn = dn1;
                break;
              }
            }
          }
          if (repServer != null)
          {
            remoteReplicationServers.put(dn, repServer);
          }
          else
          {
            logger.warn(LocalizableMessage.raw("Could not find replication server for: " + dn));
          }
        }

        ctx = getRemoteConnection(server, getTrustManager(), getPreferredConnections());
        ConfiguredReplication repl =
            helper.configureReplication(ctx, remoteReplicationServers, replicationPort, enableSecureReplication,
                ConnectionUtils.getHostPort(ctx), knownReplicationServerIds, knownServerIds);
        long remoteTimeMeasureTime = System.currentTimeMillis();
        long remoteTime = Utils.getServerClock(ctx);
        if (localTime != -1
            && remoteTime != -1
            && Math.abs(localTime - remoteTime - localTimeMeasureTime + remoteTimeMeasureTime) >
               THRESHOLD_CLOCK_DIFFERENCE_WARNING * 60 * 1000)
        {
          notifyListeners(getFormattedWarning(INFO_WARNING_SERVERS_CLOCK_DIFFERENCE.get(localServerDisplay,
              ConnectionUtils.getHostPort(ctx), THRESHOLD_CLOCK_DIFFERENCE_WARNING)));
        }

        hmConfiguredRemoteReplication.put(server, repl);

        StaticUtils.close(ctx);
        notifyListeners(getFormattedDoneWithLineBreak());
        checkAbort();
      }
    }
  }

  /**
   * This methods enables this server as a Windows service.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  protected void enableWindowsService() throws ApplicationException
  {
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_ENABLING_WINDOWS_SERVICE.get()));
    InstallerHelper helper = new InstallerHelper();
    helper.enableWindowsService();
    notifyListeners(getLineBreak());
  }

  /**
   * Updates the contents of the provided map with the localized summary
   * strings.
   *
   * @param hmSummary
   *          the Map to be updated.
   * @param isCli
   *          a boolean to indicate if the install is using CLI or GUI
   */
  protected void initSummaryMap(Map<ProgressStep, LocalizableMessage> hmSummary, boolean isCli)
  {
    put(hmSummary, NOT_STARTED, INFO_SUMMARY_INSTALL_NOT_STARTED);
    put(hmSummary, DOWNLOADING, INFO_SUMMARY_DOWNLOADING);
    put(hmSummary, EXTRACTING, INFO_SUMMARY_EXTRACTING);
    put(hmSummary, CONFIGURING_SERVER, INFO_SUMMARY_CONFIGURING);
    put(hmSummary, CREATING_BASE_ENTRY, INFO_SUMMARY_CREATING_BASE_ENTRY);
    put(hmSummary, IMPORTING_LDIF, INFO_SUMMARY_IMPORTING_LDIF);
    put(hmSummary, IMPORTING_AUTOMATICALLY_GENERATED, INFO_SUMMARY_IMPORTING_AUTOMATICALLY_GENERATED);
    put(hmSummary, CONFIGURING_REPLICATION, INFO_SUMMARY_CONFIGURING_REPLICATION);
    put(hmSummary, STARTING_SERVER, INFO_SUMMARY_STARTING);
    put(hmSummary, STOPPING_SERVER, INFO_SUMMARY_STOPPING);
    put(hmSummary, CONFIGURING_ADS, INFO_SUMMARY_CONFIGURING_ADS);
    put(hmSummary, INITIALIZE_REPLICATED_SUFFIXES, INFO_SUMMARY_INITIALIZE_REPLICATED_SUFFIXES);
    put(hmSummary, ENABLING_WINDOWS_SERVICE, INFO_SUMMARY_ENABLING_WINDOWS_SERVICE);
    put(hmSummary, WAITING_TO_CANCEL, INFO_SUMMARY_WAITING_TO_CANCEL);
    put(hmSummary, CANCELING, INFO_SUMMARY_CANCELING);

    Installation installation = getInstallation();
    String cmd = Utils.addWordBreaks(getPath(installation.getControlPanelCommandFile()), 60, 5);
    if (!isCli)
    {
      cmd = UIFactory.applyFontToHtml(cmd, UIFactory.INSTRUCTIONS_MONOSPACE_FONT);
    }
    String formattedPath =
        Utils.addWordBreaks(formatter.getFormattedText(LocalizableMessage.raw(getPath(new File(getInstancePath()))))
            .toString(), 60, 5);
    LocalizableMessage successMessage =
        Utils.getCustomizedObject("INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY",
            INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY.get(DynamicConstants.PRODUCT_NAME,
                DynamicConstants.PRODUCT_NAME, formattedPath, INFO_GENERAL_SERVER_STOPPED.get(),
                DynamicConstants.DOC_QUICK_REFERENCE_GUIDE, DynamicConstants.PRODUCT_NAME, cmd),
            LocalizableMessage.class);
    hmSummary.put(FINISHED_SUCCESSFULLY, getFormattedSuccess(successMessage));
    hmSummary.put(FINISHED_CANCELED, getFormattedSuccess(INFO_SUMMARY_INSTALL_FINISHED_CANCELED.get()));
    hmSummary.put(FINISHED_WITH_ERROR,
        getFormattedError(INFO_SUMMARY_INSTALL_FINISHED_WITH_ERROR.get(INFO_GENERAL_SERVER_STOPPED.get(), cmd)));
  }

  private void put(Map<ProgressStep, LocalizableMessage> hmSummary, InstallProgressStep step, Arg0 msg)
  {
    hmSummary.put(step, getFormattedSummary(msg.get()));
  }

  /**
   * Updates the messages in the summary with the state of the server.
   *
   * @param hmSummary
   *          the Map containing the messages.
   * @param isCli
   *          a boolean to indicate if the install is using CLI or GUI
   */
  protected void updateSummaryWithServerState(Map<ProgressStep, LocalizableMessage> hmSummary, Boolean isCli)
  {
    Installation installation = getInstallation();
    String cmd = getPath(installation.getControlPanelCommandFile());
    if (!isCli)
    {
      cmd = Utils.addWordBreaks(UIFactory.applyFontToHtml(cmd, UIFactory.INSTRUCTIONS_MONOSPACE_FONT), 60, 5);
    }
    LocalizableMessage status;
    if (installation.getStatus().isServerRunning())
    {
      status = INFO_GENERAL_SERVER_STARTED.get();
    }
    else
    {
      status = INFO_GENERAL_SERVER_STOPPED.get();
    }
    String formattedPath =
        Utils.addWordBreaks(formatter.getFormattedText(LocalizableMessage.raw(getPath(new File(getInstancePath()))))
            .toString(), 60, 5);
    LocalizableMessage successMessage =
        Utils.getCustomizedObject("INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY",
            INFO_SUMMARY_INSTALL_FINISHED_SUCCESSFULLY.get(DynamicConstants.PRODUCT_NAME,
                DynamicConstants.PRODUCT_NAME, formattedPath, status, DynamicConstants.DOC_QUICK_REFERENCE_GUIDE,
                DynamicConstants.PRODUCT_NAME, cmd), LocalizableMessage.class);
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY, getFormattedSuccess(successMessage));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR, getFormattedError(INFO_SUMMARY_INSTALL_FINISHED_WITH_ERROR
        .get(status, cmd)));
  }

  /**
   * Checks the value of <code>canceled</code> field and throws an
   * ApplicationException if true. This indicates that the user has canceled
   * this operation and the process of aborting should begin as soon as
   * possible.
   *
   * @throws ApplicationException
   *           thrown if <code>canceled</code>
   */
  @Override
  public void checkAbort() throws ApplicationException
  {
    if (canceled)
    {
      setCurrentProgressStep(InstallProgressStep.CANCELING);
      notifyListeners(null);
      throw new ApplicationException(ReturnCode.CANCELED, INFO_INSTALL_CANCELED.get(), null);
    }
  }

  /**
   * Writes the host name to a file that will be used by the server to generate
   * a self-signed certificate.
   */
  private void writeHostName()
  {
    BufferedWriter writer = null;
    try
    {
      writer = new BufferedWriter(new FileWriter(getHostNameFile(), false));
      writer.append(getUserData().getHostName());
    }
    catch (IOException ioe)
    {
      logger.warn(LocalizableMessage.raw("Error writing host name file: " + ioe, ioe));
    }
    finally
    {
      StaticUtils.close(writer);
    }
  }

  /**
   * Returns the file path where the host name is to be written.
   *
   * @return the file path where the host name is to be written.
   */
  private String getHostNameFile()
  {
    return Utils.getPath(getInstallation().getRootDirectory().getAbsolutePath(), SetupUtils.HOST_NAME_FILE);
  }

  /**
   * Writes the java home that we are using for the setup in a file. This way we
   * can use this java home even if the user has not set OPENDJ_JAVA_HOME when
   * running the different scripts.
   */
  private void writeOpenDSJavaHome()
  {
    try
    {
      // This isn't likely to happen, and it's not a serious problem even if
      // it does.
      InstallerHelper helper = new InstallerHelper();
      helper.writeSetOpenDSJavaHome(getUserData(), getInstallationPath());
    }
    catch (Exception e)
    {
      logger.warn(LocalizableMessage.raw("Error writing OpenDJ Java Home file: " + e, e));
    }
  }

  /**
   * These methods validate the data provided by the user in the panels and
   * update the userData object according to that content.
   *
   * @param cStep
   *          the current step of the wizard
   * @param qs
   *          QuickStart controller
   * @throws UserDataException
   *           if the data provided by the user is not valid.
   */
  @Override
  public void updateUserData(WizardStep cStep, QuickSetup qs) throws UserDataException
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
    else if (cStep == RUNTIME_OPTIONS)
    {
      updateUserDataForRuntimeOptionsPanel(qs);
    }
    else if (cStep == REVIEW)
    {
      updateUserDataForReviewPanel(qs);
    }
  }

  /**
   * Sets the current progress step of the installation process.
   *
   * @param currentProgressStep
   *          the current progress step of the installation process.
   */
  protected void setCurrentProgressStep(InstallProgressStep currentProgressStep)
  {
    if (currentProgressStep != null)
    {
      this.completedProgress.add(currentProgressStep);
    }
    this.currentProgressStep = currentProgressStep;
  }

  /**
   * This methods updates the data on the server based on the contents of the
   * UserData object provided in the constructor.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  protected void createData() throws ApplicationException
  {
    if (createNotReplicatedSuffix()
        && NewSuffixOptions.Type.LEAVE_DATABASE_EMPTY != getUserData().getNewSuffixOptions().getType())
    {
      currentProgressStep = getUserData().getNewSuffixOptions().getInstallProgressStep();
      if (isVerbose())
      {
        notifyListeners(getTaskSeparator());
      }

      switch (getUserData().getNewSuffixOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        createBaseEntry();
        break;
      case IMPORT_FROM_LDIF_FILE:
        importLDIF();
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        importAutomaticallyGenerated();
        break;
      default:
        break;
      }
    }
  }

  /**
   * This method initialize the contents of the synchronized servers with the
   * contents of the first server we find.
   *
   * @throws ApplicationException
   *           if something goes wrong.
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
      LocalizableMessage failedMsg = getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), t);
      StaticUtils.close(ctx);
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, failedMsg, t);
    }

    Set<SuffixDescriptor> suffixes = getUserData().getSuffixesToReplicateOptions().getSuffixes();

    /* Initialize local ADS and schema contents using any replica. */
    {
      ServerDescriptor server = suffixes.iterator().next().getReplicas().iterator().next().getServer();
      InitialLdapContext rCtx = null;
      try
      {
        rCtx = getRemoteConnection(server, getTrustManager(), getPreferredConnections());
        TopologyCacheFilter filter = new TopologyCacheFilter();
        filter.setSearchMonitoringInformation(false);
        filter.addBaseDNToSearch(ADSContext.getAdministrationSuffixDN());
        filter.addBaseDNToSearch(Constants.SCHEMA_DN);
        ServerDescriptor s = createStandalone(rCtx, filter);
        for (ReplicaDescriptor replica : s.getReplicas())
        {
          String dn = replica.getSuffix().getDN();
          if (areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()))
          {
            suffixes.add(replica.getSuffix());
          }
          else if (areDnsEqual(dn, Constants.SCHEMA_DN))
          {
            suffixes.add(replica.getSuffix());
          }
        }
      }
      catch (NamingException ne)
      {
        LocalizableMessage msg;
        if (isCertificateException(ne))
        {
          msg = INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(getHostPort(server), ne.toString(true));
        }
        else
        {
          msg = INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(getHostPort(server), ne.toString(true));
        }
        throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, msg, ne);
      }
      finally
      {
        StaticUtils.close(rCtx);
      }
    }

    for (SuffixDescriptor suffix : suffixes)
    {
      String dn = suffix.getDN();

      ReplicaDescriptor replica = suffix.getReplicas().iterator().next();
      ServerDescriptor server = replica.getServer();
      String hostPort = getHostPort(server);

      boolean isADS = areDnsEqual(dn, ADSContext.getAdministrationSuffixDN());
      boolean isSchema = areDnsEqual(dn, Constants.SCHEMA_DN);
      if (isADS)
      {
        if (isVerbose())
        {
          notifyListeners(getFormattedWithPoints(INFO_PROGRESS_INITIALIZING_ADS.get()));
        }
      }
      else if (isSchema)
      {
        if (isVerbose())
        {
          notifyListeners(getFormattedWithPoints(INFO_PROGRESS_INITIALIZING_SCHEMA.get()));
        }
      }
      else
      {
        notifyListeners(getFormattedProgress(INFO_PROGRESS_INITIALIZING_SUFFIX.get(dn, hostPort)));
        notifyListeners(getLineBreak());
      }
      try
      {
        int replicationId = replica.getReplicationId();
        if (replicationId == -1)
        {
          // This occurs if the remote server had not replication configured.
          InitialLdapContext rCtx = null;
          try
          {
            rCtx = getRemoteConnection(server, getTrustManager(), getPreferredConnections());
            TopologyCacheFilter filter = new TopologyCacheFilter();
            filter.setSearchMonitoringInformation(false);
            filter.addBaseDNToSearch(dn);
            ServerDescriptor s = createStandalone(rCtx, filter);
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
            LocalizableMessage msg;
            if (isCertificateException(ne))
            {
              msg = INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(getHostPort(server), ne.toString(true));
            }
            else
            {
              msg = INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(getHostPort(server), ne.toString(true));
            }
            throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, msg, ne);
          }
          finally
          {
            StaticUtils.close(rCtx);
          }
        }
        if (replicationId == -1)
        {
          throw new ApplicationException(ReturnCode.APPLICATION_ERROR, ERR_COULD_NOT_FIND_REPLICATIONID.get(dn), null);
        }
        StaticUtils.sleep(3000);
        int nTries = 5;
        boolean initDone = false;
        while (!initDone)
        {
          try
          {
            logger.info(LocalizableMessage.raw("Calling initializeSuffix with base DN: " + dn));
            logger.info(LocalizableMessage.raw("Try number: " + (6 - nTries)));
            logger.info(LocalizableMessage.raw("replicationId of source replica: " + replicationId));
            initializeSuffix(ctx, replicationId, dn, !isADS && !isSchema, hostPort);
            initDone = true;
          }
          catch (PeerNotFoundException pnfe)
          {
            logger.info(LocalizableMessage.raw("Peer could not be found"));
            if (nTries == 1)
            {
              throw new ApplicationException(ReturnCode.APPLICATION_ERROR, pnfe.getMessageObject(), null);
            }
            StaticUtils.sleep((5 - nTries) * 3000);
          }
          nTries--;
        }
      }
      catch (ApplicationException ae)
      {
        StaticUtils.close(ctx);
        throw ae;
      }
      if ((isADS || isSchema) && isVerbose())
      {
        notifyListeners(getFormattedDone());
        notifyListeners(getLineBreak());
      }
      checkAbort();
    }
  }

  /**
   * This method updates the ADS contents (and creates the according suffixes).
   * If the user specified an existing topology, the new instance is registered
   * with that ADS (the ADS might need to be created), and the local ADS will be
   * populated when the local server is added to the remote server's ADS
   * replication domain in a subsequent step. Otherwise, an ADS is created on
   * the new instance and the server is registered with the new ADS. NOTE: this
   * method assumes that the local server and any remote server are running.
   *
   * @throws ApplicationException
   *           if something goes wrong.
   */
  protected void updateADS() throws ApplicationException
  {
    DataReplicationOptions repl = getUserData().getReplicationOptions();
    boolean isRemoteServer = repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
    AuthenticationData auth = isRemoteServer ? repl.getAuthenticationData() : null;
    InitialLdapContext remoteCtx = null; // Bound to remote ADS host (if any).
    InitialLdapContext localCtx = null; // Bound to local server.
    ADSContext adsContext = null; // Bound to ADS host (via one of above).

    /*
     * Outer try-catch-finally to convert occurrences of NamingException and
     * ADSContextException to ApplicationException and clean up JNDI contexts.
     */
    try
    {
      if (isRemoteServer)
      {
        remoteCtx = createInitialLdapContext(auth);
        adsContext = new ADSContext(remoteCtx); // adsContext owns remoteCtx

        /*
         * Check the remote server for ADS. If it does not exist, create the
         * initial ADS there and register the server with itself.
         */
        if (!adsContext.hasAdminData())
        {
          if (isVerbose())
          {
            notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CREATING_ADS_ON_REMOTE.get(getHostDisplay(auth))));
          }

          adsContext.createAdminData(null);
          TopologyCacheFilter filter = new TopologyCacheFilter();
          filter.setSearchMonitoringInformation(false);
          filter.setSearchBaseDNInformation(false);
          ServerDescriptor server = createStandalone(remoteCtx, filter);
          server.updateAdsPropertiesWithServerProperties();
          adsContext.registerServer(server.getAdsProperties());
          createdRemoteAds = true;
          if (isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
          checkAbort();
        }
      }

      /* Act on local server depending on if using remote or local ADS */
      if (isVerbose())
      {
        notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CREATING_ADS.get()));
      }
      localCtx = createLocalContext();
      //      if (isRemoteServer)
      //      {
      //        /* Create an empty ADS suffix on the local server. */
      //        ADSContext localAdsContext = new ADSContext(localCtx);
      //        localAdsContext.createAdministrationSuffix(null);
      //      }
      if (!isRemoteServer)
      {
        /* Configure local server to have an ADS */
        adsContext = new ADSContext(localCtx); // adsContext owns localCtx
        adsContext.createAdminData(null);
      }
      /* Register new server in ADS. */
      TopologyCacheFilter filter = new TopologyCacheFilter();
      filter.setSearchMonitoringInformation(false);
      filter.setSearchBaseDNInformation(false);
      ServerDescriptor server = createStandalone(localCtx, filter);
      server.updateAdsPropertiesWithServerProperties();
      if (0 == adsContext.registerOrUpdateServer(server.getAdsProperties()))
      {
        if (isRemoteServer)
        {
          registeredNewServerOnRemote = true;
        }
      }
      else
      {
        logger.warn(LocalizableMessage.raw("Server was already registered. Updating " + "server registration."));
      }
      if (isRemoteServer)
      {
        seedAdsTrustStore(localCtx, adsContext.getTrustedCertificates());
      }
      if (isVerbose())
      {
        notifyListeners(getFormattedDoneWithLineBreak());
      }
      checkAbort();

      /* Add global administrator if the user specified one. */
      if (getUserData().mustCreateAdministrator())
      {
        try
        {
          if (isVerbose())
          {
            notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CREATING_ADMINISTRATOR.get()));
          }
          adsContext.createAdministrator(getAdministratorProperties(getUserData()));
          if (isRemoteServer && !createdRemoteAds)
          {
            createdAdministrator = true;
          }
          if (isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
          checkAbort();
        }
        catch (ADSContextException ade)
        {
          if (ade.getError() == ADSContextException.ErrorType.ALREADY_REGISTERED)
          {
            notifyListeners(getFormattedWarning(INFO_ADMINISTRATOR_ALREADY_REGISTERED.get()));
            adsContext.unregisterServer(server.getAdsProperties());
            adsContext.registerServer(server.getAdsProperties());
          }
          else
          {
            throw ade;
          }
        }
      }
    }
    catch (NamingException ne)
    {
      LocalizableMessage msg;
      if (isRemoteServer)
      {
        msg = getMessageForException(ne, getHostDisplay(auth));
      }
      else
      {
        msg = Utils.getMessageForException(ne);
      }
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, msg, ne);
    }
    catch (ADSContextException ace)
    {
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, (isRemoteServer ? INFO_REMOTE_ADS_EXCEPTION.get(
          getHostDisplay(auth), ace.getMessageObject()) : INFO_ADS_EXCEPTION.get(ace)), ace);
    }
    finally
    {
      StaticUtils.close(remoteCtx, localCtx);
    }
  }

  private InitialLdapContext createInitialLdapContext(AuthenticationData auth) throws NamingException
  {
    String ldapUrl = getLdapUrl(auth);
    String dn = auth.getDn();
    String pwd = auth.getPwd();

    if (auth.useSecureConnection())
    {
      ApplicationTrustManager trustManager = getTrustManager();
      trustManager.setHost(auth.getHostName());
      return createLdapsContext(ldapUrl, dn, pwd, getConnectTimeout(), null, trustManager, null);
    }
    return createLdapContext(ldapUrl, dn, pwd, getConnectTimeout(), null);
  }

  /**
   * Tells whether we must create a suffix that we are not going to replicate
   * with other servers or not.
   *
   * @return <CODE>true</CODE> if we must create a new suffix and
   *         <CODE>false</CODE> otherwise.
   */
  protected boolean createNotReplicatedSuffix()
  {
    DataReplicationOptions repl = getUserData().getReplicationOptions();

    SuffixesToReplicateOptions suf = getUserData().getSuffixesToReplicateOptions();

    return repl.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY
        || repl.getType() == DataReplicationOptions.Type.STANDALONE
        || suf.getType() == SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
  }

  /**
   * Returns <CODE>true</CODE> if we must configure replication and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we must configure replication and
   *         <CODE>false</CODE> otherwise.
   */
  protected boolean mustConfigureReplication()
  {
    return getUserData().getReplicationOptions().getType() != DataReplicationOptions.Type.STANDALONE;
  }

  /**
   * Returns <CODE>true</CODE> if we must create the ADS and <CODE>false</CODE>
   * otherwise.
   *
   * @return <CODE>true</CODE> if we must create the ADS and <CODE>false</CODE>
   *         otherwise.
   */
  protected boolean mustCreateAds()
  {
    return getUserData().getReplicationOptions().getType() != DataReplicationOptions.Type.STANDALONE;
  }

  /**
   * Returns <CODE>true</CODE> if we must start the server and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we must start the server and
   *         <CODE>false</CODE> otherwise.
   */
  protected boolean mustStart()
  {
    return getUserData().getStartServer() || mustCreateAds();
  }

  /**
   * Returns <CODE>true</CODE> if the start server must be launched in verbose
   * mode and <CODE>false</CODE> otherwise. The verbose flag is not enough
   * because in the case where many entries have been imported, the startup
   * phase can take long.
   *
   * @return <CODE>true</CODE> if the start server must be launched in verbose
   *         mode and <CODE>false</CODE> otherwise.
   */
  protected boolean isStartVerbose()
  {
    if (isVerbose())
    {
      return true;
    }
    boolean manyEntriesToImport = false;
    NewSuffixOptions.Type type = getUserData().getNewSuffixOptions().getType();
    if (type == NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE)
    {
      long mbTotalSize = 0;
      LinkedList<String> ldifPaths = getUserData().getNewSuffixOptions().getLDIFPaths();
      for (String ldifPath : ldifPaths)
      {
        File f = new File(ldifPath);
        mbTotalSize += f.length();
      }
      // Assume entries of 1kb
      if (mbTotalSize > THRESHOLD_VERBOSE_START * 1024)
      {
        manyEntriesToImport = true;
      }
    }
    else if (type == NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA)
    {
      int nEntries = getUserData().getNewSuffixOptions().getNumberEntries();
      if (nEntries > THRESHOLD_VERBOSE_START)
      {
        manyEntriesToImport = true;
      }
    }
    return manyEntriesToImport;
  }

  /**
   * Returns <CODE>true</CODE> if we must stop the server and <CODE>false</CODE>
   * otherwise. The server might be stopped if the user asked not to start it at
   * the end of the installation and it was started temporarily to update its
   * configuration.
   *
   * @return <CODE>true</CODE> if we must stop the server and <CODE>false</CODE>
   *         otherwise.
   */
  protected boolean mustStop()
  {
    return !getUserData().getStartServer() && mustCreateAds();
  }

  /**
   * Returns <CODE>true</CODE> if we must initialize suffixes and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we must initialize suffixes and
   *         <CODE>false</CODE> otherwise.
   */
  protected boolean mustInitializeSuffixes()
  {
    return getUserData().getReplicationOptions().getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
  }

  /**
   * Returns the list of preferred URLs to connect to remote servers. In fact it
   * returns only the URL to the remote server specified by the user in the
   * replication options panel. The method returns a list for convenience with
   * other interfaces.
   * <p>
   * NOTE: this method assumes that the UserData object has
   * already been updated with the host and port of the remote server.
   *
   * @return the list of preferred URLs to connect to remote servers.
   */
  private Set<PreferredConnection> getPreferredConnections()
  {
    Set<PreferredConnection> cnx = new LinkedHashSet<>();
    DataReplicationOptions repl = getUserData().getReplicationOptions();
    if (repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      AuthenticationData auth = repl.getAuthenticationData();
      if (auth != null)
      {
        PreferredConnection.Type type;
        if (auth.useSecureConnection())
        {
          type = PreferredConnection.Type.LDAPS;
        }
        else
        {
          type = PreferredConnection.Type.LDAP;
        }
        cnx.add(new PreferredConnection(getLdapUrl(auth), type));
      }
    }
    return cnx;
  }

  private String getLdapUrl(AuthenticationData auth)
  {
    if (auth.useSecureConnection())
    {
      return "ldaps://" + auth.getHostName() + ":" + auth.getPort();
    }
    return "ldap://" + auth.getHostName() + ":" + auth.getPort();
  }

  private String getHostDisplay(AuthenticationData auth)
  {
    return auth.getHostName() + ":" + auth.getPort();
  }

  private Map<ADSContext.ServerProperty, Object> getNewServerAdsProperties(UserData userData)
  {
    Map<ADSContext.ServerProperty, Object> serverProperties = new HashMap<>();
    serverProperties.put(ADSContext.ServerProperty.HOST_NAME, userData.getHostName());
    serverProperties.put(ADSContext.ServerProperty.LDAP_PORT, String.valueOf(userData.getServerPort()));
    serverProperties.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");

    // TODO: even if the user does not configure SSL maybe we should choose
    // a secure port that is not being used and that we can actually use.
    SecurityOptions sec = userData.getSecurityOptions();
    if (sec.getEnableSSL())
    {
      serverProperties.put(ADSContext.ServerProperty.LDAPS_PORT, String.valueOf(sec.getSslPort()));
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
      path = userData.getServerLocation();
    }
    else
    {
      path = getInstallPathFromClasspath();
    }
    serverProperties.put(ADSContext.ServerProperty.INSTANCE_PATH, path);

    String serverID = serverProperties.get(ADSContext.ServerProperty.HOST_NAME) + ":" + userData.getServerPort();

    /* TODO: do we want to ask this specifically to the user? */
    serverProperties.put(ADSContext.ServerProperty.ID, serverID);
    serverProperties.put(ADSContext.ServerProperty.HOST_OS, OperatingSystem.getOperatingSystem().toString());

    return serverProperties;
  }

  private Map<ADSContext.AdministratorProperty, Object> getAdministratorProperties(UserData userData)
  {
    Map<ADSContext.AdministratorProperty, Object> adminProperties = new HashMap<>();
    adminProperties.put(ADSContext.AdministratorProperty.UID, userData.getGlobalAdministratorUID());
    adminProperties.put(ADSContext.AdministratorProperty.PASSWORD, userData.getGlobalAdministratorPassword());
    adminProperties.put(ADSContext.AdministratorProperty.DESCRIPTION,
                        INFO_GLOBAL_ADMINISTRATOR_DESCRIPTION.get().toString());
    return adminProperties;
  }

  /**
   * Validate the data provided by the user in the server settings panel and
   * update the userData object according to that content.
   *
   * @throws UserDataException
   *           if the data provided by the user is not valid.
   */
  private void updateUserDataForServerSettingsPanel(QuickSetup qs) throws UserDataException
  {
    List<LocalizableMessage> errorMsgs = new ArrayList<>();
    LocalizableMessage confirmationMsg = null;

    if (isWebStart())
    {
      // Check the server location
      String serverLocation = qs.getFieldStringValue(FieldName.SERVER_LOCATION);

      if (serverLocation == null || "".equals(serverLocation.trim()))
      {
        errorMsgs.add(INFO_EMPTY_SERVER_LOCATION.get());
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (!parentDirectoryExists(serverLocation))
      {
        String existingParentDirectory = null;
        File f = new File(serverLocation);
        while (existingParentDirectory == null && f != null)
        {
          f = f.getParentFile();
          if (f != null && f.exists())
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
          errorMsgs.add(INFO_PARENT_DIRECTORY_COULD_NOT_BE_FOUND.get(serverLocation));
          qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
        }
        else if (!canWrite(existingParentDirectory))
        {
          errorMsgs.add(INFO_DIRECTORY_NOT_WRITABLE.get(existingParentDirectory));
          qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
        }
        else if (!hasEnoughSpace(existingParentDirectory, getRequiredInstallSpace()))
        {
          long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
          errorMsgs.add(INFO_NOT_ENOUGH_DISK_SPACE.get(existingParentDirectory, requiredInMb));
          qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
        }
        else
        {
          confirmationMsg = INFO_PARENT_DIRECTORY_DOES_NOT_EXIST_CONFIRMATION.get(serverLocation);
          getUserData().setServerLocation(serverLocation);
        }
      }
      else if (fileExists(serverLocation))
      {
        errorMsgs.add(INFO_FILE_EXISTS.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (directoryExistsAndIsNotEmpty(serverLocation))
      {
        errorMsgs.add(INFO_DIRECTORY_EXISTS_NOT_EMPTY.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (!canWrite(serverLocation))
      {
        errorMsgs.add(INFO_DIRECTORY_NOT_WRITABLE.get(serverLocation));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (!hasEnoughSpace(serverLocation, getRequiredInstallSpace()))
      {
        long requiredInMb = getRequiredInstallSpace() / (1024 * 1024);
        errorMsgs.add(INFO_NOT_ENOUGH_DISK_SPACE.get(serverLocation, requiredInMb));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else if (OperatingSystem.isWindows() && serverLocation.contains("%"))
      {
        errorMsgs.add(INFO_INVALID_CHAR_IN_PATH.get("%"));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      }
      else
      {
        getUserData().setServerLocation(serverLocation);
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, false);
      }
    }

    // Check the host is not empty.
    // TODO: check that the host name is valid...
    String hostName = qs.getFieldStringValue(FieldName.HOST_NAME);
    if (hostName == null || hostName.trim().length() == 0)
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
      if (port < MIN_PORT_VALUE || port > MAX_PORT_VALUE)
      {
        errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(MIN_PORT_VALUE, MAX_PORT_VALUE));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      }
      else if (!canUseAsPort(port))
      {
        errorMsgs.add(getCannotBindErrorMessage(port));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      }
      else
      {
        getUserData().setServerPort(port);
        qs.displayFieldInvalid(FieldName.SERVER_PORT, false);
      }
    }
    catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(MIN_PORT_VALUE, MAX_PORT_VALUE));
      qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
    }

    //  Check the admin connector port
    sPort = qs.getFieldStringValue(FieldName.ADMIN_CONNECTOR_PORT);
    int adminConnectorPort = -1;
    try
    {
      adminConnectorPort = Integer.parseInt(sPort);
      if (adminConnectorPort < MIN_PORT_VALUE || adminConnectorPort > MAX_PORT_VALUE)
      {
        errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(MIN_PORT_VALUE, MAX_PORT_VALUE));
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
      }
      else if (!canUseAsPort(adminConnectorPort))
      {
        errorMsgs.add(getCannotBindErrorMessage(adminConnectorPort));
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
      }
      else if (adminConnectorPort == port)
      {
        errorMsgs.add(INFO_ADMIN_CONNECTOR_VALUE_SEVERAL_TIMES.get());
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
      }
      else
      {
        getUserData().setAdminConnectorPort(adminConnectorPort);
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, false);
      }
    }
    catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_PORT_VALUE_RANGE.get(MIN_PORT_VALUE, MAX_PORT_VALUE));
      qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
    }

    // Check the secure port
    SecurityOptions sec = (SecurityOptions) qs.getFieldValue(FieldName.SECURITY_OPTIONS);
    int securePort = sec.getSslPort();
    if (sec.getEnableSSL())
    {
      if (securePort < MIN_PORT_VALUE || securePort > MAX_PORT_VALUE)
      {
        errorMsgs.add(INFO_INVALID_SECURE_PORT_VALUE_RANGE.get(MIN_PORT_VALUE, MAX_PORT_VALUE));
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
      }
      else if (!canUseAsPort(securePort))
      {
        errorMsgs.add(getCannotBindErrorMessage(securePort));
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
      }
      else if (port == securePort)
      {
        errorMsgs.add(INFO_EQUAL_PORTS.get());
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      }
      else if (adminConnectorPort == securePort)
      {
        errorMsgs.add(INFO_ADMIN_CONNECTOR_VALUE_SEVERAL_TIMES.get());
        qs.displayFieldInvalid(FieldName.SECURITY_OPTIONS, true);
        qs.displayFieldInvalid(FieldName.ADMIN_CONNECTOR_PORT, true);
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

    if (dmDn == null || dmDn.trim().length() == 0)
    {
      errorMsgs.add(INFO_EMPTY_DIRECTORY_MANAGER_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    }
    else if (!isDN(dmDn))
    {
      errorMsgs.add(INFO_NOT_A_DIRECTORY_MANAGER_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    }
    else if (isConfigurationDn(dmDn))
    {
      errorMsgs.add(INFO_DIRECTORY_MANAGER_DN_IS_CONFIG_DN.get());
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, true);
    }
    else
    {
      getUserData().setDirectoryManagerDn(dmDn);
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_DN, false);
    }

    // Check the provided passwords
    String pwd1 = qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_PWD);
    String pwd2 = qs.getFieldStringValue(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM);
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
      errorMsgs.add(INFO_PWD_TOO_SHORT.get(MIN_DIRECTORY_MANAGER_PWD));
      qs.displayFieldInvalid(FieldName.DIRECTORY_MANAGER_PWD, true);
      if (pwd2 == null || pwd2.length() < MIN_DIRECTORY_MANAGER_PWD)
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
    int defaultJMXPort = UserData.getDefaultJMXPort(new int[] { port, securePort });
    if (defaultJMXPort != -1)
    {
      //getUserData().setServerJMXPort(defaultJMXPort);
      getUserData().setServerJMXPort(-1);
    }

    if (!errorMsgs.isEmpty())
    {
      throw new UserDataException(Step.SERVER_SETTINGS, getMessageFromCollection(errorMsgs, "\n"));
    }
    if (confirmationMsg != null)
    {
      throw new UserDataConfirmationException(Step.SERVER_SETTINGS, confirmationMsg);
    }
  }

  private LocalizableMessage getCannotBindErrorMessage(int port)
  {
    if (isPrivilegedPort(port))
    {
      return INFO_CANNOT_BIND_PRIVILEDGED_PORT.get(port);
    }
    return INFO_CANNOT_BIND_PORT.get(port);
  }

  /**
   * Validate the data provided by the user in the data options panel and update
   * the userData object according to that content.
   *
   * @throws UserDataException
   *           if the data provided by the user is not valid.
   */
  private void updateUserDataForReplicationOptionsPanel(QuickSetup qs) throws UserDataException
  {
    boolean hasGlobalAdministrators = false;
    int replicationPort = -1;
    boolean secureReplication = false;
    Integer port = null;
    List<LocalizableMessage> errorMsgs = new ArrayList<>();

    DataReplicationOptions.Type type = (DataReplicationOptions.Type) qs.getFieldValue(FieldName.REPLICATION_OPTIONS);
    String host = qs.getFieldStringValue(FieldName.REMOTE_SERVER_HOST);
    String dn = qs.getFieldStringValue(FieldName.REMOTE_SERVER_DN);
    String pwd = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PWD);

    if (type != DataReplicationOptions.Type.STANDALONE)
    {
      // Check replication port
      replicationPort = checkReplicationPort(qs, errorMsgs);
      secureReplication = (Boolean) qs.getFieldValue(FieldName.REPLICATION_SECURE);
    }

    UserDataConfirmationException confirmEx = null;
    switch (type)
    {
    case IN_EXISTING_TOPOLOGY:
    {
      String sPort = qs.getFieldStringValue(FieldName.REMOTE_SERVER_PORT);
      checkRemoteHostPortDnAndPwd(host, sPort, dn, pwd, qs, errorMsgs);

      if (errorMsgs.isEmpty())
      {
        port = Integer.parseInt(sPort);
        // Try to connect
        boolean[] globalAdmin = { hasGlobalAdministrators };
        String[] effectiveDn = { dn };
        try
        {
          updateUserDataWithADS(host, port, dn, pwd, qs, errorMsgs, globalAdmin, effectiveDn);
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
          new SuffixesToReplicateOptions(SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE,
              new HashSet<SuffixDescriptor>(), new HashSet<SuffixDescriptor>()));
      break;
    }
    case FIRST_IN_TOPOLOGY:
    {
      getUserData().setSuffixesToReplicateOptions(
          new SuffixesToReplicateOptions(SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY,
              new HashSet<SuffixDescriptor>(), new HashSet<SuffixDescriptor>()));
      break;
    }
    default:
      throw new IllegalStateException("Do not know what to do with type: " + type);
    }

    if (errorMsgs.isEmpty())
    {
      AuthenticationData auth = new AuthenticationData();
      auth.setHostName(host);
      if (port != null)
      {
        auth.setPort(port);
      }
      auth.setDn(dn);
      auth.setPwd(pwd);
      auth.setUseSecureConnection(true);

      getUserData().setReplicationOptions(createDataReplicationOptions(replicationPort, secureReplication, type, auth));
      getUserData().createAdministrator(
          !hasGlobalAdministrators && type == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY);
    }
    if (!errorMsgs.isEmpty())
    {
      throw new UserDataException(Step.REPLICATION_OPTIONS, getMessageFromCollection(errorMsgs, "\n"));
    }
    if (confirmEx != null)
    {
      throw confirmEx;
    }
  }

  private DataReplicationOptions createDataReplicationOptions(int replicationPort, boolean secureReplication,
      DataReplicationOptions.Type type, AuthenticationData auth)
  {
    switch (type)
    {
    case IN_EXISTING_TOPOLOGY:
      return DataReplicationOptions.createInExistingTopology(auth, replicationPort, secureReplication);
    case STANDALONE:
      return DataReplicationOptions.createStandalone();
    case FIRST_IN_TOPOLOGY:
      return DataReplicationOptions.createFirstInTopology(replicationPort, secureReplication);
    default:
      throw new IllegalStateException("Do not know what to do with type: " + type);
    }
  }

  private int checkReplicationPort(QuickSetup qs, List<LocalizableMessage> errorMsgs)
  {
    int replicationPort = -1;
    String sPort = qs.getFieldStringValue(FieldName.REPLICATION_PORT);
    try
    {
      replicationPort = Integer.parseInt(sPort);
      if (replicationPort < MIN_PORT_VALUE || replicationPort > MAX_PORT_VALUE)
      {
        errorMsgs.add(INFO_INVALID_REPLICATION_PORT_VALUE_RANGE.get(MIN_PORT_VALUE, MAX_PORT_VALUE));
        qs.displayFieldInvalid(FieldName.SERVER_PORT, true);
      }
      else if (!canUseAsPort(replicationPort))
      {
        errorMsgs.add(getCannotBindErrorMessage(replicationPort));
        qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
      }
      else
      {
        /* Check that we did not chose this port for another protocol */
        SecurityOptions sec = getUserData().getSecurityOptions();
        if (replicationPort == getUserData().getServerPort() || replicationPort == getUserData().getServerJMXPort()
            || (replicationPort == sec.getSslPort() && sec.getEnableSSL()))
        {
          errorMsgs.add(INFO_REPLICATION_PORT_ALREADY_CHOSEN_FOR_OTHER_PROTOCOL.get());
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
        }
        else
        {
          qs.displayFieldInvalid(FieldName.REPLICATION_PORT, false);
        }
      }
    }
    catch (NumberFormatException nfe)
    {
      errorMsgs.add(INFO_INVALID_REPLICATION_PORT_VALUE_RANGE.get(MIN_PORT_VALUE, MAX_PORT_VALUE));
      qs.displayFieldInvalid(FieldName.REPLICATION_PORT, true);
    }
    return replicationPort;
  }

  private void checkRemoteHostPortDnAndPwd(String host, String sPort, String dn, String pwd, QuickSetup qs,
      List<LocalizableMessage> errorMsgs)
  {
    // Check host
    if (host == null || host.length() == 0)
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
    if (dn == null || dn.length() == 0)
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_DN.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, false);
    }

    // Check password
    if (pwd == null || pwd.length() == 0)
    {
      errorMsgs.add(INFO_EMPTY_REMOTE_PWD.get());
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
    }
    else
    {
      qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, false);
    }
  }

  private void updateUserDataWithADS(String host, int port, String dn, String pwd, QuickSetup qs,
      List<LocalizableMessage> errorMsgs, boolean[] hasGlobalAdministrators, String[] effectiveDn)
      throws UserDataException
  {
    host = getHostNameForLdapUrl(host);
    String ldapUrl = "ldaps://" + host + ":" + port;
    InitialLdapContext ctx = null;

    ApplicationTrustManager trustManager = getTrustManager();
    trustManager.setHost(host);
    trustManager.resetLastRefusedItems();
    try
    {
      effectiveDn[0] = dn;
      try
      {
        ctx = createLdapsContext(ldapUrl, dn, pwd, getConnectTimeout(), null, trustManager, null);
      }
      catch (Throwable t)
      {
        if (!isCertificateException(t))
        {
          // Try using a global administrator
          dn = ADSContext.getAdministratorDN(dn);
          effectiveDn[0] = dn;
          ctx = createLdapsContext(ldapUrl, dn, pwd, getConnectTimeout(), null, trustManager, null);
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
        Set<?> administrators = adsContext.readAdministratorRegistry();
        hasGlobalAdministrators[0] = !administrators.isEmpty();
        Set<TopologyCacheException> exceptions = updateUserDataWithSuffixesInADS(adsContext, trustManager);
        Set<LocalizableMessage> exceptionMsgs = new LinkedHashSet<>();
        /* Check the exceptions and see if we throw them or not. */
        for (TopologyCacheException e : exceptions)
        {
          switch (e.getType())
          {
          case NOT_GLOBAL_ADMINISTRATOR:
            LocalizableMessage errorMsg = INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get();
            throw new UserDataException(Step.REPLICATION_OPTIONS, errorMsg);
          case GENERIC_CREATING_CONNECTION:
            if (isCertificateException(e.getCause()))
            {
              UserDataCertificateException.Type excType;
              ApplicationTrustManager.Cause cause = null;
              if (e.getTrustManager() != null)
              {
                cause = e.getTrustManager().getLastRefusedCause();
              }
              logger.info(LocalizableMessage.raw("Certificate exception cause: " + cause));
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
                  logger.warn(LocalizableMessage.raw("Error parsing ldap url of TopologyCacheException.", t));
                  h = INFO_NOT_AVAILABLE_LABEL.get().toString();
                  p = -1;
                }
                throw new UserDataCertificateException(Step.REPLICATION_OPTIONS, INFO_CERTIFICATE_EXCEPTION.get(h, p),
                    e.getCause(), h, p, e.getTrustManager().getLastRefusedChain(), e.getTrustManager()
                        .getLastRefusedAuthType(), excType);
              }
            }
            break;
          default:
            break;
          }
          exceptionMsgs.add(getMessage(e));
        }
        if (!exceptionMsgs.isEmpty())
        {
          LocalizableMessage confirmationMsg =
              INFO_ERROR_READING_REGISTERED_SERVERS_CONFIRM.get(getMessageFromCollection(exceptionMsgs, "\n"));
          throw new UserDataConfirmationException(Step.REPLICATION_OPTIONS, confirmationMsg);
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
      logger.info(LocalizableMessage.raw("Error connecting to remote server.", t));
      if (isCertificateException(t))
      {
        UserDataCertificateException.Type excType;
        ApplicationTrustManager.Cause cause = trustManager.getLastRefusedCause();
        logger.info(LocalizableMessage.raw("Certificate exception cause: " + cause));
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
          throw new UserDataCertificateException(Step.REPLICATION_OPTIONS, INFO_CERTIFICATE_EXCEPTION.get(host, port),
              t, host, port, trustManager.getLastRefusedChain(), trustManager.getLastRefusedAuthType(), excType);
        }
        else
        {
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
          errorMsgs.add(INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(host + ":" + port, t));
        }
      }
      else if (t instanceof NamingException)
      {
        errorMsgs.add(getMessageForException((NamingException) t, host + ":" + port));
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_DN, true);
        qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PWD, true);
        if (!(t instanceof NamingSecurityException))
        {
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_HOST, true);
          qs.displayFieldInvalid(FieldName.REMOTE_SERVER_PORT, true);
        }
      }
      else if (t instanceof ADSContextException)
      {
        errorMsgs.add(INFO_REMOTE_ADS_EXCEPTION.get(host + ":" + port, t));
      }
      else
      {
        throw new UserDataException(Step.REPLICATION_OPTIONS, getThrowableMsg(INFO_BUG_MSG.get(), t));
      }
    }
    finally
    {
      StaticUtils.close(ctx);
    }
  }

  /**
   * Validate the data provided by the user in the create global administrator
   * panel and update the UserInstallData object according to that content.
   *
   * @throws UserDataException
   *           if the data provided by the user is not valid.
   */
  private void updateUserDataForCreateAdministratorPanel(QuickSetup qs) throws UserDataException
  {
    List<LocalizableMessage> errorMsgs = new ArrayList<>();

    // Check the Global Administrator UID
    String uid = qs.getFieldStringValue(FieldName.GLOBAL_ADMINISTRATOR_UID);

    if (uid == null || uid.trim().length() == 0)
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
    String pwd2 = qs.getFieldStringValue(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM);
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
      errorMsgs.add(INFO_PWD_TOO_SHORT.get(MIN_DIRECTORY_MANAGER_PWD));
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD, true);
      if (pwd2 == null || pwd2.length() < MIN_DIRECTORY_MANAGER_PWD)
      {
        qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM, true);
      }
      pwdValid = false;
    }

    if (pwdValid)
    {
      getUserData().setGlobalAdministratorPassword(pwd1);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD, false);
      qs.displayFieldInvalid(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM, false);
    }

    if (!errorMsgs.isEmpty())
    {
      throw new UserDataException(Step.CREATE_GLOBAL_ADMINISTRATOR, getMessageFromCollection(errorMsgs, "\n"));
    }
  }

  /**
   * Validate the data provided by the user in the replicate suffixes options
   * panel and update the UserInstallData object according to that content.
   *
   * @throws UserDataException
   *           if the data provided by the user is not valid.
   */
  @SuppressWarnings("unchecked")
  private void updateUserDataForSuffixesOptionsPanel(QuickSetup qs) throws UserDataException
  {
    List<LocalizableMessage> errorMsgs = new ArrayList<>();
    if (qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE_OPTIONS) ==
        SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES)
    {
      Set<?> s = (Set<?>) qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE);
      if (s.isEmpty())
      {
        errorMsgs.add(INFO_NO_SUFFIXES_CHOSEN_TO_REPLICATE.get());
        qs.displayFieldInvalid(FieldName.SUFFIXES_TO_REPLICATE, true);
      }
      else
      {
        Set<SuffixDescriptor> chosen = new HashSet<>();
        for (Object o : s)
        {
          chosen.add((SuffixDescriptor) o);
        }
        qs.displayFieldInvalid(FieldName.SUFFIXES_TO_REPLICATE, false);
        Set<SuffixDescriptor> available = getUserData().getSuffixesToReplicateOptions().getAvailableSuffixes();
        Map<String, BackendTypeUIAdapter> suffixesBackendTypes =
            (Map<String, BackendTypeUIAdapter>) qs.getFieldValue(FieldName.SUFFIXES_TO_REPLICATE_BACKEND_TYPE);
        SuffixesToReplicateOptions options = new SuffixesToReplicateOptions(
            SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES, available, chosen, suffixesBackendTypes);
        getUserData().setSuffixesToReplicateOptions(options);
      }
      getUserData().setRemoteWithNoReplicationPort(getRemoteWithNoReplicationPort(getUserData()));
    }
    else
    {
      Set<SuffixDescriptor> available = getUserData().getSuffixesToReplicateOptions().getAvailableSuffixes();
      Set<SuffixDescriptor> chosen = getUserData().getSuffixesToReplicateOptions().getSuffixes();
      SuffixesToReplicateOptions options =
          new SuffixesToReplicateOptions(SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY, available, chosen);
      getUserData().setSuffixesToReplicateOptions(options);
    }

    if (!errorMsgs.isEmpty())
    {
      throw new UserDataException(Step.SUFFIXES_OPTIONS, getMessageFromCollection(errorMsgs, "\n"));
    }
  }

  /**
   * Validate the data provided by the user in the remote server replication
   * port panel and update the userData object according to that content.
   *
   * @throws UserDataException
   *           if the data provided by the user is not valid.
   */
  private void updateUserDataForRemoteReplicationPorts(QuickSetup qs) throws UserDataException
  {
    List<LocalizableMessage> errorMsgs = new ArrayList<>();
    Map<ServerDescriptor, AuthenticationData> servers = getUserData().getRemoteWithNoReplicationPort();
    Map<?, ?> hm = (Map<?, ?>) qs.getFieldValue(FieldName.REMOTE_REPLICATION_PORT);
    Map<?, ?> hmSecure = (Map<?, ?>) qs.getFieldValue(FieldName.REMOTE_REPLICATION_SECURE);
    for (ServerDescriptor server : servers.keySet())
    {
      String hostName = server.getHostName();
      boolean secureReplication = (Boolean) hmSecure.get(server.getId());
      String sPort = (String) hm.get(server.getId());
      try
      {
        int replicationPort = Integer.parseInt(sPort);
        if (replicationPort < MIN_PORT_VALUE || replicationPort > MAX_PORT_VALUE)
        {
          errorMsgs.add(INFO_INVALID_REMOTE_REPLICATION_PORT_VALUE_RANGE.get(getHostPort(server), MIN_PORT_VALUE,
              MAX_PORT_VALUE));
        }
        if (hostName.equalsIgnoreCase(getUserData().getHostName()))
        {
          int securePort = -1;
          if (getUserData().getSecurityOptions().getEnableSSL())
          {
            securePort = getUserData().getSecurityOptions().getSslPort();
          }
          if (replicationPort == getUserData().getServerPort() || replicationPort == getUserData().getServerJMXPort()
              || replicationPort == getUserData().getReplicationOptions().getReplicationPort()
              || replicationPort == securePort)
          {
            errorMsgs.add(INFO_REMOTE_REPLICATION_PORT_ALREADY_CHOSEN_FOR_OTHER_PROTOCOL.get(getHostPort(server)));
          }
        }
        AuthenticationData authData = new AuthenticationData();
        authData.setPort(replicationPort);
        authData.setUseSecureConnection(secureReplication);
        servers.put(server, authData);
      }
      catch (NumberFormatException nfe)
      {
        errorMsgs.add(INFO_INVALID_REMOTE_REPLICATION_PORT_VALUE_RANGE.get(hostName, MIN_PORT_VALUE, MAX_PORT_VALUE));
      }
    }

    if (!errorMsgs.isEmpty())
    {
      qs.displayFieldInvalid(FieldName.REMOTE_REPLICATION_PORT, true);
      throw new UserDataException(Step.REMOTE_REPLICATION_PORTS, getMessageFromCollection(errorMsgs, "\n"));
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
   * @throws UserDataException
   *           if the data provided by the user is not valid.
   */
  @SuppressWarnings("unchecked")
  private void updateUserDataForNewSuffixOptionsPanel(final QuickSetup ui) throws UserDataException
  {
    final List<LocalizableMessage> errorMsgs = new ArrayList<>();
    // Singleton list with the provided baseDN (if exists and valid)
    List<String> baseDn = new LinkedList<>();
    boolean validBaseDn = checkProvidedBaseDn(ui, baseDn, errorMsgs);
    final NewSuffixOptions dataOptions = checkImportData(ui, baseDn, validBaseDn, errorMsgs);

    if (dataOptions != null)
    {
      getUserData().setBackendType((ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>)
          ui.getFieldValue(FieldName.BACKEND_TYPE));
      getUserData().setNewSuffixOptions(dataOptions);
    }

    if (!errorMsgs.isEmpty())
    {
      throw new UserDataException(Step.NEW_SUFFIX_OPTIONS,
          getMessageFromCollection(errorMsgs, Constants.LINE_SEPARATOR));
    }
  }

  private NewSuffixOptions checkImportData(final QuickSetup ui, final List<String> baseDn, final boolean validBaseDn,
      final List<LocalizableMessage> errorMsgs)
  {
    if (baseDn.isEmpty())
    {
      return NewSuffixOptions.createEmpty(baseDn);
    }

    final NewSuffixOptions.Type type = (NewSuffixOptions.Type) ui.getFieldValue(FieldName.DATA_OPTIONS);
    switch (type)
    {
    case IMPORT_FROM_LDIF_FILE:
      return checkImportLDIFFile(ui, baseDn, validBaseDn, errorMsgs);

    case IMPORT_AUTOMATICALLY_GENERATED_DATA:
      return checkImportGeneratedData(ui, baseDn, validBaseDn, errorMsgs);

    default:
      if (validBaseDn)
      {
        return type == NewSuffixOptions.Type.CREATE_BASE_ENTRY ? NewSuffixOptions.createBaseEntry(baseDn)
            : NewSuffixOptions.createEmpty(baseDn);
      }
    }

    return null;
  }

  private NewSuffixOptions checkImportGeneratedData(final QuickSetup ui, final List<String> baseDn,
      final boolean validBaseDn, final List<LocalizableMessage> errorMsgs)
  {
    boolean fieldIsValid = true;
    final List<LocalizableMessage> localErrorMsgs = new LinkedList<>();
    final String nEntries = ui.getFieldStringValue(FieldName.NUMBER_ENTRIES);
    if (nEntries == null || "".equals(nEntries.trim()))
    {
      localErrorMsgs.add(INFO_NO_NUMBER_ENTRIES.get());
      fieldIsValid = false;
    }
    else
    {
      boolean nEntriesValid = false;
      try
      {
        int n = Integer.parseInt(nEntries);
        nEntriesValid = n >= MIN_NUMBER_ENTRIES && n <= MAX_NUMBER_ENTRIES;
      }
      catch (NumberFormatException nfe)
      {
        /* do nothing */
      }

      if (!nEntriesValid)
      {
        localErrorMsgs.add(INFO_INVALID_NUMBER_ENTRIES_RANGE.get(MIN_NUMBER_ENTRIES, MAX_NUMBER_ENTRIES));
        fieldIsValid = false;
      }
    }

    ui.displayFieldInvalid(FieldName.NUMBER_ENTRIES, !fieldIsValid);
    if (validBaseDn && localErrorMsgs.isEmpty())
    {
      return NewSuffixOptions.createAutomaticallyGenerated(baseDn, Integer.parseInt(nEntries));
    }
    errorMsgs.addAll(localErrorMsgs);

    return null;
  }

  private NewSuffixOptions checkImportLDIFFile(final QuickSetup ui, final List<String> baseDn,
      final boolean validBaseDn, final List<LocalizableMessage> errorMsgs)
  {
    final boolean fieldIsValid = false;
    final String ldifPath = ui.getFieldStringValue(FieldName.LDIF_PATH);
    if (ldifPath == null || ldifPath.trim().isEmpty())
    {
      errorMsgs.add(INFO_NO_LDIF_PATH.get());
    }
    else if (!fileExists(ldifPath))
    {
      errorMsgs.add(INFO_LDIF_FILE_DOES_NOT_EXIST.get());
    }
    else if (validBaseDn)
    {
      return NewSuffixOptions.createImportFromLDIF(baseDn, Collections.singletonList(ldifPath), null, null);
    }
    ui.displayFieldInvalid(FieldName.LDIF_PATH, !fieldIsValid);

    return null;
  }

  private boolean checkProvidedBaseDn(final QuickSetup ui, final List<String> baseDn,
      final List<LocalizableMessage> errorMsgs)
  {
    boolean validBaseDn = true;
    String dn = ui.getFieldStringValue(FieldName.DIRECTORY_BASE_DN);
    if (dn == null || dn.trim().length() == 0)
    {
      // Do nothing, the user does not want to provide a base DN.
      dn = "";
    }
    else if (!isDN(dn))
    {
      validBaseDn = false;
      errorMsgs.add(INFO_NOT_A_BASE_DN.get());
    }
    else if (isConfigurationDn(dn))
    {
      validBaseDn = false;
      errorMsgs.add(INFO_BASE_DN_IS_CONFIGURATION_DN.get());
    }
    else
    {
      baseDn.add(dn);
    }
    ui.displayFieldInvalid(FieldName.DIRECTORY_BASE_DN, !validBaseDn);

    return validBaseDn;
  }

  /**
   * Update the userData object according to the content of the runtime options
   * panel.
   */
  private void updateUserDataForRuntimeOptionsPanel(QuickSetup qs)
  {
    getUserData().setJavaArguments(UserData.SERVER_SCRIPT_NAME,
        (JavaArguments) qs.getFieldValue(FieldName.SERVER_JAVA_ARGUMENTS));
    getUserData().setJavaArguments(UserData.IMPORT_SCRIPT_NAME,
        (JavaArguments) qs.getFieldValue(FieldName.IMPORT_JAVA_ARGUMENTS));
  }

  /** Update the userData object according to the content of the review panel. */
  private void updateUserDataForReviewPanel(QuickSetup qs)
  {
    Boolean b = (Boolean) qs.getFieldValue(FieldName.SERVER_START_INSTALLER);
    getUserData().setStartServer(b);
    b = (Boolean) qs.getFieldValue(FieldName.ENABLE_WINDOWS_SERVICE);
    getUserData().setEnableWindowsService(b);
  }

  /**
   * Returns the number of free disk space in bytes required to install Open DS
   * For the moment we just return 20 Megabytes. TODO we might want to have
   * something dynamic to calculate the required free disk space for the
   * installation.
   *
   * @return the number of free disk space required to install Open DS.
   */
  private long getRequiredInstallSpace()
  {
    return 20 * 1024 * 1024;
  }

  /** Update the UserInstallData with the contents we discover in the ADS. */
  private Set<TopologyCacheException> updateUserDataWithSuffixesInADS(ADSContext adsContext,
      ApplicationTrustManager trustManager) throws TopologyCacheException
  {
    Set<TopologyCacheException> exceptions = new HashSet<>();
    SuffixesToReplicateOptions suf = getUserData().getSuffixesToReplicateOptions();
    SuffixesToReplicateOptions.Type type;

    if (suf == null || suf.getType() == SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE)
    {
      type = SuffixesToReplicateOptions.Type.NO_SUFFIX_TO_REPLICATE;
    }
    else
    {
      type = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
    }
    lastLoadedCache = new TopologyCache(adsContext, trustManager, getConnectTimeout());
    LinkedHashSet<PreferredConnection> cnx = new LinkedHashSet<>();
    cnx.add(PreferredConnection.getPreferredConnection(adsContext.getDirContext()));
    // We cannot use getPreferredConnections since the user data has not been
    // updated yet.
    lastLoadedCache.setPreferredConnections(cnx);
    lastLoadedCache.reloadTopology();
    Set<SuffixDescriptor> suffixes = lastLoadedCache.getSuffixes();
    Set<SuffixDescriptor> moreSuffixes = null;
    if (suf != null)
    {
      moreSuffixes = suf.getSuffixes();
    }
    getUserData().setSuffixesToReplicateOptions(new SuffixesToReplicateOptions(type, suffixes, moreSuffixes));

    /*
     * Analyze if we had any exception while loading servers. For the moment
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
  private void updateUserDataWithSuffixesInServer(InitialLdapContext ctx) throws NamingException
  {
    SuffixesToReplicateOptions suf = getUserData().getSuffixesToReplicateOptions();
    SuffixesToReplicateOptions.Type type;
    Set<SuffixDescriptor> suffixes = new HashSet<>();
    if (suf != null)
    {
      type = suf.getType();
    }
    else
    {
      type = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
    }

    ServerDescriptor s = createStandalone(ctx, new TopologyCacheFilter());
    Set<ReplicaDescriptor> replicas = s.getReplicas();
    for (ReplicaDescriptor replica : replicas)
    {
      suffixes.add(replica.getSuffix());
    }
    Set<SuffixDescriptor> moreSuffixes = null;
    if (suf != null)
    {
      moreSuffixes = suf.getSuffixes();
    }
    getUserData().setSuffixesToReplicateOptions(new SuffixesToReplicateOptions(type, suffixes, moreSuffixes));
  }

  /**
   * Returns the keystore path to be used for generating a self-signed
   * certificate.
   *
   * @return the keystore path to be used for generating a self-signed
   *         certificate.
   */
  protected String getSelfSignedKeystorePath()
  {
    return getPath2("keystore");
  }

  /**
   * Returns the trustmanager path to be used for generating a self-signed
   * certificate.
   *
   * @return the trustmanager path to be used for generating a self-signed
   *         certificate.
   */
  private String getTrustManagerPath()
  {
    return getPath2("truststore");
  }

  /**
   * Returns the path of the self-signed that we export to be able to create a
   * truststore.
   *
   * @return the path of the self-signed that is exported.
   */
  private String getTemporaryCertificatePath()
  {
    return getPath2("server-cert.txt");
  }

  /**
   * Returns the path to be used to store the password of the keystore.
   *
   * @return the path to be used to store the password of the keystore.
   */
  private String getKeystorePinPath()
  {
    return getPath2("keystore.pin");
  }

  private String getPath2(String relativePath)
  {
    String parentFile = getPath(getInstancePath(), Installation.CONFIG_PATH_RELATIVE);
    return getPath(parentFile, relativePath);
  }

  /**
   * Returns the validity period to be used to generate the self-signed
   * certificate.
   *
   * @return the validity period to be used to generate the self-signed
   *         certificate.
   */
  private int getSelfSignedCertificateValidity()
  {
    return 20 * 365;
  }

  /**
   * Returns the Subject DN to be used to generate the self-signed certificate.
   *
   * @return the Subject DN to be used to generate the self-signed certificate.
   */
  private String getSelfSignedCertificateSubjectDN()
  {
    return "cn=" + Rdn.escapeValue(getUserData().getHostName()) + ",O=OpenDJ Self-Signed Certificate";
  }

  /**
   * Returns the self-signed certificate password used for this session. This
   * method calls <code>createSelfSignedCertificatePwd()</code> the first time
   * this method is called.
   *
   * @return the self-signed certificate password used for this session.
   */
  protected String getSelfSignedCertificatePwd()
  {
    if (selfSignedCertPw == null)
    {
      selfSignedCertPw = SetupUtils.createSelfSignedCertificatePwd();
    }
    return new String(selfSignedCertPw);
  }

  private Map<ServerDescriptor, AuthenticationData> getRemoteWithNoReplicationPort(UserData userData)
  {
    Map<ServerDescriptor, AuthenticationData> servers = new HashMap<>();
    Set<SuffixDescriptor> suffixes = userData.getSuffixesToReplicateOptions().getSuffixes();
    for (SuffixDescriptor suffix : suffixes)
    {
      for (ReplicaDescriptor replica : suffix.getReplicas())
      {
        ServerDescriptor server = replica.getServer();
        Object v = server.getServerProperties().get(IS_REPLICATION_SERVER);
        if (!Boolean.TRUE.equals(v))
        {
          AuthenticationData authData = new AuthenticationData();
          authData.setPort(Constants.DEFAULT_REPLICATION_PORT);
          authData.setUseSecureConnection(false);
          servers.put(server, authData);
        }
      }
    }
    return servers;
  }

  private InitialLdapContext createLocalContext() throws NamingException
  {
    String ldapUrl =
        "ldaps://" + getHostNameForLdapUrl(getUserData().getHostName()) + ":" + getUserData().getAdminConnectorPort();
    String dn = getUserData().getDirectoryManagerDn();
    String pwd = getUserData().getDirectoryManagerPwd();
    return createLdapsContext(ldapUrl, dn, pwd, getConnectTimeout(), null, null, null);
  }

  /**
   * Gets an InitialLdapContext based on the information that appears on the
   * provided ServerDescriptor.
   *
   * @param server
   *          the object describing the server.
   * @param trustManager
   *          the trust manager to be used to establish the connection.
   * @param cnx
   *          the list of preferred LDAP URLs to be used to connect to the
   *          server.
   * @return the InitialLdapContext to the remote server.
   * @throws ApplicationException
   *           if something goes wrong.
   */
  private InitialLdapContext getRemoteConnection(ServerDescriptor server, ApplicationTrustManager trustManager,
      Set<PreferredConnection> cnx) throws ApplicationException
  {
    Map<ADSContext.ServerProperty, Object> adsProperties;
    AuthenticationData auth = getUserData().getReplicationOptions().getAuthenticationData();
    if (!server.isRegistered())
    {
      /*
       * Create adsProperties to be able to use the class ServerLoader to get
       * the connection. Just update the connection parameters with what the
       * user chose in the Topology Options panel (i.e. even if SSL is enabled
       * on the remote server, use standard LDAP to connect to the server if the
       * user specified the LDAP port: this avoids having an issue with the
       * certificate if it has not been accepted previously by the user).
       */
      adsProperties = new HashMap<>();
      adsProperties.put(ADSContext.ServerProperty.HOST_NAME, server.getHostName());
      if (auth.useSecureConnection())
      {
        adsProperties.put(ADSContext.ServerProperty.LDAPS_PORT, String.valueOf(auth.getPort()));
        adsProperties.put(ADSContext.ServerProperty.LDAPS_ENABLED, "true");
      }
      else
      {
        adsProperties.put(ADSContext.ServerProperty.LDAP_PORT, String.valueOf(auth.getPort()));
        adsProperties.put(ADSContext.ServerProperty.LDAP_ENABLED, "true");
      }
      server.setAdsProperties(adsProperties);
    }
    return getRemoteConnection(server, auth.getDn(), auth.getPwd(), trustManager, getConnectTimeout(), cnx);
  }

  /**
   * Initializes a suffix with the contents of a replica that has a given
   * replication id.
   *
   * @param ctx
   *          the connection to the server whose suffix we want to initialize.
   * @param replicaId
   *          the replication ID of the replica we want to use to initialize the
   *          contents of the suffix.
   * @param suffixDn
   *          the dn of the suffix.
   * @param displayProgress
   *          whether we want to display progress or not.
   * @param sourceServerDisplay
   *          the string to be used to represent the server that contains the
   *          data that will be used to initialize the suffix.
   * @throws ApplicationException
   *           if an unexpected error occurs.
   * @throws PeerNotFoundException
   *           if the replication mechanism cannot find a peer.
   */
  public void initializeSuffix(InitialLdapContext ctx, int replicaId, String suffixDn, boolean displayProgress,
      String sourceServerDisplay) throws ApplicationException, PeerNotFoundException
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
    attrs.put("ds-task-initialize-replica-server-id", String.valueOf(replicaId));
    while (!taskCreated)
    {
      checkAbort();
      String id = "quicksetup-initialize" + i;
      dn = "ds-task-id=" + id + ",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        logger.info(LocalizableMessage.raw("created task entry: " + attrs));
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
        logger.warn(LocalizableMessage.raw("A task with dn: " + dn + " already existed."));
      }
      catch (NamingException ne)
      {
        logger.error(LocalizableMessage.raw("Error creating task " + attrs, ne));
        throw new ApplicationException(ReturnCode.APPLICATION_ERROR, getThrowableMsg(
            INFO_ERROR_LAUNCHING_INITIALIZATION.get(sourceServerDisplay), ne), ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(new String[] { "ds-task-unprocessed-entry-count",
      "ds-task-processed-entry-count", "ds-task-log-message", "ds-task-state" });
    LocalizableMessage lastDisplayedMsg = null;
    String lastLogMsg = null;
    long lastTimeMsgDisplayed = -1;
    long lastTimeMsgLogged = -1;
    long totalEntries = 0;
    while (!isOver)
    {
      if (canceled)
      {
        // TODO: we should try to cleanly abort the initialize.  As we have
        // aborted the install, the server will be stopped and the remote
        // server will receive a connect error.
        checkAbort();
      }
      StaticUtils.sleep(500);
      if (canceled)
      {
        // TODO: we should try to cleanly abort the initialize.  As we have
        // aborted the install, the server will be stopped and the remote
        // server will receive a connect error.
        checkAbort();
      }
      try
      {
        NamingEnumeration<SearchResult> res = ctx.search(dn, filter, searchControls);
        SearchResult sr = null;
        try
        {
          while (res.hasMore())
          {
            sr = res.next();
          }
        }
        finally
        {
          res.close();
        }
        // Get the number of entries that have been handled and
        // a percentage...
        LocalizableMessage msg;
        String sProcessed = getFirstValue(sr, "ds-task-processed-entry-count");
        String sUnprocessed = getFirstValue(sr, "ds-task-unprocessed-entry-count");
        long processed = -1;
        long unprocessed = -1;
        if (sProcessed != null)
        {
          processed = Integer.parseInt(sProcessed);
        }
        if (sUnprocessed != null)
        {
          unprocessed = Integer.parseInt(sUnprocessed);
        }
        totalEntries = Math.max(totalEntries, processed + unprocessed);

        if (processed != -1 && unprocessed != -1)
        {
          if (processed + unprocessed > 0)
          {
            long perc = (100 * processed) / (processed + unprocessed);
            msg = INFO_INITIALIZE_PROGRESS_WITH_PERCENTAGE.get(sProcessed, perc);
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
          if (currentTime - minRefreshPeriod > lastTimeMsgLogged)
          {
            lastTimeMsgLogged = currentTime;
            logger.info(LocalizableMessage.raw("Progress msg: " + msg));
          }
          if (displayProgress && currentTime - minRefreshPeriod > lastTimeMsgDisplayed && !msg.equals(lastDisplayedMsg))
          {
            notifyListeners(getFormattedProgress(msg));
            lastDisplayedMsg = msg;
            notifyListeners(getLineBreak());
            lastTimeMsgDisplayed = currentTime;
          }
        }

        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null && !logMsg.equals(lastLogMsg))
        {
          logger.info(LocalizableMessage.raw(logMsg));
          lastLogMsg = logMsg;
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          LocalizableMessage errorMsg;
          logger.info(LocalizableMessage.raw("Last task entry: " + sr));
          if (displayProgress && msg != null && !msg.equals(lastDisplayedMsg))
          {
            notifyListeners(getFormattedProgress(msg));
            lastDisplayedMsg = msg;
            notifyListeners(getLineBreak());
          }

          if (lastLogMsg != null)
          {
            errorMsg =
                INFO_ERROR_DURING_INITIALIZATION_LOG.get(sourceServerDisplay, lastLogMsg, state, sourceServerDisplay);
          }
          else
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(sourceServerDisplay, state, sourceServerDisplay);
          }

          logger.warn(LocalizableMessage.raw("Processed errorMsg: " + errorMsg));
          if (helper.isCompletedWithErrors(state))
          {
            if (displayProgress)
            {
              notifyListeners(getFormattedWarning(errorMsg));
            }
          }
          else if (!helper.isSuccessful(state) || helper.isStoppedByError(state))
          {
            ApplicationException ae = new ApplicationException(ReturnCode.APPLICATION_ERROR, errorMsg, null);
            if (lastLogMsg == null || helper.isPeersNotFoundError(lastLogMsg))
            {
              logger.warn(LocalizableMessage.raw("Throwing peer not found error.  " + "Last Log Msg: " + lastLogMsg));
              // Assume that this is a peer not found error.
              throw new PeerNotFoundException(errorMsg);
            }
            else
            {
              logger.error(LocalizableMessage.raw("Throwing ApplicationException."));
              throw ae;
            }
          }
          else if (displayProgress)
          {
            logger.info(LocalizableMessage.raw("Initialization completed successfully."));
            notifyListeners(getFormattedProgress(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get()));
            notifyListeners(getLineBreak());
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
        logger.info(LocalizableMessage.raw("Initialization entry not found."));
        if (displayProgress)
        {
          notifyListeners(getFormattedProgress(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get()));
          notifyListeners(getLineBreak());
        }
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(ReturnCode.APPLICATION_ERROR, getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION
            .get(sourceServerDisplay), ne), ne);
      }
    }
    resetGenerationId(ctx, suffixDn, sourceServerDisplay);
  }

  /**
   * Returns the configuration file path to be used when invoking the
   * command-lines.
   *
   * @return the configuration file path to be used when invoking the
   *         command-lines.
   */
  private String getConfigurationFile()
  {
    return getPath(getInstallation().getCurrentConfigurationFile());
  }

  /**
   * Returns the configuration class name to be used when invoking the
   * command-lines.
   *
   * @return the configuration class name to be used when invoking the
   *         command-lines.
   */
  private String getConfigurationClassName()
  {
    return DEFAULT_CONFIG_CLASS_NAME;
  }

  private String getLocalReplicationServer()
  {
    return getUserData().getHostName() + ":" + getUserData().getReplicationOptions().getReplicationPort();
  }

  private String getLocalHostPort()
  {
    return getUserData().getHostName() + ":" + getUserData().getServerPort();
  }

  private void resetGenerationId(InitialLdapContext ctx, String suffixDn, String sourceServerDisplay)
      throws ApplicationException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-reset-generation-id");
    attrs.put(oc);
    attrs.put("ds-task-class-name", "org.opends.server.tasks.SetGenerationIdTask");
    attrs.put("ds-task-reset-generation-id-domain-base-dn", suffixDn);
    while (!taskCreated)
    {
      checkAbort();
      String id = "quicksetup-reset-generation-id-" + i;
      dn = "ds-task-id=" + id + ",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        logger.info(LocalizableMessage.raw("created task entry: " + attrs));
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
      }
      catch (NamingException ne)
      {
        logger.error(LocalizableMessage.raw("Error creating task " + attrs, ne));
        throw new ApplicationException(ReturnCode.APPLICATION_ERROR, getThrowableMsg(
            INFO_ERROR_LAUNCHING_INITIALIZATION.get(sourceServerDisplay), ne), ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(new String[] { "ds-task-log-message", "ds-task-state" });
    String lastLogMsg = null;
    while (!isOver)
    {
      StaticUtils.sleep(500);
      try
      {
        NamingEnumeration<SearchResult> res = ctx.search(dn, filter, searchControls);
        SearchResult sr = null;
        try
        {
          while (res.hasMore())
          {
            sr = res.next();
          }
        }
        finally
        {
          res.close();
        }
        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null && !logMsg.equals(lastLogMsg))
        {
          logger.info(LocalizableMessage.raw(logMsg));
          lastLogMsg = logMsg;
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          LocalizableMessage errorMsg = lastLogMsg != null ?
              INFO_ERROR_DURING_INITIALIZATION_LOG.get(sourceServerDisplay, lastLogMsg, state, sourceServerDisplay)
            : INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(sourceServerDisplay, state, sourceServerDisplay);

          if (helper.isCompletedWithErrors(state))
          {
            logger.warn(LocalizableMessage.raw("Completed with error: " + errorMsg));
            notifyListeners(getFormattedWarning(errorMsg));
          }
          else if (!helper.isSuccessful(state) || helper.isStoppedByError(state))
          {
            logger.warn(LocalizableMessage.raw("Error: " + errorMsg));
            throw new ApplicationException(ReturnCode.APPLICATION_ERROR, errorMsg, null);
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(ReturnCode.APPLICATION_ERROR,
            getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION.get(sourceServerDisplay), ne), ne);
      }
    }
  }

  /**
   * Invokes a long operation in a separate thread and checks whether the user
   * canceled the operation or not.
   *
   * @param thread
   *          the Thread that must be launched.
   * @throws ApplicationException
   *           if there was an error executing the task or if the user canceled
   *           the installer.
   */
  private void invokeLongOperation(InvokeThread thread) throws ApplicationException
  {
    try
    {
      thread.start();
      while (!thread.isOver() && thread.isAlive())
      {
        if (canceled)
        {
          // Try to abort the thread
          try
          {
            thread.abort();
          }
          catch (Throwable t)
          {
            logger.warn(LocalizableMessage.raw("Error cancelling thread: " + t, t));
          }
        }
        else if (thread.getException() != null)
        {
          throw thread.getException();
        }
        else
        {
          StaticUtils.sleep(100);
        }
      }
      if (thread.getException() != null)
      {
        throw thread.getException();
      }
      if (canceled)
      {
        checkAbort();
      }
    }
    catch (ApplicationException e)
    {
      logger.error(LocalizableMessage.raw("Error: " + e, e));
      throw e;
    }
    catch (Throwable t)
    {
      logger.error(LocalizableMessage.raw("Error: " + t, t));
      throw new ApplicationException(ReturnCode.BUG, getThrowableMsg(INFO_BUG_MSG.get(), t), t);
    }
  }

  /**
   * Returns the host port representation of the server to be used in progress
   * and error messages. It takes into account the fact the host and port
   * provided by the user in the replication options panel. NOTE: the code
   * assumes that the user data with the contents of the replication options has
   * already been updated.
   *
   * @param server
   *          the ServerDescriptor.
   * @return the host port string representation of the provided server.
   */
  protected String getHostPort(ServerDescriptor server)
  {
    String hostPort = null;

    for (PreferredConnection connection : getPreferredConnections())
    {
      String url = connection.getLDAPURL();
      if (url.equals(server.getLDAPURL()))
      {
        hostPort = server.getHostPort(false);
      }
      else if (url.equals(server.getLDAPsURL()))
      {
        hostPort = server.getHostPort(true);
      }
    }
    if (hostPort == null)
    {
      hostPort = server.getHostPort(true);
    }
    return hostPort;
  }

  @Override
  protected void applicationPrintStreamReceived(String message)
  {
    InstallerHelper helper = new InstallerHelper();
    String parsedMessage = helper.getImportProgressMessage(message);
    if (parsedMessage != null)
    {
      lastImportProgress = parsedMessage;
    }
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.
   *
   * @return the timeout to be used to connect in milliseconds. Returns
   *         {@code 0} if there is no timeout.
   */
  protected int getConnectTimeout()
  {
    return getUserData().getConnectTimeout();
  }

  /**
   * Copies the template instance files into the instance directory.
   *
   * @throws ApplicationException
   *           If an IO error occurred.
   */
  private void copyTemplateInstance() throws ApplicationException
  {
    FileManager fileManager = new FileManager();
    fileManager.synchronize(getInstallation().getTemplateDirectory(), getInstallation().getInstanceDirectory());
  }
}

/** Class used to be able to cancel long operations. */
abstract class InvokeThread extends Thread implements Runnable
{
  protected boolean isOver;
  protected ApplicationException ae;

  /**
   * Returns <CODE>true</CODE> if the thread is over and <CODE>false</CODE>
   * otherwise.
   *
   * @return <CODE>true</CODE> if the thread is over and <CODE>false</CODE>
   *         otherwise.
   */
  public boolean isOver()
  {
    return isOver;
  }

  /**
   * Returns the exception that was encountered running the thread.
   *
   * @return the exception that was encountered running the thread.
   */
  public ApplicationException getException()
  {
    return ae;
  }

  /** Runnable implementation. */
  @Override
  public abstract void run();

  /** Abort this thread. */
  public abstract void abort();
}
