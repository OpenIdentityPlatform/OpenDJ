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

package org.opends.quicksetup;

import org.opends.quicksetup.event.ProgressNotifier;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.ui.QuickSetupDialog;
import org.opends.quicksetup.ui.QuickSetupStepPanel;

import javax.naming.NamingException;
import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.WindowEvent;

/**
 * This class represents an application that can be run in the context
 * of QuickSetup.  Examples of applications might be 'installer',
 * 'uninstaller' and 'upgrader'.
 */
public abstract class Application implements ProgressNotifier, Runnable {

  /**
   * The path to the Configuration LDIF file.
   */
  static protected final String CONFIG_PATH_RELATIVE =
      "config" + File.separator + "config.ldif";

  static private final Logger LOG =
          Logger.getLogger(Application.class.getName());

  /** The currently displayed wizard step. */
  private WizardStep displayedStep;

  /** Represents current install state. */
  protected CurrentInstallStatus installStatus;

  /**
   * Creates an application by instantiating the Application class
   * denoted by the System property
   * <code>org.opends.quicksetup.Application.class</code>.
   * @return Application object that was newly instantiated
   * @throws ApplicationException if there was a problem creating
   *         the new Application object
   */
  static public Application create()
          throws ApplicationException {
    Application app;
    String appClassName =
            System.getProperty("org.opends.quicksetup.Application.class");
    if (appClassName != null) {
      Class appClass = null;
      try {
        appClass = Class.forName(appClassName);
        app = (Application) appClass.newInstance();
      } catch (ClassNotFoundException e) {
        LOG.log(Level.INFO, "error creating quicksetup application", e);
        String msg = "Application class " + appClass + " not found";
        throw new ApplicationException(ApplicationException.Type.BUG, msg, e);
      } catch (IllegalAccessException e) {
        LOG.log(Level.INFO, "error creating quicksetup application", e);
        String msg = "Could not access class " + appClass;
        throw new ApplicationException(ApplicationException.Type.BUG, msg, e);
      } catch (InstantiationException e) {
        LOG.log(Level.INFO, "error creating quicksetup application", e);
        String msg = "Error instantiating class " + appClass;
        throw new ApplicationException(ApplicationException.Type.BUG, msg, e);
      } catch (ClassCastException e) {
        String msg = "The class indicated by the system property " +
                  "'org.opends.quicksetup.Application.class' must " +
                  " must be of type Application";
        throw new ApplicationException(ApplicationException.Type.BUG, msg, e);
      }
    } else {
      String msg = "System property 'org.opends.quicksetup.Application.class'" +
                " must specify class quicksetup application";
      throw new ApplicationException(ApplicationException.Type.BUG, msg, null);
    }
    return app;
  }

  private HashSet<ProgressUpdateListener> listeners =
      new HashSet<ProgressUpdateListener>();

  private UserData userData;

  /** Formats progress messages. */
  protected ProgressMessageFormatter formatter;

  /**
   * Constructs an instance of an application.  Subclasses
   * of this application must have a default constructor.
   */
  public Application() {
    this.displayedStep = getFirstWizardStep();
  }

  /**
   * Gets the frame title of the GUI application that will be used
   * in some operating systems.
   * @return internationalized String representing the frame title
   */
  abstract public String getFrameTitle();

  /**
   * Sets this instances user data.
   * @param userData UserData this application will use
   *        when executing
   */
  public void setUserData(UserData userData) {
    this.userData = userData;
  }

  /**
   * Creates a set of user data with default values.
   * @return UserData empty set of UserData
   */
  public UserData createUserData() {
    return new UserData();
  }

  /**
   * Adds a ProgressUpdateListener that will be notified of updates in
   * the install progress.
   * @param l the ProgressUpdateListener to be added.
   */
  public void addProgressUpdateListener(ProgressUpdateListener l)
  {
    listeners.add(l);
  }

