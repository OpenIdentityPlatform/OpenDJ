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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opends.server.api.Backend;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import static org.opends.server.config.ConfigConstants.*;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.ThreadFilterTextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.LDAPConnectionArgumentParser;

import org.opends.messages.Message;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.tasks.ExportTask;


/**
 * This program provides a utility that may be used to export the contents of a
 * Directory Server backend to an LDIF file.  Depending on the arguments given,
 * this program will either perform the export directly as a process that
 * runs separate from Directory Server; or by scheduling a task to perform the
 * action within the Directory Server via the tasks interface.
 */
public class ExportLDIF extends TaskTool {

  private static ErrorLogPublisher errorLogPublisher = null;

  /**
   * The main method for ExportLDIF tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainExportLDIF(args, true, System.out, System.err);

    if(errorLogPublisher != null)
    {
      removeErrorLogPublisher(errorLogPublisher);
    }

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

  // Define the command-line arguments that may be used with this program.
  private BooleanArgument appendToLDIF            = null;
  private BooleanArgument compressLDIF            = null;
  private BooleanArgument displayUsage            = null;
  private BooleanArgument encryptLDIF             = null;
  private BooleanArgument excludeOperationalAttrs = null;
  private BooleanArgument signHash                = null;
  private IntegerArgument wrapColumn              = null;
  private StringArgument  backendID               = null;
  private StringArgument  configClass             = null;
  private StringArgument  configFile              = null;
  private StringArgument  excludeAttributeStrings = null;
  private StringArgument  excludeBranchStrings    = null;
  private StringArgument  excludeFilterStrings    = null;
  private StringArgument  includeAttributeStrings = null;
  private StringArgument  includeBranchStrings    = null;
  private StringArgument  includeFilterStrings    = null;
  private StringArgument  ldifFile                = null;

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

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.ExportLDIF",
                            INFO_LDIFEXPORT_TOOL_DESCRIPTION.get());


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                              OPTION_LONG_CONFIG_CLASS, true, false,
                              true, OPTION_VALUE_CONFIG_CLASS,
                              ConfigFileHandler.class.getName(), null,
                              INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      ldifFile =
           new StringArgument("ldiffile", OPTION_SHORT_LDIF_FILE,
                              OPTION_LONG_LDIF_FILE,true, false, true,
                              OPTION_VALUE_LDIF_FILE, null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_LDIF_FILE.get());
      argParser.addArgument(ldifFile);


      appendToLDIF = new BooleanArgument(
                   "appendldif", 'a', "appendToLDIF",
                   INFO_LDIFEXPORT_DESCRIPTION_APPEND_TO_LDIF.get());
      argParser.addArgument(appendToLDIF);


      backendID =
           new StringArgument("backendid", 'n', "backendID", true, false, true,
                              "{backendID}", null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_BACKEND_ID.get());
      argParser.addArgument(backendID);


      includeBranchStrings =
           new StringArgument("includebranch", 'b', "includeBranch", false,
                              true, true, "{branchDN}", null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_INCLUDE_BRANCH.get());
      argParser.addArgument(includeBranchStrings);


      excludeBranchStrings =
           new StringArgument("excludebranch", 'B', "excludeBranch", false,
                              true, true, "{branchDN}", null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_BRANCH.get());
      argParser.addArgument(excludeBranchStrings);


      includeAttributeStrings =
           new StringArgument(
                   "includeattribute", 'i', "includeAttribute",
                   false, true, true, "{attribute}", null, null,
                   INFO_LDIFEXPORT_DESCRIPTION_INCLUDE_ATTRIBUTE.get());
      argParser.addArgument(includeAttributeStrings);


      excludeAttributeStrings =
           new StringArgument(
                   "excludeattribute", 'e', "excludeAttribute",
                   false, true, true, "{attribute}", null, null,
                   INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE.get());
      argParser.addArgument(excludeAttributeStrings);


      includeFilterStrings =
           new StringArgument("includefilter", 'I', "includeFilter",
                              false, true, true, "{filter}", null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_INCLUDE_FILTER.get());
      argParser.addArgument(includeFilterStrings);


      excludeFilterStrings =
           new StringArgument("excludefilter", 'E', "excludeFilter",
                              false, true, true, "{filter}", null, null,
                              INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_FILTER.get());
      argParser.addArgument(excludeFilterStrings);


      excludeOperationalAttrs =
           new BooleanArgument("excludeoperational", 'O', "excludeOperational",
                    INFO_LDIFEXPORT_DESCRIPTION_EXCLUDE_OPERATIONAL.get());
      argParser.addArgument(excludeOperationalAttrs);


      wrapColumn =
           new IntegerArgument("wrapcolumn", null, "wrapColumn", false, false,
                               true, "{wrapColumn}", 0, null, true, 0, false, 0,
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


      displayUsage =
           new BooleanArgument("help", OPTION_SHORT_HELP,
                               OPTION_LONG_HELP,
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


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
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
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>(1);
    values.add(new ASN1OctetString(ldifFile.getValue()));
    attributes.add(new LDAPAttribute(ATTR_TASK_EXPORT_LDIF_FILE, values));

    values = new ArrayList<ASN1OctetString>(1);
    values.add(new ASN1OctetString(backendID.getValue()));
    attributes.add(new LDAPAttribute(ATTR_TASK_EXPORT_BACKEND_ID, values));

    //
    // Optional attributes
    //
    if (appendToLDIF.getValue() != null &&
            !appendToLDIF.getValue().equals(appendToLDIF.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(appendToLDIF.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_APPEND_TO_LDIF, values));
    }

    if (compressLDIF.getValue() != null &&
            !compressLDIF.getValue().equals(compressLDIF.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(compressLDIF.getValue()));
      attributes.add(new LDAPAttribute(ATTR_TASK_EXPORT_COMPRESS_LDIF, values));
    }

    if (encryptLDIF.getValue() != null &&
            !encryptLDIF.getValue().equals(encryptLDIF.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(encryptLDIF.getValue()));
      attributes.add(new LDAPAttribute(ATTR_TASK_EXPORT_ENCRYPT_LDIF, values));
    }

    if (signHash.getValue() != null &&
            !signHash.getValue().equals(signHash.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(signHash.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_SIGN_HASH, values));
    }

    List<String> includeAttributes = includeAttributeStrings.getValues();
    if (includeAttributes != null && includeAttributes.size() > 0) {
      values = new ArrayList<ASN1OctetString>(includeAttributes.size());
      for (String includeAttribute : includeAttributes) {
        values.add(new ASN1OctetString(includeAttribute));
      }
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_INCLUDE_ATTRIBUTE, values));
    }

    List<String> excludeAttributes = excludeAttributeStrings.getValues();
    if (excludeAttributes != null && excludeAttributes.size() > 0) {
      values = new ArrayList<ASN1OctetString>(excludeAttributes.size());
      for (String excludeAttribute : excludeAttributes) {
        values.add(new ASN1OctetString(excludeAttribute));
      }
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_EXCLUDE_ATTRIBUTE, values));
    }

    List<String> includeFilters = includeFilterStrings.getValues();
    if (includeFilters != null && includeFilters.size() > 0) {
      values = new ArrayList<ASN1OctetString>(includeFilters.size());
      for (String includeFilter : includeFilters) {
        values.add(new ASN1OctetString(includeFilter));
      }
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_INCLUDE_FILTER, values));
    }

    List<String> excludeFilters = excludeFilterStrings.getValues();
    if (excludeFilters != null && excludeFilters.size() > 0) {
      values = new ArrayList<ASN1OctetString>(excludeFilters.size());
      for (String excludeFilter : excludeFilters) {
        values.add(new ASN1OctetString(excludeFilter));
      }
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_EXCLUDE_FILTER, values));
    }

    List<String> includeBranches = includeBranchStrings.getValues();
    if (includeBranches != null && includeBranches.size() > 0) {
      values = new ArrayList<ASN1OctetString>(includeBranches.size());
      for (String includeBranche : includeBranches) {
        values.add(new ASN1OctetString(includeBranche));
      }
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_INCLUDE_BRANCH, values));
    }

    List<String> excludeBranches = excludeBranchStrings.getValues();
    if (excludeBranches != null && excludeBranches.size() > 0) {
      values = new ArrayList<ASN1OctetString>(excludeBranches.size());
      for (String excludeBranche : excludeBranches) {
        values.add(new ASN1OctetString(excludeBranche));
      }
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_EXCLUDE_BRANCH, values));
    }

    if (wrapColumn.getValue() != null &&
            !wrapColumn.getValue().equals(wrapColumn.getDefaultValue())) {
      values = new ArrayList<ASN1OctetString>(1);
      values.add(new ASN1OctetString(wrapColumn.getValue()));
      attributes.add(
              new LDAPAttribute(ATTR_TASK_EXPORT_WRAP_COLUMN, values));
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getTaskObjectclass() {
    return "ds-task-export";
  }

  /**
   * {@inheritDoc}
   */
  public Class getTaskClass() {
    return ExportTask.class;
  }

