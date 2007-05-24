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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.admin.ads;

import java.io.OutputStream;
import java.io.PrintStream;


import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.AdminMessages.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;


/**
 * This class provides a tool that can be used to Directory Server services.
 */
public class DsServiceCLI
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
      "org.opends.admin.ads.DsServiceCLI";


  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;



  /**
   * Constructor for the DsServiceCLI object.
   *
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public DsServiceCLI(PrintStream out, PrintStream err)
  {
    this.out           = out;
    this.err           = err;
  }

  /**
   * The main method for dsservice tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the dsservice tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args)
  {
    return mainCLI(args, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the dsservice tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args, OutputStream outStream,
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

    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_DSSERVICE_TOOL_DESCRIPTION);
    SubCommandArgumentParser argParser = new SubCommandArgumentParser(
        CLASS_NAME, toolDescription, false);

    // GLOBAL OPTION
    try
    {
      BooleanArgument showUsage = null;
      BooleanArgument useSSL = null;
      StringArgument hostName = null;
      IntegerArgument port = null;
      StringArgument bindDN = null;
      FileBasedArgument bindPasswordFile = null;
      BooleanArgument verbose = null;

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
          OPTION_LONG_HELP, MSGID_DESCRIPTION_SHOWUSAGE);
      argParser.addGlobalArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);

      useSSL = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
          OPTION_LONG_USE_SSL, MSGID_DESCRIPTION_USE_SSL);
      argParser.addGlobalArgument(useSSL);

      hostName = new StringArgument("host", OPTION_SHORT_HOST,
          OPTION_LONG_HOST, false, false, true, OPTION_VALUE_HOST, "localhost",
          null, MSGID_DESCRIPTION_HOST);
      argParser.addGlobalArgument(hostName);

      port = new IntegerArgument("port", OPTION_SHORT_PORT, OPTION_LONG_PORT,
          false, false, true, OPTION_VALUE_PORT, 389, null,
          MSGID_DESCRIPTION_PORT);
      argParser.addGlobalArgument(port);

      bindDN = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
          OPTION_LONG_BINDDN, false, false, true, OPTION_VALUE_BINDDN,
          "cn=Directory Manager", null, MSGID_DESCRIPTION_BINDDN);
      argParser.addGlobalArgument(bindDN);

      bindPasswordFile = new FileBasedArgument("bindPasswordFile",
          OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_BINDPWD_FILE, false, false,
          OPTION_VALUE_BINDPWD_FILE, null, null,
          MSGID_DESCRIPTION_BINDPASSWORDFILE);
      argParser.addGlobalArgument(bindPasswordFile);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
          MSGID_DESCRIPTION_VERBOSE);
      argParser.addGlobalArgument(verbose);
    }
    catch (ArgumentException ae)
    {
      int msgID = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // SERVER GROUP MANAGEMENT
    try
    {
      SubCommand subCmd ;
      Argument argument;

      // Create-group subcommand
      subCmd = new SubCommand(argParser,"create-group",true,1,1,
          OPERAND_GROUPID,
          MSGID_DSSERVICE_SUBCMD_CREATE_GROUP_DESCRIPTION);
      argument = new StringArgument("description", OPTION_SHORT_DESCRIPTION,
          OPTION_LONG_DESCRIPTION, false, false, true,
          OPTION_VALUE_DESCRIPTION, "", null,
          MSGID_DSSERVICE_ARG_DESCRIPTION_DESCRIPTION);
      subCmd.addArgument(argument);


      // modify-group
      subCmd = new SubCommand(argParser,"modify-group",true,1,1,
          OPERAND_GROUPID,
          MSGID_DSSERVICE_SUBCMD_MODIFY_GROUP_DESCRIPTION);
      argument = new StringArgument("new-description",
          OPTION_SHORT_DESCRIPTION,
          OPTION_LONG_DESCRIPTION, false, false, true,
          OPTION_VALUE_DESCRIPTION, "", null,
          MSGID_DSSERVICE_ARG_NEW_DESCRIPTION_DESCRIPTION);
      subCmd.addArgument(argument);
      argument = new StringArgument("new-groupID",
          OPTION_SHORT_GROUPID,
          OPTION_LONG_GROUPID, false, false, true,
          OPTION_VALUE_GROUPID, "", null,
          MSGID_DSSERVICE_ARG_NEW_GROUPID_DESCRIPTION);
      subCmd.addArgument(argument);

      // delete-group
      subCmd = new SubCommand(argParser,"delete-group",true,1,1,
          OPERAND_GROUPID,
          MSGID_DSSERVICE_SUBCMD_DELETE_GROUP_DESCRIPTION);

      // list-groups
      subCmd = new SubCommand(argParser,"list-groups",
          MSGID_DSSERVICE_SUBCMD_LIST_GROUPS_DESCRIPTION);

      // add-to-group
      subCmd = new SubCommand(argParser,"add-to-group",
          true,1,1,
          OPERAND_GROUPID,
          MSGID_DSSERVICE_SUBCMD_ADD_TO_GROUP_DESCRIPTION);
      argument = new StringArgument("memberID",
          OPTION_SHORT_MEMBERID,
          OPTION_LONG_MEMBERID, false, false, true,
          OPTION_VALUE_MEMBERID, "", null,
          MSGID_DSSERVICE_ARG_ADD_MEMBERID_DESCRIPTION);
      subCmd.addArgument(argument);

      // remove-from-group
      subCmd = new SubCommand(argParser,"remove-from-group",
          true,1,1,
          OPERAND_GROUPID,
          MSGID_DSSERVICE_SUBCMD_REMOVE_FROM_GROUP_DESCRIPTION);
      argument = new StringArgument("memberID",
          OPTION_SHORT_MEMBERID,
          OPTION_LONG_MEMBERID, false, false, true,
          OPTION_VALUE_MEMBERID, "", null,
          MSGID_DSSERVICE_ARG_REMOVE_MEMBERID_DESCRIPTION);
      subCmd.addArgument(argument);

      // list-members
      subCmd = new SubCommand(argParser,"list-members",
          true,1,1,
          OPERAND_GROUPID,
          MSGID_DSSERVICE_SUBCMD_LIST_MEMBERS_DESCRIPTION);

      // list-membership
      subCmd = new SubCommand(argParser,"list-membership",
          true,1,1,
          OPERAND_MEMBERID,
          MSGID_DSSERVICE_SUBCMD_LIST_MEMBERSHIP_DESCRIPTION);

    } catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

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
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }

    // If we should just display usage information, then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }
    return 0;
  }

}

