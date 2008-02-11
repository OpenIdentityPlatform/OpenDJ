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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
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
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.ToolMessages.*;
import org.opends.messages.Message;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
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
    int retCode = mainVerifyIndex(args, true, System.out, System.err);

    if(errorLogPublisher != null)
    {
      ErrorLogger.removeErrorLogPublisher(errorLogPublisher);
    }

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the verify process.
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
  public static int mainVerifyIndex(String[] args, boolean initializeServer,
                                    OutputStream outStream,
                                    OutputStream errStream)
  {
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

    // Define the command-line arguments that may be used with this program.
    StringArgument  configClass             = null;
    StringArgument  configFile              = null;
    StringArgument  baseDNString            = null;
    StringArgument  indexList               = null;
    BooleanArgument cleanMode               = null;
    BooleanArgument countErrors             = null;
    BooleanArgument displayUsage            = null;


    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_VERIFYINDEX_TOOL_DESCRIPTION.get();
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


      baseDNString =
           new StringArgument("basedn", OPTION_SHORT_BASEDN,
                              OPTION_LONG_BASEDN, true, false, true,
                              INFO_BASEDN_PLACEHOLDER.get(), null, null,
                              INFO_VERIFYINDEX_DESCRIPTION_BASE_DN.get());
      argParser.addArgument(baseDNString);


      indexList =
           new StringArgument("index", 'i', "index",
                              false, true, true,
                              INFO_INDEX_PLACEHOLDER.get(), null, null,
                              INFO_VERIFYINDEX_DESCRIPTION_INDEX_NAME.get());
      argParser.addArgument(indexList);

      cleanMode =
           new BooleanArgument("clean", 'c', "clean",
                               INFO_VERIFYINDEX_DESCRIPTION_VERIFY_CLEAN.get());
      argParser.addArgument(cleanMode);

      countErrors =
           new BooleanArgument("counterrors", null, "countErrors",
                               INFO_VERIFYINDEX_DESCRIPTION_COUNT_ERRORS.get());
      argParser.addArgument(countErrors);

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


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
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




    // If no arguments were provided, then display usage information and exit.
    int numArgs = args.length;
    if (numArgs == 0)
    {
      out.println(argParser.getUsage());
      return 1;
    }


    if (cleanMode.isPresent() && indexList.getValues().size() != 1)
    {
      Message message =
              ERR_VERIFYINDEX_VERIFY_CLEAN_REQUIRES_SINGLE_INDEX.get();

      err.println(wrapText(message, MAX_LINE_WIDTH));
      out.println(argParser.getUsage());
      return 1;
    }

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
        Message message =
                ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage());
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
      // of the verify process.
      try
      {
        errorLogPublisher =
            new ThreadFilterTextErrorLogPublisher(Thread.currentThread(),
                                                  new TextWriter.STREAM(out));
        ErrorLogger.addErrorLogPublisher(errorLogPublisher);

      }
      catch(Exception e)
      {
        err.println("Error installing the custom error logger: " +
                    stackTraceToSingleLineString(e));
      }
    }


    // Decode the base DN provided by the user.
    DN verifyBaseDN ;
    try
    {
      verifyBaseDN = DN.decode(baseDNString.getValue());
    }
    catch (DirectoryException de)
    {
      Message message = ERR_CANNOT_DECODE_BASE_DN.get(
          baseDNString.getValue(), de.getMessageObject());
      logError(message);
      return 1;
    }
    catch (Exception e)
    {
      Message message = ERR_CANNOT_DECODE_BASE_DN.get(
          baseDNString.getValue(), getExceptionMessage(e));
      logError(message);
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
            Message message =
                ERR_MULTIPLE_BACKENDS_FOR_BASE.get(baseDNString.getValue());
            logError(message);
            return 1;
          }
          break;
        }
      }
    }

    if (backend == null)
    {
      Message message = ERR_NO_BACKENDS_FOR_BASE.get(baseDNString.getValue());
      logError(message);
      return 1;
    }

    if (!(backend instanceof BackendImpl))
    {
      Message message = ERR_BACKEND_NO_INDEXING_SUPPORT.get();
      logError(message);
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
        Message message = ERR_VERIFYINDEX_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        logError(message);
        return 1;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_VERIFYINDEX_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
      return 1;
    }


    // Launch the verify process.
    int returnCode = 0 ;
    try
    {
      BackendImpl jebBackend = (BackendImpl)backend;
      long errorCount = jebBackend.verifyBackend(verifyConfig, null);
      if (countErrors.isPresent())
      {
        if (errorCount > Integer.MAX_VALUE)
        {
          returnCode = Integer.MAX_VALUE;
        }
        else
        {
          returnCode = (int) errorCount;
        }
      }
    }
    catch (Exception e)
    {
      Message message = ERR_VERIFYINDEX_ERROR_DURING_VERIFY.get(
          stackTraceToSingleLineString(e));
      logError(message);
      returnCode = 1;
    }


    // Release the shared lock on the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        Message message = WARN_VERIFYINDEX_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        logError(message);
      }
    }
    catch (Exception e)
    {
      Message message = WARN_VERIFYINDEX_CANNOT_UNLOCK_BACKEND.get(
          backend.getBackendID(), getExceptionMessage(e));
      logError(message);
    }

    return returnCode;
  }
}
