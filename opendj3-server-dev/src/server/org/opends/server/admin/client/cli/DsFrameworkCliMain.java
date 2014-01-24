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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.admin.client.cli;
import org.forgerock.i18n.LocalizableMessage;

import java.io.OutputStream;
import java.io.PrintStream;

import org.opends.admin.ads.ADSContextException;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.args.ArgumentException;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;
import static org.opends.messages.AdminMessages.*;
import static org.opends.messages.DSConfigMessages.*;
import static org.opends.messages.ToolMessages.*;

import org.forgerock.i18n.LocalizableMessageBuilder;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;


/**
 * This class provides a tool that can be used to Directory Server framework
 * services.
 */
public class DsFrameworkCliMain
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
      "org.opends.server.admin.client.cli.DsFrameworkCliMain";

  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;



  /**
   * Constructor for the DsFrameworkCLI object.
   *
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public DsFrameworkCliMain(PrintStream out, PrintStream err)
  {
    this.out           = out;
    this.err           = err;
  }

  /**
   * The main method for dsframework tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCLI(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the dsframework tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCLI(String[] args)
  {
    return mainCLI(args, true, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the dsframework tool.
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
   * @return The error code.
   */

  public static int mainCLI(String[] args, boolean initializeServer,
      OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);

    DsFrameworkCliMain dsFrameworkCli = new DsFrameworkCliMain(out, err);
    return dsFrameworkCli.execute(args,initializeServer);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the dsframework tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   *
   * @return The error code.
   */
  public int execute(String[] args, boolean initializeServer)
  {
    // Create the command-line argument parser for use with this
    // program.
    DsFrameworkCliParser argParser ;
    try
    {
      LocalizableMessage toolDescription = INFO_ADMIN_TOOL_DESCRIPTION.get();
      argParser = new DsFrameworkCliParser(CLASS_NAME,
          toolDescription, false);
      argParser.initializeParser(out);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CANNOT_INITIALIZE_ARGS.getReturnCode();
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return ERROR_PARSING_ARGS.getReturnCode();
    }

    // If we should just display usage information, then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return SUCCESSFUL.getReturnCode();
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    if (argParser.getSubCommand() == null)
    {
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(
              ERR_DSCFG_ERROR_MISSING_SUBCOMMAND.get());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println();
      err.println(argParser.getHelpUsageReference());
      return ERROR_PARSING_ARGS.getReturnCode();
    }

    // Validate args
    int ret = argParser.validateGlobalOptions(err);
    if (ret != SUCCESSFUL_NOP.getReturnCode())
    {
      return ret;
    }

    // Check if we need a connection

    DsFrameworkCliReturnCode returnCode = SUCCESSFUL;


    // Should we initialize the server in client mode?
    if (initializeServer)
    {
      // Bootstrap and initialize directory data structures.
      DirectoryServer.bootstrapClient();

      // Bootstrap definition classes.
      try
      {
        ClassLoaderProvider.getInstance().enable();
      }
      catch (InitializationException e)
      {
        err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
        return ERROR_UNEXPECTED.getReturnCode();
      }
      catch (IllegalStateException e)
      {
        err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
        return ERROR_UNEXPECTED.getReturnCode();
      }
    }

    // perform the subCommand
    ADSContextException adsException = null;
    try
    {
      returnCode = argParser.performSubCommand(out, err);
    }
    catch (ADSContextException e)
    {
      adsException = e;
      returnCode = DsFrameworkCliReturnCode.getReturncodeFromAdsError(e
          .getError());
      if (returnCode == null)
      {
        returnCode = ERROR_UNEXPECTED;
      }
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CANNOT_INITIALIZE_ARGS.getReturnCode();
    }

    LocalizableMessage msg = returnCode.getMessage();
    if ( (returnCode == SUCCESSFUL)
         ||
         (returnCode == SUCCESSFUL_NOP))
    {
      if (argParser.isVerbose())
      {
        out.println(wrapText(msg.toString(), MAX_LINE_WIDTH));
      }
    }
    else
    if (msg != null && StaticUtils.hasDescriptor(msg, ERR_ADMIN_NO_MESSAGE))
    {
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder(INFO_ADMIN_ERROR.get());
      mb.append(msg);
      err.println(wrapText(mb.toString(), MAX_LINE_WIDTH));
      if (argParser.isVerbose() && (adsException != null))
      {
        adsException.printStackTrace();
      }
    }
    return returnCode.getReturnCode();
  }
}
