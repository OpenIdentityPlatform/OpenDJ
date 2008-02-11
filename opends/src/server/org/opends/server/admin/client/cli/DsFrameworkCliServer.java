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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.cli;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.messages.AdminMessages.*;
import static org.opends.messages.DSConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.ADSContextException.ErrorType;
import org.opends.server.tools.dsconfig.ArgumentExceptionFactory;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;
/**
 * This class is handling server group CLI.
 */
public class DsFrameworkCliServer implements DsFrameworkCliSubCommandGroup
{
  // Strings used in property help.
  private final static Message DESCRIPTION_OPTIONS_TITLE =
    INFO_DSCFG_HELP_DESCRIPTION_OPTION.get();

  private final static Message DESCRIPTION_OPTIONS_READ =
    INFO_DSCFG_HELP_DESCRIPTION_READ.get();

  private final static Message DESCRIPTION_OPTIONS_WRITE =
    INFO_DSCFG_HELP_DESCRIPTION_WRITE.get();

  private final static Message DESCRIPTION_OPTIONS_MANDATORY =
    INFO_DSCFG_HELP_DESCRIPTION_MANDATORY.get();

  private final static Message DESCRIPTION_OPTIONS_SINGLE =
    INFO_DSCFG_HELP_DESCRIPTION_SINGLE_VALUED.get();

  /**
   * The subcommand Parser.
   */
  private DsFrameworkCliParser argParser;

  /**
   * The verbose argument.
   */
  private BooleanArgument verboseArg;

  /**
   * The enumeration containing the different subCommand names.
   */
  private enum SubCommandNameEnum
  {
    /**
     * The register-server subcommand.
     */
    REGISTER_SERVER("register-server"),

    /**
     * The unregister-server subcommand.
     */
    UNREGISTER_SERVER("unregister-server"),

    /**
     * The list-servers subcommand.
     */
    LIST_SERVERS("list-servers"),

    /**
     * The get-server-properties subcommand.
     */
    GET_SERVER_PROPERTIES("get-server-properties"),

    /**
     * The set-server-properties subcommand.
     */
    SET_SERVER_PROPERTIES("set-server-properties"),

    /**
     * The list-servers subcommand.
     */
    LIST_SERVER_PROPERTIES("list-server-properties");

    // String representation of the value.
    private final String name;

    // Private constructor.
    private SubCommandNameEnum(String name)
    {
      this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      return name;
    }

    // A lookup table for resolving a unit from its name.
    private static final List<String> nameToSubCmdName;
    static
    {
      nameToSubCmdName = new ArrayList<String>();

      for (SubCommandNameEnum subCmd : SubCommandNameEnum.values())
      {
        nameToSubCmdName.add(subCmd.toString());
      }
    }

    public static boolean isSubCommand(String name)
    {
      return nameToSubCmdName.contains(name);
    }
  }

  /**
   * The 'register-server' subcommand.
   */
  private SubCommand registerServerSubCmd;

  /**
   * The 'serverID' argument of the 'register-server' subcommand.
   */
  private StringArgument registerServerServerIdArg;

  /**
   * The 'serverName' argument of the 'register-server' subcommand.
   */
  private StringArgument registerServerSetArg;

  /**
   * The 'unregister-server' subcommand.
   */
  private SubCommand unregisterServerSubCmd;

  /**
   * The 'serverHost' argument of the 'unregister-server' subcommand.
   */
  private StringArgument unregisterServerServerIDArg;

  /**
   * The 'list-server-properties' subcommand.
   */
  private SubCommand listServerPropertiesSubCmd;

  /**
   * The 'list-servers' subcommand.
   */
  private SubCommand listServersSubCmd;

  /**
   * The 'get-server-properties' subcommand.
   */
  private SubCommand getServerPropertiesSubCmd;

  /**
   * The 'serverID' argument of the 'get-server-properties' subcommand.
   */
  private StringArgument getServerPropertiesServerIdArg;

  /**
   * The 'set-server-properties' subcommand.
   */
  private SubCommand setServerPropertiesSubCmd;

  /**
   * The 'serverID' argument of the 'set-server-properties' subcommand.
   */
  private StringArgument setServerPropertiesServerIdArg;

