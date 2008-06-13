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
import static org.opends.messages.UtilityMessages.*;

import org.opends.messages.Message;

import org.opends.quicksetup.CliUserInteraction;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.UserInteraction;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;

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
        final int UPGRADE = 1;
        final int REVERT = 2;
        int[] indexes = {UPGRADE, REVERT};
        Message[] options = new Message[] {
            INFO_UPGRADE_OPERATION_UPGRADE.get(),
            INFO_UPGRADE_OPERATION_REVERSION.get()
        };

        MenuBuilder<Integer> builder = new MenuBuilder<Integer>(this);

        builder.setPrompt(INFO_UPGRADE_OPERATION_PROMPT.get());

        for (int i=0; i<indexes.length; i++)
        {
          builder.addNumberedOption(options[i], MenuResult.success(indexes[i]));
        }

        builder.setDefault(Message.raw(String.valueOf(UPGRADE)),
            MenuResult.success(UPGRADE));

        Menu<Integer> menu = builder.toMenu();
        int choice;
        try
        {
          MenuResult<Integer> m = menu.run();
          if (m.isSuccess())
          {
            choice = m.getValue();
          }
          else
          {
            // Should never happen.
            throw new RuntimeException();
          }
        }
        catch (CLIException ce)
        {
          choice = UPGRADE;
          LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
        }

        if (choice == UPGRADE) {
          uud.setOperation(UpgradeUserData.Operation.UPGRADE);
          int nTries = 0;
          while(true) {
            String fileName = readInput(
                    INFO_UPGRADE_FILE_PROMPT.get(), null, LOG);
            if (fileName != null)
            {
              try {
                uud.setInstallPackage(validateInstallPackFile(fileName));
                LOG.log(Level.INFO, "file specified interactively: " +
                    fileName);
                break;
              } catch (UserDataException ude) {
                System.out.println(ude.getMessage());
              }
            }
            else
            {
              // There was an error reading the input: add a line return
              System.out.println();
            }
            nTries++;
            if (nTries >= CONFIRMATION_MAX_TRIES)
            {
              throw new UserDataException(null,
                  ERR_CONFIRMATION_TRIES_LIMIT_REACHED.get(
                      CONFIRMATION_MAX_TRIES));
            }
          }
          System.out.println();
          Message cont = INFO_CONTINUE_BUTTON_LABEL.get();
          Message cancel = INFO_CANCEL_BUTTON_LABEL.get();
          UserInteraction ui = new CliUserInteraction();
          if (cancel.equals(ui.confirm(
              INFO_UPGRADE_CONFIRM_TITLE.get(),
              INFO_UPGRADE_CONFIRM_PROMPT.get(
                      uud.getInstallPackage().getAbsolutePath()),
              INFO_REVERT_CONFIRM_TITLE.get(),
              UserInteraction.MessageType.WARNING,
              new Message[] { cont, cancel },
              cont))) {
            LOG.log(Level.INFO, "User canceled upgrade.");
            return null;
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
      if (launcher.isRevertMostRecent() || launcher.isRevertToArchive())
      {
        uud.setOperation(UpgradeUserData.Operation.REVERSION);
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
