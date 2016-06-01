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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.INFO_BINDPWD_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_PORT_PLACEHOLDER;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.quicksetup.Constants;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.config.AdministrationConnector;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.tasks.PurgeConflictsHistoricalTask;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentGroup;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;

/**
 * This class is used to parse the arguments passed to the replication CLI.
 * It also checks the compatibility between the values and that all the
 * required information has been provided.  However it does not do any
 * verification that require connection to any server.
 */
public class ReplicationCliArgumentParser extends SecureConnectionCliParser
{
  /** Arguments used when enabling replication for a server. */
  static class ServerArgs
  {
    /** The 'hostName' argument for the first server. */
    StringArgument hostNameArg;
    /** The 'port' argument for the first server. */
    IntegerArgument portArg;
    /** The 'bindDN' argument for the first server. */
    StringArgument bindDnArg;
    /** The 'bindPasswordFile' argument for the first server. */
    FileBasedArgument bindPasswordFileArg;
    /** The 'bindPassword' argument for the first server. */
    StringArgument bindPasswordArg;
    /** The 'replicationPort' argument for the first server. */
    IntegerArgument replicationPortArg;
    /** The 'noReplicationServer' argument for the first server. */
    BooleanArgument noReplicationServerArg;
    /** The 'onlyReplicationServer' argument for the first server. */
    BooleanArgument onlyReplicationServerArg;
    /** The 'secureReplication' argument for the first server. */
    BooleanArgument secureReplicationArg;

    /**
     * Get the password which has to be used for the command to connect to this server without
     * prompting the user in the enable replication subcommand. If no password was specified return
     * null.
     *
     * @return the password which has to be used for the command to connect to this server without
     *         prompting the user in the enable replication subcommand. If no password was specified
     *         return null.
     */
    String getBindPassword()
    {
      return ReplicationCliArgumentParser.getBindPassword(bindPasswordArg, bindPasswordFileArg);
    }

    boolean configureReplicationDomain()
    {
      return !onlyReplicationServerArg.isPresent();
    }

    boolean configureReplicationServer()
    {
      return !noReplicationServerArg.isPresent();
    }
  }

  private SubCommand enableReplicationSubCmd;
  private SubCommand disableReplicationSubCmd;
  private SubCommand initializeReplicationSubCmd;
  private SubCommand initializeAllReplicationSubCmd;
  private SubCommand postExternalInitializationSubCmd;
  private SubCommand preExternalInitializationSubCmd;
  private SubCommand resetChangelogNumber;
  private SubCommand statusReplicationSubCmd;
  private SubCommand purgeHistoricalSubCmd;

  private int defaultAdminPort =
    AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;

  /** No-prompt argument. */
  BooleanArgument noPromptArg;

  /** Arguments for the first server. */
  ServerArgs server1 = new ServerArgs();
  /** Arguments for the second server. */
  ServerArgs server2 = new ServerArgs();

  /** The 'skipPortCheckArg' argument to not check replication ports. */
  private BooleanArgument skipPortCheckArg;
  /** The 'noSchemaReplication' argument to not replicate schema. */
  BooleanArgument noSchemaReplicationArg;
  /** The 'useSecondServerAsSchemaSource' argument to not replicate schema. */
  private BooleanArgument useSecondServerAsSchemaSourceArg;
  /** The 'disableAll' argument to disable all the replication configuration of server. */
  BooleanArgument disableAllArg;
  /** The 'disableReplicationServer' argument to disable the replication server. */
  BooleanArgument disableReplicationServerArg;
  /** The 'hostName' argument for the source server. */
  private StringArgument hostNameSourceArg;
  /** The 'port' argument for the source server. */
  private IntegerArgument portSourceArg;
  /** The 'hostName' argument for the destination server. */
  private StringArgument hostNameDestinationArg;
  /** The 'port' argument for the destination server. */
  private IntegerArgument portDestinationArg;
  /** The 'suffixes' global argument. */
  StringArgument baseDNsArg;
  /** The 'quiet' argument. */
  private BooleanArgument quietArg;
  /** The 'scriptFriendly' argument. */
  BooleanArgument scriptFriendlyArg;
  /** Properties file argument. */
  StringArgument propertiesFileArgument;
  /** No-properties file argument. */
  BooleanArgument noPropertiesFileArgument;
  /** The argument that the user must set to display the equivalent non-interactive mode argument. */
  BooleanArgument displayEquivalentArgument;
  /** The argument that allows the user to dump the equivalent non-interactive command to a file. */
  StringArgument equivalentCommandFileArgument;
  /** The argument that the user must set to have advanced options in interactive mode. */
  BooleanArgument advancedArg;

  /**
   * The argument set by the user to specify the configuration file
   * (useful when dsreplication purge-historical runs locally).
   */
  private StringArgument  configFileArg;

  TaskScheduleArgs taskArgs;

  /** The 'maximumDuration' argument for the purge of historical. */
  IntegerArgument maximumDurationArg;

  /** The 'change-number' argument for task reset-changenumber. */
  IntegerArgument resetChangeNumber;

  /** The text of the enable replication subcommand. */
  static final String ENABLE_REPLICATION_SUBCMD_NAME = "enable";
  /** The text of the disable replication subcommand. */
  static final String DISABLE_REPLICATION_SUBCMD_NAME = "disable";
  /** The text of the initialize replication subcommand. */
  static final String INITIALIZE_REPLICATION_SUBCMD_NAME = "initialize";
  /** The text of the initialize all replication subcommand. */
  public static final String INITIALIZE_ALL_REPLICATION_SUBCMD_NAME = "initialize-all";
  /** The text of the pre external initialization subcommand. */
  static final String PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME = "pre-external-initialization";
  /** The text of the initialize all replication subcommand. */
  static final String POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME = "post-external-initialization";
  /** The text of the reset changenumber subcommand. */
  static final String RESET_CHANGE_NUMBER_SUBCMD_NAME = "reset-change-number";

