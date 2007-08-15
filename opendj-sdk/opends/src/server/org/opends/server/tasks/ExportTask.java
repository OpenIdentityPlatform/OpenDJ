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
import org.opends.messages.Message;

import static org.opends.server.core.DirectoryServer.getAttributeType;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to export the contents of a Directory Server backend to an LDIF file.
 */
public class ExportTask extends Task
{



  private String  ldifFile;
  private String  backendID;
  private int     wrapColumn;
  private boolean appendToLDIF;
  private boolean compressLDIF;
  private boolean encryptLDIF;
  private boolean signHash;
  private ArrayList<String> includeAttributeStrings;
  private ArrayList<String> excludeAttributeStrings;
  private ArrayList<String> includeFilterStrings;
  private ArrayList<String> excludeFilterStrings;
  private ArrayList<String> includeBranchStrings;
  private ArrayList<String> excludeBranchStrings;

  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
    // If the client connection is available, then make sure the associated
    // client has the LDIF_EXPORT privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (! clientConnection.hasPrivilege(Privilege.LDIF_EXPORT, operation))
      {
        Message message = ERR_TASK_LDIFEXPORT_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }


    Entry taskEntry = getTaskEntry();

    AttributeType typeLdifFile;
    AttributeType typeBackendID;
    AttributeType typeAppendToLDIF;
    AttributeType typeCompressLDIF;
    AttributeType typeEncryptLDIF;
    AttributeType typeSignHash;
    AttributeType typeIncludeAttribute;
    AttributeType typeExcludeAttribute;
    AttributeType typeIncludeFilter;
    AttributeType typeExcludeFilter;
    AttributeType typeIncludeBranch;
    AttributeType typeExcludeBranch;
    AttributeType typeWrapColumn;