  /**
   * The 'serverName' argument of the 'set-server-properties' subcommand.
   */
  private StringArgument setServerPropertiesSetArg;

  /**
   * Association between ADSContext enum and properties.
   */
  private HashMap<ServerProperty, Argument> serverProperties;

  /**
   * List of read-only server properties.
   */
  private HashSet<ServerProperty> readonlyServerProperties;

  /**
   * The subcommand list.
   */
  private HashSet<SubCommand> subCommands = new HashSet<SubCommand>();

  /**
   * Indicates whether this subCommand should be hidden in the usage
   * information.
   */
  private boolean isHidden;

  /**
   * The subcommand group name.
   */
  private String groupName;

  /**
   * {@inheritDoc}
   */
  public Set<SubCommand> getSubCommands()
  {
    return subCommands;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isHidden()
  {
    return isHidden;
  }

  /**
   * {@inheritDoc}
   */
  public String getGroupName()
  {
    return groupName;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeCliGroup(DsFrameworkCliParser argParser,
      BooleanArgument verboseArg) throws ArgumentException
  {
    this.verboseArg = verboseArg;
    isHidden = false;
    groupName = "server";
    this.argParser = argParser;

    // list-server-properties subcommand
    listServerPropertiesSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.LIST_SERVER_PROPERTIES.toString(),
        INFO_ADMIN_SUBCMD_LIST_SERVER_PROPS_DESCRIPTION.get());
    subCommands.add(listServerPropertiesSubCmd);

    // register-server subcommand
    registerServerSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.REGISTER_SERVER.toString(),
        INFO_ADMIN_SUBCMD_REGISTER_SERVER_DESCRIPTION.get());
    subCommands.add(registerServerSubCmd);

    registerServerServerIdArg = new StringArgument("serverID", null,
        OPTION_LONG_SERVERID, false, true, INFO_SERVERID_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_SERVERID_DESCRIPTION.get());
    registerServerSubCmd.addArgument(registerServerServerIdArg);

    registerServerSetArg = new StringArgument(OPTION_LONG_SET,
        OPTION_SHORT_SET, OPTION_LONG_SET, false, true, true,
        INFO_VALUE_SET_PLACEHOLDER.get(), null, null,
        INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
    registerServerSubCmd.addArgument(registerServerSetArg);

    // unregister-server subcommand
    unregisterServerSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.UNREGISTER_SERVER.toString(),
        INFO_ADMIN_SUBCMD_UNREGISTER_SERVER_DESCRIPTION.get());
    subCommands.add(unregisterServerSubCmd);

    unregisterServerServerIDArg = new StringArgument("serverID", null,
        OPTION_LONG_SERVERID, false, true, INFO_SERVERID_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_SERVERID_DESCRIPTION.get());
    unregisterServerSubCmd.addArgument(unregisterServerServerIDArg);

    // list-servers subcommand
    listServersSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.LIST_SERVERS.toString(),
        INFO_ADMIN_SUBCMD_LIST_SERVERS_DESCRIPTION.get());
    subCommands.add(listServersSubCmd);

