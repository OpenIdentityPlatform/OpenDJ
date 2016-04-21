/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.api;

import java.io.File;
import java.nio.file.Path;
import java.util.ListIterator;

import org.opends.server.types.DirectoryException;

/**
 * Represents an entity (storage, backend) that can be backed up.
 * <p>
 * The files to backup must be located under a root directory given by
 * {@link #getDirectory()} method. They can be located at any depth level
 * in a sub-directory. For example, file1, file2 and file3 can be returned as
 * files to backup:
 * <pre>
 * +--- rootDirectory
 * |   \--- file1
 * |   \--- subDirectory
 * |      \--- file2
 * |      \--- file3
 * </pre>
 * The {@code getDirectory()} method is also used to provide the root directory used for
 * the restore of the backup. The actual restore directory depends on the strategy used for
 * restore, which can be one of these two:
 * <ul>
 *  <li>Direct restore: the backup is restored directly in the directory provided by {@code getDirectory()} method.
 *   It is the responsibility of the backupable entity to manage saving of current files before the restore, and
 *   to discard them at the end of a successful restore.</li>
 *  <li>Indirect restore: the backup is restored in a temporary directory, derived from the directory provided
 *  by {@code getDirectory()} method (suffixed by "restore-[backupID]"). It is the responsibility of the backupable
 *  entity to switch from the temporary directory to the final one.</li>
 * </ul>
 * <p>
 * The restore strategy is given by {@code isDirectRestore()} method: if {@code true}, it is a direct restore,
 * otherwise it is an indirect restore.
 * <p>
 * Actions taken before and after the restore should be handled in the {@code beforeRestore()} and
 * {@link #afterRestore(Path, Path)} methods.
 *
 * @see org.opends.server.util.BackupManager
 */
public interface Backupable
{
  /**
   * Returns the files to backup.
   *
   * @return an iterator of files to backup, which may be empty but never {@code null}
   * @throws DirectoryException
   *            If an error occurs.
   */
  ListIterator<Path> getFilesToBackup() throws DirectoryException;

  /**
   * Returns the directory which acts as the root of all files to backup and restore.
   *
   * @return the root directory
   */
  File getDirectory();

  /**
   * Indicates if restore is done directly in the restore directory.
   *
   * @return {@code true} if restore is done directly in the restore directory
   *         provided by {@code getDirectory()} method, or {@code false} if restore
   *         is done in a temporary directory.
   */
  boolean isDirectRestore();

  /**
   * Called before the restore operation begins.
   * <p>
   * In case of direct restore, the backupable entity should take any action
   * to save a copy of existing data before restore operation. Saving includes
   * removing the existing data and copying it in a save directory.
   *
   * @return the directory where current files are saved. It may be {@code null}
   *         if not applicable.
   * @throws DirectoryException
   *            If an error occurs.
   */
  Path beforeRestore() throws DirectoryException;

  /**
   * Called after the restore operation has finished successfully.
   * <p>
   * For direct restore, the backupable entity can safely discard the saved copy.
   * For indirect restore, the backupable entity should switch the restored directory
   * to the final restore directory.
   *
   * @param restoreDirectory
   *          The directory in which files have actually been restored. It is never
   *          {@code null}.
   * @param saveDirectory
   *          The directory in which current files have been saved. It may be
   *          {@code null} if {@code beforeRestore()} returned {@code null}.
   * @throws DirectoryException
   *           If an error occurs.
   */
  void afterRestore(Path restoreDirectory, Path saveDirectory) throws DirectoryException;

}
