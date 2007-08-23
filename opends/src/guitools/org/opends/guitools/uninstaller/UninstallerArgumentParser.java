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

package org.opends.guitools.uninstaller;

import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserData;
import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.tools.ToolConstants;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

/**
 * Class used to parse and populate the arguments of the Uninstaller.
 *
 */
public class UninstallerArgumentParser extends SecureConnectionCliParser
{
  private BooleanArgument interactive;
  private BooleanArgument forceOnError;
  private BooleanArgument quiet;
  private BooleanArgument removeAll;
  private BooleanArgument removeServerLibraries;
  private BooleanArgument removeDatabases;
  private BooleanArgument removeLogFiles;
  private BooleanArgument removeConfigurationFiles;
  private BooleanArgument removeBackupFiles;
  private BooleanArgument removeLDIFFiles;

  /**
   * The 'admin UID' global argument.
   */
  private StringArgument adminUidArg;
  private StringArgument referencedHostNameArg;

  /**
   * Creates a new instance of this argument parser with no arguments.
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
  public UninstallerArgumentParser(String mainClassName,
      Message toolDescription, boolean longArgumentsCaseSensitive)
  {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
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
  public void initializeGlobalArguments(OutputStream outStream)
  throws ArgumentException
  {
    LinkedHashSet<Argument> args = new LinkedHashSet<Argument>();
    removeAll = new BooleanArgument(
        "remove-all",
        'a',
        "remove-all",
        INFO_UNINSTALLDS_DESCRIPTION_REMOVE_ALL.get()
        );
    args.add(removeAll);
    removeServerLibraries = new BooleanArgument(
        "server-libraries",
        'l',
        "server-libraries",
        INFO_UNINSTALLDS_DESCRIPTION_REMOVE_SERVER_LIBRARIES.get()
        );
    args.add(removeServerLibraries);
    removeDatabases = new BooleanArgument(
        "databases",
        'd',
        "databases",
        INFO_UNINSTALLDS_DESCRIPTION_REMOVE_DATABASES.get()
        );
    args.add(removeDatabases);
    removeLogFiles = new BooleanArgument(
        "log-files",
        'L',
        "log-files",
        INFO_UNINSTALLDS_DESCRIPTION_REMOVE_LOG_FILES.get()
        );
    args.add(removeLogFiles);
    removeConfigurationFiles = new BooleanArgument(
        "configuration-files",
        'c',
        "configuration-files",
        INFO_UNINSTALLDS_DESCRIPTION_REMOVE_CONFIGURATION_FILES.get()
        );
    args.add(removeConfigurationFiles);
    removeBackupFiles = new BooleanArgument(
        "backup-files",
        'b',
        "backup-files",
        INFO_UNINSTALLDS_DESCRIPTION_REMOVE_BACKUP_FILES.get()
        );
    args.add(removeBackupFiles);
    removeLDIFFiles = new BooleanArgument(
        "ldif-files",
        'e',
        "ldif-files",
        INFO_UNINSTALLDS_DESCRIPTION_REMOVE_LDIF_FILES.get()
        );
    args.add(removeLDIFFiles);
    interactive = new BooleanArgument(
        INTERACTIVE_OPTION_LONG,
        INTERACTIVE_OPTION_SHORT,
        INTERACTIVE_OPTION_LONG,
        INFO_DESCRIPTION_INTERACTIVE.get());
    args.add(interactive);
    forceOnError = new BooleanArgument(
        "forceOnError",
        'f',
        "forceOnError",
        INFO_UNINSTALLDS_DESCRIPTION_FORCE.get());
    args.add(forceOnError);
    quiet = new BooleanArgument(
        SecureConnectionCliParser.QUIET_OPTION_LONG,
        SecureConnectionCliParser.QUIET_OPTION_SHORT,
        SecureConnectionCliParser.QUIET_OPTION_LONG,
        INFO_UNINSTALLDS_DESCRIPTION_SILENT.get());
    args.add(quiet);

    adminUidArg = new StringArgument("adminUID", 'I',
        "adminUID", false, false, true, "adminUID",
        Constants.GLOBAL_ADMIN_UID, null, INFO_DESCRIPTION_ADMIN_UID.get());

    ArrayList<Argument> defaultArgs =
      new ArrayList<Argument>(createGlobalArguments(System.err));
    int index = defaultArgs.indexOf(bindDnArg);
    if (index != -1)
    {
      defaultArgs.add(index, adminUidArg);
      defaultArgs.remove(bindDnArg);
    }
    else
    {
      defaultArgs.add(adminUidArg);
    }
    defaultArgs.remove(hostNameArg);
    defaultArgs.remove(portArg);
    defaultArgs.remove(verboseArg);
    UserData uData = new UserData();
    referencedHostNameArg = new StringArgument("referencedHostName",
        ToolConstants.OPTION_SHORT_HOST,
        "referencedHostName", false, false, true,
        ToolConstants.OPTION_VALUE_HOST,
        uData.getHostName(), null, INFO_DESCRIPTION_REFERENCED_HOST.get());
    defaultArgs.add(referencedHostNameArg);

    args.addAll(defaultArgs);
    initializeGlobalArguments(args);
  }

  /**
   * Tells whether the user specified to have an interactive uninstall or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have an interactive
   * uninstall and <CODE>false</CODE> otherwise.
   */
  public boolean isInteractive()
  {
    return interactive.isPresent();
  }

