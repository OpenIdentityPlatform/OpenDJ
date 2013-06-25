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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 *      Portions Copyright 2012 profiq s.r.o.
 */

package org.opends.server.tools.dsreplication;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.opends.admin.ads.ServerDescriptor.getReplicationServer;
import static org.opends.admin.ads.ServerDescriptor.getServerRepresentation;
import static org.opends.admin.ads.ServerDescriptor.getSuffixDisplay;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.UtilityMessages.
 ERR_CONFIRMATION_TRIES_LIMIT_REACHED;
import static org.opends.quicksetup.util.Utils.getFirstValue;
import static org.opends.quicksetup.util.Utils.getThrowableMsg;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.tools.dsreplication.ReplicationCliReturnCode.*;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.TrustManager;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.TopologyCacheFilter;
import org.opends.admin.ads.ADSContext.ADSPropertySyntax;
import org.opends.admin.ads.ADSContext.AdministratorProperty;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.admin.ads.util.ServerLoader;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.util.ConfigFromDirContext;
import org.opends.guitools.controlpanel.util.ConfigFromFile;
import org.opends.guitools.controlpanel.util.ControlPanelLog;
import org.opends.guitools.controlpanel.util.ProcessReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ReturnCode;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.InstallerHelper;
import org.opends.quicksetup.installer.PeerNotFoundException;
import org.opends.quicksetup.installer.offline.OfflineInstaller;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.*;
import org.opends.server.admin.std.meta.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tasks.PurgeConflictsHistoricalTask;
import org.opends.server.tools.ClientException;
import org.opends.server.tools.ToolConstants;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.tools.tasks.TaskScheduleInteraction;
import org.opends.server.tools.tasks.TaskScheduleUserData;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.CommandBuilder;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.cli.PointAdder;
import org.opends.server.util.table.TabSeparatedTablePrinter;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TablePrinter;
import org.opends.server.util.table.TextTablePrinter;

/**
 * This class provides a tool that can be used to enable and disable replication
 * and also to initialize the contents of a replicated suffix with the contents
 * of another suffix.  It also allows to display the replicated status of the
 * different base DNs of the servers that are registered in the ADS.
 */