    typeLdifFile =
         getAttributeType(ATTR_TASK_EXPORT_LDIF_FILE, true);
    typeBackendID =
         getAttributeType(ATTR_TASK_EXPORT_BACKEND_ID, true);
    typeAppendToLDIF =
         getAttributeType(ATTR_TASK_EXPORT_APPEND_TO_LDIF, true);
    typeCompressLDIF =
         getAttributeType(ATTR_TASK_EXPORT_COMPRESS_LDIF, true);
    typeEncryptLDIF =
         getAttributeType(ATTR_TASK_EXPORT_ENCRYPT_LDIF, true);
    typeSignHash =
         getAttributeType(ATTR_TASK_EXPORT_SIGN_HASH, true);
    typeIncludeAttribute =
         getAttributeType(ATTR_TASK_EXPORT_INCLUDE_ATTRIBUTE, true);
    typeExcludeAttribute =
         getAttributeType(ATTR_TASK_EXPORT_EXCLUDE_ATTRIBUTE, true);
    typeIncludeFilter =
         getAttributeType(ATTR_TASK_EXPORT_INCLUDE_FILTER, true);
    typeExcludeFilter =
         getAttributeType(ATTR_TASK_EXPORT_EXCLUDE_FILTER, true);
    typeIncludeBranch =
         getAttributeType(ATTR_TASK_EXPORT_INCLUDE_BRANCH, true);
    typeExcludeBranch =
         getAttributeType(ATTR_TASK_EXPORT_EXCLUDE_BRANCH, true);
    typeWrapColumn =
         getAttributeType(ATTR_TASK_EXPORT_WRAP_COLUMN, true);


    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeLdifFile);
    ldifFile = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeBackendID);
    backendID = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeAppendToLDIF);
    appendToLDIF = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeCompressLDIF);
    compressLDIF = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeEncryptLDIF);
    encryptLDIF = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeSignHash);
    signHash = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeIncludeAttribute);
    includeAttributeStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeExcludeAttribute);
    excludeAttributeStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeIncludeFilter);
    includeFilterStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeExcludeFilter);
    excludeFilterStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeIncludeBranch);
    includeBranchStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeExcludeBranch);
    excludeBranchStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeWrapColumn);
    wrapColumn = TaskUtils.getSingleValueInteger(attrList, 0);

  }

  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    // See if there were any user-defined sets of include/exclude attributes or
    // filters.  If so, then process them.
    HashSet<AttributeType> excludeAttributes;
    if (excludeAttributeStrings == null)
    {
      excludeAttributes = null;
    }
    else
    {
      excludeAttributes = new HashSet<AttributeType>();
      for (String attrName : excludeAttributeStrings)
      {
        String        lowerName = attrName.toLowerCase();
        AttributeType attrType  = DirectoryServer.getAttributeType(lowerName);
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(attrName);
        }

        excludeAttributes.add(attrType);
      }
    }

    HashSet<AttributeType> includeAttributes;
    if (includeAttributeStrings == null)
    {
      includeAttributes = null;
    }
    else
    {
      includeAttributes = new HashSet<AttributeType>();
      for (String attrName : includeAttributeStrings)
      {
        String        lowerName = attrName.toLowerCase();
        AttributeType attrType  = DirectoryServer.getAttributeType(lowerName);
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(attrName);
        }

        includeAttributes.add(attrType);
      }
    }

    ArrayList<SearchFilter> excludeFilters;
    if (excludeFilterStrings == null)
    {
      excludeFilters = null;
    }
    else
    {
      excludeFilters = new ArrayList<SearchFilter>();
      for (String filterString : excludeFilterStrings)
      {
        try
        {
          excludeFilters.add(SearchFilter.createFilterFromString(filterString));
        }
        catch (DirectoryException de)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER.get(
              filterString, de.getMessageObject());
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER.get(
              filterString, getExceptionMessage(e));
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
    }

    ArrayList<SearchFilter> includeFilters;
    if (includeFilterStrings == null)
    {
      includeFilters = null;
    }
    else
    {
      includeFilters = new ArrayList<SearchFilter>();
      for (String filterString : includeFilterStrings)
      {
        try
        {
          includeFilters.add(SearchFilter.createFilterFromString(filterString));
        }
        catch (DirectoryException de)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
              filterString, de.getMessageObject());
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
              filterString, getExceptionMessage(e));
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
    }

    // Get the backend into which the LDIF should be imported.
    Backend       backend;
    ArrayList<DN> defaultIncludeBranches;

    backend = DirectoryServer.getBackend(backendID);

    if (backend == null)
    {
      Message message = ERR_LDIFEXPORT_NO_BACKENDS_FOR_ID.get(backendID);
      logError(message);
      return TaskState.STOPPED_BY_ERROR;
    }
    else if (! backend.supportsLDIFExport())
    {
      Message message = ERR_LDIFEXPORT_CANNOT_EXPORT_BACKEND.get(backendID);
      logError(message);
      return TaskState.STOPPED_BY_ERROR;
    }

    defaultIncludeBranches = new ArrayList<DN>(backend.getBaseDNs().length);
    for (DN dn : backend.getBaseDNs())
    {
      defaultIncludeBranches.add(dn);
    }

    ArrayList<DN> excludeBranches = new ArrayList<DN>();
    if (excludeBranchStrings != null)
    {
      for (String s : excludeBranchStrings)
      {
        DN excludeBranch;
        try
        {
          excludeBranch = DN.decode(s);
        }
        catch (DirectoryException de)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
              s, de.getMessageObject());
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
              s, getExceptionMessage(e));
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }

        if (! excludeBranches.contains(excludeBranch))
        {
          excludeBranches.add(excludeBranch);
        }
      }
    }


    ArrayList<DN> includeBranches;
    if (!includeBranchStrings.isEmpty())
    {
      includeBranches = new ArrayList<DN>();
      for (String s : includeBranchStrings)
      {
        DN includeBranch;
        try
        {
          includeBranch = DN.decode(s);
        }
        catch (DirectoryException de)
        {
          Message message = ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE.get(
              s, de.getMessageObject());
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE.get(
              s, getExceptionMessage(e));
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }

        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          Message message =
              ERR_LDIFEXPORT_INVALID_INCLUDE_BASE.get(s, backendID);
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }

        includeBranches.add(includeBranch);
      }
    }
    else
    {
      includeBranches = defaultIncludeBranches;
    }


    // Create the LDIF export configuration to use when reading the LDIF.
    ExistingFileBehavior existingBehavior;
    if (appendToLDIF)
    {
      existingBehavior = ExistingFileBehavior.APPEND;
    }
    else
    {
      existingBehavior = ExistingFileBehavior.OVERWRITE;
    }

    LDIFExportConfig exportConfig =
         new LDIFExportConfig(ldifFile, existingBehavior);
    exportConfig.setCompressData(compressLDIF);
    exportConfig.setEncryptData(encryptLDIF);
    exportConfig.setExcludeAttributes(excludeAttributes);
    exportConfig.setExcludeBranches(excludeBranches);
    exportConfig.setExcludeFilters(excludeFilters);
    exportConfig.setIncludeAttributes(includeAttributes);
    exportConfig.setIncludeBranches(includeBranches);
    exportConfig.setIncludeFilters(includeFilters);
    exportConfig.setSignHash(signHash);
    exportConfig.setWrapColumn(wrapColumn);

    // FIXME -- Should this be conditional?
    exportConfig.setInvokeExportPlugins(true);


    // Get the set of base DNs for the backend as an array.
    DN[] baseDNs = new DN[defaultIncludeBranches.size()];
    defaultIncludeBranches.toArray(baseDNs);


    // From here we must make sure we close the export config.
    try
    {
      // Acquire a shared lock for the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          Message message = ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), getExceptionMessage(e));
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }


      // From here we must make sure we release the shared backend lock.
      try
      {
        // Launch the export.
        try
        {
          DirectoryServer.notifyExportBeginning(backend, exportConfig);
          backend.exportLDIF(exportConfig);
          DirectoryServer.notifyExportEnded(backend, exportConfig, true);
        }
        catch (DirectoryException de)
        {
          DirectoryServer.notifyExportEnded(backend, exportConfig, false);
          Message message =
              ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(de.getMessageObject());
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          DirectoryServer.notifyExportEnded(backend, exportConfig, false);
          Message message =
              ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(getExceptionMessage(e));
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      finally
      {
        // Release the shared lock on the backend.
        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            Message message = WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND.get(
                backend.getBackendID(), String.valueOf(failureReason));
            logError(message);
            return TaskState.COMPLETED_WITH_ERRORS;
          }
        }
        catch (Exception e)
        {
          Message message = WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), getExceptionMessage(e));
          logError(message);
          return TaskState.COMPLETED_WITH_ERRORS;
        }
      }
    }
    finally
    {
      // Clean up after the export by closing the export config.
      exportConfig.close();
    }


    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
