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

package org.opends.quicksetup.upgrader;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.CliApplicationHelper;
import org.opends.quicksetup.UserDataException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.ArgumentException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Assists Upgrader utility in CLI drudgery.
 */
public class UpgraderCliHelper extends CliApplicationHelper {

  static private final Logger LOG =
          Logger.getLogger(UpgraderCliHelper.class.getName());

  StringArgument localInstallPackFileNameArg = null;

  /**
   * Default constructor.
   */
  public UpgraderCliHelper()
  {
    super(System.out, System.err, System.in);
  }

  /**
   * Creates a set of user data from command line arguments and installation
   * status.
   * @param args String[] of arguments passed in from the command line
   * @return UserData object populated to reflect the input args and status
   * @throws UserDataException if something is wrong
   */
  public UpgradeUserData createUserData(String[] args)
    throws UserDataException {
    UpgradeUserData uud = new UpgradeUserData();
    ArgumentParser ap = createArgumentParser();
    try {
      ap.parseArguments(args);
      uud.setQuiet(isQuiet());
      uud.setInteractive(isInteractive());

      // There is no need to check/validate the file argument
      // since this is done by the BuildExtractor

    } catch (ArgumentException e) {
      throw new UserDataException(null, INFO_ERROR_PARSING_OPTIONS.get());
    }
    return uud;
  }

  private ArgumentParser createArgumentParser() {

    // TODO: get rid of this method and user launcher.getArgumentParser

    Message toolDescription = INFO_UPGRADE_LAUNCHER_DESCRIPTION.get();
    ArgumentParser argParser = createArgumentParser(
            "org.opends.quicksetup.upgrader.Upgrader",
            toolDescription,
            false);

    // Initialize all the app specific command-line argument types
    // and register them with the parser.
    try {
      localInstallPackFileNameArg =
              new StringArgument("install package file",
                      UpgradeLauncher.FILE_OPTION_SHORT,
                      UpgradeLauncher.FILE_OPTION_LONG,
                      false, true, "{install package file}", null);
      argParser.addArgument(localInstallPackFileNameArg);
    } catch (ArgumentException e) {
      LOG.log(Level.INFO, "error", e);
    }

    return argParser;
  }


}
