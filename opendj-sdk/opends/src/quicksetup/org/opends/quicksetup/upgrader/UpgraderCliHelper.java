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

import org.opends.quicksetup.CliApplicationHelper;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.ArgumentException;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

/**
 * Assists Upgrader utility in CLI drudgery.
 */
public class UpgraderCliHelper extends CliApplicationHelper {

  /** Short form of the option for specifying the installation package file. */
  static public final Character FILE_OPTION_SHORT = 'f';

  /** Long form of the option for specifying the installation package file. */
  static public final String FILE_OPTION_LONG = "file";

  static private final Logger LOG =
          Logger.getLogger(UpgraderCliHelper.class.getName());

  StringArgument localInstallPackFileNameArg = null;

  /**
   * Creates a set of user data from command line arguments and installation
   * status.
   * @param args String[] of arguments passed in from the command line
   * @param cis the current installation status
   * @return UserData object populated to reflect the input args and status
   * @throws UserDataException if something is wrong
   */
  public UpgradeUserData createUserData(String[] args, CurrentInstallStatus cis)
    throws UserDataException {
    UpgradeUserData uud = new UpgradeUserData();
    ArgumentParser ap = createArgumentParser();
    try {
      ap.parseArguments(args);
      uud.setSilent(isSilent());
      uud.setNoninteractive(isNoninteractive());
      if (localInstallPackFileNameArg.isPresent()) {
        String localInstallPackFileName =
                localInstallPackFileNameArg.getValue();
        File installPackFile = new File(localInstallPackFileName);
        if (!installPackFile.exists()) {
          throw new UserDataException(null,
                  getMsg("build-extractor-error-file-no-exist",
                          localInstallPackFileName));
        } else {
          uud.setInstallPackage(installPackFile);
        }
      } else {
        // TODO: ask the user for this information if non noninteractive
        throw new UserDataException(null,
                getMsg("error-option-required",
                        "-" + FILE_OPTION_SHORT + "/--" + FILE_OPTION_LONG));
      }

    } catch (ArgumentException e) {
      throw new UserDataException(null, getMsg("error-parsing-options"));
    }
    return uud;
  }

  private ArgumentParser createArgumentParser() {

    String toolDescription = getMsg("upgrade-launcher-description");
    ArgumentParser argParser = createArgumentParser(
            "org.opends.quicksetup.upgrader.Upgrader",
            toolDescription,
            false);

    // Initialize all the app specific command-line argument types
    // and register them with the parser.
    try {
      localInstallPackFileNameArg =
              new StringArgument("install package file",
                      FILE_OPTION_SHORT, FILE_OPTION_LONG,
                      false, true, "{install package file}", 0);
      argParser.addArgument(localInstallPackFileNameArg);
    } catch (ArgumentException e) {
      LOG.log(Level.INFO, "error", e);
    }

    return argParser;
  }


}
