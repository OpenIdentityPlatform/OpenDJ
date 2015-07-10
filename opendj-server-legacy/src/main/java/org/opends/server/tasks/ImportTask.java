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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.tasks;

import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchFilter;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to import data from an LDIF file into a backend.
 */
public class ImportTask extends Task
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Stores mapping between configuration attribute name and its label. */
  private static final Map<String, LocalizableMessage> argDisplayMap = new HashMap<>();
  static
  {
    argDisplayMap.put(ATTR_IMPORT_LDIF_FILE, INFO_IMPORT_ARG_LDIF_FILE.get());
    argDisplayMap.put(ATTR_IMPORT_TEMPLATE_FILE, INFO_IMPORT_ARG_TEMPLATE_FILE.get());
    argDisplayMap.put(ATTR_IMPORT_RANDOM_SEED, INFO_IMPORT_ARG_RANDOM_SEED.get());
    argDisplayMap.put(ATTR_IMPORT_APPEND, INFO_IMPORT_ARG_APPEND.get());
    argDisplayMap.put(ATTR_IMPORT_REPLACE_EXISTING, INFO_IMPORT_ARG_REPLACE_EXISTING.get());
    argDisplayMap.put(ATTR_IMPORT_BACKEND_ID, INFO_IMPORT_ARG_BACKEND_ID.get());
    argDisplayMap.put(ATTR_IMPORT_INCLUDE_BRANCH, INFO_IMPORT_ARG_INCL_BRANCH.get());
    argDisplayMap.put(ATTR_IMPORT_EXCLUDE_BRANCH, INFO_IMPORT_ARG_EXCL_BRANCH.get());
    argDisplayMap.put(ATTR_IMPORT_INCLUDE_ATTRIBUTE, INFO_IMPORT_ARG_INCL_ATTR.get());
    argDisplayMap.put(ATTR_IMPORT_EXCLUDE_ATTRIBUTE, INFO_IMPORT_ARG_EXCL_ATTR.get());
    argDisplayMap.put(ATTR_IMPORT_INCLUDE_FILTER, INFO_IMPORT_ARG_INCL_FILTER.get());
    argDisplayMap.put(ATTR_IMPORT_EXCLUDE_FILTER, INFO_IMPORT_ARG_EXCL_FILTER.get());
    argDisplayMap.put(ATTR_IMPORT_REJECT_FILE, INFO_IMPORT_ARG_REJECT_FILE.get());
    argDisplayMap.put(ATTR_IMPORT_SKIP_FILE, INFO_IMPORT_ARG_SKIP_FILE.get());
    argDisplayMap.put(ATTR_IMPORT_OVERWRITE, INFO_IMPORT_ARG_OVERWRITE.get());
    argDisplayMap.put(ATTR_IMPORT_SKIP_SCHEMA_VALIDATION, INFO_IMPORT_ARG_SKIP_SCHEMA_VALIDATION.get());
    argDisplayMap.put(ATTR_IMPORT_IS_COMPRESSED, INFO_IMPORT_ARG_IS_COMPRESSED.get());
    argDisplayMap.put(ATTR_IMPORT_IS_ENCRYPTED, INFO_IMPORT_ARG_IS_ENCRYPTED.get());
    argDisplayMap.put(ATTR_IMPORT_CLEAR_BACKEND, INFO_IMPORT_ARG_CLEAR_BACKEND.get());
  }


  private boolean append;
  private boolean isCompressed;
  private boolean isEncrypted;
  private boolean overwrite;
  private boolean replaceExisting;
  private boolean skipSchemaValidation;
  private boolean clearBackend;
  private boolean skipDNValidation;
  private String tmpDirectory;
  private int threadCount;
  private String backendID;
  private String rejectFile;
  private String skipFile;
  private ArrayList<String> excludeAttributeStrings;
  private ArrayList<String> excludeBranchStrings;
  private ArrayList<String> excludeFilterStrings;
  private ArrayList<String> includeAttributeStrings;
  private ArrayList<String> includeBranchStrings;
  private ArrayList<String> includeFilterStrings;
  private ArrayList<String> ldifFiles;
  private String templateFile;
  private int randomSeed;
  private LDIFImportConfig importConfig;

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_IMPORT_NAME.get();
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getAttributeDisplayName(String name) {
    return argDisplayMap.get(name);
  }

  /** {@inheritDoc} */
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
        LocalizableMessage message = ERR_TASK_LDIFIMPORT_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
      }
    }


    Entry taskEntry = getTaskEntry();

    AttributeType typeLdifFile = getAttributeType(ATTR_IMPORT_LDIF_FILE, true);
    AttributeType typeTemplateFile = getAttributeType(ATTR_IMPORT_TEMPLATE_FILE, true);
    AttributeType typeAppend = getAttributeType(ATTR_IMPORT_APPEND, true);
    AttributeType typeReplaceExisting = getAttributeType(ATTR_IMPORT_REPLACE_EXISTING, true);
    AttributeType typeBackendID = getAttributeType(ATTR_IMPORT_BACKEND_ID, true);
    AttributeType typeIncludeBranch = getAttributeType(ATTR_IMPORT_INCLUDE_BRANCH, true);
    AttributeType typeExcludeBranch = getAttributeType(ATTR_IMPORT_EXCLUDE_BRANCH, true);
    AttributeType typeIncludeAttribute = getAttributeType(ATTR_IMPORT_INCLUDE_ATTRIBUTE, true);
    AttributeType typeExcludeAttribute = getAttributeType(ATTR_IMPORT_EXCLUDE_ATTRIBUTE, true);
    AttributeType typeIncludeFilter = getAttributeType(ATTR_IMPORT_INCLUDE_FILTER, true);
    AttributeType typeExcludeFilter = getAttributeType(ATTR_IMPORT_EXCLUDE_FILTER, true);
    AttributeType typeRejectFile = getAttributeType(ATTR_IMPORT_REJECT_FILE, true);
    AttributeType typeSkipFile = getAttributeType(ATTR_IMPORT_SKIP_FILE, true);
    AttributeType typeOverwrite = getAttributeType(ATTR_IMPORT_OVERWRITE, true);
    AttributeType typeSkipSchemaValidation = getAttributeType(ATTR_IMPORT_SKIP_SCHEMA_VALIDATION, true);
    AttributeType typeIsCompressed = getAttributeType(ATTR_IMPORT_IS_COMPRESSED, true);
    AttributeType typeIsEncrypted = getAttributeType(ATTR_IMPORT_IS_ENCRYPTED, true);
    AttributeType typeClearBackend = getAttributeType(ATTR_IMPORT_CLEAR_BACKEND, true);
    AttributeType typeRandomSeed = getAttributeType(ATTR_IMPORT_RANDOM_SEED, true);
    AttributeType typeThreadCount = getAttributeType(ATTR_IMPORT_THREAD_COUNT, true);
    AttributeType typeTmpDirectory = getAttributeType(ATTR_IMPORT_TMP_DIRECTORY, true);
    AttributeType typeDNCheckPhase2 = getAttributeType(ATTR_IMPORT_SKIP_DN_VALIDATION, true);

    ArrayList<String> ldifFilestmp = asListOfStrings(taskEntry, typeLdifFile);
    ldifFiles = new ArrayList<>(ldifFilestmp.size());
    for (String s : ldifFilestmp)
    {
      File f = new File (s);
      if (!f.isAbsolute())
      {
        f = new File(DirectoryServer.getInstanceRoot(), s);
        try
        {
          s = f.getCanonicalPath();
        }
        catch (Exception ex)
        {
          s = f.getAbsolutePath();
        }
      }
      ldifFiles.add(s);
    }

    templateFile = asString(taskEntry, typeTemplateFile);
    if (templateFile != null)
    {
      File f = new File(templateFile);
      if (!f.isAbsolute())
      {
        templateFile = new File(DirectoryServer.getInstanceRoot(), templateFile)
            .getAbsolutePath();
      }
    }

    append = asBoolean(taskEntry, typeAppend);
    skipDNValidation = asBoolean(taskEntry, typeDNCheckPhase2);
    tmpDirectory = asString(taskEntry, typeTmpDirectory);
    replaceExisting = asBoolean(taskEntry, typeReplaceExisting);
    backendID = asString(taskEntry, typeBackendID);
    includeBranchStrings = asListOfStrings(taskEntry, typeIncludeBranch);
    excludeBranchStrings = asListOfStrings(taskEntry, typeExcludeBranch);
    includeAttributeStrings = asListOfStrings(taskEntry, typeIncludeAttribute);
    excludeAttributeStrings = asListOfStrings(taskEntry, typeExcludeAttribute);
    includeFilterStrings = asListOfStrings(taskEntry, typeIncludeFilter);
    excludeFilterStrings = asListOfStrings(taskEntry, typeExcludeFilter);
    rejectFile = asString(taskEntry, typeRejectFile);
    skipFile = asString(taskEntry, typeSkipFile);
    overwrite = asBoolean(taskEntry, typeOverwrite);
    skipSchemaValidation = asBoolean(taskEntry, typeSkipSchemaValidation);
    isCompressed = asBoolean(taskEntry, typeIsCompressed);
    isEncrypted = asBoolean(taskEntry, typeIsEncrypted);
    clearBackend = asBoolean(taskEntry, typeClearBackend);
    randomSeed = asInt(taskEntry, typeRandomSeed);
    threadCount = asInt(taskEntry, typeThreadCount);

    // Make sure that either the "includeBranchStrings" argument or the
    // "backendID" argument was provided.
    if(includeBranchStrings.isEmpty() && backendID == null)
    {
      LocalizableMessage message = ERR_LDIFIMPORT_MISSING_BACKEND_ARGUMENT.get(
          typeIncludeBranch.getNameOrOID(), typeBackendID.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    Backend<?> backend = null;
    ArrayList<DN> defaultIncludeBranches;
    HashSet<DN> excludeBranches = new HashSet<>(excludeBranchStrings.size());
    HashSet<DN> includeBranches = new HashSet<>(includeBranchStrings.size());

    for (String s : includeBranchStrings)
    {
      DN includeBranch;
      try
      {
        includeBranch = DN.valueOf(s);
      }
      catch (DirectoryException de)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE.get(
            s, de.getMessageObject());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE.get(
            s, getExceptionMessage(e));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      includeBranches.add(includeBranch);
    }
    for (String s : excludeBranchStrings)
    {
      DN excludeBranch;
      try
      {
        excludeBranch = DN.valueOf(s);
      }
      catch (DirectoryException de)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
            s, de.getMessageObject());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
            s, getExceptionMessage(e));
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      excludeBranches.add(excludeBranch);
    }

    for (String filterString : excludeFilterStrings)
    {
      try
      {
        SearchFilter.createFilterFromString(filterString);
      }
      catch (DirectoryException de)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER.get(
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
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
            filterString, de.getMessageObject());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }

    if(backendID != null)
    {
      backend = DirectoryServer.getBackend(backendID);
      if (backend == null)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_NO_BACKENDS_FOR_ID.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else if (!backend.supports(BackendOperation.LDIF_IMPORT))
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_IMPORT.get(backendID);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      // Make sure that if the "backendID" argument was provided, no include
      // base was included, and the "append" option was not provided, the
      // "clearBackend" argument was also provided if there are more then one
      // baseDNs for the backend being imported.
      else if(!append && includeBranchStrings.isEmpty() &&
          backend.getBaseDNs().length > 1 && !clearBackend)
      {
        StringBuilder builder = new StringBuilder();
        for(DN dn : backend.getBaseDNs())
        {
          builder.append(dn).append(" ");
        }
        LocalizableMessage message = ERR_LDIFIMPORT_MISSING_CLEAR_BACKEND.get(
            builder, typeClearBackend.getNameOrOID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
    else
    {
      // Find the backend that includes all the branches.
      for(DN includeBranch : includeBranches)
      {
        Backend<?> locatedBackend = DirectoryServer.getBackend(includeBranch);
        if(locatedBackend != null)
        {
          if(backend == null)
          {
            backend = locatedBackend;
          }
          else if(backend != locatedBackend)
          {
            // The include branches span across multiple backends.
            LocalizableMessage message = ERR_LDIFIMPORT_INVALID_INCLUDE_BASE.get(
                includeBranch, backend.getBackendID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
          }
        }
        else
        {
          // The include branch is not associated with any backend.
          LocalizableMessage message = ERR_NO_BACKENDS_FOR_BASE.get(includeBranch);
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
        }
      }
    }

    // Make sure the selected backend will handle all the include branches
    defaultIncludeBranches = new ArrayList<>(backend.getBaseDNs().length);
    Collections.addAll(defaultIncludeBranches, backend.getBaseDNs());

    for(DN includeBranch : includeBranches)
    {
      if (!Backend.handlesEntry(includeBranch, defaultIncludeBranches, excludeBranches))
      {
        LocalizableMessage message = ERR_LDIFIMPORT_INVALID_INCLUDE_BASE.get(
            includeBranch, backend.getBackendID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
  }

  private int asInt(Entry taskEntry, AttributeType typeRandomSeed)
  {
    final List<Attribute> attrList = taskEntry.getAttribute(typeRandomSeed);
    return TaskUtils.getSingleValueInteger(attrList, 0);
  }

  private boolean asBoolean(Entry taskEntry, AttributeType typeReplaceExisting)
  {
    final List<Attribute> attrList = taskEntry.getAttribute(typeReplaceExisting);
    return TaskUtils.getBoolean(attrList, false);
  }

  private String asString(Entry taskEntry, AttributeType typeBackendID)
  {
    final List<Attribute> attrList = taskEntry.getAttribute(typeBackendID);
    return TaskUtils.getSingleValueString(attrList);
  }

  private ArrayList<String> asListOfStrings(Entry taskEntry, AttributeType typeExcludeBranch)
  {
    final List<Attribute> attrList = taskEntry.getAttribute(typeExcludeBranch);
    return TaskUtils.getMultiValueString(attrList);
  }

  /** {@inheritDoc} */
  @Override
  public void interruptTask(TaskState interruptState, LocalizableMessage interruptReason)
  {
    if (TaskState.STOPPED_BY_ADMINISTRATOR.equals(interruptState) && importConfig != null)
    {
      addLogMessage(Severity.INFORMATION, TaskMessages.INFO_TASK_STOPPED_BY_ADMIN.get(
      interruptReason));
      setTaskInterruptState(interruptState);
      importConfig.cancel();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInterruptable()
  {
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

    ArrayList<SearchFilter> excludeFilters = new ArrayList<>(excludeFilterStrings.size());
    for (String filterString : excludeFilterStrings)
    {
      try
      {
        excludeFilters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (DirectoryException de)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER, filterString, de.getMessageObject());
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    ArrayList<SearchFilter> includeFilters = new ArrayList<>(includeFilterStrings.size());
    for (String filterString : includeFilterStrings)
    {
      try
      {
        includeFilters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (DirectoryException de)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER, filterString, de.getMessageObject());
        return TaskState.STOPPED_BY_ERROR;
      }
    }


    // Get the backend into which the LDIF should be imported.
    Backend<?> backend = null;
    HashSet<DN> defaultIncludeBranches;
    HashSet<DN> excludeBranches = new HashSet<>(excludeBranchStrings.size());
    HashSet<DN> includeBranches = new HashSet<>(includeBranchStrings.size());

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

      includeBranches.add(includeBranch);
    }

    if(backendID != null)
    {
      backend = DirectoryServer.getBackend(backendID);

      if (backend == null)
      {
        logger.error(ERR_LDIFIMPORT_NO_BACKENDS_FOR_ID);
        return TaskState.STOPPED_BY_ERROR;
      }
      else if (!backend.supports(BackendOperation.LDIF_IMPORT))
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_IMPORT, backendID);
        return TaskState.STOPPED_BY_ERROR;
      }
      // Make sure that if the "backendID" argument was provided, no include
      // base was included, and the "append" option was not provided, the
      // "clearBackend" argument was also provided if there are more then one
      // baseDNs for the backend being imported.
      else if(!append && includeBranches.isEmpty() &&
          backend.getBaseDNs().length > 1 && !clearBackend)
      {
        StringBuilder builder = new StringBuilder();
        builder.append(backend.getBaseDNs()[0]);
        for(int i = 1; i < backend.getBaseDNs().length; i++)
        {
          builder.append(" / ");
          builder.append(backend.getBaseDNs()[i]);
        }
        logger.error(ERR_LDIFIMPORT_MISSING_CLEAR_BACKEND, builder, ATTR_IMPORT_CLEAR_BACKEND);
        return TaskState.STOPPED_BY_ERROR;
      }
    }
    else
    {
      // Find the backend that includes all the branches.
      for(DN includeBranch : includeBranches)
      {
        Backend<?> locatedBackend = DirectoryServer.getBackend(includeBranch);
        if(locatedBackend != null)
        {
          if(backend == null)
          {
            backend = locatedBackend;
          }
          else if(backend != locatedBackend)
          {
            // The include branches span across multiple backends.
            logger.error(ERR_LDIFIMPORT_INVALID_INCLUDE_BASE, includeBranch, backend.getBackendID());
            return TaskState.STOPPED_BY_ERROR;
          }
        }
      }
    }

    // Find backends with subordinate base DNs that should be excluded from the import.
    defaultIncludeBranches = new HashSet<>(backend.getBaseDNs().length);
    Collections.addAll(defaultIncludeBranches, backend.getBaseDNs());

    if (backend.getSubordinateBackends() != null)
    {
      for (Backend<?> subBackend : backend.getSubordinateBackends())
      {
        for (DN baseDN : subBackend.getBaseDNs())
        {
          for (DN importBase : defaultIncludeBranches)
          {
            if (!baseDN.equals(importBase) && baseDN.isDescendantOf(importBase))
            {
              excludeBranches.add(baseDN);
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
        excludeBranch = DN.valueOf(s);
      }
      catch (DirectoryException de)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE, s, de.getMessageObject());
        return TaskState.STOPPED_BY_ERROR;
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE, s, getExceptionMessage(e));
        return TaskState.STOPPED_BY_ERROR;
      }

      excludeBranches.add(excludeBranch);
    }

    if (includeBranchStrings.isEmpty())
    {
      includeBranches = defaultIncludeBranches;
    }
    else
    {
      // Make sure the selected backend will handle all the include branches
      for (DN includeBranch : includeBranches)
      {
        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          logger.error(ERR_LDIFIMPORT_INVALID_INCLUDE_BASE, includeBranch, backend.getBackendID());
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

      String resourcePath = DirectoryServer.getInstanceRoot() + File.separator +
                            PATH_MAKELDIF_RESOURCE_DIR;
      TemplateFile tf = new TemplateFile(resourcePath, random);

      ArrayList<LocalizableMessage> warnings = new ArrayList<>();
      try
      {
        tf.parse(templateFile, warnings);
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_TEMPLATE_FILE, templateFile, e.getMessage());
        return TaskState.STOPPED_BY_ERROR;
      }

      importConfig = new LDIFImportConfig(tf);
    }
    else
    {
      ArrayList<String> fileList = new ArrayList<>(ldifFiles);
      importConfig = new LDIFImportConfig(fileList);
    }
    if(tmpDirectory == null)
    {
      tmpDirectory = "import-tmp";
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
    importConfig.setSkipDNValidation(skipDNValidation);
    importConfig.setTmpDirectory(tmpDirectory);
    importConfig.setThreadCount(threadCount);

    // FIXME -- Should this be conditional?
    importConfig.setInvokeImportPlugins(true);

    if (rejectFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior =
            overwrite ? ExistingFileBehavior.OVERWRITE : ExistingFileBehavior.APPEND;

        importConfig.writeRejectedEntries(rejectFile, existingBehavior);
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_OPEN_REJECTS_FILE, rejectFile, getExceptionMessage(e));
        return TaskState.STOPPED_BY_ERROR;
      }
    }

    if (skipFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior =
            overwrite ? ExistingFileBehavior.OVERWRITE : ExistingFileBehavior.APPEND;
        importConfig.writeSkippedEntries(skipFile, existingBehavior);
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_OPEN_SKIP_FILE, skipFile, getExceptionMessage(e));
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
      logger.traceException(e);

      logger.error(e.getMessageObject());
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
          logger.error(ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), failureReason);
          return TaskState.STOPPED_BY_ERROR;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        logger.error(ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
        return TaskState.STOPPED_BY_ERROR;
      }


      // Launch the import.
      try
      {
        backend.importLDIF(importConfig, DirectoryServer.getInstance().getServerContext());
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        DirectoryServer.notifyImportEnded(backend, importConfig, false);
        LocalizableMessage msg;
        if (de.getResultCode() == ResultCode.CONSTRAINT_VIOLATION)
        {
          msg = ERR_LDIFIMPORT_ERROR_CONSTRAINT_VIOLATION.get();
        }
        else
        {
          msg = de.getMessageObject();
        }
        logger.error(ERR_LDIFIMPORT_ERROR_DURING_IMPORT.get(msg));
        return TaskState.STOPPED_BY_ERROR;
      }
      catch (Exception e)
      {
        logger.traceException(e);

        DirectoryServer.notifyImportEnded(backend, importConfig, false);
        logger.error(ERR_LDIFIMPORT_ERROR_DURING_IMPORT, getExceptionMessage(e));
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
            logger.warn(WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
            return TaskState.COMPLETED_WITH_ERRORS;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          logger.warn(WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
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
        logger.traceException(e);

        logger.error(e.getMessageObject());
        return TaskState.STOPPED_BY_ERROR;
      }
      DirectoryServer.notifyImportEnded(backend, importConfig, true);
    }


    // Clean up after the import by closing the import config.
    importConfig.close();
    return getFinalTaskState();
  }

  private HashSet<AttributeType> toAttributeTypes(ArrayList<String> attrNames)
  {
    final HashSet<AttributeType> attrTypes = new HashSet<>(attrNames.size());
    for (String attrName : attrNames)
    {
      attrTypes.add(DirectoryServer.getAttributeType(attrName.toLowerCase(), attrName));
    }
    return attrTypes;
  }
}
