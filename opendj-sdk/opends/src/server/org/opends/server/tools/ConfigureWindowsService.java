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

package org.opends.server.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.wrapText;
import static org.opends.server.tools.ToolConstants.*;

/**
  * This class is used to configure the Windows service for this instance on
  * this machine.
  * This tool allows to enable and disable OpenDS to run as a Windows service
  * and allows to know if OpenDS is running as a Windows service or not.
  */
public class ConfigureWindowsService
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
    "org.opends.server.tools.ConfigureWindowsService";

  private static final String DEBUG_OPTION = "--debug";

  private static int ERROR = 1;

  /**
   * Return codes for the method enableService.
   */
  /**
   * The service was successfully enabled.
   */
  public static final int SERVICE_ENABLE_SUCCESS = 0;
  /**
   * The service was already enabled.
   */
  public static final int SERVICE_ALREADY_ENABLED = 1;
  /**
   * The service name was already in use.
   */
  public static final int SERVICE_NAME_ALREADY_IN_USE = 2;
  /**
   * An error occurred enabling the service.
   */
  public static final int SERVICE_ENABLE_ERROR = 3;

  /**
   * Return codes for the method disableService.
   */
  /**
   * The service was successfully disabled.
   */
  public static final int SERVICE_DISABLE_SUCCESS = 0;
  /**
   * The service was already disabled.
   */
  public static final int SERVICE_ALREADY_DISABLED = 1;
  /**
   * The service is marked for deletion.
   */
  public static final int SERVICE_MARKED_FOR_DELETION = 2;
  /**
   * An error occurred disabling the service.
   */
  public static final int SERVICE_DISABLE_ERROR = 3;

  /**
   * Return codes for the method serviceState.
   */
  /**
   * The service is enabled.
   */
  public static final int SERVICE_STATE_ENABLED = 0;
  /**
   * The service is disabled.
   */
  public static final int SERVICE_STATE_DISABLED = 1;
  /**
   * An error occurred checking the service state.
   */
  public static final int SERVICE_STATE_ERROR = 2;

  /**
   * Return codes for the method cleanupService.
   */
  /**
   * The service cleanup worked.
   */
  public static final int SERVICE_CLEANUP_SUCCESS = 0;
  /**
   * The service could not be found.
   */
  public static final int SERVICE_NOT_FOUND = 1;
  /**
   * An error occurred cleaning up the service.
   */
  public static final int SERVICE_CLEANUP_ERROR = 2;
  /**
   * The service is marked for deletion.
   */
  public static final int SERVICE_CLEANUP_MARKED_FOR_DELETION = 3;


  /**
   * Configures the Windows service for this instance on this machine.
   * This tool allows to enable and disable OpenDS to run as a Windows service
   * and allows to know if OpenDS is running as a Windows service or not.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int result = configureWindowsService(args, System.out, System.err);

    System.exit(result);
  }

  /**
   * Configures the Windows service for this instance on this machine.
   * This tool allows to enable and disable OpenDS to run as a Windows service
   * and allows to know if OpenDS is running as a Windows service or not.
   *
   * @param  args  The command-line arguments provided to this program.
   * @param outStream the stream used to write the standard output.
   * @param errStream the stream used to write the error output.
   * @return the integer code describing if the operation could be completed or
   * not.
   */
  public static int configureWindowsService(String[] args,
      OutputStream outStream, OutputStream errStream)
  {
    int returnValue = 0;
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

//  Define all the arguments that may be used with this program.
    String toolDescription =
      getMessage(MSGID_CONFIGURE_WINDOWS_SERVICE_TOOL_DESCRIPTION);
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME,
        toolDescription, false);
    BooleanArgument enableService = null;
    BooleanArgument disableService = null;
    BooleanArgument serviceState = null;
    StringArgument cleanupService = null;
    BooleanArgument showUsage = null;

    try
    {
      enableService = new BooleanArgument("enableservice", 'e', "enableService",
          MSGID_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_ENABLE);
      argParser.addArgument(enableService);

      disableService = new BooleanArgument("disableservice", 'd',
          "disableService",
          MSGID_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_DISABLE);
      argParser.addArgument(disableService);

      serviceState = new BooleanArgument("servicestate", 's',
          "serviceState",
          MSGID_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_STATE);
      argParser.addArgument(serviceState);

      cleanupService = new StringArgument("cleanupservice", 'c',
          "cleanupService", false, false, true, "{serviceName}", null, null,
          MSGID_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_CLEANUP);
      argParser.addArgument(cleanupService);

      showUsage = new BooleanArgument("showusage", OPTION_SHORT_HELP,
          OPTION_LONG_HELP,
          MSGID_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_SHOWUSAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      int msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      returnValue = ERROR;
    }

    // Parse the command-line arguments provided to this program.
    if (returnValue == 0)
    {
      try
      {
        argParser.parseArguments(args);
      }
      catch (ArgumentException ae)
      {
        int msgID = MSGID_ERROR_PARSING_ARGS;
        String message = getMessage(msgID, ae.getMessage());

        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        returnValue = ERROR;
      }
    }

    // If we should just display usage or version information,
    // then it is already done
    if ((returnValue == 0) && !argParser.usageOrVersionDisplayed())
    {
      /* Check that the user only asked for one argument */
      int nArgs = 0;
      if (enableService.isPresent())
      {
        nArgs++;
      }
      if (disableService.isPresent())
      {
        nArgs++;
      }
      if (serviceState.isPresent())
      {
        nArgs++;
      }
      if (cleanupService.isPresent())
      {
        nArgs++;
      }
      if (nArgs > 1)
      {
        int msgID = MSGID_CONFIGURE_WINDOWS_SERVICE_TOO_MANY_ARGS;
        String message = getMessage(msgID, (Object[])null);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        returnValue = ERROR;
      }
      if (nArgs == 0)
      {
        int msgID = MSGID_CONFIGURE_WINDOWS_SERVICE_TOO_FEW_ARGS;
        String message = getMessage(msgID, (Object[])null);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        returnValue = ERROR;
      }
    }

    if ((returnValue == 0) && !argParser.usageOrVersionDisplayed())
    {
      if (enableService.isPresent())
      {
        returnValue = enableService(out, err);
      }
      else if (disableService.isPresent())
      {
        returnValue = disableService(out, err);
      }
      else if (serviceState.isPresent())
      {
        returnValue = serviceState(out, err);
      }
      else
      {
        returnValue = cleanupService(cleanupService.getValue(), out, err);
      }
    }

    return returnValue;
  }

  /**
   * Returns the service name associated with OpenDS or null if no service name
   * could be found.
   * @return the service name associated with OpenDS or null if no service name
   * could be found.
   */
  static String getServiceName()
  {
    String serviceName = null;
    String serverRoot = getServerRoot();
    String[] cmd = {
        getBinaryFullPath(),
        "state",
        serverRoot
        };
    try
    {
      Process p = Runtime.getRuntime().exec(cmd);
      BufferedReader stdout = new BufferedReader(
          new InputStreamReader(p.getInputStream()));
      boolean processDone = false;
      String s;
      while (!processDone)
      {
        try
        {
          p.exitValue();
          processDone = true;
        }
        catch (Throwable t)
        {
        }
        while ((s = stdout.readLine()) != null)
        {
          serviceName = s;
          if (serviceName.trim().length() == 0)
          {
            serviceName = null;
          }
        }
      }
    }
    catch (Throwable t)
    {
      serviceName = null;
    }

    return serviceName;
  }

  /**
   * Enables OpenDS to run as a windows service.
   * @param out the stream used to write the standard output.
   * @param err the stream used to write the error output.
   * @return <CODE>SERVICE_ENABLE_SUCCESS</CODE>,
   * <CODE>SERVICE_ENABLE_ERROR</CODE>,
   * <CODE>SERVICE_NAME_ALREADY_IN_USE</CODE> or
   * <CODE>SERVICE_ALREADY_ENABLED</CODE> depending on whether the service could
   * be enabled or not.
   */
  public static int enableService(PrintStream out, PrintStream err)
  {
    int returnValue;
    String msg;
    String serverRoot = getServerRoot();

    String[] cmd = {
        getBinaryFullPath(),
        "create",
        serverRoot,
        getMessage(MSGID_WINDOWS_SERVICE_NAME, (Object[]) null),
        getMessage(MSGID_WINDOWS_SERVICE_DESCRIPTION, serverRoot),
        DEBUG_OPTION
        };
    try
    {
      int resultCode = Runtime.getRuntime().exec(cmd).waitFor();
      switch (resultCode)
      {
      case 0:
        returnValue = SERVICE_ENABLE_SUCCESS;
        msg = getMessage(MSGID_WINDOWS_SERVICE_SUCCESSULLY_ENABLED,
            (Object[])null);
        out.println(wrapText(msg, MAX_LINE_WIDTH));
        break;
      case 1:
        returnValue = SERVICE_ALREADY_ENABLED;
        msg = getMessage(MSGID_WINDOWS_SERVICE_ALREADY_ENABLED,
            (Object[])null);
        out.println(wrapText(msg, MAX_LINE_WIDTH));
        break;
      case 2:
        returnValue = SERVICE_NAME_ALREADY_IN_USE;
        msg = getMessage(MSGID_WINDOWS_SERVICE_NAME_ALREADY_IN_USE,
            (Object[])null);
        err.println(wrapText(msg, MAX_LINE_WIDTH));
        break;
      case 3:
        returnValue = SERVICE_ENABLE_ERROR;
        msg = getMessage(MSGID_WINDOWS_SERVICE_ENABLE_ERROR,
            (Object[])null);
        err.println(wrapText(msg, MAX_LINE_WIDTH));
        break;
      default:
        returnValue = SERVICE_ENABLE_ERROR;
        msg = getMessage(MSGID_WINDOWS_SERVICE_ENABLE_ERROR,
            (Object[])null);
        err.println(wrapText(msg, MAX_LINE_WIDTH));
      }
    }
    catch (Throwable t)
    {
      err.println("Fucking throwable: "+t);
      t.printStackTrace();
      returnValue = SERVICE_ENABLE_ERROR;
      msg = getMessage(MSGID_WINDOWS_SERVICE_ENABLE_ERROR,
          (Object[])null);
      err.println(wrapText(msg, MAX_LINE_WIDTH));
    }
    return returnValue;
  }

  /**
   * Disables OpenDS to run as a windows service.
   * @param out the stream used to write the standard output.
   * @param err the stream used to write the error output.
   * @return <CODE>SERVICE_DISABLE_SUCCESS</CODE>,
   * <CODE>SERVICE_DISABLE_ERROR</CODE>,
   * <CODE>SERVICE_MARKED_FOR_DELETION</CODE> or
   * <CODE>SERVICE_ALREADY_DISABLED</CODE> depending on whether the service
   * could be disabled or not.
   */
  public static int disableService(PrintStream out, PrintStream err)
  {
    int returnValue;
    String msg;
    String serverRoot = getServerRoot();
    String[] cmd = {
        getBinaryFullPath(),
        "remove",
        serverRoot,
        DEBUG_OPTION
        };
    try
    {
      int resultCode = Runtime.getRuntime().exec(cmd).waitFor();
      switch (resultCode)
      {
      case 0:
        returnValue = SERVICE_DISABLE_SUCCESS;
        msg = getMessage(MSGID_WINDOWS_SERVICE_SUCCESSULLY_DISABLED,
            (Object[])null);
        out.println(msg);
        break;
      case 1:
        returnValue = SERVICE_ALREADY_DISABLED;
        msg = getMessage(MSGID_WINDOWS_SERVICE_ALREADY_DISABLED,
            (Object[])null);
        out.println(msg);
        break;
      case 2:
        returnValue = SERVICE_MARKED_FOR_DELETION;
        msg = getMessage(MSGID_WINDOWS_SERVICE_MARKED_FOR_DELETION,
            (Object[])null);
        out.println(msg);
        break;
      case 3:
        returnValue = SERVICE_DISABLE_ERROR;
        msg = getMessage(MSGID_WINDOWS_SERVICE_DISABLE_ERROR,
            (Object[])null);
        err.println(msg);
        break;
      default:
        returnValue = SERVICE_DISABLE_ERROR;
        msg = getMessage(MSGID_WINDOWS_SERVICE_DISABLE_ERROR,
            (Object[])null);
        err.println(msg);
      }
    }
    catch (Throwable t)
    {
      t.printStackTrace();
      returnValue = SERVICE_DISABLE_ERROR;
      msg = getMessage(MSGID_WINDOWS_SERVICE_DISABLE_ERROR,
          (Object[])null);
      err.println(msg);
    }
    return returnValue;
  }

  /**
   * Cleans up a service for a given service name.
   * @param serviceName the service name to be cleaned up.
   * @param out the stream used to write the standard output.
   * @param err the stream used to write the error output.
   * @return <CODE>SERVICE_CLEANUP_SUCCESS</CODE>,
   * <CODE>SERVICE_NOT_FOUND</CODE>,
   * <CODE>SERVICE_MARKED_FOR_DELETION</CODE> or
   * <CODE>SERVICE_CLEANUP_ERROR</CODE> depending on whether the service
   * could be found or not.
   */
  public static int cleanupService(String serviceName, PrintStream out,
      PrintStream err)
  {
    int returnValue;
    String msg;
    String[] cmd = {
        getBinaryFullPath(),
        "cleanup",
        serviceName,
        DEBUG_OPTION
        };
    try
    {
      int resultCode = Runtime.getRuntime().exec(cmd).waitFor();
      switch (resultCode)
      {
      case 0:
        returnValue = SERVICE_CLEANUP_SUCCESS;
        msg = getMessage(MSGID_WINDOWS_SERVICE_CLEANUP_SUCCESS, serviceName);
        out.println(msg);
        break;
      case 1:
        returnValue = SERVICE_NOT_FOUND;
        msg = getMessage(MSGID_WINDOWS_SERVICE_CLEANUP_NOT_FOUND, serviceName);
        err.println(msg);
        break;
      case 2:
        returnValue = SERVICE_CLEANUP_MARKED_FOR_DELETION;
        msg = getMessage(MSGID_WINDOWS_SERVICE_CLEANUP_MARKED_FOR_DELETION,
            serviceName);
        out.println(msg);
        break;
      case 3:
        returnValue = SERVICE_CLEANUP_ERROR;
        msg = getMessage(MSGID_WINDOWS_SERVICE_CLEANUP_ERROR, serviceName);
        err.println(msg);
        break;
      default:
        returnValue = SERVICE_CLEANUP_ERROR;
        msg = getMessage(MSGID_WINDOWS_SERVICE_CLEANUP_ERROR, serviceName);
        err.println(msg);
      }
    }
    catch (Throwable t)
    {
      returnValue = SERVICE_CLEANUP_ERROR;
      msg = getMessage(MSGID_WINDOWS_SERVICE_CLEANUP_ERROR, serviceName);
      err.println(msg);
    }
    return returnValue;
  }

  /**
    * Checks if OpenDS is enabled as a windows service and if it is
    * write the serviceName in the output stream (if it is not null).
    * @param out the stream used to write the standard output.
    * @param err the stream used to write the error output.
    * @return <CODE>SERVICE_STATE_ENABLED</CODE>,
    * <CODE>SERVICE_STATE_DISABLED</CODE> or <CODE>SERVICE_STATE_ERROR</CODE>
    * depending on the state of the service.
    */
  public static int serviceState(PrintStream out, PrintStream err)
  {
    int returnValue;
    String msg;
    String serviceName = null;

    String serverRoot = getServerRoot();
    String[] cmd = {
        getBinaryFullPath(),
        "state",
        serverRoot,
        DEBUG_OPTION
        };
    try
    {
      int resultCode = -1;
      ProcessBuilder pb = new ProcessBuilder(cmd);
      Process process = pb.start();

      BufferedReader stdout =
          new BufferedReader(new InputStreamReader(process.getInputStream()));

      boolean processDone = false;
      String s;
      while (!processDone)
      {
        try
        {
          resultCode = process.exitValue();
          processDone = true;
        }
        catch (Throwable t)
        {
        }
        while ((s = stdout.readLine()) != null)
        {
          if (s.trim().length() != 0)
          {
            serviceName = s;
          }
        }
      }

      switch (resultCode)
      {
      case 0:
        returnValue = SERVICE_STATE_ENABLED;
        if (out != null)
        {
          msg = getMessage(MSGID_WINDOWS_SERVICE_ENABLED,
            serviceName);
          out.println(msg);
        }
        break;
      case 1:
        returnValue = SERVICE_STATE_DISABLED;
        if (out != null)
        {
          msg = getMessage(MSGID_WINDOWS_SERVICE_DISABLED,
            (Object[])null);
          out.println(msg);
        }
        break;
      case 2:
        returnValue = SERVICE_STATE_ERROR;
        if (out != null)
        {
          msg = getMessage(MSGID_WINDOWS_SERVICE_STATE_ERROR,
            (Object[])null);
          out.println(msg);
        }
        break;
      default:
        returnValue = SERVICE_STATE_ERROR;
        if (err != null)
        {
          msg = getMessage(MSGID_WINDOWS_SERVICE_STATE_ERROR,
            (Object[])null);
          err.println(msg);
        }
      }
    }
    catch (Throwable t)
    {
      returnValue = SERVICE_STATE_ERROR;
      if (err != null)
      {
        msg = getMessage(MSGID_WINDOWS_SERVICE_STATE_ERROR,
          (Object[])null);
        err.println(wrapText(msg, MAX_LINE_WIDTH));
      }
    }
    return returnValue;
  }

  /**
   * Returns the Directory Server installation path in a user friendly
   * representation.
   * @return the Directory Server installation path in a user friendly
   * representation.
   */
  private static String getServerRoot()
  {
    String serverRoot = DirectoryServer.getServerRoot();
    File f = new File(serverRoot);
    try
    {
      /*
       * Do a best effort to avoid having a relative representation (for
       * instance to avoid having ../../../).
       */
      File canonical = f.getCanonicalFile();
      f = canonical;
    }
    catch (IOException ioe)
    {
      /* This is a best effort to get the best possible representation of the
       * file: reporting the error is not necessary.
       */
    }
    serverRoot = f.toString();
    if (serverRoot.endsWith(File.separator))
    {
      serverRoot = serverRoot.substring(0, serverRoot.length() - 1);
    }
    return serverRoot;
  }

  /**
   * Returns the full path of the executable used by this class to perform
   * operations related to the service.
   * @return the full path of the executable used by this class to perform
   * operations related to the service.
   */
  private static String getBinaryFullPath()
  {
    return getServerRoot()+"\\lib\\opends_service.exe";
  }
}
