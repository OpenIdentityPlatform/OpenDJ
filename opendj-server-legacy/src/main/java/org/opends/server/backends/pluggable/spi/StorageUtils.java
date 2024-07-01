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
package org.opends.server.backends.pluggable.spi;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.FilePermission;

/** Utility class for implementations of {@link Storage}. */
public final class StorageUtils
{
  private StorageUtils()
  {
    // do not instantiate utility classes
  }

  /**
   * Returns a database directory file from the provided parent database directory and backendId.
   *
   * @param parentDbDirectory the parent database directory
   * @param backendId the backend id
   * @return a database directory file where to store data for the provided backendId
   */
  public static File getDBDirectory(String parentDbDirectory, String backendId)
  {
    return new File(getFileForPath(parentDbDirectory), backendId);
  }

  /**
   * Ensure backendDir exists (creating it if not) and has the specified dbDirPermissions.
   *
   * @param backendDir the backend directory where to set the storage files
   * @param dbDirPermissions the permissions to set for the database directory
   * @param configDN the backend configuration DN
   * @throws ConfigException if configuration fails
   */
  public static void setupStorageFiles(File backendDir, String dbDirPermissions, DN configDN) throws ConfigException
  {
    ConfigChangeResult ccr = new ConfigChangeResult();

    checkDBDirExistsOrCanCreate(backendDir, ccr, false);
    if (!ccr.getMessages().isEmpty())
    {
      throw new ConfigException(ccr.getMessages().get(0));
    }
    checkDBDirPermissions(dbDirPermissions, configDN, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new ConfigException(ccr.getMessages().get(0));
    }
    setDBDirPermissions(backendDir, dbDirPermissions, configDN, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new ConfigException(ccr.getMessages().get(0));
    }
  }

  /**
   * Checks a directory exists or can actually be created.
   *
   * @param backendDir the directory to check for
   * @param ccr the list of reasons to return upstream or null if called from setupStorage()
   * @param cleanup true if the directory should be deleted after creation
   */
  public static void checkDBDirExistsOrCanCreate(File backendDir, ConfigChangeResult ccr, boolean cleanup)
  {
    if (!backendDir.exists())
    {
      if (!backendDir.mkdirs())
      {
        addErrorMessage(ccr, ERR_CREATE_FAIL.get(backendDir.getPath()));
      }
      if (cleanup)
      {
        backendDir.delete();
      }
    }
    else if (!backendDir.isDirectory())
    {
      addErrorMessage(ccr, ERR_DIRECTORY_INVALID.get(backendDir.getPath()));
    }
  }

  /**
   * Returns false if directory permissions in the configuration are invalid.
   * Otherwise returns the same value as it was passed in.
   *
   * @param dbDirPermissions the permissions to set for the database directory
   * @param configDN the backend configuration DN
   * @param ccr the current list of change results
   */
  public static void checkDBDirPermissions(String dbDirPermissions, DN configDN, ConfigChangeResult ccr)
  {
    try
    {
      FilePermission backendPermission = decodeDBDirPermissions(dbDirPermissions, configDN);
      // Make sure the mode will allow the server itself access to the database
      if (!backendPermission.isOwnerWritable()
          || !backendPermission.isOwnerReadable()
          || !backendPermission.isOwnerExecutable())
      {
        addErrorMessage(ccr, ERR_CONFIG_BACKEND_INSANE_MODE.get(dbDirPermissions));
      }
    }
    catch (ConfigException ce)
    {
      addErrorMessage(ccr, ce.getMessageObject());
    }
  }

  /**
   * Sets files permissions on the backend directory.
   *
   * @param backendDir the directory to setup
   * @param dbDirPermissions the permissions to set for the database directory
   * @param configDN the backend configuration DN
   * @param ccr the current list of change results
   * @throws ConfigException if configuration fails
   */
  public static void setDBDirPermissions(File backendDir, String dbDirPermissions, DN configDN, ConfigChangeResult ccr)
      throws ConfigException
  {
    try
    {
      FilePermission backendPermission = decodeDBDirPermissions(dbDirPermissions, configDN);
      if (!FilePermission.setPermissions(backendDir, backendPermission))
      {
        addErrorMessage(ccr, WARN_UNABLE_SET_PERMISSIONS.get(backendPermission, backendDir));
      }
    }
    catch (Exception e)
    {
      addErrorMessage(ccr, WARN_SET_PERMISSIONS_FAILED.get(backendDir, stackTraceToSingleLineString(e)));
    }
  }

  private static FilePermission decodeDBDirPermissions(String dbDirPermissions, DN configDN) throws ConfigException
  {
    try
    {
      return FilePermission.decodeUNIXMode(dbDirPermissions);
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_CONFIG_BACKEND_MODE_INVALID.get(configDN));
    }
  }

  /**
   * Adds the provided message to the provided config change result.
   *
   * @param ccr the config change result
   * @param message the message to add
   */
  public static void addErrorMessage(ConfigChangeResult ccr, LocalizableMessage message)
  {
    ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
    ccr.addMessage(message);
  }

  /**
   * Removes the storage files from the provided backend directory.
   *
   * @param backendDir
   *          the backend directory where to remove storage files
   */
  public static void removeStorageFiles(File backendDir)
  {
    if (!backendDir.exists())
    {
      return;
    }
    if (!backendDir.isDirectory())
    {
      throw new StorageRuntimeException(ERR_DIRECTORY_INVALID.get(backendDir.getPath()).toString());
    }

    try
    {
      File[] files = backendDir.listFiles();
      for (File f : files)
      {
        f.delete();
      }
    }
    catch (Exception e)
    {
      throw new StorageRuntimeException(ERR_REMOVE_FAIL.get(e.getMessage()).toString(), e);
    }
  }

  /**
   * Creates a new unusable {@link StorageStatus} for the disk full threshold.
   *
   * @param directory the directory which reached the disk full threshold
   * @param thresholdInBytes the threshold in bytes
   * @param backendId the backend id
   * @return a new unusable {@link StorageStatus}
   */
  public static StorageStatus statusWhenDiskSpaceFull(File directory, long thresholdInBytes, String backendId)
  {
    return StorageStatus.unusable(WARN_DISK_SPACE_FULL_THRESHOLD_CROSSED.get(
        directory.getFreeSpace(), directory.getAbsolutePath(), thresholdInBytes, backendId));
  }

  /**
   * Creates a new locked down {@link StorageStatus} for the disk low threshold.
   *
   * @param directory the directory which reached the disk low threshold
   * @param thresholdInBytes the threshold in bytes
   * @param backendId the backend id
   * @return a new locked down {@link StorageStatus}
   */
  public static StorageStatus statusWhenDiskSpaceLow(File directory, long thresholdInBytes, String backendId)
  {
    return StorageStatus.lockedDown(WARN_DISK_SPACE_LOW_THRESHOLD_CROSSED.get(
        directory.getFreeSpace(), directory.getAbsolutePath(), thresholdInBytes, backendId));
  }
}
