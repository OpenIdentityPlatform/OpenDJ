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



import org.opends.server.api.Backend;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.jeb.VerifyConfig;
import org.opends.server.config.ConfigException;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.ThreadFilterTextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import java.util.ArrayList;
import java.util.List;

import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.util.StaticUtils;
import static org.opends.server.tools.ToolConstants.*;
import org.opends.server.admin.std.server.BackendCfg;


/**
 * This program provides a utility to verify the contents of the indexes
 * of a Directory Server backend.  This will be a process that is
 * intended to run separate from Directory Server and not internally within the
 * server process (e.g., via the tasks interface).
 */
public class VerifyIndex
{
  private static ErrorLogPublisher errorLogPublisher = null;
  /**
   * Processes the command-line arguments and invokes the verify process.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainVerifyIndex(args);

    if(errorLogPublisher != null)
    {
      ErrorLogger.removeErrorLogPublisher(errorLogPublisher);
    }

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Processes the command-line arguments and invokes the verify process.
   *
   * @param  args  The command-line arguments provided to this program.
   * @return The error code.
   */
  public static int mainVerifyIndex(String[] args)
  {
    // Define the command-line arguments that may be used with this program.
    StringArgument  configClass             = null;
    StringArgument  configFile              = null;
    StringArgument  baseDNString            = null;
    StringArgument  indexList               = null;
    BooleanArgument cleanMode               = null;
    BooleanArgument displayUsage            = null;


    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_VERIFYINDEX_TOOL_DESCRIPTION);
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.VerifyIndex",
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
           new StringArgument("basedn", OPTION_SHORT_BASEDN,
                              OPTION_LONG_BASEDN, true, false, true,
                              OPTION_VALUE_BASEDN, null, null,
                              MSGID_VERIFYINDEX_DESCRIPTION_BASE_DN);
      argParser.addArgument(baseDNString);


      indexList =
           new StringArgument("index", 'i', "index",
                              false, true, true,
                              "{index}", null, null,
                              MSGID_VERIFYINDEX_DESCRIPTION_INDEX_NAME);
      argParser.addArgument(indexList);

      cleanMode =
           new BooleanArgument("clean", 'c', "clean",
                               MSGID_VERIFYINDEX_DESCRIPTION_VERIFY_CLEAN);
      argParser.addArgument(cleanMode);


      displayUsage =
           new BooleanArgument("help", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                               MSGID_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
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
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }




    // If no arguments were provided, then display usage information and exit.
    int numArgs = args.length;
    if (numArgs == 0)
    {
      System.out.println(argParser.getUsage());
      return 1;
    }


    if (cleanMode.isPresent() && indexList.getValues().size() != 1)
    {
      int    msgID   = MSGID_VERIFYINDEX_VERIFY_CLEAN_REQUIRES_SINGLE_INDEX;
      String message = getMessage(msgID);

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.out.println(argParser.getUsage());
      return 1;
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
      String message = getMessage(msgID, getExceptionMessage(e));
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
      int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, getExceptionMessage(e));
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
      int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, getExceptionMessage(e));
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
      int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, getExceptionMessage(e));
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
      int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, getExceptionMessage(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // FIXME -- Install a custom logger to capture information about the state
    // of the verify process.
    try
    {
      errorLogPublisher =
          new ThreadFilterTextErrorLogPublisher(Thread.currentThread(),
                                                new TextWriter.STDOUT());
      ErrorLogger.addErrorLogPublisher(errorLogPublisher);

    }
    catch(Exception e)
    {
      System.err.println("Error installing the custom error logger: " +
          StaticUtils.stackTraceToSingleLineString(e));
    }


    // Decode the base DN provided by the user.
    DN verifyBaseDN ;
    try
    {
      verifyBaseDN = DN.decode(baseDNString.getValue());
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, baseDNString.getValue(),
                                  de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, baseDNString.getValue(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }


    // Get information about the backends defined in the server.  Iterate
    // through them, finding the one backend to be verified.
    Backend       backend         = null;

    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<BackendCfg>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend     b       = backendList.get(i);
      List<DN>    baseDNs = dnList.get(i);

      for (DN baseDN : baseDNs)
      {
        if (baseDN.equals(verifyBaseDN))
        {
          if (backend == null)
          {
            backend         = b;
          }
          else
          {
            int    msgID   = MSGID_MULTIPLE_BACKENDS_FOR_BASE;
            String message = getMessage(msgID, baseDNString.getValue());
            logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
            return 1;
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
      return 1;
    }

    if (!(backend instanceof BackendImpl))
    {
      int    msgID   = MSGID_BACKEND_NO_INDEXING_SUPPORT;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      return 1;
    }

    // Initialize the verify configuration.
    VerifyConfig verifyConfig = new VerifyConfig();
    verifyConfig.setBaseDN(verifyBaseDN);
    if (cleanMode.isPresent())
    {
      for (String s : indexList.getValues())
      {
        verifyConfig.addCleanIndex(s);
      }
    }
    else
    {
      for (String s : indexList.getValues())
      {
        verifyConfig.addCompleteIndex(s);
      }
    }


    // Acquire a shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        return 1;
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return 1;
    }


    // Launch the verify process.
    try
    {
      BackendImpl jebBackend = (BackendImpl)backend;
      jebBackend.verifyBackend(verifyConfig, null);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_ERROR_DURING_VERIFY;
      String message = getMessage(msgID, getExceptionMessage(e));
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
        int    msgID   = MSGID_VERIFYINDEX_CANNOT_UNLOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_UNLOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  getExceptionMessage(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }

    return 0;
  }
}
