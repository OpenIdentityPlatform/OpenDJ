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

package org.opends.guitools.statuspanel;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.io.OutputStream;
import java.util.ArrayList;

import org.opends.server.admin.client.cli.SecureConnectionCliParser;
import org.opends.server.tools.ToolConstants;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;

/**
 * The class that is used to parse the arguments provided in the status command
 * line.
 *
 */
public class StatusCliArgumentParser extends SecureConnectionCliParser
{
  private BooleanArgument noPromptArg;

  /**
   * The 'scriptFriendly' argument.
   */
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
    ArrayList<Argument> defaultArgs =
      new ArrayList<Argument>(createGlobalArguments(outStream));
    defaultArgs.remove(secureArgsList.portArg);
    defaultArgs.remove(secureArgsList.hostNameArg);
    defaultArgs.remove(verboseArg);
    defaultArgs.remove(noPropertiesFileArg);
    defaultArgs.remove(propertiesFileArg);
    noPromptArg = new BooleanArgument(
        ToolConstants.OPTION_LONG_NO_PROMPT,
        ToolConstants.OPTION_SHORT_NO_PROMPT,
        ToolConstants.OPTION_LONG_NO_PROMPT,
        INFO_DESCRIPTION_NO_PROMPT.get());
    defaultArgs.add(0, noPromptArg);
    scriptFriendlyArg = new BooleanArgument(
        "script-friendly",
        's',
        "script-friendly",
        INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());
    defaultArgs.add(1, scriptFriendlyArg);
    initializeGlobalArguments(defaultArgs);
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
   * Returns the first server bind dn explicitly provided in the enable
   * replication subcommand.
   * @return the first server bind dn explicitly provided in the enable
   * replication subcommand.  Returns -1 if no port was explicitly provided.
   */
  public String getExplicitBindDn()
  {
    String dn = null;
    if (secureArgsList.bindDnArg.isPresent())
    {
      dn = secureArgsList.bindDnArg.getValue();
    }
    return dn;
  }

  /**
   * Returns the first server bind dn default value in the enable replication
   * subcommand.
   * @return the first server bind dn default value in the enable replication
   * subcommand.
   */
  public String getDefaultBindDn()
  {
    return secureArgsList.bindDnArg.getDefaultValue();
  }
}
