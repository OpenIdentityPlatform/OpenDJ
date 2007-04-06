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
import java.io.IOException;
import java.util.*;
import java.awt.event.WindowEvent;

import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.*;
import org.opends.server.util.SetupUtils;

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
public abstract class Installer extends Application {

  /* Indicates that we've detected that there is something installed */
  boolean forceToDisplaySetup = false;

  // Constants used to do checks
  private static final int MIN_DIRECTORY_MANAGER_PWD = 1;

  private static final int MIN_PORT_VALUE = 1;

  private static final int MAX_PORT_VALUE = 65535;

  private static final int MIN_NUMBER_ENTRIES = 1;

  private static final int MAX_NUMBER_ENTRIES = 10000;

  private List<WizardStep> lstSteps = new ArrayList<WizardStep>();

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
    lstSteps.add(DATA_OPTIONS);
    lstSteps.add(REVIEW);
    lstSteps.add(PROGRESS);
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
  public void previousClicked(WizardStep cStep) {
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
        p = new InstallWelcomePanel();
    } else if (step == SERVER_SETTINGS) {
        p = new ServerSettingsPanel(getUserData());
    } else if (step == DATA_OPTIONS) {
        p = new DataOptionsPanel(getUserData());
    } else if (step == REVIEW) {
        p = new ReviewPanel(getUserData());
    } else if (step == PROGRESS) {
        p = new ProgressPanel();
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
  public void previousClicked(WizardStep cStep, QuickSetup qs) {
    // do nothing;
  }

  /**
   * {@inheritDoc}
   */
  public String getFrameTitle() {
    return getMsg("frame-install-title");
  }

  /** Indicates the current progress step. */
  protected InstallProgressStep status =
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
    int i = lstSteps.indexOf(step);
    if (i != -1 && i + 1 < lstSteps.size()) {
      next = lstSteps.get(i + 1);
    }
    return next;
  }

  /**
   * {@inheritDoc}
   */
  public WizardStep getPreviousWizardStep(WizardStep step) {
    WizardStep prev = null;
    int i = lstSteps.indexOf(step);
    if (i != -1 && i > 0) {
      prev = lstSteps.get(i - 1);
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
   * @throws QuickSetupException if an error occurs.
   */
  protected File createTemplateFile() throws QuickSetupException {
    try
    {
      return SetupUtils.createTemplateFile(
                  getUserData().getDataOptions().getBaseDn(),
                  getUserData().getDataOptions().getNumberEntries());
    }
    catch (IOException ioe)
    {
      String failedMsg = getThrowableMsg("error-creating-temp-file", null, ioe);
      throw new QuickSetupException(QuickSetupException.Type.FILE_SYSTEM_ERROR,
          failedMsg, ioe);
    }
  }

  /**
   * This methods configures the server based on the contents of the UserData
   * object provided in the constructor.
   * @throws QuickSetupException if something goes wrong.
   */
  protected void configureServer() throws QuickSetupException {
    notifyListeners(getFormattedWithPoints(getMsg("progress-configuring")));

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-c");
    argList.add(Utils.getPath(getInstallation().getCurrentConfigurationFile()));
    argList.add("-p");
    argList.add(String.valueOf(getUserData().getServerPort()));
    argList.add("-x");
    argList.add(String.valueOf(getUserData().getServerJMXPort()));

    argList.add("-D");
    argList.add(getUserData().getDirectoryManagerDn());

    argList.add("-w");
    argList.add(getUserData().getDirectoryManagerPwd());

    argList.add("-b");
    argList.add(getUserData().getDataOptions().getBaseDn());

    String[] args = new String[argList.size()];
    argList.toArray(args);
    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeConfigureServer(args);

      if (result != 0)
      {
        throw new QuickSetupException(
            QuickSetupException.Type.CONFIGURATION_ERROR,
            getMsg("error-configuring"), null);
      }
    } catch (Throwable t)
    {
      throw new QuickSetupException(
          QuickSetupException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-configuring", null, t), t);
    }
  }

  /**
   * This methods creates the base entry for the suffix based on the contents of
   * the UserData object provided in the constructor.
   * @throws QuickSetupException if something goes wrong.
   */
  protected void createBaseEntry() throws QuickSetupException {
    String[] arg =
      { getUserData().getDataOptions().getBaseDn() };
    notifyListeners(getFormattedWithPoints(
        getMsg("progress-creating-base-entry", arg)));

    InstallerHelper helper = new InstallerHelper();
    String baseDn = getUserData().getDataOptions().getBaseDn();
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
        throw new QuickSetupException(
            QuickSetupException.Type.CONFIGURATION_ERROR,
            getMsg("error-creating-base-entry"), null);
      }
    } catch (Throwable t)
    {
      throw new QuickSetupException(
          QuickSetupException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-creating-base-entry", null, t), t);
    }

    notifyListeners(getFormattedDone());
  }

  /**
   * This methods imports the contents of an LDIF file based on the contents of
   * the UserData object provided in the constructor.
   * @throws QuickSetupException if something goes wrong.
   */
  protected void importLDIF() throws QuickSetupException {
    String[] arg =
      { getUserData().getDataOptions().getLDIFPath() };
    notifyListeners(getFormattedProgress(getMsg("progress-importing-ldif", arg))
        + formatter.getLineBreak());

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(Utils.getPath(getInstallation().getCurrentConfigurationFile()));
    argList.add("-n");
    argList.add(getBackendName());
    argList.add("-l");
    argList.add(getUserData().getDataOptions().getLDIFPath());

    String[] args = new String[argList.size()];
    argList.toArray(args);

    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeImportLDIF(args);

      if (result != 0)
      {
        throw new QuickSetupException(
            QuickSetupException.Type.CONFIGURATION_ERROR,
            getMsg("error-importing-ldif"), null);
      }
    } catch (Throwable t)
    {
      throw new QuickSetupException(
          QuickSetupException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-importing-ldif", null, t), t);
    }
  }

  /**
   * This methods imports automatically generated data based on the contents
   * of the UserData object provided in the constructor.
   * @throws QuickSetupException if something goes wrong.
   */
  protected void importAutomaticallyGenerated() throws QuickSetupException {
    File templatePath = createTemplateFile();
    int nEntries = getUserData().getDataOptions().getNumberEntries();
    String[] arg =
      { String.valueOf(nEntries) };
    notifyListeners(getFormattedProgress(getMsg(
        "progress-import-automatically-generated", arg))
        + formatter.getLineBreak());

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
        throw new QuickSetupException(
            QuickSetupException.Type.CONFIGURATION_ERROR,
            getMsg("error-import-automatically-generated", msgArgs), null);
      }
    } catch (Throwable t)
    {
      throw new QuickSetupException(
          QuickSetupException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-import-automatically-generated", null, t), t);
    }
  }

