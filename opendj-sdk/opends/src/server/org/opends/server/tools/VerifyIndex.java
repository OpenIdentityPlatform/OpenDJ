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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import org.opends.server.api.Backend;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.jeb.VerifyConfig;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.ConfigFileHandler;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.loggers.StartupErrorLogger;
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
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;



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
    // Define the command-line arguments that may be used with this program.
    StringArgument  configClass             = null;
    StringArgument  configFile              = null;
    StringArgument  baseDNString            = null;
    StringArgument  indexList               = null;
    BooleanArgument cleanMode               = null;
    BooleanArgument displayUsage            = null;


    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.VerifyIndex", false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", 'C', "configClass", true, false,
                              true, "{configClass}",
                              ConfigFileHandler.class.getName(), null,
                              MSGID_VERIFYINDEX_DESCRIPTION_CONFIG_CLASS);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              MSGID_VERIFYINDEX_DESCRIPTION_CONFIG_FILE);
      argParser.addArgument(configFile);


      baseDNString =
           new StringArgument("basedn", 'b', "baseDN", true, false, true,
                              "{baseDN}", null, null,
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
           new BooleanArgument("help", 'H', "help",
                               MSGID_VERIFYINDEX_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(message);
      System.exit(1);
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_VERIFYINDEX_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(message);
      System.err.println(argParser.getUsage());
      System.exit(1);
    }


    // If we should just display usage information, then print it and exit.
    if (displayUsage.isPresent())
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


    if (cleanMode.isPresent() && indexList.getValues().size() != 1)
    {
      int    msgID   = MSGID_VERIFYINDEX_VERIFY_CLEAN_REQUIRES_SINGLE_INDEX;
      String message = getMessage(msgID);

      System.err.println(message);
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
      int    msgID   = MSGID_VERIFYINDEX_SERVER_BOOTSTRAP_ERROR;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOAD_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }



    // Initialize the Directory Server schema elements.
    try
    {
      directoryServer.initializeSchema();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOAD_SCHEMA;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(message);
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
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_INITIALIZE_CORE_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }


    // Initialize the Directory Server crypto manager.
    try
    {
      directoryServer.initializeCryptoManager();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ce.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (InitializationException ie)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }


    // FIXME -- Install a custom logger to capture information about the state
    // of the verify process.
    StartupErrorLogger startupLogger = new StartupErrorLogger();
    startupLogger.initializeErrorLogger(null);
    addErrorLogger(startupLogger);


    // Decode the base DN provided by the user.
    DN verifyBaseDN = null;
    try
    {
      verifyBaseDN = DN.decode(baseDNString.getValue());
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, baseDNString.getValue(),
                                  de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, baseDNString.getValue(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }


    // Get information about the backends defined in the server.  Iterate
    // through them, finding the one backend to be verified.
    Backend       backend         = null;
    ConfigEntry   configEntry     = null;
    DN[]          baseDNArray         = null;

    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<ConfigEntry> entryList   = new ArrayList<ConfigEntry>();
    ArrayList<List<DN>>    dnList      = new ArrayList<List<DN>>();
    getBackends(backendList, entryList, dnList);

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend     b       = backendList.get(i);
      ConfigEntry entry   = entryList.get(i);
      List<DN>    baseDNs = dnList.get(i);

      for (DN baseDN : baseDNs)
      {
        if (baseDN.equals(verifyBaseDN))
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
            int    msgID   = MSGID_VERIFYINDEX_MULTIPLE_BACKENDS_FOR_BASE;
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
      int    msgID   = MSGID_VERIFYINDEX_NO_BACKENDS_FOR_BASE;
      String message = getMessage(msgID, baseDNString.getValue());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }

    if (!(backend instanceof BackendImpl))
    {
      int    msgID   = MSGID_VERIFYINDEX_WRONG_BACKEND_TYPE;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
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
        return;
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return;
    }


    // Launch the verify process.
    try
    {
      BackendImpl jebBackend = (BackendImpl)backend;
      jebBackend.verifyBackend(verifyConfig, configEntry, baseDNArray);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_ERROR_DURING_VERIFY;
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
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }
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
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE, de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_DECODE_BACKEND_BASE_DN;
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
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE, ce.getMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_VERIFYINDEX_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
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
      // Get the backend class name attribute from the entry.  If there isn't
      // one, then just skip this entry.
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
        int    msgID   = MSGID_VERIFYINDEX_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        System.exit(1);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_VERIFYINDEX_CANNOT_DETERMINE_BACKEND_CLASS;
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
        int    msgID   = MSGID_VERIFYINDEX_CANNOT_LOAD_BACKEND_CLASS;
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
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_VERIFYINDEX_CANNOT_INSTANTIATE_BACKEND_CLASS;
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
          msgID = MSGID_VERIFYINDEX_NO_BASES_FOR_BACKEND;
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
        int    msgID   = MSGID_VERIFYINDEX_CANNOT_DETERMINE_BASES_FOR_BACKEND;
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

