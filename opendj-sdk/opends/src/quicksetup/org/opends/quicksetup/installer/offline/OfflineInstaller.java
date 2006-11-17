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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.offline;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.opends.quicksetup.installer.InstallException;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.UserInstallData;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;

/**
 * This is an implementation of the Installer class that is used to install
 * the Directory Server from a zip file.  The installer assumes that the zip
 * file contents have been unzipped.
 *
 * It just takes a UserInstallData object and based on that installs OpenDS.
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The notification
 * will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 * However it is the most appropriate part of the code to generate well
 * formatted messages.  So it generates HTML messages in the
 * ProgressUpdateEvent and to do so uses the UIFactory method.
 *
 * TODO pass an object in the constructor that would generate the messages.
 * The problem of this approach is that the resulting interface of this object
 * may be quite complex and could impact the lisibility of the code.
 *
 */
public class OfflineInstaller extends Installer
{
  private static String fullInstallPath;

  /* This map contains the ratio associated with each step */
  private HashMap<InstallProgressStep, Integer> hmRatio =
      new HashMap<InstallProgressStep, Integer>();

  /* This map contains the summary associated with each step */
  private HashMap<InstallProgressStep, String> hmSummary =
      new HashMap<InstallProgressStep, String>();

  private InstallProgressStep status;

  static
  {
    /* Get the install path from the Class Path */
    String sep = System.getProperty("path.separator");
    String[] classPaths = System.getProperty("java.class.path").split(sep);
    String path = null;
    for (int i = 0; i < classPaths.length && (path == null); i++)
    {
      for (int j = 0; j < OPEN_DS_JAR_RELATIVE_PATHS.length &&
      (path == null); j++)
      {
        String normPath = classPaths[i].replace(File.separatorChar, '/');
        if (normPath.endsWith(OPEN_DS_JAR_RELATIVE_PATHS[j]))
        {
          path = classPaths[i];
        }
      }
    }
    File f = new File(path);
    File binariesDir = f.getParentFile();
    fullInstallPath = binariesDir.getParent();
  }
  /**
   * A constant used to retrieve the full install path.
   */
  public static String FULL_INSTALL_PATH = fullInstallPath;

  /**
   * A constant used to retrieve the config file name.
   */
  public static String CONFIG_FILE_NAME =
    Utils.getPath(FULL_INSTALL_PATH, CONFIG_PATH_RELATIVE);

  /**
   * OfflineInstaller constructor.
   * @param userData the UserInstallData with the parameters provided by the
   * user.
   */
  public OfflineInstaller(UserInstallData userData)
  {
    super(userData);
    initMaps();
    status = InstallProgressStep.NOT_STARTED;
  }

  /**
   * {@inheritDoc}
   */
  public void start()
  {
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        doInstall();
      }
    });
    t.start();
  }

  /**
   * {@inheritDoc}
   */
  protected InstallProgressStep getStatus()
  {
    return status;
  }

  /**
   * Actually performs the start in this thread.  The thread is blocked.
   *
   */
  private void doInstall()
  {
    try
    {
      PrintStream err = new ErrorPrintStream();
      PrintStream out = new OutputPrintStream();
      System.setErr(err);
      System.setOut(out);

      status = InstallProgressStep.CONFIGURING_SERVER;
      configureServer();

      switch (getUserData().getDataOptions().getType())
      {
      case CREATE_BASE_ENTRY:
        status = InstallProgressStep.CREATING_BASE_ENTRY;
        notifyListeners(UIFactory.HTML_SEPARATOR);
        createBaseEntry();
        break;
      case IMPORT_FROM_LDIF_FILE:
        status = InstallProgressStep.IMPORTING_LDIF;
        notifyListeners(UIFactory.HTML_SEPARATOR);
        importLDIF();
        break;
      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        status = InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED;
        notifyListeners(UIFactory.HTML_SEPARATOR);
        importAutomaticallyGenerated();
        break;
      }

      if (getUserData().getStartServer())
      {
        notifyListeners(UIFactory.HTML_SEPARATOR);
        status = InstallProgressStep.STARTING_SERVER;
        startServer();
      }

      status = InstallProgressStep.FINISHED_SUCCESSFULLY;
      notifyListeners(null);

      if (false)
        throw new InstallException(InstallException.Type.DOWNLOAD_ERROR,
            getMsg("error-zipinputstreamnull"), null);
    } catch (InstallException ex)
    {
      if (ex.getCause() != null)
      {
        ex.getCause().printStackTrace();
      }
      status = InstallProgressStep.FINISHED_WITH_ERROR;
      String html = getHtmlError(ex, true);
      notifyListeners(html);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected Integer getRatio(InstallProgressStep status)
  {
    return hmRatio.get(status);
  }

  /**
   * {@inheritDoc}
   */
  protected String getSummary(InstallProgressStep status)
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
    hmTime.put(InstallProgressStep.CONFIGURING_SERVER, 5);
    hmTime.put(InstallProgressStep.CREATING_BASE_ENTRY, 10);
    hmTime.put(InstallProgressStep.IMPORTING_LDIF, 20);
    hmTime.put(InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        20);
    hmTime.put(InstallProgressStep.STARTING_SERVER, 10);

    int totalTime = 0;
    ArrayList<InstallProgressStep> steps =
        new ArrayList<InstallProgressStep>();
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
      totalTime += hmTime.get(
              InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED);
      break;
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

  /**
   * {@inheritDoc}
   */
  protected String getConfigFilePath()
  {
    return CONFIG_FILE_NAME;
  }

  /**
   * {@inheritDoc}
   */
  protected String getBinariesPath()
  {
    return Utils.getPath(FULL_INSTALL_PATH, BINARIES_PATH_RELATIVE);
  }

  /**
   * {@inheritDoc}
   */
  protected String getOpenDSClassPath()
  {
    return System.getProperty("java.class.path");
  }
}
