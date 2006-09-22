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

import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.getAttributeType;

import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.SearchFilter;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to import data from an LDIF file into a backend.
 */
public class ImportTask extends Task
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tasks.ImportTask";



  boolean append                  = false;
  boolean isCompressed            = false;
  boolean isEncrypted             = false;
  boolean overwriteRejects        = false;
  boolean replaceExisting         = false;
  boolean skipSchemaValidation    = false;
  String  backendID               = null;
  String  rejectFile              = null;
  ArrayList<String>  excludeAttributeStrings = null;
  ArrayList<String>  excludeBranchStrings    = null;
  ArrayList<String>  excludeFilterStrings    = null;
  ArrayList<String>  includeAttributeStrings = null;
  ArrayList<String>  includeBranchStrings    = null;
  ArrayList<String>  includeFilterStrings    = null;
  ArrayList<String>  ldifFiles               = null;



  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "initializeTask");


    // FIXME -- Do we need any special authorization here?


    Entry taskEntry = getTaskEntry();

    AttributeType typeLdifFile;
    AttributeType typeAppend;
    AttributeType typeReplaceExisting;
    AttributeType typeBackendID;
    AttributeType typeIncludeBranch;
    AttributeType typeExcludeBranch;
    AttributeType typeIncludeAttribute;
    AttributeType typeExcludeAttribute;
    AttributeType typeIncludeFilter;
    AttributeType typeExcludeFilter;
    AttributeType typeRejectFile;
    AttributeType typeOverwriteRejects;
    AttributeType typeSkipSchemaValidation;
    AttributeType typeIsCompressed;
    AttributeType typeIsEncrypted;

    typeLdifFile =
         getAttributeType(ATTR_IMPORT_LDIF_FILE, true);
    typeAppend =
         getAttributeType(ATTR_IMPORT_APPEND, true);
    typeReplaceExisting =
         getAttributeType(ATTR_IMPORT_REPLACE_EXISTING, true);
    typeBackendID =
         getAttributeType(ATTR_IMPORT_BACKEND_ID, true);
    typeIncludeBranch =
         getAttributeType(ATTR_IMPORT_INCLUDE_BRANCH, true);
    typeExcludeBranch =
         getAttributeType(ATTR_IMPORT_EXCLUDE_BRANCH, true);
    typeIncludeAttribute =
         getAttributeType(ATTR_IMPORT_INCLUDE_ATTRIBUTE, true);
    typeExcludeAttribute =
         getAttributeType(ATTR_IMPORT_EXCLUDE_ATTRIBUTE, true);
    typeIncludeFilter =
         getAttributeType(ATTR_IMPORT_INCLUDE_FILTER, true);
    typeExcludeFilter =
         getAttributeType(ATTR_IMPORT_EXCLUDE_FILTER, true);
    typeRejectFile =
         getAttributeType(ATTR_IMPORT_REJECT_FILE, true);
    typeOverwriteRejects =
         getAttributeType(ATTR_IMPORT_OVERWRITE_REJECTS, true);
    typeSkipSchemaValidation =
         getAttributeType(ATTR_IMPORT_SKIP_SCHEMA_VALIDATION, true);
    typeIsCompressed =
         getAttributeType(ATTR_IMPORT_IS_COMPRESSED, true);
    typeIsEncrypted =
         getAttributeType(ATTR_IMPORT_IS_ENCRYPTED, true);

    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeLdifFile);
    ldifFiles = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeAppend);
    append = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeReplaceExisting);
    replaceExisting = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeBackendID);
    backendID = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeIncludeBranch);
    includeBranchStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeExcludeBranch);
    excludeBranchStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeIncludeAttribute);
    includeAttributeStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeExcludeAttribute);
    excludeAttributeStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeIncludeFilter);
    includeFilterStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeExcludeFilter);
    excludeFilterStrings = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeRejectFile);
    rejectFile = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeOverwriteRejects);
    overwriteRejects = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeSkipSchemaValidation);
    skipSchemaValidation = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeIsCompressed);
    isCompressed = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeIsEncrypted);
    isEncrypted = TaskUtils.getBoolean(attrList, false);

  }



  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
    assert debugEnter(CLASS_NAME, "runTask");

    // See if there were any user-defined sets of include/exclude attributes or
    // filters.  If so, then process them.
    HashSet<AttributeType> excludeAttributes =
         new HashSet<AttributeType>(excludeAttributeStrings.size());
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

    HashSet<AttributeType> includeAttributes =
         new HashSet<AttributeType>(includeAttributeStrings.size());
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

    ArrayList<SearchFilter> excludeFilters =
         new ArrayList<SearchFilter>(excludeFilterStrings.size());
    for (String filterString : excludeFilterStrings)
    {
      try
      {
        excludeFilters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (DirectoryException de)
      {
        int    msgID   = MSGID_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER;
        String message = getMessage(msgID, filterString,
                                    de.getErrorMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    ArrayList<SearchFilter> includeFilters =
         new ArrayList<SearchFilter>(includeFilterStrings.size());
    for (String filterString : includeFilterStrings)
    {
      try
      {
        includeFilters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (DirectoryException de)
      {
        int    msgID   = MSGID_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER;
        String message = getMessage(msgID, filterString,
                                    de.getErrorMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
    }


    // Get the backend into which the LDIF should be imported.
    Backend       backend;
    ConfigEntry   configEntry;
    ArrayList<DN> defaultIncludeBranches;
    ArrayList<DN> excludeBranches = new ArrayList<DN>();

    backend = DirectoryServer.getBackend(backendID);

    if (backend == null)
    {
      int    msgID   = MSGID_LDIFIMPORT_NO_BACKENDS_FOR_ID;
      String message = getMessage(msgID, backendID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return TaskState.STOPPED_BY_ERROR;
    }
    else if (! backend.supportsLDIFImport())
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_IMPORT;
      String message = getMessage(msgID, backendID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return TaskState.STOPPED_BY_ERROR;
    }

    // Get the config entry for this backend.
    configEntry = TaskUtils.getConfigEntry(backend);

    // Find backends with subordinate base DNs that should be excluded from the
    // import.

    defaultIncludeBranches = new ArrayList<DN>(backend.getBaseDNs().length);
    for (DN dn : backend.getBaseDNs())
    {
      defaultIncludeBranches.add(dn);
    }

    if (backend.getSubordinateBackends() != null)
    {
      for (Backend subBackend : backend.getSubordinateBackends())
      {
        for (DN baseDN : subBackend.getBaseDNs())
        {
          for (DN importBase : defaultIncludeBranches)
          {
            if (baseDN.isDescendantOf(importBase) &&
                 (! baseDN.equals(importBase)))
            {
              if (! excludeBranches.contains(baseDN))
              {
                excludeBranches.add(baseDN);
              }

              break;
            }
          }
        }
      }
    }

    for (String s : excludeBranchStrings)
    {
      DN excludeBranch;
      try
      {
        excludeBranch = DN.decode(s);
      }
      catch (DirectoryException de)
      {
        int    msgID   = MSGID_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE;
        String message = getMessage(msgID, s, de.getErrorMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE;
        String message = getMessage(msgID, s, stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }

      if (! excludeBranches.contains(excludeBranch))
      {
        excludeBranches.add(excludeBranch);
      }
    }


    ArrayList<DN> includeBranches;
    if (includeBranchStrings.isEmpty())
    {
      includeBranches = defaultIncludeBranches;
    }
    else
    {
      includeBranches = new ArrayList<DN>(includeBranchStrings.size());
      for (String s : includeBranchStrings)
      {
        DN includeBranch;
        try
        {
          includeBranch = DN.decode(s);
        }
        catch (DirectoryException de)
        {
          int    msgID   = MSGID_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE;
          String message = getMessage(msgID, s, de.getErrorMessage());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE;
          String message = getMessage(msgID, s,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }

        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          int    msgID   = MSGID_LDIFIMPORT_INVALID_INCLUDE_BASE;
          String message = getMessage(msgID, s, backendID);
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }

        includeBranches.add(includeBranch);
      }
    }


    // Create the LDIF import configuration to use when reading the LDIF.
    ArrayList<String> fileList = new ArrayList<String>(ldifFiles);
    LDIFImportConfig importConfig = new LDIFImportConfig(fileList);
    importConfig.setAppendToExistingData(append);
    importConfig.setReplaceExistingEntries(replaceExisting);
    importConfig.setCompressed(isCompressed);
    importConfig.setEncrypted(isEncrypted);
    importConfig.setExcludeAttributes(excludeAttributes);
    importConfig.setExcludeBranches(excludeBranches);
    importConfig.setExcludeFilters(excludeFilters);
    importConfig.setIncludeAttributes(includeAttributes);
    importConfig.setIncludeBranches(includeBranches);
    importConfig.setIncludeFilters(includeFilters);
    importConfig.setValidateSchema(!skipSchemaValidation);

    // FIXME -- Should this be conditional?
    importConfig.setInvokeImportPlugins(true);

    if (rejectFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior;
        if (overwriteRejects)
        {
          existingBehavior = ExistingFileBehavior.OVERWRITE;
        }
        else
        {
          existingBehavior = ExistingFileBehavior.APPEND;
        }

        importConfig.writeRejectedEntries(rejectFile, existingBehavior);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFIMPORT_CANNOT_OPEN_REJECTS_FILE;
        String message = getMessage(msgID, rejectFile,
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
    }


    // Get the set of base DNs for the backend as an array.
    DN[] baseDNs = new DN[defaultIncludeBranches.size()];
    defaultIncludeBranches.toArray(baseDNs);


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


    try
    {
      // Acquire an exclusive lock for the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_LDIFIMPORT_CANNOT_LOCK_BACKEND;
          String message = getMessage(msgID, backend.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "runTask", e);

        int    msgID   = MSGID_LDIFIMPORT_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }


      // Launch the import.
      try
      {
        backend.importLDIF(configEntry, baseDNs, importConfig);
      }
      catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "runTask", de);

        int    msgID   = MSGID_LDIFIMPORT_ERROR_DURING_IMPORT;
        String message = getMessage(msgID, de.getErrorMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "runTask", e);

        int    msgID   = MSGID_LDIFIMPORT_ERROR_DURING_IMPORT;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return TaskState.STOPPED_BY_ERROR;
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
            int    msgID   = MSGID_LDIFIMPORT_CANNOT_UNLOCK_BACKEND;
            String message = getMessage(msgID, backend.getBackendID(),
                                        String.valueOf(failureReason));
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);
            return TaskState.COMPLETED_WITH_ERRORS;
          }
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "runTask", e);

          int    msgID   = MSGID_LDIFIMPORT_CANNOT_UNLOCK_BACKEND;
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


    // Clean up after the import by closing the import config.
    importConfig.close();
    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
