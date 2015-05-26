/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable.spi;

import java.io.Closeable;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RestoreConfig;

/**
 * This interface abstracts the underlying storage engine,
 * isolating the pluggable backend generic code from a particular storage engine implementation.
 */
public interface Storage extends Closeable
{

  /** Defines access modes of a Storage. */
  public enum AccessMode {
    /** Constant used to open the Storage in read-only mode. */
    READ_ONLY,
    /** Constant used to open the Storage in read-write mode. */
    READ_WRITE;
  }

  /**
   * Starts the import operation.
   *
   * @return a new Importer object which must be closed to release all resources
   * @throws ConfigException
   *           if there is a problem with the configuration
   * @throws StorageRuntimeException
   *           if a problem occurs with the underlying storage engine
   * @see #close() to release all resources once import is finished
   */
  Importer startImport() throws ConfigException, StorageRuntimeException;

  /**
   * Opens the storage engine to allow executing operations on it.
   *
   * @param accessMode
   *           Specify the access mode to this storage.
   * @throws NullPointerException
   *           if accessMode is null.
   * @throws Exception
   *           if a problem occurs with the underlying storage engine
   * @see #close() to release all resources once import is finished
   */
  void open(AccessMode accessMode) throws Exception;

  /**
   * Executes a read operation. In case of a read operation rollback, implementations must ensure
   * the read operation is retried until it succeeds.
   *
   * @param <T>
   *          type of the value returned
   * @param readOperation
   *          the read operation to execute
   * @return the value read by the read operation
   * @throws Exception
   *           if a problem occurs with the underlying storage engine
   */
  <T> T read(ReadOperation<T> readOperation) throws Exception;

  /**
   * Executes a write operation. In case of a write operation rollback, implementations must ensure
   * the write operation is retried until it succeeds.
   *
   * @param writeOperation
   *          the write operation to execute
   * @throws Exception
   *           if a problem occurs with the underlying storage engine
   */
  void write(WriteOperation writeOperation) throws Exception;

  /**
   * Remove all files for a backend of this storage.
   *
   * @throws StorageRuntimeException if removal fails
   */
  void removeStorageFiles() throws StorageRuntimeException;

  /**
   * Returns the current status of the storage.
   *
   * @return the current status of the storage
   */
  StorageStatus getStorageStatus();

  /**
   * Returns {@code true} if this storage supports backup and restore.
   *
   * @return {@code true} if this storage supports backup and restore.
   */
  boolean supportsBackupAndRestore();

  /** {@inheritDoc} */
  @Override
  void close();

  /**
   * Creates a backup for this storage.
   *
   * @param backupConfig
   *          The configuration to use when performing the backup.
   * @throws DirectoryException
   *           If a Directory Server error occurs.
   */
  void createBackup(BackupConfig backupConfig) throws DirectoryException;

  /**
   * Removes a backup for this storage.
   *
   * @param backupDirectory
   *          The backup directory structure with which the specified backup is
   *          associated.
   * @param backupID
   *          The backup ID for the backup to be removed.
   * @throws DirectoryException
   *           If it is not possible to remove the specified backup.
   */
  void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException;

  /**
   * Restores a backup for this storage.
   *
   * @param restoreConfig
   *          The configuration to use when performing the restore.
   * @throws DirectoryException
   *           If a Directory Server error occurs.
   */
  void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException;

  /**
   * TODO JNR.
   *
   * @return TODO JNR
   */
  Set<TreeName> listTrees();
}
