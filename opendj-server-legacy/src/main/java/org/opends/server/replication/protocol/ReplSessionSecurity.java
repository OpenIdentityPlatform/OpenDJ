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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.net.Socket;
import java.util.SortedSet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.CryptoManager;

/**
 * This class represents the security configuration for replication protocol
 * sessions. It contains all the configuration required to use SSL, and it
 * determines whether encryption should be enabled for a session to a given
 * replication server.
 */
public final class ReplSessionSecurity
{

  private static final String REPLICATION_SERVER_NAME = "Replication Server";

  private static final String REPLICATION_CLIENT_NAME = "Replication Client";

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Whether replication sessions use SSL encryption.
   */
  private final boolean sslEncryption;

  /**
   * The names of the local certificates to use, or null if none is specified.
   */
  private final SortedSet<String> sslCertNicknames;

  /**
   * The set of enabled SSL protocols, or null for the default set.
   */
  private final String sslProtocols[];

  /**
   * The set of enabled SSL cipher suites, or null for the default set.
   */
  private final String sslCipherSuites[];



  /**
   * Create a ReplSessionSecurity instance from a provided multimaster domain
   * configuration.
   *
   * @throws ConfigException
   *           If the supplied configuration was not valid.
   */
  public ReplSessionSecurity() throws ConfigException
  {
    // Currently use global settings from the crypto manager.
    this(getCryptoManager().getSslCertNicknames(),
        getCryptoManager().getSslProtocols(),
        getCryptoManager().getSslCipherSuites(),
        getCryptoManager().isSslEncryption());
  }



  /**
   * Create a ReplSessionSecurity instance from the supplied configuration
   * values.
   *
   * @param sslCertNicknames
   *          The names of the local certificates to use, or null if none is
   *          specified.
   * @param sslProtocols
   *          The protocols that should be enabled, or null if the default
   *          protocols should be used.
   * @param sslCipherSuites
   *          The cipher suites that should be enabled, or null if the default
   *          cipher suites should be used.
   * @param sslEncryption
   *          Whether replication sessions use SSL encryption.
   * @throws ConfigException
   *           If the supplied configuration was not valid.
   */
  public ReplSessionSecurity(final SortedSet<String> sslCertNicknames,
      final SortedSet<String> sslProtocols,
      final SortedSet<String> sslCipherSuites,
      final boolean sslEncryption) throws ConfigException
  {
    if (sslProtocols == null || sslProtocols.isEmpty())
    {
      this.sslProtocols = null;
    }
    else
    {
      this.sslProtocols = new String[sslProtocols.size()];
      sslProtocols.toArray(this.sslProtocols);
    }

    if (sslCipherSuites == null || sslCipherSuites.isEmpty())
    {
      this.sslCipherSuites = null;
    }
    else
    {
      this.sslCipherSuites = new String[sslCipherSuites.size()];
      sslCipherSuites.toArray(this.sslCipherSuites);
    }

    this.sslEncryption = sslEncryption;
    this.sslCertNicknames = sslCertNicknames;
  }



  /**
   * Create a new protocol session in the client role on the provided socket.
   *
   * @param socket
   *          The connected socket.
   * @param soTimeout
   *          The socket timeout option to use for the protocol session.
   * @return The new protocol session.
   * @throws ConfigException
   *           If the protocol session could not be established due to a
   *           configuration problem.
   * @throws IOException
   *           If the protocol session could not be established for some other
   *           reason.
   */
  public Session createClientSession(final Socket socket,
      final int soTimeout) throws ConfigException, IOException
  {
    boolean hasCompleted = false;
    SSLSocket secureSocket = null;

    try
    {
      // Create a new SSL context every time to make sure we pick up the
      // latest contents of the trust store.
      final SSLContext sslContext = getCryptoManager().getSslContext(REPLICATION_CLIENT_NAME, sslCertNicknames);
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      secureSocket = (SSLSocket) sslSocketFactory.createSocket(
          socket, socket.getInetAddress().getHostName(),
          socket.getPort(), false);
      secureSocket.setUseClientMode(true);
      secureSocket.setSoTimeout(soTimeout);

      if (sslProtocols != null)
      {
        secureSocket.setEnabledProtocols(sslProtocols);
      }

      if (sslCipherSuites != null)
      {
        secureSocket.setEnabledCipherSuites(sslCipherSuites);
      }

      // Force TLS negotiation now.
      secureSocket.startHandshake();
      hasCompleted = true;
      return new Session(socket, secureSocket);
    }
    finally
    {
      if (!hasCompleted)
      {
        close(socket);
        close(secureSocket);
      }
    }
  }

  private static CryptoManager getCryptoManager()
  {
    return DirectoryServer.getInstance().getServerContext().getCryptoManager();
  }

  /**
   * Create a new protocol session in the server role on the provided socket.
   *
   * @param socket
   *          The connected socket.
   * @param soTimeout
   *          The socket timeout option to use for the protocol session.
   * @return The new protocol session.
   * @throws ConfigException
   *           If the protocol session could not be established due to a
   *           configuration problem.
   * @throws IOException
   *           If the protocol session could not be established for some other
   *           reason.
   */
  public Session createServerSession(final Socket socket,
      final int soTimeout) throws ConfigException, IOException
  {
    boolean hasCompleted = false;
    SSLSocket secureSocket = null;

    try
    {
      // Create a new SSL context every time to make sure we pick up the
      // latest contents of the trust store.
      final SSLContext sslContext = getCryptoManager().getSslContext(REPLICATION_SERVER_NAME, sslCertNicknames);
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      secureSocket = (SSLSocket) sslSocketFactory.createSocket(
          socket, socket.getInetAddress().getHostName(),
          socket.getPort(), false);
      secureSocket.setUseClientMode(false);
      secureSocket.setNeedClientAuth(true);
      secureSocket.setSoTimeout(soTimeout);

      if (sslProtocols != null)
      {
        secureSocket.setEnabledProtocols(sslProtocols);
      }

      if (sslCipherSuites != null)
      {
        secureSocket.setEnabledCipherSuites(sslCipherSuites);
      }

      // Force TLS negotiation now.
      secureSocket.startHandshake();
      hasCompleted = true;
      return new Session(socket, secureSocket);
    }
    catch (final SSLException e)
    {
      // This is probably a connection attempt from an unexpected client
      // log that to warn the administrator.
      logger.debug(INFO_SSL_SERVER_CON_ATTEMPT_ERROR, socket.getRemoteSocketAddress(),
          socket.getLocalSocketAddress(), e.getLocalizedMessage());
      return null;
    }
    finally
    {
      if (!hasCompleted)
      {
        close(socket);
        close(secureSocket);
      }
    }
  }



  /**
   * Determine whether sessions to a given replication server should be
   * encrypted.
   *
   * @return true if sessions to the given replication server should be
   *         encrypted, or false if they should not be encrypted.
   */
  public boolean isSslEncryption()
  {
    // Currently use global settings from the crypto manager.
    return sslEncryption;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " " + (sslEncryption ? "with SSL" : "");
  }
}
