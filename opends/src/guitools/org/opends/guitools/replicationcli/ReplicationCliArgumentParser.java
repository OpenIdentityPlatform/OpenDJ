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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.replicationcli;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;

/**
 * This class is used to parse the arguments passed to the replication CLI.
 * It also checks the compatibility between the values and that all the
 * required information has been provided.  However it does not do any
 * verification that require connection to any server.
 */
public class ReplicationCliArgumentParser extends SecureConnectionCliParser
{
  private SubCommand enableReplicationSubCmd;
  private SubCommand disableReplicationSubCmd;
  private SubCommand initializeReplicationSubCmd;
  private SubCommand initializeAllReplicationSubCmd;
  private SubCommand postExternalInitializationSubCmd;
  private SubCommand preExternalInitializationSubCmd;
  private SubCommand statusReplicationSubCmd;

  private BooleanArgument noPromptArg;

  private String defaultLocalHostValue;

  /**
   * The 'hostName' argument for the first server.
   */
  private StringArgument hostName1Arg = null;

  /**
   * The 'port' argument for the first server.
   */
  private IntegerArgument port1Arg = null;

  /**
   * The 'binDN' argument for the first server.
   */
  private StringArgument bindDn1Arg = null;

  /**
   * The 'bindPasswordFile' argument for the first server.
   */
  private FileBasedArgument bindPasswordFile1Arg = null;

  /**
   * The 'bindPassword' argument for the first server.
   */
  private StringArgument bindPassword1Arg = null;

  /**
   * The 'useSSLArg' argument for the first server.
   */
  private BooleanArgument useSSL1Arg = null;

  /**
   * The 'useStartTLS1Arg' argument for the first server.
   */
  private BooleanArgument useStartTLS1Arg = null;

  /**
   * The 'replicationPort' argument for the first server.
   */
  private IntegerArgument replicationPort1Arg = null;

  /**
   * The 'secureReplication' argument for the first server.
   */
  private BooleanArgument secureReplication1Arg = null;

  /**
   * The 'hostName' argument for the second server.
   */
  private StringArgument hostName2Arg = null;

  /**
   * The 'port' argument for the second server.
   */
  private IntegerArgument port2Arg = null;

  /**
   * The 'binDN' argument for the second server.
   */
  private StringArgument bindDn2Arg = null;

  /**
   * The 'bindPasswordFile' argument for the second server.
   */
  private FileBasedArgument bindPasswordFile2Arg = null;

  /**
   * The 'bindPassword' argument for the second server.
   */
  private StringArgument bindPassword2Arg = null;

  /**
   * The 'useSSLArg' argument for the second server.
   */
  private BooleanArgument useSSL2Arg = null;

  /**
   * The 'useStartTLS2Arg' argument for the second server.
   */
  private BooleanArgument useStartTLS2Arg = null;

  /**
   * The 'replicationPort' argument for the second server.
   */
  private IntegerArgument replicationPort2Arg = null;

  /**
   * The 'secureReplication' argument for the second server.
   */
  private BooleanArgument secureReplication2Arg = null;

  /**
   * The 'skipPortCheckArg' argument to not check replication ports.
   */
  private BooleanArgument skipPortCheckArg;

  /**
   * The 'noSchemaReplication' argument to not replicate schema.
   */
  private BooleanArgument noSchemaReplicationArg;

  /**
   * The 'useSecondServerAsSchemaSource' argument to not replicate schema.
   */
  private BooleanArgument useSecondServerAsSchemaSourceArg;

  /**
   * The 'hostName' argument for the source server.
   */
  private StringArgument hostNameSourceArg = null;

  /**
   * The 'port' argument for the source server.
   */
  private IntegerArgument portSourceArg = null;

  /**
   * The 'useSSLArg' for the source server.
   */
  private BooleanArgument useSSLSourceArg = null;

  /**
   * The 'useStartTLSSourceArg' for the source server.
   */
  private BooleanArgument useStartTLSSourceArg = null;

  /**
   * The 'hostName' argument for the destination server.
   */
  private StringArgument hostNameDestinationArg = null;

  /**
   * The 'port' argument for the destination server.
   */
  private IntegerArgument portDestinationArg = null;

  /**
   * The 'useSSLArg' argument for the destination server.
   */
  private BooleanArgument useSSLDestinationArg = null;

  /**
   * The 'useStartTLSDestinationArg' argument for the destination server.
   */
  private BooleanArgument useStartTLSDestinationArg = null;

  /**
   * The 'suffixes' global argument.
   */
  private StringArgument baseDNsArg = null;

  /**
   * The argument that specifies if the external initialization will be
   * performed only on this server.
   */
  private BooleanArgument externalInitializationLocalOnlyArg;

  /**
   * The 'quiet' argument.
   */
  private BooleanArgument quietArg;

  /**
   * The 'scriptFriendly' argument.
   */
  private BooleanArgument scriptFriendlyArg;

  /**
   * The text of the enable replication subcommand.
   */
  public static final String ENABLE_REPLICATION_SUBCMD_NAME = "enable";

  /**
   * The text of the disable replication subcommand.
   */
  public static final String DISABLE_REPLICATION_SUBCMD_NAME = "disable";

  /**
   * The text of the initialize replication subcommand.
   */
  public static final String INITIALIZE_REPLICATION_SUBCMD_NAME = "initialize";

  /**
   * The text of the initialize all replication subcommand.
   */
  public static final String INITIALIZE_ALL_REPLICATION_SUBCMD_NAME =
    "initialize-all";

