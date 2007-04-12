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
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.opends.quicksetup.QuickSetupException;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.webstart.JnlpProperties;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ZipExtractor;
import org.opends.quicksetup.util.ServerController;

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
public class WebStartInstaller extends Installer implements JnlpProperties {
  private HashMap<InstallProgressStep, Integer> hmRatio =
      new HashMap<InstallProgressStep, Integer>();

  private HashMap<InstallProgressStep, String> hmSummary =
      new HashMap<InstallProgressStep, String>();

  private WebStartDownloader loader;

  /**
   * WebStartInstaller constructor.
   */
  public WebStartInstaller()
  {
    loader = new WebStartDownloader();
    loader.start(false);
    status = InstallProgressStep.NOT_STARTED;
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

      status = InstallProgressStep.DOWNLOADING;

      InputStream in =
          getZipInputStream(getRatio(InstallProgressStep.EXTRACTING));
      notifyListeners(getTaskSeparator());

      status = InstallProgressStep.EXTRACTING;
      extractZipFiles(in, getRatio(InstallProgressStep.EXTRACTING),
          getRatio(InstallProgressStep.CONFIGURING_SERVER));
      notifyListeners(getTaskSeparator());

      setInstallation(new Installation(getUserData().getServerLocation()));

      status = InstallProgressStep.CONFIGURING_SERVER;
      configureServer();

      switch (getUserData().getDataOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        status = InstallProgressStep.CREATING_BASE_ENTRY;
        notifyListeners(getTaskSeparator());
        createBaseEntry();
        break;
      case IMPORT_FROM_LDIF_FILE:
        status = InstallProgressStep.IMPORTING_LDIF;
        notifyListeners(getTaskSeparator());
        importLDIF();
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        status = InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED;
        notifyListeners(getTaskSeparator());
        importAutomaticallyGenerated();
        break;
      }

      writeJavaHome();

      if (getUserData().getStartServer())
      {
        notifyListeners(getTaskSeparator());
        status = InstallProgressStep.STARTING_SERVER;
        new ServerController(this).startServer();
      }

      if (Utils.isWindows())
      {
          notifyListeners(getTaskSeparator());
          status = InstallProgressStep.ENABLING_WINDOWS_SERVICE;
          enableWindowsService();
      }

      status = InstallProgressStep.FINISHED_SUCCESSFULLY;
      notifyListeners(null);

    } catch (QuickSetupException ex)
    {
      status = InstallProgressStep.FINISHED_WITH_ERROR;
      String html = getFormattedError(ex, true);
      notifyListeners(html);
    }
    catch (Throwable t)
    {
      status = InstallProgressStep.FINISHED_WITH_ERROR;
      QuickSetupException ex = new QuickSetupException(
          QuickSetupException.Type.BUG, getThrowableMsg("bug-msg", t), t);
      String msg = getFormattedError(ex, true);
      notifyListeners(msg);
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
  public String getSummary(ProgressStep status)
  {
    return hmSummary.get(status);
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
    hmTime.put(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        20);
    hmTime.put(InstallProgressStep.ENABLING_WINDOWS_SERVICE, 5);
    hmTime.put(InstallProgressStep.STARTING_SERVER, 10);

    int totalTime = 0;
    ArrayList<InstallProgressStep> steps =
        new ArrayList<InstallProgressStep>();
    totalTime += hmTime.get(InstallProgressStep.DOWNLOADING);
    steps.add(InstallProgressStep.DOWNLOADING);
    totalTime += hmTime.get(InstallProgressStep.EXTRACTING);
    steps.add(InstallProgressStep.EXTRACTING);
    totalTime += hmTime.get(InstallProgressStep.CONFIGURING_SERVER);
    steps.add(InstallProgressStep.CONFIGURING_SERVER);

    switch (getUserData().getDataOptions().getType())
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
    if (Utils.isWindows())
    {
        totalTime += hmTime.get(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
        steps.add(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
    }
    if (getUserData().getStartServer())
    {
      totalTime += hmTime.get(InstallProgressStep.STARTING_SERVER);
      steps.add(InstallProgressStep.STARTING_SERVER);
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
    hmRatio.put(InstallProgressStep.FINISHED_WITH_ERROR, 100);
  }

  private InputStream getZipInputStream(Integer maxRatio)
      throws QuickSetupException {
    notifyListeners(getFormattedWithPoints(getMsg("progress-downloading")));
    InputStream in = null;

    waitForLoader(maxRatio);

    String zipName = getZipFileName();
    in =
      Installer.class.getClassLoader().getResourceAsStream(zipName);

    if (in == null)
    {
      throw new QuickSetupException(QuickSetupException.Type.DOWNLOAD_ERROR,
          getMsg("error-zipinputstreamnull", new String[] {zipName}), null);
    }


    notifyListeners(getFormattedDone());
    return in;
  }

  /**
   * Waits for the loader to be finished.  Every time we have an update in the
   * percentage that is downloaded we notify the listeners of this.
   *
   * @param maxRatio is the integer value that tells us which is the max ratio
   * that corresponds to the download.  It is used to calculate how the global
   * installation ratio changes when the download ratio increases.  For instance
   * if we suppose that the download takes 25 % of the total installation
   * process, then maxRatio will be 25.  When the download is complete this
   * method will send a notification to the ProgressUpdateListeners with a ratio
   * of 25 %.
   * @throws QuickSetupException if something goes wrong
   *
   */
  private void waitForLoader(Integer maxRatio) throws QuickSetupException {
    int lastPercentage = -1;
    WebStartDownloader.Status lastStatus =
      WebStartDownloader.Status.DOWNLOADING;
    while (!loader.isFinished() && (loader.getException() == null))
    {
      // Pool until is over
      int perc = loader.getDownloadPercentage();
      WebStartDownloader.Status downloadStatus = loader.getStatus();
      if ((perc != lastPercentage) || (downloadStatus != lastStatus))
      {
        lastPercentage = perc;
        int ratio = (perc * maxRatio) / 100;
        String summary;
        switch (downloadStatus)
        {
        case VALIDATING:
          String[] argsValidating =
            { String.valueOf(perc),
              String.valueOf(loader.getCurrentValidatingPercentage())};

          summary = getMsg("validating-ratio", argsValidating);
          break;
        case UPGRADING:
          String[] argsUpgrading =
            { String.valueOf(perc),
              String.valueOf(loader.getCurrentUpgradingPercentage())};
          summary = getMsg("upgrading-ratio", argsUpgrading);
          break;
        default:
          String[] arg =
            { String.valueOf(perc) };

          summary = getMsg("downloading-ratio", arg);
        }
        hmSummary.put(InstallProgressStep.DOWNLOADING, summary);
        notifyListeners(ratio, summary, null);

        try
        {
          Thread.sleep(300);
        } catch (Exception ex)
        {
        }
      }
    }

    if (loader.getException() != null)
    {
      throw loader.getException();
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
   * @throws QuickSetupException if an error occurs.
   */
  private void extractZipFiles(InputStream is, int minRatio, int maxRatio)
      throws QuickSetupException {
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
    StringBuffer buf = new StringBuffer();
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
    return System.getProperty(ZIP_FILE_NAME);
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstallationPath()
  {
    return getUserData().getServerLocation();
  }
}
