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

package org.opends.quicksetup.installer.webstart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ApplicationReturnCode;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ZipExtractor;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.FileManager;
import org.opends.server.util.SetupUtils;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This is an implementation of the Installer class that is used to install
 * the Directory Server using Web Start.
 *
 * It just takes a UserData object and based on that installs OpenDS.
 *
 *
 * This object has as parameter a WebStartDownloader object that is downloading
 * some jar files.  Until the WebStartDownloader has not finished downloading
 * the jar files will be on the ProgressStep.DOWNLOADING step because
 * we require all the jar files to be downloaded in order to install and
 * configure the Directory Server.
 *
 * Based on the Java properties set through the QuickSetup.jnlp file this
 * class will retrieve the zip file containing the install, unzip it and extract
 * it in the path specified by the user and that is contained in the
 * UserData object.
 *
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The notification
 * will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 *
 */
public class WebStartInstaller extends Installer {
  private HashMap<InstallProgressStep, Integer> hmRatio =
      new HashMap<InstallProgressStep, Integer>();

  private HashMap<InstallProgressStep, Message> hmSummary =
      new HashMap<InstallProgressStep, Message>();

  private static final Logger LOG =
    Logger.getLogger(WebStartInstaller.class.getName());

  /**
   * WebStartInstaller constructor.
   */
  public WebStartInstaller()
  {
    initLoader();
    setCurrentProgressStep(InstallProgressStep.NOT_STARTED);
  }

