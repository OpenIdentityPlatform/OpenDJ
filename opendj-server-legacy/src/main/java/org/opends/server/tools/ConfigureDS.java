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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.INFO_FILE_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_JMXPORT_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_PORT_PLACEHOLDER;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

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
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.DefaultBehaviorProvider;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.StringPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.meta.CryptoManagerCfgDefn;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.quicksetup.installer.Installer;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.SaltedSHA512PasswordStorageScheme;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
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

  private static final String NEW_LINE = System.getProperty("line.separator");

  // FIXME: Find a better way to prevent hardcoded ldif entries.
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

  /** The DN of the configuration entry defining the LDAP connection handler. */
  private static final String DN_LDAP_CONNECTION_HANDLER = "cn=LDAP Connection Handler," + DN_CONNHANDLER_BASE;
  /** The DN of the configuration entry defining the Administration connector. */
  private static final String DN_ADMIN_CONNECTOR = "cn=Administration Connector," + DN_CONFIG_ROOT;
  /** The DN of the configuration entry defining the LDAPS connection handler. */
  private static final String DN_LDAPS_CONNECTION_HANDLER = "cn=LDAPS Connection Handler," + DN_CONNHANDLER_BASE;
  /** The DN of the configuration entry defining the HTTP connection handler. */
  private static final String DN_HTTP_CONNECTION_HANDLER =
      "cn=HTTP Connection Handler,cn=Connection Handlers,cn=config";
  /** The DN of the configuration entry defining the JMX connection handler. */
  private static final String DN_JMX_CONNECTION_HANDLER = "cn=JMX Connection Handler," + DN_CONNHANDLER_BASE;
  /** The DN of the configuration entry defining the initial root user. */
  private static final String DN_ROOT_USER = "cn=Directory Manager," + DN_ROOT_DN_CONFIG_BASE;
  /** The DN of the Crypto Manager. */
  private static final String DN_CRYPTO_MANAGER = "cn=Crypto Manager,cn=config";
  /** The DN of the DIGEST-MD5 SASL mechanism handler. */
  private static final String DN_DIGEST_MD5_SASL_MECHANISM = "cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config";

  private static final int SUCCESS = 0;
  private static final int ERROR = 1;

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
  private StringArgument configFile;
  private StringArgument rootDNString;
  private StringArgument rootPassword;
  private StringArgument keyManagerProviderDN;
  private StringArgument trustManagerProviderDN;
  private StringArgument certNickNames;
  private StringArgument keyManagerPath;
  private StringArgument serverRoot;
  private StringArgument backendType;

  private final String serverLockFileName = LockFileManager.getServerLockFileName();
  private final StringBuilder failureReason = new StringBuilder();
  private ConfigurationHandler configHandler;

  private ConfigureDS(final String[] args, final OutputStream outStream, final OutputStream errStream)
  {
    arguments = args;
    out = NullOutputStream.wrapOrNullStream(outStream);
    err = NullOutputStream.wrapOrNullStream(errStream);
    argParser = new ArgumentParser(ConfigureDS.class.getName(), INFO_CONFIGDS_TOOL_DESCRIPTION.get(), false);
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

      configHandler = DirectoryServer.getConfigurationHandler();

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
      printWrappedText(out, INFO_CONFIGDS_WROTE_UPDATED_CONFIG.get());

      return SUCCESS;
    }
    catch (final ConfigureDSException e)
    {
     if (e.isWrongUsage())
     {
       argParser.displayMessageAndUsageReference(err, e.getErrorMessage());
     }
     else
     {
       printWrappedText(err, e.getErrorMessage());
     }
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
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('c')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
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

      hostName =
              StringArgument.builder(OPTION_LONG_HOST)
                      .shortIdentifier(OPTION_SHORT_HOST)
                      .description(INFO_INSTALLDS_DESCRIPTION_HOST_NAME.get())
                      .defaultValue(defaultHostName)
                      .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      ldapPort =
              IntegerArgument.builder("ldapPort")
                      .shortIdentifier(OPTION_SHORT_PORT)
                      .description(INFO_CONFIGDS_DESCRIPTION_LDAP_PORT.get())
                      .range(1, 65535)
                      .defaultValue(389)
                      .valuePlaceholder(INFO_LDAPPORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      adminConnectorPort =
              IntegerArgument.builder("adminConnectorPort")
                      .description(INFO_INSTALLDS_DESCRIPTION_ADMINCONNECTORPORT.get())
                      .range(1, 65535)
                      .defaultValue(4444)
                      .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      ldapsPort =
              IntegerArgument.builder("ldapsPort")
                      .shortIdentifier('P')
                      .description(INFO_CONFIGDS_DESCRIPTION_LDAPS_PORT.get())
                      .range(1, 65535)
                      .defaultValue(636)
                      .valuePlaceholder(INFO_LDAPPORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      enableStartTLS =
              BooleanArgument.builder("enableStartTLS")
                      .shortIdentifier(OPTION_SHORT_START_TLS)
                      .description(INFO_CONFIGDS_DESCRIPTION_ENABLE_START_TLS.get())
                      .buildAndAddToParser(argParser);
      jmxPort =
              IntegerArgument.builder("jmxPort")
                      .shortIdentifier('x')
                      .description(INFO_CONFIGDS_DESCRIPTION_JMX_PORT.get())
                      .range(1, 65535)
                      .defaultValue(CliConstants.DEFAULT_JMX_PORT)
                      .valuePlaceholder(INFO_JMXPORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyManagerProviderDN =
              StringArgument.builder("keyManagerProviderDN")
                      .shortIdentifier('k')
                      .description(INFO_CONFIGDS_DESCRIPTION_KEYMANAGER_PROVIDER_DN.get())
                      .valuePlaceholder(INFO_KEY_MANAGER_PROVIDER_DN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustManagerProviderDN =
              StringArgument.builder("trustManagerProviderDN")
                      .shortIdentifier('t')
                      .description(INFO_CONFIGDS_DESCRIPTION_TRUSTMANAGER_PROVIDER_DN.get())
                      .valuePlaceholder(INFO_TRUST_MANAGER_PROVIDER_DN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyManagerPath =
              StringArgument.builder("keyManagerPath")
                      .shortIdentifier('m')
                      .description(INFO_CONFIGDS_DESCRIPTION_KEYMANAGER_PATH.get())
                      .valuePlaceholder(INFO_KEY_MANAGER_PATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      certNickNames =
              StringArgument.builder("certNickName")
                      .shortIdentifier('a')
                      .description(INFO_CONFIGDS_DESCRIPTION_CERTNICKNAME.get())
                      .multiValued()
                      .valuePlaceholder(INFO_NICKNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      baseDNString =
              StringArgument.builder(OPTION_LONG_BASEDN)
                      .shortIdentifier(OPTION_SHORT_BASEDN)
                      .description(INFO_CONFIGDS_DESCRIPTION_BASE_DN.get())
                      .multiValued()
                      .defaultValue("dc=example,dc=com")
                      .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      rootDNString =
              StringArgument.builder(OPTION_LONG_ROOT_USER_DN)
                      .shortIdentifier(OPTION_SHORT_ROOT_USER_DN)
                      .description(INFO_CONFIGDS_DESCRIPTION_ROOT_DN.get())
                      .defaultValue("cn=Directory Manager")
                      .valuePlaceholder(INFO_ROOT_USER_DN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      rootPassword =
              StringArgument.builder("rootPassword")
                      .shortIdentifier(OPTION_SHORT_BINDPWD)
                      .description(INFO_CONFIGDS_DESCRIPTION_ROOT_PW.get())
                      .valuePlaceholder(INFO_ROOT_USER_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      rootPasswordFile =
              FileBasedArgument.builder("rootPasswordFile")
                      .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                      .description(INFO_CONFIGDS_DESCRIPTION_ROOT_PW_FILE.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);

      serverRoot =
              StringArgument.builder(OPTION_LONG_SERVER_ROOT)
                      .shortIdentifier(OPTION_SHORT_SERVER_ROOT)
                      .hidden()
                      .valuePlaceholder(INFO_SERVER_ROOT_DIR_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backendType =
              StringArgument.builder(OPTION_LONG_BACKEND_TYPE)
                      .description(INFO_INSTALLDS_DESCRIPTION_BACKEND_TYPE.get())
                      .valuePlaceholder(INFO_INSTALLDS_BACKEND_TYPE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
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
      directoryServer.initializeConfiguration(configFile.getValue());
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

  private LinkedList<DN> parseProvidedBaseDNs() throws ConfigureDSException
  {
    LinkedList<DN> baseDNs = new LinkedList<>();
    if (baseDNString.isPresent())
    {
      for (final String dnString : baseDNString.getValues())
      {
        try
        {
          baseDNs.add(DN.valueOf(dnString));
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
      catch (final LocalizedIllegalArgumentException e)
      {
        final LocalizableMessage msg = ERR_CONFIGDS_CANNOT_PARSE_ROOT_DN.get(
            rootDNString.getValue(), e.getMessageObject());
        throw new ConfigureDSException(e, msg);
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
      catch (final LocalizedIllegalArgumentException e)
      {
        final String value = trustManagerProviderDN.getValue();
        final LocalizableMessage errorMessage = e.getMessageObject();
        final LocalizableMessage message =
            isKeyManager ? ERR_CONFIGDS_CANNOT_PARSE_KEYMANAGER_PROVIDER_DN.get(value, errorMessage)
                         : ERR_CONFIGDS_CANNOT_PARSE_TRUSTMANAGER_PROVIDER_DN.get(value, errorMessage);
        throw new ConfigureDSException(e, message);
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
            configHandler.addEntry(Converters.from(mangerConfigEntry));
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
          configHandler.getEntry(dn);
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
  private void updateBaseDNs(final List<DN> baseDNs) throws ConfigureDSException
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
        updateConfigEntryWithAttribute(
            DN_LDAP_CONNECTION_HANDLER, ATTR_LISTEN_PORT,
            CoreSchema.getIntegerSyntax(),
            ldapPort.getIntValue());
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
        updateConfigEntryWithAttribute(
            DN_ADMIN_CONNECTOR,
            ATTR_LISTEN_PORT,
            CoreSchema.getIntegerSyntax(),
            adminConnectorPort.getIntValue());
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
        updateConfigEntryWithAttribute(
            DN_LDAPS_CONNECTION_HANDLER,
            ATTR_LISTEN_PORT,
            CoreSchema.getIntegerSyntax(),
            ldapsPort.getIntValue());

        updateConfigEntryWithAttribute(
            DN_LDAPS_CONNECTION_HANDLER,
            ATTR_CONNECTION_HANDLER_ENABLED,
            CoreSchema.getBooleanSyntax(),
            ServerConstants.TRUE_VALUE);
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
        updateConfigEntryWithAttribute(
            DN_JMX_CONNECTION_HANDLER,
            ATTR_LISTEN_PORT,
            CoreSchema.getIntegerSyntax(),
            jmxPort.getIntValue());

        updateConfigEntryWithAttribute(
            DN_JMX_CONNECTION_HANDLER,
            ATTR_CONNECTION_HANDLER_ENABLED,
            CoreSchema.getBooleanSyntax(),
            ServerConstants.TRUE_VALUE);
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
        updateConfigEntryWithAttribute(
            DN_LDAP_CONNECTION_HANDLER,
            ATTR_ALLOW_STARTTLS,
            CoreSchema.getBooleanSyntax(),
            ServerConstants.TRUE_VALUE);
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
          updateConfigEntryWithAttribute(
              keyManagerProviderDN.getValue(),
              ATTR_KEYMANAGER_ENABLED,
              CoreSchema.getBooleanSyntax(),
              ServerConstants.TRUE_VALUE);
        }
        catch (final Exception e)
        {
          throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_ENABLE_KEYMANAGER.get(e));
        }
      }

      putKeyManagerConfigAttribute(enableStartTLS, DN_LDAP_CONNECTION_HANDLER);
      putKeyManagerConfigAttribute(ldapsPort, DN_LDAPS_CONNECTION_HANDLER);
      putKeyManagerConfigAttribute(ldapsPort, DN_HTTP_CONNECTION_HANDLER);

      if (keyManagerPath.isPresent())
      {
        try
        {
          updateConfigEntryWithAttribute(
              keyManagerProviderDN.getValue(),
              ATTR_KEYSTORE_FILE,
              CoreSchema.getDirectoryStringSyntax(),
              keyManagerPath.getValue());
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
        updateConfigEntryWithAttribute(
            attributeDN,
            ATTR_KEYMANAGER_DN,
            CoreSchema.getDirectoryStringSyntax(),
            keyManagerProviderDN.getValue());
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
          updateConfigEntryWithAttribute(
              trustManagerProviderDN.getValue(),
              ATTR_TRUSTMANAGER_ENABLED,
              CoreSchema.getBooleanSyntax(),
              ServerConstants.TRUE_VALUE);
        }
        catch (final Exception e)
        {
          throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_ENABLE_TRUSTMANAGER.get(e));
        }
      }
      putTrustManagerAttribute(enableStartTLS, DN_LDAP_CONNECTION_HANDLER);
      putTrustManagerAttribute(ldapsPort, DN_LDAPS_CONNECTION_HANDLER);
      putTrustManagerAttribute(ldapsPort, DN_HTTP_CONNECTION_HANDLER);
    }

    if (certNickNames.isPresent())
    {
      final List<String> attrValues = certNickNames.getValues();
      updateCertNicknameEntry(ldapPort, DN_LDAP_CONNECTION_HANDLER, ATTR_SSL_CERT_NICKNAME, attrValues);
      updateCertNicknameEntry(ldapsPort, DN_LDAPS_CONNECTION_HANDLER, ATTR_SSL_CERT_NICKNAME, attrValues);
      updateCertNicknameEntry(certNickNames, DN_HTTP_CONNECTION_HANDLER, ATTR_SSL_CERT_NICKNAME, attrValues);
      updateCertNicknameEntry(jmxPort, DN_JMX_CONNECTION_HANDLER, ATTR_SSL_CERT_NICKNAME, attrValues);
    }
    else
    {
      // Use the key manager specified for connection handlers
      removeSSLCertNicknameAttribute(DN_LDAP_CONNECTION_HANDLER);
      removeSSLCertNicknameAttribute(DN_LDAPS_CONNECTION_HANDLER);
      removeSSLCertNicknameAttribute(DN_HTTP_CONNECTION_HANDLER);
      removeSSLCertNicknameAttribute(DN_JMX_CONNECTION_HANDLER);
    }
  }

  private void putTrustManagerAttribute(final Argument arg, final String attributeDN) throws ConfigureDSException
  {
    if (arg.isPresent())
    {
      try
      {
        updateConfigEntryWithAttribute(
            attributeDN,
            ATTR_TRUSTMANAGER_DN,
            CoreSchema.getDirectoryStringSyntax(),
            trustManagerProviderDN.getValue());
      }
      catch (final Exception e)
      {
        throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_TRUSTMANAGER_REFERENCE.get(e));
      }
    }
  }

  private void updateCertNicknameEntry(final Argument arg, final String attributeDN,
      final String attrName, final List<String> attrValues) throws ConfigureDSException
  {
    try
    {
      if (arg.isPresent())
      {
        updateConfigEntryWithAttribute(
            attributeDN,
            attrName,
            CoreSchema.getDirectoryStringSyntax(),
            attrValues.toArray(new Object[attrValues.size()]));
      }
      else
      {
        updateConfigEntryByRemovingAttribute(attributeDN, ATTR_SSL_CERT_NICKNAME);
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
      updateConfigEntryByRemovingAttribute(attributeDN, ATTR_SSL_CERT_NICKNAME);
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
        updateConfigEntryWithAttribute(
            DN_ROOT_USER,
            ATTR_ROOTDN_ALTERNATE_BIND_DN,
            CoreSchema.getDirectoryStringSyntax(),
            rootDN);
        final String encodedPassword = SaltedSHA512PasswordStorageScheme.encodeOffline(getBytes(rootPW));
        updateConfigEntryWithAttribute(
            DN_ROOT_USER,
            ATTR_USER_PASSWORD,
            CoreSchema.getDirectoryStringSyntax(),
            encodedPassword);
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
      updateConfigEntryWithAttribute(
          DN_DIGEST_MD5_SASL_MECHANISM,
          "ds-cfg-server-fqdn",
          CoreSchema.getDirectoryStringSyntax(),
          hostName.getValue());
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
            updateConfigEntryWithAttribute(
                DN_CRYPTO_MANAGER,
                ATTR_CRYPTO_CIPHER_KEY_WRAPPING_TRANSFORMATION,
                CoreSchema.getDirectoryStringSyntax(),
                alternativeCipher);
          }
          catch (final Exception e)
          {
            throw new ConfigureDSException(e, ERR_CONFIGDS_CANNOT_UPDATE_CRYPTO_MANAGER.get(e));
          }
        }
      }
    }
  }

  /** Update a config entry with the provided attribute parameters. */
  private void updateConfigEntryWithAttribute(String entryDn, String attributeName, Syntax syntax, Object...values)
      throws DirectoryException, ConfigException
  {
    org.forgerock.opendj.ldap.Entry configEntry = configHandler.getEntry(DN.valueOf(entryDn));
    final org.forgerock.opendj.ldap.Entry newEntry = putAttribute(configEntry, attributeName, syntax, values);
    configHandler.replaceEntry(configEntry, newEntry);
  }

  /** Update a config entry by removing the provided attribute. */
  private void updateConfigEntryByRemovingAttribute(String entryDn, String attributeName)
      throws DirectoryException, ConfigException
  {
    final org.forgerock.opendj.ldap.Entry configEntry = configHandler.getEntry(DN.valueOf(entryDn));
    final Entry newEntry = removeAttribute(Converters.to(configEntry), attributeName);
    configHandler.replaceEntry(configEntry, Converters.from(newEntry));
  }

  /**
   * Duplicate the provided entry, and put an attribute to the duplicated entry.
   * <p>
   * Provided entry is not modified.
   *
   *  @return the duplicate entry, modified with the attribute
   */
  private org.forgerock.opendj.ldap.Entry putAttribute(
      org.forgerock.opendj.ldap.Entry configEntry, String attrName, Syntax syntax, Object...values)
  {
    org.forgerock.opendj.ldap.Entry newEntry = LinkedHashMapEntry.deepCopyOfEntry(configEntry);
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType(attrName, syntax);
    newEntry.replaceAttribute(new LinkedAttribute(AttributeDescription.create(attrType), values));
    return newEntry;
  }

  /**
   * Duplicate the provided entry, and remove an attribute to the duplicated entry.
   * <p>
   * Provided entry is not modified.
   *
   *  @return the duplicate entry, with removed attribute
   */
  private Entry removeAttribute(Entry entry, String attrName)
  {
    Entry duplicateEntry = entry.duplicate(false);
    for (AttributeType t : entry.getUserAttributes().keySet())
    {
      if (t.hasNameOrOID(attrName))
      {
        entry.getUserAttributes().remove(t);
        return duplicateEntry;
      }
    }

    for (AttributeType t : entry.getOperationalAttributes().keySet())
    {
      if (t.hasNameOrOID(attrName))
      {
        entry.getOperationalAttributes().remove(t);
        return duplicateEntry;
      }
    }
    return duplicateEntry;
  }

  /**
   * Returns a cipher that is supported by the JVM we are running at.
   * Returns <CODE>null</CODE> if no alternative cipher could be found.
   * @return a cipher that is supported by the JVM we are running at.
   */
  private static String getAlternativeCipher()
  {
    final String[] preferredAlternativeCiphers =
    {
        "RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING",
        "RSA/ECB/PKCS1Padding"
    };
    for (final String cipher : preferredAlternativeCiphers)
    {
      try
      {
        Cipher.getInstance(cipher);
        return cipher;
      }
      catch (final Throwable ignored)
      {
        // ignored
      }
    }
    return null;
  }
}
