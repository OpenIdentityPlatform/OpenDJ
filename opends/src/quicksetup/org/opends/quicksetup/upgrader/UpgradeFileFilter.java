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

import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.Utils.Dir;

import java.io.FileFilter;
import java.io.File;
import java.util.Set;
import java.util.HashSet;


/**
   * Filter defining files we want to manage in the upgrade
 * process.
 */
class UpgradeFileFilter implements FileFilter {

  /**
   * Private variable that store the filter scope.
   */
  private Dir dir ;


  private Set<File>  installDirFileList;

  Set<File> filesToIgnore;

  /**
   * Creates a filter for ignoring in an OpenDS installation at
   * <code>root</code>certain OpenDS files below root.
   * @param root the root of the installation
   */
  public UpgradeFileFilter(File root) { //throws IOException {
    this.filesToIgnore = new HashSet<File>();
    for (String rootFileNamesToIgnore :
            Upgrader.ROOT_FILES_TO_IGNORE_DURING_BACKUP) {
      filesToIgnore.add(new File(root, rootFileNamesToIgnore));
    }
    for (String rootFileNamesToIgnore :
      Upgrader.FILES_TO_IGNORE_DURING_BACKUP) {
      filesToIgnore.add(new File(root, rootFileNamesToIgnore));
    }

    dir = Dir.ALL ;
    installDirFileList = null ;
  }

  /**
   * Creates a filter for ignoring in an OpenDS installation at
   * <code>root</code>certain OpenDS files below root.
   * @param root the root of the installation
   * @param forInstallDir true if the filter is for the install directory.
   */
  public UpgradeFileFilter(File root, boolean forInstallDir) {
    this(root);
    if (forInstallDir)
    {
      dir = Dir.INSTALL;
    }
    else
    {
      dir = Dir.INSTANCE;
    }

    installDirFileList = new HashSet<File>();
    for (String rootInstallDirFile :
            Upgrader.ROOT_FILE_FOR_INSTALL_DIR) {
      installDirFileList.add(new File(root, rootInstallDirFile));
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean accept(File file) {
    boolean accept = true;
    for (File ignoreFile : filesToIgnore) {
      if (ignoreFile.equals(file) ||
              Utils.isParentOf(ignoreFile, file)) {
        accept = false;
        break;
      }
    }

    if ((!accept) || (dir.compareTo(Dir.ALL) == 0))
    {
      return accept ;
    }

    // If we are here, accept is still set to "true".
    if(dir.compareTo(Dir.INSTALL) == 0)
    {
      accept = false ;
      for (File installDirFile : installDirFileList) {
        if (installDirFile.equals(file) ||
                Utils.isParentOf(installDirFile, file)) {
          accept = true ;
          break;
        }
      }
    }
    else
    if (dir.compareTo(Dir.INSTANCE) == 0)
    {
      for (File installDirFile : installDirFileList) {
        if (installDirFile.equals(file) ||
                Utils.isParentOf(installDirFile, file)) {
          accept = false ;
          break;
        }
      }
    }
    else
    {
      // Should never occurs
      accept = false ;
    }

    return accept;
  }
}
