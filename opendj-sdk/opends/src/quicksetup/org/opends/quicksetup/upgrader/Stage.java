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

import static org.opends.quicksetup.Installation.*;
import static org.opends.messages.QuickSetupMessages.*;
import org.opends.messages.Message;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.FileManager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

/**
 * Represents a directory used to stage files for upgrade or reversion and
 * is intended to handle special case operations.
 */
public class Stage {

  private static final Logger LOG =
          Logger.getLogger(Stage.class.getName());

  private File root;

  private FileManager fm;

  private List<Message> messages = new LinkedList<Message>();

  /**
   * Creates a parameterized instance.
   *
   * @param root of the stage directory
   */
  public Stage(File root) {
    this.root = root;
    this.fm = new FileManager();
  }

  /**
   * Moves the files in the staging area to a destination directory.
   *
   * @param destination for the staged files
   * @param fileFilter the file filter to be used
   * @throws ApplicationException if something goes wrong
   */
  public void move(File destination, FileFilter fileFilter)
  throws ApplicationException {
    for (String fileName : root.list()) {
      File dest = new File(destination, fileName);
      File src = getSourceForCopy(fileName, dest);
      //fm.copyRecursively(src, destination, fileFilter, /*overwrite=*/true);
      fm.copyRecursively(src, destination, fileFilter, /*overwrite=*/true);
    }
  }

  /**
   * Returns a list of messages to be displayed to the user following
   * this operation.
   *
   * @return list of messages
   */
  public List<Message> getMessages() {
    return Collections.unmodifiableList(messages);
  }

  private File getSourceForCopy(String fileName, File dest) {

    File src = new File(root, fileName);

    // If this is the running script on Windows, see if it is actually
    // different than the new version.  If not don't do anything but if
    // so copy it over under a different name so that the running script
    // is not disturbed
    if (WINDOWS_UPGRADE_FILE_NAME.equals(fileName) &&
            Utils.isWindows()) {
      try {
        if (fm.filesDiffer(src, dest)) {
          File renamedUpgradeBatFile = new File(root,
                  WINDOWS_UPGRADE_FILE_NAME_NEW);
          if (src.renameTo(renamedUpgradeBatFile)) {
            src = renamedUpgradeBatFile;
            messages.add(INFO_NEW_UPGRADE_SCRIPT_AVAILABLE.get(
                    WINDOWS_UPGRADE_FILE_NAME,
                    WINDOWS_UPGRADE_FILE_NAME_NEW));
          } else {
            LOG.log(Level.INFO, "Failed to rename new version of " +
                    "'" + WINDOWS_UPGRADE_FILE_NAME + "' to '" +
                    WINDOWS_UPGRADE_FILE_NAME_NEW + "'");
          }
        }
      } catch (IOException e) {
        LOG.log(Level.INFO, "Exception comparing files " + e.getMessage());
      }
    }
    return src;
  }

}
