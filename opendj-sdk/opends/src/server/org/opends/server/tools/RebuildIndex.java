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

import static org.opends.server.util.StaticUtils.wrapText;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.extensions.ConfigFileHandler;

import static org.opends.server.messages.ToolMessages.*;
import org.opends.server.config.ConfigException;
import org.opends.server.config.ConfigEntry;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.loggers.Error.logError;
import org.opends.server.loggers.StartupErrorLogger;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.LockFileManager;
import org.opends.server.types.*;
import org.opends.server.api.Backend;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.jeb.RebuildConfig;

import java.util.ArrayList;
import java.util.List;


/**
 * This program provides a utility to rebuild the contents of the indexes
 * of a Directory Server backend.  This will be a process that is
 * intended to run separate from Directory Server and not internally within the
 * server process (e.g., via the tasks interface).
 */
public class RebuildIndex
{
  /**
   * Processes the command-line arguments and invokes the rebuild process.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    // Define the command-line arguments that may be used with this program.
    StringArgument  configClass             = null;
    StringArgument  configFile              = null;
    StringArgument  baseDNString            = null;
    StringArgument  indexList               = null;
    BooleanArgument displayUsage            = null;


    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_REBUILDINDEX_TOOL_DESCRIPTION);
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.RebuildIndex",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", 'C', "configClass", true, false,
                              true, "{configClass}",
                              ConfigFileHandler.class.getName(), null,
                              MSGID_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              MSGID_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      baseDNString =
           new StringArgument("basedn", 'b', "baseDN", true, false, true,
                              "{baseDN}", null, null,
                              MSGID_REBUILDINDEX_DESCRIPTION_BASE_DN);
      argParser.addArgument(baseDNString);


      indexList =
           new StringArgument("index", 'i', "index",
                              false, true, true,
                              "{index}", null, null,
                              MSGID_REBUILDINDEX_DESCRIPTION_INDEX_NAME);
      argParser.addArgument(indexList);


      displayUsage =
           new BooleanArgument("help", 'H', "help",
                               MSGID_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      System.exit(1);
    }


    // If we should just display usage information, then print it and exit.
    if (argParser.usageDisplayed())
    {
      System.exit(0);
    }




    // If no arguments were provided, then display usage information and exit.
    int numArgs = args.length;
    if (numArgs == 0)
    {
      System.out.println(argParser.getUsage());
      System.exit(1);
    }


    if (indexList.getValues().size() <= 0)
    {
      int    msgID   = MSGID_REBUILDINDEX_REQUIRES_AT_LEAST_ONE_INDEX;
      String message = getMessage(msgID);

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.out.println(argParser.getUsage());
      System.exit(1);
    }

    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();

    try
    {
      DirectoryServer.bootstrapClient();
      DirectoryServer.initializeJMX();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_SERVER_BOOTSTRAP_ERROR;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }



    // Initialize the Directory Server schema elements.
    try
    {
      directoryServer.initializeSchema();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }


    // Initialize the Directory Server core configuration.
    try
    {
      CoreConfigManager coreConfigManager = new CoreConfigManager();
      coreConfigManager.initializeCoreConfig();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }


    // Initialize the Directory Server crypto manager.
    try
    {
      directoryServer.initializeCryptoManager();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.exit(1);
    }



    // FIXME -- Install a custom logger to capture information about the state
    // of the verify process.
    StartupErrorLogger startupLogger = new StartupErrorLogger();
    startupLogger.initializeErrorLogger(null);
    addErrorLogger(startupLogger);

    // Decode the base DN provided by the user.
    DN rebuildBaseDN = null;
    try
    {
      rebuildBaseDN = DN.decode(baseDNString.getValue());
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, baseDNString.getValue(),
                                  de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, baseDNString.getValue(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }

    // Get information about the backends defined in the server.
    Backend backend = null;
    ConfigEntry configEntry = null;
    DN[]          baseDNArray = null;

    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<ConfigEntry> entryList   = new ArrayList<ConfigEntry>();
    ArrayList<List<DN>> dnList = new ArrayList<List<DN>>();
    int code = BackendToolUtils.getBackends(backendList, entryList, dnList);

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend     b       = backendList.get(i);
      ConfigEntry entry   = entryList.get(i);
      List<DN>    baseDNs = dnList.get(i);

      for (DN baseDN : baseDNs)
      {
        if (baseDN.equals(rebuildBaseDN))
        {
          if (backend == null)
          {
            backend         = b;
            configEntry     = entry;
            baseDNArray     = new DN[baseDNs.size()];
            baseDNs.toArray(baseDNArray);
          }
          else
          {
            int    msgID   = MSGID_MULTIPLE_BACKENDS_FOR_BASE;
            String message = getMessage(msgID, baseDNString.getValue());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
            System.exit(1);
          }
          break;
        }
      }
    }

    if (backend == null)
    {
      int    msgID   = MSGID_NO_BACKENDS_FOR_BASE;
      String message = getMessage(msgID, baseDNString.getValue());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }

    if (!(backend instanceof BackendImpl))
    {
      int    msgID   = MSGID_BACKEND_NO_INDEXING_SUPPORT;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }

    // Initialize the rebuild configuration.
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(rebuildBaseDN);
    for (String s : indexList.getValues())
    {
      rebuildConfig.addRebuildIndex(s);
    }

    // Acquire an exclusive lock for the backend.
    //TODO: Find a way to do this with the server online.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        int    msgID   = MSGID_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return;
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_REBUILDINDEX_CANNOT_EXCLUSIVE_LOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return;
    }

    // Launch the rebuild process.
    try
    {
      BackendImpl jebBackend = (BackendImpl)backend;
      jebBackend.rebuildBackend(rebuildConfig, configEntry, baseDNArray);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_REBUILDINDEX_ERROR_DURING_REBUILD;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }
    finally
    {
      // Release the shared lock on the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_REBUILDINDEX_CANNOT_UNLOCK_BACKEND;
          String message = getMessage(msgID, backend.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                   message, msgID);
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_REBUILDINDEX_CANNOT_UNLOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
      }
    }
  }
}