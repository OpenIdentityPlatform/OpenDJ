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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.offline;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;


import java.io.PrintStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.KeyStoreException;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.ServerController;
import org.opends.quicksetup.util.FileManager;
import org.opends.server.util.CertificateManager;

/**
 * This is an implementation of the Installer class that is used to install
 * the Directory Server from a zip file.  The installer assumes that the zip
 * file contents have been unzipped.
 *
 * It just takes a UserData object and based on that installs OpenDS.
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
  private HashMap<InstallProgressStep, Message> hmSummary =
      new HashMap<InstallProgressStep, Message>();

  private ApplicationException runError;

  private static final Logger LOG =
    Logger.getLogger(OfflineInstaller.class.getName());
  /**
   * Actually performs the install in this thread.  The thread is blocked.
   *
   */
  public void run()
  {
    runError = null;
    PrintStream origErr = System.err;
    PrintStream origOut = System.out;
    try
    {
      initMaps();

      System.setErr(getApplicationErrorStream());
      System.setOut(getApplicationOutputStream());

      checkAbort();

      setCurrentProgressStep(InstallProgressStep.CONFIGURING_SERVER);
      configureServer();

      checkAbort();

      createData();

      checkAbort();

      if (Utils.isWindows() && getUserData().getEnableWindowsService())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
        enableWindowsService();
        checkAbort();
      }

      if (mustStart())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.STARTING_SERVER);
        PointAdder pointAdder = new PointAdder();
        if (!isVerbose())
        {
          notifyListeners(getFormattedProgress(
              INFO_PROGRESS_STARTING_NON_VERBOSE.get()));
          pointAdder.start();
        }
        try
        {
          new ServerController(this).startServer(!isVerbose());
        }
        catch (ApplicationException ae)
        {
          throw ae;
        }
        finally
        {
          if (!isVerbose())
          {
            pointAdder.stop();
          }
        }
        if (!isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
        else
        {
          notifyListeners(getLineBreak());
        }
        checkAbort();
      }

      if (mustCreateAds())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.CONFIGURING_ADS);
        updateADS();
        checkAbort();
      }

      if (mustConfigureReplication())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.CONFIGURING_REPLICATION);
        createReplicatedBackends();
        configureReplication();
        checkAbort();
      }

      if (mustInitializeSuffixes())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(
            InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
        initializeSuffixes();
        checkAbort();
      }

      if (mustStop())
      {
        if (isVerbose())
        {
          notifyListeners(getTaskSeparator());
        }
        setCurrentProgressStep(InstallProgressStep.STOPPING_SERVER);
        if (!isVerbose())
        {
          notifyListeners(getFormattedWithPoints(
              INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
        }
        new ServerController(this).stopServer(!isVerbose());
        if (!isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
      }

      checkAbort();
      updateSummaryWithServerState(hmSummary);
      notifyListeners(getLineBreak());
      notifyListenersOfLog();
      setCurrentProgressStep(InstallProgressStep.FINISHED_SUCCESSFULLY);
      notifyListeners(null);

    } catch (ApplicationException ex)
    {
      LOG.log(Level.SEVERE, "Caught exception: "+ex, ex);
      if (ReturnCode.CANCELLED.equals(ex.getType())) {
        uninstall();
        setCurrentProgressStep(InstallProgressStep.FINISHED_CANCELED);
        notifyListeners(null);
      } else {
        // Stop the server if necessary
        Installation installation = getInstallation();
        if (installation.getStatus().isServerRunning()) {
          try {
            if (!isVerbose())
            {
              notifyListeners(getFormattedWithPoints(
                  INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
            }
            new ServerController(installation).stopServer(!isVerbose());
            if (!isVerbose())
            {
              notifyListeners(getFormattedDoneWithLineBreak());
            }
          } catch (Throwable t) {
            LOG.log(Level.INFO, "error stopping server", t);
          }
        }
        notifyListeners(getLineBreak());
        updateSummaryWithServerState(hmSummary);
        setCurrentProgressStep(InstallProgressStep.FINISHED_WITH_ERROR);
        Message html = getFormattedError(ex, true);
        notifyListeners(html);
        LOG.log(Level.SEVERE, "Error installing.", ex);
        notifyListeners(getLineBreak());
        notifyListenersOfLog();
      }
      runError = ex;
    }
    catch (Throwable t)
    {
      // Stop the server if necessary
      Installation installation = getInstallation();
      if (installation.getStatus().isServerRunning()) {
        try {
          if (!isVerbose())
          {
            notifyListeners(getFormattedWithPoints(
                INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
          }
          new ServerController(installation).stopServer(!isVerbose());
          if (!isVerbose())
          {
            notifyListeners(getFormattedDoneWithLineBreak());
          }
        } catch (Throwable t2) {
          LOG.log(Level.INFO, "error stopping server", t2);
        }
      }
      notifyListeners(getLineBreak());
      updateSummaryWithServerState(hmSummary);
      setCurrentProgressStep(InstallProgressStep.FINISHED_WITH_ERROR);
      ApplicationException ex = new ApplicationException(
          ReturnCode.BUG,
          Utils.getThrowableMsg(INFO_BUG_MSG.get(), t), t);
      Message msg = getFormattedError(ex, true);
      notifyListeners(msg);
      LOG.log(Level.SEVERE, "Error installing.", t);
      notifyListeners(getLineBreak());
      notifyListenersOfLog();
      runError = ex;
    }
    finally
    {
      System.setErr(origErr);
      System.setOut(origOut);
    }
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
    return hmSummary.get(status);
  }

  /**
   * {@inheritDoc}
   */
  public ApplicationException getRunError()
  {
    return runError;
  }

  /**
   * Called when the user elects to cancel this operation.
   */
  protected void uninstall() {

    notifyListeners(getTaskSeparator());
    if (!isVerbose())
    {
      notifyListeners(getFormattedWithPoints(INFO_PROGRESS_CANCELING.get()));
    }
    else
    {
      notifyListeners(
          getFormattedProgressWithLineBreak(INFO_SUMMARY_CANCELING.get()));
    }
    Installation installation = getInstallation();
    FileManager fm = new FileManager(this);

    // Stop the server if necessary
    if (installation.getStatus().isServerRunning()) {
      try {
        if (!isVerbose())
        {
          notifyListeners(getFormattedWithPoints(
              INFO_PROGRESS_STOPPING_NON_VERBOSE.get()));
        }
        new ServerController(installation).stopServer(!isVerbose());
        if (!isVerbose())
        {
          notifyListeners(getFormattedDoneWithLineBreak());
        }
      } catch (ApplicationException e) {
        LOG.log(Level.INFO, "error stopping server", e);
      }
    }

    uninstallServices();

    // Revert to the base configuration
    try {
      File newConfig = fm.copy(installation.getBaseConfigurationFile(),
                               installation.getConfigurationDirectory(),
                               /*overwrite=*/true);
      fm.rename(newConfig, installation.getCurrentConfigurationFile());

    } catch (ApplicationException ae) {
      LOG.log(Level.INFO, "failed to restore base configuration", ae);
    }

    // Cleanup SSL if necessary
    SecurityOptions sec = getUserData().getSecurityOptions();
    if (sec.getEnableSSL() || sec.getEnableStartTLS()) {
      if (SecurityOptions.CertificateType.SELF_SIGNED_CERTIFICATE.equals(
              sec.getCertificateType())) {
        CertificateManager cm = new CertificateManager(
            getSelfSignedKeystorePath(),
            CertificateManager.KEY_STORE_TYPE_JKS,
            getSelfSignedCertificatePwd());
        try {
          cm.removeCertificate(SELF_SIGNED_CERT_ALIAS);
        } catch (KeyStoreException e) {
          LOG.log(Level.INFO, "Error deleting self signed certification", e);
        }
      }

      File keystore = new File(installation.getConfigurationDirectory(),
              "keystore");
      if (keystore.exists()) {
        try {
          fm.delete(keystore);
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Failed to delete keystore", e);
        }
      }

      File keystorePin = new File(installation.getConfigurationDirectory(),
              "keystore.pin");
      if (keystorePin.exists()) {
        try {
          fm.delete(keystorePin);
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Failed to delete keystore.pin", e);
        }
      }

      File truststore = new File(installation.getConfigurationDirectory(),
              "truststore");
      if (truststore.exists()) {
        try {
          fm.delete(truststore);
        } catch (ApplicationException e) {
          LOG.log(Level.INFO, "Failed to delete truststore", e);
        }
      }
    }

    // Remove the databases
    try {
      fm.deleteChildren(installation.getDatabasesDirectory());
    } catch (ApplicationException e) {
      LOG.log(Level.INFO, "Error deleting databases", e);
    }

    if (!isVerbose())
    {
      notifyListeners(getFormattedDoneWithLineBreak());
    }

  }

  /**
   * Initialize the different map used in this class.
   *
   */
  protected void initMaps()
  {
    initSummaryMap(hmSummary);

    /*
     * hmTime contains the relative time that takes for each task to be
     * accomplished. For instance if downloading takes twice the time of
     * extracting, the value for downloading will be the double of the value for
     * extracting.
     */
    HashMap<ProgressStep, Integer> hmTime =
        new HashMap<ProgressStep, Integer>();
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
        totalTime += hmTime.get(
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

    if (mustCreateAds())
    {
      totalTime += hmTime.get(InstallProgressStep.CONFIGURING_ADS);
      steps.add(InstallProgressStep.CONFIGURING_ADS);
    }

    if (mustConfigureReplication())
    {
      steps.add(InstallProgressStep.CONFIGURING_REPLICATION);
      totalTime += hmTime.get(InstallProgressStep.CONFIGURING_REPLICATION);
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
    hmRatio.put(InstallProgressStep.FINISHED_WITH_ERROR, 100);
    hmRatio.put(InstallProgressStep.FINISHED_CANCELED, 100);
  }

  /**
   * {@inheritDoc}
   */
  public String getInstallationPath()
  {
    return Utils.getInstallPathFromClasspath();
  }

  /**
   * {@inheritDoc}
   */
  public String getInstancePath()
  {
    String installPath =  Utils.getInstallPathFromClasspath();
    return Utils.getInstancePathFromClasspath(installPath);
  }

  /**
   * {@inheritDoc}
   */
  protected String getOpenDSClassPath()
  {
    return System.getProperty("java.class.path");
  }
}
