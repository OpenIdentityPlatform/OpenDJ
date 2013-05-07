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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.opends.quicksetup;

import static org.opends.server.util.ServerConstants.SERVER_LOCK_FILE_NAME;
import static org.opends.server.util.ServerConstants.LOCK_FILE_SUFFIX;
import org.opends.server.core.LockFileManager;
import org.opends.quicksetup.util.Utils;

import java.io.File;

/**
 * This class represents the current state of a particular installation.
 */
public class Status {

  private Installation installation;

  /**
   * Creates a status instance of the installation indicated by the
   * input parameter.
   * @param installation physical installation
   */
  public Status(Installation installation) {
    this.installation = installation;
  }

  /**
   * Returns if the server is running on the given path.
   * NOTE: this method is to be called only when the OpenDS.jar class has
   * already been loaded as it uses classes in that jar.
   *
   * LIMITATIONS:
   * If the locks directory does not exist the mechanism fails if the server is
   * stopped.  However if the server.lock does not exist AND the server is not
   * running the mechanism should work most of the times (see failing case 3).
   *
   * The cases where this mechanism does not work are:
   *
   * 1. The user deletes/renames the locks directory.
   * 2. The user deletes/renames the server.lock file AND the server is running.
   * 3. The server is not running but the user that is running the code does not
   * have file system access rights.
   * 4. The server is not running and another process has a lock on the file.
   * @return <CODE>true</CODE> if the server is running and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isServerRunning() {
    boolean isServerRunning;
    String lockFileName = SERVER_LOCK_FILE_NAME + LOCK_FILE_SUFFIX;
    String lockFile =
            Utils.getPath(new File(installation.getLocksDirectory(),
                                   lockFileName));
    StringBuilder failureReason = new StringBuilder();
    try {
      if (LockFileManager.acquireExclusiveLock(lockFile,
              failureReason)) {
        LockFileManager.releaseLock(lockFile,
                failureReason);
        isServerRunning = false;
      } else {
        isServerRunning = true;
      }
    }
    catch (Throwable t) {
      // Assume that if we cannot acquire the lock file the
      // server is running.
      isServerRunning = true;
    }
    return isServerRunning;
  }
}