public class ReplicationCliMain extends ConsoleApplication
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = ReplicationCliMain.class.getName();

  /** Prefix for log files. */
  static public final String LOG_FILE_PREFIX = "opendj-replication-";

  /** Suffix for log files. */
  static public final String LOG_FILE_SUFFIX = ".log";

  /**
   * Property used to call the dsreplication script and ReplicationCliMain to
   * know which are the java properties to be used (those of dsreplication or
   * those of dsreplication.offline).
   */
  private static final String SCRIPT_CALL_STATUS =
    "org.opends.server.dsreplicationcallstatus";

  /**
   * The value set by the dsreplication script if it is called the first time.
   */
  private static final String FIRST_SCRIPT_CALL =
    "firstcall";

  private boolean forceNonInteractive;

  private static final Logger LOG =
    Logger.getLogger(ReplicationCliMain.class.getName());

  // Always use SSL with the administration connector
  private final boolean useSSL = true;
  private final boolean useStartTLS = false;

  /**
   * The enumeration containing the different options we display when we ask
   * the user to provide the subcommand interactively.
   */
  private enum SubcommandChoice
  {
    /**
     * Enable replication.
     */
    ENABLE(INFO_REPLICATION_ENABLE_MENU_PROMPT.get()),
    /**
     * Disable replication.
     */
    DISABLE(INFO_REPLICATION_DISABLE_MENU_PROMPT.get()),
    /**
     * Initialize replication.
     */
    INITIALIZE(INFO_REPLICATION_INITIALIZE_MENU_PROMPT.get()),
    /**
     * Initialize All.
     */
    INITIALIZE_ALL(INFO_REPLICATION_INITIALIZE_ALL_MENU_PROMPT.get()),
    /**
     * Pre external initialization.
     */
    PRE_EXTERNAL_INITIALIZATION(
        INFO_REPLICATION_PRE_EXTERNAL_INITIALIZATION_MENU_PROMPT.get()),
    /**
     * Post external initialization.
     */
    POST_EXTERNAL_INITIALIZATION(
        INFO_REPLICATION_POST_EXTERNAL_INITIALIZATION_MENU_PROMPT.get()),
    /**
     * Replication status.
     */
    STATUS(INFO_REPLICATION_STATUS_MENU_PROMPT.get()),
    /**
     * Replication purge historical.
     */
    PURGE_HISTORICAL(INFO_REPLICATION_PURGE_HISTORICAL_MENU_PROMPT.get()),
    /**
     * Cancel operation.
     */
    CANCEL(null);
    private Message prompt;
    private SubcommandChoice(Message prompt)
    {
      this.prompt = prompt;
    }
    Message getPrompt()
    {
      return prompt;
    }
  }

  /** The argument parser to be used. */
  private ReplicationCliArgumentParser argParser;
  private FileBasedArgument userProvidedAdminPwdFile;
  private LDAPConnectionConsoleInteraction ci = null;
  private CommandBuilder firstServerCommandBuilder;
  /** The message formatter. */
  PlainTextProgressMessageFormatter formatter =
      new PlainTextProgressMessageFormatter();

  /**
   * Constructor for the ReplicationCliMain object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   * @param in the input stream to use for standard input.
   */
  public ReplicationCliMain(PrintStream out, PrintStream err, InputStream in)
  {
    super(in, out, err);
  }

  /**
   * The main method for the replication tool.
   *
   * @param args the command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, true, System.out, System.err, System.in);

    System.exit(retCode);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the replication tool.
   *
   * @param args the command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args)
  {
    return mainCLI(args, true, System.out, System.err, System.in);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the replication tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param initializeServer   Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   * @param  inStream          The input stream to use for standard input.
   * @return The error code.
   */

  public static int mainCLI(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream, InputStream inStream)
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

    try
    {
      ControlPanelLog.initLogFileHandler(
          File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }
    ReplicationCliMain replicationCli = new ReplicationCliMain(out, err,
        inStream);
    return replicationCli.execute(args, initializeServer);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the replication tool.
   *
   * @param args the command-line arguments provided to this program.
   * @param  initializeServer  Indicates whether to initialize the server.
   *
   * @return The error code.
   */
  public int execute(String[] args, boolean initializeServer)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    // Create the command-line argument parser for use with this
    // program.
    try
    {
      createArgumenParser();
    }
    catch (ArgumentException ae)
    {
      Message message =
        ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      LOG.log(Level.SEVERE, "Complete error stack:", ae);
      returnValue = CANNOT_INITIALIZE_ARGS;
    }

    if (returnValue == SUCCESSFUL_NOP)
    {
      try
      {
        argParser.getSecureArgsList().initArgumentsWithConfiguration();
      }
      catch (ConfigException ce)
      {
        // Ignore.
      }

      //  Parse the command-line arguments provided to this program.
      try
      {
        argParser.parseArguments(args);
      }
      catch (ArgumentException ae)
      {
        Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

        println(message);
        println();
        println(Message.raw(argParser.getUsage()));
        LOG.log(Level.SEVERE, "Complete error stack:", ae);
        returnValue = ERROR_USER_DATA;
      }
    }

    //  If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed()) {
      return 0;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      println(e.getMessageObject());
      return 1;
    }

    if (!argParser.usageOrVersionDisplayed())
    {
      if (returnValue == SUCCESSFUL_NOP)
      {
        /* Check that the provided parameters are compatible.
         */
        MessageBuilder buf = new MessageBuilder();
        argParser.validateOptions(buf);
        if (buf.length() > 0)
        {
          println(buf.toMessage());
          println(Message.raw(argParser.getUsage()));
          returnValue = ERROR_USER_DATA;
        }
      }
      if (initializeServer && returnValue == SUCCESSFUL_NOP)
      {
        DirectoryServer.bootstrapClient();

        // Bootstrap definition classes.
        try
        {
          if (!ClassLoaderProvider.getInstance().isEnabled())
          {
            ClassLoaderProvider.getInstance().enable();
          }
          // Switch off class name validation in client.
          ClassPropertyDefinition.setAllowClassValidation(false);

          // Switch off attribute type name validation in client.
          AttributeTypePropertyDefinition.setCheckSchema(false);
        }
        catch (InitializationException ie)
        {
          println(ie.getMessageObject());
          returnValue = ERROR_INITIALIZING_ADMINISTRATION_FRAMEWORK;
        }
      }

      if (returnValue == SUCCESSFUL_NOP)
      {
        if (argParser.getSecureArgsList().
        bindPasswordFileArg.isPresent())
        {
          try
          {
            userProvidedAdminPwdFile = new FileBasedArgument(
                "adminPasswordFile",
                OPTION_SHORT_BINDPWD_FILE, "adminPasswordFile", false, false,
                INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
            userProvidedAdminPwdFile.getNameToValueMap().putAll(
                argParser.getSecureArgsList().
                bindPasswordFileArg.getNameToValueMap());
          }
          catch (Throwable t)
          {
            throw new IllegalStateException("Unexpected error: "+t, t);
          }
        }
        ci = new LDAPConnectionConsoleInteraction(this,
            argParser.getSecureArgsList());
        ci.setDisplayLdapIfSecureParameters(false);
      }
      if (returnValue == SUCCESSFUL_NOP)
      {
        boolean subcommandLaunched = true;
        String subCommand = null;
        if (argParser.isEnableReplicationSubcommand())
        {
          returnValue = enableReplication();
          subCommand =
            ReplicationCliArgumentParser.ENABLE_REPLICATION_SUBCMD_NAME;
        }
        else if (argParser.isDisableReplicationSubcommand())
        {
          returnValue = disableReplication();
          subCommand =
            ReplicationCliArgumentParser.DISABLE_REPLICATION_SUBCMD_NAME;
        }
        else if (argParser.isInitializeReplicationSubcommand())
        {
          returnValue = initializeReplication();
          subCommand =
            ReplicationCliArgumentParser.INITIALIZE_REPLICATION_SUBCMD_NAME;
        }
        else if (argParser.isInitializeAllReplicationSubcommand())
        {
          returnValue = initializeAllReplication();
          subCommand =
            ReplicationCliArgumentParser.INITIALIZE_ALL_REPLICATION_SUBCMD_NAME;
        }
        else if (argParser.isPreExternalInitializationSubcommand())
        {
          returnValue = preExternalInitialization();
          subCommand =
           ReplicationCliArgumentParser.PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME;
        }
        else if (argParser.isPostExternalInitializationSubcommand())
        {
          returnValue = postExternalInitialization();
          subCommand =
          ReplicationCliArgumentParser.POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME;
        }
        else if (argParser.isStatusReplicationSubcommand())
        {
          returnValue = statusReplication();
          subCommand =
            ReplicationCliArgumentParser.STATUS_REPLICATION_SUBCMD_NAME;
        }
        else if (argParser.isPurgeHistoricalSubcommand())
        {
          returnValue = purgeHistorical();
          subCommand =
            ReplicationCliArgumentParser.PURGE_HISTORICAL_SUBCMD_NAME;
        }
        else
        {
          if (argParser.isInteractive())
          {
            switch (promptForSubcommand())
            {
            case ENABLE:
              subCommand =
                ReplicationCliArgumentParser.ENABLE_REPLICATION_SUBCMD_NAME;
              break;

            case DISABLE:
              subCommand =
                ReplicationCliArgumentParser.DISABLE_REPLICATION_SUBCMD_NAME;
              break;

            case INITIALIZE:
              subCommand =
                ReplicationCliArgumentParser.INITIALIZE_REPLICATION_SUBCMD_NAME;
              break;

            case INITIALIZE_ALL:
              subCommand =
                ReplicationCliArgumentParser.
                INITIALIZE_ALL_REPLICATION_SUBCMD_NAME;
              break;

            case PRE_EXTERNAL_INITIALIZATION:
              subCommand = ReplicationCliArgumentParser.
              PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME;
              break;

            case POST_EXTERNAL_INITIALIZATION:
              subCommand = ReplicationCliArgumentParser.
                 POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME;
              break;

            case STATUS:
              subCommand =
                ReplicationCliArgumentParser.STATUS_REPLICATION_SUBCMD_NAME;
              break;

            case PURGE_HISTORICAL:
              subCommand =
                ReplicationCliArgumentParser.PURGE_HISTORICAL_SUBCMD_NAME;
              break;

            default:
              // User canceled
              returnValue = USER_CANCELLED;
            }

            if (subCommand != null)
            {
              String[] newArgs = new String[args.length + 1];
              newArgs[0] = subCommand;
              System.arraycopy(args, 0, newArgs, 1, args.length);
              // The server (if requested) has already been initialized.
              return execute(newArgs, false);
            }
          }
          else
          {
            println(ERR_REPLICATION_VALID_SUBCOMMAND_NOT_FOUND.get(
                "--"+ToolConstants.OPTION_LONG_NO_PROMPT));
            println(Message.raw(argParser.getUsage()));
            returnValue = ERROR_USER_DATA;
            subcommandLaunched = false;
          }
        }


        // Display the log file only if the operation is successful (when there
        // is a critical error this is already displayed).
        if (subcommandLaunched && (returnValue == SUCCESSFUL) &&
            displayLogFileAtEnd(subCommand))
        {
          File logFile = ControlPanelLog.getLogFile();
          if (logFile != null)
          {
            println();
            println(INFO_GENERAL_SEE_FOR_DETAILS.get(logFile.getPath()));
            println();
          }
        }
      }
    }

    return returnValue.getReturnCode();
  }

  private boolean isFirstCallFromScript()
  {
    return FIRST_SCRIPT_CALL.equals(System.getProperty(SCRIPT_CALL_STATUS));
  }

  private void createArgumenParser() throws ArgumentException
  {
    argParser = new ReplicationCliArgumentParser(CLASS_NAME);
    argParser.initializeParser(getOutputStream());
  }

  /**
   * Based on the data provided in the command-line it enables replication
   * between two servers.
   * @return the error code if the operation failed and 0 if it was successful.
   */
  private ReplicationCliReturnCode enableReplication()
  {
    ReplicationCliReturnCode returnValue;
    EnableReplicationUserData uData = new EnableReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          returnValue = enableReplication(uData);
        }
        else
        {
          returnValue = USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = enableReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line it disables replication
   * in the server.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode disableReplication()
  {
    ReplicationCliReturnCode returnValue;
    DisableReplicationUserData uData = new DisableReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          returnValue = disableReplication(uData);
        }
        else
        {
          returnValue = USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = disableReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line initialize the contents
   * of the whole replication topology.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode initializeAllReplication()
  {
    ReplicationCliReturnCode returnValue;
    InitializeAllReplicationUserData uData =
      new InitializeAllReplicationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = initializeAllReplication(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = initializeAllReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line execute the pre external
   * initialization operation.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode preExternalInitialization()
  {
    ReplicationCliReturnCode returnValue;
    PreExternalInitializationUserData uData =
      new PreExternalInitializationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = preExternalInitialization(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = preExternalInitialization(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line execute the post external
   * initialization operation.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode postExternalInitialization()
  {
    ReplicationCliReturnCode returnValue;
    PostExternalInitializationUserData uData =
      new PostExternalInitializationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = postExternalInitialization(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = postExternalInitialization(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line it displays replication
   * status.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode statusReplication()
  {
    ReplicationCliReturnCode returnValue;
    StatusReplicationUserData uData = new StatusReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          returnValue = statusReplication(uData);
        }
        else
        {
          returnValue = USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = statusReplication(uData);
    }
    return returnValue;
  }

  /**
   * Based on the data provided in the command-line it displays replication
   * status.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode purgeHistorical()
  {
    ReplicationCliReturnCode returnValue;
    PurgeHistoricalUserData uData = new PurgeHistoricalUserData();

    if (argParser.isInteractive())
    {
      uData = new PurgeHistoricalUserData();
      if (promptIfRequired(uData))
      {
        returnValue = purgeHistorical(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = purgeHistorical(uData);
    }
    return returnValue;
  }

  /**
   * Initializes the contents of the provided purge historical replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the purge historical replication user data object to be
   * initialized.
   */
  private void initializeWithArgParser(PurgeHistoricalUserData uData)
  {
    PurgeHistoricalUserData.initializeWithArgParser(uData, argParser);
  }

  private ReplicationCliReturnCode purgeHistorical(
      PurgeHistoricalUserData uData)
  {
      ReplicationCliReturnCode returnValue;
      if (uData.isOnline())
      {
        returnValue = purgeHistoricalRemotely(uData);
      }
      else
      {
        returnValue = purgeHistoricalLocally(uData);
      }
      return returnValue;
  }

  private ReplicationCliReturnCode purgeHistoricalLocally(
      PurgeHistoricalUserData uData)
  {
    ReplicationCliReturnCode returnValue;
    LinkedList<String> baseDNs = uData.getBaseDNs();
    checkSuffixesForLocalPurgeHistorical(baseDNs, false);
    if (!baseDNs.isEmpty())
    {
      uData.setBaseDNs(baseDNs);
      printPurgeHistoricalEquivalentIfRequired(uData);

      try
      {
        returnValue = purgeHistoricalLocallyTask(uData);
      }
      catch (ReplicationCliException rce)
      {
        println();
        println(getCriticalExceptionMessage(rce));
        returnValue = rce.getErrorCode();
        LOG.log(Level.SEVERE, "Complete error stack:", rce);
      }
    }
    else
    {
      returnValue = HISTORICAL_CANNOT_BE_PURGED_ON_BASEDN;
    }

    return returnValue;
  }

  private void printPurgeProgressMessage(PurgeHistoricalUserData uData)
  {
    String separator =  formatter.getLineBreak().toString() +
    formatter.getTab().toString();
    printlnProgress();
    Message msg = formatter.getFormattedProgress(
        INFO_PROGRESS_PURGE_HISTORICAL.get(separator,
            Utils.getStringFromCollection(uData.getBaseDNs(), separator)));
    printProgress(msg);
    printlnProgress();

  }

  private ReplicationCliReturnCode purgeHistoricalLocallyTask(
      PurgeHistoricalUserData uData)
  throws ReplicationCliException
  {
    ReplicationCliReturnCode returnCode = ReplicationCliReturnCode.SUCCESSFUL;
    if (isFirstCallFromScript())
    {
      // Launch the process: launch dsreplication in non-interactive mode with
      // the recursive property set.
      ArrayList<String> args = new ArrayList<String>();
      args.add(getCommandLinePath(getCommandName()));
      args.add(ReplicationCliArgumentParser.PURGE_HISTORICAL_SUBCMD_NAME);
      args.add("--"+argParser.noPromptArg.getLongIdentifier());
      args.add("--"+argParser.maximumDurationArg.getLongIdentifier());
      args.add(String.valueOf(uData.getMaximumDuration()));
      for (String baseDN : uData.getBaseDNs())
      {
        args.add("--"+argParser.baseDNsArg.getLongIdentifier());
        args.add(baseDN);
      }
      ProcessBuilder pb = new ProcessBuilder(args);
      // Use the java args in the script.
      Map<String, String> env = pb.environment();
      env.put("RECURSIVE_LOCAL_CALL", "true");
      try
      {
        Process process = pb.start();
        ProcessReader outReader =
            new ProcessReader(process, getOutputStream(), false);
        ProcessReader errReader =
            new ProcessReader(process, getErrorStream(), true);

        outReader.startReading();
        errReader.startReading();

        int code = process.waitFor();
        for (ReplicationCliReturnCode c : ReplicationCliReturnCode.values())
        {
          if (c.getReturnCode() == code)
          {
            returnCode = c;
            break;
          }
        }
      }
      catch (Exception e)
      {
        Message msg = ERR_LAUNCHING_PURGE_HISTORICAL.get();
        ReplicationCliReturnCode code = ERROR_LAUNCHING_PURGE_HISTORICAL;
        throw new ReplicationCliException(
            getThrowableMsg(msg, e), code, e);
      }
    }
    else
    {
      printPurgeProgressMessage(uData);
      LocalPurgeHistorical localPurgeHistorical =
        new LocalPurgeHistorical(uData, this, formatter,
            argParser.getConfigFile(),
            argParser.getConfigClass());
      returnCode = localPurgeHistorical.execute();

      if (returnCode == ReplicationCliReturnCode.SUCCESSFUL)
      {
        printSuccessMessage(uData, null);
      }
    }
    return returnCode;
  }

  private void printPurgeHistoricalEquivalentIfRequired(
      PurgeHistoricalUserData uData)
  {
    if (mustPrintCommandBuilder())
    {
      try
      {
        CommandBuilder commandBuilder = createCommandBuilder(
            ReplicationCliArgumentParser.PURGE_HISTORICAL_SUBCMD_NAME,
            uData);
        printCommandBuilder(commandBuilder);
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error printing equivalent command-line: "+t,
            t);
      }
    }
  }

  private ReplicationCliReturnCode purgeHistoricalRemotely(
      PurgeHistoricalUserData uData)
  {
    ReplicationCliReturnCode returnValue;
    InitialLdapContext ctx = null;

    // Connect to the provided server
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          useSSL, useStartTLS,
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getConnectTimeout(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort =
        getServerRepresentation(uData.getHostName(), uData.getPort());
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }

    if (ctx != null)
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForPurgeHistorical(baseDNs, ctx, false);
      if (!baseDNs.isEmpty())
      {
        uData.setBaseDNs(baseDNs);
        printPurgeHistoricalEquivalentIfRequired(uData);

        try
        {
          returnValue = purgeHistoricalRemoteTask(ctx, uData);
        }
        catch (ReplicationCliException rce)
        {
          println();
          println(getCriticalExceptionMessage(rce));
          returnValue = rce.getErrorCode();
          LOG.log(Level.SEVERE, "Complete error stack:", rce);
        }
      }
      else
      {
        returnValue = HISTORICAL_CANNOT_BE_PURGED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  private void printSuccessMessage(PurgeHistoricalUserData uData, String taskID)
  {
    printlnProgress();
    if (!uData.isOnline())
    {
      printProgress(
          INFO_PROGRESS_PURGE_HISTORICAL_FINISHED_PROCEDURE.get());
    }
    else if (uData.getTaskSchedule().isStartNow())
    {
      printProgress(INFO_TASK_TOOL_TASK_SUCESSFULL.get(
          INFO_PURGE_HISTORICAL_TASK_NAME.get(),
          taskID));
    }
    else if (uData.getTaskSchedule().getStartDate() != null)
    {
      printProgress(INFO_TASK_TOOL_TASK_SCHEDULED_FUTURE.get(
          INFO_PURGE_HISTORICAL_TASK_NAME.get(),
          taskID,
          StaticUtils.formatDateTimeString(
              uData.getTaskSchedule().getStartDate())));
    }
    else
    {
      printProgress(INFO_TASK_TOOL_RECURRING_TASK_SCHEDULED.get(
          INFO_PURGE_HISTORICAL_TASK_NAME.get(),
          taskID));
    }

    printlnProgress();
  }

  /**
   * Launches the purge historical operation using the
   * provided connection.
   * @param ctx the connection to the server.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private ReplicationCliReturnCode purgeHistoricalRemoteTask(
      InitialLdapContext ctx,
      PurgeHistoricalUserData uData)
  throws ReplicationCliException
  {
    printPurgeProgressMessage(uData);
    ReplicationCliReturnCode returnCode = ReplicationCliReturnCode.SUCCESSFUL;
    boolean taskCreated = false;
    boolean isOver = false;
    String dn = null;
    String taskID = null;
    while (!taskCreated)
    {
      BasicAttributes attrs = PurgeHistoricalUserData.getTaskAttributes(uData);
      dn = PurgeHistoricalUserData.getTaskDN(attrs);
      taskID = PurgeHistoricalUserData.getTaskID(attrs);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        Message msg = ERR_LAUNCHING_PURGE_HISTORICAL.get();
        ReplicationCliReturnCode code = ERROR_LAUNCHING_PURGE_HISTORICAL;
        throw new ReplicationCliException(
            getThrowableMsg(msg, ne), code, ne);
      }
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setCountLimit(1);
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-log-message",
            "ds-task-state",
            "ds-task-purge-conflicts-historical-purged-values-count",
            "ds-task-purge-conflicts-historical-purge-completed-in-time",
            "ds-task-purge-conflicts-historical-purge-completed-in-time",
            "ds-task-purge-conflicts-historical-last-purged-changenumber"
        });
    String lastLogMsg = null;

    // Polling only makes sense when we are recurrently scheduling a task
    // or the task is being executed now.
    while (!isOver && (uData.getTaskSchedule().getStartDate() == null))
    {
      try
      {
        Thread.sleep(500);
      }
      catch (Throwable t)
      {
      }
      try
      {
        NamingEnumeration<SearchResult> res =
          ctx.search(dn, filter, searchControls);
        SearchResult sr = null;
        try
        {
          sr = res.next();
        }
        finally
        {
          res.close();
        }
        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          String server = ConnectionUtils.getHostPort(ctx);
          if (lastLogMsg == null)
          {
            errorMsg = INFO_ERROR_DURING_PURGE_HISTORICAL_NO_LOG.get(
                state, server);
          }
          else
          {
            errorMsg = INFO_ERROR_DURING_PURGE_HISTORICAL_LOG.get(
                lastLogMsg, state, server);
          }

          if (helper.isCompletedWithErrors(state))
          {
            LOG.log(Level.WARNING, "Completed with error: "+errorMsg);
            println(errorMsg);
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            LOG.log(Level.WARNING, "Error: "+errorMsg);
            ReplicationCliReturnCode code = ERROR_LAUNCHING_PURGE_HISTORICAL;
            throw new ReplicationCliException(errorMsg, code, null);
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
      }
      catch (NamingException ne)
      {
        Message msg = ERR_POOLING_PURGE_HISTORICAL.get();
        throw new ReplicationCliException(
          getThrowableMsg(msg, ne), ERROR_CONNECTING, ne);
      }
    }

    if (returnCode == ReplicationCliReturnCode.SUCCESSFUL)
    {
      printSuccessMessage(uData, taskID);
    }
    return returnCode;
  }

  /**
   * Checks that historical can actually be purged in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated with the base DNs that the user provided interactively.
   * @param ctx connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be purged.
   */
  private void checkSuffixesForPurgeHistorical(Collection<String> suffixes,
      InitialLdapContext ctx, boolean interactive)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>();
    TreeSet<String> notReplicatedSuffixes = new TreeSet<String>();

    Collection<ReplicaDescriptor> replicas = getReplicas(ctx);
    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        availableSuffixes.add(dn);
      }
      else
      {
        notReplicatedSuffixes.add(dn);
      }
    }

    checkSuffixesForPurgeHistorical(suffixes, availableSuffixes,
        notReplicatedSuffixes, interactive);
  }

  /**
   * Checks that historical can actually be purged in the provided baseDNs
   * for the local server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated with the base DNs that the user provided interactively.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be purged.
   */
  private void checkSuffixesForLocalPurgeHistorical(Collection<String> suffixes,
      boolean interactive)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>();
    TreeSet<String> notReplicatedSuffixes = new TreeSet<String>();

    Collection<ReplicaDescriptor> replicas = getLocalReplicas();

    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        availableSuffixes.add(dn);
      }
      else
      {
        notReplicatedSuffixes.add(dn);
      }
    }

    checkSuffixesForPurgeHistorical(suffixes, availableSuffixes,
        notReplicatedSuffixes, interactive);
  }

  private Collection<ReplicaDescriptor> getLocalReplicas()
  {
    Collection<ReplicaDescriptor> replicas = new ArrayList<ReplicaDescriptor>();
    ConfigFromFile configFromFile = new ConfigFromFile();
    configFromFile.readConfiguration();
    Collection<BackendDescriptor> backends = configFromFile.getBackends();
    for (BackendDescriptor backend : backends)
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        SuffixDescriptor suffix = new SuffixDescriptor();
        suffix.setDN(baseDN.getDn().toString());

        ReplicaDescriptor replica = new ReplicaDescriptor();

        if (baseDN.getType() == BaseDNDescriptor.Type.REPLICATED)
        {
          replica.setReplicationId(baseDN.getReplicaID());
        }
        else
        {
          replica.setReplicationId(-1);
        }
        replica.setBackendName(backend.getBackendID());
        replica.setSuffix(suffix);
        suffix.setReplicas(Collections.singleton(replica));

        replicas.add(replica);
      }
    }
    return replicas;
  }

  private void checkSuffixesForPurgeHistorical(Collection<String> suffixes,
      Collection<String> availableSuffixes,
      Collection<String> notReplicatedSuffixes,
      boolean interactive)
  {
    if (availableSuffixes.isEmpty())
    {
      println();
      println(ERR_NO_SUFFIXES_AVAILABLE_TO_PURGE_HISTORICAL.get());
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<String> notFound = new TreeSet<String>();
      TreeSet<String> alreadyNotReplicated = new TreeSet<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          boolean notReplicated = false;
          for (String s : notReplicatedSuffixes)
          {
            if (Utils.areDnsEqual(s, dn))
            {
              notReplicated = true;
              break;
            }
          }
          if (notReplicated)
          {
            alreadyNotReplicated.add(dn);
          }
          else
          {
            notFound.add(dn);
          }
        }
      }
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_REPLICATION_PURGE_SUFFIXES_NOT_FOUND.get(
                Utils.getStringFromCollection(notFound,
                    Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        boolean confirmationLimitReached = false;
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            println(ERR_NO_SUFFIXES_AVAILABLE_TO_PURGE_HISTORICAL.get());
            break;
          }
          else
          {
            println();
            println(ERR_NO_SUFFIXES_SELECTED_TO_PURGE_HISTORICAL.get());
            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                try
                {
                  if (askConfirmation(
                      INFO_REPLICATION_PURGE_HISTORICAL_PROMPT.get(dn), true,
                      LOG))
                  {
                    suffixes.add(dn);
                  }
                }
                catch (CLIException ce)
                {
                  println(ce.getMessageObject());
                  confirmationLimitReached = true;
                  break;
                }
              }
            }
          }
          if (confirmationLimitReached)
          {
            suffixes.clear();
            break;
          }
        }
      }
    }
  }

  /**
   * Based on the data provided in the command-line it initializes replication
   * between two servers.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode initializeReplication()
  {
    ReplicationCliReturnCode returnValue;
    InitializeReplicationUserData uData = new InitializeReplicationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        returnValue = initializeReplication(uData);
      }
      else
      {
        returnValue = USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      returnValue = initializeReplication(uData);
    }
    return returnValue;
  }


  /**
   * Updates the contents of the provided PurgeHistoricalUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user canceled the operation.
   */
  private boolean promptIfRequired(PurgeHistoricalUserData uData)
  {
    boolean cancelled = false;
    boolean onlineSet = false;

    boolean firstTry = true;
    Boolean serverRunning = null;

    InitialLdapContext ctx = null;
    while (!cancelled && !onlineSet)
    {
      boolean promptForConnection = false;
      if (argParser.connectionArgumentsPresent() && firstTry)
      {
        promptForConnection = true;
      }
      else
      {
        if (serverRunning == null)
        {
          serverRunning = Utilities.isServerRunning(
              Installation.getLocal().getInstanceDirectory());
        }
        if (!serverRunning)
        {
          try
          {
            printlnProgress();
            promptForConnection =
              !askConfirmation(
                  INFO_REPLICATION_PURGE_HISTORICAL_LOCAL_PROMPT.get(),
                  true, LOG);
          }
          catch (CLIException ce)
          {
            println(ce.getMessageObject());
            cancelled = true;
          }
        }
        else
        {
          promptForConnection = true;
        }
      }
      if (promptForConnection)
      {
        try
        {
          ci.run();
          String host = ci.getHostName();
          int port = ci.getPortNumber();
          String adminUid = ci.getAdministratorUID();
          String adminPwd = ci.getBindPassword();

          ctx = createInitialLdapContextInteracting(ci);
          if (ctx == null)
          {
            cancelled = true;
          }
          else
          {
            uData.setOnline(true);
            uData.setAdminUid(adminUid);
            uData.setAdminPwd(adminPwd);
            uData.setHostName(host);
            uData.setPort(port);
            onlineSet = true;
          }
        }
        catch (ClientException ce)
        {
          LOG.log(Level.WARNING, "Client exception "+ce);
          println();
          println(ce.getMessageObject());
          println();
          ci.resetConnectionArguments();
        }
        catch (ArgumentException ae)
        {
          LOG.log(Level.WARNING, "Argument exception "+ae);
          println();
          println(ae.getMessageObject());
          println();
          cancelled = true;
        }
      }
      else
      {
        uData.setOnline(false);
        onlineSet = true;
      }
      firstTry = false;
    }

    if (!cancelled)
    {
      int maximumDuration = argParser.getMaximumDuration();
      /* Prompt for maximum duration */
      if (!argParser.maximumDurationArg.isPresent())
      {
        printlnProgress();
        maximumDuration = askInteger(
            INFO_REPLICATION_PURGE_HISTORICAL_MAXIMUM_DURATION_PROMPT.get(),
            argParser.getDefaultMaximumDuration(), LOG);
      }
      uData.setMaximumDuration(maximumDuration);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      if (uData.isOnline())
      {
        checkSuffixesForPurgeHistorical(suffixes, ctx, true);
      }
      else
      {
        checkSuffixesForLocalPurgeHistorical(suffixes, true);
      }
      cancelled = suffixes.isEmpty();
      uData.setBaseDNs(suffixes);
    }

    if (uData.isOnline() && !cancelled)
    {
      List<? extends TaskEntry> taskEntries = getAvailableTaskEntries(ctx);

      TaskScheduleInteraction interaction =
        new TaskScheduleInteraction(uData.getTaskSchedule(), argParser.taskArgs,
            this, INFO_PURGE_HISTORICAL_TASK_NAME.get());
      interaction.setFormatter(formatter);
      interaction.setTaskEntries(taskEntries);
      try
      {
        interaction.run();
      }
      catch (CLIException ce)
      {
        println(ce.getMessageObject());
        cancelled = true;
      }
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  private List<? extends TaskEntry> getAvailableTaskEntries(
      InitialLdapContext ctx)
  {
    List<TaskEntry> taskEntries = new ArrayList<TaskEntry>();
    List<OpenDsException> exceptions = new ArrayList<OpenDsException>();
    ConfigFromDirContext cfg = new ConfigFromDirContext();
    cfg.updateTaskInformation(ctx, exceptions, taskEntries);
    for (OpenDsException ode : exceptions)
    {
      LOG.log(Level.WARNING, "Error retrieving task entries: "+ode, ode);
    }
    return taskEntries;
  }

  /**
   * Updates the contents of the provided EnableReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   * @throws ReplicationCliException if a critical error occurs reading the
   * ADS.
   */
  private boolean promptIfRequired(EnableReplicationUserData uData)
  throws ReplicationCliException
  {
    boolean cancelled = false;

    boolean administratorDefined = false;

    ci.setUseAdminOrBindDn(true);

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    /*
     * Try to connect to the first server.
     */
    String host1 = argParser.getHostName1();
    int port1 = argParser.getPort1();
    String bindDn1 = argParser.getBindDn1();
    String pwd1 = argParser.getBindPassword1();
    String pwd = null;
    LinkedHashMap<String, String> pwdFile = null;
    if (argParser.bindPassword1Arg.isPresent())
    {
      pwd = argParser.bindPassword1Arg.getValue();
    }
    else if (argParser.bindPasswordFile1Arg.isPresent())
    {
      pwdFile = argParser.bindPasswordFile1Arg.getNameToValueMap();
    }
    else if (bindDn1 == null)
    {
      pwd = adminPwd;
      if (argParser.getSecureArgsList().bindPasswordFileArg.isPresent())
      {
        pwdFile = argParser.getSecureArgsList().bindPasswordFileArg.
          getNameToValueMap();
      }
    }

    /*
     * Use a copy of the argument properties since the map might be cleared
     * in initializeGlobalArguments.
     */
    ci.initializeGlobalArguments(host1, port1, adminUid,
        bindDn1, pwd,
        pwdFile == null ? null : new LinkedHashMap<String, String>(pwdFile));
    InitialLdapContext ctx1 = null;

    while ((ctx1 == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_ENABLE_HOST1_CONNECTION_PARAMETERS.get());
        ci.run();
        host1 = ci.getHostName();
        port1 = ci.getPortNumber();
        if (ci.getProvidedAdminUID() != null)
        {
          adminUid = ci.getProvidedAdminUID();
          if (ci.getProvidedBindDN() == null)
          {
            // If the explicit bind DN is not null, the password corresponds
            // to that bind DN.  We are in the case where the user provides
            // bind DN on first server and admin UID globally.
            adminPwd = ci.getBindPassword();
          }
        }
        bindDn1 = ci.getBindDN();
        pwd1 = ci.getBindPassword();

        ctx1 = createInitialLdapContextInteracting(ci);

        if (ctx1 == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }

    if (!cancelled)
    {
      uData.setHostName1(host1);
      uData.setPort1(port1);
      uData.setBindDn1(bindDn1);
      uData.setPwd1(pwd1);
    }
    int replicationPort1 = -1;
    boolean secureReplication1 = argParser.isSecureReplication1();
    boolean configureReplicationServer1 =
      !argParser.noReplicationServer1Arg.isPresent();
    boolean configureReplicationDomain1 =
      !argParser.onlyReplicationServer1Arg.isPresent();
    if (ctx1 != null)
    {
      int repPort1 = getReplicationPort(ctx1);
      boolean replicationServer1Configured = repPort1 > 0;
      if (replicationServer1Configured && !configureReplicationServer1)
      {
        try
        {
          if (!askConfirmation(
              INFO_REPLICATION_SERVER_CONFIGURED_WARNING_PROMPT.
              get(ConnectionUtils.getHostPort(ctx1), repPort1), false, LOG))
          {
            cancelled = true;
          }
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
      }

      // Try to get the replication port for server 1 only if it is required.
      if (!replicationServer1Configured && configureReplicationServer1 &&
          !cancelled)
      {
        if (argParser.advancedArg.isPresent() &&
            configureReplicationDomain1)
        {
          // Only ask if the replication domain will be configured (if not
          // the replication server MUST be configured).
          try
          {
            configureReplicationServer1 = askConfirmation(
                INFO_REPLICATION_ENABLE_REPLICATION_SERVER1_PROMPT.get(),
                true, LOG);
          }
          catch (CLIException ce)
          {
            println(ce.getMessageObject());
            cancelled = true;
          }
        }
      }
      if (!cancelled &&
          !replicationServer1Configured && configureReplicationServer1)
      {
        boolean tryWithDefault = argParser.getReplicationPort1() != -1;
        while (replicationPort1 == -1)
        {
          if (tryWithDefault)
          {
            replicationPort1 = argParser.getReplicationPort1();
            tryWithDefault = false;
          }
          else
          {
            replicationPort1 = askPort(
                INFO_REPLICATION_ENABLE_REPLICATIONPORT1_PROMPT.get(),
                argParser.getDefaultReplicationPort1(), LOG);
            println();
          }
          if (!argParser.skipReplicationPortCheck() && Utils.isLocalHost(host1))
          {
            if (!SetupUtils.canUseAsPort(replicationPort1))
            {
              println();
              println(getCannotBindToPortError(replicationPort1));
              println();
              replicationPort1 = -1;
            }
          }
          else
          {
            // This is something that we must do in any case... this test is
            // already included when we call SetupUtils.canUseAsPort
            if (replicationPort1 == port1)
            {
              println();
              println(
                  ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                      host1, String.valueOf(replicationPort1)));
              println();
              replicationPort1 = -1;
            }
          }
        }
        if (!secureReplication1)
        {
          try
          {
            secureReplication1 =
              askConfirmation(INFO_REPLICATION_ENABLE_SECURE1_PROMPT.get(
                String.valueOf(replicationPort1)), false, LOG);
          }
          catch (CLIException ce)
          {
            println(ce.getMessageObject());
            cancelled = true;
          }
          println();
        }
      }
      if (!cancelled &&
          configureReplicationDomain1 &&
          configureReplicationServer1 &&
          argParser.advancedArg.isPresent())
      {
        // Only necessary to ask if the replication server will be configured
        try
        {
          configureReplicationDomain1 = askConfirmation(
              INFO_REPLICATION_ENABLE_REPLICATION_DOMAIN1_PROMPT.get(),
              true, LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
      }
      // If the server contains an ADS. Try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // enableReplication(EnableReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx1};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, true);
      ctx1 = aux[0];
      if (!cancelled)
      {
        administratorDefined |= hasAdministrator(ctx1);
        if (uData.getAdminPwd() != null)
        {
          adminPwd = uData.getAdminPwd();
        }
      }
    }
    uData.setReplicationPort1(replicationPort1);
    uData.setSecureReplication1(secureReplication1);
    uData.setConfigureReplicationServer1(configureReplicationServer1);
    uData.setConfigureReplicationDomain1(configureReplicationDomain1);
    firstServerCommandBuilder = new CommandBuilder(null);
    if (mustPrintCommandBuilder())
    {
      firstServerCommandBuilder.append(ci.getCommandBuilder());
    }

    /*
     * Prompt for information on the second server.
     */
    String host2 = null;
    int port2 = -1;
    String bindDn2 = null;
    String pwd2 = null;
    ci.resetHeadingDisplayed();

    boolean doNotDisplayFirstError = false;

    if (!cancelled)
    {
      host2 = argParser.getHostName2();
      port2 = argParser.getPort2();
      bindDn2 = argParser.getBindDn2();
      pwd2 = argParser.getBindPassword2();

      pwdFile = null;
      pwd = null;
      if (argParser.bindPassword2Arg.isPresent())
      {
        pwd = argParser.bindPassword2Arg.getValue();
      }
      else if (argParser.bindPasswordFile2Arg.isPresent())
      {
        pwdFile = argParser.bindPasswordFile2Arg.getNameToValueMap();
      }
      else if (bindDn2 == null)
      {
        doNotDisplayFirstError = true;
        pwd = adminPwd;
        if (argParser.getSecureArgsList().bindPasswordFileArg.isPresent())
        {
          pwdFile = argParser.getSecureArgsList().bindPasswordFileArg.
            getNameToValueMap();
        }
      }

      /*
       * Use a copy of the argument properties since the map might be cleared
       * in initializeGlobalArguments.
       */
      ci.initializeGlobalArguments(host2, port2, adminUid,
          bindDn2, pwd,
          pwdFile == null ? null : new LinkedHashMap<String, String>(pwdFile));
    }
    InitialLdapContext ctx2 = null;

    while ((ctx2 == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_ENABLE_HOST2_CONNECTION_PARAMETERS.get());
        ci.run();
        host2 = ci.getHostName();
        port2 = ci.getPortNumber();
        if (ci.getProvidedAdminUID() != null)
        {
          adminUid = ci.getProvidedAdminUID();
          if (ci.getProvidedBindDN() == null)
          {
            // If the explicit bind DN is not null, the password corresponds
            // to that bind DN.  We are in the case where the user provides
            // bind DN on first server and admin UID globally.
            adminPwd = ci.getBindPassword();
          }
        }
        bindDn2 = ci.getBindDN();
        pwd2 = ci.getBindPassword();

        boolean error = false;
        if (host1.equalsIgnoreCase(host2))
        {
          if (port1 == port2)
          {
            port2 = -1;
            Message message = ERR_REPLICATION_ENABLE_SAME_SERVER_PORT.get(
                host1, String.valueOf(port1));
            println();
            println(message);
            println();
            error = true;
          }
        }

        if (!error)
        {
          ctx2 = createInitialLdapContextInteracting(ci, true);

          if (ctx2 == null)
          {
            cancelled = true;
          }
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        if (!doNotDisplayFirstError)
        {
          println();
          println(ce.getMessageObject());
          println();
          ci.resetConnectionArguments();
        }
        else
        {
          // Reset only the credential parameters.
          ci.resetConnectionArguments();
          ci.initializeGlobalArguments(host2, port2, null, null, null, null);
        }
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
      finally
      {
        doNotDisplayFirstError = false;
      }
    }

    if (!cancelled)
    {
      uData.setHostName2(host2);
      uData.setPort2(port2);
      uData.setBindDn2(bindDn2);
      uData.setPwd2(pwd2);
    }

    int replicationPort2 = -1;
    boolean secureReplication2 = argParser.isSecureReplication2();
    boolean configureReplicationServer2 =
      !argParser.noReplicationServer2Arg.isPresent();
    boolean configureReplicationDomain2 =
      !argParser.onlyReplicationServer2Arg.isPresent();
    if (ctx2 != null)
    {
      int repPort2 = getReplicationPort(ctx2);
      boolean replicationServer2Configured = repPort2 > 0;
      if (replicationServer2Configured && !configureReplicationServer2)
      {
        try
        {
          if (!askConfirmation(
              INFO_REPLICATION_SERVER_CONFIGURED_WARNING_PROMPT.
              get(ConnectionUtils.getHostPort(ctx2), repPort2), false, LOG))
          {
            cancelled = true;
          }
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
      }

      // Try to get the replication port for server 2 only if it is required.
      if (!replicationServer2Configured && configureReplicationServer2 &&
          !cancelled)
      {
        // Only ask if the replication domain will be configured (if not the
        // replication server MUST be configured).
        if (argParser.advancedArg.isPresent() &&
            configureReplicationDomain2)
        {
          try
          {
            configureReplicationServer2 = askConfirmation(
                INFO_REPLICATION_ENABLE_REPLICATION_SERVER2_PROMPT.get(),
                true, LOG);
          }
          catch (CLIException ce)
          {
            println(ce.getMessageObject());
            cancelled = true;
          }
        }
        if (!cancelled &&
            !replicationServer2Configured && configureReplicationServer2)
        {
          boolean tryWithDefault = argParser.getReplicationPort2() != -1;
          while (replicationPort2 == -1)
          {
            if (tryWithDefault)
            {
              replicationPort2 = argParser.getReplicationPort2();
              tryWithDefault = false;
            }
            else
            {
              replicationPort2 = askPort(
                  INFO_REPLICATION_ENABLE_REPLICATIONPORT2_PROMPT.get(),
                  argParser.getDefaultReplicationPort2(), LOG);
              println();
            }
            if (!argParser.skipReplicationPortCheck() &&
                Utils.isLocalHost(host2))
            {
              if (!SetupUtils.canUseAsPort(replicationPort2))
              {
                println();
                println(getCannotBindToPortError(replicationPort2));
                println();
                replicationPort2 = -1;
              }
            }
            else
            {
              // This is something that we must do in any case... this test is
              // already included when we call SetupUtils.canUseAsPort
              if (replicationPort2 == port2)
              {
                println();
                println(
                    ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                        host2, String.valueOf(replicationPort2)));
                replicationPort2 = -1;
              }
            }
            if (host1.equalsIgnoreCase(host2))
            {
              if ((replicationPort1 > 0) &&
                  (replicationPort1 == replicationPort2))
              {
                println();
                println(ERR_REPLICATION_SAME_REPLICATION_PORT.get(
                    String.valueOf(replicationPort2), host1));
                println();
                replicationPort2 = -1;
              }
            }
          }
          if (!secureReplication2)
          {
            try
            {
              secureReplication2 =
                askConfirmation(INFO_REPLICATION_ENABLE_SECURE2_PROMPT.get(
                    String.valueOf(replicationPort2)), false, LOG);
            }
            catch (CLIException ce)
            {
              println(ce.getMessageObject());
              cancelled = true;
            }
            println();
          }
        }
      }
      if (!cancelled &&
          configureReplicationDomain2 &&
          configureReplicationServer2 &&
          argParser.advancedArg.isPresent())
      {
        // Only necessary to ask if the replication server will be configured
        try
        {
          configureReplicationDomain2 = askConfirmation(
              INFO_REPLICATION_ENABLE_REPLICATION_DOMAIN2_PROMPT.get(),
              true, LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
      }
      // If the server contains an ADS. Try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // enableReplication(EnableReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx2};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, false);
      ctx2 = aux[0];
      if (!cancelled)
      {
        administratorDefined |= hasAdministrator(ctx2);
      }
    }
    uData.setReplicationPort2(replicationPort2);
    uData.setSecureReplication2(secureReplication2);
    uData.setConfigureReplicationServer2(configureReplicationServer2);
    uData.setConfigureReplicationDomain2(configureReplicationDomain2);

    // If the adminUid and adminPwd are not set in the EnableReplicationUserData
    // object, that means that there are no administrators and that they
    // must be created. The adminUId and adminPwd are updated inside
    // loadADSAndAcceptCertificates.
    boolean promptedForAdmin = false;

    // There is a case where we haven't had need for the administrator
    // credentials even if the administrators are defined: where all the servers
    // can be accessed with another user (for instance if all the server have
    // defined cn=directory manager and all the entries have the same password).
    if (!cancelled && (uData.getAdminUid() == null) && !administratorDefined)
    {
      if (adminUid == null)
      {
        println(INFO_REPLICATION_ENABLE_ADMINISTRATOR_MUST_BE_CREATED.get());
        promptedForAdmin = true;
        adminUid= askForAdministratorUID(
            argParser.getDefaultAdministratorUID(), LOG);
        println();
      }
      uData.setAdminUid(adminUid);
    }

    if (uData.getAdminPwd() == null)
    {
      uData.setAdminPwd(adminPwd);
    }
    if (!cancelled && (uData.getAdminPwd() == null) && !administratorDefined)
    {
      adminPwd = null;
      int nPasswordPrompts = 0;
      while (adminPwd == null)
      {
        if (nPasswordPrompts > CONFIRMATION_MAX_TRIES)
        {
          println(ERR_CONFIRMATION_TRIES_LIMIT_REACHED.get(
              CONFIRMATION_MAX_TRIES));
          cancelled = true;
          break;
        }
        nPasswordPrompts ++;
        if (!promptedForAdmin)
        {
          println();
          println(INFO_REPLICATION_ENABLE_ADMINISTRATOR_MUST_BE_CREATED.get());
          println();
        }
        while (adminPwd == null)
        {
          adminPwd = askForAdministratorPwd(LOG);
          println();
        }
        String adminPwdConfirm = null;
        while (adminPwdConfirm == null)
        {
          adminPwdConfirm =
          readPassword(INFO_ADMINISTRATOR_PWD_CONFIRM_PROMPT.get(), LOG);
          println();
        }
        if (!adminPwd.equals(adminPwdConfirm))
        {
          println();
          println(ERR_ADMINISTRATOR_PWD_DO_NOT_MATCH.get());
          println();
          adminPwd = null;
        }
      }
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForEnableReplication(suffixes, ctx1, ctx2, true, uData);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (ctx1 != null)
    {
      try
      {
        ctx1.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctx2 != null)
    {
      try
      {
        ctx2.close();
      }
      catch (Throwable t)
      {
      }
    }

    uData.setReplicateSchema(!argParser.noSchemaReplication());

    return !cancelled;
  }

  /**
   * Updates the contents of the provided DisableReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   * @throws ReplicationCliException if there is a critical error reading the
   * ADS.
   */
  private boolean promptIfRequired(DisableReplicationUserData uData)
  throws ReplicationCliException
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();
    String bindDn = argParser.getBindDNToDisable();

    // This is done because we want to ask explicitly for this

    String host = argParser.getHostNameToDisable();
    int port = argParser.getPortToDisable();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.setUseAdminOrBindDn(true);
        ci.run();
        host = ci.getHostName();
        port = ci.getPortNumber();
        bindDn = ci.getProvidedBindDN();
        adminUid = ci.getProvidedAdminUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }

    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setAdminUid(adminUid);
      uData.setBindDn(bindDn);
      uData.setAdminPwd(adminPwd);
    }
    if ((ctx != null) && (adminUid != null))
    {
      // If the server contains an ADS, try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // disableReplication(DisableReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, false);
      ctx = aux[0];
    }

    boolean disableAll = argParser.disableAllArg.isPresent();
    boolean disableReplicationServer =
      argParser.disableReplicationServerArg.isPresent();
    if (disableAll ||
        (argParser.advancedArg.isPresent() &&
        argParser.getBaseDNs().isEmpty() &&
        !disableReplicationServer))
    {
      try
      {
        disableAll = askConfirmation(INFO_REPLICATION_PROMPT_DISABLE_ALL.get(),
          disableAll, LOG);
      }
      catch (CLIException ce)
      {
        println(ce.getMessageObject());
        cancelled = true;
      }
    }
    int repPort = getReplicationPort(ctx);
    if (!disableAll &&
        (argParser.advancedArg.isPresent() ||
        disableReplicationServer))
    {
      if (repPort > 0)
      {
        try
        {
          disableReplicationServer = askConfirmation(
              INFO_REPLICATION_PROMPT_DISABLE_REPLICATION_SERVER.get(repPort),
              disableReplicationServer,
              LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
      }
    }
    if (disableReplicationServer && repPort < 0)
    {
      disableReplicationServer = false;
      try
      {
        cancelled = askConfirmation(
            INFO_REPLICATION_PROMPT_NO_REPLICATION_SERVER_TO_DISABLE.get(
                ConnectionUtils.getHostPort(ctx)),
                false,
                LOG);
      }
      catch (CLIException ce)
      {
        println(ce.getMessageObject());
        cancelled = true;
      }
    }
    if (repPort > 0 && disableAll)
    {
      disableReplicationServer = true;
    }
    uData.setDisableAll(disableAll);
    uData.setDisableReplicationServer(disableReplicationServer);
    if (!cancelled && !disableAll)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();

      checkSuffixesForDisableReplication(suffixes, ctx, true,
          !disableReplicationServer, !disableReplicationServer);
      cancelled = suffixes.isEmpty() && !disableReplicationServer;

      uData.setBaseDNs(suffixes);

      if (!uData.disableReplicationServer() && repPort > 0 &&
          disableAllBaseDns(ctx, uData) && !argParser.advancedArg.isPresent())
      {
        try
        {
          uData.setDisableReplicationServer(askConfirmation(
         INFO_REPLICATION_DISABLE_ALL_SUFFIXES_DISABLE_REPLICATION_SERVER.get(
             ConnectionUtils.getHostPort(ctx), repPort), true, LOG));
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
      }
    }

    if (!cancelled)
    {
      // Ask for confirmation to disable if not already done.
      boolean disableADS = false;
      boolean disableSchema = false;
      for (String dn : uData.getBaseDNs())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          disableADS = true;
        }
        else if (Utils.areDnsEqual(Constants.SCHEMA_DN, dn))
        {
          disableSchema = true;
        }
      }
      if (disableADS)
      {
        println();
        try
        {
          cancelled = !askConfirmation(INFO_REPLICATION_CONFIRM_DISABLE_ADS.get(
              ADSContext.getAdministrationSuffixDN()), true, LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
        println();
      }
      if (disableSchema)
      {
        println();
        try
        {
          cancelled = !askConfirmation(
              INFO_REPLICATION_CONFIRM_DISABLE_SCHEMA.get(), true, LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
        println();
      }
      if (!disableSchema && !disableADS)
      {
        println();
        try
        {
          if (uData.disableAll())
          {
            // Another confirmation is redundant: we already asked the user...
          }
          else
          {
            if (!uData.getBaseDNs().isEmpty())
            {
               cancelled = !askConfirmation(
                INFO_REPLICATION_CONFIRM_DISABLE_GENERIC.get(), true,
                LOG);
            }
            else
            {
              // Another confirmation for the replication server is redundant.
            }
          }
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
        println();
      }
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided InitializeAllReplicationUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(InitializeAllReplicationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToInitializeAll();
    int port = argParser.getPortToInitializeAll();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_INITIALIZE_SOURCE_CONNECTION_PARAMETERS.get());
        ci.run();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctx, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (!cancelled)
    {
      // Ask for confirmation to initialize.
      boolean initializeADS = false;
      for (String dn : uData.getBaseDNs())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          initializeADS = true;
        }
      }
      String hostPortSource = ConnectionUtils.getHostPort(ctx);
      if (initializeADS)
      {
        println();
        try
        {
          cancelled = !askConfirmation(
              INFO_REPLICATION_CONFIRM_INITIALIZE_ALL_ADS.get(
                  ADSContext.getAdministrationSuffixDN(), hostPortSource), true,
                  LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
        println();
      }
      else
      {
        println();
        try
        {
          cancelled = !askConfirmation(
              INFO_REPLICATION_CONFIRM_INITIALIZE_ALL_GENERIC.get(
                  hostPortSource), true, LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
        println();
      }
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided PreExternalInitializationUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(PreExternalInitializationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToInitializeAll();
    int port = argParser.getPortToInitializeAll();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.run();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctx, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided PostExternalInitializationUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(PostExternalInitializationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToInitializeAll();
    int port = argParser.getPortToInitializeAll();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.run();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctx, true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided StatusReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   * @throws ReplicationCliException if a critical error occurs reading the
   * ADS.
   */
  private boolean promptIfRequired(StatusReplicationUserData uData)
  throws ReplicationCliException
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String host = argParser.getHostNameToStatus();
    int port = argParser.getPortToStatus();

    /*
     * Try to connect to the server.
     */
    InitialLdapContext ctx = null;

    while ((ctx == null) && !cancelled)
    {
      try
      {
        ci.run();
        host = ci.getHostName();
        port = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctx = createInitialLdapContextInteracting(ci);

        if (ctx == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostName(host);
      uData.setPort(port);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
      uData.setScriptFriendly(argParser.isScriptFriendly());
    }
    if (ctx != null)
    {
      // If the server contains an ADS, try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // statusReplication(StatusReplicationUserData) method.  Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      InitialLdapContext[] aux = new InitialLdapContext[] {ctx};
      cancelled = !loadADSAndAcceptCertificates(aux, uData, false);
      ctx = aux[0];
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      uData.setBaseDNs(suffixes);
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return !cancelled;
  }

  /**
   * Updates the contents of the provided InitializeReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return <CODE>true</CODE> if the object was successfully updated and
   * <CODE>false</CODE> if the user cancelled the operation.
   */
  private boolean promptIfRequired(InitializeReplicationUserData uData)
  {
    boolean cancelled = false;

    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    String hostSource = argParser.getHostNameSource();
    int portSource = argParser.getPortSource();

    LinkedHashMap<String, String> pwdFile = null;

    if (argParser.getSecureArgsList().bindPasswordFileArg.isPresent())
    {
      pwdFile =
        argParser.getSecureArgsList().bindPasswordFileArg.
        getNameToValueMap();
    }


    /*
     * Use a copy of the argument properties since the map might be cleared
     * in initializeGlobalArguments.
     */
    ci.initializeGlobalArguments(hostSource, portSource, adminUid, null,
        adminPwd,
        pwdFile == null ? null : new LinkedHashMap<String, String>(pwdFile));
    /*
     * Try to connect to the source server.
     */
    InitialLdapContext ctxSource = null;

    while ((ctxSource == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
            INFO_REPLICATION_INITIALIZE_SOURCE_CONNECTION_PARAMETERS.get());
        ci.run();
        hostSource = ci.getHostName();
        portSource = ci.getPortNumber();
        adminUid = ci.getAdministratorUID();
        adminPwd = ci.getBindPassword();

        ctxSource = createInitialLdapContextInteracting(ci);

        if (ctxSource == null)
        {
          cancelled = true;
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }
    if (!cancelled)
    {
      uData.setHostNameSource(hostSource);
      uData.setPortSource(portSource);
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);
    }

    firstServerCommandBuilder = new CommandBuilder(null);
    if (mustPrintCommandBuilder())
    {
      firstServerCommandBuilder.append(ci.getCommandBuilder());
    }

    /* Prompt for destination server credentials */
    String hostDestination = argParser.getHostNameDestination();
    int portDestination = argParser.getPortDestination();

    /*
     * Use a copy of the argument properties since the map might be cleared
     * in initializeGlobalArguments.
     */
    ci.initializeGlobalArguments(hostDestination, portDestination,
        adminUid, null, adminPwd,
        pwdFile == null ? null : new LinkedHashMap<String, String>(pwdFile));
    /*
     * Try to connect to the destination server.
     */
    InitialLdapContext ctxDestination = null;

    ci.resetHeadingDisplayed();
    while ((ctxDestination == null) && !cancelled)
    {
      try
      {
        ci.setHeadingMessage(
           INFO_REPLICATION_INITIALIZE_DESTINATION_CONNECTION_PARAMETERS.get());
        ci.run();
        hostDestination = ci.getHostName();
        portDestination = ci.getPortNumber();

        boolean error = false;
        if (hostSource.equalsIgnoreCase(hostDestination))
        {
          if (portSource == portDestination)
          {
            portDestination = -1;
            Message message = ERR_REPLICATION_INITIALIZE_SAME_SERVER_PORT.get(
                hostSource, String.valueOf(portSource));
            println();
            println(message);
            println();
            error = true;
          }
        }

        if (!error)
        {
          ctxDestination = createInitialLdapContextInteracting(ci, true);

          if (ctxDestination == null)
          {
            cancelled = true;
          }
        }
      }
      catch (ClientException ce)
      {
        LOG.log(Level.WARNING, "Client exception "+ce);
        println();
        println(ce.getMessageObject());
        println();
        ci.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        LOG.log(Level.WARNING, "Argument exception "+ae);
        println();
        println(ae.getMessageObject());
        println();
        cancelled = true;
      }
    }    if (!cancelled)
    {
      uData.setHostNameDestination(hostDestination);
      uData.setPortDestination(portDestination);
    }

    if (!cancelled)
    {
      LinkedList<String> suffixes = argParser.getBaseDNs();
      checkSuffixesForInitializeReplication(suffixes, ctxSource, ctxDestination,
          true);
      cancelled = suffixes.isEmpty();

      uData.setBaseDNs(suffixes);
    }

    if (!cancelled)
    {
      // Ask for confirmation to initialize.
      boolean initializeADS = false;
      for (String dn : uData.getBaseDNs())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          initializeADS = true;
          break;
        }
      }
      String hostPortSource = ConnectionUtils.getHostPort(ctxSource);
      String hostPortDestination = ConnectionUtils.getHostPort(ctxDestination);

      if (initializeADS)
      {
        println();
        try
        {
          cancelled = !askConfirmation(
              INFO_REPLICATION_CONFIRM_INITIALIZE_ADS.get(
                  ADSContext.getAdministrationSuffixDN(), hostPortDestination,
                  hostPortSource), true, LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
        println();
      }
      else
      {
        println();
        try
        {
          cancelled = !askConfirmation(
              INFO_REPLICATION_CONFIRM_INITIALIZE_GENERIC.get(
                  hostPortDestination, hostPortSource), true, LOG);
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          cancelled = true;
        }
        println();
      }
    }

    if (ctxSource != null)
    {
      try
      {
        ctxSource.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctxDestination != null)
    {
      try
      {
        ctxDestination.close();
      }
      catch (Throwable t)
      {
      }
    }
    return !cancelled;
  }

  /**
   * Commodity method that simply checks if a provided value is null or not,
   * if it is not <CODE>null</CODE> returns it and if it is <CODE>null</CODE>
   * returns the provided default value.
   * @param v the value to analyze.
   * @param defaultValue the default value.
   * @return if the provided value is not <CODE>null</CODE> returns it and if it
   * is <CODE>null</CODE> returns the provided default value.
   */
  private String getValue(String v, String defaultValue)
  {
    if (v != null)
    {
      return v;
    }
    else
    {
      return defaultValue;
    }
  }

  /**
   * Commodity method that simply checks if a provided value is -1 or not,
   * if it is not -1 returns it and if it is -1 returns the provided default
   * value.
   * @param v the value to analyze.
   * @param defaultValue the default value.
   * @return if the provided value is not -1 returns it and if it is -1 returns
   * the provided default value.
   */
  private int getValue(int v, int defaultValue)
  {
    if (v != -1)
    {
      return v;
    }
    else
    {
      return defaultValue;
    }
  }

  /**
   * Returns the trust manager to be used by this application.
   * @return the trust manager to be used by this application.
   */
  private ApplicationTrustManager getTrustManager()
  {
    ApplicationTrustManager trust;
    if (isInteractive())
    {
      TrustManager t = ci.getTrustManager();
      if (t == null)
      {
        trust = null;
      }
      else if (t instanceof ApplicationTrustManager)
      {
        trust = (ApplicationTrustManager)t;
      }
      else
      {
        trust = new ApplicationTrustManager(ci.getKeyStore());
      }
    }
    else
    {
      trust = argParser.getTrustManager();
    }
    return trust;
  }

  /**
   * Initializes the contents of the provided enable replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the enable replication user data object to be initialized.
   */
  private void initializeWithArgParser(EnableReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String host1Name = getValue(argParser.getHostName1(),
        argParser.getDefaultHostName1());
    uData.setHostName1(host1Name);
    int port1 = getValue(argParser.getPort1(),
        argParser.getDefaultPort1());
    uData.setPort1(port1);
    String pwd1 = argParser.getBindPassword1();
    if (pwd1 == null)
    {
      uData.setBindDn1(ADSContext.getAdministratorDN(adminUid));
      uData.setPwd1(adminPwd);
    }
    else
    {
      // Best-effort: try to use admin, if it does not work, use bind DN.
      try
      {
        InitialLdapContext ctx = createAdministrativeContext(
            uData.getHostName1(), uData.getPort1(), useSSL,
            useStartTLS, ADSContext.getAdministratorDN(adminUid),
            adminPwd, getConnectTimeout(), getTrustManager());
        uData.setBindDn1(ADSContext.getAdministratorDN(adminUid));
        uData.setPwd1(adminPwd);
        ctx.close();
      }
      catch (Throwable t)
      {
        String bindDn = getValue(argParser.getBindDn1(),
            argParser.getDefaultBindDn1());
        uData.setBindDn1(bindDn);
        uData.setPwd1(pwd1);
      }
    }
    uData.setSecureReplication1(argParser.isSecureReplication1());

    String host2Name = getValue(argParser.getHostName2(),
        argParser.getDefaultHostName2());
    uData.setHostName2(host2Name);
    int port2 = getValue(argParser.getPort2(),
        argParser.getDefaultPort2());
    uData.setPort2(port2);
    String pwd2 = argParser.getBindPassword2();
    if (pwd2 == null)
    {
      uData.setBindDn2(ADSContext.getAdministratorDN(adminUid));
      uData.setPwd2(adminPwd);
    }
    else
    {
      // Best-effort: try to use admin, if it does not work, use bind DN.
      try
      {
        InitialLdapContext ctx = createAdministrativeContext(
            uData.getHostName2(), uData.getPort2(), useSSL,
            useStartTLS, ADSContext.getAdministratorDN(adminUid),
            adminPwd, getConnectTimeout(), getTrustManager());
        uData.setBindDn2(ADSContext.getAdministratorDN(adminUid));
        uData.setPwd2(adminPwd);
        ctx.close();
      }
      catch (Throwable t)
      {
        String bindDn = getValue(argParser.getBindDn2(),
            argParser.getDefaultBindDn2());
        uData.setBindDn2(bindDn);
        uData.setPwd2(pwd2);
      }
    }
    uData.setSecureReplication2(argParser.isSecureReplication2());
    uData.setReplicateSchema(!argParser.noSchemaReplication());
    uData.setConfigureReplicationDomain1(
        !argParser.onlyReplicationServer1Arg.isPresent());
    uData.setConfigureReplicationDomain2(
        !argParser.onlyReplicationServer2Arg.isPresent());
    uData.setConfigureReplicationServer1(
        !argParser.noReplicationServer1Arg.isPresent());
    uData.setConfigureReplicationServer2(
        !argParser.noReplicationServer2Arg.isPresent());

    int replicationPort1 = getValue(argParser.getReplicationPort1(),
        argParser.getDefaultReplicationPort1());
    if (uData.configureReplicationServer1())
    {
      uData.setReplicationPort1(replicationPort1);
    }

    int replicationPort2 = getValue(argParser.getReplicationPort2(),
        argParser.getDefaultReplicationPort2());
    if (uData.configureReplicationServer2())
    {
      uData.setReplicationPort2(replicationPort2);
    }
  }

  /**
   * Initializes the contents of the provided initialize replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the initialize replication user data object to be initialized.
   */
  private void initializeWithArgParser(InitializeReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostNameSource = getValue(argParser.getHostNameSource(),
        argParser.getDefaultHostNameSource());
    uData.setHostNameSource(hostNameSource);
    int portSource = getValue(argParser.getPortSource(),
        argParser.getDefaultPortSource());
    uData.setPortSource(portSource);

    String hostNameDestination = getValue(
        argParser.getHostNameDestination(),
        argParser.getDefaultHostNameDestination());
    uData.setHostNameDestination(hostNameDestination);
    int portDestination = getValue(argParser.getPortDestination(),
        argParser.getDefaultPortDestination());
    uData.setPortDestination(portDestination);
  }

  /**
   * Initializes the contents of the provided disable replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the disable replication user data object to be initialized.
   */
  private void initializeWithArgParser(DisableReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = argParser.getAdministratorUID();
    String bindDn = argParser.getBindDNToDisable();
    if ((bindDn == null) && (adminUid == null))
    {
      adminUid = argParser.getDefaultAdministratorUID();
      bindDn = ADSContext.getAdministratorDN(adminUid);
    }
    uData.setAdminUid(adminUid);
    uData.setBindDn(bindDn);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToDisable(),
        argParser.getDefaultHostNameToDisable());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToDisable(),
        argParser.getDefaultPortToDisable());
    uData.setPort(port);

    uData.setDisableAll(argParser.disableAllArg.isPresent());
    uData.setDisableReplicationServer(
        argParser.disableReplicationServerArg.isPresent());
  }

  /**
   * Initializes the contents of the provided initialize all replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the initialize all replication user data object to be
   * initialized.
   */
  private void initializeWithArgParser(InitializeAllReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToInitializeAll(),
        argParser.getDefaultHostNameToInitializeAll());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToInitializeAll(),
        argParser.getDefaultPortToInitializeAll());
    uData.setPort(port);
  }

  /**
   * Initializes the contents of the provided pre external replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the pre external replication user data object to be
   * initialized.
   */
  private void initializeWithArgParser(PreExternalInitializationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToInitializeAll(),
        argParser.getDefaultHostNameToInitializeAll());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToInitializeAll(),
        argParser.getDefaultPortToInitializeAll());
    uData.setPort(port);
  }

  /**
   * Initializes the contents of the provided post external replication user
   * data object with what was provided in the command-line without prompting to
   * the user.
   * @param uData the pre external replication user data object to be
   * initialized.
   */
  private void initializeWithArgParser(PostExternalInitializationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToInitializeAll(),
        argParser.getDefaultHostNameToInitializeAll());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToInitializeAll(),
        argParser.getDefaultPortToInitializeAll());
    uData.setPort(port);
  }


  /**
   * Initializes the contents of the provided status replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the status replication user data object to be initialized.
   */
  private void initializeWithArgParser(StatusReplicationUserData uData)
  {
    uData.setBaseDNs(new LinkedList<String>(argParser.getBaseDNs()));
    String adminUid = getValue(argParser.getAdministratorUID(),
        argParser.getDefaultAdministratorUID());
    uData.setAdminUid(adminUid);
    String adminPwd = argParser.getBindPasswordAdmin();
    uData.setAdminPwd(adminPwd);

    String hostName = getValue(argParser.getHostNameToStatus(),
        argParser.getDefaultHostNameToStatus());
    uData.setHostName(hostName);
    int port = getValue(argParser.getPortToStatus(),
        argParser.getDefaultPortToStatus());
    uData.setPort(port);

    uData.setScriptFriendly(argParser.isScriptFriendly());
  }

  /**
   * Tells whether the server to which the LdapContext is connected has a
   * replication port or not.
   * @param ctx the InitialLdapContext to be used.
   * @return <CODE>true</CODE> if the replication port for the server could
   * be found and <CODE>false</CODE> otherwise.
   */
  private boolean hasReplicationPort(InitialLdapContext ctx)
  {
    return getReplicationPort(ctx) != -1;
  }

  /**
   * Returns the replication port of server to which the LdapContext is
   * connected and -1 if the replication port could not be found.
   * @param ctx the InitialLdapContext to be used.
   * @return the replication port of server to which the LdapContext is
   * connected and -1 if the replication port could not be found.
   */
  private int getReplicationPort(InitialLdapContext ctx)
  {
    int replicationPort = -1;
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();

      ReplicationSynchronizationProviderCfgClient sync =
          (ReplicationSynchronizationProviderCfgClient)
          root.getSynchronizationProvider("Multimaster Synchronization");
      if (sync.hasReplicationServer())
      {
        ReplicationServerCfgClient replicationServer =
          sync.getReplicationServer();
        replicationPort = replicationServer.getReplicationPort();
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the replication port: "+t, t);
    }
    return replicationPort;
  }

  /**
   * Loads the ADS with the provided context.  If there are certificates to
   * be accepted we prompt them to the user.  If there are errors loading the
   * servers we display them to the user and we ask for confirmation.  If the
   * provided ctx is not using Global Administrator credentials, we prompt the
   * user to provide them and update the provide ReplicationUserData
   * accordingly.
   * @param ctx the Ldap context to be used in an array: note the context
   * may be modified with the new credentials provided by the user.
   * @param uData the ReplicationUserData to be udpated.
   * @param isFirstOrSourceServer whether this is the first server in the
   * enable replication subcommand or the source server in the initialize server
   * subcommand.
   * @throws ReplicationCliException if a critical error occurred.
   * @return <CODE>true</CODE> if everything went fine and the user accepted
   * all the certificates and confirmed everything.  Returns <CODE>false</CODE>
   * if the user did not accept a certificate or any of the confirmation
   * messages.
   */
  private boolean loadADSAndAcceptCertificates(InitialLdapContext[] ctx,
      ReplicationUserData uData, boolean isFirstOrSourceServer)
  throws ReplicationCliException
  {
    boolean cancelled = false;
    boolean triedWithUserProvidedAdmin = false;
    String host = ConnectionUtils.getHostName(ctx[0]);
    int port = ConnectionUtils.getPort(ctx[0]);
    boolean isSSL = ConnectionUtils.isSSL(ctx[0]);
    boolean isStartTLS = ConnectionUtils.isStartTLS(ctx[0]);
    if (getTrustManager() == null)
    {
      // This is required when the user did  connect to the server using SSL or
      // Start TLS.  In this case LDAPConnectionConsoleInteraction.run does not
      // initialize the keystore and the trust manager is null.
      forceTrustManagerInitialization();
    }
    try
    {
      ADSContext adsContext = new ADSContext(ctx[0]);
      if (adsContext.hasAdminData())
      {
        boolean reloadTopology = true;
        LinkedList<Message> exceptionMsgs = new LinkedList<Message>();
        while (reloadTopology && !cancelled)
        {
          // We must recreate the cache because the trust manager in the
          // LDAPConnectionConsoleInteraction object might have changed.

          TopologyCache cache = new TopologyCache(adsContext,
              getTrustManager(), getConnectTimeout());
          cache.getFilter().setSearchMonitoringInformation(false);
          cache.getFilter().setSearchBaseDNInformation(false);
          cache.setPreferredConnections(
              PreferredConnection.getPreferredConnections(ctx[0]));
          cache.reloadTopology();

          reloadTopology = false;
          exceptionMsgs.clear();

          /* Analyze if we had any exception while loading servers.  For the
           * moment only throw the exception found if the user did not provide
           * the Administrator DN and this caused a problem authenticating in
           * one server or if there is a certificate problem.
           */
          Set<TopologyCacheException> exceptions =
            new HashSet<TopologyCacheException>();
          Set<ServerDescriptor> servers = cache.getServers();
          for (ServerDescriptor server : servers)
          {
            TopologyCacheException e = server.getLastException();
            if (e != null)
            {
              exceptions.add(e);
            }
          }
          /* Check the exceptions and see if we throw them or not. */
          boolean notGlobalAdministratorError = false;
          for (TopologyCacheException e : exceptions)
          {
            if (notGlobalAdministratorError)
            {
              break;
            }
            switch (e.getType())
            {
              case NOT_GLOBAL_ADMINISTRATOR:
                notGlobalAdministratorError = true;
                boolean connected = false;

                String adminUid = uData.getAdminUid();
                String adminPwd = uData.getAdminPwd();

                boolean errorDisplayed = false;
                while (!connected)
                {
                  if ((!triedWithUserProvidedAdmin) && (adminPwd == null))
                  {
                    adminUid = getValue(argParser.getAdministratorUID(),
                        argParser.getDefaultAdministratorUID());
                    adminPwd = argParser.getBindPasswordAdmin();
                    triedWithUserProvidedAdmin = true;
                  }
                  if (adminPwd == null)
                  {
                    if (!errorDisplayed)
                    {
                      println();
                      println(
                          INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());
                      errorDisplayed = true;
                    }
                    adminUid = askForAdministratorUID(
                        argParser.getDefaultAdministratorUID(), LOG);
                    println();
                    adminPwd = askForAdministratorPwd(LOG);
                    println();
                  }
                  try
                  {
                    ctx[0].close();
                  }
                  catch (Throwable t)
                  {
                  }
                  try
                  {
                    ctx[0] = createAdministrativeContext(host, port, isSSL,
                        isStartTLS, ADSContext.getAdministratorDN(adminUid),
                        adminPwd, getConnectTimeout(), getTrustManager());
                    adsContext = new ADSContext(ctx[0]);
                    cache = new TopologyCache(adsContext, getTrustManager(),
                        getConnectTimeout());
                    cache.getFilter().setSearchMonitoringInformation(false);
                    cache.getFilter().setSearchBaseDNInformation(false);
                    cache.setPreferredConnections(
                        PreferredConnection.getPreferredConnections(ctx[0]));
                    connected = true;
                  }
                  catch (Throwable t)
                  {
                    println();
                    println(
                        ERR_ERROR_CONNECTING_TO_SERVER_PROMPT_AGAIN.get(
                          getServerRepresentation(host, port), t.getMessage()));
                    LOG.log(Level.WARNING, "Complete error stack:", t);
                    println();
                  }
                }
                uData.setAdminUid(adminUid);
                uData.setAdminPwd(adminPwd);
                if (uData instanceof EnableReplicationUserData)
                {
                  EnableReplicationUserData enableData =
                    (EnableReplicationUserData)uData;
                  if (isFirstOrSourceServer)
                  {
                    enableData.setBindDn1(
                        ADSContext.getAdministratorDN(adminUid));
                    enableData.setPwd1(adminPwd);
                  }
                  else
                  {
                    enableData.setBindDn2(
                        ADSContext.getAdministratorDN(adminUid));
                    enableData.setPwd2(adminPwd);
                  }
                }
                reloadTopology = true;
              break;
            case GENERIC_CREATING_CONNECTION:
              if ((e.getCause() != null) &&
                  Utils.isCertificateException(e.getCause()))
              {
                reloadTopology = true;
                cancelled = !ci.promptForCertificateConfirmation(e.getCause(),
                    e.getTrustManager(), e.getLdapUrl(), true, LOG);
              }
              else
              {
                exceptionMsgs.add(Utils.getMessage(e));
              }
              break;
            default:
              exceptionMsgs.add(Utils.getMessage(e));
            }
          }
        }
        if ((exceptionMsgs.size() > 0) && !cancelled)
        {
          if (uData instanceof StatusReplicationUserData)
          {
            println(
                ERR_REPLICATION_STATUS_READING_REGISTERED_SERVERS.get(
                    Utils.getMessageFromCollection(exceptionMsgs,
                        Constants.LINE_SEPARATOR).toString()));
            println();
          }
          else
          {
            try
            {
              cancelled = !askConfirmation(
              ERR_REPLICATION_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.
                  get(Utils.getMessageFromCollection(exceptionMsgs,
                      Constants.LINE_SEPARATOR).toString()), true, LOG);
            }
            catch (CLIException ce)
            {
              println(ce.getMessageObject());
              cancelled = true;
            }
          }
        }
      }
    }
    catch (ADSContextException ace)
    {
      LOG.log(Level.SEVERE, "Complete error stack:", ace);
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(ace.getMessage()),
          ERROR_READING_ADS, ace);
    }
    catch (TopologyCacheException tce)
    {
      LOG.log(Level.SEVERE, "Complete error stack:", tce);
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
    return !cancelled;
  }

  /**
   * Tells whether there is a Global Administrator defined in the server
   * to which the InitialLdapContext is connected.
   * @param ctx the InitialLdapContext.
   * @return <CODE>true</CODE> if we could find an administrator and
   * <CODE>false</CODE> otherwise.
   */
  private boolean hasAdministrator(InitialLdapContext ctx)
  {
    boolean isAdminDefined = false;
    try
    {
      ADSContext adsContext = new ADSContext(ctx);
      if (adsContext.hasAdminData())
      {
        Set<?> administrators = adsContext.readAdministratorRegistry();
        isAdminDefined = administrators.size() > 0;
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the ADS data: "+t, t);
    }
    return isAdminDefined;
  }

  /**
   * Tells whether there is a Global Administrator corresponding to the provided
   * ReplicationUserData defined in the server to which the InitialLdapContext
   * is connected.
   * @param ctx the InitialLdapContext.
   * @param uData the user data
   * @return <CODE>true</CODE> if we could find an administrator and
   * <CODE>false</CODE> otherwise.
   */
  private boolean hasAdministrator(InitialLdapContext ctx,
      ReplicationUserData uData)
  {
    boolean isAdminDefined = false;
    String adminUid = uData.getAdminUid();
    try
    {
      ADSContext adsContext = new ADSContext(ctx);
      Set<Map<AdministratorProperty, Object>> administrators =
        adsContext.readAdministratorRegistry();
      for (Map<AdministratorProperty, Object> admin : administrators)
      {
        String uid = (String)admin.get(AdministratorProperty.UID);
        if (uid != null)
        {
          // If the administrator UID is null it means that we are just
          // checking for the existence of an administrator
          isAdminDefined = uid.equalsIgnoreCase(adminUid) || (adminUid == null);
          if (isAdminDefined)
          {
            break;
          }
        }
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the ADS data: "+t, t);
    }
    return isAdminDefined;
  }

  /**
   * Helper type for the <CODE>getCommonSuffixes</CODE> method.
   */
  private enum SuffixRelationType
  {
    NOT_REPLICATED, FULLY_REPLICATED, REPLICATED, NOT_FULLY_REPLICATED, ALL
  }

  /**
   * Returns a Collection containing a list of suffixes that are defined in
   * two servers at the same time (depending on the value of the argument
   * replicated this list contains only the suffixes that are replicated
   * between the servers or the list of suffixes that are not replicated
   * between the servers).
   * @param ctx1 the connection to the first server.
   * @param ctx2 the connection to the second server.
   * @param type whether to return a list with the suffixes that are
   * replicated, fully replicated (replicas have exactly the same list of
   * replication servers), not replicated or all the common suffixes.
   * @return a Collection containing a list of suffixes that are replicated
   * (or those that can be replicated) in two servers.
   */
  private Collection<String> getCommonSuffixes(
      InitialLdapContext ctx1, InitialLdapContext ctx2, SuffixRelationType type)
  {
    LinkedList<String> suffixes = new LinkedList<String>();
    try
    {
      TopologyCacheFilter filter = new TopologyCacheFilter();
      filter.setSearchMonitoringInformation(false);
      ServerDescriptor server1 =
        ServerDescriptor.createStandalone(ctx1, filter);
      ServerDescriptor server2 =
        ServerDescriptor.createStandalone(ctx2, filter);
      Set<ReplicaDescriptor> replicas1 = server1.getReplicas();
      Set<ReplicaDescriptor> replicas2 = server2.getReplicas();

      for (ReplicaDescriptor rep1 : replicas1)
      {
        for (ReplicaDescriptor rep2 : replicas2)
        {

          switch (type)
          {
          case NOT_REPLICATED:
            if (!areReplicated(rep1, rep2) &&
                Utils.areDnsEqual(rep1.getSuffix().getDN(),
                    rep2.getSuffix().getDN()))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case FULLY_REPLICATED:
            if (areFullyReplicated(rep1, rep2))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case REPLICATED:
            if (areReplicated(rep1, rep2))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case NOT_FULLY_REPLICATED:
            if (!areFullyReplicated(rep1, rep2) &&
                Utils.areDnsEqual(rep1.getSuffix().getDN(),
                    rep2.getSuffix().getDN()))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
          case ALL:
            if (Utils.areDnsEqual(rep1.getSuffix().getDN(),
                rep2.getSuffix().getDN()))
            {
              suffixes.add(rep1.getSuffix().getDN());
            }
            break;
            default:
              throw new IllegalStateException("Unknown type: "+type);
          }
        }
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the server configuration: "+t, t);
    }
    return suffixes;
  }

  /**
   * Tells whether the two provided replicas are fully replicated or not.  The
   * code in fact checks that both replicas have the same DN that they are
   * replicated if both servers are replication servers and that both replicas
   * make reference to the other replication server.
   * @param rep1 the first replica.
   * @param rep2 the second replica.
   * @return <CODE>true</CODE> if we can assure that the two replicas are
   * replicated using the replication server and replication port information
   * and <CODE>false</CODE> otherwise.
   */
  private boolean areFullyReplicated(ReplicaDescriptor rep1,
      ReplicaDescriptor rep2)
  {
    boolean areFullyReplicated = false;
    if (Utils.areDnsEqual(rep1.getSuffix().getDN(), rep2.getSuffix().getDN()) &&
        rep1.isReplicated() && rep2.isReplicated() &&
        rep1.getServer().isReplicationServer() &&
        rep2.getServer().isReplicationServer())
    {
     Set<String> servers1 = rep1.getReplicationServers();
     Set<String> servers2 = rep2.getReplicationServers();
     String server1 = rep1.getServer().getReplicationServerHostPort();
     String server2 = rep2.getServer().getReplicationServerHostPort();
     areFullyReplicated = servers1.contains(server2) &&
     servers2.contains(server1);
    }
    return areFullyReplicated;
  }

  /**
   * Tells whether the two provided replicas are replicated or not.  The
   * code in fact checks that both replicas have the same DN and that they
   * have at least one common replication server referenced.
   * @param rep1 the first replica.
   * @param rep2 the second replica.
   * @return <CODE>true</CODE> if we can assure that the two replicas are
   * replicated and <CODE>false</CODE> otherwise.
   */
  private boolean areReplicated(ReplicaDescriptor rep1, ReplicaDescriptor rep2)
  {
    boolean areReplicated = false;
    if (Utils.areDnsEqual(rep1.getSuffix().getDN(), rep2.getSuffix().getDN()) &&
        rep1.isReplicated() && rep2.isReplicated())
    {
      Set<String> servers1 = rep1.getReplicationServers();
      Set<String> servers2 = rep2.getReplicationServers();
      servers1.retainAll(servers2);
      areReplicated = !servers1.isEmpty();
    }
    return areReplicated;
  }

  /**
   * Returns a Collection containing a list of replicas in a server.
   * @param ctx the connection to the server.
   * @return a Collection containing a list of replicas in a server.
   */
  private Collection<ReplicaDescriptor> getReplicas(InitialLdapContext ctx)
  {
    LinkedList<ReplicaDescriptor> suffixes =
      new LinkedList<ReplicaDescriptor>();
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    try
    {
      ServerDescriptor server = ServerDescriptor.createStandalone(ctx, filter);
      suffixes.addAll(server.getReplicas());
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING,
          "Unexpected error retrieving the server configuration: "+t, t);
    }
    return suffixes;
  }

  /**
   * Enables the replication between two servers using the parameters in the
   * provided EnableReplicationUserData.  This method does not prompt to the
   * user for information if something is missing.
   * @param uData the EnableReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and the replication could be enabled and an error code
   * otherwise.
   */
  private ReplicationCliReturnCode enableReplication(
      EnableReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;

    InitialLdapContext ctx1 = null;
    InitialLdapContext ctx2 = null;

    String host1 = uData.getHostName1();
    String host2 = uData.getHostName2();
    int port1 = uData.getPort1();
    int port2 = uData.getPort2();

    LinkedList<Message> errorMessages = new LinkedList<Message>();

    printlnProgress();
    printProgress(
        formatter.getFormattedWithPoints(INFO_REPLICATION_CONNECTING.get()));
    try
    {
      ctx1 = createAdministrativeContext(host1, port1, useSSL,
          useStartTLS, uData.getBindDn1(), uData.getPwd1(),
          getConnectTimeout(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = getServerRepresentation(host1, port1);
      errorMessages.add(getMessageForException(ne, hostPort));

      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    try
    {
      ctx2 = createAdministrativeContext(host2, port2, useSSL,
          useStartTLS, uData.getBindDn2(), uData.getPwd2(),
          getConnectTimeout(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort = getServerRepresentation(host2, port2);
      errorMessages.add(getMessageForException(ne, hostPort));

      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }

    if (errorMessages.size() > 0)
    {
      returnValue = ERROR_CONNECTING;
    }

    if (errorMessages.isEmpty())
    {
      // This done is for the message informing that we are connecting.
      printProgress(formatter.getFormattedDone());
      printlnProgress();

//    If we are not in interactive mode do some checks...
      if (!argParser.isInteractive())
      {
        int replPort1 = getReplicationPort(ctx1);
        boolean hasReplicationPort1 = replPort1 > 0;
        if (replPort1 < 0 && uData.configureReplicationServer1())
        {
          replPort1 = uData.getReplicationPort1();
        }
        int replPort2 = getReplicationPort(ctx2);
        boolean hasReplicationPort2 = replPort2 > 0;
        if (replPort2 < 0 && uData.configureReplicationServer2())
        {
          replPort2 = uData.getReplicationPort2();
        }
        boolean checkReplicationPort1 = replPort1 > 0;
        boolean checkReplicationPort2 = replPort2 > 0;
        if (!hasReplicationPort1 && checkReplicationPort1)
        {
          if (!argParser.skipReplicationPortCheck() &&
              uData.configureReplicationServer1() &&
              Utils.isLocalHost(host1) &&
              !SetupUtils.canUseAsPort(replPort1))
          {
            errorMessages.add(getCannotBindToPortError(replPort1));
          }
        }
        if (!hasReplicationPort2 && checkReplicationPort2)
        {
          if (!argParser.skipReplicationPortCheck() &&
              uData.configureReplicationServer2() &&
              Utils.isLocalHost(host2) &&
              !SetupUtils.canUseAsPort(replPort2))
          {
            errorMessages.add(getCannotBindToPortError(replPort2));
          }
        }
        if (checkReplicationPort1 && checkReplicationPort2 &&
            (replPort1 == replPort2) &&
            (host1.equalsIgnoreCase(host2)))
        {
          errorMessages.add(ERR_REPLICATION_SAME_REPLICATION_PORT.get(
              String.valueOf(replPort1), host1));
        }

        if (argParser.skipReplicationPortCheck())
        {
          // This is something that we must do in any case... this test is
          // already included when we call SetupUtils.canUseAsPort
          if (checkReplicationPort1 && replPort1 == port1)
          {
            errorMessages.add(
                ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                host1, String.valueOf(replPort1)));
          }

          if (checkReplicationPort2 && replPort2 == port2)
          {
            errorMessages.add(
                ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(
                host2, String.valueOf(replPort2)));
          }
        }
      }
      if (errorMessages.size() > 0)
      {
        returnValue = ERROR_USER_DATA;
      }
    }

    if (errorMessages.isEmpty())
    {
      LinkedList<String> suffixes = uData.getBaseDNs();
      checkSuffixesForEnableReplication(suffixes, ctx1, ctx2, false, uData);
      if (!suffixes.isEmpty())
      {
        uData.setBaseDNs(suffixes);

        if (mustPrintCommandBuilder())
        {
          try
          {
            CommandBuilder commandBuilder = createCommandBuilder(
                ReplicationCliArgumentParser.ENABLE_REPLICATION_SUBCMD_NAME,
                uData);
            printCommandBuilder(commandBuilder);
          }
          catch (Throwable t)
          {
            LOG.log(Level.SEVERE, "Error printing equivalente command-line: "+t,
                t);
          }
        }

        if (!isInteractive())
        {
          int repPort1 = getReplicationPort(ctx1);
          int repPort2 = getReplicationPort(ctx2);

          if (!uData.configureReplicationServer1() && repPort1 > 0)
          {

            println(INFO_REPLICATION_SERVER_CONFIGURED_WARNING.get(
                ConnectionUtils.getHostPort(ctx1), repPort1));
            println();
          }
          if (!uData.configureReplicationServer2() && repPort2 > 0)
          {
            println(INFO_REPLICATION_SERVER_CONFIGURED_WARNING.get(
                ConnectionUtils.getHostPort(ctx2), repPort2));
            println();
          }
        }

        try
        {
          updateConfiguration(ctx1, ctx2, uData);
          returnValue = SUCCESSFUL;
        }
        catch (ReplicationCliException rce)
        {
          returnValue = rce.getErrorCode();
          println();
          println(getCriticalExceptionMessage(rce));
          LOG.log(Level.SEVERE, "Complete error stack:", rce);
        }
      }
      else
      {
        // The error messages are already displayed in the method
        // checkSuffixesForEnableReplication.
        returnValue = REPLICATION_CANNOT_BE_ENABLED_ON_BASEDN;
      }
    }

    for (Message msg : errorMessages)
    {
      println();
      println(msg);
    }

    if (returnValue == SUCCESSFUL)
    {
      long time1 = Utils.getServerClock(ctx1);
      long time2 = Utils.getServerClock(ctx2);
      if ((time1 != -1) && (time2 != -1))
      {
        if (Math.abs(time1 - time2) >
        (Installer.WARNING_CLOCK_DIFFERENCE_THRESOLD_MINUTES * 60 * 1000))
        {
          println(INFO_WARNING_SERVERS_CLOCK_DIFFERENCE.get(
              ConnectionUtils.getHostPort(ctx1),
              ConnectionUtils.getHostPort(ctx2),
              String.valueOf(
                  Installer.WARNING_CLOCK_DIFFERENCE_THRESOLD_MINUTES)));
        }
      }
      printlnProgress();
      printProgress(INFO_REPLICATION_POST_ENABLE_INFO.get("dsreplication",
          ReplicationCliArgumentParser.INITIALIZE_REPLICATION_SUBCMD_NAME));
      printlnProgress();
    }

    if (ctx1 != null)
    {
      try
      {
        ctx1.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctx2 != null)
    {
      try
      {
        ctx2.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  /**
   * Disables the replication in the server for the provided suffixes using the
   * data in the DisableReplicationUserData object.  This method does not prompt
   * to the user for information if something is missing.
   * @param uData the DisableReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode disableReplication(
      DisableReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue;
    InitialLdapContext ctx = null;
    printProgress(
        formatter.getFormattedWithPoints(INFO_REPLICATION_CONNECTING.get()));
    String bindDn = uData.getAdminUid() == null ? uData.getBindDn() :
      ADSContext.getAdministratorDN(uData.getAdminUid());
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          useSSL, useStartTLS, bindDn, uData.getAdminPwd(), getConnectTimeout(),
          getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort =
        getServerRepresentation(uData.getHostName(), uData.getPort());
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }

    if (ctx != null)
    {
      // This done is for the message informing that we are connecting.
      printProgress(formatter.getFormattedDone());
      printlnProgress();
      LinkedList<String> suffixes = uData.getBaseDNs();
      checkSuffixesForDisableReplication(suffixes, ctx, false,
          !uData.disableReplicationServer(), !uData.disableReplicationServer());
      if (!suffixes.isEmpty() || uData.disableReplicationServer() ||
          uData.disableAll())
      {
        uData.setBaseDNs(suffixes);

        if (!isInteractive())
        {
          boolean hasReplicationPort = hasReplicationPort(ctx);
          if (uData.disableAll() && hasReplicationPort)
          {
            uData.setDisableReplicationServer(true);
          }
          else if (uData.disableReplicationServer() && !hasReplicationPort &&
              !uData.disableAll())
          {
            uData.setDisableReplicationServer(false);
            println(
                INFO_REPLICATION_WARNING_NO_REPLICATION_SERVER_TO_DISABLE.get(
                    ConnectionUtils.getHostPort(ctx)));
            println();
          }
        }

        if (mustPrintCommandBuilder())
        {
          try
          {
            CommandBuilder commandBuilder = createCommandBuilder(
                ReplicationCliArgumentParser.DISABLE_REPLICATION_SUBCMD_NAME,
                uData);
            printCommandBuilder(commandBuilder);
          }
          catch (Throwable t)
          {
            LOG.log(Level.SEVERE, "Error printing equivalente command-line: "+t,
                t);
          }
        }

        if (!isInteractive() && !uData.disableReplicationServer() &&
            !uData.disableAll() && disableAllBaseDns(ctx, uData) &&
            hasReplicationPort(ctx))
        {
          // Inform the user that the replication server will not be disabled.
          // Inform also of the user of the disableReplicationServerArg
          println(
           INFO_REPLICATION_DISABLE_ALL_SUFFIXES_KEEP_REPLICATION_SERVER.get(
               ConnectionUtils.getHostPort(ctx),
               argParser.disableReplicationServerArg.getLongIdentifier(),
               argParser.disableAllArg.getLongIdentifier()));
        }
        try
        {
          updateConfiguration(ctx, uData);
          returnValue = SUCCESSFUL;
        }
        catch (ReplicationCliException rce)
        {
          returnValue = rce.getErrorCode();
          println();
          println(getCriticalExceptionMessage(rce));
          LOG.log(Level.SEVERE, "Complete error stack:", rce);
        }
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_DISABLED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  /**
   * Displays the replication status of the baseDNs specified in the
   * StatusReplicationUserData object.  This method does not prompt
   * to the user for information if something is missing.
   * @param uData the StatusReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode statusReplication(
      StatusReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          useSSL, useStartTLS,
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getConnectTimeout(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort =
        getServerRepresentation(uData.getHostName(), uData.getPort());
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }

    if (ctx != null)
    {
      try
      {
        displayStatus(ctx, uData);
        returnValue = SUCCESSFUL;
      }
      catch (ReplicationCliException rce)
      {
        returnValue = rce.getErrorCode();
        println();
        println(getCriticalExceptionMessage(rce));
        LOG.log(Level.SEVERE, "Complete error stack:", rce);
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  /**
   * Initializes the contents of one server with the contents of the other
   * using the parameters in the provided InitializeReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the InitializeReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode initializeReplication(
      InitializeReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctxSource = null;
    InitialLdapContext ctxDestination = null;

     ctxSource = getAdministrativeContext(uData.getHostNameSource(),
         uData.getPortSource(), useSSL,
         useStartTLS,
         ADSContext.getAdministratorDN(uData.getAdminUid()),
         uData.getAdminPwd(), getConnectTimeout(), getTrustManager());

     ctxDestination = getAdministrativeContext(
         uData.getHostNameDestination(),
         uData.getPortDestination(), useSSL,
         useStartTLS,
         ADSContext.getAdministratorDN(uData.getAdminUid()),
         uData.getAdminPwd(), getConnectTimeout(), getTrustManager());

    if ((ctxSource != null) && (ctxDestination != null))
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctxSource, ctxDestination,
          false);
      if (!baseDNs.isEmpty())
      {
        if (mustPrintCommandBuilder())
        {
          try
          {
            uData.setBaseDNs(baseDNs);
            CommandBuilder commandBuilder = createCommandBuilder(
                ReplicationCliArgumentParser.INITIALIZE_REPLICATION_SUBCMD_NAME,
                uData);
            printCommandBuilder(commandBuilder);
          }
          catch (Throwable t)
          {
            LOG.log(Level.SEVERE, "Error printing equivalente command-line: "+t,
                t);
          }
        }

        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedProgress(
                INFO_PROGRESS_INITIALIZING_SUFFIX.get(baseDN,
                    ConnectionUtils.getHostPort(ctxSource)));
            printProgress(msg);
            printlnProgress();
            initializeSuffix(baseDN, ctxSource, ctxDestination, true);
            returnValue = SUCCESSFUL;
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctxSource != null)
    {
      try
      {
        ctxSource.close();
      }
      catch (Throwable t)
      {
      }
    }

    if (ctxDestination != null)
    {
      try
      {
        ctxDestination.close();
      }
      catch (Throwable t)
      {
      }
    }
    return returnValue;
  }

  private InitialLdapContext getAdministrativeContext(final String host,
      final int port, final boolean useSSL, final boolean useStartTLS,
      final String bindDn, final String pwd, final int connectTimeout,
      final ApplicationTrustManager trustManager)
  {
    InitialLdapContext context = null;
    try
    {
      context = createAdministrativeContext(
          host,
          port,
          useSSL,
          useStartTLS,
          bindDn,
          pwd,
          connectTimeout,
          trustManager);
    }
    catch (NamingException ne)
    {
      final String hostPort = getServerRepresentation(host, port);
      println();
      println(Utils.getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    return context;
  }

  /**
   * Initializes the contents of a whole topology with the contents of the other
   * using the parameters in the provided InitializeAllReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the InitializeAllReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode initializeAllReplication(
      InitializeAllReplicationUserData uData)
  {
    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          useSSL, useStartTLS,
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getConnectTimeout(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort =
        getServerRepresentation(uData.getHostName(), uData.getPort());
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    if (ctx != null)
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctx, false);
      if (!baseDNs.isEmpty())
      {
        if (mustPrintCommandBuilder())
        {
          uData.setBaseDNs(baseDNs);
          try
          {
            CommandBuilder commandBuilder = createCommandBuilder(
            ReplicationCliArgumentParser.INITIALIZE_ALL_REPLICATION_SUBCMD_NAME,
            uData);
            printCommandBuilder(commandBuilder);
          }
          catch (Throwable t)
          {
            LOG.log(Level.SEVERE, "Error printing equivalente command-line: "+t,
                t);
          }
        }
        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedProgress(
                INFO_PROGRESS_INITIALIZING_SUFFIX.get(baseDN,
                    ConnectionUtils.getHostPort(ctx)));
            printProgress(msg);
            println();
            initializeAllSuffix(baseDN, ctx, true);
            returnValue = SUCCESSFUL;
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return returnValue;
  }

  /**
   * Performs the operation that must be made before initializing the topology
   * using the import-ldif command or the binary copy.  The operation uses
   * the parameters in the provided InitializeAllReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the PreExternalInitializationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode preExternalInitialization(
      PreExternalInitializationUserData uData)
  {
    ReplicationCliReturnCode returnValue;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          useSSL, useStartTLS,
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getConnectTimeout(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort =
        getServerRepresentation(uData.getHostName(), uData.getPort());
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    if (ctx != null)
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctx, false);
      if (!baseDNs.isEmpty())
      {
        if (mustPrintCommandBuilder())
        {
          uData.setBaseDNs(baseDNs);
          try
          {
            CommandBuilder commandBuilder = createCommandBuilder(
           ReplicationCliArgumentParser.PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
            uData);
            printCommandBuilder(commandBuilder);
          }
          catch (Throwable t)
          {
            LOG.log(Level.SEVERE, "Error printing equivalente command-line: "+t,
                t);
          }
        }
        returnValue = SUCCESSFUL;
        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedWithPoints(
                INFO_PROGRESS_PRE_EXTERNAL_INITIALIZATION.get(baseDN));
            printProgress(msg);
            preExternalInitialization(baseDN, ctx, false);
            printProgress(formatter.getFormattedDone());
            printlnProgress();
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
        printlnProgress();
        printProgress(
          INFO_PROGRESS_PRE_INITIALIZATION_FINISHED_PROCEDURE.get(
              ReplicationCliArgumentParser.
              POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
        printlnProgress();
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return returnValue;
  }

  /**
   * Performs the operation that must be made after initializing the topology
   * using the import-ldif command or the binary copy.  The operation uses
   * the parameters in the provided InitializeAllReplicationUserData.
   * This method does not prompt to the user for information if something is
   * missing.
   * @param uData the PostExternalInitializationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode postExternalInitialization(
      PostExternalInitializationUserData uData)
  {
    ReplicationCliReturnCode returnValue;
    InitialLdapContext ctx = null;
    try
    {
      ctx = createAdministrativeContext(uData.getHostName(), uData.getPort(),
          useSSL, useStartTLS,
          ADSContext.getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getConnectTimeout(), getTrustManager());
    }
    catch (NamingException ne)
    {
      String hostPort =
        getServerRepresentation(uData.getHostName(), uData.getPort());
      println();
      println(getMessageForException(ne, hostPort));
      LOG.log(Level.SEVERE, "Complete error stack:", ne);
    }
    if (ctx != null)
    {
      LinkedList<String> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, ctx, false);
      if (!baseDNs.isEmpty())
      {
        if (mustPrintCommandBuilder())
        {
          uData.setBaseDNs(baseDNs);
          try
          {
            CommandBuilder commandBuilder = createCommandBuilder(
          ReplicationCliArgumentParser.POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
            uData);
            printCommandBuilder(commandBuilder);
          }
          catch (Throwable t)
          {
            LOG.log(Level.SEVERE, "Error printing equivalente command-line: "+t,
                t);
          }
        }
        returnValue = SUCCESSFUL;
        for (String baseDN : baseDNs)
        {
          try
          {
            printlnProgress();
            Message msg = formatter.getFormattedWithPoints(
                INFO_PROGRESS_POST_EXTERNAL_INITIALIZATION.get(baseDN));
            printProgress(msg);
            postExternalInitialization(baseDN, ctx, false);
            printProgress(formatter.getFormattedDone());
            printlnProgress();
          }
          catch (ReplicationCliException rce)
          {
            println();
            println(getCriticalExceptionMessage(rce));
            returnValue = rce.getErrorCode();
            LOG.log(Level.SEVERE, "Complete error stack:", rce);
          }
        }
        printlnProgress();
        printProgress(
            INFO_PROGRESS_POST_INITIALIZATION_FINISHED_PROCEDURE.get());
        printlnProgress();
      }
      else
      {
        returnValue = REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
    }
    else
    {
      returnValue = ERROR_CONNECTING;
    }

    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (Throwable t)
      {
      }
    }

    return returnValue;
  }


  /**
   * Checks that replication can actually be enabled in the provided baseDNs
   * for the two servers.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be enabled and with the
   * base DNs that the user provided interactively.
   * @param ctx1 connection to the first server.
   * @param ctx2 connection to the second server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be enabled.
   * @param uData the user data.  This object will not be updated by this method
   * but it is assumed that it contains information about whether the
   * replication domains must be configured or not.
   */
  private void checkSuffixesForEnableReplication(Collection<String> suffixes,
      InitialLdapContext ctx1, InitialLdapContext ctx2,
      boolean interactive, EnableReplicationUserData uData)
  {

    TreeSet<String> availableSuffixes;
    TreeSet<String> alreadyReplicatedSuffixes;
    if (uData.configureReplicationDomain1() &&
        uData.configureReplicationDomain2())
    {
      availableSuffixes =
        new TreeSet<String>(getCommonSuffixes(ctx1, ctx2,
            SuffixRelationType.NOT_FULLY_REPLICATED));
      alreadyReplicatedSuffixes =
        new TreeSet<String>(getCommonSuffixes(ctx1, ctx2,
            SuffixRelationType.FULLY_REPLICATED));
    }
    else if (uData.configureReplicationDomain1())
    {
      availableSuffixes = new TreeSet<String>();
      alreadyReplicatedSuffixes = new TreeSet<String>();

      updateAvailableAndReplicatedSuffixesForOneDomain(ctx1, ctx2,
          availableSuffixes, alreadyReplicatedSuffixes);
    }
    else if (uData.configureReplicationDomain2())
    {
      availableSuffixes = new TreeSet<String>();
      alreadyReplicatedSuffixes = new TreeSet<String>();

      updateAvailableAndReplicatedSuffixesForOneDomain(ctx2, ctx1,
          availableSuffixes, alreadyReplicatedSuffixes);
    }
    else
    {
      availableSuffixes = new TreeSet<String>();
      alreadyReplicatedSuffixes = new TreeSet<String>();

      updateAvailableAndReplicatedSuffixesForNoDomain(ctx1, ctx2,
          availableSuffixes, alreadyReplicatedSuffixes);
    }

    if (availableSuffixes.isEmpty())
    {
      println();
      if (!uData.configureReplicationDomain1() &&
          !uData.configureReplicationDomain1() &&
          alreadyReplicatedSuffixes.isEmpty())
      {
        // Use a clarifying message: there is no replicated base DN.
        println(
            ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION_NO_DOMAIN.get());
      }
      else
      {
        println(
          ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION.get());
      }

      LinkedList<String> userProvidedSuffixes = argParser.getBaseDNs();
      TreeSet<String> userProvidedReplicatedSuffixes = new TreeSet<String>();

      for (String s1 : userProvidedSuffixes)
      {
        for (String s2 : alreadyReplicatedSuffixes)
        {
          if (Utils.areDnsEqual(s1, s2))
          {
            userProvidedReplicatedSuffixes.add(s1);
          }
        }
      }
      if (userProvidedReplicatedSuffixes.size() > 0)
      {
        println();
        println(
            INFO_ALREADY_REPLICATED_SUFFIXES.get(
                Utils.getStringFromCollection(userProvidedReplicatedSuffixes,
                    Constants.LINE_SEPARATOR)));
      }
      suffixes.clear();
    }
    else
    {
      //  Verify that the provided suffixes are configured in the servers.
      TreeSet<String> notFound = new TreeSet<String>();
      TreeSet<String> alreadyReplicated = new TreeSet<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          boolean isReplicated = false;
          for (String s : alreadyReplicatedSuffixes)
          {
            if (Utils.areDnsEqual(s, dn))
            {
              isReplicated = true;
              break;
            }
          }
          if (isReplicated)
          {
            alreadyReplicated.add(dn);
          }
          else
          {
            notFound.add(dn);
          }
        }
      }
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyReplicated);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_REPLICATION_ENABLE_SUFFIXES_NOT_FOUND.get(
              Utils.getStringFromCollection(notFound,
                  Constants.LINE_SEPARATOR)));
      }
      if (alreadyReplicated.size() > 0)
      {
        println();
        println(INFO_ALREADY_REPLICATED_SUFFIXES.get(
            Utils.getStringFromCollection(alreadyReplicated,
                Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        boolean confirmationLimitReached = false;
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            println(
                ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION.get());
            break;
          }
          else
          {
            println();
            println(ERR_NO_SUFFIXES_SELECTED_TO_REPLICATE.get());
            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                try
                {
                  if (askConfirmation(
                    INFO_REPLICATION_ENABLE_SUFFIX_PROMPT.get(dn), true, LOG))
                  {
                    suffixes.add(dn);
                  }
                }
                catch (CLIException ce)
                {
                  println(ce.getMessageObject());
                  confirmationLimitReached = true;
                  break;
                }
              }
            }
          }
          if (confirmationLimitReached)
          {
            suffixes.clear();
            break;
          }
        }
      }
    }
  }

  /**
   * Checks that replication can actually be disabled in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be disabled and with the
   * base DNs that the user provided interactively.
   * @param ctx connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be disabled.
   * @param displayErrors whether to display errors or not.
   * @param areSuffixRequired whether the user must provide base DNs or not
   * (if it is <CODE>false</CODE> the user will be proposed the suffixes
   * only once).
   */
  private void checkSuffixesForDisableReplication(Collection<String> suffixes,
      InitialLdapContext ctx, boolean interactive, boolean displayErrors,
      boolean areSuffixRequired)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>();
    TreeSet<String> notReplicatedSuffixes = new TreeSet<String>();

    Collection<ReplicaDescriptor> replicas = getReplicas(ctx);
    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        availableSuffixes.add(dn);
      }
      else
      {
        notReplicatedSuffixes.add(dn);
      }
    }
    if (availableSuffixes.isEmpty())
    {
      if (displayErrors)
      {
        println();
        println(ERR_NO_SUFFIXES_AVAILABLE_TO_DISABLE_REPLICATION.get());
      }
      LinkedList<String> userProvidedSuffixes = argParser.getBaseDNs();
      TreeSet<String> userProvidedNotReplicatedSuffixes =
        new TreeSet<String>();
      for (String s1 : userProvidedSuffixes)
      {
        for (String s2 : notReplicatedSuffixes)
        {
          if (Utils.areDnsEqual(s1, s2))
          {
            userProvidedNotReplicatedSuffixes.add(s1);
          }
        }
      }
      if (userProvidedNotReplicatedSuffixes.size() > 0 && displayErrors)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
            Utils.getStringFromCollection(
                userProvidedNotReplicatedSuffixes,
                Constants.LINE_SEPARATOR)));
      }
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<String> notFound = new TreeSet<String>();
      TreeSet<String> alreadyNotReplicated = new TreeSet<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          boolean notReplicated = false;
          for (String s : notReplicatedSuffixes)
          {
            if (Utils.areDnsEqual(s, dn))
            {
              notReplicated = true;
              break;
            }
          }
          if (notReplicated)
          {
            alreadyNotReplicated.add(dn);
          }
          else
          {
            notFound.add(dn);
          }
        }
      }
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (notFound.size() > 0 && displayErrors)
      {
        println();
        println(ERR_REPLICATION_DISABLE_SUFFIXES_NOT_FOUND.get(
                Utils.getStringFromCollection(notFound,
                    Constants.LINE_SEPARATOR)));
      }
      if (alreadyNotReplicated.size() > 0 && displayErrors)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
                Utils.getStringFromCollection(alreadyNotReplicated,
                    Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        boolean confirmationLimitReached = false;
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            if (displayErrors)
            {
              println();
              println(ERR_NO_SUFFIXES_AVAILABLE_TO_DISABLE_REPLICATION.get());
            }
            break;
          }
          else
          {
            if (areSuffixRequired)
            {
              println();
              println(ERR_NO_SUFFIXES_SELECTED_TO_DISABLE.get());
            }
            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                try
                {
                  if (askConfirmation(
                      INFO_REPLICATION_DISABLE_SUFFIX_PROMPT.get(dn), true,
                      LOG))
                  {
                    suffixes.add(dn);
                  }
                }
                catch (CLIException ce)
                {
                  println(ce.getMessageObject());
                  confirmationLimitReached = true;
                  break;
                }
              }
            }
          }
          if (confirmationLimitReached)
          {
            suffixes.clear();
            break;
          }
          if (!areSuffixRequired)
          {
            break;
          }
        }
      }
    }
  }

  /**
   * Checks that replication can actually be initialized in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be initialized and with the
   * base DNs that the user provided interactively.
   * @param ctx connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be initialized.
   */
  private void checkSuffixesForInitializeReplication(
      Collection<String> suffixes, InitialLdapContext ctx, boolean interactive)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>();
    TreeSet<String> notReplicatedSuffixes = new TreeSet<String>();

    Collection<ReplicaDescriptor> replicas = getReplicas(ctx);
    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        availableSuffixes.add(dn);
      }
      else
      {
        notReplicatedSuffixes.add(dn);
      }
    }
    if (availableSuffixes.isEmpty())
    {
      println();
      if (argParser.isInitializeAllReplicationSubcommand())
      {
        println(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_ALL_REPLICATION.get());
      }
      else
      {
        println(
            ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_LOCAL_REPLICATION.get());
      }
      LinkedList<String> userProvidedSuffixes = argParser.getBaseDNs();
      TreeSet<String> userProvidedNotReplicatedSuffixes =
        new TreeSet<String>();
      for (String s1 : userProvidedSuffixes)
      {
        for (String s2 : notReplicatedSuffixes)
        {
          if (Utils.areDnsEqual(s1, s2))
          {
            userProvidedNotReplicatedSuffixes.add(s1);
          }
        }
      }
      if (userProvidedNotReplicatedSuffixes.size() > 0)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
            Utils.getStringFromCollection(
                userProvidedNotReplicatedSuffixes,
                Constants.LINE_SEPARATOR)));
      }
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<String> notFound = new TreeSet<String>();
      TreeSet<String> alreadyNotReplicated = new TreeSet<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          boolean notReplicated = false;
          for (String s : notReplicatedSuffixes)
          {
            if (Utils.areDnsEqual(s, dn))
            {
              notReplicated = true;
              break;
            }
          }
          if (notReplicated)
          {
            alreadyNotReplicated.add(dn);
          }
          else
          {
            notFound.add(dn);
          }
        }
      }
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_REPLICATION_INITIALIZE_LOCAL_SUFFIXES_NOT_FOUND.get(
                Utils.getStringFromCollection(notFound,
                    Constants.LINE_SEPARATOR)));
      }
      if (alreadyNotReplicated.size() > 0)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
                Utils.getStringFromCollection(alreadyNotReplicated,
                    Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        boolean confirmationLimitReached = false;
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            if (argParser.isInitializeAllReplicationSubcommand())
            {
              println(
                ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_ALL_REPLICATION.get());
            }
            else
            {
              println(
               ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_LOCAL_REPLICATION.get());
            }
            break;
          }
          else
          {
            println();
            if (argParser.isInitializeAllReplicationSubcommand())
            {
              println(ERR_NO_SUFFIXES_SELECTED_TO_INITIALIZE_ALL.get());
            }
            else if (argParser.isPreExternalInitializationSubcommand())
            {
              println(
                 ERR_NO_SUFFIXES_SELECTED_TO_PRE_EXTERNAL_INITIALIZATION.get());
            }
            else if (argParser.isPostExternalInitializationSubcommand())
            {
              println(
                ERR_NO_SUFFIXES_SELECTED_TO_POST_EXTERNAL_INITIALIZATION.get());
            }
            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                boolean addSuffix;
                try
                {
                  if (argParser.isPreExternalInitializationSubcommand())
                  {
                    addSuffix = askConfirmation(
                    INFO_REPLICATION_PRE_EXTERNAL_INITIALIZATION_SUFFIX_PROMPT.
                        get(dn), true, LOG);
                  }
                  else if (argParser.isPostExternalInitializationSubcommand())
                  {
                    addSuffix = askConfirmation(
                    INFO_REPLICATION_POST_EXTERNAL_INITIALIZATION_SUFFIX_PROMPT.
                        get(dn), true, LOG);
                  }
                  else
                  {
                    addSuffix = askConfirmation(
                        INFO_REPLICATION_INITIALIZE_ALL_SUFFIX_PROMPT.get(dn),
                        true, LOG);
                  }
                }
                catch (CLIException ce)
                {
                  println(ce.getMessageObject());
                  confirmationLimitReached = true;
                  break;
                }
                if (addSuffix)
                {
                  suffixes.add(dn);
                }
              }
            }
          }
          if (confirmationLimitReached)
          {
            suffixes.clear();
            break;
          }
        }
      }
    }
  }


  /**
   * Checks that we can initialize the provided baseDNs between the two servers.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be enabled and with the
   * base DNs that the user provided interactively.
   * @param ctxSource connection to the source server.
   * @param ctxDestination connection to the destination server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be initialized.
   */
  private void checkSuffixesForInitializeReplication(
      Collection<String> suffixes, InitialLdapContext ctxSource,
      InitialLdapContext ctxDestination, boolean interactive)
  {
    TreeSet<String> availableSuffixes = new TreeSet<String>(
        getCommonSuffixes(ctxSource, ctxDestination,
            SuffixRelationType.REPLICATED));
    if (availableSuffixes.isEmpty())
    {
      println();
      println(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_REPLICATION.get());
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      LinkedList<String> notFound = new LinkedList<String>();
      for (String dn : suffixes)
      {
        boolean found = false;
        for (String dn1 : availableSuffixes)
        {
          if (Utils.areDnsEqual(dn, dn1))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          notFound.add(dn);
        }
      }
      suffixes.removeAll(notFound);
      if (notFound.size() > 0)
      {
        println();
        println(ERR_SUFFIXES_CANNOT_BE_INITIALIZED.get(
                Utils.getStringFromCollection(notFound,
                    Constants.LINE_SEPARATOR)));
      }
      if (interactive)
      {
        boolean confirmationLimitReached = false;
        while (suffixes.isEmpty())
        {
          boolean noSchemaOrAds = false;
          for (String s: availableSuffixes)
          {
            if (!Utils.areDnsEqual(s, ADSContext.getAdministrationSuffixDN()) &&
                !Utils.areDnsEqual(s, Constants.SCHEMA_DN) &&
                !Utils.areDnsEqual(s, Constants.REPLICATION_CHANGES_DN))
            {
              noSchemaOrAds = true;
            }
          }
          if (!noSchemaOrAds)
          {
            // In interactive mode we do not propose to manage the
            // administration suffix.
            println();
            println(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_REPLICATION.get());
            break;
          }
          else
          {
            println();
            println(ERR_NO_SUFFIXES_SELECTED_TO_INITIALIZE.get());

            for (String dn : availableSuffixes)
            {
              if (!Utils.areDnsEqual(dn,
                  ADSContext.getAdministrationSuffixDN()) &&
                  !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
                  !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
              {
                try
                {
                  if (askConfirmation(
                      INFO_REPLICATION_INITIALIZE_SUFFIX_PROMPT.get(dn), true,
                      LOG))
                  {
                    suffixes.add(dn);
                  }
                }
                catch (CLIException ce)
                {
                  println(ce.getMessageObject());
                  confirmationLimitReached = true;
                  break;
                }
              }
            }
          }
          if (confirmationLimitReached)
          {
            suffixes.clear();
            break;
          }
        }
      }
    }
  }

  /**
   * Updates the configuration in the two servers (and in other servers if
   * they are referenced) to enable replication.
   * @param ctx1 the connection to the first server.
   * @param ctx2 the connection to the second server.
   * @param uData the EnableReplicationUserData object containing the required
   * parameters to update the configuration.
   * @throws ReplicationCliException if there is an error.
   */
  private void updateConfiguration(InitialLdapContext ctx1,
      InitialLdapContext ctx2, EnableReplicationUserData uData)
  throws ReplicationCliException
  {
    LinkedHashSet<String> twoReplServers = new LinkedHashSet<String>();
    LinkedHashSet<String> allRepServers = new LinkedHashSet<String>();
    HashMap<String, LinkedHashSet<String>> hmRepServers =
      new HashMap<String, LinkedHashSet<String>>();
    Set<Integer> usedReplicationServerIds = new HashSet<Integer>();
    HashMap<String, Set<Integer>> hmUsedReplicationDomainIds =
      new HashMap<String, Set<Integer>>();

    ServerDescriptor server1;
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    filter.addBaseDNToSearch(ADSContext.getAdministrationSuffixDN());
    filter.addBaseDNToSearch(Constants.SCHEMA_DN);
    for (String dn : uData.getBaseDNs())
    {
      filter.addBaseDNToSearch(dn);
    }
    try
    {
      server1 = ServerDescriptor.createStandalone(ctx1, filter);
    }
    catch (NamingException ne)
    {
      throw new ReplicationCliException(
          getMessageForException(ne, ConnectionUtils.getHostPort(ctx1)),
          ERROR_READING_CONFIGURATION, ne);
    }
    ServerDescriptor server2;
    try
    {
      server2 = ServerDescriptor.createStandalone(ctx2, filter);
    }
    catch (NamingException ne)
    {
      throw new ReplicationCliException(
          getMessageForException(ne, ConnectionUtils.getHostPort(ctx2)),
          ERROR_READING_CONFIGURATION, ne);
    }

    ADSContext adsCtx1 = new ADSContext(ctx1);
    ADSContext adsCtx2 = new ADSContext(ctx2);

    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found in the already
      // registered servers.
      LinkedHashSet<Message> messages = new LinkedHashSet<Message>();
      try
      {
        LinkedHashSet<PreferredConnection> cnx =
          new LinkedHashSet<PreferredConnection>();
        cnx.addAll(PreferredConnection.getPreferredConnections(ctx1));
        cnx.addAll(PreferredConnection.getPreferredConnections(ctx2));
        if (adsCtx1.hasAdminData())
        {
          TopologyCache cache = new TopologyCache(adsCtx1, getTrustManager(),
              getConnectTimeout());
          cache.setPreferredConnections(cnx);
          cache.getFilter().setSearchMonitoringInformation(false);
          for (String dn : uData.getBaseDNs())
          {
            cache.getFilter().addBaseDNToSearch(dn);
          }
          cache.reloadTopology();
          messages.addAll(cache.getErrorMessages());
        }

        if (adsCtx2.hasAdminData())
        {
          TopologyCache cache = new TopologyCache(adsCtx2, getTrustManager(),
              getConnectTimeout());
          cache.setPreferredConnections(cnx);
          cache.getFilter().setSearchMonitoringInformation(false);
          for (String dn : uData.getBaseDNs())
          {
            cache.getFilter().addBaseDNToSearch(dn);
          }
          cache.reloadTopology();
          messages.addAll(cache.getErrorMessages());
        }
      }
      catch (TopologyCacheException tce)
      {
        throw new ReplicationCliException(
            ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
            ERROR_READING_TOPOLOGY_CACHE, tce);
      }
      catch (ADSContextException adce)
      {
        throw new ReplicationCliException(
            ERR_REPLICATION_READING_ADS.get(adce.getMessage()),
            ERROR_READING_ADS, adce);
      }
      if (!messages.isEmpty())
      {
        println(ERR_REPLICATION_READING_REGISTERED_SERVERS_WARNING.get(
                Utils.getMessageFromCollection(messages,
                    Constants.LINE_SEPARATOR).toString()));
      }
    }
    // Check whether there is more than one replication server in the
    // topology.
    Set<String> baseDNsWithOneReplicationServer = new TreeSet<String>();
    Set<String> baseDNsWithNoReplicationServer = new TreeSet<String>();
    updateBaseDnsWithNotEnoughReplicationServer(adsCtx1, adsCtx2, uData,
       baseDNsWithNoReplicationServer, baseDNsWithOneReplicationServer);

    if (!baseDNsWithNoReplicationServer.isEmpty())
    {
      Message errorMsg =
        ERR_REPLICATION_NO_REPLICATION_SERVER.get(
            Utils.getStringFromCollection(baseDNsWithNoReplicationServer,
                Constants.LINE_SEPARATOR));
      throw new ReplicationCliException(
          errorMsg,
          ReplicationCliReturnCode.ERROR_USER_DATA, null);
    }
    else if (!baseDNsWithOneReplicationServer.isEmpty())
    {
      if (isInteractive())
      {
        Message confirmMsg =
          INFO_REPLICATION_ONLY_ONE_REPLICATION_SERVER_CONFIRM.get(
              Utils.getStringFromCollection(baseDNsWithOneReplicationServer,
                  Constants.LINE_SEPARATOR));
        try
        {
          if (!confirmAction(confirmMsg, false))
          {
            throw new ReplicationCliException(
                ERR_REPLICATION_USER_CANCELLED.get(),
                ReplicationCliReturnCode.USER_CANCELLED, null);
          }
        }
        catch (Throwable t)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_USER_CANCELLED.get(),
              ReplicationCliReturnCode.USER_CANCELLED, t);
        }
      }
      else
      {
        Message warningMsg =
          INFO_REPLICATION_ONLY_ONE_REPLICATION_SERVER_WARNING.get(
              Utils.getStringFromCollection(baseDNsWithOneReplicationServer,
                  Constants.LINE_SEPARATOR));
        println(warningMsg);
        println();
      }
    }

    // These are used to identify which server we use to initialize
    // the contents of the other server (if any).
    InitialLdapContext ctxSource = null;
    InitialLdapContext ctxDestination = null;
    ADSContext adsCtxSource = null;

    boolean adsAlreadyReplicated = false;
    boolean adsMergeDone = false;

    printProgress(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_UPDATING_ADS_CONTENTS.get()));
    try
    {
      if (adsCtx1.hasAdminData() && adsCtx2.hasAdminData())
      {
        Set<Map<ADSContext.ServerProperty, Object>> registry1 =
          adsCtx1.readServerRegistry();
        Set<Map<ADSContext.ServerProperty, Object>> registry2 =
          adsCtx2.readServerRegistry();
        if (registry2.size() <= 1)
        {
          if (!hasAdministrator(adsCtx1.getDirContext(), uData))
          {
            adsCtx1.createAdministrator(getAdministratorProperties(uData));
          }
          server2.updateAdsPropertiesWithServerProperties();
          registerServer(adsCtx1, server2.getAdsProperties());
          if (!ADSContext.isRegistered(server1, registry1))
          {
            server1.updateAdsPropertiesWithServerProperties();
            registerServer(adsCtx1, server1.getAdsProperties());
          }

          ctxSource = ctx1;
          ctxDestination = ctx2;
          adsCtxSource = adsCtx1;
        }
        else if (registry1.size() <= 1)
        {
          if (!hasAdministrator(adsCtx2.getDirContext(), uData))
          {
            adsCtx2.createAdministrator(getAdministratorProperties(uData));
          }
          server1.updateAdsPropertiesWithServerProperties();
          registerServer(adsCtx2, server1.getAdsProperties());

          if (!ADSContext.isRegistered(server2, registry2))
          {
            server2.updateAdsPropertiesWithServerProperties();
            registerServer(adsCtx2, server2.getAdsProperties());
          }

          ctxSource = ctx2;
          ctxDestination = ctx1;
          adsCtxSource = adsCtx2;
        }
        else if (!areEqual(registry1, registry2))
        {
          printProgress(formatter.getFormattedDone());
          printlnProgress();

          boolean isFirstSource = mergeRegistries(adsCtx1, adsCtx2);
          if (isFirstSource)
          {
            ctxSource = ctx1;
          }
          else
          {
            ctxSource = ctx2;
          }
          adsMergeDone = true;
        }
        else
        {
          // They are already replicated: nothing to do in terms of ADS
          // initialization or ADS update data
          adsAlreadyReplicated = isBaseDNReplicated(server1, server2,
              ADSContext.getAdministrationSuffixDN());

          if (!adsAlreadyReplicated)
          {
            // Try to merge if both are replicated
            boolean isADS1Replicated = isBaseDNReplicated(server1,
                ADSContext.getAdministrationSuffixDN());
            boolean isADS2Replicated = isBaseDNReplicated(server2,
                ADSContext.getAdministrationSuffixDN());
            if (isADS1Replicated && isADS2Replicated)
            {
              // Merge
              printProgress(formatter.getFormattedDone());
              printlnProgress();

              boolean isFirstSource = mergeRegistries(adsCtx1, adsCtx2);
              if (isFirstSource)
              {
                ctxSource = ctx1;
              }
              else
              {
                ctxSource = ctx2;
              }
              adsMergeDone = true;
            }
            else if (isADS1Replicated || !isADS2Replicated)
            {
              // The case where only the first ADS is replicated or none
              // is replicated.
              if (!hasAdministrator(adsCtx1.getDirContext(), uData))
              {
                adsCtx1.createAdministrator(getAdministratorProperties(uData));
              }
              server2.updateAdsPropertiesWithServerProperties();
              registerServer(adsCtx1, server2.getAdsProperties());
              if (!ADSContext.isRegistered(server1, registry1))
              {
                server1.updateAdsPropertiesWithServerProperties();
                registerServer(adsCtx1, server1.getAdsProperties());
              }

              ctxSource = ctx1;
              ctxDestination = ctx2;
              adsCtxSource = adsCtx1;
            }
            else if (isADS2Replicated)
            {
              if (!hasAdministrator(adsCtx2.getDirContext(), uData))
              {
                adsCtx2.createAdministrator(getAdministratorProperties(uData));
              }
              server1.updateAdsPropertiesWithServerProperties();
              registerServer(adsCtx2, server1.getAdsProperties());
              if (!ADSContext.isRegistered(server2, registry2))
              {
                server2.updateAdsPropertiesWithServerProperties();
                registerServer(adsCtx2, server2.getAdsProperties());
              }

              ctxSource = ctx2;
              ctxDestination = ctx1;
              adsCtxSource = adsCtx2;
            }
          }
        }
      }
      else if (!adsCtx1.hasAdminData() && adsCtx2.hasAdminData())
      {
//        adsCtx1.createAdministrationSuffix(null);
        if (!hasAdministrator(adsCtx2.getDirContext(), uData))
        {
          adsCtx2.createAdministrator(getAdministratorProperties(uData));
        }
        server1.updateAdsPropertiesWithServerProperties();
        registerServer(adsCtx2, server1.getAdsProperties());
        Set<Map<ADSContext.ServerProperty, Object>> registry2 =
          adsCtx2.readServerRegistry();
        if (!ADSContext.isRegistered(server2, registry2))
        {
          server2.updateAdsPropertiesWithServerProperties();
          registerServer(adsCtx2, server2.getAdsProperties());
        }

        ctxSource = ctx2;
        ctxDestination = ctx1;
        adsCtxSource = adsCtx2;
      }
      else if (adsCtx1.hasAdminData() && !adsCtx2.hasAdminData())
      {
//        adsCtx2.createAdministrationSuffix(null);
        if (!hasAdministrator(adsCtx1.getDirContext(), uData))
        {
          adsCtx1.createAdministrator(getAdministratorProperties(uData));
        }
        server2.updateAdsPropertiesWithServerProperties();
        registerServer(adsCtx1, server2.getAdsProperties());
        Set<Map<ADSContext.ServerProperty, Object>> registry1 =
          adsCtx1.readServerRegistry();
        if (!ADSContext.isRegistered(server1, registry1))
        {
          server1.updateAdsPropertiesWithServerProperties();
          registerServer(adsCtx1, server1.getAdsProperties());
        }

        ctxSource = ctx1;
        ctxDestination = ctx2;
        adsCtxSource = adsCtx1;
      }
      else
      {
        adsCtx1.createAdminData(null);
        if (!hasAdministrator(ctx1, uData))
        {
          // This could occur if the user created an administrator without
          // registering any server.
          adsCtx1.createAdministrator(getAdministratorProperties(uData));
        }
        server1.updateAdsPropertiesWithServerProperties();
        adsCtx1.registerServer(server1.getAdsProperties());
        server2.updateAdsPropertiesWithServerProperties();
        adsCtx1.registerServer(server2.getAdsProperties());
//        adsCtx2.createAdministrationSuffix(null);

        ctxSource = ctx1;
        ctxDestination = ctx2;
        adsCtxSource = adsCtx1;
      }
    }
    catch (ADSContextException adce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_UPDATING_ADS.get(adce.getMessageObject()),
          ERROR_UPDATING_ADS, adce);
    }
    if (!adsAlreadyReplicated && !adsMergeDone)
    {
      try
      {
        ServerDescriptor.seedAdsTrustStore(ctxDestination,
            adsCtxSource.getTrustedCertificates());
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error seeding truststores: "+t, t);
        String arg = (t instanceof OpenDsException) ?
            ((OpenDsException)t).getMessageObject().toString() : t.toString();
        throw new ReplicationCliException(
            ERR_REPLICATION_ENABLE_SEEDING_TRUSTSTORE.get(
                ConnectionUtils.getHostPort(ctxDestination),
                ConnectionUtils.getHostPort(adsCtxSource.getDirContext()),
               arg),
            ERROR_SEEDING_TRUSTORE, t);
      }
    }
    if (!adsMergeDone)
    {
      printProgress(formatter.getFormattedDone());
      printlnProgress();
    }
    LinkedList<String> baseDNs = uData.getBaseDNs();
    if (!adsAlreadyReplicated)
    {
      boolean found = false;
      for (String dn : baseDNs)
      {
        if (Utils.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()))
        {
          found = true;
          break;
        }
      }
      if (!found)
      {
        baseDNs.add(ADSContext.getAdministrationSuffixDN());
        uData.setBaseDNs(baseDNs);
      }
    }

    if (uData.replicateSchema())
    {
      baseDNs = uData.getBaseDNs();
      baseDNs.add(Constants.SCHEMA_DN);
      uData.setBaseDNs(baseDNs);
    }
    TopologyCache cache1 = null;
    TopologyCache cache2 = null;

    try
    {
      LinkedHashSet<PreferredConnection> cnx =
        new LinkedHashSet<PreferredConnection>();
      cnx.addAll(PreferredConnection.getPreferredConnections(ctx1));
      cnx.addAll(PreferredConnection.getPreferredConnections(ctx2));
      if (adsCtx1.hasAdminData())
      {
        cache1 = new TopologyCache(adsCtx1, getTrustManager(),
            getConnectTimeout());
        cache1.setPreferredConnections(cnx);
        cache1.getFilter().setSearchMonitoringInformation(false);
        for (String dn : uData.getBaseDNs())
        {
          cache1.getFilter().addBaseDNToSearch(dn);
        }
        cache1.reloadTopology();
        usedReplicationServerIds.addAll(getReplicationServerIds(cache1));
      }

      if (adsCtx2.hasAdminData())
      {
        cache2 = new TopologyCache(adsCtx2, getTrustManager(),
            getConnectTimeout());
        cache2.setPreferredConnections(cnx);
        cache2.getFilter().setSearchMonitoringInformation(false);
        for (String dn : uData.getBaseDNs())
        {
          cache2.getFilter().addBaseDNToSearch(dn);
        }
        cache2.reloadTopology();
        usedReplicationServerIds.addAll(getReplicationServerIds(cache2));
      }
    }
    catch (ADSContextException adce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(adce.getMessage()),
          ERROR_READING_ADS, adce);
    }
    catch (TopologyCacheException tce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }

    if (server1.isReplicationServer())
    {
      twoReplServers.add(server1.getReplicationServerHostPort());
      usedReplicationServerIds.add(server1.getReplicationServerId());
    }
    else if (uData.configureReplicationServer1())
    {
      twoReplServers.add(getReplicationServer(
          ConnectionUtils.getHostName(ctx1), uData.getReplicationPort1()));
    }
    if (server2.isReplicationServer())
    {
      twoReplServers.add(server2.getReplicationServerHostPort());
      usedReplicationServerIds.add(server2.getReplicationServerId());
    }
    else if (uData.configureReplicationServer2())
    {
      twoReplServers.add(getReplicationServer(
          ConnectionUtils.getHostName(ctx2), uData.getReplicationPort2()));
    }

    for (String baseDN : uData.getBaseDNs())
    {
      LinkedHashSet<String> repServersForBaseDN = new LinkedHashSet<String>();
      repServersForBaseDN.addAll(getReplicationServers(baseDN, cache1,
          server1));
      repServersForBaseDN.addAll(getReplicationServers(baseDN, cache2,
          server2));
      repServersForBaseDN.addAll(twoReplServers);
      hmRepServers.put(baseDN, repServersForBaseDN);

      Set<Integer> ids = new HashSet<Integer>();
      ids.addAll(getReplicationDomainIds(baseDN, server1));
      ids.addAll(getReplicationDomainIds(baseDN, server2));
      if (cache1 != null)
      {
        for (ServerDescriptor server : cache1.getServers())
        {
          ids.addAll(getReplicationDomainIds(baseDN, server));
        }
      }
      if (cache2 != null)
      {
        for (ServerDescriptor server : cache2.getServers())
        {
          ids.addAll(getReplicationDomainIds(baseDN, server));
        }
      }
      hmUsedReplicationDomainIds.put(baseDN, ids);
    }
    for (LinkedHashSet<String> v : hmRepServers.values())
    {
      allRepServers.addAll(v);
    }

    Set<String> alreadyConfiguredReplicationServers = new HashSet<String>();
    if (!server1.isReplicationServer() && uData.configureReplicationServer1())
    {
      try
      {
        configureAsReplicationServer(ctx1, uData.getReplicationPort1(),
            uData.isSecureReplication1(), allRepServers,
            usedReplicationServerIds);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
    }
    else if (server1.isReplicationServer())
    {
      try
      {
        updateReplicationServer(ctx1, allRepServers);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
      if (argParser.replicationPort1Arg.isPresent())
      {
        // Inform the user that the provided value will be ignored
        if (uData.getReplicationPort1() !=
          server1.getReplicationServerPort())
        {
          LOG.log(Level.WARNING, "Ignoring provided replication port for "+
              "first server (already configured with port "+
              server1.getReplicationServerPort()+")");
          println(WARN_FIRST_REPLICATION_SERVER_ALREADY_CONFIGURED.get(
              server1.getReplicationServerPort(), uData.getReplicationPort1()));
        }
      }
    }
    alreadyConfiguredReplicationServers.add(server1.getId());
    if (!server2.isReplicationServer() && uData.configureReplicationServer2())
    {
      try
      {
        configureAsReplicationServer(ctx2, uData.getReplicationPort2(),
            uData.isSecureReplication2(), allRepServers,
            usedReplicationServerIds);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
    }
    else if (server2.isReplicationServer())
    {
      try
      {
        updateReplicationServer(ctx2, allRepServers);
      }
      catch (OpenDsException ode)
      {
        throw new ReplicationCliException(
            getMessageForReplicationServerException(ode,
            ConnectionUtils.getHostPort(ctx1)),
            ERROR_CONFIGURING_REPLICATIONSERVER, ode);
      }
      if (argParser.replicationPort2Arg.isPresent())
      {
        // Inform the user that the provided value will be ignored
        if (uData.getReplicationPort2() !=
          server2.getReplicationServerPort())
        {
          LOG.log(Level.WARNING, "Ignoring provided replication port for "+
              "second server (already configured with port "+
              server2.getReplicationServerPort()+")");
          println(WARN_SECOND_REPLICATION_SERVER_ALREADY_CONFIGURED.get(
              server2.getReplicationServerPort(), uData.getReplicationPort2()));
        }
      }
    }
    alreadyConfiguredReplicationServers.add(server2.getId());

    for (String baseDN : uData.getBaseDNs())
    {
      LinkedHashSet<String> repServers = hmRepServers.get(baseDN);
      Set<Integer> usedIds = hmUsedReplicationDomainIds.get(baseDN);
      Set<String> alreadyConfiguredServers = new HashSet<String>();

      if (uData.configureReplicationDomain1() ||
          Utils.areDnsEqual(baseDN, ADSContext.getAdministrationSuffixDN()))
      {
        try
        {
          configureToReplicateBaseDN(ctx1, baseDN, repServers, usedIds);
        }
        catch (OpenDsException ode)
        {
          Message msg = getMessageForEnableException(ode,
              ConnectionUtils.getHostPort(ctx1), baseDN);
          throw new ReplicationCliException(msg,
              ERROR_ENABLING_REPLICATION_ON_BASEDN, ode);
        }
      }
      alreadyConfiguredServers.add(server1.getId());

      if (uData.configureReplicationDomain2() ||
          Utils.areDnsEqual(baseDN, ADSContext.getAdministrationSuffixDN()))
      {
        try
        {
          configureToReplicateBaseDN(ctx2, baseDN, repServers, usedIds);
        }
        catch (OpenDsException ode)
        {
          Message msg = getMessageForEnableException(ode,
              ConnectionUtils.getHostPort(ctx2), baseDN);
          throw new ReplicationCliException(msg,
              ERROR_ENABLING_REPLICATION_ON_BASEDN, ode);
        }
      }
      alreadyConfiguredServers.add(server2.getId());

      if (cache1 != null)
      {
        configureToReplicateBaseDN(baseDN, repServers, usedIds, cache1, server1,
            alreadyConfiguredServers, allRepServers,
            alreadyConfiguredReplicationServers);
      }
      if (cache2 != null)
      {
        configureToReplicateBaseDN(baseDN, repServers, usedIds, cache2, server2,
            alreadyConfiguredServers, allRepServers,
            alreadyConfiguredReplicationServers);
      }
    }

    // Now that replication is configured in all servers, simply try to
    // initialize the contents of one ADS with the other (in the case where
    // already both servers were replicating the same ADS there is nothing to be
    // done).
    if (adsMergeDone)
    {
      PointAdder pointAdder = new PointAdder(this);
      printProgress(
          INFO_ENABLE_REPLICATION_INITIALIZING_ADS_ALL.get(
              ConnectionUtils.getHostPort(ctxSource)));
      pointAdder.start();
      try
      {
        initializeAllSuffix(ADSContext.getAdministrationSuffixDN(),
          ctxSource, false);
      }
      finally
      {
        pointAdder.stop();
      }
      printProgress(formatter.getSpace());
      printProgress(formatter.getFormattedDone());
      printlnProgress();
    }
    else if ((ctxSource != null) && (ctxDestination != null))
    {
      printProgress(formatter.getFormattedWithPoints(
          INFO_ENABLE_REPLICATION_INITIALIZING_ADS.get(
              ConnectionUtils.getHostPort(ctxDestination),
              ConnectionUtils.getHostPort(ctxSource))));

      initializeSuffix(ADSContext.getAdministrationSuffixDN(), ctxSource,
          ctxDestination, false);
      printProgress(formatter.getFormattedDone());
      printlnProgress();
    }

    // If we must initialize the schema do so.
    if (mustInitializeSchema(server1, server2, uData))
    {
      if (argParser.useSecondServerAsSchemaSource())
      {
        ctxSource = ctx2;
        ctxDestination = ctx1;
      }
      else
      {
        ctxSource = ctx1;
        ctxDestination = ctx2;
      }
      if (adsMergeDone)
      {
        PointAdder pointAdder = new PointAdder(this);
        printProgress(
            INFO_ENABLE_REPLICATION_INITIALIZING_SCHEMA.get(
                ConnectionUtils.getHostPort(ctxDestination),
                ConnectionUtils.getHostPort(ctxSource)));
        pointAdder.start();
        try
        {
          initializeAllSuffix(Constants.SCHEMA_DN, ctxSource, false);
        }
        finally
        {
          pointAdder.stop();
        }
        printProgress(formatter.getSpace());
      }
      else
      {
        printProgress(formatter.getFormattedWithPoints(
            INFO_ENABLE_REPLICATION_INITIALIZING_SCHEMA.get(
                ConnectionUtils.getHostPort(ctxDestination),
                ConnectionUtils.getHostPort(ctxSource))));
        initializeSuffix(Constants.SCHEMA_DN, ctxSource,
          ctxDestination, false);
      }
      printProgress(formatter.getFormattedDone());
      printlnProgress();
    }
  }

  /**
   * Updates the configuration in the server (and in other servers if
   * they are referenced) to disable replication.
   * @param ctx the connection to the server.
   * @param uData the DisableReplicationUserData object containing the required
   * parameters to update the configuration.
   * @throws ReplicationCliException if there is an error.
   */
  private void updateConfiguration(InitialLdapContext ctx,
      DisableReplicationUserData uData) throws ReplicationCliException
  {
    ServerDescriptor server;
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    if (!uData.disableAll())
    {
      filter.addBaseDNToSearch(ADSContext.getAdministrationSuffixDN());
      for (String dn : uData.getBaseDNs())
      {
        filter.addBaseDNToSearch(dn);
      }
    }
    try
    {
      server = ServerDescriptor.createStandalone(ctx, filter);
    }
    catch (NamingException ne)
    {
      throw new ReplicationCliException(
          getMessageForException(ne, ConnectionUtils.getHostPort(ctx)),
          ERROR_READING_CONFIGURATION, ne);
    }

    ADSContext adsCtx = new ADSContext(ctx);

    TopologyCache cache = null;
    // Only try to update remote server if the user provided a Global
    // Administrator to authenticate.
    boolean tryToUpdateRemote = uData.getAdminUid() != null;
    try
    {
      if (adsCtx.hasAdminData() && tryToUpdateRemote)
      {
        cache = new TopologyCache(adsCtx, getTrustManager(),
            getConnectTimeout());
        cache.setPreferredConnections(
            PreferredConnection.getPreferredConnections(ctx));
        cache.getFilter().setSearchMonitoringInformation(false);
        if (!uData.disableAll())
        {
          for (String dn : uData.getBaseDNs())
          {
            cache.getFilter().addBaseDNToSearch(dn);
          }
        }
        cache.reloadTopology();
      }
    }
    catch (ADSContextException adce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(adce.getMessage()),
          ERROR_READING_ADS, adce);
    }
    catch (TopologyCacheException tce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found.
      LinkedHashSet<Message> messages = new LinkedHashSet<Message>();
      if (cache != null)
      {
        messages.addAll(cache.getErrorMessages());
      }
      if (!messages.isEmpty())
      {
        println(
            ERR_REPLICATION_READING_REGISTERED_SERVERS_WARNING.get(
                Utils.getMessageFromCollection(messages,
                    Constants.LINE_SEPARATOR).toString()));
      }
    }

    boolean disableReplicationServer = false;
    if (server.isReplicationServer() &&
        (uData.disableReplicationServer() || uData.disableAll()))
    {
      disableReplicationServer = true;
    }

    if ((cache != null) && disableReplicationServer)
    {
      String replicationServer = server.getReplicationServerHostPort();
      // Figure out if this is the last replication server for a given
      // topology (containing a different replica) or there will be only
      // another replication server left (single point of failure).
      Set<SuffixDescriptor> lastRepServer =
        new TreeSet<SuffixDescriptor>(new SuffixComparator());

      Set<SuffixDescriptor> beforeLastRepServer =
        new TreeSet<SuffixDescriptor>(new SuffixComparator());

      for (SuffixDescriptor suffix : cache.getSuffixes())
      {
        if (Utils.areDnsEqual(suffix.getDN(),
            ADSContext.getAdministrationSuffixDN()) ||
            Utils.areDnsEqual(suffix.getDN(), Constants.SCHEMA_DN))
        {
          // Do not display these suffixes.
          continue;
        }

        Set<String> repServers = suffix.getReplicationServers();

        if (repServers.size() <= 2)
        {
          boolean found = false;
          for (String repServer : repServers)
          {
            if (repServer.equalsIgnoreCase(replicationServer))
            {
              found = true;
              break;
            }
          }
          if (found)
          {
            if (repServers.size() == 2)
            {
              beforeLastRepServer.add(suffix);
            }
            else
            {
              lastRepServer.add(suffix);
            }
          }
        }
      }

      // Inform the user
      if (beforeLastRepServer.size() > 0)
      {
        LinkedHashSet<String> baseDNs = new LinkedHashSet<String>();
        for (SuffixDescriptor suffix : beforeLastRepServer)
        {
          if (!Utils.areDnsEqual(suffix.getDN(),
              ADSContext.getAdministrationSuffixDN()) &&
              !Utils.areDnsEqual(suffix.getDN(), Constants.SCHEMA_DN))
          {
            // Do not display these suffixes.
            baseDNs.add(suffix.getDN());
          }
        }
        if (!baseDNs.isEmpty())
        {
          String arg =
            Utils.getStringFromCollection(baseDNs, Constants.LINE_SEPARATOR);
          if (!isInteractive())
          {
            println(INFO_DISABLE_REPLICATION_ONE_POINT_OF_FAILURE.get(arg));
          }
          else
          {
            try
            {
              if (!askConfirmation(
                  INFO_DISABLE_REPLICATION_ONE_POINT_OF_FAILURE_PROMPT.get(arg),
                      false, LOG))
              {
                throw new ReplicationCliException(
                    ERR_REPLICATION_USER_CANCELLED.get(),
                    ReplicationCliReturnCode.USER_CANCELLED, null);
              }
            }
            catch (CLIException ce)
            {
              println(ce.getMessageObject());
              throw new ReplicationCliException(
                  ERR_REPLICATION_USER_CANCELLED.get(),
                  ReplicationCliReturnCode.USER_CANCELLED, null);
            }
          }
        }
      }
      if (lastRepServer.size() > 0)
      {
        // Check that there are other replicas and that this message, really
        // makes sense to be displayed.
        LinkedHashSet<String> suffixArg = new LinkedHashSet<String>();
        for (SuffixDescriptor suffix : lastRepServer)
        {
          boolean baseDNSpecified = false;
          for (String baseDN : uData.getBaseDNs())
          {
            if (Utils.areDnsEqual(baseDN,
                ADSContext.getAdministrationSuffixDN()) ||
                Utils.areDnsEqual(baseDN, Constants.SCHEMA_DN))
            {
              // Do not display these suffixes.
              continue;
            }
            if (Utils.areDnsEqual(baseDN, suffix.getDN()))
            {
              baseDNSpecified = true;
              break;
            }
          }
          if (!baseDNSpecified)
          {
            Set<ServerDescriptor> servers =
              new TreeSet<ServerDescriptor>(new ServerComparator());
            for (ReplicaDescriptor replica : suffix.getReplicas())
            {
              servers.add(replica.getServer());
            }
            suffixArg.add(getSuffixDisplay(suffix.getDN(), servers));
          }
          else
          {
            // Check that there are other replicas.
            if (suffix.getReplicas().size() > 1)
            {
              // If there is just one replica, it is the one in this server.
              Set<ServerDescriptor> servers =
                new TreeSet<ServerDescriptor>(new ServerComparator());
              for (ReplicaDescriptor replica : suffix.getReplicas())
              {
                if (!replica.getServer().isSameServer(server))
                {
                  servers.add(replica.getServer());
                }
              }
              if (!servers.isEmpty())
              {
                suffixArg.add(getSuffixDisplay(suffix.getDN(), servers));
              }
            }
          }
        }

        if (!suffixArg.isEmpty())
        {
          String arg =
            Utils.getStringFromCollection(suffixArg, Constants.LINE_SEPARATOR);
          if (!isInteractive())
          {
            println(INFO_DISABLE_REPLICATION_DISABLE_IN_REMOTE.get(arg));
          }
          else
          {
            try
            {
              if (!askConfirmation(
                  INFO_DISABLE_REPLICATION_DISABLE_IN_REMOTE_PROMPT.get(arg),
                      false, LOG))
              {
                throw new ReplicationCliException(
                    ERR_REPLICATION_USER_CANCELLED.get(),
                    ReplicationCliReturnCode.USER_CANCELLED, null);
              }
            }
            catch (CLIException ce)
            {
              println(ce.getMessageObject());
              throw new ReplicationCliException(
                  ERR_REPLICATION_USER_CANCELLED.get(),
                  ReplicationCliReturnCode.USER_CANCELLED, null);
            }
          }
        }
      }
    }

    /**
     * Try to figure out if we must explicitly disable replication on
     * cn=admin data and cn=schema.
     */
    boolean forceDisableSchema = false;
    boolean forceDisableADS = false;
    boolean schemaReplicated = false;
    boolean adsReplicated = false;
    boolean disableAllBaseDns = disableAllBaseDns(ctx, uData);

    Collection<ReplicaDescriptor> replicas = getReplicas(ctx);
    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          adsReplicated = true;
        }
        else if (Utils.areDnsEqual(Constants.SCHEMA_DN, dn))
        {
          schemaReplicated = true;
        }
      }
    }


    if (disableAllBaseDns &&
        (disableReplicationServer || !server.isReplicationServer()))
    {
      // Unregister the server from the ADS if no other server has dependencies
      // with it (no replicated base DNs and no replication server).
      server.updateAdsPropertiesWithServerProperties();
      try
      {
        adsCtx.unregisterServer(server.getAdsProperties());
        try
        {
          // To be sure that the change gets propagated
          Thread.sleep(2000);
        }
        catch (Throwable t)
        {
        }
      }
      catch (ADSContextException adce)
      {
        LOG.log(Level.SEVERE, "Error unregistering server: "+
            server.getAdsProperties(), adce);
        if (adce.getError() != ADSContextException.ErrorType.NOT_YET_REGISTERED)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_UPDATING_ADS.get(adce.getMessageObject()),
              ERROR_READING_ADS, adce);
        }
      }
    }

    Set<String> suffixesToDisable = new HashSet<String>();

    if (uData.disableAll())
    {
      for (ReplicaDescriptor replica : server.getReplicas())
      {
        if (replica.isReplicated())
        {
          suffixesToDisable.add(replica.getSuffix().getDN());
        }
      }
    }
    else
    {
      suffixesToDisable.addAll(uData.getBaseDNs());

      if (disableAllBaseDns &&
          (disableReplicationServer || !server.isReplicationServer()))
      {
        forceDisableSchema = schemaReplicated;
        forceDisableADS = adsReplicated;
      }
      for (String dn : uData.getBaseDNs())
      {
        if (Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn))
        {
          // The user already asked this to be explicitly disabled
          forceDisableADS = false;
        }
        else if (Utils.areDnsEqual(Constants.SCHEMA_DN, dn))
        {
          // The user already asked this to be explicitly disabled
          forceDisableSchema = false;
        }
      }

      if (forceDisableSchema)
      {
        suffixesToDisable.add(Constants.SCHEMA_DN);
      }
      if (forceDisableADS)
      {
        suffixesToDisable.add(ADSContext.getAdministrationSuffixDN());
      }
    }

    String replicationServerHostPort = null;
    if (server.isReplicationServer())
    {
      replicationServerHostPort = server.getReplicationServerHostPort();
    }
    boolean replicationServerDisabled = false;

    for (String baseDN : suffixesToDisable)
    {
      try
      {
        deleteReplicationDomain(ctx, baseDN);
      }
      catch (OpenDsException ode)
      {
        Message msg = getMessageForDisableException(ode,
            ConnectionUtils.getHostPort(ctx), baseDN);
        throw new ReplicationCliException(msg,
            ERROR_DISABLING_REPLICATION_ON_BASEDN, ode);
      }
    }

    if ((replicationServerHostPort != null) && (cache != null))
    {
      Set<ServerDescriptor> serversToUpdate =
        new LinkedHashSet<ServerDescriptor>();
      Set<String> baseDNsToUpdate = new HashSet<String>(suffixesToDisable);
      for (String baseDN : baseDNsToUpdate)
      {
        SuffixDescriptor suffix = getSuffix(baseDN, cache, server);
        if (suffix != null)
        {
          for (ReplicaDescriptor replica : suffix.getReplicas())
          {
            serversToUpdate.add(replica.getServer());
          }
        }
      }
      if (disableReplicationServer)
      {
        // Find references in all servers.
        Set<SuffixDescriptor> suffixes = cache.getSuffixes();
        for (SuffixDescriptor suffix : suffixes)
        {
          boolean found = false;
          for (String repServer : suffix.getReplicationServers())
          {
            found = repServer.equalsIgnoreCase(replicationServerHostPort);
            if (found)
            {
              break;
            }
          }
          if (found)
          {
            baseDNsToUpdate.add(suffix.getDN());
            for (ReplicaDescriptor replica : suffix.getReplicas())
            {
              serversToUpdate.add(replica.getServer());
            }
          }
        }
      }
      String bindDn = ConnectionUtils.getBindDN(ctx);
      String pwd = ConnectionUtils.getBindPassword(ctx);
      for (ServerDescriptor s : serversToUpdate)
      {
        removeReferencesInServer(s, replicationServerHostPort, bindDn, pwd,
            baseDNsToUpdate, disableReplicationServer,
            PreferredConnection.getPreferredConnections(ctx));
      }

      if (disableReplicationServer)
      {
        // Disable replication server
        disableReplicationServer(ctx);
        replicationServerDisabled = true;
        // Wait to be sure that changes are taken into account and reset the
        // contents of the ADS.
        try
        {
          Thread.sleep(5000);
        }
        catch (Throwable t)
        {
        }
      }
    }
    if (disableReplicationServer && !replicationServerDisabled)
    {
      // This can happen if we could not retrieve the TopologyCache
      disableReplicationServer(ctx);
      replicationServerDisabled = true;
    }

    if (uData.disableAll())
    {
      try
      {
        // Delete all contents from ADSContext.
        printProgress(formatter.getFormattedWithPoints(
            INFO_REPLICATION_REMOVE_ADS_CONTENTS.get()));
        adsCtx.removeAdminData(false /* avoid self-disconnect */);
        printProgress(formatter.getFormattedDone());
        printlnProgress();
      }
      catch (ADSContextException adce)
      {
        LOG.log(Level.SEVERE, "Error removing contents of cn=admin data: "+
            adce, adce);
        throw new ReplicationCliException(
            ERR_REPLICATION_UPDATING_ADS.get(adce.getMessageObject()),
            ERROR_UPDATING_ADS, adce);
      }
    }
    else if (disableAllBaseDns &&
        (disableReplicationServer || !server.isReplicationServer()))
    {
      // Unregister the servers from the ADS of the local server.
      try
      {
        Set<Map<ADSContext.ServerProperty, Object>> registry =
          adsCtx.readServerRegistry();
        for (Map<ADSContext.ServerProperty, Object> s : registry)
        {
          adsCtx.unregisterServer(s);
        }
        try
        {
          // To be sure that the change gets propagated
          Thread.sleep(2000);
        }
        catch (Throwable t)
        {
        }
      }
      catch (ADSContextException adce)
      {
        // This is not critical, do not send an error
        LOG.log(Level.WARNING, "Error unregistering server: "+
            server.getAdsProperties(), adce);
      }
    }
  }

  /**
   * Displays the replication status of the different base DNs in the servers
   * registered in the ADS.
   * @param ctx the connection to the server.
   * @param uData the StatusReplicationUserData object containing the required
   * parameters to update the configuration.
   * @throws ReplicationCliException if there is an error.
   */
  private void displayStatus(InitialLdapContext ctx,
      StatusReplicationUserData uData) throws ReplicationCliException
  {
    ADSContext adsCtx = new ADSContext(ctx);

    boolean somethingDisplayed = false;
    TopologyCache cache = null;
    try
    {
      cache = new TopologyCache(adsCtx, getTrustManager(),
          getConnectTimeout());
      cache.setPreferredConnections(
          PreferredConnection.getPreferredConnections(ctx));
      for (String dn : uData.getBaseDNs())
      {
        cache.getFilter().addBaseDNToSearch(dn);
      }
      cache.reloadTopology();
    }
    catch (TopologyCacheException tce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
    if (mustPrintCommandBuilder())
    {
      try
      {
        CommandBuilder commandBuilder = createCommandBuilder(
            ReplicationCliArgumentParser.STATUS_REPLICATION_SUBCMD_NAME,
            uData);
        printCommandBuilder(commandBuilder);
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error printing equivalente command-line: "+t,
            t);
      }
    }
    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found.
      LinkedHashSet<Message> messages = new LinkedHashSet<Message>();
      if (cache != null)
      {
        messages.addAll(cache.getErrorMessages());
      }
      if (!messages.isEmpty())
      {
        Message msg =
            ERR_REPLICATION_STATUS_READING_REGISTERED_SERVERS.get(
                Utils.getMessageFromCollection(messages,
                    Constants.LINE_SEPARATOR).toString());
        println(msg);
      }
    }

    LinkedList<String> userBaseDNs = uData.getBaseDNs();
    LinkedList<Set<ReplicaDescriptor>> replicaLists =
      new LinkedList<Set<ReplicaDescriptor>>();

    boolean oneReplicated = false;

    boolean displayAll = userBaseDNs.isEmpty();
    for (SuffixDescriptor suffix : cache.getSuffixes())
    {
      String dn = suffix.getDN();

      // If no base DNs where specified display all the base DNs but the schema
      // and cn=admin data.
      boolean found = displayAll &&
      !Utils.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()) &&
      !Utils.areDnsEqual(dn, Constants.SCHEMA_DN) &&
      !Utils.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN);
      for (String baseDN : userBaseDNs)
      {
        found = Utils.areDnsEqual(baseDN, dn);
        if (found)
        {
          break;
        }
      }
      if (found)
      {
        boolean replicated = false;
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          if (replica.isReplicated())
          {
            replicated = true;
            break;
          }
        }
        if (replicated)
        {
          oneReplicated = true;
          replicaLists.add(suffix.getReplicas());
        }
        else
        {
          // Check if there are already some non replicated base DNs.
          found = false;
          for (Set<ReplicaDescriptor> replicas : replicaLists)
          {
            ReplicaDescriptor replica = replicas.iterator().next();
            if (!replica.isReplicated() &&
                Utils.areDnsEqual(dn, replica.getSuffix().getDN()))
            {
              replicas.addAll(suffix.getReplicas());
              found = true;
              break;
            }
          }
          if (!found)
          {
            replicaLists.add(suffix.getReplicas());
          }
        }
      }
    }

    if (!oneReplicated)
    {
      if (displayAll)
      {
        // Maybe there are some replication server configured...
        SortedSet<ServerDescriptor> rServers =
          new TreeSet<ServerDescriptor>(new ReplicationServerComparator());
        for (ServerDescriptor server : cache.getServers())
        {
          if (server.isReplicationServer())
          {
            rServers.add(server);
          }
        }
        if (!rServers.isEmpty())
        {
          displayStatus(rServers, uData.isScriptFriendly(),
              PreferredConnection.getPreferredConnections(ctx));
          somethingDisplayed = true;
        }
      }
    }

    if (!replicaLists.isEmpty())
    {
      LinkedList<Set<ReplicaDescriptor>> orderedReplicaLists =
        new LinkedList<Set<ReplicaDescriptor>>();
      for (Set<ReplicaDescriptor> replicas : replicaLists)
      {
        String dn1 = replicas.iterator().next().getSuffix().getDN();
        boolean inserted = false;
        for (int i=0; i<orderedReplicaLists.size() && !inserted; i++)
        {
          String dn2 =
            orderedReplicaLists.get(i).iterator().next().getSuffix().getDN();
          if (dn1.compareTo(dn2) < 0)
          {
            orderedReplicaLists.add(i, replicas);
            inserted = true;
          }
        }
        if (!inserted)
        {
          orderedReplicaLists.add(replicas);
        }
      }
      Set<ReplicaDescriptor> replicasWithNoReplicationServer =
        new HashSet<ReplicaDescriptor>();
      Set<ServerDescriptor> serversWithNoReplica =
        new HashSet<ServerDescriptor>();
      displayStatus(orderedReplicaLists, uData.isScriptFriendly(),
            PreferredConnection.getPreferredConnections(ctx),
            cache.getServers(),
            replicasWithNoReplicationServer, serversWithNoReplica);
      somethingDisplayed = true;

      if (oneReplicated && !uData.isScriptFriendly())
      {
        printlnProgress();
        printProgress(INFO_REPLICATION_STATUS_REPLICATED_LEGEND.get());

        if (!replicasWithNoReplicationServer.isEmpty() ||
            !serversWithNoReplica.isEmpty())
        {
          printlnProgress();
          printProgress(
              INFO_REPLICATION_STATUS_NOT_A_REPLICATION_SERVER_LEGEND.get());

          printlnProgress();
          printProgress(
              INFO_REPLICATION_STATUS_NOT_A_REPLICATION_DOMAIN_LEGEND.get());
        }
        printlnProgress();
        somethingDisplayed = true;
      }
    }
    if (!somethingDisplayed)
    {
      if (displayAll)
      {
        printProgress(INFO_REPLICATION_STATUS_NO_REPLICATION_INFORMATION.get());
        printlnProgress();
      }
      else
      {
        printProgress(INFO_REPLICATION_STATUS_NO_BASEDNS.get());
        printlnProgress();
      }
    }
  }

  /**
   * Displays the replication status of the replicas provided.  The code assumes
   * that all the replicas have the same baseDN and that if they are replicated
   * all the replicas are replicated with each other.
   * Note: the code assumes that all the objects come from the same read of the
   * topology cache.  So comparisons in terms of pointers can be made.
   * @param replicas the list of replicas that we are trying to display.
   * @param cnx the preferred connections used to connect to the server.
   * @param scriptFriendly wheter to display it on script-friendly mode or not.
   * @param servers all the servers configured in the topology.
   * @param replicasWithNoReplicationServer the set of replicas that will be
   * updated with all the replicas that have no replication server.
   * @param serversWithNoReplica the set of servers that will be updated with
   * all the servers that act as replication server in the topology but have
   * no replica.
   */
  private void displayStatus(
      LinkedList<Set<ReplicaDescriptor>> orderedReplicaLists,
      boolean scriptFriendly, LinkedHashSet<PreferredConnection> cnx,
      Set<ServerDescriptor> servers,
      Set<ReplicaDescriptor> replicasWithNoReplicationServer,
      Set<ServerDescriptor> serversWithNoReplica)
  {
    Set<ReplicaDescriptor> orderedReplicas =
      new LinkedHashSet<ReplicaDescriptor>();
    Set<String> hostPorts = new TreeSet<String>();
    Set<ServerDescriptor> notAddedReplicationServers =
      new TreeSet<ServerDescriptor>(new ReplicationServerComparator());
    for (Set<ReplicaDescriptor> replicas : orderedReplicaLists)
    {
      for (ReplicaDescriptor replica : replicas)
      {
        hostPorts.add(getHostPort(replica.getServer(), cnx));
      }
      for (String hostPort : hostPorts)
      {
        for (ReplicaDescriptor replica : replicas)
        {
          if (getHostPort(replica.getServer(), cnx).equals(hostPort))
          {
            orderedReplicas.add(replica);
          }
        }
      }
      for (ServerDescriptor server : servers)
      {
        if (server.isReplicationServer())
        {
          boolean isDomain = false;
          boolean isRepServer = false;
          String replicationServer = server.getReplicationServerHostPort();
          for (ReplicaDescriptor replica : replicas)
          {
            if (!isRepServer)
            {
              Set<String> repServers = replica.getReplicationServers();
              for (String repServer : repServers)
              {
                if (replicationServer.equalsIgnoreCase(repServer))
                {
                  isRepServer = true;
                }
              }
            }
            if (replica.getServer() == server)
            {
              isDomain = true;
            }
            if (isDomain && isRepServer)
            {
              break;
            }
          }
          if (!isDomain && isRepServer)
          {
            notAddedReplicationServers.add(server);
          }
        }
      }
    }

    /*
     * The table has the following columns:
     * - suffix DN;
     * - server;
     * - number of entries;
     * - replication enabled indicator;
     * - directory server instance ID;
     * - replication server;
     * - replication server ID;
     * - missing changes;
     * - age of the oldest change, and
     * - security enabled indicator.
     */
    TableBuilder tableBuilder = new TableBuilder();

    /*
     * Table headings.
     */
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_SUFFIX_DN.get());
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_SERVERPORT.get());
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_NUMBER_ENTRIES.get());
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_REPLICATION_ENABLED.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_DS_ID.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_RS_ID.get());
    tableBuilder.appendHeading(
        INFO_REPLICATION_STATUS_HEADER_REPLICATION_PORT.get());
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_MISSING_CHANGES.get());
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_AGE_OF_OLDEST_MISSING_CHANGE.get());
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_SECURE.get());

    /*
     * Table data.
     */

    for (ReplicaDescriptor replica : orderedReplicas)
    {
      tableBuilder.startRow();
      if (replica.isReplicated())
      {
        // Suffix DN
        tableBuilder.appendCell(Message.raw(replica.getSuffix().getDN()));

        // Server port
        tableBuilder.appendCell(
          Message.raw(getHostPort(replica.getServer(), cnx)));

        // Number of entries
        int nEntries = replica.getEntries();
        if (nEntries >= 0)
        {
          tableBuilder.appendCell(Message.raw(String.valueOf(nEntries)));
        }
        else
        {
          tableBuilder.appendCell(Message.raw(""));
        }

        // Replication enabled
        tableBuilder.appendCell(
          Message.raw(Boolean.toString(replica.isReplicationEnabled())));

        // DS instance ID
        tableBuilder.appendCell(
            Message.raw(Integer.toString(replica.getReplicationId())));

        // RS ID and port.
        if (replica.getServer().isReplicationServer())
        {
          tableBuilder.appendCell(Integer.toString(replica.getServer()
              .getReplicationServerId()));
          tableBuilder.appendCell(Message.raw(String.valueOf(replica
              .getServer().getReplicationServerPort())));
        }
        else
        {
          if (scriptFriendly)
          {
            tableBuilder.appendCell(Message.raw(""));
          }
          else
          {
            tableBuilder.appendCell(
              INFO_REPLICATION_STATUS_NOT_A_REPLICATION_SERVER_SHORT.get());
          }
          tableBuilder.appendCell(Message.raw(""));
          replicasWithNoReplicationServer.add(replica);
        }

        // Missing changes
        int missingChanges = replica.getMissingChanges();
        if (missingChanges >= 0)
        {
          tableBuilder.appendCell(Message.raw(String.valueOf(missingChanges)));
        }
        else
        {
          tableBuilder.appendCell(Message.raw(""));
        }

        // Age of oldest missing change
        long ageOfOldestMissingChange = replica.getAgeOfOldestMissingChange();
        if (ageOfOldestMissingChange > 0)
        {
          Date date = new Date(ageOfOldestMissingChange);
          tableBuilder.appendCell(Message.raw(date.toString()));
        }
        else
        {
          tableBuilder.appendCell(Message.raw(""));
        }

        // Secure
        if (!replica.getServer().isReplicationServer())
        {
          tableBuilder.appendCell(Message.raw(""));
        }
        else
        {
          tableBuilder.appendCell(
            Message.raw(Boolean.toString(
              replica.getServer().isReplicationSecure())));
        }
      }
      else
      {
        tableBuilder.appendCell(Message.raw(replica.getSuffix().getDN()));
        tableBuilder.appendCell(
          Message.raw(getHostPort(replica.getServer(), cnx)));
        int nEntries = replica.getEntries();
        if (nEntries >= 0)
        {
          tableBuilder.appendCell(Message.raw(String.valueOf(nEntries)));
        }
        else
        {
          tableBuilder.appendCell(Message.raw(""));
        }
        tableBuilder.appendCell(Message.raw(""));
      }
    }

    for (ServerDescriptor server : notAddedReplicationServers)
    {
      tableBuilder.startRow();
      serversWithNoReplica.add(server);

      // Suffix DN
      tableBuilder.appendCell(Message.raw(""));

      // Server port
      tableBuilder.appendCell(Message.raw(getHostPort(server, cnx)));

      // Number of entries
      if (scriptFriendly)
      {
        tableBuilder.appendCell(Message.raw(""));
      }
      else
      {
        tableBuilder.appendCell(
          INFO_REPLICATION_STATUS_NOT_A_REPLICATION_DOMAIN_SHORT.get());
      }

      // Replication enabled
      tableBuilder.appendCell(Boolean.toString(true));

      // DS ID
      tableBuilder.appendCell(Message.raw(""));

      // RS ID
      tableBuilder.appendCell(
        Message.raw(Integer.toString(server.getReplicationServerId())));

      // Replication port
      int replicationPort = server.getReplicationServerPort();
      if (replicationPort >= 0)
      {
        tableBuilder.appendCell(
          Message.raw(String.valueOf(replicationPort)));
      }
      else
      {
        tableBuilder.appendCell(Message.raw(""));
      }

      // Missing changes
      tableBuilder.appendCell(Message.raw(""));

      // Age of oldest change
      tableBuilder.appendCell(Message.raw(""));

      // Secure
      tableBuilder.appendCell(
        Message.raw(Boolean.toString(server.isReplicationSecure())));
    }

    PrintStream out = getOutputStream();
    TablePrinter printer;

    if (scriptFriendly)
    {
      printer = new TabSeparatedTablePrinter(out);
    }
    else
    {
      printer = new TextTablePrinter(out);
      ((TextTablePrinter)printer).setColumnSeparator(
        ToolConstants.LIST_TABLE_SEPARATOR);
    }
    tableBuilder.print(printer);
  }

  /**
   * Displays the replication status of the replication servers provided.  The
   * code assumes that all the servers have a replication server and that there
   * are associated with no replication domain.
   * @param servers the servers
   * @param cnx the preferred connections used to connect to the server.
   * @param scriptFriendly wheter to display it on script-friendly mode or not.
   */
  private void displayStatus(Set<ServerDescriptor> servers,
      boolean scriptFriendly, LinkedHashSet<PreferredConnection> cnx)
  {
    TableBuilder tableBuilder = new TableBuilder();
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_SERVERPORT.get());
    tableBuilder.appendHeading(
      INFO_REPLICATION_STATUS_HEADER_REPLICATION_PORT.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_SECURE.get());

    for (ServerDescriptor server : servers)
    {
      tableBuilder.startRow();
      // Server port
      tableBuilder.appendCell(Message.raw(getHostPort(server, cnx)));
      // Replication port
      int replicationPort = server.getReplicationServerPort();
      if (replicationPort >= 0)
      {
        tableBuilder.appendCell(Message.raw(String.valueOf(replicationPort)));
      }
      else
      {
        tableBuilder.appendCell(Message.raw(""));
      }
      // Secure
      tableBuilder.appendCell(
        Message.raw(
          Boolean.toString(server.isReplicationSecure())));
    }

    PrintStream out = getOutputStream();
    TablePrinter printer = null;

    if (scriptFriendly)
    {
      printProgress(
          INFO_REPLICATION_STATUS_INDEPENDENT_REPLICATION_SERVERS.get());
      printlnProgress();
      printer = new TabSeparatedTablePrinter(out);
    }
    else
    {
      Message msg =
        INFO_REPLICATION_STATUS_INDEPENDENT_REPLICATION_SERVERS.get();
      printProgressMessageNoWrap(msg);
      printlnProgress();
      int length = msg.length();
      StringBuilder buf = new StringBuilder();
      for (int i=0; i<length; i++)
      {
        buf.append("=");
      }
      printProgressMessageNoWrap(Message.raw(buf.toString()));
      printlnProgress();

      printer = new TextTablePrinter(getOutputStream());
      ((TextTablePrinter)printer).setColumnSeparator(
        ToolConstants.LIST_TABLE_SEPARATOR);
    }
    tableBuilder.print(printer);
  }

  /**
   * Retrieves all the replication servers for a given baseDN.  The
   * ServerDescriptor is used to identify the server where the suffix is
   * defined and it cannot be null.  The TopologyCache is used to retrieve
   * replication servers defined in other replicas but not in the one we
   * get in the ServerDescriptor.
   * @param baseDN the base DN.
   * @param cache the TopologyCache (might be null).
   * @param server the ServerDescriptor.
   * @return a Set containing the replication servers currently being used
   * to replicate the baseDN defined in the server described by the
   * ServerDescriptor.
   */
  private LinkedHashSet<String> getReplicationServers(String baseDN,
      TopologyCache cache, ServerDescriptor server)
  {
    LinkedHashSet<String> servers = new LinkedHashSet<String>();
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        servers.addAll(replica.getReplicationServers());
        break;
      }
    }
    if (cache != null)
    {
      Set<SuffixDescriptor> suffixes = cache.getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        if (Utils.areDnsEqual(suffix.getDN(), baseDN))
        {
          Set<String> s = suffix.getReplicationServers();
          // Test that at least we share one of the replication servers.
          // If we do: we are dealing with the same replication topology
          // (we must consider the case of disjoint replication topologies
          // replicating the same base DN).
          HashSet<String> copy = new HashSet<String>(s);
          copy.retainAll(servers);
          if (!copy.isEmpty())
          {
            servers.addAll(s);
            break;
          }
          else
          {
            // Check if this server is acting as replication server with
            // no domain.
            if (server.isReplicationServer())
            {
              boolean found = false;
              String repServer = server.getReplicationServerHostPort();
              for (String rS : s)
              {
                if (rS.equalsIgnoreCase(repServer))
                {
                  servers.addAll(s);
                  found = true;
                  break;
                }
              }
              if (found)
              {
                break;
              }
            }
          }
        }
      }
    }
    return servers;
  }

  /**
   * Retrieves the suffix in the TopologyCache for a given baseDN.  The
   * ServerDescriptor is used to identify the server where the suffix is
   * defined.
   * @param baseDN the base DN.
   * @param cache the TopologyCache.
   * @param server the ServerDescriptor.
   * @return the suffix in the TopologyCache for a given baseDN.
   */
  private SuffixDescriptor getSuffix(String baseDN, TopologyCache cache,
      ServerDescriptor server)
  {
    SuffixDescriptor returnValue = null;
    String replicationServer = null;
    if (server.isReplicationServer())
    {
      replicationServer = server.getReplicationServerHostPort();
    }
    Set<String> servers = new LinkedHashSet<String>();
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        servers.addAll(replica.getReplicationServers());
        break;
      }
    }

    Set<SuffixDescriptor> suffixes = cache.getSuffixes();
    for (SuffixDescriptor suffix : suffixes)
    {
      if (Utils.areDnsEqual(suffix.getDN(), baseDN))
      {
        Set<String> s = suffix.getReplicationServers();
        // Test that at least we share one of the replication servers.
        // If we do: we are dealing with the same replication topology
        // (we must consider the case of disjoint replication topologies
        // replicating the same base DN).
        HashSet<String> copy = new HashSet<String>(s);
        copy.retainAll(servers);
        if (!copy.isEmpty())
        {
          returnValue = suffix;
          break;
        }
        else if (replicationServer != null)
        {
          // Check if the server is only a replication server.
          for (String repServer : s)
          {
            if (repServer.equalsIgnoreCase(replicationServer))
            {
              returnValue = suffix;
              break;
            }
          }
        }
      }
    }
    return returnValue;
  }

  /**
   * Retrieves all the replication domain IDs for a given baseDN in the
   * ServerDescriptor.
   * @param baseDN the base DN.
   * @param server the ServerDescriptor.
   * @return a Set containing the replication domain IDs for a given baseDN in
   * the ServerDescriptor.
   */
  private Set<Integer> getReplicationDomainIds(String baseDN,
      ServerDescriptor server)
  {
    Set<Integer> ids = new HashSet<Integer>();
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if ((replica.isReplicated()) &&
      Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        ids.add(replica.getReplicationId());
        break;
      }
    }
    return ids;
  }

  /**
   * Configures the server to which the provided InitialLdapContext is connected
   * as a replication server.  The replication server listens in the provided
   * port.
   * @param ctx the context connected to the server that we want to configure.
   * @param replicationPort the replication port of the replication server.
   * @param useSecureReplication whether to have encrypted communication with
   * the replication port or not.
   * @param replicationServers the list of replication servers to which the
   * replication server will communicate with.
   * @param usedReplicationServerIds the set of replication server IDs that
   * are already in use.  The set will be updated with the replication ID
   * that will be used by the newly configured replication server.
   * @throws OpenDsException if there is an error updating the configuration.
   */
  private void configureAsReplicationServer(InitialLdapContext ctx,
      int replicationPort, boolean useSecureReplication,
      LinkedHashSet<String> replicationServers,
      Set<Integer> usedReplicationServerIds) throws OpenDsException
  {
    printProgress(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_CONFIGURING_REPLICATION_SERVER.get(
            ConnectionUtils.getHostPort(ctx))));

    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();

    /*
     * Configure Synchronization plugin.
     */
    ReplicationSynchronizationProviderCfgClient sync = null;
    try
    {
      sync = (ReplicationSynchronizationProviderCfgClient)
      root.getSynchronizationProvider("Multimaster Synchronization");
    }
    catch (ManagedObjectNotFoundException monfe)
    {
      LOG.log(Level.INFO, "Synchronization server does not exist in "+
          ConnectionUtils.getHostPort(ctx));
    }
    if (sync == null)
    {
      ReplicationSynchronizationProviderCfgDefn provider =
        ReplicationSynchronizationProviderCfgDefn.getInstance();
      sync = root.createSynchronizationProvider(provider,
          "Multimaster Synchronization",
          new ArrayList<DefaultBehaviorException>());
      sync.setJavaClass(
          org.opends.server.replication.plugin.MultimasterReplication.class.
          getName());
      sync.setEnabled(Boolean.TRUE);
    }
    else
    {
      if (!sync.isEnabled())
      {
        sync.setEnabled(Boolean.TRUE);
      }
    }
    sync.commit();

    /*
     * Configure the replication server.
     */
    ReplicationServerCfgClient replicationServer = null;

    boolean mustCommit = false;

    if (!sync.hasReplicationServer())
    {
      CryptoManagerCfgClient crypto = root.getCryptoManager();
      if (useSecureReplication != crypto.isSSLEncryption())
      {
        crypto.setSSLEncryption(useSecureReplication);
        crypto.commit();
      }
      int id = InstallerHelper.getReplicationId(usedReplicationServerIds);
      usedReplicationServerIds.add(id);
      replicationServer = sync.createReplicationServer(
          ReplicationServerCfgDefn.getInstance(),
          new ArrayList<DefaultBehaviorException>());
      replicationServer.setReplicationServerId(id);
      replicationServer.setReplicationPort(replicationPort);
      replicationServer.setReplicationServer(replicationServers);
      mustCommit = true;
    }
    else
    {
      replicationServer = sync.getReplicationServer();
      usedReplicationServerIds.add(
          replicationServer.getReplicationServerId());
      Set<String> servers = replicationServer.getReplicationServer();
      if (servers == null)
      {
        replicationServer.setReplicationServer(replicationServers);
        mustCommit = true;
      }
      else if (!areReplicationServersEqual(servers, replicationServers))
      {
        replicationServer.setReplicationServer(
            mergeReplicationServers(replicationServers, servers));
        mustCommit = true;
      }
    }
    if (mustCommit)
    {
      replicationServer.commit();
    }

    printProgress(formatter.getFormattedDone());
    printlnProgress();
  }

  /**
   * Updates the configuration of the replication server with the list of
   * replication servers provided.
   * @param ctx the context connected to the server that we want to update.
   * @param replicationServers the list of replication servers to which the
   * replication server will communicate with.
   * @throws OpenDsException if there is an error updating the configuration.
   */
  private void updateReplicationServer(InitialLdapContext ctx,
      LinkedHashSet<String> replicationServers) throws OpenDsException
  {
    printProgress(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_UPDATING_REPLICATION_SERVER.get(
            ConnectionUtils.getHostPort(ctx))));

    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();

    ReplicationSynchronizationProviderCfgClient sync =
      (ReplicationSynchronizationProviderCfgClient)
    root.getSynchronizationProvider("Multimaster Synchronization");
    boolean mustCommit = false;
    ReplicationServerCfgClient replicationServer = sync.getReplicationServer();
    Set<String> servers = replicationServer.getReplicationServer();
    if (servers == null)
    {
      replicationServer.setReplicationServer(replicationServers);
      mustCommit = true;
    }
    else if (!areReplicationServersEqual(servers, replicationServers))
    {
      replicationServers.addAll(servers);
      replicationServer.setReplicationServer(
          mergeReplicationServers(replicationServers, servers));
      mustCommit = true;
    }
    if (mustCommit)
    {
      replicationServer.commit();
    }

    printProgress(formatter.getFormattedDone());
    printlnProgress();
  }

  /**
   * Returns a Set containing all the replication server ids found in the
   * servers of a given TopologyCache object.
   * @param cache the TopologyCache object to use.
   * @return a Set containing all the replication server ids found in a given
   * TopologyCache object.
   */
  private Set<Integer> getReplicationServerIds(TopologyCache cache)
  {
    Set<Integer> ids = new HashSet<Integer>();
    for (ServerDescriptor server : cache.getServers())
    {
      if (server.isReplicationServer())
      {
        ids.add(server.getReplicationServerId());
      }
    }
    return ids;
  }

  /**
   * Configures a replication domain for a given base DN in the server to which
   * the provided InitialLdapContext is connected.
   * @param ctx the context connected to the server that we want to configure.
   * @param baseDN the base DN of the replication domain to configure.
   * @param replicationServers the list of replication servers to which the
   * replication domain will communicate with.
   * @param usedReplicationDomainIds the set of replication domain IDs that
   * are already in use.  The set will be updated with the replication ID
   * that will be used by the newly configured replication server.
   * @throws OpenDsException if there is an error updating the configuration.
   */
  private void configureToReplicateBaseDN(InitialLdapContext ctx,
      String baseDN,
      LinkedHashSet<String> replicationServers,
      Set<Integer> usedReplicationDomainIds) throws OpenDsException
  {
    boolean userSpecifiedAdminBaseDN = false;
    LinkedList<String> l = argParser.getBaseDNs();
    if (l != null)
    {
      for (String dn : l)
      {
        if (Utils.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()))
        {
          userSpecifiedAdminBaseDN = true;
          break;
        }
      }
    }
    if (!userSpecifiedAdminBaseDN && Utils.areDnsEqual(baseDN,
        ADSContext.getAdministrationSuffixDN()))
    {
      printProgress(formatter.getFormattedWithPoints(
          INFO_REPLICATION_ENABLE_CONFIGURING_ADS.get(
              ConnectionUtils.getHostPort(ctx))));
    }
    else
    {
      printProgress(formatter.getFormattedWithPoints(
          INFO_REPLICATION_ENABLE_CONFIGURING_BASEDN.get(baseDN,
              ConnectionUtils.getHostPort(ctx))));
    }
    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(ctx));
    RootCfgClient root = mCtx.getRootConfiguration();

    ReplicationSynchronizationProviderCfgClient sync =
      (ReplicationSynchronizationProviderCfgClient)
      root.getSynchronizationProvider("Multimaster Synchronization");

    String[] domainNames = sync.listReplicationDomains();
    if (domainNames == null)
    {
      domainNames = new String[]{};
    }
    ReplicationDomainCfgClient[] domains =
      new ReplicationDomainCfgClient[domainNames.length];
    for (int i=0; i<domains.length; i++)
    {
      domains[i] = sync.getReplicationDomain(domainNames[i]);
    }
    ReplicationDomainCfgClient domain = null;
    for (int i=0; i<domains.length; i++)
    {
      if (Utils.areDnsEqual(baseDN, domains[i].getBaseDN().toString()))
      {
        domain = domains[i];
        break;
      }
    }
    boolean mustCommit = false;
    if (domain == null)
    {
      int domainId = InstallerHelper.getReplicationId(usedReplicationDomainIds);
      usedReplicationDomainIds.add(domainId);
      String domainName =
          InstallerHelper.getDomainName(domainNames, domainId, baseDN);
      domain = sync.createReplicationDomain(
          ReplicationDomainCfgDefn.getInstance(), domainName,
          new ArrayList<DefaultBehaviorException>());
      domain.setServerId(domainId);
      domain.setBaseDN(DN.decode(baseDN));
      domain.setReplicationServer(replicationServers);
      mustCommit = true;
    }
    else
    {
      Set<String> servers = domain.getReplicationServer();
      if (servers == null)
      {
        domain.setReplicationServer(null);
        mustCommit = true;
      }
      else if (!areReplicationServersEqual(servers, replicationServers))
      {
        domain.setReplicationServer(mergeReplicationServers(replicationServers,
            servers));
        mustCommit = true;
      }
    }

    if (mustCommit)
    {
      domain.commit();
    }

    printProgress(formatter.getFormattedDone());
    printlnProgress();
  }

  /**
   * Configures the baseDN to replicate in all the Replicas found in a Topology
   * Cache that are replicated with the Replica of the same base DN in the
   * provided ServerDescriptor object.
   * @param baseDN the base DN to replicate.
   * @param repServers the replication servers to be defined in the domain.
   * @param usedIds the replication domain Ids already used.  This Set is
   * updated with the new domains that are used.
   * @param cache the TopologyCache used to retrieve the different defined
   * replicas.
   * @param server the ServerDescriptor that is used to identify the
   * replication topology that we are interested at (we only update the replicas
   * that are already replicated with this server).
   * @param alreadyConfiguredServers the list of already configured servers.  If
   * a server is in this list no updates are performed to the domain.
   * @param alreadyConfiguredReplicationServers the list of already configured
   * servers.  If a server is in this list no updates are performed to the
   * replication server.
   * @throws ReplicationCliException if something goes wrong.
   */
  private void configureToReplicateBaseDN(String baseDN,
      LinkedHashSet<String> repServers, Set<Integer> usedIds,
      TopologyCache cache, ServerDescriptor server,
      Set<String> alreadyConfiguredServers, LinkedHashSet<String> allRepServers,
      Set<String> alreadyConfiguredReplicationServers)
  throws ReplicationCliException
  {
    LOG.log(Level.INFO, "Configuring base DN '"+baseDN+
        "' the replication servers are "+repServers);
    Set<ServerDescriptor> serversToConfigureDomain =
      new HashSet<ServerDescriptor>();
    Set<ServerDescriptor> replicationServersToConfigure =
      new HashSet<ServerDescriptor>();
    SuffixDescriptor suffix = getSuffix(baseDN, cache, server);
    if (suffix != null)
    {
      for (ReplicaDescriptor replica: suffix.getReplicas())
      {
        ServerDescriptor s = replica.getServer();
        if (!alreadyConfiguredServers.contains(s.getId()))
        {
          serversToConfigureDomain.add(s);
        }
      }
    }
    // Now check the replication servers.
    for (ServerDescriptor s : cache.getServers())
    {
      if (s.isReplicationServer() &&
          !alreadyConfiguredReplicationServers.contains(s.getId()))
      {
        // Check if it is part of the replication topology
        boolean isInTopology = false;
        String repServerID = s.getReplicationServerHostPort();
        for (String rID : repServers)
        {
          if (repServerID.equalsIgnoreCase(rID))
          {
            isInTopology = true;
            break;
          }
        }
        if (isInTopology)
        {
          replicationServersToConfigure.add(s);
        }
      }
    }

    Set<ServerDescriptor> allServers = new HashSet<ServerDescriptor>();
    allServers.addAll(serversToConfigureDomain);
    allServers.addAll(replicationServersToConfigure);

    for (ServerDescriptor s : allServers)
    {
      LOG.log(Level.INFO,"Configuring server "+server.getHostPort(true));
      InitialLdapContext ctx = null;
      try
      {
        ctx = getDirContextForServer(cache, s);
        if (serversToConfigureDomain.contains(s))
        {
          configureToReplicateBaseDN(ctx, baseDN, repServers, usedIds);
        }
        if (replicationServersToConfigure.contains(s))
        {
          updateReplicationServer(ctx, allRepServers);
        }
      }
      catch (NamingException ne)
      {
        String hostPort = getHostPort(s, cache.getPreferredConnections());
        Message msg = getMessageForException(ne, hostPort);
        throw new ReplicationCliException(msg, ERROR_CONNECTING, ne);
      }
      catch (OpenDsException ode)
      {
        String hostPort = getHostPort(s, cache.getPreferredConnections());
        Message msg = getMessageForEnableException(ode, hostPort, baseDN);
        throw new ReplicationCliException(msg,
            ERROR_ENABLING_REPLICATION_ON_BASEDN, ode);
      }
      finally
      {
        if (ctx != null)
        {
          try
          {
            ctx.close();
          }
          catch (Throwable t)
          {
          }
        }
      }
      alreadyConfiguredServers.add(s.getId());
      alreadyConfiguredReplicationServers.add(s.getId());
    }
  }

  /**
   * Returns the Map of properties to be used to update the ADS.
   * This map uses the data provided by the user.
   * @return the Map of properties to be used to update the ADS.
   * This map uses the data provided by the user
   */
  private Map<ADSContext.AdministratorProperty, Object>
  getAdministratorProperties(ReplicationUserData uData)
  {
    Map<ADSContext.AdministratorProperty, Object> adminProperties =
      new HashMap<ADSContext.AdministratorProperty, Object>();
    adminProperties.put(ADSContext.AdministratorProperty.UID,
        uData.getAdminUid());
    adminProperties.put(ADSContext.AdministratorProperty.PASSWORD,
        uData.getAdminPwd());
    adminProperties.put(ADSContext.AdministratorProperty.DESCRIPTION,
        INFO_GLOBAL_ADMINISTRATOR_DESCRIPTION.get().toString());
    return adminProperties;
  }

  private void initializeSuffix(String baseDN, InitialLdapContext ctxSource,
      InitialLdapContext ctxDestination, boolean displayProgress)
  throws ReplicationCliException
  {
    int replicationId = -1;
    try
    {
      TopologyCacheFilter filter = new TopologyCacheFilter();
      filter.setSearchMonitoringInformation(false);
      filter.addBaseDNToSearch(baseDN);
      ServerDescriptor source = ServerDescriptor.createStandalone(ctxSource,
          filter);
      for (ReplicaDescriptor replica : source.getReplicas())
      {
        if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
        {
          replicationId = replica.getReplicationId();
          break;
        }
      }
    }
    catch (NamingException ne)
    {
      String hostPort = ConnectionUtils.getHostPort(ctxSource);
      Message msg = getMessageForException(ne, hostPort);
      throw new ReplicationCliException(msg, ERROR_READING_CONFIGURATION, ne);
    }

    if (replicationId == -1)
    {
      throw new ReplicationCliException(
          ERR_INITIALIZING_REPLICATIONID_NOT_FOUND.get(
              ConnectionUtils.getHostPort(ctxSource), baseDN),
          REPLICATIONID_NOT_FOUND, null);
    }

    OfflineInstaller installer = new OfflineInstaller();
    installer.setProgressMessageFormatter(formatter);
    installer.addProgressUpdateListener(new ProgressUpdateListener()
    {
      @Override
      public void progressUpdate(ProgressUpdateEvent ev)
      {
        Message newLogDetails = ev.getNewLogs();
        if ((newLogDetails != null) &&
            !newLogDetails.toString().trim().equals(""))
        {
          printProgress(newLogDetails);
          printlnProgress();
        }
      }
    });
    int nTries = 5;
    boolean initDone = false;
    while (!initDone)
    {
      try
      {
        installer.initializeSuffix(ctxDestination, replicationId, baseDN,
            displayProgress, ConnectionUtils.getHostPort(ctxSource));
        initDone = true;
      }
      catch (PeerNotFoundException pnfe)
      {
        LOG.log(Level.INFO, "Peer could not be found");
        if (nTries == 1)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_INITIALIZING_TRIES_COMPLETED.get(
                  pnfe.getMessageObject().toString()),
              INITIALIZING_TRIES_COMPLETED, pnfe);
        }
        try
        {
          Thread.sleep((5 - nTries) * 3000);
        }
        catch (Throwable t)
        {
        }
      }
      catch (ApplicationException ae)
      {
        throw new ReplicationCliException(ae.getMessageObject(),
            ERROR_INITIALIZING_BASEDN_GENERIC, ae);
      }
      nTries--;
    }
  }

  /**
   * Initializes all the replicas in the topology with the contents of a
   * given replica.
   * @param ctx the connection to the server where the source replica of the
   * initialization is.
   * @param baseDN the dn of the suffix.
   * @param displayProgress whether we want to display progress or not.
   * @throws ReplicationCliException if an unexpected error occurs.
   */
  public void initializeAllSuffix(String baseDN, InitialLdapContext ctx,
  boolean displayProgress) throws ReplicationCliException
  {
    if (argParser == null)
    {
      try
      {
        createArgumenParser();
      }
      catch (ArgumentException ae)
      {
        throw new RuntimeException("Error creating argument parser: "+ae, ae);
      }
    }
    int nTries = 5;
    boolean initDone = false;
    while (!initDone)
    {
      try
      {
        initializeAllSuffixTry(baseDN, ctx, displayProgress);
        postPreExternalInitialization(baseDN, ctx, displayProgress, false);
        initDone = true;
      }
      catch (PeerNotFoundException pnfe)
      {
        LOG.log(Level.INFO, "Peer could not be found");
        if (nTries == 1)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_INITIALIZING_TRIES_COMPLETED.get(
                  pnfe.getMessageObject().toString()),
              INITIALIZING_TRIES_COMPLETED, pnfe);
        }
        try
        {
          Thread.sleep((5 - nTries) * 3000);
        }
        catch (Throwable t)
        {
        }
      }
      catch (ApplicationException ae)
      {
        throw new ReplicationCliException(ae.getMessageObject(),
            ERROR_INITIALIZING_BASEDN_GENERIC, ae);
      }
      nTries--;
    }
  }

  /**
   * Launches the pre external initialization operation using the provided
   * connection on a given base DN.
   * @param baseDN the base DN that we want to reset.
   * @param ctx the connection to the server.
   * @param displayProgress whether to display operation progress or not.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void preExternalInitialization(String baseDN, InitialLdapContext ctx,
      boolean displayProgress) throws ReplicationCliException
  {
    postPreExternalInitialization(baseDN, ctx, displayProgress,
        true);
  }

  /**
   * Launches the post external initialization operation using the provided
   * connection on a given base DN required for replication to work.
   * @param baseDN the base DN that we want to reset.
   * @param ctx the connection to the server.
   * @param displayProgress whether to display operation progress or not.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void postExternalInitialization(String baseDN, InitialLdapContext ctx,
      boolean displayProgress) throws ReplicationCliException
  {
    postPreExternalInitialization(baseDN, ctx, displayProgress, false);
  }

  /**
   * Launches the pre or post external initialization operation using the
   * provided connection on a given base DN.
   * @param baseDN the base DN that we want to reset.
   * @param ctx the connection to the server.
   * @param displayProgress whether to display operation progress or not.
   * @param isPre whether this is the pre operation or the post operation.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void postPreExternalInitialization(String baseDN,
      InitialLdapContext ctx, boolean displayProgress,
      boolean isPre) throws ReplicationCliException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-reset-generation-id");
    attrs.put(oc);
    attrs.put("ds-task-class-name",
        "org.opends.server.tasks.SetGenerationIdTask");
    if (isPre)
    {
      attrs.put("ds-task-reset-generation-id-new-value", "-1");
    }
    attrs.put("ds-task-reset-generation-id-domain-base-dn", baseDN);
    while (!taskCreated)
    {
      String id = "dsreplication-reset-generation-id-"+i;
      dn = "ds-task-id="+id+",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        Message msg = isPre ?
        ERR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION.get():
          ERR_LAUNCHING_POST_EXTERNAL_INITIALIZATION.get();
        ReplicationCliReturnCode code = isPre?
            ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION:
              ERROR_LAUNCHING_POST_EXTERNAL_INITIALIZATION;
        throw new ReplicationCliException(
            getThrowableMsg(msg, ne), code, ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-log-message",
            "ds-task-state"
        });
    String lastLogMsg = null;
    while (!isOver)
    {
      try
      {
        Thread.sleep(500);
      }
      catch (Throwable t)
      {
      }
      try
      {
        NamingEnumeration<SearchResult> res =
          ctx.search(dn, filter, searchControls);
        SearchResult sr = null;
        try
        {
          while (res.hasMore())
          {
            sr = res.next();
          }
        }
        finally
        {
          res.close();
        }
        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          String server = ConnectionUtils.getHostPort(ctx);
          if (lastLogMsg == null)
          {
            errorMsg = isPre ?
                INFO_ERROR_DURING_PRE_EXTERNAL_INITIALIZATION_NO_LOG.get(
                state, server) :
                  INFO_ERROR_DURING_POST_EXTERNAL_INITIALIZATION_NO_LOG.get(
                      state, server);
          }
          else
          {
            errorMsg = isPre ?
                INFO_ERROR_DURING_PRE_EXTERNAL_INITIALIZATION_LOG.get(
                lastLogMsg, state, server) :
                  INFO_ERROR_DURING_POST_EXTERNAL_INITIALIZATION_LOG.get(
                      lastLogMsg, state, server);
          }

          if (helper.isCompletedWithErrors(state))
          {
            LOG.log(Level.WARNING, "Completed with error: "+errorMsg);
            println(errorMsg);
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            LOG.log(Level.WARNING, "Error: "+errorMsg);
            ReplicationCliReturnCode code = isPre?
                ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION:
                  ERROR_LAUNCHING_POST_EXTERNAL_INITIALIZATION;
            throw new ReplicationCliException(errorMsg, code, null);
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
      }
      catch (NamingException ne)
      {
        Message msg = isPre ?
            ERR_POOLING_PRE_EXTERNAL_INITIALIZATION.get():
              ERR_POOLING_POST_EXTERNAL_INITIALIZATION.get();
            throw new ReplicationCliException(
                getThrowableMsg(msg, ne), ERROR_CONNECTING, ne);
      }
    }
  }

  /**
   * Initializes all the replicas in the topology with the contents of a
   * given replica.  This method will try to create the task only once.
   * @param ctx the connection to the server where the source replica of the
   * initialization is.
   * @param baseDN the dn of the suffix.
   * @param displayProgress whether we want to display progress or not.
   * @throws ApplicationException if an unexpected error occurs.
   * @throws PeerNotFoundException if the replication mechanism cannot find
   * a peer.
   */
  public void initializeAllSuffixTry(String baseDN, InitialLdapContext ctx,
      boolean displayProgress)
  throws ApplicationException, PeerNotFoundException
  {
    boolean taskCreated = false;
    int i = 1;
    boolean isOver = false;
    String dn = null;
    String serverDisplay = ConnectionUtils.getHostPort(ctx);
    BasicAttributes attrs = new BasicAttributes();
    Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-task");
    oc.add("ds-task-initialize-remote-replica");
    attrs.put(oc);
    attrs.put("ds-task-class-name",
        "org.opends.server.tasks.InitializeTargetTask");
    attrs.put("ds-task-initialize-domain-dn", baseDN);
    attrs.put("ds-task-initialize-replica-server-id", "all");
    while (!taskCreated)
    {
      String id = "dsreplication-initialize"+i;
      dn = "ds-task-id="+id+",cn=Scheduled Tasks,cn=Tasks";
      attrs.put("ds-task-id", id);
      try
      {
        DirContext dirCtx = ctx.createSubcontext(dn, attrs);
        taskCreated = true;
        LOG.log(Level.INFO, "created task entry: "+attrs);
        dirCtx.close();
      }
      catch (NameAlreadyBoundException x)
      {
        LOG.log(Level.WARNING, "A task with dn: "+dn+" already existed.");
      }
      catch (NamingException ne)
      {
        LOG.log(Level.SEVERE, "Error creating task "+attrs, ne);
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_LAUNCHING_INITIALIZATION.get(
                        serverDisplay), ne), ne);
      }
      i++;
    }
    // Wait until it is over
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(
        SearchControls. OBJECT_SCOPE);
    String filter = "objectclass=*";
    searchControls.setReturningAttributes(
        new String[] {
            "ds-task-unprocessed-entry-count",
            "ds-task-processed-entry-count",
            "ds-task-log-message",
            "ds-task-state"
        });
    Message lastDisplayedMsg = null;
    String lastLogMsg = null;
    long lastTimeMsgDisplayed = -1;
    long lastTimeMsgLogged = -1;
    long totalEntries = 0;
    while (!isOver)
    {
      try
      {
        Thread.sleep(500);
      }
      catch (Throwable t)
      {
      }
      try
      {
        NamingEnumeration<SearchResult> res =
          ctx.search(dn, filter, searchControls);
        SearchResult sr = null;
        try
        {
          while (res.hasMore())
          {
            sr = res.next();
          }
        }
        finally
        {
          res.close();
        }

        // Get the number of entries that have been handled and
        // a percentage...
        Message msg;
        String sProcessed = getFirstValue(sr,
        "ds-task-processed-entry-count");
        String sUnprocessed = getFirstValue(sr,
        "ds-task-unprocessed-entry-count");
        long processed = -1;
        long unprocessed = -1;
        if (sProcessed != null)
        {
          processed = Integer.parseInt(sProcessed);
        }
        if (sUnprocessed != null)
        {
          unprocessed = Integer.parseInt(sUnprocessed);
        }
        totalEntries = Math.max(totalEntries, processed+unprocessed);

        if ((processed != -1) && (unprocessed != -1))
        {
          if (processed + unprocessed > 0)
          {
            long perc = (100 * processed) / (processed + unprocessed);
            msg = INFO_INITIALIZE_PROGRESS_WITH_PERCENTAGE.get(sProcessed,
                String.valueOf(perc));
          }
          else
          {
            //msg = INFO_NO_ENTRIES_TO_INITIALIZE.get();
            msg = null;
          }
        }
        else if (processed != -1)
        {
          msg = INFO_INITIALIZE_PROGRESS_WITH_PROCESSED.get(sProcessed);
        }
        else if (unprocessed != -1)
        {
          msg = INFO_INITIALIZE_PROGRESS_WITH_UNPROCESSED.get(sUnprocessed);
        }
        else
        {
          msg = lastDisplayedMsg;
        }

        if (msg != null)
        {
          long currentTime = System.currentTimeMillis();
          /* Refresh period: to avoid having too many lines in the log */
          long minRefreshPeriod;
          if (totalEntries < 100)
          {
            minRefreshPeriod = 0;
          }
          else if (totalEntries < 1000)
          {
            minRefreshPeriod = 1000;
          }
          else if (totalEntries < 10000)
          {
            minRefreshPeriod = 5000;
          }
          else
          {
            minRefreshPeriod = 10000;
          }
          if (((currentTime - minRefreshPeriod) > lastTimeMsgLogged))
          {
            lastTimeMsgLogged = currentTime;
            LOG.log(Level.INFO, "Progress msg: "+msg);
          }
          if (displayProgress)
          {
            if (((currentTime - minRefreshPeriod) > lastTimeMsgDisplayed) &&
                !msg.equals(lastDisplayedMsg))
            {
              printProgress(msg);
              lastDisplayedMsg = msg;
              printlnProgress();
              lastTimeMsgDisplayed = currentTime;
            }
          }
        }

        String logMsg = getFirstValue(sr, "ds-task-log-message");
        if (logMsg != null)
        {
          if (!logMsg.equals(lastLogMsg))
          {
            LOG.log(Level.INFO, logMsg);
            lastLogMsg = logMsg;
          }
        }
        InstallerHelper helper = new InstallerHelper();
        String state = getFirstValue(sr, "ds-task-state");

        if (helper.isDone(state) || helper.isStoppedByError(state))
        {
          isOver = true;
          Message errorMsg;
          LOG.log(Level.INFO, "Last task entry: "+sr);
          if (displayProgress && (msg != null) && !msg.equals(lastDisplayedMsg))
          {
            printProgress(msg);
            lastDisplayedMsg = msg;
            printlnProgress();
          }
          if (lastLogMsg == null)
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(
                    serverDisplay, state, serverDisplay);
          }
          else
          {
            errorMsg = INFO_ERROR_DURING_INITIALIZATION_LOG.get(
                serverDisplay, lastLogMsg, state, serverDisplay);
          }

          if (helper.isCompletedWithErrors(state))
          {
            LOG.log(Level.WARNING, "Processed errorMsg: "+errorMsg);
            if (displayProgress)
            {
              println(errorMsg);
            }
          }
          else if (!helper.isSuccessful(state) ||
              helper.isStoppedByError(state))
          {
            LOG.log(Level.WARNING, "Processed errorMsg: "+errorMsg);
            ApplicationException ae = new ApplicationException(
                ReturnCode.APPLICATION_ERROR, errorMsg,
                null);
            if ((lastLogMsg == null) ||
                helper.isPeersNotFoundError(lastLogMsg))
            {
              LOG.log(Level.WARNING, "Throwing peer not found error.  "+
                  "Last Log Msg: "+lastLogMsg);
              // Assume that this is a peer not found error.
              throw new PeerNotFoundException(errorMsg);
            }
            else
            {
              LOG.log(Level.SEVERE, "Throwing ApplicationException.");
              throw ae;
            }
          }
          else
          {
            if (displayProgress)
            {
              printProgress(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get());
              printlnProgress();
            }
            LOG.log(Level.INFO, "Processed msg: "+errorMsg);
            LOG.log(Level.INFO, "Initialization completed successfully.");
          }
        }
      }
      catch (NameNotFoundException x)
      {
        isOver = true;
        LOG.log(Level.INFO, "Initialization entry not found.");
        if (displayProgress)
        {
          printProgress(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get());
          printlnProgress();
        }
      }
      catch (NamingException ne)
      {
        throw new ApplicationException(
            ReturnCode.APPLICATION_ERROR,
                getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION.get(
                    serverDisplay), ne), ne);
      }
    }
  }

  /**
   * Removes the references to a replication server in the base DNs of a
   * given server.
   * @param server the server that we want to update.
   * @param replicationServer the replication server whose references we want
   * to remove.
   * @param bindDn the bindDn that must be used to log to the server.
   * @param pwd the password that must be used to log to the server.
   * @param baseDNs the list of base DNs where we want to remove the references
   * to the provided replication server.
   * @param removeFromReplicationServers if references must be removed from
   * the replication servers.
   * @param preferredURLs the preferred LDAP URLs to be used to connect to the
   * server.
   * @throws ReplicationCliException if there is an error updating the
   * configuration.
   */
  private void removeReferencesInServer(ServerDescriptor server,
      String replicationServer, String bindDn, String pwd,
      Collection<String> baseDNs, boolean updateReplicationServers,
      LinkedHashSet<PreferredConnection> cnx)
  throws ReplicationCliException
  {
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    filter.setSearchBaseDNInformation(false);
    ServerLoader loader = new ServerLoader(server.getAdsProperties(), bindDn,
        pwd, getTrustManager(), getConnectTimeout(), cnx, filter);
    InitialLdapContext ctx = null;
    String lastBaseDN = null;
    String hostPort = null;

    try
    {
      ctx = loader.createContext();
      hostPort = ConnectionUtils.getHostPort(ctx);
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      ReplicationSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = (ReplicationSynchronizationProviderCfgClient)
        root.getSynchronizationProvider("Multimaster Synchronization");
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        LOG.log(Level.INFO, "No synchronization found on "+ hostPort +".",
            monfe);
      }
      if (sync != null)
      {
        String[] domainNames = sync.listReplicationDomains();
        if (domainNames != null)
        {
          for (int i=0; i<domainNames.length; i++)
          {
            ReplicationDomainCfgClient domain =
              sync.getReplicationDomain(domainNames[i]);
            for (String baseDN : baseDNs)
            {
              lastBaseDN = baseDN;
              if (Utils.areDnsEqual(domain.getBaseDN().toString(),
                  baseDN))
              {
                printProgress(formatter.getFormattedWithPoints(
                    INFO_REPLICATION_REMOVING_REFERENCES_ON_REMOTE.get(baseDN,
                        hostPort)));
                Set<String> replServers = domain.getReplicationServer();
                if (replServers != null)
                {
                  String replServer = null;
                  for (String o : replServers)
                  {
                    if (replicationServer.equalsIgnoreCase(o))
                    {
                      replServer = o;
                      break;
                    }
                  }
                  if (replServer != null)
                  {
                    LOG.log(Level.INFO, "Updating references in domain " +
                        domain.getBaseDN()+" on " + hostPort + ".");
                    replServers.remove(replServer);
                    if (replServers.size() > 0)
                    {
                      domain.setReplicationServer(replServers);
                      domain.commit();
                    }
                    else
                    {
                      sync.removeReplicationDomain(domainNames[i]);
                      sync.commit();
                    }
                  }
                }
                printProgress(formatter.getFormattedDone());
                printlnProgress();
              }
            }
          }
        }
        if (updateReplicationServers && sync.hasReplicationServer())
        {
          ReplicationServerCfgClient rServerObj = sync.getReplicationServer();
          Set<String> replServers = rServerObj.getReplicationServer();
          if (replServers != null)
          {
            String replServer = null;
            for (String o : replServers)
            {
              if (replicationServer.equalsIgnoreCase(o))
              {
                replServer = o;
                break;
              }
            }
            if (replServer != null)
            {
              replServers.remove(replServer);
              if (replServers.size() > 0)
              {
                rServerObj.setReplicationServer(replServers);
                rServerObj.commit();
              }
              else
              {
                sync.removeReplicationServer();
                sync.commit();
              }
            }
          }
        }
      }
    }
    catch (NamingException ne)
    {
      hostPort = getHostPort(server, cnx);
      Message msg = getMessageForException(ne, hostPort);
      throw new ReplicationCliException(msg, ERROR_CONNECTING, ne);
    }
    catch (OpenDsException ode)
    {
      if (lastBaseDN != null)
      {
        Message msg = getMessageForDisableException(ode, hostPort, lastBaseDN);
        throw new ReplicationCliException(msg,
          ERROR_DISABLING_REPLICATION_REMOVE_REFERENCE_ON_BASEDN, ode);
      }
      else
      {
        Message msg = ERR_REPLICATION_ERROR_READING_CONFIGURATION.get(hostPort,
            ode.getMessage());
        throw new ReplicationCliException(msg, ERROR_CONNECTING, ode);
      }
    }
    finally
    {
      if (ctx != null)
      {
        try
        {
          ctx.close();
        }
        catch (Throwable t)
        {
        }
      }
    }
  }

  /**
   * Deletes a replication domain in a server for a given base DN (disable
   * replication of the base DN).
   * @param ctx the connection to the server.
   * @param baseDN the base DN of the replication domain that we want to
   * delete.
   * @throws ReplicationCliException if there is an error updating the
   * configuration of the server.
   */
  private void deleteReplicationDomain(InitialLdapContext ctx,
      String baseDN) throws ReplicationCliException
  {
    String hostPort = ConnectionUtils.getHostPort(ctx);
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      ReplicationSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = (ReplicationSynchronizationProviderCfgClient)
        root.getSynchronizationProvider("Multimaster Synchronization");
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        LOG.log(Level.INFO, "No synchronization found on "+ hostPort +".",
            monfe);
      }
      if (sync != null)
      {
        String[] domainNames = sync.listReplicationDomains();
        if (domainNames != null)
        {
          for (int i=0; i<domainNames.length; i++)
          {
            ReplicationDomainCfgClient domain =
              sync.getReplicationDomain(domainNames[i]);
            if (Utils.areDnsEqual(domain.getBaseDN().toString(), baseDN))
            {
              printProgress(formatter.getFormattedWithPoints(
                  INFO_REPLICATION_DISABLING_BASEDN.get(baseDN,
                      hostPort)));
              sync.removeReplicationDomain(domainNames[i]);
              sync.commit();

              printProgress(formatter.getFormattedDone());
              printlnProgress();
            }
          }
        }
      }
    }
    catch (OpenDsException ode)
    {
      Message msg = getMessageForDisableException(ode, hostPort, baseDN);
        throw new ReplicationCliException(msg,
          ERROR_DISABLING_REPLICATION_REMOVE_REFERENCE_ON_BASEDN, ode);
    }
  }

  /**
   * Disables the replication server for a given server.
   * @param ctx the connection to the server.
   * @throws ReplicationCliException if there is an error updating the
   * configuration of the server.
   */
  private void disableReplicationServer(InitialLdapContext ctx)
  throws ReplicationCliException
  {
    String hostPort = ConnectionUtils.getHostPort(ctx);
    try
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      ReplicationSynchronizationProviderCfgClient sync = null;
      ReplicationServerCfgClient replicationServer = null;
      try
      {
        sync = (ReplicationSynchronizationProviderCfgClient)
        root.getSynchronizationProvider("Multimaster Synchronization");
        if (sync.hasReplicationServer())
        {
          replicationServer = sync.getReplicationServer();
        }
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        LOG.log(Level.INFO, "No synchronization found on "+ hostPort +".",
            monfe);
      }
      if (replicationServer != null)
      {

        String s = String.valueOf(replicationServer.getReplicationPort());
        printProgress(formatter.getFormattedWithPoints(
            INFO_REPLICATION_DISABLING_REPLICATION_SERVER.get(s,
                hostPort)));

        sync.removeReplicationServer();
        sync.commit();
        printProgress(formatter.getFormattedDone());
        printlnProgress();
      }
    }
    catch (OpenDsException ode)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_DISABLING_REPLICATIONSERVER.get(hostPort),
          ERROR_DISABLING_REPLICATION_SERVER,
          ode);
    }
  }

  /**
   * Returns a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring the replication server.
   * @param ode the OpenDsException.
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring the replication server.
   */
  private Message getMessageForReplicationServerException(OpenDsException ode,
      String hostPort)
  {
    return ERR_REPLICATION_CONFIGURING_REPLICATIONSERVER.get(hostPort);
  }

  /**
   * Returns a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (creating
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   * @param ode the OpenDsException.
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (creating
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   */
  private Message getMessageForEnableException(OpenDsException ode,
      String hostPort, String baseDN)
  {
    return ERR_REPLICATION_CONFIGURING_BASEDN.get(baseDN, hostPort);
  }

  /**
   * Returns a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (deleting
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   * @param ode the OpenDsException.
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (deleting
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   */
  private Message getMessageForDisableException(OpenDsException ode,
      String hostPort, String baseDN)
  {
    return ERR_REPLICATION_CONFIGURING_BASEDN.get(baseDN, hostPort);
  }

  /**
   * Returns a message informing the user that the provided port cannot be used.
   * @param port the port that cannot be used.
   * @return a message informing the user that the provided port cannot be used.
   */
  private Message getCannotBindToPortError(int port)
  {
    Message message;
    if (SetupUtils.isPriviledgedPort(port))
    {
      message = ERR_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT.get(port);
    }
    else
    {
      message = ERR_INSTALLDS_CANNOT_BIND_TO_PORT.get(port);
    }
    return message;
  }

  /**
   * Convenience method used to know if one Set of replication servers equals
   * another set of replication servers.
   * @param s1 the first set of replication servers.
   * @param s2 the second set of replication servers.
   * @return <CODE>true</CODE> if the two sets represent the same replication
   * servers and <CODE>false</CODE> otherwise.
   */
  private boolean areReplicationServersEqual(Set<String> s1, Set<String> s2)
  {
    Set<String> c1 = new HashSet<String>();
    for (String s : s1)
    {
      c1.add(s.toLowerCase());
    }
    Set<String> c2 = new HashSet<String>();
    for (String s : s2)
    {
      c2.add(s.toLowerCase());
    }
    return c1.equals(c2);
  }

  /**
   * Convenience method used to merge two Sets of replication servers.
   * @param s1 the first set of replication servers.
   * @param s2 the second set of replication servers.
   * @return a Set of replication servers containing all the replication servers
   * specified in the provided Sets.
   */
  private Set<String> mergeReplicationServers(Set<String> s1, Set<String> s2)
  {
    Set<String> c1 = new HashSet<String>();
    for (String s : s1)
    {
      c1.add(s.toLowerCase());
    }
    for (String s : s2)
    {
      c1.add(s.toLowerCase());
    }
    return c1;
  }

  /**
   * Returns the message that must be displayed to the user for a given
   * exception.  This is assumed to be a critical exception that stops all
   * the processing.
   * @param rce the ReplicationCliException.
   * @return a message to be displayed to the user.
   */
  private Message getCriticalExceptionMessage(ReplicationCliException rce)
  {
    MessageBuilder mb = new MessageBuilder();
    mb.append(rce.getMessageObject());
    File logFile = ControlPanelLog.getLogFile();
    if ((logFile != null) &&
        (rce.getErrorCode() != ReplicationCliReturnCode.USER_CANCELLED))
    {
      mb.append(Constants.LINE_SEPARATOR);
      mb.append(INFO_GENERAL_SEE_FOR_DETAILS.get(logFile.getPath()));
    }
    // Check if the cause has already been included in the message
    Throwable c = rce.getCause();
    if (c != null)
    {
      String s;
      if (c instanceof NamingException)
      {
        s = ((NamingException)c).toString(true);
      }
      else if (c instanceof OpenDsException)
      {
        Message msg = ((OpenDsException)c).getMessageObject();
        if (msg != null)
        {
          s = msg.toString();
        }
        else
        {
          s = c.toString();
        }
      }
      else
      {
        s = c.toString();
      }
      if (mb.toString().indexOf(s) == -1)
      {
        mb.append(Constants.LINE_SEPARATOR);
        mb.append(INFO_REPLICATION_CRITICAL_ERROR_DETAILS.get(s));
      }
    }
    return mb.toMessage();
  }

  private boolean mustInitializeSchema(ServerDescriptor server1,
      ServerDescriptor server2, EnableReplicationUserData uData)
  {
    boolean mustInitializeSchema = false;
    if (!argParser.noSchemaReplication())
    {
      String id1 = server1.getSchemaReplicationID();
      String id2 = server2.getSchemaReplicationID();
      if (id1 != null)
      {
        mustInitializeSchema = !id1.equals(id2);
      }
      else
      {
        mustInitializeSchema = true;
      }
    }
    if (mustInitializeSchema)
    {
      // Check that both will contain replication data
      mustInitializeSchema = uData.configureReplicationDomain1() &&
      uData.configureReplicationDomain2();
    }
    return mustInitializeSchema;
  }

  /**
   * This method registers a server in a given ADSContext.  If the server was
   * already registered it unregisters it and registers again (some properties
   * might have changed).
   * @param adsContext the ADS Context to be used.
   * @param server the server to be registered.
   * @throws ADSContextException if an error occurs during the registration or
   * unregistration of the server.
   */
  private void registerServer(ADSContext adsContext,
      Map<ADSContext.ServerProperty, Object> serverProperties)
  throws ADSContextException
  {
    try
    {
      adsContext.registerServer(serverProperties);
    }
    catch (ADSContextException ade)
    {
      if (ade.getError() ==
        ADSContextException.ErrorType.ALREADY_REGISTERED)
      {
        LOG.log(Level.WARNING, "The server was already registered: "+
            serverProperties);
        adsContext.unregisterServer(serverProperties);
        adsContext.registerServer(serverProperties);
      }
      else
      {
        throw ade;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAdvancedMode() {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInteractive() {
    if (forceNonInteractive)
    {
      return false;
    }
    else
    {
      return argParser.isInteractive();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isQuiet()
  {
    return argParser.isQuiet();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isScriptFriendly() {
    return argParser.isScriptFriendly();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVerbose() {
    return true;
  }

  /**
   * Prints a message to the output with no wrapping if we are not in quiet
   * mode.
   * @param msg the message to be displayed.
   */
  private void printProgressMessageNoWrap(Message msg)
  {
    if (!isQuiet())
    {
      getOutputStream().print(msg.toString());
    }
  }

  /**
   * Forces the initialization of the trust manager in the
   * LDAPConnectionInteraction object.
   */
  private void forceTrustManagerInitialization()
  {
    forceNonInteractive = true;
    try
    {
      ci.initializeTrustManagerIfRequired();
    }
    catch (ArgumentException ae)
    {
      LOG.log(Level.WARNING, "Error initializing trust store: "+ae, ae);
    }
    forceNonInteractive = false;
  }

  /**
   * Method used to compare two server registries.
   * @param registry1 the first registry to compare.
   * @param registry2 the second registry to compare.
   * @return <CODE>true</CODE> if the registries are equal and
   * <CODE>false</CODE> otherwise.
   */
  private boolean areEqual(
      Set<Map<ADSContext.ServerProperty, Object>> registry1,
      Set<Map<ADSContext.ServerProperty, Object>> registry2)
  {
    boolean areEqual = registry1.size() == registry2.size();
    if (areEqual)
    {
      Set<ADSContext.ServerProperty> propertiesToCompare =
        new HashSet<ADSContext.ServerProperty>();
      ADSContext.ServerProperty[] properties =
        ADSContext.ServerProperty.values();
      for (int i=0; i<properties.length; i++)
      {
        if (properties[i].getAttributeSyntax() !=
          ADSPropertySyntax.CERTIFICATE_BINARY)
        {
          propertiesToCompare.add(properties[i]);
        }
      }
      for (Map<ADSContext.ServerProperty, Object> server1 : registry1)
      {
        boolean found = false;

        for (Map<ADSContext.ServerProperty, Object> server2 : registry2)
        {
          found = true;
          for (ADSContext.ServerProperty prop : propertiesToCompare)
          {
            Object v1 = server1.get(prop);
            Object v2 = server2.get(prop);
            if (v1 != null)
            {
              found = v1.equals(v2);
            }
            else if (v2 != null)
            {
              found = false;
            }
            if (!found)
            {
              break;
            }
          }
          if (found)
          {
            break;
          }
        }

        areEqual = found;
        if (!areEqual)
        {
          break;
        }
      }
    }
    return areEqual;
  }

  /**
   * Tells whether we are trying to disable all the replicated suffixes.
   * @param uData the disable replication data provided by the user.
   * @return <CODE>true</CODE> if we want to disable all the replicated suffixes
   * and <CODE>false</CODE> otherwise.
   */
  private boolean disableAllBaseDns(InitialLdapContext ctx,
      DisableReplicationUserData uData)
  {
    if (uData.disableAll())
    {
      return true;
    }

    boolean returnValue = true;
    Collection<ReplicaDescriptor> replicas = getReplicas(ctx);
    Set<String> replicatedSuffixes = new HashSet<String>();
    for (ReplicaDescriptor rep : replicas)
    {
      String dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        replicatedSuffixes.add(dn);
      }
    }

    for (String dn1 : replicatedSuffixes)
    {
      if (!Utils.areDnsEqual(ADSContext.getAdministrationSuffixDN(), dn1) &&
          !Utils.areDnsEqual(Constants.SCHEMA_DN, dn1))
      {
        boolean found = false;
        for (String dn2 : uData.getBaseDNs())
        {
          if (Utils.areDnsEqual(dn1, dn2))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          returnValue = false;
          break;
        }
      }
    }
    return returnValue;
  }

  /**
   * Returns the host port representation of the server to be used in progress,
   * status and error messages.  It takes into account the fact the host and
   * port provided by the user.
   * @param server the ServerDescriptor.
   * @param cnx the preferred connections list.
   * @return the host port string representation of the provided server.
   */
  protected String getHostPort(ServerDescriptor server,
      Collection<PreferredConnection> cnx)
  {
    String hostPort = null;

    for (PreferredConnection connection : cnx)
    {
      String url = connection.getLDAPURL();
      if (url.equals(server.getLDAPURL()))
      {
        hostPort = server.getHostPort(false);
      }
      else if (url.equals(server.getLDAPsURL()))
      {
        hostPort = server.getHostPort(true);
      }
    }
    if (hostPort == null)
    {
      hostPort = server.getHostPort(true);
    }
    return hostPort;
  }

  /**
   * Prompts the user for the subcommand that should be executed.
   * @return the subcommand choice of the user.
   */
  private SubcommandChoice promptForSubcommand()
  {
    SubcommandChoice returnValue;
    MenuBuilder<SubcommandChoice> builder =
      new MenuBuilder<SubcommandChoice>(this);
    builder.setPrompt(INFO_REPLICATION_SUBCOMMAND_PROMPT.get());
    builder.addCancelOption(false);
    for (SubcommandChoice choice : SubcommandChoice.values())
    {
      if (choice != SubcommandChoice.CANCEL)
      {
        builder.addNumberedOption(choice.getPrompt(),
            MenuResult.success(choice));
      }
    }
    try
    {
      MenuResult<SubcommandChoice> m = builder.toMenu().run();
      if (m.isSuccess())
      {
        returnValue = m.getValue();
      }
      else
      {
       // The user cancelled
        returnValue = SubcommandChoice.CANCEL;
      }
    }
    catch (CLIException ce)
    {
      returnValue = SubcommandChoice.CANCEL;
      LOG.log(Level.WARNING, "Error reading input: "+ce, ce);
    }
    return returnValue;
  }

  private boolean mustPrintCommandBuilder()
  {
    return argParser.isInteractive() &&
        (argParser.displayEquivalentArgument.isPresent() ||
        argParser.equivalentCommandFileArgument.isPresent());
  }

  /**
   * Prints the contents of a command builder.  This method has been created
   * since SetPropSubCommandHandler calls it.  All the logic of DSConfig is on
   * this method.  Currently it simply writes the content of the CommandBuilder
   * to the standard output, but if we provide an option to write the content
   * to a file only the implementation of this method must be changed.
   * @param commandBuilder the command builder to be printed.
   */
  private void printCommandBuilder(CommandBuilder commandBuilder)
  {
    if (argParser.displayEquivalentArgument.isPresent())
    {
      println();
      // We assume that the app we are running is this one.
      println(
          INFO_REPLICATION_NON_INTERACTIVE.get(commandBuilder.toString()));
    }
    if (argParser.equivalentCommandFileArgument.isPresent())
    {
      // Write to the file.
      String file = argParser.equivalentCommandFileArgument.getValue();
      try
      {
        BufferedWriter writer =
          new BufferedWriter(new FileWriter(file, true));

        writer.write(SHELL_COMMENT_SEPARATOR+getCurrentOperationDateMessage());
        writer.newLine();

        writer.write(commandBuilder.toString());
        writer.newLine();
        writer.newLine();

        writer.flush();
        writer.close();
      }
      catch (IOException ioe)
      {
        println(
            ERR_REPLICATION_ERROR_WRITING_EQUIVALENT_COMMAND_LINE.get(file,
                ioe.toString()));
      }
    }
  }

  /**
   * Creates a command builder with the global options: script friendly,
   * verbose, etc. for a given subcommand name.  It also adds systematically the
   * no-prompt option.
   * @param subcommandName the subcommand name.
   * @param uData the user data.
   * @return the command builder that has been created with the specified
   * subcommandName.
   */
  private CommandBuilder createCommandBuilder(String subcommandName,
      ReplicationUserData uData) throws ArgumentException
  {
    String commandName = getCommandName();

    CommandBuilder commandBuilder =
      new CommandBuilder(commandName, subcommandName);


    if (subcommandName.equals(
            ReplicationCliArgumentParser.ENABLE_REPLICATION_SUBCMD_NAME))
    {
      // All the arguments for enable replication are update here.
      updateCommandBuilder(commandBuilder, (EnableReplicationUserData)uData);
    }
    else if (subcommandName.equals(
            ReplicationCliArgumentParser.INITIALIZE_REPLICATION_SUBCMD_NAME))
    {
      // All the arguments for initialize replication are update here.
      updateCommandBuilder(commandBuilder,
          (InitializeReplicationUserData)uData);
    }
    else if (subcommandName.equals(
        ReplicationCliArgumentParser.PURGE_HISTORICAL_SUBCMD_NAME))
    {
      // All the arguments for initialize replication are update here.
      updateCommandBuilder(commandBuilder, (PurgeHistoricalUserData)uData);
    }
    else
    {
      // Update the arguments used in the console interaction with the
      // actual arguments of dsreplication.
      updateCommandBuilderWithConsoleInteraction(commandBuilder, ci);
    }

    if (subcommandName.equals(
        ReplicationCliArgumentParser.DISABLE_REPLICATION_SUBCMD_NAME))
    {
      DisableReplicationUserData disableData =
        (DisableReplicationUserData)uData;
      if (disableData.disableAll())
      {
        commandBuilder.addArgument(new BooleanArgument(
            argParser.disableAllArg.getName(),
            argParser.disableAllArg.getShortIdentifier(),
            argParser.disableAllArg.getLongIdentifier(),
            INFO_DESCRIPTION_DISABLE_ALL.get()));
      }
      else if (disableData.disableReplicationServer())
      {
        commandBuilder.addArgument(new BooleanArgument(
            argParser.disableReplicationServerArg.getName(),
            argParser.disableReplicationServerArg.getShortIdentifier(),
            argParser.disableReplicationServerArg.getLongIdentifier(),
            INFO_DESCRIPTION_DISABLE_REPLICATION_SERVER.get()));
      }
    }

    addGlobalArguments(commandBuilder, uData);
    return commandBuilder;
  }

  private String getCommandName()
  {
    String commandName =
      System.getProperty(ServerConstants.PROPERTY_SCRIPT_NAME);
    if (commandName == null)
    {
      commandName = "dsreplication";
    }
    return commandName;
  }

  private void updateCommandBuilderWithConsoleInteraction(
      CommandBuilder commandBuilder,
      LDAPConnectionConsoleInteraction ci) throws ArgumentException
  {
    if ((ci != null) && (ci.getCommandBuilder() != null))
    {
      CommandBuilder interactionBuilder = ci.getCommandBuilder();
      for (Argument arg : interactionBuilder.getArguments())
      {
        if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD))
        {
          StringArgument bindPasswordArg = new StringArgument("adminPassword",
              OPTION_SHORT_BINDPWD, "adminPassword", false, false, true,
              INFO_BINDPWD_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
          bindPasswordArg.addValue(arg.getValue());
          commandBuilder.addObfuscatedArgument(bindPasswordArg);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD_FILE))
        {
          FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
              "adminPasswordFile",
              OPTION_SHORT_BINDPWD_FILE, "adminPasswordFile", false, false,
              INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
          bindPasswordFileArg.getNameToValueMap().putAll(
              ((FileBasedArgument)arg).getNameToValueMap());
          commandBuilder.addArgument(bindPasswordFileArg);
        }
        else
        {
          if (interactionBuilder.isObfuscated(arg))
          {
            commandBuilder.addObfuscatedArgument(arg);
          }
          else
          {
            commandBuilder.addArgument(arg);
          }
        }
      }
    }
  }

  private void updateCommandBuilder(CommandBuilder commandBuilder,
      PurgeHistoricalUserData uData) throws ArgumentException
  {
    if (uData.isOnline())
    {
      updateCommandBuilderWithConsoleInteraction(commandBuilder, ci);
      if (uData.getTaskSchedule() != null)
      {
        updateCommandBuilderWithTaskSchedule(commandBuilder,
            uData.getTaskSchedule());
      }
    }

    IntegerArgument maximumDurationArg = new IntegerArgument(
        argParser.maximumDurationArg.getName(),
        argParser.maximumDurationArg.getShortIdentifier(),
        argParser.maximumDurationArg.getLongIdentifier(),
        argParser.maximumDurationArg.isRequired(),
        argParser.maximumDurationArg.isMultiValued(),
        argParser.maximumDurationArg.needsValue(),
        argParser.maximumDurationArg.getValuePlaceholder(),
        PurgeConflictsHistoricalTask.DEFAULT_MAX_DURATION,
        argParser.maximumDurationArg.getPropertyName(),
        argParser.maximumDurationArg.getDescription());
    maximumDurationArg.addValue(String.valueOf(uData.getMaximumDuration()));
    commandBuilder.addArgument(maximumDurationArg);
  }

  private void updateCommandBuilderWithTaskSchedule(
      CommandBuilder commandBuilder,
      TaskScheduleUserData taskSchedule)
  {
    TaskScheduleUserData.updateCommandBuilderWithTaskSchedule(
        commandBuilder, taskSchedule);
  }

  private void addGlobalArguments(CommandBuilder commandBuilder,
      ReplicationUserData uData)
  throws ArgumentException
  {
    LinkedList<String> baseDNs = uData.getBaseDNs();
    StringArgument baseDNsArg = new StringArgument("baseDNs",
        OPTION_SHORT_BASEDN,
        OPTION_LONG_BASEDN, false, true, true, INFO_BASEDN_PLACEHOLDER.get(),
        null,
        null, INFO_DESCRIPTION_REPLICATION_BASEDNS.get());
    for (String baseDN : baseDNs)
    {
      baseDNsArg.addValue(baseDN);
    }
    commandBuilder.addArgument(baseDNsArg);

    // Try to find some arguments and put them at the end.
    String[] identifiersToMove ={
        OPTION_LONG_ADMIN_UID,
        "adminPassword",
        "adminPasswordFile",
        OPTION_LONG_SASLOPTION,
        OPTION_LONG_TRUSTALL,
        OPTION_LONG_TRUSTSTOREPATH,
        OPTION_LONG_TRUSTSTORE_PWD,
        OPTION_LONG_TRUSTSTORE_PWD_FILE,
        OPTION_LONG_KEYSTOREPATH,
        OPTION_LONG_KEYSTORE_PWD,
        OPTION_LONG_KEYSTORE_PWD_FILE,
        OPTION_LONG_CERT_NICKNAME
    };

    ArrayList<Argument> toMoveArgs = new ArrayList<Argument>();
    for (String longID : identifiersToMove)
    {
      for (Argument arg : commandBuilder.getArguments())
      {
        if (longID.equals(arg.getLongIdentifier()))
        {
          toMoveArgs.add(arg);
          break;
        }
      }
    }
    for (Argument argToMove : toMoveArgs)
    {
      boolean toObfuscate = commandBuilder.isObfuscated(argToMove);
      commandBuilder.removeArgument(argToMove);
      if (toObfuscate)
      {
        commandBuilder.addObfuscatedArgument(argToMove);
      }
      else
      {
        commandBuilder.addArgument(argToMove);
      }
    }

    if (argParser.isVerbose())
    {
      commandBuilder.addArgument(new BooleanArgument("verbose",
          OPTION_SHORT_VERBOSE,
          OPTION_LONG_VERBOSE, INFO_DESCRIPTION_VERBOSE.get()));
    }

    if (argParser.isScriptFriendly())
    {
      commandBuilder.addArgument(argParser.scriptFriendlyArg);
    }

    commandBuilder.addArgument(argParser.noPromptArg);

    if (argParser.propertiesFileArgument.isPresent())
    {
      commandBuilder.addArgument(argParser.propertiesFileArgument);
    }

    if (argParser.noPropertiesFileArgument.isPresent())
    {
      commandBuilder.addArgument(argParser.noPropertiesFileArgument);
    }
  }

  private void updateCommandBuilder(CommandBuilder commandBuilder,
      EnableReplicationUserData uData)
  throws ArgumentException
  {
    // Update the arguments used in the console interaction with the
    // actual arguments of dsreplication.
    boolean adminInformationAdded = false;

    if (firstServerCommandBuilder != null)
    {
      boolean useAdminUID = false;
      for (Argument arg : firstServerCommandBuilder.getArguments())
      {
        if (arg.getLongIdentifier().equals(OPTION_LONG_ADMIN_UID))
        {
          useAdminUID = true;
          break;
        }
      }
      // This is required when both the bindDN and the admin UID are provided
      // in the command-line.
      boolean forceAddBindDN1 = false;
      boolean forceAddBindPwdFile1 = false;
      if (useAdminUID)
      {
        String bindDN1 = uData.getBindDn1();
        String adminUID = uData.getAdminUid();
        if (bindDN1 != null && adminUID != null)
        {
          if (!Utils.areDnsEqual(ADSContext.getAdministratorDN(adminUID),
              bindDN1))
          {
            forceAddBindDN1 = true;

            for (Argument arg : firstServerCommandBuilder.getArguments())
            {
              if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD_FILE))
              {
                forceAddBindPwdFile1 = true;
                break;
              }
            }
          }
        }
      }
      for (Argument arg : firstServerCommandBuilder.getArguments())
      {
        if (arg.getLongIdentifier().equals(OPTION_LONG_HOST))
        {
          StringArgument host = new StringArgument("host1", OPTION_SHORT_HOST,
              "host1", false, false, true, INFO_HOST_PLACEHOLDER.get(),
              null,
              null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST1.get());
          host.addValue(uData.getHostName1());
          commandBuilder.addArgument(host);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_PORT))
        {
          IntegerArgument port = new IntegerArgument("port1", OPTION_SHORT_PORT,
              "port1",
              false, false, true, INFO_PORT_PLACEHOLDER.get(), 4444, null,
              INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT1.get());
          port.addValue(String.valueOf(uData.getPort1()));
          commandBuilder.addArgument(port);

          if (forceAddBindDN1)
          {
            StringArgument bindDN = new StringArgument("bindDN1",
                OPTION_SHORT_BINDDN,
                "bindDN1", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
                "cn=Directory Manager", null,
                INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN1.get());
            bindDN.addValue(uData.getBindDn1());
            commandBuilder.addArgument(bindDN);
            if (forceAddBindPwdFile1)
            {
              FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
                  "bindPasswordFile1",
                  null, "bindPasswordFile1", false, false,
                  INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                  INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE1.get());
              bindPasswordFileArg.getNameToValueMap().put("{password file}",
                  "{password file}");
              commandBuilder.addArgument(bindPasswordFileArg);
            }
            else
            {
              StringArgument bindPasswordArg = new StringArgument(
                  "bindPassword1",
                  null, "bindPassword1", false, false, true,
                  INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                  INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD1.get());
              bindPasswordArg.addValue(arg.getValue());
              commandBuilder.addObfuscatedArgument(bindPasswordArg);
            }
          }
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDDN))
        {
          StringArgument bindDN = new StringArgument("bindDN1",
              OPTION_SHORT_BINDDN,
              "bindDN1", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
              "cn=Directory Manager", null,
              INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN1.get());
          bindDN.addValue(uData.getBindDn1());
          commandBuilder.addArgument(bindDN);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD))
        {
          if (useAdminUID)
          {
            adminInformationAdded = true;
            StringArgument bindPasswordArg = new StringArgument("adminPassword",
                OPTION_SHORT_BINDPWD, "adminPassword", false, false, true,
                INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
            bindPasswordArg.addValue(arg.getValue());
            commandBuilder.addObfuscatedArgument(bindPasswordArg);
          }
          else
          {
            StringArgument bindPasswordArg = new StringArgument("bindPassword1",
                null, "bindPassword1", false, false, true,
                INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD1.get());
            bindPasswordArg.addValue(arg.getValue());
            commandBuilder.addObfuscatedArgument(bindPasswordArg);
          }
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD_FILE))
        {
          if (useAdminUID)
          {
            FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
                "adminPasswordFile",
                OPTION_SHORT_BINDPWD_FILE, "adminPasswordFile", false, false,
                INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
            bindPasswordFileArg.getNameToValueMap().putAll(
                ((FileBasedArgument)arg).getNameToValueMap());
            commandBuilder.addArgument(bindPasswordFileArg);
          }
          else
          {
            FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
                "bindPasswordFile1",
                null, "bindPasswordFile1", false, false,
                INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE1.get());
            bindPasswordFileArg.getNameToValueMap().putAll(
                ((FileBasedArgument)arg).getNameToValueMap());
            commandBuilder.addArgument(bindPasswordFileArg);
          }
        }
        else
        {
          if (arg.getLongIdentifier().equals(OPTION_LONG_ADMIN_UID))
          {
            adminInformationAdded = true;
          }
          if (firstServerCommandBuilder.isObfuscated(arg))
          {
            commandBuilder.addObfuscatedArgument(arg);
          }
          else
          {
            commandBuilder.addArgument(arg);
          }
        }
      }
    }


    if ((ci != null) && (ci.getCommandBuilder() != null))
    {
      CommandBuilder interactionBuilder = ci.getCommandBuilder();
      boolean useAdminUID = false;
      boolean hasBindDN = false;
      for (Argument arg : interactionBuilder.getArguments())
      {
        if (arg.getLongIdentifier().equals(OPTION_LONG_ADMIN_UID))
        {
          useAdminUID = true;
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDDN))
        {
          hasBindDN = true;
        }
        if (useAdminUID && hasBindDN)
        {
          break;
        }
      }