  /**
   * Actually performs the install in this thread.  The thread is blocked.
   *
   */
  public void run()
  {
    initMaps();
    PrintStream origErr = System.err;
    PrintStream origOut = System.out;
    try
    {
      PrintStream err = new ErrorPrintStream();
      PrintStream out = new OutputPrintStream();
      System.setErr(err);
      System.setOut(out);

      setCurrentProgressStep(InstallProgressStep.DOWNLOADING);

      checkAbort();

      InputStream in =
          getZipInputStream(getRatio(InstallProgressStep.EXTRACTING));

      setCurrentProgressStep(InstallProgressStep.EXTRACTING);
      notifyListeners(getTaskSeparator());

      checkAbort();

      createParentDirectoryIfRequired();
      extractZipFiles(in, getRatio(InstallProgressStep.EXTRACTING),
          getRatio(InstallProgressStep.CONFIGURING_SERVER));

      try
      {
        in.close();
      }
      catch (Throwable t)
      {
        LOG.log(Level.INFO, "Error closing zip input stream: "+t, t);
      }

      checkAbort();

      setCurrentProgressStep(InstallProgressStep.CONFIGURING_SERVER);
      notifyListeners(getTaskSeparator());

      // Write java home before calling Installation class.  The installation
      // class does a call to start-ds to get information about the build.
      writeJavaHome();
      setInstallation(new Installation(getUserData().getServerLocation()));

      checkAbort();

      setCurrentProgressStep(InstallProgressStep.CONFIGURING_SERVER);
      configureServer();

      checkAbort();

      createData();

      checkAbort();

      if (Utils.isWindows() && getUserData().getEnableWindowsService())
      {
          notifyListeners(getTaskSeparator());
          setCurrentProgressStep(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
          enableWindowsService();
          checkAbort();
      }

      if (mustStart())
      {
        notifyListeners(getTaskSeparator());
        setCurrentProgressStep(InstallProgressStep.STARTING_SERVER);
        new ServerController(this).startServer();
        checkAbort();
      }

      if (mustCreateAds())
      {
        notifyListeners(getTaskSeparator());
        setCurrentProgressStep(InstallProgressStep.CONFIGURING_ADS);
        updateADS();
        checkAbort();
      }

      if (mustConfigureReplication())
      {
        notifyListeners(getTaskSeparator());
        setCurrentProgressStep(InstallProgressStep.CONFIGURING_REPLICATION);
        configureReplication();
        checkAbort();
      }

      if (mustInitializeSuffixes())
      {
        notifyListeners(getTaskSeparator());
        setCurrentProgressStep(
            InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
        initializeSuffixes();
        checkAbort();
      }

      if (mustStop())
      {
        notifyListeners(getTaskSeparator());
        setCurrentProgressStep(InstallProgressStep.STOPPING_SERVER);
        new ServerController(this).stopServer();
      }

      checkAbort();
      updateSummaryWithServerState(hmSummary);
      setCurrentProgressStep(InstallProgressStep.FINISHED_SUCCESSFULLY);
      notifyListeners(null);

    } catch (ApplicationException ex)
    {
      if (ApplicationReturnCode.ReturnCode.CANCELLED.equals(ex.getType())) {
        uninstall();
        setCurrentProgressStep(InstallProgressStep.FINISHED_CANCELED);
        notifyListeners(null);
      } else {
        // Stop the server if necessary
        Installation installation = getInstallation();
        if (installation.getStatus().isServerRunning()) {
          try {
            new ServerController(installation).stopServer(true);
          } catch (Throwable t) {
            LOG.log(Level.INFO, "error stopping server", t);
          }
        }
        notifyListeners(getLineBreak());
        notifyListenersOfLog();
        updateSummaryWithServerState(hmSummary);
        setCurrentProgressStep(InstallProgressStep.FINISHED_WITH_ERROR);
        Message html = getFormattedError(ex, true);
        notifyListeners(html);
        LOG.log(Level.SEVERE, "Error installing.", ex);
      }
    }
    catch (Throwable t)
    {
      // Stop the server if necessary
      Installation installation = getInstallation();
      if (installation.getStatus().isServerRunning()) {
        try {
          new ServerController(installation).stopServer(true);
        } catch (Throwable t2) {
          LOG.log(Level.INFO, "error stopping server", t2);
        }
      }
      notifyListeners(getLineBreak());
      notifyListenersOfLog();
      updateSummaryWithServerState(hmSummary);
      setCurrentProgressStep(InstallProgressStep.FINISHED_WITH_ERROR);
      ApplicationException ex = new ApplicationException(
          ApplicationReturnCode.ReturnCode.BUG,
          Utils.getThrowableMsg(INFO_BUG_MSG.get(), t), t);
      Message msg = getFormattedError(ex, true);
      notifyListeners(msg);
      LOG.log(Level.SEVERE, "Error installing.", t);
    }
    System.setErr(origErr);
    System.setOut(origOut);
  }

  /**
   * {@inheritDoc}
   */
  public Integer getRatio(ProgressStep status)
  {
    return hmRatio.get(status);
  }

  /**
   * {@inheritDoc}
   */
  public Message getSummary(ProgressStep status)
  {
    Message summary = null;
    if (InstallProgressStep.DOWNLOADING.equals(status)) {
      summary = loader.getSummary();
    } else {
      summary = hmSummary.get(status);
    }
    return summary;
  }

  /**
   * Initialize the different map used in this class.
   *
   */
  private void initMaps()
  {
    initSummaryMap(hmSummary);

    /*
     * hmTime contains the relative time that takes for each task to be
     * accomplished. For instance if downloading takes twice the time of
     * extracting, the value for downloading will be the double of the value for
     * extracting.
     */
    HashMap<InstallProgressStep, Integer> hmTime =
        new HashMap<InstallProgressStep, Integer>();
    hmTime.put(InstallProgressStep.DOWNLOADING, 15);
    hmTime.put(InstallProgressStep.EXTRACTING, 15);
    hmTime.put(InstallProgressStep.CONFIGURING_SERVER, 5);
    hmTime.put(InstallProgressStep.CREATING_BASE_ENTRY, 10);
    hmTime.put(InstallProgressStep.IMPORTING_LDIF, 20);
    hmTime.put(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED, 20);
    hmTime.put(InstallProgressStep.CONFIGURING_REPLICATION, 10);
    hmTime.put(InstallProgressStep.ENABLING_WINDOWS_SERVICE, 5);
    hmTime.put(InstallProgressStep.STARTING_SERVER, 10);
    hmTime.put(InstallProgressStep.STOPPING_SERVER, 5);
    hmTime.put(InstallProgressStep.CONFIGURING_ADS, 5);
    hmTime.put(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES, 25);

    int totalTime = 0;
    ArrayList<InstallProgressStep> steps =
        new ArrayList<InstallProgressStep>();
    totalTime += hmTime.get(InstallProgressStep.DOWNLOADING);
    steps.add(InstallProgressStep.DOWNLOADING);
    totalTime += hmTime.get(InstallProgressStep.EXTRACTING);
    steps.add(InstallProgressStep.EXTRACTING);
    totalTime += hmTime.get(InstallProgressStep.CONFIGURING_SERVER);
    steps.add(InstallProgressStep.CONFIGURING_SERVER);

    if (createNotReplicatedSuffix())
    {
      switch (getUserData().getNewSuffixOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        steps.add(InstallProgressStep.CREATING_BASE_ENTRY);
        totalTime += hmTime.get(InstallProgressStep.CREATING_BASE_ENTRY);
        break;
      case IMPORT_FROM_LDIF_FILE:
        steps.add(InstallProgressStep.IMPORTING_LDIF);
        totalTime += hmTime.get(InstallProgressStep.IMPORTING_LDIF);
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        steps.add(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED);
        totalTime +=hmTime.get(
            InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED);
        break;
      }
    }

    if (Utils.isWindows())
    {
        totalTime += hmTime.get(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
        steps.add(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
    }
    if (mustStart())
    {
      totalTime += hmTime.get(InstallProgressStep.STARTING_SERVER);
      steps.add(InstallProgressStep.STARTING_SERVER);
    }

    if (mustConfigureReplication())
    {
      steps.add(InstallProgressStep.CONFIGURING_REPLICATION);
      totalTime += hmTime.get(InstallProgressStep.CONFIGURING_REPLICATION);
    }

    if (mustCreateAds())
    {
      totalTime += hmTime.get(InstallProgressStep.CONFIGURING_ADS);
      steps.add(InstallProgressStep.CONFIGURING_ADS);
    }

    if (mustInitializeSuffixes())
    {
      totalTime += hmTime.get(
          InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
      steps.add(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
    }

    if (mustStop())
    {
      totalTime += hmTime.get(InstallProgressStep.STOPPING_SERVER);
      steps.add(InstallProgressStep.STOPPING_SERVER);
    }

    int cumulatedTime = 0;
    for (InstallProgressStep s : steps)
    {
      Integer statusTime = hmTime.get(s);
      hmRatio.put(s, (100 * cumulatedTime) / totalTime);
      if (statusTime != null)
      {
        cumulatedTime += statusTime;
      }
    }

    hmRatio.put(InstallProgressStep.FINISHED_SUCCESSFULLY, 100);
    hmRatio.put(InstallProgressStep.FINISHED_CANCELED, 100);
    hmRatio.put(InstallProgressStep.FINISHED_WITH_ERROR, 100);
  }

  private InputStream getZipInputStream(Integer maxRatio)
      throws ApplicationException {
    notifyListeners(getFormattedWithPoints(INFO_PROGRESS_DOWNLOADING.get()));
    InputStream in = null;

    waitForLoader(maxRatio);

    String zipName = getZipFileName();
    in =
      Installer.class.getClassLoader().getResourceAsStream(zipName);

    if (in == null)
    {
      throw new ApplicationException(
          ApplicationReturnCode.ReturnCode.DOWNLOAD_ERROR,
              INFO_ERROR_ZIPINPUTSTREAMNULL.get(zipName), null);
    }

    notifyListeners(getFormattedDone());
    return in;
  }

  /**
   * Creates the parent Directory for the server location if it does not exist.
   * @throws ApplicationException if something goes wrong.
   */
  private void createParentDirectoryIfRequired() throws ApplicationException
  {
    String serverLocation = getUserData().getServerLocation();
    if (!Utils.parentDirectoryExists(serverLocation))
    {
      File f = new File(serverLocation);
      String parent = f.getParent();
      try
      {
        if (!Utils.createDirectory(parent))
        {
          throw new ApplicationException(
              ApplicationReturnCode.ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_COULD_NOT_CREATE_PARENT_DIR.get(parent), null);
        }
      }
      catch (IOException ioe)
      {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
            INFO_ERROR_COULD_NOT_CREATE_PARENT_DIR.get(parent),
            ioe);
      }
    }
  }

  /**
   * This method extracts the zip file.
   * @param is the inputstream with the contents of the zip file.
   * @param minRatio the value of the ratio in the install that corresponds to
   * the moment where we start extracting the zip files.  Used to update
   * properly the install progress ratio.
   * @param maxRatio the value of the ratio in the installation that corresponds
   * to the moment where we finished extracting the last zip file.  Used to
   * update properly the install progress ratio.
   * @throws ApplicationException if an error occurs.
   */
  private void extractZipFiles(InputStream is, int minRatio, int maxRatio)
      throws ApplicationException {
    ZipExtractor extractor =
            new ZipExtractor(is, minRatio, maxRatio,
            Utils.getNumberZipEntries(),
            getZipFileName(),
            this);
    extractor.extract(getUserData().getServerLocation());
  }

  /**
   * {@inheritDoc}
   */
  protected String getOpenDSClassPath()
  {
    StringBuilder buf = new StringBuilder();
    String[] jars = getOpenDSJarPaths();
    for (int i = 0; i < jars.length; i++)
    {
      if (i != 0)
      {
        buf.append(System.getProperty("path.separator"));
      }
      buf.append(jars[i]);
    }
    return buf.toString();
  }

  /**
   * Returns the jar file paths in the installation.  This is used to launch
   * command lines that require a classpath.
   * @return the jar file paths in the installation.
   */
  private String[] getOpenDSJarPaths()
  {
    String[] jarPaths =
            new String[Installation.OPEN_DS_JAR_RELATIVE_PATHS.length];
    File parentDir = new File(getUserData().getServerLocation());
    for (int i = 0; i < jarPaths.length; i++)
    {
      File f = new File(parentDir, Installation.OPEN_DS_JAR_RELATIVE_PATHS[i]);
      jarPaths[i] = f.getAbsolutePath();
    }
    return jarPaths;

  }

  /**
   * Returns the name of the zip file name that contains all the installation.
   * @return the name of the zip file name that contains all the installation.
   */
  private String getZipFileName()
  {
    // Passed as a java option in the JNLP file
    return System.getProperty(SetupUtils.ZIP_FILE_NAME);
  }

  /**
   * Uninstall what has already been installed.
   */
  private void uninstall() {
    notifyListeners(getTaskSeparator());

    Installation installation = getInstallation();
    FileManager fm = new FileManager(this);

    // Stop the server if necessary
    if (installation.getStatus().isServerRunning()) {
      try {
        new ServerController(installation).stopServer(true);
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "error stopping server", e);
      }
    }

    uninstallServices();

    try {
      fm.deleteRecursively(installation.getRootDirectory(), null,
              FileManager.DeletionPolicy.DELETE_ON_EXIT_IF_UNSUCCESSFUL);
    } catch (ApplicationException e) {
      LOG.log(Level.INFO, "error deleting files", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getInstallationPath()
  {
    return getUserData().getServerLocation();
  }
}
