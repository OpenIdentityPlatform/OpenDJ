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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2025 3A Systems, LLC.
 */
package org.opends.server.tasks;

import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.messages.Severity;
import org.opends.messages.TaskMessages;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.LocalBackend.BackendOperation;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;

/**
 * This class provides an implementation of a Directory Server task that may be
 * used to back up a Directory Server backend in a binary form that may be
 * quickly archived and restored.
 */
public class BackupTask extends Task
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** Stores mapping between configuration attribute name and its label. */
  private static Map<String,LocalizableMessage> argDisplayMap = new HashMap<>();
  static {
    argDisplayMap.put(ATTR_TASK_BACKUP_ALL, INFO_BACKUP_ARG_BACKUPALL.get());
    argDisplayMap.put(ATTR_TASK_BACKUP_COMPRESS, INFO_BACKUP_ARG_COMPRESS.get());
    argDisplayMap.put(ATTR_TASK_BACKUP_ENCRYPT, INFO_BACKUP_ARG_ENCRYPT.get());
    argDisplayMap.put(ATTR_TASK_BACKUP_HASH, INFO_BACKUP_ARG_HASH.get());
    argDisplayMap.put(ATTR_TASK_BACKUP_INCREMENTAL, INFO_BACKUP_ARG_INCREMENTAL.get());
    argDisplayMap.put(ATTR_TASK_BACKUP_SIGN_HASH, INFO_BACKUP_ARG_SIGN_HASH.get());
    argDisplayMap.put(ATTR_TASK_BACKUP_BACKEND_ID, INFO_BACKUP_ARG_BACKEND_IDS.get());
    argDisplayMap.put(ATTR_BACKUP_ID, INFO_BACKUP_ARG_BACKUP_ID.get());
    argDisplayMap.put(ATTR_BACKUP_DIRECTORY_PATH, INFO_BACKUP_ARG_BACKUP_DIR.get());
    argDisplayMap.put(ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID, INFO_BACKUP_ARG_INC_BASE_ID.get());
  }


  // The task arguments.
  private boolean backUpAll;
  private boolean compress;
  private boolean encrypt;
  private boolean hash;
  private boolean incremental;
  private boolean signHash;
  private List<String>  backendIDList;
  private String  backupID;
  private File    backupDirectory;
  private String  incrementalBase;

  private BackupConfig backupConfig;

  /**
   * All the backend configuration entries defined in the server mapped
   * by their backend ID.
   */
  private Map<String,Entry> configEntries;

  private ArrayList<LocalBackend<?>> backendsToArchive;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_BACKUP_NAME.get();
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getAttributeDisplayName(String attrName) {
    return argDisplayMap.get(attrName);
  }

  /** {@inheritDoc} */
  @Override
  public void initializeTask() throws DirectoryException
  {
    // If the client connection is available, then make sure the associated
    // client has the BACKEND_BACKUP privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (! clientConnection.hasPrivilege(Privilege.BACKEND_BACKUP, operation))
      {
        LocalizableMessage message = ERR_TASK_BACKUP_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }


    Entry taskEntry = getTaskEntry();

    backUpAll = TaskUtils.getBoolean(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_ALL), false);
    compress = TaskUtils.getBoolean(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_COMPRESS), false);
    encrypt = TaskUtils.getBoolean(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_ENCRYPT), false);
    hash = TaskUtils.getBoolean(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_HASH), false);
    incremental = TaskUtils.getBoolean(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_INCREMENTAL), false);
    signHash = TaskUtils.getBoolean(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_SIGN_HASH), false);
    backendIDList = TaskUtils.getMultiValueString(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_BACKEND_ID));
    backupID = TaskUtils.getSingleValueString(taskEntry.getAllAttributes(ATTR_BACKUP_ID));

    String backupDirectoryPath = TaskUtils.getSingleValueString(taskEntry.getAllAttributes(ATTR_BACKUP_DIRECTORY_PATH));
    backupDirectory = new File(backupDirectoryPath);
    if (! backupDirectory.isAbsolute())
    {
      backupDirectory = new File(DirectoryServer.getInstanceRoot(), backupDirectoryPath);
    }

    incrementalBase = TaskUtils.getSingleValueString(taskEntry.getAllAttributes(ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID));

    configEntries = TaskUtils.getBackendConfigEntries();
  }


  /**
   * Validate the task arguments and construct the list of backends to be
   * archived.
   * @return  true if the task arguments are valid.
   */
  private boolean argumentsAreValid()
  {
    // Make sure that either the backUpAll argument was provided or at least one
    // backend ID was given.  They are mutually exclusive.
    if (backUpAll)
    {
      if (!backendIDList.isEmpty())
      {
        logger.error(ERR_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID,
            ATTR_TASK_BACKUP_ALL, ATTR_TASK_BACKUP_BACKEND_ID);
        return false;
      }
    }
    else if (backendIDList.isEmpty())
    {
      logger.error(ERR_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID,
          ATTR_TASK_BACKUP_ALL, ATTR_TASK_BACKUP_BACKEND_ID);
      return false;
    }


    // Use task id for backup id in case of recurring task.
    if (super.isRecurring()) {
      backupID = super.getTaskID();
    }


    // If no backup ID was provided, then create one with the current timestamp.
    if (backupID == null)
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      backupID = dateFormat.format(new Date());
    }


    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    if (incrementalBase != null && ! incremental)
    {
      logger.error(ERR_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL, ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID,
              ATTR_TASK_BACKUP_INCREMENTAL);
      return false;
    }


    // If the signHash option was provided, then make sure that the hash option
    // was given.
    if (signHash && !hash)
    {
      logger.error(ERR_BACKUPDB_SIGN_REQUIRES_HASH, ATTR_TASK_BACKUP_SIGN_HASH, ATTR_TASK_BACKUP_HASH);
      return false;
    }


    // Make sure that the backup directory exists.  If not, then create it.
    if (! backupDirectory.exists())
    {
      try
      {
        backupDirectory.mkdirs();
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR.get(
                backupDirectory.getPath(), getExceptionMessage(e));
        System.err.println(message);
        return false;
      }
    }

    int numBackends = configEntries.size();


    backendsToArchive = new ArrayList<>(numBackends);

    if (backUpAll)
    {
      for (Map.Entry<String,Entry> mapEntry : configEntries.entrySet())
      {
        LocalBackend<?> b = getServerContext().getBackendConfigManager().getLocalBackendById(mapEntry.getKey());
        if (b != null && b.supports(BackendOperation.BACKUP))
        {
          backendsToArchive.add(b);
        }
      }
    }
    else
    {
      // Iterate through the set of requested backends and make sure they can
      // be used.
      for (String id : backendIDList)
      {
        LocalBackend<?> b = getServerContext().getBackendConfigManager().getLocalBackendById(id);
        if (b == null || configEntries.get(id) == null)
        {
          logger.error(ERR_BACKUPDB_NO_BACKENDS_FOR_ID, id);
        }
        else if (!b.supports(BackendOperation.BACKUP))
        {
          logger.warn(WARN_BACKUPDB_BACKUP_NOT_SUPPORTED, b.getBackendID());
        }
        else
        {
          backendsToArchive.add(b);
        }
      }

      // It is an error if any of the requested backends could not be used.
      if (backendsToArchive.size() != backendIDList.size())
      {
        return false;
      }
    }


    // If there are no backends to archive, then print an error and exit.
    if (backendsToArchive.isEmpty())
    {
      logger.warn(WARN_BACKUPDB_NO_BACKENDS_TO_ARCHIVE);
      return false;
    }


    return true;
  }


  /**
   * Archive a single backend, where the backend is known to support backups.
   * @param b The backend to be archived.
   * @param backupLocation The backup directory.
   * @return true if the backend was successfully archived.
   */
  private boolean backupBackend(LocalBackend<?> b, File backupLocation)
  {
    // Get the config entry for this backend.
    BackendCfg cfg = TaskUtils.getConfigEntry(b);


    // If the directory doesn't exist, then create it.  If it does exist, then
    // see if it has a backup descriptor file.
    BackupDirectory backupDir;
    if (backupLocation.exists())
    {
      String descriptorPath = backupLocation.getPath() + File.separator +
                              BACKUP_DIRECTORY_DESCRIPTOR_FILE;
      File descriptorFile = new File(descriptorPath);
      if (descriptorFile.exists())
      {
        try
        {
          backupDir = BackupDirectory.readBackupDirectoryDescriptor(
               backupLocation.getPath());

          // Check the current backup directory corresponds to the provided
          // backend
          if (! backupDir.getConfigEntryDN().equals(cfg.dn()))
          {
            logger.error(ERR_BACKUPDB_CANNOT_BACKUP_IN_DIRECTORY, b.getBackendID(), backupLocation.getPath(),
                backupDir.getConfigEntryDN().rdn().getFirstAVA().getAttributeValue());
            return false ;
          }
        }
        catch (ConfigException ce)
        {
          logger.error(ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR, descriptorPath, ce.getMessage());
          return false;
        }
        catch (Exception e)
        {
          logger.error(ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR, descriptorPath, getExceptionMessage(e));
          return false;
        }
      }
      else
      {
        backupDir = new BackupDirectory(backupLocation.getPath(), cfg.dn());
      }
    }
    else
    {
      try
      {
        backupLocation.mkdirs();
      }
      catch (Exception e)
      {
        logger.error(ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR, backupLocation.getPath(), getExceptionMessage(e));
        return false;
      }

      backupDir = new BackupDirectory(backupLocation.getPath(),
                                      cfg.dn());
    }


    // Create a backup configuration.
    backupConfig = new BackupConfig(backupDir, backupID,
                                                 incremental);
    backupConfig.setCompressData(compress);
    backupConfig.setEncryptData(encrypt);
    backupConfig.setHashData(hash);
    backupConfig.setSignHash(signHash);
    backupConfig.setIncrementalBaseID(incrementalBase);


    // Perform the backup.
    try
    {
      DirectoryServer.notifyBackupBeginning(b, backupConfig);
      b.createBackup(backupConfig);
      DirectoryServer.notifyBackupEnded(b, backupConfig, true);
    }
    catch (DirectoryException de)
    {
      DirectoryServer.notifyBackupEnded(b, backupConfig, false);
      logger.error(ERR_BACKUPDB_ERROR_DURING_BACKUP, b.getBackendID(), de.getMessageObject());
      return false;
    }
    catch (Exception e)
    {
      DirectoryServer.notifyBackupEnded(b, backupConfig, false);
      logger.error(ERR_BACKUPDB_ERROR_DURING_BACKUP, b.getBackendID(), getExceptionMessage(e));
      return false;
    }finally {
      backupConfig=null;
    }

    return true;
  }

  /**
   * Acquire a shared lock on a backend.
   * @param b The backend on which the lock is to be acquired.
   * @return true if the lock was successfully acquired.
   */
  private boolean lockBackend(LocalBackend<?> b)
  {
    try
    {
      String        lockFile      = LockFileManager.getBackendLockFileName(b);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        logger.error(ERR_BACKUPDB_CANNOT_LOCK_BACKEND, b.getBackendID(), failureReason);
        return false;
      }
    }
    catch (Exception e)
    {
      logger.error(ERR_BACKUPDB_CANNOT_LOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
      return false;
    }

    return true;
  }

  /**
   * Release a lock on a backend.
   * @param b The backend on which the lock is held.
   * @return true if the lock was successfully released.
   */
  private boolean unlockBackend(LocalBackend<?> b)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(b);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
        return false;
      }
    }
    catch (Exception e)
    {
      logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
      return false;
    }

    return true;
  }


  /** {@inheritDoc} */
  @Override
  public void interruptTask(TaskState interruptState, LocalizableMessage interruptReason)
  {
    if (TaskState.STOPPED_BY_ADMINISTRATOR.equals(interruptState) &&
            backupConfig != null)
    {
      addLogMessage(Severity.INFORMATION, TaskMessages.INFO_TASK_STOPPED_BY_ADMIN.get(
      interruptReason));
      setTaskInterruptState(interruptState);
      backupConfig.cancel();
      backupConfig=null;
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
    if (!argumentsAreValid())
    {
      return TaskState.STOPPED_BY_ERROR;
    }

    boolean multiple;
    if (backUpAll)
    {
      // We'll proceed as if we're backing up multiple backends in this case
      // even if there's just one.
      multiple = true;
    }
    else
    {
      // See if there are multiple backends to archive.
      multiple = backendsToArchive.size() > 1;
    }


    // Iterate through the backends to archive and back them up individually.
    boolean errorsEncountered = false;
    for (LocalBackend<?> b : backendsToArchive)
    {
      if (isCancelled())
      {
        break;
      }

      // Acquire a shared lock for this backend.
      if (!lockBackend(b))
      {
        errorsEncountered = true;
        continue;
      }


      try
      {
        logger.info(NOTE_BACKUPDB_STARTING_BACKUP, b.getBackendID());


        // Get the path to the directory to use for this backup.  If we will be
        // backing up multiple backends (or if we are backing up all backends,
        // even if there's only one of them), then create a subdirectory for
        // each
        // backend.
        File backupLocation;
        if (multiple)
        {
          backupLocation = new File(backupDirectory, b.getBackendID());
        }
        else
        {
          backupLocation = backupDirectory;
        }


        if (!backupBackend(b, backupLocation))
        {
          errorsEncountered = true;
        }
      }
      finally
      {
        // Release the shared lock for the backend.
        if (!unlockBackend(b))
        {
          errorsEncountered = true;
        }
      }
    }


    // Print a final completed message, indicating whether there were any errors
    // in the process.  In this case it means that the backup could not be
    // completed at least for one of the backends.
    if (errorsEncountered)
    {
      logger.info(NOTE_BACKUPDB_COMPLETED_WITH_ERRORS);
      return TaskState.STOPPED_BY_ERROR;
    }
    else if (isCancelled())
    {
      logger.info(NOTE_BACKUPDB_CANCELLED);
      return getTaskInterruptState();
    }
    else
    {
      logger.info(NOTE_BACKUPDB_COMPLETED_SUCCESSFULLY);
      return TaskState.COMPLETED_SUCCESSFULLY;
    }
  }


}
