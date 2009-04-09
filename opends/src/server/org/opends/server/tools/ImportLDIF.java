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
package org.opends.server.tools;



import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.TextDebugLogPublisher;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.ImportTask;
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.cli.CLIException;



/**
 * This program provides a utility that may be used to import the contents of an
 * LDIF file into a Directory Server backend.  This will be a process that is
 * intended to run separate from Directory Server and not internally within the
 * server process (e.g., via the tasks interface).
 */
public class ImportLDIF extends TaskTool {
  /**
   * The buffer size that should be used when reading data from LDIF.
   */
  public static final int LDIF_BUFFER_SIZE = 1048576;


  /**
   * The main method for ImportLDIF tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainImportLDIF(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the import process.
   *
   * @param  args  The command-line arguments provided to thisprogram.
   *
   * @return The error code.
   */
  public static int mainImportLDIF(String[] args)
  {
    return mainImportLDIF(args, true, System.out, System.err);
  }

  /**
   * Processes the command-line arguments and invokes the import process.
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
  public static int mainImportLDIF(String[] args, boolean initializeServer,
                                   OutputStream outStream,
                                   OutputStream errStream)
  {
    ImportLDIF tool = new ImportLDIF();
    return tool.process(args, initializeServer, outStream, errStream);
  }

  // Define the command-line arguments that may be used with this program.
  private BooleanArgument append                  = null;
  private BooleanArgument countRejects            = null;
  private BooleanArgument displayUsage            = null;
  private BooleanArgument isCompressed            = null;
  private BooleanArgument isEncrypted             = null;
  private BooleanArgument overwrite               = null;
  private BooleanArgument quietMode               = null;
  private BooleanArgument replaceExisting         = null;
  private BooleanArgument skipSchemaValidation    = null;
  private BooleanArgument clearBackend            = null;
  private IntegerArgument randomSeed              = null;
  private StringArgument  backendID               = null;
  private StringArgument  configClass             = null;
  private StringArgument  configFile              = null;
  private StringArgument  excludeAttributeStrings = null;
  private StringArgument  excludeBranchStrings    = null;
  private StringArgument  excludeFilterStrings    = null;
  private StringArgument  includeAttributeStrings = null;
  private StringArgument  includeBranchStrings    = null;
  private StringArgument  includeFilterStrings    = null;
  private StringArgument  ldifFiles               = null;
  private StringArgument  rejectFile              = null;
  private StringArgument  skipFile                = null;
  private StringArgument  templateFile            = null;

  private int process(String[] args, boolean initializeServer,
                      OutputStream outStream, OutputStream errStream) {

    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    // FIXME -- Need to add a mechanism for verifying the file signature.


    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.ImportLDIF",
                            INFO_LDIFIMPORT_TOOL_DESCRIPTION.get());

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


      ldifFiles =
           new StringArgument("ldiffile", OPTION_SHORT_LDIF_FILE,
                              OPTION_LONG_LDIF_FILE, false, true, true,
                              INFO_LDIFFILE_PLACEHOLDER.get(), null, null,
                              INFO_LDIFIMPORT_DESCRIPTION_LDIF_FILE.get());
      argParser.addArgument(ldifFiles);


      templateFile =
           new StringArgument("templatefile", 'A', "templateFile", false, false,
                              true, INFO_TEMPLATE_FILE_PLACEHOLDER.get(), null,
                              null,
                              INFO_LDIFIMPORT_DESCRIPTION_TEMPLATE_FILE.get());
      argParser.addArgument(templateFile);


      append =
           new BooleanArgument("append", 'a', "append",
                               INFO_LDIFIMPORT_DESCRIPTION_APPEND.get());
      argParser.addArgument(append);


      replaceExisting =
           new BooleanArgument(
                   "replaceexisting", 'r', "replaceExisting",
                   INFO_LDIFIMPORT_DESCRIPTION_REPLACE_EXISTING.get());
      argParser.addArgument(replaceExisting);


      backendID =
           new StringArgument("backendid", 'n', "backendID", false, false, true,
                              INFO_BACKENDNAME_PLACEHOLDER.get(), null, null,
                              INFO_LDIFIMPORT_DESCRIPTION_BACKEND_ID.get());
      argParser.addArgument(backendID);

      clearBackend =
          new BooleanArgument("clearbackend", 'F', "clearBackend",
                              INFO_LDIFIMPORT_DESCRIPTION_CLEAR_BACKEND.get());
      argParser.addArgument(clearBackend);


      includeBranchStrings =
           new StringArgument("includebranch", 'b', "includeBranch", false,
                              true, true, INFO_BRANCH_DN_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDIFIMPORT_DESCRIPTION_INCLUDE_BRANCH.get());
      argParser.addArgument(includeBranchStrings);


      excludeBranchStrings =
           new StringArgument("excludebranch", 'B', "excludeBranch", false,
                              true, true, INFO_BRANCH_DN_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDIFIMPORT_DESCRIPTION_EXCLUDE_BRANCH.get());
      argParser.addArgument(excludeBranchStrings);


      includeAttributeStrings =
           new StringArgument(
                   "includeattribute", 'i', "includeAttribute",
                   false, true, true, INFO_ATTRIBUTE_PLACEHOLDER.get(), null,
                   null,
                   INFO_LDIFIMPORT_DESCRIPTION_INCLUDE_ATTRIBUTE.get());
      argParser.addArgument(includeAttributeStrings);


      excludeAttributeStrings =
           new StringArgument(
                   "excludeattribute", 'e', "excludeAttribute",
                   false, true, true, INFO_ATTRIBUTE_PLACEHOLDER.get(), null,
                   null,
                   INFO_LDIFIMPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE.get());
      argParser.addArgument(excludeAttributeStrings);


      includeFilterStrings =
           new StringArgument(
                   "includefilter", 'I', "includeFilter",
                   false, true, true, INFO_FILTER_PLACEHOLDER.get(), null, null,
                   INFO_LDIFIMPORT_DESCRIPTION_INCLUDE_FILTER.get());
      argParser.addArgument(includeFilterStrings);


      excludeFilterStrings =
           new StringArgument("excludefilter", 'E', "excludeFilter",
                              false, true, true, INFO_FILTER_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDIFIMPORT_DESCRIPTION_EXCLUDE_FILTER.get());
      argParser.addArgument(excludeFilterStrings);


      rejectFile =
           new StringArgument("rejectfile", 'R', "rejectFile", false, false,
                              true, INFO_REJECT_FILE_PLACEHOLDER.get(), null,
                              null,
                              INFO_LDIFIMPORT_DESCRIPTION_REJECT_FILE.get());
      argParser.addArgument(rejectFile);


      skipFile =
           new StringArgument("skipfile", null, "skipFile", false, false,
                              true, INFO_SKIP_FILE_PLACEHOLDER.get(), null,
                              null,
                              INFO_LDIFIMPORT_DESCRIPTION_SKIP_FILE.get());
      argParser.addArgument(skipFile);


      overwrite =
           new BooleanArgument("overwrite", 'O', "overwrite",
                               INFO_LDIFIMPORT_DESCRIPTION_OVERWRITE.get());
      argParser.addArgument(overwrite);


      randomSeed =
           new IntegerArgument("randomseed", OPTION_SHORT_RANDOM_SEED,
                               OPTION_LONG_RANDOM_SEED, false, false,
                               true, INFO_SEED_PLACEHOLDER.get(),
                               0, null, false, 0, false, 0,
                               INFO_LDIFIMPORT_DESCRIPTION_RANDOM_SEED.get());
      argParser.addArgument(randomSeed);


      skipSchemaValidation =
           new BooleanArgument("skipschema", 'S', "skipSchemaValidation",
                    INFO_LDIFIMPORT_DESCRIPTION_SKIP_SCHEMA_VALIDATION.get());
      argParser.addArgument(skipSchemaValidation);


      countRejects =
           new BooleanArgument("countrejects", null, "countRejects",
                               INFO_LDIFIMPORT_DESCRIPTION_COUNT_REJECTS.get());
      argParser.addArgument(countRejects);


      isCompressed =
           new BooleanArgument("iscompressed", 'c', "isCompressed",
                               INFO_LDIFIMPORT_DESCRIPTION_IS_COMPRESSED.get());
      argParser.addArgument(isCompressed);


      isEncrypted =
           new BooleanArgument("isencrypted", 'y', "isEncrypted",
                               INFO_LDIFIMPORT_DESCRIPTION_IS_ENCRYPTED.get());
      isEncrypted.setHidden(true); //See issue #27
      argParser.addArgument(isEncrypted);


      quietMode = new BooleanArgument("quietmode", OPTION_SHORT_QUIET,
                                      OPTION_LONG_QUIET,
                                      INFO_LDIFIMPORT_DESCRIPTION_QUIET.get());
      argParser.addArgument(quietMode);


      displayUsage =
           new BooleanArgument("help", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                               INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
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
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }
    catch (CLIException ce)
    {
      // No need to display the usage since the problem comes with a provided
      // value.
      err.println(wrapText(ce.getMessageObject(), MAX_LINE_WIDTH));
      return 1;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }


    // Make sure that either the "ldifFile" argument or the "templateFile"
    // argument was provided, but not both.
    if (ldifFiles.isPresent())
    {
      if (templateFile.isPresent())
      {
        Message message = ERR_LDIFIMPORT_CONFLICTING_OPTIONS.get(
                ldifFiles.getLongIdentifier(),
                templateFile.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else if (! templateFile.isPresent())
    {
      Message message = ERR_LDIFIMPORT_MISSING_REQUIRED_ARGUMENT.get(
              ldifFiles.getLongIdentifier(),
              templateFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Make sure that either the "includeBranchStrings" argument or the
    // "backendID" argument was provided.
    if(!includeBranchStrings.isPresent() && !backendID.isPresent())
    {
      Message message = ERR_LDIFIMPORT_MISSING_BACKEND_ARGUMENT.get(
              includeBranchStrings.getLongIdentifier(),
              backendID.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Count rejects option requires the ability to return result codes
    // which are potentially greater than 1. This is not supported by
    // the task framework.
    if (countRejects.isPresent()
        && argParser.connectionArgumentsPresent())
    {
      Message message =
          ERR_LDIFIMPORT_COUNT_REJECTS_REQUIRES_OFFLINE
              .get(countRejects.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Don't write non-error messages to console if quite
    if (quietMode.isPresent()) {
      out = new PrintStream(NullOutputStream.instance());
    }

    return process(argParser, initializeServer, out, err);
  }

  /**
   * {@inheritDoc}
   */
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    //
    // Required attributes
    //
    ArrayList<ByteString> values;

