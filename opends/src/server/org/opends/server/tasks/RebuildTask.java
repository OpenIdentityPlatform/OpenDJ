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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;
import org.opends.messages.Message;
import org.opends.messages.TaskMessages;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.backends.jeb.RebuildConfig;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;


import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.Backend;

import static org.opends.server.core.DirectoryServer.getAttributeType;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;


import java.util.List;
import java.util.ArrayList;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to rebuild indexes in the JEB backend..
 */
public class RebuildTask extends Task
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  String baseDN = null;
  ArrayList<String> indexes = null;
  private String tmpDirectory = null;
  private boolean rebuildAll = false;

  /**
   * {@inheritDoc}
   */
  public Message getDisplayName() {
    return TaskMessages.INFO_TASK_REBUILD_NAME.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
    // If the client connection is available, then make sure the associated
    // client has the INDEX_REBUILD privilege.

    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (! clientConnection.hasPrivilege(Privilege.LDIF_IMPORT, operation))
      {
        Message message = ERR_TASK_INDEXREBUILD_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }


    Entry taskEntry = getTaskEntry();

    AttributeType typeBaseDN;
    AttributeType typeIndex;
    AttributeType typeTmpDirectory;

    typeBaseDN =
         getAttributeType(ATTR_REBUILD_BASE_DN, true);
    typeIndex =
         getAttributeType(ATTR_REBUILD_INDEX, true);
    typeTmpDirectory =
         getAttributeType(ATTR_REBUILD_TMP_DIRECTORY, true);

    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeBaseDN);
    baseDN = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeIndex);
    indexes = TaskUtils.getMultiValueString(attrList);

    if(isRebuildAll(indexes))
    {
      if(indexes.size() != 1)
      {
        Message msg = ERR_TASK_INDEXREBUILD_ALL_ERROR.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
      }
      rebuildAll = true;
      indexes.clear();
    }

    attrList = taskEntry.getAttribute(typeTmpDirectory);
    tmpDirectory = TaskUtils.getSingleValueString(attrList);

  }

  private boolean isRebuildAll(List<String> indexList)
  {
    for(String s : indexList)
    {
      if(s.equalsIgnoreCase(REBUILD_ALL))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    RebuildConfig rebuildConfig = new RebuildConfig();

    try
    {
      rebuildConfig.setBaseDN(DN.decode(baseDN));
    }
    catch(DirectoryException de)
    {
      Message message =
          ERR_CANNOT_DECODE_BASE_DN.get(baseDN, de.getMessageObject());
      logError(message);
      return TaskState.STOPPED_BY_ERROR;
    }

    for(String index : indexes)
    {
      rebuildConfig.addRebuildIndex(index);
    }


    Backend backend =
        DirectoryServer.getBackendWithBaseDN(rebuildConfig.getBaseDN());

    if(backend == null)
    {
      Message message = ERR_NO_BACKENDS_FOR_BASE.get(baseDN);
      logError(message);
      return TaskState.STOPPED_BY_ERROR;
    }

    if (!(backend instanceof BackendImpl))
    {
      Message message = ERR_REBUILDINDEX_WRONG_BACKEND_TYPE.get();
      logError(message);
      return TaskState.STOPPED_BY_ERROR;
    }

    // Acquire a shared lock for the backend if we are rebuilding attribute
    // indexes only. If we are rebuilding one or more system indexes, we have
    // to aquire exclusive lock.
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();

    // Disable the backend.
    try
    {
      TaskUtils.disableBackend(backend.getBackendID());
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      logError(e.getMessageObject());
      return TaskState.STOPPED_BY_ERROR;
    }

    try
    {
      if(! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        Message message = ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
      return TaskState.STOPPED_BY_ERROR;
    }

     if(tmpDirectory == null)
    {
      tmpDirectory = "import-tmp";
    }
    rebuildConfig.setTmpDirectory(tmpDirectory);
    rebuildConfig.setRebuildAll(rebuildAll);
    TaskState returnCode = TaskState.COMPLETED_SUCCESSFULLY;
    // Launch the rebuild process.
    try
    {
      BackendImpl jebBackend = (BackendImpl)backend;
      jebBackend.rebuildBackend(rebuildConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_REBUILDINDEX_ERROR_DURING_REBUILD.get(e.getMessage());
      logError(message);
      returnCode = TaskState.STOPPED_BY_ERROR;
    }
    // Release the lock on the backend.
    finally
    {
      try
      {
        lockFile = LockFileManager.getBackendLockFileName(backend);
        failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          Message message = WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          logError(message);
          returnCode = TaskState.COMPLETED_WITH_ERRORS;
        }
      }
      catch (Throwable t)
      {
        Message message = WARN_REBUILDINDEX_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), getExceptionMessage(t));
        logError(message);
        returnCode = TaskState.COMPLETED_WITH_ERRORS;
      }
    }

    if(returnCode == TaskState.COMPLETED_SUCCESSFULLY)
    {
      // Enable the backend.
      try
      {
        TaskUtils.enableBackend(backend.getBackendID());
      }
      catch (DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(e.getMessageObject());
        returnCode = TaskState.STOPPED_BY_ERROR;
      }
    }

    return returnCode;
  }
}