    // get-server-properties subcommand
    getServerPropertiesSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.GET_SERVER_PROPERTIES.toString(),
        INFO_ADMIN_SUBCMD_GET_SERVER_PROPERTIES_DESCRIPTION.get());
    subCommands.add(getServerPropertiesSubCmd);

    getServerPropertiesServerIdArg = new StringArgument("serverID", null,
        OPTION_LONG_SERVERID, false, true, INFO_SERVERID_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_SERVERID_DESCRIPTION.get());
    getServerPropertiesServerIdArg.setMultiValued(true);
    getServerPropertiesSubCmd.addArgument(getServerPropertiesServerIdArg);


    // set-server-properties subcommand
    setServerPropertiesSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.SET_SERVER_PROPERTIES.toString(),
        INFO_ADMIN_SUBCMD_SET_SERVER_PROPERTIES_DESCRIPTION.get());
    subCommands.add(setServerPropertiesSubCmd);

    setServerPropertiesServerIdArg = new StringArgument("serverID", null,
        OPTION_LONG_SERVERID, true, true, INFO_SERVERID_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_SERVERID_DESCRIPTION.get());
    setServerPropertiesSubCmd.addArgument(setServerPropertiesServerIdArg);

    setServerPropertiesSetArg = new StringArgument(OPTION_LONG_SET,
        OPTION_SHORT_SET, OPTION_LONG_SET, false, true, true,
        INFO_VALUE_SET_PLACEHOLDER.get(), null, null,
        INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
    setServerPropertiesSubCmd.addArgument(setServerPropertiesSetArg);


    // Create association between ADSContext enum and server
    // properties
    // Server properties are mapped to Argument.
    serverProperties = new HashMap<ServerProperty, Argument>();
    readonlyServerProperties = new HashSet<ServerProperty>();

    /**
     * The ID used to identify the server.
     */
    {
      ServerProperty prop = ServerProperty.ID;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null,
          prop.getAttributeName(), false, false, true, Message.raw(""), null,
          null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * The host name of the server.
     */
    {
      ServerProperty prop = ServerProperty.HOST_NAME;
      String attName = prop.getAttributeName();
      readonlyServerProperties.add(prop);
      StringArgument arg = new StringArgument(attName, null, attName, true,
          false, true, Message.raw(""), "localhost", null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * The LDAP port of the server.
     */
    {
      ServerProperty prop = ServerProperty.LDAP_PORT;
      String attName = prop.getAttributeName();
      IntegerArgument arg = new IntegerArgument(attName, null, attName, true,
          true, true, Message.raw(attName), 389, null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * The JMX port of the server.
     */
    {
      ServerProperty prop = ServerProperty.JMX_PORT;
      String attName = prop.getAttributeName();
      IntegerArgument arg = new IntegerArgument(attName, null, attName,
          false, true, Message.raw(attName), null);
      arg.setMultiValued(true);
      serverProperties.put(prop, arg);
    }

    /**
     * The JMX secure port of the server.
     */
    {
      ServerProperty prop = ServerProperty.JMXS_PORT;
      String attName = prop.getAttributeName();
      IntegerArgument arg = new IntegerArgument(attName, null, attName,
          false, true, Message.raw(attName), null);
      arg.setMultiValued(true);
      serverProperties.put(prop, arg);
    }

    /**
     * The LDAPS port of the server.
     */
    {
      ServerProperty prop = ServerProperty.LDAPS_PORT;
      String attName = prop.getAttributeName();
      IntegerArgument arg = new IntegerArgument(attName, null, attName,
          false, true, Message.raw(attName), null);
      arg.setMultiValued(true);
      serverProperties.put(prop, arg);
    }

    /**
     * The certificate used by the server.
     */
    {
      ServerProperty prop = ServerProperty.CERTIFICATE;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, attName, false,
          false, true, Message.raw(attName), null, null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * The path where the server is installed.
     */
    {
      ServerProperty prop = ServerProperty.INSTANCE_PATH;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, attName, false,
          false, true, Message.raw(attName), null, null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * The description of the server.
     */
    {
      ServerProperty prop = ServerProperty.DESCRIPTION;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, attName, false,
          false, true, Message.raw(attName), null, null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * The OS of the machine where the server is installed.
     */
    {
      ServerProperty prop = ServerProperty.HOST_OS;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, attName, false,
          false, true, Message.raw(attName), null, null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * Whether LDAP is enabled or not.
     */
    {
      ServerProperty prop = ServerProperty.LDAP_ENABLED;
      String attName = prop.getAttributeName();
      BooleanArgument arg = new BooleanArgument(attName, null, attName, null);
      arg.setDefaultValue("false");
      serverProperties.put(prop, arg);
    }

    /**
     * Whether LDAPS is enabled or not.
     */
    {
      ServerProperty prop = ServerProperty.LDAPS_ENABLED;
      String attName = prop.getAttributeName();
      BooleanArgument arg = new BooleanArgument(attName, null, attName, null);
      arg.setDefaultValue("false");
      serverProperties.put(prop, arg);
    }

    /**
     * Whether StartTLS is enabled or not.
     */
    {
      ServerProperty prop = ServerProperty.STARTTLS_ENABLED;
      String attName = prop.getAttributeName();
      BooleanArgument arg = new BooleanArgument(attName, null, attName, null);
      arg.setDefaultValue("false");
      serverProperties.put(prop, arg);
    }

    /**
     * Whether JMX is enabled or not.
     */
    {
      ServerProperty prop = ServerProperty.JMX_ENABLED;
      String attName = prop.getAttributeName();
      BooleanArgument arg = new BooleanArgument(attName, null, attName, null);
      arg.setDefaultValue("false");
      serverProperties.put(prop, arg);
    }

    /**
     * Whether JMXS is enabled or not.
     */
    {
      ServerProperty prop = ServerProperty.JMXS_ENABLED;
      String attName = prop.getAttributeName();
      BooleanArgument arg = new BooleanArgument(attName, null, attName, null);
      arg.setDefaultValue("false");
      serverProperties.put(prop, arg);
    }

    /**
     * The location of the server.
     */
    {
      ServerProperty prop = ServerProperty.LOCATION;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, attName, false,
          false, true, Message.raw(attName), null, null, null);
      serverProperties.put(prop, arg);
    }

    /**
     * The list of groups in which the server is registered.
     */
    {
      ServerProperty prop = ServerProperty.GROUPS;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, attName, false,
          true, true, Message.raw(attName), null, null, null);
      arg.setHidden(true);
      serverProperties.put(prop, arg);
    }

   /**
    * The INSTANCE_KEY_ID used to identify the server key ID.
    */
    {
      ServerProperty prop = ServerProperty.INSTANCE_KEY_ID;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, prop
          .getAttributeName(), false, false, true, Message.raw(""), null, null,
          null);
      serverProperties.put(prop, arg);
    }

    /**
     * The INSTANCE_PUBLIC_KEY_CERTIFICATE associated to the server.
     */
    {
      ServerProperty prop = ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null, prop
          .getAttributeName(), false, false, true, Message.raw(""), null, null,
          null);
      serverProperties.put(prop, arg);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSubCommand(SubCommand subCmd)
  {
    return SubCommandNameEnum.isSubCommand(subCmd.getName());
  }

  /**
   * {@inheritDoc}
   */
  public DsFrameworkCliReturnCode performSubCommand(SubCommand subCmd,
      OutputStream outStream, OutputStream errStream)
      throws ADSContextException, ArgumentException
  {

    ADSContext adsCtx = null;
    InitialLdapContext ctx = null;
    DsFrameworkCliReturnCode returnCode = ERROR_UNEXPECTED;

    try
    {
      // -----------------------
      // register-server subcommand
      // -----------------------
      if (subCmd.getName().equals(registerServerSubCmd.getName()))
      {
        String serverId ;
        Map<ServerProperty, Object> map =
          mapSetOptionsToMap(registerServerSetArg);
        if (registerServerServerIdArg.isPresent())
        {
          serverId = registerServerServerIdArg.getValue();
        }
        else
        {
          serverId = ADSContext.getServerIdFromServerProperties(map);
        }
        map.put(ServerProperty.ID, serverId);

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        adsCtx.registerServer(map);

        returnCode = SUCCESSFUL;
      }
      else
      // -----------------------
      // unregister-server subcommand
      // -----------------------
      if (subCmd.getName().equals(unregisterServerSubCmd.getName()))
      {
        returnCode = SUCCESSFUL;

        Map<ServerProperty, Object> map = new HashMap<ServerProperty, Object>();
        String serverId = null;
        if (unregisterServerServerIDArg.isPresent())
        {
          serverId = unregisterServerServerIDArg.getValue();
        }
        else
        {
          serverId = ADSContext.getServerIdFromServerProperties(map);
        }
        map.put(ServerProperty.ID,serverId);

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);

        // update groups in which server was registered
        Set<Map<ServerProperty, Object>> serverList =
          adsCtx.readServerRegistry();
        boolean found = false;
        for (Map<ServerProperty,Object> elm : serverList)
        {
          if (serverId.equals(elm.get(ServerProperty.ID)))
          {
            found = true ;
            break ;
          }
        }
        if ( ! found )
        {
          throw new ADSContextException (ErrorType.NOT_YET_REGISTERED) ;
        }

        // unregister the server
        adsCtx.unregisterServer(map);
      }
      else
      // -----------------------
      // list-servers subcommand
      // -----------------------
      if (subCmd.getName().equals(listServersSubCmd.getName()))
      {
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        Set<Map<ServerProperty, Object>> serverList = adsCtx
            .readServerRegistry();

        PrintStream out = new PrintStream(outStream);
        for (Map<ServerProperty, Object> server : serverList)
        {
          // print out server ID
          out.println(ServerProperty.ID.getAttributeName() + ": "
              + server.get(ServerProperty.ID));
        }
        returnCode = SUCCESSFUL;
      }
      else
      // -----------------------
      // get-server-properties subcommand
      // -----------------------
      if (subCmd.getName().equals(getServerPropertiesSubCmd.getName()))
      {
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        Set<Map<ServerProperty, Object>> adsServerList = adsCtx
            .readServerRegistry();

        LinkedList<String> userServerList = getServerPropertiesServerIdArg
            .getValues();
        PrintStream out = new PrintStream(outStream);
        for (Map<ServerProperty, Object> server : adsServerList)
        {
          String serverID = (String) server.get(ServerProperty.ID);
          if (!userServerList.contains(serverID))
          {
            continue;
          }
          // print out server ID
          out.println(ServerProperty.ID.getAttributeName() + ": "
              + server.get(ServerProperty.ID));
          for (ServerProperty sp : server.keySet())
          {
            if (sp.equals(ServerProperty.ID))
            {
              continue;
            }
            out.println(sp.getAttributeName() + ": " + server.get(sp));
          }
          out.println();
        }
        returnCode = SUCCESSFUL;
      }
      else
      // -----------------------
      // set-server-properties subcommand
      // -----------------------
      if (subCmd.getName().equals(setServerPropertiesSubCmd.getName()))
      {
        Map<ServerProperty, Object> map =
          mapSetOptionsToMap(setServerPropertiesSetArg);

        // if the ID is specify in the --set list, it may mean that
        // the user wants to rename the serverID
        String newServerId = (String) map.get(ServerProperty.ID) ;

        // replace the serverID in the map
        map.put(ServerProperty.ID, setServerPropertiesServerIdArg.getValue());

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        adsCtx.updateServer(map, newServerId);
        returnCode = SUCCESSFUL;
      }
      else
      // -----------------------
      // list-server-properties subcommand
      // -----------------------
      if (subCmd.getName().equals(listServerPropertiesSubCmd.getName()))
      {
        PrintStream out = new PrintStream(outStream);
        out.println(DESCRIPTION_OPTIONS_TITLE);
        out.println();
        out.print(" r -- ");
        out.println(DESCRIPTION_OPTIONS_READ);
        out.print(" w -- ");
        out.println(DESCRIPTION_OPTIONS_WRITE);
        out.print(" m -- ");
        out.println(DESCRIPTION_OPTIONS_MANDATORY);
        out.print(" s -- ");
        out.println(DESCRIPTION_OPTIONS_SINGLE);
        out.println();

        TableBuilder table = new TableBuilder();
        table.appendHeading(INFO_DSCFG_HEADING_PROPERTY_NAME.get());
        table.appendHeading(INFO_DSCFG_HEADING_PROPERTY_OPTIONS.get());
        table.appendHeading(INFO_DSCFG_HEADING_PROPERTY_SYNTAX.get());
        table.appendHeading(INFO_CLI_HEADING_PROPERTY_DEFAULT_VALUE.get());
        for (ServerProperty serverProp : serverProperties.keySet())
        {
          if (serverProperties.get(serverProp).isHidden())
          {
            continue;
          }
          table.startRow();
          table.appendCell(serverProp.getAttributeName());
          table.appendCell(getPropertyOptionSummary(serverProperties
              .get(serverProp)));
          table.appendCell(serverProp.getAttributeSyntax());
          if (serverProperties.get(serverProp).getDefaultValue() != null)
          {
            table.appendCell(serverProperties.get(serverProp)
                .getDefaultValue());
          }
          else
          {
            table.appendCell("-");
          }
        }
        TextTablePrinter printer = new TextTablePrinter(outStream);
        table.print(printer);
        returnCode = SUCCESSFUL;
      }
      else
      {
        // Should never occurs: If we are here, it means that the code
        // to
        // handle to subcommand is not yet written.
        returnCode = ERROR_UNEXPECTED;
      }
    }
    catch (ADSContextException e)
    {
      if (ctx != null)
      {
        try
        {
          ctx.close();
        }
        catch (NamingException x)
        {
        }
      }
      throw e;
    }

    // Close the connection, if needed
    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (NamingException x)
      {
      }
    }

    // return part
    return returnCode;
  }

  // Compute the options field.
  private String getPropertyOptionSummary(Argument arg)
  {
    StringBuilder b = new StringBuilder();

    if (readonlyServerProperties.contains(
        ADSContext.getServerPropFromName(arg.getName())))
    {
      b.append("r-"); //$NON-NLS-1$
    }
    else
    {
      b.append("rw"); //$NON-NLS-1$
    }

    if (arg.isRequired())
    {
      b.append('m');
    }
    else
    {
      b.append('-');
    }

    if (arg.isMultiValued())
    {
      b.append('-');
    }
    else
    {
      b.append('s');
    }
    return b.toString();
  }

  /**
   * Translate a Set properties a to a MAP.
   *
   * @param propertySetArgument
   *          The input set argument.
   * @return The created map.
   * @throws ArgumentException
   *           If error error occurs during set parsing.
   */
  private Map<ServerProperty, Object> mapSetOptionsToMap(
      StringArgument propertySetArgument) throws ArgumentException
  {
    HashMap<ServerProperty, Object> map = new HashMap<ServerProperty, Object>();
    for (String m : propertySetArgument.getValues())
    {
      // Parse the property "property:value".
      int sep = m.indexOf(':');

      if (sep < 0)
      {
        throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(m);
      }

      if (sep == 0)
      {
        throw ArgumentExceptionFactory.missingNameInPropertyArgument(m);
      }

      String propertyName = m.substring(0, sep);
      String value = m.substring(sep + 1, m.length());
      if (value.length() == 0)
      {
        throw ArgumentExceptionFactory.missingValueInPropertyArgument(m);
      }

      // Check that propName is a known prop.
      ServerProperty serverProperty = ADSContext
          .getServerPropFromName(propertyName);
      if (serverProperty == null)
      {
        Message message = ERR_CLI_ERROR_PROPERTY_UNRECOGNIZED.get(propertyName);
        throw new ArgumentException(message);
      }

      // Check that propName is not hidden.
      if (serverProperties.get(serverProperty).isHidden())
      {
        Message message = ERR_CLI_ERROR_PROPERTY_UNRECOGNIZED.get(propertyName);
        throw new ArgumentException(message);
      }

      // Check the property Syntax.
      MessageBuilder invalidReason = new MessageBuilder();
      Argument arg = serverProperties.get(serverProperty) ;
      if ( ! arg.valueIsAcceptable(value, invalidReason))
      {
        Message message =
            ERR_CLI_ERROR_INVALID_PROPERTY_VALUE.get(propertyName, value);
        throw new ArgumentException(message);
      }
      serverProperties.get(serverProperty).addValue(value);

      // add to the map.
      map.put(serverProperty, value);
    }

    // Check that all mandatory props are set.
    for (ServerProperty s : ServerProperty.values())
    {
      Argument arg = serverProperties.get(s);
      if (arg.isHidden())
      {
        continue;
      }
      if (map.containsKey(s))
      {
        continue ;
      }
      if ( ! arg.isRequired())
      {
        continue ;
      }

      // If we are here, it means that the argument is required
      // but not yet is the map. Check if we have a default value.
      if (arg.getDefaultValue() == null)
      {
        Message message =
            ERR_CLI_ERROR_MISSING_PROPERTY.get(s.getAttributeName());
        throw new ArgumentException(message);
      }
      else
      {
        map.put(s, arg.getDefaultValue());
      }
    }
    return map;
  }
}
