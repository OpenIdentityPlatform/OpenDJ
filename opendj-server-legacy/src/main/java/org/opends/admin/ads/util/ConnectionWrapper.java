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
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.util.time.Duration.*;
import static org.opends.admin.ads.util.PreferredConnection.Type.*;

import java.io.Closeable;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
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
  private final HostPort hostPort;
  private DN bindDn;
  private String bindPwd;
  private final int connectTimeout;
  private final TrustManager trustManager;
  private final KeyManager keyManager;
  private Type connectionType;

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
   * @throws LdapException
   *           If an error occurs
   */
  public ConnectionWrapper(HostPort hostPort, Type connectionType, DN bindDn, String bindPwd, int connectTimeout,
      TrustManager trustManager) throws LdapException
  {
    this(hostPort, connectionType, bindDn, bindPwd, connectTimeout, trustManager, null);
  }

  /**
   * Creates a connection wrapper by copying the provided one.
   *
   * @param other
   *          the {@link ConnectionWrapper} to copy
   * @throws LdapException
   *           If an error occurs
   */
  public ConnectionWrapper(ConnectionWrapper other) throws LdapException
  {
    this(other.hostPort, other.connectionType, other.bindDn, other.bindPwd, other.connectTimeout,
        other.trustManager, other.keyManager);
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
   * @throws LdapException
   *           If an error occurs
   */
  public ConnectionWrapper(HostPort hostPort, PreferredConnection.Type connectionType, DN bindDn, String bindPwd,
      int connectTimeout, TrustManager trustManager, KeyManager keyManager) throws LdapException
  {
    this.hostPort = hostPort;
    this.connectionType = connectionType;
    this.bindDn = bindDn;
    this.bindPwd = bindPwd;
    this.connectTimeout = connectTimeout;
    this.trustManager = trustManager;
    this.keyManager = keyManager;

    final Options options = toOptions(connectionType, bindDn, bindPwd, connectTimeout, trustManager, keyManager);
    connectionFactory = new LDAPConnectionFactory(hostPort.getHost(), hostPort.getPort(), options);
    connection = connectionFactory.getConnection();
  }

  private static Options toOptions(Type connectionType, DN bindDn, String bindPwd, long connectTimeout,
      TrustManager trustManager, KeyManager keyManager) throws LdapException
  {
    final boolean isStartTls = START_TLS.equals(connectionType);
    final boolean isLdaps = LDAPS.equals(connectionType);

    Options options = Options.defaultOptions()
        .set(CONNECT_TIMEOUT, duration(connectTimeout, TimeUnit.MILLISECONDS));
    if (isLdaps || isStartTls)
    {
      try {
    	SSLContext sslContext = getSSLContext(trustManager, keyManager);

    	List<String> defaultProtocols;
    	if (trustManager == null) {
    		defaultProtocols = ConnectionFactoryProvider.getDefaultProtocols();
    	} else {
    		defaultProtocols = ConnectionFactoryProvider.getDefaultProtocols(sslContext);
    	}

    	options.set(SSL_CONTEXT, sslContext)
                .set(SSL_USE_STARTTLS, isStartTls)
                .set(SSL_ENABLED_PROTOCOLS, defaultProtocols);
      } catch (NoSuchAlgorithmException e) {
          throw newLdapException(CLIENT_SIDE_PARAM_ERROR, "Unable to perform SSL initialization:" + e.getMessage());
      }
    }
    SimpleBindRequest request = bindDn != null && bindPwd != null
        ? newSimpleBindRequest(bindDn.toString(), bindPwd.toCharArray())
        : newSimpleBindRequest(); // anonymous bind
    options.set(AUTHN_BIND_REQUEST, request);
    return options;
  }

  private static SSLContext getSSLContext(TrustManager trustManager, KeyManager keyManager) throws LdapException
  {
    try
    {
      return new SSLContextBuilder()
          .setTrustManager(trustManager != null ? trustManager : new BlindTrustManager())
          .setKeyManager(keyManager)
          .getSSLContext();
    }
    catch (GeneralSecurityException e)
    {
      throw newLdapException(CLIENT_SIDE_PARAM_ERROR, "Unable to perform SSL initialization:" + e.getMessage());
    }
  }

  /**
   * Returns the bind DN used by this connection.
   *
   * @return the bind DN used by this connection.
   */
  public DN getBindDn()
  {
    return bindDn;
  }

  /**
   * Returns the bind password used by this connection.
   *
   * @return the bind password used by this connection.
   */
  public String getBindPassword()
  {
    return bindPwd;
  }

  /**
   * Returns the LDAP URL used by this connection.
   *
   * @return the LDAP URL used by this connection.
   */
  public String getLdapUrl()
  {
    return (isLdaps() ? "ldaps" : "ldap") + "://" + getHostPort();
  }

  /**
   * Returns whether this connection uses LDAPS.
   *
   * @return {@code true} if this connection uses LDAPS, {@code false} otherwise.
   */
  public boolean isLdaps()
  {
    return getConnectionType() == LDAPS;
  }

  /**
   * Returns whether this connection uses StartTLS.
   *
   * @return {@code true} if this connection uses StartTLS, {@code false} otherwise.
   */
  public boolean isStartTls()
  {
    return getConnectionType() == START_TLS;
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
   * Returns the connection type used by this connection wrapper.
   *
   * @return the connection type used by this connection wrapper
   */
  public PreferredConnection.Type getConnectionType()
  {
    return this.connectionType;
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
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "(" + getLdapUrl() + ")";
  }
}
