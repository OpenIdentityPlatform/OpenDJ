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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.guitools.controlpanel;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.util.LinkedHashSet;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.messages.Message;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.Utils;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

/**
 * Class used to parse the arguments of the control panel command-line.
 */
public class ControlPanelArgumentParser extends ArgumentParser
{
  /**
   * The 'hostName' global argument.
   */
  private StringArgument hostNameArg = null;

  /**
   * The 'port' global argument.
   */
  private IntegerArgument portArg = null;

  /**
   * The 'bindDN' global argument.
   */
  private StringArgument bindDnArg = null;

  /**
   * The 'bindPasswordFile' global argument.
   */
  private FileBasedArgument bindPasswordFileArg = null;

  /**
   * The 'bindPassword' global argument.
   */
  private StringArgument bindPasswordArg = null;

  /**
   * The 'trustAllArg' global argument.
   */
  private BooleanArgument trustAllArg = null;

  /**
   * The 'remoteArg' global argument.
   */
  private BooleanArgument remoteArg = null;

  /**
   * Argument to specify the connect timeout.
   */
  private IntegerArgument connectTimeoutArg = null;

  private BooleanArgument showUsageArg;

  /**
   * The default constructor for this class.
   * @param mainClassName the class name of the main class for the command-line
   * that is being used.
   * @param msg the usage message.
   */
  public ControlPanelArgumentParser(String mainClassName,
      Message msg)
  {
    super(mainClassName, msg, false);
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
    hostNameArg = new StringArgument("host", OPTION_SHORT_HOST,
        OPTION_LONG_HOST, false, false, true, INFO_HOST_PLACEHOLDER.get(),
        UserData.getDefaultHostName(),
        null, INFO_DESCRIPTION_HOST.get());
    hostNameArg.setPropertyName(OPTION_LONG_HOST);
    addArgument(hostNameArg);

    portArg = new IntegerArgument("port", OPTION_SHORT_PORT, OPTION_LONG_PORT,
        false, false, true, INFO_PORT_PLACEHOLDER.get(),
        getDefaultAdministrationPort(), null,
        true, 1, true, 65535,
        INFO_DESCRIPTION_ADMIN_PORT.get());
    portArg.setPropertyName(OPTION_LONG_PORT);
    addArgument(portArg);

    bindDnArg = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
        OPTION_LONG_BINDDN, false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
        getDefaultBindDN(), null, INFO_DESCRIPTION_BINDDN.get());
    bindDnArg.setPropertyName(OPTION_LONG_BINDDN);
    addArgument(bindDnArg);

    bindPasswordArg = new StringArgument("bindPassword",
        OPTION_SHORT_BINDPWD, OPTION_LONG_BINDPWD, false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_BINDPASSWORD.get());
    bindPasswordArg.setPropertyName(OPTION_LONG_BINDPWD);
    addArgument(bindPasswordArg);

    bindPasswordFileArg = new FileBasedArgument("bindPasswordFile",
        OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_BINDPWD_FILE, false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_BINDPASSWORDFILE.get());
    bindPasswordFileArg.setPropertyName(OPTION_LONG_BINDPWD_FILE);
    addArgument(bindPasswordFileArg);

    trustAllArg = new BooleanArgument("trustAll", OPTION_SHORT_TRUSTALL,
        OPTION_LONG_TRUSTALL, INFO_DESCRIPTION_TRUSTALL.get());
    trustAllArg.setPropertyName(OPTION_LONG_TRUSTALL);
    addArgument(trustAllArg);

    remoteArg = new BooleanArgument("remote", OPTION_SHORT_REMOTE,
        OPTION_LONG_REMOTE, INFO_DESCRIPTION_REMOTE.get());
    remoteArg.setPropertyName(OPTION_LONG_REMOTE);
    addArgument(remoteArg);

    int defaultTimeout = ConnectionUtils.getDefaultLDAPTimeout();
    connectTimeoutArg = new IntegerArgument(OPTION_LONG_CONNECT_TIMEOUT,
        null, OPTION_LONG_CONNECT_TIMEOUT,
        false, false, true, INFO_TIMEOUT_PLACEHOLDER.get(),
        defaultTimeout, null,
        true, 0, false, Integer.MAX_VALUE,
        INFO_DESCRIPTION_CONNECTION_TIMEOUT.get());
    connectTimeoutArg.setPropertyName(OPTION_LONG_CONNECT_TIMEOUT);
    addArgument(connectTimeoutArg);

    showUsageArg = new BooleanArgument("help", OPTION_SHORT_HELP,
        OPTION_LONG_HELP,
        INFO_DESCRIPTION_USAGE.get());
    addArgument(showUsageArg);
    setUsageArgument(showUsageArg);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void parseArguments(String[] args) throws ArgumentException
  {
    LinkedHashSet<Message> errorMessages = new LinkedHashSet<Message>();
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
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          bindPasswordArg.getLongIdentifier(),
          bindPasswordFileArg.getLongIdentifier());
      errorMessages.add(message);
    }

    if (errorMessages.size() > 0)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(
          Utils.getMessageFromCollection(errorMessages,
              Constants.LINE_SEPARATOR));
      throw new ArgumentException(message);
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
