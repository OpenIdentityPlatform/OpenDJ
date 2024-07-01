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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2012 profiq s.r.o.
 */
package org.opends.server.tools.dsreplication;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.util.Utils.*;
import static org.opends.admin.ads.ServerDescriptor.*;
import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.admin.ads.util.PreferredConnection.*;
import static org.opends.admin.ads.util.PreferredConnection.Type.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.quicksetup.Constants.LINE_SEPARATOR;
import static org.opends.quicksetup.util.Utils.*;
import static org.opends.server.backends.task.TaskState.*;
import static org.opends.server.tools.dsreplication.ReplicationCliArgumentParser.*;
import static org.opends.server.tools.dsreplication.ReplicationCliReturnCode.*;
import static org.opends.server.types.HostPort.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg0;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.DecodingException;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.OperationsException;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.server.config.client.CryptoManagerCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationDomainCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationServerCfgClient;
import org.forgerock.opendj.server.config.client.ReplicationSynchronizationProviderCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn;
import org.forgerock.opendj.server.config.meta.ReplicationServerCfgDefn;
import org.forgerock.opendj.server.config.meta.ReplicationSynchronizationProviderCfgDefn;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContext.ADSPropertySyntax;
import org.opends.admin.ads.ADSContext.AdministratorProperty;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCache;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.TopologyCacheFilter;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.admin.ads.util.OpendsCertificateException;
import org.opends.admin.ads.util.PreferredConnection;
import org.opends.admin.ads.util.PreferredConnection.Type;
import org.opends.admin.ads.util.ServerLoader;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.util.ConfigFromConnection;
import org.opends.guitools.controlpanel.util.ConfigFromFile;
import org.opends.guitools.controlpanel.util.ControlPanelLog;
import org.opends.guitools.controlpanel.util.ProcessReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.installer.Installer;
import org.opends.quicksetup.installer.InstallerHelper;
import org.opends.quicksetup.installer.PeerNotFoundException;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.tasks.PurgeConflictsHistoricalTask;
import org.opends.server.tools.dsreplication.EnableReplicationUserData.EnableReplicationServerData;
import org.opends.server.tools.dsreplication.ReplicationCliArgumentParser.ServerArgs;
import org.opends.server.tools.tasks.TaskClient;
import org.opends.server.tools.tasks.TaskEntry;
import org.opends.server.tools.tasks.TaskScheduleInteraction;
import org.opends.server.tools.tasks.TaskScheduleUserData;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;
import org.opends.server.util.cli.PointAdder;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.TabSeparatedTablePrinter;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TablePrinter;
import com.forgerock.opendj.cli.TextTablePrinter;
import com.forgerock.opendj.cli.ValidationCallback;

/**
 * This class provides a tool that can be used to enable and disable replication
 * and also to initialize the contents of a replicated suffix with the contents
 * of another suffix.  It also allows to display the replicated status of the
 * different base DNs of the servers that are registered in the ADS.
 */