  /** The text of the status replication subcommand. */
  static final String STATUS_REPLICATION_SUBCMD_NAME = "status";
  /** The text of the purge historical subcommand. */
  static final String PURGE_HISTORICAL_SUBCMD_NAME = "purge-historical";
  /** This CLI is always using the administration connector with SSL. */
  private static final boolean alwaysSSL = true;

  /**
   * Creates a new instance of this argument parser with no arguments.
   *
   * @param mainClassName
   *          The fully-qualified name of the Java class that should
   *          be invoked to launch the program with which this
   *          argument parser is associated.
   */
  ReplicationCliArgumentParser(String mainClassName)
  {
    super(mainClassName,
        INFO_REPLICATION_TOOL_DESCRIPTION.get(ENABLE_REPLICATION_SUBCMD_NAME, INITIALIZE_REPLICATION_SUBCMD_NAME),
        false);
    setShortToolDescription(REF_SHORT_DESC_DSREPLICATION.get());
    setVersionHandler(new DirectoryServerVersionHandler());
  }

  /**
   * Initialize the parser with the Global options and subcommands.
   *
   * @param outStream
   *          The output stream to use for standard output, or {@code null}
   *          if standard output is not needed.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used to create this argument.
   */
  void initializeParser(OutputStream outStream)
      throws ArgumentException
  {
    taskArgs = new TaskScheduleArgs();
    initializeGlobalArguments(outStream);
    try
    {
      defaultAdminPort = secureArgsList.getAdminPortFromConfig();
    }
    catch (Throwable t)
    {
      // Ignore
    }

    secureArgsList.initArgumentsWithConfiguration(this);

    createEnableReplicationSubCommand();
    createDisableReplicationSubCommand();
    createRelatedServersOptions();
    createInitializeReplicationSubCommand();
    createInitializeAllReplicationSubCommand();
    createPreExternalInitializationSubCommand();
    createPostExternalInitializationSubCommand();
    createResetChangeNumberSubCommand();
    createStatusReplicationSubCommand();
    createPurgeHistoricalSubCommand();
  }

  /**
   * Checks all the options parameters and updates the provided LocalizableMessageBuilder
   * with the errors that where encountered.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  void validateOptions(LocalizableMessageBuilder buf)
  {
    validateGlobalOptions(buf);
    validateSubcommandOptions(buf);
  }

  @Override
  public int validateGlobalOptions(LocalizableMessageBuilder buf)
  {
    int returnValue;
    super.validateGlobalOptions(buf);

    final List<LocalizableMessage> errors = new ArrayList<>();
    // Check that we can write on the provided path where we write the
    // equivalent non-interactive commands.
    if (equivalentCommandFileArgument.isPresent())
    {
      String file = equivalentCommandFileArgument.getValue();
      if (!canWrite(file))
      {
        errors.add(ERR_REPLICATION_CANNOT_WRITE_EQUIVALENT_COMMAND_LINE_FILE.get(file));
      }
      else
      {
        File f = new File(file);
        if (f.isDirectory())
        {
          errors.add(
              ERR_REPLICATION_EQUIVALENT_COMMAND_LINE_FILE_DIRECTORY.get(file));
        }
      }
    }

    addErrorMessageIfArgumentsConflict(errors, noPromptArg, advancedArg);

    if (!isInteractive())
    {
      // Check that we have the required data
      if (!baseDNsArg.isPresent() &&
          !isStatusReplicationSubcommand() &&
          !isResetChangeNumber() &&
          !disableAllArg.isPresent() &&
          !disableReplicationServerArg.isPresent())
      {
        errors.add(ERR_REPLICATION_NO_BASE_DN_PROVIDED.get());
      }
      if (getBindPasswordAdmin() == null &&
          !isPurgeHistoricalSubcommand())
      {
        errors.add(ERR_REPLICATION_NO_ADMINISTRATOR_PASSWORD_PROVIDED.get(
            "--"+ secureArgsList.getBindPasswordArg().getLongIdentifier(),
            "--"+ secureArgsList.getBindPasswordFileArg().getLongIdentifier()));
      }
    }

    if (baseDNsArg.isPresent())
    {
      List<String> baseDNs = baseDNsArg.getValues();
      for (String dn : baseDNs)
      {
        if (!isDN(dn))
        {
          errors.add(ERR_REPLICATION_NOT_A_VALID_BASEDN.get(dn));
        }
        if (Constants.REPLICATION_CHANGES_DN.equalsIgnoreCase(dn))
        {
          errors.add(ERR_REPLICATION_NOT_A_USER_SUFFIX.get(Constants.REPLICATION_CHANGES_DN));
        }
      }
    }
    if (!errors.isEmpty())
    {
      for (LocalizableMessage error : errors)
      {
        addMessage(buf, error);
      }
    }

    if (buf.length() > 0)
    {
      returnValue = ReplicationCliReturnCode.CONFLICTING_ARGS.getReturnCode();
    }
    else
    {
      returnValue = ReplicationCliReturnCode.SUCCESSFUL_NOP.getReturnCode();
    }
    return returnValue;
  }

  /**
   * Initialize Global option.
   *
   * @param outStream
   *          The output stream used for the usage.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   */
  private void initializeGlobalArguments(OutputStream outStream)
  throws ArgumentException
  {
    ArrayList<Argument> defaultArgs = new ArrayList<>(createGlobalArguments(outStream, alwaysSSL));

    Argument[] argsToRemove = {
            secureArgsList.getHostNameArg(),
            secureArgsList.getPortArg(),
            secureArgsList.getBindDnArg(),
            secureArgsList.getBindPasswordFileArg(),
            secureArgsList.getBindPasswordArg()
    };

    for (Argument arg : argsToRemove)
    {
      defaultArgs.remove(arg);
    }
    defaultArgs.remove(super.noPropertiesFileArg);
    defaultArgs.remove(super.propertiesFileArg);
    // Remove it from the default location and redefine it.
    defaultArgs.remove(getAdminUidArg());

    int index = 0;

    baseDNsArg =
            StringArgument.builder(OPTION_LONG_BASEDN)
                    .shortIdentifier(OPTION_SHORT_BASEDN)
                    .description(INFO_DESCRIPTION_REPLICATION_BASEDNS.get())
                    .multiValued()
                    .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                    .buildArgument();
    defaultArgs.add(index++, baseDNsArg);

    secureArgsList.createVisibleAdminUidArgument(
        INFO_DESCRIPTION_REPLICATION_ADMIN_UID.get(ENABLE_REPLICATION_SUBCMD_NAME));
    defaultArgs.add(index++, secureArgsList.getAdminUidArg());

    secureArgsList.setBindPasswordArgument(
            StringArgument.builder(OPTION_LONG_ADMIN_PWD)
                    .shortIdentifier(OPTION_SHORT_BINDPWD)
                    .description(INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get())
                    .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                    .buildArgument());
    defaultArgs.add(index++, secureArgsList.getBindPasswordArg());

    secureArgsList.setBindPasswordFileArgument(
            FileBasedArgument.builder(OPTION_LONG_ADMIN_PWD_FILE)
                    .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                    .description(INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get())
                    .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                    .buildArgument());
    defaultArgs.add(index++, secureArgsList.getBindPasswordFileArg());

    defaultArgs.remove(verboseArg);

    quietArg = quietArgument();
    defaultArgs.add(index++, quietArg);

    noPromptArg = noPromptArgument();
    defaultArgs.add(index++, noPromptArg);

    displayEquivalentArgument = displayEquivalentCommandArgument();

    defaultArgs.add(index++, displayEquivalentArgument);

    equivalentCommandFileArgument =
        CommonArguments
            .equivalentCommandFileArgument(
                INFO_REPLICATION_DESCRIPTION_EQUIVALENT_COMMAND_FILE_PATH.get());
    defaultArgs.add(index++, equivalentCommandFileArgument);

    advancedArg = advancedModeArgument();
    defaultArgs.add(index++, advancedArg);

    configFileArg = configFileArgument();
    defaultArgs.add(index++, configFileArg);

    this.propertiesFileArgument = propertiesFileArgument();
    defaultArgs.add(this.propertiesFileArgument);
    setFilePropertiesArgument(this.propertiesFileArgument);

    this.noPropertiesFileArgument = noPropertiesFileArgument();
    defaultArgs.add(this.noPropertiesFileArgument);
    setNoPropertiesFileArgument(this.noPropertiesFileArgument);

    initializeGlobalArguments(defaultArgs, null);
  }

