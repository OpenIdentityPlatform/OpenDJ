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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.admin.client.cli;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.ReturnCode.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.FileBasedTrustManagerProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.TrustManagerProviderCfg;
import org.opends.server.core.DirectoryServer;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This is a commodity class that can be used to check the arguments required to
 * establish a secure connection in the command line. It can be used to generate
 * an ApplicationTrustManager object based on the options provided by the user
 * in the command line.
 */
public final class SecureConnectionCliArgs
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The 'hostName' global argument. */
  public StringArgument hostNameArg;

  /** The 'port' global argument. */
  public IntegerArgument portArg;

  /** The 'bindDN' global argument. */
  public StringArgument bindDnArg;

  /** The 'adminUID' global argument. */
  public StringArgument adminUidArg;

  /** The 'bindPasswordFile' global argument. */
  public FileBasedArgument bindPasswordFileArg;

  /** The 'bindPassword' global argument. */
  public StringArgument bindPasswordArg;

  /** The 'trustAllArg' global argument. */
  public BooleanArgument trustAllArg;

  /** The 'trustStore' global argument. */
  public StringArgument trustStorePathArg;

  /** The 'trustStorePassword' global argument. */
  public StringArgument trustStorePasswordArg;

  /** The 'trustStorePasswordFile' global argument. */
  public FileBasedArgument trustStorePasswordFileArg;

  /** The 'keyStore' global argument. */
  public StringArgument keyStorePathArg;

  /** The 'keyStorePassword' global argument. */
  public StringArgument keyStorePasswordArg;

  /** The 'keyStorePasswordFile' global argument. */
  public FileBasedArgument keyStorePasswordFileArg;

  /** The 'certNicknameArg' global argument. */
  public StringArgument certNicknameArg;

  /** The 'useSSLArg' global argument. */
  public BooleanArgument useSSLArg;

  /** The 'useStartTLSArg' global argument. */
  public BooleanArgument useStartTLSArg;

  /** Argument indicating a SASL option. */
  public StringArgument saslOptionArg;

  /** Argument to specify the connection timeout. */
  public IntegerArgument connectTimeoutArg;

  /** Private container for global arguments. */
  private Set<Argument> argList;

  /** The trust manager. */
  private ApplicationTrustManager trustManager;

  private boolean configurationInitialized;

  /** Defines if the CLI always use the SSL connection type. */
  private boolean alwaysSSL;

  /**
   * Creates a new instance of secure arguments.
   *
   * @param alwaysSSL
   *          If true, always use the SSL connection type. In this case, the
   *          arguments useSSL and startTLS are not present.
   */
  public SecureConnectionCliArgs(boolean alwaysSSL)
  {
    this.alwaysSSL = alwaysSSL;
  }

  /**
   * Indicates whether or not any of the arguments are present.
   *
   * @return boolean where true indicates that at least one of the arguments is
   *         present
   */
  public boolean argumentsPresent()
  {
    if (argList != null)
    {
      for (Argument arg : argList)
      {
        if (arg.isPresent())
        {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Get the admin UID which has to be used for the command.
   *
   * @return The admin UID specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getAdministratorUID()
  {
    if (adminUidArg.isPresent())
    {
      return adminUidArg.getValue();
    }
    return adminUidArg.getDefaultValue();
  }

  /**
   * Tells whether this parser uses the Administrator UID (instead of the bind
   * DN) or not.
   *
   * @return {@code true} if this parser uses the Administrator UID and
   *         {@code false} otherwise.
   */
  public boolean useAdminUID()
  {
    return !adminUidArg.isHidden();
  }

  /**
   * Get the bindDN which has to be used for the command.
   *
   * @return The bindDN specified by the command line argument, or the default
   *         value, if not specified.
   */
  public String getBindDN()
  {
    if (bindDnArg.isPresent())
    {
      return bindDnArg.getValue();
    }
    return bindDnArg.getDefaultValue();
  }

  /**
   * Initialize Global option.
   *
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used to create
   *           this argument.
   * @return a ArrayList with the options created.
   */
  public Set<Argument> createGlobalArguments() throws ArgumentException
  {
    argList = new LinkedHashSet<>();

    useSSLArg = CommonArguments.getUseSSL();
    if (!alwaysSSL)
    {
      argList.add(useSSLArg);
    }
    else
    {
      // simulate that the useSSL arg has been given in the CLI
      useSSLArg.setPresent(true);
    }

    useStartTLSArg = CommonArguments.getStartTLS();
    if (!alwaysSSL)
    {
      argList.add(useStartTLSArg);
    }

    String defaultHostName;
    try
    {
      defaultHostName = InetAddress.getLocalHost().getHostName();
    }
    catch (Exception e)
    {
      defaultHostName = "Unknown (" + e + ")";
    }
    hostNameArg = CommonArguments.getHostName(defaultHostName);
    argList.add(hostNameArg);

    portArg = CommonArguments.getPort(AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT,
                                      alwaysSSL ? INFO_DESCRIPTION_ADMIN_PORT.get() : INFO_DESCRIPTION_PORT.get());
    argList.add(portArg);

    bindDnArg = CommonArguments.getBindDN(CliConstants.DEFAULT_ROOT_USER_DN);
    argList.add(bindDnArg);

    // It is up to the classes that required admin UID to make this argument
    // visible and add it.
    adminUidArg = new StringArgument("adminUID", 'I', OPTION_LONG_ADMIN_UID, false, false, true,
                                     INFO_ADMINUID_PLACEHOLDER.get(), CliConstants.GLOBAL_ADMIN_UID,
                                     null, INFO_DESCRIPTION_ADMIN_UID.get());
    adminUidArg.setPropertyName(OPTION_LONG_ADMIN_UID);
    adminUidArg.setHidden(true);

    bindPasswordArg = CommonArguments.getBindPassword();
    argList.add(bindPasswordArg);

    bindPasswordFileArg = CommonArguments.getBindPasswordFile();
    argList.add(bindPasswordFileArg);

    saslOptionArg = CommonArguments.getSASL();
    argList.add(saslOptionArg);

    trustAllArg = CommonArguments.getTrustAll();
    argList.add(trustAllArg);

    trustStorePathArg = CommonArguments.getTrustStorePath();
    argList.add(trustStorePathArg);

    trustStorePasswordArg = CommonArguments.getTrustStorePassword();
    argList.add(trustStorePasswordArg);

    trustStorePasswordFileArg = CommonArguments.getTrustStorePasswordFile();
    argList.add(trustStorePasswordFileArg);

    keyStorePathArg = CommonArguments.getKeyStorePath();
    argList.add(keyStorePathArg);

    keyStorePasswordArg = CommonArguments.getKeyStorePassword();
    argList.add(keyStorePasswordArg);

    keyStorePasswordFileArg = CommonArguments.getKeyStorePasswordFile();
    argList.add(keyStorePasswordFileArg);

    certNicknameArg = CommonArguments.getCertNickName();
    argList.add(certNicknameArg);

    connectTimeoutArg = CommonArguments.getConnectTimeOut();
    connectTimeoutArg.setHidden(false);
    argList.add(connectTimeoutArg);

    return argList;
  }

  /**
   * Get the host name which has to be used for the command.
   *
   * @return The host name specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getHostName()
  {
    if (hostNameArg.isPresent())
    {
      return hostNameArg.getValue();
    }
    return hostNameArg.getDefaultValue();
  }

  /**
   * Get the port which has to be used for the command.
   *
   * @return The port specified by the command line argument, or the default
   *         value, if not specified.
   */
  public String getPort()
  {
    if (portArg.isPresent())
    {
      return portArg.getValue();
    }
    return portArg.getDefaultValue();
  }

  /**
   * Indication if provided global options are validate.
   *
   * @param buf
   *          the LocalizableMessageBuilder to write the error messages.
   * @return return code.
   */
  public int validateGlobalOptions(LocalizableMessageBuilder buf)
  {
    List<LocalizableMessage> errors = new ArrayList<>();
    // Couldn't have at the same time bindPassword and bindPasswordFile
    if (bindPasswordArg.isPresent() && bindPasswordFileArg.isPresent())
    {
      errors.add(
          ERR_TOOL_CONFLICTING_ARGS.get(bindPasswordArg.getLongIdentifier(), bindPasswordFileArg.getLongIdentifier()));
    }

    // Couldn't have at the same time trustAll and trustStore related arg
    if (trustAllArg.isPresent() && trustStorePathArg.isPresent())
    {
      errors.add(
          ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg.getLongIdentifier(), trustStorePathArg.getLongIdentifier()));
    }
    if (trustAllArg.isPresent() && trustStorePasswordArg.isPresent())
    {
      errors.add(
          ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg.getLongIdentifier(), trustStorePasswordArg.getLongIdentifier()));
    }
    if (trustAllArg.isPresent() && trustStorePasswordFileArg.isPresent())
    {
      errors.add(
         ERR_TOOL_CONFLICTING_ARGS.get(trustAllArg.getLongIdentifier(), trustStorePasswordFileArg.getLongIdentifier()));
    }

    // Couldn't have at the same time trustStorePasswordArg and
    // trustStorePasswordFileArg
    if (trustStorePasswordArg.isPresent() && trustStorePasswordFileArg.isPresent())
    {
      LocalizableMessage message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustStorePasswordArg.getLongIdentifier(), trustStorePasswordFileArg.getLongIdentifier());
      errors.add(message);
    }
    checkIfPathArgumentIsReadable(
        trustStorePathArg, errors, ERR_CANNOT_READ_TRUSTSTORE.get(trustStorePathArg.getValue()));
    checkIfPathArgumentIsReadable(
        keyStorePathArg, errors, ERR_CANNOT_READ_KEYSTORE.get(keyStorePasswordArg.getValue()));
    // Couldn't have at the same time startTLSArg and useSSLArg
    if (useStartTLSArg.isPresent() && useSSLArg.isPresent())
    {
      errors.add(ERR_TOOL_CONFLICTING_ARGS.get(useStartTLSArg.getLongIdentifier(), useSSLArg.getLongIdentifier()));
    }
    if (!errors.isEmpty())
    {
      for (LocalizableMessage error : errors)
      {
        if (buf.length() > 0)
        {
          buf.append(LINE_SEPARATOR);
        }
        buf.append(error);
      }
      return CONFLICTING_ARGS.get();
    }

    return SUCCESS.get();
  }

  private void checkIfPathArgumentIsReadable(
      StringArgument pathArgument, List<LocalizableMessage> errors, LocalizableMessage errorMessage)
  {
    if (pathArgument.isPresent() && !canRead(pathArgument.getValue()))
    {
      errors.add(errorMessage);
    }
  }

  /**
   * Indicate if the SSL mode is required.
   *
   * @return True if SSL mode is required
   */
  public boolean useSSL()
  {
    return useSSLArg.isPresent() || alwaysSSL();
  }

  /**
   * Indicate if the startTLS mode is required.
   *
   * @return True if startTLS mode is required
   */
  public boolean useStartTLS()
  {
    return useStartTLSArg.isPresent();
  }

  /**
   * Indicate if the SSL mode is always used.
   *
   * @return True if SSL mode is always used.
   */
  public boolean alwaysSSL()
  {
    return alwaysSSL;
  }

  /**
   * Handle TrustStore.
   *
   * @return The trustStore manager to be used for the command.
   */
  public ApplicationTrustManager getTrustManager()
  {
    if (trustManager == null)
    {
      KeyStore truststore = null;
      if (trustAllArg.isPresent())
      {
        // Running a null TrustManager  will force createLdapsContext and
        // createStartTLSContext to use a bindTrustManager.
        return null;
      }
      else if (trustStorePathArg.isPresent())
      {
        try (final FileInputStream fos = new FileInputStream(trustStorePathArg.getValue()))
        {
          String trustStorePasswordStringValue = null;
          if (trustStorePasswordArg.isPresent())
          {
            trustStorePasswordStringValue = trustStorePasswordArg.getValue();
          }
          else if (trustStorePasswordFileArg.isPresent())
          {
            trustStorePasswordStringValue = trustStorePasswordFileArg.getValue();
          }

          if (trustStorePasswordStringValue != null)
          {
            trustStorePasswordStringValue = System.getProperty("javax.net.ssl.trustStorePassword");
          }

          char[] trustStorePasswordValue = null;
          if (trustStorePasswordStringValue != null)
          {
            trustStorePasswordValue = trustStorePasswordStringValue.toCharArray();
          }

          truststore = KeyStore.getInstance(KeyStore.getDefaultType());
          truststore.load(fos, trustStorePasswordValue);
        }
        catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e)
        {
          // Nothing to do: if this occurs we will systematically refuse the
          // certificates.  Maybe we should avoid this and be strict, but we
          // are in a best effort mode.
          logger.warn(LocalizableMessage.raw("Error with the truststore"), e);
        }
      }
      trustManager = new ApplicationTrustManager(truststore);
    }
    return trustManager;
  }

  /**
   * Returns {@code true} if we can read on the provided path and {@code false}
   * otherwise.
   *
   * @param path
   *          the path.
   * @return {@code true} if we can read on the provided path and {@code false}
   *         otherwise.
   */
  private boolean canRead(String path)
  {
    final File file = new File(path);
    return file.exists() && file.canRead();
  }

  /**
   * Returns the absolute path of the trust store file that appears on the
   * config. Returns {@code null} if the trust store is not defined or it does
   * not exist.
   *
   * @return the absolute path of the trust store file that appears on the
   *         config.
   * @throws ConfigException
   *           if there is an error reading the configuration.
   */
  public String getTruststoreFileFromConfig() throws ConfigException
  {
    String truststoreFileAbsolute = null;
    TrustManagerProviderCfg trustManagerCfg = null;
    AdministrationConnectorCfg administrationConnectorCfg = null;

    boolean couldInitializeConfig = configurationInitialized;
    // Initialization for admin framework
    if (!configurationInitialized)
    {
      couldInitializeConfig = initializeConfiguration();
    }
    if (couldInitializeConfig)
    {
      // Get the Directory Server configuration handler and use it.
      RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
      administrationConnectorCfg = root.getAdministrationConnector();

      String trustManagerStr = administrationConnectorCfg.getTrustManagerProvider();
      trustManagerCfg = root.getTrustManagerProvider(trustManagerStr);
      if (trustManagerCfg instanceof FileBasedTrustManagerProviderCfg)
      {
        FileBasedTrustManagerProviderCfg fileBasedTrustManagerCfg = (FileBasedTrustManagerProviderCfg) trustManagerCfg;
        String truststoreFile = fileBasedTrustManagerCfg.getTrustStoreFile();
        // Check the file
        if (truststoreFile.startsWith(File.separator))
        {
          truststoreFileAbsolute = truststoreFile;
        }
        else
        {
          truststoreFileAbsolute = DirectoryServer.getInstanceRoot() + File.separator + truststoreFile;
        }
        File f = new File(truststoreFileAbsolute);
        if (!f.exists() || !f.canRead() || f.isDirectory())
        {
          truststoreFileAbsolute = null;
        }
        else
        {
          // Try to get the canonical path.
          try
          {
            truststoreFileAbsolute = f.getCanonicalPath();
          }
          catch (Throwable t)
          {
            // We can ignore this error.
          }
        }
      }
    }
    return truststoreFileAbsolute;
  }

  /**
   * Returns the admin port from the configuration.
   *
   * @return the admin port from the configuration.
   * @throws ConfigException
   *           if an error occurs reading the configuration.
   */
  public int getAdminPortFromConfig() throws ConfigException
  {
    // Initialization for admin framework
    boolean couldInitializeConfiguration = configurationInitialized;
    if (!configurationInitialized)
    {
      couldInitializeConfiguration = initializeConfiguration();
    }
    if (couldInitializeConfiguration)
    {
      RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
      return root.getAdministrationConnector().getListenPort();
    }
    else
    {
      return AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
    }
  }

  private boolean initializeConfiguration()
  {
    // check if the initialization is required
    try
    {
      ServerManagementContext.getInstance().getRootConfiguration().getAdministrationConnector();
    }
    catch (java.lang.Throwable th)
    {
      try
      {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
        DirectoryServer.getInstance().initializeConfiguration();
      }
      catch (Exception ex)
      {
        // do nothing
        return false;
      }
    }
    configurationInitialized = true;
    return true;
  }

  /**
   * Returns the port to be used according to the configuration and the
   * arguments provided by the user. This method should be called after the
   * arguments have been parsed.
   *
   * @return the port to be used according to the configuration and the
   *         arguments provided by the user.
   */
  public int getPortFromConfig()
  {
    int portNumber;
    if (alwaysSSL())
    {
      portNumber = AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
      // Try to get the port from the config file
      try
      {
        portNumber = getAdminPortFromConfig();
      }
      catch (ConfigException ex)
      {
        // Nothing to do
      }
    }
    else
    {
      portNumber = CliConstants.DEFAULT_SSL_PORT;
    }
    return portNumber;
  }

  /**
   * Updates the default values of the port and the trust store with what is
   * read in the configuration.
   *
   * @throws ConfigException
   *           if there is an error reading the configuration.
   */
  public void initArgumentsWithConfiguration() throws ConfigException
  {
    portArg.setDefaultValue(String.valueOf(getPortFromConfig()));

    String truststoreFileAbsolute = getTruststoreFileFromConfig();
    if (truststoreFileAbsolute != null)
    {
      trustStorePathArg.setDefaultValue(truststoreFileAbsolute);
    }
  }
}
