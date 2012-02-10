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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.extensions;



import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.security.cert.Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.*;

import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;



/**
 * A class that provides a TLS byte channel implementation.
 */
public class TLSByteChannel implements ConnectionSecurityProvider
{
  /**
   * Private implementation.
   */
  private final class ByteChannelImpl implements ByteChannel
  {

    /**
     * {@inheritDoc}
     */
    public int read(ByteBuffer dst) throws IOException
    {
      // TODO Auto-generated method stub
      return 0;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isOpen()
    {
      // TODO Auto-generated method stub
      return false;
    }



    /**
     * {@inheritDoc}
     */
    public void close() throws IOException
    {
      // TODO Auto-generated method stub

    }



    /**
     * {@inheritDoc}
     */
    public int write(ByteBuffer src) throws IOException
    {
      // TODO Auto-generated method stub
      return 0;
    }

  }



  private static final DebugTracer TRACER = getTracer();
  private final ByteChannel socketChannel;
  private final SSLEngine sslEngine;
  private final ByteChannelImpl pimpl = new ByteChannelImpl();


  // Map of cipher phrases to effective key size (bits). Taken from the
  // following RFCs: 5289, 4346, 3268,4132 and 4162.
  private static final Map<String, Integer> CIPHER_MAP;
  static
  {
    CIPHER_MAP = new LinkedHashMap<String, Integer>();
    CIPHER_MAP.put("_WITH_AES_256_CBC_", new Integer(256));
    CIPHER_MAP.put("_WITH_CAMELLIA_256_CBC_", new Integer(256));
    CIPHER_MAP.put("_WITH_AES_256_GCM_", new Integer(256));
    CIPHER_MAP.put("_WITH_3DES_EDE_CBC_", new Integer(112));
    CIPHER_MAP.put("_WITH_AES_128_GCM_", new Integer(128));
    CIPHER_MAP.put("_WITH_SEED_CBC_", new Integer(128));
    CIPHER_MAP.put("_WITH_CAMELLIA_128_CBC_", new Integer(128));
    CIPHER_MAP.put("_WITH_AES_128_CBC_", new Integer(128));
    CIPHER_MAP.put("_WITH_IDEA_CBC_", new Integer(128));
    CIPHER_MAP.put("_WITH_DES_CBC_", new Integer(56));
    CIPHER_MAP.put("_WITH_RC2_CBC_40_", new Integer(40));
    CIPHER_MAP.put("_WITH_RC4_40_", new Integer(40));
    CIPHER_MAP.put("_WITH_DES40_CBC_", new Integer(40));
    CIPHER_MAP.put("_WITH_NULL_", new Integer(0));
  };



  /**
   * Create an TLS byte channel instance using the specified LDAP connection
   * configuration, client connection, SSL context and socket channel
   * parameters.
   *
   * @param config
   *          The LDAP connection configuration.
   * @param c
   *          The client connection.
   * @param sslContext
   *          The SSL context.
   * @param socketChannel
   *          The socket channel.
   * @return A TLS capable byte channel.
   */
  public static TLSByteChannel getTLSByteChannel(
      final LDAPConnectionHandlerCfg config, final ClientConnection c,
      final SSLContext sslContext, final ByteChannel socketChannel)
  {
    return new TLSByteChannel(config, c, socketChannel, sslContext);
  }



  private TLSByteChannel(final LDAPConnectionHandlerCfg config,
      final ClientConnection c, final ByteChannel socketChannel,
      final SSLContext sslContext)
  {

    this.socketChannel = socketChannel;

    // getHostName could potentially be very expensive and could block
    // the connection handler for several minutes. (See issue 4229)
    // Accepting new connections should be done in a seperate thread to
    // avoid blocking new connections. Just remove for now to prevent
    // potential DoS attacks. SSL sessions will not be reused and some
    // cipher suites (such as Kerberos) will not work.

    // String hostName = socketChannel.socket().getInetAddress().getHostName();
    // int port = socketChannel.socket().getPort();
    // sslEngine = sslContext.createSSLEngine(hostName, port);

    sslEngine = sslContext.createSSLEngine();
    sslEngine.setUseClientMode(false);
    final Set<String> protocols = config.getSSLProtocol();
    if (!protocols.isEmpty())
    {
      sslEngine.setEnabledProtocols(protocols.toArray(new String[0]));
    }

    final Set<String> ciphers = config.getSSLCipherSuite();
    if (!ciphers.isEmpty())
    {
      sslEngine.setEnabledCipherSuites(ciphers.toArray(new String[0]));
    }

    switch (config.getSSLClientAuthPolicy())
    {
    case DISABLED:
      sslEngine.setNeedClientAuth(false);
      sslEngine.setWantClientAuth(false);
      break;
    case REQUIRED:
      sslEngine.setWantClientAuth(true);
      sslEngine.setNeedClientAuth(true);
      break;
    case OPTIONAL:
    default:
      sslEngine.setNeedClientAuth(false);
      sslEngine.setWantClientAuth(true);
      break;
    }
  }



  /**
   * {@inheritDoc}
   */
  public Certificate[] getClientCertificateChain()
  {
    try
    {
      return sslEngine.getSession().getPeerCertificates();
    }
    catch (final SSLPeerUnverifiedException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return new Certificate[0];
    }
  }



  /**
   * {@inheritDoc}
   */
  public String getName()
  {
    return "TLS";
  }



  /**
   * {@inheritDoc}
   */
  public int getSSF()
  {
    int cipherKeySSF = 0;
    final String cipherString = sslEngine.getSession().getCipherSuite();
    for (final Map.Entry<String, Integer> mapEntry : CIPHER_MAP.entrySet())
    {
      if (cipherString.indexOf(mapEntry.getKey()) >= 0)
      {
        cipherKeySSF = mapEntry.getValue().intValue();
        break;
      }
    }
    return cipherKeySSF;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isSecure()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ByteChannel wrapChannel(final ByteChannel channel)
  {
    return pimpl;
  }

}
