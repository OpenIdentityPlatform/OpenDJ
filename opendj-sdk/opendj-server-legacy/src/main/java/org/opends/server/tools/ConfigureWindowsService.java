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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.opends.messages.ToolMessages.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.util.Utils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.SetupUtils;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.StringArgument;

/**
  * This class is used to configure the Windows service for this instance on
  * this machine.
  * This tool allows to enable and disable OpenDJ to run as a Windows service
  * and allows to know if OpenDJ is running as a Windows service or not.
  *
  * Some comments about Vista:
  * In Vista, when we launch the subcommands that require administrator
  * privileges (enable, disable and cleanup) we cannot use the administrator
  * launcher binary directly from Java (see
  * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6410605) so we use
  * winlauncher.exe.
  * When we launch subcommands that required administrator privileges
  * we must launch a binary containing the manifest that specifies that we
  * require administrator privileges (requireAdministrator value): if UAC is
  * enabled, the user will be asked for confirmation.
  * To minimize the number of confirmation that the user must provide when
  * launching the state subcommand we will use a binary whose manifest does
  * not contain the requireAdministrator value.
  *
  * See the files under src/build-tools/windows for more details.
  */
public class ConfigureWindowsService
{
  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.tools.ConfigureWindowsService";

  private static final String DEBUG_OPTION = "--debug";
  /** Option to be used when calling the launchers. */
  public static final String LAUNCHER_OPTION = "run";

  private static final int SUCCESS = 0;
  private static final int ERROR = 1;

  /** Return codes for the method enableService. */
  /** The service was successfully enabled. */
  public static final int SERVICE_ENABLE_SUCCESS = 0;
  /** The service was already enabled. */
  public static final int SERVICE_ALREADY_ENABLED = 1;
  /** The service name was already in use. */
  public static final int SERVICE_NAME_ALREADY_IN_USE = 2;
  /** An error occurred enabling the service. */
  public static final int SERVICE_ENABLE_ERROR = 3;

  /** Return codes for the method disableService. */
  /** The service was successfully disabled. */
  public static final int SERVICE_DISABLE_SUCCESS = 0;
  /** The service was already disabled. */
  public static final int SERVICE_ALREADY_DISABLED = 1;
  /** The service is marked for deletion. */
  public static final int SERVICE_MARKED_FOR_DELETION = 2;
  /** An error occurred disabling the service. */
  public static final int SERVICE_DISABLE_ERROR = 3;

  /** Return codes for the method serviceState. */
  /** The service is enabled. */
  public static final int SERVICE_STATE_ENABLED = 0;
  /** The service is disabled. */
  public static final int SERVICE_STATE_DISABLED = 1;
  /** An error occurred checking the service state. */
  public static final int SERVICE_STATE_ERROR = 2;

  /** Return codes for the method cleanupService. */
  /** The service cleanup worked. */
  public static final int SERVICE_CLEANUP_SUCCESS = 0;
  /** The service could not be found. */
  public static final int SERVICE_NOT_FOUND = 1;
  /** An error occurred cleaning up the service. */
  public static final int SERVICE_CLEANUP_ERROR = 2;
  /** The service is marked for deletion. */
  public static final int SERVICE_CLEANUP_MARKED_FOR_DELETION = 3;

  /**
   * Configures the Windows service for this instance on this machine. This tool
   * allows to enable and disable OpenDJ to run as a Windows service and allows
   * to know if OpenDJ is running as a Windows service or not.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int result = configureWindowsService(args, System.out, System.err);

    System.exit(filterExitCode(result));
  }

  /**
   * Configures the Windows service for this instance on this machine. This tool
   * allows to enable and disable OpenDJ to run as a Windows service and allows
   * to know if OpenDJ is running as a Windows service or not.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @param outStream
   *          the stream used to write the standard output.
   * @param errStream
   *          the stream used to write the error output.
   * @return the integer code describing if the operation could be completed or
   *         not.
   */
  public static int configureWindowsService(String[] args, OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    //  Define all the arguments that may be used with this program.
    LocalizableMessage toolDescription = INFO_CONFIGURE_WINDOWS_SERVICE_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_WINDOWS_SERVICE.get());
    BooleanArgument enableService = null;
    BooleanArgument disableService = null;
    BooleanArgument serviceState = null;
    StringArgument cleanupService = null;
    BooleanArgument showUsage = null;

