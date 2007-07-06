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

import org.opends.quicksetup.*;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ZipExtractor;
import org.opends.quicksetup.util.FileManager;
import org.opends.quicksetup.util.ProgressMessageFormatter;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BuildExtractor unzips an OpenDS installation package (.zip file) from a user
 * specified location into the current builds staging directory.  This Java
 * program handles this task so that we don't need to rely on the operating
 * system's tools for managing zip files.
 *
 * This tool is a stand-alone program since it is run in preparation for a
 * off line upgrade and runs using the current program's jars rather than
 * the new build's jars as is manditory for the Upgrader itself.  Since this
 * tool itself is run using the old bits prior to upgrade and the upgrade
 * itself is dependent upon this tool, it should be kept simple and stable
 * to insure that the upgrade will work.
 */
public class BuildExtractor extends UpgradeLauncher implements CliApplication {

  static private final Logger LOG =
          Logger.getLogger(BuildExtractor.class.getName());

  /**
   * Creates and run a BuildExtractor using command line arguments.
   * @param args String[] command line arguments
   */
  public static void main(String[] args) {
    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(
                      UpgradeLauncher.LOG_FILE_PREFIX + "ext-",
                      UpgradeLauncher.LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println(
              ResourceProvider.getInstance().getMsg("error-initializing-log"));
      t.printStackTrace();
    }
    Launcher launcher = new BuildExtractor(args);
    int returnCode = launcher.launch();
    System.exit(returnCode);
  }

  private BuildExtractorCliHelper helper = new BuildExtractorCliHelper();

  private UpgradeUserData userData;

  private boolean finished;

  private ApplicationException error;

  private BuildExtractor(String[] args) {
    super(args);
  }

  /**
   * Executes this build extractor.  First and attempt is made to extract the
   * build file name from the command line arguments.  If no such file has been
   * specified this program simply exits with a return code of 0.  If such a
   * file has been specified this program performs a certain amount of
   * verification on the file.  If the verification fails this program prints
   * a message and exits with with a return code of 1 meaning that the upgrade
   * process should end.  If verification succeeeds this program unzips its
   * contents into the current build's staging are and exits with return code 0.
   */
  public void run() {
    try {
      UpgradeUserData uud = (UpgradeUserData)getUserData();
      File buildFile = uud.getInstallPackage();
      if (buildFile != null) {
        LOG.log(Level.INFO, "expanding zip file " + buildFile.getPath());
        File stageDirectory = initStageDirectory();
        ZipExtractor extractor = new ZipExtractor(buildFile);
        extractor.extract(stageDirectory);
        LOG.log(Level.INFO, "extraction finished");
        Installation installation = new Installation(stageDirectory);
        if (!installation.isValid()) {
          LOG.log(Level.INFO, "extraction produed an invalid OpenDS" +
                  "installation: " + installation.getInvalidityReason());
          String invalidMsg = getMsg("build-extractor-file-invalid",
                  Utils.getPath(buildFile),
                  installation.getInvalidityReason());
          error = new ApplicationException(
                ApplicationException.Type.APPLICATION,
                  invalidMsg, null);
          System.err.println(invalidMsg);
        }
      }
    } catch (Throwable t) {
      LOG.log(Level.INFO, "unexpected error extracting build", t);
      String reason = t.getLocalizedMessage();
      error = new ApplicationException(ApplicationException.Type.APPLICATION,
                getMsg("build-extractor-error", reason), t);
      System.err.println(reason);
    } finally   {
      finished = true;
    }
  }

  private File initStageDirectory() throws ApplicationException {
    File stageDir;
    Installation installation = new Installation(getInstallationPath());
    stageDir = installation.getTemporaryUpgradeDirectory();
    if (stageDir.exists()) {
      FileManager fm = new FileManager();
      fm.deleteRecursively(stageDir);
    }
    if (!stageDir.mkdirs()) {
      String msg = getMsg("error-failed-to-create-stage-directory",
              Utils.getPath(stageDir));
      throw ApplicationException.createFileSystemException(msg, null);
    }
    LOG.log(Level.INFO, "stage directory " + stageDir.getPath());
    return stageDir;
  }

  /**
   * {@inheritDoc}
   */
  protected CliApplication createCliApplication() {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public String getInstallationPath() {
    return Utils.getInstallPathFromClasspath();
  }

  /**
   * {@inheritDoc}
   */
  public UserData createUserData(String[] args, CurrentInstallStatus status)
          throws UserDataException
  {
    return helper.createUserData(args);
  }

  /**
   * {@inheritDoc}
   */
  public UserData getUserData() {
    return userData;
  }

  /**
   * {@inheritDoc}
   */
  public void setUserData(UserData userData) {
    if (userData instanceof UpgradeUserData) {
      this.userData = (UpgradeUserData)userData;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void setProgressMessageFormatter(ProgressMessageFormatter formatter) {
    // ignore
  }

  /**
   * {@inheritDoc}
   */
  public boolean isFinished() {
    return finished;
  }

  /**
   * {@inheritDoc}
   */
  public ApplicationException getException() {
    return error;
  }

  /**
   * {@inheritDoc}
   */
  public void addProgressUpdateListener(ProgressUpdateListener l) {
    // ignored;  no progress messages
  }

  /**
   * {@inheritDoc}
   */
  public void removeProgressUpdateListener(ProgressUpdateListener l) {
    // ignored;  no progress messages
  }

  /**
   * {@inheritDoc}
   */
  public void notifyListeners(Integer ratio, String currentPhaseSummary,
                              String newLogDetail) {
    // ignored;  no progress messages
  }
}
