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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.util.cli;

import static com.forgerock.opendj.cli.Utils.portValidationCallback;
import static com.forgerock.opendj.cli.Utils.isDN;
import static com.forgerock.opendj.cli.Utils.getAdministratorDN;
import static com.forgerock.opendj.cli.Utils.getThrowableMsg;
import static org.opends.messages.ToolMessages.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.FileBasedArgument;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.admin.ads.util.ApplicationKeyManager;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.tools.LDAPConnectionOptions;
import org.opends.server.tools.SSLConnectionException;
import org.opends.server.tools.SSLConnectionFactory;
import org.opends.server.util.CollectionUtils;
import org.opends.server.util.SelectableCertificateKeyManager;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.Menu;
import com.forgerock.opendj.cli.MenuBuilder;
import com.forgerock.opendj.cli.MenuResult;
import com.forgerock.opendj.cli.ValidationCallback;

/**
 * Supports interacting with a user through the command line to prompt for
 * information necessary to create an LDAP connection.
 *
 * Actually the LDAPConnectionConsoleInteraction is used by UninstallCliHelper, StatusCli,
 * LDAPManagementContextFactory and ReplicationCliMain.
 */
public class LDAPConnectionConsoleInteraction
{

  private static final Protocol DEFAULT_PROMPT_PROTOCOL = Protocol.SSL;
  private static final TrustMethod DEFAULT_PROMPT_TRUST_METHOD = TrustMethod.DISPLAY_CERTIFICATE;
  private static final TrustOption DEFAULT_PROMPT_TRUST_OPTION = TrustOption.SESSION;

  private static final boolean ALLOW_EMPTY_PATH = true;
  private static final boolean FILE_MUST_EXISTS = true;
  private boolean allowAnonymousIfNonInteractive;

  /**
   * Information from the latest console interaction.
   * TODO: should it extend MonoServerReplicationUserData or a subclass?
   */
  private static class State
  {
    private boolean useSSL;
    private boolean useStartTLS;
    private String hostName;
    private String bindDN;
    private String providedBindDN;
    private String adminUID;
    private String providedAdminUID;
    private String bindPassword;
    /** The timeout to be used to connect. */
    private int connectTimeout;
    /** Indicate if we need to display the heading. */
    private boolean isHeadingDisplayed;

    private ApplicationTrustManager trustManager;
    /** Indicate if the trust store in in memory. */
    private boolean trustStoreInMemory;
    /** Indicate if the all certificates are accepted. */
    private boolean trustAll;
    /** Indicate that the trust manager was created with the parameters provided. */
    private boolean trustManagerInitialized;
    /** The trust store to use for the SSL or STARTTLS connection. */
    private KeyStore truststore;
    private String truststorePath;
    private String truststorePassword;

    private KeyManager keyManager;
    private String keyStorePath;
    private String keystorePassword;
    private String certifNickname;

    private State(SecureConnectionCliArgs secureArgs)
    {
      setSsl(secureArgs);
      trustAll = secureArgs.getTrustAllArg().isPresent();
    }

    protected LocalizableMessage getPrompt()
    {
      if (providedAdminUID != null)
      {
        return INFO_LDAPAUTH_PASSWORD_PROMPT.get(providedAdminUID);
      }
      else if (providedBindDN != null)
      {
        return INFO_LDAPAUTH_PASSWORD_PROMPT.get(providedBindDN);
      }
      else if (bindDN != null)
      {
        return INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDN);
      }

      return INFO_LDAPAUTH_PASSWORD_PROMPT.get(adminUID);
    }

    protected String getAdminOrBindDN()
    {
      if (providedBindDN != null)
      {
        return providedBindDN;
      }
      else if (providedAdminUID != null)
      {
        return getAdministratorDN(providedAdminUID);
      }
      else if (bindDN != null)
      {
        return bindDN;
      }
      else if (adminUID != null)
      {
        return getAdministratorDN(adminUID);
      }

      return null;
    }

