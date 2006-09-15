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
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.getAttributeType;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.ServerConstants.DATE_FORMAT_UTC_TIME;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.util.ServerConstants.
     BACKUP_DIRECTORY_DESCRIPTOR_FILE;

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
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.io.File;

/**
 * This class provides an implementation of a Directory Server task that may be
 * used to back up a Directory Server backend in a binary form that may be
 * quickly archived and restored.
 */
public class BackupTask extends Task
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tasks.BackupTask";



  private boolean backUpAll;
  private boolean compress;
  private boolean encrypt;
  private boolean hash;
  private boolean incremental;
  private boolean signHash;
  private List<String>  backendIDList;
  private String  backupID;
  private String  backupDirectory;
  private String  incrementalBase;


  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "initializeTask");


    // FIXME -- Do we need any special authorization here?


    Entry taskEntry = getTaskEntry();

    AttributeType typeBackupAll;
    AttributeType typeCompress;
    AttributeType typeEncrypt;
    AttributeType typeHash;
    AttributeType typeIncremental;
    AttributeType typeSignHash;
    AttributeType typeBackendID;
    AttributeType typeBackupID;
    AttributeType typeBackupDirectory;
    AttributeType typeIncrementalBaseID;


    typeBackupAll =
         getAttributeType(ATTR_TASK_BACKUP_ALL, true);
    typeCompress =
         getAttributeType(ATTR_TASK_BACKUP_COMPRESS, true);
    typeEncrypt =
         getAttributeType(ATTR_TASK_BACKUP_ENCRYPT, true);
    typeHash =
         getAttributeType(ATTR_TASK_BACKUP_HASH, true);
    typeIncremental =
         getAttributeType(ATTR_TASK_BACKUP_INCREMENTAL, true);
    typeSignHash =
         getAttributeType(ATTR_TASK_BACKUP_SIGN_HASH, true);
    typeBackendID =
         getAttributeType(ATTR_TASK_BACKUP_BACKEND_ID, true);
    typeBackupID =
         getAttributeType(ATTR_BACKUP_ID, true);
    typeBackupDirectory =
         getAttributeType(ATTR_BACKUP_DIRECTORY_PATH, true);
    typeIncrementalBaseID =
         getAttributeType(ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID, true);


    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeBackupAll);
    backUpAll = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeCompress);
    compress = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeEncrypt);
    encrypt = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeHash);
    hash = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeIncremental);
    incremental = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeSignHash);
    signHash = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeBackendID);
    backendIDList = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeBackupID);
    backupID = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeBackupDirectory);
    backupDirectory = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeIncrementalBaseID);
    incrementalBase = TaskUtils.getSingleValueString(attrList);

  }


  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    assert debugEnter(CLASS_NAME, "runTask");

    // Make sure that either the backUpAll argument was provided or at least one
    // backend ID was given.  They are mutually exclusive.
    if (backUpAll)
    {
      if (!backendIDList.isEmpty())
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID;
        String message = getMessage(msgID, ATTR_TASK_BACKUP_ALL,
                                    ATTR_TASK_BACKUP_BACKEND_ID);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    else if (backendIDList.isEmpty())
    {
      int    msgID   = MSGID_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID;
      String message = getMessage(msgID, ATTR_TASK_BACKUP_ALL,
                                  ATTR_TASK_BACKUP_BACKEND_ID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return TaskState.STOPPED_BY_ERROR;
    }


    // If no backup ID was provided, then create one with the current timestamp.
    if (backupID == null)
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_UTC_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      backupID = dateFormat.format(new Date());
    }


    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    if (incrementalBase != null)
    {
      if (! incremental)
      {
        int    msgID   = MSGID_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL;
        String message = getMessage(msgID,
                                    ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID,
                                    ATTR_TASK_BACKUP_INCREMENTAL);
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
    }


    // If the signHash option was provided, then make sure that the hash option
    // was given.
    if (signHash && (! hash))
    {
      int    msgID   = MSGID_BACKUPDB_SIGN_REQUIRES_HASH;
      String message = getMessage(msgID,
                                  ATTR_TASK_BACKUP_SIGN_HASH,
                                  ATTR_TASK_BACKUP_HASH);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return TaskState.STOPPED_BY_ERROR;
    }


    // Make sure that the backup directory exists.  If not, then create it.
    File backupDirFile = new File(backupDirectory);
    if (! backupDirFile.isAbsolute())
    {
      backupDirectory = DirectoryServer.getServerRoot() + File.separator +
                        backupDirectory;
      backupDirFile = new File(backupDirectory);
    }

    if (! backupDirFile.exists())
    {
      try
      {
        backupDirFile.mkdirs();
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_CREATE_BACKUP_DIR;
        String message = getMessage(msgID, backupDirectory,
                                    stackTraceToSingleLineString(e));
        System.err.println(message);
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    Map<String,ConfigEntry> configEntries =
         TaskUtils.getBackendConfigEntries();
    int numBackends = configEntries.size();


    boolean multiple;
    ArrayList<Backend> backendsToArchive = new ArrayList<Backend>(numBackends);

    if (backUpAll)
    {
      for (Map.Entry<String,ConfigEntry> mapEntry : configEntries.entrySet())
      {
        Backend b = DirectoryServer.getBackend(mapEntry.getKey());
        if (b != null && b.supportsBackup())
        {
          backendsToArchive.add(b);
        }
      }

      // We'll proceed as if we're backing up multiple backends in this case
      // even if there's just one.
      multiple = true;
    }
    else
    {
      // Iterate through the set of requested backends and make sure they can
      // be used.
      for (String id : backendIDList)
      {
        Backend b = DirectoryServer.getBackend(id);
        if (b == null || configEntries.get(id) == null)
        {
          int    msgID   = MSGID_BACKUPDB_NO_BACKENDS_FOR_ID;
          String message = getMessage(msgID, id);
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
        else if (! b.supportsBackup())
        {
          int    msgID   = MSGID_BACKUPDB_BACKUP_NOT_SUPPORTED;
          String message = getMessage(msgID, b.getBackendID());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                   msgID);
        }
        else
        {
          backendsToArchive.add(b);
        }
      }

      // It is an error if any of the requested backends could not be used.
      if (backendsToArchive.size() != backendIDList.size())
      {
        return TaskState.STOPPED_BY_ERROR;
      }

      // See if there are multiple backends to archive.
      multiple = (backendsToArchive.size() > 1);
    }


    // If there are no backends to archive, then print an error and exit.
    if (backendsToArchive.isEmpty())
    {
      int    msgID   = MSGID_BACKUPDB_NO_BACKENDS_TO_ARCHIVE;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return TaskState.STOPPED_BY_ERROR;
    }


    // Iterate through the backends to archive and back them up individually.
    boolean errorsEncountered = false;
    for (Backend b : backendsToArchive)
    {
      // Acquire a shared lock for this backend.
      try
      {
        String        lockFile      = LockFileManager.getBackendLockFileName(b);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_BACKUPDB_CANNOT_LOCK_BACKEND;
          String message = getMessage(msgID, b.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          errorsEncountered = true;
          continue;
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_BACKUPDB_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, b.getBackendID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;
        continue;
      }


      int    msgID = MSGID_BACKUPDB_STARTING_BACKUP;
      String message = getMessage(msgID, b.getBackendID());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);


      // Get the config entry for this backend.
      ConfigEntry configEntry = configEntries.get(b.getBackendID());


      // Get the path to the directory to use for this backup.  If we will be
      // backing up multiple backends (or if we are backing up all backends,
      // even if there's only one of them), then create a subdirectory for each
      // backend.
      String backupDirPath;
      if (multiple)
      {
        backupDirPath = backupDirectory + File.separator +
                        b.getBackendID();
      }
      else
      {
        backupDirPath = backupDirectory;
      }


      // If the directory doesn't exist, then create it.  If it does exist, then
      // see if it has a backup descriptor file.
      BackupDirectory backupDir;
      backupDirFile = new File(backupDirPath);
      if (backupDirFile.exists())
      {
        String descriptorPath = backupDirPath + File.separator +
                                BACKUP_DIRECTORY_DESCRIPTOR_FILE;
        File descriptorFile = new File(descriptorPath);
        if (descriptorFile.exists())
        {
          try
          {
            backupDir =
                 BackupDirectory.readBackupDirectoryDescriptor(backupDirPath);
          }
          catch (ConfigException ce)
          {
            msgID   = MSGID_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR;
            message = getMessage(msgID, descriptorPath, ce.getMessage());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
                message = getMessage(msgID, b.getBackendID(),
                                     String.valueOf(failureReason));
                logError(ErrorLogCategory.BACKEND,
                         ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              }
            }
            catch (Exception e)
            {
              msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
              message = getMessage(msgID, b.getBackendID(),
                                   stackTraceToSingleLineString(e));
              logError(ErrorLogCategory.BACKEND,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            }

            continue;
          }
          catch (Exception e)
          {
            msgID   = MSGID_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR;
            message = getMessage(msgID, descriptorPath,
                                 stackTraceToSingleLineString(e));
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
                message = getMessage(msgID, b.getBackendID(),
                                     String.valueOf(failureReason));
                logError(ErrorLogCategory.BACKEND,
                         ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              }
            }
            catch (Exception e2)
            {
              msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
              message = getMessage(msgID, b.getBackendID(),
                                   stackTraceToSingleLineString(e2));
              logError(ErrorLogCategory.BACKEND,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            }

            continue;
          }
        }
        else
        {
          backupDir = new BackupDirectory(backupDirPath, configEntry.getDN());
        }
      }
      else
      {
        try
        {
          backupDirFile.mkdirs();
        }
        catch (Exception e)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_CREATE_BACKUP_DIR;
          message = getMessage(msgID, backupDirPath,
                               stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          errorsEncountered = true;

          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(b);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
              message = getMessage(msgID, b.getBackendID(),
                                   String.valueOf(failureReason));
              logError(ErrorLogCategory.BACKEND,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            }
          }
          catch (Exception e2)
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 stackTraceToSingleLineString(e2));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }

          continue;
        }

        backupDir = new BackupDirectory(backupDirPath, configEntry.getDN());
      }


      // Create a backup configuration and determine whether the requested
      // backup can be performed using the selected backend.
      BackupConfig backupConfig = new BackupConfig(backupDir, backupID,
                                                   incremental);
      backupConfig.setCompressData(compress);
      backupConfig.setEncryptData(encrypt);
      backupConfig.setHashData(hash);
      backupConfig.setSignHash(signHash);
      backupConfig.setIncrementalBaseID(incrementalBase);

      StringBuilder unsupportedReason = new StringBuilder();
      if (! b.supportsBackup(backupConfig, unsupportedReason))
      {
        msgID   = MSGID_BACKUPDB_CANNOT_BACKUP;
        message = getMessage(msgID, b.getBackendID(),
                             unsupportedReason.toString());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
        }
        catch (Exception e2)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        }

        continue;
      }


      // Perform the backup.
      try
      {
        b.createBackup(configEntry, backupConfig);
      }
      catch (DirectoryException de)
      {
        msgID   = MSGID_BACKUPDB_ERROR_DURING_BACKUP;
        message = getMessage(msgID, b.getBackendID(),
                                    de.getErrorMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
        }
        catch (Exception e)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        }

        continue;
      }
      catch (Exception e)
      {
        msgID   = MSGID_BACKUPDB_ERROR_DURING_BACKUP;
        message = getMessage(msgID, b.getBackendID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
            message = getMessage(msgID, b.getBackendID(),
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
        }
        catch (Exception e2)
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        }

        continue;
      }


      // Release the shared lock for the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(b);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
          message = getMessage(msgID, b.getBackendID(),
                               String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          errorsEncountered = true;
        }
      }
      catch (Exception e)
      {
        msgID   = MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND;
        message = getMessage(msgID, b.getBackendID(),
                             stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        errorsEncountered = true;
      }
    }


    // Print a final completed message, indicating whether there were any errors
    // in the process.
    if (errorsEncountered)
    {
      int    msgID = MSGID_BACKUPDB_COMPLETED_WITH_ERRORS;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);
      return TaskState.COMPLETED_WITH_ERRORS;
    }
    else
    {
      int    msgID = MSGID_BACKUPDB_COMPLETED_SUCCESSFULLY;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
               msgID);
      return TaskState.COMPLETED_SUCCESSFULLY;
    }
  }


}
