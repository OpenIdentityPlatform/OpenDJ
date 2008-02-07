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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ServerLoader;
import org.opends.quicksetup.event.ProgressNotifier;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.util.Utils;

import static org.opends.messages.QuickSetupMessages.*;


import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

/**
 * This class represents an application that can be run in the context of
 * QuickSetup.  Examples of applications might be 'installer', 'uninstaller'
 * and 'upgrader'.
 */
public abstract class Application implements ProgressNotifier, Runnable {

  static private final Logger LOG =
          Logger.getLogger(Application.class.getName());

  /** Represents current install state. */
  protected CurrentInstallStatus installStatus;

  private UserData userData;

  private Installation installation;

  private ServerController serverController;

  private ApplicationTrustManager trustManager;

  private boolean notifyListeners = true;

  /** Formats progress messages. */
  protected ProgressMessageFormatter formatter;

  /** Handler for listeners and event firing. */
  protected ProgressUpdateListenerDelegate listenerDelegate;

  private ErrorPrintStream err = new ErrorPrintStream();
  private OutputPrintStream out = new OutputPrintStream();

  /**
   * Creates an application by instantiating the Application class
   * denoted by the System property
   * <code>org.opends.quicksetup.Application.class</code>.
   * @return Application object that was newly instantiated
   * @throws RuntimeException if there was a problem
   *  creating the new Application object
   */
  static public GuiApplication create()
          throws RuntimeException {
    GuiApplication app;
    String appClassName =
            System.getProperty("org.opends.quicksetup.Application.class");
    if (appClassName != null) {
      Class appClass = null;
      try {
        appClass = Class.forName(appClassName);
        app = (GuiApplication) appClass.newInstance();
      } catch (ClassNotFoundException e) {
        LOG.log(Level.INFO, "error creating quicksetup application", e);
        String msg = "Application class " + appClass + " not found";
        throw new RuntimeException(msg, e);
      } catch (IllegalAccessException e) {
        LOG.log(Level.INFO, "error creating quicksetup application", e);
        String msg = "Could not access class " + appClass;
        throw new RuntimeException(msg, e);
      } catch (InstantiationException e) {
        LOG.log(Level.INFO, "error creating quicksetup application", e);
        String msg = "Error instantiating class " + appClass;
        throw new RuntimeException(msg, e);
      } catch (ClassCastException e) {
        String msg = "The class indicated by the system property " +
                  "'org.opends.quicksetup.Application.class' must " +
                  " must be of type Application";
        throw new RuntimeException(msg, e);
      }
    } else {
      String msg = "System property 'org.opends.quicksetup.Application.class'" +
                " must specify class quicksetup application";
      throw new RuntimeException(msg);
    }
    return app;
  }

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
    listenerDelegate.addProgressUpdateListener(l);
  }

  /**
   * Removes a ProgressUpdateListener.
   * @param l the ProgressUpdateListener to be removed.
   */
  public void removeProgressUpdateListener(ProgressUpdateListener l)
  {
    listenerDelegate.removeProgressUpdateListener(l);
  }

  /**
   * Gets the OpenDS installation associated with the execution of this
   * command.
   * @return Installation object representing the current OpenDS installation
   */
  public Installation getInstallation() {
    if (installation == null) {
      String installPath = getInstallationPath();
      if (installPath != null) {
        installation = new Installation(installPath);
      }
    }
    return installation;
  }

  /**
   * Sets the application's installation.
   * @param installation describing the application's OpenDS installation
   */
  public void setInstallation(Installation installation) {
    this.installation = installation;
  }

  /**
   * Gets a server controller for use by this application.
   * @return ServerController that can be used to start and stop the server.
   */
  public ServerController getServerController() {
    if (serverController == null) {
      serverController = new ServerController(this);
    }
    return serverController;
  }

  /**
   * Returns the UserData object representing the parameters provided by
   * the user to do the installation.
   *
   * @return the UserData object representing the parameters provided
   * by the user to do the installation.
   */
  public UserData getUserData()
  {
    if (userData == null) {
      userData = createUserData();
    }
    return userData;
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratio the integer that specifies which percentage of the whole
   * installation has been completed.
   */
  public void notifyListenersDone(Integer ratio) {
    notifyListeners(ratio,
            getSummary(getCurrentProgressStep()),
            getFormattedDoneWithLineBreak());
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratio the integer that specifies which percentage of the whole
   * installation has been completed.
   */
  public void notifyListenersRatioChange(Integer ratio) {
    notifyListeners(ratio,
            getSummary(getCurrentProgressStep()),
            null);
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
  public void notifyListeners(Integer ratio, Message currentPhaseSummary,
      Message newLogDetail)
  {
    if (notifyListeners)
    {
      listenerDelegate.notifyListeners(getCurrentProgressStep(),
            ratio, currentPhaseSummary, newLogDetail);
    }
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratio the integer that specifies which percentage of
   * the whole installation has been completed.
   * @param newLogDetail the localized additional log message.
   */
  public void notifyListenersWithPoints(Integer ratio,
      Message newLogDetail) {
    notifyListeners(ratio, getSummary(getCurrentProgressStep()),
        formatter.getFormattedWithPoints(newLogDetail));
  }

  /**
   * Sets the formatter this instance should use to used
   * to format progress messages.
   * @param formatter ProgressMessageFormatter for formatting
   * progress messages
   */
  public void setProgressMessageFormatter(ProgressMessageFormatter formatter) {
    this.formatter = formatter;
    this.listenerDelegate = new ProgressUpdateListenerDelegate();
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
  protected Message getFormattedSummary(Message text)
  {
    return formatter.getFormattedSummary(text);
  }

  /**
   * Returns the formatted text string 'Error' with a line break at the end.
   * @return formatted 'Error'
   */
  protected Message getFormattedErrorWithLineBreak() {
    return new MessageBuilder(formatter.getFormattedError())
            .append(formatter.getLineBreak()).toMessage();
  }

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  protected Message getFormattedError(Message text)
  {
    return formatter.getFormattedError(text, false);
  }

  /**
   * Returns the formatted representation of an error message for a given
   * exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param m the exception.
   * @param b specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error message for the given
   * exception.
   */
  protected Message getFormattedErrorWithLineBreak(Message m, boolean b) {
    return new MessageBuilder(formatter.getFormattedError(m,b))
            .append(formatter.getLineBreak()).toMessage();
  }

  /**
   * Returns the formatted representation of an error message for a given
   * exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param t the exception.
   * @param b specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error message for the given
   * exception.
   */
  protected Message getFormattedErrorWithLineBreak(Throwable t, boolean b) {
    return new MessageBuilder(formatter.getFormattedError(t,b))
            .append(formatter.getLineBreak()).toMessage();
  }


  /**
   * Returns the formatted representation of an warning for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an warning for the given text.
   */
  public Message getFormattedWarning(Message text)
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
  protected Message getFormattedSuccess(Message text)
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
  public Message getFormattedLogError(Message text)
  {
    return formatter.getFormattedLogError(text);
  }

  /**
   * Returns the formatted representation of a log message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log message for the given text.
   */
  public Message getFormattedLog(Message text)
  {
    return formatter.getFormattedLog(text);
  }

  /**
   * Returns the formatted representation of the 'Done' text string.
   * @return the formatted representation of the 'Done' text string.
   */
  public Message getFormattedDone()
  {
    return Message.raw(formatter.getFormattedDone());
  }

  /**
   * Returns the formatted representation of the 'Done' text string
   * with a line break at the end.
   * @return the formatted representation of the 'Done' text string.
   */
  public Message getFormattedDoneWithLineBreak() {
    return new MessageBuilder(formatter.getFormattedDone())
            .append(formatter.getLineBreak()).toMessage();
  }

  /**
   * Returns the formatted representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server .....'.
   * @param text the String to which add points.
   * @return the formatted representation of the '.....' text string.
   */
  public Message getFormattedWithPoints(Message text)
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
  public Message getFormattedProgress(Message text)
  {
    return formatter.getFormattedProgress(text);
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text with a line break.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  public Message getFormattedProgressWithLineBreak(Message text)
  {
    return new MessageBuilder(formatter.getFormattedProgress(text))
            .append(getLineBreak()).toMessage();
  }

  /**
   * Returns the formatted representation of an error message for a given
   * exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param t the exception.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error message for the given
   * exception.
   */
  protected Message getFormattedError(Throwable t, boolean applyMargin)
  {
    return formatter.getFormattedError(t, applyMargin);
  }

  /**
   * Returns the formatted representation of an error message for a given
   * exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param m the message.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error message for the given
   * exception.
   */
  protected Message getFormattedError(Message m, boolean applyMargin)
  {
    return formatter.getFormattedError(m, applyMargin);
  }

  /**
   * Returns the line break formatted.
   * @return the line break formatted.
   */
  public Message getLineBreak()
  {
    return formatter.getLineBreak();
  }

  /**
   * Returns the task separator formatted.
   * @return the task separator formatted.
   */
  protected Message getTaskSeparator()
  {
    return formatter.getTaskSeparator();
  }

  /**
   * This method is called when a new log message has been received.  It will
   * notify the ProgressUpdateListeners of this fact.
   * @param newLogDetail the new log detail.
   */
  public void notifyListeners(Message newLogDetail)
  {
    Integer ratio = getRatio(getCurrentProgressStep());
    Message currentPhaseSummary = getSummary(getCurrentProgressStep());
    notifyListeners(ratio, currentPhaseSummary, newLogDetail);
  }

  /**
   * Returns the installation path.
   * @return the installation path.
   */
  public abstract String getInstallationPath();

  /**
   * Gets the current step.
   * @return ProgressStep representing the current step
   */
  public abstract ProgressStep getCurrentProgressStep();

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
  public abstract Message getSummary(ProgressStep step);

  /**
   * Sets the current install status for this application.
   * @param installStatus for the current installation.
   */
  public void setCurrentInstallStatus(CurrentInstallStatus installStatus) {
    this.installStatus = installStatus;
  }

  /**
   * Returns whether the installer has finished or not.
   * @return <CODE>true</CODE> if the install is finished or <CODE>false
   * </CODE> if not.
   */
  abstract public boolean isFinished();

  /**
   * Returns the trust manager that can be used to establish secure connections.
   * @return the trust manager that can be used to establish secure connections.
   */
  public ApplicationTrustManager getTrustManager()
  {
    if (trustManager == null)
    {
      if (!Utils.isCli())
      {
        try
        {
          trustManager = new ApplicationTrustManager(UIKeyStore.getInstance());
        }
        catch (Throwable t)
        {
          LOG.log(Level.WARNING, "Error retrieving UI key store: "+t, t);
          trustManager = new ApplicationTrustManager(null);
        }
      }
      else
      {
        trustManager = new ApplicationTrustManager(null);
      }
    }
    return trustManager;
  }



  /**
   * Indicates whether or not this application is capable of cancelling
   * the operation performed in the run method.  A cancellable operation
   * should leave its environment in the same state as it was prior to
   * running the operation (files deleted, changes backed out etc.).
   *
   * Marking an <code>Application</code> as cancellable may control UI
   * elements like the presense of a cancel button while the operation
   * is being performed.
   *
   * Applications marked as cancellable should override the
   * <code>cancel</code> method in such a way as to undo whatever
   * actions have taken place in the run method up to that point.
   *
   * @return boolean where true inidcates that the operation is cancellable
   */
  abstract public boolean isCancellable();

  /**
   * Signals that the application should cancel a currently running
   * operation as soon as possible and return the environment to the
   * state prior to running the operation.  When finished backing
   * out changes the application should make sure that <code>isFinished</code>
   * returns true so that the application can complete.
   */
  abstract public void cancel();

  /**
   * Checks whether the operation has been aborted.  If it has throws an
   * ApplicationException.  All the applications that support abort must
   * provide their implementation as the default implementation is empty.
   *
   * @throws ApplicationException thrown if the application was aborted.
   */
  public void checkAbort() throws ApplicationException
  {
  }

  /**
   * Makes available a <code>UserInteraction</code> class that can be used
   * by the application to interact with the user.  If the user has requested
   * a quiet session this method returns null.
   * @return UserInteraction object
   */
  public UserInteraction userInteraction() {
    // Note:  overridden in GuiApplication
    UserInteraction ui = null;
    UserData ud = getUserData();
    if (ud != null && ud.isInteractive()) {
      ui = new CliUserInteraction();
    }
    return ui;
  }

  /**
   * Conditionally notifies listeners of the log file if it
   * has been initialized.
   */
  protected void notifyListenersOfLog() {
    File logFile = QuickSetupLog.getLogFile();
    if (logFile != null) {
      notifyListeners(getFormattedProgress(
          INFO_GENERAL_SEE_FOR_DETAILS.get(logFile.getPath())));
      notifyListeners(getLineBreak());
    }
  }

  /**
   * Writes an initial record in the installation's historical
   * log describing moving from one version to another.
   * @param fromVersion from with install will be migrated
   * @param toVersion to which install will be migrated
   * @return Long ID for this session
   * @throws ApplicationException if something goes wrong
   */
  protected Long writeInitialHistoricalRecord(
          BuildInformation fromVersion,
          BuildInformation toVersion)
          throws ApplicationException {
    Long id;
    try {
      HistoricalLog log =
              new HistoricalLog(getInstallation().getHistoryLogFile());
      id = log.append(fromVersion, toVersion,
              HistoricalRecord.Status.STARTED,
              "log file '" + QuickSetupLog.getLogFile().getPath() + "'");
    } catch (IOException e) {
      Message msg = INFO_ERROR_LOGGING_OPERATION.get();
      throw ApplicationException.createFileSystemException(
              msg, e);
    }
    return id;
  }

  /**
   * Writes a record into this installation's historical log.
   * @param id obtained from calling <code>writeInitialHistoricalRecord</code>
   * @param from version from with install will be migrated
   * @param to version to which install will be migrated
   * @param status of the operation
   * @param note string with additional information
   * @throws ApplicationException if something goes wrong
   * @see {@link #writeInitialHistoricalRecord(BuildInformation,
          BuildInformation)}
   */
  protected void writeHistoricalRecord(
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
      Message msg = INFO_ERROR_LOGGING_OPERATION.get();
      throw ApplicationException.createFileSystemException(msg, e);
    }
  }

  /**
   * Returns a localized representation of a TopologyCacheException object.
   * @param e the exception we want to obtain the representation from.
   * @return a localized representation of a TopologyCacheException object.
   */
  protected Message getMessage(TopologyCacheException e)
  {
    return Utils.getMessage(e);
  }

  /**
   * Gets an InitialLdapContext based on the information that appears on the
   * provided ServerDescriptor object.  Note that the server is assumed to be
   * registered and that contains a Map with ADSContext.ServerProperty keys.
   * @param server the object describing the server.
   * @param trustManager the trust manager to be used to establish the
   * connection.
   * @param dn the dn to be used to authenticate.
   * @param pwd the pwd to be used to authenticate.
   * @return the InitialLdapContext to the remote server.
   * @throws ApplicationException if something goes wrong.
   */
  protected InitialLdapContext getRemoteConnection(ServerDescriptor server,
      String dn, String pwd, ApplicationTrustManager trustManager)
  throws ApplicationException
  {
    Map<ADSContext.ServerProperty, Object> adsProperties =
      server.getAdsProperties();
    ServerLoader loader = new ServerLoader(adsProperties, dn, pwd,
        trustManager);

    InitialLdapContext ctx = null;
    try
    {
      ctx = loader.createContext();
    }
    catch (NamingException ne)
    {
      Message msg;
      if (Utils.isCertificateException(ne))
      {
        msg = INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE_SERVER.get(
            server.getHostPort(true), ne.toString(true));
      }
      else
      {
         msg = INFO_CANNOT_CONNECT_TO_REMOTE_GENERIC.get(
             server.getHostPort(true), ne.toString(true));
      }
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR, msg,
          ne);
    }
    return ctx;
  }

  /**
   * Returns <CODE>true</CODE> if the application is running in verbose mode and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the application is running in verbose mode and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isVerbose()
  {
    return getUserData().isVerbose();
  }

  /**
   * Returns the error stream to be used by the application when launching
   * command lines.
   * @return  the error stream to be used by the application when launching
   * command lines.
   */
  public ErrorPrintStream getApplicationErrorStream()
  {
    return err;
  }

  /**
   * Returns the output stream to be used by the application when launching
   * command lines.
   * @return  the output stream to be used by the application when launching
   * command lines.
   */
  public OutputPrintStream getApplicationOutputStream()
  {
    return out;
  }



  /**
   * Notifies the progress update listeners of the application of the message
   * we received.
   * @return <CODE>true</CODE> if we must notify the application listeners
   * of the message and <CODE>false</CODE> otherwise.
   */
  public boolean isNotifyListeners()
  {
    return notifyListeners;
  }

  /**
   * Tells whether we must notify the listeners or not of the message
   * received.
   * @param notifyListeners the boolean that informs of whether we have
   * to notify the listeners or not.
   */
  public void setNotifyListeners(boolean notifyListeners)
  {
    this.notifyListeners = notifyListeners;
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
  public class ErrorPrintStream extends ApplicationPrintStream {

    /**
     * Default constructor.
     *
     */
    public ErrorPrintStream() {
      super();
    }

    /**
     * {@inheritDoc}
     */
    protected Message formatString(String s) {
      return getFormattedLogError(Message.raw(s));
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
  public class OutputPrintStream extends ApplicationPrintStream
  {

    /**
     * Default constructor.
     *
     */
    public OutputPrintStream() {
      super();
    }

    /**
     * {@inheritDoc}
     */
    protected Message formatString(String s) {
      return getFormattedLog(Message.raw(s));
    }

  }

  /**
   * This class is used to notify the ProgressUpdateListeners of events
   * that are written to the standard streams.
   */
  protected abstract class ApplicationPrintStream extends PrintStream {

    private boolean isFirstLine;

    /**
     * Format a string before sending a listener notification.
     * @param string to format
     * @return formatted message
     */
    abstract protected Message formatString(String string);

    /**
     * Default constructor.
     *
     */
    public ApplicationPrintStream()
    {
      super(new ByteArrayOutputStream(), true);
      isFirstLine = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void println(String msg)
    {
      MessageBuilder mb = new MessageBuilder();
      if (isFirstLine)
      {
        mb.append(formatString(msg));
      } else
      {
        if (!Utils.isCli())
        {
          mb.append(getLineBreak());
        }
        mb.append(formatString(msg));
      }

      notifyListeners(mb.toMessage());
      LOG.log(Level.INFO, "server: " + msg);
      isFirstLine = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
   * Class used to add points periodically to the end of the logs.
   *
   */
  protected class PointAdder implements Runnable
  {
    private Thread t;
    private boolean stopPointAdder;
    private boolean pointAdderStopped;

    /**
     * Default constructor.
     */
    public PointAdder()
    {
    }

    /**
     * Starts the PointAdder: points are added at the end of the logs
     * periodically.
     */
    public void start()
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(formatter.getSpace());
      for (int i=0; i< 5; i++)
      {
        mb.append(formatter.getFormattedPoint());
      }
      Integer ratio = getRatio(getCurrentProgressStep());
      Message currentPhaseSummary = getSummary(getCurrentProgressStep());
      listenerDelegate.notifyListeners(getCurrentProgressStep(),
          ratio, currentPhaseSummary, mb.toMessage());
      t = new Thread(this);
      t.start();
    }

    /**
     * Stops the PointAdder: points are no longer added at the end of the logs
     * periodically.
     */
    public synchronized void stop()
    {
      stopPointAdder = true;
      while (!pointAdderStopped)
      {
        try
        {
          t.interrupt();
          // To allow the thread to set the boolean.
          Thread.sleep(100);
        }
        catch (Throwable t)
        {
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    public void run()
    {
      while (!stopPointAdder)
      {
        try
        {
          Thread.sleep(3000);
          Integer ratio = getRatio(getCurrentProgressStep());
          Message currentPhaseSummary = getSummary(getCurrentProgressStep());
          listenerDelegate.notifyListeners(getCurrentProgressStep(),
              ratio, currentPhaseSummary, formatter.getFormattedPoint());
        }
        catch (Throwable t)
        {
        }
      }
      pointAdderStopped = true;

      Integer ratio = getRatio(getCurrentProgressStep());
      Message currentPhaseSummary = getSummary(getCurrentProgressStep());
      listenerDelegate.notifyListeners(getCurrentProgressStep(),
          ratio, currentPhaseSummary, formatter.getSpace());
    }
  }
}
