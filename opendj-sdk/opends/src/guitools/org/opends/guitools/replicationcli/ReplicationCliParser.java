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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
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
import org.opends.quicksetup.util.Utils;
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
public class ReplicationCliParser extends SecureConnectionCliParser
{
  private SubCommand enableReplicationSubCmd;
  private SubCommand disableReplicationSubCmd;
  private SubCommand initializeReplicationSubCmd;

  private BooleanArgument interactive;

  /**
   * The 'hostName' global argument for the first server.
   */
  private StringArgument hostName1Arg = null;

  /**
   * The 'port' global argument for the first server.
   */
  private IntegerArgument port1Arg = null;

  /**
   * The 'binDN' global argument for the first server.
   */
  private StringArgument bindDn1Arg = null;

  /**
   * The 'bindPasswordFile' global argument for the first server.
   */
  private FileBasedArgument bindPasswordFile1Arg = null;

  /**
   * The 'bindPassword' global argument for the first server.
   */
  private StringArgument bindPassword1Arg = null;

  /**
   * The 'useSSLArg' argument for the first server.
   */
  protected BooleanArgument useSSL1Arg = null;

  /**
   * The 'useStartTLS1Arg' argument for the first server.
   */
  protected BooleanArgument useStartTLS1Arg = null;

  /**
   * The 'replicationPort' global argument for the first server.
   */
  private IntegerArgument replicationPort1Arg = null;

  /**
   * The 'hostName' global argument for the second server.
   */
  private StringArgument hostName2Arg = null;

  /**
   * The 'port' global argument for the second server.
   */
  private IntegerArgument port2Arg = null;

  /**
   * The 'binDN' global argument for the second server.
   */
  private StringArgument bindDn2Arg = null;

  /**
   * The 'bindPasswordFile' global argument for the second server.
   */
  private FileBasedArgument bindPasswordFile2Arg = null;

  /**
   * The 'bindPassword' global argument for the second server.
   */
  private StringArgument bindPassword2Arg = null;

  /**
   * The 'useSSLArg' argument for the second server.
   */
  protected BooleanArgument useSSL2Arg = null;

  /**
   * The 'useStartTLS2Arg' argument for the second server.
   */
  protected BooleanArgument useStartTLS2Arg = null;

  /**
   * The 'replicationPort' global argument for the second server.
   */
  private IntegerArgument replicationPort2Arg = null;

  /**
   * The 'hostName' global argument for the source server.
   */
  private StringArgument hostNameSourceArg = null;

  /**
   * The 'port' global argument for the source server.
   */
  private IntegerArgument portSourceArg = null;

  /**
   * The 'useSSLArg' argument for the source server.
   */
  protected BooleanArgument useSSLSourceArg = null;

  /**
   * The 'useStartTLSSourceArg' argument for the source server.
   */
  protected BooleanArgument useStartTLSSourceArg = null;

  /**
   * The 'hostName' global argument for the destination server.
   */
  private StringArgument hostNameDestinationArg = null;

  /**
   * The 'port' global argument for the destination server.
   */
  private IntegerArgument portDestinationArg = null;

  /**
   * The 'useSSLArg' argument for the destination server.
   */
  protected BooleanArgument useSSLDestinationArg = null;

  /**
   * The 'useStartTLSDestinationArg' argument for the destination server.
   */
  protected BooleanArgument useStartTLSDestinationArg = null;

  /**
   * The 'suffixes' global argument.
   */
  private StringArgument baseDNsArg = null;

  /**
   * The 'admin UID' global argument.
   */
  private StringArgument adminUidArg;

  /**
   * The 'admin Password' global argument.
   */
  private StringArgument adminPasswordArg;

  /**
   * The 'admin Password File' global argument.
   */
  private FileBasedArgument adminPasswordFileArg;

