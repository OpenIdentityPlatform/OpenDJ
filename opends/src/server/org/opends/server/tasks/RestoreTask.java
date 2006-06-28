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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;

import static org.opends.server.loggers.Debug.debugEnter;
import static org.opends.server.core.DirectoryServer.getAttributeType;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.loggers.Debug.*;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.RestoreConfig;

import java.util.List;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to restore a binary backup of a Directory Server backend.
 */
public class RestoreTask extends Task
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tasks.RestoreTask";



  private String backupDirectory;
  private String backupID;
  private boolean verifyOnly;


  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "initializeTask");


    // FIXME -- Do we need any special authorization here?


    Entry taskEntry = getTaskEntry();

    AttributeType typeBackupDirectory;
    AttributeType typebackupID;
    AttributeType typeVerifyOnly;


    typeBackupDirectory = getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
    typebackupID        = getAttributeType(ATTR_BACKUP_ID, true);
    typeVerifyOnly      = getAttributeType(ATTR_TASK_RESTORE_VERIFY_ONLY, true);

    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeBackupDirectory);
    backupDirectory = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typebackupID);
    backupID = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeVerifyOnly);
    verifyOnly = TaskUtils.getBoolean(attrList, false);

  }

  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    assert debugEnter(CLASS_NAME, "runTask");

    // Open the backup directory and make sure it is valid.
    BackupDirectory backupDir = null;
    try
    {
      backupDir =
           BackupDirectory.readBackupDirectoryDescriptor(backupDirectory);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY;
      String message = getMessage(msgID, backupDirectory,
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return TaskState.STOPPED_BY_ERROR;
    }


    // If a backup ID was specified, then make sure it is valid.  If none was
    // provided, then choose the latest backup from the archive.
    if (backupID != null)
    {
      BackupInfo backupInfo = backupDir.getBackupInfo(backupID);
      if (backupInfo == null)
      {
        int    msgID   = MSGID_RESTOREDB_INVALID_BACKUP_ID;
        String message = getMessage(msgID, backupID, backupDirectory);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    else
    {
      BackupInfo latestBackup = backupDir.getLatestBackup();
      if (latestBackup == null)
      {
        int    msgID   = MSGID_RESTOREDB_NO_BACKUPS_IN_DIRECTORY;
        String message = getMessage(msgID, backupDirectory);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
      else
      {
        backupID = latestBackup.getBackupID();
      }
    }

    // Get the DN of the backend configuration entry from the backup.
    DN configEntryDN = backupDir.getConfigEntryDN();

    // Get the backend configuration entry.
    ConfigEntry configEntry;
    try
    {
      configEntry = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (ConfigException e)
    {
      assert debugException(CLASS_NAME, "runTask", e);
      int    msgID   = MSGID_RESTOREDB_NO_BACKENDS_FOR_DN;
      String message = getMessage(msgID, backupDirectory,
                                  configEntryDN.toString());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    // Get the backend ID from the configuration entry.
    String backendID = TaskUtils.getBackendID(configEntry);

    // Get the backend.
    Backend backend = DirectoryServer.getBackend(backendID);

    if (! backend.supportsRestore())
    {
      int    msgID   = MSGID_RESTOREDB_CANNOT_RESTORE;
      String message = getMessage(msgID, backend.getBackendID());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    // Create the restore config object from the information available.
    RestoreConfig restoreConfig = new RestoreConfig(backupDir, backupID,
                                                    verifyOnly);


    // Disable the backend.
    try
    {
      TaskUtils.setBackendEnabled(configEntry, false);
    }
    catch (DirectoryException e)
    {
      assert debugException(CLASS_NAME, "runTask", e);

      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               e.getErrorMessage(), e.getErrorMessageID());
      return TaskState.STOPPED_BY_ERROR;
    }


    // From here we must make sure to re-enable the backend before returning.
    try
    {
      // Acquire an exclusive lock for the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_RESTOREDB_CANNOT_LOCK_BACKEND;
          String message = getMessage(msgID, backend.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_RESTOREDB_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }


      // From here we must make sure to release the backend exclusive lock.
      try
      {
        // Perform the restore.
        try
        {
          backend.restoreBackup(configEntry, restoreConfig);
        }
        catch (DirectoryException de)
        {
          int    msgID   = MSGID_RESTOREDB_ERROR_DURING_BACKUP;
          String message = getMessage(msgID, backupID, backupDir.getPath(),
                                      de.getErrorMessage());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_RESTOREDB_ERROR_DURING_BACKUP;
          String message = getMessage(msgID, backupID, backupDir.getPath(),
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      finally
      {
        // Release the exclusive lock on the backend.
        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            int    msgID   = MSGID_RESTOREDB_CANNOT_UNLOCK_BACKEND;
            String message = getMessage(msgID, backend.getBackendID(),
                                        String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);
            return TaskState.COMPLETED_WITH_ERRORS;
          }
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_RESTOREDB_CANNOT_UNLOCK_BACKEND;
          String message = getMessage(msgID, backend.getBackendID(),
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                   message, msgID);
          return TaskState.COMPLETED_WITH_ERRORS;
        }
      }

    }
    finally
    {
      // Enable the backend.
      try
      {
        TaskUtils.setBackendEnabled(configEntry, true);
      }
      catch (DirectoryException e)
      {
        assert debugException(CLASS_NAME, "runTask", e);

        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 e.getErrorMessage(), e.getErrorMessageID());
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
