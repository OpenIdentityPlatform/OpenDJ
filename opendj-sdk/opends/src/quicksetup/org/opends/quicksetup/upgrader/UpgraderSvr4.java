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

import static org.opends.messages.QuickSetupMessages.*;


import org.opends.quicksetup.ApplicationException;



import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Launcher;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.util.FileManager;
import java.io.FileFilter;
import org.opends.quicksetup.ReturnCode;

/**
 * QuickSetup application of upgrading the bits of an SVR4 based installation
 * of OpenDS.
 */
public class UpgraderSvr4 extends Upgrader {

  static private final Logger LOG = Logger.getLogger(
          UpgraderSvr4.class.getName());

  /**
   * {@inheritDoc}
   */
  @Override
  public UserData createUserData(Launcher launcher)
          throws UserDataException {
    return new UpgraderCliHelper((UpgradeSvr4Launcher) launcher).
            createUserData(launcher.getArguments());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected File getStageDirectory()
          throws ApplicationException, IOException {
    return getInstallation().getTmplInstanceDirectory();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Installation getStagedInstallation()
          throws IOException, ApplicationException {
    /* New bits have replaced old  */
    return getInstallation();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void cleanup() throws ApplicationException {
    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void backupFilesystem() throws ApplicationException {
    try {
      FileManager fm = new FileManager();
      File filesBackupDirectory = getFilesInstanceBackupDirectory();
      File root = getInstallation().getInstanceDirectory();
      FileFilter filter = new UpgradeFileFilter(root, false);
      for (String fileName : root.list()) {
        File f = new File(root, fileName);
        fm.move(f, filesBackupDirectory, filter);
      }
    } catch (ApplicationException ae) {
      throw ae;
    } catch (Exception e) {
      throw new ApplicationException(
              ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
              INFO_ERROR_BACKUP_FILESYSTEM.get(),
              e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void upgradeComponents() throws ApplicationException {
    try {
      /* Only instance data have to be upgraded */
      Stage stage = getStage();
      Installation installation = getInstallation();
      File root = installation.getInstanceDirectory();
      stage.move(root, new UpgradeFileFilter(getStageDirectory(), false));

      LOG.log(Level.INFO, "upgraded bits to " +
              installation.getBuildInformation(false));

    } catch (IOException e) {
      throw ApplicationException.createFileSystemException(
              INFO_ERROR_UPGRADING_COMPONENTS.get(), e);
    }
  }
}
