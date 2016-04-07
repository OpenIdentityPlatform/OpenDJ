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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.admin.ads.util;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.AUTHN_BIND_REQUEST;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.CONNECT_TIMEOUT;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_CONTEXT;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.SSL_USE_STARTTLS;
import static org.opends.admin.ads.util.ConnectionUtils.getBindDN;
import static org.opends.admin.ads.util.ConnectionUtils.getBindPassword;
import static org.opends.admin.ads.util.ConnectionUtils.getHostPort;
import static org.opends.admin.ads.util.ConnectionUtils.isSSL;
import static org.opends.admin.ads.util.ConnectionUtils.isStartTLS;

import java.io.Closeable;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.ldap.LDAPManagementContext;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.util.Options;
import org.forgerock.util.time.Duration;
import org.opends.server.types.HostPort;
import org.opends.server.util.StaticUtils;

/**
 * Wraps a connection to a directory, either relying on JNDI or relying on OpenDJ Connection.
 * <p>
 * You can either:
 * <ul>
 *  <li>call {@code getLdapContext()} method to obtain an {@code InitialLdapContext} for JNDI.</li>
 *  <li>or call the {@code getConnection()} method to obtain a {@code Connection} object.</li>
 * </ul>
 */
public class ConnectionWrapper implements Closeable
{
  private final LDAPConnectionFactory connectionFactory;
  private final Connection connection;
  private final InitialLdapContext ldapContext;

  /**
   * Creates a connection wrapper from JNDI context and connection data.
   *
   * @param ctx
   *          the initial ldap context for JNDI
   * @param connectTimeout
   *            connect timeout to use for the connection
   * @param trustManager
   *            trust manager to use for a secure connection
   * @throws NamingException
   *           If an error occurs
   */
  public ConnectionWrapper(InitialLdapContext ctx, long connectTimeout, TrustManager trustManager)
      throws NamingException
  {
    ldapContext = ctx;

    Options options = Options.defaultOptions();
    options.set(CONNECT_TIMEOUT, new Duration(connectTimeout, TimeUnit.MILLISECONDS));
    if (isSSL(ctx) || isStartTLS(ctx))
    {
      options.set(SSL_CONTEXT, getSSLContext(trustManager)).set(SSL_USE_STARTTLS, isStartTLS(ctx));
    }
    options.set(AUTHN_BIND_REQUEST, Requests.newSimpleBindRequest(getBindDN(ctx), getBindPassword(ctx).toCharArray()));
    HostPort hostPort = getHostPort(ctx);
    connectionFactory = new LDAPConnectionFactory(hostPort.getHost(), hostPort.getPort(), options);
    try
    {
      connection = connectionFactory.getConnection();
    }
    catch (LdapException e)
    {
      throw new NamingException("Unable to get a connection from connection factory:" + e.getMessage());
    }
  }

  /**
   * Returns the connection.
   *
   * @return the connection
   */
  public Connection getConnection()
  {
    return connection;
  }

  /**
   * Returns the root configuration client by using the inrnal Connection.
   *
   * @return the root configuration client
   */
  public RootCfgClient getRootConfiguration()
  {
    ManagementContext ctx = LDAPManagementContext.newManagementContext(getConnection(), LDAPProfile.getInstance());
    return ctx.getRootConfiguration();
  }

  /**
   * Returns the ldap context (JNDI).
   *
   * @return the ldap context
   */
  public InitialLdapContext getLdapContext()
  {
    return ldapContext;
  }

  private SSLContext getSSLContext(TrustManager trustManager) throws NamingException
  {
    try
    {
      return new SSLContextBuilder()
        .setTrustManager(trustManager != null ? trustManager : new BlindTrustManager())
        .getSSLContext();
    }
    catch (GeneralSecurityException e)
    {
      throw new NamingException("Unable to perform SSL initialization:" + e.getMessage());
    }
  }

  @Override
  public void close()
  {
    StaticUtils.close(connectionFactory, connection);
    StaticUtils.close(ldapContext);
  }
}
