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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.UserData;

import java.io.File;

/**
 * Holds state data specific to a reversion operation.
 */
class ReverterUserData extends UserData {

  /** Directory where the reversion archive lives. */
  private File reversionArchiveDir = null;

  /** Indicates that we are reverting to the most recent version. */
  private boolean mostRecent;

  /**
   * Gets the directory where the files are stored for the reversion.
   * @return File where the reversion files are kept
   */
  public File getReversionArchiveDirectory() {
    return reversionArchiveDir;
  }

  /**
   * Sets the directory where the files are stored for the reversion.
   * @param files where the reversion files are kept
   */
  public void setReversionArchiveDirectory(File files) {
    this.reversionArchiveDir = files;
  }

  /**
   * Sets whether or not we will be reverting to the most recent version.
   * @param mostRecent version or not
   */
  public void setRevertMostRecent(boolean mostRecent) {
    this.mostRecent = mostRecent;
  }

  /**
   * Indicates whether or not we will be reverting to the most recent version.
   * @return boolean where true means revert to the most recent version
   */
  public boolean isRevertMostRecent() {
    return this.mostRecent;
  }

}