    List<String> fileList = ldifFiles.getValues();
    if ((fileList != null) && (fileList.size() > 0))
    {
      if (fileList != null && fileList.size() > 0) {
        values = new ArrayList<ByteString>(fileList.size());
        for (String file : fileList) {
          values.add(ByteString.valueOf(file));
        }
        attributes.add(new LDAPAttribute(ATTR_IMPORT_LDIF_FILE, values));
      }
    }

    String templateFileValue = templateFile.getValue();
    if (templateFileValue != null)
    {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(templateFileValue));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_TEMPLATE_FILE, values));
    }

    String randomSeedValue = randomSeed.getValue();
    if (randomSeedValue != null)
    {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(randomSeedValue));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_RANDOM_SEED, values));
    }

    //
    // Optional attributes
    //
    if (append.getValue() != null &&
            !append.getValue().equals(append.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(append.getValue()));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_APPEND, values));
    }

    if (replaceExisting.getValue() != null &&
            !replaceExisting.getValue().equals(
                    replaceExisting.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(replaceExisting.getValue()));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_REPLACE_EXISTING, values));
    }

    if (backendID.getValue() != null &&
            !backendID.getValue().equals(
                    backendID.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(backendID.getValue()));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_BACKEND_ID, values));
    }

    List<String> includeAttributes = includeAttributeStrings.getValues();
    if (includeAttributes != null && includeAttributes.size() > 0) {
      values = new ArrayList<ByteString>(includeAttributes.size());
      for (String includeAttribute : includeAttributes) {
        values.add(ByteString.valueOf(includeAttribute));
      }
      attributes.add(new LDAPAttribute(ATTR_IMPORT_INCLUDE_ATTRIBUTE, values));
    }

    List<String> excludeAttributes = excludeAttributeStrings.getValues();
    if (excludeAttributes != null && excludeAttributes.size() > 0) {
      values = new ArrayList<ByteString>(excludeAttributes.size());
      for (String excludeAttribute : excludeAttributes) {
        values.add(ByteString.valueOf(excludeAttribute));
      }
      attributes.add(new LDAPAttribute(ATTR_IMPORT_EXCLUDE_ATTRIBUTE, values));
    }

    List<String> includeFilters = includeFilterStrings.getValues();
    if (includeFilters != null && includeFilters.size() > 0) {
      values = new ArrayList<ByteString>(includeFilters.size());
      for (String includeFilter : includeFilters) {
        values.add(ByteString.valueOf(includeFilter));
      }
      attributes.add(new LDAPAttribute(ATTR_IMPORT_INCLUDE_FILTER, values));
    }

    List<String> excludeFilters = excludeFilterStrings.getValues();
    if (excludeFilters != null && excludeFilters.size() > 0) {
      values = new ArrayList<ByteString>(excludeFilters.size());
      for (String excludeFilter : excludeFilters) {
        values.add(ByteString.valueOf(excludeFilter));
      }
      attributes.add(new LDAPAttribute(ATTR_IMPORT_EXCLUDE_FILTER, values));
    }

    List<String> includeBranches = includeBranchStrings.getValues();
    if (includeBranches != null && includeBranches.size() > 0) {
      values = new ArrayList<ByteString>(includeBranches.size());
      for (String includeBranche : includeBranches) {
        values.add(ByteString.valueOf(includeBranche));
      }
      attributes.add(new LDAPAttribute(ATTR_IMPORT_INCLUDE_BRANCH, values));
    }

    List<String> excludeBranches = excludeBranchStrings.getValues();
    if (excludeBranches != null && excludeBranches.size() > 0) {
      values = new ArrayList<ByteString>(excludeBranches.size());
      for (String excludeBranch : excludeBranches) {
        values.add(ByteString.valueOf(excludeBranch));
      }
      attributes.add(new LDAPAttribute(ATTR_IMPORT_EXCLUDE_BRANCH, values));
    }

    if (rejectFile.getValue() != null &&
            !rejectFile.getValue().equals(
                    rejectFile.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(rejectFile.getValue()));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_REJECT_FILE, values));
    }

    if (skipFile.getValue() != null &&
            !skipFile.getValue().equals(
                    skipFile.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(skipFile.getValue()));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_SKIP_FILE, values));
    }

    if (overwrite.getValue() != null &&
            !overwrite.getValue().equals(
                    overwrite.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(overwrite.getValue()));
      attributes.add(new LDAPAttribute(ATTR_IMPORT_OVERWRITE, values));
    }

    if (skipSchemaValidation.getValue() != null &&
            !skipSchemaValidation.getValue().equals(
                    skipSchemaValidation.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(skipSchemaValidation.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_IMPORT_SKIP_SCHEMA_VALIDATION, values));
    }

    if (isCompressed.getValue() != null &&
            !isCompressed.getValue().equals(
                    isCompressed.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(isCompressed.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_IMPORT_IS_COMPRESSED, values));
    }

    if (isEncrypted.getValue() != null &&
            !isEncrypted.getValue().equals(
                    isEncrypted.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(isEncrypted.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_IMPORT_IS_ENCRYPTED, values));
    }

    if (clearBackend.getValue() != null &&
            !clearBackend.getValue().equals(
                    clearBackend.getDefaultValue())) {
      values = new ArrayList<ByteString>(1);
      values.add(ByteString.valueOf(clearBackend.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_IMPORT_CLEAR_BACKEND, values));
    }

  }

  /**
   * {@inheritDoc}
   */
  public String getTaskObjectclass() {
    return "ds-task-import";
  }

  /**
   * {@inheritDoc}
   */
  public Class getTaskClass() {
    return ImportTask.class;
  }

  /**
   * {@inheritDoc}
   */
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
        Message message = ERR_SERVER_BOOTSTRAP_ERROR.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager();
        coreConfigManager.initializeCoreConfig();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(
                ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      if (! quietMode.isPresent())
      {
        try
        {
          ErrorLogPublisher errorLogPublisher =
              TextErrorLogPublisher.getStartupTextErrorPublisher(
                  new TextWriter.STREAM(out));
          DebugLogPublisher debugLogPublisher =
              TextDebugLogPublisher.getStartupTextDebugPublisher(
                  new TextWriter.STREAM(out));
          ErrorLogger.addErrorLogPublisher(errorLogPublisher);
          DebugLogger.addDebugLogPublisher(debugLogPublisher);
        }
        catch(Exception e)
        {
          err.println("Error installing the custom error logger: " +
              stackTraceToSingleLineString(e));
        }
      }


      // Initialize all the password policy information.
      try
      {
        directoryServer.initializePasswordPolicyComponents();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PWPOLICY.get(
                ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PWPOLICY.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PWPOLICY.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Make sure that the Directory Server plugin initialization is performed.
      try
      {
        HashSet<PluginType> pluginTypes = new HashSet<PluginType>(1);
        pluginTypes.add(PluginType.LDIF_IMPORT);
        directoryServer.initializePlugins(pluginTypes);
      }
      catch (ConfigException ce)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PLUGINS.get(
                ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PLUGINS.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PLUGINS.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }


    // See if there were any user-defined sets of include/exclude attributes or
    // filters.  If so, then process them.
    HashSet<AttributeType> excludeAttributes;
    boolean excludeAllUserAttributes = false;
    boolean excludeAllOperationalAttributes = false;
    if (excludeAttributeStrings == null)
    {
      excludeAttributes = null;
    }
    else
    {
      excludeAttributes = new HashSet<AttributeType>();
      for (String attrName : excludeAttributeStrings.getValues())
      {
        String        lowerName = attrName.toLowerCase();
        if(lowerName.equals("*"))
        {
          excludeAllUserAttributes = true;
        }
        else if(lowerName.equals("+"))
        {
          excludeAllOperationalAttributes = true;
        }
        else
        {
          AttributeType attrType  = DirectoryServer.getAttributeType(lowerName);
          if (attrType == null)
          {
            attrType = DirectoryServer.getDefaultAttributeType(attrName);
          }

          excludeAttributes.add(attrType);
        }
      }
    }

    HashSet<AttributeType> includeAttributes;
    boolean includeAllUserAttributes = false;
    boolean includeAllOperationalAttributes = false;
    if (includeAttributeStrings == null)
    {
      includeAttributes = null;
    }
    else
    {
      includeAttributes = new HashSet<AttributeType>();
      for (String attrName : includeAttributeStrings.getValues())
      {
        String        lowerName = attrName.toLowerCase();
         if(lowerName.equals("*"))
        {
          includeAllUserAttributes = true;
        }
        else if(lowerName.equals("+"))
        {
          includeAllOperationalAttributes = true;
        }
        else
        {
          AttributeType attrType  = DirectoryServer.getAttributeType(lowerName);
          if (attrType == null)
          {
            attrType = DirectoryServer.getDefaultAttributeType(attrName);
          }

          includeAttributes.add(attrType);
        }
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
      for (String filterString : excludeFilterStrings.getValues())
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
          return 1;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER.get(
              filterString, getExceptionMessage(e));
          logError(message);
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
      includeFilters = new ArrayList<SearchFilter>();
      for (String filterString : includeFilterStrings.getValues())
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
          return 1;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
              filterString, getExceptionMessage(e));
          logError(message);
          return 1;
        }
      }
    }


    // Get information about the backends defined in the server.  Iterate
    // through them, finding the one backend into which the LDIF should be
    // imported and finding backends with subordinate base DNs that should be
    // excluded from the import.
    Backend       backend           = null;
    List<DN> defaultIncludeBranches = null;
    List<DN> excludeBranches        = new ArrayList<DN>();
    List<DN> includeBranches        = new ArrayList<DN>();

    if (includeBranchStrings.isPresent())
    {
      includeBranches = new ArrayList<DN>();
      for (String s : includeBranchStrings.getValues())
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
          return 1;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE.get(
              s, getExceptionMessage(e));
          logError(message);
          return 1;
        }

        includeBranches.add(includeBranch);
      }
    }

    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<BackendCfg>();
    ArrayList<List<DN>> dnList = new ArrayList<List<DN>>();
    int code = BackendToolUtils.getBackends(backendList, entryList, dnList);
    if (code != 0)
    {
      return code;
    }

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend b = backendList.get(i);

      if(backendID.isPresent())
      {
        if (! backendID.getValue().equals(b.getBackendID()))
        {
          continue;
        }
      }
      else
      {
        boolean useBackend = false;
        for(DN baseDN : dnList.get(i))
        {
          for(DN includeDN : includeBranches)
          {
            if(baseDN.isAncestorOf(includeDN))
            {
              useBackend = true;
              break;
            }
          }
          if(useBackend)
          {
            break;
          }
        }
        if(!useBackend)
        {
          continue;
        }
      }

      if (backend == null)
      {
        backend                = b;
        defaultIncludeBranches = dnList.get(i);
      }
      else
      {
        Message message = ERR_LDIFIMPORT_MULTIPLE_BACKENDS_FOR_ID.get();
        logError(message);
        return 1;
      }
    }

    if (backend == null)
    {
      Message message =
          ERR_LDIFIMPORT_NO_BACKENDS_FOR_ID.get();
      logError(message);
      return 1;
    }
    else if (! backend.supportsLDIFImport())
    {
      Message message = ERR_LDIFIMPORT_CANNOT_IMPORT.get(backendID.getValue());
      logError(message);
      return 1;
    }

    for (List<DN> baseList : dnList)
    {
      for (DN baseDN : baseList)
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

    // Make sure that if the "backendID" argument was provided, no include base
    // was included, and the "append" option was not provided, the
    // "clearBackend" argument was also provided if there are more then one
    // baseDNs for the backend being imported.
    if(backendID.isPresent() && !includeBranchStrings.isPresent() &&
        !append.isPresent() && defaultIncludeBranches.size() > 1 &&
        !clearBackend.isPresent())
    {
      StringBuilder builder = new StringBuilder();
      builder.append(backend.getBaseDNs()[0].toNormalizedString());
      for(int i = 1; i < backend.getBaseDNs().length; i++)
      {
        builder.append(" / ");
        builder.append(backend.getBaseDNs()[i].toNormalizedString());
      }
      Message message = ERR_LDIFIMPORT_MISSING_CLEAR_BACKEND.get(
              builder.toString(), clearBackend.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    for (String s : excludeBranchStrings.getValues())
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
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
            s, getExceptionMessage(e));
        logError(message);
        return 1;
      }

      if (! excludeBranches.contains(excludeBranch))
      {
        excludeBranches.add(excludeBranch);
      }
    }

    if (! includeBranchStrings.isPresent())
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
              includeBranch.toNormalizedString(), backendID.getValue());
          logError(message);
          return 1;
        }
      }
    }


    // See if the data should be read from LDIF files or generated via MakeLDIF.
    LDIFImportConfig importConfig;
    if (ldifFiles.isPresent())
    {
      ArrayList<String> fileList = new ArrayList<String>(ldifFiles.getValues());
      int badFileCount = 0;
      for (String pathname : fileList)
      {
        File f = new File(pathname);
        if (!f.canRead())
        {
          Message message = ERR_LDIFIMPORT_CANNOT_READ_FILE.get(pathname);
          logError(message);
          badFileCount++;
        }
      }
      if (badFileCount > 0) return 1;
      importConfig = new LDIFImportConfig(fileList);
    }
    else
    {
      Random random;
      if (randomSeed.isPresent())
      {
        try
        {
          random = new Random(randomSeed.getIntValue());
        }
        catch (Exception e)
        {
          random = new Random();
        }
      }
      else
      {
        random = new Random();
      }

      String resourcePath = DirectoryServer.getInstanceRoot() + File.separator +
                            PATH_MAKELDIF_RESOURCE_DIR;
      TemplateFile tf = new TemplateFile(resourcePath, random);

      ArrayList<Message> warnings = new ArrayList<Message>();
      try
      {
        tf.parse(templateFile.getValue(), warnings);
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_PARSE_TEMPLATE_FILE.get(
            templateFile.getValue(), e.getMessage());
        logError(message);
        return 1;
      }

      importConfig = new LDIFImportConfig(tf);
    }



    // Create the LDIF import configuration to use when reading the LDIF.
    importConfig.setAppendToExistingData(append.isPresent());
    importConfig.setReplaceExistingEntries(replaceExisting.isPresent());
    importConfig.setCompressed(isCompressed.isPresent());
    importConfig.setClearBackend(clearBackend.isPresent());
    importConfig.setEncrypted(isEncrypted.isPresent());
    importConfig.setExcludeAttributes(excludeAttributes);
    importConfig.setExcludeBranches(excludeBranches);
    importConfig.setExcludeFilters(excludeFilters);
    importConfig.setIncludeAttributes(includeAttributes);
    importConfig.setIncludeBranches(includeBranches);
    importConfig.setIncludeFilters(includeFilters);
    importConfig.setValidateSchema(!skipSchemaValidation.isPresent());
    importConfig.setBufferSize(LDIF_BUFFER_SIZE);
    importConfig.setExcludeAllUserAttributes(
                                     excludeAllUserAttributes);
    importConfig.setExcludeAllOperationalAttributes(
                                     excludeAllOperationalAttributes);
    importConfig.setIncludeAllOpAttributes(
                                      includeAllOperationalAttributes);
    importConfig.setIncludeAllUserAttributes(includeAllUserAttributes);

    // FIXME -- Should this be conditional?
    importConfig.setInvokeImportPlugins(true);

    if (rejectFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior;
        if (overwrite.isPresent())
        {
          existingBehavior = ExistingFileBehavior.OVERWRITE;
        }
        else
        {
          existingBehavior = ExistingFileBehavior.APPEND;
        }

        importConfig.writeRejectedEntries(rejectFile.getValue(),
                                          existingBehavior);
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_OPEN_REJECTS_FILE.get(
            rejectFile.getValue(), getExceptionMessage(e));
        logError(message);
        return 1;
      }
    }

    if (skipFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior;
        if (overwrite.isPresent())
        {
          existingBehavior = ExistingFileBehavior.OVERWRITE;
        }
        else
        {
          existingBehavior = ExistingFileBehavior.APPEND;
        }

        importConfig.writeSkippedEntries(skipFile.getValue(),
                                          existingBehavior);
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFIMPORT_CANNOT_OPEN_SKIP_FILE.get(
            skipFile.getValue(), getExceptionMessage(e));
        logError(message);
        return 1;
      }
    }

    // Get the set of base DNs for the backend as an array.
    DN[] baseDNs = new DN[defaultIncludeBranches.size()];
    defaultIncludeBranches.toArray(baseDNs);


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
        return 1;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
      return 1;
    }


    // Launch the import.
    int retCode = 0;
    try
    {
      LDIFImportResult importResult = backend.importLDIF(importConfig);
      if (countRejects.isPresent())
      {
        if (importResult.getEntriesRejected() > Integer.MAX_VALUE)
        {
          retCode = Integer.MAX_VALUE;
        }
        else
        {
          retCode = (int) importResult.getEntriesRejected();
        }
      }
    }
    catch (DirectoryException de)
    {
      Message message =
          ERR_LDIFIMPORT_ERROR_DURING_IMPORT.get(de.getMessageObject());
      logError(message);
      retCode = 1;
    }
    catch (Exception e)
    {
      Message message =
          ERR_LDIFIMPORT_ERROR_DURING_IMPORT.get(getExceptionMessage(e));
      logError(message);
      retCode = 1;
    }


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
        retCode = 1;
      }
    }
    catch (Exception e)
    {
      Message message = WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
      retCode = 1;
    }


    // Clean up after the import by closing the import config.
    importConfig.close();
    return retCode;
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskId() {
    // NYI.
    return null;
  }
}