  /**
   * Tells whether the user specified to force on non critical error in the non
   * interactive mode.
   * @return <CODE>true</CODE> if the user specified to force the uninstall in
   * non critical error and <CODE>false</CODE> otherwise.
   */
  public boolean isForceOnError()
  {
    return forceOnError.isPresent();
  }

  /**
   * Tells whether the user specified to have a quiet uninstall or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have a quiet
   * uninstall and <CODE>false</CODE> otherwise.
   */
  public boolean isQuiet()
  {
    return quiet.isPresent();
  }

  /**
   * Tells whether the user specified to remove all files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove all files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeAll()
  {
    return removeAll.isPresent();
  }

  /**
   * Tells whether the user specified to remove library files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove library files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeServerLibraries()
  {
    return removeServerLibraries.isPresent();
  }

  /**
   * Tells whether the user specified to remove database files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove database files
   * and <CODE>false</CODE> otherwise.
   */
  public boolean removeDatabases()
  {
    return removeDatabases.isPresent();
  }

  /**
   * Tells whether the user specified to remove configuration files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove configuration
   * files and <CODE>false</CODE> otherwise.
   */
  public boolean removeConfigurationFiles()
  {
    return removeConfigurationFiles.isPresent();
  }

  /**
   * Tells whether the user specified to remove backup files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove backup files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeBackupFiles()
  {
    return removeBackupFiles.isPresent();
  }

  /**
   * Tells whether the user specified to remove LDIF files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove LDIF files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeLDIFFiles()
  {
    return removeLDIFFiles.isPresent();
  }

  /**
   * Tells whether the user specified to remove log files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove log files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeLogFiles()
  {
    return removeLogFiles.isPresent();
  }

  /**
   * Returns the Administrator UID provided in the command-line.
   * @return the Administrator UID provided in the command-line.
   */
  public String getAdministratorUID()
  {
    String uid = null;
    if (adminUidArg.isPresent())
    {
      uid = adminUidArg.getValue();
    }
    return uid;
  }

  /**
   * Returns the default Administrator UID value.
   * @return the default Administrator UID value.
   */
  public String getDefaultAdministratorUID()
  {
    return adminUidArg.getDefaultValue();
  }

  /**
   * Returns the Host name to update remote references as provided in the
   * command-line.
   * @return the Host name to update remote references as provided in the
   * command-line.
   */
  public String getReferencedHostName()
  {
    String hostName = null;
    if (referencedHostNameArg.isPresent())
    {
      hostName = referencedHostNameArg.getValue();
    }
    return hostName;
  }

  /**
   * Returns the default value for the Host name to update remote references as
   * provided in the command-line.
   * @return the default value for the Host name to update remote references as
   * provided in the command-line.
   */
  public String getDefaultReferencedHostName()
  {
    return referencedHostNameArg.getDefaultValue();
  }

  /**
   * Indication if provided global options are validate.
   *
   * @param buf the MessageBuilder to write the error messages.
   * @return return code.
   */
  public int validateGlobalOptions(MessageBuilder buf)
  {
    int returnValue;
    if (interactive.isPresent() && forceOnError.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          interactive.getLongIdentifier(),
          forceOnError.getLongIdentifier());
      if (buf.length() > 0)
      {
        buf.append(EOL);
      }
      buf.append(message);
    }
    if (removeAll.isPresent())
    {
      BooleanArgument[] removeArgs = {
          removeServerLibraries,
          removeDatabases,
          removeLogFiles,
          removeConfigurationFiles,
          removeBackupFiles,
          removeLDIFFiles
      };
      for (int i=0; i<removeArgs.length; i++)
      {
        if (removeArgs[i].isPresent())
        {
          Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              removeAll.getLongIdentifier(),
              removeArgs[i].getLongIdentifier());
          if (buf.length() > 0)
          {
            buf.append(EOL);
          }
          buf.append(message);
        }
      }
    }
    if (buf.length() > 0)
    {
      returnValue = CONFLICTING_ARGS.getReturnCode();
    }
    else
    {
      returnValue = SUCCESSFUL_NOP.getReturnCode();
    }
    return returnValue;
  }
}
