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

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.ApplicationReturnCode;
import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ProgressUpdateListenerDelegate;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.Status;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.BuildInformation;
import org.opends.quicksetup.Application;
import org.opends.quicksetup.HistoricalRecord;
import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.FileManager;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reverts an installation from its current version to a prior version.
 */
public class Reverter extends Application implements CliApplication {

  static private final Logger LOG =
          Logger.getLogger(Reverter.class.getName());

  private ReversionProgressStep currentProgressStep =
          ReversionProgressStep.NOT_STARTED;

  private ReverterUserData userData;
  private ProgressMessageFormatter formatter;
  private ProgressUpdateListenerDelegate listenerDelegate;
  private ApplicationException runError;
  private ApplicationException runWarning;
  private Installation installation;
  private File tempBackupDir;
  private long historicalOperationId;
  private BuildInformation fromBuildInfo;
  private BuildInformation toBuildInfo;
  private boolean abort = false;

  /**
   * {@inheritDoc}
   */
  public UserData createUserData(Launcher launcher) throws UserDataException {
    ReverterUserData ud = null;
    if (launcher instanceof ReversionLauncher) {
      ud = new ReverterUserData();
      ReversionLauncher rl = (ReversionLauncher)launcher;
      File filesDir = null;
      if (rl.useMostRecentUpgrade()) {
        Installation install = getInstallation();
        File historyDir = install.getHistoryDirectory();
        if (historyDir.exists()) {
          FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return !Installation.HISTORY_LOG_FILE_NAME.equals(name);
            }
          };
          String[] childNames = historyDir.list(filter);
          if (childNames != null && childNames.length > 0) {

            // The directories beneath 'history' are named according
            // to the system time at which they were generated.
            // Go through the directory names in order of most
            // recent to oldest looking for the first one that
            // looks like a backup directory
            Arrays.sort(childNames);
            for (String childName : childNames) {
              File b = new File(historyDir, childName);
              File d = new File(b, Installation.HISTORY_BACKUP_FILES_DIR_NAME);
              if (isFilesDirectory(d)) {
                filesDir = d;
                break;
              }
            }

          } else {
            throw new UserDataException(null,
                    INFO_REVERT_ERROR_EMPTY_HISTORY_DIR.get());
          }
        } else {
          throw new UserDataException(null,
                  INFO_REVERT_ERROR_NO_HISTORY_DIR.get());
        }
      } else {
        filesDir = rl.getFilesDirectory();

        if (filesDir != null) {
          // Automatically append the 'filesDir' subdirectory if necessary
          if (!filesDir.getName().
                  endsWith(Installation.HISTORY_BACKUP_FILES_DIR_NAME)) {
            filesDir = new File(filesDir,
                    Installation.HISTORY_BACKUP_FILES_DIR_NAME);
          }
        } else {
          StringBuilder sb = new StringBuilder()
                  .append("-")
                  .append(ReversionLauncher.DIRECTORY_OPTION_SHORT)
                  .append("/--")
                  .append(ReversionLauncher.DIRECTORY_OPTION_LONG)
                  .append(", -")
                  .append(ReversionLauncher.MOST_RECENT_OPTION_SHORT)
                  .append("/--")
                  .append(ReversionLauncher.MOST_RECENT_OPTION_LONG);
          throw new UserDataException(null,
                  INFO_REVERT_ERROR_NO_DIR.get(sb.toString()));
        }
      }
      if (validateFilesDirectory(filesDir)) {
        ud.setFilesDirectory(filesDir);
      }
    }
    return ud;
  }

  /**
   * {@inheritDoc}
   */
  public UserData getUserData() {
    return this.userData;
  }

  /**
   * {@inheritDoc}
   */
  public void setUserData(UserData userData) {
    if (userData instanceof ReverterUserData) {
      this.userData = (ReverterUserData)userData;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void setProgressMessageFormatter(ProgressMessageFormatter formatter) {
    this.formatter = formatter;
    this.listenerDelegate = new ProgressUpdateListenerDelegate(formatter);
  }

  /**
   * {@inheritDoc}
   */
  public ApplicationException getRunError() {
    return this.runError;
  }

  /**
   * {@inheritDoc}
   */
  public void addProgressUpdateListener(ProgressUpdateListener l) {
    listenerDelegate.addProgressUpdateListener(l);
  }

  /**
   * {@inheritDoc}
   */
  public void removeProgressUpdateListener(ProgressUpdateListener l) {
    listenerDelegate.removeProgressUpdateListener(l);
  }

  /**
   * {@inheritDoc}
   */
  public void notifyListeners(Integer ratio,
                              Message currentPhaseSummary,
                              Message newLogDetail) {
    listenerDelegate.notifyListeners(null,
            ratio,
            currentPhaseSummary,
            newLogDetail);
  }

  /**
   * {@inheritDoc}
   */
  public String getInstallationPath() {
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
    return this.currentProgressStep;
  }

  /**
   * {@inheritDoc}
   */
  public Integer getRatio(ProgressStep step) {
    Integer ratio = null;
    if (step instanceof ReversionProgressStep) {
      ratio = ((ReversionProgressStep)step).getProgress();
    }
    return ratio;
  }

  /**
   * {@inheritDoc}
   */
  public Message getSummary(ProgressStep step) {
    Message txt;
    if (step == ReversionProgressStep.FINISHED) {
      txt = getFinalSuccessMessage();
//    } else if (step == ReversionProgressStep.FINISHED_CANCELED) {
//      txt = getFinalCanceledMessage();
//    } else if (step == ReversionProgressStep.FINISHED_WITH_ERRORS) {
//      txt = getFinalErrorMessage();
//    } else if (step == ReversionProgressStep.FINISHED_WITH_WARNINGS) {
//      txt = getFinalWarningMessage();
    }
    else {
      txt = (((ReversionProgressStep) step).getSummaryMesssage());
    }
    return txt;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isFinished() {
    return getCurrentProgressStep() ==
            ReversionProgressStep.FINISHED
            || getCurrentProgressStep() ==
            ReversionProgressStep.FINISHED_WITH_ERRORS
            || getCurrentProgressStep() ==
            ReversionProgressStep.FINISHED_WITH_WARNINGS
            || getCurrentProgressStep() ==
            ReversionProgressStep.FINISHED_CANCELED;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isCancellable() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void cancel() {
    // not supported
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
   * {@inheritDoc}
   */
  public void run() {

    try {
      initialize();

      UserInteraction ui = userInteraction();
      if (ui != null) {
        Message cont = INFO_CONTINUE_BUTTON_LABEL.get();
        Message cancel = INFO_CANCEL_BUTTON_LABEL.get();

        String toBuildString = null;
        BuildInformation toBi = getToBuildInformation();
        if (toBi != null) {
          toBuildString = toBi.toString();
        } else {
          toBuildString = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
        }
        if (cancel.equals(ui.confirm( // TODO: i18n
                Message.raw("Confirm Reversion"),
                Message.raw("This installation will be reverted to version " +
                        toBuildString +
                        " using the files in " + getFilesDirectory() + "."),
                Message.raw("Confirm"),
                UserInteraction.MessageType.WARNING,
                new Message[] { cont, cancel },
                cont))) {
          throw new ApplicationException(
              ApplicationReturnCode.ReturnCode.CANCELLED,
              INFO_REVERSION_CANCELED.get(), null);
        }
      }

      stopServer();
      revertFiles();
    } catch (Throwable e) {
      if (!(e instanceof ApplicationException)) {
        runError = new ApplicationException(
            ApplicationReturnCode.ReturnCode.BUG,
                Message.raw(e.getLocalizedMessage()), e);
      } else {
        runError = (ApplicationException)e;
      }
    } finally {
      end();
    }
  }

  private void setCurrentProgressStep(ReversionProgressStep step) {
    this.currentProgressStep = step;
    int progress = step.getProgress();
    Message msg = getSummary(step);
    notifyListeners(progress, msg, formatter.getFormattedProgress(msg));
  }

  private void initialize() throws ApplicationException {
    this.historicalOperationId =
      writeInitialHistoricalRecord(
              getFromBuildInformation(),
              getToBuildInformation());
  }

  private void stopServer() throws ApplicationException {
    Installation installation = getInstallation();
    Status status = installation.getStatus();
    if (status.isServerRunning()) {
      setCurrentProgressStep(ReversionProgressStep.STOPPING_SERVER);
      ServerController sc = new ServerController(installation);
      sc.stopServer(true);
    }
  }

  private void revertFiles() throws ApplicationException {
    backupFilesytem();
    revertComponents();
  }

  private void backupFilesytem() throws ApplicationException {
    try {
      File filesBackupDirectory = getTempBackupDirectory();
      FileManager fm = new FileManager();
      File root = getInstallation().getRootDirectory();
      FileFilter filter = new UpgradeFileFilter(root);
      for (String fileName : root.list()) {
        File f = new File(root, fileName);
        //fm.copyRecursively(f, filesBackupDirectory,
        fm.move(f, filesBackupDirectory, filter);
      }
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_BACKUP_FILESYSTEM.get(),
              e);
    }
  }

  private void revertComponents() throws ApplicationException {
    try {
      File stageDir = getFilesDirectory();
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
      LOG.log(Level.INFO, "reverted bits to " +
              installation.getBuildInformation(false));

    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_UPGRADING_COMPONENTS.get(), e);
    }
  }

  private File getFilesDirectory()
          throws ApplicationException, IOException {
    return userData.getFilesDirectory();
  }

  private boolean validateFilesDirectory(File filesDir)
          throws UserDataException
  {
    if (filesDir == null) {
      throw new UserDataException(null,
              INFO_REVERT_ERROR_NULL_FILES_DIR.get());
    } else if (!filesDir.isDirectory()) {
      throw new UserDataException(null,
              INFO_REVERT_ERROR_NOT_DIR_FILES_DIR.get());
    } else if (!isFilesDirectory(filesDir)) {
      throw new UserDataException(null,
              INFO_REVERT_ERROR_NOT_DIR_FILES_DIR.get());
    }
    return true;
  }

  private boolean isFilesDirectory(File filesDir) {
    boolean isFilesDir = false;
    if (filesDir != null && filesDir.isDirectory()) {
      String[] children = filesDir.list();
      Set<String> cs = new HashSet<String>(Arrays.asList(children));

      // TODO:  more testing of file dir
      isFilesDir = cs.contains(Installation.CONFIG_PATH_RELATIVE) &&
              cs.contains(Installation.LIBRARIES_PATH_RELATIVE);
    }
    return isFilesDir;
  }

  private void end() {
    try {
      HistoricalRecord.Status status;
      String note = null;
      if (runError == null && !abort) {
        status = HistoricalRecord.Status.SUCCESS;
      } else {
        if (abort) {
          status = HistoricalRecord.Status.CANCEL;
        } else {
          status = HistoricalRecord.Status.FAILURE;
          note = runError.getLocalizedMessage();
        }

        // Abort the reversion and put things back like we found it
        LOG.log(Level.INFO, "canceling reversion");
        ProgressStep lastProgressStep = currentProgressStep;
        setCurrentProgressStep(ReversionProgressStep.ABORT);
        abort(lastProgressStep);
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "cancelation complete");
      }

      LOG.log(Level.INFO, "cleaning up after reversion");
      setCurrentProgressStep(ReversionProgressStep.CLEANUP);
      cleanup();
      notifyListeners(getFormattedDoneWithLineBreak());
      LOG.log(Level.INFO, "clean up complete");


      // Write a record in the log file indicating success/failure
      LOG.log(Level.INFO, "recording history");
      setCurrentProgressStep(ReversionProgressStep.RECORDING_HISTORY);
      writeHistoricalRecord(historicalOperationId,
              getFromBuildInformation(),
              getToBuildInformation(),
              status,
              note);

      notifyListeners(getFormattedDoneWithLineBreak());
      LOG.log(Level.INFO, "history recorded");
      notifyListeners(
              new MessageBuilder(
                INFO_GENERAL_SEE_FOR_HISTORY.get(
                     Utils.getPath(getInstallation().getHistoryLogFile())))
              .append(getLineBreak()).toMessage());
    } catch (ApplicationException e) {
      notifyListeners(getFormattedDoneWithLineBreak());
      LOG.log(Level.INFO, "Error cleaning up after upgrade.", e);
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
      setCurrentProgressStep(ReversionProgressStep.FINISHED_CANCELED);
    } else if (runError != null) {
      LOG.log(Level.INFO, "upgrade completed with errors", runError);
      notifyListeners(getFormattedErrorWithLineBreak(runError,true));
      notifyListeners(getLineBreak());
      setCurrentProgressStep(ReversionProgressStep.FINISHED_WITH_ERRORS);
      notifyListeners(getLineBreak());
    } else if (runWarning != null) {
      LOG.log(Level.INFO, "upgrade completed with warnings");
      Message warningText = runWarning.getMessageObject();

      // By design, the warnings are written as errors to the details section
      // as errors.  Warning markup is used surrounding the main message
      // at the end of progress.
      notifyListeners(getFormattedErrorWithLineBreak(warningText, true));
      notifyListeners(getLineBreak());
      setCurrentProgressStep(ReversionProgressStep.FINISHED_WITH_WARNINGS);
      notifyListeners(formatter.getLineBreak());

    } else {
      LOG.log(Level.INFO, "reversion completed successfully");
      setCurrentProgressStep(ReversionProgressStep.FINISHED);
    }
  }

  /**
   * Abort this reversion and repair the installation.
   *
   * @param lastStep ProgressStep indicating how much work we will have to
   *                 do to get the installation back like we left it
   * @throws ApplicationException of something goes wrong
   */
  private void abort(ProgressStep lastStep) throws ApplicationException {

    // This can be used to bypass the aborted reversion cleanup
    // process so that an autopsy can be performed on the
    // crippled server.
    if ("true".equals(System.getProperty(Upgrader.SYS_PROP_NO_ABORT))) {
      return;
    }

    ReversionProgressStep lastReversionStep = (ReversionProgressStep) lastStep;
    EnumSet<ReversionProgressStep> stepsStarted =
            EnumSet.range(ReversionProgressStep.NOT_STARTED, lastReversionStep);

    if (stepsStarted.contains(ReversionProgressStep.REVERTING_FILESYSTEM)) {

      // Files were copied from the reversion directory to the current
      // directory.  Repair things by overwriting file in the
      // root with those that were copied to the backup directory
      // during revertFiles()

      File root = getInstallation().getRootDirectory();
      File backupDirectory;
      try {
        backupDirectory = getTempBackupDirectory();
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
            notifyListeners(INFO_ERROR_RESTORING_FILE.get(Utils.getPath(f),
                    Utils.getPath(root)));
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

  private void cleanup() {
    // TODO:
  }

  /**
   * Gets the directory that will be used to store the bits that this
   * reversion operation will replace.  The bits are stored in case there
   * is a problem with this reversion in which case they can be restored.
   *
   * @return File directory where the unreverted bits will be stored.
   */
  private File getTempBackupDirectory()
          throws IOException, ApplicationException
  {
    if (tempBackupDir == null) {
      tempBackupDir = new File(getInstallation().getTemporaryDirectory(),
              Installation.HISTORY_BACKUP_FILES_DIR_NAME);
      if (tempBackupDir.exists()) {
        FileManager fm = new FileManager();
        fm.deleteRecursively(tempBackupDir);
      }
      if (!tempBackupDir.mkdirs()) {
        throw new IOException("error creating files backup directory");
      }
    }
    return tempBackupDir;
  }

  private BuildInformation getFromBuildInformation() {
    if (fromBuildInfo == null) {
      if (currentProgressStep.ordinal() <
              ReversionProgressStep.REVERTING_FILESYSTEM.ordinal()) {
        try {
          fromBuildInfo = installation.getBuildInformation(false);
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Failed to obtain 'from' build information", e);
        }
      }
    }
    return fromBuildInfo;
  }

  private BuildInformation getToBuildInformation() {
    if (toBuildInfo == null) {
      if (currentProgressStep.ordinal() >
              ReversionProgressStep.REVERTING_FILESYSTEM.ordinal()) {
        try {
          toBuildInfo = installation.getBuildInformation(false);
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Failed to obtain 'from' build information", e);
        }
      } else {
        // TODO: try to determine build info from backed up bits
      }
    }
    return toBuildInfo;
  }

  private Message getFinalSuccessMessage() {
    Message txt;
    String installPath = Utils.getPath(getInstallation().getRootDirectory());
    String newVersion;
    try {
      BuildInformation bi = getInstallation().getBuildInformation();
      if (bi != null) {
        newVersion = bi.toString();
      } else {
        newVersion = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
      }
    } catch (ApplicationException e) {
      newVersion = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
    }
    txt = INFO_SUMMARY_REVERT_FINISHED_SUCCESSFULLY_CLI.get(
            formatter.getFormattedText(Message.raw(installPath)),
            newVersion);
    return txt;
  }


}
