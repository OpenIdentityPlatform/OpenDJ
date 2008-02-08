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

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.UserDataException;
import org.opends.server.util.cli.ConsoleApplication;

import java.util.logging.Logger;

/**
 * Assists Upgrader utility in CLI drudgery.
 */
public class UpgraderCliHelper extends ConsoleApplication {

  static private final Logger LOG =
          Logger.getLogger(UpgraderCliHelper.class.getName());

  /** Launcher for this CLI invocation. */
  protected UpgradeLauncher launcher;

  /**
   * Creates a parameterized instance.
   * @param launcher for this CLI
   */
  public UpgraderCliHelper(UpgradeLauncher launcher)
  {
    super(System.in, System.out, System.err);
    this.launcher = launcher;
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
    // It is assumed that if we got here that the build
    // extractor took care of extracting the file and
    // putting it in tmp/upgrade for us.  So there's
    // not too much to do at this point.
    UpgradeUserData uud = new UpgradeUserData();
    uud.setQuiet(launcher.isQuiet());
    uud.setInteractive(!launcher.isNoPrompt());
    uud.setVerbose(launcher.isVerbose());
    return uud;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAdvancedMode() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isInteractive() {
    return launcher.isInteractive();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isQuiet() {
    return launcher.isQuiet();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isScriptFriendly() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isVerbose() {
    return launcher.isVerbose();
  }
}
