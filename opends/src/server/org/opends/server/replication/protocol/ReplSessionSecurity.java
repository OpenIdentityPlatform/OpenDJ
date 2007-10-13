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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.replication.protocol;

import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.crypto.CryptoManager;
import org.opends.server.config.ConfigException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.util.SortedSet;
import java.net.Socket;
import java.io.IOException;

/**
 * This class represents the security configuration for replication protocol
 * sessions. It contains all the configuration required to use SSL, and it
 * determines whether encryption should be enabled for a session to a given
 * replication server.
 *
 */
public class ReplSessionSecurity
{
  /**
   * Whether the replication server should listen on a secure port.
   * Set false for test purposes only.
   */
  private static boolean useSSL = true;

  /**
   * Whether replication sessions use SSL encryption.
   */
  private boolean sslEncryption;

  /**
   * The name of the local certificate to use, or null if none is specified.
   */
  private String sslCertNickname;

  /**
   * The set of enabled SSL protocols, or null for the default set.
   */
  private String sslProtocols[];

  /**
   * The set of enabled SSL cipher suites, or null for the default set.
   */
  private String sslCipherSuites[];


  /**
   * Create a ReplSessionSecurity instance from the supplied configuration
   * values.
   *
   * @param sslCertNickname The name of the local certificate to use, or null
   *                        if none is specified.
   * @param sslProtocols    The protocols that should be enabled, or null if
   *                        the default protocols should be used.
   * @param sslCipherSuites The cipher suites that should be enabled, or null
   *                        if the default cipher suites should be used.
   * @param sslEncryption   Whether replication sessions use SSL encryption.
   *
   * @throws ConfigException    If the supplied configuration was not valid.
   */
  public ReplSessionSecurity(String sslCertNickname,
                             SortedSet<String> sslProtocols,
                             SortedSet<String> sslCipherSuites,
                             boolean sslEncryption)
       throws ConfigException
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
      this.sslCipherSuites = new String[sslProtocols.size()];
      sslProtocols.toArray(this.sslCipherSuites);
    }

    this.sslEncryption = sslEncryption;
    this.sslCertNickname = sslCertNickname;
  }

  /**
   * Create a ReplSessionSecurity instance from a provided replication server
   * configuration.
   *
   * @param replServerCfg The replication server configuration.
   *
   * @throws ConfigException If the supplied configuration was not valid.
   */
  public ReplSessionSecurity(ReplicationServerCfg replServerCfg)
       throws ConfigException
  {
    // Currently use global settings from the crypto manager.
    this(DirectoryConfig.getCryptoManager().getSslCertNickname(),
         DirectoryConfig.getCryptoManager().getSslProtocols(),
         DirectoryConfig.getCryptoManager().getSslCipherSuites(),
         DirectoryConfig.getCryptoManager().isSslEncryption());
  }

  /**
   * Create a ReplSessionSecurity instance from a provided multimaster domain
   * configuration.
   *
   * @param multimasterDomainCfg The multimaster domain configuration.
   *
   * @throws ConfigException If the supplied configuration was not valid.
   */
  public ReplSessionSecurity(ReplicationDomainCfg multimasterDomainCfg)
       throws ConfigException
  {
    // Currently use global settings from the crypto manager.
    this(DirectoryConfig.getCryptoManager().getSslCertNickname(),
         DirectoryConfig.getCryptoManager().getSslProtocols(),
         DirectoryConfig.getCryptoManager().getSslCipherSuites(),
         DirectoryConfig.getCryptoManager().isSslEncryption());
  }

  /**
   * Determine whether a given replication server is listening on a secure
   * port.
   * @param serverURL The replication server URL.
   * @return true if the given replication server is listening on a secure
   *         port, or false if it is listening on a non-secure port.
   */
  private boolean isSecurePort(String serverURL)
  {
    // Always true unless changed for test purposes.
    return useSSL;
  }

  /**
   * Determine whether sessions to a given replication server should be
   * encrypted.
   * @param serverURL The replication server URL.
   * @return true if sessions to the given replication server should be
   *         encrypted, or false if they should not be encrypted.
   */
  public boolean isSslEncryption(String serverURL)
  {
    // Currently use global settings from the crypto manager.
    return sslEncryption;
  }

  /**
   * Create a new protocol session in the client role on the provided socket.
   * @param serverURL The remote replication server to which the socket is
   *                  connected.
   * @param socket The connected socket.
   * @return The new protocol session.
   * @throws ConfigException If the protocol session could not be established
   *                         due to a configuration problem.
   * @throws IOException     If the protocol session could not be established
   *                         for some other reason.
   */
  public ProtocolSession createClientSession(String serverURL, Socket socket)
       throws ConfigException, IOException
  {
    boolean useSSL = isSecurePort(serverURL);
    if (useSSL)
    {
      // Create a new SSL context every time to make sure we pick up the
      // latest contents of the trust store.
      CryptoManager cryptoManager = DirectoryConfig.getCryptoManager();
      SSLContext sslContext = cryptoManager.getSslContext(sslCertNickname);
      SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      SSLSocket secureSocket = (SSLSocket)
           sslSocketFactory.createSocket(socket,
                                         socket.getInetAddress().getHostName(),
                                         socket.getPort(), false);
      secureSocket.setUseClientMode(true);

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

      return new TLSSocketSession(socket, secureSocket);
    }
    else
    {
      return new SocketSession(socket);
    }
  }

  /**
   * Create a new protocol session in the server role on the provided socket.
   * @param socket The connected socket.
   * @return The new protocol session.
   * @throws ConfigException If the protocol session could not be established
   *                         due to a configuration problem.
   * @throws IOException     If the protocol session could not be established
   *                         for some other reason.
   */
  public ProtocolSession createServerSession(Socket socket)
       throws ConfigException, IOException
  {
    if (useSSL)
    {
      // Create a new SSL context every time to make sure we pick up the
      // latest contents of the trust store.
      CryptoManager cryptoManager = DirectoryConfig.getCryptoManager();
      SSLContext sslContext = cryptoManager.getSslContext(sslCertNickname);
      SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      SSLSocket secureSocket = (SSLSocket)
           sslSocketFactory.createSocket(socket,
                                         socket.getInetAddress().getHostName(),
                                         socket.getPort(), false);
      secureSocket.setUseClientMode(false);
      secureSocket.setNeedClientAuth(true);

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

//      SSLSession sslSession = secureSocket.getSession();
//      System.out.println("Peer      = " + sslSession.getPeerHost() + ":" +
//           sslSession.getPeerPort());
//      System.out.println("Principal = " + sslSession.getPeerPrincipal());

      return new TLSSocketSession(socket, secureSocket);
    }
    else
    {
      return new SocketSession(socket);
    }
  }

}
