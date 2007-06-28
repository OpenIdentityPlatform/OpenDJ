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

package org.opends.quicksetup.installer.offline;

import java.io.PrintStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.KeyStoreException;

import org.opends.quicksetup.ApplicationException;
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
  private HashMap<InstallProgressStep, String> hmSummary =
      new HashMap<InstallProgressStep, String>();

  private static final Logger LOG =
    Logger.getLogger(OfflineInstaller.class.getName());
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

      checkAbort();

      setStatus(InstallProgressStep.CONFIGURING_SERVER);
      configureServer();

      checkAbort();

      createData();

      checkAbort();

      writeJavaHome();

      if (Utils.isWindows() && getUserData().getEnableWindowsService())
      {
          notifyListeners(getTaskSeparator());
          setStatus(InstallProgressStep.ENABLING_WINDOWS_SERVICE);
          enableWindowsService();
          checkAbort();
      }

      if (mustStart())
      {
        notifyListeners(getTaskSeparator());
        setStatus(InstallProgressStep.STARTING_SERVER);
        new ServerController(this).startServer();
        checkAbort();
      }

      if (mustConfigureReplication())
      {
        setStatus(InstallProgressStep.CONFIGURING_REPLICATION);
        notifyListeners(getTaskSeparator());

        configureReplication();
        checkAbort();
      }

      if (mustInitializeSuffixes())
      {
        notifyListeners(getTaskSeparator());
        setStatus(InstallProgressStep.INITIALIZE_REPLICATED_SUFFIXES);
        initializeSuffixes();
        checkAbort();
      }

      if (mustCreateAds())
      {
        notifyListeners(getTaskSeparator());
        setStatus(InstallProgressStep.CONFIGURING_ADS);
        updateADS();
        checkAbort();
      }

      if (mustStop())
      {
        notifyListeners(getTaskSeparator());
        setStatus(InstallProgressStep.STOPPING_SERVER);
        new ServerController(this).stopServer();
      }

      checkAbort();
      setStatus(InstallProgressStep.FINISHED_SUCCESSFULLY);
      notifyListeners(null);

    } catch (ApplicationException ex)
    {
      if (ApplicationException.Type.CANCEL.equals(ex.getType())) {
        uninstall();
        setStatus(InstallProgressStep.FINISHED_CANCELED);
        notifyListeners(null);
      } else {
        notifyListeners(getLineBreak());
        notifyListenersOfLog();
        setStatus(InstallProgressStep.FINISHED_WITH_ERROR);
        String html = getFormattedError(ex, true);
        notifyListeners(html);
        LOG.log(Level.SEVERE, "Error installing.", ex);
      }
    }
    catch (Throwable t)
    {
      notifyListeners(getLineBreak());
      notifyListenersOfLog();
      setStatus(InstallProgressStep.FINISHED_WITH_ERROR);
      ApplicationException ex = new ApplicationException(
          ApplicationException.Type.BUG, getThrowableMsg("bug-msg", t), t);
      String msg = getFormattedError(ex, true);
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
  public String getSummary(ProgressStep status)
  {
    return hmSummary.get(status);
  }

  /**
   * Called when the user elects to cancel this operation.
   */
  protected void uninstall() {

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

    if (mustCreateAds())
    {
      totalTime += hmTime.get(InstallProgressStep.CONFIGURING_ADS);
      steps.add(InstallProgressStep.CONFIGURING_ADS);
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
