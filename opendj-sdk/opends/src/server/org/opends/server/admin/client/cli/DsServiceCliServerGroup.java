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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.cli;

import static org.opends.server.messages.AdminMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.ldap.Rdn;


import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ADSContext.ServerGroupProperty;
import org.opends.server.admin.client.cli.DsServiceCliReturnCode.ReturnCode;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;

/**
 * This class is handling server group CLI.
 */
public class DsServiceCliServerGroup implements DsServiceCliSubCommandGroup
{

  /**
   * End Of Line.
   */
  private String EOL = System.getProperty("line.separator");

  /**
   * The subcommand Parser.
   */
  SubCommandArgumentParser argParser ;

  /**
   * The verbose argument.
   */
  BooleanArgument verboseArg ;

  /**
   * The enumeration containing the different subCommand names.
   */
  private enum SubCommandNameEnum
  {
    /**
     * The create-group subcommand.
     */
    CREATE_GROUP("create-group"),

    /**
     * The delete-group subcommand.
     */
    DELETE_GROUP("delete-group"),

    /**
     * The modify-group subcommand.
     */
    MODIFY_GROUP("modify-group"),

    /**
     * The list-groups subcommand.
     */
    LIST_GROUPS("list-groups"),

    /**
     * The list-members subcommand.
     */
    LIST_MEMBERS("list-members"),

    /**
     * The list-membership subcommand.
     */
    LIST_MEMBERSHIP("list-membership"),

    /**
     * The add-to-group subcommand.
     */
    ADD_TO_GROUP("add-to-group"),

    /**
     * The remove-from-group subcommand.
     */
    REMOVE_FROM_GROUP("remove-from-group");

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
    private static final List<String> nameToSubCmdName ;
    static
    {
      nameToSubCmdName = new ArrayList<String>();

      for (SubCommandNameEnum subCmd : SubCommandNameEnum.values())
      {
        nameToSubCmdName.add(subCmd.toString());
      }
    }
    public static boolean  isSubCommand(String name)
    {
      return nameToSubCmdName.contains(name);
    }
  }

  /**
   * The 'create-group' subcommand.
   */
  public SubCommand createGoupSubCmd;

  /**
   * The 'description' argument of the 'create-group' subcommand.
   */
  private StringArgument createGoupDescriptionArg;

  /**
   * The 'modify-group' subcommand.
   */
  private SubCommand modifyGroupSubCmd;

  /**
   * The 'description' argument of the 'modify-group' subcommand.
   */
  private StringArgument modifyGroupDescriptionArg;

  /**
   * The 'group-id' argument of the 'modify-group' subcommand.
   */
  private StringArgument modifyGroupGroupIdArg;

  /**
   * The 'delete-group' subcommand.
   */
  private SubCommand deleteGroupSubCmd;

  /**
   * The 'list-group' subcommand.
   */
  private SubCommand listGroupSubCmd;

  /**
   * The 'add-to-group' subcommand.
   */
  private SubCommand addToGroupSubCmd;

  /**
   * The 'member-id' argument of the 'add-to-group' subcommand.
   */
  private StringArgument addToGoupMemberIdArg;

  /**
   * The 'remove-from-group' subcommand.
   */
  private SubCommand removeFromGroupSubCmd;

  /**
   * The 'member-id' argument of the 'remove-from-group' subcommand.
   */
  private StringArgument removeFromGoupMemberIdArg;

  /**
   * The 'list-members' subcommand.
   */
  private SubCommand listMembersSubCmd;

  /**
   * The 'mlist-membership' subcommand.
   */
  private SubCommand listMembershipSubCmd;

  /**
   * Association between ADSContext enum and display field.
   */
  private HashMap<ServerGroupProperty, String> attributeDisplayName;

