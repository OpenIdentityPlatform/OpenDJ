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

package org.opends.quicksetup;

import java.io.File;
import java.io.IOException;

/**
 * This class represents the current state of a particular installation.
 */
public class Status {

  private static boolean lockPathInitialized;

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
   * Indicates whether there is something installed or not.
   *
   * @return <CODE>true</CODE> if there is something installed under the
   *         binaries that we are running, or <CODE>false</CODE> if not.
   */
  public boolean isInstalled() {
    File rootDirectory = installation.getRootDirectory();
    return rootDirectory == null || !rootDirectory.exists() ||
            !rootDirectory.isDirectory();
  }

  /**
   * Determines whether or not the configuration has been modified for this
   * installation.
   * @return boolean where true means the configuration has been modified
   */
  public boolean configurationHasBeenModified() {
    boolean mod = false;
    try {
      mod = installation.getCurrentConfiguration().hasBeenModified();
    } catch (IOException e) {
      // do nothing for now;
    }
    return mod;
  }

  /**
   * Determines whether or not the schema has been modified for this
   * installation.
   * @return boolean where true means the schema has been modified
   */
  public boolean schemaHasBeenModified() {
    File f = installation.getSchemaConcatFile();
    return f.exists();
  }

  /**
   * Returns if the server is running on the given path.
   * NOTE: this method is to be called only when the OpenDS.jar class has
   * already been loaded as it uses classes in that jar.
   *
   * @return <CODE>true</CODE> if the server is running and <CODE>false</CODE>
   *         otherwise.
   * @throws java.io.IOException if there was a problem reading required
   * configuration information
   */
  public boolean isServerRunning() throws IOException {
    boolean isServerRunning;
    if (!lockPathInitialized) {
      File lockDirectory = installation.getLocksDirectory();

      System.setProperty(
              org.opends.server.util.ServerConstants.PROPERTY_LOCK_DIRECTORY,
              lockDirectory.getCanonicalPath());
      lockPathInitialized = true;
    }
    String lockFile =
            org.opends.server.core.LockFileManager.getServerLockFileName();
    StringBuilder failureReason = new StringBuilder();
    try {
      if (org.opends.server.core.LockFileManager.acquireExclusiveLock(lockFile,
              failureReason)) {
        org.opends.server.core.LockFileManager.releaseLock(lockFile,
                failureReason);
        isServerRunning = false;
      } else {
        isServerRunning = true;
      }
    }
    catch (Throwable t) {
      // Assume that if we cannot acquire the lock file the server is
      // running.
      isServerRunning = true;
    }
    return isServerRunning;
  }

  /**
   * Indicates whether there are database files under this installation.
   *
   * @return <CODE>true</CODE> if there are database files, or
   *         <CODE>false</CODE> if not.
   */
  public boolean dbFilesExist() {
    boolean dbFilesExist = false;
    File dbDir = installation.getDatabasesDirectory();
    File[] children = dbDir.listFiles();
    if ((children != null) && (children.length > 0)) {
      dbFilesExist = true;
    }
    return dbFilesExist;
  }

}
