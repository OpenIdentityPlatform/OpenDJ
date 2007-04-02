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

import static org.opends.quicksetup.Step.WELCOME;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;
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

  /**
   * An static String that contains the class name of ConfigFileHandler.
   */
  protected static final String CONFIG_CLASS_NAME =
      "org.opends.server.extensions.ConfigFileHandler";

  /**
   * {@inheritDoc}
   */
  public void forceToDisplay() {
    forceToDisplaySetup = true;
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
      p = new FramePanel(dlg.getStepsPanel(),
              dlg.getCurrentStepPanel(),
              dlg.getButtonsPanel());
    }
    return p;
  }

  /**
   * {@inheritDoc}
   */
  public Set<Step> getWizardSteps() {
    return EnumSet.of(WELCOME,
            Step.SERVER_SETTINGS,
            Step.DATA_OPTIONS,
            Step.REVIEW,
            Step.PROGRESS);
  }

  /**
   * {@inheritDoc}
   */
  public QuickSetupStepPanel createWizardStepPanel(Step step) {
    QuickSetupStepPanel p = null;
    switch (step) {
      case WELCOME:
        p = new InstallWelcomePanel();
        break;
      case SERVER_SETTINGS:
        p = new ServerSettingsPanel(getUserData());
        break;
      case DATA_OPTIONS:
        p = new DataOptionsPanel(getUserData());
        break;
      case REVIEW:
        p = new ReviewPanel(getUserData());
        break;
      case PROGRESS:
        p = new ProgressPanel();
        break;
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
                                      Step step) {
    if (!installStatus.isInstalled() || forceToDisplaySetup) {

      // Set the default button for the frame
      switch (step) {
        case REVIEW:
          dlg.setDefaultButton(ButtonName.FINISH);
          break;

        case PROGRESS:
          dlg.setDefaultButton(ButtonName.CLOSE);
          break;

        default:
          dlg.setDefaultButton(ButtonName.NEXT);
      }

      // Set the focus for the current step
      switch (step) {
        case WELCOME:
          dlg.setFocusOnButton(ButtonName.NEXT);
          break;

        case SERVER_SETTINGS:
          // The focus is set by the panel itself
          break;

        case DATA_OPTIONS:
          // The focus is set by the panel itself
          break;

        case REVIEW:
          dlg.setFocusOnButton(ButtonName.FINISH);
          break;

        case PROGRESS:
          dlg.setFocusOnButton(ButtonName.CLOSE);
          dlg.setButtonEnabled(ButtonName.CLOSE, false);
          break;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public ProgressStep getStatus()
  {
    return status;
  }

  /**
   * {@inheritDoc}
   */
  public Step getFirstWizardStep() {
    return WELCOME;
  }

  /**
   * {@inheritDoc}
   */
  public Step getNextWizardStep(Step step) {
    Step nextStep = null;
    if (step != null) {
      if (step.equals(Step.WELCOME)) {
        nextStep = Step.SERVER_SETTINGS;
      } else if (step.equals(Step.SERVER_SETTINGS)) {
        nextStep = Step.DATA_OPTIONS;
      } else if (step.equals(Step.DATA_OPTIONS)) {
        nextStep = Step.REVIEW;
      } else if (step.equals(Step.REVIEW)) {
        nextStep = Step.PROGRESS;
      }
    }
    return nextStep;
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
    argList.add(getConfigFilePath());
    argList.add("-p");
    argList.add(String.valueOf(getUserData().getServerPort()));
    argList.add("-j");
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
    argList.add(getConfigFilePath());

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
    argList.add(getConfigFilePath());
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
    argList.add(getConfigFilePath());
    argList.add("-n");
    argList.add(getBackendName());
    argList.add("-t");
    argList.add(templatePath.getAbsolutePath());
    argList.add("-S");
    argList.add("0");

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
            getMsg("error-import-automatically-generated"), null);
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
   * Returns the default backend name (the one that will be created).
   * @return the default backend name (the one that will be created).
   */
  protected String getBackendName()
  {
    return "userRoot";
  }

  /**
   * {@inheritDoc}
   */
  protected String getBinariesPath()
  {
    return Utils.getPath(getInstallationPath(),
        Utils.getBinariesRelativePath());
  }
}
