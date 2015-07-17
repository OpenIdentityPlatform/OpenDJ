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
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.ExportTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.args.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This program provides a utility that may be used to export the contents of a
 * Directory Server backend to an LDIF file.  Depending on the arguments given,
 * this program will either perform the export directly as a process that
 * runs separate from Directory Server; or by scheduling a task to perform the
 * action within the Directory Server via the tasks interface.
 */
public class ExportLDIF extends TaskTool {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The main method for ExportLDIF tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainExportLDIF(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */
  public static int mainExportLDIF(String[] args)
  {
    return mainExportLDIF(args, true, System.out, System.err);
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           {@code null} if standard output is not needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   *
   * @return The error code.
   */
  public static int mainExportLDIF(String[] args, boolean initializeServer,
                                   OutputStream outStream,
                                   OutputStream errStream)
  {
    ExportLDIF tool = new ExportLDIF();
    return tool.process(args, initializeServer, outStream, errStream);
  }

  /** Define the command-line arguments that may be used with this program. */
  private BooleanArgument appendToLDIF;
  private BooleanArgument compressLDIF;
  private BooleanArgument displayUsage;
  private BooleanArgument encryptLDIF;
  private BooleanArgument excludeOperationalAttrs;
  private BooleanArgument signHash;
  private IntegerArgument wrapColumn;
  private StringArgument  backendID;
  private StringArgument  configClass;
  private StringArgument  configFile;
  private StringArgument  excludeAttributeStrings;
  private StringArgument  excludeBranchStrings;
  private StringArgument  excludeFilterStrings;
  private StringArgument  includeAttributeStrings;
  private StringArgument  includeBranchStrings;
  private StringArgument  includeFilterStrings;
  private StringArgument  ldifFile;

  private int process(String[] args, boolean initializeServer,
                      OutputStream outStream, OutputStream errStream) {

    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.ExportLDIF",
                            INFO_LDIFEXPORT_TOOL_DESCRIPTION.get());
    argParser.setShortToolDescription(REF_SHORT_DESC_EXPORT_LDIF.get());


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                              OPTION_LONG_CONFIG_CLASS, true, false,
                              true, INFO_CONFIGCLASS_PLACEHOLDER.get(),
                              ConfigFileHandler.class.getName(), null,
                              INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, INFO_CONFIGFILE_PLACEHOLDER.get(), null,
                              null,
                              INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      ldifFile =
           new StringArgument("ldiffile", OPTION_SHORT_LDIF_FILE,
                              OPTION_LONG_LDIF_FILE,true, false, true,
                              INFO_LDIFFILE_PLACEHOLDER.get(), null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_LDIF_FILE.get());
      argParser.addArgument(ldifFile);


      appendToLDIF = new BooleanArgument(
                   "appendldif", 'a', "appendToLDIF",
                   INFO_LDIFEXPORT_DESCRIPTION_APPEND_TO_LDIF.get());
      argParser.addArgument(appendToLDIF);


      backendID =
           new StringArgument("backendid", 'n', "backendID", true, false, true,
                              INFO_BACKENDNAME_PLACEHOLDER.get(), null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_BACKEND_ID.get());
      argParser.addArgument(backendID);


      includeBranchStrings =
           new StringArgument("includebranch", 'b', "includeBranch", false,
                              true, true, INFO_BRANCH_DN_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_INCLUDE_BRANCH.get());
      argParser.addArgument(includeBranchStrings);


      excludeBranchStrings =
           new StringArgument("excludebranch", 'B', "excludeBranch", false,
                              true, true, INFO_BRANCH_DN_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_BRANCH.get());
      argParser.addArgument(excludeBranchStrings);


      includeAttributeStrings =
           new StringArgument(
                   "includeattribute", 'i', "includeAttribute",
                   false, true, true, INFO_ATTRIBUTE_PLACEHOLDER.get(), null,
                   null,
                   INFO_LDIFEXPORT_DESCRIPTION_INCLUDE_ATTRIBUTE.get());
      argParser.addArgument(includeAttributeStrings);


      excludeAttributeStrings =
           new StringArgument(
                   "excludeattribute", 'e', "excludeAttribute",
                   false, true, true, INFO_ATTRIBUTE_PLACEHOLDER.get(), null,
                   null,
                   INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE.get());
      argParser.addArgument(excludeAttributeStrings);


      includeFilterStrings =
           new StringArgument("includefilter", 'I', "includeFilter",
                              false, true, true, INFO_FILTER_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_INCLUDE_FILTER.get());
      argParser.addArgument(includeFilterStrings);


      excludeFilterStrings =
           new StringArgument("excludefilter", 'E', "excludeFilter",
                              false, true, true, INFO_FILTER_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_FILTER.get());
      argParser.addArgument(excludeFilterStrings);


      excludeOperationalAttrs =
           new BooleanArgument("excludeoperational", 'O', "excludeOperational",
                    INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_OPERATIONAL.get());
      argParser.addArgument(excludeOperationalAttrs);


      wrapColumn =
           new IntegerArgument("wrapcolumn", null, "wrapColumn", false, false,
                               true, INFO_WRAP_COLUMN_PLACEHOLDER.get(), 0,
                               null, true, 0, false, 0,
                               INFO_LDIFEXPORT_DESCRIPTION_WRAP_COLUMN.get());
      argParser.addArgument(wrapColumn);


      compressLDIF =
           new BooleanArgument("compressldif", OPTION_SHORT_COMPRESS,
                               OPTION_LONG_COMPRESS,
                               INFO_LDIFEXPORT_DESCRIPTION_COMPRESS_LDIF.get());
      argParser.addArgument(compressLDIF);


      encryptLDIF =
           new BooleanArgument("encryptldif", 'y', "encryptLDIF",
                               INFO_LDIFEXPORT_DESCRIPTION_ENCRYPT_LDIF.get());
      encryptLDIF.setHidden(true); // See issue #27
      argParser.addArgument(encryptLDIF);


      signHash =
           new BooleanArgument("signhash", 's', "signHash",
                               INFO_LDIFEXPORT_DESCRIPTION_SIGN_HASH.get());
      signHash.setHidden(true); // See issue #28
      argParser.addArgument(signHash);


      displayUsage = CommonArguments.getShowUsage();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }


    // Init the default values so that they can appear also on the usage.
    try
    {
      argParser.getArguments().initArgumentsWithConfiguration();
    }
    catch (ConfigException ce)
    {
      // Ignore.
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      validateTaskArgs();
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      err.println(argParser.getUsage());
      return 1;
    }
    catch (ClientException ce)
    {
      // No need to display the usage since the problem comes with a provided value.
      printWrappedText(err, ce.getMessageObject());
      return 1;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      checkVersion();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessage());
      return 1;
    }