//    This is required when both the bindDN and the admin UID are provided
      // in the command-line.
      boolean forceAddBindDN2 = false;
      boolean forceAddBindPwdFile2 = false;
      if (useAdminUID)
      {
        String bindDN2 = uData.getBindDn2();
        String adminUID = uData.getAdminUid();
        if (bindDN2 != null && adminUID != null)
        {
          if (!Utils.areDnsEqual(ADSContext.getAdministratorDN(adminUID),
              bindDN2))
          {
            forceAddBindDN2 = true;

            for (Argument arg : interactionBuilder.getArguments())
            {
              if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD_FILE))
              {
                forceAddBindPwdFile2 = true;
                break;
              }
            }
          }
        }
      }
      ArrayList<Argument> argsToAnalyze = new ArrayList<Argument>();
      for (Argument arg : interactionBuilder.getArguments())
      {
        if (arg.getLongIdentifier().equals(OPTION_LONG_HOST))
        {
          StringArgument host = new StringArgument("host2", 'O',
              "host2", false, false, true, INFO_HOST_PLACEHOLDER.get(),
              null,
              null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST2.get());
          host.addValue(uData.getHostName2());
          commandBuilder.addArgument(host);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_PORT))
        {
          IntegerArgument port = new IntegerArgument("port2", null, "port2",
              false, false, true, INFO_PORT_PLACEHOLDER.get(), 4444, null,
              INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT2.get());
          port.addValue(String.valueOf(uData.getPort2()));
          commandBuilder.addArgument(port);

          if (forceAddBindDN2)
          {
            StringArgument bindDN = new StringArgument("bindDN2",
                OPTION_SHORT_BINDDN,
                "bindDN2", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
                "cn=Directory Manager", null,
                INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN2.get());
            bindDN.addValue(uData.getBindDn2());
            commandBuilder.addArgument(bindDN);
            if (forceAddBindPwdFile2)
            {
              FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
                  "bindPasswordFile2",
                  null, "bindPasswordFile2", false, false,
                  INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                  INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE2.get());
              bindPasswordFileArg.getNameToValueMap().put("{password file}",
                  "{password file}");
              commandBuilder.addArgument(bindPasswordFileArg);
            }
            else
            {
              StringArgument bindPasswordArg = new StringArgument(
                  "bindPassword2",
                  null, "bindPassword2", false, false, true,
                  INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                  INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD2.get());
              bindPasswordArg.addValue(arg.getValue());
              commandBuilder.addObfuscatedArgument(bindPasswordArg);
            }
          }
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDDN))
        {
          StringArgument bindDN = new StringArgument("bindDN2", null,
              "bindDN2", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
              "cn=Directory Manager", null,
              INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN2.get());
          bindDN.addValue(uData.getBindDn2());
          commandBuilder.addArgument(bindDN);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD))
        {
          if (useAdminUID && !adminInformationAdded)
          {
            adminInformationAdded = true;
            StringArgument bindPasswordArg = new StringArgument("adminPassword",
                OPTION_SHORT_BINDPWD, "adminPassword", false, false, true,
                INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
            bindPasswordArg.addValue(arg.getValue());
            commandBuilder.addObfuscatedArgument(bindPasswordArg);
          }
          else if (hasBindDN)
          {
            StringArgument bindPasswordArg = new StringArgument("bindPassword2",
                null, "bindPassword2", false, false, true,
                INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD2.get());
            bindPasswordArg.addValue(arg.getValue());
            commandBuilder.addObfuscatedArgument(bindPasswordArg);
          }
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD_FILE))
        {
          if (useAdminUID && !adminInformationAdded)
          {
            adminInformationAdded = true;
            FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
                "adminPasswordFile",
                OPTION_SHORT_BINDPWD_FILE, "adminPasswordFile", false, false,
                INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
            bindPasswordFileArg.getNameToValueMap().putAll(
                ((FileBasedArgument)arg).getNameToValueMap());
            commandBuilder.addArgument(bindPasswordFileArg);
          }
          else if (hasBindDN)
          {
            FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
                "bindPasswordFile2",
                null, "bindPasswordFile2", false, false,
                INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE2.get());
            bindPasswordFileArg.getNameToValueMap().putAll(
                ((FileBasedArgument)arg).getNameToValueMap());
            commandBuilder.addArgument(bindPasswordFileArg);
          }
        }
        else
        {
          argsToAnalyze.add(arg);
        }
      }

      for (Argument arg : argsToAnalyze)
      {
        // Just check that the arguments have not already been added.
        boolean found = false;
        for (Argument a : commandBuilder.getArguments())
        {
          if (a.getLongIdentifier().equals(arg.getLongIdentifier()))
          {
            found = true;
            break;
          }
        }

        if (!found)
        {
          if (interactionBuilder.isObfuscated(arg))
          {
            commandBuilder.addObfuscatedArgument(arg);
          }
          else
          {
            commandBuilder.addArgument(arg);
          }
        }
      }
    }

    // Try to add the new administration information.
    if (!adminInformationAdded)
    {
      StringArgument adminUID = new StringArgument(OPTION_LONG_ADMIN_UID, 'I',
          OPTION_LONG_ADMIN_UID, false, false, true,
          INFO_ADMINUID_PLACEHOLDER.get(),
          Constants.GLOBAL_ADMIN_UID, null,
          INFO_DESCRIPTION_REPLICATION_ADMIN_UID.get(
              ReplicationCliArgumentParser.ENABLE_REPLICATION_SUBCMD_NAME));
      if (uData.getAdminUid() != null)
      {
        adminUID.addValue(uData.getAdminUid());
        commandBuilder.addArgument(adminUID);
      }

      if (userProvidedAdminPwdFile != null)
      {
        commandBuilder.addArgument(userProvidedAdminPwdFile);
      }
      else
      {
        Argument bindPasswordArg = new StringArgument("adminPassword",
            OPTION_SHORT_BINDPWD, "adminPassword", false, false, true,
            INFO_BINDPWD_PLACEHOLDER.get(), null, null,
            INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
        if (uData.getAdminPwd() != null)
        {
          bindPasswordArg.addValue(uData.getAdminPwd());
          commandBuilder.addObfuscatedArgument(bindPasswordArg);
        }
      }
    }

    if (uData.configureReplicationServer1() &&
        !uData.configureReplicationDomain1())
    {
      commandBuilder.addArgument(new BooleanArgument(
          argParser.onlyReplicationServer1Arg.getName(),
          argParser.onlyReplicationServer1Arg.getShortIdentifier(),
          argParser.onlyReplicationServer1Arg.getLongIdentifier(),
          INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER1.get()));
    }

    if (!uData.configureReplicationServer1() &&
        uData.configureReplicationDomain1())
    {
      commandBuilder.addArgument(new BooleanArgument(
          argParser.noReplicationServer1Arg.getName(),
          argParser.noReplicationServer1Arg.getShortIdentifier(),
          argParser.noReplicationServer1Arg.getLongIdentifier(),
          INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER1.get()));
    }

    if (uData.configureReplicationServer1() &&
        uData.getReplicationPort1() > 0)
    {
      IntegerArgument replicationPort1 = new IntegerArgument(
          "replicationPort1", 'r',
          "replicationPort1", false, false, true, INFO_PORT_PLACEHOLDER.get(),
          8989, null,
          INFO_DESCRIPTION_ENABLE_REPLICATION_PORT1.get());
      replicationPort1.addValue(String.valueOf(uData.getReplicationPort1()));
      commandBuilder.addArgument(replicationPort1);
    }
    if (uData.isSecureReplication1())
    {
      commandBuilder.addArgument(new BooleanArgument("secureReplication1", null,
          "secureReplication1",
          INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION1.get()));
    }


    if (uData.configureReplicationServer2() &&
        !uData.configureReplicationDomain2())
    {
      commandBuilder.addArgument(new BooleanArgument(
          argParser.onlyReplicationServer2Arg.getName(),
          argParser.onlyReplicationServer2Arg.getShortIdentifier(),
          argParser.onlyReplicationServer2Arg.getLongIdentifier(),
          INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER2.get()));
    }

    if (!uData.configureReplicationServer2() &&
        uData.configureReplicationDomain2())
    {
      commandBuilder.addArgument(new BooleanArgument(
          argParser.noReplicationServer2Arg.getName(),
          argParser.noReplicationServer2Arg.getShortIdentifier(),
          argParser.noReplicationServer2Arg.getLongIdentifier(),
          INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER2.get()));
    }
    if (uData.configureReplicationServer2() &&
        uData.getReplicationPort2() > 0)
    {
      IntegerArgument replicationPort2 = new IntegerArgument(
          "replicationPort2", 'r',
          "replicationPort2", false, false, true, INFO_PORT_PLACEHOLDER.get(),
          uData.getReplicationPort2(), null,
          INFO_DESCRIPTION_ENABLE_REPLICATION_PORT2.get());
      replicationPort2.addValue(String.valueOf(uData.getReplicationPort2()));
      commandBuilder.addArgument(replicationPort2);
    }
    if (uData.isSecureReplication2())
    {
      commandBuilder.addArgument(new BooleanArgument("secureReplication2", null,
          "secureReplication2",
          INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION2.get()));
    }


    if (!uData.replicateSchema())
    {
      commandBuilder.addArgument(new BooleanArgument(
          "noschemareplication", null, "noSchemaReplication",
          INFO_DESCRIPTION_ENABLE_REPLICATION_NO_SCHEMA_REPLICATION.get()));
    }
    if (argParser.skipReplicationPortCheck())
    {
      commandBuilder.addArgument(new BooleanArgument(
          "skipportcheck", 'S', "skipPortCheck",
          INFO_DESCRIPTION_ENABLE_REPLICATION_SKIPPORT.get()));
    }
    if (argParser.useSecondServerAsSchemaSource())
    {
      commandBuilder.addArgument(new BooleanArgument(
          "usesecondserverasschemasource", null,
          "useSecondServerAsSchemaSource",
          INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SECOND_AS_SCHEMA_SOURCE.get(
              "--"+argParser.noSchemaReplicationArg.getLongIdentifier())));
    }
  }

  private void updateCommandBuilder(CommandBuilder commandBuilder,
      InitializeReplicationUserData uData)
  throws ArgumentException
  {
    // Update the arguments used in the console interaction with the
    // actual arguments of dsreplication.

    if (firstServerCommandBuilder != null)
    {
      for (Argument arg : firstServerCommandBuilder.getArguments())
      {
        if (arg.getLongIdentifier().equals(OPTION_LONG_HOST))
        {
          StringArgument host = new StringArgument("hostSource", 'O',
              "hostSource", false, false, true,
              INFO_HOST_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_SOURCE.get());
          host.addValue(uData.getHostNameSource());
          commandBuilder.addArgument(host);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_PORT))
        {
          IntegerArgument port = new IntegerArgument("portSource", null,
              "portSource", false, false, true,
              INFO_PORT_PLACEHOLDER.get(),
              4444,
              null,
         INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_SOURCE.get());
          port.addValue(String.valueOf(uData.getPortSource()));
          commandBuilder.addArgument(port);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD))
        {
          StringArgument bindPasswordArg = new StringArgument("adminPassword",
              OPTION_SHORT_BINDPWD, "adminPassword", false, false, true,
              INFO_BINDPWD_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
          bindPasswordArg.addValue(arg.getValue());
          commandBuilder.addObfuscatedArgument(bindPasswordArg);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_BINDPWD_FILE))
        {
          FileBasedArgument bindPasswordFileArg = new FileBasedArgument(
              "adminPasswordFile",
              OPTION_SHORT_BINDPWD_FILE, "adminPasswordFile", false, false,
              INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
          bindPasswordFileArg.getNameToValueMap().putAll(
              ((FileBasedArgument)arg).getNameToValueMap());
          commandBuilder.addArgument(bindPasswordFileArg);
        }
        else
        {
          if (firstServerCommandBuilder.isObfuscated(arg))
          {
            commandBuilder.addObfuscatedArgument(arg);
          }
          else
          {
            commandBuilder.addArgument(arg);
          }
        }
      }
    }


    if ((ci != null) && (ci.getCommandBuilder() != null))
    {
      CommandBuilder interactionBuilder = ci.getCommandBuilder();
      for (Argument arg : interactionBuilder.getArguments())
      {
        if (arg.getLongIdentifier().equals(OPTION_LONG_HOST))
        {
          StringArgument host = new StringArgument("hostDestination", 'O',
              "hostDestination", false, false, true,
              INFO_HOST_PLACEHOLDER.get(),
              null, null,
              INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_DESTINATION.get());
          host.addValue(uData.getHostNameDestination());
          commandBuilder.addArgument(host);
        }
        else if (arg.getLongIdentifier().equals(OPTION_LONG_PORT))
        {
          IntegerArgument port = new IntegerArgument("portDestination", null,
              "portDestination", false, false, true,
              INFO_PORT_PLACEHOLDER.get(),
              4444,
              null,
         INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_DESTINATION.get());
          port.addValue(String.valueOf(uData.getPortDestination()));
          commandBuilder.addArgument(port);
        }
      }
    }
  }

  private void updateAvailableAndReplicatedSuffixesForOneDomain(
      InitialLdapContext ctxDomain, InitialLdapContext ctxOther,
      Collection<String> availableSuffixes,
      Collection<String> alreadyReplicatedSuffixes)
  {
    Collection<ReplicaDescriptor> replicas = getReplicas(ctxDomain);
    int replicationPort = getReplicationPort(ctxOther);
    boolean isReplicationServerConfigured = replicationPort != -1;
    String replicationServer = getReplicationServer(
      ConnectionUtils.getHostName(ctxOther), replicationPort);
    for (ReplicaDescriptor replica : replicas)
    {
      if (!isReplicationServerConfigured)
      {
        if (replica.isReplicated())
        {
          alreadyReplicatedSuffixes.add(replica.getSuffix().getDN());
        }
        availableSuffixes.add(replica.getSuffix().getDN());
      }
      if (!isReplicationServerConfigured)
      {
        availableSuffixes.add(replica.getSuffix().getDN());
      }
      else
      {
        if (!replica.isReplicated())
        {
          availableSuffixes.add(replica.getSuffix().getDN());
        }
        else
        {
          // Check if the replica is already configured with the replication
          // server.
          boolean alreadyReplicated = false;
          Set<String> rServers = replica.getReplicationServers();
          for (String rServer : rServers)
          {
            if (replicationServer.equalsIgnoreCase(rServer))
            {
              alreadyReplicated = true;
            }
          }
          if (alreadyReplicated)
          {
            alreadyReplicatedSuffixes.add(replica.getSuffix().getDN());
          }
          else
          {
            availableSuffixes.add(replica.getSuffix().getDN());
          }
        }
      }
    }
  }

  private void updateAvailableAndReplicatedSuffixesForNoDomain(
      InitialLdapContext ctx1, InitialLdapContext ctx2,
      Collection<String> availableSuffixes,
      Collection<String> alreadyReplicatedSuffixes)
  {
    int replicationPort1 = getReplicationPort(ctx1);
    boolean isReplicationServer1Configured = replicationPort1 != -1;
    String replicationServer1 = getReplicationServer(
      ConnectionUtils.getHostName(ctx1), replicationPort1);

    int replicationPort2 = getReplicationPort(ctx2);
    boolean isReplicationServer2Configured = replicationPort2 != -1;
    String replicationServer2 = getReplicationServer(
      ConnectionUtils.getHostName(ctx2), replicationPort2);

    TopologyCache cache1 = null;
    TopologyCache cache2 = null;

    if (isReplicationServer1Configured)
    {
      try
      {
        ADSContext adsContext = new ADSContext(ctx1);
        if (adsContext.hasAdminData())
        {
          cache1 = new TopologyCache(adsContext, getTrustManager(),
              getConnectTimeout());
          cache1.getFilter().setSearchMonitoringInformation(false);
          cache1.setPreferredConnections(
              PreferredConnection.getPreferredConnections(ctx1));
          cache1.reloadTopology();
        }
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Error loading topology cache in "+
            ConnectionUtils.getLdapUrl(ctx1)+": "+t, t);
      }
    }

    if (isReplicationServer2Configured)
    {
      try
      {
        ADSContext adsContext = new ADSContext(ctx2);
        if (adsContext.hasAdminData())
        {
          cache2 = new TopologyCache(adsContext, getTrustManager(),
              getConnectTimeout());
          cache2.getFilter().setSearchMonitoringInformation(false);
          cache2.setPreferredConnections(
              PreferredConnection.getPreferredConnections(ctx2));
          cache2.reloadTopology();
        }
      }
      catch (Throwable t)
      {
        LOG.log(Level.WARNING, "Error loading topology cache in "+
            ConnectionUtils.getLdapUrl(ctx2)+": "+t, t);
      }
    }

    if (cache1 != null && cache2 != null)
    {
      updateAvailableAndReplicatedSuffixesForNoDomainOneSense(cache1, cache2,
          replicationServer1, replicationServer2, availableSuffixes,
          alreadyReplicatedSuffixes);
      updateAvailableAndReplicatedSuffixesForNoDomainOneSense(cache2, cache1,
          replicationServer2, replicationServer1, availableSuffixes,
          alreadyReplicatedSuffixes);
    }
    else if (cache1 != null)
    {
      Set<SuffixDescriptor> suffixes = cache1.getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        for (String rServer : suffix.getReplicationServers())
        {
          if (rServer.equalsIgnoreCase(replicationServer1))
          {
            availableSuffixes.add(suffix.getDN());
          }
        }
      }
    }
    else if (cache2 != null)
    {
      Set<SuffixDescriptor> suffixes = cache2.getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        for (String rServer : suffix.getReplicationServers())
        {
          if (rServer.equalsIgnoreCase(replicationServer2))
          {
            availableSuffixes.add(suffix.getDN());
          }
        }
      }
    }
  }

  private void updateAvailableAndReplicatedSuffixesForNoDomainOneSense(
      TopologyCache cache1, TopologyCache cache2, String replicationServer1,
      String replicationServer2,
      Collection<String> availableSuffixes,
      Collection<String> alreadyReplicatedSuffixes)
  {
    Set<SuffixDescriptor> suffixes = cache1.getSuffixes();
    for (SuffixDescriptor suffix : suffixes)
    {
      for (String rServer : suffix.getReplicationServers())
      {
        if (rServer.equalsIgnoreCase(replicationServer1))
        {
          boolean isSecondReplicatedInSameTopology = false;
          boolean isSecondReplicated = false;
          boolean isFirstReplicated = false;
          for (SuffixDescriptor suffix2 : cache2.getSuffixes())
          {
            if (Utils.areDnsEqual(suffix.getDN(), suffix2.getDN()))
            {
              for (String rServer2 : suffix2.getReplicationServers())
              {
                if (rServer2.equalsIgnoreCase(replicationServer2))
                {
                  isSecondReplicated = true;
                }
                if (rServer.equalsIgnoreCase(replicationServer2))
                {
                  isFirstReplicated = true;
                }
                if (isFirstReplicated && isSecondReplicated)
                {
                  isSecondReplicatedInSameTopology = true;
                  break;
                }
              }
              break;
            }
          }
          if (!isSecondReplicatedInSameTopology)
          {
            availableSuffixes.add(suffix.getDN());
          }
          else
          {
            alreadyReplicatedSuffixes.add(suffix.getDN());
          }
          break;
        }
      }
    }
  }

  private void updateBaseDnsWithNotEnoughReplicationServer(ADSContext adsCtx1,
      ADSContext adsCtx2, EnableReplicationUserData uData,
      Set<String> baseDNsWithNoReplicationServer,
      Set<String> baseDNsWithOneReplicationServer)
  {
    if (uData.configureReplicationServer1() &&
        uData.configureReplicationServer2())
    {
      return;
    }
    Set<SuffixDescriptor> suffixes = new HashSet<SuffixDescriptor>();
    try
    {
      if (adsCtx1.hasAdminData())
      {
        TopologyCache cache = new TopologyCache(adsCtx1,
            getTrustManager(), getConnectTimeout());
        cache.getFilter().setSearchMonitoringInformation(false);
        for (String dn : uData.getBaseDNs())
        {
          cache.getFilter().addBaseDNToSearch(dn);
        }
        cache.reloadTopology();
        suffixes.addAll(cache.getSuffixes());
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error loading topology cache from "+
          ConnectionUtils.getHostPort(adsCtx1.getDirContext())+": "+t, t);
    }

    try
    {
      if (adsCtx2.hasAdminData())
      {
        TopologyCache cache = new TopologyCache(adsCtx2,
            getTrustManager(), getConnectTimeout());
        cache.getFilter().setSearchMonitoringInformation(false);
        cache.reloadTopology();
        for (String dn : uData.getBaseDNs())
        {
          cache.getFilter().addBaseDNToSearch(dn);
        }
        suffixes.addAll(cache.getSuffixes());
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error loading topology cache from "+
          ConnectionUtils.getHostPort(adsCtx2.getDirContext())+": "+t, t);
    }

    int repPort1 = getReplicationPort(adsCtx1.getDirContext());
    String repServer1 =  getReplicationServer(uData.getHostName1(), repPort1);
    int repPort2 = getReplicationPort(adsCtx2.getDirContext());
    String repServer2 =  getReplicationServer(uData.getHostName2(), repPort2);
    for (String baseDN : uData.getBaseDNs())
    {
      int nReplicationServers = 0;
      for (SuffixDescriptor suffix : suffixes)
      {
        if (Utils.areDnsEqual(suffix.getDN(), baseDN))
        {
          Set<String> replicationServers = suffix.getReplicationServers();
          nReplicationServers += replicationServers.size();
          for (String repServer : replicationServers)
          {
            if (uData.configureReplicationServer1() &&
                repServer.equalsIgnoreCase(repServer1))
            {
              nReplicationServers --;
            }
            if (uData.configureReplicationServer2() &&
                repServer.equalsIgnoreCase(repServer2))
            {
              nReplicationServers --;
            }
          }
        }
      }
      if (uData.configureReplicationServer1())
      {
        nReplicationServers ++;
      }
      if (uData.configureReplicationServer2())
      {
        nReplicationServers ++;
      }
      if (nReplicationServers == 1)
      {
        baseDNsWithOneReplicationServer.add(baseDN);
      }
      else if (nReplicationServers == 0)
      {
        baseDNsWithNoReplicationServer.add(baseDN);
      }
    }
  }

  /**
   * Merge the contents of the two registries but only does it partially.
   * Only one of the two ADSContext will be updated (in terms of data in
   * cn=admin data), while the other registry's replication servers will have
   * their truststore updated to be able to initialize all the contents.
   *
   * This method does NOT configure replication between topologies or initialize
   * replication.
   *
   * @param adsCtx1 the ADSContext of the first registry.
   * @param adsCtx2 the ADSContext of the second registry.
   * @return <CODE>true</CODE> if the registry containing all the data is
   * the first registry and <CODE>false</CODE> otherwise.
   * @throws ReplicationCliException if there is a problem reading or updating
   * the registries.
   */
  private boolean mergeRegistries(ADSContext adsCtx1, ADSContext adsCtx2)
  throws ReplicationCliException
  {
    PointAdder pointAdder = new PointAdder(this);
    try
    {
      LinkedHashSet<PreferredConnection> cnx =
        new LinkedHashSet<PreferredConnection>();
      cnx.addAll(PreferredConnection.getPreferredConnections(
          adsCtx1.getDirContext()));
      cnx.addAll(PreferredConnection.getPreferredConnections(
          adsCtx2.getDirContext()));
      // Check that there are no errors.  We do not allow to do the merge with
      // errors.
      TopologyCache cache1 = new TopologyCache(adsCtx1, getTrustManager(),
          getConnectTimeout());
      cache1.setPreferredConnections(cnx);
      cache1.getFilter().setSearchBaseDNInformation(false);
      try
      {
        cache1.reloadTopology();
      }
      catch (TopologyCacheException te)
      {
        LOG.log(Level.SEVERE, "Error reading topology cache of "+
            ConnectionUtils.getHostPort(adsCtx1.getDirContext())+ " "+te, te);
        throw new ReplicationCliException(
            ERR_REPLICATION_READING_ADS.get(te.getMessageObject()),
            ERROR_UPDATING_ADS, te);
      }
      TopologyCache cache2 = new TopologyCache(adsCtx2, getTrustManager(),
          getConnectTimeout());
      cache2.setPreferredConnections(cnx);
      cache2.getFilter().setSearchBaseDNInformation(false);
      try
      {
        cache2.reloadTopology();
      }
      catch (TopologyCacheException te)
      {
        LOG.log(Level.SEVERE, "Error reading topology cache of "+
            ConnectionUtils.getHostPort(adsCtx2.getDirContext())+ " "+te, te);
        throw new ReplicationCliException(
            ERR_REPLICATION_READING_ADS.get(te.getMessageObject()),
            ERROR_UPDATING_ADS, te);
      }

      // Look for the cache with biggest number of replication servers:
      // that one is going to be source.
      int nRepServers1 = 0;
      for (ServerDescriptor server : cache1.getServers())
      {
        if (server.isReplicationServer())
        {
          nRepServers1 ++;
        }
      }

      int nRepServers2 = 0;
      for (ServerDescriptor server : cache2.getServers())
      {
        if (server.isReplicationServer())
        {
          nRepServers2 ++;
        }
      }

      InitialLdapContext ctxSource;
      InitialLdapContext ctxDestination;
      if (nRepServers1 >= nRepServers2)
      {
        ctxSource = adsCtx1.getDirContext();
        ctxDestination = adsCtx2.getDirContext();
      }
      else
      {
        ctxSource = adsCtx2.getDirContext();
        ctxDestination = adsCtx1.getDirContext();
      }

      if (isInteractive())
      {
        Message msg = INFO_REPLICATION_MERGING_REGISTRIES_CONFIRMATION.get(
            ConnectionUtils.getHostPort(ctxSource),
            ConnectionUtils.getHostPort(ctxDestination),
            ConnectionUtils.getHostPort(ctxSource),
            ConnectionUtils.getHostPort(ctxDestination));
        try
        {
          if (!askConfirmation(msg, true, LOG))
          {
            throw new ReplicationCliException(
                ERR_REPLICATION_USER_CANCELLED.get(),
                ReplicationCliReturnCode.USER_CANCELLED, null);
          }
        }
        catch (CLIException ce)
        {
          println(ce.getMessageObject());
          throw new ReplicationCliException(
              ERR_REPLICATION_USER_CANCELLED.get(),
              ReplicationCliReturnCode.USER_CANCELLED, null);
        }
      }
      else
      {
        Message msg = INFO_REPLICATION_MERGING_REGISTRIES_DESCRIPTION.get(
            ConnectionUtils.getHostPort(ctxSource),
            ConnectionUtils.getHostPort(ctxDestination),
            ConnectionUtils.getHostPort(ctxSource),
            ConnectionUtils.getHostPort(ctxDestination));
        println(msg);
        println();
      }

      printProgress(INFO_REPLICATION_MERGING_REGISTRIES_PROGRESS.get());
      pointAdder.start();

      Collection<Message> cache1Errors = cache1.getErrorMessages();
      if (!cache1Errors.isEmpty())
      {
        throw new ReplicationCliException(
            ERR_REPLICATION_CANNOT_MERGE_WITH_ERRORS.get(
                ConnectionUtils.getHostPort(adsCtx1.getDirContext()),
                Utils.getMessageFromCollection(cache1Errors,
                    Constants.LINE_SEPARATOR)),
                    ERROR_READING_ADS, null);
      }

      Collection<Message> cache2Errors = cache2.getErrorMessages();
      if (!cache2Errors.isEmpty())
      {
        throw new ReplicationCliException(
            ERR_REPLICATION_CANNOT_MERGE_WITH_ERRORS.get(
                ConnectionUtils.getHostPort(adsCtx2.getDirContext()),
                Utils.getMessageFromCollection(cache2Errors,
                    Constants.LINE_SEPARATOR)),
                    ERROR_READING_ADS, null);
      }

      Set<Message> commonRepServerIDErrors = new HashSet<Message>();
      for (ServerDescriptor server1 : cache1.getServers())
      {
        if (server1.isReplicationServer())
        {
          int replicationID1 = server1.getReplicationServerId();
          String replServerHostPort1 = server1.getReplicationServerHostPort();
          boolean found = false;
          for (ServerDescriptor server2 : cache2.getServers())
          {
            if (server2.isReplicationServer())
            {
              if ((server2.getReplicationServerId() == replicationID1)
                  && (! server2.getReplicationServerHostPort()
                          .equalsIgnoreCase(replServerHostPort1)))
              {
                commonRepServerIDErrors.add(
                    ERR_REPLICATION_ENABLE_COMMON_REPLICATION_SERVER_ID_ARG.get(
                        server1.getHostPort(true),
                        server2.getHostPort(true),
                        replicationID1));
                found = true;
                break;
              }
            }
          }
          if (found)
          {
            break;
          }
        }
      }
      Set<Message> commonDomainIDErrors = new HashSet<Message>();
      for (SuffixDescriptor suffix1 : cache1.getSuffixes())
      {
        for (ReplicaDescriptor replica1 : suffix1.getReplicas())
        {
          if (replica1.isReplicated())
          {
            int domain1Id = replica1.getReplicationId();
            boolean found = false;
            for (SuffixDescriptor suffix2 : cache2.getSuffixes())
            {
              if (!Utils.areDnsEqual(suffix2.getDN(),
                  replica1.getSuffix().getDN()))
              {
                // Conflicting domain names must apply to same suffix.
                continue;
              }
              for (ReplicaDescriptor replica2 : suffix2.getReplicas())
              {
                if (replica2.isReplicated())
                {
                  if (domain1Id == replica2.getReplicationId())
                  {
                    commonDomainIDErrors.add(
                        ERR_REPLICATION_ENABLE_COMMON_DOMAIN_ID_ARG.get(
                            replica1.getServer().getHostPort(true),
                            suffix1.getDN(),
                            replica2.getServer().getHostPort(true),
                            suffix2.getDN(),
                            domain1Id));
                    found = true;
                    break;
                  }
                }
              }
              if (found)
              {
                break;
              }
            }
          }
        }
      }
      if (!commonRepServerIDErrors.isEmpty() || !commonDomainIDErrors.isEmpty())
      {
        MessageBuilder mb = new MessageBuilder();
        if (!commonRepServerIDErrors.isEmpty())
        {
          mb.append(ERR_REPLICATION_ENABLE_COMMON_REPLICATION_SERVER_ID.get(
            Utils.getMessageFromCollection(commonRepServerIDErrors,
                Constants.LINE_SEPARATOR)));
        }
        if (!commonDomainIDErrors.isEmpty())
        {
          if (mb.length() > 0)
          {
            mb.append(Constants.LINE_SEPARATOR);
          }
          mb.append(ERR_REPLICATION_ENABLE_COMMON_DOMAIN_ID.get(
            Utils.getMessageFromCollection(commonDomainIDErrors,
                Constants.LINE_SEPARATOR)));
        }
        throw new ReplicationCliException(mb.toMessage(),
            ReplicationCliReturnCode.REPLICATION_ADS_MERGE_NOT_SUPPORTED,
            null);
      }

      ADSContext adsCtxSource;
      ADSContext adsCtxDestination;
      TopologyCache cacheDestination;
      if (nRepServers1 >= nRepServers2)
      {
        adsCtxSource = adsCtx1;
        adsCtxDestination = adsCtx2;
        cacheDestination = cache2;
      }
      else
      {
        adsCtxSource = adsCtx2;
        adsCtxDestination = adsCtx1;
        cacheDestination = cache1;
      }

      try
      {
        adsCtxSource.mergeWithRegistry(adsCtxDestination);
      }
      catch (ADSContextException adce)
      {
        LOG.log(Level.SEVERE, "Error merging registry of "+
            ConnectionUtils.getHostPort(adsCtxSource.getDirContext())+
            " with registry of "+
            ConnectionUtils.getHostPort(adsCtxDestination.getDirContext())+" "+
            adce, adce);
        if (adce.getError() == ADSContextException.ErrorType.ERROR_MERGING)
        {
          throw new ReplicationCliException(adce.getMessageObject(),
          REPLICATION_ADS_MERGE_NOT_SUPPORTED, adce);
        }
        else
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_UPDATING_ADS.get(adce.getMessageObject()),
              ERROR_UPDATING_ADS, adce);
        }
      }

      try
      {
        for (ServerDescriptor server : cacheDestination.getServers())
        {
          if (server.isReplicationServer())
          {
            LOG.log(Level.INFO, "Seeding to replication server on "+
                server.getHostPort(true)+" with certificates of "+
                ConnectionUtils.getHostPort(adsCtxSource.getDirContext()));
            InitialLdapContext ctx = null;
            try
            {
              ctx = getDirContextForServer(cacheDestination, server);
              ServerDescriptor.seedAdsTrustStore(ctx,
                  adsCtxSource.getTrustedCertificates());
            }
            finally
            {
              if (ctx != null)
              {
                ctx.close();
              }
            }
          }
        }
      }
      catch (Throwable t)
      {
        LOG.log(Level.SEVERE, "Error seeding truststore: "+t, t);
        String arg = (t instanceof OpenDsException) ?
            ((OpenDsException)t).getMessageObject().toString() : t.toString();
            throw new ReplicationCliException(
                ERR_REPLICATION_ENABLE_SEEDING_TRUSTSTORE.get(
                    ConnectionUtils.getHostPort(adsCtx2.getDirContext()),
                    ConnectionUtils.getHostPort(adsCtx1.getDirContext()),
                    arg),
                    ERROR_SEEDING_TRUSTORE, t);
      }
      pointAdder.stop();
      printProgress(formatter.getSpace());
      printProgress(formatter.getFormattedDone());
      printlnProgress();

      return adsCtxSource == adsCtx1;
    }
    finally
    {
      pointAdder.stop();
    }
  }

  private InitialLdapContext getDirContextForServer(TopologyCache cache,
      ServerDescriptor server) throws NamingException
  {
    String dn = ConnectionUtils.getBindDN(
        cache.getAdsContext().getDirContext());
    String pwd = ConnectionUtils.getBindPassword(
        cache.getAdsContext().getDirContext());
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    filter.setSearchBaseDNInformation(false);
    ServerLoader loader = new ServerLoader(server.getAdsProperties(),
        dn, pwd, getTrustManager(), getConnectTimeout(),
        cache.getPreferredConnections(), filter);
    return loader.createContext();
  }

  /**
   * Returns <CODE>true</CODE> if the provided baseDN is replicated in the
   * provided server, <CODE>false</CODE> otherwise.
   * @param server the server.
   * @param baseDN the base DN.
   * @return <CODE>true</CODE> if the provided baseDN is replicated in the
   * provided server, <CODE>false</CODE> otherwise.
   */
  private boolean isBaseDNReplicated(ServerDescriptor server, String baseDN)
  {
    boolean isReplicated = false;
    for (ReplicaDescriptor replica : server.getReplicas())
    {
      if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        isReplicated = replica.isReplicated();
        break;
      }
    }
    return isReplicated;
  }

  /**
   * Returns <CODE>true</CODE> if the provided baseDN is replicated between
   * both servers, <CODE>false</CODE> otherwise.
   * @param server1 the first server.
   * @param server2 the second server.
   * @param baseDN the base DN.
   * @return <CODE>true</CODE> if the provided baseDN is replicated between
   * both servers, <CODE>false</CODE> otherwise.
   */
  private boolean isBaseDNReplicated(ServerDescriptor server1,
      ServerDescriptor server2, String baseDN)
  {
    boolean isReplicatedInBoth = false;
    ReplicaDescriptor replica1 = null;
    ReplicaDescriptor replica2;
    for (ReplicaDescriptor replica : server1.getReplicas())
    {
      if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
      {
        replica1 = replica;
        break;
      }
    }
    if (replica1 != null && replica1.isReplicated())
    {
      for (ReplicaDescriptor replica : server2.getReplicas())
      {
        if (Utils.areDnsEqual(replica.getSuffix().getDN(), baseDN))
        {
          replica2 = replica;
          if (replica2.isReplicated())
          {
            Set<String> replServers1 =
              replica1.getSuffix().getReplicationServers();
            Set<String> replServers2 =
              replica1.getSuffix().getReplicationServers();
            for (String replServer1 : replServers1)
            {
              for (String replServer2 : replServers2)
              {
                if (replServer1.equalsIgnoreCase(replServer2))
                {
                  isReplicatedInBoth = true;
                  break;
                }
              }
              if (isReplicatedInBoth)
              {
                break;
              }
            }
          }
          break;
        }
      }
    }
    return isReplicatedInBoth;
  }

  private boolean displayLogFileAtEnd(String subCommand)
  {
    String[] subCommands =
    {
      ReplicationCliArgumentParser.ENABLE_REPLICATION_SUBCMD_NAME,
      ReplicationCliArgumentParser.DISABLE_REPLICATION_SUBCMD_NAME,
      ReplicationCliArgumentParser.INITIALIZE_ALL_REPLICATION_SUBCMD_NAME,
      ReplicationCliArgumentParser.INITIALIZE_REPLICATION_SUBCMD_NAME};
    for (String sub : subCommands)
    {
      if (sub.equals(subCommand))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.  The method
   * must be called after parsing the arguments.
   * @return the timeout to be used to connect in milliseconds.  Returns
   * {@code 0} if there is no timeout.
   */
  private int getConnectTimeout()
  {
    return argParser.getConnectTimeout();
  }

  private String binDir;
  /**
   * Returns the binary/script directory.
   * @return the binary/script directory.
   */
  private String getBinaryDir()
  {
    if (binDir == null)
    {
      File f = Installation.getLocal().getBinariesDirectory();
      try
      {
        binDir = f.getCanonicalPath();
      }
      catch (Throwable t)
      {
        binDir = f.getAbsolutePath();
      }
      if (binDir.lastIndexOf(File.separatorChar) != (binDir.length() - 1))
      {
        binDir += File.separatorChar;
      }
    }

    return binDir;
  }

  /**
   * Returns the full path of the command-line for a given script name.
   * @param scriptBasicName the script basic name (with no extension).
   * @return the full path of the command-line for a given script name.
   */
  private String getCommandLinePath(String scriptBasicName)
  {
    String cmdLineName;
    if (Utilities.isWindows())
    {
      cmdLineName = getBinaryDir()+scriptBasicName+".bat";
    }
    else
    {
      cmdLineName = getBinaryDir()+scriptBasicName;
    }
    return cmdLineName;
  }
}



/**
 * Class used to compare replication servers.
 *
 */
class ReplicationServerComparator implements Comparator<ServerDescriptor>
{
  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(ServerDescriptor s1, ServerDescriptor s2)
  {
    int compare = s1.getHostName().compareTo(s2.getHostName());
    if (compare == 0)
    {
      if (s1.getReplicationServerPort() > s2.getReplicationServerPort())
      {
        compare = 1;
      }
      else if (s1.getReplicationServerPort() < s2.getReplicationServerPort())
      {
        compare = -1;
      }
    }
    return compare;
  }
}

/**
 * Class used to compare suffixes.
 *
 */
class SuffixComparator implements Comparator<SuffixDescriptor>
{
  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(SuffixDescriptor s1, SuffixDescriptor s2)
  {
    return s1.getId().compareTo(s2.getId());
  }
}

/**
 * Class used to compare servers.
 *
 */
class ServerComparator implements Comparator<ServerDescriptor>
{
  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(ServerDescriptor s1, ServerDescriptor s2)
  {
    return s1.getId().compareTo(s2.getId());
  }
}
