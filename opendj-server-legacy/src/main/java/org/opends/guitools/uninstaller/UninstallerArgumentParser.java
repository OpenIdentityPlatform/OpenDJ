/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.uninstaller;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.AdminToolMessages.*;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.quicksetup.UserData;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;

/** Class used to parse and populate the arguments of the Uninstaller. */
public class UninstallerArgumentParser extends SecureConnectionCliParser
{
  private BooleanArgument cliArg;
  private BooleanArgument noPromptArg;
  BooleanArgument forceOnErrorArg;
  private BooleanArgument quietArg;
  private BooleanArgument removeAllArg;
  private BooleanArgument removeServerLibrariesArg;
  private BooleanArgument removeDatabasesArg;
  private BooleanArgument removeLogFilesArg;
  private BooleanArgument removeConfigurationFilesArg;
  private BooleanArgument removeBackupFilesArg;
  private BooleanArgument removeLDIFFilesArg;

  private StringArgument referencedHostNameArg;

  /** This CLI is always using the administration connector with SSL. */
  private final boolean alwaysSSL = true;

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
      LocalizableMessage toolDescription, boolean longArgumentsCaseSensitive)
  {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
    setShortToolDescription(REF_SHORT_DESC_UNINSTALL.get());
    setVersionHandler(new DirectoryServerVersionHandler());
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
    LinkedHashSet<Argument> args = new LinkedHashSet<>();
    cliArg = cliArgument();
    args.add(cliArg);

    removeAllArg =
            BooleanArgument.builder("remove-all")
                    .shortIdentifier('a')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_REMOVE_ALL.get())
                    .buildArgument();
    args.add(removeAllArg);

    removeServerLibrariesArg =
            BooleanArgument.builder("server-libraries")
                    .shortIdentifier('l')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_REMOVE_SERVER_LIBRARIES.get())
                    .buildArgument();
    args.add(removeServerLibrariesArg);

    removeDatabasesArg =
            BooleanArgument.builder("databases")
                    .shortIdentifier('d')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_REMOVE_DATABASES.get())
                    .buildArgument();
    args.add(removeDatabasesArg);

    removeLogFilesArg =
            BooleanArgument.builder("log-files")
                    .shortIdentifier('L')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_REMOVE_LOG_FILES.get())
                    .buildArgument();
    args.add(removeLogFilesArg);

    removeConfigurationFilesArg =
            BooleanArgument.builder("configuration-files")
                    .shortIdentifier('c')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_REMOVE_CONFIGURATION_FILES.get())
                    .buildArgument();
    args.add(removeConfigurationFilesArg);

    removeBackupFilesArg =
            BooleanArgument.builder("backup-files")
                    .shortIdentifier('b')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_REMOVE_BACKUP_FILES.get())
                    .buildArgument();
    args.add(removeBackupFilesArg);

    removeLDIFFilesArg =
            BooleanArgument.builder("ldif-files")
                    .shortIdentifier('e')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_REMOVE_LDIF_FILES.get())
                    .buildArgument();
    args.add(removeLDIFFilesArg);

    noPromptArg = noPromptArgument();
    args.add(noPromptArg);

    forceOnErrorArg =
            BooleanArgument.builder("forceOnError")
                    .shortIdentifier('f')
                    .description(INFO_UNINSTALLDS_DESCRIPTION_FORCE.get("--" + noPromptArg.getLongIdentifier()))
                    .buildArgument();
    args.add(forceOnErrorArg);

    quietArg = quietArgument();
    args.add(quietArg);

    final List<Argument> defaultArgs = new ArrayList<>(createGlobalArguments(outStream, alwaysSSL));
    secureArgsList.createVisibleAdminUidArgument(INFO_DESCRIPTION_ADMIN_UID.get());
    int index = defaultArgs.indexOf(secureArgsList.getBindDnArg());
    if (index != -1)
    {
      defaultArgs.add(index, secureArgsList.getAdminUidArg());
      defaultArgs.remove(secureArgsList.getBindDnArg());
    }
    else
    {
      defaultArgs.add(secureArgsList.getAdminUidArg());
    }
    defaultArgs.remove(secureArgsList.getHostNameArg());
    defaultArgs.remove(secureArgsList.getPortArg());
    referencedHostNameArg =
            StringArgument.builder(OPTION_LONG_REFERENCED_HOST_NAME)
                    .shortIdentifier(OPTION_SHORT_HOST)
                    .description(INFO_DESCRIPTION_REFERENCED_HOST.get())
                    .defaultValue(UserData.getDefaultHostName())
                    .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                    .buildArgument();
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
    return !noPromptArg.isPresent();
  }

  /**
   * Tells whether the user specified to force on non critical error in the non
   * interactive mode.
   * @return <CODE>true</CODE> if the user specified to force the uninstall in
   * non critical error and <CODE>false</CODE> otherwise.
   */
  public boolean isForceOnError()
  {
    return forceOnErrorArg.isPresent();
  }

  /**
   * Tells whether the user specified to have a quiet uninstall or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have a quiet
   * uninstall and <CODE>false</CODE> otherwise.
   */
  public boolean isQuiet()
  {
    return quietArg.isPresent();
  }

  /**
   * Tells whether the user specified to have a verbose uninstall or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have a verbose
   * uninstall and <CODE>false</CODE> otherwise.
   */
  @Override
  public boolean isVerbose()
  {
    return verboseArg.isPresent();
  }

  /**
   * Tells whether the user specified to remove all files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove all files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeAll()
  {
    return removeAllArg.isPresent();
  }

  /**
   * Tells whether the user specified to remove library files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove library files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeServerLibraries()
  {
    return removeServerLibrariesArg.isPresent();
  }

  /**
   * Tells whether the user specified to remove database files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove database files
   * and <CODE>false</CODE> otherwise.
   */
  public boolean removeDatabases()
  {
    return removeDatabasesArg.isPresent();
  }

  /**
   * Tells whether the user specified to remove configuration files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove configuration
   * files and <CODE>false</CODE> otherwise.
   */
  public boolean removeConfigurationFiles()
  {
    return removeConfigurationFilesArg.isPresent();
  }

  /**
   * Tells whether the user specified to remove backup files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove backup files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeBackupFiles()
  {
    return removeBackupFilesArg.isPresent();
  }

  /**
   * Tells whether the user specified to remove LDIF files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove LDIF files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeLDIFFiles()
  {
    return removeLDIFFilesArg.isPresent();
  }

  /**
   * Tells whether the user specified to remove log files.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to remove log files and
   * <CODE>false</CODE> otherwise.
   */
  public boolean removeLogFiles()
  {
    return removeLogFilesArg.isPresent();
  }

  /**
   * Returns the default Administrator UID value.
   * @return the default Administrator UID value.
   */
  public String getDefaultAdministratorUID()
  {
    return secureArgsList.getAdminUidArg().getDefaultValue();
  }

  /**
   * Returns the Host name to update remote references as provided in the
   * command-line.
   * @return the Host name to update remote references as provided in the
   * command-line.
   */
  public String getReferencedHostName()
  {
    if (referencedHostNameArg.isPresent())
    {
      return referencedHostNameArg.getValue();
    }
    return null;
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
   * @param buf the LocalizableMessageBuilder to write the error messages.
   * @return return code.
   */
  @Override
  public int validateGlobalOptions(LocalizableMessageBuilder buf)
  {
    if (!noPromptArg.isPresent() && forceOnErrorArg.isPresent())
    {
      final LocalizableMessage message = ERR_UNINSTALL_FORCE_REQUIRES_NO_PROMPT.get(
          forceOnErrorArg.getLongIdentifier(), noPromptArg.getLongIdentifier());
      if (buf.length() > 0)
      {
        buf.append(LINE_SEPARATOR);
      }
      buf.append(message);
    }

    appendErrorMessageIfArgumentsConflict(buf, removeAllArg, removeServerLibrariesArg);
    appendErrorMessageIfArgumentsConflict(buf, removeAllArg, removeDatabasesArg);
    appendErrorMessageIfArgumentsConflict(buf, removeAllArg, removeLogFilesArg);
    appendErrorMessageIfArgumentsConflict(buf, removeAllArg, removeConfigurationFilesArg);
    appendErrorMessageIfArgumentsConflict(buf, removeAllArg, removeBackupFilesArg);
    appendErrorMessageIfArgumentsConflict(buf, removeAllArg, removeLDIFFilesArg);
    super.validateGlobalOptions(buf);
    if (buf.length() > 0)
    {
      return ReturnCode.CONFLICTING_ARGS.get();
    }
    return ReturnCode.SUCCESS.get();
  }

  /**
   * Returns whether the command was launched in CLI mode or not.
   * @return <CODE>true</CODE> if the command was launched to use CLI mode and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCli()
  {
    return cliArg.isPresent();
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
