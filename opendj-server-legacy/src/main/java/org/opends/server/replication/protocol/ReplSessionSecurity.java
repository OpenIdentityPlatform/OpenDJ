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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.IOException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import java.net.Socket;
import java.util.SortedSet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryConfig;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class represents the security configuration for replication protocol
 * sessions. It contains all the configuration required to use SSL, and it
 * determines whether encryption should be enabled for a session to a given
 * replication server.
 */
public final class ReplSessionSecurity
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Whether replication sessions use SSL encryption.
   */
  private final boolean sslEncryption;

  /**
   * The name of the local certificate to use, or null if none is specified.
   */
  private final String sslCertNickname;

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
    this(DirectoryConfig.getCryptoManager().getSslCertNickname(),
        DirectoryConfig.getCryptoManager().getSslProtocols(),
        DirectoryConfig.getCryptoManager().getSslCipherSuites(),
        DirectoryConfig.getCryptoManager().isSslEncryption());
  }



  /**
   * Create a ReplSessionSecurity instance from the supplied configuration
   * values.
   *
   * @param sslCertNickname
   *          The name of the local certificate to use, or null if none is
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
  public ReplSessionSecurity(final String sslCertNickname,
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
    this.sslCertNickname = sslCertNickname;
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
      final CryptoManager cryptoManager = DirectoryConfig.getCryptoManager();
      final SSLContext sslContext = cryptoManager
          .getSslContext(sslCertNickname);
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
      final CryptoManager cryptoManager = DirectoryConfig.getCryptoManager();
      final SSLContext sslContext = cryptoManager
          .getSslContext(sslCertNickname);
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