  /**
   * This methods enables this server as a Windows service.
   * @throws QuickSetupException if something goes wrong.
   */
  protected void enableWindowsService() throws QuickSetupException {
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

    String cmd;
    if (Utils.isWindows())
    {
      cmd = Utils.getBinariesRelativePath()+File.separator+
      Utils.getWindowsStatusPanelFileName();
    }
    else
    {
      cmd = Utils.getBinariesRelativePath()+File.separator+
      Utils.getUnixStatusPanelFileName();
    }
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
  protected void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException
  {
    if (cStep == SERVER_SETTINGS) {
      updateUserDataForServerSettingsPanel(qs);
    } else if (cStep == DATA_OPTIONS) {
      updateUserDataForDataOptionsPanel(qs);
    } else if (cStep == REVIEW) {
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
   * Validate the data provided by the user in the server settings panel and
   * update the userData object according to that content.
   *
   * @throws UserDataException if the data provided by the user is not
   *           valid.
   *
   */
  private void updateUserDataForServerSettingsPanel(QuickSetup qs)
      throws UserDataException {
    ArrayList<String> errorMsgs = new ArrayList<String>();

    if (Utils.isWebStart())
    {
      // Check the server location
      String serverLocation = qs.getFieldStringValue(FieldName.SERVER_LOCATION);

      if ((serverLocation == null) || ("".equals(serverLocation.trim())))
      {
        errorMsgs.add(getMsg("empty-server-location"));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
      } else if (!Utils.parentDirectoryExists(serverLocation))
      {
        String[] arg =
          { serverLocation };
        errorMsgs.add(getMsg("parent-directory-does-not-exist", arg));
        qs.displayFieldInvalid(FieldName.SERVER_LOCATION, true);
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
    try
    {
      int port = Integer.parseInt(sPort);
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

    int defaultJMXPort = UserData.getDefaultJMXPort();
    if (defaultJMXPort != -1)
    {
      getUserData().setServerJMXPort(defaultJMXPort);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.SERVER_SETTINGS,
          Utils.getStringFromCollection(errorMsgs, "\n"));
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
  private void updateUserDataForDataOptionsPanel(QuickSetup qs)
      throws UserDataException {
    ArrayList<String> errorMsgs = new ArrayList<String>();

    DataOptions dataOptions = null;

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
    DataOptions.Type type =
        (DataOptions.Type) qs.getFieldValue(FieldName.DATA_OPTIONS);

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
        dataOptions = new DataOptions(type, baseDn, ldifPath);
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
        dataOptions = new DataOptions(type, baseDn, new Integer(nEntries));
      }
      break;

    default:
      qs.displayFieldInvalid(FieldName.LDIF_PATH, false);
      qs.displayFieldInvalid(FieldName.NUMBER_ENTRIES, false);
      if (validBaseDn)
      {
        dataOptions = new DataOptions(type, baseDn);
      }
    }

    if (dataOptions != null)
    {
      getUserData().setDataOptions(dataOptions);
    }

    if (errorMsgs.size() > 0)
    {
      throw new UserDataException(Step.DATA_OPTIONS,
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

}
