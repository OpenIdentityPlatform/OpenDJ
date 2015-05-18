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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.guitools.controlpanel;

import static org.opends.messages.ToolMessages.*;

import java.util.LinkedHashSet;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * Class used to parse the arguments of the control panel command-line.
 */
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
    hostNameArg = CommonArguments.getHostName(UserData.getDefaultHostName());
    addArgument(hostNameArg);

    portArg =
        CommonArguments.getPort(getDefaultAdministrationPort(),
            INFO_DESCRIPTION_ADMIN_PORT.get());
    addArgument(portArg);

    bindDnArg = CommonArguments.getBindDN(getDefaultBindDN());
    addArgument(bindDnArg);

    bindPasswordArg = CommonArguments.getBindPassword();
    addArgument(bindPasswordArg);

    bindPasswordFileArg = CommonArguments.getBindPasswordFile();
    addArgument(bindPasswordFileArg);

    trustAllArg = CommonArguments.getTrustAll();
    addArgument(trustAllArg);

    remoteArg = CommonArguments.getRemote();
    addArgument(remoteArg);

    connectTimeoutArg = CommonArguments.getConnectTimeOut();
    connectTimeoutArg.setHidden(false);
    addArgument(connectTimeoutArg);

    showUsageArg = CommonArguments.getShowUsage();
    addArgument(showUsageArg);
    setUsageArgument(showUsageArg);
  }

  /** {@inheritDoc} */
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

    if (bindPasswordArg.isPresent() && bindPasswordFileArg.isPresent())
    {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          bindPasswordArg.getLongIdentifier(),
          bindPasswordFileArg.getLongIdentifier());
      errorMessages.add(message);
    }

    if (!errorMessages.isEmpty())
    {
      throw new ArgumentException(ERR_CANNOT_INITIALIZE_ARGS.get(
          Utils.getMessageFromCollection(errorMessages, Constants.LINE_SEPARATOR)));
    }
  }

  /**
   * Returns the host name explicitly provided in the command-line.
   * @return the host name bind DN explicitly provided in the command-line.
   * Returns <CODE>null</CODE> if no bind DN was explicitly provided.
   */
  public String getExplicitHostName()
  {
    String hostName = null;
    if (hostNameArg.isPresent())
    {
      hostName = hostNameArg.getValue();
    }
    return hostName;
  }

  /**
   * Returns the administration port explicitly provided in the command-line.
   * @return the administration port explicitly provided in the command-line.
   * Returns -1 if no port was explicitly provided.
   */
  public int getExplicitPort()
  {
    int port = -1;
    if (portArg.isPresent())
    {
      try
      {
        port = portArg.getIntValue();
      }
      catch (ArgumentException ae)
      {
        throw new IllegalStateException("Error parsing data: "+ae, ae);
      }
    }
    return port;
  }

  /**
   * Returns the bind DN explicitly provided in the command-line.
   * @return the bind DN explicitly provided in the command-line.
   * Returns <CODE>null</CODE> if no bind DN was explicitly provided.
   */
  public String getExplicitBindDn()
  {
    String dn = null;
    if (bindDnArg.isPresent())
    {
      dn = bindDnArg.getValue();
    }
    return dn;
  }

  /**
   * Get the password which has to be used for the command without prompting
   * the user.  If no password was specified, return <CODE>null</CODE>.
   *
   * @return The password stored into the specified file on by the
   *         command line argument, or <CODE>null</CODE> it if not specified.
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
   * @throw {@code IllegalStateException} if the method is called before
   * parsing the arguments.
   */
  public int getConnectTimeout()
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
