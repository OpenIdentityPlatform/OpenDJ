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
package org.opends.server.admin.client.cli;

import static org.opends.server.messages.AdminMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ToolMessages.*;
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
import org.opends.admin.ads.ADSContext.AdministratorProperty;
import org.opends.admin.ads.ADSContextException.ErrorType;
import org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.ReturnCode;
import org.opends.server.tools.dsconfig.ArgumentExceptionFactory;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;

/**
 * This class is handling user Admin CLI.
 */
public class DsFrameworkCliGlobalAdmin implements DsFrameworkCliSubCommandGroup
{
  // Strings used in property help.
  private final static String DESCRIPTION_OPTIONS_TITLE =
    getMessage(MSGID_DSCFG_HELP_DESCRIPTION_OPTION);

  private final static String DESCRIPTION_OPTIONS_READ =
    getMessage(MSGID_DSCFG_HELP_DESCRIPTION_READ);

  private final static String DESCRIPTION_OPTIONS_WRITE =
    getMessage(MSGID_DSCFG_HELP_DESCRIPTION_WRITE);

  private final static String DESCRIPTION_OPTIONS_MANDATORY =
    getMessage(MSGID_DSCFG_HELP_DESCRIPTION_MANDATORY);

  private final static String DESCRIPTION_OPTIONS_SINGLE =
    getMessage(MSGID_DSCFG_HELP_DESCRIPTION_SINGLE_VALUED);

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
     * The create-admin-user subcommand.
     */
    CREATE_ADMIN_USER("create-admin-user"),

    /**
     * The delete-admin-user subcommand.
     */
    DELETE_ADMIN_USER("delete-admin-user"),

    /**
     * The list-admin-user subcommand.
     */
    LIST_ADMIN_USER("list-admin-user"),

    /**
     * The list-admin-user-properties subcommand.
     */
    LIST_ADMIN_USER_PROPERTIES("list-admin-user-properties"),

    /**
     * The get-admin-user-properties subcommand.
     */
    GET_ADMIN_USER_PROPERTIES("get-admin-user-properties"),

    /**
     * The set-admin-user-properties subcommand.
     */
    SET_ADMIN_USER_PROPERTIES("set-admin-user-properties");


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
   * The create-admin-user subcommand.
   */
  private SubCommand createAdminUserSubCmd;

  /**
   * The 'userID' argument of the 'create-admin-user' subcommand.
   */
  private StringArgument createAdminUserUserIdArg;

  /**
   * The 'set' argument of the 'create-admin-user' subcommand.
   */
  private StringArgument createAdminUserSetArg ;

  /**
   * The delete-admin-user subcommand.
   */
  private SubCommand deleteAdminUserSubCmd;

  /**
   * The 'userID' argument of the 'delete-admin-user' subcommand.
   */
  private StringArgument deleteAdminUserUserIdArg;

  /**
   * The list-admin-user subcommand.
   */
  private SubCommand listAdminUserSubCmd;

  /**
   * The get-admin-user-properties subcommand.
   */
  private SubCommand getAdminUserPropertiesSubCmd;

  /**
   * The 'userID' argument of the 'get-admin-user-properties' subcommand.
   */
  private StringArgument getAdminUserPropertiesUserIdArg;

  /**
   * The set-admin-user-properties subcommand.
   */
  private SubCommand setAdminUserPropertiesSubCmd;

  /**
   * The 'userID' argument of the 'set-admin-user-properties' subcommand.
   */
  private StringArgument setAdminUserPropertiesUserIdArg;

  /**
   * The 'set' argument of the 'set-admin-user-properties' subcommand.
   */
  private StringArgument setAdminUserPropertiesSetArg;


  /**
   * The list-admin-user-properties subcommand.
   */
  private SubCommand listAdminUserPropertiesSubCmd;

  /**
   * Association between ADSContext enum and properties.
   */
  private HashMap<AdministratorProperty, Argument> userAdminProperties;

  /**
   * List of read-only server properties.
   */
  private HashSet<AdministratorProperty> readonlyadminUserProperties;


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
    groupName = "admin-user";
    this.argParser = argParser;