  /**
   * {@inheritDoc}
   */
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
        Message message =
                ERR_SERVER_BOOTSTRAP_ERROR.get(getExceptionMessage(e));
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
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getExceptionMessage(e));
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
        Message message =
                ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                        getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // FIXME -- Install a custom logger to capture information about the state
      // of the export.
      try
      {
        errorLogPublisher =
            new ThreadFilterTextErrorLogPublisher(Thread.currentThread(),
                                                  new TextWriter.STREAM(out));
        addErrorLogPublisher(errorLogPublisher);

      }
      catch(Exception e)
      {
        err.println("Error installing the custom error logger: " +
                    stackTraceToSingleLineString(e));
      }



      // Make sure that the Directory Server plugin initialization is performed.
      try
      {
        HashSet<PluginType> pluginTypes = new HashSet<PluginType>(1);
        pluginTypes.add(PluginType.LDIF_EXPORT);
        directoryServer.initializePlugins(pluginTypes);
      }
      catch (ConfigException ce)
      {
        Message message =
                ERR_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        Message message =
                ERR_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        Message message =
                ERR_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS.get(
                        getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }


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
      for (String attrName : excludeAttributeStrings.getValues())
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
      includeAttributes =new HashSet<AttributeType>();
      for (String attrName : includeAttributeStrings.getValues())
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
      for (String filterString : excludeFilterStrings.getValues())
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
          return 1;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER.get(
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
          Message message = ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
              filterString, de.getMessageObject());
          logError(message);
          return 1;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER.get(
              filterString, getExceptionMessage(e));
          logError(message);
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

    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<BackendCfg>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
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
        Message message =
            ERR_LDIFEXPORT_MULTIPLE_BACKENDS_FOR_ID.get(backendID.getValue());
        logError(message);
        return 1;
      }
    }

    if (backend == null)
    {
      Message message =
          ERR_LDIFEXPORT_NO_BACKENDS_FOR_ID.get(backendID.getValue());
      logError(message);
      return 1;
    }
    else if (! backend.supportsLDIFExport())
    {
      Message message =
          ERR_LDIFEXPORT_CANNOT_EXPORT_BACKEND.get(backendID.getValue());
      logError(message);
      return 1;
    }

    if (excludeBranchStrings.isPresent())
    {
      excludeBranches = new ArrayList<DN>();
      for (String s : excludeBranchStrings.getValues())
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
          return 1;
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE.get(
              s, getExceptionMessage(e));
          logError(message);
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

        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          Message message =
              ERR_LDIFEXPORT_INVALID_INCLUDE_BASE.get(s, backendID.getValue());
          logError(message);
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
                      (! excludeOperationalAttrs.isPresent()));

    // FIXME -- Should this be conditional?
    exportConfig.setInvokeExportPlugins(true);

    try
    {
      exportConfig.setWrapColumn(wrapColumn.getIntValue());
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_LDIFEXPORT_CANNOT_DECODE_WRAP_COLUMN_AS_INTEGER.get(
          wrapColumn.getValue());
      logError(message);
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
        Message message = ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        logError(message);
        return 0;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
      return 0;
    }


    // Launch the export.
    try
    {
      backend.exportLDIF(exportConfig);
    }
    catch (DirectoryException de)
    {
      Message message =
          ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(de.getMessageObject());
      logError(message);
    }
    catch (Exception e)
    {
      Message message =
          ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(getExceptionMessage(e));
      logError(message);
    }


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
      }
    }
    catch (Exception e)
    {
      Message message = WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
    }


    // Clean up after the export by closing the export config.
    exportConfig.close();
    return 0;
  }
}

