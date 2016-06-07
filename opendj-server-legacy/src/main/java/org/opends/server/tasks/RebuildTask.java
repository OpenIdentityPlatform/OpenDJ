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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.messages.TaskMessages;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.RebuildConfig.RebuildMode;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;

/**
 * This class provides an implementation of a Directory Server task that can be
 * used to rebuild indexes in a backend.
 */
public class RebuildTask extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private String baseDN;
  private ArrayList<String> indexes;
  private String tmpDirectory;
  private RebuildMode rebuildMode = RebuildMode.USER_DEFINED;
  private boolean isClearDegradedState;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName()
  {
    return TaskMessages.INFO_TASK_REBUILD_NAME.get();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeTask() throws DirectoryException
  {
    // If the client connection is available, then make sure the associated
    // client has the INDEX_REBUILD privilege.

    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (!clientConnection.hasPrivilege(Privilege.LDIF_IMPORT, operation))
      {
        LocalizableMessage message = ERR_TASK_INDEXREBUILD_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            message);
      }
    }

    Entry taskEntry = getTaskEntry();

    baseDN = asString(taskEntry, ATTR_REBUILD_BASE_DN);
    tmpDirectory = asString(taskEntry, ATTR_REBUILD_TMP_DIRECTORY);
    final String val = asString(taskEntry, ATTR_REBUILD_INDEX_CLEARDEGRADEDSTATE);
    isClearDegradedState = Boolean.parseBoolean(val);

    AttributeType typeIndex = getSchema().getAttributeType(ATTR_REBUILD_INDEX);
    List<Attribute> attrList = taskEntry.getAttribute(typeIndex);
    indexes = TaskUtils.getMultiValueString(attrList);

    rebuildMode = getRebuildMode(indexes);
    if (rebuildMode != RebuildMode.USER_DEFINED)
    {
      if (indexes.size() != 1)
      {
        LocalizableMessage msg = ERR_TASK_INDEXREBUILD_ALL_ERROR.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
      }
      indexes.clear();
    }
  }

  private String asString(Entry taskEntry, String attrName)
  {
    final AttributeType attrType = getSchema().getAttributeType(attrName);
    final List<Attribute> attrList = taskEntry.getAttribute(attrType);
    return TaskUtils.getSingleValueString(attrList);
  }

  private RebuildMode getRebuildMode(List<String> indexList)
  {
    for (String s : indexList)
    {
      if (REBUILD_ALL.equalsIgnoreCase(s))
      {
        return RebuildMode.ALL;
      }
      else if (REBUILD_DEGRADED.equalsIgnoreCase(s))
      {
        return RebuildMode.DEGRADED;
      }
    }
    return RebuildMode.USER_DEFINED;
  }

  @Override
  protected TaskState runTask()
  {
    RebuildConfig rebuildConfig = new RebuildConfig();

    try
    {
      rebuildConfig.setBaseDN(DN.valueOf(baseDN));
    }
    catch (LocalizedIllegalArgumentException e)
    {
      logger.error(ERR_CANNOT_DECODE_BASE_DN, baseDN, e.getMessageObject());
      return TaskState.STOPPED_BY_ERROR;
    }

    for (final String index : indexes)
    {
      rebuildConfig.addRebuildIndex(index);
    }

    // The degraded state is set(if present in args)
    // during the initialization.
    rebuildConfig.isClearDegradedState(isClearDegradedState);
    boolean isBackendNeedToBeEnabled = false;

    if (tmpDirectory == null)
    {
      tmpDirectory = "import-tmp";
    }
    rebuildConfig.setTmpDirectory(tmpDirectory);
    rebuildConfig.setRebuildMode(rebuildMode);

    final Backend<?> backend = DirectoryServer.getBackendWithBaseDN(rebuildConfig.getBaseDN());
    if (backend == null)
    {
      logger.error(ERR_NO_BACKENDS_FOR_BASE, baseDN);
      return TaskState.STOPPED_BY_ERROR;
    }
    if (!backend.supports(BackendOperation.INDEXING))
    {
      logger.error(ERR_REBUILDINDEX_WRONG_BACKEND_TYPE);
      return TaskState.STOPPED_BY_ERROR;
    }

    // If we are rebuilding one or more system indexes, we have
    // to acquire exclusive lock. Shared lock in 'cleardegradedstate' mode.
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();

    // Disable the backend
    // Except in 'cleardegradedstate' mode we don't need to disable it.
    if (!isClearDegradedState)
    {
      try
      {
        TaskUtils.disableBackend(backend.getBackendID());
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);

        logger.error(e.getMessageObject());
        return TaskState.STOPPED_BY_ERROR;
      }

      try
      {
        if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
        {
          logger.error(ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND, backend.getBackendID(), failureReason);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        logger.error(ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND, backend
                .getBackendID(), getExceptionMessage(e));
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    else
    {
      // We just need a shared lock on the backend for this part.
      try
      {
        if (!LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          logger.error(ERR_REBUILDINDEX_CANNOT_SHARED_LOCK_BACKEND, backend.getBackendID(), failureReason);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        logger.error(ERR_REBUILDINDEX_CANNOT_SHARED_LOCK_BACKEND, backend
                .getBackendID(), getExceptionMessage(e));
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    TaskState returnCode = TaskState.COMPLETED_SUCCESSFULLY;

    // Launch the rebuild process.
    try
    {
      backend.rebuildBackend(rebuildConfig, DirectoryServer.getInstance().getServerContext());
    }
    catch (InitializationException e)
    {
      // This exception catches all 'index not found'
      // The backend needs to be re-enabled at the end of the process.
      LocalizableMessage message =
          ERR_REBUILDINDEX_ERROR_DURING_REBUILD.get(getExceptionMessage(e));
      logger.traceException(e);
      logger.error(message);
      isBackendNeedToBeEnabled = true;
      returnCode = TaskState.STOPPED_BY_ERROR;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      logger.error(ERR_REBUILDINDEX_ERROR_DURING_REBUILD, getExceptionMessage(e));
      returnCode = TaskState.STOPPED_BY_ERROR;
    }
    finally
    {
      // Release the lock on the backend.
      try
      {
        lockFile = LockFileManager.getBackendLockFileName(backend);
        failureReason = new StringBuilder();
        if (!LockFileManager.releaseLock(lockFile, failureReason))
        {
          logger.warn(WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
          returnCode = TaskState.COMPLETED_WITH_ERRORS;
        }
      }
      catch (Throwable t)
      {
        logger.warn(WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND, backend.getBackendID(),
                getExceptionMessage(t));
        returnCode = TaskState.COMPLETED_WITH_ERRORS;
      }
    }

    // The backend must be enabled only if the task is successful
    // for prevent potential risks of database corruption.
    if ((returnCode == TaskState.COMPLETED_SUCCESSFULLY || isBackendNeedToBeEnabled)
        && !isClearDegradedState)
    {
      // Enable the backend.
      try
      {
        TaskUtils.enableBackend(backend.getBackendID());
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);

        logger.error(e.getMessageObject());
        returnCode = TaskState.STOPPED_BY_ERROR;
      }
    }

    return returnCode;
  }
}