    // create-admin-user subcommand.
    createAdminUserSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.CREATE_ADMIN_USER.toString(),
        MSGID_ADMIN_SUBCMD_CREATE_ADMIN_USER_DESCRIPTION);
    subCommands.add(createAdminUserSubCmd);

    createAdminUserUserIdArg = new StringArgument("userID", null,
        OPTION_LONG_USERID, false, true, OPTION_VALUE_USERID,
        MSGID_ADMIN_ARG_USERID_DESCRIPTION);
    createAdminUserSubCmd.addArgument(createAdminUserUserIdArg);

    createAdminUserSetArg = new StringArgument(OPTION_LONG_SET,
        OPTION_SHORT_SET, OPTION_LONG_SET, false, true, true,
        OPTION_VALUE_SET, null, null, MSGID_DSCFG_DESCRIPTION_PROP_VAL);
    createAdminUserSubCmd.addArgument(createAdminUserSetArg);

    // delete-admin-user subcommand.
    deleteAdminUserSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.DELETE_ADMIN_USER.toString(),
        MSGID_ADMIN_SUBCMD_DELETE_ADMIN_USER_DESCRIPTION);
    subCommands.add(deleteAdminUserSubCmd);

    deleteAdminUserUserIdArg = new StringArgument("userID", null,
        OPTION_LONG_USERID, false, true, OPTION_VALUE_USERID,
        MSGID_ADMIN_ARG_USERID_DESCRIPTION);
    deleteAdminUserSubCmd.addArgument(deleteAdminUserUserIdArg);

    // list-admin-user subcommand.
    listAdminUserSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.LIST_ADMIN_USER.toString(),
        MSGID_ADMIN_SUBCMD_LIST_ADMIN_USER_DESCRIPTION);
    subCommands.add(listAdminUserSubCmd);

    // get-admin-user-properties subcommand.
    getAdminUserPropertiesSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.GET_ADMIN_USER_PROPERTIES.toString(),
        MSGID_ADMIN_SUBCMD_GET_ADMIN_USER_PROPERTIES_DESCRIPTION);
    subCommands.add(getAdminUserPropertiesSubCmd);

    getAdminUserPropertiesUserIdArg = new StringArgument("userID", null,
        OPTION_LONG_USERID, false, true, OPTION_VALUE_USERID,
        MSGID_ADMIN_ARG_USERID_DESCRIPTION);
    getAdminUserPropertiesUserIdArg.setMultiValued(true);
    getAdminUserPropertiesSubCmd.addArgument(getAdminUserPropertiesUserIdArg);

    // set-admin-user-properties subcommand.
    setAdminUserPropertiesSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.SET_ADMIN_USER_PROPERTIES.toString(),
        MSGID_ADMIN_SUBCMD_SET_ADMIN_USER_PROPERTIES_DESCRIPTION);
    subCommands.add(setAdminUserPropertiesSubCmd);

    setAdminUserPropertiesUserIdArg = new StringArgument("userID", null,
        OPTION_LONG_USERID, false, true, OPTION_VALUE_USERID,
        MSGID_ADMIN_ARG_USERID_DESCRIPTION);
    setAdminUserPropertiesSubCmd.addArgument(setAdminUserPropertiesUserIdArg);

    setAdminUserPropertiesSetArg = new StringArgument(OPTION_LONG_SET,
        OPTION_SHORT_SET, OPTION_LONG_SET, false, true, true,
        OPTION_VALUE_SET, null, null, MSGID_DSCFG_DESCRIPTION_PROP_VAL);
    setAdminUserPropertiesSubCmd.addArgument(setAdminUserPropertiesSetArg);

    // list-admin-user-properties subcommand.
    listAdminUserPropertiesSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.LIST_ADMIN_USER_PROPERTIES.toString(),
        MSGID_ADMIN_SUBCMD_LIST_ADMIN_USER_PROPERTIES_DESCRIPTION);
    subCommands.add(listAdminUserPropertiesSubCmd);

    // Create association between ADSContext enum and server
    // properties
    // Server properties are mapped to Argument.
    userAdminProperties = new HashMap<AdministratorProperty, Argument>();
    readonlyadminUserProperties = new HashSet<AdministratorProperty>();

    /**
     * The ID used to identify the user.
     */
    {
      AdministratorProperty prop = AdministratorProperty.UID;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null,
          prop.getAttributeName(), false, false, true, "", null, null, -1);
      userAdminProperties.put(prop, arg);
    }

    /**
     * The PASSWORD used to identify the user.
     */
    {
      // TODO : Allow file based password
      AdministratorProperty prop = AdministratorProperty.PASSWORD;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null,
          prop.getAttributeName(), false, false, true, "", null, null, -1);
      userAdminProperties.put(prop, arg);
    }

    /**
     * The DESCRIPTION used to identify the user.
     */
    {
      AdministratorProperty prop = AdministratorProperty.DESCRIPTION;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null,
          prop.getAttributeName(), false, false, true, "", null, null, -1);
      userAdminProperties.put(prop, arg);
    }

    /**
     * The ADMINISTRATOR_DN used to identify the user.
     */
    {
      AdministratorProperty prop = AdministratorProperty.ADMINISTRATOR_DN;
      String attName = prop.getAttributeName();
      StringArgument arg = new StringArgument(attName, null,
          prop.getAttributeName(), false, false, true, "", null, null, -1);
      userAdminProperties.put(prop, arg);
      readonlyadminUserProperties.add(prop);
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
  public ReturnCode performSubCommand(SubCommand subCmd,
      OutputStream outStream, OutputStream errStream)
      throws ADSContextException, ArgumentException
  {

    ADSContext adsCtx = null;
    InitialLdapContext ctx = null;
    ReturnCode returnCode = ReturnCode.ERROR_UNEXPECTED;

    try
    {
      // -----------------------
      // create-admin-user subcommand.
      // -----------------------
      if (subCmd.getName().equals(createAdminUserSubCmd.getName()))
      {
        String userId  = createAdminUserUserIdArg.getValue();
        Map<AdministratorProperty, Object> map =
          mapSetOptionsToMap(createAdminUserSetArg,true);
        map.put(AdministratorProperty.UID, userId);

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return ReturnCode.CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        adsCtx.createAdministrator(map);

        returnCode = ReturnCode.SUCCESSFUL;
      }
      else
      // -----------------------
      // delete-admin-user subcommand.
      // -----------------------
      if (subCmd.getName().equals(deleteAdminUserSubCmd.getName()))
      {
        String userId  = deleteAdminUserUserIdArg.getValue();
        Map<AdministratorProperty, Object> map =
          new HashMap<AdministratorProperty, Object>();
        map.put(AdministratorProperty.UID, userId);

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return ReturnCode.CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        adsCtx.deleteAdministrator(map);

        returnCode = ReturnCode.SUCCESSFUL;
      }
      else
      // -----------------------
      // list-admin-user subcommand.
      // -----------------------
      if (subCmd.getName().equals(listAdminUserSubCmd.getName()))
      {
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return ReturnCode.CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        Set<Map<AdministratorProperty, Object>> adminUserList = adsCtx
            .readAdministratorRegistry();

        PrintStream out = new PrintStream(outStream);
        for (Map<AdministratorProperty, Object> user : adminUserList)
        {
          // print out server ID
          out.println(AdministratorProperty.UID.getAttributeName() + ": "
              + user.get(AdministratorProperty.UID));
        }
        returnCode = ReturnCode.SUCCESSFUL;
      }
      else
      // -----------------------
      // get-admin-user-properties subcommand.
      // -----------------------
      if (subCmd.getName().equals(getAdminUserPropertiesSubCmd.getName()))
      {
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return ReturnCode.CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        Set<Map<AdministratorProperty, Object>> adsAdminUserList = adsCtx
            .readAdministratorRegistry();

        LinkedList<String> userAdminUserList = getAdminUserPropertiesUserIdArg
            .getValues();
        PrintStream out = new PrintStream(outStream);
        for (Map<AdministratorProperty, Object> adminUser : adsAdminUserList)
        {
          String adminUserID = (String) adminUser
              .get(AdministratorProperty.UID);
          if (!userAdminUserList.contains(adminUserID))
          {
            continue;
          }
          // print out the Admin User ID
          out.println(AdministratorProperty.UID.getAttributeName() + ": "
              + adminUser.get(AdministratorProperty.UID));
          for (AdministratorProperty ap : adminUser.keySet())
          {
            if (ap.equals(AdministratorProperty.UID))
            {
              continue;
            }
            out.println(ap.getAttributeName() + ": " + adminUser.get(ap));
          }
          out.println();
        }
        returnCode = ReturnCode.SUCCESSFUL;
      }
      else
      // -----------------------
      // set-admin-user-properties subcommand.
      // -----------------------
      if (subCmd.getName().equals(setAdminUserPropertiesSubCmd.getName()))
      {
        Map<AdministratorProperty, Object> map =
          mapSetOptionsToMap(setAdminUserPropertiesSetArg,false);

        // if the ID is specify in the --set list, it may mean that
        // the user wants to rename the serverID
        String newServerId = (String) map.get(AdministratorProperty.UID) ;

        // replace the serverID in the map
        map.put(AdministratorProperty.UID, setAdminUserPropertiesUserIdArg
            .getValue());

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return ReturnCode.CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        adsCtx.updateAdministrator(map, newServerId);
        returnCode = ReturnCode.SUCCESSFUL;
      }
      else
      // -----------------------
      // list-admin-user-properties subcommand.
      // -----------------------
      if (subCmd.getName().equals(listAdminUserPropertiesSubCmd.getName()))
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
        table.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_NAME));
        table.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_OPTIONS));
        table.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_SYNTAX));
        table.appendHeading(getMessage(
            MSGID_CLI_HEADING_PROPERTY_DEFAULT_VALUE));
        for (AdministratorProperty adminUserProp : userAdminProperties.keySet())
        {
          if (userAdminProperties.get(adminUserProp).isHidden())
          {
            continue;
          }
          table.startRow();
          table.appendCell(adminUserProp.getAttributeName());
          table.appendCell(getPropertyOptionSummary(userAdminProperties
              .get(adminUserProp)));
          table.appendCell(adminUserProp.getAttributeSyntax());
          if (userAdminProperties.get(adminUserProp).getDefaultValue() != null)
          {
            table.appendCell(userAdminProperties.get(adminUserProp)
                .getDefaultValue());
          }
          else
          {
            table.appendCell("-");
          }
        }
        TextTablePrinter printer = new TextTablePrinter(outStream);
        table.print(printer);
        returnCode = ReturnCode.SUCCESSFUL;
      }
      // -----------------------
      // ERROR
      // -----------------------
      else
      {
        // Should never occurs: If we are here, it means that the code
        // to handle to subcommand is not yet written.
        throw new ADSContextException(ErrorType.ERROR_UNEXPECTED);
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

  /**
   * Translate a Set properties a to a MAP.
   *
   * @param propertySetArgument
   *          The input set argument.
   * @param checkMandatoryProps
   *          Indicates if we should check the presence of mandatory
   *          properties.
   * @return The created map.
   * @throws ArgumentException
   *           If error error occurs during set parsing.
   */
  private Map<AdministratorProperty, Object> mapSetOptionsToMap(
      StringArgument propertySetArgument, boolean checkMandatoryProps)
      throws ArgumentException
  {
    HashMap<AdministratorProperty, Object> map =
      new HashMap<AdministratorProperty, Object>();
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
      AdministratorProperty adminUserProperty = ADSContext
          .getAdminUSerPropFromName(propertyName);
      if (adminUserProperty == null)
      {
        int msgID = MSGID_CLI_ERROR_PROPERTY_UNRECOGNIZED;
        String message = getMessage(msgID, propertyName);
        throw new ArgumentException(msgID, message);
      }

      // Check that propName is not hidden.
      if (userAdminProperties.get(adminUserProperty).isHidden())
      {
        int msgID = MSGID_CLI_ERROR_PROPERTY_UNRECOGNIZED;
        String message = getMessage(msgID, propertyName);
        throw new ArgumentException(msgID, message);
      }

      // Check the property Syntax.
      StringBuilder invalidReason = new StringBuilder();
      Argument arg = userAdminProperties.get(adminUserProperty) ;
      if ( ! arg.valueIsAcceptable(value, invalidReason))
      {
        int msgID = MSGID_CLI_ERROR_INVALID_PROPERTY_VALUE;
        String message = getMessage(msgID, propertyName, value);
        throw new ArgumentException(msgID, message);
      }
      userAdminProperties.get(adminUserProperty).addValue(value);

      // add to the map.
      map.put(adminUserProperty, value);
    }

    // Check that all mandatory props are set.
    if (! checkMandatoryProps)
    {
      return map ;
    }

    for (AdministratorProperty s : AdministratorProperty.values())
    {
      Argument arg = userAdminProperties.get(s);
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
        int msgID = MSGID_CLI_ERROR_MISSING_PROPERTY;
        String message = getMessage(msgID, s.getAttributeName());
        throw new ArgumentException(msgID, message);
      }
      else
      {
        map.put(s, arg.getDefaultValue());
      }
    }
    return map;
  }

  //Compute the options field.
  private String getPropertyOptionSummary(Argument arg)
  {
    StringBuilder b = new StringBuilder();

    if (readonlyadminUserProperties.contains(
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

}
