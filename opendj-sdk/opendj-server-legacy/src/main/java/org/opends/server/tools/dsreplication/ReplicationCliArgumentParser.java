/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.quicksetup.Constants;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.admin.client.cli.TaskScheduleArgs;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.extensions.ConfigFileHandler;
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
  private SubCommand statusReplicationSubCmd;
  private SubCommand purgeHistoricalSubCmd;

  private int defaultAdminPort =
    AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;

  /** No-prompt argument. */
  BooleanArgument noPromptArg;
  private String defaultLocalHostValue;

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
  /**The 'quiet' argument.   */
  private BooleanArgument quietArg;
  /**The 'scriptFriendly' argument.   */
  BooleanArgument scriptFriendlyArg;
  /**Properties file argument.   */
  StringArgument propertiesFileArgument;
  /**No-properties file argument.   */
  BooleanArgument noPropertiesFileArgument;
  /**
   * The argument that the user must set to display the equivalent
   * non-interactive mode argument.
   */
  BooleanArgument displayEquivalentArgument;
  /**
   * The argument that allows the user to dump the equivalent non-interactive
   * command to a file.
   */
  StringArgument equivalentCommandFileArgument;
  /** The argument that the user must set to have advanced options in interactive mode. */
  BooleanArgument advancedArg;

  /**
   * The argument set by the user to specify the configuration class
   * (useful when dsreplication purge-historical runs locally).
   */
  private StringArgument  configClassArg;

  /**
   * The argument set by the user to specify the configuration file
   * (useful when dsreplication purge-historical runs locally).
   */
  private StringArgument  configFileArg;

  TaskScheduleArgs taskArgs;

  /** The 'maximumDuration' argument for the purge of historical. */
  IntegerArgument maximumDurationArg;

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
        INFO_REPLICATION_TOOL_DESCRIPTION.get(ENABLE_REPLICATION_SUBCMD_NAME,
            INITIALIZE_REPLICATION_SUBCMD_NAME),
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
    createEnableReplicationSubCommand();
    createDisableReplicationSubCommand();
    createInitializeReplicationSubCommand();
    createInitializeAllReplicationSubCommand();
    createPreExternalInitializationSubCommand();
    createPostExternalInitializationSubCommand();
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

  /** {@inheritDoc} */
  @Override
  public int validateGlobalOptions(LocalizableMessageBuilder buf)
  {
    int returnValue;
    super.validateGlobalOptions(buf);
    ArrayList<LocalizableMessage> errors = new ArrayList<LocalizableMessage>();
    if (secureArgsList.bindPasswordArg.isPresent() &&
        secureArgsList.bindPasswordFileArg.isPresent()) {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          secureArgsList.bindPasswordArg.getLongIdentifier(),
          secureArgsList.bindPasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    // Check that we can write on the provided path where we write the
    // equivalent non-interactive commands.
    if (equivalentCommandFileArgument.isPresent())
    {
      String file = equivalentCommandFileArgument.getValue();
      if (!canWrite(file))
      {
        errors.add(
            ERR_REPLICATION_CANNOT_WRITE_EQUIVALENT_COMMAND_LINE_FILE.get(
                file));
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

    if (noPromptArg.isPresent() && advancedArg.isPresent())
    {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          noPromptArg.getLongIdentifier(),
          advancedArg.getLongIdentifier());
      errors.add(message);
    }

    if (!isInteractive())
    {
      // Check that we have the required data
      if (!baseDNsArg.isPresent() &&
          !isStatusReplicationSubcommand() &&
          !disableAllArg.isPresent() &&
          !disableReplicationServerArg.isPresent())
      {
        errors.add(ERR_REPLICATION_NO_BASE_DN_PROVIDED.get());
      }
      if (getBindPasswordAdmin() == null &&
          !isPurgeHistoricalSubcommand())
      {
        errors.add(ERR_REPLICATION_NO_ADMINISTRATOR_PASSWORD_PROVIDED.get(
            "--"+secureArgsList.bindPasswordArg.getLongIdentifier(),
            "--"+secureArgsList.bindPasswordFileArg.getLongIdentifier()));
      }
    }

    if (baseDNsArg.isPresent())
    {
      LinkedList<String> baseDNs = baseDNsArg.getValues();
      for (String dn : baseDNs)
      {
        if (!isDN(dn))
        {
          errors.add(ERR_REPLICATION_NOT_A_VALID_BASEDN.get(dn));
        }
        if (dn.equalsIgnoreCase(Constants.REPLICATION_CHANGES_DN))
        {
          errors.add(ERR_REPLICATION_NOT_A_USER_SUFFIX.get(Constants.REPLICATION_CHANGES_DN));
        }
      }
    }
    if (errors.size() > 0)
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
    ArrayList<Argument> defaultArgs =
      new ArrayList<Argument>(createGlobalArguments(outStream, alwaysSSL));

    Argument[] argsToRemove = {
      secureArgsList.hostNameArg,
      secureArgsList.portArg,
      secureArgsList.bindDnArg,
      secureArgsList.bindPasswordFileArg,
      secureArgsList.bindPasswordArg
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

    baseDNsArg = new StringArgument("baseDNs", OPTION_SHORT_BASEDN,
        OPTION_LONG_BASEDN, false, true, true, INFO_BASEDN_PLACEHOLDER.get(),
        null,
        null, INFO_DESCRIPTION_REPLICATION_BASEDNS.get());
    baseDNsArg.setPropertyName(OPTION_LONG_BASEDN);
    defaultArgs.add(index++, baseDNsArg);

    secureArgsList.adminUidArg = new StringArgument("adminUID", 'I',
        OPTION_LONG_ADMIN_UID, false, false, true,
        INFO_ADMINUID_PLACEHOLDER.get(),
        Constants.GLOBAL_ADMIN_UID, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_UID.get(
            ENABLE_REPLICATION_SUBCMD_NAME));
    getAdminUidArg().setPropertyName(OPTION_LONG_ADMIN_UID);
    getAdminUidArg().setHidden(false);
    defaultArgs.add(index++, getAdminUidArg());

    secureArgsList.bindPasswordArg = new StringArgument(
        OPTION_LONG_ADMIN_PWD.toLowerCase(),
        OPTION_SHORT_BINDPWD, OPTION_LONG_ADMIN_PWD, false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
    defaultArgs.add(index++, secureArgsList.bindPasswordArg);

    secureArgsList.bindPasswordFileArg = new FileBasedArgument(
        OPTION_LONG_ADMIN_PWD_FILE.toLowerCase(),
        OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_ADMIN_PWD_FILE, false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
    defaultArgs.add(index++, secureArgsList.bindPasswordFileArg);

    defaultArgs.remove(verboseArg);

    quietArg = CommonArguments.getQuiet();
    defaultArgs.add(index++, quietArg);

    noPromptArg = CommonArguments.getNoPrompt();
    defaultArgs.add(index++, noPromptArg);

    displayEquivalentArgument = CommonArguments.getDisplayEquivalentCommand();

    defaultArgs.add(index++, displayEquivalentArgument);

    equivalentCommandFileArgument =
        CommonArguments
            .getEquivalentCommandFile(
                INFO_REPLICATION_DESCRIPTION_EQUIVALENT_COMMAND_FILE_PATH.get());
    defaultArgs.add(index++, equivalentCommandFileArgument);

    advancedArg = CommonArguments.getAdvancedMode();
    defaultArgs.add(index++, advancedArg);

    configClassArg =
        CommonArguments.getConfigClass(ConfigFileHandler.class.getName());
    defaultArgs.add(index++, configClassArg);

    configFileArg = CommonArguments.getConfigFile();
    defaultArgs.add(index++, configFileArg);

    for (int i=0; i<index; i++)
    {
      Argument arg = defaultArgs.get(i);
      arg.setPropertyName(arg.getLongIdentifier());
    }

    this.propertiesFileArgument = CommonArguments.getPropertiesFile();
    defaultArgs.add(this.propertiesFileArgument);
    setFilePropertiesArgument(this.propertiesFileArgument);

    this.noPropertiesFileArgument = CommonArguments.getNoPropertiesFile();
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

  /**
   * Creates the enable replication subcommand and all the specific options
   * for the subcommand.
   */
  private void createEnableReplicationSubCommand() throws ArgumentException
  {
    createServerArgs1();
    createServerArgs2();

    skipPortCheckArg = new BooleanArgument(
        "skipportcheck", 'S', "skipPortCheck",
        INFO_DESCRIPTION_ENABLE_REPLICATION_SKIPPORT.get());

    noSchemaReplicationArg = new BooleanArgument(
        "noschemareplication", null, "noSchemaReplication",
        INFO_DESCRIPTION_ENABLE_REPLICATION_NO_SCHEMA_REPLICATION.get());

    useSecondServerAsSchemaSourceArg = new BooleanArgument(
        "usesecondserverasschemasource", null, "useSecondServerAsSchemaSource",
        INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SECOND_AS_SCHEMA_SOURCE.get(
            "--"+noSchemaReplicationArg.getLongIdentifier()));

    enableReplicationSubCmd = new SubCommand(this,
        ENABLE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_ENABLE_REPLICATION.get());

    Argument[] argsToAdd = {
          server1.hostNameArg, server1.portArg, server1.bindDnArg, server1.bindPasswordArg,
          server1.bindPasswordFileArg, server1.replicationPortArg, server1.secureReplicationArg,
          server1.noReplicationServerArg, server1.onlyReplicationServerArg,
          server2.hostNameArg, server2.portArg, server2.bindDnArg, server2.bindPasswordArg,
          server2.bindPasswordFileArg, server2.replicationPortArg, server2.secureReplicationArg,
          server2.noReplicationServerArg, server2.onlyReplicationServerArg,
          skipPortCheckArg, noSchemaReplicationArg, useSecondServerAsSchemaSourceArg
    };
    for (Argument arg : argsToAdd)
    {
      arg.setPropertyName(arg.getLongIdentifier());
      enableReplicationSubCmd.addArgument(arg);
    }
  }

  private void createServerArgs1() throws ArgumentException
  {
    ServerArgs server = server1;
    server.hostNameArg = new StringArgument("host1", OPTION_SHORT_HOST,
        "host1", false, false, true, INFO_HOST_PLACEHOLDER.get(),
        getDefaultHostValue(),
        null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST1.get());

    server.portArg = new IntegerArgument("port1", OPTION_SHORT_PORT, "port1",
        false, false, true, INFO_PORT_PLACEHOLDER.get(),
        defaultAdminPort, null,
        true, 1,
        true, 65336,
        INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT1.get());

    server.bindDnArg = new StringArgument("bindDN1", OPTION_SHORT_BINDDN,
        "bindDN1", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
        "cn=Directory Manager", null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN1.get());

    server.bindPasswordArg = new StringArgument("bindPassword1",
        null, "bindPassword1", false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD1.get());

    server.bindPasswordFileArg = new FileBasedArgument("bindPasswordFile1",
        null, "bindPasswordFile1", false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE1.get());

    server.replicationPortArg = new IntegerArgument("replicationPort1", 'r',
        "replicationPort1", false, false, true, INFO_PORT_PLACEHOLDER.get(),
        8989, null,
        true, 1,
        true, 65336,
        INFO_DESCRIPTION_ENABLE_REPLICATION_PORT1.get());

    server.secureReplicationArg = new BooleanArgument("secureReplication1", null,
        "secureReplication1",
        INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION1.get());

    server.noReplicationServerArg = new BooleanArgument(
        "noreplicationserver1", null, "noReplicationServer1",
        INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER1.get());

    server.onlyReplicationServerArg = new BooleanArgument(
        "onlyreplicationserver1", null, "onlyReplicationServer1",
        INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER1.get());
  }

  private void createServerArgs2() throws ArgumentException
  {
    ServerArgs server = server2;
    server.hostNameArg = new StringArgument("host2", 'O',
        "host2", false, false, true, INFO_HOST_PLACEHOLDER.get(),
        getDefaultHostValue(),
        null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST2.get());

    server.portArg = new IntegerArgument("port2", null, "port2",
        false, false, true, INFO_PORT_PLACEHOLDER.get(), defaultAdminPort, null,
        true, 1,
        true, 65336,
        INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT2.get());

    server.bindDnArg = new StringArgument("bindDN2", null,
        "bindDN2", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
        "cn=Directory Manager", null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN2.get());

    server.bindPasswordArg = new StringArgument("bindPassword2",
        null, "bindPassword2", false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD2.get());

    server.bindPasswordFileArg = new FileBasedArgument("bindPasswordFile2",
        'F', "bindPasswordFile2", false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE2.get());

    server.replicationPortArg = new IntegerArgument("replicationPort2", 'R',
        "replicationPort2", false, false, true, INFO_PORT_PLACEHOLDER.get(),
        8989, null,
        true, 1,
        true, 65336,
        INFO_DESCRIPTION_ENABLE_REPLICATION_PORT2.get());

    server.secureReplicationArg = new BooleanArgument("secureReplication2", null,
        "secureReplication2",
        INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION2.get());

    server.noReplicationServerArg = new BooleanArgument(
        "noreplicationserver2", null, "noReplicationServer2",
        INFO_DESCRIPTION_ENABLE_REPLICATION_NO_REPLICATION_SERVER2.get());

    server.onlyReplicationServerArg = new BooleanArgument(
        "onlyreplicationserver2", null, "onlyReplicationServer2",
        INFO_DESCRIPTION_ENABLE_REPLICATION_ONLY_REPLICATION_SERVER2.get());
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
    secureArgsList.hostNameArg.setDefaultValue(getDefaultHostValue());
    secureArgsList.bindDnArg = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
        OPTION_LONG_BINDDN, false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
        "cn=Directory Manager", OPTION_LONG_BINDDN,
        INFO_DESCRIPTION_DISABLE_REPLICATION_BINDDN.get());
    disableReplicationServerArg = new BooleanArgument(
        "disablereplicationserver", null, "disableReplicationServer",
        INFO_DESCRIPTION_DISABLE_REPLICATION_SERVER.get());
    disableAllArg = new BooleanArgument(
        "disableall", 'a', "disableAll",
        INFO_DESCRIPTION_DISABLE_ALL.get());


    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg, secureArgsList.bindDnArg,
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
  private void createInitializeReplicationSubCommand()
  throws ArgumentException
  {
    hostNameSourceArg = new StringArgument("hostSource", OPTION_SHORT_HOST,
        "hostSource", false, false, true, INFO_HOST_PLACEHOLDER.get(),
        getDefaultHostValue(), null,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_SOURCE.get());

    portSourceArg = new IntegerArgument("portSource", OPTION_SHORT_PORT,
        "portSource", false, false, true, INFO_PORT_PLACEHOLDER.get(),
        defaultAdminPort, null,
        true, 1,
        true, 65336,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_SOURCE.get());

    hostNameDestinationArg = new StringArgument("hostDestination", 'O',
        "hostDestination", false, false, true, INFO_HOST_PLACEHOLDER.get(),
        getDefaultHostValue(), null,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_DESTINATION.get());

    portDestinationArg = new IntegerArgument("portDestination", null,
        "portDestination", false, false, true, INFO_PORT_PLACEHOLDER.get(),
        defaultAdminPort,
        null,
        true, 1,
        true, 65336,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_DESTINATION.get());

    initializeReplicationSubCmd = new SubCommand(this,
        INITIALIZE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_INITIALIZE_REPLICATION.get(
            INITIALIZE_ALL_REPLICATION_SUBCMD_NAME));

    Argument[] argsToAdd = {
        hostNameSourceArg, portSourceArg, hostNameDestinationArg,
        portDestinationArg
    };
    for (Argument arg : argsToAdd)
    {
      arg.setPropertyName(arg.getLongIdentifier());
      initializeReplicationSubCmd.addArgument(arg);
    }
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
    secureArgsList.hostNameArg.setDefaultValue(getDefaultHostValue());
    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg };
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
    secureArgsList.hostNameArg.setDefaultValue(getDefaultHostValue());
    BooleanArgument externalInitializationLocalOnlyArg = new BooleanArgument(
        "local-only",
        'l',
        "local-only",
        LocalizableMessage.EMPTY);
    externalInitializationLocalOnlyArg.setHidden(true);

    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg,
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
    secureArgsList.hostNameArg.setDefaultValue(getDefaultHostValue());
    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg };
    for (Argument arg : argsToAdd)
    {
      postExternalInitializationSubCmd.addArgument(arg);
    }
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
    scriptFriendlyArg = new BooleanArgument(
        "script-friendly",
        's',
        "script-friendly",
        INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());
    scriptFriendlyArg.setPropertyName(scriptFriendlyArg.getLongIdentifier());
    secureArgsList.hostNameArg.setDefaultValue(getDefaultHostValue());
    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg, scriptFriendlyArg };
    for (Argument arg : argsToAdd)
    {
      statusReplicationSubCmd.addArgument(arg);
    }
  }

  /**
   * Creates the purge historical subcommand and all the specific options
   * for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg and
   * portArg have been created.
   */
  private void createPurgeHistoricalSubCommand()
  throws ArgumentException
  {
    maximumDurationArg = new IntegerArgument(
        "maximumDuration",
        null, // shortId
        "maximumDuration",
        true, // isRequired
        false, // isMultivalued
        true,  // needsValue
        INFO_MAXIMUM_DURATION_PLACEHOLDER.get(),
        PurgeConflictsHistoricalTask.DEFAULT_MAX_DURATION,
        null,
        true, 0,
        false, Integer.MAX_VALUE,
        INFO_DESCRIPTION_PURGE_HISTORICAL_MAXIMUM_DURATION.get());

    purgeHistoricalSubCmd = new SubCommand(
        this,
        PURGE_HISTORICAL_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_PURGE_HISTORICAL.get());

    Argument[] argsToAdd = {
        secureArgsList.hostNameArg,
        secureArgsList.portArg,
        maximumDurationArg};

    for (Argument arg : argsToAdd)
    {
      arg.setPropertyName(arg.getLongIdentifier());
      purgeHistoricalSubCmd.addArgument(arg);
    }
    for (Argument arg : taskArgs.getArguments())
    {
      purgeHistoricalSubCmd.addArgument(arg);
    }
  }

  /**
   * Tells whether the user specified to have an interactive operation or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have an interactive
   * operation and <CODE>false</CODE> otherwise.
   */
  public boolean isInteractive()
  {
    return !noPromptArg.isPresent();
  }

  /**
   * Tells whether the user specified to have a quite operation or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have a quite operation
   * and <CODE>false</CODE> otherwise.
   */
  public boolean isQuiet()
  {
    return quietArg.isPresent();
  }

  /**
   * Tells whether the user specified to have a script-friendly output or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have a script-friendly
   * output and <CODE>false</CODE> otherwise.
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
    return getBindPassword(secureArgsList.bindPasswordArg,
        secureArgsList.bindPasswordFileArg);
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
    return secureArgsList.adminUidArg;
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
   * @return <CODE>true</CODE> the user asked to skip the replication port
   * checks (if the ports are free) and <CODE>false</CODE> otherwise.
   */
  boolean skipReplicationPortCheck()
  {
    return skipPortCheckArg.isPresent();
  }

  /**
   * Returns whether the user asked to not replicate the schema between servers.
   * @return <CODE>true</CODE> if the user asked to not replicate schema and
   * <CODE>false</CODE> otherwise.
   */
  boolean noSchemaReplication()
  {
    return noSchemaReplicationArg.isPresent();
  }

  /**
   * Returns whether the user asked to use the second server to initialize the
   * schema of the first server.
   * @return <CODE>true</CODE> if the user asked to use the second server to
   * initialize the schema of the first server and <CODE>false</CODE> otherwise.
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
    return getValue(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name default value in the disable replication
   * subcommand.
   * @return the host name default value in the disable replication
   * subcommand.
   */
  public String getHostNameToDisableOrDefault()
  {
    return getValueOrDefault(secureArgsList.hostNameArg);
  }

  /**
   * Returns the server bind dn explicitly provided in the disable replication
   * subcommand.
   * @return the server bind dn explicitly provided in the disable replication
   * subcommand.
   */
  public String getBindDNToDisable()
  {
    return getValue(secureArgsList.bindDnArg);
  }

  /**
   * Returns the server bind dn default value in the disable replication
   * subcommand.
   * @return the server bind dn default value in the enable replication
   * subcommand.
   */
  public String getDefaultBindDnToDisable()
  {
    return getDefaultValue(secureArgsList.bindDnArg);
  }

  /**
   * Returns the host name explicitly provided in the status replication
   * subcommand.
   * @return the host name explicitly provided in the status replication
   * subcommand.
   */
  public String getHostNameToStatus()
  {
    return getValue(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name default value in the status replication subcommand.
   * @return the host name default value in the status replication subcommand.
   */
  public String getHostNameToStatusOrDefault()
  {
    return getValueOrDefault(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name explicitly provided in the initialize all replication
   * subcommand.
   * @return the host name explicitly provided in the initialize all replication
   * subcommand.
   */
  public String getHostNameToInitializeAll()
  {
    return getValue(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name default value in the initialize all replication
   * subcommand.
   * @return the host name default value in the initialize all replication
   * subcommand.
   */
  public String getHostNameToInitializeAllOrDefault()
  {
    return getValueOrDefault(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name explicitly provided in the pre external
   * initialization subcommand.
   * @return the host name explicitly provided in the pre external
   * initialization subcommand.
   */
  public String getHostNameToPreExternalInitialization()
  {
    return getValue(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name default value in the pre external initialization
   * subcommand.
   * @return the host name default value in the pre external initialization
   * subcommand.
   */
  public String getDefaultHostNameToPreExternalInitialization()
  {
    return getDefaultValue(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name explicitly provided in the post external
   * initialization subcommand.
   * @return the host name explicitly provided in the post external
   * initialization subcommand.
   */
  public String getHostNameToPostExternalInitialization()
  {
    return getValue(secureArgsList.hostNameArg);
  }

  /**
   * Returns the host name default value in the post external initialization
   * subcommand.
   * @return the host name default value in the post external initialization
   * subcommand.
   */
  public String getDefaultHostNameToPostExternalInitialization()
  {
    return getDefaultValue(secureArgsList.hostNameArg);
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
    return getValue(secureArgsList.portArg);
  }

  /**
   * Returns the server port default value in the disable replication
   * subcommand.
   * @return the server port default value in the disable replication
   * subcommand.
   */
  public int getPortToDisableOrDefault()
  {
    return getValueOrDefault(secureArgsList.portArg);
  }

  /**
   * Returns the server port explicitly provided in the initialize all
   * replication subcommand.
   * @return the server port explicitly provided in the initialize all
   * replication subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPortToInitializeAll()
  {
    return getValue(secureArgsList.portArg);
  }

  /**
   * Returns the server port default value in the initialize all replication
   * subcommand.
   * @return the server port default value in the initialize all replication
   * subcommand.
   */
  public int getPortToInitializeAllOrDefault()
  {
    return getValueOrDefault(secureArgsList.portArg);
  }

  /**
   * Returns the server port explicitly provided in the pre external
   * initialization subcommand.
   * @return the server port explicitly provided in the pre external
   * initialization subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPortToPreExternalInitialization()
  {
    return getValue(secureArgsList.portArg);
  }

  /**
   * Returns the server port default value in the pre external initialization
   * subcommand.
   * @return the server port default value in the pre external initialization
   * subcommand.
   */
  public int getDefaultPortToPreExternalInitialization()
  {
    return getDefaultValue(secureArgsList.portArg);
  }

  /**
   * Returns the server port explicitly provided in the post external
   * initialization subcommand.
   * @return the server port explicitly provided in the post external
   * initialization subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPortToPostExternalInitialization()
  {
    return getValue(secureArgsList.portArg);
  }

  /**
   * Returns the server port default value in the post external initialization
   * subcommand.
   * @return the server port default value in the post external initialization
   * subcommand.
   */
  public int getDefaultPortToPostExternalInitialization()
  {
    return getDefaultValue(secureArgsList.portArg);
  }

  /**
   * Returns the server port explicitly provided in the status replication
   * subcommand.
   * @return the server port explicitly provided in the status replication
   * subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPortToStatus()
  {
    return getValue(secureArgsList.portArg);
  }

  /**
   * Returns the server port default value in the status replication subcommand.
   * @return the server port default value in the status replication subcommand.
   */
  public int getPortToStatusOrDefault()
  {
    return getValueOrDefault(secureArgsList.portArg);
  }

  /**
   * Returns the list of base DNs provided by the user.
   * @return the list of base DNs provided by the user.
   */
  public LinkedList<String> getBaseDNs()
  {
    return baseDNsArg.getValues();
  }

  /**
   * Returns the config class value provided in the hidden argument of the
   * command-line.
   * @return the config class value provided in the hidden argument of the
   * command-line.
   */
  public String getConfigClass()
  {
    return getValue(configClassArg);
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
      validateInitializeReplicationOptions(buf);
    }
    else if (isInitializeAllReplicationSubcommand())
    {
      validateInitializeAllReplicationOptions(buf);
    }
    else if (isPreExternalInitializationSubcommand())
    {
      validatePreExternalInitializationOptions(buf);
    }
    else if (isPostExternalInitializationSubcommand())
    {
      validatePostExternalInitializationOptions(buf);
    }
    else if (isPurgeHistoricalSubcommand())
    {
      validatePurgeHistoricalOptions(buf);
    }
    else
    {
      // This can occur if the user did not provide any subcommand.  We assume
      // that the error informing of this will be generated in
      // validateGlobalOptions.
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
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * enable replication and <CODE>false</CODE> otherwise.
   */
  public boolean isEnableReplicationSubcommand()
  {
    return isSubcommand(ENABLE_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the disable replication
   * or not.
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * disable replication and <CODE>false</CODE> otherwise.
   */
  public boolean isDisableReplicationSubcommand()
  {
    return isSubcommand(DISABLE_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the status replication
   * or not.
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * status replication and <CODE>false</CODE> otherwise.
   */
  public boolean isStatusReplicationSubcommand()
  {
    return isSubcommand(STATUS_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the purge historical
   * or not.
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * purge historical and <CODE>false</CODE> otherwise.
   */
  public boolean isPurgeHistoricalSubcommand()
  {
    return isSubcommand(PURGE_HISTORICAL_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the initialize all
   * replication or not.
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * initialize all replication and <CODE>false</CODE> otherwise.
   */
  public boolean isInitializeAllReplicationSubcommand()
  {
    return isSubcommand(INITIALIZE_ALL_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the pre external
   * initialization or not.
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * pre external initialization and <CODE>false</CODE> otherwise.
   */
  public boolean isPreExternalInitializationSubcommand()
  {
    return isSubcommand(PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the post external
   * initialization or not.
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * post external initialization and <CODE>false</CODE> otherwise.
   */
  public boolean isPostExternalInitializationSubcommand()
  {
    return isSubcommand(POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the user provided subcommand is the initialize replication
   * or not.
   * @return <CODE>true</CODE> if the user provided subcommand is the
   * initialize replication and <CODE>false</CODE> otherwise.
   */
  public boolean isInitializeReplicationSubcommand()
  {
    return isSubcommand(INITIALIZE_REPLICATION_SUBCMD_NAME);
  }

  /**
   * Returns whether the command-line subcommand has the name provided
   * or not.
   * @param name the name of the subcommand.
   * @return <CODE>true</CODE> if command-line subcommand has the name provided
   * and <CODE>false</CODE> otherwise.
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
    Argument[][] conflictingPairs =
    {
        { server1.bindPasswordArg, server1.bindPasswordFileArg },
        { server2.bindPasswordArg, server2.bindPasswordFileArg },
        { server1.replicationPortArg, server1.noReplicationServerArg },
        { server1.noReplicationServerArg, server1.onlyReplicationServerArg },
        { server2.replicationPortArg, server2.noReplicationServerArg },
        { server2.noReplicationServerArg, server2.onlyReplicationServerArg },
        {noSchemaReplicationArg, useSecondServerAsSchemaSourceArg}
    };

    for (Argument[] conflictingPair : conflictingPairs)
    {
      Argument arg1 = conflictingPair[0];
      Argument arg2 = conflictingPair[1];
      if (arg1.isPresent() && arg2.isPresent())
      {
        LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
            arg1.getLongIdentifier(), arg2.getLongIdentifier());
        addMessage(buf, message);
      }
    }

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
    Argument[][] conflictingPairs =
    {
        {getAdminUidArg(), secureArgsList.bindDnArg},
        {disableAllArg, disableReplicationServerArg},
        {disableAllArg, baseDNsArg}
    };

    for (Argument[] conflictingPair : conflictingPairs)
    {
      Argument arg1 = conflictingPair[0];
      Argument arg2 = conflictingPair[1];
      if (arg1.isPresent() && arg2.isPresent())
      {
        LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
            arg1.getLongIdentifier(), arg2.getLongIdentifier());
        addMessage(buf, message);
      }
    }
  }

  /**
   * Checks the initialize all replication subcommand options and updates the
   * provided LocalizableMessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateInitializeAllReplicationOptions(LocalizableMessageBuilder buf)
  {
  }

  /**
   * Checks the pre external initialization subcommand options and updates the
   * provided LocalizableMessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validatePreExternalInitializationOptions(LocalizableMessageBuilder buf)
  {
    validateInitializeAllReplicationOptions(buf);
  }

  /**
   * Checks the post external initialization subcommand options and updates the
   * provided LocalizableMessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the LocalizableMessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validatePostExternalInitializationOptions(LocalizableMessageBuilder buf)
  {
    validateInitializeAllReplicationOptions(buf);
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
  private void validateInitializeReplicationOptions(LocalizableMessageBuilder buf)
  {
    if (hostNameSourceArg.getValue().equalsIgnoreCase(hostNameDestinationArg.getValue())
        && !isInteractive()
        && portSourceArg.getValue().equals(portDestinationArg.getValue()))
    {
      LocalizableMessage message = ERR_REPLICATION_INITIALIZE_SAME_SERVER_PORT.get(
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
   * Returns the default value to be used for the host.
   * @return the default value to be used for the host.
   */
  private String getDefaultHostValue()
  {
    if (defaultLocalHostValue == null)
    {
      try
      {
        defaultLocalHostValue =
          java.net.InetAddress.getLocalHost().getHostName();
      }
      catch (Throwable t)
      {
      }
      if (defaultLocalHostValue == null)
      {
        defaultLocalHostValue = "localhost";
      }
    }
    return defaultLocalHostValue;
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
      secureArgsList.bindPasswordArg.isPresent() ||
      secureArgsList.bindPasswordFileArg.isPresent();
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
}