  /**
   * The text of the pre external initialization subcommand.
   */
  public static final String PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME =
    "pre-external-initialization";

  /**
   * The text of the initialize all replication subcommand.
   */
  public static final String POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME =
    "post-external-initialization";

  /**
   * The text of the status replication subcommand.
   */
  public static final String STATUS_REPLICATION_SUBCMD_NAME = "status";

  /**
   * Creates a new instance of this argument parser with no arguments.
   *
   * @param mainClassName
   *          The fully-qualified name of the Java class that should
   *          be invoked to launch the program with which this
   *          argument parser is associated.
   */
  public ReplicationCliArgumentParser(String mainClassName)
  {
    super(mainClassName,
        INFO_REPLICATION_TOOL_DESCRIPTION.get(ENABLE_REPLICATION_SUBCMD_NAME,
            INITIALIZE_REPLICATION_SUBCMD_NAME),
            false);
  }

  /**
   * Initialize the parser with the Global options and subcommands.
   *
   * @param outStream
   *          The output stream to use for standard output, or <CODE>null</CODE>
   *          if standard output is not needed.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   */
  public void initializeParser(OutputStream outStream)
      throws ArgumentException
  {
    initializeGlobalArguments(outStream);
    createEnableReplicationSubCommand();
    createDisableReplicationSubCommand();
    createInitializeReplicationSubCommand();
    createInitializeAllReplicationSubCommand();
    createPreExternalInitializationSubCommand();
    createPostExternalInitializationSubCommand();
    createStatusReplicationSubCommand();
  }

