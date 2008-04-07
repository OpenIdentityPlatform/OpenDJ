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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.ReturnCode;
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
import org.opends.quicksetup.CliUserInteraction;
import static org.opends.quicksetup.Installation.*;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.FileManager;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.text.DateFormat;

/**
 * Reverts an installation from its current version to a prior version.
 */
public class Reverter extends Application implements CliApplication {

  static private final Logger LOG =
          Logger.getLogger(Reverter.class.getName());

  private ReversionProgressStep currentProgressStep =
          ReversionProgressStep.NOT_STARTED;

  private ReverterUserData userData;
  private ProgressUpdateListenerDelegate listenerDelegate;
  private ApplicationException runError;
  private ApplicationException runWarning;
  private Installation installation;
  private Installation archiveInstallation;
  private File tempBackupDir;
  private long historicalOperationId;
  private BuildInformation fromBuildInfo;
  private BuildInformation archiveBuildInfo;
  private boolean abort = false;
  private boolean restartServer = false;
  private Stage stage = null;

  /**
   * {@inheritDoc}
   */
  public UserData createUserData(Launcher launcher) throws UserDataException {
    ReverterUserData ud = null;

    if (launcher instanceof UpgradeLauncher) {
      ud = new ReverterUserData();
      UpgradeLauncher rl = (UpgradeLauncher)launcher;
      File filesDir;
      if (rl.isInteractive()) {
        if (rl.isNoPrompt()) {
          StringBuilder sb = new StringBuilder()
                  .append("-")
                  .append(UpgradeLauncher.REVERT_ARCHIVE_OPTION_SHORT)
                  .append("/--")
                  .append(ReversionLauncher.REVERT_ARCHIVE_OPTION_LONG)
                  .append(", -")
                  .append(ReversionLauncher.REVERT_MOST_RECENT_OPTION_SHORT)
                  .append("/--")
                  .append(ReversionLauncher.REVERT_MOST_RECENT_OPTION_LONG);
          throw new UserDataException(null,
                  INFO_REVERT_ERROR_NO_DIR.get(sb.toString()));
        } else {
          CliUserInteraction ui = new CliUserInteraction();
          Message[] options = new Message[] {
                  INFO_REVERSION_TYPE_PROMPT_RECENT.get(),
                  INFO_REVERSION_TYPE_PROMPT_FILE.get()};
          if (options[0].equals(ui.confirm(
                  INFO_REVERT_CONFIRM_TITLE.get(),
                  INFO_REVERSION_TYPE_PROMPT.get(),
                  INFO_REVERT_CONFIRM_TITLE.get(),
                  UserInteraction.MessageType.QUESTION,
                  options, options[0])))
          {
            ud.setRevertMostRecent(true);
          } else {
            ud.setRevertMostRecent(false);

            // Present a list of reversion archive choices to the user.
            // In the future perhaps we might also allow them to type
            // freehand.
            File historyDir = getInstallation().getHistoryDirectory();
            if (historyDir != null && historyDir.exists()) {

              // Print a wait message, this could take a while
              System.out.println(formatter.getFormattedWithPoints(
                  INFO_REVERSION_DIR_WAIT.get()));

              String[] historyChildren = historyDir.list();
              Arrays.sort(historyChildren);
              List<File> raDirList = new ArrayList<File>();
              for (int i = historyChildren.length - 1; i >=0; i--) {
                File raDirCandidate = new File(historyDir, historyChildren[i]);
                if (isReversionFilesDirectory(raDirCandidate)) {
                  raDirList.add(raDirCandidate);
                }
              }
              File[] raDirs = raDirList.toArray(new File[raDirList.size()]);
              List<Message> raDirChoiceList = new ArrayList<Message>();
              for (File raDir : raDirs) {
                String name = raDir.getName();
                Message buildInfo = INFO_UPGRADE_BUILD_ID_UNKNOWN.get();
                Message date = INFO_GENERAL_UNKNOWN.get();
                try {
                  Installation i =
                          new Installation(appendFilesDirIfNeccessary(raDir));
                  BuildInformation bi = i.getBuildInformation();
                  buildInfo = Message.raw(bi.toString());
                } catch (Exception e) {
                  LOG.log(Level.INFO,
                          "Error determining archive version for " + name);
                }

                try {
                  Date d = new Date(Long.valueOf(name));
                  DateFormat df = DateFormat.getInstance();
                  date = Message.raw(df.format(d));
                } catch (Exception e) {
                  LOG.log(Level.INFO, "Error converting reversion archive " +
                          "name " + name + " to date helper");
                }
                MessageBuilder mb = new MessageBuilder(name);
                mb.append(" (");
                mb.append(INFO_REVERSION_DIR_FROM_UPGRADE.get(buildInfo, date));
                mb.append(")");
                raDirChoiceList.add(mb.toMessage());
              }
              Message[] raDirChoices =
                      raDirChoiceList.toArray(new Message[0]);
              if (raDirChoices.length > 0) {
                MenuBuilder<Integer> builder = new MenuBuilder<Integer>(ui);

                builder.setPrompt(INFO_REVERSION_DIR_PROMPT.get());

                for (int i=0; i<raDirChoices.length; i++)
                {
                  builder.addNumberedOption(raDirChoices[i],
                      MenuResult.success(i+1));
                }

                builder.setDefault(Message.raw(String.valueOf("1")),
                    MenuResult.success(1));

                Menu<Integer> menu = builder.toMenu();
                int resp;
                try
                {
                  MenuResult<Integer> m = menu.run();
                  if (m.isSuccess())
                  {
                    resp = m.getValue();
                  }
                  else
                  {
                    // Should never happen.
                    throw new RuntimeException();
                  }
                }
                catch (CLIException ce)
                {
                  resp = 1;
                  LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
                }

                File raDir = raDirs[resp - 1];
                raDir = appendFilesDirIfNeccessary(raDir);
                try {
                  ud.setReversionArchiveDirectory(
                          validateReversionArchiveDirectory(raDir));
                } catch (UserDataException ude) {
                  System.err.println(ude.getMessageObject());
                }
              } else {
                LOG.log(Level.INFO, "No archives in history dir");
                throw new UserDataException(null,
                        INFO_REVERT_ERROR_NO_HISTORY_DIR.get());
              }
            } else {
              LOG.log(Level.INFO, "History dir does not exist");
              throw new UserDataException(null,
                      INFO_REVERT_ERROR_NO_HISTORY_DIR.get());
            }
          }
        }
      } else if (rl.isRevertMostRecent()) {
        ud.setRevertMostRecent(true);
      } else {
        filesDir = rl.getReversionArchiveDirectory();
        filesDir = appendFilesDirIfNeccessary(filesDir);
        ud.setReversionArchiveDirectory(
                validateReversionArchiveDirectory(filesDir));
      }
      if (ud.isRevertMostRecent()) {
        Installation install = getInstallation();
        File historyDir = install.getHistoryDirectory();
        if (historyDir.exists()) {
          FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return !HISTORY_LOG_FILE_NAME.equals(name);
            }
          };
          String[] childNames = historyDir.list(filter);
          boolean found = false;
          if (childNames != null && childNames.length > 0) {

            // The directories beneath 'history' are named according
            // to the system time at which they were generated.
            // Go through the directory names in order of most
            // recent to oldest looking for the first one that
            // looks like a backup directory
            Arrays.sort(childNames);
            for (String childName : childNames) {
              File b = new File(historyDir, childName);
              File d = new File(b, HISTORY_BACKUP_FILES_DIR_NAME);
              if (isReversionFilesDirectory(d)) {
                found = true;
                ud.setReversionArchiveDirectory(d);
                break;
              }
            }
            if (!found)
            {
              throw new UserDataException(null,
                  INFO_REVERT_ERROR_INVALID_HISTORY_DIR.get());
            }
          } else {
            throw new UserDataException(null,
                    INFO_REVERT_ERROR_EMPTY_HISTORY_DIR.get());
          }
        } else {
          throw new UserDataException(null,
                  INFO_REVERT_ERROR_NO_HISTORY_DIR.get());
        }
      }
      ud.setQuiet(rl.isQuiet());
      ud.setInteractive(!rl.isNoPrompt());
      ud.setVerbose(rl.isVerbose());
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
    this.listenerDelegate = new ProgressUpdateListenerDelegate();
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
  public ReturnCode getReturnCode() {
    return null;
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
                      LOCKS_PATH_RELATIVE).exists()) {
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
      txt = (((ReversionProgressStep) step).getSummaryMessage());
    }
    return txt;
  }

  /**
   * Returns the progress message for a given progress step.
   * @param step the progress step.
   * @return the progress message for the provided step.
   */
  private Message getLogMsg(ReversionProgressStep step) {
    Message txt;
    if (step == ReversionProgressStep.FINISHED) {
      txt = getFinalSuccessMessage();
    } else if (step == ReversionProgressStep.FINISHED_CANCELED) {
      txt = step.getSummaryMessage();
    } else if (step == ReversionProgressStep.FINISHED_WITH_ERRORS) {
      txt = step.getSummaryMessage();
    } else if (step == ReversionProgressStep.FINISHED_WITH_WARNINGS) {
      txt = step.getSummaryMessage();
    }
    else
    {
      txt = step.getLogMsg(isVerbose());
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

      // Get the user to confirm if possible
      UserInteraction ui = userInteraction();
      if (ui != null) {
        Message cont = INFO_CONTINUE_BUTTON_LABEL.get();
        Message cancel = INFO_CANCEL_BUTTON_LABEL.get();

        String toBuildString;
        BuildInformation toBi = getArchiveBuildInformation();
        if (toBi != null) {
          toBuildString = toBi.toString();
        } else {
          if (getReversionFilesDirectory() == null)
          {
            throw new ApplicationException(
                ReturnCode.APPLICATION_ERROR,
                INFO_REVERT_ERROR_INVALID_HISTORY_DIR.get(), null);
          }
          toBuildString = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
        }
        if (cancel.equals(ui.confirm(
                INFO_REVERT_CONFIRM_TITLE.get(),
                INFO_REVERT_CONFIRM_PROMPT.get(
                        toBuildString,
                        Utils.getPath(getReversionFilesDirectory())),
                INFO_REVERT_CONFIRM_TITLE.get(),
                UserInteraction.MessageType.WARNING,
                new Message[] { cont, cancel },
                cont))) {
          throw new ApplicationException(
              ReturnCode.CANCELLED,
              INFO_REVERSION_CANCELED.get(), null);
        }
      }

      // Stop the server if necessary.  Task note as to whether the server
      // is running since we will leave it in that state when we are done.
      Installation installation = getInstallation();
      Status status = installation.getStatus();
      ServerController sc = new ServerController(installation);
      if (status.isServerRunning()) {
        restartServer = true;
        sc = new ServerController(installation);
        try {
          setCurrentProgressStep(ReversionProgressStep.STOPPING_SERVER);
          LOG.log(Level.INFO, "Stopping server");

          if (isVerbose())
          {
            notifyListeners(getTaskSeparator());
          }
          else
          {
            notifyListeners(getFormattedWithPoints(
                INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
          }

          sc.stopServer(!isVerbose());

          if (!isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
          else
          {
            notifyListeners(getLineBreak());
          }
        } catch (ApplicationException ae) {
          runError = ae;
          notifyListeners(getFormattedErrorWithLineBreak());
        }
      }

      try {
        setCurrentProgressStep(ReversionProgressStep.INITIALIZING);
        initialize();
        notifyListeners(getFormattedDoneWithLineBreak());
      } catch (ApplicationException ae) {
        LOG.log(Level.INFO, "Error initializing reversion", ae);
        notifyListeners(getFormattedErrorWithLineBreak());
        throw ae;
      }

      try {
        LOG.log(Level.INFO, "Reverting components");
        setCurrentProgressStep(ReversionProgressStep.REVERTING_FILESYSTEM);
        revertComponents();
        LOG.log(Level.INFO, "Finished reverting components");
        notifyListeners(getFormattedDoneWithLineBreak());
      } catch (ApplicationException ae) {
        LOG.log(Level.INFO, "Error reverting components", ae);
        notifyListeners(getFormattedErrorWithLineBreak());
        throw ae;
      }

      if (restartServer) {
        try {
          LOG.log(Level.INFO, "Restarting server");
          setCurrentProgressStep(ReversionProgressStep.STARTING_SERVER);
          if (isVerbose())
          {
            notifyListeners(getTaskSeparator());
          }
          else
          {
            notifyListeners(getFormattedWithPoints(
                INFO_PROGRESS_STARTING_NON_VERBOSE.get()));
          }
          sc.startServer(!isVerbose());
          if (!isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
          else
          {
            notifyListeners(getLineBreak());
          }
        } catch (ApplicationException ae) {
          runError = ae;
          notifyListeners(getFormattedErrorWithLineBreak());
        }
      }

    } catch (Throwable e) {
      if (!(e instanceof ApplicationException)) {
        runError = new ApplicationException(
            ReturnCode.BUG,
                Message.raw(e.getLocalizedMessage()), e);
      } else {
        runError = (ApplicationException)e;
        abort = ReturnCode.CANCELLED.equals(
                ((ApplicationException)e).getType());
      }
    } finally {
      end();
    }
  }

  private void setCurrentProgressStep(ReversionProgressStep step) {
    this.currentProgressStep = step;
    int progress = step.getProgress();
    Message summary = getSummary(step);
    Message log = getLogMsg(step);
    if (step.logRequiresPoints(isVerbose()) && (log != null))
    {
      log = getFormattedWithPoints(log);
    }
    notifyListeners(progress, summary, log);
  }

  private void initialize() throws ApplicationException {
    this.historicalOperationId =
      writeInitialHistoricalRecord(
              getFromBuildInformation(),
              getArchiveBuildInformation());
    insureRevertability();
    backupCurrentInstallation();
  }

  private void backupCurrentInstallation() throws ApplicationException {
    LOG.log(Level.INFO, "Backing up filesystem");
    try {
      File filesBackupDirectory = getTempBackupDirectory();
      FileManager fm = new FileManager();
      File root = getInstallation().getRootDirectory();
      FileFilter filter = new RevertFileFilter(root);
      for (String fileName : root.list()) {
        File f = new File(root, fileName);

        // Replacing a Windows bat file while it is running with a different
        // version leads to unpredictable behavior so we make a special case
        // here and during the upgrade components step.
        if (Utils.isWindows() &&
                fileName.equals(WINDOWS_UPGRADE_FILE_NAME)) {
          continue;
        }

        fm.move(f, filesBackupDirectory, filter);
      }
      LOG.log(Level.INFO, "Finished backing up filesystem");
    } catch (ApplicationException ae) {
      LOG.log(Level.INFO, "Error backing up filesystem", ae);
      throw ae;
    } catch (Exception e) {
      LOG.log(Level.INFO, "Error backing up filesystem", e);
      throw new ApplicationException(
          ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_BACKUP_FILESYSTEM.get(),
              e);
    }
  }

  private void revertComponents() throws ApplicationException {
    try {
      Stage stage = getStage();
      Installation installation = getInstallation();
      File root = installation.getRootDirectory();
      stage.move(root, new RevertFileFilter(getReversionFilesDirectory()));

      // The bits should now be of the new version.  Have
      // the installation update the build information so
      // that it is correct.
      LOG.log(Level.INFO, "Reverted bits to " +
              installation.getBuildInformation(false));

    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_UPGRADING_COMPONENTS.get(), e);
    }
  }

  private File getReversionFilesDirectory()
          throws ApplicationException, IOException {
    return userData.getReversionArchiveDirectory();
  }

  private File validateReversionArchiveDirectory(File filesDir)
          throws UserDataException
  {
    if (filesDir == null) {
      throw new UserDataException(null,
              INFO_REVERT_ERROR_NULL_FILES_DIR.get());
    } else if (!filesDir.isDirectory()) {
      throw new UserDataException(null,
              INFO_REVERT_ERROR_NOT_DIR_FILES_DIR.get());
    } else if (!isReversionFilesDirectory(filesDir)) {
      throw new UserDataException(null,
              INFO_REVERT_ERROR_INVALID_FILES_DIR.get());
    }
    return filesDir;
  }

  private boolean isReversionFilesDirectory(File filesDir) {
    filesDir = appendFilesDirIfNeccessary(filesDir);
    boolean isFilesDir = false;
    if (filesDir != null && filesDir.exists() && filesDir.isDirectory()) {
      String[] children = filesDir.list();
      Set<String> cs = new HashSet<String>(Arrays.asList(children));

      // TODO:  more testing of file dir
      isFilesDir = cs.contains(CONFIG_PATH_RELATIVE) &&
              cs.contains(LIBRARIES_PATH_RELATIVE);
    }
    return isFilesDir;
  }

  private void end() {
    try {
      HistoricalRecord.Status status;
      String note = null;
      if (runError == null && !abort) {
        status = HistoricalRecord.Status.SUCCESS;

        // Since everything went OK, delete the archive
        LOG.log(Level.INFO, "Cleaning up after reversion");
        setCurrentProgressStep(ReversionProgressStep.CLEANUP);
        deleteArchive();
        deleteBackup();
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "Clean up complete");

      } else {
        if (abort) {
          status = HistoricalRecord.Status.CANCEL;
        } else {
          status = HistoricalRecord.Status.FAILURE;
          note = runError.getLocalizedMessage();
        }

        // Abort the reversion and put things back like we found it
        LOG.log(Level.INFO, "Canceling reversion");
        ProgressStep lastProgressStep = currentProgressStep;
        setCurrentProgressStep(ReversionProgressStep.ABORT);
        abort(lastProgressStep);
        notifyListeners(getFormattedDoneWithLineBreak());
        LOG.log(Level.INFO, "Cancelation complete");
      }

      // Remove the lib directory temporarily created by the
      // launch script with which the program is running
      deleteReversionLib();

      // Write a record in the log file indicating success/failure
      LOG.log(Level.INFO, "Recording history");
      setCurrentProgressStep(ReversionProgressStep.RECORDING_HISTORY);
      writeHistoricalRecord(historicalOperationId,
              getFromBuildInformation(),
              getArchiveBuildInformation(),
              status,
              note);

      notifyListeners(getFormattedDoneWithLineBreak());
      LOG.log(Level.INFO, "History recorded");
      notifyListeners(
              new MessageBuilder(
                INFO_GENERAL_SEE_FOR_HISTORY.get(
                     Utils.getPath(getInstallation().getHistoryLogFile())))
              .append(getLineBreak()).toMessage());

      try {
        Stage stage = getStage();
        List<Message> stageMessages = stage.getMessages();
        for (Message m : stageMessages) {
          notifyListeners(m);
        }
      } catch (IOException e) {
        LOG.log(Level.INFO, "failed to access stage", e);
      }

    } catch (ApplicationException e) {
      notifyListeners(getFormattedDoneWithLineBreak());
      LOG.log(Level.INFO, "Error cleaning up after reversion.", e);
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
      LOG.log(Level.INFO, "reversion canceled by user");
      setCurrentProgressStep(ReversionProgressStep.FINISHED_CANCELED);
    } else if (runError != null) {
      LOG.log(Level.INFO, "reversion completed with errors", runError);
      notifyListeners(getFormattedErrorWithLineBreak(runError,true));
      notifyListeners(getLineBreak());
      setCurrentProgressStep(ReversionProgressStep.FINISHED_WITH_ERRORS);
      notifyListeners(getLineBreak());
    } else if (runWarning != null) {
      LOG.log(Level.INFO, "reversion completed with warnings");
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

      } catch (IOException e) {
        LOG.log(Level.INFO, "Error getting backup directory", e);
      }
    }

  }

  /**
   * Deletes the archived backup to which we reverted.
   */
  private void deleteArchive() {
    FileManager fm = new FileManager();
    try {
      fm.deleteRecursively(getReversionFilesDirectory());
      File parent = getReversionFilesDirectory().getParentFile();
      if (Utils.directoryExistsAndIsEmpty(parent.getAbsolutePath()))
      {
        fm.deleteRecursively(parent);
      }
    } catch (Exception e) {
      // ignore; this is best effort
      LOG.log(Level.WARNING, "Error: "+e, e);
    }
  }

  /**
   * Deletes the backup of the current installation.
   */
  private void deleteBackup() {
    FileManager fm = new FileManager();
    try {
      fm.deleteRecursively(getTempBackupDirectory());
    } catch (Exception e) {
      // ignore; this is best effort
      LOG.log(Level.WARNING, "Error: "+e, e);
    }
  }

  /**
   * Delete the library with which this reversion is currently
   * running.
   */
  private void deleteReversionLib() {
    FileManager fm = new FileManager();
    try {
      File tmpDir = getInstallation().getTemporaryDirectory();
      File revertLibDir = new File(tmpDir, "revert");
      fm.deleteRecursively(
              revertLibDir, null,
              FileManager.DeletionPolicy.DELETE_ON_EXIT);
    } catch (Exception e) {
      // ignore; this is best effort
    }
  }

  /**
   * Gets the directory that will be used to store the bits that this
   * reversion operation will replace.  The bits are stored in case there
   * is a problem with this reversion in which case they can be restored.
   *
   * @return File directory where the unreverted bits will be stored.
   * @throws java.io.IOException if something goes wrong
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong
   */
  private File getTempBackupDirectory()
          throws IOException, ApplicationException
  {
    if (tempBackupDir == null) {
      tempBackupDir = new File(getInstallation().getTemporaryDirectory(),
              HISTORY_BACKUP_FILES_DIR_NAME);
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

  private BuildInformation getArchiveBuildInformation() {
    if (archiveBuildInfo == null) {
      if (currentProgressStep.ordinal() >
              ReversionProgressStep.REVERTING_FILESYSTEM.ordinal()) {
        try {
          archiveBuildInfo = installation.getBuildInformation(false);
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Failed to obtain archived build information " +
                  "from reverted files", e);
        }
      } else {

        Installation archiveInstall;
        try {
          archiveInstall = getArchiveInstallation();
          archiveBuildInfo = archiveInstall.getBuildInformation();
        } catch (Exception e) {
          LOG.log(Level.INFO, "Failed to obtain archived build information " +
                  "from archived files", e);
        }
      }
    }
    return archiveBuildInfo;
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

  /**
   * Given the current information, determines whether or not
   * a reversion from the current version to the archived version
   * is possible.  Reversion may not be possible due to 'flag
   * day' types of changes to the codebase.
   * @throws org.opends.quicksetup.ApplicationException if revertability
   *         cannot be insured.
   */
  private void insureRevertability() throws ApplicationException {
    BuildInformation currentVersion;
    BuildInformation newVersion;
    try {
      currentVersion = getInstallation().getBuildInformation();
    } catch (ApplicationException e) {
      LOG.log(Level.INFO, "Error getting build information for " +
              "current installation", e);
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_DETERMINING_CURRENT_BUILD.get(), e);
    }

    try {
      Installation revInstallation = getArchiveInstallation();
      newVersion = revInstallation.getBuildInformation();
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      LOG.log(Level.INFO, "Error getting build information for " +
              "staged installation", e);
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_DETERMINING_REVERSION_BUILD.get(), e);
    }

    if (currentVersion != null && newVersion != null) {
      ReversionIssueNotifier uo = new ReversionIssueNotifier(
              userInteraction(), currentVersion, newVersion);
      uo.notifyUser();
      if (uo.noServerStartFollowingOperation()) {
        // Some issue dicatates that we don't try and restart the server
        // after this operation.  It may be that the databases are no
        // longer readable after the reversion or something equally earth
        // shattering.
        getUserData().setStartServer(false);
      }
    } else {
      LOG.log(Level.INFO, "Did not run reversion issue notifier due to " +
              "incomplete build information");
    }
  }

  private Installation getArchiveInstallation()
          throws ApplicationException, IOException
  {
    if (archiveInstallation == null) {
      File revFiles = getReversionFilesDirectory();
      archiveInstallation = new Installation(revFiles);
    }
    return archiveInstallation;
  }

  private File appendFilesDirIfNeccessary(File archiveDir) {
    if (archiveDir != null) {
      // Automatically append the 'filesDir' subdirectory if necessary
      if (!archiveDir.getName().
              endsWith(HISTORY_BACKUP_FILES_DIR_NAME)) {
        archiveDir = new File(archiveDir,
                HISTORY_BACKUP_FILES_DIR_NAME);
      }
    }
    return archiveDir;
  }

  private Stage getStage() throws IOException, ApplicationException {
    if (this.stage == null) {
      this.stage = new Stage(getReversionFilesDirectory());
    }
    return stage;
  }

}
