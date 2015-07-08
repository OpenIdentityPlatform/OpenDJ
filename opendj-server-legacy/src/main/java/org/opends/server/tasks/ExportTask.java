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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.tasks;

import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.messages.Severity;
import org.opends.messages.TaskMessages;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchFilter;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to export the contents of a Directory Server backend to an LDIF file.
 */
public class ExportTask extends Task
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /** Stores mapping between configuration attribute name and its label. */
  private static Map<String,LocalizableMessage> argDisplayMap = new HashMap<>();
  static {
    argDisplayMap.put(ATTR_TASK_EXPORT_LDIF_FILE, INFO_EXPORT_ARG_LDIF_FILE.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_BACKEND_ID, INFO_EXPORT_ARG_BACKEND_ID.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_APPEND_TO_LDIF, INFO_EXPORT_ARG_APPEND_TO_LDIF.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_COMPRESS_LDIF, INFO_EXPORT_ARG_COMPRESS_LDIF.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_ENCRYPT_LDIF, INFO_EXPORT_ARG_ENCRYPT_LDIF.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_SIGN_HASH, INFO_EXPORT_ARG_SIGN_HASH.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_INCLUDE_ATTRIBUTE, INFO_EXPORT_ARG_INCL_ATTR.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_EXCLUDE_ATTRIBUTE, INFO_EXPORT_ARG_EXCL_ATTR.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_INCLUDE_FILTER, INFO_EXPORT_ARG_INCL_FILTER.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_EXCLUDE_FILTER, INFO_EXPORT_ARG_EXCL_FILTER.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_INCLUDE_BRANCH, INFO_EXPORT_ARG_INCL_BRANCH.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_EXCLUDE_BRANCH, INFO_EXPORT_ARG_EXCL_BRANCH.get());
    argDisplayMap.put(ATTR_TASK_EXPORT_WRAP_COLUMN, INFO_EXPORT_ARG_WRAP_COLUMN.get());
  }

  private String  ldifFile;
  private String  backendID;
  private int     wrapColumn;
  private boolean appendToLDIF;
  private boolean compressLDIF;
  private boolean encryptLDIF;
  private boolean signHash;
  private boolean includeOperationalAttributes;
  private ArrayList<String> includeAttributeStrings;
  private ArrayList<String> excludeAttributeStrings;
  private ArrayList<String> includeFilterStrings;
  private ArrayList<String> excludeFilterStrings;
  private ArrayList<String> includeBranchStrings;
  private ArrayList<String> excludeBranchStrings;

  private LDIFExportConfig exportConfig;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_EXPORT_NAME.get();
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
    // client has the LDIF_EXPORT privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (! clientConnection.hasPrivilege(Privilege.LDIF_EXPORT, operation))
      {
        LocalizableMessage message = ERR_TASK_LDIFEXPORT_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }


    Entry taskEntry = getTaskEntry();
    AttributeType typeWrapColumn = getAttributeType(ATTR_TASK_EXPORT_WRAP_COLUMN, true);

    ldifFile = toString(taskEntry, ATTR_TASK_EXPORT_LDIF_FILE);
    File f = new File (ldifFile);
    if (! f.isAbsolute())
    {
      f = new File(DirectoryServer.getInstanceRoot(), ldifFile);
      try
      {
        ldifFile = f.getCanonicalPath();
      }
      catch (Exception ex)
      {
        ldifFile = f.getAbsolutePath();
      }
    }

    backendID = toString(taskEntry, ATTR_TASK_EXPORT_BACKEND_ID);
    appendToLDIF = toBoolean(taskEntry, false, ATTR_TASK_EXPORT_APPEND_TO_LDIF);
    compressLDIF = toBoolean(taskEntry, false, ATTR_TASK_EXPORT_COMPRESS_LDIF);
    encryptLDIF = toBoolean(taskEntry, false, ATTR_TASK_EXPORT_ENCRYPT_LDIF);
    signHash = toBoolean(taskEntry, false, ATTR_TASK_EXPORT_SIGN_HASH);
    includeAttributeStrings = toListOfString(taskEntry, ATTR_TASK_EXPORT_INCLUDE_ATTRIBUTE);
    excludeAttributeStrings = toListOfString(taskEntry, ATTR_TASK_EXPORT_EXCLUDE_ATTRIBUTE);
    includeFilterStrings = toListOfString(taskEntry, ATTR_TASK_EXPORT_INCLUDE_FILTER);
    excludeFilterStrings = toListOfString(taskEntry, ATTR_TASK_EXPORT_EXCLUDE_FILTER);
    includeBranchStrings = toListOfString(taskEntry, ATTR_TASK_EXPORT_INCLUDE_BRANCH);
    excludeBranchStrings = toListOfString(taskEntry, ATTR_TASK_EXPORT_EXCLUDE_BRANCH);

    List<Attribute> attrList = taskEntry.getAttribute(typeWrapColumn);
    wrapColumn = TaskUtils.getSingleValueInteger(attrList, 0);

    includeOperationalAttributes = toBoolean(taskEntry, true, ATTR_TASK_EXPORT_INCLUDE_OPERATIONAL_ATTRIBUTES);
  }

  private boolean toBoolean(Entry entry, boolean defaultValue, String attrName)
  {
    final AttributeType attrType = getAttributeType(attrName, true);
    final List<Attribute> attrs = entry.getAttribute(attrType);
    return TaskUtils.getBoolean(attrs, defaultValue);
  }

  private ArrayList<String> toListOfString(Entry entry, String attrName)
  {
    final AttributeType attrType = getAttributeType(attrName, true);
    final List<Attribute> attrs = entry.getAttribute(attrType);
    return TaskUtils.getMultiValueString(attrs);
  }

  private String toString(Entry entry, String attrName)
  {
    final AttributeType attrType = getAttributeType(attrName, true);
    final List<Attribute> attrs = entry.getAttribute(attrType);
    return TaskUtils.getSingleValueString(attrs);
  }

  /** {@inheritDoc} */
  @Override
  public void interruptTask(TaskState interruptState, LocalizableMessage interruptReason)
  {
    if (TaskState.STOPPED_BY_ADMINISTRATOR.equals(interruptState) &&
            exportConfig != null)
    {
      addLogMessage(Severity.INFORMATION, TaskMessages.INFO_TASK_STOPPED_BY_ADMIN.get(
      interruptReason));
      setTaskInterruptState(interruptState);
      exportConfig.cancel();
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
    // See if there were any user-defined sets of include/exclude attributes or
    // filters.  If so, then process them.
    HashSet<AttributeType> excludeAttributes = toAttributeTypes(excludeAttributeStrings);
    HashSet<AttributeType> includeAttributes = toAttributeTypes(includeAttributeStrings);

    ArrayList<SearchFilter> excludeFilters;
    if (excludeFilterStrings == null)
    {
      excludeFilters = null;
    }
    else
    {
      excludeFilters = new ArrayList<>();
      for (String filterString : excludeFilterStrings)
      {
        try
        {
          excludeFilters.add(SearchFilter.createFilterFromString(filterString));
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER, filterString, de.getMessageObject());
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER, filterString, getExceptionMessage(e));
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
      includeFilters = new ArrayList<>();
      for (String filterString : includeFilterStrings)
      {
        try
        {
          includeFilters.add(SearchFilter.createFilterFromString(filterString));
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER, filterString, de.getMessageObject());
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER, filterString, getExceptionMessage(e));
          return TaskState.STOPPED_BY_ERROR;
        }
      }
    }

    // Get the backend into which the LDIF should be imported.

    Backend<?> backend = DirectoryServer.getBackend(backendID);

    if (backend == null)
    {
      logger.error(ERR_LDIFEXPORT_NO_BACKENDS_FOR_ID, backendID);
      return TaskState.STOPPED_BY_ERROR;
    }
    else if (!backend.supports(BackendOperation.LDIF_EXPORT))
    {
      logger.error(ERR_LDIFEXPORT_CANNOT_EXPORT_BACKEND, backendID);
      return TaskState.STOPPED_BY_ERROR;
    }

    ArrayList<DN> defaultIncludeBranches = new ArrayList<>(backend.getBaseDNs().length);
    for (DN dn : backend.getBaseDNs())
    {
      defaultIncludeBranches.add(dn);
    }

    ArrayList<DN> excludeBranches = new ArrayList<>();
    if (excludeBranchStrings != null)
    {
      for (String s : excludeBranchStrings)
      {
        DN excludeBranch;
        try
        {
          excludeBranch = DN.valueOf(s);
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE, s, de.getMessageObject());
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE, s, getExceptionMessage(e));
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
      includeBranches = new ArrayList<>();
      for (String s : includeBranchStrings)
      {
        DN includeBranch;
        try
        {
          includeBranch = DN.valueOf(s);
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE, s, de.getMessageObject());
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE, s, getExceptionMessage(e));
          return TaskState.STOPPED_BY_ERROR;
        }

        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          logger.error(ERR_LDIFEXPORT_INVALID_INCLUDE_BASE, s, backendID);
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

    exportConfig = new LDIFExportConfig(ldifFile, existingBehavior);
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
    exportConfig.setIncludeOperationalAttributes(includeOperationalAttributes);

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
          logger.error(ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), failureReason);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
        return TaskState.STOPPED_BY_ERROR;
      }


      // From here we must make sure we release the shared backend lock.
      try
      {
        // Launch the export.
        try
        {
          DirectoryServer.notifyExportBeginning(backend, exportConfig);
          addLogMessage(Severity.INFORMATION, INFO_LDIFEXPORT_PATH_TO_LDIF_FILE.get(ldifFile));
          backend.exportLDIF(exportConfig);
          DirectoryServer.notifyExportEnded(backend, exportConfig, true);
        }
        catch (DirectoryException de)
        {
          DirectoryServer.notifyExportEnded(backend, exportConfig, false);
          logger.error(ERR_LDIFEXPORT_ERROR_DURING_EXPORT, de.getMessageObject());
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          DirectoryServer.notifyExportEnded(backend, exportConfig, false);
          logger.error(ERR_LDIFEXPORT_ERROR_DURING_EXPORT, getExceptionMessage(e));
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
            logger.warn(WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
            return TaskState.COMPLETED_WITH_ERRORS;
          }
        }
        catch (Exception e)
        {
          logger.warn(WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
          return TaskState.COMPLETED_WITH_ERRORS;
        }
      }
    }
    finally
    {
      // Clean up after the export by closing the export config.
      exportConfig.close();
    }

    // If the operation was cancelled delete the export file since
    // if will be incomplete.
    if (exportConfig.isCancelled())
    {
      File f = new File(ldifFile);
      if (f.exists())
      {
        f.delete();
      }
    }

    // If we got here the task either completed successfully or
    // was interrupted
    return getFinalTaskState();
  }

  private HashSet<AttributeType> toAttributeTypes(ArrayList<String> attributeStrings)
  {
    if (attributeStrings == null)
    {
      return null;
    }
    HashSet<AttributeType> attributes = new HashSet<>();
    for (String attrName : attributeStrings)
    {
      String lowerName = attrName.toLowerCase();
      AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
      if (attrType == null)
      {
        attrType = DirectoryServer.getDefaultAttributeType(attrName);
      }

      attributes.add(attrType);
    }
    return attributes;
  }
}
