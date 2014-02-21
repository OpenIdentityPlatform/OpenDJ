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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.OpendsCertificateException;
import org.opends.server.admin.client.AuthenticationException;
import org.opends.server.admin.client.AuthenticationNotSupportedException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPConnection;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.config.ConfigException;

import com.forgerock.opendj.cli.ClientException;

import org.opends.server.tools.JavaPropertiesTool.ErrorReturnCode;
import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_LONG_HELP;
import static com.forgerock.opendj.cli.ArgumentConstants.OPTION_SHORT_HELP;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.ConsoleApplication;
import org.opends.server.util.cli.LDAPConnectionConsoleInteraction;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.SubCommandArgumentParser;

import java.util.LinkedHashSet;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;

import static org.opends.messages.DSConfigMessages.*;


/**
 * An LDAP management context factory.
 */
public final class LDAPManagementContextFactory implements
    ManagementContextFactory {

  // The SecureConnectionCliArgsList object.
  private SecureConnectionCliArgs secureArgsList = null;

  // The management context.
  private ManagementContext context = null;

  // The connection parameters command builder.
  private CommandBuilder contextCommandBuilder;

  // This CLI is always using the administration connector with SSL
  private boolean alwaysSSL = false;

  // Raw arguments
  private String[] rawArgs = null;

  /**
   * Creates a new LDAP management context factory.
   *
   * @param alwaysSSL If true, always use the SSL connection type. In this case,
   * the arguments useSSL and startTLS are not present.
   */
  public LDAPManagementContextFactory(boolean alwaysSSL) {
    this.alwaysSSL = alwaysSSL;
  }

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void close()
  {
    if (context != null)
    {
      context.close();
    }
  }

  /**
   * {@inheritDoc}
   */
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
      String hostName = ConnectionUtils.getHostNameForLdapUrl(ci.getHostName());
      Integer portNumber = ci.getPortNumber();
      String bindDN = ci.getBindDN();
      String bindPassword = ci.getBindPassword();
      TrustManager trustManager = ci.getTrustManager();
      KeyManager keyManager = ci.getKeyManager();

      // Do we have a secure connection ?
      LDAPConnection conn ;
      if (ci.useSSL())
      {
        InitialLdapContext ctx;
        String ldapsUrl = "ldaps://" + hostName + ":" + portNumber;
        while (true)
        {
          try
          {
            ctx = ConnectionUtils.createLdapsContext(ldapsUrl, bindDN,
                bindPassword, ci.getConnectTimeout(), null,
                trustManager, keyManager);
            ctx.reconnect(null);
            conn = JNDIDirContextAdaptor.adapt(ctx);
            break;
          }
          catch (NamingException e)
          {
            if ( app.isInteractive() && ci.isTrustStoreInMemory())
            {
              if ((e.getRootCause() != null)
                  && (e.getRootCause().getCause()
                      instanceof OpendsCertificateException))
              {
                OpendsCertificateException oce =
                  (OpendsCertificateException) e.getRootCause().getCause();
                String authType = null;
                if (trustManager instanceof ApplicationTrustManager)
                {
                  ApplicationTrustManager appTrustManager =
                    (ApplicationTrustManager)trustManager;
                  authType = appTrustManager.getLastRefusedAuthType();
                }
                  if (ci.checkServerCertificate(oce.getChain(), authType,
                      hostName))
                  {
                    // If the certificate is trusted, update the trust manager.
                    trustManager = ci.getTrustManager();

                    // Try to connect again.
                    continue ;
                  }
              }
            }
            if (e.getRootCause() != null) {
              if (e.getRootCause().getCause() != null) {
                if (((e.getRootCause().getCause()
                  instanceof OpendsCertificateException)) ||
                  (e.getRootCause() instanceof SSLHandshakeException)) {
                  LocalizableMessage message =
                    ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT_NOT_TRUSTED.get(hostName, portNumber);
                  throw new ClientException(
                    ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
                }
              }
              if (e.getRootCause() instanceof SSLException) {
                LocalizableMessage message =
                  ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT_WRONG_PORT.get(hostName, portNumber);
                throw new ClientException(
                    ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
              }
            }
            LocalizableMessage message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, portNumber);
            throw new ClientException(
                ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
          }
        }
      }
      else if (ci.useStartTLS())
      {
        InitialLdapContext ctx;
        String ldapUrl = "ldap://" + hostName + ":" + portNumber;
        while (true)
        {
          try
          {
            ctx = ConnectionUtils.createStartTLSContext(ldapUrl, bindDN,
                bindPassword, ConnectionUtils.getDefaultLDAPTimeout(), null,
                trustManager, keyManager, null);
            ctx.reconnect(null);
            conn = JNDIDirContextAdaptor.adapt(ctx);
            break;
          }
          catch (NamingException e)
          {
            if ( app.isInteractive() && ci.isTrustStoreInMemory())
            {
              if ((e.getRootCause() != null)
                  && (e.getRootCause().getCause()
                      instanceof OpendsCertificateException))
              {
                String authType = null;
                if (trustManager instanceof ApplicationTrustManager)
                {
                  ApplicationTrustManager appTrustManager =
                    (ApplicationTrustManager)trustManager;
                  authType = appTrustManager.getLastRefusedAuthType();
                }
                OpendsCertificateException oce =
                  (OpendsCertificateException) e.getRootCause().getCause();
                  if (ci.checkServerCertificate(oce.getChain(), authType,
                      hostName))
                  {
                    // If the certificate is trusted, update the trust manager.
                    trustManager = ci.getTrustManager();

                    // Try to connect again.
                    continue ;
                  }
              }
              else
              {
                LocalizableMessage message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                    hostName, portNumber);
                throw new ClientException(
                    ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
              }
            }
            LocalizableMessage message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
                hostName, portNumber);
            throw new ClientException(
                ReturnCode.CLIENT_SIDE_CONNECT_ERROR, message);
          }
        }
      }
      else
      {
        // Create the management context.
        try
        {
          conn = JNDIDirContextAdaptor.simpleBind(hostName, portNumber,
              bindDN, bindPassword);
        }
        catch (AuthenticationNotSupportedException e)
        {
          LocalizableMessage message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_NOT_SUPPORTED
              .get();
          throw new ClientException(ReturnCode.AUTH_METHOD_NOT_SUPPORTED,
              message);
        }
        catch (AuthenticationException e)
        {
          LocalizableMessage message = ERR_DSCFG_ERROR_LDAP_SIMPLE_BIND_FAILED
              .get(bindDN);
          throw new ClientException(ReturnCode.INVALID_CREDENTIALS,
              message);
        }
        catch (CommunicationException e)
        {
          LocalizableMessage message = ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(
              hostName, portNumber);
          throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
              message);
        }
      }
      context = LDAPManagementContext.createFromContext(conn);
    }
    return context;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRawArguments(String[] args) {
    this.rawArgs = args;

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void registerGlobalArguments(SubCommandArgumentParser parser)
      throws ArgumentException {
    // Create the global arguments.
    secureArgsList = new SecureConnectionCliArgs(alwaysSSL);
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
            (rawArg.charAt(1) == OPTION_SHORT_HELP) || (rawArg.
            charAt(1) == '?')) {
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



  /**
   * {@inheritDoc}
   */
  @Override
  public void validateGlobalArguments() throws ArgumentException {
    // Make sure that the user didn't specify any conflicting
    // arguments.
    LocalizableMessageBuilder buf = new LocalizableMessageBuilder();
    int v = secureArgsList.validateGlobalOptions(buf);
    if (v != ErrorReturnCode.SUCCESSFUL_NOP.getReturnCode())
    {
      throw new ArgumentException(buf.toMessage());
    }
  }

}
