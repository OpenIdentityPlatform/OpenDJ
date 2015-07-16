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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.tasks;

import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.messages.Severity;
import org.opends.messages.TaskMessages;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.RestoreConfig;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to restore a binary backup of a Directory Server backend.
 */
public class RestoreTask extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /** Stores mapping between configuration attribute name and its label. */
  private static Map<String,LocalizableMessage> argDisplayMap = new HashMap<>();
  static {
    argDisplayMap.put(ATTR_BACKUP_DIRECTORY_PATH, INFO_RESTORE_ARG_BACKUP_DIR.get());
    argDisplayMap.put(ATTR_BACKUP_ID, INFO_RESTORE_ARG_BACKUP_ID.get());
    argDisplayMap.put(ATTR_TASK_RESTORE_VERIFY_ONLY, INFO_RESTORE_ARG_VERIFY_ONLY.get());
  }


  /** The task arguments. */
  private File backupDirectory;
  private String backupID;
  private boolean verifyOnly;

  private RestoreConfig restoreConfig;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_RESTORE_NAME.get();
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getAttributeDisplayName(String name) {
    return argDisplayMap.get(name);
  }

  /** {@inheritDoc} */
  @Override
  public void initializeTask() throws DirectoryException
  {
    // If the client connection is available, then make sure the associated
    // client has the BACKEND_RESTORE privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (! clientConnection.hasPrivilege(Privilege.BACKEND_RESTORE, operation))
      {
        LocalizableMessage message = ERR_TASK_RESTORE_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }


    Entry taskEntry = getTaskEntry();

    AttributeType typeBackupDirectory = getAttributeTypeOrDefault(ATTR_BACKUP_DIRECTORY_PATH);
    AttributeType typebackupID = getAttributeTypeOrDefault(ATTR_BACKUP_ID);
    AttributeType typeVerifyOnly = getAttributeTypeOrDefault(ATTR_TASK_RESTORE_VERIFY_ONLY);

    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeBackupDirectory);
    String backupDirectoryPath = TaskUtils.getSingleValueString(attrList);
    backupDirectory = new File(backupDirectoryPath);
    if (! backupDirectory.isAbsolute())
    {
      backupDirectory =
           new File(DirectoryServer.getInstanceRoot(), backupDirectoryPath);
    }

    attrList = taskEntry.getAttribute(typebackupID);
    backupID = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeVerifyOnly);
    verifyOnly = TaskUtils.getBoolean(attrList, false);

  }

  /**
   * Acquire an exclusive lock on a backend.
   * @param backend The backend on which the lock is to be acquired.
   * @return true if the lock was successfully acquired.
   */
  private boolean lockBackend(Backend<?> backend)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        logger.error(ERR_RESTOREDB_CANNOT_LOCK_BACKEND, backend.getBackendID(), failureReason);
        return false;
      }
    }
    catch (Exception e)
    {
      logger.error(ERR_RESTOREDB_CANNOT_LOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
      return false;
    }
    return true;
  }

  /**
   * Release a lock on a backend.
   * @param backend The backend on which the lock is held.
   * @return true if the lock was successfully released.
   */
  private boolean unlockBackend(Backend<?> backend)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_RESTOREDB_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
        return false;
      }
    }
    catch (Exception e)
    {
      logger.warn(WARN_RESTOREDB_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void interruptTask(TaskState interruptState, LocalizableMessage interruptReason)
  {
    if (TaskState.STOPPED_BY_ADMINISTRATOR.equals(interruptState) &&
            restoreConfig != null)
    {
      addLogMessage(Severity.INFORMATION, TaskMessages.INFO_TASK_STOPPED_BY_ADMIN.get(
      interruptReason));
      setTaskInterruptState(interruptState);
      restoreConfig.cancel();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInterruptable() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  protected TaskState runTask()
  {
    // Open the backup directory and make sure it is valid.
    BackupDirectory backupDir;
    try
    {
      backupDir = BackupDirectory.readBackupDirectoryDescriptor(
           backupDirectory.getPath());
    }
    catch (Exception e)
    {
      logger.error(ERR_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY, backupDirectory, getExceptionMessage(e));
      return TaskState.STOPPED_BY_ERROR;
    }


    // If a backup ID was specified, then make sure it is valid.  If none was
    // provided, then choose the latest backup from the archive.
    if (backupID != null)
    {
      BackupInfo backupInfo = backupDir.getBackupInfo(backupID);
      if (backupInfo == null)
      {
        logger.error(ERR_RESTOREDB_INVALID_BACKUP_ID, backupID, backupDirectory);
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    else
    {
      BackupInfo latestBackup = backupDir.getLatestBackup();
      if (latestBackup == null)
      {
        logger.error(ERR_RESTOREDB_NO_BACKUPS_IN_DIRECTORY, backupDirectory);
        return TaskState.STOPPED_BY_ERROR;
      }
      else
      {
        backupID = latestBackup.getBackupID();
      }
    }

    // Get the DN of the backend configuration entry from the backup.
    DN configEntryDN = backupDir.getConfigEntryDN();

    ConfigEntry configEntry;
    try
    {
      // Get the backend configuration entry.
      configEntry = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (ConfigException e)
    {
      logger.traceException(e);
      logger.error(ERR_RESTOREDB_NO_BACKENDS_FOR_DN, backupDirectory, configEntryDN);
      return TaskState.STOPPED_BY_ERROR;
    }

    // Get the backend ID from the configuration entry.
    String backendID = TaskUtils.getBackendID(configEntry);

    // Get the backend.
    Backend<?> backend = DirectoryServer.getBackend(backendID);
    if (!backend.supports(BackendOperation.RESTORE))
    {
      logger.error(ERR_RESTOREDB_CANNOT_RESTORE, backend.getBackendID());
      return TaskState.STOPPED_BY_ERROR;
    }

    // Create the restore config object from the information available.
    restoreConfig = new RestoreConfig(backupDir, backupID, verifyOnly);

    // Notify the task listeners that a restore is going to start
    // this must be done before disabling the backend to allow
    // listener to get access to the backend configuration
    // and to take appropriate actions.
    DirectoryServer.notifyRestoreBeginning(backend, restoreConfig);

    // Disable the backend.
    if ( !verifyOnly)
    {
      try
      {
        TaskUtils.disableBackend(backendID);
      } catch (DirectoryException e)
      {
        logger.traceException(e);

        logger.error(e.getMessageObject());
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    // From here we must make sure to re-enable the backend before returning.
    boolean errorsEncountered = false;
    try
    {
      // Acquire an exclusive lock for the backend.
      if (verifyOnly || lockBackend(backend))
      {
        // From here we must make sure to release the backend exclusive lock.
        try
        {
          // Perform the restore.
          try
          {
            backend.restoreBackup(restoreConfig);
          }
          catch (DirectoryException de)
          {
            DirectoryServer.notifyRestoreEnded(backend, restoreConfig, false);
            logger.error(ERR_RESTOREDB_ERROR_DURING_BACKUP, backupID, backupDir.getPath(), de.getMessageObject());
            errorsEncountered = true;
          }
          catch (Exception e)
          {
            DirectoryServer.notifyRestoreEnded(backend, restoreConfig, false);
            logger.error(ERR_RESTOREDB_ERROR_DURING_BACKUP, backupID, backupDir.getPath(), getExceptionMessage(e));
            errorsEncountered = true;
          }
        }
        finally
        {
          // Release the exclusive lock on the backend.
          if (!verifyOnly && !unlockBackend(backend))
          {
            errorsEncountered = true;
          }
        }
      }
    }
    finally
    {
      // Enable the backend.
      if (! verifyOnly)
      {
        try
        {
          TaskUtils.enableBackend(backendID);
          // it is necessary to retrieve the backend structure again
          // because disabling and enabling it again may have resulted
          // in a new backend being registered to the server.
          backend = DirectoryServer.getBackend(backendID);
        } catch (DirectoryException e)
        {
          logger.traceException(e);

          logger.error(e.getMessageObject());
          errorsEncountered = true;
        }
      }
      DirectoryServer.notifyRestoreEnded(backend, restoreConfig, true);
    }

    if (errorsEncountered)
    {
      return TaskState.COMPLETED_WITH_ERRORS;
    }
    else
    {
      return getFinalTaskState();
    }
  }
}
