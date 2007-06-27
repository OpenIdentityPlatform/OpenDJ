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
package org.opends.server.admin.client.cli;

import java.io.OutputStream;
import java.io.PrintStream;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.AdminMessages.*;
import static org.opends.server.messages.ToolMessages.*;
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
      String toolDescription = getMessage(MSGID_ADMIN_TOOL_DESCRIPTION);
      argParser = new DsFrameworkCliParser(CLASS_NAME,
          toolDescription, false);
      argParser.initializeParser(out);
    }
    catch (ArgumentException ae)
    {
      int msgID = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return ReturnCode.CANNOT_INITIALIZE_ARGS.getReturnCode();
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
      return ReturnCode.ERROR_PARSING_ARGS.getReturnCode();
    }

    // If we should just display usage information, then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return ReturnCode.SUCCESSFUL.getReturnCode();
    }

    if (argParser.getSubCommand() == null)
    {
      err.println(argParser.getUsage());
      return ReturnCode.ERROR_PARSING_ARGS.getReturnCode();
    }

    // Validate args
    int ret = argParser.validateGlobalOption(err);
    if (ret != ReturnCode.SUCCESSFUL_NOP.getReturnCode())
    {
      return ret;
    }


    // Get connection parameters
    String host = argParser.getHostName() ;
    String port = argParser.getPort() ;
    String dn   = argParser.getBindDN() ;
    String pwd  = argParser.getBindPassword(dn,out,err) ;

    // Try to connect
    InitialLdapContext ctx = null;
    ReturnCode returnCode = ReturnCode.SUCCESSFUL;
    if (argParser.useSSL())
    {
      String ldapsUrl = "ldaps://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createLdapsContext(ldapsUrl,
            dn, pwd, ConnectionUtils.getDefaultLDAPTimeout(), null,
             argParser.getTrustManager(), argParser.getKeyManager());
      }
      catch (NamingException e)
      {
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        err.println(wrapText(message, MAX_LINE_WIDTH));
        return ReturnCode.CANNOT_CONNECT_TO_ADS.getReturnCode();
      }
    }
    else
    if (argParser.startTLS())
    {
      String ldapUrl = "ldap://" + host + ":" + port;
      try
      {
        ctx = ConnectionUtils.createStartTLSContext(ldapUrl, dn, pwd,
            ConnectionUtils.getDefaultLDAPTimeout(), null, argParser
                .getTrustManager(), argParser.getKeyManager(), null);
      }
      catch (NamingException e)
      {
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        err.println(wrapText(message, MAX_LINE_WIDTH));
        return ReturnCode.CANNOT_CONNECT_TO_ADS.getReturnCode();
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
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        err.println(wrapText(message, MAX_LINE_WIDTH));
        return ReturnCode.CANNOT_CONNECT_TO_ADS.getReturnCode();
      }
    }
    ADSContext adsContext = new ADSContext(ctx);

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
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        err.println(wrapText(message, MAX_LINE_WIDTH));
        return ReturnCode.CANNOT_CONNECT_TO_ADS.getReturnCode();
      }
      catch (IllegalStateException e)
      {
        int msgID = MSGID_ADMIN_CANNOT_CONNECT_TO_ADS;
        String message = getMessage(msgID, host);

        err.println(wrapText(message, MAX_LINE_WIDTH));
        return ReturnCode.CANNOT_CONNECT_TO_ADS.getReturnCode();
      }
    }

    // perform the subCommand
    ADSContextException adsException = null ;
    try
    {
      returnCode = argParser.performSubCommand(adsContext, out, err);
    }
    catch (ADSContextException e)
    {
      adsException = e;
      returnCode = DsFrameworkCliReturnCode.getReturncodeFromAdsError(e
          .getError());
      if (returnCode == null)
      {
        returnCode = ReturnCode.ERROR_UNEXPECTED;
      }
    }

    // deconnection
    try
    {
      ctx.close();
    }
    catch (NamingException e)
    {
      // TODO Should we do something ?
    }

    int msgID = returnCode.getMessageId();
    String message = "" ;
    if ( (returnCode == ReturnCode.SUCCESSFUL)
         ||
         (returnCode == ReturnCode.SUCCESSFUL_NOP))
    {
      if (argParser.isVerbose())
      {
        out.println(wrapText(getMessage(msgID), MAX_LINE_WIDTH));
      }
    }
    else
    if (msgID != MSGID_ADMIN_NO_MESSAGE)
    {
      message = getMessage(MSGID_ADMIN_ERROR);
      message = message + getMessage(msgID);
      err.println(wrapText(message, MAX_LINE_WIDTH));
      if (argParser.isVerbose() && (adsException != null))
      {
        adsException.printStackTrace();
      }
    }
    return returnCode.getReturnCode();
  }
}
