/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.opends.server.replication.protocol;



import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;

import java.io.IOException;
import java.net.Socket;
import java.util.SortedSet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.opends.messages.Message;
import org.opends.server.config.ConfigException;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryConfig;



/**
 * This class represents the security configuration for replication protocol
 * sessions. It contains all the configuration required to use SSL, and it
 * determines whether encryption should be enabled for a session to a given
 * replication server.
 */
public final class ReplSessionSecurity
{
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
    if (sslProtocols == null || sslProtocols.size() == 0)
    {
      this.sslProtocols = null;
    }
    else
    {
      this.sslProtocols = new String[sslProtocols.size()];
      sslProtocols.toArray(this.sslProtocols);
    }

    if (sslCipherSuites == null || sslCipherSuites.size() == 0)
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
      final CryptoManager cryptoManager = DirectoryConfig
          .getCryptoManager();
      final SSLContext sslContext = cryptoManager
          .getSslContext(sslCertNickname);
      final SSLSocketFactory sslSocketFactory = sslContext
          .getSocketFactory();

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
        try
        {
          socket.close();
        }
        catch (final Exception ignored)
        {
          // Ignore.
        }

        if (secureSocket != null)
        {
          try
          {
            secureSocket.close();
          }
          catch (final Exception ignored)
          {
            // Ignore.
          }
        }
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
      final CryptoManager cryptoManager = DirectoryConfig
          .getCryptoManager();
      final SSLContext sslContext = cryptoManager
          .getSslContext(sslCertNickname);
      final SSLSocketFactory sslSocketFactory = sslContext
          .getSocketFactory();

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
      final Message message = INFO_SSL_SERVER_CON_ATTEMPT_ERROR.get(
          socket.getRemoteSocketAddress().toString(),
          socket.getLocalSocketAddress().toString(),
          e.getLocalizedMessage());
      logError(message);
      return null;
    }
    finally
    {
      if (!hasCompleted)
      {
        try
        {
          socket.close();
        }
        catch (final Exception ignored)
        {
          // Ignore.
        }

        if (secureSocket != null)
        {
          try
          {
            secureSocket.close();
          }
          catch (final Exception ignored)
          {
            // Ignore.
          }
        }
      }
    }
  }



  /**
   * Determine whether sessions to a given replication server should be
   * encrypted.
   *
   * @param serverURL
   *          The replication server URL.
   * @return true if sessions to the given replication server should be
   *         encrypted, or false if they should not be encrypted.
   */
  public boolean isSslEncryption(final String serverURL)
  {
    // Currently use global settings from the crypto manager.
    return sslEncryption;
  }

}
