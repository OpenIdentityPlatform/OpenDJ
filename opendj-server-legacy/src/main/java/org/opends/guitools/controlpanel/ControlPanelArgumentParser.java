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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel;

import static com.forgerock.opendj.cli.Utils.addErrorMessageIfArgumentsConflict;
import static org.opends.messages.ToolMessages.*;

import static com.forgerock.opendj.cli.CommonArguments.*;

import java.util.LinkedHashSet;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.Utils;
import org.opends.server.config.AdministrationConnector;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/** Class used to parse the arguments of the control panel command-line. */
public class ControlPanelArgumentParser extends ArgumentParser
{
  /** The 'hostName' global argument. */
  private StringArgument hostNameArg;
  /** The 'port' global argument. */
  private IntegerArgument portArg;

  /** The 'bindDN' global argument. */
  private StringArgument bindDnArg;
  /** The 'bindPasswordFile' global argument. */
  private FileBasedArgument bindPasswordFileArg;
  /** The 'bindPassword' global argument. */
  private StringArgument bindPasswordArg;

  /** The 'trustAllArg' global argument. */
  private BooleanArgument trustAllArg;
  /** The 'remoteArg' global argument. */
  private BooleanArgument remoteArg;
  /** Argument to specify the connect timeout. */
  private IntegerArgument connectTimeoutArg;
  private BooleanArgument showUsageArg;

  /**
   * The default constructor for this class.
   * @param mainClassName the class name of the main class for the command-line
   * that is being used.
   * @param msg the usage message.
   */
  public ControlPanelArgumentParser(String mainClassName,
      LocalizableMessage msg)
  {
    super(mainClassName, msg, false);
    setShortToolDescription(REF_SHORT_DESC_CONTROL_PANEL.get());
    setVersionHandler(new DirectoryServerVersionHandler());
  }

  /**
   * Returns the default value for the administration port.
   * @return the default value for the administration port.
   */
  public static int getDefaultAdministrationPort()
  {
    return AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
  }

  /**
   * Returns the default bind DN.
   * @return the default bind DN.
   */
  public static String getDefaultBindDN()
  {
    return "cn=Directory Manager";
  }

  /**
   * Initializes the arguments without parsing them.
   * @throws ArgumentException if there was an error creating or adding the
   * arguments.  If this occurs is likely to be a bug.
   */
  public void initializeArguments() throws ArgumentException
  {
    hostNameArg = hostNameArgument(UserData.getDefaultHostName());
    addArgument(hostNameArg);

    portArg =
        portArgument(getDefaultAdministrationPort(),
            INFO_DESCRIPTION_ADMIN_PORT.get());
    addArgument(portArg);

    bindDnArg = bindDNArgument(getDefaultBindDN());
    addArgument(bindDnArg);

    bindPasswordArg = bindPasswordArgument();
    addArgument(bindPasswordArg);

    bindPasswordFileArg = bindPasswordFileArgument();
    addArgument(bindPasswordFileArg);

    trustAllArg = trustAllArgument();
    addArgument(trustAllArg);

    remoteArg = remoteArgument();
    addArgument(remoteArg);

    connectTimeoutArg = connectTimeOutArgument();
    addArgument(connectTimeoutArg);

    showUsageArg = showUsageArgument();
    addArgument(showUsageArg);
    setUsageArgument(showUsageArg);
  }

  @Override
  public void parseArguments(String[] args) throws ArgumentException
  {
    LinkedHashSet<LocalizableMessage> errorMessages = new LinkedHashSet<>();
    try
    {
      super.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      errorMessages.add(ae.getMessageObject());
    }
    addErrorMessageIfArgumentsConflict(errorMessages, bindPasswordArg, bindPasswordFileArg);

    if (!errorMessages.isEmpty())
    {
      throw new ArgumentException(ERR_CANNOT_INITIALIZE_ARGS.get(
          Utils.getMessageFromCollection(errorMessages, Constants.LINE_SEPARATOR)));
    }
  }

  /**
   * Returns the host name explicitly provided in the command-line.
   * @return the host name bind DN explicitly provided in the command-line.
   * Returns {@code null} if no bind DN was explicitly provided.
   */
  public String getExplicitHostName()
  {
    return hostNameArg.isPresent() ? hostNameArg.getValue() : null;
  }

  /**
   * Returns the administration port explicitly provided in the command-line.
   * @return the administration port explicitly provided in the command-line.
   * Returns -1 if no port was explicitly provided.
   */
  public int getExplicitPort()
  {
    if (portArg.isPresent())
    {
      try
      {
        return portArg.getIntValue();
      }
      catch (ArgumentException ae)
      {
        throw new IllegalStateException("Error parsing data: "+ae, ae);
      }
    }
    return -1;
  }

  /**
   * Returns the bind DN explicitly provided in the command-line.
   * @return the bind DN explicitly provided in the command-line.
   * Returns {@code null} if no bind DN was explicitly provided.
   */
  public String getExplicitBindDn()
  {
    return bindDnArg.isPresent() ? bindDnArg.getValue() : null;
  }

  /**
   * Get the password which has to be used for the command without prompting
   * the user.  If no password was specified, return {@code null}.
   *
   * @return The password stored into the specified file on by the
   *         command line argument, or {@code null} if not specified.
   */
  public String getBindPassword()
  {
    return getBindPassword(bindPasswordArg, bindPasswordFileArg);
  }

  /**
   * Returns whether the user specified to trust all certificates or not.
   * @return whether the user specified to trust all certificates or not.
   */
  public boolean isTrustAll()
  {
    return trustAllArg.isPresent();
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.  The method
   * must be called after parsing the arguments.
   * @return the timeout to be used to connect in milliseconds.  Returns
   * {@code 0} if there is no timeout.
   * @throws IllegalStateException if the method is called before
   * parsing the arguments.
   */
  public int getConnectTimeout() throws IllegalStateException
  {
    try
    {
      return connectTimeoutArg.getIntValue();
    }
    catch (ArgumentException ae)
    {
      throw new IllegalStateException("Argument parser is not parsed: "+ae, ae);
    }
  }

  /**
   * Returns whether the user specified to connect to a remote server.
   * @return whether the user specified to connect to a remote server.
   */
  public boolean isRemote()
  {
    return remoteArg.isPresent();
  }
}
