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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.offline;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.opends.quicksetup.installer.InstallException;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.UserInstallData;
import org.opends.quicksetup.util.ProgressMessageFormatter;
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
 *
 */
public class OfflineInstaller extends Installer
{
  /* This map contains the ratio associated with each step */
  private HashMap<InstallProgressStep, Integer> hmRatio =
      new HashMap<InstallProgressStep, Integer>();

  /* This map contains the summary associated with each step */
  private HashMap<InstallProgressStep, String> hmSummary =
      new HashMap<InstallProgressStep, String>();

  private InstallProgressStep status;

  /**
   * OfflineInstaller constructor.
   * @param userData the UserInstallData with the parameters provided by the
   * user.
   * @param formatter the message formatter to be used to generate the text of
   * the ProgressUpdateEvent.
   *
   */
  public OfflineInstaller(UserInstallData userData,
      ProgressMessageFormatter formatter)
  {
    super(userData, formatter);
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
   * Actually performs the install in this thread.  The thread is blocked.
   *
   */
  private void doInstall()
  {
    PrintStream origErr = System.err;
    PrintStream origOut = System.out;
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
        startServer();
      }

      status = InstallProgressStep.FINISHED_SUCCESSFULLY;
      notifyListeners(null);

    } catch (InstallException ex)
    {
      if (ex.getCause() != null)
      {
        ex.getCause().printStackTrace();
      }
      status = InstallProgressStep.FINISHED_WITH_ERROR;
      String html = getFormattedError(ex, true);
      notifyListeners(html);
    }
    catch (Throwable t)
    {
      status = InstallProgressStep.FINISHED_WITH_ERROR;
      InstallException ex = new InstallException(
          InstallException.Type.BUG, getThrowableMsg("bug-msg", t), t);
      String msg = getFormattedError(ex, true);
      notifyListeners(msg);
    }
    System.setErr(origErr);
    System.setOut(origOut);
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
  protected String getInstallationPath()
  {
    return Utils.getInstallPathFromClasspath();
  }

  /**
   * {@inheritDoc}
   */
  protected String getOpenDSClassPath()
  {
    return System.getProperty("java.class.path");
  }
}