    try
    {
      enableService = new BooleanArgument("enableservice", 'e', "enableService",
          INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_ENABLE.get());
      argParser.addArgument(enableService);

      disableService = new BooleanArgument("disableservice", 'd', "disableService",
          INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_DISABLE.get());
      argParser.addArgument(disableService);

      serviceState = new BooleanArgument("servicestate", 's', "serviceState",
          INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_STATE.get());
      argParser.addArgument(serviceState);

      cleanupService = new StringArgument("cleanupservice", 'c', "cleanupService", false, false, true,
          INFO_SERVICE_NAME_PLACEHOLDER.get(), null, null,
          INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_CLEANUP.get());
      argParser.addArgument(cleanupService);

      showUsage = CommonArguments.getShowUsage();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return ERROR;
    }

    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return ERROR;
    }

    // If we should just display usage or version information,
    // then it is already done
    if (!argParser.usageOrVersionDisplayed())
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
      if (nArgs != 1)
      {
        LocalizableMessage message = nArgs == 0 ? ERR_CONFIGURE_WINDOWS_SERVICE_TOO_FEW_ARGS.get()
                                                : ERR_CONFIGURE_WINDOWS_SERVICE_TOO_MANY_ARGS.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return ERROR;
      }
    }

    if (argParser.usageOrVersionDisplayed())
    {
      return SUCCESS;
    }

    if (enableService.isPresent())
    {
      return enableService(out, err);
    }
    else if (disableService.isPresent())
    {
      return disableService(out, err);
    }
    else if (serviceState.isPresent())
    {
      return serviceState(out, err);
    }

    return cleanupService(cleanupService.getValue(), out, err);
  }

  /**
   * Returns the service name associated with OpenDJ or null if no service name
   * could be found.
   *
   * @return the service name associated with OpenDJ or null if no service name
   *         could be found.
   */
  static String getServiceName()
  {
    String serverRoot = getServerRoot();
    String[] cmd = { getBinaryFullPath(), "state", serverRoot };
    try
    {
      String serviceName = null;
      Process p = Runtime.getRuntime().exec(cmd);
      BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
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
      return serviceName;
    }
    catch (Throwable t)
    {
      return null;
    }
  }

  /**
   * Enables OpenDJ to run as a windows service.
   *
   * @param out
   *          the stream used to write the standard output.
   * @param err
   *          the stream used to write the error output.
   * @return <CODE>SERVICE_ENABLE_SUCCESS</CODE>,
   *         <CODE>SERVICE_ENABLE_ERROR</CODE>,
   *         <CODE>SERVICE_NAME_ALREADY_IN_USE</CODE> or
   *         <CODE>SERVICE_ALREADY_ENABLED</CODE> depending on whether the
   *         service could be enabled or not.
   */
  public static int enableService(PrintStream out, PrintStream err)
  {
    LocalizableMessage serviceName = Utils.getCustomizedObject(
        "INFO_WINDOWS_SERVICE_NAME",
        INFO_WINDOWS_SERVICE_NAME.get(DynamicConstants.PRODUCT_NAME),
        LocalizableMessage.class);
    LocalizableMessage serviceDescription = Utils.getCustomizedObject(
        "INFO_WINDOWS_SERVICE_DESCRIPTION",
        INFO_WINDOWS_SERVICE_DESCRIPTION.get(getServerRoot()), LocalizableMessage.class);
    return enableService(out, err, serviceName.toString(), serviceDescription.toString());
  }

  /**
   * Enables OpenDJ to run as a windows service.
   *
   * @param out
   *          the stream used to write the standard output.
   * @param err
   *          the stream used to write the error output.
   * @param serviceName
   *          the name of the service as it will appear in the registry.
   * @param serviceDescription
   *          the description of the service as it will appear in the registry.
   * @return <CODE>SERVICE_ENABLE_SUCCESS</CODE>,
   *         <CODE>SERVICE_ENABLE_ERROR</CODE>,
   *         <CODE>SERVICE_NAME_ALREADY_IN_USE</CODE> or
   *         <CODE>SERVICE_ALREADY_ENABLED</CODE> depending on whether the
   *         service could be enabled or not.
   */
  public static int enableService(PrintStream out, PrintStream err, String serviceName, String serviceDescription)
  {
    LocalizableMessage msg;
    String serverRoot = getServerRoot();

    String[] cmd;

    if (hasUAC())
    {
      cmd = new String[] {
          getLauncherBinaryFullPath(),
          LAUNCHER_OPTION,
          getLauncherAdministratorBinaryFullPath(),
          LAUNCHER_OPTION,
          getBinaryFullPath(),
          "create",
          serverRoot,
          serviceName,
          serviceDescription,
          DEBUG_OPTION
      };
    }
    else
    {
      cmd = new String[] {
          getBinaryFullPath(),
          "create",
          serverRoot,
          serviceName,
          serviceDescription,
          DEBUG_OPTION
      };
    }

    try
    {
      boolean isServerRunning = Utilities.isServerRunning(new File(serverRoot));

      int resultCode = Runtime.getRuntime().exec(cmd).waitFor();
      switch (resultCode)
      {
      case 0:
        if (isServerRunning)
        {
          // We have to launch the windows service.  The service code already
          // handles this case (the service binary is executed when the server
          // already runs).
          final int returnValue = StartWindowsService.startWindowsService(out, err);
          if (returnValue == 0)
          {
            msg = INFO_WINDOWS_SERVICE_SUCCESSULLY_ENABLED.get();
            out.println(wrapText(msg, MAX_LINE_WIDTH));
            return SERVICE_ENABLE_SUCCESS;
          }
          else
          {
            msg = ERR_WINDOWS_SERVICE_ENABLING_ERROR_STARTING_SERVER.get(returnValue);
            err.println(wrapText(msg, MAX_LINE_WIDTH));
            return SERVICE_ENABLE_ERROR;
          }
        }
        else
        {
          msg = INFO_WINDOWS_SERVICE_SUCCESSULLY_ENABLED.get();
          out.println(wrapText(msg, MAX_LINE_WIDTH));
          return SERVICE_ENABLE_SUCCESS;
        }
      case 1:
        msg = INFO_WINDOWS_SERVICE_ALREADY_ENABLED.get();
        out.println(wrapText(msg, MAX_LINE_WIDTH));
        return SERVICE_ALREADY_ENABLED;
      case 2:
        msg = ERR_WINDOWS_SERVICE_NAME_ALREADY_IN_USE.get();
        err.println(wrapText(msg, MAX_LINE_WIDTH));
        return SERVICE_NAME_ALREADY_IN_USE;
      case 3:
        msg = ERR_WINDOWS_SERVICE_ENABLE_ERROR.get();
        err.println(wrapText(msg, MAX_LINE_WIDTH));
        return SERVICE_ENABLE_ERROR;
      default:
        msg = ERR_WINDOWS_SERVICE_ENABLE_ERROR.get();
        err.println(wrapText(msg, MAX_LINE_WIDTH));
        return SERVICE_ENABLE_ERROR;
      }
    }
    catch (Throwable t)
    {
      err.println("Unexpected throwable: "+t);
      t.printStackTrace();
      msg = ERR_WINDOWS_SERVICE_ENABLE_ERROR.get();
      err.println(wrapText(msg, MAX_LINE_WIDTH));
      return SERVICE_ENABLE_ERROR;
    }
  }

  /**
   * Disables OpenDJ to run as a windows service.
   *
   * @param out
   *          the stream used to write the standard output.
   * @param err
   *          the stream used to write the error output.
   * @return <CODE>SERVICE_DISABLE_SUCCESS</CODE>,
   *         <CODE>SERVICE_DISABLE_ERROR</CODE>,
   *         <CODE>SERVICE_MARKED_FOR_DELETION</CODE> or
   *         <CODE>SERVICE_ALREADY_DISABLED</CODE> depending on whether the
   *         service could be disabled or not.
   */
  public static int disableService(PrintStream out, PrintStream err)
  {
    LocalizableMessage msg;
    String serverRoot = getServerRoot();
    String[] cmd;
    if (hasUAC())
    {
      cmd = new String[] {
          getLauncherBinaryFullPath(),
          LAUNCHER_OPTION,
          getLauncherAdministratorBinaryFullPath(),
          LAUNCHER_OPTION,
          getBinaryFullPath(),
          "remove",
          serverRoot,
          DEBUG_OPTION
      };
    }
    else
    {
      cmd = new String[] {
        getBinaryFullPath(),
        "remove",
        serverRoot,
        DEBUG_OPTION
        };
    }
    try
    {
      int resultCode = Runtime.getRuntime().exec(cmd).waitFor();
      switch (resultCode)
      {
      case 0:
        msg = INFO_WINDOWS_SERVICE_SUCCESSULLY_DISABLED.get();
        out.println(msg);
        return SERVICE_DISABLE_SUCCESS;
      case 1:
        msg = INFO_WINDOWS_SERVICE_ALREADY_DISABLED.get();
        out.println(msg);
        return SERVICE_ALREADY_DISABLED;
      case 2:
        msg = WARN_WINDOWS_SERVICE_MARKED_FOR_DELETION.get();
        out.println(msg);
        return SERVICE_MARKED_FOR_DELETION;
      case 3:
        msg = ERR_WINDOWS_SERVICE_DISABLE_ERROR.get();
        err.println(msg);
        return SERVICE_DISABLE_ERROR;
      default:
        msg = ERR_WINDOWS_SERVICE_DISABLE_ERROR.get();
        err.println(msg);
        return SERVICE_DISABLE_ERROR;
      }
    }
    catch (Throwable t)
    {
      t.printStackTrace();
      msg = ERR_WINDOWS_SERVICE_DISABLE_ERROR.get();
      err.println(msg);
      return SERVICE_DISABLE_ERROR;
    }
  }

  /**
   * Cleans up a service for a given service name.
   *
   * @param serviceName
   *          the service name to be cleaned up.
   * @param out
   *          the stream used to write the standard output.
   * @param err
   *          the stream used to write the error output.
   * @return <CODE>SERVICE_CLEANUP_SUCCESS</CODE>,
   *         <CODE>SERVICE_NOT_FOUND</CODE>,
   *         <CODE>SERVICE_MARKED_FOR_DELETION</CODE> or
   *         <CODE>SERVICE_CLEANUP_ERROR</CODE> depending on whether the service
   *         could be found or not.
   */
  public static int cleanupService(String serviceName, PrintStream out,
      PrintStream err)
  {
    LocalizableMessage msg;
    String[] cmd;
    if (hasUAC())
    {
      cmd = new String[] {
          getLauncherBinaryFullPath(),
          LAUNCHER_OPTION,
          getLauncherAdministratorBinaryFullPath(),
          LAUNCHER_OPTION,
          getBinaryFullPath(),
          "cleanup",
          serviceName,
          DEBUG_OPTION
      };
    }
    else
    {
      cmd = new String[] {
          getBinaryFullPath(),
          "cleanup",
          serviceName,
          DEBUG_OPTION
      };
    }
    try
    {
      int resultCode = Runtime.getRuntime().exec(cmd).waitFor();
      switch (resultCode)
      {
      case 0:
        msg = INFO_WINDOWS_SERVICE_CLEANUP_SUCCESS.get(serviceName);
        out.println(msg);
        return SERVICE_CLEANUP_SUCCESS;
      case 1:
        msg = ERR_WINDOWS_SERVICE_CLEANUP_NOT_FOUND.get(serviceName);
        err.println(msg);
        return SERVICE_NOT_FOUND;
      case 2:
        msg = WARN_WINDOWS_SERVICE_CLEANUP_MARKED_FOR_DELETION.get(serviceName);
        out.println(msg);
        return SERVICE_CLEANUP_MARKED_FOR_DELETION;
      case 3:
        msg = ERR_WINDOWS_SERVICE_CLEANUP_ERROR.get(serviceName);
        err.println(msg);
        return SERVICE_CLEANUP_ERROR;
      default:
        msg = ERR_WINDOWS_SERVICE_CLEANUP_ERROR.get(serviceName);
        err.println(msg);
        return SERVICE_CLEANUP_ERROR;
      }
    }
    catch (Throwable t)
    {
      msg = ERR_WINDOWS_SERVICE_CLEANUP_ERROR.get(serviceName);
      err.println(msg);
      err.println("Exception:" + t);
      return SERVICE_CLEANUP_ERROR;
    }
  }

  /**
   * Checks if OpenDJ is enabled as a windows service.
   *
   * @return <CODE>SERVICE_STATE_ENABLED</CODE>,
   *         <CODE>SERVICE_STATE_DISABLED</CODE> or
   *         <CODE>SERVICE_STATE_ERROR</CODE> depending on the state of the
   *         service.
   */
  public static int serviceState()
  {
    return serviceState(NullOutputStream.printStream(), NullOutputStream.printStream());
  }

  /**
   * Checks if OpenDJ is enabled as a windows service and if it is write the
   * serviceName in the output stream (if it is not null).
   *
   * @param out
   *          the stream used to write the standard output.
   * @param err
   *          the stream used to write the error output.
   * @return <CODE>SERVICE_STATE_ENABLED</CODE>,
   *         <CODE>SERVICE_STATE_DISABLED</CODE> or
   *         <CODE>SERVICE_STATE_ERROR</CODE> depending on the state of the
   *         service.
   */
  public static int serviceState(PrintStream out, PrintStream err)
  {
    LocalizableMessage msg;
    String serviceName = null;

    String serverRoot = getServerRoot();
    String[] cmd = new String[] {
        getBinaryFullPath(),
        "state",
        serverRoot,
        DEBUG_OPTION
    };

    try
    {
      int resultCode = -1;
      Process process = new ProcessBuilder(cmd).start();
      BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));

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
        msg = INFO_WINDOWS_SERVICE_ENABLED.get(serviceName);
        out.println(msg);
        return SERVICE_STATE_ENABLED;
      case 1:
        msg = INFO_WINDOWS_SERVICE_DISABLED.get();
        out.println(msg);
        return SERVICE_STATE_DISABLED;
      case 2:
        msg = ERR_WINDOWS_SERVICE_STATE_ERROR.get();
        err.println(msg);
        return SERVICE_STATE_ERROR;
      default:
        msg = ERR_WINDOWS_SERVICE_STATE_ERROR.get();
        err.println(msg);
        return SERVICE_STATE_ERROR;
      }
    }
    catch (Throwable t)
    {
      msg = ERR_WINDOWS_SERVICE_STATE_ERROR.get();
      err.println(msg);
      err.println(wrapText(t.toString(), MAX_LINE_WIDTH));
      return SERVICE_STATE_ERROR;
    }
  }

  /**
   * Returns the Directory Server installation path in a user friendly
   * representation.
   *
   * @return the Directory Server installation path in a user friendly
   *         representation.
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
      f = f.getCanonicalFile();
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
   * operations related to the service. This binaries file has the asInvoker
   * value in its manifest.
   *
   * @return the full path of the executable used by this class to perform
   *         operations related to the service.
   */
  private static String getBinaryFullPath()
  {
    return SetupUtils.getScriptPath(getServerRoot() + "\\lib\\opendj_service.exe");
  }

  /**
   * Returns the full path of the executable that has a manifest requiring
   * administrator privileges used by this class to perform operations related
   * to the service.
   *
   * @return the full path of the executable that has a manifest requiring
   *         administrator privileges used by this class to perform operations
   *         related to the service.
   */
  public static String getLauncherAdministratorBinaryFullPath()
  {
    return getServerRoot() + "\\lib\\launcher_administrator.exe";
  }

  /**
   * Returns the full path of the executable that has a manifest requiring
   * administrator privileges used by this class to perform operations related
   * to the service.
   *
   * @return the full path of the executable that has a manifest requiring
   *         administrator privileges used by this class to perform operations
   *         related to the service.
   */
  public static String getLauncherBinaryFullPath()
  {
    return getServerRoot() + "\\lib\\winlauncher.exe";
  }
}
