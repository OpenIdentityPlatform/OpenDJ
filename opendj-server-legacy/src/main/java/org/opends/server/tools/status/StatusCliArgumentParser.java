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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.tools.status;

import static com.forgerock.opendj.cli.CommonArguments.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.OutputStream;
import java.util.ArrayList;

import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * The class that is used to parse the arguments provided in the status command
 * line.
 */
public class StatusCliArgumentParser extends SecureConnectionCliParser
{
  private BooleanArgument noPromptArg;
  /** This CLI is always using the administration connector with SSL. */
  private static final boolean alwaysSSL = true;

  /** The 'refresh' argument. */
  private IntegerArgument refreshArg;
  /** The 'scriptFriendly' argument. */
  private BooleanArgument scriptFriendlyArg;

  /**
   * Creates a new instance of this argument parser with no arguments.
   *
   * @param mainClassName
   *          The fully-qualified name of the Java class that should
   *          be invoked to launch the program with which this
   *          argument parser is associated.
   */
  public StatusCliArgumentParser(String mainClassName)
  {
    super(mainClassName, INFO_STATUS_CLI_USAGE_DESCRIPTION.get(), false);
    setVersionHandler(new DirectoryServerVersionHandler());
    setShortToolDescription(REF_SHORT_DESC_STATUS.get());
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
    ArrayList<Argument> defaultArgs = new ArrayList<>(createGlobalArguments(outStream, alwaysSSL));
    defaultArgs.remove(secureArgsList.portArg);
    defaultArgs.remove(secureArgsList.hostNameArg);
    defaultArgs.remove(verboseArg);
    defaultArgs.remove(noPropertiesFileArg);
    defaultArgs.remove(propertiesFileArg);
    noPromptArg = getNoPrompt();
    defaultArgs.add(0, noPromptArg);

    scriptFriendlyArg = getScriptFriendly();
    defaultArgs.add(1, scriptFriendlyArg);

    StringArgument propertiesFileArgument = getPropertiesFile();

    defaultArgs.add(propertiesFileArgument);
    setFilePropertiesArgument(propertiesFileArgument);

    BooleanArgument noPropertiesFileArgument = getNoPropertiesFile();
    defaultArgs.add(noPropertiesFileArgument);
    setNoPropertiesFileArgument(noPropertiesFileArgument);

    initializeGlobalArguments(defaultArgs);

    refreshArg = new IntegerArgument("refresh", 'r',
        "refresh", false, true, INFO_PERIOD_PLACEHOLDER.get(),
        true, 1, false, Integer.MAX_VALUE,
        INFO_DESCRIPTION_REFRESH_PERIOD.get());
    addGlobalArgument(refreshArg, ioArgGroup);
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

  /**
   * Tells whether the user specified to have an interactive status CLI or not.
   * This method must be called after calling parseArguments.
   * @return <CODE>true</CODE> if the user specified to have an interactive
   * status CLI and <CODE>false</CODE> otherwise.
   */
  public boolean isInteractive()
  {
    return !noPromptArg.isPresent();
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
   * Returns the refresh period (in seconds) specified in the command-line.
   * If no refresh period was specified, returns -1.
   * The code assumes that the attributes have been successfully parsed.
   * @return the specified refresh period in the command-line.
   */
  public int getRefreshPeriod()
  {
    if (refreshArg.isPresent())
    {
      try
      {
        return refreshArg.getIntValue();
      }
      catch (ArgumentException ae)
      {
        // Bug
        throw new IllegalStateException("Error getting value, this method "+
            "should be called after parsing the attributes: "+ae, ae);
      }
    }
    return -1;
  }

  /**
   * Returns the bind DN explicitly provided in the command-line.
   * @return the bind DN explicitly provided in the command-line.
   * Returns <CODE>null</CODE> if no bind DN was explicitly provided.
   */
  public String getExplicitBindDn()
  {
    if (secureArgsList.bindDnArg.isPresent())
    {
      return secureArgsList.bindDnArg.getValue();
    }
    return null;
  }

  /**
   * Returns the bind DN default value.
   * @return the bind DN default value.
   */
  public String getDefaultBindDn()
  {
    return secureArgsList.bindDnArg.getDefaultValue();
  }
}
