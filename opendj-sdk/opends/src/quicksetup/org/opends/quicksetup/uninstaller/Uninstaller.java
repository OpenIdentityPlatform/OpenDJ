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

package org.opends.quicksetup.uninstaller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.event.UninstallProgressUpdateEvent;
import org.opends.quicksetup.event.UninstallProgressUpdateListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

/**
 * This class is in charge of performing the uninstallation of Open DS.
 *
 */
public class Uninstaller
{
  private UserUninstallData userData;
  private ProgressMessageFormatter formatter;
  private UninstallProgressStep status;
  private HashMap<UninstallProgressStep, Integer> hmRatio =
    new HashMap<UninstallProgressStep, Integer>();

  private HashMap<UninstallProgressStep, String> hmSummary =
    new HashMap<UninstallProgressStep, String>();

  private HashSet<UninstallProgressUpdateListener> listeners =
    new HashSet<UninstallProgressUpdateListener>();

  private UninstallException ue;

  /**
   * Uninstaller constructor.
   * @param userData the object containing the information provided by the user
   * in the uninstallation.
   * @param formatter the message formatter to be used to generate the text of
   * the UninstallProgressUpdateEvent.
   */
  public Uninstaller(UserUninstallData userData,
      ProgressMessageFormatter formatter)
  {
    this.userData = userData;
    this.formatter = formatter;
    initMaps();
    status = UninstallProgressStep.NOT_STARTED;
  }

