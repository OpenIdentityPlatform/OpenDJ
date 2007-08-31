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

import static org.opends.messages.QuickSetupMessages.*;
import org.opends.messages.Message;

import org.opends.quicksetup.UserDataException;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;

/**
 * Assists Upgrader utility in CLI drudgery.
 */
public class BuildExtractorCliHelper extends UpgraderCliHelper {

  static private final Logger LOG =
          Logger.getLogger(BuildExtractorCliHelper.class.getName());

  /**
   * Create a parameterized instance.
   * @param launcher for this CLI
   */
  public BuildExtractorCliHelper(UpgradeLauncher launcher) {
    super(launcher);
  }

  /**
   * Creates a set of user data from command line arguments and installation
   * status.
   * @param args String[] of arguments passed in from the command line
   * @return UserData object populated to reflect the input args and status
   * @throws org.opends.quicksetup.UserDataException if something is wrong
   */
  public UpgradeUserData createUserData(String[] args)
    throws UserDataException {
    UpgradeUserData uud = super.createUserData(args);
    if (launcher.isInteractive()) {
      if (!launcher.isNoPrompt()) {
        LOG.log(Level.INFO, "obtaining file information interactively");
        Message[] options = new Message[] {
                INFO_UPGRADE_OPERATION_UPGRADE.get(),
                INFO_UPGRADE_OPERATION_REVERSION.get()
        };
        int response = promptOptions(
                INFO_UPGRADE_OPERATION_PROMPT.get(),
                options[0],
                options);
        if (response == 0) {
          uud.setOperation(UpgradeUserData.Operation.UPGRADE);
          while(true) {
            String fileName = promptForString(
                    INFO_UPGRADE_FILE_PROMPT.get(), null);
            try {
              uud.setInstallPackage(validateInstallPackFile(fileName));
              LOG.log(Level.INFO, "file specified interactively: " +
                      fileName);
              break;
            } catch (UserDataException ude) {
              System.out.println(ude.getMessage());
            }
          }
        } else {
          uud.setOperation(UpgradeUserData.Operation.REVERSION);
        }
      } else {
        throw new UserDataException(null,
                INFO_ERROR_OPTIONS_REQUIRED_OR_INTERACTIVE.get());
      }
    } else {
      String upgradeFile = launcher.getUpgradeFileName();
      if (upgradeFile != null) {
        uud.setInstallPackage(
                validateInstallPackFile(upgradeFile));
      }
    }
    return uud;
  }

  private File validateInstallPackFile(String fileName)
          throws UserDataException
  {
    File f = new File(fileName);
    if (!f.exists()) {
        throw new UserDataException(null,
                INFO_BUILD_EXTRACTOR_ERROR_FILE_NO_EXIST.get(fileName));
    } else if (f.isDirectory() || !f.getName().toLowerCase().endsWith(".zip")) {
      throw new UserDataException(null,
              INFO_BUILD_EXTRACTOR_ERROR_FILE_NOT_ZIP.get(fileName));
    }
    return f;
  }

}
