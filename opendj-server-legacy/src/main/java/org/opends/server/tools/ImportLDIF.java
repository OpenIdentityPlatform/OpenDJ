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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
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
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.LocalBackend.BackendOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.InitializationBuilder;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.ImportTask;
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.cli.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
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

  /** The buffer size that should be used when reading data from LDIF. */
  private static final int LDIF_BUFFER_SIZE = 1048576;

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
  private BooleanArgument countRejects;
  private BooleanArgument isCompressed;
  private BooleanArgument isEncrypted;
  private BooleanArgument overwrite;
  private BooleanArgument quietMode;
  private BooleanArgument skipSchemaValidation;
  private BooleanArgument clearBackend;
  private IntegerArgument randomSeed;
  private StringArgument  backendID;
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
            createArgParser("org.opends.server.tools.ImportLDIF", INFO_LDIFIMPORT_TOOL_DESCRIPTION.get());
    argParser.setShortToolDescription(REF_SHORT_DESC_IMPORT_LDIF.get());

    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      createArguments(argParser);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }

    // Init the default values so that they can appear also on the usage.
    argParser.getArguments().initArgumentsWithConfiguration(argParser);

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      validateTaskArgs();
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }
    catch (ClientException ce)
    {
      // No need to display the usage since the problem comes with a provided value.
      printWrappedText(err, ce.getMessageObject());
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
        printWrappedText(err, conflictingArgsErrorMessage(ldifFiles, templateFile));
        return 1;
      }
    }
    else if (! templateFile.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_LDIFIMPORT_MISSING_REQUIRED_ARGUMENT.get(
          ldifFiles.getLongIdentifier(), templateFile.getLongIdentifier()));
      return 1;
    }

    // Make sure that either the "includeBranchStrings" argument or the
    // "backendID" argument was provided.
    if(!includeBranchStrings.isPresent() && !backendID.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_LDIFIMPORT_MISSING_BACKEND_ARGUMENT.get(
          includeBranchStrings.getLongIdentifier(), backendID.getLongIdentifier()));
      return 1;
    }

    // Count rejects option requires the ability to return result codes
    // which are potentially greater than 1. This is not supported by
    // the task framework.
    if (countRejects.isPresent() && !runOffline())
    {
      argParser.displayMessageAndUsageReference(err, ERR_LDIFIMPORT_COUNT_REJECTS_REQUIRES_OFFLINE.get(
          countRejects.getLongIdentifier()));
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
      printWrappedText(err, e.getMessage());
      return 1;
    }

    return process(argParser, initializeServer, out, err);
  }

  private void createArguments(LDAPConnectionArgumentParser argParser) throws ArgumentException
  {
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('f')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      ldifFiles =
              StringArgument.builder(OPTION_LONG_LDIF_FILE)
                      .shortIdentifier(OPTION_SHORT_LDIF_FILE)
                      .description(INFO_LDIFIMPORT_DESCRIPTION_LDIF_FILE.get())
                      .multiValued()
                      .valuePlaceholder(INFO_LDIFFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      templateFile =
              StringArgument.builder("templateFile")
                      .shortIdentifier('A')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_TEMPLATE_FILE.get())
                      .valuePlaceholder(INFO_TEMPLATE_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backendID =
              StringArgument.builder("backendID")
                      .shortIdentifier('n')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_BACKEND_ID.get())
                      .valuePlaceholder(INFO_BACKENDNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      clearBackend =
              BooleanArgument.builder("clearBackend")
                      .shortIdentifier('F')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_CLEAR_BACKEND.get())
                      .buildAndAddToParser(argParser);
      includeBranchStrings =
              StringArgument.builder("includeBranch")
                      .shortIdentifier('b')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_INCLUDE_BRANCH.get())
                      .multiValued()
                      .valuePlaceholder(INFO_BRANCH_DN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      excludeBranchStrings =
              StringArgument.builder("excludeBranch")
                      .shortIdentifier('B')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_EXCLUDE_BRANCH.get())
                      .multiValued()
                      .valuePlaceholder(INFO_BRANCH_DN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      includeAttributeStrings =
              StringArgument.builder("includeAttribute")
                      .shortIdentifier('i')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_INCLUDE_ATTRIBUTE.get())
                      .multiValued()
                      .valuePlaceholder(INFO_ATTRIBUTE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      excludeAttributeStrings =
              StringArgument.builder("excludeAttribute")
                      .shortIdentifier('e')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE.get())
                      .multiValued()
                      .valuePlaceholder(INFO_ATTRIBUTE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      includeFilterStrings =
              StringArgument.builder("includeFilter")
                      .shortIdentifier('I')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_INCLUDE_FILTER.get())
                      .multiValued()
                      .valuePlaceholder(INFO_FILTER_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      excludeFilterStrings =
              StringArgument.builder("excludeFilter")
                      .shortIdentifier('E')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_EXCLUDE_FILTER.get())
                      .multiValued()
                      .valuePlaceholder(INFO_FILTER_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      rejectFile =
              StringArgument.builder("rejectFile")
                      .shortIdentifier('R')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_REJECT_FILE.get())
                      .valuePlaceholder(INFO_REJECT_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      skipFile =
              StringArgument.builder("skipFile")
                      .description(INFO_LDIFIMPORT_DESCRIPTION_SKIP_FILE.get())
                      .valuePlaceholder(INFO_SKIP_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      overwrite =
              BooleanArgument.builder("overwrite")
                      .shortIdentifier('O')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_OVERWRITE.get())
                      .buildAndAddToParser(argParser);
      randomSeed =
              IntegerArgument.builder(OPTION_LONG_RANDOM_SEED)
                      .shortIdentifier(OPTION_SHORT_RANDOM_SEED)
                      .description(INFO_LDIFIMPORT_DESCRIPTION_RANDOM_SEED.get())
                      .defaultValue(0)
                      .valuePlaceholder(INFO_SEED_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      skipSchemaValidation =
              BooleanArgument.builder("skipSchemaValidation")
                      .shortIdentifier('S')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_SKIP_SCHEMA_VALIDATION.get())
                      .buildAndAddToParser(argParser);
      threadCount =
              IntegerArgument.builder("threadCount")
                      .description(INFO_LDIFIMPORT_DESCRIPTION_THREAD_COUNT.get())
                      .lowerBound(1)
                      .defaultValue(0)
                      .valuePlaceholder(INFO_LDIFIMPORT_THREAD_COUNT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      tmpDirectory =
              StringArgument.builder("tmpdirectory")
                      .description(INFO_LDIFIMPORT_DESCRIPTION_TEMP_DIRECTORY.get())
                      .defaultValue("import-tmp")
                      .valuePlaceholder(INFO_LDIFIMPORT_TEMP_DIR_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      countRejects =
              BooleanArgument.builder("countRejects")
                      .description(INFO_LDIFIMPORT_DESCRIPTION_COUNT_REJECTS.get())
                      .buildAndAddToParser(argParser);
      isCompressed =
              BooleanArgument.builder("isCompressed")
                      .shortIdentifier('c')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_IS_COMPRESSED.get())
                      .buildAndAddToParser(argParser);
      isEncrypted =
              BooleanArgument.builder("isEncrypted")
                      .shortIdentifier('y')
                      .description(INFO_LDIFIMPORT_DESCRIPTION_IS_ENCRYPTED.get())
                      .hidden() //See issue #27
                      .buildAndAddToParser(argParser);
      quietMode =
              BooleanArgument.builder(OPTION_LONG_QUIET)
                      .shortIdentifier(OPTION_SHORT_QUIET)
                      .description(INFO_LDIFIMPORT_DESCRIPTION_QUIET.get())
                      .buildAndAddToParser(argParser);

    final BooleanArgument displayUsage = showUsageArgument();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
  }

  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    // Required attributes
    addAttribute(attributes, ATTR_IMPORT_LDIF_FILE, ldifFiles.getValues());
    addAttribute(attributes, ATTR_IMPORT_TEMPLATE_FILE, templateFile.getValue());
    addAttribute(attributes, ATTR_IMPORT_RANDOM_SEED, randomSeed.getValue());
    addAttribute(attributes, ATTR_IMPORT_THREAD_COUNT, threadCount.getValue());

    // Optional attributes
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
    addAttribute2(attributes, ATTR_IMPORT_IS_COMPRESSED, isCompressed);
    addAttribute2(attributes, ATTR_IMPORT_IS_ENCRYPTED, isEncrypted);
    addAttribute2(attributes, ATTR_IMPORT_CLEAR_BACKEND, clearBackend);
  }

  private void addAttribute(List<RawAttribute> attributes, String attrName, String value)
  {
    if (value != null)
    {
      attributes.add(new LDAPAttribute(attrName, value));
    }
  }

  private void addAttribute2(List<RawAttribute> attributes, String attrName, Argument arg)
  {
    final String value = arg.getValue();
    if (value != null && !value.equals(arg.getDefaultValue()))
    {
      attributes.add(new LDAPAttribute(attrName, value));
    }
  }

  private void addAttribute(List<RawAttribute> attributes, String attrName, List<String> attrValues)
  {
    if (attrValues != null && !attrValues.isEmpty())
    {
      attributes.add(new LDAPAttribute(attrName, attrValues));
    }
  }

  @Override
  public String getTaskObjectclass() {
    return "ds-task-import";
  }

  @Override
  public Class<?> getTaskClass() {
    return ImportTask.class;
  }

  @Override
  protected int processLocal(boolean initializeServer, PrintStream out, PrintStream err) {
    if (initializeServer)
    {
      try
      {
        InitializationBuilder initBuilder = new DirectoryServer.InitializationBuilder(configFile.getValue())
            .requireCryptoServices()
            .requireUserPlugins(PluginType.LDIF_IMPORT);
        if (!quietMode.isPresent())
        {
          initBuilder.requireErrorAndDebugLogPublisher(out, err);
        }
        initBuilder.initialize();
      }
      catch (InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(e.getLocalizedMessage()));
        return 1;
      }
    }

    // See if there were any user-defined sets of include/exclude attributes or
    // filters.  If so, then process them.
    HashSet<AttributeType> excludeAttributes;
    boolean excludeAllUserAttributes = false;
    boolean excludeAllOperationalAttributes = false;
    Schema schema = DirectoryServer.getInstance().getServerContext().getSchema();
    if (excludeAttributeStrings == null)
    {
      excludeAttributes = null;
    }
    else
    {
      excludeAttributes = new HashSet<>();
      for (String attrName : excludeAttributeStrings.getValues())
      {
        String lowerName = attrName.toLowerCase();
        if (lowerName.equals("*"))
        {
          excludeAllUserAttributes = true;
        }
        else if (lowerName.equals("+"))
        {
          excludeAllOperationalAttributes = true;
        }
        else
        {
          excludeAttributes.add(schema.getAttributeType(attrName));
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
        String lowerName = attrName.toLowerCase();
        if (lowerName.equals("*"))
        {
          includeAllUserAttributes = true;
        }
        else if (lowerName.equals("+"))
        {
          includeAllOperationalAttributes = true;
        }
        else
        {
          includeAttributes.add(schema.getAttributeType(attrName));
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
    LocalBackend<?> backend = null;
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
        catch (Exception e)
        {
          logger.error(ERR_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE, s, getExceptionMessage(e));
          return 1;
        }

        includeBranches.add(includeBranch);
      }
    }

    ArrayList<LocalBackend<?>> backendList = new ArrayList<>();
    ArrayList<BackendCfg> entryList = new ArrayList<>();
    ArrayList<List<DN>> dnList = new ArrayList<>();
    int code = BackendToolUtils.getBackends(backendList, entryList, dnList);
    if (code != 0)
    {
      return code;
    }

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      LocalBackend<?> b = backendList.get(i);

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
          if (!baseDN.equals(importBase) && baseDN.isSubordinateOrEqualTo(importBase))
          {
            excludeBranches.add(baseDN);
            break;
          }
        }
      }
    }

    for (String s : excludeBranchStrings.getValues())
    {
      DN excludeBranch;
      try
      {
        excludeBranch = DN.valueOf(s);
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
        if (! LocalBackend.handlesEntry(includeBranch, defaultIncludeBranches,
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
      if (badFileCount > 0)
      {
        return 1;
      }
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
      if (de.getResultCode().equals(DirectoryServer.getCoreConfigManager().getServerErrorResultCode()))
      {
        logger.error(ERR_LDIFIMPORT_ERROR_DURING_IMPORT.get(de.getMessageObject()));
      }
      else
      {
        logger.error(de.getMessageObject());
      }
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

  @Override
  protected void cleanup()
  {
    DirectoryServer.getInstance().getServerContext().getBackendConfigManager().shutdownLocalBackends();
  }

  private boolean useBackend(Set<DN> includeBranches, List<DN> dnlist)
  {
    for (DN baseDN : dnlist)
    {
      for (DN includeDN : includeBranches)
      {
        if (baseDN.isSuperiorOrEqualTo(includeDN))
        {
          return true;
        }
      }
    }
    return false;
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

  @Override
  public String getTaskId() {
    // NYI.
    return null;
  }
}
