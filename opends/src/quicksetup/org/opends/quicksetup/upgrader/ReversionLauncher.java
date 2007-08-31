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

import org.opends.quicksetup.CliApplication;
import org.opends.quicksetup.QuickSetupLog;

import java.io.File;

/**
 * Launches a reversion operation.  This class just extends UpgradeLauncher
 * which really contains all the smarts for launching reversion/upgrade
 * operations.
 */
public class ReversionLauncher extends UpgradeLauncher {

  /**
   * Creates and launches a reversion operation.
   * @param args from the command line
   */
  static public void main(String[] args) {
    try {
      QuickSetupLog.initLogFileHandler(
              File.createTempFile(LOG_FILE_PREFIX,
                      QuickSetupLog.LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println(INFO_ERROR_INITIALIZING_LOG.get());
      t.printStackTrace();
    }
    new ReversionLauncher(args).launch();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isReversion() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  protected CliApplication createCliApplication() {
    return new Reverter();
  }

  /**
   * Creates a new launcher.
   * @param args from the command line
   */
  protected  ReversionLauncher(String[] args) {
    super(args);
  }

}
