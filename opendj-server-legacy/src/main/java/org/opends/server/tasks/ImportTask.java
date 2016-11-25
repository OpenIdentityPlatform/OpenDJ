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
 * Portions Copyright 2013-2016 ForgeRock AS.
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
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.messages.Severity;
import org.opends.messages.TaskMessages;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.LocalBackend.BackendOperation;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.tools.makeldif.TemplateFile;
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

  private boolean isCompressed;
  private boolean isEncrypted;
  private boolean overwrite;
  private boolean skipSchemaValidation;
  private boolean clearBackend;
  private String tmpDirectory;
  private int threadCount;
  private String backendID;
  private String rejectFile;
  private String skipFile;
  private List<String> excludeAttributeStrings;
  private List<String> excludeBranchStrings;
  private List<String> excludeFilterStrings;
  private List<String> includeAttributeStrings;
  private List<String> includeBranchStrings;
  private List<String> includeFilterStrings;
  private List<String> ldifFiles;
  private String templateFile;
  private int randomSeed;
  private LDIFImportConfig importConfig;

  @Override
  public LocalizableMessage getDisplayName() {
    return INFO_TASK_IMPORT_NAME.get();
  }

  @Override
  public LocalizableMessage getAttributeDisplayName(String name) {
    return argDisplayMap.get(name);
  }

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

    Schema schema = getInstance().getServerContext().getSchema();
    AttributeType typeLdifFile = schema.getAttributeType(ATTR_IMPORT_LDIF_FILE);
    AttributeType typeTemplateFile = schema.getAttributeType(ATTR_IMPORT_TEMPLATE_FILE);
    AttributeType typeBackendID = schema.getAttributeType(ATTR_IMPORT_BACKEND_ID);
    AttributeType typeIncludeBranch = schema.getAttributeType(ATTR_IMPORT_INCLUDE_BRANCH);
    AttributeType typeExcludeBranch = schema.getAttributeType(ATTR_IMPORT_EXCLUDE_BRANCH);
    AttributeType typeIncludeAttribute = schema.getAttributeType(ATTR_IMPORT_INCLUDE_ATTRIBUTE);
    AttributeType typeExcludeAttribute = schema.getAttributeType(ATTR_IMPORT_EXCLUDE_ATTRIBUTE);
    AttributeType typeIncludeFilter = schema.getAttributeType(ATTR_IMPORT_INCLUDE_FILTER);
    AttributeType typeExcludeFilter = schema.getAttributeType(ATTR_IMPORT_EXCLUDE_FILTER);
    AttributeType typeRejectFile = schema.getAttributeType(ATTR_IMPORT_REJECT_FILE);
    AttributeType typeSkipFile = schema.getAttributeType(ATTR_IMPORT_SKIP_FILE);
    AttributeType typeOverwrite = schema.getAttributeType(ATTR_IMPORT_OVERWRITE);
    AttributeType typeSkipSchemaValidation = schema.getAttributeType(ATTR_IMPORT_SKIP_SCHEMA_VALIDATION);
    AttributeType typeIsCompressed = schema.getAttributeType(ATTR_IMPORT_IS_COMPRESSED);
    AttributeType typeIsEncrypted = schema.getAttributeType(ATTR_IMPORT_IS_ENCRYPTED);
    AttributeType typeClearBackend = schema.getAttributeType(ATTR_IMPORT_CLEAR_BACKEND);
    AttributeType typeRandomSeed = schema.getAttributeType(ATTR_IMPORT_RANDOM_SEED);
    AttributeType typeThreadCount = schema.getAttributeType(ATTR_IMPORT_THREAD_COUNT);
    AttributeType typeTmpDirectory = schema.getAttributeType(ATTR_IMPORT_TMP_DIRECTORY);

    List<String> ldifFilestmp = asListOfStrings(taskEntry, typeLdifFile);
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
      if (!f.canRead()) {
        LocalizableMessage message = ERR_LDIFIMPORT_LDIF_FILE_DOESNT_EXIST.get(s);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      ldifFiles.add(s);
    }

    templateFile = asString(taskEntry, typeTemplateFile);
    if (templateFile != null)
    {
      File f = new File(templateFile);
      if (!f.isAbsolute())
      {
        templateFile = new File(DirectoryServer.getInstanceRoot(), templateFile).getAbsolutePath();
      }
    }

    tmpDirectory = asString(taskEntry, typeTmpDirectory);
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

    LocalBackend<?> backend = null;
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
      backend = getServerContext().getBackendConfigManager().getLocalBackendById(backendID);
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
    }
    else
    {
      // Find the backend that includes all the branches.
      BackendConfigManager backendConfigManager = getServerContext().getBackendConfigManager();
      for(DN includeBranch : includeBranches)
      {
        LocalBackend<?> locatedBackend = backendConfigManager.findLocalBackendForEntry(includeBranch);
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
    defaultIncludeBranches = new ArrayList<>(backend.getBaseDNs());

    for(DN includeBranch : includeBranches)
    {
      if (!LocalBackend.handlesEntry(includeBranch, defaultIncludeBranches, excludeBranches))
      {
        LocalizableMessage message = ERR_LDIFIMPORT_INVALID_INCLUDE_BASE.get(
            includeBranch, backend.getBackendID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
  }

  private int asInt(Entry taskEntry, AttributeType attributeType)
  {
    return TaskUtils.getSingleValueInteger(taskEntry.getAllAttributes(attributeType), 0);
  }

  private boolean asBoolean(Entry taskEntry, AttributeType attributeType)
  {
    return TaskUtils.getBoolean(taskEntry.getAllAttributes(attributeType), false);
  }

  private String asString(Entry taskEntry, AttributeType attributeType)
  {
    return TaskUtils.getSingleValueString(taskEntry.getAllAttributes(attributeType));
  }

  private List<String> asListOfStrings(Entry taskEntry, AttributeType attributeType)
  {
    return TaskUtils.getMultiValueString(taskEntry.getAllAttributes(attributeType));
  }

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

  @Override
  public boolean isInterruptable()
  {
    return true;
  }

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
    LocalBackend<?> backend = null;
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
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE, s, getExceptionMessage(e));
        return TaskState.STOPPED_BY_ERROR;
      }

      includeBranches.add(includeBranch);
    }

    if(backendID != null)
    {
      backend = getServerContext().getBackendConfigManager().getLocalBackendById(backendID);

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
    }
    else
    {
      // Find the backend that includes all the branches.
      BackendConfigManager backendConfigManager = getServerContext().getBackendConfigManager();
      for(DN includeBranch : includeBranches)
      {
        LocalBackend<?> locatedBackend = backendConfigManager.findLocalBackendForEntry(includeBranch);
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
    defaultIncludeBranches = new HashSet<>(backend.getBaseDNs());
    for (Backend<?> subBackend : getServerContext().getBackendConfigManager().getSubordinateBackends(backend))
    {
      excludeBranches.addAll(subBackend.getBaseDNs());
    }

    for (String s : excludeBranchStrings)
    {
      DN excludeBranch;
      try
      {
        excludeBranch = DN.valueOf(s);
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
        if (! LocalBackend.handlesEntry(includeBranch, defaultIncludeBranches,
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
        if (de.getResultCode().equals(DirectoryServer.getCoreConfigManager().getServerErrorResultCode()))
        {
          logger.error(ERR_LDIFIMPORT_ERROR_DURING_IMPORT.get(de.getMessageObject()));
        }
        else
        {
          logger.error(de.getMessageObject());
        }
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
        backend = getServerContext().getBackendConfigManager().getLocalBackendById(backend.getBackendID());
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

  private HashSet<AttributeType> toAttributeTypes(List<String> attrNames)
  {
    final HashSet<AttributeType> attrTypes = new HashSet<>(attrNames.size());
    for (String attrName : attrNames)
    {
      attrTypes.add(DirectoryServer.getInstance().getServerContext().getSchema().getAttributeType(attrName));
    }
    return attrTypes;
  }
}
