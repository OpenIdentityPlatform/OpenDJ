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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.quicksetup.installer.Installer;
import org.opends.server.admin.DefaultBehaviorProvider;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.StringPropertyDefinition;
import org.opends.server.admin.std.meta.CryptoManagerCfgDefn;
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
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.LDIFReader;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

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
  private static final boolean WRONG_USAGE = true;

  /** Private exception class to handle error message printing. */
  @SuppressWarnings("serial")
  private class ConfigureDSException extends Exception
  {
    private final int returnedErrorCode;
    private final LocalizableMessage errorMessage;
    private final boolean wrongUsage;

    ConfigureDSException(final LocalizableMessage errorMessage)
    {
      this(new Exception("An error occured in ConfigureDS: " + errorMessage), errorMessage, false);
    }

    ConfigureDSException(final Exception parentException, final LocalizableMessage errorMessage)
    {
      this(parentException, errorMessage, false);
    }

    ConfigureDSException(final LocalizableMessage errorMessage, final boolean showUsage)
    {
      this(new Exception("An error occured in ConfigureDS: " + errorMessage), errorMessage, showUsage);
    }

    ConfigureDSException(final Exception parentException, final LocalizableMessage errorMessage,
        final boolean showUsage)
    {
      this(parentException, errorMessage, showUsage, ERROR);
    }

    ConfigureDSException(final Exception parentException, final LocalizableMessage errorMessage,
        final boolean wrongUsage, final int retCode)
    {
      super(parentException);
      this.errorMessage = errorMessage;
      this.wrongUsage = wrongUsage;
      returnedErrorCode = retCode;
    }

    private LocalizableMessage getErrorMessage()
    {
      return errorMessage;
    }

    private boolean isWrongUsage()
    {
      return wrongUsage;
    }

    private int getErrorCode()
    {
      return returnedErrorCode;
    }
  }

  //FIXME: Find a better way to do to prevent hardcoded ldif entries.
  private static final String NEW_LINE = System.getProperty("line.separator");

  private static final String JCKES_KEY_MANAGER_DN = "cn=JCEKS,cn=Key Manager Providers,cn=config";
  private static final String JCKES_KEY_MANAGER_LDIF_ENTRY =
        "dn: " + JCKES_KEY_MANAGER_DN + NEW_LINE
      + "objectClass: top" + NEW_LINE
      + "objectClass: ds-cfg-key-manager-provider" + NEW_LINE
      + "objectClass: ds-cfg-file-based-key-manager-provider" + NEW_LINE
      + "cn: JCEKS" + NEW_LINE
      + "ds-cfg-java-class: org.opends.server.extensions.FileBasedKeyManagerProvider" + NEW_LINE
      + "ds-cfg-enabled: true" + NEW_LINE
      + "ds-cfg-key-store-type: JCEKS" + NEW_LINE
      + "ds-cfg-key-store-file: config/keystore.jceks" + NEW_LINE
      + "ds-cfg-key-store-pin-file: config/keystore.pin" + NEW_LINE;

  private static final String JCKES_TRUST_MANAGER_DN = "cn=JCEKS,cn=Trust Manager Providers,cn=config";
  private static final String JCKES_TRUST_MANAGER_LDIF_ENTRY =
        "dn: " + JCKES_TRUST_MANAGER_DN + NEW_LINE
      + "objectClass: top" + NEW_LINE
      + "objectClass: ds-cfg-trust-manager-provider" + NEW_LINE
      + "objectClass: ds-cfg-file-based-trust-manager-provider" + NEW_LINE
      + "cn: JCEKS" + NEW_LINE
      + "ds-cfg-java-class: org.opends.server.extensions.FileBasedTrustManagerProvider" + NEW_LINE
      + "ds-cfg-enabled: false" + NEW_LINE
      + "ds-cfg-trust-store-type: JCEKS" + NEW_LINE
      + "ds-cfg-trust-store-file: config/truststore" + NEW_LINE;

  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.tools.ConfigureDS";

  /** The DN of the configuration entry defining the LDAP connection handler. */
  public static final String DN_LDAP_CONNECTION_HANDLER = "cn=LDAP Connection Handler," + DN_CONNHANDLER_BASE;

  /** The DN of the configuration entry defining the Administration connector. */
  public static final String DN_ADMIN_CONNECTOR = "cn=Administration Connector," + DN_CONFIG_ROOT;

  /** The DN of the configuration entry defining the LDAPS connection handler. */
  private static final String DN_LDAPS_CONNECTION_HANDLER = "cn=LDAPS Connection Handler," + DN_CONNHANDLER_BASE;

  /** The DN of the configuration entry defining the JMX connection handler. */
  private static final String DN_JMX_CONNECTION_HANDLER = "cn=JMX Connection Handler," + DN_CONNHANDLER_BASE;

  /** The DN of the configuration entry defining the initial root user. */
  public static final String DN_ROOT_USER = "cn=Directory Manager," + DN_ROOT_DN_CONFIG_BASE;

  /** The DN of the Crypto Manager. */
  public static final String DN_CRYPTO_MANAGER = "cn=Crypto Manager,cn=config";

  /** The DN of the DIGEST-MD5 SASL mechanism handler. */
  public static final String DN_DIGEST_MD5_SASL_MECHANISM = "cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config";

  private static int SUCCESS = 0;
  private static int ERROR = 1;

  /**
   * Provides the command-line arguments to the <CODE>configMain</CODE> method
   * for processing.
   *
   * @param  args  The set of command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    final int exitCode = configMain(args, System.out, System.err);
    if (exitCode != SUCCESS)
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
   * @param outStream Output stream.
   * @param errStream Error stream.
   * @return  The exit code from the configuration processing.  A nonzero value
   *          indicates that there was some kind of problem during the
   *          configuration processing.
   */
  public static int configMain(final String[] args, final OutputStream outStream, final OutputStream errStream)
  {
    final ConfigureDS tool = new ConfigureDS(args, outStream, errStream);
    return tool.run();
  }

  private final String[] arguments;
  private final PrintStream out;
  private final PrintStream err;

  private final ArgumentParser argParser;

  private BooleanArgument showUsage;
  private BooleanArgument enableStartTLS;
  private FileBasedArgument rootPasswordFile;
  private StringArgument hostName;
  private IntegerArgument ldapPort;
  private IntegerArgument adminConnectorPort;
  private IntegerArgument ldapsPort;
  private IntegerArgument jmxPort;
  private StringArgument baseDNString;
  private StringArgument configClass;
  private StringArgument configFile;
  private StringArgument rootDNString;
  private StringArgument rootPassword;
  private StringArgument keyManagerProviderDN;
  private StringArgument trustManagerProviderDN;
  private StringArgument certNickName;
  private StringArgument keyManagerPath;
  private StringArgument serverRoot;
  private StringArgument backendType;

  private final String serverLockFileName = LockFileManager.getServerLockFileName();
  private final StringBuilder failureReason = new StringBuilder();
  private ConfigHandler<?> configHandler;

  private ConfigureDS(final String[] args, final OutputStream outStream, final OutputStream errStream)
  {
    arguments = args;
    out = NullOutputStream.wrapOrNullStream(outStream);
    err = NullOutputStream.wrapOrNullStream(errStream);
    argParser = new ArgumentParser(CLASS_NAME, INFO_CONFIGDS_TOOL_DESCRIPTION.get(), false);
  }

  private int run()
  {
    try
    {
      initializeArguments();
      parseArguments();
      if (argParser.usageOrVersionDisplayed())
      {
        return SUCCESS;
      }

      checkArgumentsConsistency();
      checkPortArguments();

      tryAcquireExclusiveLocks();
      updateBaseDNs(parseProvidedBaseDNs());

      initializeDirectoryServer();

      final DN rootDN = parseRootDN();
      final String rootPW = parseRootDNPassword();

      // Get the Directory Server configuration handler and use it to make the
      // appropriate configuration changes.
      configHandler = DirectoryServer.getConfigHandler();

      checkManagerProvider(keyManagerProviderDN, JCKES_KEY_MANAGER_DN, JCKES_KEY_MANAGER_LDIF_ENTRY, true);
      checkManagerProvider(trustManagerProviderDN, JCKES_TRUST_MANAGER_DN, JCKES_TRUST_MANAGER_LDIF_ENTRY, false);
      // Check that the keystore path values are valid.
      if (keyManagerPath.isPresent() && !keyManagerProviderDN.isPresent())
      {
        final LocalizableMessage message = ERR_CONFIGDS_KEYMANAGER_PROVIDER_DN_REQUIRED.get(
            keyManagerProviderDN.getLongIdentifier(), keyManagerPath.getLongIdentifier());
        throw new ConfigureDSException(message);
      }

      updateLdapPort();
      updateAdminConnectorPort();
      updateLdapSecurePort();
      updateJMXport();
      updateStartTLS();
      updateKeyManager();
      updateTrustManager();
      updateRootUser(rootDN, rootPW);
      addFQDNDigestMD5();
      updateCryptoCipher();
      writeUpdatedConfiguration();

      return SUCCESS;
    }
    catch (final ConfigureDSException e)
    {
     if (e.isWrongUsage())
     {
       err.println(argParser.getUsage());
     }
     printWrappedText(err, e.getErrorMessage());
     return e.getErrorCode();
    }
    finally
    {
      LockFileManager.releaseLock(serverLockFileName, failureReason);
    }
  }

  private void initializeArguments() throws ConfigureDSException
  {
    try
    {
      configFile = new StringArgument(
          "configfile", 'c', "configFile",
          true, false, true, INFO_CONFIGFILE_PLACEHOLDER.get(),
          null, null, INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);

      configClass = new StringArgument(
          "configclass", OPTION_SHORT_CONFIG_CLASS, OPTION_LONG_CONFIG_CLASS,
          false, false, true, INFO_CONFIGCLASS_PLACEHOLDER.get(),
          ConfigFileHandler.class.getName(), null, INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);

      String defaultHostName;
      try
      {
        defaultHostName = InetAddress.getLocalHost().getHostName();
      }
      catch (final Exception e)
      {
        // Not much we can do here.
        defaultHostName = "localhost";
      }

      hostName = new StringArgument(
          OPTION_LONG_HOST.toLowerCase(), OPTION_SHORT_HOST, OPTION_LONG_HOST,
          false, false, true, INFO_HOST_PLACEHOLDER.get(),
          defaultHostName, null, INFO_INSTALLDS_DESCRIPTION_HOST_NAME.get());
      argParser.addArgument(hostName);

      ldapPort = new IntegerArgument(
          "ldapport", OPTION_SHORT_PORT, "ldapPort",
          false, false, true, INFO_LDAPPORT_PLACEHOLDER.get(),
          389, null, true, 1, true, 65535, INFO_CONFIGDS_DESCRIPTION_LDAP_PORT.get());
      argParser.addArgument(ldapPort);

      adminConnectorPort = new IntegerArgument(
          "adminConnectorPort".toLowerCase(), null, "adminConnectorPort",
          false, false, true, INFO_PORT_PLACEHOLDER.get(),
          4444, "adminConnectorPort", true, 1, true, 65535, INFO_INSTALLDS_DESCRIPTION_ADMINCONNECTORPORT.get());
      argParser.addArgument(adminConnectorPort);

      ldapsPort = new IntegerArgument(
          "ldapsPort", 'P', "ldapsPort",
          false, false, true, INFO_LDAPPORT_PLACEHOLDER.get(),
          636, null, true, 1, true, 65535, INFO_CONFIGDS_DESCRIPTION_LDAPS_PORT.get());
      argParser.addArgument(ldapsPort);

      enableStartTLS = new BooleanArgument(
          "enableStartTLS", OPTION_SHORT_START_TLS, "enableStartTLS",
          INFO_CONFIGDS_DESCRIPTION_ENABLE_START_TLS.get());
      argParser.addArgument(enableStartTLS);

      jmxPort = new IntegerArgument(
          "jmxport", 'x', "jmxPort",
          false, false, true, INFO_JMXPORT_PLACEHOLDER.get(),
          CliConstants.DEFAULT_JMX_PORT, null, true, 1, true, 65535, INFO_CONFIGDS_DESCRIPTION_JMX_PORT.get());
      argParser.addArgument(jmxPort);

      keyManagerProviderDN = new StringArgument(
          "keymanagerproviderdn", 'k', "keyManagerProviderDN",
          false, false, true, INFO_KEY_MANAGER_PROVIDER_DN_PLACEHOLDER.get(),
          null, null, INFO_CONFIGDS_DESCRIPTION_KEYMANAGER_PROVIDER_DN.get());
      argParser.addArgument(keyManagerProviderDN);

      trustManagerProviderDN = new StringArgument(
          "trustmanagerproviderdn", 't', "trustManagerProviderDN",
          false, false, true, INFO_TRUST_MANAGER_PROVIDER_DN_PLACEHOLDER.get(),
          null, null, INFO_CONFIGDS_DESCRIPTION_TRUSTMANAGER_PROVIDER_DN.get());
      argParser.addArgument(trustManagerProviderDN);

      keyManagerPath = new StringArgument(
          "keymanagerpath", 'm', "keyManagerPath",
          false, false, true, INFO_KEY_MANAGER_PATH_PLACEHOLDER.get(),
          null, null, INFO_CONFIGDS_DESCRIPTION_KEYMANAGER_PATH.get());
      argParser.addArgument(keyManagerPath);

      certNickName = new StringArgument(
          "certnickname", 'a', "certNickName",
          false, false, true, INFO_NICKNAME_PLACEHOLDER.get(),
          null, null, INFO_CONFIGDS_DESCRIPTION_CERTNICKNAME.get());
      argParser.addArgument(certNickName);

      baseDNString = new StringArgument(
          "basedn", OPTION_SHORT_BASEDN, OPTION_LONG_BASEDN,
          false, true, true, INFO_BASEDN_PLACEHOLDER.get(),
          "dc=example,dc=com", null, INFO_CONFIGDS_DESCRIPTION_BASE_DN.get());
      argParser.addArgument(baseDNString);

      rootDNString = new StringArgument(
          "rootdn", OPTION_SHORT_ROOT_USER_DN, OPTION_LONG_ROOT_USER_DN,
          false, false, true, INFO_ROOT_USER_DN_PLACEHOLDER.get(),
          "cn=Directory Manager", null, INFO_CONFIGDS_DESCRIPTION_ROOT_DN.get());
      argParser.addArgument(rootDNString);

      rootPassword = new StringArgument(
          "rootpw", OPTION_SHORT_BINDPWD, "rootPassword",
          false, false, true, INFO_ROOT_USER_PWD_PLACEHOLDER.get(),
          null, null, INFO_CONFIGDS_DESCRIPTION_ROOT_PW.get());
      argParser.addArgument(rootPassword);

      rootPasswordFile = new FileBasedArgument(
          "rootpwfile", OPTION_SHORT_BINDPWD_FILE, "rootPasswordFile",
          false, false, INFO_FILE_PLACEHOLDER.get(),
          null, null, INFO_CONFIGDS_DESCRIPTION_ROOT_PW_FILE.get());
      argParser.addArgument(rootPasswordFile);

      showUsage = CommonArguments.getShowUsage();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);

      serverRoot = new StringArgument(
          "serverRoot", OPTION_SHORT_SERVER_ROOT, OPTION_LONG_SERVER_ROOT,
          false, false, true, INFO_SERVER_ROOT_DIR_PLACEHOLDER.get(),
          null, null, null);
      serverRoot.setHidden(true);
      argParser.addArgument(serverRoot);

      backendType = new StringArgument(
          OPTION_LONG_BACKEND_TYPE.toLowerCase(), null, OPTION_LONG_BACKEND_TYPE,
          false, false, true, INFO_INSTALLDS_BACKEND_TYPE_PLACEHOLDER.get(),
          null, OPTION_LONG_BACKEND_TYPE, INFO_INSTALLDS_DESCRIPTION_BACKEND_TYPE.get()
      );
      argParser.addArgument(backendType);
    }
    catch (final ArgumentException ae)
    {
      throw new ConfigureDSException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
    }
  }

  private int parseArguments() throws ConfigureDSException
  {
    try
    {
      argParser.parseArguments(arguments);
      return SUCCESS;
    }
    catch (final ArgumentException ae)
    {
      throw new ConfigureDSException(ae, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()),
          WRONG_USAGE, LDAPResultCode.CLIENT_SIDE_PARAM_ERROR);
    }
  }

  /** Make sure that the user actually tried to configure something. */
  private void checkArgumentsConsistency() throws ConfigureDSException
  {
    if (!baseDNString.isPresent()
        && !ldapPort.isPresent()
        && !jmxPort.isPresent()
        && !rootDNString.isPresent())
    {
      throw new ConfigureDSException(ERR_CONFIGDS_NO_CONFIG_CHANGES.get(), WRONG_USAGE);
    }
  }

  private void checkPortArguments() throws ConfigureDSException
  {
    try
    {
      final IntegerArgument[] portArgs = {ldapPort, adminConnectorPort, ldapsPort, jmxPort};
      final Set<Integer> portsAdded = new HashSet<>();

      for (final IntegerArgument portArg : portArgs)
      {
        if (portArg.isPresent())
        {
          final int portNumber = portArg.getIntValue();
          if (portsAdded.contains(portNumber))
          {
            throw new ConfigureDSException(ERR_CONFIGDS_PORT_ALREADY_SPECIFIED.get(portArg.getIntValue()), WRONG_USAGE);
          }
          portsAdded.add(portNumber);
        }
      }
    }
    catch (final ArgumentException ae)
    {
      throw new ConfigureDSException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
    }
  }

  private void initializeDirectoryServer() throws ConfigureDSException
  {
    if (serverRoot.isPresent()) {
      final DirectoryEnvironmentConfig env = DirectoryServer.getEnvironmentConfig();
      final String root = serverRoot.getValue();
      try {
        env.setServerRoot(new File(serverRoot.getValue()));
      } catch (final InitializationException e) {
        ERR_INITIALIZE_SERVER_ROOT.get(root, e.getMessageObject());
      }
    }

    // Initialize the Directory Server configuration handler using the
    // information that was provided.
    final DirectoryServer directoryServer = DirectoryServer.getInstance();
    DirectoryServer.bootstrapClient();

    try
    {
      DirectoryServer.initializeJMX();
    }
    catch (final Exception e)
    {
      final LocalizableMessage msg = ERR_CONFIGDS_CANNOT_INITIALIZE_JMX.get(configFile.getValue(), e.getMessage());
      throw new ConfigureDSException(e, msg);
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(), configFile.getValue());
    }
    catch (final Exception e)
    {
      final LocalizableMessage msg = ERR_CONFIGDS_CANNOT_INITIALIZE_CONFIG.get(configFile.getValue(), e.getMessage());
      throw new ConfigureDSException(e, msg);
    }

    try
    {
      directoryServer.initializeSchema();
    }
    catch (final Exception e)
    {
      final LocalizableMessage msg = ERR_CONFIGDS_CANNOT_INITIALIZE_SCHEMA.get(configFile.getValue(), e.getMessage());
      throw new ConfigureDSException(e, msg);
    }
  }

  /**
   * Make sure that we can get an exclusive lock for the Directory Server, so
   * that no other operation will be allowed while this is in progress.
   *
   * @throws ConfigureDSException
   */
  private void tryAcquireExclusiveLocks() throws ConfigureDSException
  {
    if (! LockFileManager.acquireExclusiveLock(serverLockFileName, failureReason))
    {
      throw new ConfigureDSException(ERR_CONFIGDS_CANNOT_ACQUIRE_SERVER_LOCK.get(serverLockFileName, failureReason));
    }
  }

  private LinkedList<org.forgerock.opendj.ldap.DN> parseProvidedBaseDNs() throws ConfigureDSException
  {
    LinkedList<org.forgerock.opendj.ldap.DN> baseDNs = new LinkedList<>();
    if (baseDNString.isPresent())
    {
      for (final String dnString : baseDNString.getValues())
      {
        try
        {
          baseDNs.add(org.forgerock.opendj.ldap.DN.valueOf(dnString));
        }
        catch (final Exception e)
        {
          throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_PARSE_BASE_DN.get(dnString, e.getMessage()));
        }
      }
    }

    return baseDNs;
  }

  private DN parseRootDN() throws ConfigureDSException
  {
    DN rootDN = null;
    if (rootDNString.isPresent())
    {
      try
      {
        rootDN = DN.valueOf(rootDNString.getValue());
      }
      catch (final DirectoryException de)
      {
        final LocalizableMessage msg = ERR_CONFIGDS_CANNOT_PARSE_ROOT_DN.get(
            rootDNString.getValue(), de.getMessageObject());
        throw new ConfigureDSException(de, msg);
      }
    }
    return rootDN;
  }

  private String parseRootDNPassword() throws ConfigureDSException
  {
    String rootPW = null;
    if (rootDNString.isPresent())
    {
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
        throw new ConfigureDSException(ERR_CONFIGDS_NO_ROOT_PW.get());
      }
    }
    return rootPW;
  }

  private void checkManagerProvider(final Argument arg, final String jckesDN, final String ldifEntry,
      final boolean isKeyManager) throws ConfigureDSException
  {
    if (arg.isPresent())
    {
      DN dn = null;
      DN JCEKSManagerDN = null;
      try
      {
        dn = DN.valueOf(trustManagerProviderDN.getValue());
        JCEKSManagerDN = DN.valueOf(jckesDN);
      }
      catch (final DirectoryException de)
      {
        final String value = trustManagerProviderDN.getValue();
        final LocalizableMessage errorMessage = de.getMessageObject();
        final LocalizableMessage message =
            isKeyManager ? ERR_CONFIGDS_CANNOT_PARSE_KEYMANAGER_PROVIDER_DN.get(value, errorMessage)
                         : ERR_CONFIGDS_CANNOT_PARSE_TRUSTMANAGER_PROVIDER_DN.get(value, errorMessage);
        throw new ConfigureDSException(de, message);
      }

      if (dn.equals(JCEKSManagerDN))
      {
        LDIFReader reader = null;
        try
        {

          final String ldif = ldifEntry;
          final LDIFImportConfig ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
          reader = new LDIFReader(ldifImportConfig);
          Entry mangerConfigEntry;
          while ((mangerConfigEntry = reader.readEntry()) != null)
          {
            configHandler.addEntry(mangerConfigEntry, null);
          }
        }
        catch (final Exception e)
        {
          final LocalizableMessage message = isKeyManager ? ERR_CONFIG_KEYMANAGER_CANNOT_CREATE_JCEKS_PROVIDER.get(e)
                                                          : ERR_CONFIG_KEYMANAGER_CANNOT_GET_BASE.get(e);
          throw new ConfigureDSException(e, message);
        }
        finally
        {
          close(reader);
        }
      }
      else
      {
        try
        {
          configHandler.getConfigEntry(dn);
        }
        catch (final Exception e)
        {
          final LocalizableMessage message = isKeyManager ? ERR_CONFIG_KEYMANAGER_CANNOT_GET_BASE.get(e)
                                                          : ERR_CONFIG_TRUSTMANAGER_CANNOT_GET_BASE.get(e);
          throw new ConfigureDSException(e, message);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void updateBaseDNs(final List<org.forgerock.opendj.ldap.DN> baseDNs) throws ConfigureDSException
  {
    if (!baseDNs.isEmpty())
    {
      final String backendTypeName = backendType.getValue();
      final BackendTypeHelper backendTypeHelper = new BackendTypeHelper();
      final ManagedObjectDefinition<?, ?> backend = backendTypeHelper.retrieveBackendTypeFromName(backendTypeName);
      if (backend == null)
      {
        throw new ConfigureDSException(
            ERR_CONFIGDS_BACKEND_TYPE_UNKNOWN.get(backendTypeName, backendTypeHelper.getPrintableBackendTypeNames()));
      }

      try
      {
        BackendCreationHelper.createBackendOffline(Installer.ROOT_BACKEND_NAME, baseDNs,
            (ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg>) backend);
      }
      catch (Exception e)
      {
        throw new ConfigureDSException(ERR_CONFIGDS_SET_BACKEND_TYPE.get(backendTypeName, e.getMessage()));
      }
    }
  }

  private void updateLdapPort() throws ConfigureDSException
  {
    if (ldapPort.isPresent())
    {
      try
      {
        final IntegerConfigAttribute portAttr = new IntegerConfigAttribute(
            ATTR_LISTEN_PORT, INFO_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT.get(),
            true, false, true, true, 1, true, 65535, ldapPort.getIntValue());
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_LDAP_CONNECTION_HANDLER));
        configEntry.putConfigAttribute(portAttr);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_LDAP_PORT.get(e));
      }
    }
  }

  private void updateAdminConnectorPort() throws ConfigureDSException
  {
    if (adminConnectorPort.isPresent())
    {
      try
      {
        final IntegerConfigAttribute portAttr = new IntegerConfigAttribute(
            ATTR_LISTEN_PORT, INFO_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT.get(),
            true, false, true, true, 1, true, 65535, adminConnectorPort.getIntValue());
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_ADMIN_CONNECTOR));
        configEntry.putConfigAttribute(portAttr);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_ADMIN_CONNECTOR_PORT.get(e));
      }
    }
  }

  private void updateLdapSecurePort() throws ConfigureDSException
  {
    if (ldapsPort.isPresent())
    {
      try
      {
        final IntegerConfigAttribute portAttr = new IntegerConfigAttribute(
            ATTR_LISTEN_PORT, INFO_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT.get(),
            true, false, true, true, 1, true, 65535, ldapsPort.getIntValue());
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_LDAPS_CONNECTION_HANDLER));
        configEntry.putConfigAttribute(portAttr);

        final BooleanConfigAttribute enablePortAttr = new BooleanConfigAttribute(
            ATTR_CONNECTION_HANDLER_ENABLED, INFO_LDAPS_CONNHANDLER_DESCRIPTION_ENABLE.get(), true, true);
        configEntry.putConfigAttribute(enablePortAttr);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_LDAPS_PORT.get(e));
      }
    }
  }

  private void updateJMXport() throws ConfigureDSException
  {
    if (jmxPort.isPresent())
    {
      try
      {

        final IntegerConfigAttribute portAttr = new IntegerConfigAttribute(
            ATTR_LISTEN_PORT, INFO_JMX_CONNHANDLER_DESCRIPTION_LISTEN_PORT.get(),
            true, false, true, true, 1, true, 65535, jmxPort.getIntValue());
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_JMX_CONNECTION_HANDLER));
        configEntry.putConfigAttribute(portAttr);

        final BooleanConfigAttribute enablePortAttr = new BooleanConfigAttribute(
            ATTR_CONNECTION_HANDLER_ENABLED, INFO_JMX_CONNHANDLER_DESCRIPTION_ENABLE.get(), true, true);
        configEntry.putConfigAttribute(enablePortAttr);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_JMX_PORT.get(e));
      }
    }
  }

  private void updateStartTLS() throws ConfigureDSException
  {
    if (enableStartTLS.isPresent())
    {
      try
      {
        final BooleanConfigAttribute startTLS = new BooleanConfigAttribute(
            ATTR_ALLOW_STARTTLS, INFO_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS.get(), true, true);
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_LDAP_CONNECTION_HANDLER));
        configEntry.putConfigAttribute(startTLS);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_ENABLE_STARTTLS.get(e));
      }
    }
  }

  private void updateKeyManager() throws ConfigureDSException
  {
    if (keyManagerProviderDN.isPresent())
    {
      if (enableStartTLS.isPresent() || ldapsPort.isPresent())
      {
        try
        {
          // Enable the key manager
          final BooleanConfigAttribute enableAttr = new BooleanConfigAttribute(
              ATTR_KEYMANAGER_ENABLED, INFO_CONFIG_KEYMANAGER_DESCRIPTION_ENABLED.get(), true, true);
          final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(keyManagerProviderDN.getValue()));
          configEntry.putConfigAttribute(enableAttr);
        }
        catch (final Exception e)
        {
          throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_ENABLE_KEYMANAGER.get(e));
        }
      }

      putKeyManagerConfigAttribute(enableStartTLS, DN_LDAP_CONNECTION_HANDLER);
      putKeyManagerConfigAttribute(ldapsPort, DN_LDAPS_CONNECTION_HANDLER);

      if (keyManagerPath.isPresent())
      {
        try
        {
          final StringConfigAttribute pathAttr = new StringConfigAttribute(
              ATTR_KEYSTORE_FILE, INFO_FILE_KEYMANAGER_DESCRIPTION_FILE.get(),
              true, true, true, keyManagerPath.getValue());
          final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(keyManagerProviderDN.getValue()));
          configEntry.putConfigAttribute(pathAttr);
        }
        catch (final Exception e)
        {
          throw new ConfigureDSException(e, LocalizableMessage.raw(e.toString()));
        }
      }
    }
  }

  private void putKeyManagerConfigAttribute(final Argument arg, final String attributeDN)
      throws ConfigureDSException
  {
    if (arg.isPresent())
    {
      try
      {
        final StringConfigAttribute keyManagerProviderAttr = new StringConfigAttribute(
            ATTR_KEYMANAGER_DN, INFO_LDAP_CONNHANDLER_DESCRIPTION_KEYMANAGER_DN.get(),
            false, false, true, keyManagerProviderDN.getValue());
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(attributeDN));
        configEntry.putConfigAttribute(keyManagerProviderAttr);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_KEYMANAGER_REFERENCE.get(e));
      }
    }
  }

  private void updateTrustManager() throws ConfigureDSException
  {
    if (trustManagerProviderDN.isPresent())
    {
      if (enableStartTLS.isPresent() || ldapsPort.isPresent())
      {
        try
        {
          final BooleanConfigAttribute enableAttr = new BooleanConfigAttribute(
              ATTR_TRUSTMANAGER_ENABLED, ERR_CONFIG_TRUSTMANAGER_DESCRIPTION_ENABLED.get(), true, true);
          final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(trustManagerProviderDN.getValue()));
          configEntry.putConfigAttribute(enableAttr);
        }
        catch (final Exception e)
        {
          throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_ENABLE_TRUSTMANAGER.get(e));
        }
      }
      putTrustManagerAttribute(enableStartTLS, DN_LDAP_CONNECTION_HANDLER);
      putTrustManagerAttribute(ldapsPort, DN_LDAPS_CONNECTION_HANDLER);
    }

    if (certNickName.isPresent())
    {
      final StringConfigAttribute certNickNameAttr = new StringConfigAttribute(
          ATTR_SSL_CERT_NICKNAME, INFO_LDAP_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME.get(),
          false, false, true, certNickName.getValue());
      updateCertNicknameEntry(ldapPort, DN_LDAP_CONNECTION_HANDLER, certNickNameAttr);
      updateCertNicknameEntry(ldapsPort, DN_LDAPS_CONNECTION_HANDLER, certNickNameAttr);

      final StringConfigAttribute certNickNameJmxAttr = new StringConfigAttribute(
          ATTR_SSL_CERT_NICKNAME, INFO_JMX_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME.get(),
          false, false, true, certNickName.getValue());
      updateCertNicknameEntry(jmxPort, DN_JMX_CONNECTION_HANDLER, certNickNameJmxAttr);
    }
    else
    {
      // Use the key manager specified for connection handlers
      removeSSLCertNicknameAttribute(DN_LDAP_CONNECTION_HANDLER);
      removeSSLCertNicknameAttribute(DN_LDAPS_CONNECTION_HANDLER);
      removeSSLCertNicknameAttribute(DN_JMX_CONNECTION_HANDLER);
    }
  }

  private void putTrustManagerAttribute(final Argument arg, final String attributeDN) throws ConfigureDSException
  {
    if (arg.isPresent())
    {
      try
      {
        final StringConfigAttribute trustManagerProviderAttr = new StringConfigAttribute(
            ATTR_TRUSTMANAGER_DN, INFO_LDAP_CONNHANDLER_DESCRIPTION_TRUSTMANAGER_DN.get(),
            false, false, true, trustManagerProviderDN.getValue());
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(attributeDN));
        configEntry.putConfigAttribute(trustManagerProviderAttr);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_TRUSTMANAGER_REFERENCE.get(e));
      }
    }
  }

  private void updateCertNicknameEntry(final Argument arg, final String attributeDN,
      final StringConfigAttribute configAttr) throws ConfigureDSException
  {
    try
    {
      ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(attributeDN));
      if (arg.isPresent())
      {
        configEntry.putConfigAttribute(configAttr);
      }
      else
      {
        configEntry.removeConfigAttribute(ATTR_SSL_CERT_NICKNAME);
      }
    }
    catch (final Exception e)
    {
      throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_CERT_NICKNAME.get(e));
    }
  }

  private void removeSSLCertNicknameAttribute(final String attributeDN) throws ConfigureDSException
  {
    try
    {
      final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(attributeDN));
      configEntry.removeConfigAttribute(ATTR_SSL_CERT_NICKNAME.toLowerCase());
    }
    catch (final Exception e)
    {
      throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_CERT_NICKNAME.get(e));
    }
  }

  private void updateRootUser(final DN rootDN, final String rootPW) throws ConfigureDSException
  {
    if (rootDN != null)
    {
      try
      {
        final DNConfigAttribute bindDNAttr = new DNConfigAttribute(
            ATTR_ROOTDN_ALTERNATE_BIND_DN, INFO_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN.get(),
            false, true, false, rootDN);
        final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_ROOT_USER));
        configEntry.putConfigAttribute(bindDNAttr);

        final String encodedPassword = SaltedSHA512PasswordStorageScheme.encodeOffline(getBytes(rootPW));
        final StringConfigAttribute bindPWAttr = new StringConfigAttribute(
            ATTR_USER_PASSWORD, LocalizableMessage.EMPTY, false, false, false, encodedPassword);
        configEntry.putConfigAttribute(bindPWAttr);
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_ROOT_USER.get(e));
      }
    }
  }

  /** Set the FQDN for the DIGEST-MD5 SASL mechanism. */
  private void addFQDNDigestMD5() throws ConfigureDSException
  {
    try
    {
      final StringConfigAttribute fqdnAttr = new StringConfigAttribute(
            "ds-cfg-server-fqdn", LocalizableMessage.EMPTY, false, false, false, hostName.getValue());
      final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_DIGEST_MD5_SASL_MECHANISM));
      configEntry.putConfigAttribute(fqdnAttr);
    }
    catch (final Exception e)
    {
      throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_DIGEST_MD5_FQDN.get(e));
    }
  }

  /**
   * Check that the cipher specified is supported. This is intended to fix
   * issues with JVM that do not support the default cipher (see issue 3075 for
   * instance).
   *
   * @throws ConfigureDSException
   */
  private void updateCryptoCipher() throws ConfigureDSException
  {
    final CryptoManagerCfgDefn cryptoManager = CryptoManagerCfgDefn.getInstance();
    final StringPropertyDefinition prop = cryptoManager.getKeyWrappingTransformationPropertyDefinition();
    String defaultCipher = null;

    final DefaultBehaviorProvider<?> p = prop.getDefaultBehaviorProvider();
    if (p instanceof DefinedDefaultBehaviorProvider)
    {
      final Collection<?> defaultValues = ((DefinedDefaultBehaviorProvider<?>) p).getDefaultValues();
      if (!defaultValues.isEmpty())
      {
        defaultCipher = defaultValues.iterator().next().toString();
      }
    }

    if (defaultCipher != null)
    {
      // Check that the default cipher is supported by the JVM.
      try
      {
        Cipher.getInstance(defaultCipher);
      }
      catch (final GeneralSecurityException ex)
      {
        // The cipher is not supported: try to find an alternative one.
        final String alternativeCipher = getAlternativeCipher();
        if (alternativeCipher != null)
        {
          try
          {
            final StringConfigAttribute keyWrappingTransformation = new StringConfigAttribute(
                ATTR_CRYPTO_CIPHER_KEY_WRAPPING_TRANSFORMATION, LocalizableMessage.EMPTY,
                false, false, true, alternativeCipher);
            final ConfigEntry configEntry = configHandler.getConfigEntry(DN.valueOf(DN_CRYPTO_MANAGER));
            configEntry.putConfigAttribute(keyWrappingTransformation);
          }
          catch (final Exception e)
          {
            throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_CRYPTO_MANAGER.get(e));
          }
        }
      }
    }
  }

  private void writeUpdatedConfiguration() throws ConfigureDSException
  {
    try
    {
      configHandler.writeUpdatedConfig();
      printWrappedText(out, INFO_CONFIGDS_WROTE_UPDATED_CONFIG.get());
    }
    catch (final DirectoryException de)
    {
      throw new ConfigureDSException(de, ERR_CONFIGDS_CANNOT_WRITE_UPDATED_CONFIG.get(de.getMessageObject()));
    }
  }

  /**
   * Returns a cipher that is supported by the JVM we are running at.
   * Returns <CODE>null</CODE> if no alternative cipher could be found.
   * @return a cipher that is supported by the JVM we are running at.
   */
  public static String getAlternativeCipher()
  {
    final String[] preferredAlternativeCiphers =
    {
        "RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING",
        "RSA/ECB/PKCS1Padding"
    };
    String alternativeCipher = null;
    for (final String cipher : preferredAlternativeCiphers)
    {
      try
      {
        Cipher.getInstance(cipher);
        alternativeCipher = cipher;
        break;
      }
      catch (final Throwable t)
      {
      }
    }
    return alternativeCipher;
  }
}