  /**
   * Get the display attribute name for a given attribute.
   * @param prop The server prperty
   * @return the display attribute name for a given attribute
   */
  public String getAttributeDisplayName(ServerGroupProperty prop)
  {
    return attributeDisplayName.get(prop);
  }
  /**
   * {@inheritDoc}
   */
  public void initializeCliGroup(SubCommandArgumentParser argParser,
      BooleanArgument verboseArg)
      throws ArgumentException
  {
    this.verboseArg = verboseArg ;

    // Create-group subcommand
    createGoupSubCmd = new SubCommand(argParser, SubCommandNameEnum.CREATE_GROUP
        .toString(), true, 1, 1, OPERAND_GROUPID,
        MSGID_ADMIN_SUBCMD_CREATE_GROUP_DESCRIPTION);
    createGoupDescriptionArg = new StringArgument("description",
        OPTION_SHORT_DESCRIPTION, OPTION_LONG_DESCRIPTION, false, false,
        true, OPTION_VALUE_DESCRIPTION, "", null,
        MSGID_ADMIN_ARG_DESCRIPTION_DESCRIPTION);
    createGoupSubCmd.addArgument(createGoupDescriptionArg);

    // modify-group
    modifyGroupSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.MODIFY_GROUP.toString(), true, 1, 1,
        OPERAND_GROUPID, MSGID_ADMIN_SUBCMD_MODIFY_GROUP_DESCRIPTION);
    modifyGroupDescriptionArg = new StringArgument("new-description",
        OPTION_SHORT_DESCRIPTION, OPTION_LONG_DESCRIPTION, false, false,
        true, OPTION_VALUE_DESCRIPTION, "", null,
        MSGID_ADMIN_ARG_NEW_DESCRIPTION_DESCRIPTION);
    modifyGroupSubCmd.addArgument(modifyGroupDescriptionArg);
    modifyGroupGroupIdArg = new StringArgument("new-groupID",
        OPTION_SHORT_GROUPID, OPTION_LONG_GROUPID, false, false, true,
        OPTION_VALUE_GROUPID, "", null,
        MSGID_ADMIN_ARG_NEW_GROUPID_DESCRIPTION);
    modifyGroupSubCmd.addArgument(modifyGroupGroupIdArg);

    // delete-group
    deleteGroupSubCmd = new SubCommand(argParser,SubCommandNameEnum.DELETE_GROUP
        .toString(), true, 1, 1, OPERAND_GROUPID,
        MSGID_ADMIN_SUBCMD_DELETE_GROUP_DESCRIPTION);

    // list-groups
    listGroupSubCmd = new SubCommand(argParser, "list-groups",
        MSGID_ADMIN_SUBCMD_LIST_GROUPS_DESCRIPTION);

    // add-to-group
    addToGroupSubCmd = new SubCommand(argParser, SubCommandNameEnum.ADD_TO_GROUP
        .toString(), true, 1, 1, OPERAND_GROUPID,
        MSGID_ADMIN_SUBCMD_ADD_TO_GROUP_DESCRIPTION);
    addToGoupMemberIdArg = new StringArgument("memberID", OPTION_SHORT_MEMBERID,
        OPTION_LONG_MEMBERID, false, false, true, OPTION_VALUE_MEMBERID, "",
        null, MSGID_ADMIN_ARG_ADD_MEMBERID_DESCRIPTION);
    addToGroupSubCmd.addArgument(addToGoupMemberIdArg);

