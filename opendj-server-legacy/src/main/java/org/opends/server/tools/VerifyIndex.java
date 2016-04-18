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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.BuildVersion;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This program provides a utility to verify the contents of the indexes
 * of a Directory Server backend.  This will be a process that is
 * intended to run separate from Directory Server and not internally within the
 * server process (e.g., via the tasks interface).
 */
public class VerifyIndex
{

  /**
   * Processes the command-line arguments and invokes the verify process.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainVerifyIndex(args, true, System.err);
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
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   * @return The error code.
   */
  public static int mainVerifyIndex(String[] args, boolean initializeServer,
                                    OutputStream errStream)
  {
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.enableConsoleLoggingForOpenDJTool();

    // Define the command-line arguments that may be used with this program.
    StringArgument  configFile              = null;
    StringArgument  baseDNString            = null;
    StringArgument  indexList               = null;
    BooleanArgument cleanMode               = null;
    BooleanArgument countErrors             = null;
    BooleanArgument displayUsage            = null;


    // Create the command-line argument parser for use with this program.
    LocalizableMessage toolDescription = INFO_VERIFYINDEX_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.VerifyIndex",
                            toolDescription, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_VERIFY_INDEX.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    // Initialize all the command-line argument types and register them with the parser.
    try
    {
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('f')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      baseDNString =
              StringArgument.builder(OPTION_LONG_BASEDN)
                      .shortIdentifier(OPTION_SHORT_BASEDN)
                      .description(INFO_VERIFYINDEX_DESCRIPTION_BASE_DN.get())
                      .required()
                      .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      indexList =
              StringArgument.builder("index")
                      .shortIdentifier('i')
                      .description(INFO_VERIFYINDEX_DESCRIPTION_INDEX_NAME.get())
                      .multiValued()
                      .valuePlaceholder(INFO_INDEX_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      cleanMode =
              BooleanArgument.builder("clean")
                      .shortIdentifier('c')
                      .description(INFO_VERIFYINDEX_DESCRIPTION_VERIFY_CLEAN.get())
                      .buildAndAddToParser(argParser);
      countErrors =
              BooleanArgument.builder("countErrors")
                      .description(INFO_VERIFYINDEX_DESCRIPTION_COUNT_ERRORS.get())
                      .buildAndAddToParser(argParser);

      displayUsage = showUsageArgument();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    if (cleanMode.isPresent() && indexList.getValues().size() != 1)
    {
      argParser.displayMessageAndUsageReference(err, ERR_VERIFYINDEX_VERIFY_CLEAN_REQUIRES_SINGLE_INDEX.get());
      return 1;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessage());
      return 1;
    }

    if (initializeServer)
    {
      try
      {
        new DirectoryServer.InitializationBuilder(configFile.getValue())
            .requireCryptoServices()
            .initialize();
      }
      catch (InitializationException ie)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(ie.getLocalizedMessage()));
        return 1;
      }
    }

    // Decode the base DN provided by the user.
    DN verifyBaseDN ;
    try
    {
      verifyBaseDN = DN.valueOf(baseDNString.getValue());
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_CANNOT_DECODE_BASE_DN.get(baseDNString.getValue(), getExceptionMessage(e)));
      return 1;
    }


    // Get information about the backends defined in the server.  Iterate
    // through them, finding the one backend to be verified.
    List<Backend<?>> backendList = new ArrayList<>();
    List<BackendCfg> entryList = new ArrayList<>();
    List<List<DN>> dnList = new ArrayList<>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);

    Backend<?> backend = null;
    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend<?> b = backendList.get(i);
      List<DN>    baseDNs = dnList.get(i);

      if (baseDNs.contains(verifyBaseDN))
      {
        if (backend != null)
        {
          printWrappedText(err, ERR_MULTIPLE_BACKENDS_FOR_BASE.get(baseDNString.getValue()));
          return 1;
        }
        backend = b;
      }
    }

    if (backend == null)
    {
      printWrappedText(err, ERR_NO_BACKENDS_FOR_BASE.get(baseDNString.getValue()));
      return 1;
    }

    if (!backend.supports(BackendOperation.INDEXING))
    {
      printWrappedText(err, ERR_BACKEND_NO_INDEXING_SUPPORT.get());
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
        printWrappedText(err, ERR_VERIFYINDEX_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), failureReason));
        return 1;
      }
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_VERIFYINDEX_CANNOT_LOCK_BACKEND.get(backend.getBackendID(), getExceptionMessage(e)));
      return 1;
    }


    try
    {
      // Launch the verify process.
      final long errorCount = backend.verifyBackend(verifyConfig);
      if (countErrors.isPresent())
      {
        if (errorCount > Integer.MAX_VALUE)
        {
          return Integer.MAX_VALUE;
        }
        return (int) errorCount;
      }
      return 0;
    }
    catch (InitializationException e)
    {
      printWrappedText(err, ERR_VERIFYINDEX_ERROR_DURING_VERIFY.get(e.getMessage()));
      return 1;
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_VERIFYINDEX_ERROR_DURING_VERIFY.get(stackTraceToSingleLineString(e)));
      return 1;
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
          printWrappedText(err, WARN_VERIFYINDEX_CANNOT_UNLOCK_BACKEND.get(backend.getBackendID(), failureReason));
        }
      }
      catch (Exception e)
      {
        printWrappedText(err,
            WARN_VERIFYINDEX_CANNOT_UNLOCK_BACKEND.get(backend.getBackendID(), getExceptionMessage(e)));
      }
    }
  }
}
