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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.admin.client.cli;

import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.ReturnCode.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;

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
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.server.AdministrationConnectorCfg;
import org.forgerock.opendj.server.config.server.FileBasedTrustManagerProviderCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.TrustManagerProviderCfg;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.server.config.AdministrationConnector;
import org.opends.server.core.DirectoryServer;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
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

  private StringArgument hostNameArg;
  private IntegerArgument portArg;
  private StringArgument bindDnArg;
  private StringArgument adminUidArg;
  private FileBasedArgument bindPasswordFileArg;
  private StringArgument bindPasswordArg;
  private BooleanArgument trustAllArg;
  private StringArgument trustStorePathArg;
  private StringArgument trustStorePasswordArg;
  private FileBasedArgument trustStorePasswordFileArg;
  private StringArgument keyStorePathArg;
  private StringArgument keyStorePasswordArg;
  private FileBasedArgument keyStorePasswordFileArg;
  private StringArgument certNicknameArg;
  private BooleanArgument useSSLArg;
  private BooleanArgument useStartTLSArg;
  private StringArgument saslOptionArg;
  private IntegerArgument connectTimeoutArg;

  /** Private container for global arguments. */
  private Set<Argument> argList;

  /** The trust manager. */
  private ApplicationTrustManager trustManager;

  private boolean configurationInitialized;

  /** Defines if the CLI always use the SSL connection type. */
  private final boolean alwaysSSL;

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
   * Indicates whether any of the arguments are present.
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

    useSSLArg = useSSLArgument();
    if (!alwaysSSL)
    {
      argList.add(useSSLArg);
    }
    else
    {
      // simulate that the useSSL arg has been given in the CLI
      useSSLArg.setPresent(true);
    }

    useStartTLSArg = startTLSArgument();
    if (!alwaysSSL)
    {
      argList.add(useStartTLSArg);
    }

    hostNameArg = hostNameArgument(getDefaultHostName());
    argList.add(hostNameArg);

    portArg = createPortArgument(AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT);
    argList.add(portArg);

    bindDnArg = bindDNArgument(CliConstants.DEFAULT_ROOT_USER_DN);
    argList.add(bindDnArg);

    // Classes that required admin UID to be not hidden must call createVisibleAdminUidArgument(localizedDescription)
    adminUidArg = adminUidHiddenArgument(INFO_DESCRIPTION_ADMIN_UID.get());

    bindPasswordArg = bindPasswordArgument();
    argList.add(bindPasswordArg);

    bindPasswordFileArg = bindPasswordFileArgument();
    argList.add(bindPasswordFileArg);

    saslOptionArg = saslArgument();
    argList.add(saslOptionArg);

    trustAllArg = trustAllArgument();
    argList.add(trustAllArg);

    trustStorePathArg = trustStorePathArgument();
    argList.add(trustStorePathArg);

    trustStorePasswordArg = trustStorePasswordArgument();
    argList.add(trustStorePasswordArg);

    trustStorePasswordFileArg = trustStorePasswordFileArgument();
    argList.add(trustStorePasswordFileArg);

    keyStorePathArg = keyStorePathArgument();
    argList.add(keyStorePathArg);

    keyStorePasswordArg = keyStorePasswordArgument();
    argList.add(keyStorePasswordArg);

    keyStorePasswordFileArg = keyStorePasswordFileArgument();
    argList.add(keyStorePasswordFileArg);

    certNicknameArg = certNickNameArgument();
    argList.add(certNicknameArg);

    connectTimeoutArg = connectTimeOutArgument();
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
   * Returns the current hostname.
   *
   * If the hostname resolution fails, this method returns {@literal "localhost"}.
   * @return the current hostname
     */
  public String getDefaultHostName() {
    try
    {
      return InetAddress.getLocalHost().getHostName();
    }
    catch (Exception e)
    {
      return "localhost";
    }
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
  int validateGlobalOptions(LocalizableMessageBuilder buf)
  {
    final List<LocalizableMessage> errors = new ArrayList<>();
    addErrorMessageIfArgumentsConflict(errors, bindPasswordArg, bindPasswordFileArg);
    addErrorMessageIfArgumentsConflict(errors, trustAllArg, trustStorePathArg);
    addErrorMessageIfArgumentsConflict(errors, trustAllArg, trustStorePasswordArg);
    addErrorMessageIfArgumentsConflict(errors, trustAllArg, trustStorePasswordFileArg);
    addErrorMessageIfArgumentsConflict(errors, trustStorePasswordArg, trustStorePasswordFileArg);
    addErrorMessageIfArgumentsConflict(errors, useStartTLSArg, useSSLArg);

    checkIfPathArgumentIsReadable(errors, trustStorePathArg, ERR_CANNOT_READ_TRUSTSTORE);
    checkIfPathArgumentIsReadable(errors, keyStorePathArg, ERR_CANNOT_READ_KEYSTORE);

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

  private void checkIfPathArgumentIsReadable(List<LocalizableMessage> errors, StringArgument pathArg, Arg1<Object> msg)
  {
    if (pathArg.isPresent() && !canRead(pathArg.getValue()))
    {
      errors.add(msg.get(pathArg.getValue()));
    }
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
      RootCfg root = DirectoryServer.getInstance().getServerContext().getRootConfig();
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
      RootCfg root = DirectoryServer.getInstance().getServerContext().getRootConfig();
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
      DirectoryServer.getInstance().getServerContext().getRootConfig().getAdministrationConnector();
    }
    catch (Throwable th)
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
   * @param parser
   *        The argument parser where the secure connection arguments were added.
   */
  public void initArgumentsWithConfiguration(final ArgumentParser parser) {
    try
    {
      portArg = createPortArgument(getPortFromConfig());
      trustStorePathArg = trustStorePathArgument(getTruststoreFileFromConfig());
      parser.replaceArgument(portArg);
      parser.replaceArgument(trustStorePathArg);
    }
    catch (ConfigException | ArgumentException e)
    {
      logger.error(LocalizableMessage.raw(
              "Internal error while reading arguments of this program from configuration"), e);
    }
  }

  /**
   * Replace the admin UID argument by a non hidden one.
   *
   * @param description
   *         The localized description for the non hidden admin UID argument.
   */
  public void createVisibleAdminUidArgument(final LocalizableMessage description)
  {
    try
    {
      this.adminUidArg = adminUid(description);
    }
    catch (final ArgumentException unexpected)
    {
      throw new RuntimeException("Unexpected");
    }
  }

  private IntegerArgument createPortArgument(final int defaultValue) throws ArgumentException
  {
    return portArgument(
            defaultValue, alwaysSSL ? INFO_DESCRIPTION_ADMIN_PORT.get() : INFO_DESCRIPTION_PORT.get());
  }

  /**
   * Return the 'keyStore' global argument.
   *
   * @return The 'keyStore' global argument.
   */
  public StringArgument getKeyStorePathArg() {
    return keyStorePathArg;
  }

  /**
   * Return the 'hostName' global argument.
   *
   * @return The 'hostName' global argument.
   */
  public StringArgument getHostNameArg() {
    return hostNameArg;
  }

  /**
   * Return the 'port' global argument.
   *
   * @return The 'port' global argument.
   */
  public IntegerArgument getPortArg() {
    return portArg;
  }

  /**
   * Return the 'bindDN' global argument.
   *
   * @return The 'bindDN' global argument.
   */
  public StringArgument getBindDnArg() {
    return bindDnArg;
  }

  /**
   * Return the 'adminUID' global argument.
   *
   * @return The 'adminUID' global argument.
   */
  public StringArgument getAdminUidArg() {
    return adminUidArg;
  }

  /**
   * Return the 'bindPasswordFile' global argument.
   *
   * @return The 'bindPasswordFile' global argument.
   */
  public FileBasedArgument getBindPasswordFileArg() {
    return bindPasswordFileArg;
  }

  /**
   * Return the 'bindPassword' global argument.
   *
   * @return The 'bindPassword' global argument.
   */
  public StringArgument getBindPasswordArg() {
    return bindPasswordArg;
  }

  /**
   * Return the 'trustAllArg' global argument.
   *
   * @return The 'trustAllArg' global argument.
   */
  public BooleanArgument getTrustAllArg() {
    return trustAllArg;
  }

  /**
   * Return the 'trustStore' global argument.
   *
   * @return The 'trustStore' global argument.
   */
  public StringArgument getTrustStorePathArg() {
    return trustStorePathArg;
  }

  /**
   * Return the 'trustStorePassword' global argument.
   *
   * @return The 'trustStorePassword' global argument.
   */
  public StringArgument getTrustStorePasswordArg() {
    return trustStorePasswordArg;
  }

  /**
   * Return the 'trustStorePasswordFile' global argument.
   *
   * @return The 'trustStorePasswordFile' global argument.
   */
  public FileBasedArgument getTrustStorePasswordFileArg() {
    return trustStorePasswordFileArg;
  }

  /**
   * Return the 'keyStorePassword' global argument.
   *
   * @return The 'keyStorePassword' global argument.
   */
  public StringArgument getKeyStorePasswordArg() {
    return keyStorePasswordArg;
  }

  /**
   * Return the 'keyStorePasswordFile' global argument.
   *
   * @return The 'keyStorePasswordFile' global argument.
   */
  public FileBasedArgument getKeyStorePasswordFileArg() {
    return keyStorePasswordFileArg;
  }

  /**
   * Return the 'certNicknameArg' global argument.
   *
   * @return The 'certNicknameArg' global argument.
   */
  public StringArgument getCertNicknameArg() {
    return certNicknameArg;
  }

  /**
   * Return the 'useSSLArg' global argument.
   *
   * @return The 'useSSLArg' global argument.
   */
  public BooleanArgument getUseSSLArg() {
    return useSSLArg;
  }

  /**
   * Return the 'useStartTLSArg' global argument.
   *
   * @return The 'useStartTLSArg' global argument.
   */
  public BooleanArgument getUseStartTLSArg() {
    return useStartTLSArg;
  }

  /**
   * Return the 'saslOption' argument.
   *
   * @return the 'saslOption' argument.
   */
  public StringArgument getSaslOptionArg() {
    return saslOptionArg;
  }

  /**
   * Return the 'connectTimeout' argument.
   *
   * @return the 'connectTimeout' argument.
   */
  public IntegerArgument getConnectTimeoutArg() {
    return connectTimeoutArg;
  }

  /**
   * Set the bind DN argument with the provided description.
   * Note that this method will create a new {@link Argument} instance replacing the current one.
   *
   * @param description
   *         The localized description which will be used in help messages.
   */
  public void setBindDnArgDescription(final LocalizableMessage description)
  {
    try
    {
      this.bindDnArg = bindDNArgument(CliConstants.DEFAULT_ROOT_USER_DN, description);
    }
    catch (final ArgumentException unexpected)
    {
      throw new RuntimeException("unexpected");
    }
  }

  /**
   * Set the bind password argument.
   *
   * @param bindPasswordArg
   *         The argument which will replace the current one.
   */
  public void setBindPasswordArgument(final StringArgument bindPasswordArg)
  {
    this.bindPasswordArg = bindPasswordArg;
  }

  /**
   * Set the bind password file argument.
   *
   * @param bindPasswordFileArg
   *         The argument which will replace the current one.
   */
  public void setBindPasswordFileArgument(final FileBasedArgument bindPasswordFileArg)
  {
    this.bindPasswordFileArg = bindPasswordFileArg;
  }
}
