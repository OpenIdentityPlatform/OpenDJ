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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.tools.dsconfig;

import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_LONG_HELP;
import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_SHORT_HELP;
import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static org.forgerock.util.Utils.closeSilently;

import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import javax.naming.AuthenticationException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.ldap.LDAPManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.SubCommandArgumentParser;

/**
 * An LDAP management context factory.
 */
public final class LDAPManagementContextFactory implements
    ManagementContextFactory {

  /** The SecureConnectionCliArgsList object. */
  private SecureConnectionCliArgs secureArgsList;

  /** The management context. */
  private ManagementContext context;

  /** The connection parameters command builder. */
  private CommandBuilder contextCommandBuilder;

  /** Raw arguments. */
  private String[] rawArgs;

  /**
   * Creates a new LDAP management context factory.
   */
  public LDAPManagementContextFactory() {
    // Nothing to do.
  }

  /** {@inheritDoc} */
  @Override
  public ManagementContext getManagementContext(ConsoleApplication app)
      throws ArgumentException, ClientException
  {
    // Lazily create the LDAP management context.
    if (context == null)
    {
      LDAPConnectionConsoleInteraction ci =
        new LDAPConnectionConsoleInteraction(app, secureArgsList);
      ci.run();
      context = getManagementContext(app, ci);
      contextCommandBuilder = ci.getCommandBuilder();
    }
    return context;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    closeSilently(context);
  }

  /** {@inheritDoc} */
  @Override
  public CommandBuilder getContextCommandBuilder()
  {
    return contextCommandBuilder;
  }

  /**
   * Gets the management context which sub-commands should use in
   * order to manage the directory server. Implementations can use the
   * application instance for retrieving passwords interactively.
   *
   * @param app
   *          The application instance.
   * @param ci the LDAPConsoleInteraction object to be used.  The code assumes
   *        that the LDAPConsoleInteraction has already been run.
   * @return Returns the management context which sub-commands should
   *         use in order to manage the directory server.
   * @throws ArgumentException
   *           If a management context related argument could not be
   *           parsed successfully.
   * @throws ClientException
   *           If the management context could not be created.
   */
  public ManagementContext getManagementContext(ConsoleApplication app,
      LDAPConnectionConsoleInteraction ci)
      throws ArgumentException, ClientException
  {
    // Lazily create the LDAP management context.
    if (context == null)
    {
      // Interact with the user though the console to get
      // LDAP connection information
      final String hostName = ConnectionUtils.getHostNameForLdapUrl(ci.getHostName());
      final Integer portNumber = ci.getPortNumber();
      final String bindDN = ci.getBindDN();
      final String bindPassword = ci.getBindPassword();
      TrustManager trustManager = ci.getTrustManager();
      final KeyManager keyManager = ci.getKeyManager();

      final LDAPOptions options = new LDAPOptions();
      options.setConnectTimeout(ci.getConnectTimeout(), TimeUnit.MILLISECONDS);
      LDAPConnectionFactory factory = null;
      Connection connection = null;
      while (true)
      {
        try
        {
          final SSLContextBuilder sslBuilder = new SSLContextBuilder();
          sslBuilder.setTrustManager((trustManager == null ? TrustManagers
              .trustAll() : trustManager));
          sslBuilder.setKeyManager(keyManager);
          options.setUseStartTLS(ci.useStartTLS());
          options.setSSLContext(sslBuilder.getSSLContext());

          factory = new LDAPConnectionFactory(hostName, portNumber, options);
          connection = factory.getConnection();
          connection.bind(bindDN, bindPassword.toCharArray());
          break;
        }
        catch (ErrorResultException e)
        {
          final Throwable cause = e.getCause();
          if (app.isInteractive() && ci.isTrustStoreInMemory() && cause != null
              && cause instanceof SSLException
              && cause.getCause() instanceof CertificateException)
          {
            String authType = null;
            if (trustManager instanceof ApplicationTrustManager)
            { // FIXME use PromptingTrustManager
              ApplicationTrustManager appTrustManager =
                  (ApplicationTrustManager) trustManager;
              authType = appTrustManager.getLastRefusedAuthType();
              X509Certificate[] cert = appTrustManager.getLastRefusedChain();

              if (ci.checkServerCertificate(cert, authType, hostName))
              {
                // If the certificate is trusted, update the trust manager.
                trustManager = ci.getTrustManager();
                // Try to connect again.
                continue;
              }
            }
          }
          if (cause instanceof SSLException)
          {
            throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
                ERR_FAILED_TO_CONNECT_NOT_TRUSTED.get(hostName, portNumber));
          }
          throw couldNotConnect(cause, hostName, portNumber, bindDN);
        }
        catch (GeneralSecurityException e)
        {
          throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
              ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, portNumber));
        } finally {
          closeSilently(factory);
        }
      }
      context =
          LDAPManagementContext.newManagementContext(connection, LDAPProfile
              .getInstance());
    }
    return context;
  }

  private ClientException couldNotConnect(Throwable cause, String hostName,
      Integer portNumber, String bindDN)
  {
    if (cause instanceof AuthorizationException)
    {
      return new ClientException(ReturnCode.AUTH_METHOD_NOT_SUPPORTED,
          ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_NOT_SUPPORTED.get());
    }
    else if (cause instanceof AuthenticationException)
    {
      return new ClientException(ReturnCode.INVALID_CREDENTIALS,
          ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_FAILED.get(bindDN));
    }
    return new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
        ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, portNumber));
  }

  /** {@inheritDoc} */
  @Override
  public void setRawArguments(String[] args) {
    this.rawArgs = args;

  }

  /** {@inheritDoc} */
  @Override
  public void registerGlobalArguments(SubCommandArgumentParser parser)
      throws ArgumentException {
    // Create the global arguments.
    secureArgsList = new SecureConnectionCliArgs(true);
    LinkedHashSet<Argument> args = secureArgsList.createGlobalArguments();


    // Register the global arguments.
    for (Argument arg : args)
    {
      parser.addGlobalArgument(arg);
    }

    try
    {
      if (rawArgs != null) {
        for (String rawArg : rawArgs) {
          if (rawArg.length() < 2) {
            // This is not a help command
            continue;
          }
          if (rawArg.contains(OPTION_LONG_HELP) ||
            rawArg.charAt(1) == OPTION_SHORT_HELP || rawArg.charAt(1) == '?') {
            // used for usage help default values only
            secureArgsList.initArgumentsWithConfiguration();
          }
        }
      }
    }
    catch (ConfigException ce)
    {
      // Ignore.
    }
  }



  /** {@inheritDoc} */
  @Override
  public void validateGlobalArguments() throws ArgumentException {
    // Make sure that the user didn't specify any conflicting
    // arguments.
    LocalizableMessageBuilder buf = new LocalizableMessageBuilder();
    int v = secureArgsList.validateGlobalOptions(buf);
    if (v != ReturnCode.SUCCESS.get())
    {
      throw new ArgumentException(buf.toMessage());
    }
  }

}
