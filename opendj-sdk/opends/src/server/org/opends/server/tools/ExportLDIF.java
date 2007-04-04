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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opends.server.api.Backend;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.StartupErrorLogger;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



/**
 * This program provides a utility that may be used to export the contents of a
 * Directory Server backend to an LDIF file.  This will be a process that is
 * intended to run separate from Directory Server and not internally within the
 * server process (e.g., via the tasks interface).
 */
public class ExportLDIF
{
  /**
   * The main method for ExportLDIF tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainExportLDIF(args);

    if(retCode != 0)
    {
      System.exit(retCode);
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
    return mainExportLDIF(args, true);
  }

  /**
   * Processes the command-line arguments and invokes the export process.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   *
   * @return The error code.
   */
  public static int mainExportLDIF(String[] args, boolean initializeServer)
  {
    // Define the command-line arguments that may be used with this program.
    BooleanArgument appendToLDIF            = null;
    BooleanArgument compressLDIF            = null;
    BooleanArgument displayUsage            = null;
    BooleanArgument encryptLDIF             = null;
    BooleanArgument excludeOperationalAttrs = null;
    BooleanArgument signHash                = null;
    IntegerArgument wrapColumn              = null;
    StringArgument  backendID               = null;
    StringArgument  configClass             = null;
    StringArgument  configFile              = null;
    StringArgument  excludeAttributeStrings = null;
    StringArgument  excludeBranchStrings    = null;
    StringArgument  excludeFilterStrings    = null;
    StringArgument  includeAttributeStrings = null;
    StringArgument  includeBranchStrings    = null;
    StringArgument  includeFilterStrings    = null;
    StringArgument  ldifFile                = null;


    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_LDIFEXPORT_TOOL_DESCRIPTION);
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.ExportLDIF",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                              OPTION_LONG_CONFIG_CLASS, true, false,
                              true, OPTION_VALUE_CONFIG_CLASS,
                              ConfigFileHandler.class.getName(), null,
                              MSGID_LDIFEXPORT_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      ldifFile =
           new StringArgument("ldiffile", OPTION_SHORT_LDIF_FILE,
                              OPTION_LONG_LDIF_FILE,true, false, true,
                              OPTION_VALUE_LDIF_FILE, null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_LDIF_FILE);
      argParser.addArgument(ldifFile);


      appendToLDIF =
           new BooleanArgument("appendldif", 'a', "appendToLDIF",
                               MSGID_LDIFEXPORT_DESCRIPTION_APPEND_TO_LDIF);
      argParser.addArgument(appendToLDIF);