  /**
   * The 'quiet' argument.
   */
  private BooleanArgument quietArg;

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
   * Creates a new instance of this argument parser with no arguments.
   *
   * @param mainClassName
   *          The fully-qualified name of the Java class that should
   *          be invoked to launch the program with which this
   *          argument parser is associated.
   */
  public ReplicationCliParser(String mainClassName)
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
    if (adminPasswordArg.isPresent() && adminPasswordFileArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          adminPasswordArg.getLongIdentifier(),
          adminPasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    if (!isInteractive())
    {
      // Check that we have the required data
      if (!baseDNsArg.isPresent())
      {
        errors.add(ERR_REPLICATION_NO_BASE_DN_PROVIDED.get());
      }
      if (getBindPasswordAdmin() == null)
      {
        errors.add(ERR_REPLICATION_NO_ADMINISTRATOR_PASSWORD_PROVIDED.get(
                adminPasswordArg.getLongIdentifier(),
                adminPasswordFileArg.getLongIdentifier()));
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
      hostNameArg,
      portArg,
      bindDnArg,
      bindPasswordFileArg,
      bindPasswordArg,
      useSSLArg,
      useStartTLSArg
    };

    for (int i=0; i<argsToRemove.length; i++)
    {
      defaultArgs.remove(argsToRemove[i]);
    }

    baseDNsArg = new StringArgument("baseDNs", 'b',
        "baseDNs", false, true, true, OPTION_VALUE_BASEDN, null,
        null, INFO_DESCRIPTION_REPLICATION_BASEDNS.get());
    defaultArgs.add(baseDNsArg);

    adminUidArg = new StringArgument("adminUID", 'I',
        "adminUID", false, false, true, "adminUID",
        Constants.GLOBAL_ADMIN_UID, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_UID.get(
            ENABLE_REPLICATION_SUBCMD_NAME));
    defaultArgs.add(adminUidArg);

    adminPasswordArg = new StringArgument("adminPassword",
        OPTION_SHORT_BINDPWD, "adminPassword", false, false, true,
        OPTION_VALUE_BINDPWD, null, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORD.get());
    defaultArgs.add(adminPasswordArg);

    adminPasswordFileArg = new FileBasedArgument("adminPasswordFile",
        OPTION_SHORT_BINDPWD_FILE, "adminPasswordFile", false, false,
        OPTION_VALUE_BINDPWD_FILE, null, null,
        INFO_DESCRIPTION_REPLICATION_ADMIN_BINDPASSWORDFILE.get());
    defaultArgs.add(adminPasswordFileArg);

    defaultArgs.remove(verboseArg);
    interactive = new BooleanArgument(
        INTERACTIVE_OPTION_LONG,
        INTERACTIVE_OPTION_SHORT,
        INTERACTIVE_OPTION_LONG,
        INFO_DESCRIPTION_INTERACTIVE.get());
    defaultArgs.add(interactive);

    quietArg = new BooleanArgument(
        SecureConnectionCliParser.QUIET_OPTION_LONG,
        SecureConnectionCliParser.QUIET_OPTION_SHORT,
        SecureConnectionCliParser.QUIET_OPTION_LONG,
        INFO_REPLICATION_DESCRIPTION_QUIET.get());
    defaultArgs.add(quietArg);
    initializeGlobalArguments(defaultArgs);
  }

  /**
   * Creates the enable replication subcommand and all the specific options
   * for the subcommand.
   */
  private void createEnableReplicationSubCommand()
  throws ArgumentException
  {

    hostName1Arg = new StringArgument("host1", 'h',
        "host1", false, false, true, OPTION_VALUE_HOST, "localhost",
        null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST1.get());

    port1Arg = new IntegerArgument("port1", 'p', "port1",
        false, false, true, OPTION_VALUE_PORT, 389, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT1.get());

    bindDn1Arg = new StringArgument("bindDN1", 'D',
        "bindDN1", false, false, true, OPTION_VALUE_BINDDN,
        "cn=Directory Manager", null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN1.get());

    bindPassword1Arg = new StringArgument("bindPassword1",
        'w', "bindPassword1", false, false, true,
        OPTION_VALUE_BINDPWD, null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD1.get());

    bindPasswordFile1Arg = new FileBasedArgument("bindPasswordFile1",
        'j', "bindPasswordFile1", false, false,
        OPTION_VALUE_BINDPWD_FILE, null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE1.get());

    useSSL1Arg = new BooleanArgument("useSSL1", 'Z',
        "useSSL1", INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SSL1.get());

    useStartTLS1Arg = new BooleanArgument("startTLS1", 'q',
        "startTLS1",
        INFO_DESCRIPTION_ENABLE_REPLICATION_STARTTLS1.get());

    replicationPort1Arg = new IntegerArgument("replicationPort1", 'r',
        "replicationPort1", false, false, true, OPTION_VALUE_PORT, 8989, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_PORT1.get());

    hostName2Arg = new StringArgument("host2", 'H',
        "host2", false, false, true, OPTION_VALUE_HOST, "localhost",
        null, INFO_DESCRIPTION_ENABLE_REPLICATION_HOST2.get());

    port2Arg = new IntegerArgument("port2", 'P', "port2",
        false, false, true, OPTION_VALUE_PORT, 389, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_SERVER_PORT2.get());

    bindDn2Arg = new StringArgument("bindDN2", 'N',
        "bindDN2", false, false, true, OPTION_VALUE_BINDDN,
        "cn=Directory Manager", null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDDN2.get());

    bindPassword2Arg = new StringArgument("bindPassword2",
        'W', "bindPassword2", false, false, true,
        OPTION_VALUE_BINDPWD, null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORD2.get());

    bindPasswordFile2Arg = new FileBasedArgument("bindPasswordFile2",
        'F', "bindPasswordFile2", false, false,
        OPTION_VALUE_BINDPWD_FILE, null, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_BINDPASSWORDFILE2.get());

    useSSL2Arg = new BooleanArgument("useSSL2", 'S',
        "useSSL2", INFO_DESCRIPTION_ENABLE_REPLICATION_USE_SSL2.get());

    useStartTLS2Arg = new BooleanArgument("startTLS2", 'Q',
        "startTLS2",
        INFO_DESCRIPTION_ENABLE_REPLICATION_STARTTLS2.get());

    replicationPort2Arg = new IntegerArgument("replicationPort2", 'R',
        "replicationPort2", false, false, true, OPTION_VALUE_PORT, 8989, null,
        INFO_DESCRIPTION_ENABLE_REPLICATION_PORT2.get());

    enableReplicationSubCmd = new SubCommand(this,
        ENABLE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_ENABLE_REPLICATION.get());

    Argument[] argsToAdd = {
        hostName1Arg, port1Arg, bindDn1Arg, bindPassword1Arg,
        bindPasswordFile1Arg, useStartTLS1Arg, useSSL1Arg, replicationPort1Arg,
        hostName2Arg, port2Arg, bindDn2Arg, bindPassword2Arg,
        bindPasswordFile2Arg, useStartTLS2Arg, useSSL2Arg, replicationPort2Arg,
    };
    for (int i=0; i<argsToAdd.length; i++)
    {
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

    Argument[] argsToAdd = {
        hostNameArg, portArg,
        useSSLArg, useStartTLSArg
    };
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
    hostNameSourceArg = new StringArgument("hostSource", 'h',
        "hostSource", false, false, true, OPTION_VALUE_HOST, "localhost",
        null, INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_SOURCE.get());

    portSourceArg = new IntegerArgument("portSource", 'p', "portSource",
        false, false, true, OPTION_VALUE_PORT, 389, null,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_SOURCE.get());

    useSSLSourceArg = new BooleanArgument("useSSLSource", 'Z',
        "useSSLSource",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_USE_SSL_SOURCE.get());

    useStartTLSSourceArg = new BooleanArgument("startTLSSource", 'q',
        "startTLSSource",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_STARTTLS_SOURCE.get());

    hostNameDestinationArg = new StringArgument("hostDestination", 'H',
        "hostDestination", false, false, true, OPTION_VALUE_HOST, "localhost",
        null, INFO_DESCRIPTION_INITIALIZE_REPLICATION_HOST_DESTINATION.get());

    portDestinationArg = new IntegerArgument("portDestination", 'P',
        "portDestination", false, false, true, OPTION_VALUE_PORT, 389, null,
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_SERVER_PORT_DESTINATION.get());

    useSSLDestinationArg = new BooleanArgument("useSSLDestination", 'S',
        "useSSLDestination",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_USE_SSL_DESTINATION.get());

    useStartTLSDestinationArg = new BooleanArgument("startTLSDestination", 'Q',
        "startTLSDestination",
        INFO_DESCRIPTION_INITIALIZE_REPLICATION_STARTTLS_DESTINATION.get());

    initializeReplicationSubCmd = new SubCommand(this,
        INITIALIZE_REPLICATION_SUBCMD_NAME,
        INFO_DESCRIPTION_SUBCMD_INITIALIZE_REPLICATION.get());

    Argument[] argsToAdd = {
        hostNameSourceArg, portSourceArg, useSSLSourceArg, useStartTLSSourceArg,
        hostNameDestinationArg, portDestinationArg, useSSLDestinationArg,
        useStartTLSDestinationArg
    };
    for (int i=0; i<argsToAdd.length; i++)
    {
      initializeReplicationSubCmd.addArgument(argsToAdd[i]);
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
    return interactive.isPresent();
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
    return getBindPassword(adminPasswordArg, adminPasswordFileArg);
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
    return getBindPassword(dn, out, err, adminPasswordArg,
        adminPasswordFileArg);
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
    return useSSLArg.isPresent();
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
    return useStartTLSArg.isPresent();
  }

  /**
   * Returns the Administrator UID explicitly provided in the command-line.
   * @return the Administrator UID explicitly provided in the command-line.
   */
  public String getAdministratorUID()
  {
    return getValue(adminUidArg);
  }

  /**
   * Returns the default Administrator UID value.
   * @return the default Administrator UID value.
   */
  public String getDefaultAdministratorUID()
  {
    return getDefaultValue(adminUidArg);
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
   * replication subcommand.  Returns -1 if no port was explicitly provided.
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
   * replication subcommand.  Returns -1 if no port was explicitly provided.
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
   * Returns the host name explicitly provided in the disable replication
   * subcommand.
   * @return the host name explicitly provided in the disable replication
   * subcommand.
   */
  public String getHostNameToDisable()
  {
    return getValue(hostNameArg);
  }

  /**
   * Returns the host name default value in the disable replication
   * subcommand.
   * @return the host name default value in the disable replication
   * subcommand.
   */
  public String getDefaultHostNameToDisable()
  {
    return getDefaultValue(hostNameArg);
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
    return getValue(portArg);
  }

  /**
   * Returns the server port default value in the disable replication
   * subcommand.
   * @return the server port default value in the disable replication
   * subcommand.
   */
  public int getDefaultPortToDisable()
  {
    return getDefaultValue(portArg);
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
    else  if (isInitializeReplicationSubcommand())
    {
      validateInitializeReplicationOptions(buf);
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
        {useStartTLS2Arg, useSSL2Arg}
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

    if (hostName1Arg.getValue().equalsIgnoreCase(hostName2Arg.getValue()))
    {
      if (port1Arg.getValue() == port2Arg.getValue())
      {
        Message message = ERR_REPLICATION_SAME_SERVER_PORT.get(
            hostName1Arg.getValue(), port1Arg.getValue());
        addMessage(buf, message);
      }

      // If the user explicitly provides the same port in the same host,
      // reject it.
      if (getValue(replicationPort1Arg) == getValue(replicationPort2Arg))
      {
        Message message = ERR_REPLICATION_SAME_REPLICATION_PORT.get(
            replicationPort1Arg.getValue(), hostName1Arg.getValue());
        addMessage(buf, message);
      }

      try
      {
        if (replicationPort1Arg.getIntValue() == port1Arg.getIntValue())
        {
          Message message = ERR_REPLICATION_SAME_REPLICATION_PORT.get(
              replicationPort1Arg.getValue(), hostName1Arg.getValue());
          addMessage(buf, message);
        }
      } catch (ArgumentException ae)
      {
        // This is a bug
        throw new IllegalStateException(
            "There was an argument exception calling "+
            "ReplicationCliParser.validateEnableReplicationOptions().  "+
            "This appears to be a bug "+
            "because this method should be called after calling "+
            "parseArguments which should result in an error.", ae);
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
        {useStartTLSSourceArg, useSSLSourceArg},
        {useStartTLSDestinationArg, useSSLDestinationArg}
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

    if (hostNameSourceArg.getValue().equalsIgnoreCase(
        hostNameDestinationArg.getValue()))
    {
      if (portSourceArg.getValue() == portDestinationArg.getValue())
      {
        Message message = ERR_REPLICATION_SAME_SERVER_PORT.get(
            hostNameSourceArg.getValue(), portSourceArg.getValue());
        addMessage(buf, message);
      }
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
}
