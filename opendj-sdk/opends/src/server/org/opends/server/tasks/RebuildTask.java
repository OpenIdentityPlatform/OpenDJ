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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;

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
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
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
import static org.opends.server.messages.TaskMessages.
    MSGID_TASK_INDEXREBUILD_INSUFFICIENT_PRIVILEGES;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ToolMessages.
    MSGID_REBUILDINDEX_ERROR_DURING_REBUILD;
import static org.opends.server.messages.ToolMessages.
    MSGID_REBUILDINDEX_WRONG_BACKEND_TYPE;
import static org.opends.server.messages.ToolMessages.
    MSGID_NO_BACKENDS_FOR_BASE;
import static org.opends.server.messages.ToolMessages.
    MSGID_CANNOT_DECODE_BASE_DN;
import static org.opends.server.messages.ToolMessages.
    MSGID_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND;
import static org.opends.server.messages.ToolMessages.
    MSGID_REBUILDINDEX_CANNOT_SHARED_LOCK_BACKEND;
import static org.opends.server.messages.ToolMessages.
    MSGID_REBUILDINDEX_CANNOT_UNLOCK_BACKEND;
import static org.opends.server.config.ConfigConstants.
    ATTR_REBUILD_BASE_DN;
import static org.opends.server.config.ConfigConstants.
    ATTR_REBUILD_INDEX;
import static org.opends.server.config.ConfigConstants.
    ATTR_REBUILD_MAX_THREADS;

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
  int maxThreads = -1;

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
        int    msgID   = MSGID_TASK_INDEXREBUILD_INSUFFICIENT_PRIVILEGES;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message, msgID);
      }
    }


    Entry taskEntry = getTaskEntry();

    AttributeType typeBaseDN;
    AttributeType typeIndex;
    AttributeType typeMaxThreads;

    typeBaseDN =
         getAttributeType(ATTR_REBUILD_BASE_DN, true);
    typeIndex =
         getAttributeType(ATTR_REBUILD_INDEX, true);
    typeMaxThreads =
         getAttributeType(ATTR_REBUILD_MAX_THREADS, true);

    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeBaseDN);
    baseDN = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeIndex);
    indexes = TaskUtils.getMultiValueString(attrList);


    attrList = taskEntry.getAttribute(typeMaxThreads);
    maxThreads = TaskUtils.getSingleValueInteger(attrList, -1);
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
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, baseDN, de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    for(String index : indexes)
    {
      rebuildConfig.addRebuildIndex(index);
    }

    rebuildConfig.setMaxRebuildThreads(maxThreads);

    Backend backend =
        DirectoryServer.getBackendWithBaseDN(rebuildConfig.getBaseDN());

    if(backend == null)
    {
      int    msgID   = MSGID_NO_BACKENDS_FOR_BASE;
      String message = getMessage(msgID, baseDN);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    if (!(backend instanceof BackendImpl))
    {
      int    msgID   = MSGID_REBUILDINDEX_WRONG_BACKEND_TYPE;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    // Acquire a shared lock for the backend if we are rebuilding attribute
    // indexes only. If we are rebuilding one or more system indexes, we have
    // to aquire exclusive lock.
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();
    if(rebuildConfig.includesSystemIndex())
    {
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

        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 e.getErrorMessage(), e.getMessageID());
        return TaskState.STOPPED_BY_ERROR;
      }

      try
      {
        if(! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND;
          String message = getMessage(msgID, backend.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    else
    {
      try
      {
        if(! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_REBUILDINDEX_CANNOT_SHARED_LOCK_BACKEND;
          String message = getMessage(msgID, backend.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_REBUILDINDEX_CANNOT_SHARED_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    getExceptionMessage(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }

    }


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

      int    msgID   = MSGID_REBUILDINDEX_ERROR_DURING_REBUILD;
      String message = getMessage(msgID, e.getMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    // Release the lock on the backend.
    try
    {
      lockFile = LockFileManager.getBackendLockFileName(backend);
      failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        int    msgID   = MSGID_REBUILDINDEX_CANNOT_UNLOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
        return TaskState.COMPLETED_WITH_ERRORS;
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_REBUILDINDEX_CANNOT_UNLOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
      return TaskState.COMPLETED_WITH_ERRORS;
    }

    if(rebuildConfig.includesSystemIndex())
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

        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 e.getErrorMessage(), e.getMessageID());
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
