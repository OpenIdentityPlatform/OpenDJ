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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;



import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.io.File;

import org.opends.server.api.ConfigHandler;
import org.opends.server.config.BooleanConfigAttribute;
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
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.InitializationException;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



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
   * The DN of the configuration entry defining the LDAPS connection handler.
   */
  private static final String DN_LDAPS_CONNECTION_HANDLER =
       "cn=LDAPS Connection Handler," + DN_CONNHANDLER_BASE;

  /**
   * The DN of the configuration entry defining the JMX connection handler.
   */
  private static final String DN_JMX_CONNECTION_HANDLER =
       "cn=JMX Connection Handler," + DN_CONNHANDLER_BASE;


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
      System.exit(filterExitCode(exitCode));
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
    BooleanArgument   enableStartTLS;
    FileBasedArgument rootPasswordFile;
    IntegerArgument   ldapPort;
    IntegerArgument   ldapsPort;
    IntegerArgument   jmxPort;
    StringArgument    baseDNString;
    StringArgument    configClass;
    StringArgument    configFile;
    StringArgument    rootDNString;
    StringArgument    rootPassword;
    StringArgument    keyManagerProviderDN;
    StringArgument    trustManagerProviderDN;
    StringArgument    certNickName;
    StringArgument    keyManagerPath;
    StringArgument    serverRoot;

    Message toolDescription = INFO_CONFIGDS_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    try
    {
      configFile = new StringArgument("configfile", 'c', "configFile", true,
                                      false, true, "{configFile}", null, null,
                                      INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);

      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                             OPTION_LONG_CONFIG_CLASS, false,
                             false, true, OPTION_VALUE_CONFIG_CLASS,
                             ConfigFileHandler.class.getName(), null,
                             INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);

      ldapPort = new IntegerArgument("ldapport", OPTION_SHORT_PORT,
                                    "ldapPort", false, false,
                                     true, "{ldapPort}", 389, null, true, 1,
                                     true, 65535,
                                     INFO_CONFIGDS_DESCRIPTION_LDAP_PORT.get());
      argParser.addArgument(ldapPort);

      ldapsPort = new IntegerArgument("ldapsPort", 'P', "ldapsPort", false,
          false, true, "{ldapPort}", 636, null, true, 1, true, 65535,
          INFO_CONFIGDS_DESCRIPTION_LDAPS_PORT.get());
      argParser.addArgument(ldapsPort);

      enableStartTLS = new BooleanArgument("enableStartTLS",
          OPTION_SHORT_START_TLS, "enableStartTLS",
          INFO_CONFIGDS_DESCRIPTION_ENABLE_START_TLS.get());
      argParser.addArgument(enableStartTLS);

      jmxPort = new IntegerArgument("jmxport", 'x', "jmxPort", false, false,
          true, "{jmxPort}", SetupUtils.getDefaultJMXPort(), null, true, 1,
          true, 65535,
          INFO_CONFIGDS_DESCRIPTION_JMX_PORT.get());
      argParser.addArgument(jmxPort);

      keyManagerProviderDN = new StringArgument("keymanagerproviderdn",
          'k',
          "keyManagerProviderDN",
          false, false,
          true, "{keyManagerProviderDN}",
          null,
          null,
          INFO_CONFIGDS_DESCRIPTION_KEYMANAGER_PROVIDER_DN.get());
      argParser.addArgument(keyManagerProviderDN);

      trustManagerProviderDN = new StringArgument("trustmanagerproviderdn",
          't',
          "trustManagerProviderDN",
          false, false,
          true, "{trustManagerProviderDN}",
          null,
          null,
          INFO_CONFIGDS_DESCRIPTION_TRUSTMANAGER_PROVIDER_DN.get());
      argParser.addArgument(trustManagerProviderDN);

      keyManagerPath = new StringArgument("keymanagerpath",
          'm',
          "keyManagerPath",
          false, false, true,
          "{keyManagerPath}",
          null,
          null,
          INFO_CONFIGDS_DESCRIPTION_KEYMANAGER_PATH.get());
      argParser.addArgument(keyManagerPath);

      certNickName = new StringArgument("certnickname",
          'a',
          "certNickName",
          false, false,
          true, "{certNickName}",
          null,
          null,
          INFO_CONFIGDS_DESCRIPTION_CERTNICKNAME.get());
      argParser.addArgument(certNickName);

      baseDNString = new StringArgument(
          "basedn", OPTION_SHORT_BASEDN,
          OPTION_LONG_BASEDN, false, true,
          true, OPTION_VALUE_BASEDN,
          "dc=example,dc=com",
          null,
          INFO_CONFIGDS_DESCRIPTION_BASE_DN.get());
      argParser.addArgument(baseDNString);

      rootDNString = new StringArgument(
          "rootdn", OPTION_SHORT_ROOT_USER_DN,
          OPTION_LONG_ROOT_USER_DN, false, false,
          true, OPTION_VALUE_ROOT_USER_DN,
          "cn=Directory Manager", null,
          INFO_CONFIGDS_DESCRIPTION_ROOT_DN.get());
      argParser.addArgument(rootDNString);

      rootPassword = new StringArgument(
          "rootpw", OPTION_SHORT_BINDPWD,
          "rootPassword", false,
          false, true, "{rootUserPW}", null, null,
          INFO_CONFIGDS_DESCRIPTION_ROOT_PW.get());
      argParser.addArgument(rootPassword);

      rootPasswordFile = new FileBasedArgument(
          "rootpwfile",
          OPTION_SHORT_BINDPWD_FILE,
          "rootPasswordFile", false, false,
          "{filename}", null, null,
          INFO_CONFIGDS_DESCRIPTION_ROOT_PW_FILE.get());
      argParser.addArgument(rootPasswordFile);

      showUsage = new BooleanArgument("showusage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);

      serverRoot = new StringArgument("serverRoot",
              ToolConstants.OPTION_SHORT_SERVER_ROOT,
              ToolConstants.OPTION_LONG_SERVER_ROOT,
              false, false, true, "{serverRootDir}", null, null,
              null);
      serverRoot.setHidden(true);
      argParser.addArgument(serverRoot);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
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
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }


    // Make sure that the user actually tried to configure something.
    if (! (baseDNString.isPresent() || ldapPort.isPresent() ||
        jmxPort.isPresent() || rootDNString.isPresent()))
    {
      Message message = ERR_CONFIGDS_NO_CONFIG_CHANGES.get();
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }

    try
    {
      Set<Integer> ports = new HashSet<Integer>();
      if (ldapPort.isPresent())
      {
        ports.add(ldapPort.getIntValue());
      }
      if (ldapsPort.isPresent())
      {
        if (ports.contains(ldapsPort.getIntValue()))
        {
          Message message = ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(
                  String.valueOf(ldapsPort.getIntValue()));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          System.err.println(argParser.getUsage());
          return 1;
        }
        else
        {
          ports.add(ldapsPort.getIntValue());
        }
      }
      if (jmxPort.isPresent())
      {
        if (ports.contains(jmxPort.getIntValue()))
        {
          Message message = ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(
                  String.valueOf(jmxPort.getIntValue()));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          System.err.println(argParser.getUsage());
          return 1;
        }
        else
        {
          ports.add(jmxPort.getIntValue());
        }
      }
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    if (serverRoot.isPresent()) {
      DirectoryEnvironmentConfig env = DirectoryServer.getEnvironmentConfig();
      String root = serverRoot.getValue();
      try {
        env.setServerRoot(new File(serverRoot.getValue()));
      } catch (InitializationException e) {
        ERR_INITIALIZE_SERVER_ROOT.get(root, e.getMessageObject());
      }
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
      Message message = ERR_CONFIGDS_CANNOT_INITIALIZE_JMX.get(
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
      Message message = ERR_CONFIGDS_CANNOT_INITIALIZE_CONFIG.get(
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
      Message message = ERR_CONFIGDS_CANNOT_INITIALIZE_SCHEMA.get(
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
      Message message = ERR_CONFIGDS_CANNOT_ACQUIRE_SERVER_LOCK.get(
              String.valueOf(serverLockFileName),
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
            Message message = ERR_CONFIGDS_CANNOT_PARSE_BASE_DN.get(
                    String.valueOf(dnString),
                    de.getMessageObject());
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
          Message message = ERR_CONFIGDS_CANNOT_PARSE_ROOT_DN.get(
                  String.valueOf(rootDNString.getValue()),
                  de.getMessageObject());
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
          Message message = ERR_CONFIGDS_NO_ROOT_PW.get();
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }


      // Get the Directory Server configuration handler and use it to make the
      // appropriate configuration changes.
      ConfigHandler configHandler = directoryServer.getConfigHandler();


      // Check that the key manager provided is valid.
      if (keyManagerProviderDN.isPresent())
      {
        DN dn = null;
        try
        {
          dn = DN.decode(keyManagerProviderDN.getValue());
        }
        catch (DirectoryException de)
        {
          Message message =
                  ERR_CONFIGDS_CANNOT_PARSE_KEYMANAGER_PROVIDER_DN.get(
                          keyManagerProviderDN.getValue(),
                          de.getMessageObject());
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }

        try
        {
          configHandler.getConfigEntry(dn);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIG_KEYMANAGER_CANNOT_GET_BASE.get(
              String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      // Check that the trust manager provided is valid.
      if (trustManagerProviderDN.isPresent())
      {
        DN dn = null;
        try
        {
          dn = DN.decode(trustManagerProviderDN.getValue());
        }
        catch (DirectoryException de)
        {
          Message message = ERR_CONFIGDS_CANNOT_PARSE_TRUSTMANAGER_PROVIDER_DN.
                  get(trustManagerProviderDN.getValue(), de.getMessageObject());
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }

        try
        {
          configHandler.getConfigEntry(dn);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIG_TRUSTMANAGER_CANNOT_GET_BASE.get(
                String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      // Check that the keystore path values are valid.
      if (keyManagerPath.isPresent())
      {
        if (!keyManagerProviderDN.isPresent())
        {
          Message message = ERR_CONFIGDS_KEYMANAGER_PROVIDER_DN_REQUIRED.get(
                  keyManagerProviderDN.getLongIdentifier(),
              keyManagerPath.getLongIdentifier());
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      // If one or more base DNs were specified, then update the config
      // accordingly.
      if (baseDNs != null)
      {
        try
        {
          DN jeBackendDN = DN.decode(DN_JE_BACKEND);
          ConfigEntry configEntry = configHandler.getConfigEntry(jeBackendDN);

          DNConfigAttribute baseDNAttr =
               new DNConfigAttribute(
                       ATTR_BACKEND_BASE_DN,
                       INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS.get(),
                                     true, true, false, baseDNs);
          configEntry.putConfigAttribute(baseDNAttr);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_UPDATE_BASE_DN.get(
                  String.valueOf(e));
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


          IntegerConfigAttribute portAttr =
               new IntegerConfigAttribute(ATTR_LISTEN_PORT,
                       INFO_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT.get(),
                                          true, false, true, true, 1, true,
                                          65535, ldapPort.getIntValue());
          configEntry.putConfigAttribute(portAttr);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_UPDATE_LDAP_PORT.get(
                  String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

//    If an LDAPS port was specified, then update the config accordingly.
      if (ldapsPort.isPresent())
      {
        try
        {
          DN ldapListenerDN = DN.decode(DN_LDAPS_CONNECTION_HANDLER);
          ConfigEntry configEntry =
               configHandler.getConfigEntry(ldapListenerDN);


          IntegerConfigAttribute portAttr =
               new IntegerConfigAttribute(ATTR_LISTEN_PORT,
                       INFO_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT.get(),
                                          true, false, true, true, 1, true,
                                          65535, ldapsPort.getIntValue());
          configEntry.putConfigAttribute(portAttr);

          BooleanConfigAttribute enablePortAttr =
            new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                INFO_LDAPS_CONNHANDLER_DESCRIPTION_ENABLE.get(),
                    true, true);
          configEntry.putConfigAttribute(enablePortAttr);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_UPDATE_LDAPS_PORT.get(
                  String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

//    If an JMX port was specified, then update the config accordingly.
      if (jmxPort.isPresent())
      {
        try
        {
          DN jmxListenerDN = DN.decode(DN_JMX_CONNECTION_HANDLER);
          ConfigEntry configEntry =
               configHandler.getConfigEntry(jmxListenerDN);

          IntegerConfigAttribute portAttr =
               new IntegerConfigAttribute(
                       ATTR_LISTEN_PORT,
                       INFO_JMX_CONNHANDLER_DESCRIPTION_LISTEN_PORT.get(),
                       true, false, true, true, 1, true,
                       65535, jmxPort.getIntValue());
          configEntry.putConfigAttribute(portAttr);

          BooleanConfigAttribute enablePortAttr =
            new BooleanConfigAttribute(ATTR_CONNECTION_HANDLER_ENABLED,
                INFO_JMX_CONNHANDLER_DESCRIPTION_ENABLE.get(),
                    true, true);
          configEntry.putConfigAttribute(enablePortAttr);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_UPDATE_JMX_PORT.get(
                  String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      // Start TLS configuration
      if (enableStartTLS.isPresent())
      {
        try
        {
          DN ldapListenerDN = DN.decode(DN_LDAP_CONNECTION_HANDLER);
          ConfigEntry configEntry =
               configHandler.getConfigEntry(ldapListenerDN);


          BooleanConfigAttribute startTLS =
            new BooleanConfigAttribute(ATTR_ALLOW_STARTTLS,
                INFO_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS.get(),
                    true, true);
          configEntry.putConfigAttribute(startTLS);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_ENABLE_STARTTLS.get(
                  String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      // Key manager provider
      if (keyManagerProviderDN.isPresent())
      {
        if (enableStartTLS.isPresent() || ldapsPort.isPresent())
        {
          try
          {
            // Enable the key manager
            DN dn = DN.decode(keyManagerProviderDN.getValue());
            ConfigEntry configEntry = configHandler.getConfigEntry(dn);

            BooleanConfigAttribute enableAttr =
              new BooleanConfigAttribute(ATTR_KEYMANAGER_ENABLED,
                  INFO_CONFIG_KEYMANAGER_DESCRIPTION_ENABLED.get(),
                      true, true);
            configEntry.putConfigAttribute(enableAttr);
          }
          catch (Exception e)
          {
            Message message = ERR_CONFIGDS_CANNOT_ENABLE_KEYMANAGER.get(
                    String.valueOf(e));
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }
        }

        try
        {
          if (enableStartTLS.isPresent())
          {
            // Use the key manager specified for the LDAP connection handler.
            DN ldapListenerDN = DN.decode(DN_LDAP_CONNECTION_HANDLER);
            ConfigEntry configEntry =
              configHandler.getConfigEntry(ldapListenerDN);

            StringConfigAttribute keyManagerProviderAttr =
              new StringConfigAttribute(ATTR_KEYMANAGER_DN,
                      INFO_LDAP_CONNHANDLER_DESCRIPTION_KEYMANAGER_DN.get(),
                  false, false, true, keyManagerProviderDN.getValue());
            configEntry.putConfigAttribute(keyManagerProviderAttr);
          }

          if (ldapsPort.isPresent())
          {
            // Use the key manager specified for the LDAPS connection handler.
            DN ldapsListenerDN = DN.decode(DN_LDAPS_CONNECTION_HANDLER);
            ConfigEntry configEntry =
              configHandler.getConfigEntry(ldapsListenerDN);

            StringConfigAttribute keyManagerProviderAttr =
              new StringConfigAttribute(ATTR_KEYMANAGER_DN,
                  INFO_LDAP_CONNHANDLER_DESCRIPTION_KEYMANAGER_DN.get(),
                      false, false,
                  true, keyManagerProviderDN.getValue());
            configEntry.putConfigAttribute(keyManagerProviderAttr);
          }
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_UPDATE_KEYMANAGER_REFERENCE.get(
                  String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }

        if (keyManagerPath.isPresent())
        {
          try
          {
            // Enable the key manager
            DN dn = DN.decode(keyManagerProviderDN.getValue());
            ConfigEntry configEntry = configHandler.getConfigEntry(dn);

            StringConfigAttribute pathAttr =
              new StringConfigAttribute(ATTR_KEYSTORE_FILE,
                  INFO_FILE_KEYMANAGER_DESCRIPTION_FILE.get(), true, true, true,
                  keyManagerPath.getValue());
            configEntry.putConfigAttribute(pathAttr);
          }
          catch (Exception e)
          {
            String message = String.valueOf(e);
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }
        }
      }
      if (trustManagerProviderDN.isPresent())
      {
        if (enableStartTLS.isPresent() || ldapsPort.isPresent())
        {
          // Enable the trust manager
          try
          {
            DN dn = DN.decode(trustManagerProviderDN.getValue());
            ConfigEntry configEntry = configHandler.getConfigEntry(dn);

            BooleanConfigAttribute enableAttr =
              new BooleanConfigAttribute(ATTR_TRUSTMANAGER_ENABLED,
                  ERR_CONFIG_TRUSTMANAGER_DESCRIPTION_ENABLED.get(),
                      true, true);
            configEntry.putConfigAttribute(enableAttr);
          }
          catch (Exception e)
          {
            Message message = ERR_CONFIGDS_CANNOT_ENABLE_TRUSTMANAGER.get(
                    String.valueOf(e));
            System.err.println(wrapText(message, MAX_LINE_WIDTH));
            return 1;
          }
        }

        try
        {
          if (enableStartTLS.isPresent())
          {
            // Use the trust manager specified for the LDAP connection handler.
            DN ldapListenerDN = DN.decode(DN_LDAP_CONNECTION_HANDLER);
            ConfigEntry configEntry =
              configHandler.getConfigEntry(ldapListenerDN);

            StringConfigAttribute trustManagerProviderAttr =
              new StringConfigAttribute(ATTR_TRUSTMANAGER_DN,
                  INFO_LDAP_CONNHANDLER_DESCRIPTION_TRUSTMANAGER_DN.get(),
                      false, false,
                  true, trustManagerProviderDN.getValue());
            configEntry.putConfigAttribute(trustManagerProviderAttr);
          }

          if (ldapsPort.isPresent())
          {
            // Use the trust manager specified for the LDAPS connection handler.
            DN ldapsListenerDN = DN.decode(DN_LDAPS_CONNECTION_HANDLER);
            ConfigEntry configEntry =
              configHandler.getConfigEntry(ldapsListenerDN);

            StringConfigAttribute trustManagerProviderAttr =
              new StringConfigAttribute(ATTR_TRUSTMANAGER_DN,
                  INFO_LDAP_CONNHANDLER_DESCRIPTION_TRUSTMANAGER_DN.get(),
                      false, false,
                  true, trustManagerProviderDN.getValue());
            configEntry.putConfigAttribute(trustManagerProviderAttr);
          }
        }
        catch (Exception e)
        {
          Message message =
                  ERR_CONFIGDS_CANNOT_UPDATE_TRUSTMANAGER_REFERENCE.get(
                          String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }

      if (certNickName.isPresent())
      {
        try
        {
          StringConfigAttribute certNickNameAttr =
            new StringConfigAttribute(
                    ATTR_SSL_CERT_NICKNAME,
                    INFO_LDAP_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME.get(),
                false, false, true, certNickName.getValue());

          if (ldapPort.isPresent())
          {
            // Use the key manager specified for the LDAP connection handler.
            DN ldapListenerDN = DN.decode(DN_LDAP_CONNECTION_HANDLER);
            ConfigEntry configEntry =
              configHandler.getConfigEntry(ldapListenerDN);

            configEntry.putConfigAttribute(certNickNameAttr);
          }

          if (ldapsPort.isPresent())
          {
            // Use the key manager specified for the LDAPS connection handler.
            DN ldapsListenerDN = DN.decode(DN_LDAPS_CONNECTION_HANDLER);
            ConfigEntry configEntry =
              configHandler.getConfigEntry(ldapsListenerDN);

            configEntry.putConfigAttribute(certNickNameAttr);
          }

          if (jmxPort.isPresent())
          {
            certNickNameAttr = new StringConfigAttribute(ATTR_SSL_CERT_NICKNAME,
                INFO_JMX_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME.get(),
                    false, false, true, certNickName.getValue());

            // Use the key manager specified for the JMX connection handler.
            DN jmxListenerDN = DN.decode(DN_JMX_CONNECTION_HANDLER);
            ConfigEntry configEntry =
              configHandler.getConfigEntry(jmxListenerDN);

            configEntry.putConfigAttribute(certNickNameAttr);
          }
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_UPDATE_CERT_NICKNAME.get(
                  String.valueOf(e));
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

          DNConfigAttribute bindDNAttr =
               new DNConfigAttribute(
                       ATTR_ROOTDN_ALTERNATE_BIND_DN,
                       INFO_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN.get(),
                       false, true, false,
                                     rootDN);
          configEntry.putConfigAttribute(bindDNAttr);

          byte[] rootPWBytes = getBytes(rootPW);
          String encodedPassword =
               SaltedSHA512PasswordStorageScheme.encodeOffline(rootPWBytes);
          StringConfigAttribute bindPWAttr =
               new StringConfigAttribute(ATTR_USER_PASSWORD, Message.EMPTY,
                                         false, false, false, encodedPassword);
          configEntry.putConfigAttribute(bindPWAttr);
        }
        catch (Exception e)
        {
          Message message = ERR_CONFIGDS_CANNOT_UPDATE_ROOT_USER.get(
                  String.valueOf(e));
          System.err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
      }


      // Write the updated configuration.
      try
      {
        configHandler.writeUpdatedConfig();

        Message message = INFO_CONFIGDS_WROTE_UPDATED_CONFIG.get();
        System.out.println(wrapText(message, MAX_LINE_WIDTH));
      }
      catch (DirectoryException de)
      {
        Message message = ERR_CONFIGDS_CANNOT_WRITE_UPDATED_CONFIG.get(
                de.getMessageObject());
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

