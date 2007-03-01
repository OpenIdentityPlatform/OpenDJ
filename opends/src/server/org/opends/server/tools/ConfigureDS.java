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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.util.LinkedList;

import org.opends.server.api.ConfigHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.extensions.SaltedSHA512PasswordStorageScheme;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a very basic tool that can be used to configure some of
 * the most important settings in the Directory Server.  This configuration is
 * performed by editing the server's configuration files and therefore the
 * Directory Server must be offline.  This utility will be used during the
 * Directory Server installation process.
 * <BR><BR>
 * The options that this tool can currently set include:
 * <BR>
 * <UL>
 *   <LI>The port on which the server will listen for LDAP communication</LI>
 *   <LI>The DN and password for the initial root user.
 *   <LI>The set of base DNs for user data</LI>
 * </UL>
 */
public class ConfigureDS
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.ConfigureDS";



  /**
   * The DN of the configuration entry defining the JE database backend.
   */
  private static final String DN_JE_BACKEND =
       ATTR_BACKEND_ID + "=userRoot," + DN_BACKEND_BASE;



  /**
   * The DN of the configuration entry defining the LDAP connection handler.
   */
  private static final String DN_LDAP_CONNECTION_HANDLER =
       "cn=LDAP Connection Handler," + DN_CONNHANDLER_BASE;



  /**
   * The DN of the configuration entry defining the initial root user.
   */
  private static final String DN_ROOT_USER =
       "cn=Directory Manager," + DN_ROOT_DN_CONFIG_BASE;



  /**
   * Provides the command-line arguments to the <CODE>configMain</CODE> method
   * for processing.
   *
   * @param  args  The set of command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int exitCode = configMain(args);
    if (exitCode != 0)
    {
      System.exit(exitCode);
    }
  }



  /**
   * Parses the provided command-line arguments and makes the appropriate
   * changes to the Directory Server configuration.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  The exit code from the configuration processing.  A nonzero value
   *          indicates that there was some kind of problem during the
   *          configuration processing.
   */
  public static int configMain(String[] args)
  {
    BooleanArgument   showUsage;
    FileBasedArgument rootPasswordFile;
    IntegerArgument   ldapPort;
    StringArgument    baseDNString;
    StringArgument    configClass;
    StringArgument    configFile;
    StringArgument    rootDNString;
    StringArgument    rootPassword;


    String toolDescription = getMessage(MSGID_CONFIGDS_TOOL_DESCRIPTION);
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    try
    {
      configFile = new StringArgument("configfile", 'c', "configFile", true,
                                      false, true, "{configFile}", null, null,
                                      MSGID_CONFIGDS_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);

      configClass = new StringArgument("configclass", 'C', "configClass", false,
                             false, true, "{configClass}",
                             ConfigFileHandler.class.getName(), null,
                             MSGID_CONFIGDS_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);

      ldapPort = new IntegerArgument("ldapport", 'p', "ldapPort", false, false,
                                     true, "{ldapPort}", 389, null, true, 1,
                                     true, 65535,
                                     MSGID_CONFIGDS_DESCRIPTION_LDAP_PORT);
      argParser.addArgument(ldapPort);

      baseDNString = new StringArgument("basedn", 'b', "baseDN", false, true,
                                        true, "{baseDN}", "dc=example,dc=com",
                                        null,
                                        MSGID_CONFIGDS_DESCRIPTION_BASE_DN);
      argParser.addArgument(baseDNString);

      rootDNString = new StringArgument("rootdn", 'D', "rootDN", false, false,
                                        true, "{rootUserDN}",
                                        "cn=Directory Manager", null,
                                        MSGID_CONFIGDS_DESCRIPTION_ROOT_DN);
      argParser.addArgument(rootDNString);

      rootPassword = new StringArgument("rootpw", 'w', "rootPassword", false,
                                        false, true, "{rootUserPW}", null, null,
                                        MSGID_CONFIGDS_DESCRIPTION_ROOT_PW);
      argParser.addArgument(rootPassword);

      rootPasswordFile = new FileBasedArgument("rootpwfile", 'W',
                                  "rootPasswordFile", false, false,
                                  "{filename}", null, null,
                                  MSGID_CONFIGDS_DESCRIPTION_ROOT_PW_FILE);
      argParser.addArgument(rootPasswordFile);

      showUsage = new BooleanArgument("showusage", 'H', "help",
                                      MSGID_CONFIGDS_DESCRIPTION_USAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CONFIGDS_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CONFIGDS_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage information, then print it and exit.
    if (showUsage.isPresent())
    {
      return 0;
    }


    // Make sure that the user actually tried to configure something.
    if (! (baseDNString.isPresent() || ldapPort.isPresent() ||
           rootDNString.isPresent()))
    {
      int    msgID   = MSGID_CONFIGDS_NO_CONFIG_CHANGES;
      String message = getMessage(msgID);
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // Initialize the Directory Server configuration handler using the
    // information that was provided.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    directoryServer.bootstrapClient();

    try
    {
      directoryServer.initializeJMX();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIGDS_CANNOT_INITIALIZE_JMX;
      String message = getMessage(msgID,
                                  String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIGDS_CANNOT_INITIALIZE_CONFIG;
      String message = getMessage(msgID,
                                  String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeSchema();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CONFIGDS_CANNOT_INITIALIZE_SCHEMA;
      String message = getMessage(msgID,
                                  String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Make sure that we can get an exclusive lock for the Directory Server, so
    // that no other operation will be allowed while this is in progress.
    String serverLockFileName = LockFileManager.getServerLockFileName();
    StringBuilder failureReason = new StringBuilder();
    if (! LockFileManager.acquireExclusiveLock(serverLockFileName,
                                               failureReason))
    {
      int    msgID   = MSGID_CONFIGDS_CANNOT_ACQUIRE_SERVER_LOCK;
      String message = getMessage(msgID, String.valueOf(serverLockFileName),
                                  String.valueOf(failureReason));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    try
    {
      // If one or more base DNs were provided, then make sure that they can be
      // parsed as valid DNs.
      LinkedList<DN> baseDNs = null;
      if (baseDNString.isPresent())
      {
        baseDNs = new LinkedList<DN>();
        for (String dnString : baseDNString.getValues())
        {
          try
          {
            baseDNs.add(DN.decode(dnString));
          }
          catch (DirectoryException de)
          {
            int    msgID   = MSGID_CONFIGDS_CANNOT_PARSE_BASE_DN;
            String message = getMessage(msgID, String.valueOf(dnString),
                                        de.getErrorMessage());
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }
        }
      }


      // If a root user DN was provided, then make sure it can be parsed.  Also,
      // make sure that either a password or password file was specified.
      DN     rootDN = null;
      String rootPW = null;
      if (rootDNString.isPresent())
      {
        try
        {
          rootDN = DN.decode(rootDNString.getValue());
        }
        catch (DirectoryException de)
        {
          int    msgID   = MSGID_CONFIGDS_CANNOT_PARSE_ROOT_DN;
          String message = getMessage(msgID,
                                      String.valueOf(rootDNString.getValue()),
                                      de.getErrorMessage());
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }

        if (rootPassword.isPresent())
        {
          rootPW = rootPassword.getValue();
        }
        else if (rootPasswordFile.isPresent())
        {
          rootPW = rootPasswordFile.getValue();
        }
        else
        {
          int    msgID   = MSGID_CONFIGDS_NO_ROOT_PW;
          String message = getMessage(msgID);
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }


      // Get the Directory Server configuration handler and use it to make the
      // appropriate configuration changes.
      ConfigHandler configHandler = directoryServer.getConfigHandler();


      // If one or more base DNs were specified, then update the config
      // accordingly.
      if (baseDNs != null)
      {
        try
        {
          DN jeBackendDN = DN.decode(DN_JE_BACKEND);
          ConfigEntry configEntry = configHandler.getConfigEntry(jeBackendDN);

          int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
          DNConfigAttribute baseDNAttr =
               new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID),
                                     true, true, false, baseDNs);
          configEntry.putConfigAttribute(baseDNAttr);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_CONFIGDS_CANNOT_UPDATE_BASE_DN;
          String message = getMessage(msgID, String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }


      // If an LDAP port was specified, then update the config accordingly.
      if (ldapPort.isPresent())
      {
        try
        {
          DN ldapListenerDN = DN.decode(DN_LDAP_CONNECTION_HANDLER);
          ConfigEntry configEntry =
               configHandler.getConfigEntry(ldapListenerDN);

          int msgID = MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT;
          IntegerConfigAttribute portAttr =
               new IntegerConfigAttribute(ATTR_LISTEN_PORT, getMessage(msgID),
                                          true, false, true, true, 1, true,
                                          65535, ldapPort.getIntValue());
          configEntry.putConfigAttribute(portAttr);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_CONFIGDS_CANNOT_UPDATE_LDAP_PORT;
          String message = getMessage(msgID, String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }


      // If a root user DN and password were specified, then update the config
      // accordingly.
      if (rootDN != null)
      {
        try
        {
          DN rootUserDN = DN.decode(DN_ROOT_USER);
          ConfigEntry configEntry = configHandler.getConfigEntry(rootUserDN);

          int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN;
          DNConfigAttribute bindDNAttr =
               new DNConfigAttribute(ATTR_ROOTDN_ALTERNATE_BIND_DN,
                                     getMessage(msgID), false, true, false,
                                     rootDN);
          configEntry.putConfigAttribute(bindDNAttr);

          byte[] rootPWBytes = getBytes(rootPW);
          String encodedPassword =
               SaltedSHA512PasswordStorageScheme.encodeOffline(rootPWBytes);
          StringConfigAttribute bindPWAttr =
               new StringConfigAttribute(ATTR_USER_PASSWORD, "", false, false,
                                         false, encodedPassword);
          configEntry.putConfigAttribute(bindPWAttr);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_CONFIGDS_CANNOT_UPDATE_ROOT_USER;
          String message = getMessage(msgID, String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }


      // Write the updated configuration.
      try
      {
        configHandler.writeUpdatedConfig();

        int    msgID   = MSGID_CONFIGDS_WROTE_UPDATED_CONFIG;
        String message = getMessage(msgID);
        System.out.println(wrapText(message, MAX_LINE_WIDTH));
      }
      catch (DirectoryException de)
      {
        int    msgID   = MSGID_CONFIGDS_CANNOT_WRITE_UPDATED_CONFIG;
        String message = getMessage(msgID, de.getErrorMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }
    finally
    {
      LockFileManager.releaseLock(serverLockFileName, failureReason);
    }


    // If we've gotten here, then everything was successful.
    return 0;
  }
}