  /**
   * Removes a ProgressUpdateListener.
   * @param l the ProgressUpdateListener to be removed.
   */
  public void removeProgressUpdateListener(ProgressUpdateListener l)
  {
    listeners.remove(l);
  }

  /**
   * Returns whether the installer has finished or not.
   * @return <CODE>true</CODE> if the install is finished or <CODE>false
   * </CODE> if not.
   */
  public boolean isFinished()
  {
    return getStatus().isLast();
  }

  /**
   * Returns the UserData object representing the parameters provided by
   * the user to do the installation.
   *
   * @return the UserData object representing the parameters provided
   * by the user to do the installation.
   */
  protected UserData getUserData()
  {
    if (userData == null) {
      userData = createUserData();
    }
    return userData;
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratioWhenCompleted the integer that specifies which percentage of
   * the whole installation has been completed.
   */
  public void notifyListenersDone(Integer ratioWhenCompleted) {
    notifyListeners(ratioWhenCompleted,
            getSummary(getStatus()),
            formatter.getFormattedDone() + formatter.getLineBreak());
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratio the integer that specifies which percentage of
   * the whole installation has been completed.
   * @param currentPhaseSummary the localized summary message for the
   * current installation progress in formatted form.
   * @param newLogDetail the new log messages that we have for the
   * installation in formatted form.
   */
  public void notifyListeners(Integer ratio, String currentPhaseSummary,
      String newLogDetail)
  {
    ProgressUpdateEvent ev =
        new ProgressUpdateEvent(getStatus(), ratio, currentPhaseSummary,
            newLogDetail);
    for (ProgressUpdateListener l : listeners)
    {
      l.progressUpdate(ev);
    }
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratio the integer that specifies which percentage of
   * the whole installation has been completed.
   * @param currentPhaseSummary the localized summary message for the
   * current installation progress in formatted form.
   */
  public void notifyListeners(Integer ratio, String currentPhaseSummary) {
    notifyListeners(ratio, getSummary(getStatus()),
        formatter.getFormattedWithPoints(currentPhaseSummary));
  }

  /**
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * @see org.opends.quicksetup.i18n.ResourceProvider#getMsg(String)
   * @param key the key in the properties file.
   * @return the value associated to the key in the properties file.
   * properties file.
   */
  protected String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  /**
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * For instance if we pass as key "mykey" and as arguments {"value1"} and
   * in the properties file we have:
   * mykey=value with argument {0}.
   *
   * This method will return "value with argument value1".
   * @see org.opends.quicksetup.i18n.ResourceProvider#getMsg(String, String[])
   * @param key the key in the properties file.
   * @param args the arguments to be passed to generate the resulting value.
   * @return the value associated to the key in the properties file.
   */
  protected String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  /**
   * Returns a ResourceProvider instance.
   * @return a ResourceProvider instance.
   */
  protected ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /**
   * Returns a localized message for a given properties key and throwable.
   * @param key the key of the message in the properties file.
   * @param t the throwable for which we want to get a message.
   * @return a localized message for a given properties key and throwable.
   */
  protected String getThrowableMsg(String key, Throwable t)
  {
    return getThrowableMsg(key, null, t);
  }

  /**
   * Returns a localized message for a given properties key and throwable.
   * @param key the key of the message in the properties file.
   * @param args the arguments of the message in the properties file.
   * @param t the throwable for which we want to get a message.
   *
   * @return a localized message for a given properties key and throwable.
   */
  protected String getThrowableMsg(String key, String[] args, Throwable t)
  {
    return Utils.getThrowableMsg(getI18n(), key, args, t);
  }

  /**
   * Sets the formatter this instance should use to used
   * to format progress messages.
   * @param formatter ProgressMessageFormatter for formatting
   * progress messages
   */
  public void setProgressMessageFormatter(ProgressMessageFormatter formatter) {
    this.formatter = formatter;
  }

  /**
   * Gets the formatter this instance is currently using.
   * @return the progress message formatter currently used by this
   * application
   */
  public ProgressMessageFormatter getProgressMessageFormatter() {
    return formatter;
  }

  /**
   * Returns the formatted representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  protected String getFormattedSummary(String text)
  {
    return formatter.getFormattedSummary(text);
  }

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  protected String getFormattedError(String text)
  {
    return formatter.getFormattedError(text, false);
  }

  /**
   * Returns the formatted representation of an warning for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an warning for the given text.
   */
  protected String getFormattedWarning(String text)
  {
    return formatter.getFormattedWarning(text, false);
  }

  /**
   * Returns the formatted representation of a success message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an success message for the given
   * text.
   */
  protected String getFormattedSuccess(String text)
  {
    return formatter.getFormattedSuccess(text);
  }

  /**
   * Returns the formatted representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log error message for the given
   * text.
   */
  protected String getFormattedLogError(String text)
  {
    return formatter.getFormattedLogError(text);
  }

  /**
   * Returns the formatted representation of a log message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log message for the given text.
   */
  protected String getFormattedLog(String text)
  {
    return formatter.getFormattedLog(text);
  }

  /**
   * Returns the formatted representation of the 'Done' text string.
   * @return the formatted representation of the 'Done' text string.
   */
  protected String getFormattedDone()
  {
    return formatter.getFormattedDone();
  }

  /**
   * Returns the formatted representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server .....'.
   * @param text the String to which add points.
   * @return the formatted representation of the '.....' text string.
   */
  protected String getFormattedWithPoints(String text)
  {
    return formatter.getFormattedWithPoints(text);
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  protected String getFormattedProgress(String text)
  {
    return formatter.getFormattedProgress(text);
  }

  /**
   * Returns the formatted representation of an error message for a given
   * exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param ex the exception.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error message for the given
   * exception.
   */
  protected String getFormattedError(Exception ex, boolean applyMargin)
  {
    return formatter.getFormattedError(ex, applyMargin);
  }

  /**
   * Returns the line break formatted.
   * @return the line break formatted.
   */
  protected String getLineBreak()
  {
    return formatter.getLineBreak();
  }

  /**
   * Returns the task separator formatted.
   * @return the task separator formatted.
   */
  protected String getTaskSeparator()
  {
    return formatter.getTaskSeparator();
  }

  /**
   * This method is called when a new log message has been received.  It will
   * notify the ProgressUpdateListeners of this fact.
   * @param newLogDetail the new log detail.
   */
  protected void notifyListeners(String newLogDetail)
  {
    Integer ratio = getRatio(getStatus());
    String currentPhaseSummary = getSummary(getStatus());
    notifyListeners(ratio, currentPhaseSummary, newLogDetail);
  }

  /**
   * This methods starts the server.
   * @throws org.opends.quicksetup.QuickSetupException if something goes wrong.
   */
  protected void startServer() throws QuickSetupException {
    notifyListeners(getFormattedProgress(getMsg("progress-starting")) +
        getLineBreak());

    ArrayList<String> argList = new ArrayList<String>();

    if (Utils.isWindows())
    {
      argList.add(Utils.getPath(getBinariesPath(),
              Utils.getWindowsStartFileName()));
    } else
    {
      argList.add(Utils.getPath(getBinariesPath(),
              Utils.getUnixStartFileName()));
    }

    String[] args = new String[argList.size()];
    argList.toArray(args);
    ProcessBuilder pb = new ProcessBuilder(args);
    Map<String, String> env = pb.environment();
    env.put("JAVA_HOME", System.getProperty("java.home"));
    /* Remove JAVA_BIN to be sure that we use the JVM running the installer
     * JVM to start the server.
     */
    env.remove("JAVA_BIN");

    try
    {
      String startedId = getStartedId();

      Process process = pb.start();

      BufferedReader err =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader out =
          new BufferedReader(new InputStreamReader(process.getInputStream()));

      StartReader errReader = new StartReader(err, startedId, true);
      StartReader outputReader = new StartReader(out, startedId, false);

      while (!errReader.isFinished() && !outputReader.isFinished())
      {
        try
        {
          Thread.sleep(100);
        } catch (InterruptedException ie)
        {
        }
      }
      // Check if something wrong occurred reading the starting of the server
      QuickSetupException ex = errReader.getException();
      if (ex == null)
      {
        ex = outputReader.getException();
      }
      if (ex != null)
      {
        throw ex;

      } else
      {
        /*
         * There are no exceptions from the readers and they are marked as
         * finished. This means that the server has written in its output the
         * message id informing that it started. So it seems that everything
         * went fine.
         *
         * However we can have issues with the firewalls or do not have rights
         * to connect.  Just check if we can connect to the server.
         * Try 5 times with an interval of 1 second between try.
         */
        boolean connected = false;
        for (int i=0; i<5 && !connected; i++)
        {
          String ldapUrl = "ldap://localhost:"+userData.getServerPort();
          try
          {
            Utils.createLdapContext(
                ldapUrl,
                userData.getDirectoryManagerDn(),
                userData.getDirectoryManagerPwd(), 3000, null);
            connected = true;
          }
          catch (NamingException ne)
          {
          }
          if (!connected)
          {
            try
            {
              Thread.sleep(1000);
            }
            catch (Throwable t)
            {
            }
          }
        }
        if (!connected)
        {
          if (Utils.isWindows())
          {
            String[] arg = {String.valueOf(userData.getServerPort())};
            throw new QuickSetupException(QuickSetupException.Type.START_ERROR,
                getMsg("error-starting-server-in-windows", arg), null);
          }
          else
          {
            String[] arg = {String.valueOf(userData.getServerPort())};
            throw new QuickSetupException(QuickSetupException.Type.START_ERROR,
                getMsg("error-starting-server-in-unix", arg), null);
          }
        }
      }

    } catch (IOException ioe)
    {
      throw new QuickSetupException(QuickSetupException.Type.START_ERROR,
          getThrowableMsg("error-starting-server", ioe), ioe);
    }
  }

  /**
   * Returns the initial wizard step.
   * @return Step representing the first step to show in the wizard
   */
  public abstract WizardStep getFirstWizardStep();

  /**
   * Called by the quicksetup controller when the user advances to
   * a new step in the wizard.  Applications are expected to manipulate
   * the QuickSetupDialog to reflect the current step.
   *
   * @param step     Step indicating the new current step
   * @param userData UserData representing the data specified by the user
   * @param dlg      QuickSetupDialog hosting the wizard
   */
  protected void setDisplayedWizardStep(WizardStep step,
                                        UserData userData,
                                        QuickSetupDialog dlg) {
    this.displayedStep = step;

    // First call the panels to do the required updates on their layout
    dlg.setDisplayedStep(step, userData);
    setWizardDialogState(dlg, userData, step);
  }

  /**
   * Called when the user advances to new step in the wizard.  Applications
   * are expected to manipulate the QuickSetupDialog to reflect the current
   * step.
   * @param dlg QuickSetupDialog hosting the wizard
   * @param userData UserData representing the data specified by the user
   * @param step Step indicating the new current step
   */
  protected abstract void setWizardDialogState(QuickSetupDialog dlg,
                                               UserData userData,
                                               WizardStep step);

  /**
   * Returns the installation path.
   * @return the installation path.
   */
  protected abstract String getInstallationPath();

  /**
   * Returns the config file path.
   * @return the config file path.
   */
  protected String getConfigFilePath()
  {
    return Utils.getPath(getInstallationPath(), CONFIG_PATH_RELATIVE);
  }

  /**
   * Returns the path to the binaries.
   * @return the path to the binaries.
   */
  protected abstract String getBinariesPath();

  /**
   * Returns the Message ID indicating that the server has started.
   * @return the Message ID indicating that the server has started.
   */
  private String getStartedId()
  {
    InstallerHelper helper = new InstallerHelper();
    return helper.getStartedId();
  }

  /**
   * Returns the tab formatted.
   * @return the tab formatted.
   */
  protected String getTab()
  {
    return formatter.getTab();
  }

  /**
   * Returns the path to the libraries.
   * @return the path to the libraries.
   */
  protected String getLibrariesPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getLibrariesRelativePath());
  }

  /**
   * Gets the current step.
   * @return ProgressStep representing the current step
   */
  public abstract ProgressStep getStatus();

  /**
   * Gets an integer representing the amount of processing
   * this application still needs to perform as a ratio
   * out of 100.
   * @param step ProgressStop for which a summary is needed
   * @return ProgressStep representing the current step
   */
  public abstract Integer getRatio(ProgressStep step);

  /**
   * Gets an i18n'd string representing the summary of
   * a give ProgressStep.
   * @param step ProgressStop for which a summary is needed
   * @return String representing the summary
   */
  public abstract String getSummary(ProgressStep step);

  /**
   * Sets the current install status for this application.
   * @param installStatus for the current installation.
   */
  public void setCurrentInstallStatus(CurrentInstallStatus installStatus) {
    this.installStatus = installStatus;
  }

  /**
   * Called by the controller when the window is closing.  The application
   * can take application specific actions here.
   * @param dlg QuickSetupDialog that will be closing
   * @param evt The event from the Window indicating closing
   */
  abstract public void windowClosing(QuickSetupDialog dlg, WindowEvent evt);

  /**
   * This method is called when we detected that there is something installed
   * we inform of this to the user and the user wants to proceed with the
   * installation destroying the contents of the data and the configuration
   * in the current installation.
   */
  public void forceToDisplay() {
    // This is really only appropriate for Installer.
    // The default implementation is to do nothing.
    // The Installer application overrides this with
    // whatever it needs.
  }

  /**
   * Get the name of the button that will receive initial focus.
   * @return ButtonName of the button to receive initial focus
   */
  abstract public ButtonName getInitialFocusButtonName();

  /**
   * Creates the main panel for the wizard dialog.
   * @param dlg QuickSetupDialog used
   * @return JPanel frame panel
   */
  abstract public JPanel createFramePanel(QuickSetupDialog dlg);

  /**
   * Returns the set of wizard steps used in this application's wizard.
   * @return Set of Step objects representing wizard steps
   */
  abstract public Set<WizardStep> getWizardSteps();

  /**
   * Creates a wizard panel given a specific step.
   * @param step for which a panel representation should be created
   * @return QuickSetupStepPanel for representing the <code>step</code>
   */
  abstract public QuickSetupStepPanel createWizardStepPanel(WizardStep step);

  /**
   * Gets the next step in the wizard given a current step.
   * @param step Step the current step
   * @return Step the next step
   */
  abstract public WizardStep getNextWizardStep(WizardStep step);

  /**
   * Gets the previous step in the wizard given a current step.
   * @param step Step the current step
   * @return Step the previous step
   */
  abstract public WizardStep getPreviousWizardStep(WizardStep step);

  /**
   * Indicates whether or not the user is allowed to return to a previous
   * step from <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can return to a previous step
   * @return boolean where true indicates the user can return to a previous
   * step from <code>step</code>
   */
  public boolean canGoBack(WizardStep step) {
    return !getFirstWizardStep().equals(step);
  }

  /**
   * Indicates whether or not the user is allowed to move to a new
   * step from <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can move to a new step
   * @return boolean where true indicates the user can move to a new
   * step from <code>step</code>
   */
  public boolean canGoForward(WizardStep step) {
    return getNextWizardStep(step) != null;
  }

  /**
   * Inidicates whether or not the user is allowed to finish the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can finish the wizard
   * @return boolean where true indicates the user can finish the wizard
   */
  public boolean canFinish(WizardStep step) {
    return getNextWizardStep(step) != null;
  }

  /**
   * Inidicates whether or not the user is allowed to quit the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can quit the wizard
   * @return boolean where true indicates the user can quit the wizard
   */
  public boolean canQuit(WizardStep step) {
    return false;
  }

  /**
   * Inidicates whether or not the user is allowed to close the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can close the wizard
   * @return boolean where true indicates the user can close the wizard
   */
  public boolean canClose(WizardStep step) {
    return false;
  }

  /**
   * Inidicates whether or not the user is allowed to cancel the wizard from
   * <code>step</code>.
   * @param step WizardStep for which the the return value indicates whether
   * or not the user can cancel the wizard
   * @return boolean where true indicates the user can cancel the wizard
   */
  public boolean canCancel(WizardStep step) {
    return false;
  }

  /**
   * Called when the user has clicked the 'previous' button.
   * @param cStep WizardStep at which the user clicked the previous button
   * @param qs QuickSetup controller
   */
  public abstract void previousClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'finish' button.
   * @param cStep WizardStep at which the user clicked the previous button
   * @param qs QuickSetup controller
   */
  public abstract void finishClicked(final WizardStep cStep,
                                     final QuickSetup qs);

  /**
   * Called when the user has clicked the 'next' button.
   * @param cStep WizardStep at which the user clicked the next button
   * @param qs QuickSetup controller
   */
  public abstract void nextClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'close' button.
   * @param cStep WizardStep at which the user clicked the close button
   * @param qs QuickSetup controller
   */
  public abstract void closeClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'cancel' button.
   * @param cStep WizardStep at which the user clicked the cancel button
   * @param qs QuickSetup controller
   */
  public abstract void cancelClicked(WizardStep cStep, QuickSetup qs);

  /**
   * Called when the user has clicked the 'quit' button.
   * @param step WizardStep at which the user clicked the quit button
   * @param qs QuickSetup controller
   */
  abstract public void quitClicked(WizardStep step, QuickSetup qs);

  /**
   * Called whenever this application should update its user data from
   * values found in QuickSetup.
   * @param cStep current wizard step
   * @param qs QuickSetup controller
   * @throws UserDataException if there is a problem with the data
   */
  abstract protected void updateUserData(WizardStep cStep, QuickSetup qs)
          throws UserDataException;

  /**
   * Gets the key for the close button's tool tip text.
   * @return String key of the text in the resource bundle
   */
  public String getCloseButtonToolTip() {
    return "close-button-tooltip";
  }

  /**
   * Gets the key for the finish button's tool tip text.
   * @return String key of the text in the resource bundle
   */
  public String getFinishButtonToolTip() {
    return "finish-button-tooltip";
  }

  /**
   * Gets the key for the finish button's label.
   * @return String key of the text in the resource bundle
   */
  public String getFinishButtonLabel() {
    return "finish-button-label";
  }

  /**
   * This class is used to read the standard error and standard output of the
   * Start process.
   *
   * When a new log message is found notifies the ProgressUpdateListeners
   * of it. If an error occurs it also notifies the listeners.
   *
   */
  private class StartReader
  {
    private QuickSetupException ex;

    private boolean isFinished;

    private boolean isFirstLine;

    /**
     * The protected constructor.
     * @param reader the BufferedReader of the start process.
     * @param startedId the message ID that this class can use to know whether
     * the start is over or not.
     * @param isError a boolean indicating whether the BufferedReader
     * corresponds to the standard error or to the standard output.
     */
    public StartReader(final BufferedReader reader, final String startedId,
        final boolean isError)
    {
      final String errorTag =
          isError ? "error-reading-erroroutput" : "error-reading-output";

      isFirstLine = true;

      Thread t = new Thread(new Runnable()
      {
        public void run()
        {
          try
          {
            String line = reader.readLine();
            while (line != null)
            {
              StringBuffer buf = new StringBuffer();
              if (!isFirstLine)
              {
                buf.append(formatter.getLineBreak());
              }
              if (isError)
              {
                buf.append(getFormattedLogError(line));
              } else
              {
                buf.append(getFormattedLog(line));
              }
              notifyListeners(buf.toString());
              isFirstLine = false;

              if (line.indexOf("id=" + startedId) != -1)
              {
                isFinished = true;
              }
              line = reader.readLine();
            }
          } catch (IOException ioe)
          {
            String errorMsg = getThrowableMsg(errorTag, ioe);
            ex =
                new QuickSetupException(QuickSetupException.Type.START_ERROR,
                    errorMsg, ioe);

          } catch (Throwable t)
          {
            String errorMsg = getThrowableMsg(errorTag, t);
            ex =
                new QuickSetupException(QuickSetupException.Type.START_ERROR,
                    errorMsg, t);
          }
          isFinished = true;
        }
      });
      t.start();
    }

    /**
     * Returns the QuickSetupException that occurred reading the Start error and
     * output or <CODE>null</CODE> if no exception occurred.
     * @return the exception that occurred reading or <CODE>null</CODE> if
     * no exception occurred.
     */
    public QuickSetupException getException()
    {
      return ex;
    }

    /**
     * Returns <CODE>true</CODE> if the server starting process finished
     * (successfully or not) and <CODE>false</CODE> otherwise.
     * @return <CODE>true</CODE> if the server starting process finished
     * (successfully or not) and <CODE>false</CODE> otherwise.
     */
    public boolean isFinished()
    {
      return isFinished;
    }
  }

  /**
   * This class is used to notify the ProgressUpdateListeners of events
   * that are written to the standard error.  It is used in WebStartInstaller
   * and in OfflineInstaller.  These classes just create a ErrorPrintStream and
   * then they do a call to System.err with it.
   *
   * The class just reads what is written to the standard error, obtains an
   * formatted representation of it and then notifies the
   * ProgressUpdateListeners with the formatted messages.
   *
   */
  protected class ErrorPrintStream extends PrintStream {
    private boolean isFirstLine;

    /**
     * Default constructor.
     *
     */
    public ErrorPrintStream()
    {
      super(new ByteArrayOutputStream(), true);
      isFirstLine = true;
    }

    /**
     * {@inheritDoc}
     */
    public void println(String msg)
    {
      if (isFirstLine)
      {
        notifyListeners(getFormattedLogError(msg));
      } else
      {
        notifyListeners(formatter.getLineBreak() + getFormattedLogError(msg));
      }
      isFirstLine = false;
    }

    /**
     * {@inheritDoc}
     */
    public void write(byte[] b, int off, int len)
    {
      if (b == null)
      {
        throw new NullPointerException("b is null");
      }

      if (off + len > b.length)
      {
        throw new IndexOutOfBoundsException(
            "len + off are bigger than the length of the byte array");
      }
      println(new String(b, off, len));
    }
  }

  /**
   * This class is used to notify the ProgressUpdateListeners of events
   * that are written to the standard output. It is used in WebStartInstaller
   * and in OfflineInstaller. These classes just create a OutputPrintStream and
   * then they do a call to System.out with it.
   *
   * The class just reads what is written to the standard output, obtains an
   * formatted representation of it and then notifies the
   * ProgressUpdateListeners with the formatted messages.
   *
   */
  protected class OutputPrintStream extends PrintStream
  {
    private boolean isFirstLine;

    /**
     * Default constructor.
     *
     */
    public OutputPrintStream()
    {
      super(new ByteArrayOutputStream(), true);
      isFirstLine = true;
    }

    /**
     * {@inheritDoc}
     */
    public void println(String msg)
    {
      if (isFirstLine)
      {
        notifyListeners(getFormattedLog(msg));
      } else
      {
        notifyListeners(formatter.getLineBreak() + getFormattedLog(msg));
      }
      isFirstLine = false;
    }

    /**
     * {@inheritDoc}
     */
    public void write(byte[] b, int off, int len)
    {
      if (b == null)
      {
        throw new NullPointerException("b is null");
      }

      if (off + len > b.length)
      {
        throw new IndexOutOfBoundsException(
            "len + off are bigger than the length of the byte array");
      }

      println(new String(b, off, len));
    }
  }
}