      backendID =
           new StringArgument("backendid", 'n', "backendID", true, false, true,
                              "{backendID}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_BACKEND_ID);
      argParser.addArgument(backendID);


      includeBranchStrings =
           new StringArgument("includebranch", 'b', "includeBranch", false,
                              true, true, "{branchDN}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_BRANCH);
      argParser.addArgument(includeBranchStrings);


      excludeBranchStrings =
           new StringArgument("excludebranch", 'B', "excludeBranch", false,
                              true, true, "{branchDN}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_BRANCH);
      argParser.addArgument(excludeBranchStrings);


      includeAttributeStrings =
           new StringArgument("includeattribute", 'i', "includeAttribute",
                              false, true, true, "{attribute}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_ATTRIBUTE);
      argParser.addArgument(includeAttributeStrings);


      excludeAttributeStrings =
           new StringArgument("excludeattribute", 'e', "excludeAttribute",
                              false, true, true, "{attribute}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE);
      argParser.addArgument(excludeAttributeStrings);


      includeFilterStrings =
           new StringArgument("includefilter", 'I', "includeFilter",
                              false, true, true, "{filter}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_FILTER);
      argParser.addArgument(includeFilterStrings);


      excludeFilterStrings =
           new StringArgument("excludefilter", 'E', "excludeFilter",
                              false, true, true, "{filter}", null, null,
                              MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_FILTER);
      argParser.addArgument(excludeFilterStrings);


      excludeOperationalAttrs =
           new BooleanArgument("excludeoperational", 'O', "excludeOperational",
                    MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_OPERATIONAL);
      argParser.addArgument(excludeOperationalAttrs);


      wrapColumn =
           new IntegerArgument("wrapcolumn", 'w', "wrapColumn", false, false,
                               true, "{wrapColumn}", 0, null, true, 0, false, 0,
                               MSGID_LDIFEXPORT_DESCRIPTION_WRAP_COLUMN);
      argParser.addArgument(wrapColumn);


      compressLDIF =
           new BooleanArgument("compressldif", OPTION_SHORT_COMPRESS,
                               OPTION_LONG_COMPRESS,
                               MSGID_LDIFEXPORT_DESCRIPTION_COMPRESS_LDIF);
      argParser.addArgument(compressLDIF);


      encryptLDIF =
           new BooleanArgument("encryptldif", 'y', "encryptLDIF",
                               MSGID_LDIFEXPORT_DESCRIPTION_ENCRYPT_LDIF);
      argParser.addArgument(encryptLDIF);


      signHash =
           new BooleanArgument("signhash", 's', "signHash",
                               MSGID_LDIFEXPORT_DESCRIPTION_SIGN_HASH);
      argParser.addArgument(signHash);


      displayUsage =
           new BooleanArgument("help", OPTION_SHORT_HELP,
                               OPTION_LONG_HELP,
                               MSGID_LDIFEXPORT_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_LDIFEXPORT_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage information, then print it and exit.
    if (argParser.usageDisplayed())
    {
      return 0;
    }


    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    if (initializeServer)
    {
      try
      {
        directoryServer.bootstrapClient();
        directoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_SERVER_BOOTSTRAP_ERROR;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
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
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CORE_CONFIG;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }


      // FIXME -- Install a custom logger to capture information about the state
      // of the export.
      StartupErrorLogger startupLogger = new StartupErrorLogger();
      startupLogger.initializeErrorLogger(null);
      addErrorLogger(startupLogger);


      // Make sure that the Directory Server plugin initialization is performed.
      try
      {
        HashSet<PluginType> pluginTypes = new HashSet<PluginType>(1);
        pluginTypes.add(PluginType.LDIF_EXPORT);
        directoryServer.initializePlugins(pluginTypes);
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS;
        String message = getMessage(msgID, ce.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS;
        String message = getMessage(msgID, ie.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
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
          int    msgID   = MSGID_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER;
          String message = getMessage(msgID, filterString,
                                      de.getErrorMessage());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return 1;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER;
          String message = getMessage(msgID, filterString,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
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
          int    msgID   = MSGID_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER;
          String message = getMessage(msgID, filterString,
                                      de.getErrorMessage());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return 1;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER;
          String message = getMessage(msgID, filterString,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return 1;
        }
      }
    }


    // Get information about the backends defined in the server.  Iterate
    // through them, finding the one backend that should be used for the export,
    // and also finding backends with subordinate base DNs that should be
    // excluded from the export.
    Backend       backend                = null;
    ConfigEntry   configEntry            = null;
    List<DN>      baseDNList             = null;
    List<DN>      defaultIncludeBranches = null;
    ArrayList<DN> excludeBranches        = null;

    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<ConfigEntry> entryList   = new ArrayList<ConfigEntry>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
    getBackends(backendList, entryList, dnList);

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
        configEntry            = entryList.get(i);
        baseDNList             = dnList.get(i);
        defaultIncludeBranches = dnList.get(i);
      }
      else
      {
        int    msgID   = MSGID_LDIFEXPORT_MULTIPLE_BACKENDS_FOR_ID;
        String message = getMessage(msgID, backendID.getValue());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return 1;
      }
    }

    if (backend == null)
    {
      int    msgID   = MSGID_LDIFEXPORT_NO_BACKENDS_FOR_ID;
      String message = getMessage(msgID, backendID.getValue());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }
    else if (! backend.supportsLDIFExport())
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_EXPORT_BACKEND;
      String message = getMessage(msgID, backendID.getValue());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }

    if (excludeBranchStrings.isPresent())
    {
      excludeBranches = new ArrayList<DN>();
      for (String s : excludeBranchStrings.getValues())
      {
        DN excludeBranch = null;
        try
        {
          excludeBranch = DN.decode(s);
        }
        catch (DirectoryException de)
        {
          int    msgID   = MSGID_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE;
          String message = getMessage(msgID, s, de.getErrorMessage());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return 1;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE;
          String message = getMessage(msgID, s,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
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
        DN includeBranch = null;
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
          return 1;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE;
          String message = getMessage(msgID, s,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return 1;
        }

        if (! Backend.handlesEntry(includeBranch, defaultIncludeBranches,
                                   excludeBranches))
        {
          int    msgID   = MSGID_LDIFEXPORT_INVALID_INCLUDE_BASE;
          String message = getMessage(msgID, s, backendID.getValue());
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
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
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_DECODE_WRAP_COLUMN_AS_INTEGER;
      String message = getMessage(msgID, wrapColumn.getValue());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return 0;
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return 0;
    }


    // Launch the export.
    try
    {
      backend.exportLDIF(configEntry, baseDNs, exportConfig);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_LDIFEXPORT_ERROR_DURING_EXPORT;
      String message = getMessage(msgID, de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFEXPORT_ERROR_DURING_EXPORT;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    // Release the shared lock on the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_UNLOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_UNLOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }


    // Clean up after the export by closing the export config.
    exportConfig.close();
    return 0;
  }



  /**
   * Retrieves information about the backends defined in the Directory Server
   * configuration.
   *
   * @param  backendList  A list into which instantiated (but not initialized)
   *                      backend instances will be placed.
   * @param  entryList    A list into which the config entries associated with
   *                      the backends will be placed.
   * @param  dnList       A list into which the set of base DNs for each backend
   *                      will be placed.
   */
  private static void getBackends(ArrayList<Backend> backendList,
                                  ArrayList<ConfigEntry> entryList,
                                  ArrayList<List<DN>> dnList)
  {
    // Get the base entry for all backend configuration.
    DN backendBaseDN = null;
    try
    {
      backendBaseDN = DN.decode(DN_BACKEND_BASE);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE, de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }

    ConfigEntry baseEntry = null;
    try
    {
      baseEntry = DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE, ce.getMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }


    // Iterate through the immediate children, attempting to parse them as
    // backends.
    for (ConfigEntry configEntry : baseEntry.getChildren().values())
    {
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
        StringConfigAttribute idStub =
             new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID),
                                       true, false, true);
        StringConfigAttribute idAttr =
             (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
        if (idAttr == null)
        {
          continue;
        }
        else
        {
          backendID = idAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }


      // Get the backend class name attribute from the entry.  If there isn't
      // one, then just skip the entry.
      String backendClassName = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
        StringConfigAttribute classStub =
             new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
                                       true, false, false);
        StringConfigAttribute classAttr =
             (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          continue;
        }
        else
        {
          backendClassName = classAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }

      Class backendClass = null;
      try
      {
        backendClass = Class.forName(backendClassName);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOAD_BACKEND_CLASS;
        String message = getMessage(msgID, backendClassName,
                                    String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }

      Backend backend = null;
      try
      {
        backend = (Backend) backendClass.newInstance();
        backend.setBackendID(backendID);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_INSTANTIATE_BACKEND_CLASS;
        String message = getMessage(msgID, backendClassName,
                                    String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }


      // Get the base DN attribute from the entry.  If there isn't one, then
      // just skip this entry.
      List<DN> baseDNs = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
        DNConfigAttribute baseDNStub =
             new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID),
                                   true, true, true);
        DNConfigAttribute baseDNAttr =
             (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
        if (baseDNAttr == null)
        {
          msgID = MSGID_LDIFEXPORT_NO_BASES_FOR_BACKEND;
          String message = getMessage(msgID,
                                      String.valueOf(configEntry.getDN()));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
        else
        {
          baseDNs = baseDNAttr.activeValues();
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_DETERMINE_BASES_FOR_BACKEND;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }


      backendList.add(backend);
      entryList.add(configEntry);
      dnList.add(baseDNs);
    }
  }
}

