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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools.status;


import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import static com.forgerock.opendj.cli.CommonArguments.*;

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
    defaultArgs.remove(secureArgsList.getPortArg());
    defaultArgs.remove(secureArgsList.getHostNameArg());
    defaultArgs.remove(verboseArg);
    defaultArgs.remove(noPropertiesFileArg);
    defaultArgs.remove(propertiesFileArg);
    noPromptArg = noPromptArgument();
    defaultArgs.add(0, noPromptArg);

    scriptFriendlyArg = scriptFriendlyArgument();
    defaultArgs.add(1, scriptFriendlyArg);

    StringArgument propertiesFileArgument = propertiesFileArgument();

    defaultArgs.add(propertiesFileArgument);
    setFilePropertiesArgument(propertiesFileArgument);

    BooleanArgument noPropertiesFileArgument = noPropertiesFileArgument();
    defaultArgs.add(noPropertiesFileArgument);
    setNoPropertiesFileArgument(noPropertiesFileArgument);

    initializeGlobalArguments(defaultArgs);

    refreshArg =
            IntegerArgument.builder("refresh")
                    .shortIdentifier('r')
                    .description(INFO_DESCRIPTION_REFRESH_PERIOD.get())
                    .lowerBound(1)
                    .valuePlaceholder(INFO_PERIOD_PLACEHOLDER.get())
                    .buildArgument();
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
    if (secureArgsList.getBindDnArg().isPresent())
    {
      return secureArgsList.getBindDnArg().getValue();
    }
    return null;
  }

  /**
   * Returns the bind DN default value.
   * @return the bind DN default value.
   */
  public String getDefaultBindDn()
  {
    return secureArgsList.getBindDnArg().getDefaultValue();
  }
}
