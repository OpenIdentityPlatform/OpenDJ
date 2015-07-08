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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.ImportTask;
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
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
 * This program provides a utility that may be used to import the contents of an
 * LDIF file into a Directory Server backend.  This will be a process that is
 * intended to run separate from Directory Server and not internally within the
 * server process (e.g., via the tasks interface).
 */
public class ImportLDIF extends TaskTool {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
   * @param  args  The command-line arguments provided to this program.
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

  /** Define the command-line arguments that may be used with this program. */
  private BooleanArgument append;
  private BooleanArgument countRejects;
  private BooleanArgument displayUsage;
  private BooleanArgument isCompressed;
  private BooleanArgument isEncrypted;
  private BooleanArgument overwrite;
  private BooleanArgument quietMode;
  private BooleanArgument replaceExisting;
  private BooleanArgument skipSchemaValidation;
  private BooleanArgument clearBackend;
  private IntegerArgument randomSeed;
  private StringArgument  backendID;
  private StringArgument  configClass;
  private StringArgument  configFile;
  private StringArgument  excludeAttributeStrings;
  private StringArgument  excludeBranchStrings;
  private StringArgument  excludeFilterStrings;
  private StringArgument  includeAttributeStrings;
  private StringArgument  includeBranchStrings;
  private StringArgument  includeFilterStrings;
  private StringArgument  ldifFiles;
  private StringArgument  rejectFile;
  private StringArgument  skipFile;
  private StringArgument  templateFile;
  private BooleanArgument skipDNValidation;
  private IntegerArgument threadCount;
  private StringArgument  tmpDirectory;