public class ReplicationCliMain extends ConsoleApplication
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = ReplicationCliMain.class.getName();
  /** Prefix for log files. */
  private static final String LOG_FILE_PREFIX = "opendj-replication-";
  /** Suffix for log files. */
  private static final String LOG_FILE_SUFFIX = ".log";

  /**
   * Property used to call the dsreplication script and ReplicationCliMain to
   * know which are the java properties to be used (those of dsreplication or
   * those of dsreplication.offline).
   */
  private static final String SCRIPT_CALL_STATUS = "org.opends.server.dsreplicationcallstatus";

  /** The value set by the dsreplication script if it is called the first time. */
  private static final String FIRST_SCRIPT_CALL = "firstcall";
  private static final LocalizableMessage EMPTY_MSG = LocalizableMessage.EMPTY;

  private boolean forceNonInteractive;

  /** Always use SSL with the administration connector. */
  private final Type connectionType = LDAPS;

  /**
   * The enumeration containing the different options we display when we ask
   * the user to provide the subcommand interactively.
   */
  private enum SubcommandChoice
  {
    /** Enable replication. */
    ENABLE(ENABLE_REPLICATION_SUBCMD_NAME, INFO_REPLICATION_ENABLE_MENU_PROMPT.get()),
    /** Disable replication. */
    DISABLE(DISABLE_REPLICATION_SUBCMD_NAME, INFO_REPLICATION_DISABLE_MENU_PROMPT.get()),
    /** Initialize replication. */
    INITIALIZE(INITIALIZE_REPLICATION_SUBCMD_NAME, INFO_REPLICATION_INITIALIZE_MENU_PROMPT.get()),
    /** Initialize All. */
    INITIALIZE_ALL(INITIALIZE_ALL_REPLICATION_SUBCMD_NAME, INFO_REPLICATION_INITIALIZE_ALL_MENU_PROMPT.get()),
    /** Pre external initialization. */
    PRE_EXTERNAL_INITIALIZATION(PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
        INFO_REPLICATION_PRE_EXTERNAL_INITIALIZATION_MENU_PROMPT.get()),
    /** Post external initialization. */
    POST_EXTERNAL_INITIALIZATION(POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
        INFO_REPLICATION_POST_EXTERNAL_INITIALIZATION_MENU_PROMPT.get()),
    /** Replication status. */
    STATUS(STATUS_REPLICATION_SUBCMD_NAME, INFO_REPLICATION_STATUS_MENU_PROMPT.get()),
    /** Replication purge historical. */
    PURGE_HISTORICAL(PURGE_HISTORICAL_SUBCMD_NAME, INFO_REPLICATION_PURGE_HISTORICAL_MENU_PROMPT.get()),
    /** Set changelog change number from another server. */
    RESET_CHANGE_NUMBER(ReplicationCliArgumentParser.RESET_CHANGE_NUMBER_SUBCMD_NAME,
        INFO_DESCRIPTION_RESET_CHANGE_NUMBER.get()),
    /** Cancel operation. */
    CANCEL(null, null);

    private final String name;
    private LocalizableMessage prompt;

    private SubcommandChoice(String name, LocalizableMessage prompt)
    {
      this.name = name;
      this.prompt = prompt;
    }

    private LocalizableMessage getPrompt()
    {
      return prompt;
    }

    private String getName()
    {
      return name;
    }

    private static SubcommandChoice fromName(String subCommandName)
    {
      SubcommandChoice[] f = values();
      for (SubcommandChoice subCommand : f)
      {
        if (subCommand.name.equals(subCommandName))
        {
          return subCommand;
        }
      }
      return null;
    }
  }

  /** Abstract some of the operations when two servers must be queried for information. */
  private interface OperationBetweenSourceAndDestinationServers
  {
    /**
     * Returns whether we should stop processing after asking the user for additional information.
     * Might connect to servers to run configuration checks.
     * @param baseDNs user specified baseDNs
     * @param source the source server
     * @param dest the destination server
     * @return whether we should continue
     */
    boolean continueAfterUserInput(Collection<DN> baseDNs, ConnectionWrapper source, ConnectionWrapper dest);

    /**
     * Confirm with the user whether the current task should continue.
     *
     * @param uData servers address and authentication parameters
     * @param connSource connection to the source server
     * @param connDestination connection to the destination server
     * @param defaultValue default yes or no
     * @return whether the current task should continue
     */
    boolean confirmOperation(SourceDestinationServerUserData uData, ConnectionWrapper connSource,
        ConnectionWrapper connDestination, boolean defaultValue);
  }

  /** The argument parser to be used. */
  private ReplicationCliArgumentParser argParser;
  private FileBasedArgument userProvidedAdminPwdFile;
  private LDAPConnectionConsoleInteraction sourceServerCI;
  private CommandBuilder firstServerCommandBuilder;
  /** The message formatter. */
  private final PlainTextProgressMessageFormatter formatter = new PlainTextProgressMessageFormatter();

  /**
   * Constructor for the ReplicationCliMain object.
   *
   * @param out the print stream to use for standard output.
   * @param err the print stream to use for standard error.
   */
  public ReplicationCliMain(PrintStream out, PrintStream err)
  {
    super(out, err);
  }

  /**
   * The main method for the replication tool.
   *
   * @param args the command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, true, System.out, System.err);
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
    return mainCLI(args, true, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the replication tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param initializeServer   Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           {@code null} if standard output is not needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   * @return The error code.
   */
  public static int mainCLI(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    try
    {
      ControlPanelLog.initLogFileHandler(
          File.createTempFile(LOG_FILE_PREFIX, LOG_FILE_SUFFIX));
    } catch (Throwable t) {
      System.err.println("Unable to initialize log");
      t.printStackTrace();
    }
    ReplicationCliMain replicationCli = new ReplicationCliMain(out, err);
    ReplicationCliReturnCode result = replicationCli.execute(args, initializeServer);
    if (result.getReturnCode() == 0)
    {
      // Delete the temp log file, in case of success.
      ControlPanelLog.closeAndDeleteLogFile();
    }
    return result.getReturnCode();
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
  private ReplicationCliReturnCode execute(String[] args, boolean initializeServer)
  {
    // Create the command-line argument parser for use with this program.
    try
    {
      createArgumenParser();
    }
    catch (ArgumentException ae)
    {
      errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      logger.error(LocalizableMessage.raw("Complete error stack:"), ae);
      return CANNOT_INITIALIZE_ARGS;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      logger.error(LocalizableMessage.raw("Complete error stack:"), ae);
      return ERROR_USER_DATA;
    }

    // If we should just display usage or version information, then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return SUCCESSFUL_NOP;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      errPrintln(e.getMessageObject());
      return CANNOT_INITIALIZE_ARGS;
    }

    // Check that the provided parameters are compatible.
    LocalizableMessageBuilder buf = new LocalizableMessageBuilder();
    argParser.validateOptions(buf);
    if (buf.length() > 0)
    {
      errPrintln(buf.toMessage());
      errPrintln(LocalizableMessage.raw(argParser.getUsage()));
      return ERROR_USER_DATA;
    }

    if (initializeServer)
    {
      DirectoryServer.bootstrapClient();

      // Bootstrap definition classes.
      try
      {
        ConfigurationFramework configFramework = ConfigurationFramework.getInstance();
        if (!configFramework.isInitialized())
        {
          configFramework.initialize();
        }
        configFramework.setIsClient(true);
      }
      catch (ConfigException ie)
      {
        errPrintln(ie.getMessageObject());
        return ERROR_INITIALIZING_ADMINISTRATION_FRAMEWORK;
      }
    }

    if (argParser.getSecureArgsList().getBindPasswordFileArg().isPresent())
    {
      try
      {
        userProvidedAdminPwdFile = FileBasedArgument.builder("adminPasswordFile")
                .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                .description(INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get())
                .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                .buildArgument();
        userProvidedAdminPwdFile.getNameToValueMap().putAll(
            argParser.getSecureArgsList().getBindPasswordFileArg().getNameToValueMap());
      }
      catch (Throwable t)
      {
        throw new IllegalStateException("Unexpected error: " + t, t);
      }
    }
    sourceServerCI = new LDAPConnectionConsoleInteraction(this, argParser.getSecureArgsList());
    sourceServerCI.setDisplayLdapIfSecureParameters(false);

    ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
    String subCommand = null;
    final SubcommandChoice subcommandChoice = getSubcommandChoice(argParser.getSubCommand());
    if (subcommandChoice != null)
    {
      subCommand = subcommandChoice.getName();
      returnValue = execute(subcommandChoice);
    }
    else if (argParser.isInteractive())
    {
      final SubcommandChoice subCommandChoice = promptForSubcommand();
      if (subCommandChoice == null || SubcommandChoice.CANCEL.equals(subCommandChoice))
      {
        return USER_CANCELLED;
      }

      subCommand = subCommandChoice.getName();
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
      errPrintln(ERR_REPLICATION_VALID_SUBCOMMAND_NOT_FOUND.get("--" + OPTION_LONG_NO_PROMPT));
      errPrintln(LocalizableMessage.raw(argParser.getUsage()));
      return ERROR_USER_DATA;
    }

    // Display the log file only if the operation is successful (when there
    // is a critical error this is already displayed).
    if (returnValue == SUCCESSFUL && displayLogFileAtEnd(subCommand))
    {
      File logFile = ControlPanelLog.getLogFile();
      if (logFile != null)
      {
        println();
        println(INFO_GENERAL_SEE_FOR_DETAILS.get(logFile.getPath()));
        println();
      }
    }

    return returnValue;
  }

  private SubcommandChoice getSubcommandChoice(SubCommand subCommand)
  {
    if (subCommand != null)
    {
      return SubcommandChoice.fromName(subCommand.getName());
    }
    return null;
  }

  private ReplicationCliReturnCode execute(SubcommandChoice subcommandChoice)
  {
    switch (subcommandChoice)
    {
    case ENABLE:
      return enableReplication();
    case DISABLE:
      return disableReplication();
    case INITIALIZE:
      return initializeReplication();
    case INITIALIZE_ALL:
      return initializeAllReplication();
    case PRE_EXTERNAL_INITIALIZATION:
      return preExternalInitialization();
    case POST_EXTERNAL_INITIALIZATION:
      return postExternalInitialization();
    case STATUS:
      return statusReplication();
    case PURGE_HISTORICAL:
      return purgeHistorical();
    case RESET_CHANGE_NUMBER:
      return resetChangeNumber();
    default:
      return SUCCESSFUL_NOP;
    }
  }

  /**
   * Prompts the user to give the Global Administrator UID.
   *
   * @param defaultValue
   *          the default value that will be proposed in the prompt message.
   * @param logger
   *          the Logger to be used to log the error message.
   * @return the Global Administrator UID as provided by the user.
   */
  private String askForAdministratorUID(String defaultValue, LocalizedLogger logger)
  {
    return ask(logger, INFO_ADMINISTRATOR_UID_PROMPT.get(), defaultValue);
  }

  /**
   * Prompts the user to give the Global Administrator password.
   *
   * @param logger
   *          the Logger to be used to log the error message.
   * @return the Global Administrator password as provided by the user.
   */
  private String askForAdministratorPwd(LocalizedLogger logger)
  {
    try
    {
      return new String(readPassword(INFO_ADMINISTRATOR_PWD_PROMPT.get()));
    }
    catch (ClientException ex)
    {
      logger.warn(LocalizableMessage.raw("Error reading input: " + ex, ex));
      return null;
    }
  }

  private String ask(LocalizedLogger logger, LocalizableMessage msgPrompt, String defaultValue)
  {
    try
    {
      return readInput(msgPrompt, defaultValue);
    }
    catch (ClientException ce)
    {
      logger.warn(LocalizableMessage.raw("Error reading input: " + ce, ce));
      return defaultValue;
    }
  }

  /**
   * Commodity method used to repeatidly ask the user to provide an integer
   * value.
   *
   * @param prompt
   *          the prompt message.
   * @param defaultValue
   *          the default value to be proposed to the user.
   * @param logger
   *          the logger where the errors will be written.
   * @return the value provided by the user.
   */
  private int askInteger(LocalizableMessage prompt, int defaultValue, LocalizedLogger logger)
  {
    int newInt = -1;
    while (newInt == -1)
    {
      try
      {
        newInt = readInteger(prompt, defaultValue);
      }
      catch (ClientException ce)
      {
        newInt = -1;
        logger.warn(LocalizableMessage.raw("Error reading input: " + ce, ce));
      }
    }
    return newInt;
  }

  /**
   * Interactively retrieves an integer value from the console.
   *
   * @param prompt
   *          The message prompt.
   * @param defaultValue
   *          The default value.
   * @return Returns the value.
   * @throws ClientException
   *           If the value could not be retrieved for some reason.
   */
  private final int readInteger(
      LocalizableMessage prompt, final int defaultValue) throws ClientException
  {
    ValidationCallback<Integer> callback = new ValidationCallback<Integer>()
    {
      @Override
      public Integer validate(ConsoleApplication app, String input)
          throws ClientException
      {
        String ninput = input.trim();
        if (ninput.length() == 0)
        {
          return defaultValue;
        }

        try
        {
          int i = Integer.parseInt(ninput);
          if (i < 1)
          {
            throw new NumberFormatException();
          }
          return i;
        }
        catch (NumberFormatException e)
        {
          // Try again...
          app.errPrintln();
          app.errPrintln(ERR_BAD_INTEGER.get(ninput));
          app.errPrintln();
          return null;
        }
      }

    };

    if (defaultValue != -1)
    {
      prompt = INFO_PROMPT_SINGLE_DEFAULT.get(prompt, defaultValue);
    }

    return readValidatedInput(prompt, callback, CONFIRMATION_MAX_TRIES);
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
    EnableReplicationUserData uData = new EnableReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          return enableReplication(uData);
        }
        else
        {
          return USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        errPrintln();
        errPrintln(getCriticalExceptionMessage(rce));
        return rce.getErrorCode();
      }
    }
    else
    {
      initializeWithArgParser(uData);
      return enableReplication(uData);
    }
  }

  /**
   * Based on the data provided in the command-line it disables replication
   * in the server.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode disableReplication()
  {
    DisableReplicationUserData uData = new DisableReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          return disableReplication(uData);
        }
        else
        {
          return USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        errPrintln();
        errPrintln(getCriticalExceptionMessage(rce));
        return rce.getErrorCode();
      }
    }
    else
    {
      initializeWithArgParser(uData);
      return disableReplication(uData);
    }
  }

  /**
   * Based on the data provided in the command-line initialize the contents
   * of the whole replication topology.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode initializeAllReplication()
  {
    InitializeAllReplicationUserData uData =
      new InitializeAllReplicationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        return initializeAllReplication(uData);
      }
      else
      {
        return USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      return initializeAllReplication(uData);
    }
  }

  /**
   * Based on the data provided in the command-line execute the pre external
   * initialization operation.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode preExternalInitialization()
  {
    PreExternalInitializationUserData uData =
      new PreExternalInitializationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequiredForPreOrPost(uData))
      {
        return preExternalInitialization(uData);
      }
      else
      {
        return USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      return preExternalInitialization(uData);
    }
  }

  /**
   * Based on the data provided in the command-line execute the post external
   * initialization operation.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode postExternalInitialization()
  {
    PostExternalInitializationUserData uData =
      new PostExternalInitializationUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequiredForPreOrPost(uData))
      {
        return postExternalInitialization(uData);
      }
      else
      {
        return USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      return postExternalInitialization(uData);
    }
  }

  /**
   * Based on the data provided in the command-line it displays replication
   * status.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode statusReplication()
  {
    StatusReplicationUserData uData = new StatusReplicationUserData();
    if (argParser.isInteractive())
    {
      try
      {
        if (promptIfRequired(uData))
        {
          return statusReplication(uData);
        }
        else
        {
          return USER_CANCELLED;
        }
      }
      catch (ReplicationCliException rce)
      {
        errPrintln();
        errPrintln(getCriticalExceptionMessage(rce));
        return rce.getErrorCode();
      }
    }
    else
    {
      initializeWithArgParser(uData);
      return statusReplication(uData);
    }
  }

  /**
   * Based on the data provided in the command-line it displays replication
   * status.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode purgeHistorical()
  {
    final PurgeHistoricalUserData uData = new PurgeHistoricalUserData();
    if (argParser.isInteractive())
    {
      if (promptIfRequired(uData))
      {
        return purgeHistorical(uData);
      }
      else
      {
        return USER_CANCELLED;
      }
    }
    else
    {
      initializeWithArgParser(uData);
      return purgeHistorical(uData);
    }
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

  private ReplicationCliReturnCode purgeHistorical(PurgeHistoricalUserData uData)
  {
      return uData.isOnline()
          ? purgeHistoricalRemotely(uData)
          : purgeHistoricalLocally(uData);
  }

  private ReplicationCliReturnCode purgeHistoricalLocally(PurgeHistoricalUserData uData)
  {
    List<DN> baseDNs = uData.getBaseDNs();
    checkSuffixesForLocalPurgeHistorical(baseDNs, false);
    if (baseDNs.isEmpty())
    {
      return HISTORICAL_CANNOT_BE_PURGED_ON_BASEDN;
    }
    uData.setBaseDNs(baseDNs);
    if (mustPrintCommandBuilder())
    {
      printNewCommandBuilder(PURGE_HISTORICAL_SUBCMD_NAME, uData);
    }

    try
    {
      return purgeHistoricalLocallyTask(uData);
    }
    catch (ReplicationCliException rce)
    {
      errPrintln();
      errPrintln(getCriticalExceptionMessage(rce));
      logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
      return rce.getErrorCode();
    }
  }

  private List<DN> toDNs(List<String> baseDNs)
  {
    final List<DN> results = new ArrayList<>(baseDNs.size());
    for (String dn : baseDNs)
    {
      results.add(DN.valueOf(dn));
    }
    return results;
  }

  private void printPurgeProgressMessage(PurgeHistoricalUserData uData)
  {
    String separator = formatter.getLineBreak().toString() + formatter.getTab();
    println();
    LocalizableMessage msg = formatter.getFormattedProgress(
        INFO_PROGRESS_PURGE_HISTORICAL.get(separator, joinAsString(separator, uData.getBaseDNs())));
    print(msg);
    println();
  }

  private ReplicationCliReturnCode purgeHistoricalLocallyTask(PurgeHistoricalUserData uData)
      throws ReplicationCliException
  {
    ReplicationCliReturnCode returnCode = SUCCESSFUL;
    if (isFirstCallFromScript())
    {
      // Launch the process: launch dsreplication in non-interactive mode with
      // the recursive property set.
      ArrayList<String> args = new ArrayList<>();
      args.add(getCommandLinePath(getCommandName()));
      args.add(PURGE_HISTORICAL_SUBCMD_NAME);
      args.add("--"+argParser.noPromptArg.getLongIdentifier());
      args.add("--"+argParser.maximumDurationArg.getLongIdentifier());
      args.add(String.valueOf(uData.getMaximumDuration()));
      for (DN baseDN : uData.getBaseDNs())
      {
        args.add("--"+argParser.baseDNsArg.getLongIdentifier());
        args.add(baseDN.toString());
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
        LocalizableMessage msg = ERR_LAUNCHING_PURGE_HISTORICAL.get();
        ReplicationCliReturnCode code = ERROR_LAUNCHING_PURGE_HISTORICAL;
        throw new ReplicationCliException(
            getThrowableMsg(msg, e), code, e);
      }
    }
    else
    {
      printPurgeProgressMessage(uData);
      LocalPurgeHistorical localPurgeHistorical =
        new LocalPurgeHistorical(uData, this, formatter, argParser.getConfigFile());
      returnCode = localPurgeHistorical.execute();

      if (returnCode == SUCCESSFUL)
      {
        printSuccessMessage(uData, null);
      }
    }
    return returnCode;
  }

  private ConnectionWrapper createConnectionInteracting(LDAPConnectionConsoleInteraction ci) throws ClientException
  {
    return createConnectionInteracting(ci, isInteractive() && ci.isTrustStoreInMemory());
  }

  private OpendsCertificateException getCertificateRootException(Throwable t)
  {
    while (t != null)
    {
      t = t.getCause();
      if (t instanceof OpendsCertificateException)
      {
        return (OpendsCertificateException) t;
      }
    }
    return null;
  }

  /**
   * Creates a connection interacting with the user if the application is interactive.
   *
   * @param ci
   *          the LDAPConnectionConsoleInteraction object that is assumed to have been already run.
   * @param promptForCertificate
   *          whether we should prompt for the certificate or not.
   * @return the connection or {@code null} if the user did not accept to trust the certificates.
   * @throws ClientException
   *           if there was an error establishing the connection.
   */
  private ConnectionWrapper createConnectionInteracting(LDAPConnectionConsoleInteraction ci,
      boolean promptForCertificate) throws ClientException
  {
    // Interact with the user though the console to get LDAP connection information
    final HostPort hostPort = getHostPort(ci);
    if (ci.useSSL())
    {
      while (true)
      {
        try
        {
          return newConnectionWrapper(ci, LDAPS, ci.getConnectTimeout());
        }
        catch (LdapException e)
        {
          if (promptForCertificate)
          {
            OpendsCertificateException oce = getCertificateRootException(e);
            if (oce != null)
            {
              String authType = getAuthType(ci.getTrustManager());
              if (ci.checkServerCertificate(oce.getChain(), authType, hostPort.getHost()))
              {
                // User trusts the certificate, try to connect again.
                continue;
              }
              else
              {
                // Assume user canceled.
                return null;
              }
            }
          }
          if (e.getCause() != null)
          {
            if (!isInteractive()
                && !ci.isTrustAll()
                && (getCertificateRootException(e) != null
                  || e.getCause() instanceof SSLHandshakeException))
            {
              LocalizableMessage message =
                  ERR_FAILED_TO_CONNECT_NOT_TRUSTED.get(hostPort.getHost(), hostPort.getPort());
              throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
            }
            if (e.getCause() instanceof SSLException)
            {
              LocalizableMessage message =
                  ERR_FAILED_TO_CONNECT_WRONG_PORT.get(hostPort.getHost(), hostPort.getPort());
              throw new ClientException(
                  ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
            }
          }
          LocalizableMessage message = getMessageForException(e, hostPort.toString());
          throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
        }
      }
    }
    else if (ci.useStartTLS())
    {
      while (true)
      {
        try
        {
          return newConnectionWrapper(ci, START_TLS, CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT);
        }
        catch (LdapException e)
        {
          if (!promptForCertificate)
          {
            throw failedToConnect(hostPort);
          }
          OpendsCertificateException oce = getCertificateRootException(e);
          if (oce == null)
          {
            throw failedToConnect(hostPort);
          }
          String authType = getAuthType(ci.getTrustManager());
          if (ci.checkServerCertificate(oce.getChain(), authType, hostPort.getHost()))
          {
            // User trusts the certificate, try to connect again.
            continue;
          }
          else
          {
            // Assume user cancelled.
            return null;
          }
        }
      }
    }
    else
    {
      try
      {
        return newConnectionWrapper(ci, LDAP, CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT);
      }
      catch (LdapException e)
      {
        throw failedToConnect(hostPort);
      }
    }
  }

  private ConnectionWrapper newConnectionWrapper(
      LDAPConnectionConsoleInteraction ci, Type connType, int connectTimeout) throws LdapException
  {
    return new ConnectionWrapper(getHostPort(ci), connType, ci.getBindDN(), ci.getBindPassword(),
        connectTimeout, ci.getTrustManager(), ci.getKeyManager());
  }

  private HostPort getHostPort(LDAPConnectionConsoleInteraction ci)
  {
    return new HostPort(ci.getHostName(), ci.getPortNumber());
  }

  private String getAuthType(TrustManager trustManager)
  {
    if (trustManager instanceof ApplicationTrustManager)
    {
      return ((ApplicationTrustManager) trustManager).getLastRefusedAuthType();
    }
    return null;
  }

  private ClientException failedToConnect(HostPort hostPort)
  {
    return new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
        ERR_FAILED_TO_CONNECT.get(hostPort.getHost(), hostPort.getPort()));
  }

  private ReplicationCliReturnCode purgeHistoricalRemotely(PurgeHistoricalUserData uData)
  {
    ConnectionWrapper conn = createAdministrativeConnection(uData);
    if (conn == null)
    {
      return ERROR_CONNECTING;
    }

    try
    {
      List<DN> baseDNs = uData.getBaseDNs();
      checkSuffixesForPurgeHistorical(baseDNs, conn, false);
      if (baseDNs.isEmpty())
      {
        return HISTORICAL_CANNOT_BE_PURGED_ON_BASEDN;
      }
      uData.setBaseDNs(baseDNs);
      if (mustPrintCommandBuilder())
      {
        printNewCommandBuilder(PURGE_HISTORICAL_SUBCMD_NAME, uData);
      }

      return purgeHistoricalRemoteTask(conn, uData);
    }
    catch (ReplicationCliException rce)
    {
      errPrintln();
      errPrintln(getCriticalExceptionMessage(rce));
      logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
      return rce.getErrorCode();
    }
    finally
    {
      close(conn);
    }
  }

  private ReplicationCliReturnCode resetChangeNumber()
  {
    final SourceDestinationServerUserData uData = new SourceDestinationServerUserData();

    if (!argParser.isInteractive())
    {
      initializeWithArgParser(uData);
      return resetChangeNumber(uData);
    }
    OperationBetweenSourceAndDestinationServers
        resetChangeNumberOperations = new OperationBetweenSourceAndDestinationServers()
    {
      @Override
      public boolean continueAfterUserInput(Collection<DN> baseDNs, ConnectionWrapper source, ConnectionWrapper dest)
      {
        TopologyCacheFilter filter = new TopologyCacheFilter();
        filter.setSearchMonitoringInformation(false);

        if (!argParser.resetChangeNumber.isPresent())
        {
          String cn = getNewestChangeNumber(source);
          if (cn.isEmpty())
          {
            return false;
          }
          argParser.setResetChangeNumber(
              ask(logger, INFO_RESET_CHANGE_NUMBER_TO.get(uData.getSource(), uData.getDestination()), cn));
        }
        return true;
      }

      @Override
      public boolean confirmOperation(SourceDestinationServerUserData uData, ConnectionWrapper connSource,
          ConnectionWrapper connDestination, boolean defaultValue)
      {
        LocalizableMessage promptMsg = INFO_RESET_CHANGE_NUMBER_CONFIRM_RESET.get(uData.getDestinationHostPort());
        return askConfirmation(promptMsg, defaultValue);
      }
    };

    return promptIfRequired(uData, resetChangeNumberOperations) ? resetChangeNumber(uData) : USER_CANCELLED;
  }

  private ReplicationCliReturnCode resetChangeNumber(SourceDestinationServerUserData uData)
  {
    ConnectionWrapper connSource = createAdministrativeConnection(uData, uData.getSource());
    ConnectionWrapper connDest = createAdministrativeConnection(uData, uData.getDestination());
    if (!getCommonSuffixes(connSource, connDest, SuffixRelationType.NOT_FULLY_REPLICATED).isEmpty())
    {
      errPrintln(ERROR_RESET_CHANGE_NUMBER_SERVERS_BASEDNS_DIFFER.get(uData.getSourceHostPort(),
          uData.getDestinationHostPort()));
      return ERROR_RESET_CHANGE_NUMBER_BASEDNS_SHOULD_EQUAL;
    }
    if (mustPrintCommandBuilder())
    {
      printNewCommandBuilder(RESET_CHANGE_NUMBER_SUBCMD_NAME, uData);
    }
    try
    {
      String newStartCN;
      if (argParser.resetChangeNumber.isPresent())
      {
        newStartCN = String.valueOf(argParser.getResetChangeNumber());
      }
      else
      {
        newStartCN = getNewestChangeNumber(connSource);
        if (newStartCN.isEmpty())
        {
          return ERROR_UNKNOWN_CHANGE_NUMBER;
        }
        argParser.setResetChangeNumber(newStartCN);
      }

      SearchResultEntry sr;
      SearchRequest request=newSearchRequest("cn=changelog", WHOLE_SUBTREE, "(changeNumber=" + newStartCN + ")",
          "changeNumber",
          "replicationCSN",
          "targetDN");
      try (ConnectionEntryReader entryReader = connSource.getConnection().search(request))
      {
        if (!entryReader.hasNext())
        {
          errPrintln(ERROR_RESET_CHANGE_NUMBER_UNKNOWN_NUMBER.get(newStartCN, uData.getSourceHostPort()));
          return ERROR_UNKNOWN_CHANGE_NUMBER;
        }
        sr = entryReader.readEntry();
      }
      String newStartCSN = sr.parseAttribute("replicationCSN").asString();
      if (newStartCSN == null)
      {
        errPrintln(ERROR_RESET_CHANGE_NUMBER_NO_CSN_FOUND.get(newStartCN, uData.getSourceHostPort()));
        return ERROR_RESET_CHANGE_NUMBER_NO_CSN;
      }
      DN targetDN;
      DN targetBaseDN = DN.rootDN();
      try
      {
        targetDN = sr.parseAttribute("targetDN").asDN();
        for (DN dn : getCommonSuffixes(connSource, connDest, SuffixRelationType.REPLICATED))
        {
          if (targetDN.isSubordinateOrEqualTo(dn) && dn.isSubordinateOrEqualTo(targetBaseDN))
          {
            targetBaseDN = dn;
          }
        }
      }
      catch (LocalizedIllegalArgumentException e)
      {
        errPrintln(ERROR_RESET_CHANGE_NUMBER_EXCEPTION.get(e.getLocalizedMessage()));
        return ERROR_RESET_CHANGE_NUMBER_PROBLEM;
      }
      if (targetBaseDN.isRootDN())
      {
        errPrintln(ERROR_RESET_CHANGE_NUMBER_NO_BASEDN.get(newStartCN, targetDN, newStartCSN));
        return ERROR_RESET_CHANGE_NUMBER_UNKNOWN_BASEDN;
      }
      logger.info(INFO_RESET_CHANGE_NUMBER_INFO.get(uData.getDestinationHostPort(),
          newStartCN, newStartCSN, targetBaseDN.toString()));
      Map<String, String> taskAttrs = new TreeMap<>();
      taskAttrs.put("ds-task-reset-change-number-to", newStartCN);
      taskAttrs.put("ds-task-reset-change-number-csn", newStartCSN);
      taskAttrs.put("ds-task-reset-change-number-base-dn", targetBaseDN.toString());
      String taskDN = createServerTask(connDest,
          "ds-task-reset-change-number", "org.opends.server.tasks.ResetChangeNumberTask", "dsreplication-reset-cn",
          taskAttrs);
      waitUntilResetChangeNumberTaskEnds(connDest, taskDN);
      return SUCCESSFUL;
    }
    catch (ReplicationCliException | IOException | NullPointerException e)
    {
      errPrintln(ERROR_RESET_CHANGE_NUMBER_EXCEPTION.get(e.getLocalizedMessage()));
      return ERROR_RESET_CHANGE_NUMBER_PROBLEM;
    }
  }

  private String getNewestChangeNumber(ConnectionWrapper conn)
  {
    try
    {
      SearchResultEntry sr =
          conn.getConnection().searchSingleEntry("", BASE_OBJECT, "objectclass=*", "lastChangeNumber");
      return sr.parseAttribute("lastChangeNumber").asString();
    }
    catch (LdapException e)
    {
      errPrintln(ERROR_RESET_CHANGE_NUMBER_EXCEPTION.get(e.getLocalizedMessage()));
      return "";
    }
  }

  private void waitUntilResetChangeNumberTaskEnds(ConnectionWrapper conn, String taskDN)
      throws ReplicationCliException
  {
    String lastLogMsg = null;
    while (true)
    {
      sleepCatchInterrupt(500);
      try
      {
        SearchResultEntry sr = getLastSearchResult(conn, taskDN, "ds-task-log-message", "ds-task-state");
        String logMsg = firstValueAsString(sr, "ds-task-log-message");
        if (logMsg != null && !logMsg.equals(lastLogMsg))
        {
          logger.info(LocalizableMessage.raw(logMsg));
          lastLogMsg = logMsg;
        }

        String state = firstValueAsString(sr, "ds-task-state");
        TaskState taskState = TaskState.fromString(state);
        if (TaskState.isDone(taskState) || taskState == STOPPED_BY_ERROR)
        {
          LocalizableMessage errorMsg = ERR_UNEXPECTED_DURING_TASK_WITH_LOG.get(lastLogMsg, state, conn.getHostPort());
          if (taskState == COMPLETED_WITH_ERRORS)
          {
            logger.warn(LocalizableMessage.raw("Completed with error: " + errorMsg));
            errPrintln(errorMsg);
          }
          else if (!TaskState.isSuccessful(taskState) || taskState == STOPPED_BY_ERROR)
          {
            logger.warn(LocalizableMessage.raw("Error: " + errorMsg));
            throw new ReplicationCliException(errorMsg, ERROR_LAUNCHING_RESET_CHANGE_NUMBER, null);
          }
          else
          {
            print(INFO_RESET_CHANGE_NUMBER_TASK_FINISHED.get());
            println();
          }
          return;
        }
      }
      catch (EntryNotFoundException x)
      {
        return;
      }
      catch (IOException e)
      {
        throw new ReplicationCliException(getThrowableMsg(ERR_READING_SERVER_TASK_PROGRESS.get(), e),
            ERROR_CONNECTING, e);
      }
    }
  }

  private ConnectionWrapper createAdministrativeConnection(MonoServerReplicationUserData uData)
  {
    return createAdministrativeConnection(uData, getAdministratorDN(uData.getAdminUid()));
  }

  private ConnectionWrapper createAdministrativeConnection(MonoServerReplicationUserData uData, final DN bindDn)
  {
    try
    {
      return new ConnectionWrapper(uData.getHostPort(), connectionType,
          bindDn, uData.getAdminPwd(), getConnectTimeout(), getTrustManager(sourceServerCI));
    }
    catch (LdapException e)
    {
      errPrintln();
      errPrintln(getMessageForException(e, uData.getHostPort().toString()));
      logger.error(LocalizableMessage.raw("Error when creating connection for:" + uData.getHostPort()), e);
      return null;
    }
  }

  private void printSuccessMessage(PurgeHistoricalUserData uData, String taskID)
  {
    println();
    if (!uData.isOnline())
    {
      print(
          INFO_PROGRESS_PURGE_HISTORICAL_FINISHED_PROCEDURE.get());
    }
    else if (uData.getTaskSchedule().isStartNow())
    {
      print(INFO_TASK_TOOL_TASK_SUCESSFULL.get(
          INFO_PURGE_HISTORICAL_TASK_NAME.get(),
          taskID));
    }
    else if (uData.getTaskSchedule().getStartDate() != null)
    {
      print(INFO_TASK_TOOL_TASK_SCHEDULED_FUTURE.get(
          INFO_PURGE_HISTORICAL_TASK_NAME.get(),
          taskID,
          StaticUtils.formatDateTimeString(
              uData.getTaskSchedule().getStartDate())));
    }
    else
    {
      print(INFO_TASK_TOOL_RECURRING_TASK_SCHEDULED.get(
          INFO_PURGE_HISTORICAL_TASK_NAME.get(),
          taskID));
    }

    println();
  }

  /**
   * Launches the purge historical operation using the provided connection.
   *
   * @param conn
   *          the connection to the server.
   * @throws ReplicationCliException
   *           if there is an error performing the operation.
   */
  private ReplicationCliReturnCode purgeHistoricalRemoteTask(ConnectionWrapper conn, PurgeHistoricalUserData uData)
  throws ReplicationCliException
  {
    printPurgeProgressMessage(uData);
    ReplicationCliReturnCode returnCode = SUCCESSFUL;
    boolean taskCreated = false;
    boolean isOver = false;
    String dn = null;
    String taskID = null;
    while (!taskCreated)
    {
      List<RawAttribute> rawAttrs = TaskClient.getTaskAttributes(new PurgeHistoricalScheduleInformation(uData));
      dn = TaskClient.getTaskDN(rawAttrs);
      taskID = TaskClient.getTaskID(rawAttrs);

      AddRequest request = newAddRequest(dn);
      for (RawAttribute rawAttr : rawAttrs)
      {
        request.addAttribute(new LinkedAttribute(rawAttr.getAttributeType(), rawAttr.getValues()));
      }

      try
      {
        conn.getConnection().add(request);
        taskCreated = true;
        logger.info(LocalizableMessage.raw("created task entry: " + request));
      }
      catch (LdapException e)
      {
        logger.error(LocalizableMessage.raw("Error creating task " + request, e));
        LocalizableMessage msg = ERR_LAUNCHING_PURGE_HISTORICAL.get();
        ReplicationCliReturnCode code = ERROR_LAUNCHING_PURGE_HISTORICAL;
        throw new ReplicationCliException(getThrowableMsg(msg, e), code, e);
      }
    }

    // Polling only makes sense when we are recurrently scheduling a task
    // or the task is being executed now.
    String lastLogMsg = null;
    while (!isOver && uData.getTaskSchedule().getStartDate() == null)
    {
      sleepCatchInterrupt(500);
      try
      {
        SearchResultEntry sr = getFirstSearchResult(conn, dn,
            "ds-task-log-message",
            "ds-task-state",
            "ds-task-purge-conflicts-historical-purged-values-count",
            "ds-task-purge-conflicts-historical-purge-completed-in-time",
            "ds-task-purge-conflicts-historical-purge-completed-in-time",
            "ds-task-purge-conflicts-historical-last-purged-changenumber");
        String logMsg = firstValueAsString(sr, "ds-task-log-message");
        if (logMsg != null && !logMsg.equals(lastLogMsg))
        {
          logger.info(LocalizableMessage.raw(logMsg));
          lastLogMsg = logMsg;
        }

        String state = firstValueAsString(sr, "ds-task-state");
        TaskState taskState = TaskState.fromString(state);
        if (TaskState.isDone(taskState) || taskState == STOPPED_BY_ERROR)
        {
          isOver = true;
          LocalizableMessage errorMsg = getPurgeErrorMsg(lastLogMsg, state, conn);

          if (taskState == COMPLETED_WITH_ERRORS)
          {
            logger.warn(LocalizableMessage.raw("Completed with error: "+errorMsg));
            errPrintln(errorMsg);
          }
          else if (!TaskState.isSuccessful(taskState) || taskState == STOPPED_BY_ERROR)
          {
            logger.warn(LocalizableMessage.raw("Error: "+errorMsg));
            ReplicationCliReturnCode code = ERROR_LAUNCHING_PURGE_HISTORICAL;
            throw new ReplicationCliException(errorMsg, code, null);
          }
        }
      }
      catch (EntryNotFoundException x)
      {
        isOver = true;
      }
      catch (LdapException e)
      {
        LocalizableMessage msg = ERR_READING_SERVER_TASK_PROGRESS.get();
        throw new ReplicationCliException(getThrowableMsg(msg, e), ERROR_CONNECTING, e);
      }
    }

    if (returnCode == SUCCESSFUL)
    {
      printSuccessMessage(uData, taskID);
    }
    return returnCode;
  }

  private SearchResultEntry getFirstSearchResult(ConnectionWrapper conn, String dn, String... returnedAttributes)
      throws LdapException
  {
    SearchRequest request = newSearchRequest(dn, BASE_OBJECT, "(objectclass=*)", returnedAttributes).setSizeLimit(1);
    return conn.getConnection().searchSingleEntry(request);
  }

  private LocalizableMessage getPurgeErrorMsg(String lastLogMsg, String state, ConnectionWrapper conn)
  {
    HostPort hostPort = conn.getHostPort();
    if (lastLogMsg != null)
    {
      return ERR_UNEXPECTED_DURING_TASK_WITH_LOG.get(lastLogMsg, state, hostPort);
    }
    return ERR_UNEXPECTED_DURING_TASK_NO_LOG.get(state, hostPort);
  }

  /**
   * Checks that historical can actually be purged in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated with the base DNs that the user provided interactively.
   * @param conn connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be purged.
   */
  private void checkSuffixesForPurgeHistorical(Collection<DN> suffixes, ConnectionWrapper conn, boolean interactive)
  {
    checkSuffixesForPurgeHistorical(suffixes, getReplicas(conn), interactive);
  }

  /**
   * Checks that historical can actually be purged in the provided baseDNs
   * for the local server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated with the base DNs that the user provided interactively.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be purged.
   */
  private void checkSuffixesForLocalPurgeHistorical(Collection<DN> suffixes, boolean interactive)
  {
    checkSuffixesForPurgeHistorical(suffixes, getLocalReplicas(), interactive);
  }

  private Collection<ReplicaDescriptor> getLocalReplicas()
  {
    Collection<ReplicaDescriptor> replicas = new ArrayList<>();
    ConfigFromFile configFromFile = new ConfigFromFile();
    configFromFile.readConfiguration();
    Collection<BackendDescriptor> backends = configFromFile.getBackends();
    for (BackendDescriptor backend : backends)
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        ReplicaDescriptor replica = new ReplicaDescriptor();
        replica.setServerId(baseDN.getType() == BaseDNDescriptor.Type.REPLICATED ? baseDN.getReplicaID() : -1);
        replica.setBackendId(backend.getBackendID());
        replica.setSuffix(new SuffixDescriptor(baseDN.getDn(), replica));

        replicas.add(replica);
      }
    }
    return replicas;
  }

  private void checkSuffixesForPurgeHistorical(Collection<DN> suffixes, Collection<ReplicaDescriptor> replicas,
      boolean interactive)
  {
    TreeSet<DN> availableSuffixes = new TreeSet<>();
    TreeSet<DN> notReplicatedSuffixes = new TreeSet<>();

    partitionReplicasByReplicated(replicas, availableSuffixes, notReplicatedSuffixes);

    if (availableSuffixes.isEmpty())
    {
      errPrintln();
      errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_PURGE_HISTORICAL.get());
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<DN> notFound = new TreeSet<>();
      TreeSet<DN> alreadyNotReplicated = new TreeSet<>();
      determineSuffixesNotFoundAndAlreadyNotReplicated(
          suffixes, availableSuffixes, notReplicatedSuffixes, notFound, alreadyNotReplicated);
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (!notFound.isEmpty())
      {
        errPrintln();
        errPrintln(ERR_REPLICATION_PURGE_SUFFIXES_NOT_FOUND.get(toSingleLine(notFound)));
      }
      if (interactive)
      {
        askConfirmations(suffixes, availableSuffixes,
            ERR_NO_SUFFIXES_AVAILABLE_TO_PURGE_HISTORICAL,
            ERR_NO_SUFFIXES_SELECTED_TO_PURGE_HISTORICAL,
            INFO_REPLICATION_PURGE_HISTORICAL_PROMPT);
      }
    }
  }

  private void partitionReplicasByReplicated(Collection<ReplicaDescriptor> replicas,
      Set<DN> replicatedSuffixes, Set<DN> notReplicatedSuffixes)
  {
    for (ReplicaDescriptor rep : replicas)
    {
      DN dn = rep.getSuffix().getDN();
      if (rep.isReplicated())
      {
        replicatedSuffixes.add(dn);
      }
      else
      {
        notReplicatedSuffixes.add(dn);
      }
    }
  }

  private void determineSuffixesNotFoundAndAlreadyNotReplicated(Collection<DN> suffixes,
      Set<DN> availableSuffixes, Set<DN> notReplicatedSuffixes, Set<DN> notFound, Set<DN> alreadyNotReplicated)
  {
    for (DN dn : suffixes)
    {
      if (!availableSuffixes.contains(dn))
      {
        if (notReplicatedSuffixes.contains(dn))
        {
          alreadyNotReplicated.add(dn);
        }
        else
        {
          notFound.add(dn);
        }
      }
    }
  }

  private void askConfirmations(Collection<DN> suffixes,
      Collection<DN> availableSuffixes, Arg0 noSuffixAvailableMsg,
      Arg0 noSuffixSelectedMsg, Arg1<Object> confirmationMsgPromt)
  {
    if (containsOnlySchemaOrAdminSuffix(availableSuffixes))
    {
      // In interactive mode we do not propose to manage the administration suffix.
      errPrintln();
      errPrintln(noSuffixAvailableMsg.get());
      return;
    }

    while (suffixes.isEmpty())
    {
      errPrintln();
      errPrintln(noSuffixSelectedMsg.get());
      boolean confirmationLimitReached = askConfirmations(confirmationMsgPromt, availableSuffixes, suffixes);
      if (confirmationLimitReached)
      {
        suffixes.clear();
        break;
      }
    }
  }

  private boolean containsOnlySchemaOrAdminSuffix(Collection<DN> suffixes)
  {
    for (DN suffix : suffixes)
    {
      if (!isSchemaOrInternalAdminSuffix(suffix))
      {
        return false;
      }
    }
    return true;
  }

  private boolean isSchemaOrInternalAdminSuffix(DN suffix)
  {
    return suffix.equals(ADSContext.getAdministrationSuffixDN())
        || suffix.equals(Constants.SCHEMA_DN)
        || suffix.equals(Constants.REPLICATION_CHANGES_DN);
  }

  /**
   * Based on the data provided in the command-line it initializes replication
   * between two servers.
   * @return the error code if the operation failed and SUCCESSFUL if it was
   * successful.
   */
  private ReplicationCliReturnCode initializeReplication()
  {
    SourceDestinationServerUserData uData = new SourceDestinationServerUserData();
    if (!argParser.isInteractive())
    {
      initializeWithArgParser(uData);
      return initializeReplication(uData);
    }

    OperationBetweenSourceAndDestinationServers
        initializeReplicationOperations = new OperationBetweenSourceAndDestinationServers()
    {
      @Override
      public boolean continueAfterUserInput(Collection<DN> baseDNs, ConnectionWrapper source, ConnectionWrapper dest)
      {
        checkSuffixesForInitializeReplication(baseDNs, source, dest, true);
        return !baseDNs.isEmpty();
      }

      @Override
      public boolean confirmOperation(SourceDestinationServerUserData uData, ConnectionWrapper connSource,
          ConnectionWrapper connDestination, boolean defaultValue)
      {
        return askConfirmation(getInitializeReplicationPrompt(uData, connSource, connDestination), defaultValue);
      }
    };
    return promptIfRequired(uData, initializeReplicationOperations) ? initializeReplication(uData) : USER_CANCELLED;
  }

  /**
   * Updates the contents of the provided PurgeHistoricalUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return {@code true} if the object was successfully updated and
   * {@code false} if the user canceled the operation.
   */
  private boolean promptIfRequired(PurgeHistoricalUserData uData)
  {
    ConnectionWrapper conn = getConnection(uData);
    if (conn == null)
    {
      return false;
    }

    try
    {
      /* Prompt for maximum duration */
      int maximumDuration = argParser.getMaximumDuration();
      if (!argParser.maximumDurationArg.isPresent())
      {
        println();
        maximumDuration = askInteger(INFO_REPLICATION_PURGE_HISTORICAL_MAXIMUM_DURATION_PROMPT.get(),
            getDefaultValue(argParser.maximumDurationArg), logger);
      }
      uData.setMaximumDuration(maximumDuration);

      List<DN> suffixes = toDNs(argParser.getBaseDNs());
      if (uData.isOnline())
      {
        checkSuffixesForPurgeHistorical(suffixes, conn, true);
      }
      else
      {
        checkSuffixesForLocalPurgeHistorical(suffixes, true);
      }
      if (suffixes.isEmpty())
      {
        return false;
      }
      uData.setBaseDNs(suffixes);

      if (uData.isOnline())
      {
        List<? extends TaskEntry> taskEntries = getAvailableTaskEntries(conn);

        TaskScheduleInteraction interaction =
            new TaskScheduleInteraction(uData.getTaskSchedule(), argParser.taskArgs, this,
                INFO_PURGE_HISTORICAL_TASK_NAME.get());
        interaction.setFormatter(formatter);
        interaction.setTaskEntries(taskEntries);
        try
        {
          interaction.run();
        }
        catch (ClientException ce)
        {
          errPrintln(ce.getMessageObject());
          return false;
        }
      }
      return true;
    }
    finally
    {
      close(conn);
    }
  }

  private ConnectionWrapper getConnection(PurgeHistoricalUserData uData)
  {
    boolean serverRunning = Utilities.isServerRunning(Installation.getLocal().getInstanceDirectory());

    boolean firstTry = true;
    while (true)
    {
      boolean promptForConnection = firstTry && argParser.connectionArgumentsPresent();
      if (!promptForConnection && !serverRunning)
      {
        try
        {
          println();
          promptForConnection = !askConfirmation(INFO_REPLICATION_PURGE_HISTORICAL_LOCAL_PROMPT.get(), true, logger);
        }
        catch (ClientException ce)
        {
          errPrintln(ce.getMessageObject());
        }

        if (!promptForConnection)
        {
          uData.setOnline(false);
          return null;
        }
      }

      try
      {
        sourceServerCI.run();

        ConnectionWrapper conn = createConnectionInteracting(sourceServerCI);
        if (conn != null)
        {
          uData.setOnline(true);
          uData.setHostPort(getHostPort(sourceServerCI));
          uData.setAdminUid(sourceServerCI.getAdministratorUID());
          uData.setAdminPwd(sourceServerCI.getBindPassword());
        }
        return conn;
      }
      catch (ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Client exception " + ce));
        errPrintln();
        errPrintln(ce.getMessageObject());
        errPrintln();
        sourceServerCI.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        logger.warn(LocalizableMessage.raw("Argument exception " + ae));
        argParser.displayMessageAndUsageReference(getErrStream(), ae.getMessageObject());
        return null;
      }
      firstTry = false;
    }
  }

  private List<? extends TaskEntry> getAvailableTaskEntries(ConnectionWrapper conn)
  {
    List<TaskEntry> taskEntries = new ArrayList<>();
    List<Exception> exceptions = new ArrayList<>();
    ConfigFromConnection cfg = new ConfigFromConnection();
    cfg.updateTaskInformation(conn, exceptions, taskEntries);
    for (Exception ode : exceptions)
    {
      logger.warn(LocalizableMessage.raw("Error retrieving task entries: "+ode, ode));
    }
    return taskEntries;
  }

  /**
   * Updates the contents of the provided EnableReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return {@code true} if the object was successfully updated and
   * {@code false} if the user cancelled the operation.
   * @throws ReplicationCliException if a critical error occurs reading the ADS.
   */
  private boolean promptIfRequired(EnableReplicationUserData uData) throws ReplicationCliException
  {
    sourceServerCI.setUseAdminOrBindDn(true);

    ConnectionWrapper conn1 = null;
    ConnectionWrapper conn2 = null;
    try
    {
      String adminPwd = argParser.getBindPasswordAdmin();
      String adminUid = argParser.getAdministratorUID();

      /* Try to connect to the first server. */
      initializeGlobalArguments(sourceServerCI, argParser.server1, adminPwd, adminUid);

      conn1 = initializeFirstConnection(INFO_REPLICATION_ENABLE_HOST1_CONNECTION_PARAMETERS.get());
      if (conn1 == null)
      {
        return false;
      }

      if (sourceServerCI.getProvidedAdminUID() != null)
      {
        adminUid = sourceServerCI.getProvidedAdminUID();
        if (sourceServerCI.getProvidedBindDN() == null)
        {
          // If the explicit bind DN is not null, the password corresponds
          // to that bind DN. We are in the case where the user provides
          // bind DN on first server and admin UID globally.
          adminPwd = sourceServerCI.getBindPassword();
        }
      }

      setConnectionDetails(uData.getServer1(), sourceServerCI);
      if (!setReplicationDetails(uData.getServer1(), sourceServerCI, argParser.server1, conn1, null))
      {
        return false;
      }

      // If the server contains an ADS. Try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // enableReplication(EnableReplicationUserData) method. Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      AtomicReference<ConnectionWrapper> aux = new AtomicReference<>(conn1);
      if (!loadADSAndAcceptCertificates(sourceServerCI, aux, uData, true))
      {
        return false;
      }
      conn1 = aux.get();

      boolean administratorDefined = false;
      administratorDefined |= hasAdministrator(conn1);
      if (uData.getAdminPwd() != null)
      {
        adminPwd = uData.getAdminPwd();
      }
      firstServerCommandBuilder = new CommandBuilder();
      if (mustPrintCommandBuilder())
      {
        firstServerCommandBuilder.append(sourceServerCI.getCommandBuilder());
      }

      /* Prompt for information on the second server. */
      LDAPConnectionConsoleInteraction destinationServerCI =
          new LDAPConnectionConsoleInteraction(this, argParser.getSecureArgsList());
      boolean doNotDisplayFirstError =
          initializeGlobalArguments(destinationServerCI, argParser.server2, adminPwd, adminUid);
      destinationServerCI.setUseAdminOrBindDn(true);

      conn2 = initializeConnection2(destinationServerCI, sourceServerCI, doNotDisplayFirstError);
      if (conn2 == null)
      {
        return false;
      }

      if (destinationServerCI.getProvidedAdminUID() != null)
      {
        adminUid = destinationServerCI.getProvidedAdminUID();
        if (destinationServerCI.getProvidedBindDN() == null)
        {
          // If the explicit bind DN is not null, the password corresponds
          // to that bind DN. We are in the case where the user provides
          // bind DN on first server and admin UID globally.
          adminPwd = destinationServerCI.getBindPassword();
        }
      }

      setConnectionDetails(uData.getServer2(), destinationServerCI);
      if (!setReplicationDetails(
          uData.getServer2(), destinationServerCI, argParser.server2, conn2, uData.getServer1()))
      {
        return false;
      }

      // If the server contains an ADS. Try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // enableReplication(EnableReplicationUserData) method. Here we have
      // to load the ADS to ask the user to accept the certificates.
      AtomicReference<ConnectionWrapper> aux1 = new AtomicReference<>(conn2);
      if (!loadADSAndAcceptCertificates(destinationServerCI, aux1, uData, false))
      {
        return false;
      }
      conn2 = aux1.get();
      administratorDefined |= hasAdministrator(conn2);

      // If the adminUid and adminPwd are not set in the EnableReplicationUserData
      // object, that means that there are no administrators and that they
      // must be created. The adminUId and adminPwd are updated inside
      // loadADSAndAcceptCertificates.
      boolean promptedForAdmin = false;

      // There is a case where we haven't had need for the administrator
      // credentials even if the administrators are defined: where all the servers
      // can be accessed with another user (for instance if all the server have
      // defined cn=directory manager and all the entries have the same password).
      if (uData.getAdminUid() == null && !administratorDefined)
      {
        if (adminUid == null)
        {
          println(INFO_REPLICATION_ENABLE_ADMINISTRATOR_MUST_BE_CREATED.get());
          promptedForAdmin = true;
          adminUid = askForAdministratorUID(getDefaultValue(argParser.getAdminUidArg()), logger);
          println();
        }
        uData.setAdminUid(adminUid);
      }

      if (uData.getAdminPwd() == null)
      {
        uData.setAdminPwd(adminPwd);
      }
      if (uData.getAdminPwd() == null && !administratorDefined)
      {
        adminPwd = null;
        int nPasswordPrompts = 0;
        while (adminPwd == null)
        {
          if (nPasswordPrompts > CONFIRMATION_MAX_TRIES)
          {
            errPrintln(ERR_CONFIRMATION_TRIES_LIMIT_REACHED.get(CONFIRMATION_MAX_TRIES));
            return false;
          }
          nPasswordPrompts++;
          if (!promptedForAdmin)
          {
            println();
            println(INFO_REPLICATION_ENABLE_ADMINISTRATOR_MUST_BE_CREATED.get());
            println();
          }
          while (adminPwd == null)
          {
            adminPwd = askForAdministratorPwd(logger);
            println();
          }
          String adminPwdConfirm = null;
          while (adminPwdConfirm == null)
          {
            try
            {
              adminPwdConfirm = String.valueOf(readPassword(INFO_ADMINISTRATOR_PWD_CONFIRM_PROMPT.get()));
            }
            catch (ClientException ex)
            {
              logger.warn(LocalizableMessage.raw("Error reading input: " + ex, ex));
            }
            println();
          }
          if (!adminPwd.equals(adminPwdConfirm))
          {
            println();
            errPrintln(ERR_ADMINISTRATOR_PWD_DO_NOT_MATCH.get());
            println();
            adminPwd = null;
          }
        }
        uData.setAdminPwd(adminPwd);
      }

      List<DN> suffixes = toDNs(argParser.getBaseDNs());
      checkSuffixesForEnableReplication(suffixes, conn1, conn2, true, uData);

      uData.setBaseDNs(suffixes);
      return !suffixes.isEmpty();
    }
    finally
    {
      close(conn1, conn2);
      uData.setReplicateSchema(!argParser.noSchemaReplication());
    }
  }

  private boolean setReplicationDetails(
      EnableReplicationServerData serverData, LDAPConnectionConsoleInteraction serverCI, ServerArgs serverArgs,
      ConnectionWrapper conn, EnableReplicationServerData otherServerData)
  {
    final String host = serverCI.getHostName();
    final int port = serverCI.getPortNumber();

    int replicationPort = -1;
    boolean secureReplication = serverArgs.secureReplicationArg.isPresent();
    boolean configureReplicationServer = serverArgs.configureReplicationServer();
    boolean configureReplicationDomain = serverArgs.configureReplicationDomain();
    final int repPort = getReplicationPort(conn);
    final boolean replicationServerConfigured = repPort > 0;
    if (replicationServerConfigured && !configureReplicationServer)
    {
      final LocalizableMessage prompt =
          INFO_REPLICATION_SERVER_CONFIGURED_WARNING_PROMPT.get(conn.getHostPort(), repPort);
      if (!askConfirmation(prompt, false))
      {
        return false;
      }
    }

    // Try to get the replication port for server only if it is required.
    if (configureReplicationServer && !replicationServerConfigured)
    {
      if (argParser.advancedArg.isPresent() && configureReplicationDomain)
      {
        // Only ask if the replication domain will be configured
        // (if not the replication server MUST be configured).
        try
        {
          configureReplicationServer = askConfirmation(configureReplicationServerPrompt(otherServerData), true, logger);
        }
        catch (ClientException ce)
        {
          errPrintln(ce.getMessageObject());
          return false;
        }
      }

      if (configureReplicationServer && !replicationServerConfigured)
      {
        boolean tryWithDefault = getValue(serverArgs.replicationPortArg) != -1;
        while (replicationPort == -1)
        {
          if (tryWithDefault)
          {
            replicationPort = getValue(serverArgs.replicationPortArg);
            tryWithDefault = false;
          }
          else
          {
            replicationPort = askPort(
                replicationPortPrompt(otherServerData), getDefaultValue(serverArgs.replicationPortArg), logger);
            println();
          }
          if (!argParser.skipReplicationPortCheck() && isLocalHost(host))
          {
            if (!SetupUtils.canUseAsPort(replicationPort))
            {
              errPrintln();
              errPrintln(getCannotBindToPortError(replicationPort));
              errPrintln();
              replicationPort = -1;
            }
          }
          else if (replicationPort == port)
          {
            // This is something that we must do in any case...
            // this test is already included when we call SetupUtils.canUseAsPort()
            errPrintln();
            errPrintln(ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(host, replicationPort));
            errPrintln();
            replicationPort = -1;
          }

          if (otherServerData != null)
          {
            final String otherHost = otherServerData.getHostName();
            final int otherReplPort = otherServerData.getReplicationPort();
            if (otherHost.equalsIgnoreCase(host) && otherReplPort > 0 && otherReplPort == replicationPort)
            {
              errPrintln();
              errPrintln(ERR_REPLICATION_SAME_REPLICATION_PORT.get(replicationPort, otherHost));
              errPrintln();
              replicationPort = -1;
            }
          }
        }

        if (!secureReplication)
        {
          try
          {
            secureReplication = askConfirmation(
                secureReplicationPrompt(replicationPort, otherServerData), false, logger);
          }
          catch (ClientException ce)
          {
            errPrintln(ce.getMessageObject());
            return false;
          }
          println();
        }
      }
    }

    if (configureReplicationDomain && configureReplicationServer && argParser.advancedArg.isPresent())
    {
      // Only necessary to ask if the replication server will be configured
      try
      {
        configureReplicationDomain = askConfirmation(configureReplicationDomainPrompt(otherServerData), true, logger);
      }
      catch (ClientException ce)
      {
        errPrintln(ce.getMessageObject());
        return false;
      }
    }

    serverData.setReplicationPort(replicationPort);
    serverData.setSecureReplication(secureReplication);
    serverData.setConfigureReplicationServer(configureReplicationServer);
    serverData.setConfigureReplicationDomain(configureReplicationDomain);
    return true;
  }

  private LocalizableMessage configureReplicationDomainPrompt(EnableReplicationServerData otherServerData)
  {
    return otherServerData == null
        ? INFO_REPLICATION_ENABLE_REPLICATION_DOMAIN1_PROMPT.get()
        : INFO_REPLICATION_ENABLE_REPLICATION_DOMAIN2_PROMPT.get();
  }

  private LocalizableMessage secureReplicationPrompt(int replicationPort, EnableReplicationServerData otherServerData)
  {
    return otherServerData == null
        ? INFO_REPLICATION_ENABLE_SECURE1_PROMPT.get(replicationPort)
        : INFO_REPLICATION_ENABLE_SECURE2_PROMPT.get(replicationPort);
  }

  private LocalizableMessage configureReplicationServerPrompt(EnableReplicationServerData otherServerData)
  {
    return otherServerData == null
        ? INFO_REPLICATION_ENABLE_REPLICATION_SERVER1_PROMPT.get()
        : INFO_REPLICATION_ENABLE_REPLICATION_SERVER2_PROMPT.get();
  }

  private LocalizableMessage replicationPortPrompt(EnableReplicationServerData otherServerData)
  {
    return otherServerData == null
        ? INFO_REPLICATION_ENABLE_REPLICATIONPORT1_PROMPT.get()
        : INFO_REPLICATION_ENABLE_REPLICATIONPORT2_PROMPT.get();
  }

  private boolean initializeGlobalArguments(LDAPConnectionConsoleInteraction serverCI, ServerArgs serverArgs,
      String adminPwd, String adminUid)
  {
    boolean doNotDisplayFirstError = false;
    String host = getValue(serverArgs.hostNameArg);
    int port = getValue(serverArgs.portArg);
    String bindDn = getValue(serverArgs.bindDnArg);

    String pwd = null;
    LinkedHashMap<String, String> pwdFile = null;
    if (serverArgs.bindPasswordArg.isPresent())
    {
      pwd = serverArgs.bindPasswordArg.getValue();
    }
    else if (serverArgs.bindPasswordFileArg.isPresent())
    {
      pwdFile = new LinkedHashMap<>(serverArgs.bindPasswordFileArg.getNameToValueMap());
    }
    else if (bindDn == null)
    {
      doNotDisplayFirstError = true;
      pwd = adminPwd;
      pwdFile = getPwdFile();
    }

    final DN bindDN = bindDn != null ? DN.valueOf(bindDn) : null;
    serverCI.initializeGlobalArguments(host, port, adminUid, bindDN, pwd, pwdFile);
    return doNotDisplayFirstError;
  }

  private void setConnectionDetails(EnableReplicationServerData serverData, LDAPConnectionConsoleInteraction serverCI)
  {
    serverData.setHostPort(getHostPort(serverCI));
    serverData.setBindDn(serverCI.getBindDN());
    serverData.setPwd(serverCI.getBindPassword());
  }

  private ConnectionWrapper initializeFirstConnection(LocalizableMessage headingMsg)
  {
    while (true)
    {
      try
      {
        sourceServerCI.setHeadingMessage(headingMsg);
        sourceServerCI.run();
        return createConnectionInteracting(sourceServerCI);
      }
      catch (ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Client exception " + ce));
        errPrintln();
        errPrintln(ce.getMessageObject());
        errPrintln();
        sourceServerCI.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        logger.warn(LocalizableMessage.raw("Argument exception " + ae));
        argParser.displayMessageAndUsageReference(getErrStream(), ae.getMessageObject());
        return null;
      }
    }
  }

  private ConnectionWrapper initializeConnection2(LDAPConnectionConsoleInteraction destinationServerCI,
      LDAPConnectionConsoleInteraction sourceServerCI, boolean doNotDisplayFirstError)
  {
    destinationServerCI.resetHeadingDisplayed();
    while (true)
    {
      try
      {
        destinationServerCI.setHeadingMessage(INFO_REPLICATION_ENABLE_HOST2_CONNECTION_PARAMETERS.get());
        destinationServerCI.run();

        String host1 = sourceServerCI.getHostName();
        int port1 = sourceServerCI.getPortNumber();
        if (host1.equalsIgnoreCase(destinationServerCI.getHostName())
            && port1 == destinationServerCI.getPortNumber())
        {
          errPrintln();
          errPrintln(ERR_REPLICATION_ENABLE_SAME_SERVER_PORT.get(host1, port1));
          errPrintln();
          return null;
        }

        return createConnectionInteracting(destinationServerCI, true);
      }
      catch (ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Client exception " + ce));
        if (!doNotDisplayFirstError)
        {
          errPrintln();
          errPrintln(ce.getMessageObject());
          errPrintln();
          destinationServerCI.resetConnectionArguments();
        }
        else
        {
          // Reset only the credential parameters.
          destinationServerCI.resetConnectionArguments();
          destinationServerCI.initializeGlobalArguments(
              destinationServerCI.getHostName(), destinationServerCI.getPortNumber(), null, null, null, null);
        }
      }
      catch (ArgumentException ae)
      {
        logger.warn(LocalizableMessage.raw("Argument exception " + ae));
        argParser.displayMessageAndUsageReference(getErrStream(), ae.getMessageObject());
        return null;
      }
      finally
      {
        doNotDisplayFirstError = false;
      }
    }
  }

  /**
   * Updates the contents of the provided DisableReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return {@code true} if the object was successfully updated and
   * {@code false} if the user cancelled the operation.
   * @throws ReplicationCliException if there is a critical error reading the ADS.
   */
  private boolean promptIfRequired(DisableReplicationUserData uData) throws ReplicationCliException
  {
    ConnectionWrapper conn = createConnection();
    if (conn == null)
    {
      return false;
    }

    try
    {
      final String adminUid = sourceServerCI.getProvidedAdminUID();
      uData.setHostPort(getHostPort(sourceServerCI));
      uData.setAdminUid(adminUid);
      uData.setBindDn(sourceServerCI.getProvidedBindDN());
      uData.setAdminPwd(sourceServerCI.getBindPassword());

      if (adminUid != null)
      {
        // If the server contains an ADS, try to load it and only load it: if
        // there are issues with the ADS they will be encountered in the
        // disableReplication(DisableReplicationUserData) method. Here we have
        // to load the ADS to ask the user to accept the certificates and
        // eventually admin authentication data.
        AtomicReference<ConnectionWrapper> aux = new AtomicReference<>(conn);
        if (!loadADSAndAcceptCertificates(sourceServerCI, aux, uData, false))
        {
          return false;
        }
        conn = aux.get();
      }

      boolean disableAll = argParser.disableAllArg.isPresent();
      boolean disableReplicationServer = argParser.disableReplicationServerArg.isPresent();
      if (disableAll
          || (argParser.advancedArg.isPresent()
              && argParser.getBaseDNs().isEmpty()
              && !disableReplicationServer))
      {
        try
        {
          disableAll = askConfirmation(INFO_REPLICATION_PROMPT_DISABLE_ALL.get(), disableAll, logger);
        }
        catch (ClientException ce)
        {
          errPrintln(ce.getMessageObject());
          return false;
        }
      }

      int repPort = getReplicationPort(conn);
      if (!disableAll
          && repPort > 0
          && (argParser.advancedArg.isPresent() || disableReplicationServer))
      {
        try
        {
          LocalizableMessage prompt = INFO_REPLICATION_PROMPT_DISABLE_REPLICATION_SERVER.get(repPort);
          disableReplicationServer = askConfirmation(prompt, disableReplicationServer, logger);
        }
        catch (ClientException ce)
        {
          errPrintln(ce.getMessageObject());
          return false;
        }
      }

      if (disableReplicationServer && repPort < 0)
      {
        disableReplicationServer = false;
        try
        {
          LocalizableMessage prompt = INFO_REPLICATION_PROMPT_NO_REPLICATION_SERVER_TO_DISABLE.get(conn.getHostPort());
          if (!askConfirmation(prompt, false, logger))
          {
            return false;
          }
        }
        catch (ClientException ce)
        {
          errPrintln(ce.getMessageObject());
          return false;
        }
      }
      if (repPort > 0 && disableAll)
      {
        disableReplicationServer = true;
      }

      uData.setDisableAll(disableAll);
      uData.setDisableReplicationServer(disableReplicationServer);

      if (!disableAll)
      {
        List<DN> suffixes = toDNs(argParser.getBaseDNs());
        checkSuffixesForDisableReplication(suffixes, conn, true, !disableReplicationServer);
        if (suffixes.isEmpty() && !disableReplicationServer)
        {
          return false;
        }

        uData.setBaseDNs(suffixes);

        if (!uData.disableReplicationServer()
            && repPort > 0
            && disableAllBaseDns(conn, uData)
            && !argParser.advancedArg.isPresent())
        {
          try
          {
            LocalizableMessage prompt =
                INFO_REPLICATION_DISABLE_ALL_SUFFIXES_DISABLE_REPLICATION_SERVER.get(conn.getHostPort(), repPort);
            uData.setDisableReplicationServer(askConfirmation(prompt, true, logger));
          }
          catch (ClientException ce)
          {
            errPrintln(ce.getMessageObject());
            return false;
          }
        }
      }

      // Ask for confirmation to disable if not already done.
      boolean disableADS = false;
      boolean disableSchema = false;
      for (DN dn : uData.getBaseDNs())
      {
        if (ADSContext.getAdministrationSuffixDN().equals(dn))
        {
          disableADS = true;
        }
        else if (Constants.SCHEMA_DN.equals(dn))
        {
          disableSchema = true;
        }
      }

      boolean cancelled = false;
      if (disableADS)
      {
        println();
        LocalizableMessage msg = INFO_REPLICATION_CONFIRM_DISABLE_ADS.get(ADSContext.getAdministrationSuffixDN());
        cancelled = !askConfirmation(msg, true);
        println();
      }
      if (disableSchema)
      {
        println();
        LocalizableMessage msg = INFO_REPLICATION_CONFIRM_DISABLE_SCHEMA.get();
        cancelled = !askConfirmation(msg, true);
        println();
      }
      if (!disableSchema && !disableADS)
      {
        println();
        if (!uData.disableAll() && !uData.getBaseDNs().isEmpty())
        {
          cancelled = !askConfirmation(INFO_REPLICATION_CONFIRM_DISABLE_GENERIC.get(), true);
        }
        println();
      }
      return !cancelled;
    }
    finally
    {
      close(conn);
    }
  }

  private ConnectionWrapper createConnection()
  {
    while (true)
    {
      try
      {
        sourceServerCI.setUseAdminOrBindDn(true);
        sourceServerCI.run();
        return createConnectionInteracting(sourceServerCI);
      }
      catch (ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Client exception " + ce));
        errPrintln();
        errPrintln(ce.getMessageObject());
        errPrintln();
        sourceServerCI.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        logger.warn(LocalizableMessage.raw("Argument exception " + ae));
        argParser.displayMessageAndUsageReference(getErrStream(), ae.getMessageObject());
        return null;
      }
    }
  }

  /**
   * Updates the contents of the provided InitializeAllReplicationUserData
   * object with the information provided in the command-line.  If some
   * information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return {@code true} if the object was successfully updated and
   * {@code false} if the user cancelled the operation.
   */
  private boolean promptIfRequired(InitializeAllReplicationUserData uData)
  {
    ConnectionWrapper conn = getConnection(uData);
    if (conn == null)
    {
      return false;
    }

    try
    {
      List<DN> suffixes = toDNs(argParser.getBaseDNs());
      checkSuffixesForInitializeReplication(suffixes, conn, true);
      if (suffixes.isEmpty())
      {
        return false;
      }
      uData.setBaseDNs(suffixes);

      // Ask for confirmation to initialize.
      println();
      final boolean cancelled = !askConfirmation(getPrompt(uData, conn), true);
      println();
      return !cancelled;
    }
    finally
    {
      close(conn);
    }
  }

  private LocalizableMessage getPrompt(InitializeAllReplicationUserData uData, ConnectionWrapper conn)
  {
    HostPort hostPortSource = conn.getHostPort();
    if (initializeADS(uData.getBaseDNs()))
    {
      return INFO_REPLICATION_CONFIRM_INITIALIZE_ALL_ADS.get(ADSContext.getAdministrationSuffixDN(), hostPortSource);
    }
    return INFO_REPLICATION_CONFIRM_INITIALIZE_ALL_GENERIC.get(hostPortSource);
  }

  private boolean askConfirmation(final LocalizableMessage msg, final boolean defaultValue)
  {
    try
    {
      return askConfirmation(msg, defaultValue, logger);
    }
    catch (ClientException ce)
    {
      errPrintln(ce.getMessageObject());
      return false;
    }
  }

  /**
   * Updates the contents of the provided user data
   * object with the information provided in the command-line.
   * If some information is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return {@code true} if the object was successfully updated and
   * {@code false} if the user cancelled the operation.
   */
  private boolean promptIfRequiredForPreOrPost(MonoServerReplicationUserData uData)
  {
    ConnectionWrapper conn = getConnection(uData);
    if (conn == null)
    {
      return false;
    }
    try
    {
      List<DN> suffixes = toDNs(argParser.getBaseDNs());
      checkSuffixesForInitializeReplication(suffixes, conn, true);
      uData.setBaseDNs(suffixes);
      return !suffixes.isEmpty();
    }
    finally
    {
      close(conn);
    }
  }

  private ConnectionWrapper getConnection(MonoServerReplicationUserData uData)
  {
    // Try to connect to the server.
    while (true)
    {
      try
      {
        if (uData instanceof InitializeAllReplicationUserData)
        {
          sourceServerCI.setHeadingMessage(INFO_INITIALIZE_SOURCE_CONNECTION_PARAMETERS.get());
        }
        sourceServerCI.run();

        ConnectionWrapper conn = createConnectionInteracting(sourceServerCI);
        if (conn != null)
        {
          uData.setHostPort(getHostPort(sourceServerCI));
          uData.setAdminUid(sourceServerCI.getAdministratorUID());
          uData.setAdminPwd(sourceServerCI.getBindPassword());
          if (uData instanceof StatusReplicationUserData)
          {
            ((StatusReplicationUserData) uData).setScriptFriendly(argParser.isScriptFriendly());
          }
        }
        return conn;
      }
      catch (ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Client exception " + ce));
        errPrintln();
        errPrintln(ce.getMessageObject());
        errPrintln();
        sourceServerCI.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        logger.warn(LocalizableMessage.raw("Argument exception " + ae));
        argParser.displayMessageAndUsageReference(getErrStream(), ae.getMessageObject());
        return null;
      }
    }
  }

  /**
   * Updates the contents of the provided StatusReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @return {@code true} if the object was successfully updated and
   * {@code false} if the user cancelled the operation.
   * @throws ReplicationCliException if a critical error occurs reading the ADS.
   */
  private boolean promptIfRequired(StatusReplicationUserData uData) throws ReplicationCliException
  {
    ConnectionWrapper conn = getConnection(uData);
    if (conn == null)
    {
      return false;
    }

    try
    {
      // If the server contains an ADS, try to load it and only load it: if
      // there are issues with the ADS they will be encountered in the
      // statusReplication(StatusReplicationUserData) method. Here we have
      // to load the ADS to ask the user to accept the certificates and
      // eventually admin authentication data.
      AtomicReference<ConnectionWrapper> aux = new AtomicReference<>(conn);
      if (!loadADSAndAcceptCertificates(sourceServerCI, aux, uData, false))
      {
        return false;
      }
      conn = aux.get();

      uData.setBaseDNs(toDNs(argParser.getBaseDNs()));
      return true;
    }
    finally
    {
      close(conn);
    }
  }

  /**
   * Updates the contents of the provided InitializeReplicationUserData object
   * with the information provided in the command-line.  If some information
   * is missing, ask the user to provide valid data.
   * We assume that if this method is called we are in interactive mode.
   * @param uData the object to be updated.
   * @param serversOperations Additional processing for the command
   * @return {@code true} if the object was successfully updated and
   * {@code false} if the user cancelled the operation.
   */
  private boolean promptIfRequired(SourceDestinationServerUserData uData,
      OperationBetweenSourceAndDestinationServers serversOperations)
  {
    String adminPwd = argParser.getBindPasswordAdmin();
    String adminUid = argParser.getAdministratorUID();

    ConnectionWrapper connSource = null;
    ConnectionWrapper connDestination = null;
    try
    {
      // Prompt for source server credentials
      sourceServerCI.initializeGlobalArguments(argParser.getHostNameSource(), argParser.getPortSource(), adminUid, null,
          adminPwd, getPwdFile());
      // Try to connect to the source server
      connSource = initializeFirstConnection(INFO_INITIALIZE_SOURCE_CONNECTION_PARAMETERS.get());
      if (connSource == null)
      {
        return false;
      }

      adminUid = sourceServerCI.getAdministratorUID();
      adminPwd = sourceServerCI.getBindPassword();

      uData.setHostNameSource(sourceServerCI.getHostName());
      uData.setPortSource(sourceServerCI.getPortNumber());
      uData.setAdminUid(adminUid);
      uData.setAdminPwd(adminPwd);

      firstServerCommandBuilder = new CommandBuilder();
      if (mustPrintCommandBuilder())
      {
        firstServerCommandBuilder.append(sourceServerCI.getCommandBuilder());
      }

      // Prompt for destination server credentials
      LDAPConnectionConsoleInteraction destinationServerCI =
          new LDAPConnectionConsoleInteraction(this, argParser.getSecureArgsList());
      destinationServerCI.initializeGlobalArguments(argParser.getHostNameDestination(), argParser.getPortDestination(),
          adminUid, null, adminPwd, getPwdFile());

      // Try to connect to the destination server.
      connDestination = initializeDestinationConnection(sourceServerCI, destinationServerCI);
      if (connDestination == null)
      {
        return false;
      }

      uData.setHostNameDestination(destinationServerCI.getHostName());
      uData.setPortDestination(destinationServerCI.getPortNumber());

      List<DN> suffixes = toDNs(argParser.getBaseDNs());
      uData.setBaseDNs(suffixes);
      if (!serversOperations.continueAfterUserInput(suffixes, connSource, connDestination))
      {
        return false;
      }
      println();
      final boolean confirmed = serversOperations.confirmOperation(uData, connSource, connDestination, true);
      println();
      return confirmed;
    }
    finally
    {
      close(connSource, connDestination);
    }
  }

  private LinkedHashMap<String, String> getPwdFile()
  {
    FileBasedArgument bindPasswordFileArg = argParser.getSecureArgsList().getBindPasswordFileArg();
    if (bindPasswordFileArg.isPresent())
    {
      // Use a copy of the argument properties since the map might be cleared in
      // {@link LDAPConnectionConsoleInteraction#initializeGlobalArguments()}
      return new LinkedHashMap<>(bindPasswordFileArg.getNameToValueMap());
    }
    return null;
  }

  private ConnectionWrapper initializeDestinationConnection(LDAPConnectionConsoleInteraction sourceServerCI,
      LDAPConnectionConsoleInteraction destinationServerCI)
  {
    destinationServerCI.resetHeadingDisplayed();
    while (true)
    {
      try
      {
        destinationServerCI.setHeadingMessage(INFO_INITIALIZE_DESTINATION_CONNECTION_PARAMETERS.get());
        destinationServerCI.run();

        final String hostSource = sourceServerCI.getHostName();
        final int portSource = sourceServerCI.getPortNumber();
        if (hostSource.equalsIgnoreCase(destinationServerCI.getHostName())
            && portSource == destinationServerCI.getPortNumber())
        {
          errPrintln();
          errPrintln(ERR_SOURCE_DESTINATION_INITIALIZE_SAME_SERVER_PORT.get(hostSource, portSource));
          errPrintln();
          return null;
        }

        return createConnectionInteracting(destinationServerCI, true);
      }
      catch (ClientException ce)
      {
        logger.warn(LocalizableMessage.raw("Client exception " + ce));
        errPrintln();
        errPrintln(ce.getMessageObject());
        errPrintln();
        destinationServerCI.resetConnectionArguments();
      }
      catch (ArgumentException ae)
      {
        logger.warn(LocalizableMessage.raw("Argument exception " + ae));
        argParser.displayMessageAndUsageReference(getErrStream(), ae.getMessageObject());
        return null;
      }
    }
  }

  private LocalizableMessage getInitializeReplicationPrompt(SourceDestinationServerUserData uData,
      ConnectionWrapper connSource, ConnectionWrapper connDestination)
  {
    HostPort hostPortSource = connSource.getHostPort();
    HostPort hostPortDestination = connDestination.getHostPort();
    if (initializeADS(uData.getBaseDNs()))
    {
      final DN adminSuffixDN = ADSContext.getAdministrationSuffixDN();
      return INFO_REPLICATION_CONFIRM_INITIALIZE_ADS.get(adminSuffixDN, hostPortDestination, hostPortSource);
    }
    return INFO_REPLICATION_CONFIRM_INITIALIZE_GENERIC.get(hostPortDestination, hostPortSource);
  }

  private boolean initializeADS(List<DN> baseDNs)
  {
    return baseDNs.contains(ADSContext.getAdministrationSuffixDN());
  }

  /**
   * Returns the trust manager to be used by this application.
   * @param ci the LDAP connection to the server
   * @return the trust manager to be used by this application.
   */
  private ApplicationTrustManager getTrustManager(LDAPConnectionConsoleInteraction ci)
  {
    return isInteractive() ? ci.getTrustManager() : argParser.getTrustManager();
  }

  /**
   * Initializes the contents of the provided enable replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the enable replication user data object to be initialized.
   */
  private void initializeWithArgParser(EnableReplicationUserData uData)
  {
    initialize(uData);

    final DN adminDN = getAdministratorDN(uData.getAdminUid());
    final String adminPwd = uData.getAdminPwd();
    setConnectionDetails(uData.getServer1(), argParser.server1, adminDN, adminPwd);
    setConnectionDetails(uData.getServer2(), argParser.server2, adminDN, adminPwd);

    uData.setReplicateSchema(!argParser.noSchemaReplication());

    setReplicationDetails(uData.getServer1(), argParser.server1);
    setReplicationDetails(uData.getServer2(), argParser.server2);
  }

  private void setConnectionDetails(
      EnableReplicationServerData server, ServerArgs args, DN adminDN, String adminPwd)
  {
    server.setHostPort(new HostPort(
        getValueOrDefault(args.hostNameArg), getValueOrDefault(args.portArg)));

    String pwd = args.getBindPassword();
    if (pwd == null || canConnectWithCredentials(server, adminDN, adminPwd))
    {
      server.setBindDn(adminDN);
      server.setPwd(adminPwd);
    }
    else
    {
      server.setBindDn(DN.valueOf(getValueOrDefault(args.bindDnArg)));
      server.setPwd(pwd);
    }
  }

  private boolean canConnectWithCredentials(EnableReplicationServerData server, DN adminDN, String adminPwd)
  {
    try (ConnectionWrapper validCredentials = new ConnectionWrapper(
        server.getHostPort(), connectionType, adminDN, adminPwd, getConnectTimeout(), getTrustManager(sourceServerCI)))
    {
      return true;
    }
    catch (Throwable t)
    {
      return false;
    }
  }

  private void setReplicationDetails(EnableReplicationServerData server, ServerArgs args)
  {
    server.setSecureReplication(args.secureReplicationArg.isPresent());
    server.setConfigureReplicationDomain(args.configureReplicationDomain());
    server.setConfigureReplicationServer(args.configureReplicationServer());
    if (server.configureReplicationServer())
    {
      server.setReplicationPort(getValueOrDefault(args.replicationPortArg));
    }
  }

  /**
   * Initializes the contents of the provided initialize replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the initialize replication user data object to be initialized.
   */
  private void initializeWithArgParser(SourceDestinationServerUserData uData)
  {
    initialize(uData);

    uData.setHostNameSource(argParser.getHostNameSourceOrDefault());
    uData.setPortSource(argParser.getPortSourceOrDefault());
    uData.setHostNameDestination(argParser.getHostNameDestinationOrDefault());
    uData.setPortDestination(argParser.getPortDestinationOrDefault());
  }

  /**
   * Initializes the contents of the provided disable replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the disable replication user data object to be initialized.
   */
  private void initializeWithArgParser(DisableReplicationUserData uData)
  {
    uData.setBaseDNs(toDNs(argParser.getBaseDNs()));
    String adminUid = argParser.getAdministratorUID();
    String bindDnStr = argParser.getBindDNToDisable();
    DN bindDn = bindDnStr != null ? DN.valueOf(bindDnStr) : null;
    if (bindDn == null && adminUid == null)
    {
      adminUid = argParser.getAdministratorUIDOrDefault();
      bindDn = getAdministratorDN(adminUid);
    }
    uData.setAdminUid(adminUid);
    uData.setBindDn(bindDn);
    uData.setAdminPwd(argParser.getBindPasswordAdmin());

    uData.setHostPort(new HostPort(
        argParser.getHostNameToDisableOrDefault(), argParser.getPortToDisableOrDefault()));

    uData.setDisableAll(argParser.disableAllArg.isPresent());
    uData.setDisableReplicationServer(argParser.disableReplicationServerArg.isPresent());
  }

  /**
   * Initializes the contents of the provided user data object with what was
   * provided in the command-line without prompting to the user.
   * @param uData the user data object to be initialized.
   */
  private void initializeWithArgParser(MonoServerReplicationUserData uData)
  {
    initialize(uData);

    uData.setHostPort(new HostPort(
        argParser.getHostNameToInitializeAllOrDefault(), argParser.getPortToInitializeAllOrDefault()));
  }

  /**
   * Initializes the contents of the provided status replication user data
   * object with what was provided in the command-line without prompting to the
   * user.
   * @param uData the status replication user data object to be initialized.
   */
  private void initializeWithArgParser(StatusReplicationUserData uData)
  {
    initialize(uData);

    uData.setHostPort(new HostPort(argParser.getHostNameToStatusOrDefault(), argParser.getPortToStatusOrDefault()));
    uData.setScriptFriendly(argParser.isScriptFriendly());
  }

  private void initialize(ReplicationUserData uData)
  {
    uData.setBaseDNs(toDNs(argParser.getBaseDNs()));
    uData.setAdminUid(argParser.getAdministratorUIDOrDefault());
    uData.setAdminPwd(argParser.getBindPasswordAdmin());
  }

  /**
   * Tells whether the server for which a connection is provided has a replication port or not.
   *
   * @param conn
   *          the connection to be used.
   * @return {@code true} if the server replication port could be found, {@code false} otherwise.
   */
  private boolean hasReplicationPort(ConnectionWrapper conn)
  {
    return getReplicationPort(conn) != -1;
  }

  /**
   * Returns the replication port of server for which the connection is provided.
   * @param conn the connection to be used.
   * @return the server's replication port or -1 if the replication port could not be found
   */
  private int getReplicationPort(ConnectionWrapper conn)
  {
    try
    {
      ReplicationSynchronizationProviderCfgClient sync = getMultimasterSynchronization(conn);
      if (sync.hasReplicationServer())
      {
        return sync.getReplicationServer().getReplicationPort();
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Unexpected error retrieving the replication port: " + t, t));
    }
    return -1;
  }

  /**
   * Loads the ADS with the provided connection.  If there are certificates to
   * be accepted we prompt them to the user.  If there are errors loading the
   * servers we display them to the user and we ask for confirmation.  If the
   * provided connection is not using Global Administrator credentials, we prompt the
   * user to provide them and update the provide ReplicationUserData
   * accordingly.
   *
   * @param ci the LDAP connection to the server
   * @param conn the connection to be used in an array: note the connection
   * may be modified with the new credentials provided by the user.
   * @param uData the ReplicationUserData to be updated.
   * @param isFirstOrSourceServer whether this is the first server in the
   * enable replication subcommand or the source server in the initialize server
   * subcommand.
   * @throws ReplicationCliException if a critical error occurred.
   * @return {@code true} if everything went fine and the user accepted
   * all the certificates and confirmed everything.  Returns {@code false}
   * if the user did not accept a certificate or any of the confirmation
   * messages.
   */
  private boolean loadADSAndAcceptCertificates(LDAPConnectionConsoleInteraction ci,
      AtomicReference<ConnectionWrapper> conn, ReplicationUserData uData, boolean isFirstOrSourceServer)
  throws ReplicationCliException
  {
    boolean cancelled = false;
    boolean triedWithUserProvidedAdmin = false;
    final ConnectionWrapper conn1 = conn.get();
    HostPort hostPort = conn1.getHostPort();
    Type connectionType = conn1.getConnectionType();
    if (getTrustManager(ci) == null)
    {
      // This is required when the user did  connect to the server using SSL or
      // Start TLS.  In this case LDAPConnectionConsoleInteraction.run does not
      // initialize the keystore and the trust manager is null.
      forceTrustManagerInitialization(ci);
    }
    try
    {
      ADSContext adsContext = new ADSContext(conn1);
      if (adsContext.hasAdminData())
      {
        boolean reloadTopology = true;
        LinkedList<LocalizableMessage> exceptionMsgs = new LinkedList<>();
        while (reloadTopology && !cancelled)
        {
          // We must recreate the cache because the trust manager in the
          // LDAPConnectionConsoleInteraction object might have changed.

          TopologyCache cache = new TopologyCache(adsContext,
              getTrustManager(ci), getConnectTimeout());
          cache.getFilter().setSearchMonitoringInformation(false);
          cache.getFilter().setSearchBaseDNInformation(false);
          cache.setPreferredConnections(getPreferredConnections(conn1));
          cache.reloadTopology();

          reloadTopology = false;
          exceptionMsgs.clear();

          /* Analyze if we had any exception while loading servers.  For the
           * moment only throw the exception found if the user did not provide
           * the Administrator DN and this caused a problem authenticating in
           * one server or if there is a certificate problem.
           */
          Set<TopologyCacheException> exceptions = new HashSet<>();
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
          loopOnExceptions:
          for (TopologyCacheException e : exceptions)
          {
            switch (e.getType())
            {
              case NOT_GLOBAL_ADMINISTRATOR:
                boolean connected = false;

                String adminUid = uData.getAdminUid();
                String adminPwd = uData.getAdminPwd();

                boolean errorDisplayed = false;
                while (!connected)
                {
                  if (!triedWithUserProvidedAdmin && adminPwd == null)
                  {
                    adminUid = argParser.getAdministratorUIDOrDefault();
                    adminPwd = argParser.getBindPasswordAdmin();
                    triedWithUserProvidedAdmin = true;
                  }
                  if (adminPwd == null)
                  {
                    if (!errorDisplayed)
                    {
                      println();
                      println(INFO_NOT_GLOBAL_ADMINISTRATOR_PROVIDED.get());
                      errorDisplayed = true;
                    }
                    adminUid = askForAdministratorUID(
                        getDefaultValue(argParser.getAdminUidArg()), logger);
                    println();
                    adminPwd = askForAdministratorPwd(logger);
                    println();
                  }
                close(conn1);
                  try
                  {
                    final ConnectionWrapper conn2 = new ConnectionWrapper(
                          hostPort, connectionType, getAdministratorDN(adminUid), adminPwd,
                          getConnectTimeout(), getTrustManager(ci));
                    conn.set(conn2);
                    adsContext = new ADSContext(conn2);
                    cache = new TopologyCache(adsContext, getTrustManager(ci), getConnectTimeout());
                    cache.getFilter().setSearchMonitoringInformation(false);
                    cache.getFilter().setSearchBaseDNInformation(false);
                    cache.setPreferredConnections(getPreferredConnections(conn2));
                    connected = true;
                  }
                  catch (Throwable t)
                  {
                    errPrintln();
                    errPrintln(ERR_ERROR_CONNECTING_TO_SERVER_PROMPT_AGAIN.get(hostPort, t.getMessage()));
                    logger.warn(LocalizableMessage.raw("Complete error stack:", t));
                    errPrintln();
                  }
                }
                uData.setAdminUid(adminUid);
                uData.setAdminPwd(adminPwd);
                if (uData instanceof EnableReplicationUserData)
                {
                  EnableReplicationUserData enableData = (EnableReplicationUserData) uData;
                  EnableReplicationServerData server =
                      isFirstOrSourceServer ? enableData.getServer1() : enableData.getServer2();
                  server.setBindDn(getAdministratorDN(adminUid));
                  server.setPwd(adminPwd);
                }
                reloadTopology = true;
              break loopOnExceptions;
            case GENERIC_CREATING_CONNECTION:
              if (isCertificateException(e.getCause()))
              {
                reloadTopology = true;
                cancelled = !ci.promptForCertificateConfirmation(e.getCause(),
                    e.getTrustManager(), e.getHostPort(), logger);
              }
              else
              {
                exceptionMsgs.add(getMessage(e));
              }
              break;
            default:
              exceptionMsgs.add(getMessage(e));
            }
          }
        }
        if (!exceptionMsgs.isEmpty() && !cancelled)
        {
          if (uData instanceof StatusReplicationUserData)
          {
            errPrintln(
                ERR_REPLICATION_STATUS_READING_REGISTERED_SERVERS.get(
                    getMessageFromCollection(exceptionMsgs,
                        Constants.LINE_SEPARATOR)));
            errPrintln();
          }
          else
          {
            LocalizableMessage msg = ERR_REPLICATION_READING_REGISTERED_SERVERS_CONFIRM_UPDATE_REMOTE.get(
                getMessageFromCollection(exceptionMsgs, Constants.LINE_SEPARATOR));
            cancelled = !askConfirmation(msg, true);
          }
        }
      }
    }
    catch (ADSContextException ace)
    {
      logger.error(LocalizableMessage.raw("Complete error stack:"), ace);
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(ace.getMessage()),
          ERROR_READING_ADS, ace);
    }
    catch (TopologyCacheException tce)
    {
      logger.error(LocalizableMessage.raw("Complete error stack:"), tce);
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
    return !cancelled;
  }

  /**
   * Tells whether there is a Global Administrator defined in the server for which the connection is
   * provided.
   *
   * @param conn
   *          the connection.
   * @return {@code true} if we could find an administrator and {@code false} otherwise.
   */
  private boolean hasAdministrator(ConnectionWrapper conn)
  {
    try
    {
      ADSContext adsContext = new ADSContext(conn);
      if (adsContext.hasAdminData())
      {
        return !adsContext.readAdministratorRegistry().isEmpty();
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw(
          "Unexpected error retrieving the ADS data: "+t, t));
    }
    return false;
  }

  /**
   * Tells whether there is a Global Administrator corresponding to the provided
   * ReplicationUserData defined in the server for which the connection is provided.
   * @param conn the connection
   * @param uData the user data
   * @return {@code true} if we could find an administrator, {@code false} otherwise.
   */
  private boolean hasAdministrator(ConnectionWrapper conn, ReplicationUserData uData)
  {
    String adminUid = uData.getAdminUid();
    try
    {
      ADSContext adsContext = new ADSContext(conn);
      Set<Map<AdministratorProperty, Object>> administrators =
        adsContext.readAdministratorRegistry();
      for (Map<AdministratorProperty, Object> admin : administrators)
      {
        String uid = (String)admin.get(AdministratorProperty.UID);
        // If the administrator UID is null it means that we are just
        // checking for the existence of an administrator
        if (uid != null && (uid.equalsIgnoreCase(adminUid) || adminUid == null))
        {
          return true;
        }
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw(
          "Unexpected error retrieving the ADS data: "+t, t));
    }
    return false;
  }

  /** Helper type for {@link #getCommonSuffixes(ConnectionWrapper, ConnectionWrapper, SuffixRelationType)}. */
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
   * @param conn1 the connection to the first server.
   * @param conn2 the connection to the second server.
   * @param type whether to return a list with the suffixes that are
   * replicated, fully replicated (replicas have exactly the same list of
   * replication servers), not replicated or all the common suffixes.
   * @return a Collection containing a list of suffixes that are replicated
   * (or those that can be replicated) in two servers.
   */
  private List<DN> getCommonSuffixes(ConnectionWrapper conn1, ConnectionWrapper conn2, SuffixRelationType type)
  {
    List<DN> suffixes = new LinkedList<>();
    try
    {
      TopologyCacheFilter filter = new TopologyCacheFilter();
      filter.setSearchMonitoringInformation(false);
      ServerDescriptor server1 = ServerDescriptor.createStandalone(conn1, filter);
      ServerDescriptor server2 = ServerDescriptor.createStandalone(conn2, filter);

      for (ReplicaDescriptor rep1 : server1.getReplicas())
      {
        for (ReplicaDescriptor rep2 : server2.getReplicas())
        {
          DN rep1SuffixDN = rep1.getSuffix().getDN();
          DN rep2SuffixDN = rep2.getSuffix().getDN();
          boolean areDnsEqual = rep1SuffixDN.equals(rep2SuffixDN);
          switch (type)
          {
          case NOT_REPLICATED:
            if (!areReplicated(rep1, rep2) && areDnsEqual)
            {
              suffixes.add(rep1SuffixDN);
            }
            break;
          case FULLY_REPLICATED:
            if (areFullyReplicated(rep1, rep2))
            {
              suffixes.add(rep1SuffixDN);
            }
            break;
          case REPLICATED:
            if (areReplicated(rep1, rep2))
            {
              suffixes.add(rep1SuffixDN);
            }
            break;
          case NOT_FULLY_REPLICATED:
            if (!areFullyReplicated(rep1, rep2) && areDnsEqual)
            {
              suffixes.add(rep1SuffixDN);
            }
            break;
          case ALL:
            if (areDnsEqual)
            {
              suffixes.add(rep1SuffixDN);
            }
            break;
          default:
            throw new IllegalStateException("Unknown type: " + type);
          }
        }
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw(
          "Unexpected error retrieving the server configuration: "+t, t));
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
   * @return {@code true} if we can assure that the two replicas are
   * replicated using the replication server and replication port information
   * and {@code false} otherwise.
   */
  private boolean areFullyReplicated(ReplicaDescriptor rep1, ReplicaDescriptor rep2)
  {
    if (rep1.getSuffix().getDN().equals(rep2.getSuffix().getDN()) &&
        rep1.isReplicated() && rep2.isReplicated() &&
        rep1.getServer().isReplicationServer() &&
        rep2.getServer().isReplicationServer())
    {
      Set<HostPort> servers1 = rep1.getReplicationServers();
      Set<HostPort> servers2 = rep2.getReplicationServers();
      HostPort server1 = rep1.getServer().getReplicationServerHostPort();
      HostPort server2 = rep2.getServer().getReplicationServerHostPort();
      return servers1.contains(server2) && servers2.contains(server1);
    }
    return false;
  }

  /**
   * Tells whether the two provided replicas are replicated or not.  The
   * code in fact checks that both replicas have the same DN and that they
   * have at least one common replication server referenced.
   * @param rep1 the first replica.
   * @param rep2 the second replica.
   * @return {@code true} if we can assure that the two replicas are replicated,
   *         {@code false} otherwise.
   */
  private boolean areReplicated(ReplicaDescriptor rep1, ReplicaDescriptor rep2)
  {
    if (rep1.getSuffix().getDN().equals(rep2.getSuffix().getDN()) &&
        rep1.isReplicated() && rep2.isReplicated())
    {
      Set<HostPort> servers1 = rep1.getReplicationServers();
      Set<HostPort> servers2 = rep2.getReplicationServers();
      servers1.retainAll(servers2);
      return !servers1.isEmpty();
    }
    return false;
  }

  /**
   * Returns a Collection containing a list of replicas in a server.
   * @param conn the connection to the server.
   * @return a Collection containing a list of replicas in a server.
   */
  private Collection<ReplicaDescriptor> getReplicas(ConnectionWrapper conn)
  {
    LinkedList<ReplicaDescriptor> suffixes = new LinkedList<>();
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    try
    {
      ServerDescriptor server = ServerDescriptor.createStandalone(conn, filter);
      suffixes.addAll(server.getReplicas());
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw(
          "Unexpected error retrieving the server configuration: "+t, t));
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
  private ReplicationCliReturnCode enableReplication(EnableReplicationUserData uData)
  {
    println();
    print(formatter.getFormattedWithPoints(INFO_REPLICATION_CONNECTING.get()));

    List<LocalizableMessage> errorMessages = new LinkedList<>();
    try (ConnectionWrapper conn1 = createAdministrativeConnection(uData.getServer1(), errorMessages);
        ConnectionWrapper conn2 = createAdministrativeConnection(uData.getServer2(), errorMessages))
    {
      if (!errorMessages.isEmpty())
      {
        errPrintLn(errorMessages);
        return ERROR_CONNECTING;
      }

      // This done is for the message informing that we are connecting.
      print(formatter.getFormattedDone());
      println();

      if (!argParser.isInteractive())
      {
        checksForNonInteractiveMode(uData, conn1, conn2, errorMessages);
        if (!errorMessages.isEmpty())
        {
          errPrintLn(errorMessages);
          return ERROR_USER_DATA;
        }
      }

      List<DN> suffixes = uData.getBaseDNs();
      checkSuffixesForEnableReplication(suffixes, conn1, conn2, false, uData);
      if (suffixes.isEmpty())
      {
        // The error messages are already displayed in the method
        // checkSuffixesForEnableReplication.
        return REPLICATION_CANNOT_BE_ENABLED_ON_BASEDN;
      }

      uData.setBaseDNs(suffixes);
      if (mustPrintCommandBuilder())
      {
        printNewCommandBuilder(ENABLE_REPLICATION_SUBCMD_NAME, uData);
      }

      if (!isInteractive())
      {
        checkReplicationServerAlreadyConfigured(conn1, uData.getServer1());
        checkReplicationServerAlreadyConfigured(conn2, uData.getServer2());
      }

      try
      {
        updateConfiguration(conn1, conn2, uData);
        printSuccessfullyEnabled(conn1, conn2);
        return SUCCESSFUL;
      }
      catch (ReplicationCliException rce)
      {
        errPrintln();
        errPrintln(getCriticalExceptionMessage(rce));
        logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
        return rce.getErrorCode();
      }
    }
  }

  private void checkReplicationServerAlreadyConfigured(ConnectionWrapper conn, EnableReplicationServerData server)
  {
    int repPort = getReplicationPort(conn);
    if (!server.configureReplicationServer() && repPort > 0)
    {
      println(INFO_REPLICATION_SERVER_CONFIGURED_WARNING.get(conn.getHostPort(), repPort));
      println();
    }
  }

  private void checksForNonInteractiveMode(EnableReplicationUserData uData,
      ConnectionWrapper conn1, ConnectionWrapper conn2, List<LocalizableMessage> errorMessages)
  {
    EnableReplicationServerData server1 = uData.getServer1();
    EnableReplicationServerData server2 = uData.getServer2();
    String host1 = server1.getHostName();
    String host2 = server2.getHostName();

    int replPort1 = checkReplicationPort(conn1, server1, errorMessages);
    int replPort2 = checkReplicationPort(conn2, server2, errorMessages);
    if (replPort1 > 0 && replPort1 == replPort2 && host1.equalsIgnoreCase(host2))
    {
      errorMessages.add(ERR_REPLICATION_SAME_REPLICATION_PORT.get(replPort1, host1));
    }

    if (argParser.skipReplicationPortCheck())
    {
      // This is something that we must do in any case... this test is
      // already included when we call SetupUtils.canUseAsPort
      checkAdminAndReplicationPortsAreDifferent(replPort1, server1, errorMessages);
      checkAdminAndReplicationPortsAreDifferent(replPort2, server2, errorMessages);
    }
  }

  private int checkReplicationPort(
      ConnectionWrapper conn, EnableReplicationServerData server, List<LocalizableMessage> errorMessages)
  {
    int replPort = getReplicationPort(conn);
    boolean hasReplicationPort = replPort > 0;
    if (replPort < 0 && server.configureReplicationServer())
    {
      replPort = server.getReplicationPort();
    }
    boolean checkReplicationPort = replPort > 0;
    if (!hasReplicationPort
        && checkReplicationPort
        && !argParser.skipReplicationPortCheck()
        && server.configureReplicationServer()
        && isLocalHost(server.getHostName())
        && !SetupUtils.canUseAsPort(replPort))
    {
      errorMessages.add(getCannotBindToPortError(replPort));
    }
    return replPort;
  }

  private void checkAdminAndReplicationPortsAreDifferent(
      int replPort, EnableReplicationServerData server, List<LocalizableMessage> errorMessages)
  {
    if (replPort > 0 && replPort == server.getPort())
    {
      errorMessages.add(ERR_REPLICATION_PORT_AND_REPLICATION_PORT_EQUAL.get(server.getHostName(), replPort));
    }
  }

  private void printSuccessfullyEnabled(ConnectionWrapper conn1, ConnectionWrapper conn2)
  {
    long time1 = getServerClock(conn1);
    long time2 = getServerClock(conn2);
    if (time1 != -1
        && time2 != -1
        && Math.abs(time1 - time2) > Installer.THRESHOLD_CLOCK_DIFFERENCE_WARNING * 60 * 1000)
    {
      println(INFO_WARNING_SERVERS_CLOCK_DIFFERENCE.get(conn1.getHostPort(), conn2.getHostPort(),
            Installer.THRESHOLD_CLOCK_DIFFERENCE_WARNING));
    }
    println();
    println(INFO_REPLICATION_POST_ENABLE_INFO.get("dsreplication", INITIALIZE_REPLICATION_SUBCMD_NAME));
    println();
  }

  private void errPrintLn(List<LocalizableMessage> errorMessages)
  {
    for (LocalizableMessage msg : errorMessages)
    {
      errPrintln();
      errPrintln(msg);
    }
  }

  private ConnectionWrapper createAdministrativeConnection(EnableReplicationServerData server,
      List<LocalizableMessage> errorMessages)
  {
    try
    {
      return new ConnectionWrapper(server.getHostPort(), connectionType, server.getBindDn(), server.getPwd(),
          getConnectTimeout(), getTrustManager(sourceServerCI));
    }
    catch (LdapException e)
    {
      errorMessages.add(getMessageForException(e, server.getHostPort().toString()));
      logger.error(LocalizableMessage.raw("Error when creating connection for:" + server.getHostPort()));
      return null;
    }
  }

  /**
   * Disables the replication in the server for the provided suffixes using the
   * data in the DisableReplicationUserData object.  This method does not prompt
   * to the user for information if something is missing.
   * @param uData the DisableReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode disableReplication(DisableReplicationUserData uData)
  {
    print(formatter.getFormattedWithPoints(INFO_REPLICATION_CONNECTING.get()));
    DN bindDn = uData.getAdminUid() != null
        ? getAdministratorDN(uData.getAdminUid())
        : uData.getBindDn();

    ConnectionWrapper conn = createAdministrativeConnection(uData, bindDn);
    if (conn == null)
    {
      return ERROR_CONNECTING;
    }

    try
    {
      // This done is for the message informing that we are connecting.
      print(formatter.getFormattedDone());
      println();

      List<DN> suffixes = uData.getBaseDNs();
      checkSuffixesForDisableReplication(suffixes, conn, false, !uData.disableReplicationServer());
      if (suffixes.isEmpty() && !uData.disableReplicationServer() && !uData.disableAll())
      {
        return REPLICATION_CANNOT_BE_DISABLED_ON_BASEDN;
      }
      uData.setBaseDNs(suffixes);

      if (!isInteractive())
      {
        boolean hasReplicationPort = hasReplicationPort(conn);
        if (uData.disableAll() && hasReplicationPort)
        {
          uData.setDisableReplicationServer(true);
        }
        else if (uData.disableReplicationServer() && !hasReplicationPort && !uData.disableAll())
        {
          uData.setDisableReplicationServer(false);
          println(
              INFO_REPLICATION_WARNING_NO_REPLICATION_SERVER_TO_DISABLE.get(conn.getHostPort()));
          println();
        }
      }

      if (mustPrintCommandBuilder())
      {
        printNewCommandBuilder(DISABLE_REPLICATION_SUBCMD_NAME, uData);
      }

      if (!isInteractive() && !uData.disableReplicationServer() && !uData.disableAll()
          && disableAllBaseDns(conn, uData) && hasReplicationPort(conn))
      {
        // Inform the user that the replication server will not be disabled.
        // Inform also of the user of the disableReplicationServerArg
        println(INFO_REPLICATION_DISABLE_ALL_SUFFIXES_KEEP_REPLICATION_SERVER.get(
            conn.getHostPort(),
            argParser.disableReplicationServerArg.getLongIdentifier(),
            argParser.disableAllArg.getLongIdentifier()));
      }
      try
      {
        updateConfiguration(conn, uData);
        return SUCCESSFUL;
      }
      catch (ReplicationCliException rce)
      {
        errPrintln();
        errPrintln(getCriticalExceptionMessage(rce));
        logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
        return rce.getErrorCode();
      }
    }
    finally
    {
      close(conn);
    }
  }

  /**
   * Displays the replication status of the baseDNs specified in the
   * StatusReplicationUserData object.  This method does not prompt
   * to the user for information if something is missing.
   * @param uData the StatusReplicationUserData object.
   * @return ReplicationCliReturnCode.SUCCESSFUL if the operation was
   * successful and an error code otherwise.
   */
  private ReplicationCliReturnCode statusReplication(StatusReplicationUserData uData)
  {
    final ConnectionWrapper conn = createAdministrativeConnection(uData);
    if (conn == null)
    {
      return ERROR_CONNECTING;
    }

    try
    {
      displayStatus(conn, uData);
      return SUCCESSFUL;
    }
    catch (ReplicationCliException rce)
    {
      errPrintln();
      errPrintln(getCriticalExceptionMessage(rce));
      logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
      return rce.getErrorCode();
    }
    finally
    {
      close(conn);
    }
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
  private ReplicationCliReturnCode initializeReplication(SourceDestinationServerUserData uData)
  {
    try (ConnectionWrapper connSource = createAdministrativeConnection(uData, uData.getSource());
        ConnectionWrapper connDestination = createAdministrativeConnection(uData, uData.getDestination()))
    {
      if (connSource == null || connDestination == null)
      {
        return ERROR_CONNECTING;
      }

      List<DN> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, connSource, connDestination, false);
      if (baseDNs.isEmpty())
      {
        return REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
      if (mustPrintCommandBuilder())
      {
        uData.setBaseDNs(baseDNs);
        printNewCommandBuilder(INITIALIZE_REPLICATION_SUBCMD_NAME, uData);
      }

      ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
      for (DN baseDN : baseDNs)
      {
        try
        {
          println();
          print(formatter.getFormattedProgress(
              INFO_PROGRESS_INITIALIZING_SUFFIX.get(baseDN, connSource.getHostPort())));
          println();
          initializeSuffix(baseDN, connSource, connDestination, true);
          returnValue = SUCCESSFUL;
        }
        catch (ReplicationCliException rce)
        {
          errPrintln();
          errPrintln(getCriticalExceptionMessage(rce));
          returnValue = rce.getErrorCode();
          logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
        }
      }
      return returnValue;
    }
  }

  private ConnectionWrapper createAdministrativeConnection(SourceDestinationServerUserData uData, HostPort server)
  {
    try
    {
      return new ConnectionWrapper(server, connectionType, getAdministratorDN(uData.getAdminUid()),
          uData.getAdminPwd(), getConnectTimeout(), getTrustManager(sourceServerCI));
    }
    catch (LdapException ne)
    {
      errPrintln();
      errPrintln(getMessageForException(ne, server.toString()));
      logger.error(LocalizableMessage.raw("Complete error stack:"), ne);
      return null;
    }
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
    final ConnectionWrapper conn = createAdministrativeConnection(uData);
    if (conn == null)
    {
      return ERROR_CONNECTING;
    }

    try
    {
      List<DN> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, conn, false);
      if (baseDNs.isEmpty())
      {
        return REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
      if (mustPrintCommandBuilder())
      {
        uData.setBaseDNs(baseDNs);
        printNewCommandBuilder(INITIALIZE_ALL_REPLICATION_SUBCMD_NAME, uData);
      }

      ReplicationCliReturnCode returnValue = SUCCESSFUL_NOP;
      for (DN baseDN : baseDNs)
      {
        try
        {
          println();
          print(formatter.getFormattedProgress(INFO_PROGRESS_INITIALIZING_SUFFIX.get(baseDN, conn.getHostPort())));
          println();
          initializeAllSuffix(baseDN, conn, true);
          returnValue = SUCCESSFUL;
        }
        catch (ReplicationCliException rce)
        {
          errPrintln();
          errPrintln(getCriticalExceptionMessage(rce));
          returnValue = rce.getErrorCode();
          logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
        }
      }
      return returnValue;
    }
    finally
    {
      close(conn);
    }
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
  private ReplicationCliReturnCode preExternalInitialization(PreExternalInitializationUserData uData)
  {
    ConnectionWrapper conn = createAdministrativeConnection(uData);
    if (conn == null)
    {
      return ERROR_CONNECTING;
    }

    try
    {
      List<DN> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, conn, false);
      if (baseDNs.isEmpty())
      {
        return REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
      if (mustPrintCommandBuilder())
      {
        uData.setBaseDNs(baseDNs);
        printNewCommandBuilder(PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME, uData);
      }

      ReplicationCliReturnCode returnValue = SUCCESSFUL;
      for (DN baseDN : baseDNs)
      {
        try
        {
          println();
          print(formatter.getFormattedWithPoints(INFO_PROGRESS_PRE_EXTERNAL_INITIALIZATION.get(baseDN)));
          preExternalInitialization(baseDN, conn);
          print(formatter.getFormattedDone());
          println();
        }
        catch (ReplicationCliException rce)
        {
          errPrintln();
          errPrintln(getCriticalExceptionMessage(rce));
          returnValue = rce.getErrorCode();
          logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
        }
      }
      println();
      print(INFO_PROGRESS_PRE_INITIALIZATION_FINISHED_PROCEDURE.get(POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
      println();
      return returnValue;
    }
    finally
    {
      close(conn);
    }
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
  private ReplicationCliReturnCode postExternalInitialization(PostExternalInitializationUserData uData)
  {
    ConnectionWrapper conn = createAdministrativeConnection(uData);
    if (conn == null)
    {
      return ERROR_CONNECTING;
    }

    try
    {
      List<DN> baseDNs = uData.getBaseDNs();
      checkSuffixesForInitializeReplication(baseDNs, conn, false);
      if (baseDNs.isEmpty())
      {
        return REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN;
      }
      if (mustPrintCommandBuilder())
      {
        uData.setBaseDNs(baseDNs);
        printNewCommandBuilder(POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME, uData);
      }

      ReplicationCliReturnCode returnValue = SUCCESSFUL;
      for (DN baseDN : baseDNs)
      {
        try
        {
          println();
          print(formatter.getFormattedWithPoints(INFO_PROGRESS_POST_EXTERNAL_INITIALIZATION.get(baseDN)));
          postExternalInitialization(baseDN, conn);
          println(formatter.getFormattedDone());
          println();
        }
        catch (ReplicationCliException rce)
        {
          errPrintln();
          errPrintln(getCriticalExceptionMessage(rce));
          returnValue = rce.getErrorCode();
          logger.error(LocalizableMessage.raw("Complete error stack:"), rce);
        }
      }
      println();
      print(INFO_PROGRESS_POST_INITIALIZATION_FINISHED_PROCEDURE.get());
      println();
      return returnValue;
    }
    finally
    {
      close(conn);
    }
  }

  /**
   * Checks that replication can actually be enabled in the provided baseDNs
   * for the two servers.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be enabled and with the
   * base DNs that the user provided interactively.
   * @param conn1 connection to the first server.
   * @param conn2 connection to the second server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be enabled.
   * @param uData the user data.  This object will not be updated by this method
   * but it is assumed that it contains information about whether the
   * replication domains must be configured or not.
   */
  private void checkSuffixesForEnableReplication(Collection<DN> suffixes,
      ConnectionWrapper conn1, ConnectionWrapper conn2,
      boolean interactive, EnableReplicationUserData uData)
  {
    EnableReplicationServerData server1 = uData.getServer1();
    EnableReplicationServerData server2 = uData.getServer2();
    final TreeSet<DN> availableSuffixes = new TreeSet<>();
    final TreeSet<DN> alreadyReplicatedSuffixes = new TreeSet<>();
    if (server1.configureReplicationDomain() &&
        server2.configureReplicationDomain())
    {
      availableSuffixes.addAll(getCommonSuffixes(conn1, conn2, SuffixRelationType.NOT_FULLY_REPLICATED));
      alreadyReplicatedSuffixes.addAll(getCommonSuffixes(conn1, conn2, SuffixRelationType.FULLY_REPLICATED));
    }
    else if (server1.configureReplicationDomain())
    {
      updateAvailableAndReplicatedSuffixesForOneDomain(conn1, conn2,
          availableSuffixes, alreadyReplicatedSuffixes);
    }
    else if (server2.configureReplicationDomain())
    {
      updateAvailableAndReplicatedSuffixesForOneDomain(conn2, conn1,
          availableSuffixes, alreadyReplicatedSuffixes);
    }
    else
    {
      updateAvailableAndReplicatedSuffixesForNoDomain(conn1, conn2,
          availableSuffixes, alreadyReplicatedSuffixes);
    }

    if (availableSuffixes.isEmpty())
    {
      println();
      if (!server1.configureReplicationDomain() &&
          !server1.configureReplicationDomain() &&
          alreadyReplicatedSuffixes.isEmpty())
      {
        // Use a clarifying message: there is no replicated base DN.
        errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION_NO_DOMAIN.get());
      }
      else
      {
        errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION.get());
      }

      List<DN> userProvidedSuffixes = toDNs(argParser.getBaseDNs());
      TreeSet<DN> userProvidedReplicatedSuffixes = new TreeSet<>();

      for (DN s1 : userProvidedSuffixes)
      {
        for (DN s2 : alreadyReplicatedSuffixes)
        {
          if (s1.equals(s2))
          {
            userProvidedReplicatedSuffixes.add(s1);
          }
        }
      }
      if (!userProvidedReplicatedSuffixes.isEmpty())
      {
        println();
        println(INFO_ALREADY_REPLICATED_SUFFIXES.get(toSingleLine(userProvidedReplicatedSuffixes)));
      }
      suffixes.clear();
    }
    else
    {
      //  Verify that the provided suffixes are configured in the servers.
      TreeSet<DN> notFound = new TreeSet<>();
      TreeSet<DN> alreadyReplicated = new TreeSet<>();
      determineSuffixesNotFoundAndAlreadyNotReplicated(
          suffixes, availableSuffixes, alreadyReplicatedSuffixes, notFound,alreadyReplicated);
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyReplicated);
      if (!notFound.isEmpty())
      {
        errPrintln();
        errPrintln(ERR_REPLICATION_ENABLE_SUFFIXES_NOT_FOUND.get(toSingleLine(notFound)));
      }
      if (!alreadyReplicated.isEmpty())
      {
        println();
        println(INFO_ALREADY_REPLICATED_SUFFIXES.get(toSingleLine(alreadyReplicated)));
      }
      if (interactive)
      {
        askConfirmations(suffixes, availableSuffixes,
            ERR_NO_SUFFIXES_AVAILABLE_TO_ENABLE_REPLICATION,
            ERR_NO_SUFFIXES_SELECTED_TO_REPLICATE,
            INFO_REPLICATION_ENABLE_SUFFIX_PROMPT);
      }
    }
  }

  /**
   * Checks that replication can actually be disabled in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be disabled and with the
   * base DNs that the user provided interactively.
   * @param conn connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be disabled.
   * @param displayErrors whether to display errors or not.
   */
  private void checkSuffixesForDisableReplication(Collection<DN> suffixes,
      ConnectionWrapper conn, boolean interactive, boolean displayErrors)
  {
    // whether the user must provide base DNs or not
    // (if it is {@code false} the user will be proposed the suffixes only once)
    final boolean areSuffixRequired = displayErrors;

    TreeSet<DN> availableSuffixes = new TreeSet<>();
    TreeSet<DN> notReplicatedSuffixes = new TreeSet<>();

    partitionReplicasByReplicated(getReplicas(conn), availableSuffixes, notReplicatedSuffixes);
    if (availableSuffixes.isEmpty())
    {
      if (displayErrors)
      {
        errPrintln();
        errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_DISABLE_REPLICATION.get());
      }
      List<DN> userProvidedSuffixes = toDNs(argParser.getBaseDNs());
      TreeSet<DN> userProvidedNotReplicatedSuffixes = new TreeSet<>();
      for (DN s1 : userProvidedSuffixes)
      {
        for (DN s2 : notReplicatedSuffixes)
        {
          if (s1.equals(s2))
          {
            userProvidedNotReplicatedSuffixes.add(s1);
          }
        }
      }
      if (!userProvidedNotReplicatedSuffixes.isEmpty() && displayErrors)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
            toSingleLine(userProvidedNotReplicatedSuffixes)));
      }
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<DN> notFound = new TreeSet<>();
      TreeSet<DN> alreadyNotReplicated = new TreeSet<>();
      determineSuffixesNotFoundAndAlreadyNotReplicated(
          suffixes, availableSuffixes, notReplicatedSuffixes, notFound, alreadyNotReplicated);
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (!notFound.isEmpty() && displayErrors)
      {
        errPrintln();
        errPrintln(ERR_REPLICATION_DISABLE_SUFFIXES_NOT_FOUND.get(toSingleLine(notFound)));
      }
      if (!alreadyNotReplicated.isEmpty() && displayErrors)
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(toSingleLine(alreadyNotReplicated)));
      }
      if (interactive)
      {
        while (suffixes.isEmpty())
        {
          if (containsOnlySchemaOrAdminSuffix(availableSuffixes))
          {
            // In interactive mode we do not propose to manage the administration suffix.
            if (displayErrors)
            {
              errPrintln();
              errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_DISABLE_REPLICATION.get());
            }
            break;
          }

          if (areSuffixRequired)
          {
            errPrintln();
            errPrintln(ERR_NO_SUFFIXES_SELECTED_TO_DISABLE.get());
          }
          boolean confirmationLimitReached =
              askConfirmations(INFO_REPLICATION_DISABLE_SUFFIX_PROMPT, availableSuffixes, suffixes);
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

  private boolean askConfirmations(Arg1<Object> confirmationMsg,
      Collection<DN> availableSuffixes, Collection<DN> suffixes)
  {
    for (DN dn : availableSuffixes)
    {
      if (!isSchemaOrInternalAdminSuffix(dn))
      {
        try
        {
          if (askConfirmation(confirmationMsg.get(dn), true, logger))
          {
            suffixes.add(dn);
          }
        }
        catch (ClientException ce)
        {
          errPrintln(ce.getMessageObject());
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks that replication can actually be initialized in the provided baseDNs
   * for the server.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be initialized and with the
   * base DNs that the user provided interactively.
   * @param conn connection to the server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be initialized.
   */
  private void checkSuffixesForInitializeReplication(
      Collection<DN> suffixes, ConnectionWrapper conn, boolean interactive)
  {
    TreeSet<DN> availableSuffixes = new TreeSet<>();
    TreeSet<DN> notReplicatedSuffixes = new TreeSet<>();

    partitionReplicasByReplicated(getReplicas(conn), availableSuffixes, notReplicatedSuffixes);
    if (availableSuffixes.isEmpty())
    {
      println();
      if (argParser.isInitializeAllReplicationSubcommand())
      {
        errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_ALL_REPLICATION.get());
      }
      else
      {
        errPrintln(
            ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_LOCAL_REPLICATION.get());
      }
      List<DN> userProvidedSuffixes = toDNs(argParser.getBaseDNs());
      TreeSet<DN> userProvidedNotReplicatedSuffixes = new TreeSet<>();
      for (DN s1 : userProvidedSuffixes)
      {
        for (DN s2 : notReplicatedSuffixes)
        {
          if (s1.equals(s2))
          {
            userProvidedNotReplicatedSuffixes.add(s1);
          }
        }
      }
      if (!userProvidedNotReplicatedSuffixes.isEmpty())
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(
            toSingleLine(userProvidedNotReplicatedSuffixes)));
      }
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      TreeSet<DN> notFound = new TreeSet<>();
      TreeSet<DN> alreadyNotReplicated = new TreeSet<>();
      determineSuffixesNotFoundAndAlreadyNotReplicated(
          suffixes, availableSuffixes, notReplicatedSuffixes, notFound, alreadyNotReplicated);
      suffixes.removeAll(notFound);
      suffixes.removeAll(alreadyNotReplicated);
      if (!notFound.isEmpty())
      {
        errPrintln();
        errPrintln(ERR_REPLICATION_INITIALIZE_LOCAL_SUFFIXES_NOT_FOUND.get(toSingleLine(notFound)));
      }
      if (!alreadyNotReplicated.isEmpty())
      {
        println();
        println(INFO_ALREADY_NOT_REPLICATED_SUFFIXES.get(toSingleLine(alreadyNotReplicated)));
      }
      if (interactive)
      {
        boolean confirmationLimitReached = false;
        while (suffixes.isEmpty())
        {
          println();
          if (containsOnlySchemaOrAdminSuffix(availableSuffixes))
          {
            // In interactive mode we do not propose to manage the administration suffix.
            if (argParser.isInitializeAllReplicationSubcommand())
            {
              errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_ALL_REPLICATION.get());
            }
            else
            {
              errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_LOCAL_REPLICATION.get());
            }
            break;
          }
          else
          {
            if (argParser.isInitializeAllReplicationSubcommand())
            {
              errPrintln(ERR_NO_SUFFIXES_SELECTED_TO_INITIALIZE_ALL.get());
            }
            else if (argParser.isPreExternalInitializationSubcommand())
            {
              errPrintln(ERR_NO_SUFFIXES_SELECTED_TO_PRE_EXTERNAL_INITIALIZATION.get());
            }
            else if (argParser.isPostExternalInitializationSubcommand())
            {
              errPrintln(ERR_NO_SUFFIXES_SELECTED_TO_POST_EXTERNAL_INITIALIZATION.get());
            }

            for (DN dn : availableSuffixes)
            {
              if (!isSchemaOrInternalAdminSuffix(dn))
              {
                boolean addSuffix;
                try
                {
                  if (argParser.isPreExternalInitializationSubcommand())
                  {
                    addSuffix = askConfirmation(
                    INFO_REPLICATION_PRE_EXTERNAL_INITIALIZATION_SUFFIX_PROMPT.
                        get(dn), true, logger);
                  }
                  else if (argParser.isPostExternalInitializationSubcommand())
                  {
                    addSuffix = askConfirmation(
                    INFO_REPLICATION_POST_EXTERNAL_INITIALIZATION_SUFFIX_PROMPT.
                        get(dn), true, logger);
                  }
                  else
                  {
                    addSuffix = askConfirmation(
                        INFO_REPLICATION_INITIALIZE_ALL_SUFFIX_PROMPT.get(dn),
                        true, logger);
                  }
                }
                catch (ClientException ce)
                {
                  errPrintln(ce.getMessageObject());
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

  private String toSingleLine(Collection<?> notFound)
  {
    return joinAsString(Constants.LINE_SEPARATOR, notFound);
  }

  /**
   * Checks that we can initialize the provided baseDNs between the two servers.
   * @param suffixes the suffixes provided by the user.  This Collection is
   * updated by removing the base DNs that cannot be enabled and with the
   * base DNs that the user provided interactively.
   * @param connSource connection to the source server.
   * @param connDestination connection to the destination server.
   * @param interactive whether to ask the user to provide interactively
   * base DNs if none of the provided base DNs can be initialized.
   */
  private void checkSuffixesForInitializeReplication(Collection<DN> suffixes, ConnectionWrapper connSource,
      ConnectionWrapper connDestination, boolean interactive)
  {
    TreeSet<DN> availableSuffixes = new TreeSet<>(
        getCommonSuffixes(connSource, connDestination, SuffixRelationType.REPLICATED));
    if (availableSuffixes.isEmpty())
    {
      errPrintln();
      errPrintln(ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_REPLICATION.get());
      suffixes.clear();
    }
    else
    {
      // Verify that the provided suffixes are configured in the servers.
      LinkedList<DN> notFound = new LinkedList<>();
      for (DN dn : suffixes)
      {
        if (!availableSuffixes.contains(dn))
        {
          notFound.add(dn);
        }
      }
      suffixes.removeAll(notFound);
      if (!notFound.isEmpty())
      {
        errPrintln();
        errPrintln(ERR_SUFFIXES_CANNOT_BE_INITIALIZED.get(toSingleLine(notFound)));
      }
      if (interactive)
      {
        askConfirmations(suffixes, availableSuffixes,
            ERR_NO_SUFFIXES_AVAILABLE_TO_INITIALIZE_REPLICATION,
            ERR_NO_SUFFIXES_SELECTED_TO_INITIALIZE,
            INFO_REPLICATION_INITIALIZE_SUFFIX_PROMPT);
      }
    }
  }

  /**
   * Updates the configuration in the two servers (and in other servers if
   * they are referenced) to enable replication.
   * @param conn1 the connection to the first server.
   * @param conn2 the connection to the second server.
   * @param uData the EnableReplicationUserData object containing the required
   * parameters to update the configuration.
   * @throws ReplicationCliException if there is an error.
   */
  private void updateConfiguration(ConnectionWrapper conn1, ConnectionWrapper conn2, EnableReplicationUserData uData)
      throws ReplicationCliException
  {
    final TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    filter.addBaseDNToSearch(ADSContext.getAdministrationSuffixDN());
    filter.addBaseDNToSearch(Constants.SCHEMA_DN);
    filter.addBaseDNsToSearch(uData.getBaseDNs());

    final ServerDescriptor serverDesc1 = createStandalone(conn1, filter);
    final ServerDescriptor serverDesc2 = createStandalone(conn2, filter);

    final ADSContext adsCtx1 = new ADSContext(conn1);
    final ADSContext adsCtx2 = new ADSContext(conn2);

    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found in the already registered servers.
      printErrorWhenServersAlreadyHaveErrors(conn1, conn2, uData, adsCtx1, adsCtx2);
    }
    warnIfOnlyOneReplicationServerInTopology(uData, adsCtx1, adsCtx2);


    // These are used to identify which server we use to initialize
    // the contents of the other server (if any).
    ConnectionWrapper connSource = null;
    ConnectionWrapper connDestination = null;
    ADSContext adsCtxSource = null;

    boolean adsAlreadyReplicated = false;
    boolean adsMergeDone = false;

    print(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_UPDATING_ADS_CONTENTS.get()));
    try
    {
      if (adsCtx1.hasAdminData() && adsCtx2.hasAdminData())
      {
        Set<Map<ServerProperty, Object>> registry1 = adsCtx1.readServerRegistry();
        Set<Map<ServerProperty, Object>> registry2 = adsCtx2.readServerRegistry();
        if (registry2.size() <= 1)
        {
          registerServers(adsCtx1, serverDesc2, serverDesc1, uData);

          connSource = conn1;
          connDestination = conn2;
          adsCtxSource = adsCtx1;
        }
        else if (registry1.size() <= 1)
        {
          registerServers(adsCtx2, serverDesc1, serverDesc2, uData);

          connSource = conn2;
          connDestination = conn1;
          adsCtxSource = adsCtx2;
        }
        else if (!areEqual(registry1, registry2))
        {
          print(formatter.getFormattedDone());
          println();

          boolean isFirstSource = mergeRegistries(adsCtx1, adsCtx2);
          connSource = isFirstSource ? conn1 : conn2;
          adsMergeDone = true;
        }
        else
        {
          // They are already replicated: nothing to do in terms of ADS
          // initialization or ADS update data
          DN adminDataSuffix = ADSContext.getAdministrationSuffixDN();
          adsAlreadyReplicated = isBaseDNReplicated(serverDesc1, serverDesc2, adminDataSuffix);

          if (!adsAlreadyReplicated)
          {
            // Try to merge if both are replicated
            boolean isADS1Replicated = isBaseDNReplicated(serverDesc1, adminDataSuffix);
            boolean isADS2Replicated = isBaseDNReplicated(serverDesc2, adminDataSuffix);
            if (isADS1Replicated && isADS2Replicated)
            {
              // Merge
              print(formatter.getFormattedDone());
              println();

              boolean isFirstSource = mergeRegistries(adsCtx1, adsCtx2);
              connSource = isFirstSource ? conn1 : conn2;
              adsMergeDone = true;
            }
            else if (isADS1Replicated || !isADS2Replicated)
            {
              registerServers(adsCtx1, serverDesc2, serverDesc1, uData);

              connSource = conn1;
              connDestination = conn2;
              adsCtxSource = adsCtx1;
            }
            else if (isADS2Replicated)
            {
              registerServers(adsCtx2, serverDesc1, serverDesc2, uData);

              connSource = conn2;
              connDestination = conn1;
              adsCtxSource = adsCtx2;
            }
          }
        }
      }
      else if (!adsCtx1.hasAdminData() && adsCtx2.hasAdminData())
      {
        registerServers(adsCtx2, serverDesc1, serverDesc2, uData);

        connSource = conn2;
        connDestination = conn1;
        adsCtxSource = adsCtx2;
      }
      else if (adsCtx1.hasAdminData() && !adsCtx2.hasAdminData())
      {
        registerServers(adsCtx1, serverDesc2, serverDesc1, uData);

        connSource = conn1;
        connDestination = conn2;
        adsCtxSource = adsCtx1;
      }
      else
      {
        adsCtx1.createAdminData(null);
        if (!hasAdministrator(conn1, uData))
        {
          // This could occur if the user created an administrator without
          // registering any server.
          adsCtx1.createAdministrator(getAdministratorProperties(uData));
        }
        serverDesc1.updateAdsPropertiesWithServerProperties();
        adsCtx1.registerServer(serverDesc1.getAdsProperties());
        serverDesc2.updateAdsPropertiesWithServerProperties();
        adsCtx1.registerServer(serverDesc2.getAdsProperties());

        connSource = conn1;
        connDestination = conn2;
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
        ServerDescriptor.seedAdsTrustStore(connDestination, adsCtxSource.getTrustedCertificates());
      }
      catch (Throwable t)
      {
        logger.error(LocalizableMessage.raw("Error seeding truststores: "+t, t));
        throw new ReplicationCliException(
            ERR_REPLICATION_ENABLE_SEEDING_TRUSTSTORE.get(connDestination.getHostPort(),
            adsCtxSource.getHostPort(), toString(t)),
            ERROR_SEEDING_TRUSTORE, t);
      }
    }
    if (!adsMergeDone)
    {
      print(formatter.getFormattedDone());
      println();
    }
    if (!adsAlreadyReplicated)
    {
      uData.addBaseDN(ADSContext.getAdministrationSuffixDN());
    }
    if (uData.replicateSchema())
    {
      uData.addBaseDN(Constants.SCHEMA_DN);
    }

    final Set<Integer> usedReplicationServerIds = new HashSet<>();
    final TopologyCache cache1;
    final TopologyCache cache2;
    try
    {
      Set<PreferredConnection> cnx = new LinkedHashSet<>();
      cnx.addAll(getPreferredConnections(conn1));
      cnx.addAll(getPreferredConnections(conn2));
      cache1 = createTopologyCacheOrNull(adsCtx1, cnx, uData);
      if (cache1 != null)
      {
        usedReplicationServerIds.addAll(getReplicationServerIds(cache1));
      }
      cache2 = createTopologyCacheOrNull(adsCtx2, cnx, uData);
      if (cache1 != null)
      {
        usedReplicationServerIds.addAll(getReplicationServerIds(cache1));
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

    final Set<HostPort> twoReplServers = new LinkedHashSet<>();
    addToSets(serverDesc1, uData.getServer1(), conn1, twoReplServers, usedReplicationServerIds);
    addToSets(serverDesc2, uData.getServer2(), conn2, twoReplServers, usedReplicationServerIds);

    final Map<DN, Set<HostPort>> hmRepServers = new HashMap<>();
    final Map<DN, Set<Integer>> hmUsedReplicaServerIds = new HashMap<>();
    for (DN baseDN : uData.getBaseDNs())
    {
      Set<HostPort> repServersForBaseDN = new LinkedHashSet<>();
      repServersForBaseDN.addAll(getReplicationServers(baseDN, cache1, serverDesc1));
      repServersForBaseDN.addAll(getReplicationServers(baseDN, cache2, serverDesc2));
      repServersForBaseDN.addAll(twoReplServers);
      hmRepServers.put(baseDN, repServersForBaseDN);

      Set<Integer> replicaServerIds = new HashSet<>();
      addReplicaServerIds(replicaServerIds, serverDesc1, baseDN);
      addReplicaServerIds(replicaServerIds, serverDesc2, baseDN);
      if (cache1 != null)
      {
        for (ServerDescriptor server : cache1.getServers())
        {
          addReplicaServerIds(replicaServerIds, server, baseDN);
        }
      }
      if (cache2 != null)
      {
        for (ServerDescriptor server : cache2.getServers())
        {
          addReplicaServerIds(replicaServerIds, server, baseDN);
        }
      }
      hmUsedReplicaServerIds.put(baseDN, replicaServerIds);
    }

    final Set<HostPort> allRepServers = new LinkedHashSet<>();
    for (Set<HostPort> v : hmRepServers.values())
    {
      allRepServers.addAll(v);
    }

    Set<String> alreadyConfiguredReplicationServers = new HashSet<>();
    configureServer(conn1, serverDesc1, uData.getServer1(), argParser.server1.replicationPortArg,
        usedReplicationServerIds, allRepServers, alreadyConfiguredReplicationServers,
        WARN_FIRST_REPLICATION_SERVER_ALREADY_CONFIGURED);
    configureServer(conn2, serverDesc2, uData.getServer2(), argParser.server2.replicationPortArg,
        usedReplicationServerIds, allRepServers, alreadyConfiguredReplicationServers,
        WARN_SECOND_REPLICATION_SERVER_ALREADY_CONFIGURED);

    for (DN baseDN : uData.getBaseDNs())
    {
      Set<HostPort> repServers = hmRepServers.get(baseDN);
      Set<Integer> usedIds = hmUsedReplicaServerIds.get(baseDN);
      Set<String> alreadyConfiguredServers = new HashSet<>();

      configureToReplicateBaseDN(uData.getServer1(), conn1, serverDesc1, cache1, baseDN,
          usedIds, alreadyConfiguredServers, repServers, allRepServers, alreadyConfiguredReplicationServers);
      configureToReplicateBaseDN(uData.getServer2(), conn2, serverDesc2, cache2, baseDN,
          usedIds, alreadyConfiguredServers, repServers, allRepServers, alreadyConfiguredReplicationServers);
    }

    // Now that replication is configured in all servers, simply try to
    // initialize the contents of one ADS with the other (in the case where
    // already both servers were replicating the same ADS there is nothing to be done).
    if (adsMergeDone)
    {
      PointAdder pointAdder = new PointAdder(this);
      print(INFO_ENABLE_REPLICATION_INITIALIZING_ADS_ALL.get(connSource.getHostPort()));
      pointAdder.start();
      try
      {
        initializeAllSuffix(ADSContext.getAdministrationSuffixDN(), connSource, false);
      }
      finally
      {
        pointAdder.stop();
      }
      print(formatter.getSpace());
      print(formatter.getFormattedDone());
      println();
    }
    else if (connSource != null && connDestination != null)
    {
      print(formatter.getFormattedWithPoints(
          INFO_ENABLE_REPLICATION_INITIALIZING_ADS.get(connDestination.getHostPort(), connSource.getHostPort())));

      initializeSuffix(ADSContext.getAdministrationSuffixDN(), connSource, connDestination, false);
      print(formatter.getFormattedDone());
      println();
    }

    // If we must initialize the schema do so.
    if (mustInitializeSchema(serverDesc1, serverDesc2, uData))
    {
      if (argParser.useSecondServerAsSchemaSource())
      {
        connSource = conn2;
        connDestination = conn1;
      }
      else
      {
        connSource = conn1;
        connDestination = conn2;
      }
      if (adsMergeDone)
      {
        PointAdder pointAdder = new PointAdder(this);
        println(INFO_ENABLE_REPLICATION_INITIALIZING_SCHEMA.get(
            connDestination.getHostPort(), connSource.getHostPort()));
        pointAdder.start();
        try
        {
          initializeAllSuffix(Constants.SCHEMA_DN, connSource, false);
        }
        finally
        {
          pointAdder.stop();
        }
        print(formatter.getSpace());
      }
      else
      {
        print(formatter.getFormattedWithPoints(INFO_ENABLE_REPLICATION_INITIALIZING_SCHEMA.get(
            connDestination.getHostPort(), connSource.getHostPort())));
        initializeSuffix(Constants.SCHEMA_DN, connSource, connDestination, false);
      }
      print(formatter.getFormattedDone());
      println();
    }
  }

  private void printErrorWhenServersAlreadyHaveErrors(ConnectionWrapper conn1, ConnectionWrapper conn2,
      EnableReplicationUserData uData, final ADSContext adsCtx1, final ADSContext adsCtx2)
      throws ReplicationCliException
  {
    final Set<LocalizableMessage> messages = new LinkedHashSet<>();
    try
    {
      final Set<PreferredConnection> cnx = new LinkedHashSet<>();
      cnx.addAll(getPreferredConnections(conn1));
      cnx.addAll(getPreferredConnections(conn2));
      final TopologyCache cache1 = createTopologyCacheOrNull(adsCtx1, cnx, uData);
      if (cache1 != null)
      {
        messages.addAll(cache1.getErrorMessages());
      }
      final TopologyCache cache2 = createTopologyCacheOrNull(adsCtx2, cnx, uData);
      if (cache2 != null)
      {
        messages.addAll(cache2.getErrorMessages());
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
      errPrintln(ERR_REPLICATION_READING_REGISTERED_SERVERS_WARNING.get(
          getMessageFromCollection(messages, LINE_SEPARATOR)));
    }
  }

  private void warnIfOnlyOneReplicationServerInTopology(EnableReplicationUserData uData, final ADSContext adsCtx1,
      final ADSContext adsCtx2) throws ReplicationCliException
  {
    final Set<DN> baseDNsWithOneReplicationServer = new TreeSet<>();
    final Set<DN> baseDNsWithNoReplicationServer = new TreeSet<>();
    updateBaseDnsWithNotEnoughReplicationServer(adsCtx1, adsCtx2, uData,
       baseDNsWithNoReplicationServer, baseDNsWithOneReplicationServer);

    if (!baseDNsWithNoReplicationServer.isEmpty())
    {
      LocalizableMessage errorMsg =
        ERR_REPLICATION_NO_REPLICATION_SERVER.get(toSingleLine(baseDNsWithNoReplicationServer));
      throw new ReplicationCliException(errorMsg, ERROR_USER_DATA, null);
    }
    else if (!baseDNsWithOneReplicationServer.isEmpty())
    {
      if (isInteractive())
      {
        LocalizableMessage prompt = INFO_REPLICATION_ONLY_ONE_REPLICATION_SERVER_CONFIRM.get(
            toSingleLine(baseDNsWithOneReplicationServer));
        try
        {
          if (!confirmAction(prompt, false))
          {
            throw new ReplicationCliException(ERR_REPLICATION_USER_CANCELLED.get(), USER_CANCELLED, null);
          }
        }
        catch (Throwable t)
        {
          throw new ReplicationCliException(ERR_REPLICATION_USER_CANCELLED.get(), USER_CANCELLED, t);
        }
      }
      else
      {
        errPrintln(INFO_REPLICATION_ONLY_ONE_REPLICATION_SERVER_WARNING.get(
            toSingleLine(baseDNsWithOneReplicationServer)));
        errPrintln();
      }
    }
  }

  private void registerServers(final ADSContext adsCtx2, final ServerDescriptor serverDesc1,
      final ServerDescriptor serverDesc2, EnableReplicationUserData uData) throws ADSContextException
  {
    if (!hasAdministrator(adsCtx2.getConnection(), uData))
    {
      adsCtx2.createAdministrator(getAdministratorProperties(uData));
    }
    serverDesc1.updateAdsPropertiesWithServerProperties();
    registerServer(adsCtx2, serverDesc1.getAdsProperties());

    Set<Map<ServerProperty, Object>> registry2 = adsCtx2.readServerRegistry();
    if (!ADSContext.isRegistered(serverDesc2, registry2))
    {
      serverDesc2.updateAdsPropertiesWithServerProperties();
      registerServer(adsCtx2, serverDesc2.getAdsProperties());
    }
  }

  private void addReplicaServerIds(Set<Integer> replicaServerIds, ServerDescriptor serverDesc1, DN baseDN)
  {
    ReplicaDescriptor replica = findReplicatedReplicaForSuffixDN(serverDesc1.getReplicas(), baseDN);
    if (replica != null)
    {
      replicaServerIds.add(replica.getServerId());
    }
  }

  private void addToSets(ServerDescriptor serverDesc, EnableReplicationServerData serverData, ConnectionWrapper conn,
      final Set<HostPort> twoReplServers, final Set<Integer> usedReplicationServerIds)
  {
    if (serverDesc.isReplicationServer())
    {
      twoReplServers.add(serverDesc.getReplicationServerHostPort());
      usedReplicationServerIds.add(serverDesc.getReplicationServerId());
    }
    else if (serverData.configureReplicationServer())
    {
      twoReplServers.add(new HostPort(conn.getHostPort().getHost(), serverData.getReplicationPort()));
    }
  }

  private void configureToReplicateBaseDN(EnableReplicationServerData server, ConnectionWrapper conn,
      ServerDescriptor serverDesc, TopologyCache cache, DN baseDN, Set<Integer> usedIds,
      Set<String> alreadyConfiguredServers, Set<HostPort> repServers, final Set<HostPort> allRepServers,
      Set<String> alreadyConfiguredReplicationServers) throws ReplicationCliException
  {
    if (server.configureReplicationDomain()
        || baseDN.equals(ADSContext.getAdministrationSuffixDN()))
    {
      try
      {
        configureToReplicateBaseDN(conn, baseDN, repServers, usedIds);
      }
      catch (Exception e)
      {
        LocalizableMessage msg = getMessageForEnableException(conn.getHostPort(), baseDN);
        throw new ReplicationCliException(msg, ERROR_ENABLING_REPLICATION_ON_BASEDN, e);
      }
    }
    alreadyConfiguredServers.add(serverDesc.getId());

    if (cache != null)
    {
      configureToReplicateBaseDN(baseDN, repServers, usedIds, cache, serverDesc, alreadyConfiguredServers,
          allRepServers, alreadyConfiguredReplicationServers);
    }
  }

  private void configureServer(ConnectionWrapper conn, ServerDescriptor serverDesc,
      EnableReplicationServerData enableServer, IntegerArgument replicationPortArg,
      Set<Integer> usedReplicationServerIds, Set<HostPort> allRepServers,
      Set<String> alreadyConfiguredReplicationServers, Arg2<Number, Number> replicationServerAlreadyConfiguredMsg)
      throws ReplicationCliException
  {
    if (!serverDesc.isReplicationServer() && enableServer.configureReplicationServer())
    {
      try
      {
        configureAsReplicationServer(conn, enableServer.getReplicationPort(), enableServer.isSecureReplication(),
            allRepServers, usedReplicationServerIds);
      }
      catch (Exception ode)
      {
        throw errorConfiguringReplicationServer(conn, ode);
      }
    }
    else if (serverDesc.isReplicationServer())
    {
      try
      {
        updateReplicationServer(conn, allRepServers);
      }
      catch (Exception ode)
      {
        throw errorConfiguringReplicationServer(conn, ode);
      }
      if (replicationPortArg.isPresent() && enableServer.getReplicationPort() != serverDesc.getReplicationServerPort())
      {
        LocalizableMessage msg = replicationServerAlreadyConfiguredMsg.get(
            serverDesc.getReplicationServerPort(), enableServer.getReplicationPort());
        logger.warn(msg);
        errPrintln(msg);
      }
    }
    alreadyConfiguredReplicationServers.add(serverDesc.getId());
  }

  private ReplicationCliException errorConfiguringReplicationServer(ConnectionWrapper conn, Exception ode)
  {
    return new ReplicationCliException(
        ERR_REPLICATION_CONFIGURING_REPLICATIONSERVER.get(conn.getHostPort()),
        ERROR_CONFIGURING_REPLICATIONSERVER, ode);
  }

  private TopologyCache createTopologyCacheOrNull(ADSContext adsCtx, Set<PreferredConnection> cnx,
      ReplicationUserData uData) throws ADSContextException, TopologyCacheException
  {
    if (adsCtx.hasAdminData())
    {
      TopologyCache cache = new TopologyCache(adsCtx, getTrustManager(sourceServerCI), getConnectTimeout());
      cache.setPreferredConnections(cnx);
      cache.getFilter().setSearchMonitoringInformation(false);
      cache.getFilter().addBaseDNsToSearch(uData.getBaseDNs());
      cache.reloadTopology();
      return cache;
    }
    return null;
  }

  private ServerDescriptor createStandalone(ConnectionWrapper conn, TopologyCacheFilter filter)
      throws ReplicationCliException
  {
    try
    {
      return ServerDescriptor.createStandalone(conn, filter);
    }
    catch (IOException e)
    {
      throw new ReplicationCliException(
          getMessageForException(e, conn.getHostPort().toString()), ERROR_READING_CONFIGURATION, e);
    }
  }

  /**
   * Updates the configuration in the server (and in other servers if they are referenced) to
   * disable replication.
   *
   * @param conn
   *          the connection to the server.
   * @param uData
   *          the DisableReplicationUserData object containing the required parameters to update the
   *          configuration.
   * @throws ReplicationCliException
   *           if there is an error.
   */
  private void updateConfiguration(ConnectionWrapper conn, DisableReplicationUserData uData)
      throws ReplicationCliException
  {
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    if (!uData.disableAll())
    {
      filter.addBaseDNToSearch(ADSContext.getAdministrationSuffixDN());
      filter.addBaseDNsToSearch(uData.getBaseDNs());
    }
    ServerDescriptor server = createStandalone(conn, filter);

    ADSContext adsCtx = new ADSContext(conn);

    TopologyCache cache = null;
    // Only try to update remote server if the user provided a Global
    // Administrator to authenticate.
    boolean tryToUpdateRemote = uData.getAdminUid() != null;
    try
    {
      if (adsCtx.hasAdminData() && tryToUpdateRemote)
      {
        cache = new TopologyCache(adsCtx, getTrustManager(sourceServerCI), getConnectTimeout());
        cache.setPreferredConnections(getPreferredConnections(conn));
        cache.getFilter().setSearchMonitoringInformation(false);
        if (!uData.disableAll())
        {
          cache.getFilter().addBaseDNsToSearch(uData.getBaseDNs());
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
      Set<LocalizableMessage> messages = new LinkedHashSet<>();
      if (cache != null)
      {
        messages.addAll(cache.getErrorMessages());
      }
      if (!messages.isEmpty())
      {
        errPrintln(
            ERR_REPLICATION_READING_REGISTERED_SERVERS_WARNING.get(
                getMessageFromCollection(messages,
                    Constants.LINE_SEPARATOR)));
      }
    }

    final boolean disableReplicationServer = server.isReplicationServer()
        && (uData.disableReplicationServer() || uData.disableAll());
    if (cache != null && disableReplicationServer)
    {
      HostPort replicationServer = server.getReplicationServerHostPort();
      // Figure out if this is the last replication server for a given
      // topology (containing a different replica) or there will be only
      // another replication server left (single point of failure).
      Set<SuffixDescriptor> lastRepServer = new TreeSet<>();
      Set<SuffixDescriptor> beforeLastRepServer = new TreeSet<>();

      for (SuffixDescriptor suffix : cache.getSuffixes())
      {
        if (isSchemaOrInternalAdminSuffix(suffix.getDN()))
        {
          // Do not display these suffixes.
          continue;
        }

        Set<HostPort> repServers = suffix.getReplicationServers();
        if (repServers.size() <= 2
            && repServers.contains(replicationServer))
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

      // Inform the user
      if (!beforeLastRepServer.isEmpty())
      {
        Set<DN> baseDNs = new LinkedHashSet<>();
        for (SuffixDescriptor suffix : beforeLastRepServer)
        {
          if (!isSchemaOrInternalAdminSuffix(suffix.getDN()))
          {
            // Do not display these suffixes.
            baseDNs.add(suffix.getDN());
          }
        }
        if (!baseDNs.isEmpty())
        {
          String arg = toSingleLine(baseDNs);
          if (!isInteractive())
          {
            println(INFO_DISABLE_REPLICATION_ONE_POINT_OF_FAILURE.get(arg));
          }
          else
          {
            LocalizableMessage msg = INFO_DISABLE_REPLICATION_ONE_POINT_OF_FAILURE_PROMPT.get(arg);
            if (!askConfirmation(msg, false))
            {
              throw new ReplicationCliException(ERR_REPLICATION_USER_CANCELLED.get(), USER_CANCELLED, null);
            }
          }
        }
      }
      if (!lastRepServer.isEmpty())
      {
        // Check that there are other replicas and that this message, really
        // makes sense to be displayed.
        Set<String> suffixArg = new LinkedHashSet<>();
        for (SuffixDescriptor suffix : lastRepServer)
        {
          if (!isBaseDNSpecified(uData.getBaseDNs(), suffix.getDN()))
          {
            Set<ServerDescriptor> servers = new TreeSet<>();
            for (ReplicaDescriptor replica : suffix.getReplicas())
            {
              servers.add(replica.getServer());
            }
            suffixArg.add(getSuffixDisplay(suffix.getDN(), servers));
          }
          else if (suffix.getReplicas().size() > 1)
          {
            // If there is just one replica, it is the one in this server.
            Set<ServerDescriptor> servers = new TreeSet<>();
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

        if (!suffixArg.isEmpty())
        {
          String arg = toSingleLine(suffixArg);
          if (!isInteractive())
          {
            println(INFO_DISABLE_REPLICATION_DISABLE_IN_REMOTE.get(arg));
          }
          else
          {
            LocalizableMessage msg = INFO_DISABLE_REPLICATION_DISABLE_IN_REMOTE_PROMPT.get(arg);
            if (!askConfirmation(msg, false))
            {
              throw new ReplicationCliException(ERR_REPLICATION_USER_CANCELLED.get(), USER_CANCELLED, null);
            }
          }
        }
      }
    }

    // Try to figure out if we must explicitly disable replication on cn=admin data and cn=schema.
    boolean forceDisableSchema = false;
    boolean forceDisableADS = false;
    boolean schemaReplicated = false;
    boolean adsReplicated = false;
    boolean disableAllBaseDns = disableAllBaseDns(conn, uData);

    Collection<ReplicaDescriptor> replicas = getReplicas(conn);
    for (ReplicaDescriptor rep : replicas)
    {
      if (rep.isReplicated())
      {
        DN dn = rep.getSuffix().getDN();
        if (ADSContext.getAdministrationSuffixDN().equals(dn))
        {
          adsReplicated = true;
        }
        else if (Constants.SCHEMA_DN.equals(dn))
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
        // To be sure that the change gets propagated
        sleepCatchInterrupt(2000);
      }
      catch (ADSContextException adce)
      {
        logger.error(LocalizableMessage.raw("Error unregistering server: "+
            server.getAdsProperties(), adce));
        if (adce.getError() != ADSContextException.ErrorType.NOT_YET_REGISTERED)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_UPDATING_ADS.get(adce.getMessageObject()),
              ERROR_READING_ADS, adce);
        }
      }
    }

    Set<DN> suffixesToDisable;
    if (uData.disableAll())
    {
      suffixesToDisable = findAllReplicatedSuffixDNs(server.getReplicas());
    }
    else
    {
      suffixesToDisable = new HashSet<>(uData.getBaseDNs());

      if (disableAllBaseDns &&
          (disableReplicationServer || !server.isReplicationServer()))
      {
        forceDisableSchema = schemaReplicated;
        forceDisableADS = adsReplicated;
      }
      for (DN dn : uData.getBaseDNs())
      {
        if (ADSContext.getAdministrationSuffixDN().equals(dn))
        {
          // The user already asked this to be explicitly disabled
          forceDisableADS = false;
        }
        else if (Constants.SCHEMA_DN.equals(dn))
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

    HostPort replicationServerHostPort = server.getReplicationServerHostPort();

    for (DN baseDN : suffixesToDisable)
    {
      try
      {
        deleteReplicationDomain(conn, baseDN);
      }
      catch (OpenDsException ode)
      {
        LocalizableMessage msg = getMessageForDisableException(conn.getHostPort(), baseDN);
        throw new ReplicationCliException(msg, ERROR_DISABLING_REPLICATION_ON_BASEDN, ode);
      }
    }

    boolean replicationServerDisabled = false;
    if (replicationServerHostPort != null && cache != null)
    {
      Set<ServerDescriptor> serversToUpdate = new LinkedHashSet<>();
      Set<DN> baseDNsToUpdate = new HashSet<>(suffixesToDisable);
      for (DN baseDN : baseDNsToUpdate)
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
        for (SuffixDescriptor suffix : cache.getSuffixes())
        {
          if (suffix.getReplicationServers().contains(replicationServerHostPort))
          {
            baseDNsToUpdate.add(suffix.getDN());
            for (ReplicaDescriptor replica : suffix.getReplicas())
            {
              serversToUpdate.add(replica.getServer());
            }
          }
        }
      }
      DN bindDn = conn.getBindDn();
      String pwd = conn.getBindPassword();
      for (ServerDescriptor s : serversToUpdate)
      {
        removeReferencesInServer(s, replicationServerHostPort.toString(), bindDn, pwd,
            baseDNsToUpdate, disableReplicationServer,
            getPreferredConnections(conn));
      }

      if (disableReplicationServer)
      {
        disableReplicationServer(conn);
        replicationServerDisabled = true;
        // Wait to be sure that changes are taken into account
        // and reset the contents of the ADS.
        sleepCatchInterrupt(5000);
      }
    }
    if (disableReplicationServer && !replicationServerDisabled)
    {
      // This can happen if we could not retrieve the TopologyCache
      disableReplicationServer(conn);
      replicationServerDisabled = true;
    }

    if (uData.disableAll())
    {
      try
      {
        // Delete all contents from ADSContext.
        print(formatter.getFormattedWithPoints(
            INFO_REPLICATION_REMOVE_ADS_CONTENTS.get()));
        adsCtx.removeAdminData(false /* avoid self-disconnect */);
        print(formatter.getFormattedDone());
        println();
      }
      catch (ADSContextException adce)
      {
        logger.error(LocalizableMessage.raw("Error removing contents of cn=admin data: "+
            adce, adce));
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
        for (Map<ADSContext.ServerProperty, Object> s : adsCtx.readServerRegistry())
        {
          adsCtx.unregisterServer(s);
        }
        // To be sure that the change gets propagated
        sleepCatchInterrupt(2000);
      }
      catch (ADSContextException adce)
      {
        // This is not critical, do not send an error
        logger.warn(LocalizableMessage.raw("Error unregistering server: "+
            server.getAdsProperties(), adce));
      }
    }
  }

  private boolean isBaseDNSpecified(List<DN> baseDns, DN dnToFind)
  {
    for (DN baseDN : baseDns)
    {
      if (!isSchemaOrInternalAdminSuffix(baseDN) && baseDN.equals(dnToFind))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Displays the replication status of the different base DNs in the servers registered in the ADS.
   *
   * @param conn
   *          the connection to the server.
   * @param uData
   *          the StatusReplicationUserData object containing the required parameters to update the
   *          configuration.
   * @throws ReplicationCliException
   *           if there is an error.
   */
  private void displayStatus(ConnectionWrapper conn,
      StatusReplicationUserData uData) throws ReplicationCliException
  {
    ADSContext adsCtx = new ADSContext(conn);
    TopologyCache cache = createTopologyCache(conn, uData, adsCtx);
    if (mustPrintCommandBuilder())
    {
      printNewCommandBuilder(STATUS_REPLICATION_SUBCMD_NAME, uData);
    }
    if (!argParser.isInteractive())
    {
      // Inform the user of the potential errors that we found.
      Set<LocalizableMessage> messages = new LinkedHashSet<>(cache.getErrorMessages());
      if (!messages.isEmpty())
      {
        errPrintln(ERR_REPLICATION_STATUS_READING_REGISTERED_SERVERS.get(
            getMessageFromCollection(messages, Constants.LINE_SEPARATOR)));
      }
    }

    List<DN> userBaseDNs = uData.getBaseDNs();
    List<Set<ReplicaDescriptor>> replicaLists = new LinkedList<>();
    boolean oneReplicated = false;
    boolean displayAll = userBaseDNs.isEmpty();
    for (SuffixDescriptor suffix : cache.getSuffixes())
    {
      DN suffixDn = suffix.getDN();

      // If no base DNs where specified display all the base DNs but the schema
      // and cn=admin data.
      if (userBaseDNs.contains(suffixDn)
          || (displayAll && !isSchemaOrInternalAdminSuffix(suffixDn)))
      {
        Set<ReplicaDescriptor> suffixReplicas = suffix.getReplicas();
        if (isAnyReplicated(suffixReplicas))
        {
          oneReplicated = true;
          replicaLists.add(suffixReplicas);
        }
        else
        {
          // Check if there are already some non replicated base DNs.
          Set<ReplicaDescriptor> replicas = findNonReplicatedReplicasForSuffixDn(replicaLists, suffixDn);
          if (replicas != null)
          {
            replicas.addAll(suffixReplicas);
          }
          else
          {
            replicaLists.add(suffixReplicas);
          }
        }
      }
    }

    boolean somethingDisplayed = false;
    if (!oneReplicated && displayAll)
    {
      // Maybe there are some replication server configured...
      SortedSet<ServerDescriptor> rServers = new TreeSet<>(new ReplicationServerComparator());
      for (ServerDescriptor server : cache.getServers())
      {
        if (server.isReplicationServer())
        {
          rServers.add(server);
        }
      }
      if (!rServers.isEmpty())
      {
        displayReplicationServerStatuses(rServers, uData.isScriptFriendly(), getPreferredConnections(conn));
        somethingDisplayed = true;
      }
    }

    if (!replicaLists.isEmpty())
    {
      sort(replicaLists);
      Set<ReplicaDescriptor> replicasWithNoReplicationServer = new HashSet<>();
      Set<ServerDescriptor> serversWithNoReplica = new HashSet<>();
      displayReplicaStatuses(replicaLists, uData.isScriptFriendly(),
            getPreferredConnections(conn), cache.getServers(),
            replicasWithNoReplicationServer, serversWithNoReplica);
      somethingDisplayed = true;

      if (oneReplicated && !uData.isScriptFriendly())
      {
        println();
        println(INFO_REPLICATION_STATUS_REPLICATED_LEGEND.get());

        if (!replicasWithNoReplicationServer.isEmpty() ||
            !serversWithNoReplica.isEmpty())
        {
          println(INFO_REPLICATION_STATUS_NOT_A_REPLICATION_SERVER_LEGEND.get());
          println(INFO_REPLICATION_STATUS_NOT_A_REPLICATION_DOMAIN_LEGEND.get());
        }
        somethingDisplayed = true;
      }
    }
    if (!somethingDisplayed)
    {
      println(displayAll
          ? INFO_REPLICATION_STATUS_NO_REPLICATION_INFORMATION.get()
          : INFO_REPLICATION_STATUS_NO_BASEDNS.get());
    }
  }

  private TopologyCache createTopologyCache(ConnectionWrapper conn, StatusReplicationUserData uData, ADSContext adsCtx)
      throws ReplicationCliException
  {
    try
    {
      TopologyCache cache = new TopologyCache(adsCtx, getTrustManager(sourceServerCI), getConnectTimeout());
      cache.setPreferredConnections(getPreferredConnections(conn));
      cache.getFilter().addBaseDNsToSearch(uData.getBaseDNs());
      cache.reloadTopology();
      return cache;
    }
    catch (TopologyCacheException tce)
    {
      throw new ReplicationCliException(
          ERR_REPLICATION_READING_ADS.get(tce.getMessage()),
          ERROR_READING_TOPOLOGY_CACHE, tce);
    }
  }

  private Set<ReplicaDescriptor> findNonReplicatedReplicasForSuffixDn(List<Set<ReplicaDescriptor>> replicaLists,
      DN suffixDn)
  {
    for (Set<ReplicaDescriptor> replicas : replicaLists)
    {
      ReplicaDescriptor replica = replicas.iterator().next();
      if (!replica.isReplicated() && replica.getSuffix().getDN().equals(suffixDn))
      {
        return replicas;
      }
    }
    return null;
  }

  private boolean isAnyReplicated(Set<ReplicaDescriptor> replicas)
  {
    for (ReplicaDescriptor replica : replicas)
    {
      if (replica.isReplicated())
      {
        return true;
      }
    }
    return false;
  }

  private void sort(List<Set<ReplicaDescriptor>> replicaLists)
  {
    Collections.sort(replicaLists, new Comparator<Set<ReplicaDescriptor>>()
    {
      @Override
      public int compare(Set<ReplicaDescriptor> o1, Set<ReplicaDescriptor> o2)
      {
        DN dn1 = o1.iterator().next().getSuffix().getDN();
        DN dn2 = o2.iterator().next().getSuffix().getDN();
        return dn1.compareTo(dn2);
      }
    });
  }

  /**
   * Displays the replication status of the replicas provided.  The code assumes
   * that all the replicas have the same baseDN and that if they are replicated
   * all the replicas are replicated with each other.
   * Note: the code assumes that all the objects come from the same read of the
   * topology cache.  So comparisons in terms of pointers can be made.
   * @param orderedReplicaLists the list of replicas that we are trying to
   * display.
   * @param scriptFriendly whether to display it on script-friendly mode or not.
   * @param cnx the preferred connections used to connect to the server.
   * @param servers all the servers configured in the topology.
   * @param replicasWithNoReplicationServer the set of replicas that will be
   * updated with all the replicas that have no replication server.
   * @param serversWithNoReplica the set of servers that will be updated with
   * all the servers that act as replication server in the topology but have
   * no replica.
   */
  private void displayReplicaStatuses(
      List<Set<ReplicaDescriptor>> orderedReplicaLists,
      boolean scriptFriendly, Set<PreferredConnection> cnx,
      Set<ServerDescriptor> servers,
      Set<ReplicaDescriptor> replicasWithNoReplicationServer,
      Set<ServerDescriptor> serversWithNoReplica)
  {
    Set<ReplicaDescriptor> orderedReplicas = new LinkedHashSet<>();
    Set<ServerDescriptor> notAddedReplicationServers = new TreeSet<>(new ReplicationServerComparator());
    for (Set<ReplicaDescriptor> replicas : orderedReplicaLists)
    {
      addReplicasSortedByHostPort(orderedReplicas, replicas, cnx);
      for (ServerDescriptor server : servers)
      {
        if (server.isReplicationServer() && isRepServerNotInDomain(replicas, server))
        {
          notAddedReplicationServers.add(server);
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

    /* Table headings. */
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_SUFFIX_DN.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_SERVERPORT.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_NUMBER_ENTRIES.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_REPLICATION_ENABLED.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_DS_ID.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_RS_ID.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_REPLICATION_PORT.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_MISSING_CHANGES.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_AGE_OF_OLDEST_MISSING_CHANGE.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_SECURE.get());

    /* Table data. */
    for (ReplicaDescriptor replica : orderedReplicas)
    {
      ServerDescriptor replicaServer = replica.getServer();

      tableBuilder.startRow();
      // Suffix DN
      tableBuilder.appendCell(fromObject(replica.getSuffix().getDN()));
      // Server port
      tableBuilder.appendCell(fromObject(getHostPort2(replicaServer, cnx)));
      // Number of entries
      tableBuilder.appendCell(fromPositiveInt(replica.getEntries()));

      if (!replica.isReplicated())
      {
        tableBuilder.appendCell(EMPTY_MSG);
      }
      else
      {
        // Replication enabled
        tableBuilder.appendCell(fromBoolean(replica.isReplicationEnabled()));

        // DS instance ID
        tableBuilder.appendCell(fromInt(replica.getServerId()));

        // RS ID and port.
        if (replicaServer.isReplicationServer())
        {
          tableBuilder.appendCell(fromInt(replicaServer.getReplicationServerId()));
          tableBuilder.appendCell(fromInt(replicaServer.getReplicationServerPort()));
        }
        else
        {
          if (scriptFriendly)
          {
            tableBuilder.appendCell(EMPTY_MSG);
          }
          else
          {
            tableBuilder.appendCell(
              INFO_REPLICATION_STATUS_NOT_A_REPLICATION_SERVER_SHORT.get());
          }
          tableBuilder.appendCell(EMPTY_MSG);
          replicasWithNoReplicationServer.add(replica);
        }

        // Missing changes
        tableBuilder.appendCell(fromPositiveInt(replica.getMissingChanges()));

        // Age of oldest missing change
        tableBuilder.appendCell(fromDate(replica.getAgeOfOldestMissingChange()));

        // Secure
        if (!replicaServer.isReplicationServer())
        {
          tableBuilder.appendCell(EMPTY_MSG);
        }
        else
        {
          tableBuilder.appendCell(fromBoolean(replicaServer.isReplicationSecure()));
        }
      }
    }

    for (ServerDescriptor server : notAddedReplicationServers)
    {
      tableBuilder.startRow();
      serversWithNoReplica.add(server);

      // Suffix DN
      tableBuilder.appendCell(EMPTY_MSG);
      // Server port
      tableBuilder.appendCell(fromObject(getHostPort2(server, cnx)));
      // Number of entries
      if (scriptFriendly)
      {
        tableBuilder.appendCell(EMPTY_MSG);
      }
      else
      {
        tableBuilder.appendCell(
          INFO_REPLICATION_STATUS_NOT_A_REPLICATION_DOMAIN_SHORT.get());
      }

      // Replication enabled
      tableBuilder.appendCell(fromBoolean(true));

      // DS ID
      tableBuilder.appendCell(EMPTY_MSG);

      // RS ID
      tableBuilder.appendCell(fromInt(server.getReplicationServerId()));

      // Replication port
      tableBuilder.appendCell(fromPositiveInt(server.getReplicationServerPort()));

      // Missing changes
      tableBuilder.appendCell(EMPTY_MSG);

      // Age of oldest change
      tableBuilder.appendCell(EMPTY_MSG);

      // Secure
      tableBuilder.appendCell(fromBoolean(server.isReplicationSecure()));
    }

    TablePrinter printer;
    PrintStream out = getOutputStream();
    if (scriptFriendly)
    {
      printer = new TabSeparatedTablePrinter(out);
    }
    else
    {
      final TextTablePrinter ttPrinter = new TextTablePrinter(out);
      ttPrinter.setColumnSeparator(LIST_TABLE_SEPARATOR);
      printer = ttPrinter;
    }
    tableBuilder.print(printer);
  }

  private void addReplicasSortedByHostPort(Set<ReplicaDescriptor> orderedReplicas, Set<ReplicaDescriptor> replicas,
      final Set<PreferredConnection> cnx)
  {
    List<ReplicaDescriptor> sortedReplicas = new ArrayList<>(replicas);
    Collections.sort(sortedReplicas, new Comparator<ReplicaDescriptor>()
    {
      @Override
      public int compare(ReplicaDescriptor replica1, ReplicaDescriptor replica2)
      {
        HostPort hp1 = getHostPort2(replica1.getServer(), cnx);
        HostPort hp2 = getHostPort2(replica2.getServer(), cnx);
        return hp1.compareTo(hp2);
      }
    });
    orderedReplicas.addAll(sortedReplicas);
  }

  private LocalizableMessage fromObject(Object value)
  {
    return LocalizableMessage.raw("%s", value);
  }

  private LocalizableMessage fromDate(long ageOfOldestMissingChange)
  {
    if (ageOfOldestMissingChange > 0)
    {
      Date date = new Date(ageOfOldestMissingChange);
      return LocalizableMessage.raw(date.toString());
    }
    return EMPTY_MSG;
  }

  private LocalizableMessage fromBoolean(boolean value)
  {
    return LocalizableMessage.raw(Boolean.toString(value));
  }

  private LocalizableMessage fromInt(int value)
  {
    return LocalizableMessage.raw(Integer.toString(value));
  }

  private LocalizableMessage fromPositiveInt(int value)
  {
    return value >= 0 ? LocalizableMessage.raw(Integer.toString(value)) : EMPTY_MSG;
  }

  private boolean isRepServerNotInDomain(Set<ReplicaDescriptor> replicas, ServerDescriptor server)
  {
    boolean isDomain = false;
    boolean isRepServer = false;
    HostPort replicationServer = server.getReplicationServerHostPort();
    for (ReplicaDescriptor replica : replicas)
    {
      if (!isRepServer)
      {
        isRepServer = isRepServer || replica.getReplicationServers().contains(replicationServer);
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
    return !isDomain && isRepServer;
  }

  /**
   * Displays the replication status of the replication servers provided.  The
   * code assumes that all the servers have a replication server and that there
   * are associated with no replication domain.
   * @param servers the servers
   * @param cnx the preferred connections used to connect to the server.
   * @param scriptFriendly whether to display it on script-friendly mode or not.
   */
  private void displayReplicationServerStatuses(
      Set<ServerDescriptor> servers, boolean scriptFriendly, Set<PreferredConnection> cnx)
  {
    TableBuilder tableBuilder = new TableBuilder();
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_SERVERPORT.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_REPLICATION_PORT.get());
    tableBuilder.appendHeading(INFO_REPLICATION_STATUS_HEADER_SECURE.get());

    for (ServerDescriptor server : servers)
    {
      tableBuilder.startRow();
      // Server host+port
      tableBuilder.appendCell(fromObject(getHostPort2(server, cnx)));
      // Replication port
      tableBuilder.appendCell(fromPositiveInt(server.getReplicationServerPort()));
      // Secure
      tableBuilder.appendCell(fromBoolean(server.isReplicationSecure()));
    }

    TablePrinter printer;
    if (scriptFriendly)
    {
      print(INFO_REPLICATION_STATUS_INDEPENDENT_REPLICATION_SERVERS.get());
      println();
      printer = new TabSeparatedTablePrinter(getOutputStream());
    }
    else
    {
      LocalizableMessage msg = INFO_REPLICATION_STATUS_INDEPENDENT_REPLICATION_SERVERS.get();
      print(msg);
      println();
      print(LocalizableMessage.raw(times('=', msg.length())));
      println();

      final TextTablePrinter ttPrinter = new TextTablePrinter(getOutputStream());
      ttPrinter.setColumnSeparator(LIST_TABLE_SEPARATOR);
      printer = ttPrinter;
    }
    tableBuilder.print(printer);
  }

  private String times(char c, int nb)
  {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < nb; i++)
    {
      buf.append(c);
    }
    return buf.toString();
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
  private Set<HostPort> getReplicationServers(DN baseDN, TopologyCache cache, ServerDescriptor server)
  {
    Set<HostPort> servers = getAllReplicationServers(baseDN, server);
    if (cache != null)
    {
      for (SuffixDescriptor suffix : cache.getSuffixes())
      {
        if (suffix.getDN().equals(baseDN))
        {
          Set<HostPort> s = suffix.getReplicationServers();
          // Test that at least we share one of the replication servers.
          // If we do: we are dealing with the same replication topology
          // (we must consider the case of disjoint replication topologies
          // replicating the same base DN).
          Set<HostPort> copy = new HashSet<>(s);
          copy.retainAll(servers);
          if (!copy.isEmpty())
          {
            servers.addAll(s);
            break;
          }
          else if (server.isReplicationServer()
              && s.contains(server.getReplicationServerHostPort()))
          {
            // this server is acting as replication server with no domain.
            servers.addAll(s);
            break;
          }
        }
      }
    }
    return servers;
  }

  private String findIgnoreCase(Set<String> col, String toFind)
  {
    for (String s : col)
    {
      if (toFind.equalsIgnoreCase(s))
      {
        return s;
      }
    }
    return null;
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
  private SuffixDescriptor getSuffix(DN baseDN, TopologyCache cache, ServerDescriptor server)
  {
    final HostPort replicationServer = server.getReplicationServerHostPort();

    SuffixDescriptor returnValue = null;
    Set<HostPort> servers = getAllReplicationServers(baseDN, server);
    for (SuffixDescriptor suffix : cache.getSuffixes())
    {
      if (suffix.getDN().equals(baseDN))
      {
        Set<HostPort> s = suffix.getReplicationServers();
        // Test that at least we share one of the replication servers.
        // If we do: we are dealing with the same replication topology
        // (we must consider the case of disjoint replication topologies
        // replicating the same base DN).
        HashSet<HostPort> copy = new HashSet<>(s);
        copy.retainAll(servers);
        if (!copy.isEmpty())
        {
          return suffix;
        }
        else if (replicationServer != null && s.contains(replicationServer))
        {
          returnValue = suffix;
        }
      }
    }
    return returnValue;
  }

  private Set<HostPort> getAllReplicationServers(DN baseDN, ServerDescriptor server)
  {
    ReplicaDescriptor replica = findReplicaForSuffixDN(server.getReplicas(), baseDN);
    Set<HostPort> servers = new LinkedHashSet<>();
    if (replica != null)
    {
      servers.addAll(replica.getReplicationServers());
    }
    return servers;
  }

  /**
   * Configures the server as a replication server by using the provided connection.
   * The replication server listens to the provided port.
   * @param conn the connection to the server that we want to configure.
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
  private void configureAsReplicationServer(ConnectionWrapper conn,
      int replicationPort, boolean useSecureReplication,
      Set<HostPort> replicationServers,
      Set<Integer> usedReplicationServerIds) throws Exception
  {
    Set<String> replicationServersLC = toLowerCaseStrings(replicationServers);
    print(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_CONFIGURING_REPLICATION_SERVER.get(conn.getHostPort())));

    /* Configure Synchronization plugin. */
    ReplicationSynchronizationProviderCfgClient sync = null;
    try
    {
      sync = getMultimasterSynchronization(conn);
    }
    catch (ManagedObjectNotFoundException monfe)
    {
      logger.info(LocalizableMessage.raw(
          "Synchronization server does not exist in " + conn.getHostPort()));
    }
    RootCfgClient root = conn.getRootConfiguration();
    if (sync == null)
    {
      ReplicationSynchronizationProviderCfgDefn provider =
        ReplicationSynchronizationProviderCfgDefn.getInstance();
      sync = root.createSynchronizationProvider(provider,
          "Multimaster Synchronization",
          new ArrayList<PropertyException>());
      sync.setJavaClass(
          org.opends.server.replication.plugin.MultimasterReplication.class.
          getName());
      sync.setEnabled(Boolean.TRUE);
    }
    else if (!sync.isEnabled())
    {
      sync.setEnabled(Boolean.TRUE);
    }
    sync.commit();

    /* Configure the replication server. */
    ReplicationServerCfgClient rsCfgClient;
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
      rsCfgClient = sync.createReplicationServer(
          ReplicationServerCfgDefn.getInstance(),
          new ArrayList<PropertyException>());
      rsCfgClient.setReplicationServerId(id);
      rsCfgClient.setReplicationPort(replicationPort);
      rsCfgClient.setReplicationServer(replicationServersLC);
      mustCommit = true;
    }
    else
    {
      rsCfgClient = sync.getReplicationServer();
      usedReplicationServerIds.add(rsCfgClient.getReplicationServerId());
      mustCommit = addAllReplicationServers(rsCfgClient, replicationServersLC);
    }
    if (mustCommit)
    {
      rsCfgClient.commit();
    }

    print(formatter.getFormattedDone());
    println();
  }

  /**
   * Updates the configuration of the replication server with the list of replication servers
   * provided.
   *
   * @param conn
   *          the connection to the server that we want to update.
   * @param replicationServers
   *          the list of replication servers to which the replication server will communicate with.
   * @throws OpenDsException
   *           if there is an error updating the configuration.
   */
  private void updateReplicationServer(ConnectionWrapper conn, Set<HostPort> replicationServers) throws Exception
  {
    Set<String> replicationServersLC = toLowerCaseStrings(replicationServers);
    print(formatter.getFormattedWithPoints(
        INFO_REPLICATION_ENABLE_UPDATING_REPLICATION_SERVER.get(conn.getHostPort())));

    ReplicationServerCfgClient rsCfgClient = getMultimasterSynchronization(conn).getReplicationServer();
    if (addAllReplicationServers(rsCfgClient, replicationServersLC))
    {
      rsCfgClient.commit();
    }

    print(formatter.getFormattedDone());
    println();
  }

  private boolean addAllReplicationServers(ReplicationServerCfgClient rsCfgClient, Set<String> replicationServersLC)
  {
    final Set<String> servers = rsCfgClient.getReplicationServer();
    if (servers == null)
    {
      rsCfgClient.setReplicationServer(replicationServersLC);
      return true;
    }

    final Set<String> serversLC = toLowerCase(servers);
    if (!serversLC.equals(replicationServersLC))
    {
      serversLC.addAll(replicationServersLC);
      rsCfgClient.setReplicationServer(replicationServersLC);
      return true;
    }
    return false;
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
    Set<Integer> ids = new HashSet<>();
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
   * Configures a replication domain for a given base DN in the server for which the connection is
   * provided.
   *
   * @param conn
   *          the connection to the server that we want to configure.
   * @param baseDN
   *          the base DN of the replication domain to configure.
   * @param replicationServers
   *          the list of replication servers to which the replication domain will communicate with.
   * @param usedReplicaServerIds
   *          the set of replication domain IDs that are already in use. The set will be updated
   *          with the replication ID that will be used by the newly configured replication server.
   * @throws OpenDsException
   *           if there is an error updating the configuration.
   */
  private void configureToReplicateBaseDN(ConnectionWrapper conn,
      DN baseDN,
      Set<HostPort> replicationServers,
      Set<Integer> usedReplicaServerIds) throws Exception
  {
    Set<String> replicationServersLC = toLowerCaseStrings(replicationServers);

    boolean userSpecifiedAdminBaseDN = false;
    List<DN> baseDNs = toDNs(argParser.getBaseDNs());
    if (baseDNs != null)
    {
      userSpecifiedAdminBaseDN = baseDNs.contains(ADSContext.getAdministrationSuffixDN());
    }
    if (!userSpecifiedAdminBaseDN
        && baseDN.equals(ADSContext.getAdministrationSuffixDN()))
    {
      print(formatter.getFormattedWithPoints(
          INFO_REPLICATION_ENABLE_CONFIGURING_ADS.get(conn.getHostPort())));
    }
    else
    {
      print(formatter.getFormattedWithPoints(
          INFO_REPLICATION_ENABLE_CONFIGURING_BASEDN.get(baseDN, conn.getHostPort())));
    }

    ReplicationSynchronizationProviderCfgClient sync = getMultimasterSynchronization(conn);

    String[] domainNames = sync.listReplicationDomains();
    if (domainNames == null)
    {
      domainNames = new String[]{};
    }
    ReplicationDomainCfgClient domain = findDomainByBaseDN(sync, baseDN, domainNames);
    boolean mustCommit = false;
    if (domain == null)
    {
      int replicaServerId = InstallerHelper.getReplicationId(usedReplicaServerIds);
      usedReplicaServerIds.add(replicaServerId);
      String domainName = InstallerHelper.getDomainName(domainNames, baseDN);
      domain = sync.createReplicationDomain(
          ReplicationDomainCfgDefn.getInstance(), domainName,
          null);
      domain.setServerId(replicaServerId);
      domain.setBaseDN(baseDN);
      domain.setReplicationServer(replicationServersLC);
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
      else
      {
        Set<String> serversLC = toLowerCase(servers);
        if (!serversLC.equals(replicationServersLC))
        {
          serversLC.addAll(replicationServersLC);
          domain.setReplicationServer(replicationServersLC);
          mustCommit = true;
        }
      }
    }

    if (mustCommit)
    {
      domain.commit();
    }

    print(formatter.getFormattedDone());
    println();
  }

  private ReplicationDomainCfgClient findDomainByBaseDN(ReplicationSynchronizationProviderCfgClient sync, DN baseDN,
      String[] domainNames) throws OperationsException, LdapException
  {
    ReplicationDomainCfgClient[] domains = new ReplicationDomainCfgClient[domainNames.length];
    for (int i=0; i<domains.length; i++)
    {
      domains[i] = sync.getReplicationDomain(domainNames[i]);
    }
    for (ReplicationDomainCfgClient domain : domains)
    {
      if (baseDN.equals(domain.getBaseDN()))
      {
        return domain;
      }
    }
    return null;
  }

  private static Set<String> toLowerCase(Set<String> strings)
  {
    Set<String> results = new HashSet<>();
    for (String s : strings)
    {
      results.add(s.toLowerCase(Locale.ROOT));
    }
    return results;
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
  private void configureToReplicateBaseDN(DN baseDN,
      Set<HostPort> repServers, Set<Integer> usedIds,
      TopologyCache cache, ServerDescriptor server,
      Set<String> alreadyConfiguredServers, Set<HostPort> allRepServers,
      Set<String> alreadyConfiguredReplicationServers)
  throws ReplicationCliException
  {
    logger.info(LocalizableMessage.raw("Configuring base DN '"+baseDN+
        "' the replication servers are "+repServers));
    Set<ServerDescriptor> serversToConfigureDomain = new HashSet<>();
    Set<ServerDescriptor> replicationServersToConfigure = new HashSet<>();
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
      if (s.isReplicationServer()
          && !alreadyConfiguredReplicationServers.contains(s.getId())
          // Check if it is part of the replication topology
          && repServers.contains(s.getReplicationServerHostPort()))
      {
        replicationServersToConfigure.add(s);
      }
    }

    Set<ServerDescriptor> allServers = new HashSet<>(serversToConfigureDomain);
    allServers.addAll(replicationServersToConfigure);

    for (ServerDescriptor s : allServers)
    {
      logger.info(LocalizableMessage.raw("Configuring server "+server.getHostPort(true)));
      try (ConnectionWrapper conn = getConnection(cache, s))
      {
        if (serversToConfigureDomain.contains(s))
        {
          configureToReplicateBaseDN(conn, baseDN, repServers, usedIds);
        }
        if (replicationServersToConfigure.contains(s))
        {
          updateReplicationServer(conn, allRepServers);
        }
      }
      catch (LdapException e)
      {
        HostPort hostPort = getHostPort2(s, cache.getPreferredConnections());
        LocalizableMessage msg = getMessageForException(e, hostPort.toString());
        throw new ReplicationCliException(msg, ERROR_CONNECTING, e);
      }
      catch (Exception ode)
      {
        HostPort hostPort = getHostPort2(s, cache.getPreferredConnections());
        LocalizableMessage msg = getMessageForEnableException(hostPort, baseDN);
        throw new ReplicationCliException(msg, ERROR_ENABLING_REPLICATION_ON_BASEDN, ode);
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
    Map<ADSContext.AdministratorProperty, Object> adminProperties = new HashMap<>();
    adminProperties.put(ADSContext.AdministratorProperty.UID, uData.getAdminUid());
    adminProperties.put(ADSContext.AdministratorProperty.PASSWORD, uData.getAdminPwd());
    adminProperties.put(ADSContext.AdministratorProperty.DESCRIPTION,
        INFO_GLOBAL_ADMINISTRATOR_DESCRIPTION.get().toString());
    return adminProperties;
  }

  private void initializeSuffix(DN baseDN, ConnectionWrapper connSource, ConnectionWrapper connDestination,
      boolean displayProgress) throws ReplicationCliException
  {
    int replicationId = -1;
    try
    {
      TopologyCacheFilter filter = new TopologyCacheFilter();
      filter.setSearchMonitoringInformation(false);
      filter.addBaseDNToSearch(baseDN);
      ServerDescriptor source = ServerDescriptor.createStandalone(connSource, filter);
      ReplicaDescriptor replica = findReplicaForSuffixDN(source.getReplicas(), baseDN);
      if (replica != null)
      {
        replicationId = replica.getServerId();
      }
    }
    catch (IOException ne)
    {
      LocalizableMessage msg = getMessageForException(ne, connSource.getHostPort().toString());
      throw new ReplicationCliException(msg, ERROR_READING_CONFIGURATION, ne);
    }

    if (replicationId == -1)
    {
      throw new ReplicationCliException(
          ERR_INITIALIZING_REPLICATIONID_NOT_FOUND.get(connSource.getHostPort(), baseDN),
          REPLICATIONID_NOT_FOUND, null);
    }

    final Installer installer = new Installer();
    installer.setProgressMessageFormatter(formatter);
    installer.addProgressUpdateListener(new ProgressUpdateListener()
    {
      @Override
      public void progressUpdate(ProgressUpdateEvent ev)
      {
        LocalizableMessage newLogDetails = ev.getNewLogs();
        if (newLogDetails != null && !"".equals(newLogDetails.toString().trim()))
        {
          print(newLogDetails);
          println();
        }
      }
    });
    int nTries = 5;
    boolean initDone = false;
    while (!initDone)
    {
      try
      {
        installer.initializeSuffix(
            connDestination, replicationId, baseDN, displayProgress, connSource.getHostPort());
        initDone = true;
      }
      catch (PeerNotFoundException pnfe)
      {
        logger.info(LocalizableMessage.raw("Peer could not be found"));
        if (nTries == 1)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_INITIALIZING_TRIES_COMPLETED.get(
                  pnfe.getMessageObject()), INITIALIZING_TRIES_COMPLETED, pnfe);
        }
        sleepCatchInterrupt((5 - nTries) * 3000);
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
   * @param conn the connection to the server where the source replica of the
   * initialization is.
   * @param baseDN the dn of the suffix.
   * @param displayProgress whether we want to display progress or not.
   * @throws ReplicationCliException if an unexpected error occurs.
   */
  public void initializeAllSuffix(DN baseDN, ConnectionWrapper conn, boolean displayProgress)
      throws ReplicationCliException
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
        initializeAllSuffixTry(baseDN, conn, displayProgress);
        postPreExternalInitialization(baseDN, conn, false);
        initDone = true;
      }
      catch (PeerNotFoundException pnfe)
      {
        logger.info(LocalizableMessage.raw("Peer could not be found"));
        if (nTries == 1)
        {
          throw new ReplicationCliException(
              ERR_REPLICATION_INITIALIZING_TRIES_COMPLETED.get(
                  pnfe.getMessageObject()), INITIALIZING_TRIES_COMPLETED, pnfe);
        }
        sleepCatchInterrupt((5 - nTries) * 3000);
      }
      catch (ClientException ae)
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
   * @param conn the connection to the server.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void preExternalInitialization(DN baseDN, ConnectionWrapper conn) throws ReplicationCliException
  {
    postPreExternalInitialization(baseDN, conn, true);
  }

  /**
   * Launches the post external initialization operation using the provided
   * connection on a given base DN required for replication to work.
   * @param baseDN the base DN that we want to reset.
   * @param conn the connection to the server.
   * @throws ReplicationCliException if there is an error performing the
   * operation.
   */
  private void postExternalInitialization(DN baseDN, ConnectionWrapper conn) throws ReplicationCliException
  {
    postPreExternalInitialization(baseDN, conn, false);
  }

  /**
   * Launches the pre or post external initialization operation using the
   * provided connection on a given base DN.
   * @param baseDN the base DN that we want to reset.
   * @param conn the connection to the server.
   * @param isPre whether this is the pre operation or the post operation.
   * @throws ReplicationCliException if there is an error performing the operation
   */
  private void postPreExternalInitialization(DN baseDN,
      ConnectionWrapper conn, boolean isPre) throws ReplicationCliException
  {
    boolean isOver = false;
    String dn = null;
    Map<String, String> attrMap = new TreeMap<>();
    if (isPre)
    {
      attrMap.put("ds-task-reset-generation-id-new-value", "-1");
    }
    attrMap.put("ds-task-reset-generation-id-domain-base-dn", baseDN.toString());

    try {
      dn = createServerTask(conn,
          "ds-task-reset-generation-id",
          "org.opends.server.tasks.SetGenerationIdTask",
          "dsreplication-reset-generation-id",
          attrMap);
    }
    catch (LdapException e)
    {
      LocalizableMessage msg = isPre ?
          ERR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION.get():
          ERR_LAUNCHING_POST_EXTERNAL_INITIALIZATION.get();
      ReplicationCliReturnCode code = isPre?
          ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION:
          ERROR_LAUNCHING_POST_EXTERNAL_INITIALIZATION;
      throw new ReplicationCliException(getThrowableMsg(msg, e), code, e);
    }

    String lastLogMsg = null;
    while (!isOver)
    {
      sleepCatchInterrupt(500);
      try
      {
        SearchResultEntry sr = getLastSearchResult(conn, dn, "ds-task-log-message", "ds-task-state");
        String logMsg = firstValueAsString(sr, "ds-task-log-message");
        if (logMsg != null && !logMsg.equals(lastLogMsg))
        {
          logger.info(LocalizableMessage.raw(logMsg));
          lastLogMsg = logMsg;
        }
        String state = firstValueAsString(sr, "ds-task-state");
        TaskState taskState = TaskState.fromString(state);

        if (TaskState.isDone(taskState) || taskState == STOPPED_BY_ERROR)
        {
          isOver = true;
          LocalizableMessage errorMsg = getPrePostErrorMsg(lastLogMsg, state, conn);

          if (TaskState.COMPLETED_WITH_ERRORS == taskState)
          {
            logger.warn(LocalizableMessage.raw("Completed with error: "+errorMsg));
            errPrintln(errorMsg);
          }
          else if (!TaskState.isSuccessful(taskState) || taskState == STOPPED_BY_ERROR)
          {
            logger.warn(LocalizableMessage.raw("Error: "+errorMsg));
            ReplicationCliReturnCode code = isPre?
                ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION:
                  ERROR_LAUNCHING_POST_EXTERNAL_INITIALIZATION;
            throw new ReplicationCliException(errorMsg, code, null);
          }
        }
      }
      catch (EntryNotFoundException x)
      {
        isOver = true;
      }
      catch (IOException ne)
      {
        throw new ReplicationCliException(getThrowableMsg(ERR_READING_SERVER_TASK_PROGRESS.get(), ne),
            ERROR_CONNECTING, ne);
      }
    }
  }

  private LocalizableMessage getPrePostErrorMsg(String lastLogMsg, String state, ConnectionWrapper conn)
  {
    HostPort hostPort = conn.getHostPort();
    if (lastLogMsg != null)
    {
      return ERR_UNEXPECTED_DURING_TASK_WITH_LOG.get(lastLogMsg, state, hostPort);
    }
    return ERR_UNEXPECTED_DURING_TASK_NO_LOG.get(state, hostPort);
  }

  private void sleepCatchInterrupt(long millis)
  {
    try
    {
      Thread.sleep(millis);
    }
    catch (InterruptedException e)
    {
    }
  }

  /**
   * Initializes all the replicas in the topology with the contents of a
   * given replica.  This method will try to create the task only once.
   * @param conn the connection to the server where the source replica of the
   * initialization is.
   * @param baseDN the dn of the suffix.
   * @param displayProgress whether we want to display progress or not.
   * @throws ClientException if an unexpected error occurs.
   * @throws PeerNotFoundException if the replication mechanism cannot find
   * a peer.
   */
  private void initializeAllSuffixTry(DN baseDN, ConnectionWrapper conn, boolean displayProgress)
      throws ClientException, PeerNotFoundException
  {
    boolean isOver = false;
    String dn = null;
    HostPort hostPort = conn.getHostPort();
    Map<String, String> attrsMap = new TreeMap<>();
    attrsMap.put("ds-task-initialize-domain-dn", baseDN.toString());
    attrsMap.put("ds-task-initialize-replica-server-id", "all");
    try
    {
      dn = createServerTask(conn,
          "ds-task-initialize-remote-replica",
          "org.opends.server.tasks.InitializeTargetTask",
          "dsreplication-initialize",
          attrsMap);
    }
    catch (LdapException e)
    {
      throw new ClientException(ReturnCode.APPLICATION_ERROR,
          getThrowableMsg(INFO_ERROR_LAUNCHING_INITIALIZATION.get(hostPort), e), e);
    }

    LocalizableMessage lastDisplayedMsg = null;
    String lastLogMsg = null;
    long lastTimeMsgDisplayed = -1;
    long lastTimeMsgLogged = -1;
    long totalEntries = 0;
    while (!isOver)
    {
      sleepCatchInterrupt(500);
      try
      {
        SearchResultEntry sr = getLastSearchResult(conn, dn, "ds-task-unprocessed-entry-count",
            "ds-task-processed-entry-count", "ds-task-log-message", "ds-task-state" );

        // Get the number of entries that have been handled and a percentage...
        long processed = sr.parseAttribute("ds-task-processed-entry-count").asLong();
        long unprocessed = sr.parseAttribute("ds-task-unprocessed-entry-count").asLong();
        totalEntries = Math.max(totalEntries, processed+unprocessed);

        LocalizableMessage msg = getMsg(lastDisplayedMsg, processed, unprocessed);
        if (msg != null)
        {
          long currentTime = System.currentTimeMillis();
          /* Refresh period: to avoid having too many lines in the log */
          long minRefreshPeriod = getMinRefreshPeriod(totalEntries);
          if (currentTime - minRefreshPeriod > lastTimeMsgLogged)
          {
            lastTimeMsgLogged = currentTime;
            logger.info(LocalizableMessage.raw("Progress msg: "+msg));
          }
          if (displayProgress
              && currentTime - minRefreshPeriod > lastTimeMsgDisplayed
              && !msg.equals(lastDisplayedMsg))
          {
            print(msg);
            lastDisplayedMsg = msg;
            println();
            lastTimeMsgDisplayed = currentTime;
          }
        }

        String logMsg = firstValueAsString(sr, "ds-task-log-message");
        if (logMsg != null && !logMsg.equals(lastLogMsg))
        {
          logger.info(LocalizableMessage.raw(logMsg));
          lastLogMsg = logMsg;
        }
        InstallerHelper helper = new InstallerHelper();
        String state = firstValueAsString(sr, "ds-task-state");
        TaskState taskState = TaskState.fromString(state);

        if (TaskState.isDone(taskState) || taskState == STOPPED_BY_ERROR)
        {
          isOver = true;
          logger.info(LocalizableMessage.raw("Last task entry: "+sr));
          if (displayProgress && msg != null && !msg.equals(lastDisplayedMsg))
          {
            print(msg);
            lastDisplayedMsg = msg;
            println();
          }

          LocalizableMessage errorMsg = getInitializeAllErrorMsg(hostPort, lastLogMsg, state);
          if (TaskState.COMPLETED_WITH_ERRORS == taskState)
          {
            logger.warn(LocalizableMessage.raw("Processed errorMsg: "+errorMsg));
            if (displayProgress)
            {
              errPrintln(errorMsg);
            }
          }
          else if (!TaskState.isSuccessful(taskState) || taskState == STOPPED_BY_ERROR)
          {
            logger.warn(LocalizableMessage.raw("Processed errorMsg: "+errorMsg));
            ClientException ce = new ClientException(
                ReturnCode.APPLICATION_ERROR, errorMsg,
                null);
            if (lastLogMsg == null
                || helper.isPeersNotFoundError(lastLogMsg))
            {
              logger.warn(LocalizableMessage.raw("Throwing peer not found error.  "+
                  "Last Log Msg: "+lastLogMsg));
              // Assume that this is a peer not found error.
              throw new PeerNotFoundException(errorMsg);
            }
            else
            {
              logger.error(LocalizableMessage.raw("Throwing ApplicationException."));
              throw ce;
            }
          }
          else
          {
            if (displayProgress)
            {
              print(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get());
              println();
            }
            logger.info(LocalizableMessage.raw("Processed msg: "+errorMsg));
            logger.info(LocalizableMessage.raw("Initialization completed successfully."));
          }
        }
      }
      catch (EntryNotFoundException x)
      {
        isOver = true;
        logger.info(LocalizableMessage.raw("Initialization entry not found."));
        if (displayProgress)
        {
          print(INFO_SUFFIX_INITIALIZED_SUCCESSFULLY.get());
          println();
        }
      }
      catch (IOException e)
      {
        throw new ClientException(
            ReturnCode.APPLICATION_ERROR,
            getThrowableMsg(INFO_ERROR_POOLING_INITIALIZATION.get(hostPort), e), e);
      }
    }
  }

  private SearchResultEntry getLastSearchResult(ConnectionWrapper conn, String dn, String... returnedAttributes)
      throws IOException
  {
    SearchRequest request = newSearchRequest(dn, BASE_OBJECT, "(objectclass=*)", returnedAttributes);
    try (ConnectionEntryReader entryReader = conn.getConnection().search(request))
    {
      SearchResultEntry sr = null;
      while (entryReader.hasNext())
      {
        sr = entryReader.readEntry();
      }
      return sr;
    }
  }

  private String createServerTask(ConnectionWrapper conn, String taskObjectclass,
      String taskJavaClass, String taskID, Map<String, String> taskAttrs) throws LdapException
  {
    int i = 1;
    String dn = "";
    AddRequest request = newAddRequest(dn)
        .addAttribute("objectclass", taskObjectclass)
        .addAttribute("ds-task-class-name", taskJavaClass);
    for (Map.Entry<String, String> attr : taskAttrs.entrySet())
    {
      request.addAttribute(attr.getKey(), attr.getValue());
    }

    while (true)
    {
      String id = taskID + "-" + i;
      dn = "ds-task-id=" + id + ",cn=Scheduled Tasks,cn=Tasks";
      request.setName(dn);
      try
      {
        conn.getConnection().add(request);
        logger.info(LocalizableMessage.raw("created task entry: " + request));
        return dn;
      }
      catch (LdapException e)
      {
        if (e.getResult().getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS)
        {
          logger.warn(LocalizableMessage.raw("A task with dn: " + dn + " already existed."));
        }
        else
        {
          logger.error(LocalizableMessage.raw("Error creating task " + request, e));
          throw e;
        }
      }
      i++;
    }
  }

  private LocalizableMessage getInitializeAllErrorMsg(HostPort serverDisplay, String lastLogMsg, String state)
  {
    if (lastLogMsg != null)
    {
      return INFO_ERROR_DURING_INITIALIZATION_LOG.get(serverDisplay, lastLogMsg, state, serverDisplay);
    }
    return INFO_ERROR_DURING_INITIALIZATION_NO_LOG.get(serverDisplay, state, serverDisplay);
  }

  private LocalizableMessage getMsg(LocalizableMessage lastDisplayedMsg, long processed, long unprocessed)
  {
    if (processed != -1 && unprocessed != -1)
    {
      if (processed + unprocessed > 0)
      {
        long perc = (100 * processed) / (processed + unprocessed);
        return INFO_INITIALIZE_PROGRESS_WITH_PERCENTAGE.get(processed, perc);
      }
      else
      {
        // return INFO_NO_ENTRIES_TO_INITIALIZE.get();
        return null;
      }
    }
    else if (processed != -1)
    {
      return INFO_INITIALIZE_PROGRESS_WITH_PROCESSED.get(processed);
    }
    else if (unprocessed != -1)
    {
      return INFO_INITIALIZE_PROGRESS_WITH_UNPROCESSED.get(unprocessed);
    }
    else
    {
      return lastDisplayedMsg;
    }
  }

  private long getMinRefreshPeriod(long totalEntries)
  {
    if (totalEntries < 100)
    {
      return 0;
    }
    else if (totalEntries < 1000)
    {
      return 1000;
    }
    else if (totalEntries < 10000)
    {
      return 5000;
    }
    return 10000;
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
   * @param updateReplicationServers if references in the replication servers
   * must be updated.
   * @param cnx the preferred LDAP URLs to be used to connect to the
   * server.
   * @throws ReplicationCliException if there is an error updating the
   * configuration.
   */
  private void removeReferencesInServer(ServerDescriptor server,
      String replicationServer, DN bindDn, String pwd,
      Collection<DN> baseDNs, boolean updateReplicationServers,
      Set<PreferredConnection> cnx)
  throws ReplicationCliException
  {
    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    filter.setSearchBaseDNInformation(false);
    ServerLoader loader = new ServerLoader(server.getAdsProperties(), bindDn,
        pwd, getTrustManager(sourceServerCI), getConnectTimeout(), cnx, filter);
    DN lastBaseDN = null;
    HostPort hostPort = null;

    try (ConnectionWrapper conn = loader.createConnectionWrapper())
    {
      hostPort = conn.getHostPort();
      ReplicationSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = getMultimasterSynchronization(conn);
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        logger.info(LocalizableMessage.raw("No synchronization found on "+ hostPort +".",
            monfe));
      }
      if (sync != null)
      {
        String[] domainNames = sync.listReplicationDomains();
        if (domainNames != null)
        {
          for (String domainName : domainNames)
          {
            ReplicationDomainCfgClient domain = sync.getReplicationDomain(domainName);
            for (DN baseDN : baseDNs)
            {
              lastBaseDN = baseDN;
              if (domain.getBaseDN().equals(baseDN))
              {
                print(formatter.getFormattedWithPoints(
                    INFO_REPLICATION_REMOVING_REFERENCES_ON_REMOTE.get(baseDN, hostPort)));
                Set<String> replServers = domain.getReplicationServer();
                if (replServers != null)
                {
                  String replServer = findIgnoreCase(replServers, replicationServer);
                  if (replServer != null)
                  {
                    logger.info(LocalizableMessage.raw("Updating references in domain " +
                        domain.getBaseDN()+" on " + hostPort + "."));
                    replServers.remove(replServer);
                    if (!replServers.isEmpty())
                    {
                      domain.setReplicationServer(replServers);
                      domain.commit();
                    }
                    else
                    {
                      sync.removeReplicationDomain(domainName);
                      sync.commit();
                    }
                  }
                }
                print(formatter.getFormattedDone());
                println();
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
            String replServer = findIgnoreCase(replServers, replicationServer);
            if (replServer != null)
            {
              replServers.remove(replServer);
              if (!replServers.isEmpty())
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
    catch (LdapException ne)
    {
      hostPort = getHostPort2(server, cnx);
      LocalizableMessage msg = getMessageForException(ne, hostPort.toString());
      throw new ReplicationCliException(msg, ERROR_CONNECTING, ne);
    }
    catch (Exception ode)
    {
      if (lastBaseDN != null)
      {
        LocalizableMessage msg = getMessageForDisableException(hostPort, lastBaseDN);
        throw new ReplicationCliException(msg,
          ERROR_DISABLING_REPLICATION_REMOVE_REFERENCE_ON_BASEDN, ode);
      }
      else
      {
        LocalizableMessage msg = ERR_REPLICATION_ERROR_READING_CONFIGURATION.get(hostPort, ode.getMessage());
        throw new ReplicationCliException(msg, ERROR_CONNECTING, ode);
      }
    }
  }

  /**
   * Deletes a replication domain in a server for a given base DN (disable
   * replication of the base DN).
   * @param conn the connection to the server.
   * @param baseDN the base DN of the replication domain that we want to delete.
   * @throws ReplicationCliException if there is an error updating the
   * configuration of the server.
   */
  private void deleteReplicationDomain(ConnectionWrapper conn, DN baseDN) throws ReplicationCliException
  {
    HostPort hostPort = conn.getHostPort();
    try
    {
      ReplicationSynchronizationProviderCfgClient sync = null;
      try
      {
        sync = getMultimasterSynchronization(conn);
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        logger.info(LocalizableMessage.raw("No synchronization found on " + hostPort + ".", monfe));
      }
      if (sync != null)
      {
        String[] domainNames = sync.listReplicationDomains();
        if (domainNames != null)
        {
          for (String domainName : domainNames)
          {
            ReplicationDomainCfgClient domain =
              sync.getReplicationDomain(domainName);
            if (domain.getBaseDN().equals(baseDN))
            {
              print(formatter.getFormattedWithPoints(
                  INFO_REPLICATION_DISABLING_BASEDN.get(baseDN, hostPort)));
              sync.removeReplicationDomain(domainName);
              sync.commit();

              print(formatter.getFormattedDone());
              println();
            }
          }
        }
      }
    }
    catch (Exception ode)
    {
      LocalizableMessage msg = getMessageForDisableException(hostPort, baseDN);
        throw new ReplicationCliException(msg,
          ERROR_DISABLING_REPLICATION_REMOVE_REFERENCE_ON_BASEDN, ode);
    }
  }

  private ReplicationSynchronizationProviderCfgClient getMultimasterSynchronization(ConnectionWrapper conn)
      throws DecodingException, OperationsException, LdapException
  {
    RootCfgClient root = conn.getRootConfiguration();
    return (ReplicationSynchronizationProviderCfgClient) root.getSynchronizationProvider("Multimaster Synchronization");
  }

  /**
   * Disables the replication server for a given server.
   * @param conn the connection to the server.
   * @throws ReplicationCliException if there is an error updating the
   * configuration of the server.
   */
  private void disableReplicationServer(ConnectionWrapper conn) throws ReplicationCliException
  {
    HostPort hostPort = conn.getHostPort();
    try
    {
      ReplicationSynchronizationProviderCfgClient sync = null;
      ReplicationServerCfgClient replicationServer = null;
      try
      {
        sync = getMultimasterSynchronization(conn);
        if (sync.hasReplicationServer())
        {
          replicationServer = sync.getReplicationServer();
        }
      }
      catch (ManagedObjectNotFoundException monfe)
      {
        // It does not exist.
        logger.info(LocalizableMessage.raw("No synchronization found on " + hostPort + ".", monfe));
      }
      if (replicationServer != null)
      {
        String s = String.valueOf(replicationServer.getReplicationPort());
        print(formatter.getFormattedWithPoints(
            INFO_REPLICATION_DISABLING_REPLICATION_SERVER.get(s, hostPort)));

        sync.removeReplicationServer();
        sync.commit();
        print(formatter.getFormattedDone());
        println();
      }
    }
    catch (Exception ode)
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
   * occurred when we were configuring some replication domain (creating
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (creating
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   */
  private LocalizableMessage getMessageForEnableException(HostPort hostPort, DN baseDN)
  {
    return ERR_REPLICATION_CONFIGURING_BASEDN.get(baseDN, hostPort);
  }

  /**
   * Returns a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (deleting
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   * @param hostPort the hostPort representation of the server we were
   * contacting when the OpenDsException occurred.
   * @return a message for a given OpenDsException (we assume that was an
   * exception generated updating the configuration of the server) that
   * occurred when we were configuring some replication domain (deleting
   * the replication domain or updating the list of replication servers of
   * the replication domain).
   */
  private LocalizableMessage getMessageForDisableException(HostPort hostPort, DN baseDN)
  {
    return ERR_REPLICATION_CONFIGURING_BASEDN.get(baseDN, hostPort);
  }

  /**
   * Returns a message informing the user that the provided port cannot be used.
   * @param port the port that cannot be used.
   * @return a message informing the user that the provided port cannot be used.
   */
  private LocalizableMessage getCannotBindToPortError(int port)
  {
    if (SetupUtils.isPrivilegedPort(port))
    {
      return ERR_CANNOT_BIND_TO_PRIVILEGED_PORT.get(port);
    }
    return ERR_CANNOT_BIND_TO_PORT.get(port);
  }

  /**
   * Returns the message that must be displayed to the user for a given
   * exception.  This is assumed to be a critical exception that stops all
   * the processing.
   * @param rce the ReplicationCliException.
   * @return a message to be displayed to the user.
   */
  private LocalizableMessage getCriticalExceptionMessage(ReplicationCliException rce)
  {
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    mb.append(rce.getMessageObject());
    File logFile = ControlPanelLog.getLogFile();
    if (logFile != null && rce.getErrorCode() != USER_CANCELLED)
    {
      mb.append(Constants.LINE_SEPARATOR);
      mb.append(INFO_GENERAL_SEE_FOR_DETAILS.get(logFile.getPath()));
    }
    // Check if the cause has already been included in the message
    Throwable c = rce.getCause();
    if (c != null)
    {
      String s = toString(c);
      if (!mb.toString().contains(s))
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
      mustInitializeSchema = id1 == null || !id1.equals(id2);
    }
    if (mustInitializeSchema)
    {
      // Check that both will contain replication data
      mustInitializeSchema = uData.getServer1().configureReplicationDomain()
          && uData.getServer2().configureReplicationDomain();
    }
    return mustInitializeSchema;
  }

  /**
   * This method registers a server in a given ADSContext.  If the server was
   * already registered it unregisters it and registers again (some properties
   * might have changed).
   * @param adsContext the ADS Context to be used.
   * @param serverProperties the properties of the server to be registered.
   * @throws ADSContextException if an error occurs during the registration or
   * unregistration of the server.
   */
  private void registerServer(ADSContext adsContext, Map<ServerProperty, Object> serverProperties)
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
        logger.warn(LocalizableMessage.raw("The server was already registered: "+
            serverProperties));
        adsContext.unregisterServer(serverProperties);
        adsContext.registerServer(serverProperties);
      }
      else
      {
        throw ade;
      }
    }
  }

  @Override
  public boolean isAdvancedMode() {
    return false;
  }

  @Override
  public boolean isInteractive() {
    return !forceNonInteractive && argParser.isInteractive();
  }

  @Override
  public boolean isMenuDrivenMode() {
    return true;
  }

  @Override
  public boolean isQuiet()
  {
    return argParser.isQuiet();
  }

  @Override
  public boolean isScriptFriendly() {
    return argParser.isScriptFriendly();
  }

  @Override
  public boolean isVerbose() {
    return true;
  }

  /**
   * Forces the initialization of the trust manager in the LDAPConnectionInteraction object.
   * @param ci the LDAP connection to the server
   */
  private void forceTrustManagerInitialization(LDAPConnectionConsoleInteraction ci)
  {
    forceNonInteractive = true;
    try
    {
      ci.initializeTrustManagerIfRequired();
    }
    catch (ArgumentException ae)
    {
      logger.warn(LocalizableMessage.raw("Error initializing trust store: "+ae, ae));
    }
    forceNonInteractive = false;
  }

  /**
   * Method used to compare two server registries.
   * @param registry1 the first registry to compare.
   * @param registry2 the second registry to compare.
   * @return {@code true} if the registries are equal and {@code false} otherwise.
   */
  private boolean areEqual(Set<Map<ServerProperty, Object>> registry1, Set<Map<ServerProperty, Object>> registry2)
  {
    return registry1.size() == registry2.size()
        && equals(registry1, registry2, getPropertiesToCompare());
  }

  private Set<ServerProperty> getPropertiesToCompare()
  {
    final Set<ServerProperty> propertiesToCompare = new HashSet<>();
    for (ServerProperty property : ServerProperty.values())
    {
      if (property.getAttributeSyntax() != ADSPropertySyntax.CERTIFICATE_BINARY)
      {
        propertiesToCompare.add(property);
      }
    }
    return propertiesToCompare;
  }

  private boolean equals(Set<Map<ServerProperty, Object>> registry1, Set<Map<ServerProperty, Object>> registry2,
      Set<ServerProperty> propertiesToCompare)
  {
    for (Map<ServerProperty, Object> server1 : registry1)
    {
      if (!exists(registry2, server1, propertiesToCompare))
      {
        return false;
      }
    }
    return true;
  }

  private boolean exists(Set<Map<ServerProperty, Object>> registry2, Map<ServerProperty, Object> server1,
      Set<ServerProperty> propertiesToCompare)
  {
    for (Map<ServerProperty, Object> server2 : registry2)
    {
      if (equals(server1, server2, propertiesToCompare))
      {
        return true;
      }
    }
    return false;
  }

  private boolean equals(Map<ServerProperty, Object> server1, Map<ServerProperty, Object> server2,
      Set<ServerProperty> propertiesToCompare)
  {
    for (ServerProperty prop : propertiesToCompare)
    {
      if (!Objects.equals(server1.get(prop), server2.get(prop)))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Tells whether we are trying to disable all the replicated suffixes.
   * @param uData the disable replication data provided by the user.
   * @return {@code true} if we want to disable all the replicated suffixes
   * and {@code false} otherwise.
   */
  private boolean disableAllBaseDns(ConnectionWrapper conn, DisableReplicationUserData uData)
  {
    if (uData.disableAll())
    {
      return true;
    }

    Set<DN> replicatedSuffixes = findAllReplicatedSuffixDNs(getReplicas(conn));
    for (DN dn : replicatedSuffixes)
    {
      if (!ADSContext.getAdministrationSuffixDN().equals(dn)
          && !Constants.SCHEMA_DN.equals(dn)
          && !uData.getBaseDNs().contains(dn))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the host port representation of the server to be used in progress,
   * status and error messages.  It takes into account the fact the host and
   * port provided by the user.
   * @param server the ServerDescriptor.
   * @param cnx the preferred connections list.
   * @return the host port string representation of the provided server.
   */
  private HostPort getHostPort2(ServerDescriptor server, Collection<PreferredConnection> cnx)
  {
    HostPort hostPort = null;
    for (PreferredConnection connection : cnx)
    {
      HostPort hp = connection.getHostPort();
      if (hp.equals(server.getLdapHostPort()))
      {
        hostPort = server.getHostPort(false);
      }
      else if (hp.equals(server.getLdapsHostPort()))
      {
        hostPort = server.getHostPort(true);
      }
    }
    if (hostPort != null)
    {
      return hostPort;
    }
    return server.getHostPort(true);
  }

  /**
   * Prompts the user for the subcommand that should be executed.
   * @return the subcommand choice of the user.
   */
  private SubcommandChoice promptForSubcommand()
  {
    MenuBuilder<SubcommandChoice> builder = new MenuBuilder<>(this);
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
        return m.getValue();
      }
      // The user cancelled
      return SubcommandChoice.CANCEL;
    }
    catch (ClientException ce)
    {
      logger.warn(LocalizableMessage.raw("Error reading input: "+ce, ce));
      return SubcommandChoice.CANCEL;
    }
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
   * @param subCommandName the command builder to be printed.
   * @param uData input parameters from cli
   */
  private void printNewCommandBuilder(String subCommandName, ReplicationUserData uData)
  {
    try
    {
      final CommandBuilder commandBuilder = createCommandBuilder(sourceServerCI, subCommandName, uData);
      if (argParser.displayEquivalentArgument.isPresent())
      {
        println();
        // We assume that the app we are running is this one.
        println(INFO_REPLICATION_NON_INTERACTIVE.get(commandBuilder));
      }
      if (argParser.equivalentCommandFileArgument.isPresent())
      {
        // Write to the file.
        String file = argParser.equivalentCommandFileArgument.getValue();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true)))
        {
          writer.write(SHELL_COMMENT_SEPARATOR + getCurrentOperationDateMessage());
          writer.newLine();

          writer.write(commandBuilder.toString());
          writer.newLine();
          writer.newLine();
        }
        catch (IOException ioe)
        {
          errPrintln(ERR_REPLICATION_ERROR_WRITING_EQUIVALENT_COMMAND_LINE.get(file, ioe));
        }
      }
    }
    catch (Throwable t)
    {
      logger.error(LocalizableMessage.raw("Error printing equivalent command-line: " + t), t);
    }
  }

  /**
   * Creates a command builder with the global options: script friendly,
   * verbose, etc. for a given subcommand name.  It also adds systematically the
   * no-prompt option.
   *
   * @param ci the LDAP connection to the server
   * @param subcommandName the subcommand name.
   * @param uData the user data.
   * @return the command builder that has been created with the specified
   * subcommandName.
   */
  private CommandBuilder createCommandBuilder(LDAPConnectionConsoleInteraction ci, String subcommandName,
      ReplicationUserData uData) throws ArgumentException
  {
    String commandName = getCommandName();

    CommandBuilder commandBuilder = new CommandBuilder(commandName, subcommandName);

    if (ENABLE_REPLICATION_SUBCMD_NAME.equals(subcommandName))
    {
      // All the arguments for enable replication are update here.
      updateCommandBuilder(commandBuilder, (EnableReplicationUserData)uData);
    }
    else if (INITIALIZE_REPLICATION_SUBCMD_NAME.equals(subcommandName) ||
        RESET_CHANGE_NUMBER_SUBCMD_NAME.equals(subcommandName))
    {
      // All the arguments for initialize replication are update here.
      updateCommandBuilder(commandBuilder, (SourceDestinationServerUserData)uData);
    }
    else if (PURGE_HISTORICAL_SUBCMD_NAME.equals(subcommandName))
    {
      // All the arguments for initialize replication are update here.
      updateCommandBuilder(ci, commandBuilder, (PurgeHistoricalUserData)uData);
    }
    else
    {
      // Update the arguments used in the console interaction with the
      // actual arguments of dsreplication.
      updateCommandBuilderWithConsoleInteraction(commandBuilder, ci);
    }

    if (DISABLE_REPLICATION_SUBCMD_NAME.equals(subcommandName))
    {
      DisableReplicationUserData disableData =
        (DisableReplicationUserData)uData;
      if (disableData.disableAll())
      {
        commandBuilder.addArgument(newBooleanArgument(
            argParser.disableAllArg, INFO_DESCRIPTION_DISABLE_ALL));
      }
      else if (disableData.disableReplicationServer())
      {
        commandBuilder.addArgument(newBooleanArgument(
            argParser.disableReplicationServerArg, INFO_DESCRIPTION_DISABLE_REPLICATION_SERVER));
      }
    }

    addGlobalArguments(commandBuilder, uData);
    return commandBuilder;
  }

  private String getCommandName()
  {
    String commandName = System.getProperty(ServerConstants.PROPERTY_SCRIPT_NAME);
    if (commandName != null)
    {
      return commandName;
    }
    return "dsreplication";
  }

  private void updateCommandBuilderWithConsoleInteraction(CommandBuilder commandBuilder,
      LDAPConnectionConsoleInteraction ci) throws ArgumentException
  {
    if (ci != null && ci.getCommandBuilder() != null)
    {
      CommandBuilder interactionBuilder = ci.getCommandBuilder();
      for (Argument arg : interactionBuilder.getArguments())
      {
        if (OPTION_LONG_BINDPWD.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addObfuscatedArgument(getAdminPasswordArg(arg));
        }
        else if (OPTION_LONG_BINDPWD_FILE.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getAdminPasswordFileArg(arg));
        }
        else
        {
          addArgument(commandBuilder, arg, interactionBuilder.isObfuscated(arg));
        }
      }
    }
  }

  private void updateCommandBuilder(LDAPConnectionConsoleInteraction ci, CommandBuilder commandBuilder,
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

    IntegerArgument maximumDurationArg = IntegerArgument.builder(argParser.maximumDurationArg.getLongIdentifier())
            .shortIdentifier(argParser.maximumDurationArg.getShortIdentifier())
            .description(argParser.maximumDurationArg.getDescription())
            .required()
            .defaultValue(PurgeConflictsHistoricalTask.DEFAULT_MAX_DURATION)
            .valuePlaceholder(argParser.maximumDurationArg.getValuePlaceholder())
            .buildArgument();
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

  private void addGlobalArguments(CommandBuilder commandBuilder, ReplicationUserData uData)
  throws ArgumentException
  {
    List<DN> baseDNs = uData.getBaseDNs();
    StringArgument baseDNsArg =
            StringArgument.builder(OPTION_LONG_BASEDN)
                    .shortIdentifier(OPTION_SHORT_BASEDN)
                    .description(INFO_DESCRIPTION_REPLICATION_BASEDNS.get())
                    .multiValued()
                    .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                    .buildArgument();
    for (DN baseDN : baseDNs)
    {
      baseDNsArg.addValue(baseDN.toString());
    }
    commandBuilder.addArgument(baseDNsArg);

    if (argParser.resetChangeNumber.isPresent())
    {
      commandBuilder.addArgument(argParser.resetChangeNumber);
    }

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

    ArrayList<Argument> toMoveArgs = new ArrayList<>();
    for (String longID : identifiersToMove)
    {
      final Argument arg = findArg(commandBuilder, longID);
      if (arg != null)
      {
        toMoveArgs.add(arg);
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
      commandBuilder.addArgument(
              BooleanArgument.builder(OPTION_LONG_VERBOSE)
                      .shortIdentifier(OPTION_SHORT_VERBOSE)
                      .description(INFO_DESCRIPTION_VERBOSE.get())
                      .buildArgument());
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

  private Argument findArg(CommandBuilder commandBuilder, String longIdentifier)
  {
    for (Argument arg : commandBuilder.getArguments())
    {
      if (longIdentifier.equals(arg.getLongIdentifier()))
      {
        return arg;
      }
    }
    return null;
  }

  private boolean existsArg(CommandBuilder commandBuilder, String longIdentifier)
  {
    return findArg(commandBuilder, longIdentifier) != null;
  }

  private void addArgument(CommandBuilder commandBuilder, Argument arg, boolean isObfuscated)
  {
    if (isObfuscated)
    {
      commandBuilder.addObfuscatedArgument(arg);
    }
    else
    {
      commandBuilder.addArgument(arg);
    }
  }

  private void updateCommandBuilder(CommandBuilder commandBuilder, EnableReplicationUserData uData)
      throws ArgumentException
  {
    // Update the arguments used in the console interaction with the
    // actual arguments of dsreplication.
    boolean adminInformationAdded = false;

    EnableReplicationServerData server1 = uData.getServer1();
    if (firstServerCommandBuilder != null)
    {
      boolean useAdminUID = existsArg(firstServerCommandBuilder, OPTION_LONG_ADMIN_UID);
      // This is required when both the bindDN and the admin UID are provided
      // in the command-line.
      boolean forceAddBindDN1 = false;
      boolean forceAddBindPwdFile1 = false;
      if (useAdminUID)
      {
        DN bindDN1 = server1.getBindDn();
        String adminUID = uData.getAdminUid();
        if (bindDN1 != null
            && adminUID != null
            && !getAdministratorDN(adminUID).equals(bindDN1))
        {
          forceAddBindDN1 = true;
          forceAddBindPwdFile1 = existsArg(firstServerCommandBuilder, OPTION_LONG_BINDPWD_FILE);
        }
      }
      for (Argument arg : firstServerCommandBuilder.getArguments())
      {
        if (OPTION_LONG_HOST.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getHostArg("host1", OPTION_SHORT_HOST, server1.getHostName(),
              INFO_DESCRIPTION_ENABLE_REPLICATION_HOST1));
        }
        else if (OPTION_LONG_PORT.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getPortArg("port1", OPTION_SHORT_PORT, server1.getPort(),
              INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT1));

          if (forceAddBindDN1)
          {
            commandBuilder.addArgument(getBindDN1Arg(uData));
            if (forceAddBindPwdFile1)
            {
              FileBasedArgument bindPasswordFileArg = getBindPasswordFile1Arg();
              bindPasswordFileArg.getNameToValueMap().put("{password file}",
                  "{password file}");
              commandBuilder.addArgument(bindPasswordFileArg);
            }
            else
            {
              commandBuilder.addObfuscatedArgument(getBindPassword1Arg(arg));
            }
          }
        }
        else if (OPTION_LONG_BINDDN.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getBindDN1Arg(uData));
        }
        else if (OPTION_LONG_BINDPWD.equals(arg.getLongIdentifier()))
        {
          if (useAdminUID)
          {
            adminInformationAdded = true;
            commandBuilder.addObfuscatedArgument(getAdminPasswordArg(arg));
          }
          else
          {
            commandBuilder.addObfuscatedArgument(getBindPassword1Arg(arg));
          }
        }
        else if (OPTION_LONG_BINDPWD_FILE.equals(arg.getLongIdentifier()))
        {
          if (useAdminUID)
          {
            commandBuilder.addArgument(getAdminPasswordFileArg(arg));
          }
          else
          {
            FileBasedArgument bindPasswordFileArg = getBindPasswordFile1Arg();
            bindPasswordFileArg.getNameToValueMap().putAll(
                ((FileBasedArgument)arg).getNameToValueMap());
            commandBuilder.addArgument(bindPasswordFileArg);
          }
        }
        else
        {
          if (OPTION_LONG_ADMIN_UID.equals(arg.getLongIdentifier()))
          {
            adminInformationAdded = true;
          }

          addArgument(commandBuilder, arg, firstServerCommandBuilder.isObfuscated(arg));
        }
      }
    }

    EnableReplicationServerData server2 = uData.getServer2();
    if (sourceServerCI != null && sourceServerCI.getCommandBuilder() != null)
    {
      CommandBuilder interactionBuilder = sourceServerCI.getCommandBuilder();
      boolean useAdminUID = existsArg(interactionBuilder, OPTION_LONG_ADMIN_UID);
      boolean hasBindDN = existsArg(interactionBuilder, OPTION_LONG_BINDDN);
//    This is required when both the bindDN and the admin UID are provided
      // in the command-line.
      boolean forceAddBindDN2 = false;
      boolean forceAddBindPwdFile2 = false;
      if (useAdminUID)
      {
        DN bindDN2 = server2.getBindDn();
        String adminUID = uData.getAdminUid();
        if (bindDN2 != null
            && adminUID != null
            && !getAdministratorDN(adminUID).equals(bindDN2))
        {
          forceAddBindDN2 = true;
          forceAddBindPwdFile2 = existsArg(interactionBuilder, OPTION_LONG_BINDPWD_FILE);
        }
      }
      ArrayList<Argument> argsToAnalyze = new ArrayList<>();
      for (Argument arg : interactionBuilder.getArguments())
      {
        if (OPTION_LONG_HOST.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(
              getHostArg("host2", 'O', server2.getHostName(), INFO_DESCRIPTION_ENABLE_REPLICATION_HOST2));
        }
        else if (OPTION_LONG_PORT.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getPortArg("port2", null, server2.getPort(),
              INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT2));

          if (forceAddBindDN2)
          {
            commandBuilder.addArgument(getBindDN2Arg(uData, OPTION_SHORT_BINDDN));
            if (forceAddBindPwdFile2)
            {
              FileBasedArgument bindPasswordFileArg = getBindPasswordFile2Arg();
              bindPasswordFileArg.getNameToValueMap().put("{password file}",
                  "{password file}");
              commandBuilder.addArgument(bindPasswordFileArg);
            }
            else
            {
              commandBuilder.addObfuscatedArgument(getBindPassword2Arg(arg));
            }
          }
        }
        else if (OPTION_LONG_BINDDN.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getBindDN2Arg(uData, null));
        }
        else if (OPTION_LONG_BINDPWD.equals(arg.getLongIdentifier()))
        {
          if (useAdminUID && !adminInformationAdded)
          {
            adminInformationAdded = true;
            commandBuilder.addObfuscatedArgument(getAdminPasswordArg(arg));
          }
          else if (hasBindDN)
          {
            commandBuilder.addObfuscatedArgument(getBindPassword2Arg(arg));
          }
        }
        else if (OPTION_LONG_BINDPWD_FILE.equals(arg.getLongIdentifier()))
        {
          if (useAdminUID && !adminInformationAdded)
          {
            adminInformationAdded = true;
            commandBuilder.addArgument(getAdminPasswordFileArg(arg));
          }
          else if (hasBindDN)
          {
            FileBasedArgument bindPasswordFileArg = getBindPasswordFile2Arg();
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
        if (!existsArg(commandBuilder, arg.getLongIdentifier()))
        {
          addArgument(commandBuilder, arg, interactionBuilder.isObfuscated(arg));
        }
      }
    }

    // Try to add the new administration information.
    if (!adminInformationAdded)
    {
      if (uData.getAdminUid() != null)
      {
        final StringArgument adminUID = adminUid(
                INFO_DESCRIPTION_REPLICATION_ADMIN_UID.get(ENABLE_REPLICATION_SUBCMD_NAME));
        adminUID.addValue(uData.getAdminUid());
        commandBuilder.addArgument(adminUID);
      }

      if (userProvidedAdminPwdFile != null)
      {
        commandBuilder.addArgument(userProvidedAdminPwdFile);
      }
      else if (uData.getAdminPwd() != null)
      {
        Argument bindPasswordArg = getAdminPasswordArg();
        bindPasswordArg.addValue(uData.getAdminPwd());
        commandBuilder.addObfuscatedArgument(bindPasswordArg);
      }
    }

    if (server1.configureReplicationServer() &&
        !server1.configureReplicationDomain())
    {
      commandBuilder.addArgument(newBooleanArgument(
          argParser.server1.onlyReplicationServerArg, INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER1));
    }

    if (!server1.configureReplicationServer() &&
        server1.configureReplicationDomain())
    {
      commandBuilder.addArgument(newBooleanArgument(
          argParser.server1.noReplicationServerArg, INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER1));
    }

    if (server1.configureReplicationServer() &&
        server1.getReplicationPort() > 0)
    {
      commandBuilder.addArgument(getReplicationPortArg(
          "replicationPort1", server1, 8989, INFO_DESCRIPTION_ENABLE_REPLICATION_PORT1));
    }
    if (server1.isSecureReplication())
    {
      commandBuilder.addArgument(
          newBooleanArgument("secureReplication1", INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION1));
    }

    if (server2.configureReplicationServer() &&
        !server2.configureReplicationDomain())
    {
      commandBuilder.addArgument(newBooleanArgument(
          argParser.server2.onlyReplicationServerArg, INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER2));
    }

    if (!server2.configureReplicationServer() &&
        server2.configureReplicationDomain())
    {
      commandBuilder.addArgument(newBooleanArgument(
          argParser.server2.noReplicationServerArg, INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER2));
    }
    if (server2.configureReplicationServer() &&
        server2.getReplicationPort() > 0)
    {
      commandBuilder.addArgument(getReplicationPortArg(
          "replicationPort2", server2, server2.getReplicationPort(), INFO_DESCRIPTION_ENABLE_REPLICATION_PORT2));
    }
    if (server2.isSecureReplication())
    {
      commandBuilder.addArgument(
          newBooleanArgument("secureReplication2", INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION2));
    }

    if (!uData.replicateSchema())
    {
      commandBuilder.addArgument(
              BooleanArgument.builder("noSchemaReplication")
                      .description(INFO_DESCRIPTION_ENABLE_REPLICATION_NO_SCHEMA_REPLICATION.get())
                      .buildArgument());
    }
    if (argParser.skipReplicationPortCheck())
    {
      commandBuilder.addArgument(
              BooleanArgument.builder("skipPortCheck")
                      .shortIdentifier('S')
                      .description(INFO_DESCRIPTION_ENABLE_REPLICATION_SKIPPORT.get())
                      .buildArgument());
    }
    if (argParser.useSecondServerAsSchemaSource())
    {
      commandBuilder.addArgument(
              BooleanArgument.builder("useSecondServerAsSchemaSource")
                      .description(INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SECOND_AS_SCHEMA_SOURCE.get(
                              "--" + argParser.noSchemaReplicationArg.getLongIdentifier()))
                      .buildArgument());
    }
  }

  private IntegerArgument getReplicationPortArg(
      String name, EnableReplicationServerData server, int defaultValue, Arg0 description) throws ArgumentException
  {
    IntegerArgument replicationPort =
            IntegerArgument.builder(name)
                    .shortIdentifier('r')
                    .description(description.get())
                    .defaultValue(defaultValue)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    replicationPort.addValue(String.valueOf(server.getReplicationPort()));
    return replicationPort;
  }

  private BooleanArgument newBooleanArgument(String name, Arg0 msg) throws ArgumentException
  {
    return BooleanArgument.builder(name)
            .description(msg.get())
            .buildArgument();
  }

  private BooleanArgument newBooleanArgument(BooleanArgument arg, Arg0 msg) throws ArgumentException
  {
    return BooleanArgument.builder(arg.getLongIdentifier())
            .shortIdentifier(arg.getShortIdentifier())
            .description(msg.get())
            .buildArgument();
  }

  private StringArgument getBindPassword1Arg(Argument arg) throws ArgumentException
  {
    return getBindPasswordArg("bindPassword1", arg, INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD1);
  }

  private StringArgument getBindPassword2Arg(Argument arg) throws ArgumentException
  {
    return getBindPasswordArg("bindPassword2", arg, INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD2);
  }

  private StringArgument getBindPasswordArg(String name, Argument arg, Arg0 bindPwdMsg) throws ArgumentException
  {
    StringArgument bindPasswordArg =
            StringArgument.builder(name)
                    .description(bindPwdMsg.get())
                    .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                    .buildArgument();
    bindPasswordArg.addValue(arg.getValue());
    return bindPasswordArg;
  }

  private FileBasedArgument getBindPasswordFile1Arg() throws ArgumentException
  {
    return FileBasedArgument.builder("bindPasswordFile1")
            .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE1.get())
            .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
            .buildArgument();
  }

  private FileBasedArgument getBindPasswordFile2Arg() throws ArgumentException
  {
    return FileBasedArgument.builder("bindPasswordFile2")
            .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE2.get())
            .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
            .buildArgument();
  }

  private StringArgument getBindDN1Arg(EnableReplicationUserData uData) throws ArgumentException
  {
    StringArgument bindDN =
            StringArgument.builder("bindDN1")
                    .shortIdentifier(OPTION_SHORT_BINDDN)
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN1.get())
                    .defaultValue("cn=Directory Manager")
                    .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                    .buildArgument();
    bindDN.addValue(uData.getServer1().getBindDn().toString());
    return bindDN;
  }

  private StringArgument getBindDN2Arg(EnableReplicationUserData uData, Character shortIdentifier)
      throws ArgumentException
  {
    StringArgument bindDN =
            StringArgument.builder("bindDN2")
                    .shortIdentifier(shortIdentifier)
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN2.get())
                    .defaultValue("cn=Directory Manager")
                    .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                    .buildArgument();
    bindDN.addValue(uData.getServer2().getBindDn().toString());
    return bindDN;
  }

  private void updateCommandBuilder(CommandBuilder commandBuilder,
      SourceDestinationServerUserData uData)
  throws ArgumentException
  {
    // Update the arguments used in the console interaction with the
    // actual arguments of dsreplication.

    if (firstServerCommandBuilder != null)
    {
      for (Argument arg : firstServerCommandBuilder.getArguments())
      {
        if (OPTION_LONG_HOST.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getHostArg("hostSource", 'O', uData.getHostNameSource(),
              INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_SOURCE));
        }
        else if (OPTION_LONG_PORT.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getPortArg("portSource", null, uData.getPortSource(),
              INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_SOURCE));
        }
        else if (OPTION_LONG_BINDPWD.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addObfuscatedArgument(getAdminPasswordArg(arg));
        }
        else if (OPTION_LONG_BINDPWD_FILE.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getAdminPasswordFileArg(arg));
        }
        else
        {
          addArgument(commandBuilder, arg, firstServerCommandBuilder.isObfuscated(arg));
        }
      }
    }

    if (sourceServerCI != null && sourceServerCI.getCommandBuilder() != null)
    {
      CommandBuilder interactionBuilder = sourceServerCI.getCommandBuilder();
      for (Argument arg : interactionBuilder.getArguments())
      {
        if (OPTION_LONG_HOST.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getHostArg("hostDestination", 'O', uData.getHostNameDestination(),
              INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_DESTINATION));
        }
        else if (OPTION_LONG_PORT.equals(arg.getLongIdentifier()))
        {
          commandBuilder.addArgument(getPortArg("portDestination", null, uData.getPortDestination(),
              INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_DESTINATION));
        }
      }
    }
  }

  private StringArgument getAdminPasswordArg(Argument arg) throws ArgumentException
  {
    StringArgument sArg = getAdminPasswordArg();
    sArg.addValue(arg.getValue());
    return sArg;
  }

  private StringArgument getAdminPasswordArg() throws ArgumentException
  {
    return StringArgument.builder("adminPassword")
            .shortIdentifier(OPTION_SHORT_BINDPWD)
            .description(INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get())
            .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
            .buildArgument();
  }

  private FileBasedArgument getAdminPasswordFileArg(Argument arg) throws ArgumentException
  {
    FileBasedArgument fbArg =
            FileBasedArgument.builder("adminPasswordFile")
                    .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                    .description(INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get())
                    .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                    .buildArgument();
    fbArg.getNameToValueMap().putAll(((FileBasedArgument) arg).getNameToValueMap());
    return fbArg;
  }

  private IntegerArgument getPortArg(String longIdentifier, Character shortIdentifier, int value, Arg0 arg)
      throws ArgumentException
  {
    IntegerArgument iArg =
            IntegerArgument.builder(longIdentifier)
                    .shortIdentifier(shortIdentifier)
                    .description(arg.get())
                    .defaultValue(4444)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    iArg.addValue(String.valueOf(value));
    return iArg;
  }

  private StringArgument getHostArg(String longIdentifier, char shortIdentifier, String value,
      Arg0 description) throws ArgumentException
  {
    StringArgument sArg =
            StringArgument.builder(longIdentifier)
                    .shortIdentifier(shortIdentifier)
                    .description(description.get())
                    .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                    .buildArgument();
    sArg.addValue(value);
    return sArg;
  }

  private void updateAvailableAndReplicatedSuffixesForOneDomain(
      ConnectionWrapper connDomain, ConnectionWrapper connOther,
      Set<DN> availableSuffixes, Set<DN> alreadyReplicatedSuffixes)
  {
    HostPort replicationServer = getReplicationServerHostPort(connOther);
    boolean isReplicationServerConfigured = replicationServer != null;

    Collection<ReplicaDescriptor> replicas = getReplicas(connDomain);
    for (ReplicaDescriptor replica : replicas)
    {
      final DN suffixDn = replica.getSuffix().getDN();
      if (!isReplicationServerConfigured)
      {
        if (replica.isReplicated())
        {
          alreadyReplicatedSuffixes.add(suffixDn);
        }
        availableSuffixes.add(suffixDn);
      }

      if (!isReplicationServerConfigured)
      {
        availableSuffixes.add(suffixDn);
      }
      else if (!replica.isReplicated())
      {
        availableSuffixes.add(suffixDn);
      }
      else if (replica.getReplicationServers().contains(replicationServer))
      {
        alreadyReplicatedSuffixes.add(suffixDn);
      }
      else
      {
        availableSuffixes.add(suffixDn);
      }
    }
  }

  private void updateAvailableAndReplicatedSuffixesForNoDomain(
      ConnectionWrapper conn1, ConnectionWrapper conn2,
      Set<DN> availableSuffixes, Set<DN> alreadyReplicatedSuffixes)
  {
    HostPort replicationServer1 = getReplicationServerHostPort(conn1);
    boolean isReplicationServer1Configured = replicationServer1 != null;

    HostPort replicationServer2 = getReplicationServerHostPort(conn2);
    boolean isReplicationServer2Configured = replicationServer2 != null;

    TopologyCache cache1 = isReplicationServer1Configured ? createTopologyCache(conn1) : null;
    TopologyCache cache2 = isReplicationServer2Configured ? createTopologyCache(conn2) : null;
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
      addAllAvailableSuffixes(availableSuffixes, cache1.getSuffixes(), replicationServer1);
    }
    else if (cache2 != null)
    {
      addAllAvailableSuffixes(availableSuffixes, cache2.getSuffixes(), replicationServer2);
    }
  }

  private TopologyCache createTopologyCache(ConnectionWrapper conn)
  {
    try
    {
      ADSContext adsContext = new ADSContext(conn);
      if (adsContext.hasAdminData())
      {
        TopologyCache cache = new TopologyCache(adsContext, getTrustManager(sourceServerCI), getConnectTimeout());
        cache.getFilter().setSearchMonitoringInformation(false);
        cache.setPreferredConnections(getPreferredConnections(conn));
        cache.reloadTopology();
        return cache;
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error loading topology cache in "
          + conn.getLdapUrl() + ": " + t, t));
    }
    return null;
  }

  private void addAllAvailableSuffixes(Collection<DN> availableSuffixes,
      Set<SuffixDescriptor> suffixes, HostPort rsToFind)
  {
    for (SuffixDescriptor suffix : suffixes)
    {
      for (HostPort rs : suffix.getReplicationServers())
      {
        if (rs.equals(rsToFind))
        {
          availableSuffixes.add(suffix.getDN());
        }
      }
    }
  }

  private void updateAvailableAndReplicatedSuffixesForNoDomainOneSense(
      TopologyCache cache1, TopologyCache cache2, HostPort replicationServer1, HostPort replicationServer2,
      Set<DN> availableSuffixes, Set<DN> alreadyReplicatedSuffixes)
  {
    for (SuffixDescriptor suffix : cache1.getSuffixes())
    {
      for (HostPort rServer : suffix.getReplicationServers())
      {
        if (rServer.equals(replicationServer1))
        {
          boolean isSecondReplicatedInSameTopology = false;
          boolean isSecondReplicated = false;
          boolean isFirstReplicated = false;
          for (SuffixDescriptor suffix2 : cache2.getSuffixes())
          {
            if (suffix.getDN().equals(suffix2.getDN()))
            {
              for (HostPort rServer2 : suffix2.getReplicationServers())
              {
                if (rServer2.equals(replicationServer2))
                {
                  isSecondReplicated = true;
                }
                if (rServer.equals(replicationServer2))
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
      Set<DN> baseDNsWithNoReplicationServer, Set<DN> baseDNsWithOneReplicationServer)
  {
    EnableReplicationServerData server1 = uData.getServer1();
    EnableReplicationServerData server2 = uData.getServer2();
    if (server1.configureReplicationServer() &&
        server2.configureReplicationServer())
    {
      return;
    }

    Set<SuffixDescriptor> suffixes = new HashSet<>();
    createTopologyCache(adsCtx1, uData, suffixes);
    createTopologyCache(adsCtx2, uData, suffixes);

    HostPort repServer1 = getReplicationServerHostPort(adsCtx1.getConnection(), server1.getHostName());
    HostPort repServer2 = getReplicationServerHostPort(adsCtx2.getConnection(), server2.getHostName());
    for (DN baseDN : uData.getBaseDNs())
    {
      int nReplicationServers = 0;
      for (SuffixDescriptor suffix : suffixes)
      {
        if (suffix.getDN().equals(baseDN))
        {
          Set<HostPort> replicationServers = suffix.getReplicationServers();
          nReplicationServers += replicationServers.size();
          for (HostPort repServer : replicationServers)
          {
            if (server1.configureReplicationServer() &&
                repServer.equals(repServer1))
            {
              nReplicationServers --;
            }
            if (server2.configureReplicationServer() &&
                repServer.equals(repServer2))
            {
              nReplicationServers --;
            }
          }
        }
      }
      if (server1.configureReplicationServer())
      {
        nReplicationServers ++;
      }
      if (server2.configureReplicationServer())
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

  private HostPort getReplicationServerHostPort(ConnectionWrapper conn)
  {
    return getReplicationServerHostPort(conn, conn.getHostPort().getHost());
  }

  private HostPort getReplicationServerHostPort(ConnectionWrapper conn, String hostName)
  {
    int replPort = getReplicationPort(conn);
    return replPort != -1 ? new HostPort(hostName, replPort) : null;
  }

  private void createTopologyCache(ADSContext adsCtx, ReplicationUserData uData, Set<SuffixDescriptor> suffixes)
  {
    try
    {
      if (adsCtx.hasAdminData())
      {
        TopologyCache cache = new TopologyCache(adsCtx, getTrustManager(sourceServerCI), getConnectTimeout());
        cache.getFilter().setSearchMonitoringInformation(false);
        cache.getFilter().addBaseDNsToSearch(uData.getBaseDNs());
        cache.reloadTopology();
        suffixes.addAll(cache.getSuffixes());
      }
    }
    catch (Throwable t)
    {
      String msg = "Error loading topology cache from " + adsCtx.getHostPort() + ": " + t;
      logger.warn(LocalizableMessage.raw(msg, t));
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
   * @return {@code true} if the registry containing all the data is
   * the first registry and {@code false} otherwise.
   * @throws ReplicationCliException if there is a problem reading or updating
   * the registries.
   */
  private boolean mergeRegistries(ADSContext adsCtx1, ADSContext adsCtx2)
  throws ReplicationCliException
  {
    PointAdder pointAdder = new PointAdder(this);
    try
    {
      Set<PreferredConnection> cnx = new LinkedHashSet<>(getPreferredConnections(adsCtx1.getConnection()));
      cnx.addAll(getPreferredConnections(adsCtx2.getConnection()));
      TopologyCache cache1 = createTopologyCache(adsCtx1, cnx);
      TopologyCache cache2 = createTopologyCache(adsCtx2, cnx);

      // Look for the cache with biggest number of replication servers:
      // that one is going to be source.
      int nRepServers1 = countReplicationServers(cache1);
      int nRepServers2 = countReplicationServers(cache2);

      ADSContext ctxSource;
      ADSContext ctxDestination;
      if (nRepServers1 >= nRepServers2)
      {
        ctxSource = adsCtx1;
        ctxDestination = adsCtx2;
      }
      else
      {
        ctxSource = adsCtx2;
        ctxDestination = adsCtx1;
      }

      HostPort hostPortSource = ctxSource.getHostPort();
      HostPort hostPortDestination = ctxDestination.getHostPort();
      if (isInteractive())
      {
        LocalizableMessage msg = INFO_REPLICATION_MERGING_REGISTRIES_CONFIRMATION.get(hostPortSource,
            hostPortDestination, hostPortSource, hostPortDestination);
        if (!askConfirmation(msg, true))
        {
          throw new ReplicationCliException(ERR_REPLICATION_USER_CANCELLED.get(), USER_CANCELLED, null);
        }
      }
      else
      {
        LocalizableMessage msg = INFO_REPLICATION_MERGING_REGISTRIES_DESCRIPTION.get(hostPortSource,
            hostPortDestination, hostPortSource, hostPortDestination);
        println(msg);
        println();
      }

      print(INFO_REPLICATION_MERGING_REGISTRIES_PROGRESS.get());
      pointAdder.start();

      checkCanMergeReplicationTopologies(adsCtx1, cache1);
      checkCanMergeReplicationTopologies(adsCtx2, cache2);

      Set<LocalizableMessage> commonRepServerIDErrors = new HashSet<>();
      for (ServerDescriptor server1 : cache1.getServers())
      {
        if (findSameReplicationServer(server1, cache2.getServers(), commonRepServerIDErrors))
        {
          break;
        }
      }
      Set<LocalizableMessage> commonDomainIDErrors = new HashSet<>();
      for (SuffixDescriptor suffix1 : cache1.getSuffixes())
      {
        for (ReplicaDescriptor replica1 : suffix1.getReplicas())
        {
          if (replica1.isReplicated())
          {
            for (SuffixDescriptor suffix2 : cache2.getSuffixes())
            {
              if (findReplicaInSuffix2(replica1, suffix2, suffix1.getDN(), commonDomainIDErrors))
              {
                break;
              }
            }
          }
        }
      }
      if (!commonRepServerIDErrors.isEmpty() || !commonDomainIDErrors.isEmpty())
      {
        LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
        if (!commonRepServerIDErrors.isEmpty())
        {
          mb.append(ERR_REPLICATION_ENABLE_COMMON_REPLICATION_SERVER_ID.get(
              getMessageFromCollection(commonRepServerIDErrors, Constants.LINE_SEPARATOR)));
        }
        if (!commonDomainIDErrors.isEmpty())
        {
          if (mb.length() > 0)
          {
            mb.append(Constants.LINE_SEPARATOR);
          }
          mb.append(ERR_REPLICATION_ENABLE_COMMON_DOMAIN_ID.get(
              getMessageFromCollection(commonDomainIDErrors, Constants.LINE_SEPARATOR)));
        }
        throw new ReplicationCliException(mb.toMessage(),
            REPLICATION_ADS_MERGE_NOT_SUPPORTED, null);
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
        logger.error(LocalizableMessage.raw("Error merging registry of "
            + adsCtxSource.getHostPort()
            + " with registry of "
            + adsCtxDestination.getHostPort()
            + " " + adce, adce));
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
            logger.info(LocalizableMessage.raw("Seeding to replication server on "+
                server.getHostPort(true)+" with certificates of "+ adsCtxSource.getHostPort()));
            try (ConnectionWrapper conn = getConnection(cacheDestination, server))
            {
              ServerDescriptor.seedAdsTrustStore(conn, adsCtxSource.getTrustedCertificates());
            }
          }
        }
      }
      catch (Throwable t)
      {
        logger.error(LocalizableMessage.raw("Error seeding truststore: "+t, t));
        LocalizableMessage msg = ERR_REPLICATION_ENABLE_SEEDING_TRUSTSTORE.get(adsCtx2.getHostPort(),
            adsCtx1.getHostPort(), toString(t));
        throw new ReplicationCliException(msg, ERROR_SEEDING_TRUSTORE, t);
      }
      pointAdder.stop();
      print(formatter.getSpace());
      print(formatter.getFormattedDone());
      println();

      return adsCtxSource == adsCtx1;
    }
    finally
    {
      pointAdder.stop();
    }
  }

  private int countReplicationServers(TopologyCache cache)
  {
    int nbRepServers = 0;
    for (ServerDescriptor server : cache.getServers())
    {
      if (server.isReplicationServer())
      {
        nbRepServers++;
      }
    }
    return nbRepServers;
  }

  private void checkCanMergeReplicationTopologies(ADSContext adsCtx, TopologyCache cache)
      throws ReplicationCliException
  {
    Set<LocalizableMessage> cacheErrors = cache.getErrorMessages();
    if (!cacheErrors.isEmpty())
    {
      LocalizableMessage msg = getMessageFromCollection(cacheErrors, Constants.LINE_SEPARATOR);
      throw new ReplicationCliException(
          ERR_REPLICATION_CANNOT_MERGE_WITH_ERRORS.get(adsCtx.getHostPort(), msg),
          ERROR_READING_ADS, null);
    }
  }

  private boolean findSameReplicationServer(ServerDescriptor serverToFind, Set<ServerDescriptor> servers,
      Set<LocalizableMessage> commonRepServerIDErrors)
  {
    if (!serverToFind.isReplicationServer())
    {
      return false;
    }

    int replicationID1 = serverToFind.getReplicationServerId();
    HostPort replServerHostPort1 = serverToFind.getReplicationServerHostPort();
    for (ServerDescriptor server2 : servers)
    {
      if (server2.isReplicationServer() && server2.getReplicationServerId() == replicationID1
          && !server2.getReplicationServerHostPort().equals(replServerHostPort1))
      {
        commonRepServerIDErrors.add(ERR_REPLICATION_ENABLE_COMMON_REPLICATION_SERVER_ID_ARG.get(
            serverToFind.getHostPort(true), server2.getHostPort(true), replicationID1));
        return true;
      }
    }
    return false;
  }

  private boolean findReplicaInSuffix2(ReplicaDescriptor replica1, SuffixDescriptor suffix2, DN suffix1DN,
      Set<LocalizableMessage> commonDomainIDErrors)
  {
    if (!suffix2.getDN().equals(replica1.getSuffix().getDN()))
    {
      // Conflicting domain names must apply to same suffix.
      return false;
    }

    int domain1Id = replica1.getServerId();
    for (ReplicaDescriptor replica2 : suffix2.getReplicas())
    {
      if (replica2.isReplicated()
          && domain1Id == replica2.getServerId())
      {
        commonDomainIDErrors.add(
            ERR_REPLICATION_ENABLE_COMMON_DOMAIN_ID_ARG.get(replica1.getServer().getHostPort(true), suffix1DN,
                replica2.getServer().getHostPort(true), suffix2.getDN(), domain1Id));
        return true;
      }
    }
    return false;
  }

  private String toString(Throwable t)
  {
    if (t instanceof OpenDsException)
    {
      LocalizableMessage msg = ((OpenDsException) t).getMessageObject();
      if (msg != null)
      {
        return msg.toString();
      }
    }
    return t.toString();
  }

  private TopologyCache createTopologyCache(ADSContext adsCtx, Set<PreferredConnection> cnx)
      throws ReplicationCliException
  {
    TopologyCache cache = new TopologyCache(adsCtx, getTrustManager(sourceServerCI), getConnectTimeout());
    cache.setPreferredConnections(cnx);
    cache.getFilter().setSearchBaseDNInformation(false);
    try
    {
      cache.reloadTopology();
      return cache;
    }
    catch (TopologyCacheException te)
    {
      logger.error(LocalizableMessage.raw(
          "Error reading topology cache of " + adsCtx.getHostPort() + " " + te, te));
      throw new ReplicationCliException(ERR_REPLICATION_READING_ADS.get(te.getMessageObject()), ERROR_UPDATING_ADS, te);
    }
  }

  private ConnectionWrapper getConnection(TopologyCache cache, ServerDescriptor server) throws LdapException
  {
    ConnectionWrapper conn = cache.getAdsContext().getConnection();

    TopologyCacheFilter filter = new TopologyCacheFilter();
    filter.setSearchMonitoringInformation(false);
    filter.setSearchBaseDNInformation(false);

    ServerLoader loader = new ServerLoader(server.getAdsProperties(),
        conn.getBindDn(), conn.getBindPassword(), getTrustManager(sourceServerCI), getConnectTimeout(),
        cache.getPreferredConnections(), filter);
    return loader.createConnectionWrapper();
  }

  /**
   * Returns {@code true} if the provided baseDN is replicated in the
   * provided server, {@code false} otherwise.
   * @param server the server.
   * @param baseDN the base DN.
   * @return {@code true} if the provided baseDN is replicated in the
   * provided server, {@code false} otherwise.
   */
  private boolean isBaseDNReplicated(ServerDescriptor server, DN baseDN)
  {
    return findReplicaForSuffixDN(server.getReplicas(), baseDN) != null;
  }

  /**
   * Returns {@code true} if the provided baseDN is replicated between
   * both servers, {@code false} otherwise.
   * @param server1 the first server.
   * @param server2 the second server.
   * @param baseDN the base DN.
   * @return {@code true} if the provided baseDN is replicated between
   * both servers, {@code false} otherwise.
   */
  private boolean isBaseDNReplicated(ServerDescriptor server1, ServerDescriptor server2, DN baseDN)
  {
    final ReplicaDescriptor replica1 = findReplicaForSuffixDN(server1.getReplicas(), baseDN);
    final ReplicaDescriptor replica2 = findReplicaForSuffixDN(server2.getReplicas(), baseDN);
    if (replica1 != null && replica2 != null)
    {
      Set<HostPort> replServers1 = replica1.getSuffix().getReplicationServers();
      Set<HostPort> replServers2 = replica1.getSuffix().getReplicationServers();
      for (HostPort replServer1 : replServers1)
      {
        if (replServers2.contains(replServer1))
        {
          // it is replicated in both
          return true;
        }
      }
    }
    return false;
  }

  private ReplicaDescriptor findReplicaForSuffixDN(Set<ReplicaDescriptor> replicas, DN suffixDN)
  {
    for (ReplicaDescriptor replica : replicas)
    {
      if (replica.getSuffix().getDN().equals(suffixDN))
      {
        return replica;
      }
    }
    return null;
  }

  private ReplicaDescriptor findReplicatedReplicaForSuffixDN(Set<ReplicaDescriptor> replicas, DN baseDN)
  {
    for (ReplicaDescriptor replica : replicas)
    {
      if (replica.isReplicated() && replica.getSuffix().getDN().equals(baseDN))
      {
        return replica;
      }
    }
    return null;
  }

  private Set<DN> findAllReplicatedSuffixDNs(Collection<ReplicaDescriptor> replicas)
  {
    Set<DN> results = new HashSet<>();
    for (ReplicaDescriptor replica : replicas)
    {
      if (replica.isReplicated())
      {
        results.add(replica.getSuffix().getDN());
      }
    }
    return results;
  }

  private boolean displayLogFileAtEnd(String subCommand)
  {
    final List<String> subCommands = Arrays.asList(ENABLE_REPLICATION_SUBCMD_NAME, DISABLE_REPLICATION_SUBCMD_NAME,
        INITIALIZE_ALL_REPLICATION_SUBCMD_NAME, INITIALIZE_REPLICATION_SUBCMD_NAME, RESET_CHANGE_NUMBER_SUBCMD_NAME);
    return subCommands.contains(subCommand);
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
      if (binDir.lastIndexOf(File.separatorChar) != binDir.length() - 1)
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
    if (isWindows())
    {
      return getBinaryDir() + scriptBasicName + ".bat";
    }
    return getBinaryDir() + scriptBasicName;
  }
}

/** Class used to compare replication servers. */
class ReplicationServerComparator implements Comparator<ServerDescriptor>
{
  @Override
  public int compare(ServerDescriptor s1, ServerDescriptor s2)
  {
    return s1.getReplicationServerHostPort().compareTo(s2.getReplicationServerHostPort());
  }
}