  /**
   * Checks all the options parameters and updates the provided MessageBuilder
   * with the errors that where encountered.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  public void validateOptions(MessageBuilder buf)
  {
    validateGlobalOptions(buf);
    validateSubcommandOptions(buf);
  }

  /**
   * {@inheritDoc}
   */
  public int validateGlobalOptions(MessageBuilder buf)
  {
    int returnValue;
    super.validateGlobalOptions(buf);
    ArrayList<Message> errors = new ArrayList<Message>();
    if (secureArgsList.bindPasswordArg.isPresent() &&
        secureArgsList.bindPasswordFileArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          secureArgsList.bindPasswordArg.getLongIdentifier(),
          secureArgsList.bindPasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    if (!isInteractive())
    {
      // Check that we have the required data
      if (!baseDNsArg.isPresent() && !isStatusReplicationSubcommand())
      {
        errors.add(ERR_REPLICATION_NO_BASE_DN_PROVIDED.get());
      }
      if (getBindPasswordAdmin() == null)
      {
        errors.add(ERR_REPLICATION_NO_ADMINISTRATOR_PASSWORD_PROVIDED.get(
            secureArgsList.bindPasswordArg.getLongIdentifier(),
                secureArgsList.bindPasswordFileArg.getLongIdentifier()));
      }
    }

    if (baseDNsArg.isPresent())
    {
      LinkedList<String> baseDNs = baseDNsArg.getValues();
      for (String dn : baseDNs)
      {
        if (!Utils.isDn(dn))
        {
          errors.add(ERR_REPLICATION_NOT_A_VALID_BASEDN.get(dn));
        }
      }
    }
    if (errors.size() > 0)
    {
      for (Message error : errors)
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
      new ArrayList<Argument>(createGlobalArguments(outStream));

    Argument[] argsToRemove = {
      secureArgsList.hostNameArg,
      secureArgsList.portArg,
      secureArgsList.bindDnArg,
      secureArgsList.bindPasswordFileArg,
      secureArgsList.bindPasswordArg,
      secureArgsList.useSSLArg,
      secureArgsList.useStartTLSArg
    };

    for (int i=0; i<argsToRemove.length; i++)
    {
      defaultArgs.remove(argsToRemove[i]);
    }
    defaultArgs.remove(noPropertiesFileArg);
    defaultArgs.remove(propertiesFileArg);
    // Remove it from the default location and redefine it.
    defaultArgs.remove(secureArgsList.adminUidArg);

    int index = 0;

    baseDNsArg = new StringArgument("baseDNs", OPTION_SHORT_BASEDN,
        OPTION_LONG_BASEDN, false, true, true, INFO_BASEDN_PLACEHOLDER.get(),
        null,
        null, INFO_DESCRIPTION_REPLICATION_BASEDNS.get());
    baseDNsArg.setPropertyName(OPTION_LONG_BASEDN);
    defaultArgs.add(index++, baseDNsArg);

    secureArgsList.adminUidArg = new StringArgument("adminUID", 'I',
        "adminUID", false, false, true, INFO_ADMINUID_PLACEHOLDER.get(),
        Constants.GLOBAL_ADMIN_UID, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_UID.get(
            ENABLE_REPLICATION_SUBCMD_NAME));
    secureArgsList.adminUidArg.setPropertyName("adminUID");
    secureArgsList.adminUidArg.setHidden(false);
    defaultArgs.add(index++, secureArgsList.adminUidArg);

    secureArgsList.bindPasswordArg = new StringArgument("adminPassword",
        OPTION_SHORT_BINDPWD, "adminPassword", false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
    defaultArgs.add(index++, secureArgsList.bindPasswordArg);

    secureArgsList.bindPasswordFileArg = new FileBasedArgument(
        "adminPasswordFile",
        OPTION_SHORT_BINDPWD_FILE, "adminPasswordFile", false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
    defaultArgs.add(index++, secureArgsList.bindPasswordFileArg);

    defaultArgs.remove(verboseArg);
    noPromptArg = new BooleanArgument(
        OPTION_LONG_NO_PROMPT,
        OPTION_SHORT_NO_PROMPT,
        OPTION_LONG_NO_PROMPT,
        INFO_DESCRIPTION_NO_PROMPT.get());
    defaultArgs.add(index++, noPromptArg);

    for (int i=0; i<index; i++)
    {
      Argument arg = defaultArgs.get(i);
      arg.setPropertyName(arg.getLongIdentifier());
    }

    quietArg = new BooleanArgument(
        OPTION_LONG_QUIET,
        OPTION_SHORT_QUIET,
        OPTION_LONG_QUIET,
        INFO_REPLICATION_DESCRIPTION_QUIET.get());
    quietArg.setPropertyName(OPTION_LONG_QUIET);
    defaultArgs.add(quietArg);

    StringArgument propertiesFileArgument = new StringArgument(
        "propertiesFilePath", null, OPTION_LONG_PROP_FILE_PATH, false, false,
        true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_PROP_FILE_PATH.get());
    defaultArgs.add(propertiesFileArgument);
    setFilePropertiesArgument(propertiesFileArgument);

    BooleanArgument noPropertiesFileArgument = new BooleanArgument(
        "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
        INFO_DESCRIPTION_NO_PROP_FILE.get());
    defaultArgs.add(noPropertiesFileArgument);
    setNoPropertiesFileArgument(noPropertiesFileArgument);

    initializeGlobalArguments(defaultArgs);
  }

  /**
   * Creates the enable replication subcommand and all the specific options
   * for the subcommand.
   */
  private void createEnableReplicationSubCommand()
  throws ArgumentException
  {

    hostName1Arg = new StringArgument("host1", OPTION_SHORT_HOST,
        "host1", false, false, true, INFO_HOST_PLACEHOLDER.get(),
        getDefaultHostValue(),
        null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST1.get());

    port1Arg = new IntegerArgument("port1", OPTION_SHORT_PORT, "port1",
        false, false, true, INFO_PORT_PLACEHOLDER.get(), 389, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT1.get());

    bindDn1Arg = new StringArgument("bindDN1", OPTION_SHORT_BINDDN,
        "bindDN1", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
        "cn=Directory Manager", null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN1.get());

    bindPassword1Arg = new StringArgument("bindPassword1",
        null, "bindPassword1", false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD1.get());

    bindPasswordFile1Arg = new FileBasedArgument("bindPasswordFile1",
        null, "bindPasswordFile1", false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE1.get());

    useSSL1Arg = new BooleanArgument("useSSL1", OPTION_SHORT_USE_SSL,
        "useSSL1", INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SSL1.get());

    useStartTLS1Arg = new BooleanArgument("startTLS1", OPTION_SHORT_START_TLS,
        "startTLS1",
        INFO_DESCRIPTION_ENABLE_REPLICATION_STARTTLS1.get());

    replicationPort1Arg = new IntegerArgument("replicationPort1", 'r',
        "replicationPort1", false, false, true, INFO_PORT_PLACEHOLDER.get(),
        8989, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_PORT1.get());

    secureReplication1Arg = new BooleanArgument("secureReplication1", null,
        "secureReplication1",
        INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION1.get());

    hostName2Arg = new StringArgument("host2", 'O',
        "host2", false, false, true, INFO_HOST_PLACEHOLDER.get(),
        getDefaultHostValue(),
        null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST2.get());

    port2Arg = new IntegerArgument("port2", null, "port2",
        false, false, true, INFO_PORT_PLACEHOLDER.get(), 389, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT2.get());

    bindDn2Arg = new StringArgument("bindDN2", null,
        "bindDN2", false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
        "cn=Directory Manager", null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN2.get());

    bindPassword2Arg = new StringArgument("bindPassword2",
        null, "bindPassword2", false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD2.get());

    bindPasswordFile2Arg = new FileBasedArgument("bindPasswordFile2",
        'F', "bindPasswordFile2", false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE2.get());

    useSSL2Arg = new BooleanArgument("useSSL2", 'z',
        "useSSL2", INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SSL2.get());

    useStartTLS2Arg = new BooleanArgument("startTLS2", null,
        "startTLS2",
        INFO_DESCRIPTION_ENABLE_REPLICATION_STARTTLS2.get());

    replicationPort2Arg = new IntegerArgument("replicationPort2", 'R',
        "replicationPort2", false, false, true, INFO_PORT_PLACEHOLDER.get(),
        8989, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_PORT2.get());

    secureReplication2Arg = new BooleanArgument("secureReplication2", null,
        "secureReplication2",
        INFO_DESCRIPTION_ENABLE_SECURE_REPLICATION2.get());

    skipPortCheckArg = new BooleanArgument(
        "skipportcheck", 'S', "skipPortCheck",
        INFO_DESCRIPTION_ENABLE_REPLICATION_SKIPPORT.get());

    noSchemaReplicationArg = new BooleanArgument(
        "noschemareplication", null, "noSchemaReplication",
        INFO_DESCRIPTION_ENABLE_REPLICATION_NO_SCHEMA_REPLICATION.get());

    useSecondServerAsSchemaSourceArg = new BooleanArgument(
        "usesecondserverasschemasource", null, "useSecondServerAsSchemaSource",
        INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SECOND_AS_SCHEMA_SOURCE.get(
            noSchemaReplicationArg.getLongIdentifier()));

    enableReplicationSubCmd = new SubCommand(this,
        ENABLE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_ENABLE_REPLICATION.get());

    Argument[] argsToAdd = {
        hostName1Arg, port1Arg, bindDn1Arg, bindPassword1Arg,
        bindPasswordFile1Arg, useStartTLS1Arg, useSSL1Arg, replicationPort1Arg,
        secureReplication1Arg,
        hostName2Arg, port2Arg, bindDn2Arg, bindPassword2Arg,
        bindPasswordFile2Arg, useStartTLS2Arg, useSSL2Arg, replicationPort2Arg,
        secureReplication2Arg,
        skipPortCheckArg, noSchemaReplicationArg,
        useSecondServerAsSchemaSourceArg
    };
    for (int i=0; i<argsToAdd.length; i++)
    {
      argsToAdd[i].setPropertyName(argsToAdd[i].getLongIdentifier());
      enableReplicationSubCmd.addArgument(argsToAdd[i]);
    }
  }

  /**
   * Creates the disable replication subcommand and all the specific options
   * for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg,
   * portArg, startTLSArg and useSSLArg have been created.
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
    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg, secureArgsList.useSSLArg,
        secureArgsList.useStartTLSArg, secureArgsList.bindDnArg };
    for (int i=0; i<argsToAdd.length; i++)
    {
      disableReplicationSubCmd.addArgument(argsToAdd[i]);
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
        "portSource", false, false, true, INFO_PORT_PLACEHOLDER.get(), 389,
        null,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_SOURCE.get());

    useSSLSourceArg = new BooleanArgument("useSSLSource", OPTION_SHORT_USE_SSL,
        "useSSLSource",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_USE_SSL_SOURCE.get());

    useStartTLSSourceArg = new BooleanArgument("startTLSSource",
        OPTION_SHORT_START_TLS, "startTLSSource",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_STARTTLS_SOURCE.get());

    hostNameDestinationArg = new StringArgument("hostDestination", 'O',
        "hostDestination", false, false, true, INFO_HOST_PLACEHOLDER.get(),
        getDefaultHostValue(), null,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_DESTINATION.get());

    portDestinationArg = new IntegerArgument("portDestination", null,
        "portDestination", false, false, true, INFO_PORT_PLACEHOLDER.get(), 389,
        null,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_DESTINATION.get());

    useSSLDestinationArg = new BooleanArgument("useSSLDestination", 'z',
        "useSSLDestination",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_USE_SSL_DESTINATION.get());

    useStartTLSDestinationArg = new BooleanArgument("startTLSDestination", null,
        "startTLSDestination",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_STARTTLS_DESTINATION.get());

    initializeReplicationSubCmd = new SubCommand(this,
        INITIALIZE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_INITIALIZE_REPLICATION.get(
            INITIALIZE_ALL_REPLICATION_SUBCMD_NAME));

    Argument[] argsToAdd = {
        hostNameSourceArg, portSourceArg, useSSLSourceArg, useStartTLSSourceArg,
        hostNameDestinationArg, portDestinationArg, useSSLDestinationArg,
        useStartTLSDestinationArg
    };
    for (int i=0; i<argsToAdd.length; i++)
    {
      argsToAdd[i].setPropertyName(argsToAdd[i].getLongIdentifier());
      initializeReplicationSubCmd.addArgument(argsToAdd[i]);
    }
  }

  /**
   * Creates the initialize all replication subcommand and all the specific
   * options for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg,
   * portArg, startTLSArg and useSSLArg have been created.
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
        secureArgsList.portArg, secureArgsList.useSSLArg,
        secureArgsList.useStartTLSArg };
    for (int i=0; i<argsToAdd.length; i++)
    {
      initializeAllReplicationSubCmd.addArgument(argsToAdd[i]);
    }
  }

  /**
   * Creates the subcommand that the user must launch before doing an external
   * initialization of the topology ( and all the specific
   * options for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg,
   * portArg, startTLSArg and useSSLArg have been created.
   */
  private void createPreExternalInitializationSubCommand()
  throws ArgumentException
  {
    preExternalInitializationSubCmd = new SubCommand(this,
        PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_PRE_EXTERNAL_INITIALIZATION.get(
            POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
    secureArgsList.hostNameArg.setDefaultValue(getDefaultHostValue());
    externalInitializationLocalOnlyArg = new BooleanArgument(
        "local-only",
        'l',
        "local-only",
        INFO_DESCRIPTION_EXTERNAL_INITIALIZATION_LOCAL.get());
    externalInitializationLocalOnlyArg.setPropertyName(
        externalInitializationLocalOnlyArg.getLongIdentifier());
    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg, secureArgsList.useSSLArg,
        secureArgsList.useStartTLSArg,
        externalInitializationLocalOnlyArg};

    for (int i=0; i<argsToAdd.length; i++)
    {
      preExternalInitializationSubCmd.addArgument(argsToAdd[i]);
    }
  }

  /**
   * Creates the subcommand that the user must launch after doing an external
   * initialization of the topology ( and all the specific
   * options for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg,
   * portArg, startTLSArg and useSSLArg have been created.
   */
  private void createPostExternalInitializationSubCommand()
  throws ArgumentException
  {
    postExternalInitializationSubCmd = new SubCommand(this,
        POST_EXTERNAL_INITIALIZATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_POST_EXTERNAL_INITIALIZATION.get(
            PRE_EXTERNAL_INITIALIZATION_SUBCMD_NAME));
    secureArgsList.hostNameArg.setDefaultValue(getDefaultHostValue());
    externalInitializationLocalOnlyArg.setPropertyName(
        externalInitializationLocalOnlyArg.getLongIdentifier());
    Argument[] argsToAdd = { secureArgsList.hostNameArg,
        secureArgsList.portArg, secureArgsList.useSSLArg,
        secureArgsList.useStartTLSArg};
    for (int i=0; i<argsToAdd.length; i++)
    {
      postExternalInitializationSubCmd.addArgument(argsToAdd[i]);
    }
  }

  /**
   * Creates the status replication subcommand and all the specific options
   * for the subcommand.  Note: this method assumes that
   * initializeGlobalArguments has already been called and that hostNameArg,
   * portArg, startTLSArg and useSSLArg have been created.
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
        secureArgsList.portArg, secureArgsList.useSSLArg,
        secureArgsList.useStartTLSArg, scriptFriendlyArg };
    for (int i=0; i<argsToAdd.length; i++)
    {
      statusReplicationSubCmd.addArgument(argsToAdd[i]);
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
   * Get the password which has to be used for the command to connect to the
   * first server without prompting the user in the enable replication
   * subcommand.  If no password was specified return null.
   *
   * @return the password which has to be used for the command to connect to the
   * first server without prompting the user in the enable replication
   * subcommand.  If no password was specified return null.
   */
  public String getBindPassword1()
  {
    return getBindPassword(bindPassword1Arg, bindPasswordFile1Arg);
  }

  /**
   * Get the password which has to be used for the command to connect to the
   * second server without prompting the user in the enable replication
   * subcommand.  If no password was specified return null.
   *
   * @return the password which has to be used for the command to connect to the
   * second server without prompting the user in the enable replication
   * subcommand.  If no password was specified return null.
   */
  public String getBindPassword2()
  {
    return getBindPassword(bindPassword2Arg, bindPasswordFile2Arg);
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
   * Get the password of the first server which has to be used in the
   * enable replication subcommand.
   *
   * @param dn
   *          The user DN for which to password could be asked.
   * @param out
   *          The input stream to used if we have to prompt to the
   *          user.
   * @param err
   *          The error stream to used if we have to prompt to the
   *          user.
   * @return the password of the first server which has to be used n the
   *          enable replication subcommand.
   */
  public String getBindPassword1(
      String dn, OutputStream out, OutputStream err)
  {
    return getBindPassword(dn, out, err, bindPassword1Arg,
        bindPasswordFile1Arg);
  }

  /**
   * Get the password of the second server which has to be used in the
   * enable replication subcommand.
   *
   * @param dn
   *          The user DN for which to password could be asked.
   * @param out
   *          The input stream to used if we have to prompt to the
   *          user.
   * @param err
   *          The error stream to used if we have to prompt to the
   *          user.
   * @return the password of the second server which has to be used in the
   *          enable replication subcommand.
   */
  public String getBindPassword2(
      String dn, OutputStream out, OutputStream err)
  {
    return getBindPassword(dn, out, err, bindPassword2Arg,
        bindPasswordFile2Arg);
  }

  /**
   * Get the password of the global administrator which has to be used for the
   * command.
   *
   * @param dn
   *          The user DN for which to password could be asked.
   * @param out
   *          The input stream to used if we have to prompt to the
   *          user.
   * @param err
   *          The error stream to used if we have to prompt to the
   *          user.
   * @return the password of the global administrator which has to be used for
   *          the command.
   */
  public String getBindPasswordAdmin(
      String dn, OutputStream out, OutputStream err)
  {
    return getBindPassword(dn, out, err, secureArgsList.bindPasswordArg,
        secureArgsList.bindPasswordFileArg);
  }

  /**
   * Indicate if the SSL mode is required for the first server in the enable
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the first server in
   * the enable replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSL1()
  {
    return useSSL1Arg.isPresent();
  }

  /**
   * Indicate if the startTLS mode is required for the first server in the
   * enable replication subcommand.
   *
   * @return <CODE>true</CODE> if startTLS mode is required for the first server
   * in the enable replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useStartTLS1()
  {
    return useStartTLS1Arg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the second server in the enable
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the second server in
   * the enable replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSL2()
  {
    return useSSL2Arg.isPresent();
  }

  /**
   * Indicate if the startTLS mode is required for the second server in the
   * enable replication subcommand.
   *
   * @return <CODE>true</CODE> if startTLS mode is required for the second
   * server in the enable replication subcommand and <CODE>false</CODE>
   * otherwise.
   */
  public boolean useStartTLS2()
  {
    return useStartTLS2Arg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the source server in the
   * initialize replication subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the source server
   * in the initialize replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSLSource()
  {
    return useSSLSourceArg.isPresent();
  }

  /**
   * Indicate if the StartTLS mode is required for the source server in the
   * initialize replication subcommand.
   *
   * @return <CODE>true</CODE> if StartTLS mode is required for the source
   * server in the initialize replication subcommand and <CODE>false</CODE>
   * otherwise.
   */
  public boolean useStartTLSSource()
  {
    return useStartTLSSourceArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the destinaton server in the
   * initialize replication subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the destination
   * server in the initialize replication subcommand and <CODE>false</CODE>
   * otherwise.
   */
  public boolean useSSLDestination()
  {
    return useSSLDestinationArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the destination server in the
   * initialize replication subcommand.
   *
   * @return <CODE>true</CODE> if StartTLS mode is required for the destination
   * server in the initialize replication subcommand and <CODE>false</CODE>
   * otherwise.
   */
  public boolean useStartTLSDestination()
  {
    return useStartTLSDestinationArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the disable
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the server in the
   * disable replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSLToDisable()
  {
    return secureArgsList.useSSLArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the disable
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if StartTLS mode is required for the server in
   * the disable replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useStartTLSToDisable()
  {
    return secureArgsList.useStartTLSArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the initialize all
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the server in the
   * initialize all replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSLToInitializeAll()
  {
    return secureArgsList.useSSLArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the initialize all
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if StartTLS mode is required for the server in
   * the initialize all replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useStartTLSToInitializeAll()
  {
    return secureArgsList.useStartTLSArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the pre external
   * initialization subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the server in the
   * pre external initialization subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSLToPreExternalInitialization()
  {
    return secureArgsList.useSSLArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the pre external
   * initialization subcommand.
   *
   * @return <CODE>true</CODE> if StartTLS mode is required for the server in
   * the pre external initialization subcommand and <CODE>false</CODE>
   * otherwise.
   */
  public boolean useStartTLSToPreExternalInitialization()
  {
    return secureArgsList.useStartTLSArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the post external
   * initialization subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the server in the
   * post external initialization subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSLToPostExternalInitialization()
  {
    return secureArgsList.useSSLArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the post external
   * initialization subcommand.
   *
   * @return <CODE>true</CODE> if StartTLS mode is required for the server in
   * the post external initialization subcommand and <CODE>false</CODE>
   * otherwise.
   */
  public boolean useStartTLSToPostExternalInitialization()
  {
    return secureArgsList.useStartTLSArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the status
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if SSL mode is required for the server in the
   * status replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useSSLToStatus()
  {
    return secureArgsList.useSSLArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is required for the server in the status
   * replication subcommand.
   *
   * @return <CODE>true</CODE> if StartTLS mode is required for the server in
   * the status replication subcommand and <CODE>false</CODE> otherwise.
   */
  public boolean useStartTLSToStatus()
  {
    return secureArgsList.useStartTLSArg.isPresent();
  }

  /**
   * Returns the Administrator UID explicitly provided in the command-line.
   * @return the Administrator UID explicitly provided in the command-line.
   */
  public String getAdministratorUID()
  {
    return getValue(secureArgsList.adminUidArg);
  }

  /**
   * Returns the default Administrator UID value.
   * @return the default Administrator UID value.
   */
  public String getDefaultAdministratorUID()
  {
    return getDefaultValue(secureArgsList.adminUidArg);
  }

  /**
   * Returns the first host name explicitly provided in the enable replication
   * subcommand.
   * @return the first host name explicitly provided in the enable replication
   * subcommand.
   */
  public String getHostName1()
  {
    return getValue(hostName1Arg);
  }

  /**
   * Returns the first host name default value in the enable replication
   * subcommand.
   * @return the first host name default value in the enable replication
   * subcommand.
   */
  public String getDefaultHostName1()
  {
    return getDefaultValue(hostName1Arg);
  }

  /**
   * Returns the first server port explicitly provided in the enable replication
   * subcommand.
   * @return the first server port explicitly provided in the enable replication
   * subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPort1()
  {
    return getValue(port1Arg);
  }

  /**
   * Returns the first server port default value in the enable replication
   * subcommand.
   * @return the first server port default value in the enable replication
   * subcommand.
   */
  public int getDefaultPort1()
  {
    return getDefaultValue(port1Arg);
  }

  /**
   * Returns the first server bind dn explicitly provided in the enable
   * replication subcommand.
   * @return the first server bind dn explicitly provided in the enable
   * replication subcommand.
   */
  public String getBindDn1()
  {
    return getValue(bindDn1Arg);
  }

  /**
   * Returns the first server bind dn default value in the enable replication
   * subcommand.
   * @return the first server bind dn default value in the enable replication
   * subcommand.
   */
  public String getDefaultBindDn1()
  {
    return getDefaultValue(bindDn1Arg);
  }

  /**
   * Returns the first server replication port explicitly provided in the enable
   * replication subcommand.
   * @return the first server replication port explicitly provided in the enable
   * replication subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getReplicationPort1()
  {
    return getValue(replicationPort1Arg);
  }

  /**
   * Returns the first server replication port default value in the enable
   * replication subcommand.
   * @return the first server replication port default value in the enable
   * replication subcommand.
   */
  public int getDefaultReplicationPort1()
  {
    return getDefaultValue(replicationPort1Arg);
  }

  /**
   * Returns whether the user asked to have replication communication with the
   * first server or not.
   * @return <CODE>true</CODE> the user asked to have replication communication
   * with the first server and <CODE>false</CODE> otherwise.
   */
  public boolean isSecureReplication1()
  {
    return secureReplication1Arg.isPresent();
  }

  /**
   * Returns the second host name explicitly provided in the enable replication
   * subcommand.
   * @return the second host name explicitly provided in the enable replication
   * subcommand.
   */
  public String getHostName2()
  {
    return getValue(hostName2Arg);
  }

  /**
   * Returns the second host name default value in the enable replication
   * subcommand.
   * @return the second host name default value in the enable replication
   * subcommand.
   */
  public String getDefaultHostName2()
  {
    return getDefaultValue(hostName2Arg);
  }

  /**
   * Returns the second server port explicitly provided in the enable
   * replication subcommand.
   * @return the second server port explicitly provided in the enable
   * replication subcommand.  Returns -1 if no port was explicitly provided.
   */
  public int getPort2()
  {
    return getValue(port2Arg);
  }

  /**
   * Returns the second server port default value in the enable replication
   * subcommand.
   * @return the second server port default value in the enable replication
   * subcommand.
   */
  public int getDefaultPort2()
  {
    return getDefaultValue(port2Arg);
  }

  /**
   * Returns the second server bind dn explicitly provided in the enable
   * replication subcommand.
   * @return the second server bind dn explicitly provided in the enable
   * replication subcommand.
   */
  public String getBindDn2()
  {
    return getValue(bindDn2Arg);
  }

  /**
   * Returns the second server bind dn default value in the enable replication
   * subcommand.
   * @return the second server bind dn default value in the enable replication
   * subcommand.
   */
  public String getDefaultBindDn2()
  {
    return getDefaultValue(bindDn2Arg);
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
    return getValue(replicationPort2Arg);
  }

  /**
   * Returns the second server replication port default value in the enable
   * replication subcommand.
   * @return the second server replication port default value in the enable
   * replication subcommand.
   */
  public int getDefaultReplicationPort2()
  {
    return getDefaultValue(replicationPort2Arg);
  }

  /**
   * Returns whether the user asked to have replication communication with the
   * second server or not.
   * @return <CODE>true</CODE> the user asked to have replication communication
   * with the second server and <CODE>false</CODE> otherwise.
   */
  public boolean isSecureReplication2()
  {
    return secureReplication2Arg.isPresent();
  }

  /**
   * Returns whether the user asked to skip the replication port checks (if the
   * ports are free) or not.
   * @return <CODE>true</CODE> the user asked to skip the replication port
   * checks (if the ports are free) and <CODE>false</CODE> otherwise.
   */
  public boolean skipReplicationPortCheck()
  {
    return skipPortCheckArg.isPresent();
  }

  /**
   * Returns whether the user asked to not replicate the schema between servers.
   * @return <CODE>true</CODE> if the user asked to not replicate schema and
   * <CODE>false</CODE> otherwise.
   */
  public boolean noSchemaReplication()
  {
    return noSchemaReplicationArg.isPresent();
  }

  /**
   * Returns whether the user asked to use the second server to initialize the
   * schema of the first server.
   * @return <CODE>true</CODE> if the user asked to use the second server to
   * initialize the schema of the first server and <CODE>false</CODE> otherwise.
   */
  public boolean useSecondServerAsSchemaSource()
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
  public String getDefaultHostNameToDisable()
  {
    return getDefaultValue(secureArgsList.hostNameArg);
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
  public String getDefaultHostNameToStatus()
  {
    return getDefaultValue(secureArgsList.hostNameArg);
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
  public String getDefaultHostNameToInitializeAll()
  {
    return getDefaultValue(secureArgsList.hostNameArg);
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
  public String getDefaultHostNameSource()
  {
    return getDefaultValue(hostNameSourceArg);
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
  public String getDefaultHostNameDestination()
  {
    return getDefaultValue(hostNameDestinationArg);
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
  public int getDefaultPortSource()
  {
    return getDefaultValue(portSourceArg);
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
  public int getDefaultPortDestination()
  {
    return getDefaultValue(portDestinationArg);
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
  public int getDefaultPortToDisable()
  {
    return getDefaultValue(secureArgsList.portArg);
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
  public int getDefaultPortToInitializeAll()
  {
    return getDefaultValue(secureArgsList.portArg);
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
  public int getDefaultPortToStatus()
  {
    return getDefaultValue(secureArgsList.portArg);
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
   * Returns the value of the provided argument only if the user provided it
   * explicitly.
   * @param arg the StringArgument to be handled.
   * @return the value of the provided argument only if the user provided it
   * explicitly.
   */
  private String getValue(StringArgument arg)
  {
    String v = null;
    if (arg.isPresent())
    {
      v = arg.getValue();
    }
    return v;
  }

  /**
   * Returns the default value of the provided argument.
   * @param arg the StringArgument to be handled.
   * @return the default value of the provided argument.
   */
  private String getDefaultValue(StringArgument arg)
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
  private int getValue(IntegerArgument arg)
  {
    int v = -1;
    if (arg.isPresent())
    {
      try
      {
        v = arg.getIntValue();
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
    return v;
  }

  /**
   * Returns the default value of the provided argument.
   * @param arg the StringArgument to be handled.
   * @return the default value of the provided argument.
   */
  private int getDefaultValue(IntegerArgument arg)
  {
    int returnValue = -1;
    String defaultValue = arg.getDefaultValue();
    if (defaultValue != null)
    {
      returnValue = Integer.parseInt(arg.getDefaultValue());
    }
    return returnValue;
  }

  /**
   * Checks the subcommand options and updates the provided MessageBuilder
   * with the errors that were encountered with the subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  public void validateSubcommandOptions(MessageBuilder buf)
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

    else
    {
      // This can occur if the user did not provide any subcommand.  We assume
      // that the error informing of this will be generated in
      // validateGlobalOptions.
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
   * Tells whether the user specified to apply the pre (or post) external
   * initialization operations only on the local server.
   * @return <CODE>true</CODE> if the user specified to apply the pre (or post)
   * external initialization operations only on the local server and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isExternalInitializationLocalOnly()
  {
    return externalInitializationLocalOnlyArg.isPresent();
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
    boolean isSubcommand = false;
    SubCommand subCommand = getSubCommand();
    if (subCommand != null)
    {
      isSubcommand = subCommand.getName().equalsIgnoreCase(name);
    }
    return isSubcommand;
  }

  /**
   * Checks the enable replication subcommand options and updates the provided
   * MessageBuilder with the errors that were encountered with the subcommand
   * options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateEnableReplicationOptions(MessageBuilder buf)
  {
    Argument[][] conflictingPairs =
    {
        {bindPassword1Arg, bindPasswordFile1Arg},
        {useStartTLS1Arg, useSSL1Arg},
        {bindPassword2Arg, bindPasswordFile2Arg},
        {useStartTLS2Arg, useSSL2Arg},
        {noSchemaReplicationArg, useSecondServerAsSchemaSourceArg}
    };

    for (int i=0; i< conflictingPairs.length; i++)
    {
      Argument arg1 = conflictingPairs[i][0];
      Argument arg2 = conflictingPairs[i][1];
      if (arg1.isPresent() && arg2.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
            arg1.getLongIdentifier(), arg2.getLongIdentifier());
        addMessage(buf, message);
      }
    }

    if (hostName1Arg.getValue().equalsIgnoreCase(hostName2Arg.getValue()) &&
        !isInteractive())
    {
      if (port1Arg.getValue() == port2Arg.getValue())
      {
        Message message = ERR_REPLICATION_ENABLE_SAME_SERVER_PORT.get(
            hostName1Arg.getValue(), port1Arg.getValue());
        addMessage(buf, message);
      }
    }
  }

  /**
   * Checks the disable replication subcommand options and updates the provided
   * MessageBuilder with the errors that were encountered with the subcommand
   * options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateDisableReplicationOptions(MessageBuilder buf)
  {
    Argument[][] conflictingPairs =
    {
        {secureArgsList.useStartTLSArg, secureArgsList.useSSLArg},
        {secureArgsList.adminUidArg, secureArgsList.bindDnArg}
    };

    for (int i=0; i< conflictingPairs.length; i++)
    {
      Argument arg1 = conflictingPairs[i][0];
      Argument arg2 = conflictingPairs[i][1];
      if (arg1.isPresent() && arg2.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
            arg1.getLongIdentifier(), arg2.getLongIdentifier());
        addMessage(buf, message);
      }
    }
  }

  /**
   * Checks the initialize all replication subcommand options and updates the
   * provided MessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateInitializeAllReplicationOptions(MessageBuilder buf)
  {
    Argument[][] conflictingPairs =
    {
        {secureArgsList.useStartTLSArg, secureArgsList.useSSLArg}
    };

    for (int i=0; i< conflictingPairs.length; i++)
    {
      Argument arg1 = conflictingPairs[i][0];
      Argument arg2 = conflictingPairs[i][1];
      if (arg1.isPresent() && arg2.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
            arg1.getLongIdentifier(), arg2.getLongIdentifier());
        addMessage(buf, message);
      }
    }
  }

  /**
   * Checks the pre external initialization subcommand options and updates the
   * provided MessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validatePreExternalInitializationOptions(MessageBuilder buf)
  {
    validateInitializeAllReplicationOptions(buf);
  }

  /**
   * Checks the post external initialization subcommand options and updates the
   * provided MessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validatePostExternalInitializationOptions(MessageBuilder buf)
  {
    validateInitializeAllReplicationOptions(buf);
  }

  /**
   * Checks the status replication subcommand options and updates the provided
   * MessageBuilder with the errors that were encountered with the subcommand
   * options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateStatusReplicationOptions(MessageBuilder buf)
  {
    Argument[][] conflictingPairs =
    {
        {secureArgsList.useStartTLSArg, secureArgsList.useSSLArg}
    };

    for (int i=0; i< conflictingPairs.length; i++)
    {
      Argument arg1 = conflictingPairs[i][0];
      Argument arg2 = conflictingPairs[i][1];
      if (arg1.isPresent() && arg2.isPresent())
      {
        Message message = ERR_TOOL_CONFLICTING_ARGS.get(
            arg1.getLongIdentifier(), arg2.getLongIdentifier());
        addMessage(buf, message);
      }
    }

    if (quietArg.isPresent())
    {
      Message message = ERR_REPLICATION_STATUS_QUIET.get(
          STATUS_REPLICATION_SUBCMD_NAME, quietArg.getLongIdentifier());
      addMessage(buf, message);
    }
  }

  /**
   * Checks the initialize replication subcommand options and updates the
   * provided MessageBuilder with the errors that were encountered with the
   * subcommand options.
   *
   * This method assumes that the method parseArguments for the parser has
   * already been called.
   * @param buf the MessageBuilder object where we add the error messages
   * describing the errors encountered.
   */
  private void validateInitializeReplicationOptions(MessageBuilder buf)
  {
    // The startTLS and useSSL arguments are already validated in
    // SecureConnectionCliParser.validateGlobalOptions.
    if (hostNameSourceArg.getValue().equalsIgnoreCase(
        hostNameDestinationArg.getValue()) && !isInteractive())
    {
      if (portSourceArg.getValue() == portDestinationArg.getValue())
      {
        Message message = ERR_REPLICATION_INITIALIZE_SAME_SERVER_PORT.get(
            hostNameSourceArg.getValue(), portSourceArg.getValue());
        addMessage(buf, message);
      }
    }
  }

  /**
   * Adds a message to the provided MessageBuilder.
   * @param buf the MessageBuilder.
   * @param message the message to be added.
   */
  private void addMessage(MessageBuilder buf, Message message)
  {
    if (buf.length() > 0)
    {
      buf.append(EOL);
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
      defaultLocalHostValue = UserData.getDefaultHostName();
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
  SecureConnectionCliArgs getSecureArgsList()
  {
    return secureArgsList;
  }
}