  /**
   * Initialize the global options with the provided set of arguments.
   * @param args the arguments to use to initialize the global options.
   * @param argGroup the group to which args will be added.
   * @throws ArgumentException if there is a conflict with the provided
   * arguments.
   */
  @Override
  protected void initializeGlobalArguments(
          Collection<Argument> args,
          ArgumentGroup argGroup)
  throws ArgumentException
  {
    for (Argument arg : args)
    {
      if (arg == advancedArg)
      {
        ArgumentGroup toolOptionsGroup = new ArgumentGroup(
            INFO_DESCRIPTION_CONFIG_OPTIONS_ARGS.get(), 2);
        addGlobalArgument(advancedArg, toolOptionsGroup);
      }
      else
      {
        addGlobalArgument(arg, argGroup);
      }
    }

    // Set the propertiesFile argument
    setFilePropertiesArgument(propertiesFileArg);
  }

  /** Creates the enable replication subcommand and all the specific options for the subcommand. */
  private void createEnableReplicationSubCommand() throws ArgumentException
  {
    createServerArgs1();
    createServerArgs2();

    skipPortCheckArg =
            BooleanArgument.builder("skipPortCheck")
                    .shortIdentifier('S')
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_SKIPPORT.get())
                    .buildArgument();
    noSchemaReplicationArg =
            BooleanArgument.builder("noSchemaReplication")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_NO_SCHEMA_REPLICATION.get())
                    .buildArgument();
    useSecondServerAsSchemaSourceArg =
            BooleanArgument.builder("useSecondServerAsSchemaSource")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SECOND_AS_SCHEMA_SOURCE.get(
                            "--" + noSchemaReplicationArg.getLongIdentifier()))
                    .buildArgument();

    enableReplicationSubCmd = new SubCommand(this,
        ENABLE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_ENABLE_REPLICATION.get());
    addArgumentsToSubCommand(enableReplicationSubCmd,
            server1.hostNameArg, server1.portArg, server1.bindDnArg, server1.bindPasswordArg,
            server1.bindPasswordFileArg, server1.replicationPortArg, server1.secureReplicationArg,
            server1.noReplicationServerArg, server1.onlyReplicationServerArg,
            server2.hostNameArg, server2.portArg, server2.bindDnArg, server2.bindPasswordArg,
            server2.bindPasswordFileArg, server2.replicationPortArg, server2.secureReplicationArg,
            server2.noReplicationServerArg, server2.onlyReplicationServerArg,
            skipPortCheckArg, noSchemaReplicationArg, useSecondServerAsSchemaSourceArg);
  }

  private void createServerArgs1() throws ArgumentException
  {
    ServerArgs server = server1;
    server.hostNameArg =
            StringArgument.builder("host1")
                    .shortIdentifier(OPTION_SHORT_HOST)
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_HOST1.get())
                    .defaultValue(secureArgsList.getDefaultHostName())
                    .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                    .buildArgument();
    server.portArg =
            IntegerArgument.builder("port1")
                    .shortIdentifier(OPTION_SHORT_PORT)
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT1.get())
                    .range(1, 65336)
                    .defaultValue(defaultAdminPort)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    server.bindDnArg =
            StringArgument.builder("bindDN1")
                    .shortIdentifier(OPTION_SHORT_BINDDN)
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN1.get())
                    .defaultValue("cn=Directory Manager")
                    .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                    .buildArgument();
    server.bindPasswordArg =
            StringArgument.builder("bindPassword1")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD1.get())
                    .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                    .buildArgument();
    server.bindPasswordFileArg =
            FileBasedArgument.builder("bindPasswordFile1")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE1.get())
                    .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                    .buildArgument();
    server.replicationPortArg =
            IntegerArgument.builder("replicationPort1")
                    .shortIdentifier('r')
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_PORT1.get())
                    .range(1, 65336)
                    .defaultValue(8989)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    server.secureReplicationArg =
            BooleanArgument.builder("secureReplication1")
                    .description(INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION1.get())
                    .buildArgument();
    server.noReplicationServerArg =
            BooleanArgument.builder("noReplicationServer1")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER1.get())
                    .buildArgument();
    server.onlyReplicationServerArg =
            BooleanArgument.builder("onlyReplicationServer1")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER1.get())
                    .buildArgument();
  }

  private void createServerArgs2() throws ArgumentException
  {
    ServerArgs server = server2;
    server.hostNameArg =
            StringArgument.builder("host2")
                    .shortIdentifier('O')
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_HOST2.get())
                    .defaultValue(secureArgsList.getDefaultHostName())
                    .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                    .buildArgument();
    server.portArg =
            IntegerArgument.builder("port2")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT2.get())
                    .range(1, 65336)
                    .defaultValue(defaultAdminPort)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    server.bindDnArg =
            StringArgument.builder("bindDN2")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN2.get())
                    .defaultValue("cn=Directory Manager")
                    .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                    .buildArgument();
    server.bindPasswordArg =
            StringArgument.builder("bindPassword2")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD2.get())
                    .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                    .buildArgument();
    server.bindPasswordFileArg =
            FileBasedArgument.builder("bindPasswordFile2")
                    .shortIdentifier('F')
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE2.get())
                    .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                    .buildArgument();
    server.replicationPortArg =
            IntegerArgument.builder("replicationPort2")
                    .shortIdentifier('R')
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_PORT2.get())
                    .range(1, 65336)
                    .defaultValue(8989)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    server.secureReplicationArg =
            BooleanArgument.builder("secureReplication2")
                    .description(INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION2.get())
                    .buildArgument();
    server.noReplicationServerArg =
            BooleanArgument.builder("noReplicationServer2")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER2.get())
                    .buildArgument();
    server.onlyReplicationServerArg =
            BooleanArgument.builder("onlyReplicationServer2")
                    .description(INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER2.get())
                    .buildArgument();
  }

  /**
   * Creates the disable replication subcommand and all the specific options
   * for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg and
   * portArg have been created.
   */
  private void createDisableReplicationSubCommand()
  throws ArgumentException
  {
    disableReplicationSubCmd = new SubCommand(this,
        DISABLE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_DISABLE_REPLICATION.get());
    secureArgsList.setBindDnArgDescription(INFO_DESCRIPTION_DISABLE_REPLICATION_BINDDN.get());
    disableReplicationServerArg =
            BooleanArgument.builder("disableReplicationServer")
                    .shortIdentifier('a')
                    .description(INFO_DESCRIPTION_DISABLE_REPLICATION_SERVER.get())
                    .buildArgument();
    disableAllArg =
            BooleanArgument.builder("disableAll")
                    .description(INFO_DESCRIPTION_DISABLE_ALL.get())
                    .buildArgument();

    Argument[] argsToAdd = { secureArgsList.getHostNameArg(),
            secureArgsList.getPortArg(), secureArgsList.getBindDnArg(),
        disableReplicationServerArg, disableAllArg};
    for (Argument arg : argsToAdd)
    {
      disableReplicationSubCmd.addArgument(arg);
    }
  }

  /**
   * Creates the initialize replication subcommand and all the specific options
   * for the subcommand.
   */
  private void createInitializeReplicationSubCommand() throws ArgumentException
  {
    initializeReplicationSubCmd = new SubCommand(this, INITIALIZE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_INITIALIZE_REPLICATION.get(INITIALIZE_ALL_REPLICATION_SUBCMD_NAME));
    addArgumentsToSubCommand(initializeReplicationSubCmd,
        hostNameSourceArg, portSourceArg, hostNameDestinationArg, portDestinationArg);
  }

  private void createRelatedServersOptions() throws ArgumentException
  {
    final String defaultHostName = secureArgsList.getDefaultHostName();
    hostNameSourceArg =
            StringArgument.builder("hostSource")
                    .shortIdentifier(OPTION_SHORT_HOST)
                    .description(INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_SOURCE.get())
                    .defaultValue(defaultHostName)
                    .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                    .buildArgument();
    portSourceArg =
            IntegerArgument.builder("portSource")
                    .shortIdentifier(OPTION_SHORT_PORT)
                    .description(INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_SOURCE.get())
                    .range(1, 65336)
                    .defaultValue(defaultAdminPort)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
    hostNameDestinationArg =
            StringArgument.builder("hostDestination")
                    .shortIdentifier('O')
                    .description(INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_DESTINATION.get())
                    .defaultValue(defaultHostName)
                    .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                    .buildArgument();
    portDestinationArg =
            IntegerArgument.builder("portDestination")
                    .description(INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_DESTINATION.get())
                    .range(1, 65336)
                    .defaultValue(defaultAdminPort)
                    .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                    .buildArgument();
  }

  /**
   * Creates the initialize all replication subcommand and all the specific
   * options for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg and
   * portArg have been created.
   */
  private void createInitializeAllReplicationSubCommand()
  throws ArgumentException
  {
    initializeAllReplicationSubCmd = new SubCommand(this,
        INITIALIZE_ALL_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_INITIALIZE_ALL_REPLICATION.get(
            INITIALIZE_REPLICATION_SUBCMD_NAME));
    Argument[] argsToAdd = { secureArgsList.getHostNameArg(),
            secureArgsList.getPortArg() };
    for (Argument arg : argsToAdd)
    {
      initializeAllReplicationSubCmd.addArgument(arg);
    }
  }

  /**
   * Creates the subcommand that the user must launch before doing an external
   * initialization of the topology ( and all the specific
   * options for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg and
   * portArg have been created.
   */
  private void createPreExternalInitializationSubCommand()
  throws ArgumentException
  {
    preExternalInitializationSubCmd = new SubCommand(this,
        PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_PRE_EXTERNAL_INITIALIZATION.get(
            POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
    BooleanArgument externalInitializationLocalOnlyArg =
            BooleanArgument.builder("local-only")
                    .shortIdentifier('l')
                    .description(LocalizableMessage.EMPTY)
                    .hidden()
                    .buildArgument();

    Argument[] argsToAdd = { secureArgsList.getHostNameArg(),
            secureArgsList.getPortArg(),
        externalInitializationLocalOnlyArg};

    for (Argument arg : argsToAdd)
    {
      preExternalInitializationSubCmd.addArgument(arg);
    }
  }

  /**
   * Creates the subcommand that the user must launch after doing an external
   * initialization of the topology ( and all the specific
   * options for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg and
   * portArg have been created.
   */
  private void createPostExternalInitializationSubCommand()
  throws ArgumentException
  {
    postExternalInitializationSubCmd = new SubCommand(this,
        POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_POST_EXTERNAL_INITIALIZATION.get(
            PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
    Argument[] argsToAdd = { secureArgsList.getHostNameArg(),
            secureArgsList.getPortArg() };
    for (Argument arg : argsToAdd)
    {
      postExternalInitializationSubCmd.addArgument(arg);
    }
  }

  private void createResetChangeNumberSubCommand() throws ArgumentException
  {
    resetChangelogNumber = new SubCommand(this, RESET_CHANGE_NUMBER_SUBCMD_NAME,
        INFO_DESCRIPTION_RESET_CHANGE_NUMBER.get());
    resetChangeNumber = newChangeNumberArgument();
    addArgumentsToSubCommand(resetChangelogNumber,
            hostNameSourceArg, portSourceArg, hostNameDestinationArg, portDestinationArg, resetChangeNumber);
  }

  private IntegerArgument newChangeNumberArgument() throws ArgumentException
  {
    return IntegerArgument.builder("change-number")
            .description(INFO_DESCRIPTION_START_CHANGE_NUMBER.get())
            .valuePlaceholder(INFO_CHANGE_NUMBER_PLACEHOLDER.get())
            .buildArgument();
  }

  /**
   * Creates the status replication subcommand and all the specific options
   * for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg and
   * portArg have been created.
   */
  private void createStatusReplicationSubCommand() throws ArgumentException
  {
    statusReplicationSubCmd = new SubCommand(this,
        STATUS_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_STATUS_REPLICATION.get());
    scriptFriendlyArg =
            BooleanArgument.builder("script-friendly")
                    .shortIdentifier('s')
                    .description(INFO_DESCRIPTION_SCRIPT_FRIENDLY.get())
                    .buildArgument();
    addArgumentsToSubCommand(
            statusReplicationSubCmd, secureArgsList.getHostNameArg(), secureArgsList.getPortArg(), scriptFriendlyArg);
  }

  /**
   * Creates the purge historical subcommand and all the specific options
   * for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg and
   * portArg have been created.
   */
  private void createPurgeHistoricalSubCommand() throws ArgumentException
  {
    maximumDurationArg =
            IntegerArgument.builder("maximumDuration")
                    .description(INFO_DESCRIPTION_PURGE_HISTORICAL_MAXIMUM_DURATION.get())
                    .required()
                    .lowerBound(0)
                    .defaultValue(PurgeConflictsHistoricalTask.DEFAULT_MAX_DURATION)
                    .valuePlaceholder(INFO_MAXIMUM_DURATION_PLACEHOLDER.get())
                    .buildArgument();

    purgeHistoricalSubCmd = new SubCommand(
        this,
        PURGE_HISTORICAL_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_PURGE_HISTORICAL.get());

    addArgumentsToSubCommand(purgeHistoricalSubCmd,
            secureArgsList.getHostNameArg(), secureArgsList.getPortArg(), maximumDurationArg);
    addArgumentsToSubCommand(purgeHistoricalSubCmd, taskArgs.getArguments());
  }

  private void addArgumentsToSubCommand(final SubCommand subCommand, final Argument... args) throws ArgumentException
  {
    for (final Argument arg : args)
    {
      subCommand.addArgument(arg);
    }
  }

  /**
   * Tells whether the user specified to have an interactive operation or not.
   * This method must be called after calling parseArguments.
   * @return {@code true} if the user specified to have an interactive
   * operation and {@code false} otherwise.
   */
  public boolean isInteractive()
  {
    return !noPromptArg.isPresent();
  }

  /**
   * Tells whether the user specified to have a quite operation or not.
   * This method must be called after calling parseArguments.
   * @return {@code true} if the user specified to have a quite operation
   * and {@code false} otherwise.
   */
  public boolean isQuiet()
  {
    return quietArg.isPresent();
  }

  /**
   * Tells whether the user specified to have a script-friendly output or not.
   * This method must be called after calling parseArguments.
   * @return {@code true} if the user specified to have a script-friendly
   * output and {@code false} otherwise.
   */
  public boolean isScriptFriendly()
  {
    return scriptFriendlyArg.isPresent();
  }

  /**
   * Get the global administrator password which has to be used for the command
   * to connect to the server(s) without prompting the user.  If no password was
   * specified, return null.
   *
   * @return the global administrator password which has to be used for the
   * command to connect to the server(s) without prompting the user.  If no
   * password was specified, return null.
   */
  public String getBindPasswordAdmin()
  {
    return getBindPassword(secureArgsList.getBindPasswordArg(), secureArgsList.getBindPasswordFileArg());
  }

  /**
   * Returns the Administrator UID explicitly provided in the command-line.
   * @return the Administrator UID explicitly provided in the command-line.
   */
  @Override
  public String getAdministratorUID()
  {
    return getValue(getAdminUidArg());
  }

  /**
   * Returns the default Administrator UID value.
   * @return the default Administrator UID value.
   */
  public String getAdministratorUIDOrDefault()
  {
    return getValueOrDefault(getAdminUidArg());
  }

  /**
   * Returns the Administrator UID argument.
   * @return the Administrator UID argument.
   */
  StringArgument getAdminUidArg()
  {
    return secureArgsList.getAdminUidArg();
  }

  /**
   * Returns the first server replication port explicitly provided in the enable
   * replication subcommand.
   * @return the first server replication port explicitly provided in the enable
   * replication subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getReplicationPort1()
  {
    return getValue(server1.replicationPortArg);
  }

  /**
   * Returns the second server replication port explicitly provided in the
   * enable replication subcommand.
   * @return the second server replication port explicitly provided in the
   * enable replication subcommand.  Returns -1 if no port was explicitly
   * provided.
   */
  public int getReplicationPort2()
  {
    return getValue(server2.replicationPortArg);
  }

  /**
   * Returns whether the user asked to skip the replication port checks (if the
   * ports are free) or not.
   * @return {@code true} the user asked to skip the replication port
   * checks (if the ports are free) and {@code false} otherwise.
   */
  boolean skipReplicationPortCheck()
  {
    return skipPortCheckArg.isPresent();
  }

  /**
   * Returns whether the user asked to not replicate the schema between servers.
   * @return {@code true} if the user asked to not replicate schema and
   * {@code false} otherwise.
   */
  boolean noSchemaReplication()
  {
    return noSchemaReplicationArg.isPresent();
  }

  /**
   * Returns whether the user asked to use the second server to initialize the
   * schema of the first server.
   * @return {@code true} if the user asked to use the second server to
   * initialize the schema of the first server and {@code false} otherwise.
   */
  boolean useSecondServerAsSchemaSource()
  {
    return useSecondServerAsSchemaSourceArg.isPresent();
  }

  /**
   * Returns the host name explicitly provided in the disable replication
   * subcommand.
   * @return the host name explicitly provided in the disable replication
   * subcommand.
   */
  public String getHostNameToDisable()
  {
    return getValue(secureArgsList.getHostNameArg());
  }

  /**
   * Returns the host name default value in the disable replication
   * subcommand.
   * @return the host name default value in the disable replication
   * subcommand.
   */
  public String getHostNameToDisableOrDefault()
  {
    return getValueOrDefault(secureArgsList.getHostNameArg());
  }

  /**
   * Returns the server bind dn explicitly provided in the disable replication
   * subcommand.
   * @return the server bind dn explicitly provided in the disable replication
   * subcommand.
   */
  public String getBindDNToDisable()
  {
    return getValue(secureArgsList.getBindDnArg());
  }

  /**
   * Returns the host name default value in the status replication subcommand.
   * @return the host name default value in the status replication subcommand.
   */
  public String getHostNameToStatusOrDefault()
  {
    return getValueOrDefault(secureArgsList.getHostNameArg());
  }

  /**
   * Returns the host name default value in the initialize all replication
   * subcommand.
   * @return the host name default value in the initialize all replication
   * subcommand.
   */
  public String getHostNameToInitializeAllOrDefault()
  {
    return getValueOrDefault(secureArgsList.getHostNameArg());
  }

  /**
   * Returns the source host name explicitly provided in the initialize
   * replication subcommand.
   * @return the source host name explicitly provided in the initialize
   * replication subcommand.
   */
  public String getHostNameSource()
  {
    return getValue(hostNameSourceArg);
  }

  /**
   * Returns the first host name default value in the initialize replication
   * subcommand.
   * @return the first host name default value in the initialize replication
   * subcommand.
   */
  public String getHostNameSourceOrDefault()
  {
    return getValueOrDefault(hostNameSourceArg);
  }

  /**
   * Returns the destination host name explicitly provided in the initialize
   * replication subcommand.
   * @return the destination host name explicitly provided in the initialize
   * replication subcommand.
   */
  public String getHostNameDestination()
  {
    return getValue(hostNameDestinationArg);
  }

  /**
   * Returns the destination host name default value in the initialize
   * replication subcommand.
   * @return the destination host name default value in the initialize
   * replication subcommand.
   */
  public String getHostNameDestinationOrDefault()
  {
    return getValueOrDefault(hostNameDestinationArg);
  }

  /**
   * Returns the source server port explicitly provided in the initialize
   * replication subcommand.
   * @return the source server port explicitly provided in the initialize
   * replication subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPortSource()
  {
    return getValue(portSourceArg);
  }

  /**
   * Returns the source server port default value in the initialize replication
   * subcommand.
   * @return the source server port default value in the initialize replication
   * subcommand.
   */
  public int getPortSourceOrDefault()
  {
    return getValueOrDefault(portSourceArg);
  }

  /**
   * Returns the destination server port explicitly provided in the initialize
   * replication subcommand.
   * @return the destination server port explicitly provided in the initialize
   * replication subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPortDestination()
  {
    return getValue(portDestinationArg);
  }

  /**
   * Returns the destination server port default value in the initialize
   * replication subcommand.
   * @return the destination server port default value in the initialize
   * replication subcommand.
   */
  public int getPortDestinationOrDefault()
  {
    return getValueOrDefault(portDestinationArg);
  }

  /**
   * Returns the server port explicitly provided in the disable replication
   * subcommand.
   * @return the server port explicitly provided in the disable replication
   * subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPortToDisable()
  {
    return getValue(secureArgsList.getPortArg());
  }

  /**
   * Returns the server port default value in the disable replication
   * subcommand.
   * @return the server port default value in the disable replication
   * subcommand.
   */
  public int getPortToDisableOrDefault()
  {
    return getValueOrDefault(secureArgsList.getPortArg());
  }

  /**
   * Returns the server port default value in the initialize all replication
   * subcommand.
   * @return the server port default value in the initialize all replication
   * subcommand.
   */
  public int getPortToInitializeAllOrDefault()
  {
    return getValueOrDefault(secureArgsList.getPortArg());
  }

  /**
   * Returns the server port default value in the status replication subcommand.
   * @return the server port default value in the status replication subcommand.
   */
  public int getPortToStatusOrDefault()
  {
    return getValueOrDefault(secureArgsList.getPortArg());
  }

  /**
   * Returns the list of base DNs provided by the user.
   * @return the list of base DNs provided by the user.
   */
  public List<String> getBaseDNs()
  {
    return baseDNsArg.getValues();
  }

  /**
   * Returns the config file value provided in the hidden argument of the
   * command-line.
   * @return the config file value provided in the hidden argument of the
   * command-line.
   */
  public String getConfigFile()
  {
    return getValue(configFileArg);
  }

  /**
   * Returns the argument's value if present or else return the argument's default value.
   *
   * @param arg the argument
   * @return the argument's value if present, the argument's default value if not present
   */
  static String getValueOrDefault(StringArgument arg)
  {
    String v = getValue(arg);
    String defaultValue = getDefaultValue(arg);
    return v != null ? v : defaultValue;
  }

  /**
   * Returns the argument's value if present or else return the argument's default value.
   *
   * @param arg the argument
   * @return the argument's value if present, the argument's default value if not present
   */
  static int getValueOrDefault(IntegerArgument arg)
  {
    int v = getValue(arg);
    int defaultValue = getDefaultValue(arg);
    return v != -1 ? v : defaultValue;
  }

  /**
   * Returns the value of the provided argument only if the user provided it
   * explicitly.
   * @param arg the StringArgument to be handled.
   * @return the value of the provided argument only if the user provided it
   * explicitly.
   */
  static String getValue(StringArgument arg)
  {
    return arg.isPresent() ? arg.getValue() : null;
  }

  /**
   * Returns the default value of the provided argument.
   * @param arg the StringArgument to be handled.
   * @return the default value of the provided argument.
   */
  static String getDefaultValue(StringArgument arg)
  {
    return arg.getDefaultValue();
  }

  /**
   * Returns the value of the provided argument only if the user provided it
   * explicitly.
   * @param arg the StringArgument to be handled.
   * @return the value of the provided argument only if the user provided it
   * explicitly.
   */
  static int getValue(IntegerArgument arg)
  {
    if (arg.isPresent())
    {
      try
      {
        return arg.getIntValue();
      }
      catch (ArgumentException ae)
      {
        // This is a bug
        throw new IllegalStateException(
            "There was an argument exception calling "+
            "ReplicationCliParser.getValue().  This appears to be a bug "+
            "because this method should be called after calling "+
            "parseArguments which should result in an error.", ae);
      }
    }
    return -1;
  }

  /**
   * Returns the default value of the provided argument.
   * @param arg the StringArgument to be handled.
   * @return the default value of the provided argument.
   */
  static int getDefaultValue(IntegerArgument arg)
  {
    String v = arg.getDefaultValue();
    return v != null ? Integer.parseInt(v) : -1;
  }

  /**
   * Checks the subcommand options and updates the provided LocalizableMessageBuilder
   * with the errors that were encountered with the subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateSubcommandOptions(LocalizableMessageBuilder buf)
  {
    if (isEnableReplicationSubcommand())
    {
      validateEnableReplicationOptions(buf);
    }
    else if (isDisableReplicationSubcommand())
    {
      validateDisableReplicationOptions(buf);
    }
    else if (isStatusReplicationSubcommand())
    {
      validateStatusReplicationOptions(buf);
    }
    else  if (isInitializeReplicationSubcommand())
    {
      validateSourceAndDestinationServersOptions(buf);
    }
    else if (isPurgeHistoricalSubcommand())
    {
      validatePurgeHistoricalOptions(buf);
    }
    else if (isResetChangeNumber())
    {
      validateSourceAndDestinationServersOptions(buf);
    }
  }

  /**
   * Checks the purge historical subcommand options and updates the
   * provided LocalizableMessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validatePurgeHistoricalOptions(LocalizableMessageBuilder buf)
  {
    try
    {
      if (!isInteractive() && !connectionArgumentsPresent())
      {
        taskArgs.validateArgsIfOffline();
      }
      else
      {
        taskArgs.validateArgs();
      }
    }
    catch (ClientException | ArgumentException e)
    {
      addMessage(buf, e.getMessageObject());
    }
  }

  /**
   * Returns whether the user provided subcommand is the enable replication
   * or not.
   * @return {@code true} if the user provided subcommand is the
   * enable replication and {@code false} otherwise.
   */
  public boolean isEnableReplicationSubcommand()
  {
    return isSubcommand(ENABLE_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the disable replication
   * or not.
   * @return {@code true} if the user provided subcommand is the
   * disable replication and {@code false} otherwise.
   */
  public boolean isDisableReplicationSubcommand()
  {
    return isSubcommand(DISABLE_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user specified the reset changelog numbering subcommand.
   * @return {@code true} if the user wanted to reset change number
   */
  public boolean isResetChangeNumber()
  {
    return isSubcommand(RESET_CHANGE_NUMBER_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the status replication
   * or not.
   * @return {@code true} if the user provided subcommand is the
   * status replication and {@code false} otherwise.
   */
  public boolean isStatusReplicationSubcommand()
  {
    return isSubcommand(STATUS_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the purge historical
   * or not.
   * @return {@code true} if the user provided subcommand is the
   * purge historical and {@code false} otherwise.
   */
  public boolean isPurgeHistoricalSubcommand()
  {
    return isSubcommand(PURGE_HISTORICAL_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the initialize all
   * replication or not.
   * @return {@code true} if the user provided subcommand is the
   * initialize all replication and {@code false} otherwise.
   */
  public boolean isInitializeAllReplicationSubcommand()
  {
    return isSubcommand(INITIALIZE_ALL_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the pre external
   * initialization or not.
   * @return {@code true} if the user provided subcommand is the
   * pre external initialization and {@code false} otherwise.
   */
  public boolean isPreExternalInitializationSubcommand()
  {
    return isSubcommand(PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the post external
   * initialization or not.
   * @return {@code true} if the user provided subcommand is the
   * post external initialization and {@code false} otherwise.
   */
  public boolean isPostExternalInitializationSubcommand()
  {
    return isSubcommand(POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the initialize replication
   * or not.
   * @return {@code true} if the user provided subcommand is the
   * initialize replication and {@code false} otherwise.
   */
  public boolean isInitializeReplicationSubcommand()
  {
    return isSubcommand(INITIALIZE_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the command-line subcommand has the name provided
   * or not.
   * @param name the name of the subcommand.
   * @return {@code true} if command-line subcommand has the name provided
   * and {@code false} otherwise.
   */
  private boolean isSubcommand(String name)
  {
    SubCommand subCommand = getSubCommand();
    return subCommand != null && subCommand.getName().equalsIgnoreCase(name);
  }

  /**
   * Checks the enable replication subcommand options and updates the provided
   * LocalizableMessageBuilder with the errors that were encountered with the subcommand
   * options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateEnableReplicationOptions(LocalizableMessageBuilder buf)
  {
    appendErrorMessageIfArgumentsConflict(buf, server1.bindPasswordArg, server1.bindPasswordFileArg );
    appendErrorMessageIfArgumentsConflict(buf, server2.bindPasswordArg, server2.bindPasswordFileArg );
    appendErrorMessageIfArgumentsConflict(buf, server1.replicationPortArg, server1.noReplicationServerArg );
    appendErrorMessageIfArgumentsConflict(buf, server1.noReplicationServerArg, server1.onlyReplicationServerArg );
    appendErrorMessageIfArgumentsConflict(buf, server2.replicationPortArg, server2.noReplicationServerArg );
    appendErrorMessageIfArgumentsConflict(buf, server2.noReplicationServerArg, server2.onlyReplicationServerArg );
    appendErrorMessageIfArgumentsConflict(buf,noSchemaReplicationArg, useSecondServerAsSchemaSourceArg);

    if (server1.hostNameArg.getValue().equalsIgnoreCase(server2.hostNameArg.getValue())
        && !isInteractive()
        && server1.portArg.getValue().equals(server2.portArg.getValue()))
    {
      LocalizableMessage message = ERR_REPLICATION_ENABLE_SAME_SERVER_PORT.get(
          server1.hostNameArg.getValue(), server1.portArg.getValue());
      addMessage(buf, message);
    }
  }

  /**
   * Checks the disable replication subcommand options and updates the provided
   * LocalizableMessageBuilder with the errors that were encountered with the subcommand
   * options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateDisableReplicationOptions(LocalizableMessageBuilder buf)
  {
    appendErrorMessageIfArgumentsConflict(buf, getAdminUidArg(), secureArgsList.getBindDnArg());
    appendErrorMessageIfArgumentsConflict(buf, disableAllArg, disableReplicationServerArg);
    appendErrorMessageIfArgumentsConflict(buf, disableAllArg, baseDNsArg);
  }

  /**
   * Checks the status replication subcommand options and updates the provided
   * LocalizableMessageBuilder with the errors that were encountered with the subcommand
   * options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateStatusReplicationOptions(LocalizableMessageBuilder buf)
  {
    if (quietArg.isPresent())
    {
      LocalizableMessage message = ERR_REPLICATION_STATUS_QUIET.get(
          STATUS_REPLICATION_SUBCMD_NAME, "--"+quietArg.getLongIdentifier());
      addMessage(buf, message);
    }
  }

  /**
   * Checks the initialize replication subcommand options and updates the
   * provided LocalizableMessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateSourceAndDestinationServersOptions(LocalizableMessageBuilder buf)
  {
    if (hostNameSourceArg.getValue().equalsIgnoreCase(hostNameDestinationArg.getValue())
        && !isInteractive()
        && portSourceArg.getValue().equals(portDestinationArg.getValue()))
    {
      LocalizableMessage message = ERR_SOURCE_DESTINATION_INITIALIZE_SAME_SERVER_PORT.get(
          hostNameSourceArg.getValue(), portSourceArg.getValue());
      addMessage(buf, message);
    }
  }

  /**
   * Adds a message to the provided LocalizableMessageBuilder.
   * @param buf the LocalizableMessageBuilder.
   * @param message the message to be added.
   */
  private void addMessage(LocalizableMessageBuilder buf, LocalizableMessage message)
  {
    if (buf.length() > 0)
    {
      buf.append(LINE_SEPARATOR);
    }
    buf.append(message);
  }

  /**
   * Returns the SecureConnectionCliArgs object containing the arguments
   * of this parser.
   * @return the SecureConnectionCliArgs object containing the arguments
   * of this parser.
   */
  public SecureConnectionCliArgs getSecureArgsList()
  {
    return secureArgsList;
  }

  /**
   * Returns the TaskScheduleArgs object containing the arguments
   * of this parser.
   * @return the TaskScheduleArgs object containing the arguments
   * of this parser.
   */
  public TaskScheduleArgs getTaskArgsList()
  {
    return taskArgs;
  }

  /**
   * Returns whether the user specified connection arguments or not.
   * @return {@code true} if the user specified connection arguments and
   * {@code false} otherwise.
   */
  boolean connectionArgumentsPresent()
  {
    if (isPurgeHistoricalSubcommand()) {
      boolean secureArgsPresent = getSecureArgsList() != null &&
      getSecureArgsList().argumentsPresent();
      // This have to be explicitly specified because their original definition
      // has been replaced.
      boolean adminArgsPresent = getAdminUidArg().isPresent() ||
      secureArgsList.getBindPasswordArg().isPresent() ||
      secureArgsList.getBindPasswordFileArg().isPresent();
      return secureArgsPresent || adminArgsPresent;
    }
    return true;
  }

  /**
    * Returns the maximum duration explicitly provided in the purge historical
    * replication subcommand.
    * @return the maximum duration explicitly provided in the purge historical
    * replication subcommand.  Returns -1 if no port was explicitly provided.
    */
  public int getMaximumDuration()
  {
     return getValue(maximumDurationArg);
  }

  /**
   * Returns the maximum duration default value in the purge historical
   * replication subcommand.
   * @return the maximum duration default value in the purge historical
   * replication subcommand.
   */
  public int getMaximumDurationOrDefault()
  {
    return getValueOrDefault(maximumDurationArg);
  }

  /**
   * Returns the changenumber specified as argument.
   * @return the changenumber specified as argument
   */
  public int getResetChangeNumber()
  {
    return getValue(resetChangeNumber);
  }

  /**
   * Sets the start change number value.
   * @param changeNumber the new value of the option
   */
  public void setResetChangeNumber(String changeNumber)
  {
    resetChangeNumber.setPresent(true);
    resetChangeNumber.addValue(changeNumber);
  }
}
