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
package org.opends.server.admin.client.cli;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.AdminMessages.*;
import static org.opends.messages.DSConfigMessages.*;
import org.opends.messages.Message;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.wrapText;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.SubCommand;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;

/**
 * This class will parse CLI arguments for the dsframework command lines.
 */
public class DsFrameworkCliParser extends SecureConnectionCliParser
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The Logger.
   */
  static private final Logger LOG =
    Logger.getLogger(DsFrameworkCliParser.class.getName());

  /**
   * The different CLI group.
   */
  public HashSet<DsFrameworkCliSubCommandGroup> cliGroup;



  /**
   * Creates a new instance of this subcommand argument parser with no
   * arguments.
   *
   * @param mainClassName
   *          The fully-qualified name of the Java class that should
   *          be invoked to launch the program with which this
   *          argument parser is associated.
   * @param toolDescription
   *          A human-readable description for the tool, which will be
   *          included when displaying usage information.
   * @param longArgumentsCaseSensitive
   *          Indicates whether subcommand and long argument names
   *          should be treated in a case-sensitive manner.
   */
  public DsFrameworkCliParser(String mainClassName, Message toolDescription,
      boolean longArgumentsCaseSensitive)
  {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
    cliGroup = new HashSet<DsFrameworkCliSubCommandGroup>();
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
    // Global parameters
    initializeGlobalArguments(createGlobalArguments(outStream));

    // ads  Group cli
    cliGroup.add(new DsFrameworkCliAds());

    // Server-group Group cli
    cliGroup.add(new DsFrameworkCliServerGroup());

    // Server Group cli
    cliGroup.add(new DsFrameworkCliServer());

    // User Admin cli
    cliGroup.add(new DsFrameworkCliGlobalAdmin());

    // Initialization
    Comparator<SubCommand> c = new Comparator<SubCommand>() {

      public int compare(SubCommand o1, SubCommand o2) {
        return o1.getName().compareTo(o2.getName());
      }
    };

    SortedSet<SubCommand> allSubCommands = new TreeSet<SubCommand>(c);

    for (DsFrameworkCliSubCommandGroup oneCli : cliGroup)
    {
      oneCli.initializeCliGroup(this, verboseArg);
      Set<SubCommand> oneCliSubCmds = oneCli.getSubCommands() ;
      allSubCommands.addAll(oneCliSubCmds);

      // register group help
      String grpName = oneCli.getGroupName();
      String option = OPTION_LONG_HELP + "-" + grpName;
      BooleanArgument arg = new BooleanArgument(option, null, option,
          INFO_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE.get(grpName));
      addGlobalArgument(arg);
      arg.setHidden(oneCli.isHidden());
      TreeSet<SubCommand> subCmds = new TreeSet<SubCommand>(c);
      subCmds.addAll(oneCliSubCmds);
      setUsageGroupArgument(arg, subCmds);
    }

    // Register the --help-all argument.
    String option = OPTION_LONG_HELP + "-all";
    BooleanArgument arg = new BooleanArgument(option, null, option,
        INFO_DSCFG_DESCRIPTION_SHOW_GROUP_USAGE_ALL.get());

    addGlobalArgument(arg);
    setUsageGroupArgument(arg, allSubCommands);

  }



  /**
   * Handle the subcommand.
   * @param  outStream         The output stream to use for standard output.
   * @param  errStream         The output stream to use for standard error.
   *
   * @return the return code
   * @throws ADSContextException
   *           If there is a problem with when trying to perform the
   *           operation.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to execute this subcommand.
   */
  public DsFrameworkCliReturnCode performSubCommand(OutputStream outStream,
      OutputStream errStream)
    throws ADSContextException, ArgumentException
  {
    SubCommand subCmd = getSubCommand();

    for (DsFrameworkCliSubCommandGroup oneCli : cliGroup)
    {
      if (oneCli.isSubCommand(subCmd))
      {
        return oneCli.performSubCommand( subCmd, outStream, errStream);
      }
    }

    // Should never occurs: If we are here, it means that the code to
    // handle to subcommand is not yet written.
    return ERROR_UNEXPECTED;
  }


  /**
   * Get the InitialLdapContext that has to be used for the ADS.
   * @param  out         The output stream to use for standard output.
   * @param  err         The output stream to use for standard error.
   *
   * @return The InitialLdapContext that has to be used for the ADS.
   */
  public InitialLdapContext getContext(OutputStream out, OutputStream err)
  {
    // Get connection parameters
    String host = null ;
    String port = null;
    String dn   = null ;
    String pwd  = null;
    InitialLdapContext ctx = null;

    // Get connection parameters
    host = ConnectionUtils.getHostNameForLdapUrl(getHostName());
    port = getPort();
    dn   = getBindDN();
    pwd  = getBindPassword(dn, out, err);

    // Try to connect
    if (useSSL())
    {
      String ldapsUrl = "ldaps://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createLdapsContext(ldapsUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null,getTrustManager(),
            getKeyManager());
      }
      catch (NamingException e)
      {
        Message message = ERR_ADMIN_CANNOT_CONNECT_TO_ADS.get(host);
        try
        {
          err.write(wrapText(message, MAX_LINE_WIDTH).getBytes());
          err.write(EOL.getBytes());
        }
        catch (IOException e1)
        {
        }
        return null;
      }
    }
    else if (useStartTLS())
    {
      String ldapUrl = "ldap://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createStartTLSContext(ldapUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null, getTrustManager(),
            getKeyManager(), null);
      }
      catch (NamingException e)
      {
        Message message = ERR_ADMIN_CANNOT_CONNECT_TO_ADS.get(host);
        try
        {
          err.write(wrapText(message, MAX_LINE_WIDTH).getBytes());
          err.write(EOL.getBytes());
        }
        catch (IOException e1)
        {
        }
        return null;
      }
    }
    else
    {
      String ldapUrl = "ldap://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createLdapContext(ldapUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null);
      }
      catch (NamingException e)
      {
        Message message = ERR_ADMIN_CANNOT_CONNECT_TO_ADS.get(host);
        try
        {
          err.write(wrapText(message, MAX_LINE_WIDTH).getBytes());
          err.write(EOL.getBytes());
        }
        catch (IOException e1)
        {
        }
        return null;
      }
    }
    return ctx;
  }
}
