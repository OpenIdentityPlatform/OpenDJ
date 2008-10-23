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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;
import org.opends.messages.Message;
import org.opends.messages.TaskMessages;

import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.getAttributeType;

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
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;

import java.io.File;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to import data from an LDIF file into a backend.
 */
public class ImportTask extends Task
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * Stores mapping between configuration attribute name and its label.
   */
  static private Map<String,Message> argDisplayMap =
          new HashMap<String,Message>();

  static {
    argDisplayMap.put(
            ATTR_IMPORT_LDIF_FILE,
            INFO_IMPORT_ARG_LDIF_FILE.get());

    argDisplayMap.put(
        ATTR_IMPORT_TEMPLATE_FILE,
        INFO_IMPORT_ARG_TEMPLATE_FILE.get());

    argDisplayMap.put(
        ATTR_IMPORT_RANDOM_SEED,
        INFO_IMPORT_ARG_RANDOM_SEED.get());

    argDisplayMap.put(
            ATTR_IMPORT_APPEND,
            INFO_IMPORT_ARG_APPEND.get());

    argDisplayMap.put(
            ATTR_IMPORT_REPLACE_EXISTING,
            INFO_IMPORT_ARG_REPLACE_EXISTING.get());

    argDisplayMap.put(
            ATTR_IMPORT_BACKEND_ID,
            INFO_IMPORT_ARG_BACKEND_ID.get());

    argDisplayMap.put(
            ATTR_IMPORT_INCLUDE_BRANCH,
            INFO_IMPORT_ARG_INCL_BRANCH.get());

    argDisplayMap.put(
            ATTR_IMPORT_EXCLUDE_BRANCH,
            INFO_IMPORT_ARG_EXCL_BRANCH.get());

    argDisplayMap.put(
            ATTR_IMPORT_INCLUDE_ATTRIBUTE,
            INFO_IMPORT_ARG_INCL_ATTR.get());

    argDisplayMap.put(
            ATTR_IMPORT_EXCLUDE_ATTRIBUTE,
            INFO_IMPORT_ARG_EXCL_ATTR.get());

    argDisplayMap.put(
            ATTR_IMPORT_INCLUDE_FILTER,
            INFO_IMPORT_ARG_INCL_FILTER.get());

    argDisplayMap.put(
            ATTR_IMPORT_EXCLUDE_FILTER,
            INFO_IMPORT_ARG_EXCL_FILTER.get());

    argDisplayMap.put(
            ATTR_IMPORT_REJECT_FILE,
            INFO_IMPORT_ARG_REJECT_FILE.get());

    argDisplayMap.put(
            ATTR_IMPORT_SKIP_FILE,
            INFO_IMPORT_ARG_SKIP_FILE.get());

    argDisplayMap.put(
            ATTR_IMPORT_OVERWRITE,
            INFO_IMPORT_ARG_OVERWRITE.get());

    argDisplayMap.put(
            ATTR_IMPORT_SKIP_SCHEMA_VALIDATION,
            INFO_IMPORT_ARG_SKIP_SCHEMA_VALIDATION.get());

    argDisplayMap.put(
            ATTR_IMPORT_IS_COMPRESSED,
            INFO_IMPORT_ARG_IS_COMPRESSED.get());

    argDisplayMap.put(
            ATTR_IMPORT_IS_ENCRYPTED,
            INFO_IMPORT_ARG_IS_ENCRYPTED.get());

    argDisplayMap.put(
            ATTR_IMPORT_CLEAR_BACKEND,
            INFO_IMPORT_ARG_CLEAR_BACKEND.get());
  }


  boolean append                  = false;
  boolean isCompressed            = false;
  boolean isEncrypted             = false;
  boolean overwrite               = false;
  boolean replaceExisting         = false;
  boolean skipSchemaValidation    = false;
  boolean clearBackend            = false;
  String  backendID               = null;
  String  rejectFile              = null;
  String  skipFile                = null;
  ArrayList<String>  excludeAttributeStrings = null;
  ArrayList<String>  excludeBranchStrings    = null;
  ArrayList<String>  excludeFilterStrings    = null;
  ArrayList<String>  includeAttributeStrings = null;
  ArrayList<String>  includeBranchStrings    = null;
  ArrayList<String>  includeFilterStrings    = null;
  ArrayList<String>  ldifFiles               = null;
  String templateFile = null;
  int randomSeed = 0;

  private LDIFImportConfig importConfig;

  /**
   * {@inheritDoc}
   */
  public Message getDisplayName() {
    return INFO_TASK_IMPORT_NAME.get();
  }

  /**
   * {@inheritDoc}
   */
  public Message getAttributeDisplayName(String name) {
    return argDisplayMap.get(name);
  }

  /**
   * {@inheritDoc}
   */
  @Override public void initializeTask() throws DirectoryException
  {
    // If the client connection is available, then make sure the associated
    // client has the LDIF_IMPORT privilege.
    Operation operation = getOperation();
    if (operation != null)
    {
      ClientConnection clientConnection = operation.getClientConnection();
      if (! clientConnection.hasPrivilege(Privilege.LDIF_IMPORT, operation))
      {
        Message message = ERR_TASK_LDIFIMPORT_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                                     message);
      }
    }


    Entry taskEntry = getTaskEntry();

    AttributeType typeLdifFile;
    AttributeType typeTemplateFile;
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
    AttributeType typeSkipFile;
    AttributeType typeOverwrite;
    AttributeType typeSkipSchemaValidation;
    AttributeType typeIsCompressed;
    AttributeType typeIsEncrypted;
    AttributeType typeClearBackend;
    AttributeType typeRandomSeed;

    typeLdifFile =
         getAttributeType(ATTR_IMPORT_LDIF_FILE, true);
    typeTemplateFile =
         getAttributeType(ATTR_IMPORT_TEMPLATE_FILE, true);
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
    typeSkipFile =
      getAttributeType(ATTR_IMPORT_SKIP_FILE, true);
    typeOverwrite =
         getAttributeType(ATTR_IMPORT_OVERWRITE, true);
    typeSkipSchemaValidation =
         getAttributeType(ATTR_IMPORT_SKIP_SCHEMA_VALIDATION, true);
    typeIsCompressed =
         getAttributeType(ATTR_IMPORT_IS_COMPRESSED, true);
    typeIsEncrypted =
         getAttributeType(ATTR_IMPORT_IS_ENCRYPTED, true);
    typeClearBackend =
         getAttributeType(ATTR_IMPORT_CLEAR_BACKEND, true);
    typeRandomSeed =
         getAttributeType(ATTR_IMPORT_RANDOM_SEED, true);

    List<Attribute> attrList;

    attrList = taskEntry.getAttribute(typeLdifFile);
    ldifFiles = TaskUtils.getMultiValueString(attrList);

    attrList = taskEntry.getAttribute(typeTemplateFile);
    templateFile = TaskUtils.getSingleValueString(attrList);

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

    attrList = taskEntry.getAttribute(typeSkipFile);
    skipFile = TaskUtils.getSingleValueString(attrList);

    attrList = taskEntry.getAttribute(typeOverwrite);
    overwrite = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeSkipSchemaValidation);
    skipSchemaValidation = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeIsCompressed);
    isCompressed = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeIsEncrypted);
    isEncrypted = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeClearBackend);
    clearBackend = TaskUtils.getBoolean(attrList, false);

    attrList = taskEntry.getAttribute(typeRandomSeed);
    randomSeed = TaskUtils.getSingleValueInteger(attrList, 0);

    // Make sure that either the "includeBranchStrings" argument or the
    // "backendID" argument was provided.
    if(includeBranchStrings.isEmpty() && backendID == null)
    {
      Message message = ERR_LDIFIMPORT_MISSING_BACKEND_ARGUMENT.get(
          typeIncludeBranch.getNameOrOID(), typeBackendID.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    Backend backend = null;
    ArrayList<DN> defaultIncludeBranches;
    ArrayList<DN> excludeBranches =
        new ArrayList<DN>(excludeBranchStrings.size());
    ArrayList<DN> includeBranches =
        new ArrayList<DN>(includeBranchStrings.size());

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
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE.get(
            s, getExceptionMessage(e));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      if(! includeBranches.contains(includeBranch))
      {
        includeBranches.add(includeBranch);
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
        Message message = ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
            s, de.getMessageObject());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
            s, getExceptionMessage(e));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      if (! excludeBranches.contains(excludeBranch))
      {
        excludeBranches.add(excludeBranch);
      }
    }

    for (String filterString : excludeFilterStrings)
    {
      try
      {
        SearchFilter.createFilterFromString(filterString);
      }
      catch (DirectoryException de)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER.get(
            filterString, de.getMessageObject());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }

    for (String filterString : includeFilterStrings)
    {
      try
      {
        SearchFilter.createFilterFromString(filterString);
      }
      catch (DirectoryException de)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
            filterString, de.getMessageObject());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }

    if(backendID != null)
    {
      backend = DirectoryServer.getBackend(backendID);
      if (backend == null)
      {
        Message message = ERR_LDIFIMPORT_NO_BACKENDS_FOR_ID.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (! backend.supportsLDIFImport())
      {
        Message message = ERR_LDIFIMPORT_CANNOT_IMPORT.get(backendID);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      // Make sure that if the "backendID" argument was provided, no include
      // base was included, and the "append" ption was not provided, the
      // "clearBackend" argument was also provided if there are more then one
      // baseDNs for the backend being imported.
      else if(!append && includeBranchStrings.isEmpty() &&
          backend.getBaseDNs().length > 1 && !clearBackend)
      {
        StringBuilder builder = new StringBuilder();
        for(DN dn : backend.getBaseDNs())
        {
          builder.append(dn.toNormalizedString());
          builder.append(" ");
        }
        Message message = ERR_LDIFIMPORT_MISSING_CLEAR_BACKEND.get(
            builder.toString(), typeClearBackend.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    else
    {
      // Find the backend that includes all the branches.
      for(DN includeBranch : includeBranches)
      {
        Backend locatedBackend = DirectoryServer.getBackend(includeBranch);
        if(locatedBackend != null)
        {
          if(backend == null)
          {
            backend = locatedBackend;
          }
          else if(backend != locatedBackend)
          {
            // The include branches span across multiple backends.
            Message message = ERR_LDIFIMPORT_INVALID_INCLUDE_BASE.get(
                includeBranch.toNormalizedString(), backend.getBackendID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }
      }
    }

    // Make sure the selected backend will handle all the include branches
    defaultIncludeBranches = new ArrayList<DN>(backend.getBaseDNs().length);
    for (DN dn : backend.getBaseDNs())
    {
      defaultIncludeBranches.add(dn);
    }

    for(DN includeBranch : includeBranches)
    {
      if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                 excludeBranches))
      {
        Message message = ERR_LDIFIMPORT_INVALID_INCLUDE_BASE.get(
            includeBranch.toNormalizedString(), backend.getBackendID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
  }


  /**
   * {@inheritDoc}
   */
  public void interruptTask(TaskState interruptState, Message interruptReason)
  {
    if (TaskState.STOPPED_BY_ADMINISTRATOR.equals(interruptState) &&
            importConfig != null)
    {
      addLogMessage(TaskMessages.INFO_TASK_STOPPED_BY_ADMIN.get(
              interruptReason));
      setTaskInterruptState(interruptState);
      importConfig.cancel();
    }
  }


  /**
   * {@inheritDoc}
   */
  public boolean isInterruptable()
  {
    return true;
  }


  /**
   * {@inheritDoc}
   */
  protected TaskState runTask()
  {
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
        Message message = ERR_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER.get(
            filterString, de.getMessageObject());
        logError(message);
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
        Message message = ERR_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
            filterString, de.getMessageObject());
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
    }


    // Get the backend into which the LDIF should be imported.
    Backend       backend = null;
    ArrayList<DN> defaultIncludeBranches;
    ArrayList<DN> excludeBranches =
        new ArrayList<DN>(excludeBranchStrings.size());
    ArrayList<DN> includeBranches =
        new ArrayList<DN>(includeBranchStrings.size());

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

      if(! includeBranches.contains(includeBranch))
      {
        includeBranches.add(includeBranch);
      }
    }

    if(backendID != null)
    {
      backend = DirectoryServer.getBackend(backendID);

      if (backend == null)
      {
        Message message = ERR_LDIFIMPORT_NO_BACKENDS_FOR_ID.get();
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
      else if (! backend.supportsLDIFImport())
      {
        Message message = ERR_LDIFIMPORT_CANNOT_IMPORT.get(backendID);
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
      // Make sure that if the "backendID" argument was provided, no include
      // base was included, and the "append" ption was not provided, the
      // "clearBackend" argument was also provided if there are more then one
      // baseDNs for the backend being imported.
      else if(!append && includeBranches.isEmpty() &&
          backend.getBaseDNs().length > 1 && !clearBackend)
      {
        StringBuilder builder = new StringBuilder();
        builder.append(backend.getBaseDNs()[0].toNormalizedString());
        for(int i = 1; i < backend.getBaseDNs().length; i++)
        {
          builder.append(" / ");
          builder.append(backend.getBaseDNs()[i].toNormalizedString());
        }
        Message message = ERR_LDIFIMPORT_MISSING_CLEAR_BACKEND.get(
            builder.toString(), ATTR_IMPORT_CLEAR_BACKEND);
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    else
    {
      // Find the backend that includes all the branches.
      for(DN includeBranch : includeBranches)
      {
        Backend locatedBackend = DirectoryServer.getBackend(includeBranch);
        if(locatedBackend != null)
        {
          if(backend == null)
          {
            backend = locatedBackend;
          }
          else if(backend != locatedBackend)
          {
            // The include branches span across multiple backends.
            Message message = ERR_LDIFIMPORT_INVALID_INCLUDE_BASE.get(
                includeBranch.toNormalizedString(), backend.getBackendID());
            logError(message);
            return TaskState.STOPPED_BY_ERROR;
          }
        }
      }
    }

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
        Message message = ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
            s, de.getMessageObject());
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
            s, getExceptionMessage(e));
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }

      if (! excludeBranches.contains(excludeBranch))
      {
        excludeBranches.add(excludeBranch);
      }
    }

    if (includeBranchStrings.isEmpty())
    {
      includeBranches = defaultIncludeBranches;
    }
    else
    {
      // Make sure the selected backend will handle all the include branches
      for(DN includeBranch : includeBranches)
      {
        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          Message message = ERR_LDIFIMPORT_INVALID_INCLUDE_BASE.get(
              includeBranch.toNormalizedString(), backend.getBackendID());
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
    }

    // Create the LDIF import configuration to use when reading the LDIF.
    if (templateFile != null)
    {
      Random random;
      try
      {
        random = new Random(randomSeed);
      }
      catch (Exception e)
      {
        random = new Random();
      }

      String resourcePath = DirectoryServer.getServerRoot() + File.separator +
                            PATH_MAKELDIF_RESOURCE_DIR;
      TemplateFile tf = new TemplateFile(resourcePath, random);

      ArrayList<Message> warnings = new ArrayList<Message>();
      try
      {
        tf.parse(templateFile, warnings);
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_PARSE_TEMPLATE_FILE.get(
            templateFile, e.getMessage());
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }

      importConfig = new LDIFImportConfig(tf);
    }
    else
    {
      ArrayList<String> fileList = new ArrayList<String>(ldifFiles);
      importConfig = new LDIFImportConfig(fileList);
    }
    importConfig.setAppendToExistingData(append);
    importConfig.setReplaceExistingEntries(replaceExisting);
    importConfig.setCompressed(isCompressed);
    importConfig.setEncrypted(isEncrypted);
    importConfig.setClearBackend(clearBackend);
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
        if (overwrite)
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
        Message message = ERR_LDIFIMPORT_CANNOT_OPEN_REJECTS_FILE.get(
            rejectFile, getExceptionMessage(e));
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    if (skipFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior;
        if (overwrite)
        {
          existingBehavior = ExistingFileBehavior.OVERWRITE;
        }
        else
        {
          existingBehavior = ExistingFileBehavior.APPEND;
        }

        importConfig.writeRejectedEntries(skipFile, existingBehavior);
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_OPEN_SKIP_FILE.get(
            skipFile, getExceptionMessage(e));
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    // Get the set of base DNs for the backend as an array.
    DN[] baseDNs = new DN[defaultIncludeBranches.size()];
    defaultIncludeBranches.toArray(baseDNs);

    // Notify the task listeners that an import is going to start
    // this must be done before disabling the backend to allow
    // listeners to get access to the backend configuration
    // and to take appropriate actions.
    DirectoryServer.notifyImportBeginning(backend, importConfig);

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
      // Acquire an exclusive lock for the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
        {
          Message message = ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          logError(message);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), getExceptionMessage(e));
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }


      // Launch the import.
      try
      {
        backend.importLDIF(importConfig);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        DirectoryServer.notifyImportEnded(backend, importConfig, false);
        Message message =
            ERR_LDIFIMPORT_ERROR_DURING_IMPORT.get(de.getMessageObject());
        logError(message);
        return TaskState.STOPPED_BY_ERROR;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        DirectoryServer.notifyImportEnded(backend, importConfig, false);
        Message message =
            ERR_LDIFIMPORT_ERROR_DURING_IMPORT.get(getExceptionMessage(e));
        logError(message);
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
            Message message = WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND.get(
                backend.getBackendID(), String.valueOf(failureReason));
            logError(message);
            return TaskState.COMPLETED_WITH_ERRORS;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), getExceptionMessage(e));
          logError(message);
          return TaskState.COMPLETED_WITH_ERRORS;
        }

      }
    }
    finally
    {
      // Enable the backend.
      try
      {
        TaskUtils.enableBackend(backend.getBackendID());
        // It is necessary to retrieve the backend structure again
        // because disabling and enabling it again may have resulted
        // in a new backend being registered to the server.
        backend = DirectoryServer.getBackend(backend.getBackendID());
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
      DirectoryServer.notifyImportEnded(backend, importConfig, true);
    }


    // Clean up after the import by closing the import config.
    importConfig.close();
    return getFinalTaskState();
  }
}
