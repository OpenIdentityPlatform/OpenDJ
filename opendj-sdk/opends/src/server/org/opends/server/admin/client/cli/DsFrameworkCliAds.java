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

import static org.opends.messages.AdminMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ADSContextHelper;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;

/**
 * This class is handling server group CLI.
 */
public class DsFrameworkCliAds implements DsFrameworkCliSubCommandGroup
{
  /**
   * The subcommand Parser.
   */
  DsFrameworkCliParser argParser ;

  /**
   * The enumeration containing the different subCommand names.
   */
  private enum SubCommandNameEnum
  {
    /**
     * The create-ads subcommand.
     */
    CREATE_ADS("create-ads"),

    /**
     * The delete-ads subcommand.
     */
    DELETE_ADS("delete-ads");

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
   * The 'create-ads' subcommand.
   */
  public SubCommand createAdsSubCmd;

  /**
   * The 'backend-name' argument of the 'create-ads' subcommand.
   */
  private StringArgument createAdsBackendNameArg;

  /**
   * The 'delete-ads' subcommand.
   */
  private SubCommand deleteAdsSubCmd;

  /**
   * The 'backend-name' argument of the 'delete-ads' subcommand.
   */
  private StringArgument deleteAdsBackendNameArg;

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
    return isHidden ;
  }

  /**
   * {@inheritDoc}
   */
  public String getGroupName()
  {
    return groupName ;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeCliGroup(DsFrameworkCliParser argParser,
      BooleanArgument verboseArg)
      throws ArgumentException
  {

    isHidden = true;
    groupName = "ads";
    this.argParser = argParser;

    // Create-ads subcommand
    createAdsSubCmd = new SubCommand(argParser, SubCommandNameEnum.CREATE_ADS
        .toString(), INFO_ADMIN_SUBCMD_CREATE_ADS_DESCRIPTION.get());
    createAdsSubCmd.setHidden(true);
    subCommands.add(createAdsSubCmd);

    createAdsBackendNameArg = new StringArgument("backendName",
        OPTION_SHORT_BACKENDNAME, OPTION_LONG_BACKENDNAME, true, true,
        OPTION_VALUE_BACKENDNAME,
        INFO_ADMIN_ARG_BACKENDNAME_DESCRIPTION.get());
    createAdsSubCmd.addArgument(createAdsBackendNameArg);

    // delete-ads
    deleteAdsSubCmd = new SubCommand(argParser,SubCommandNameEnum.DELETE_ADS
        .toString(), INFO_ADMIN_SUBCMD_DELETE_ADS_DESCRIPTION.get());
    deleteAdsSubCmd.setHidden(true);
    subCommands.add(deleteAdsSubCmd);

    deleteAdsBackendNameArg = new StringArgument("backendName",
        OPTION_SHORT_BACKENDNAME, OPTION_LONG_BACKENDNAME, true, true,
        OPTION_VALUE_BACKENDNAME,
        INFO_ADMIN_ARG_BACKENDNAME_DESCRIPTION.get());
    deleteAdsSubCmd.addArgument(deleteAdsBackendNameArg);
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
    ADSContext adsCtx = null ;
    InitialLdapContext ctx = null ;

    DsFrameworkCliReturnCode returnCode = ERROR_UNEXPECTED;

    try
    {
      //
      // create-ads subcommand
      if (subCmd.getName().equals(createAdsSubCmd.getName()))
      {
        String backendName = createAdsBackendNameArg.getValue();
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        adsCtx.createAdminData(backendName);
        returnCode = SUCCESSFUL;
      }
      else if (subCmd.getName().equals(deleteAdsSubCmd.getName()))
      {
        String backendName = deleteAdsBackendNameArg.getValue();
        ADSContextHelper helper = new ADSContextHelper();
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx);
        helper
            .removeAdministrationSuffix(adsCtx.getDirContext(), backendName);
        returnCode =  SUCCESSFUL;
      }
      else
      {
        // Should never occurs: If we are here, it means that the code to
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
}