    return process(argParser, initializeServer, out, err);
  }

  /** {@inheritDoc} */
  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    // Required attributes
    attributes.add(new LDAPAttribute(ATTR_TASK_EXPORT_LDIF_FILE, ldifFile.getValue()));
    attributes.add(new LDAPAttribute(ATTR_TASK_EXPORT_BACKEND_ID, backendID.getValue()));

    // Optional attributes
    addAttribute(attributes, ATTR_TASK_EXPORT_APPEND_TO_LDIF, appendToLDIF);
    addAttribute(attributes, ATTR_TASK_EXPORT_COMPRESS_LDIF, compressLDIF);
    addAttribute(attributes, ATTR_TASK_EXPORT_ENCRYPT_LDIF, encryptLDIF);
    addAttribute(attributes, ATTR_TASK_EXPORT_SIGN_HASH, signHash);
    addAttribute(attributes, ATTR_TASK_EXPORT_INCLUDE_ATTRIBUTE, includeAttributeStrings.getValues());
    addAttribute(attributes, ATTR_TASK_EXPORT_EXCLUDE_ATTRIBUTE, excludeAttributeStrings.getValues());
    addAttribute(attributes, ATTR_TASK_EXPORT_INCLUDE_FILTER, includeFilterStrings.getValues());
    addAttribute(attributes, ATTR_TASK_EXPORT_EXCLUDE_FILTER, excludeFilterStrings.getValues());
    addAttribute(attributes, ATTR_TASK_EXPORT_INCLUDE_BRANCH, includeBranchStrings.getValues());
    addAttribute(attributes, ATTR_TASK_EXPORT_EXCLUDE_BRANCH, excludeBranchStrings.getValues());
    addAttribute(attributes, ATTR_TASK_EXPORT_WRAP_COLUMN, wrapColumn);

    if (excludeOperationalAttrs.isPresent())
    {
      attributes.add(
          new LDAPAttribute(ATTR_TASK_EXPORT_INCLUDE_OPERATIONAL_ATTRIBUTES, "false"));
    }
  }

  private void addAttribute(List<RawAttribute> attributes, String attrName, Argument arg)
  {
    if (arg.getValue() != null && !arg.getValue().equals(arg.getDefaultValue()))
    {
      attributes.add(new LDAPAttribute(attrName, arg.getValue()));
    }
  }

  private void addAttribute(List<RawAttribute> attributes, String attrName, List<String> attrValues)
  {
    if (attrValues != null && !attrValues.isEmpty())
    {
      attributes.add(new LDAPAttribute(attrName, attrValues));
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskObjectclass() {
    return "ds-task-export";
  }

  /** {@inheritDoc} */
  @Override
  public Class<?> getTaskClass() {
    return ExportTask.class;
  }

  /** {@inheritDoc} */
  @Override
  protected int processLocal(boolean initializeServer,
                           PrintStream out,
                           PrintStream err) {

    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    if (initializeServer)
    {
      try
      {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_SERVER_BOOTSTRAP_ERROR.get(getExceptionMessage(e)));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(e)));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_SCHEMA.get(e.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e)));
        return 1;
      }


      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager(directoryServer.getServerContext());
        coreConfigManager.initializeCoreConfig();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(e.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getExceptionMessage(e)));
        return 1;
      }


      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(e.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(getExceptionMessage(e)));
        return 1;
      }


      try
      {
        ErrorLogPublisher errorLogPublisher =
            TextErrorLogPublisher.getToolStartupTextErrorPublisher(
            new TextWriter.STREAM(out));
        ErrorLogger.getInstance().addLogPublisher(errorLogPublisher);

        DebugLogger.getInstance().addPublisherIfRequired(new TextWriter.STREAM(out));
      }
      catch(Exception e)
      {
        err.println("Error installing the custom error logger: " +
                    stackTraceToSingleLineString(e));
      }



      // Make sure that the Directory Server plugin initialization is performed.
      try
      {
        HashSet<PluginType> pluginTypes = new HashSet<>(1);
        pluginTypes.add(PluginType.LDIF_EXPORT);
        directoryServer.initializePlugins(pluginTypes);
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS.get(e.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS.get(getExceptionMessage(e)));
        return 1;
      }
    }


    // See if there were any user-defined sets of include/exclude attributes or
    // filters.  If so, then process them.
    Set<AttributeType> excludeAttributes = toAttributeTypes(excludeAttributeStrings);
    Set<AttributeType> includeAttributes = toAttributeTypes(includeAttributeStrings);

    ArrayList<SearchFilter> excludeFilters;
    if (excludeFilterStrings == null)
    {
      excludeFilters = null;
    }
    else
    {
      excludeFilters = new ArrayList<>();
      for (String filterString : excludeFilterStrings.getValues())
      {
        try
        {
          excludeFilters.add(SearchFilter.createFilterFromString(filterString));
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER, filterString, de.getMessageObject());
          return 1;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER, filterString, getExceptionMessage(e));
          return 1;
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
      for (String filterString : includeFilterStrings.getValues())
      {
        try
        {
          includeFilters.add(SearchFilter.createFilterFromString(filterString));
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER, filterString, de.getMessageObject());
          return 1;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER, filterString, getExceptionMessage(e));
          return 1;
        }
      }
    }


    // Get information about the backends defined in the server.  Iterate
    // through them, finding the one backend that should be used for the export,
    // and also finding backends with subordinate base DNs that should be
    // excluded from the export.
    Backend       backend                = null;
    List<DN>      baseDNList             = null;
    List<DN>      defaultIncludeBranches = null;
    ArrayList<DN> excludeBranches        = null;

    ArrayList<Backend>     backendList = new ArrayList<>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<>();
    ArrayList<List<DN>>    dnList      = new ArrayList<>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend b = backendList.get(i);
      if (! backendID.getValue().equals(b.getBackendID()))
      {
        continue;
      }

      if (backend == null)
      {
        backend                = b;
        baseDNList             = dnList.get(i);
        defaultIncludeBranches = dnList.get(i);
      }
      else
      {
        logger.error(ERR_LDIFEXPORT_MULTIPLE_BACKENDS_FOR_ID, backendID.getValue());
        return 1;
      }
    }

    if (backend == null)
    {
      logger.error(ERR_LDIFEXPORT_NO_BACKENDS_FOR_ID, backendID.getValue());
      return 1;
    }
    else if (!backend.supports(BackendOperation.RESTORE))
    {
      logger.error(ERR_LDIFEXPORT_CANNOT_EXPORT_BACKEND, backendID.getValue());
      return 1;
    }

    if (excludeBranchStrings.isPresent())
    {
      excludeBranches = new ArrayList<>();
      for (String s : excludeBranchStrings.getValues())
      {
        DN excludeBranch;
        try
        {
          excludeBranch = DN.valueOf(s);
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE, s, de.getMessageObject());
          return 1;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE, s, getExceptionMessage(e));
          return 1;
        }

        if (! excludeBranches.contains(excludeBranch))
        {
          excludeBranches.add(excludeBranch);
        }
      }
    }


    List<DN> includeBranches;
    if (includeBranchStrings.isPresent())
    {
      includeBranches = new ArrayList<>();
      for (String s : includeBranchStrings.getValues())
      {
        DN includeBranch;
        try
        {
          includeBranch = DN.valueOf(s);
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE, s, de.getMessageObject());
          return 1;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE, s, getExceptionMessage(e));
          return 1;
        }

        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          logger.error(ERR_LDIFEXPORT_INVALID_INCLUDE_BASE, s, backendID.getValue());
          return 1;
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
    if (appendToLDIF.isPresent())
    {
      existingBehavior = ExistingFileBehavior.APPEND;
    }
    else
    {
      existingBehavior = ExistingFileBehavior.OVERWRITE;
    }

    LDIFExportConfig exportConfig = new LDIFExportConfig(ldifFile.getValue(),
                                                         existingBehavior);
    exportConfig.setCompressData(compressLDIF.isPresent());
    exportConfig.setEncryptData(encryptLDIF.isPresent());
    exportConfig.setExcludeAttributes(excludeAttributes);
    exportConfig.setExcludeBranches(excludeBranches);
    exportConfig.setExcludeFilters(excludeFilters);
    exportConfig.setIncludeAttributes(includeAttributes);
    exportConfig.setIncludeBranches(includeBranches);
    exportConfig.setIncludeFilters(includeFilters);
    exportConfig.setSignHash(signHash.isPresent());
    exportConfig.setIncludeOperationalAttributes(
                      !excludeOperationalAttrs.isPresent());

    // FIXME -- Should this be conditional?
    exportConfig.setInvokeExportPlugins(true);

    try
    {
      exportConfig.setWrapColumn(wrapColumn.getIntValue());
    }
    catch (ArgumentException ae)
    {
      logger.error(ERR_LDIFEXPORT_CANNOT_DECODE_WRAP_COLUMN_AS_INTEGER, wrapColumn.getValue());
      return 1;
    }


    // Get the set of base DNs for the backend as an array.
    DN[] baseDNs = new DN[baseDNList.size()];
    baseDNList.toArray(baseDNs);


    // Acquire a shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        logger.error(ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), failureReason);
        return 1;
      }
    }
    catch (Exception e)
    {
      logger.error(ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
      return 1;
    }

    boolean errorOccurred = false;

    // Launch the export.
    try
    {
      backend.exportLDIF(exportConfig);
    }
    catch (DirectoryException de)
    {
      logger.error(ERR_LDIFEXPORT_ERROR_DURING_EXPORT, de.getMessageObject());
      errorOccurred = true;
    }
    catch (Exception e)
    {
      logger.error(ERR_LDIFEXPORT_ERROR_DURING_EXPORT, getExceptionMessage(e));
      errorOccurred = true;
    }


    // Release the shared lock on the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
      }
    }
    catch (Exception e)
    {
      logger.warn(WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
    }


    // Clean up after the export by closing the export config.
    exportConfig.close();
    return !errorOccurred ? 0 : 1;
  }

  private Set<AttributeType> toAttributeTypes(StringArgument attributeArg)
  {
    if (attributeArg == null)
    {
      return null;
    }

    Set<AttributeType> results = new HashSet<>();
    for (String attrName : attributeArg.getValues())
    {
      results.add(DirectoryServer.getAttributeTypeOrDefault(attrName.toLowerCase(), attrName));
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskId() {
    // NYI.
    return null;
  }
}