    private void setSsl(final SecureConnectionCliArgs secureArgs)
    {
      this.useSSL = secureArgs.alwaysSSL() || secureArgs.getUseSSLArg().isPresent();
      this.useStartTLS = secureArgs.getUseStartTLSArg().isPresent();
    }
  }

  /** The console application. */
  private ConsoleApplication app;

  private State state;

  /** The SecureConnectionCliArgsList object. */
  private final SecureConnectionCliArgs secureArgsList;

  /** The command builder that we can return with the connection information. */
  private CommandBuilder commandBuilder;

  /** A copy of the secureArgList for convenience. */
  private SecureConnectionCliArgs copySecureArgsList;

  /**
   * Boolean that tells if we must propose LDAP if it is available even if the
   * user provided certificate parameters.
   */
  private boolean displayLdapIfSecureParameters;

  private int portNumber;

  private LocalizableMessage heading = INFO_LDAP_CONN_HEADING_CONNECTION_PARAMETERS.get();

  /** Boolean that tells if we ask for bind DN or admin UID in the same prompt. */
  private boolean useAdminOrBindDn;

  /** Enumeration description protocols for interactive CLI choices. */
  private enum Protocol
  {
    LDAP(INFO_LDAP_CONN_PROMPT_SECURITY_LDAP.get()),
    SSL(INFO_LDAP_CONN_PROMPT_SECURITY_USE_SSL.get()),
    START_TLS(INFO_LDAP_CONN_PROMPT_SECURITY_USE_START_TLS.get());

    private final LocalizableMessage message;

    Protocol(final LocalizableMessage message)
    {
      this.message = message;
    }

    private int getChoice()
    {
      return ordinal() + 1;
    }
  }

  /** Enumeration description protocols for interactive CLI choices. */
  private enum TrustMethod
  {
    TRUSTALL(INFO_LDAP_CONN_PROMPT_SECURITY_USE_TRUST_ALL.get()),
    TRUSTSTORE(INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE.get()),
    DISPLAY_CERTIFICATE(INFO_LDAP_CONN_PROMPT_SECURITY_MANUAL_CHECK.get());

    private LocalizableMessage message;

    TrustMethod(final LocalizableMessage message)
    {
      this.message = message;
    }

    private int getChoice()
    {
      return ordinal() + 1;
    }

    private static TrustMethod getTrustMethodForIndex(final int value)
    {
      for (final TrustMethod trustMethod : TrustMethod.values())
      {
        if (trustMethod.getChoice() == value)
        {
          return trustMethod;
        }
      }
      return null;
    }
  }

  /** Enumeration description server certificate trust option. */
  private enum TrustOption
  {
    UNTRUSTED(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_NO.get()),
    SESSION(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_SESSION.get()),
    PERMAMENT(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_ALWAYS.get()),
    CERTIFICATE_DETAILS(INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_DETAILS.get());

    private LocalizableMessage message;

    TrustOption(final LocalizableMessage message)
    {
      this.message = message;
    }

    private int getChoice()
    {
      return ordinal() + 1;
    }

    private static TrustOption getTrustOptionForIndex(final int value)
    {
      for (final TrustOption trustOption : TrustOption.values())
      {
        if (trustOption.getChoice() == value)
        {
          return trustOption;
        }
      }
      return null;
    }
  }

  /**
   * Constructs a new console interaction.
   *
   * @param app
   *          console application
   * @param secureArgs
   *          existing set of arguments that have already been parsed and
   *          contain some potential command line specified LDAP arguments
   */
  public LDAPConnectionConsoleInteraction(ConsoleApplication app, SecureConnectionCliArgs secureArgs)
  {
    this(app, secureArgs, false);
  }

  /**
   * Constructs a new console interaction.
   *
   * @param app
   *          console application
   * @param secureArgs
   *          existing set of arguments that have already been parsed and
   *          contain some potential command line specified LDAP arguments
   * @param allowAnonymousIfNonInteractive
   *          If this console interaction should allow anonymous user in non interactive mode.
   *          If console application is interactive, the user will always be prompted for credentials.
   */
  public LDAPConnectionConsoleInteraction(
      ConsoleApplication app, SecureConnectionCliArgs secureArgs, final boolean allowAnonymousIfNonInteractive)
  {
    this.app = app;
    this.secureArgsList = secureArgs;
    this.commandBuilder = new CommandBuilder();
    this.allowAnonymousIfNonInteractive = allowAnonymousIfNonInteractive;
    state = new State(secureArgs);
    copySecureArgsList = new SecureConnectionCliArgs(secureArgs.alwaysSSL());
    try
    {
      copySecureArgsList.createGlobalArguments();
    }
    catch (Throwable t)
    {
      // This is  a bug: we should always be able to create the global arguments
      // no need to localize this one.
      throw new RuntimeException("Unexpected error: " + t, t);
    }
  }

  /**
   * Interact with the user though the console to get information necessary to
   * establish an LDAP connection.
   *
   * @throws ArgumentException
   *           if there is a problem with the arguments
   */
  public void run() throws ArgumentException
  {
    run(true);
  }

  /**
   * Interact with the user though the console to get information necessary to
   * establish an LDAP connection.
   *
   * @param canUseStartTLS
   *          whether we can propose to connect using Start TLS or not.
   * @throws ArgumentException
   *           if there is a problem with the arguments
   */
  public void run(boolean canUseStartTLS) throws ArgumentException
  {
    resetBeforeRun();
    resolveHostName();
    resolveConnectionType(canUseStartTLS);
    resolvePortNumber();
    resolveTrustAndKeyManagers();
    resolveCredentialLogin();
    resolveCredentialPassword();
    resolveConnectTimeout();
  }

  private void resetBeforeRun() throws ArgumentException
  {
    commandBuilder.clearArguments();
    copySecureArgsList.createGlobalArguments();
    state.providedAdminUID = null;
    state.providedBindDN = null;
  }

  private void resolveHostName() throws ArgumentException
  {
    state.hostName = secureArgsList.getHostNameArg().getValue();
    promptForHostNameIfRequired();
    addArgToCommandBuilder(copySecureArgsList.getHostNameArg(), state.hostName);
  }

  private void resolveConnectionType(boolean canUseStartTLS)
  {
    state.setSsl(secureArgsList);
    promptForConnectionTypeIfRequired(canUseStartTLS);
    addConnectionTypeToCommandBuilder();
  }

  private void resolvePortNumber() throws ArgumentException
  {
    portNumber = (state.useSSL && !secureArgsList.getPortArg().isPresent())
        ? secureArgsList.getPortFromConfig()
        : secureArgsList.getPortArg().getIntValue();
    promptForPortNumberIfRequired();
    addArgToCommandBuilder(copySecureArgsList.getPortArg(), String.valueOf(portNumber));
  }

  private void resolveTrustAndKeyManagers() throws ArgumentException {
    if ((state.useSSL || state.useStartTLS) && state.trustManager == null)
    {
      initializeTrustAndKeyManagers();
    }
  }

  private void resolveCredentialLogin() throws ArgumentException
  {
    setAdminUidAndBindDnFromArgs();
    if (useKeyManager())
    {
      return;
    }
    promptForCredentialLoginIfRequired(secureArgsList.getBindDnArg().getValue(),
                                       secureArgsList.getAdminUidArg().getValue());
    final boolean onlyBindDnProvided = state.providedAdminUID != null || state.providedBindDN == null;
    if ((useAdminOrBindDn && onlyBindDnProvided)
     || (!useAdminOrBindDn && isAdminUidArgVisible()))
    {
      addArgToCommandBuilder(copySecureArgsList.getAdminUidArg(), getAdministratorUID());
    }
    else
    {
      addArgToCommandBuilder(copySecureArgsList.getBindDnArg(), getBindDN());
    }
  }

  private void setAdminUidAndBindDnFromArgs()
  {
    final Argument adminUid = secureArgsList.getAdminUidArg();
    final Argument bindDn = secureArgsList.getBindDnArg();

    state.providedAdminUID = (isAdminUidArgVisible() && adminUid.isPresent()) ? adminUid.getValue() : null;
    state.providedBindDN = ((useAdminOrBindDn || !isAdminUidArgVisible()) && bindDn.isPresent()) ? bindDn.getValue()
                                                                                                 : null;
    state.adminUID = !useKeyManager() ? adminUid.getValue() : null;
    state.bindDN = !useKeyManager() ? bindDn.getValue() : null;
  }

  private void resolveCredentialPassword() throws ArgumentException
  {
    if (secureArgsList.getBindPasswordArg().isPresent())
    {
      state.bindPassword = secureArgsList.getBindPasswordArg().getValue();
    }

    if (useKeyManager())
    {
      return;
    }

    setBindPasswordFileFromArgs();
    final boolean addedPasswordFileArgument = secureArgsList.getBindPasswordFileArg().isPresent();
    if (!addedPasswordFileArgument && (state.bindPassword == null || "-".equals(state.bindPassword)))
    {
      promptForBindPasswordIfRequired();
    }

    final Argument bindPassword = copySecureArgsList.getBindPasswordArg();
    bindPassword.clearValues();
    bindPassword.addValue(state.bindPassword);
    if (!addedPasswordFileArgument)
    {
      commandBuilder.addObfuscatedArgument(bindPassword);
    }
  }

  private void setBindPasswordFileFromArgs() throws ArgumentException
  {
    final FileBasedArgument bindPasswordFile = secureArgsList.getBindPasswordFileArg();
    if (bindPasswordFile.isPresent())
    {
      // Read from file if it exists.
      state.bindPassword = bindPasswordFile.getValue();
      if (state.bindPassword == null)
      {
        throw new ArgumentException(
            ERR_ERROR_NO_ADMIN_PASSWORD.get(isAdminUidArgVisible() ? state.adminUID : state.bindDN));
      }
      addArgToCommandBuilder(copySecureArgsList.getBindPasswordFileArg(), bindPasswordFile.getNameToValueMap());
    }
  }

  private void resolveConnectTimeout() throws ArgumentException
  {
    state.connectTimeout = secureArgsList.getConnectTimeoutArg().getIntValue();
  }

  private void promptForHostNameIfRequired() throws ArgumentException
  {
    if (!app.isInteractive() || secureArgsList.getHostNameArg().isPresent())
    {
      return;
    }
    checkHeadingDisplayed();
    ValidationCallback<String> callback = new ValidationCallback<String>()
    {
      @Override
      public String validate(ConsoleApplication app, String rawInput) throws ClientException
      {
        final String input = rawInput.trim();
        if (input.length() == 0)
        {
          return state.hostName;
        }

        try
        {
          // Ensure that the prompted host is known
          InetAddress.getByName(input);
          return input;
        }
        catch (UnknownHostException e)
        {
          // Try again...
          app.println();
          app.println(ERR_LDAP_CONN_BAD_HOST_NAME.get(input));
          app.println();
          return null;
        }
      }
    };

    try
    {
      app.println();
      state.hostName = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_HOST_NAME.get(state.hostName), callback);
    }
    catch (ClientException e)
    {
      throw cannotReadConnectionParameters(e);
    }
  }

  private void promptForConnectionTypeIfRequired(final boolean canUseStartTLS)
  {
    final boolean valuesSetByProperty = secureArgsList.getUseSSLArg().isValueSetByProperty()
                                     && secureArgsList.getUseStartTLSArg().isValueSetByProperty();
    if (!app.isInteractive() || state.useSSL || state.useStartTLS || valuesSetByProperty)
    {
      return;
    }
    checkHeadingDisplayed();
    final MenuBuilder<Integer> builder = new MenuBuilder<>(app);
    builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_USE_SECURE_CTX.get());

    for (Protocol p : Protocol.values())
    {
      if ((!displayLdapIfSecureParameters && Protocol.LDAP.equals(p))
          || (!canUseStartTLS && Protocol.START_TLS.equals(p)))
      {
        continue;
      }

      final MenuResult<Integer> menuResult = MenuResult.success(p.getChoice());
      final int i = builder.addNumberedOption(p.message, menuResult);
      if (DEFAULT_PROMPT_PROTOCOL.equals(p))
      {
        builder.setDefault(INFO_LDAP_CONN_PROMPT_SECURITY_PROTOCOL_DEFAULT_CHOICE.get(i), menuResult);
      }
    }

    Menu<Integer> menu = builder.toMenu();
    try
    {
      final MenuResult<Integer> result = menu.run();
      throwIfMenuResultNotSucceeded(result);
      final int userChoice = result.getValue();
      if (Protocol.SSL.getChoice() == userChoice)
      {
        state.useSSL = true;
      }
      else if (Protocol.START_TLS.getChoice() == userChoice)
      {
        state.useStartTLS = true;
      }
    }
    catch (ClientException e)
    {
      throw new RuntimeException(e);
    }
  }

  private void promptForPortNumberIfRequired() throws ArgumentException
  {
    if (!app.isInteractive() || secureArgsList.getPortArg().isPresent())
    {
      return;
    }
    checkHeadingDisplayed();
    try
    {
      app.println();
      final LocalizableMessage askPortNumberMsg = secureArgsList.alwaysSSL() ?
          INFO_ADMIN_CONN_PROMPT_PORT_NUMBER.get(portNumber) :
          INFO_LDAP_CONN_PROMPT_PORT_NUMBER.get(portNumber);
      portNumber = app.readValidatedInput(askPortNumberMsg, portValidationCallback(portNumber));
    }
    catch (ClientException e)
    {
      throw cannotReadConnectionParameters(e);
    }
  }

  private void promptForCredentialLoginIfRequired(final String defaultBindDN, final String defaultAdminUID)
      throws ArgumentException
  {
    if (!app.isInteractive() || state.providedAdminUID != null || state.providedBindDN != null)
    {
      return;
    }
    checkHeadingDisplayed();
    ValidationCallback<String> callback = new ValidationCallback<String>()
    {
      @Override public String validate(ConsoleApplication app, String rawInput) throws ClientException
      {
        final String input = rawInput.trim();
        if (input.isEmpty())
        {
          return isAdminUidArgVisible() ? defaultAdminUID : defaultBindDN;
        }

        return input;
      }
    };

    try
    {
      app.println();
      if (useAdminOrBindDn)
      {
        String def = state.adminUID != null ? state.adminUID : state.bindDN;
        String v = app.readValidatedInput(INFO_LDAP_CONN_GLOBAL_ADMINISTRATOR_OR_BINDDN_PROMPT.get(def), callback);
        if (isDN(v))
        {
          state.bindDN = v;
          state.providedBindDN = v;
          state.adminUID = null;
          state.providedAdminUID = null;
        }
        else
        {
          state.bindDN = null;
          state.providedBindDN = null;
          state.adminUID = v;
          state.providedAdminUID = v;
        }
      }
      else if (isAdminUidArgVisible())
      {
        state.adminUID = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_ADMINISTRATOR_UID.get(state.adminUID), callback);
        state.providedAdminUID = state.adminUID;
      }
      else
      {
        state.bindDN = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_BIND_DN.get(state.bindDN), callback);
        state.providedBindDN = state.bindDN;
      }
    }
    catch (ClientException e)
    {
      throw cannotReadConnectionParameters(e);
    }
  }

  private void promptForBindPasswordIfRequired() throws ArgumentException
  {
    if (!app.isInteractive())
    {
      if (allowAnonymousIfNonInteractive)
      {
        return;
      }
      throw new ArgumentException(ERR_ERROR_BIND_PASSWORD_NONINTERACTIVE.get());
    }
    checkHeadingDisplayed();
    try
    {
      state.bindPassword = readPassword(state.getPrompt());
    }
    catch (Exception e)
    {
      throw new ArgumentException(ERR_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(e.getMessage()), e.getCause());
    }
  }

  /**
   * Get the trust manager.
   *
   * @return The trust manager based on CLI args on interactive prompt.
   * @throws ArgumentException
   *           If an error occurs when getting args values.
   */
  private ApplicationTrustManager getTrustManagerInternal() throws ArgumentException
  {
    // Remove these arguments since this method might be called several times.
    commandBuilder.removeArguments(copySecureArgsList.getTrustAllArg(),
                                   copySecureArgsList.getTrustStorePathArg(),
                                   copySecureArgsList.getTrustStorePasswordArg(),
                                   copySecureArgsList.getTrustStorePasswordFileArg());

    final TrustMethod trustMethod = resolveTrustMethod();
    if (TrustMethod.TRUSTALL == trustMethod)
    {
      return null;
    }

    final boolean promptForTrustStore = TrustMethod.TRUSTSTORE == trustMethod;
    resolveTrustStorePath(promptForTrustStore);
    setTrustStorePassword();
    setTrustStorePasswordFromFile();
    if ("-".equals(state.truststorePassword))
    {
      // Read the password from the stdin.
      promptForTrustStorePasswordIfRequired();
    }

    return resolveTrustStore();
  }

  /** As the most common case is to have no password for trust store, we do not ask it in the interactive mode.*/
  private void setTrustStorePassword()
  {
    if (secureArgsList.getTrustStorePasswordArg().isPresent())
    {
      state.truststorePassword = secureArgsList.getTrustStorePasswordArg().getValue();
    }
  }

  private void setTrustStorePasswordFromFile()
  {
    if (secureArgsList.getTrustStorePasswordFileArg().isPresent())
    {
      state.truststorePassword = secureArgsList.getTrustStorePasswordFileArg().getValue();
    }
  }

  /** Return the trust method chosen by user or {@code null} if the information is not available. */
  private TrustMethod resolveTrustMethod()
  {
    state.trustAll = secureArgsList.getTrustAllArg().isPresent();
    // Check if some trust manager info are set
    boolean needPromptForTrustMethod = !state.trustAll
        && !secureArgsList.getTrustStorePathArg().isPresent()
        && !secureArgsList.getTrustStorePasswordArg().isPresent()
        && !secureArgsList.getTrustStorePasswordFileArg().isPresent();

    TrustMethod trustMethod = state.trustAll ? TrustMethod.TRUSTALL : null;
    // Try to use the local instance trust store, to avoid certificate
    // validation when both the CLI and the server are in the same instance.
    if (needPromptForTrustMethod && !useLocalTrustStoreIfPossible())
    {
      trustMethod = promptForTrustMethodIfRequired();
    }

    if (trustMethod != TrustMethod.TRUSTSTORE)
    {
      // There is no direct equivalent for the display certificate option,
      // so propose trust all option as command-line argument.
      commandBuilder.addArgument(copySecureArgsList.getTrustAllArg());
    }

    return trustMethod;
  }

  private void resolveTrustStorePath(final boolean promptForTrustStore) throws ArgumentException
  {
    state.truststorePath = secureArgsList.getTrustStorePathArg().getValue();
    if (promptForTrustStore)
    {
      promptForTrustStorePathIfRequired();
    }
    addArgToCommandBuilder(copySecureArgsList.getTrustStorePathArg(), state.truststorePath);
  }

  private ApplicationTrustManager resolveTrustStore() throws ArgumentException
  {
    try
    {
      state.truststore = KeyStore.getInstance(KeyStore.getDefaultType());
      if (state.truststorePath != null)
      {
        try (FileInputStream fos = new FileInputStream(state.truststorePath))
        {
          state.truststore.load(fos, state.truststorePassword != null ? state.truststorePassword.toCharArray() : null);
        }
      }
      else
      {
        state.truststore.load(null, null);
      }

      if (secureArgsList.getTrustStorePasswordFileArg().isPresent() && state.truststorePath != null)
      {
        addArgToCommandBuilder(copySecureArgsList.getTrustStorePasswordFileArg(),
            secureArgsList.getTrustStorePasswordFileArg().getNameToValueMap());
      }
      else if (state.truststorePassword != null && state.truststorePath != null)
      {
        addObfuscatedArgToCommandBuilder(copySecureArgsList.getTrustStorePasswordArg(), state.truststorePassword);
      }

      return new ApplicationTrustManager(state.truststore);
    }
    catch (Exception e)
    {
      throw new ArgumentException(ERR_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(e.getMessage()), e.getCause());
    }
  }

  private TrustMethod promptForTrustMethodIfRequired()
  {
    if (!app.isInteractive())
    {
      return null;
    }

    checkHeadingDisplayed();
    app.println();
    MenuBuilder<Integer> builder = new MenuBuilder<>(app);
    builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_METHOD.get());

    for (TrustMethod t : TrustMethod.values())
    {
      int i = builder.addNumberedOption(t.message, MenuResult.success(t.getChoice()));
      if (DEFAULT_PROMPT_TRUST_METHOD.equals(t))
      {
        builder.setDefault(
            INFO_LDAP_CONN_PROMPT_SECURITY_PROTOCOL_DEFAULT_CHOICE.get(i), MenuResult.success(t.getChoice()));
      }
    }

    Menu<Integer> menu = builder.toMenu();
    state.trustStoreInMemory = false;
    try
    {
      final MenuResult<Integer> result = menu.run();
      throwIfMenuResultNotSucceeded(result);
      final int userChoice = result.getValue();
      if (TrustMethod.TRUSTALL.getChoice() == userChoice)
      {
        state.trustAll = true;
      }
      else if (TrustMethod.DISPLAY_CERTIFICATE.getChoice() == userChoice)
      {
        state.trustStoreInMemory = true;
      }
      return TrustMethod.getTrustMethodForIndex(userChoice);
    }
    catch (ClientException e)
    {
      throw new RuntimeException(e);
    }
  }

  private void promptForTrustStorePathIfRequired() throws ArgumentException
  {
    if (!app.isInteractive() || secureArgsList.getTrustStorePathArg().isPresent())
    {
      return;
    }

    checkHeadingDisplayed();
    try
    {
      app.println();
      state.truststorePath = app.readValidatedInput(
          INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE_PATH.get(),
          filePathValidationCallback(!ALLOW_EMPTY_PATH, FILE_MUST_EXISTS));
    }
    catch (ClientException e)
    {
      throw cannotReadConnectionParameters(e);
    }
  }

  private void promptForTrustStorePasswordIfRequired() throws ArgumentException
  {
    if (!app.isInteractive())
    {
      return;
    }

    checkHeadingDisplayed();
    try
    {
      state.truststorePassword = readPassword(
          INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE_PASSWORD.get(state.truststorePath));
    }
    catch (Exception e)
    {
      throw new ArgumentException(ERR_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(e.getMessage()), e.getCause());
    }
  }

  /**
   * Get the key manager.
   *
   * @return The key manager based on CLI args on interactive prompt.
   * @throws ArgumentException
   *           If an error occurs when getting args values.
   */
  private KeyManager getKeyManagerInternal() throws ArgumentException
  {
    //  Remove these arguments since this method might be called several times.
    commandBuilder.removeArguments(copySecureArgsList.getCertNicknameArg(),
                                   copySecureArgsList.getKeyStorePathArg(),
                                   copySecureArgsList.getKeyStorePasswordArg(),
                                   copySecureArgsList.getKeyStorePasswordFileArg());

    if (!secureArgsList.getKeyStorePathArg().isPresent()
     && !secureArgsList.getKeyStorePasswordArg().isPresent()
     && !secureArgsList.getKeyStorePasswordFileArg().isPresent()
     && !secureArgsList.getCertNicknameArg().isPresent())
    {
      // If no one of these parameters above are set, we assume that we do not need client side authentication.
      // Client side authentication is not the common use case so interactive mode doesn't add an extra question.
      return null;
    }

    resolveKeyStorePath();
    resolveKeyStorePassword();

    final KeyStore keystore = createKeyStore();
    resolveCertificateNickname(keystore);

    final ApplicationKeyManager keyManager = new ApplicationKeyManager(keystore, state.keystorePassword.toCharArray());
    addKeyStorePasswordArgToCommandBuilder();
    if (state.certifNickname != null)
    {
      addArgToCommandBuilder(copySecureArgsList.getCertNicknameArg(), state.certifNickname);
      return SelectableCertificateKeyManager.wrap(
          new KeyManager[] { keyManager }, CollectionUtils.newTreeSet(state.certifNickname))[0];
    }

    return keyManager;
  }

  private void resolveKeyStorePath() throws ArgumentException
  {
    state.keyStorePath = secureArgsList.getKeyStorePathArg().getValue();
    promptForKeyStorePathIfRequired();

    if (state.keyStorePath == null)
    {
      throw new ArgumentException(ERR_ERROR_INCOMPATIBLE_PROPERTY_MOD.get("null keystorePath"));
    }
    addArgToCommandBuilder(copySecureArgsList.getKeyStorePathArg(), state.keyStorePath);
  }

  private void resolveKeyStorePassword() throws ArgumentException
  {
    state.keystorePassword = secureArgsList.getKeyStorePasswordArg().getValue();

    if (secureArgsList.getKeyStorePasswordFileArg().isPresent())
    {
      state.keystorePassword = secureArgsList.getKeyStorePasswordFileArg().getValue();
      if (state.keystorePassword == null)
      {
        throw new ArgumentException(ERR_INSTALLDS_NO_KEYSTORE_PASSWORD.get(
            secureArgsList.getKeyStorePathArg().getLongIdentifier(),
            secureArgsList.getKeyStorePasswordFileArg().getLongIdentifier()));
      }
    }
    else if (state.keystorePassword == null || "-".equals(state.keystorePassword))
    {
      promptForKeyStorePasswordIfRequired();
    }
  }

  private KeyStore createKeyStore() throws ArgumentException
  {
    try (FileInputStream fos = new FileInputStream(state.keyStorePath))
    {
      final KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
      keystore.load(fos, state.keystorePassword.toCharArray());
      return keystore;
    }
    catch (Exception e)
    {
      throw new ArgumentException(ERR_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(e.getMessage()), e.getCause());
    }
  }

  private void resolveCertificateNickname(final KeyStore keystore) throws ArgumentException
  {
    state.certifNickname = secureArgsList.getCertNicknameArg().getValue();
    try
    {
      promptForCertificateNicknameIfRequired(keystore, keystore.aliases());
    }
    catch (final KeyStoreException e)
    {
      throw new ArgumentException(ERR_RESOLVE_KEYSTORE_ALIASES.get(e.getMessage()), e);
    }
  }

  private void promptForKeyStorePathIfRequired() throws ArgumentException
  {
    if (!app.isInteractive() || secureArgsList.getKeyStorePathArg().isPresent())
    {
      return;
    }
    checkHeadingDisplayed();
    try
    {
      app.println();
      state.keyStorePath = app.readValidatedInput(INFO_LDAP_CONN_PROMPT_SECURITY_KEYSTORE_PATH.get(),
                                                  filePathValidationCallback(ALLOW_EMPTY_PATH, FILE_MUST_EXISTS));
    }
    catch (ClientException e)
    {
      throw cannotReadConnectionParameters(e);
    }
  }

  private void promptForKeyStorePasswordIfRequired() throws ArgumentException
  {
    if (!app.isInteractive())
    {
      throw new ArgumentException(ERR_ERROR_BIND_PASSWORD_NONINTERACTIVE.get());
    }
    checkHeadingDisplayed();
    try
    {
      state.keystorePassword = readPassword(INFO_LDAP_CONN_PROMPT_SECURITY_KEYSTORE_PASSWORD.get(state.keyStorePath));
    }
    catch (Exception e)
    {
      throw new ArgumentException(ERR_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(e.getMessage()), e.getCause());
    }
  }

  private void promptForCertificateNicknameIfRequired(KeyStore keystore, Enumeration<String> aliasesEnum)
      throws ArgumentException
  {
    if (!app.isInteractive() || secureArgsList.getCertNicknameArg().isPresent() || !aliasesEnum.hasMoreElements())
    {
      return;
    }
    state.certifNickname = null;
    checkHeadingDisplayed();
    try
    {
      MenuBuilder<String> builder = new MenuBuilder<>(app);
      builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_ALIASES.get());
      int certificateNumber = 0;
      while (aliasesEnum.hasMoreElements())
      {
        final String alias = aliasesEnum.nextElement();
        if (keystore.isKeyEntry(alias))
        {
          certificateNumber++;
          X509Certificate certif = (X509Certificate) keystore.getCertificate(alias);
          builder.addNumberedOption(INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_ALIAS.get(
                                                                                alias, certif.getSubjectDN().getName()),
                                    MenuResult.success(alias));
        }
      }

      if (certificateNumber > 1)
      {
        app.println();
        Menu<String> menu = builder.toMenu();
        final MenuResult<String> result = menu.run();
        throwIfMenuResultNotSucceeded(result);
        state.certifNickname = result.getValue();
      }
    }
    catch (KeyStoreException e)
    {
      throw new ArgumentException(ERR_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(e.getMessage()), e.getCause());
    }
    catch (ClientException e)
    {
      throw cannotReadConnectionParameters(e);
    }
  }

  private void addKeyStorePasswordArgToCommandBuilder()
  {
    if (secureArgsList.getKeyStorePasswordFileArg().isPresent())
    {
      addArgToCommandBuilder(copySecureArgsList.getKeyStorePasswordFileArg(),
          secureArgsList.getKeyStorePasswordFileArg().getNameToValueMap());
    }
    else if (state.keystorePassword != null)
    {
      addObfuscatedArgToCommandBuilder(copySecureArgsList.getKeyStorePasswordArg(), state.keystorePassword);
    }
  }

  private void addConnectionTypeToCommandBuilder()
  {
    if (state.useSSL)
    {
      commandBuilder.addArgument(copySecureArgsList.getUseSSLArg());
    }
    else if (state.useStartTLS)
    {
      commandBuilder.addArgument(copySecureArgsList.getUseStartTLSArg());
    }
  }

  private void addArgToCommandBuilder(final Argument arg, final String value)
  {
    addArgToCommandBuilder(arg, value, false);
  }

  private void addObfuscatedArgToCommandBuilder(final Argument arg, final String value)
  {
    addArgToCommandBuilder(arg, value, true);
  }

  private void addArgToCommandBuilder(final Argument arg, final String value, final boolean obfuscated)
  {
    if (value != null)
    {
      arg.clearValues();
      arg.addValue(value);
      commandBuilder.addArgument(arg);
    }
  }

  private void addArgToCommandBuilder(final FileBasedArgument arg, final Map<String, String> nameToValueMap)
  {
    arg.clearValues();
    arg.getNameToValueMap().putAll(nameToValueMap);
    commandBuilder.addArgument(arg);
  }

  private ArgumentException cannotReadConnectionParameters(ClientException e)
  {
    return new ArgumentException(ERR_ERROR_CANNOT_READ_CONNECTION_PARAMETERS.get(e.getMessage()), e.getCause());
  }

  private String readPassword(LocalizableMessage prompt) throws ClientException
  {
    app.println();
    final char[] pwd = app.readPassword(prompt);
    if (pwd != null)
    {
      return String.valueOf(pwd);
    }
    return null;
  }

  private ValidationCallback<String> filePathValidationCallback(
      final boolean allowEmptyPath, final boolean checkExistenceAndReadability)
  {
    return new ValidationCallback<String>()
    {
      @Override
      public String validate(final ConsoleApplication app, final String filePathUserInput) throws ClientException
      {
        final String filePath = filePathUserInput.trim();
        final File f = new File(filePath);

        if ((!allowEmptyPath && filePath.isEmpty())
            || f.isDirectory()
            || (checkExistenceAndReadability && !(f.exists() && f.canRead())))
        {
          app.println();
          app.println(ERR_LDAP_CONN_PROMPT_SECURITY_INVALID_FILE_PATH.get());
          app.println();
          return null;
        }

        return filePath;
      }
    };
  }

  /** Returns {@code true} if client script uses the adminUID argument. */
  private boolean isAdminUidArgVisible()
  {
    return !secureArgsList.getAdminUidArg().isHidden();
  }

  private boolean useKeyManager()
  {
    return state.keyManager != null;
  }

  /**
   * Indicates whether a connection should use SSL based on this interaction.
   *
   * @return boolean where true means use SSL
   */
  public boolean useSSL()
  {
    return state.useSSL;
  }

  /**
   * Indicates whether a connection should use StartTLS based on this interaction.
   *
   * @return boolean where true means use StartTLS
   */
  public boolean useStartTLS()
  {
    return state.useStartTLS;
  }

  /**
   * Gets the host name that should be used for connections based on this
   * interaction.
   *
   * @return host name for connections
   */
  public String getHostName()
  {
    return state.hostName;
  }

  /**
   * Gets the port number name that should be used for connections based on this
   * interaction.
   *
   * @return port number for connections
   */
  public int getPortNumber()
  {
    return portNumber;
  }

  /**
   * Sets the port number name that should be used for connections based on this
   * interaction.
   *
   * @param portNumber
   *          port number for connections
   */
  public void setPortNumber(int portNumber)
  {
    this.portNumber = portNumber;
  }

  /**
   * Gets the bind DN name that should be used for connections based on this
   * interaction.
   *
   * @return bind DN for connections
   */
  public String getBindDN()
  {
    if (useAdminOrBindDn)
    {
      return state.getAdminOrBindDN();
    }
    else if (isAdminUidArgVisible())
    {
      return getAdministratorDN(state.adminUID);
    }
    else
    {
      return state.bindDN;
    }
  }

  /**
   * Gets the administrator UID name that should be used for connections based
   * on this interaction.
   *
   * @return administrator UID for connections
   */
  public String getAdministratorUID()
  {
    return state.adminUID;
  }

  /**
   * Gets the bind password that should be used for connections based on this
   * interaction.
   *
   * @return bind password for connections
   */
  public String getBindPassword()
  {
    return state.bindPassword;
  }

  /**
   * Gets the trust manager that should be used for connections based on this
   * interaction.
   *
   * @return trust manager for connections
   */
  public ApplicationTrustManager getTrustManager()
  {
    return state.trustManager;
  }

  /**
   * Gets the key store that should be used for connections based on this
   * interaction.
   *
   * @return key store for connections
   */
  public KeyStore getKeyStore()
  {
    return state.truststore;
  }

  /**
   * Gets the key manager that should be used for connections based on this
   * interaction.
   *
   * @return key manager for connections
   */
  public KeyManager getKeyManager()
  {
    return state.keyManager;
  }

  /**
   * Indicate if the trust store is in memory.
   *
   * @return true if the trust store is in memory.
   */
  public boolean isTrustStoreInMemory()
  {
    return state.trustStoreInMemory;
  }

  /**
   * Indicate if all certificates must be accepted.
   *
   * @return true all certificates must be accepted.
   */
  public boolean isTrustAll()
  {
    return state.trustAll;
  }

  /**
   * Returns the timeout to be used to connect with the server.
   *
   * @return the timeout to be used to connect with the server.
   */
  public int getConnectTimeout()
  {
    return state.connectTimeout;
  }

  /**
   * Indicate if the certificate chain can be trusted.
   *
   * @param chain
   *          The certificate chain to validate
   * @param authType
   *          the authentication type.
   * @param host
   *          the host we tried to connect and that presented the certificate.
   * @return true if the server certificate is trusted.
   */
  public boolean checkServerCertificate(final X509Certificate[] chain, final String authType, final String host)
  {
    if (state.trustManager == null)
    {
      try
      {
        initializeTrustAndKeyManagers();
      }
      catch (ArgumentException ae)
      {
        // Should not append because this.run() should has been called at this stage.
        throw new RuntimeException(ae);
      }
    }
    printCertificateChain(chain);
    MenuBuilder<Integer> builder = new MenuBuilder<>(app);
    builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION.get());

    for (TrustOption t : TrustOption.values())
    {
      final MenuResult<Integer> result = MenuResult.success(t.getChoice());
      int i = builder.addNumberedOption(t.message, result);
      if (DEFAULT_PROMPT_TRUST_OPTION.equals(t))
      {
        builder.setDefault(INFO_LDAP_CONN_PROMPT_SECURITY_PROTOCOL_DEFAULT_CHOICE.get(i), result);
      }
    }

    app.println();
    app.println();

    final Menu<Integer> menu = builder.toMenu();
    try
    {
      boolean promptAgain;
      int userChoice;
      do
      {
        promptAgain = false;
        final MenuResult<Integer> result = menu.run();
        throwIfMenuResultNotSucceeded(result);
        userChoice = result.getValue();
        if (TrustOption.CERTIFICATE_DETAILS.getChoice() == userChoice)
        {
          promptAgain = true;
          printCertificateDetails(chain);
        }
      }
      while (promptAgain);

      return trustCertificate(TrustOption.getTrustOptionForIndex(userChoice), chain, authType, host);
    }
    catch (ClientException e)
    {
      throw new RuntimeException(e);
    }
  }

  private void printCertificateChain(X509Certificate[] chain)
  {
    app.println();
    app.println(INFO_LDAP_CONN_PROMPT_SECURITY_SERVER_CERTIFICATE.get());
    app.println();
    boolean printSeparatorLines = false;
    for (final X509Certificate cert : chain)
    {
      if (!printSeparatorLines)
      {
        app.println();
        app.println();
        printSeparatorLines = true;
      }

      // Certificate DN
      app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_USER_DN.get(cert.getSubjectDN()));
      // certificate validity
      app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_VALIDITY.get(
          cert.getNotBefore(), cert.getNotAfter()));
      // certificate Issuer
      app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_ISSUER.get(cert.getIssuerDN()));
    }
  }

  private void printCertificateDetails(X509Certificate[] chain)
  {
    for (X509Certificate cert : chain)
    {
      app.println();
      app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE.get(cert));
    }
  }

  private boolean trustCertificate(final TrustOption trustOption, final X509Certificate[] chain,
      final String authType, final String host) throws ClientException
  {
    try
    {
      switch (trustOption)
      {
      case SESSION:
        updateTrustManager(chain, authType, host);
        return true;

      case PERMAMENT:
        updateTrustManager(chain, authType, host);
        try
        {
          trustCertificatePermanently(chain);
        }
        catch (Exception e)
        {
          app.println(ERR_TRUSTING_CERTIFICATE_PERMANENTLY.get(e.getMessage()));
        }
        return true;

      case UNTRUSTED:
      default:
        return false;
      }
    }
    catch (KeyStoreException e)
    {
      app.println(ERR_TRUSTING_CERTIFICATE.get(e.getMessage()));
      return false;
    }
  }

  private void updateTrustManager(X509Certificate[] chain, String authType, String host) throws KeyStoreException
  {
    // User choice if to add the certificate to the trust store for the current session or permanently.
    for (final X509Certificate cert : chain)
    {
      state.truststore.setCertificateEntry(cert.getSubjectDN().getName(), cert);
    }

    // Update the trust manager
    if (state.trustManager == null)
    {
      state.trustManager = new ApplicationTrustManager(state.truststore);
    }

    if (authType != null && host != null)
    {
      // Update the trust manager with the new certificate
      state.trustManager.acceptCertificate(chain, authType, host);
    }
    else
    {
      // Do a full reset of the contents of the keystore.
      state.trustManager = new ApplicationTrustManager(state.truststore);
    }
  }

  private void trustCertificatePermanently(final X509Certificate[] chain) throws Exception
  {
    app.println();
    final String trustStorePath = app.readValidatedInput(
        INFO_LDAP_CONN_PROMPT_SECURITY_TRUSTSTORE_PATH.get(),
        filePathValidationCallback(!ALLOW_EMPTY_PATH, !FILE_MUST_EXISTS));

    // Read the password from the stdin.
    final String trustStorePasswordStr = readPassword(
        INFO_LDAP_CONN_PROMPT_SECURITY_KEYSTORE_PASSWORD.get(trustStorePath));
    final KeyStore keyStore = KeyStore.getInstance("JKS");
    final char[] trustStorePassword = trustStorePasswordStr.toCharArray();
    loadKeyStoreFromFile(keyStore, trustStorePath, trustStorePassword);

    for (final X509Certificate cert : chain)
    {
      keyStore.setCertificateEntry(cert.getSubjectDN().getName(), cert);
    }

    try (final FileOutputStream trustStoreOutputFile = new FileOutputStream(trustStorePath))
    {
      keyStore.store(trustStoreOutputFile, trustStorePassword);
    }
  }

  private void loadKeyStoreFromFile(
      final KeyStore keyStore, final String trustStorePath, final char[] trustStorePassword) throws Exception
  {
      try (FileInputStream inputStream = new FileInputStream(trustStorePath))
      {
        keyStore.load(inputStream, trustStorePassword);
      }
      catch (FileNotFoundException ignored)
      {
        // create empty keystore
        keyStore.load(null, trustStorePassword);
      }
  }

  /**
   * Populates a set of LDAP options with state from this interaction.
   *
   * @param options
   *          existing set of options; may be null in which case this method
   *          will create a new set of <code>LDAPConnectionOptions</code> to be
   *          returned
   * @return used during this interaction
   * @throws SSLConnectionException
   *           if this interaction has specified the use of SSL and there is a
   *           problem initializing the SSL connection factory
   */
  public LDAPConnectionOptions populateLDAPOptions(LDAPConnectionOptions options) throws SSLConnectionException
  {
    if (options == null)
    {
      options = new LDAPConnectionOptions();
    }
    options.setUseSSL(state.useSSL);
    options.setStartTLS(state.useStartTLS);
    if (state.useSSL)
    {
      SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
      sslConnectionFactory.init(getTrustManager() == null, state.keyStorePath,
          state.keystorePassword, state.certifNickname, state.truststorePath, state.truststorePassword);
      options.setSSLConnectionFactory(sslConnectionFactory);
    }

    return options;
  }

  /**
   * Prompts the user to accept the certificate.
   *
   * @param errorRaised
   *          the error raised because the certificate was not trusted.
   * @param usedTrustManager
   *          the trustManager used when trying to establish the connection.
   * @param usedUrl
   *          the LDAP URL used to connect to the server.
   * @param logger
   *          the Logger used to log messages.
   * @return {@code true} if the user accepted the certificate and
   *         {@code false} otherwise.
   */
  public boolean promptForCertificateConfirmation(Throwable errorRaised,
      ApplicationTrustManager usedTrustManager, String usedUrl, LocalizedLogger logger)
  {
    final ApplicationTrustManager.Cause cause = usedTrustManager != null ? usedTrustManager.getLastRefusedCause()
                                                                         : null;
    logger.debug(INFO_CERTIFICATE_EXCEPTION_CAUSE.get(cause));

    if (cause == null)
    {
      app.println(getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), errorRaised));
      return false;
    }

    String host;
    int port;
    try
    {
      URI uri = new URI(usedUrl);
      host = uri.getHost();
      port = uri.getPort();
    }
    catch (URISyntaxException e)
    {
      logger.warn(ERROR_CERTIFICATE_PARSING_URL.get(usedUrl, e));
      host = INFO_NOT_AVAILABLE_LABEL.get().toString();
      port = -1;
    }

    final String authType = usedTrustManager.getLastRefusedAuthType();
    if (authType == null)
    {
      logger.warn(ERROR_CERTIFICATE_NULL_AUTH_TYPE.get());
    }
    else
    {
      app.println(ApplicationTrustManager.Cause.NOT_TRUSTED.equals(authType)
          ? INFO_CERTIFICATE_NOT_TRUSTED_TEXT_CLI.get(host, port)
          : INFO_CERTIFICATE_NAME_MISMATCH_TEXT_CLI.get(host, port, host, host, port));
    }

    final X509Certificate[] chain = usedTrustManager.getLastRefusedChain();
    if (chain == null)
    {
      logger.warn(ERROR_CERTIFICATE_NULL_CHAIN.get());
      return false;
    }
    if (host == null)
    {
      logger.warn(ERROR_CERTIFICATE_NULL_HOST_NAME.get());
    }

    return checkServerCertificate(chain, authType, host);
  }

  /**
   * Sets the heading that is displayed in interactive mode.
   *
   * @param heading
   *          the heading that is displayed in interactive mode.
   */
  public void setHeadingMessage(LocalizableMessage heading)
  {
    this.heading = heading;
  }

  /**
   * Returns the command builder with the equivalent arguments on the
   * non-interactive mode.
   *
   * @return the command builder with the equivalent arguments on the
   *         non-interactive mode.
   */
  public CommandBuilder getCommandBuilder()
  {
    return commandBuilder;
  }

  /**
   * Displays the heading if it was not displayed before.
   */
  private void checkHeadingDisplayed()
  {
    if (!state.isHeadingDisplayed)
    {
      app.println();
      app.println();
      app.println(heading);
      state.isHeadingDisplayed = true;
    }
  }

  /**
   * Tells whether we can ask during interaction for both the DN and the admin
   * UID or not.
   * Default value is {@code false}.
   *
   * @param useAdminOrBindDn
   *          whether we can ask for both the DN and the admin UID during
   *          interaction or not.
   */
  public void setUseAdminOrBindDn(boolean useAdminOrBindDn)
  {
    this.useAdminOrBindDn = useAdminOrBindDn;
  }

  /**
   * Tells whether we propose LDAP as protocol even if the user provided
   * security parameters. This is required in command-lines that access multiple
   * servers (like dsreplication).
   *
   * @param displayLdapIfSecureParameters
   *          whether propose LDAP as protocol even if the user provided
   *          security parameters or not.
   */
  public void setDisplayLdapIfSecureParameters(boolean displayLdapIfSecureParameters)
  {
    this.displayLdapIfSecureParameters = displayLdapIfSecureParameters;
  }

  /**
   * Resets the heading displayed flag, so that next time we call run the
   * heading is displayed.
   */
  public void resetHeadingDisplayed()
  {
    state.isHeadingDisplayed = false;
  }

  /**
   * Forces the initialization of the trust manager with the arguments provided
   * by the user.
   *
   * @throws ArgumentException
   *           if there is an error with the arguments provided by the user.
   */
  public void initializeTrustManagerIfRequired() throws ArgumentException
  {
    if (!state.trustManagerInitialized)
    {
      initializeTrustAndKeyManagers();
    }
  }

  /**
   * Initializes the global arguments in the parser with the provided values.
   * This is useful when we want to call LDAPConnectionConsoleInteraction.run()
   * with some default values.
   *
   * @param hostName
   *          the host name.
   * @param port
   *          the port to connect to the server.
   * @param adminUid
   *          the administrator UID.
   * @param bindDn
   *          the bind DN to bind to the server.
   * @param bindPwd
   *          the password to bind.
   * @param pwdFile
   *          the Map containing the file and the password to bind.
   */
  public void initializeGlobalArguments(String hostName, int port,
      String adminUid, String bindDn, String bindPwd,
      LinkedHashMap<String, String> pwdFile)
  {
    resetConnectionArguments();
    if (hostName != null)
    {
      secureArgsList.getHostNameArg().addValue(hostName);
      secureArgsList.getHostNameArg().setPresent(true);
    }
    // resetConnectionArguments does not clear the values for the port
    secureArgsList.getPortArg().clearValues();
    if (port != -1)
    {
      secureArgsList.getPortArg().addValue(String.valueOf(port));
      secureArgsList.getPortArg().setPresent(true);
    }
    else
    {
      // This is done to be able to call IntegerArgument.getIntValue()
      secureArgsList.getPortArg().addValue(secureArgsList.getPortArg().getDefaultValue());
    }
    secureArgsList.getUseSSLArg().setPresent(state.useSSL);
    secureArgsList.getUseStartTLSArg().setPresent(state.useStartTLS);
    if (adminUid != null)
    {
      secureArgsList.getAdminUidArg().addValue(adminUid);
      secureArgsList.getAdminUidArg().setPresent(true);
    }
    if (bindDn != null)
    {
      secureArgsList.getBindDnArg().addValue(bindDn);
      secureArgsList.getBindDnArg().setPresent(true);
    }
    if (pwdFile != null)
    {
      secureArgsList.getBindPasswordFileArg().getNameToValueMap().putAll(pwdFile);
      for (String value : pwdFile.keySet())
      {
        secureArgsList.getBindPasswordFileArg().addValue(value);
      }
      secureArgsList.getBindPasswordFileArg().setPresent(true);
    }
    else if (bindPwd != null)
    {
      secureArgsList.getBindPasswordArg().addValue(bindPwd);
      secureArgsList.getBindPasswordArg().setPresent(true);
    }
    state = new State(secureArgsList);
  }

  /**
   * Resets the connection parameters for the LDAPConsoleInteraction object. The
   * reset does not apply to the certificate parameters. This is called in order
   * the LDAPConnectionConsoleInteraction object to ask for all this connection
   * parameters next time we call LDAPConnectionConsoleInteraction.run().
   */
  public void resetConnectionArguments()
  {
    secureArgsList.getHostNameArg().clearValues();
    secureArgsList.getHostNameArg().setPresent(false);
    secureArgsList.getPortArg().clearValues();
    secureArgsList.getPortArg().setPresent(false);
    //  This is done to be able to call IntegerArgument.getIntValue()
    secureArgsList.getPortArg().addValue(secureArgsList.getPortArg().getDefaultValue());
    secureArgsList.getBindDnArg().clearValues();
    secureArgsList.getBindDnArg().setPresent(false);
    secureArgsList.getBindPasswordArg().clearValues();
    secureArgsList.getBindPasswordArg().setPresent(false);
    secureArgsList.getBindPasswordFileArg().clearValues();
    secureArgsList.getBindPasswordFileArg().getNameToValueMap().clear();
    secureArgsList.getBindPasswordFileArg().setPresent(false);
    state.bindPassword = null;
    secureArgsList.getAdminUidArg().clearValues();
    secureArgsList.getAdminUidArg().setPresent(false);
  }

  private void initializeTrustAndKeyManagers() throws ArgumentException
  {
    // Get trust store info
    state.trustManager = getTrustManagerInternal();
    // Check if we need client side authentication
    state.keyManager = getKeyManagerInternal();
    state.trustManagerInitialized = true;
  }

  /**
   * Returns the explicitly provided Admin UID from the user (interactively or
   * through the argument).
   *
   * @return the explicitly provided Admin UID from the user (interactively or
   *         through the argument).
   */
  public String getProvidedAdminUID()
  {
    return state.providedAdminUID;
  }

  /**
   * Returns the explicitly provided bind DN from the user (interactively or
   * through the argument).
   *
   * @return the explicitly provided bind DN from the user (interactively or
   *         through the argument).
   */
  public String getProvidedBindDN()
  {
    return state.providedBindDN;
  }

  /**
   * Add the TrustStore of the administration connector of the local instance.
   *
   * @return true if the local trust store has been added.
   */
  private boolean useLocalTrustStoreIfPossible()
  {
    try
    {
      if (InetAddress.getLocalHost().getHostName().equals(state.hostName)
          && secureArgsList.getAdminPortFromConfig() == portNumber)
      {
        final String trustStoreFileAbsolute = secureArgsList.getTruststoreFileFromConfig();
        if (trustStoreFileAbsolute != null)
        {
          secureArgsList.getTrustStorePathArg().addValue(trustStoreFileAbsolute);
          return true;
        }
      }
    }
    catch (Exception ex)
    {
      // do nothing
    }
    return false;
  }

  private void throwIfMenuResultNotSucceeded(final MenuResult<?> result)
  {
    if (!result.isSuccess())
    {
      throw new RuntimeException("Expected successful menu result, but got " + result);
    }
  }
}