  private int process(String[] args, boolean initializeServer,
                      OutputStream outStream, OutputStream errStream) {

    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // FIXME -- Need to add a mechanism for verifying the file signature.


    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.ImportLDIF",
                            INFO_LDIFIMPORT_TOOL_DESCRIPTION.get());
    argParser.setShortToolDescription(REF_SHORT_DESC_IMPORT_LDIF.get());

    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      createArguments(argParser);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

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
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }
    catch (ClientException ce)
    {
      // No need to display the usage since the problem comes with a provided
      // value.
      err.println(wrapText(ce.getMessageObject(), MAX_LINE_WIDTH));
      return 1;
    }


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
        LocalizableMessage message = ERR_LDIFIMPORT_CONFLICTING_OPTIONS.get(
                ldifFiles.getLongIdentifier(),
                templateFile.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    else if (! templateFile.isPresent())
    {
      LocalizableMessage message = ERR_LDIFIMPORT_MISSING_REQUIRED_ARGUMENT.get(
              ldifFiles.getLongIdentifier(),
              templateFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Make sure that either the "includeBranchStrings" argument or the
    // "backendID" argument was provided.
    if(!includeBranchStrings.isPresent() && !backendID.isPresent())
    {
      LocalizableMessage message = ERR_LDIFIMPORT_MISSING_BACKEND_ARGUMENT.get(
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
      LocalizableMessage message =
          ERR_LDIFIMPORT_COUNT_REJECTS_REQUIRES_OFFLINE
              .get(countRejects.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Don't write non-error messages to console if quite
    if (quietMode.isPresent()) {
      out = new PrintStream(NullOutputStream.instance());
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      checkVersion();
    }
    catch (InitializationException e)
    {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    return process(argParser, initializeServer, out, err);
  }

  private void createArguments(LDAPConnectionArgumentParser argParser) throws ArgumentException
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


      skipDNValidation =
           new BooleanArgument("skipDNValidation", null, "skipDNValidation",
                    INFO_LDIFIMPORT_DESCRIPTION_DN_VALIDATION.get());
      argParser.addArgument(skipDNValidation);


      threadCount = new IntegerArgument("threadCount", null, "threadCount",
              false, false, true,
              INFO_LDIFIMPORT_THREAD_COUNT_PLACEHOLDER.get(),
              0, null,
              true, 1, true, Integer.MAX_VALUE,
              INFO_LDIFIMPORT_DESCRIPTION_THREAD_COUNT.get());
      argParser.addArgument(threadCount);

      tmpDirectory =
           new StringArgument("tmpdirectory", null, "tmpdirectory", false,
                   false, true, INFO_LDIFIMPORT_TEMP_DIR_PLACEHOLDER.get(),
                   "import-tmp",
                    null, INFO_LDIFIMPORT_DESCRIPTION_TEMP_DIRECTORY.get());
      argParser.addArgument(tmpDirectory);


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


      displayUsage = CommonArguments.getShowUsage();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
  }

  /** {@inheritDoc} */
  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    // Required attributes
    addAttribute(attributes, ATTR_IMPORT_LDIF_FILE, ldifFiles.getValues());
    addAttribute(attributes, ATTR_IMPORT_TEMPLATE_FILE, templateFile.getValue());
    addAttribute(attributes, ATTR_IMPORT_RANDOM_SEED, randomSeed.getValue());
    addAttribute(attributes, ATTR_IMPORT_THREAD_COUNT, threadCount.getValue());

    // Optional attributes
    addAttribute2(attributes, ATTR_IMPORT_APPEND, append);
    addAttribute2(attributes, ATTR_IMPORT_REPLACE_EXISTING, replaceExisting);
    addAttribute2(attributes, ATTR_IMPORT_BACKEND_ID, backendID);
    addAttribute(attributes, ATTR_IMPORT_INCLUDE_ATTRIBUTE, includeAttributeStrings.getValues());
    addAttribute(attributes, ATTR_IMPORT_EXCLUDE_ATTRIBUTE, excludeAttributeStrings.getValues());
    addAttribute(attributes, ATTR_IMPORT_INCLUDE_FILTER, includeFilterStrings.getValues());
    addAttribute(attributes, ATTR_IMPORT_EXCLUDE_FILTER, excludeFilterStrings.getValues());
    addAttribute(attributes, ATTR_IMPORT_INCLUDE_BRANCH, includeBranchStrings.getValues());
    addAttribute(attributes, ATTR_IMPORT_EXCLUDE_BRANCH, excludeBranchStrings.getValues());
    addAttribute2(attributes, ATTR_IMPORT_REJECT_FILE, rejectFile);
    addAttribute2(attributes, ATTR_IMPORT_SKIP_FILE, skipFile);
    addAttribute2(attributes, ATTR_IMPORT_OVERWRITE, overwrite);
    addAttribute2(attributes, ATTR_IMPORT_SKIP_SCHEMA_VALIDATION, skipSchemaValidation);
    addAttribute2(attributes, ATTR_IMPORT_TMP_DIRECTORY, tmpDirectory);
    addAttribute2(attributes, ATTR_IMPORT_SKIP_DN_VALIDATION, skipDNValidation);
    addAttribute2(attributes, ATTR_IMPORT_IS_COMPRESSED, isCompressed);
    addAttribute2(attributes, ATTR_IMPORT_IS_ENCRYPTED, isEncrypted);
    addAttribute2(attributes, ATTR_IMPORT_CLEAR_BACKEND, clearBackend);
  }

  private void addAttribute(List<RawAttribute> attributes, String attrName, String value)
  {
    if (value != null)
    {
      attributes.add(new LDAPAttribute(attrName, toByteStrings(value)));
    }
  }

  private void addAttribute2(List<RawAttribute> attributes, String attrName, Argument arg)
  {
    final String value = arg.getValue();
    if (value != null && !value.equals(arg.getDefaultValue()))
    {
      attributes.add(new LDAPAttribute(attrName, toByteStrings(value)));
    }
  }

  private void addAttribute(List<RawAttribute> attributes, String attrName, List<String> attrValues)
  {
    if (attrValues != null && !attrValues.isEmpty())
    {
      attributes.add(new LDAPAttribute(attrName, toByteStrings(attrValues)));
    }
  }

  private ArrayList<ByteString> toByteStrings(String value)
  {
    final ArrayList<ByteString> values = new ArrayList<>(1);
    values.add(ByteString.valueOf(value));
    return values;
  }

  private ArrayList<ByteString> toByteStrings(List<String> attrValues)
  {
    final ArrayList<ByteString> values = new ArrayList<>(attrValues.size());
    for (String includeBranche : attrValues)
    {
      values.add(ByteString.valueOf(includeBranche));
    }
    return values;
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskObjectclass() {
    return "ds-task-import";
  }

  /** {@inheritDoc} */
  @Override
  public Class<?> getTaskClass() {
    return ImportTask.class;
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
        LocalizableMessage message = ERR_SERVER_BOOTSTRAP_ERROR.get(
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
        LocalizableMessage message = ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_LOAD_SCHEMA.get(getMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager(directoryServer.getServerContext());
        coreConfigManager.initializeCoreConfig();
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(getMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      if (! quietMode.isPresent())
      {
        try
        {
          ErrorLogPublisher errorLogPublisher =
              TextErrorLogPublisher.getToolStartupTextErrorPublisher(
                  new TextWriter.STREAM(out));
          ErrorLogger.getInstance().addLogPublisher(errorLogPublisher);
        }
        catch(Exception e)
        {
          err.println("Error installing the custom error logger: " +
              stackTraceToSingleLineString(e));
        }
      }

      // Initialize the root DNs.
      try
      {
        directoryServer.initializeRootDNConfigManager();
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_ROOTDN_MANAGER.get(getMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      // Initialize the plugin manager.
      try
      {
        HashSet<PluginType> pluginTypes = new HashSet<>(1);
        directoryServer.initializePlugins(pluginTypes);
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PLUGINS.get(getMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      // Initialize the subentry manager.
      try
      {
        directoryServer.initializeSubentryManager();
      }
      catch (InitializationException ie)
      {
        LocalizableMessage message = ERR_CANNOT_INITIALIZE_SUBENTRY_MANAGER.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      // Initialize all the password policy information.
      try
      {
        directoryServer.initializeAuthenticationPolicyComponents();
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PWPOLICY.get(getMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    // Make sure that the plugin initialization is performed.
    try
    {
      HashSet<PluginType> pluginTypes = new HashSet<>(1);
      pluginTypes.add(PluginType.LDIF_IMPORT);
      PluginConfigManager pluginConfigManager =
              DirectoryServer.getPluginConfigManager();
      pluginConfigManager.initializeUserPlugins(pluginTypes);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_LDIFIMPORT_CANNOT_INITIALIZE_PLUGINS.get(getMessage(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
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
      excludeAttributes = new HashSet<>();
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
      includeAttributes = new HashSet<>();
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
      excludeFilters = new ArrayList<>();
      for (String filterString : excludeFilterStrings.getValues())
      {
        try
        {
          excludeFilters.add(SearchFilter.createFilterFromString(filterString));
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER, filterString, de.getMessageObject());
          return 1;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER, filterString, getExceptionMessage(e));
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
          logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER, filterString, de.getMessageObject());
          return 1;
        }
        catch (Exception e)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER, filterString, getExceptionMessage(e));
          return 1;
        }
      }
    }


    // Get information about the backends defined in the server.  Iterate
    // through them, finding the one backend into which the LDIF should be
    // imported and finding backends with subordinate base DNs that should be
    // excluded from the import.
    Backend<?> backend = null;
    Set<DN> defaultIncludeBranches = null;
    Set<DN> excludeBranches = new HashSet<>();
    Set<DN> includeBranches = new HashSet<>();

    if (includeBranchStrings.isPresent())
    {
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

        includeBranches.add(includeBranch);
      }
    }

    ArrayList<Backend>     backendList = new ArrayList<>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<>();
    ArrayList<List<DN>> dnList = new ArrayList<>();
    int code = BackendToolUtils.getBackends(backendList, entryList, dnList);
    if (code != 0)
    {
      return code;
    }

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend<?> b = backendList.get(i);

      if(backendID.isPresent())
      {
        if (! backendID.getValue().equals(b.getBackendID()))
        {
          continue;
        }
      }
      else
      {
        if (!useBackend(includeBranches, dnList.get(i)))
        {
          continue;
        }
      }

      if (backend == null)
      {
        backend                = b;
        defaultIncludeBranches = new HashSet<>(dnList.get(i));
      }
      else
      {
        logger.error(ERR_LDIFIMPORT_MULTIPLE_BACKENDS_FOR_ID);
        return 1;
      }
    }

    if (backend == null)
    {
      logger.error(ERR_LDIFIMPORT_NO_BACKENDS_FOR_ID);
      return 1;
    }
    else if (!backend.supports(BackendOperation.LDIF_IMPORT))
    {
      logger.error(ERR_LDIFIMPORT_CANNOT_IMPORT, backendID.getValue());
      return 1;
    }

    for (List<DN> baseList : dnList)
    {
      for (DN baseDN : baseList)
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

    // Make sure that if the "backendID" argument was provided, no include base
    // was included, the
    // "clearBackend" argument was also provided if there are more then one
    // baseDNs for the backend being imported.

    if(backendID.isPresent() && !includeBranchStrings.isPresent() &&
       !append.isPresent() &&
        defaultIncludeBranches.size() > 1 &&
        !clearBackend.isPresent())
    {
      StringBuilder builder = join(backend.getBaseDNs(), " / ");
      LocalizableMessage message = ERR_LDIFIMPORT_MISSING_CLEAR_BACKEND.get(
              builder, clearBackend.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    for (String s : excludeBranchStrings.getValues())
    {
      DN excludeBranch;
      try
      {
        excludeBranch = DN.valueOf(s);
      }
      catch (DirectoryException de)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE, s, de.getMessageObject());
        return 1;
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE, s, getExceptionMessage(e));
        return 1;
      }

      excludeBranches.add(excludeBranch);
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
          logger.error(ERR_LDIFIMPORT_INVALID_INCLUDE_BASE, includeBranch, backendID.getValue());
          return 1;
        }
      }
    }


    // See if the data should be read from LDIF files or generated via MakeLDIF.
    LDIFImportConfig importConfig;
    if (ldifFiles.isPresent())
    {
      ArrayList<String> fileList = new ArrayList<>(ldifFiles.getValues());
      int badFileCount = 0;
      for (String pathname : fileList)
      {
        File f = new File(pathname);
        if (!f.canRead())
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_READ_FILE, pathname);
          badFileCount++;
        }
      }
      if (badFileCount > 0) return 1;
      importConfig = new LDIFImportConfig(fileList);
    }
    else
    {
      Random random = newRandom();

      String resourcePath = DirectoryServer.getInstanceRoot() + File.separator +
                            PATH_MAKELDIF_RESOURCE_DIR;
      TemplateFile tf = new TemplateFile(resourcePath, random);

      ArrayList<LocalizableMessage> warnings = new ArrayList<>();
      try
      {
        tf.parse(templateFile.getValue(), warnings);
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_TEMPLATE_FILE, templateFile.getValue(), e.getMessage());
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
      importConfig.setSkipDNValidation(skipDNValidation.isPresent());
      importConfig.setTmpDirectory(tmpDirectory.getValue());

      try
      {
          importConfig.setThreadCount(threadCount.getIntValue());
      }
      catch(Exception e)
      {
          logger.error(ERR_LDIFIMPORT_CANNOT_PARSE_THREAD_COUNT,
                  threadCount.getValue(), e.getMessage());
          return 1;
      }

    importConfig.setBufferSize(LDIF_BUFFER_SIZE);
    importConfig.setExcludeAllUserAttributes(excludeAllUserAttributes);
    importConfig.setExcludeAllOperationalAttributes(excludeAllOperationalAttributes);
    importConfig.setIncludeAllOpAttributes(includeAllOperationalAttributes);
    importConfig.setIncludeAllUserAttributes(includeAllUserAttributes);

    // FIXME -- Should this be conditional?
    importConfig.setInvokeImportPlugins(true);

    if (rejectFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior = overwrite.isPresent()
            ? ExistingFileBehavior.OVERWRITE
            : ExistingFileBehavior.APPEND;

        importConfig.writeRejectedEntries(rejectFile.getValue(),
                                          existingBehavior);
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_OPEN_REJECTS_FILE, rejectFile.getValue(), getExceptionMessage(e));
        return 1;
      }
    }

    if (skipFile != null)
    {
      try
      {
        ExistingFileBehavior existingBehavior = overwrite.isPresent()
            ? ExistingFileBehavior.OVERWRITE
            : ExistingFileBehavior.APPEND;

        importConfig.writeSkippedEntries(skipFile.getValue(),
                                          existingBehavior);
      }
      catch (Exception e)
      {
        logger.error(ERR_LDIFIMPORT_CANNOT_OPEN_SKIP_FILE, skipFile.getValue(), getExceptionMessage(e));
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
        logger.error(ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), failureReason);
        return 1;
      }
    }
    catch (Exception e)
    {
      logger.error(ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
      return 1;
    }


    // Launch the import.
    int retCode = 0;
    try
    {
      LDIFImportResult importResult =
          backend.importLDIF(importConfig, DirectoryServer.getInstance().getServerContext());
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
      retCode = 1;
    }
    catch (Exception e)
    {
      logger.error(ERR_LDIFIMPORT_ERROR_DURING_IMPORT, getExceptionMessage(e));
      retCode = 1;
    }


    // Release the exclusive lock on the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), failureReason);
        retCode = 1;
      }
    }
    catch (Exception e)
    {
      logger.warn(WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND, backend.getBackendID(), getExceptionMessage(e));
      retCode = 1;
    }


    // Clean up after the import by closing the import config.
    importConfig.close();
    return retCode;
  }

  private Object getMessage(Exception e)
  {
    try
    {
      throw e;
    }
    catch (ConfigException | InitializationException e2)
    {
      return e2.getMessage();
    }
    catch (Exception e2)
    {
      return getExceptionMessage(e2);
    }
  }

  private boolean useBackend(Set<DN> includeBranches, List<DN> dnlist)
  {
    for (DN baseDN : dnlist)
    {
      for (DN includeDN : includeBranches)
      {
        if (baseDN.isAncestorOf(includeDN))
        {
          return true;
        }
      }
    }
    return false;
  }

  private StringBuilder join(final DN[] baseDNs, final String separator)
  {
    final StringBuilder builder = new StringBuilder();
    builder.append(baseDNs[0]);
    for (int i = 1; i < baseDNs.length; i++)
    {
      builder.append(separator);
      builder.append(baseDNs[i]);
    }
    return builder;
  }

  private Random newRandom()
  {
    if (randomSeed.isPresent())
    {
      try
      {
        return new Random(randomSeed.getIntValue());
      }
      catch (Exception ignored)
      {
        // ignore
      }
    }
    return new Random();
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskId() {
    // NYI.
    return null;
  }
}