  /**
   * Start the uninstall process.  This method will not block the thread on
   * which is invoked.
   */
  public void start()
  {
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        doUninstall();
      }
    });
    t.start();
  }

  /**
   * Returns whether the uninstaller has finished or not.
   * @return <CODE>true</CODE> if the install is finished or <CODE>false
   * </CODE> if not.
   */
  public boolean isFinished()
  {
    return getStatus() == UninstallProgressStep.FINISHED_SUCCESSFULLY
    || getStatus() == UninstallProgressStep.FINISHED_WITH_ERROR;
  }

  /**
   * Adds a UninstallProgressUpdateListener that will be notified of updates in
   * the uninstall progress.
   * @param l the UninstallProgressUpdateListener to be added.
   */
  public void addProgressUpdateListener(UninstallProgressUpdateListener l)
  {
    listeners.add(l);
  }

  /**
   * Removes a UninstallProgressUpdateListener.
   * @param l the UninstallProgressUpdateListener to be removed.
   */
  public void removeProgressUpdateListener(UninstallProgressUpdateListener l)
  {
    listeners.remove(l);
  }

  /**
   * Returns the UninstallException that might occur during installation or
   * <CODE>null</CODE> if no exception occurred.
   * @return the UninstallException that might occur during installation or
   * <CODE>null</CODE> if no exception occurred.
   */
  public UninstallException getException()
  {
    return ue;
  }

  /**
   * Initialize the different map used in this class.
   *
   */
  private void initMaps()
  {
    hmSummary.put(UninstallProgressStep.NOT_STARTED,
        getFormattedSummary(getMsg("summary-uninstall-not-started")));
    hmSummary.put(UninstallProgressStep.STOPPING_SERVER,
        getFormattedSummary(getMsg("summary-stopping")));
    hmSummary.put(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES,
        getFormattedSummary(getMsg("summary-deleting-external-db-files")));
    hmSummary.put(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES,
        getFormattedSummary(getMsg("summary-deleting-external-log-files")));
    hmSummary.put(UninstallProgressStep.REMOVING_EXTERNAL_REFERENCES,
        getFormattedSummary(getMsg("summary-deleting-external-references")));
    hmSummary.put(UninstallProgressStep.DELETING_INSTALLATION_FILES,
        getFormattedSummary(getMsg("summary-deleting-installation-files")));

    String successMsg;
    if (Utils.isCli())
    {
      if (userData.getRemoveLibrariesAndTools())
      {
        String[] arg = {getLibrariesPath()};
        successMsg = getMsg(
            "summary-uninstall-finished-successfully-remove-jarfiles-cli",
            arg);
      }
      else
      {
        successMsg = getMsg("summary-uninstall-finished-successfully-cli");
      }
    }
    else
    {
      if (userData.getRemoveLibrariesAndTools())
      {
        String[] arg = {getLibrariesPath()};
        successMsg = getMsg(
            "summary-uninstall-finished-successfully-remove-jarfiles", arg);
      }
      else
      {
        successMsg = getMsg("summary-uninstall-finished-successfully");
      }
    }
    hmSummary.put(UninstallProgressStep.FINISHED_SUCCESSFULLY,
        getFormattedSuccess(successMsg));
    hmSummary.put(UninstallProgressStep.FINISHED_WITH_ERROR,
        getFormattedError(getMsg("summary-uninstall-finished-with-error")));


    /*
     * hmTime contains the relative time that takes for each task to be
     * accomplished. For instance if stopping takes twice the time of
     * deleting files, the value for downloading will be the double of the
     * value for extracting.
     */
    HashMap<UninstallProgressStep, Integer> hmTime =
        new HashMap<UninstallProgressStep, Integer>();
    hmTime.put(UninstallProgressStep.STOPPING_SERVER, 15);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES, 30);
    hmTime.put(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES, 5);
    hmTime.put(UninstallProgressStep.REMOVING_EXTERNAL_REFERENCES, 5);
    hmTime.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, 10);

    int totalTime = 0;
    ArrayList<UninstallProgressStep> steps =
      new ArrayList<UninstallProgressStep>();
    if (getUserData().getStopServer())
    {
      totalTime += hmTime.get(UninstallProgressStep.STOPPING_SERVER);
      steps.add(UninstallProgressStep.STOPPING_SERVER);
    }
    totalTime += hmTime.get(UninstallProgressStep.DELETING_INSTALLATION_FILES);
    steps.add(UninstallProgressStep.DELETING_INSTALLATION_FILES);

    if (getUserData().getExternalDbsToRemove().size() > 0)
    {
      totalTime += hmTime.get(
          UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES);
      steps.add(UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES);
    }

    if (getUserData().getExternalLogsToRemove().size() > 0)
    {
      totalTime += hmTime.get(
          UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES);
      steps.add(UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES);
    }

    int cumulatedTime = 0;
    for (UninstallProgressStep s : steps)
    {
      Integer statusTime = hmTime.get(s);
      hmRatio.put(s, (100 * cumulatedTime) / totalTime);
      if (statusTime != null)
      {
        cumulatedTime += statusTime;
      }
    }

    hmRatio.put(UninstallProgressStep.FINISHED_SUCCESSFULLY, 100);
    hmRatio.put(UninstallProgressStep.FINISHED_WITH_ERROR, 100);
  }

  /**
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * @see ResourceProvider.getMsg(String key)
   * @param key the key in the properties file.
   * @return the value associated to the key in the properties file.
   * properties file.
   */
  private String getMsg(String key)
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
   * @see ResourceProvider.getMsg(String key, String[] args)
   * @param key the key in the properties file.
   * @param args the arguments to be passed to generate the resulting value.
   * @return the value associated to the key in the properties file.
   */
  private String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  /**
   * Returns a ResourceProvider instance.
   * @return a ResourceProvider instance.
   */
  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /**
   * Returns a localized message for a given properties key and throwable.
   * @param key the key of the message in the properties file.
   * @param t the throwable for which we want to get a message.
   * @return a localized message for a given properties key and throwable.
   */
  private String getThrowableMsg(String key, Throwable t)
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
  private String getThrowableMsg(String key, String[] args, Throwable t)
  {
    return Utils.getThrowableMsg(getI18n(), key, args, t);
  }

  /**
   * Returns the formatted representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  private String getFormattedSummary(String text)
  {
    return formatter.getFormattedSummary(text);
  }

  /**
   * Returns the formatted representation of a success message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an success message for the given
   * text.
   */
  private String getFormattedSuccess(String text)
  {
    return formatter.getFormattedSuccess(text);
  }

  /**
   * Returns the formatted representation of an warning for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an warning for the given text.
   */
  private String getFormattedWarning(String text)
  {
    return formatter.getFormattedWarning(text, true);
  }

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  private String getFormattedError(String text)
  {
    return formatter.getFormattedError(text, false);
  }

  /**
   * Returns the formatted representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log error message for the given
   * text.
   */
  private String getFormattedLogError(String text)
  {
    return formatter.getFormattedLogError(text);
  }

  /**
   * Returns the formatted representation of a log message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log message for the given text.
   */
  private String getFormattedLog(String text)
  {
    return formatter.getFormattedLog(text);
  }

  /**
   * Returns the formatted representation of the 'Done' text string.
   * @return the formatted representation of the 'Done' text string.
   */
  private String getFormattedDone()
  {
    return formatter.getFormattedDone();
  }

  /**
   * Returns the formatted representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Deleting file' the
   * return value will be 'Deleting file .....'.
   * @param text the String to which add points.
   * @return the formatted representation of the '.....' text string.
   */
  private String getFormattedWithPoints(String text)
  {
    return formatter.getFormattedWithPoints(text);
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
  private String getFormattedError(Exception ex, boolean applyMargin)
  {
    return formatter.getFormattedError(ex, applyMargin);
  }

  /**
   * Returns the line break formatted.
   * @return the line break formatted.
   */
  private String getLineBreak()
  {
    return formatter.getLineBreak();
  }

  /**
   * Returns the tab formatted.
   * @return the tab formatted.
   */
  private String getTab()
  {
    return formatter.getTab();
  }

  /**
   * Returns the task separator formatted.
   * @return the task separator formatted.
   */
  private String getTaskSeparator()
  {
    return formatter.getTaskSeparator();
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  private String getFormattedProgress(String text)
  {
    return formatter.getFormattedProgress(text);
  }

  /**
   * Returns the current UninstallProgressStep of the installation process.
   * @return the current UninstallProgressStep of the installation process.
   */
  private UninstallProgressStep getStatus()
  {
    return status;
  }

  private UserUninstallData getUserData()
  {
    return userData;
  }

  /**
   * Actually performs the uninstall in this thread.  The thread is blocked.
   *
   */
  private void doUninstall()
  {
    try
    {
      boolean displaySeparator = false;
      if (getUserData().getStopServer())
      {
        status = UninstallProgressStep.STOPPING_SERVER;
        stopServer();
        displaySeparator = true;
      }

      Set<String> dbsToDelete = getUserData().getExternalDbsToRemove();
      if (dbsToDelete.size() > 0)
      {
        status = UninstallProgressStep.DELETING_EXTERNAL_DATABASE_FILES;
        if (displaySeparator)
        {
          notifyListeners(getTaskSeparator());
        }

        deleteExternalDatabaseFiles(dbsToDelete);
        displaySeparator = true;
      }

      Set<String> logsToDelete = getUserData().getExternalLogsToRemove();
      if (logsToDelete.size() > 0)
      {
        status = UninstallProgressStep.DELETING_EXTERNAL_LOG_FILES;

        if (displaySeparator)
        {
          notifyListeners(getTaskSeparator());
        }

        deleteExternalLogFiles(logsToDelete);
        displaySeparator = true;
      }

      boolean somethingToDelete = userData.getRemoveBackups() ||
      userData.getRemoveConfigurationAndSchema() ||
      userData.getRemoveDatabases() ||
      userData.getRemoveLDIFs() ||
      userData.getRemoveLibrariesAndTools() ||
      userData.getRemoveLogs();
      if (displaySeparator && somethingToDelete)
      {
        notifyListeners(getTaskSeparator());
      }

      if (somethingToDelete)
      {
        status = UninstallProgressStep.DELETING_INSTALLATION_FILES;
        deleteInstallationFiles(getRatio(status),
            getRatio(UninstallProgressStep.FINISHED_SUCCESSFULLY));
      }
      status = UninstallProgressStep.FINISHED_SUCCESSFULLY;
      if (Utils.isCli())
      {
        notifyListeners(getLineBreak()+getLineBreak()+getSummary(status));
      }
      else
      {
        notifyListeners(null);
      }

    } catch (UninstallException ex)
    {
      ue = ex;
      status = UninstallProgressStep.FINISHED_WITH_ERROR;
      String msg = getFormattedError(ex, true);
      notifyListeners(msg);
    }
    catch (Throwable t)
    {
      ue = new UninstallException(
          UninstallException.Type.BUG,
          getThrowableMsg("bug-msg", t), t);
      status = UninstallProgressStep.FINISHED_WITH_ERROR;
      String msg = getFormattedError(ue, true);
      notifyListeners(msg);
    }
  }

  /**
   * This method notifies the UninstallProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratio the integer that specifies which percentage of
   * the whole installation has been completed.
   * @param currentPhaseSummary the localized summary message for the
   * current installation progress in formatted form.
   * @param newLogDetail the new log messages that we have for the
   * installation in formatted form.
   */
  private void notifyListeners(Integer ratio, String currentPhaseSummary,
      String newLogDetail)
  {
    UninstallProgressUpdateEvent ev =
        new UninstallProgressUpdateEvent(getStatus(), ratio,
            currentPhaseSummary, newLogDetail);
    for (UninstallProgressUpdateListener l : listeners)
    {
      l.progressUpdate(ev);
    }
  }

  /**
   * This method is called when a new log message has been received.  It will
   * notify the UninstallProgressUpdateListeners of this fact.
   * @param newLogDetail the new log detail.
   */
  private void notifyListeners(String newLogDetail)
  {
    Integer ratio = getRatio(getStatus());
    String currentPhaseSummary = getSummary(getStatus());
    notifyListeners(ratio, currentPhaseSummary, newLogDetail);
  }

  /**
   * Returns an integer that specifies which percentage of the whole
   * installation has been completed.
   * @param step the UninstallProgressStep for which we want to get the ratio.
   * @return an integer that specifies which percentage of the whole
   * uninstallation has been completed.
   */
  private Integer getRatio(UninstallProgressStep status)
  {
    return hmRatio.get(status);
  }

  /**
   * Returns an formatted representation of the summary for the specified
   * UninstallProgressStep.
   * @param step the UninstallProgressStep for which we want to get the summary.
   * @return an formatted representation of the summary for the specified
   * UninstallProgressStep.
   */
  private String getSummary(UninstallProgressStep status)
  {
    return hmSummary.get(status);
  }

  /**
   * This methods stops the server.
   * @throws UninstallException if something goes wrong.
   */
  private void stopServer() throws UninstallException
  {
    notifyListeners(getFormattedProgress(getMsg("progress-stopping")) +
        getLineBreak());

    ArrayList<String> argList = new ArrayList<String>();

    if (Utils.isWindows())
    {
      argList.add(Utils.getPath(getBinariesPath(),
              Utils.getWindowsStopFileName()));
    } else
    {
      argList.add(Utils.getPath(getBinariesPath(),
              Utils.getUnixStopFileName()));
    }
    String[] args = new String[argList.size()];
    argList.toArray(args);
    ProcessBuilder pb = new ProcessBuilder(args);
    Map<String, String> env = pb.environment();
    env.put("JAVA_HOME", System.getProperty("java.home"));
    /* Remove JAVA_BIN to be sure that we use the JVM running the uninstaller
     * JVM to stop the server.
     */
    env.remove("JAVA_BIN");

    try
    {
      Process process = pb.start();

      BufferedReader err =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader out =
          new BufferedReader(new InputStreamReader(process.getInputStream()));

      /* Create these objects to resend the stop process output to the details
       * area.
       */
      new StopReader(err, true);
      new StopReader(out, false);

      int returnValue = process.waitFor();

      int clientSideError =
      org.opends.server.protocols.ldap.LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
      if ((returnValue == clientSideError) || (returnValue == 0))
      {
        if (Utils.isWindows())
        {
          /*
           * Sometimes the server keeps some locks on the files.
           * TODO: remove this code once stop-ds returns properly when server
           * is stopped.
           */
          int nTries = 10;
          boolean stopped = false;

          for (int i=0; i<nTries && !stopped; i++)
          {
            stopped = !CurrentInstallStatus.isServerRunning();
            if (!stopped)
            {
              String msg =
                getFormattedLog(getMsg("progress-server-waiting-to-stop"))+
              getLineBreak();
              notifyListeners(msg);
              try
              {
                Thread.sleep(5000);
              }
              catch (Exception ex)
              {

              }
            }
          }
          if (!stopped)
          {
            returnValue = -1;
          }
        }
      }

      if (returnValue == clientSideError)
      {
        String msg = getLineBreak() +
            getFormattedLog(getMsg("progress-server-already-stopped"))+
            getLineBreak();
        notifyListeners(msg);

      }
      else if (returnValue != 0)
      {
        String[] arg = {String.valueOf(returnValue)};
        String msg = getMsg("error-stopping-server-code", arg);

        /*
         * The return code is not the one expected, assume the server could
         * not be stopped.
         */
        throw new UninstallException(UninstallException.Type.STOP_ERROR, msg,
            null);
      }
      else
      {
        String msg = getFormattedLog(getMsg("progress-server-stopped"));
        notifyListeners(msg);
      }

    } catch (IOException ioe)
    {
      throw new UninstallException(UninstallException.Type.STOP_ERROR,
          getThrowableMsg("error-stopping-server", ioe), ioe);
    }
    catch (InterruptedException ie)
    {
      throw new UninstallException(UninstallException.Type.BUG,
          getThrowableMsg("error-stopping-server", ie), ie);
    }
  }

  /**
   * Deletes the external database files specified in the provided Set.
   * @param dbFiles the database directories to be deleted.
   * @throws UninstallException if something goes wrong.
   */
  private void deleteExternalDatabaseFiles(Set<String> dbFiles)
  throws UninstallException
  {
    notifyListeners(getFormattedProgress(
        getMsg("progress-deleting-external-db-files")) +
        getLineBreak());
    for (String path : dbFiles)
    {
      deleteRecursively(new File(path));
    }
  }

  /**
   * Deletes the external database files specified in the provided Set.
   * @param logFiles the log files to be deleted.
   * @throws UninstallException if something goes wrong.
   */
  private void deleteExternalLogFiles(Set<String> logFiles)
  throws UninstallException
  {
    notifyListeners(getFormattedProgress(
        getMsg("progress-deleting-external-log-files")) +
        getLineBreak());
    for (String path : logFiles)
    {
      deleteRecursively(new File(path));
    }
  }

  /**
   * Deletes the files under the installation path.
   * @throws UninstallException if something goes wrong.
   */
  private void deleteInstallationFiles(int minRatio, int maxRatio)
  throws UninstallException
  {
    notifyListeners(getFormattedProgress(
        getMsg("progress-deleting-installation-files")) +
        getLineBreak());
    File f = new File(Utils.getInstallPathFromClasspath());
    InstallationFilesToDeleteFilter filter =
      new InstallationFilesToDeleteFilter();
    File[] rootFiles = f.listFiles();
    if (rootFiles != null)
    {
      /* The following is done to have a moving progress bar when we delete
       * the installation files.
       */
      int totalRatio = 0;
      ArrayList<Integer> cumulatedRatio = new ArrayList<Integer>();
      for (int i=0; i<rootFiles.length; i++)
      {
       if (filter.accept(rootFiles[i]))
       {
         int relativeRatio;
         if (equalsOrDescendant(rootFiles[i], new File(getLibrariesPath())))
         {
           relativeRatio = 10;
         }
         else if (equalsOrDescendant(rootFiles[i], new File(getBinariesPath())))
         {
           relativeRatio = 5;
         }
         else if (equalsOrDescendant(rootFiles[i], new File(getConfigPath())))
         {
           relativeRatio = 5;
         }
         else if (equalsOrDescendant(rootFiles[i], new File(getBackupsPath())))
         {
           relativeRatio = 20;
         }
         else if (equalsOrDescendant(rootFiles[i], new File(getLDIFsPath())))
         {
           relativeRatio = 20;
         }
         else if (equalsOrDescendant(rootFiles[i],
             new File(getDatabasesPath())))
         {
           relativeRatio = 50;
         }
         else if (equalsOrDescendant(rootFiles[i], new File(getLogsPath())))
         {
           relativeRatio = 30;
         }
         else
         {
           relativeRatio = 2;
         }
         cumulatedRatio.add(totalRatio);
         totalRatio += relativeRatio;
       }
       else
       {
         cumulatedRatio.add(totalRatio);
       }
      }
      Iterator<Integer> it = cumulatedRatio.iterator();
      for (int i=0; i<rootFiles.length; i++)
      {
        int beforeRatio = minRatio +
        ((it.next() * (maxRatio - minRatio)) / totalRatio);
        hmRatio.put(UninstallProgressStep.DELETING_INSTALLATION_FILES,
            beforeRatio);
        deleteRecursively(rootFiles[i], filter);
      }
      hmRatio.put(UninstallProgressStep.DELETING_INSTALLATION_FILES, maxRatio);
    }
  }

  /**
   * Returns the path to the binaries.
   * @return the path to the binaries.
   */
  private String getBinariesPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getBinariesRelativePath());
  }

  /**
   * Returns the path to the libraries.
   * @return the path to the libraries.
   */
  private String getLibrariesPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getLibrariesRelativePath());
  }

  /**
   * Returns the path to the backup files under the install path.
   * @return the path to the backup files under the install path.
   */
  private String getBackupsPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getBackupsRelativePath());
  }

  /**
   * Returns the path to the LDIF files under the install path.
   * @return the path to the LDIF files under the install path.
   */
  private String getLDIFsPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getLDIFsRelativePath());
  }

  /**
   * Returns the path to the config files under the install path.
   * @return the path to the config files under the install path.
   */
  private String getConfigPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getConfigRelativePath());
  }

  /**
   * Returns the path to the log files under the install path.
   * @return the path to the log files under the install path.
   */
  private String getLogsPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getLogsRelativePath());
  }

  /**
   * Returns the path to the database files under the install path.
   * @return the path to the database files under the install path.
   */
  private String getDatabasesPath()
  {
    return Utils.getPath(Utils.getInstallPathFromClasspath(),
        Utils.getDatabasesRelativePath());
  }

  /**
   * Deletes everything below the specified file.
   * @param file the path to be deleted.
   * @throws UninstallException if something goes wrong.
   */
  private void deleteRecursively(File file) throws UninstallException
  {
    deleteRecursively(file, null);
  }

  /**
   * Deletes everything below the specified file.
   * @param file the path to be deleted.
   * @param filter the filter of the files to know if the file can be deleted
   * directly or not.
   * @throws UninstallException if something goes wrong.
   */

  private void deleteRecursively(File file, FileFilter filter)
  throws UninstallException
  {
    if (file.exists())
    {
      if (file.isFile())
      {
        if (filter != null)
        {
          if (filter.accept(file))
          {
            delete(file);
          }
        }
        else
        {
          delete(file);
        }
      }
      else
      {
        File[] children = file.listFiles();
        if (children != null)
        {
          for (int i=0; i<children.length; i++)
          {
            deleteRecursively(children[i], filter);
          }
        }
        if (filter != null)
        {
          if (filter.accept(file))
          {
            delete(file);
          }
        }
        else
        {
          delete(file);
        }
      }
    }
    else
    {
      // Just tell that the file/directory does not exist.
      String[] arg = {file.toString()};
      notifyListeners(getFormattedWarning(
          getMsg("deleting-file-does-not-exist", arg)));
    }
  }

  /**
   * Deletes the specified file.
   * @param file the file to be deleted.
   * @throws UninstallException if something goes wrong.
   */
  private void delete(File file) throws UninstallException
  {
    String[] arg = {file.getAbsolutePath()};
    boolean isFile = file.isFile();

    if (isFile)
    {
      notifyListeners(getFormattedWithPoints(
          getMsg("progress-deleting-file", arg)));
    }
    else
    {
      notifyListeners(getFormattedWithPoints(
          getMsg("progress-deleting-directory", arg)));
    }

    boolean delete = false;
    /*
     * Sometimes the server keeps some locks on the files.
     * TODO: remove this code once stop-ds returns properly when server
     * is stopped.
     */
    int nTries = 5;
    for (int i=0; i<nTries && !delete; i++)
    {
      delete = file.delete();
      if (!delete)
      {
        try
        {
          Thread.sleep(1000);
        }
        catch (Exception ex)
        {
        }
      }
    }

    if (!delete)
    {
      String errMsg;
      if (isFile)
      {
        errMsg = getMsg("error-deleting-file", arg);
      }
      else
      {
        errMsg = getMsg("error-deleting-directory", arg);
      }
      throw new UninstallException(
          UninstallException.Type.FILE_SYSTEM_ERROR, errMsg, null);
    }

    notifyListeners(getFormattedDone()+getLineBreak());
  }

  private boolean equalsOrDescendant(File file, File directory)
  {
    return file.equals(directory) ||
    Utils.isDescendant(file.toString(), directory.toString());
  }

  /**
   * This class is used to read the standard error and standard output of the
   * Stop process.
   *
   * When a new log message is found notifies the
   * UninstallProgressUpdateListeners of it. If an error occurs it also
   * notifies the listeners.
   *
   */
  private class StopReader
  {
    private boolean isFirstLine;

    /**
     * The protected constructor.
     * @param reader the BufferedReader of the stop process.
     * @param isError a boolean indicating whether the BufferedReader
     * corresponds to the standard error or to the standard output.
     */
    public StopReader(final BufferedReader reader,final boolean isError)
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

              line = reader.readLine();
            }
          } catch (IOException ioe)
          {
            String errorMsg = getThrowableMsg(errorTag, ioe);
            notifyListeners(errorMsg);

          } catch (Throwable t)
          {
            String errorMsg = getThrowableMsg(errorTag, t);
            notifyListeners(errorMsg);
          }
        }
      });
      t.start();
    }
  }

  /**
   * This class is used to get the files that are not binaries.  This is
   * required to know which are the files that can be deleted directly and which
   * not.
   */
  class InstallationFilesToDeleteFilter implements FileFilter
  {
    File librariesFile = new File(getLibrariesPath());

    File installationPath = new File(Utils.getInstallPathFromClasspath());
    /**
     * {@inheritDoc}
     */
    public boolean accept(File file)
    {
      boolean[] uData = {
          userData.getRemoveLibrariesAndTools(),
          userData.getRemoveLibrariesAndTools(),
          userData.getRemoveDatabases(),
          userData.getRemoveLogs(),
          userData.getRemoveConfigurationAndSchema(),
          userData.getRemoveBackups(),
          userData.getRemoveLDIFs()
      };

      String[] parentFiles = {
          getLibrariesPath(),
          getBinariesPath(),
          getDatabasesPath(),
          getLogsPath(),
          getConfigPath(),
          getBackupsPath(),
          getLDIFsPath()
      };

     boolean accept =
          !installationPath.equals(file)&&
          !equalsOrDescendant(file, librariesFile);

     for (int i=0; i<uData.length && accept; i++)
     {
       accept &= uData[i] ||
       !equalsOrDescendant(file, new File(parentFiles[i]));
     }

     return accept;
    }
  }
}