    // remove-from-group
    removeFromGroupSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.REMOVE_FROM_GROUP.toString(), true, 1, 1,
        OPERAND_GROUPID, MSGID_ADMIN_SUBCMD_REMOVE_FROM_GROUP_DESCRIPTION);
    removeFromGoupMemberIdArg = new StringArgument("memberID",
        OPTION_SHORT_MEMBERID, OPTION_LONG_MEMBERID, false, false, true,
        OPTION_VALUE_MEMBERID, "", null,
        MSGID_ADMIN_ARG_REMOVE_MEMBERID_DESCRIPTION);
    removeFromGroupSubCmd.addArgument(removeFromGoupMemberIdArg);

    // list-members
    listMembersSubCmd = new SubCommand(argParser,SubCommandNameEnum.LIST_MEMBERS
        .toString(), true, 1, 1, OPERAND_GROUPID,
        MSGID_ADMIN_SUBCMD_LIST_MEMBERS_DESCRIPTION);

    // list-membership
    listMembershipSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.LIST_MEMBERSHIP.toString(), true, 1, 1,
        OPERAND_MEMBERID, MSGID_ADMIN_SUBCMD_LIST_MEMBERSHIP_DESCRIPTION);

    // Create association between ADSContext enum and display field
    attributeDisplayName = new HashMap<ServerGroupProperty, String>();
    attributeDisplayName.put(ServerGroupProperty.UID, OPTION_LONG_GROUPID);
    attributeDisplayName.put(ServerGroupProperty.DESCRIPTION,
        OPTION_LONG_DESCRIPTION);
    attributeDisplayName.put(ServerGroupProperty.MEMBERS,
        OPTION_LONG_MEMBERID);
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
  public ReturnCode performSubCommand(ADSContext adsContext, SubCommand subCmd,
      OutputStream outStream, OutputStream errStream)
      throws ADSContextException
  {
    // -----------------------
    // create-group subcommand
    // -----------------------
    if (subCmd.getName().equals(createGoupSubCmd.getName()))
    {
      String groupId = subCmd.getTrailingArguments().get(0);
      HashMap<ServerGroupProperty, Object> serverGroupProperties =
        new HashMap<ServerGroupProperty, Object>();

      // get the GROUP_ID
      serverGroupProperties.put(ServerGroupProperty.UID, groupId);

      // get the Description
      if (createGoupDescriptionArg.isPresent())
      {
        serverGroupProperties.put(ServerGroupProperty.DESCRIPTION,
            createGoupDescriptionArg.getValue());
      }

      // Create the group
      adsContext.createServerGroup(serverGroupProperties);
      return ReturnCode.SUCCESSFUL;
    }
    // -----------------------
    // delete-group subcommand
    // -----------------------
    else if (subCmd.getName().equals(deleteGroupSubCmd.getName()))
    {
      String groupId = subCmd.getTrailingArguments().get(0);
      HashMap<ServerGroupProperty, Object> serverGroupProperties =
        new HashMap<ServerGroupProperty, Object>();

      // get the GROUP_ID
      serverGroupProperties.put(ServerGroupProperty.UID, groupId);

      // Delete the group
      adsContext.deleteServerGroup(serverGroupProperties);
      return ReturnCode.SUCCESSFUL;
    }
    // -----------------------
    // list-groups subcommand
    // -----------------------
    else if (subCmd.getName().equals(listGroupSubCmd.getName()))
    {
      Set<Map<ServerGroupProperty, Object>> result = adsContext
          .readServerGroupRegistry();
      StringBuffer buffer = new StringBuffer();

      // if not verbose mode, print group name (1 per line)
      if (! verboseArg.isPresent())
      {
        for (Map<ServerGroupProperty, Object> groupProps : result)
        {
          // Get the group name
          buffer.append(groupProps.get(ServerGroupProperty.UID));
          buffer.append(EOL);
        }
      }
      else
      {
        // Look for the max group identifier length
        int uidLength = 0 ;
        for (ServerGroupProperty sgp : ServerGroupProperty.values())
        {
          int cur = attributeDisplayName.get(sgp).toString().length();
          if (cur > uidLength)
          {
            uidLength = cur;
          }
        }
        uidLength++;

        for (Map<ServerGroupProperty, Object> groupProps : result)
        {
          // Get the group name
          buffer.append(attributeDisplayName.get(ServerGroupProperty.UID));
          // add space
          int curLen = attributeDisplayName.get(ServerGroupProperty.UID)
              .length();
          for (int i = curLen; i < uidLength; i++)
          {
            buffer.append(" ");
          }
          buffer.append(": ");
          buffer.append(groupProps.get(ServerGroupProperty.UID));
          buffer.append(EOL);

          // Write other props
          for (ServerGroupProperty propName : ServerGroupProperty.values())
          {
            if (propName.compareTo(ServerGroupProperty.UID) == 0)
            {
              // We have already displayed the group Id
              continue;
            }
            buffer.append(attributeDisplayName.get(propName));
            // add space
            curLen = attributeDisplayName.get(propName).length();
            for (int i = curLen; i < uidLength; i++)
            {
              buffer.append(" ");
            }
            buffer.append(": ");

            if (propName.compareTo(ServerGroupProperty.MEMBERS) == 0)
            {
              Set atts = (Set) groupProps.get(propName);
              if (atts != null)
              {
                boolean indent = false;
                for (Object att : atts)
                {
                  if (indent)
                  {
                    buffer.append(EOL);
                    for (int i = 0; i < uidLength + 2; i++)
                    {
                      buffer.append(" ");
                    }
                  }
                  else
                  {
                    indent = true;
                  }
                  buffer.append(att.toString().substring(3));
                }
              }
            }
            else
            {
              if (groupProps.get(propName) != null)
              {
                buffer.append(groupProps.get(propName));
              }
            }
            buffer.append(EOL);
          }
          buffer.append(EOL);
        }
      }
      try
      {
        outStream.write(buffer.toString().getBytes());
      }
      catch (IOException e)
      {
      }
      return ReturnCode.SUCCESSFUL;
    }
    // -----------------------
    // modify-group subcommand
    // -----------------------
    else if (subCmd.getName().equals(modifyGroupSubCmd.getName()))
    {
      String groupId = subCmd.getTrailingArguments().get(0);
      HashMap<ServerGroupProperty, Object> serverGroupProperties =
        new HashMap<ServerGroupProperty, Object>();
      HashSet<ServerGroupProperty> serverGroupPropertiesToRemove =
        new HashSet<ServerGroupProperty>();

      Boolean updateRequired = false;
      Boolean removeRequired = false;
      // get the GROUP_ID
      if (modifyGroupGroupIdArg.isPresent())
      {
        // rename the entry !
        serverGroupProperties.put(ServerGroupProperty.UID,
            modifyGroupGroupIdArg.getValue());
        updateRequired = true;
      }
      else
      {
        serverGroupProperties.put(ServerGroupProperty.UID, groupId) ;
      }


      // get the Description
      if (modifyGroupDescriptionArg.isPresent())
      {
        String newDesc = modifyGroupDescriptionArg.getValue();
        if (newDesc.length() == 0)
        {
          serverGroupPropertiesToRemove.add(ServerGroupProperty.DESCRIPTION);
          removeRequired = true;
        }
        else
        {
          serverGroupProperties.put(ServerGroupProperty.DESCRIPTION,
              modifyGroupDescriptionArg.getValue());
          updateRequired = true;
        }
      }


      // Update the server group
      if (updateRequired)
      {
        adsContext.updateServerGroup(groupId, serverGroupProperties);
      }
      if (removeRequired)
      {
        adsContext.removeServerGroupProp(groupId,
            serverGroupPropertiesToRemove);
      }

      if (updateRequired || removeRequired)
      {
        return ReturnCode.SUCCESSFUL;
      }
      else
      {
       return ReturnCode.SUCCESSFUL_NOP;
      }
    }
    // -----------------------
    // add-to-group subcommand
    // -----------------------
    else if (subCmd.getName().equals(addToGroupSubCmd.getName()))
    {
      String groupId = subCmd.getTrailingArguments().get(0);
      HashMap<ServerGroupProperty, Object> serverGroupProperties =
        new HashMap<ServerGroupProperty, Object>();

      // get the current member list
      Set<String> memberList = adsContext.getServerGroupMemberList(groupId);
      if (memberList == null)
      {
        memberList = new HashSet<String>();
      }
      String newMember = "cn="
          + Rdn.escapeValue(addToGoupMemberIdArg.getValue());
      if (memberList.contains(newMember))
      {
        return ReturnCode.ALREADY_REGISTERED;
      }
      memberList.add(newMember);
      serverGroupProperties.put(ServerGroupProperty.MEMBERS, memberList);

      // Update the server group
      adsContext.updateServerGroup(groupId, serverGroupProperties);

      return ReturnCode.SUCCESSFUL;
    }
    // -----------------------
    // remove-from-group subcommand
    // -----------------------
    else if (subCmd.getName().equals(removeFromGroupSubCmd.getName()))
    {
      String groupId = subCmd.getTrailingArguments().get(0);
      HashMap<ServerGroupProperty, Object> serverGroupProperties =
        new HashMap<ServerGroupProperty, Object>();

      // get the current member list
      Set<String> memberList = adsContext.getServerGroupMemberList(groupId);
      if (memberList == null)
      {
        return ReturnCode.NOT_YET_REGISTERED;
      }
      String memberToRemove = "cn="
          + Rdn.escapeValue(removeFromGoupMemberIdArg.getValue());
      if (!memberList.contains(memberToRemove))
      {
        return ReturnCode.NOT_YET_REGISTERED;
      }

      memberList.remove(memberToRemove);
      serverGroupProperties.put(ServerGroupProperty.MEMBERS, memberList);

      // Update the server group
      adsContext.updateServerGroup(groupId, serverGroupProperties);

      return ReturnCode.SUCCESSFUL;
    }
    // -----------------------
    // list-members subcommand
    // -----------------------
    else if (subCmd.getName().equals(listMembersSubCmd.getName()))
    {
      String groupId = subCmd.getTrailingArguments().get(0);

      // get the current member list
      Set<String> memberList = adsContext.getServerGroupMemberList(groupId);
      if (memberList == null)
      {
        return ReturnCode.SUCCESSFUL;
      }
      StringBuffer buffer = new StringBuffer();
      for (String member : memberList)
      {
        buffer.append(member.substring(3));
        buffer.append(EOL);
      }
      try
      {
        outStream.write(buffer.toString().getBytes());
      }
      catch (IOException e)
      {
      }

      return ReturnCode.SUCCESSFUL;
    }
    // -----------------------
    // list-membership subcommand
    // -----------------------
    else if (subCmd.getName().equals(listMembershipSubCmd.getName()))
    {

      Set<Map<ServerGroupProperty, Object>> result = adsContext
          .readServerGroupRegistry();
      String MemberId = subCmd.getTrailingArguments().get(0);

      StringBuffer buffer = new StringBuffer();
      for (Map<ServerGroupProperty, Object> groupProps : result)
      {
        // Get the group name;
        String groupId = groupProps.get(ServerGroupProperty.UID).toString();

        // look for memeber list attribute
        for (ServerGroupProperty propName : groupProps.keySet())
        {
          if (propName.compareTo(ServerGroupProperty.MEMBERS) != 0)
          {
            continue;
          }
          // Check if the member list contains the member-id
          Set atts = (Set) groupProps.get(propName);
          for (Object att : atts)
          {
            if (att.toString().substring(3).toLowerCase().equals(
                MemberId.toLowerCase()))
            {
              buffer.append(groupId);
              buffer.append(EOL);
              break;
            }
          }
          break;
        }
      }
      try
      {
        outStream.write(buffer.toString().getBytes());
      }
      catch (IOException e)
      {
      }
      return ReturnCode.SUCCESSFUL;
    }

    // Should never occurs: If we are here, it means that the code to
    // handle to subcommand is not yet written.
    return ReturnCode.ERROR_UNEXPECTED;
  }
}
