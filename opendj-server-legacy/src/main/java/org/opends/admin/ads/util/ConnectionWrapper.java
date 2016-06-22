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

import static org.forgerock.opendj.config.client.ldap.LDAPManagementContext.*;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.util.time.Duration.*;
import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.admin.ads.util.PreferredConnection.Type.*;
import static org.opends.messages.AdminToolMessages.*;

import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.ldap.InitialLdapContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.util.Options;
import org.opends.admin.ads.util.PreferredConnection.Type;
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
  private final HostPort hostPort;
  private final int connectTimeout;
  private final TrustManager trustManager;
  private final KeyManager keyManager;

  /**
   * Creates a connection wrapper.
   *
   * @param ldapUrl
   *          the ldap URL containing the host name and port number to connect to
   * @param connectionType
   *          the type of connection (LDAP, LDAPS, START_TLS)
   * @param bindDn
   *          the bind DN
   * @param bindPwd
   *          the bind password
   * @param connectTimeout
   *          connect timeout to use for the connection
   * @param trustManager
   *          trust manager to use for a secure connection
   * @throws NamingException
   *           If an error occurs
   */
  public ConnectionWrapper(String ldapUrl, Type connectionType, String bindDn, String bindPwd, int connectTimeout,
      ApplicationTrustManager trustManager) throws NamingException
  {
    this(toHostPort(ldapUrl), connectionType, bindDn, bindPwd, connectTimeout, trustManager);
  }

  private static HostPort toHostPort(String ldapUrl) throws NamingException
  {
    try
    {
      URI uri = new URI(ldapUrl);
      return new HostPort(uri.getHost(), uri.getPort());
    }
    catch (URISyntaxException e)
    {
      throw new NamingException(e.getLocalizedMessage() + ". LDAP URL was: \"" + ldapUrl + "\"");
    }
  }

  /**
   * Creates a connection wrapper.
   *
   * @param hostPort
   *          the host name and port number to connect to
   * @param connectionType
   *          the type of connection (LDAP, LDAPS, START_TLS)
   * @param bindDn
   *          the bind DN
   * @param bindPwd
   *          the bind password
   * @param connectTimeout
   *          connect timeout to use for the connection
   * @param trustManager
   *          trust manager to use for a secure connection
   * @throws NamingException
   *           If an error occurs
   */
  public ConnectionWrapper(HostPort hostPort, Type connectionType, String bindDn, String bindPwd, int connectTimeout,
      TrustManager trustManager) throws NamingException
  {
    this(hostPort, connectionType, bindDn, bindPwd, connectTimeout, trustManager, null);
  }

  /**
   * Creates a connection wrapper.
   *
   * @param hostPort
   *          the host name and port number to connect to
   * @param connectionType
   *          the type of connection (LDAP, LDAPS, START_TLS)
   * @param bindDn
   *          the bind DN
   * @param bindPwd
   *          the bind password
   * @param connectTimeout
   *          connect timeout to use for the connection
   * @param trustManager
   *          trust manager to use for a secure connection
   * @param keyManager
   *          key manager to use for a secure connection
   * @throws NamingException
   *           If an error occurs
   */
  public ConnectionWrapper(HostPort hostPort, PreferredConnection.Type connectionType, String bindDn, String bindPwd,
      int connectTimeout, TrustManager trustManager, KeyManager keyManager) throws NamingException
  {
    this.hostPort = hostPort;
    this.connectTimeout = connectTimeout;
    this.trustManager = trustManager;
    this.keyManager = keyManager;

    final Options options = toOptions(connectionType, bindDn, bindPwd, connectTimeout, trustManager, keyManager);
    ldapContext = createAdministrativeContext(options, bindDn, bindPwd);
    connectionFactory = new LDAPConnectionFactory(hostPort.getHost(), hostPort.getPort(), options);
    connection = buildConnection();
  }

  private static Options toOptions(Type connectionType, String bindDn, String bindPwd, long connectTimeout,
      TrustManager trustManager, KeyManager keyManager) throws NamingException
  {
    final boolean isStartTls = START_TLS.equals(connectionType);
    final boolean isLdaps = LDAPS.equals(connectionType);

    Options options = Options.defaultOptions()
        .set(CONNECT_TIMEOUT, duration(connectTimeout, TimeUnit.MILLISECONDS));
    if (isLdaps || isStartTls)
    {
      options.set(SSL_CONTEXT, getSSLContext(trustManager, keyManager))
             .set(SSL_USE_STARTTLS, isStartTls);
    }
    SimpleBindRequest request = bindDn != null && bindPwd != null
        ? newSimpleBindRequest(bindDn, bindPwd.toCharArray())
        : newSimpleBindRequest(); // anonymous bind
    options.set(AUTHN_BIND_REQUEST, request);
    return options;
  }

  private static SSLContext getSSLContext(TrustManager trustManager, KeyManager keyManager) throws NamingException
  {
    try
    {
      return new SSLContextBuilder()
          .setTrustManager(trustManager != null ? trustManager : new BlindTrustManager())
          .setKeyManager(keyManager).getSSLContext();
    }
    catch (GeneralSecurityException e)
    {
      throw new NamingException("Unable to perform SSL initialization:" + e.getMessage());
    }
  }

  private InitialLdapContext createAdministrativeContext(Options options, String bindDn, String bindPwd)
      throws NamingException
  {
    final InitialLdapContext ctx = createAdministrativeContext0(options, bindDn, bindPwd);
    if (!connectedAsAdministrativeUser(ctx))
    {
      throw new NoPermissionException(ERR_NOT_ADMINISTRATIVE_USER.get().toString());
    }
    return ctx;
  }

  private InitialLdapContext createAdministrativeContext0(Options options, String bindDn, String bindPwd)
      throws NamingException
  {
    SSLContext sslContext = options.get(SSL_CONTEXT);
    boolean useSSL = sslContext != null;
    boolean useStartTLS = options.get(SSL_USE_STARTTLS);
    final String ldapUrl = getLDAPUrl(getHostPort(), useSSL);
    if (useSSL)
    {
      return createLdapsContext(ldapUrl, bindDn, bindPwd, connectTimeout, null, trustManager, keyManager);
    }
    else if (useStartTLS)
    {
      return createStartTLSContext(ldapUrl, bindDn, bindPwd, connectTimeout, null, trustManager, keyManager, null);
    }
    else
    {
      return createLdapContext(ldapUrl, bindDn, bindPwd, connectTimeout, null);
    }
  }

  private Connection buildConnection() throws NamingException
  {
    try
    {
      return connectionFactory.getConnection();
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
   * Returns the ldap context (JNDI).
   *
   * @return the ldap context
   */
  public InitialLdapContext getLdapContext()
  {
    return ldapContext;
  }

  /**
   * Returns the host name and port number of this connection.
   *
   * @return the hostPort of this connection
   */
  public HostPort getHostPort()
  {
    return hostPort;
  }

  /**
   * Returns the root configuration client by using the inrnal Connection.
   *
   * @return the root configuration client
   */
  public RootCfgClient getRootConfiguration()
  {
    return newManagementContext(getConnection(), LDAPProfile.getInstance()).getRootConfiguration();
  }

  @Override
  public void close()
  {
    StaticUtils.close(connectionFactory, connection);
    StaticUtils.close(ldapContext);
  }
}
