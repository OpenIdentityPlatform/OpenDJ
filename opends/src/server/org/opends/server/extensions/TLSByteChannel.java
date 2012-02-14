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
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;



/**
 * A class that provides a TLS byte channel implementation.
 */
public final class TLSByteChannel implements ConnectionSecurityProvider
{
  /**
   * Private implementation.
   */
  private final class ByteChannelImpl implements ByteChannel
  {

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException
    {
      synchronized (readLock)
      {
        synchronized (writeLock)
        {
          final boolean isInitiator = !sslEngine.isInboundDone();

          try
          {
            if (!sslEngine.isOutboundDone())
            {
              sslEngine.closeOutbound();
              while (doWrapAndSend(EMPTY_BUFFER) > 0)
              {
                // Write out any remaining SSL close notifications.
              }
            }
          }
          catch (final ClosedChannelException e)
          {
            // Ignore this so that close is idempotent.
          }
          finally
          {
            try
            {
              sslEngine.closeInbound();
            }
            catch (final SSLException e)
            {
              // Not yet received peer's close notification. Ignore this if we
              // are the initiator.
              if (!isInitiator)
              {
                throw e;
              }
            }
            finally
            {
              channel.close();
            }
          }
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isOpen()
    {
      return !sslEngine.isOutboundDone() || !sslEngine.isInboundDone();
    }



    /**
     * {@inheritDoc}
     */
    public int read(final ByteBuffer unwrappedData) throws IOException
    {
      synchronized (readLock)
      {
        // Repeat until there is some unwrapped data available or all available
        // data has been read from the underlying socket.
        if (!recvUnwrappedBuffer.hasRemaining())
        {
          final int read = doRecvAndUnwrap();
          if (read <= 0)
          {
            // No data read or end of stream.
            return read;
          }
        }

        // Copy available data.
        final int startPos = unwrappedData.position();
        if (recvUnwrappedBuffer.remaining() > unwrappedData.remaining())
        {
          // Unwrapped data does not fit in client buffer so copy one by at a
          // time: it's annoying that there is no easy way to do this with
          // ByteBuffers.
          while (unwrappedData.hasRemaining())
          {
            unwrappedData.put(recvUnwrappedBuffer.get());
          }
        }
        else
        {
          // Unwrapped data fits client buffer so block copy.
          unwrappedData.put(recvUnwrappedBuffer);
        }
        return unwrappedData.position() - startPos;
      }
    }



    /**
     * {@inheritDoc}
     */
    public int write(final ByteBuffer unwrappedData) throws IOException
    {
      // This method will block until the entire message is sent.
      final int bytesWritten = unwrappedData.remaining();

      // Synchronized in order to prevent interleaving and reordering.
      synchronized (writeLock)
      {
        // Repeat until the entire input data is written.
        while (unwrappedData.hasRemaining())
        {
          // Wrap and send the data.
          doWrapAndSend(unwrappedData);

          // Perform handshake if needed.
          if (isHandshaking(sslEngine.getHandshakeStatus()))
          {
            doHandshake(false /* isReading */);
          }
        }
      }

      return bytesWritten;
    }



    private void doHandshake(final boolean isReading) throws IOException
    {
      // This lock is probably unnecessary since tasks can be run in parallel,
      // but it adds no additional overhead so there's little harm in having
      // it.
      synchronized (handshakeLock)
      {
        while (true)
        {
          switch (sslEngine.getHandshakeStatus())
          {
          case NEED_TASK:
            Runnable runnable;
            while ((runnable = sslEngine.getDelegatedTask()) != null)
            {
              runnable.run();
            }
            break;
          case NEED_UNWRAP:
            // Block for writes, but be non-blocking for reads.
            if (isReading)
            {
              // Let doRecvAndUnwrap() deal with this.
              return;
            }

            // Need to do an unwrap (read) while writing.
            if (doRecvAndUnwrap() < 0)
            {
              throw new ClosedChannelException();
            }
            break;
          case NEED_WRAP:
            doWrapAndSend(EMPTY_BUFFER);
            break;
          default: // NOT_HANDSHAKING, FINISHED.
            return;
          }
        }
      }
    }



    // Attempt to read and unwrap the next SSL packet.
    private int doRecvAndUnwrap() throws IOException
    {
      // Synchronize SSL unwrap with channel reads.
      synchronized (unwrapLock)
      {
        // Repeat if there is underflow or overflow.
        boolean needRead = true;
        while (true)
        {
          // Read wrapped data if needed.
          if (needRead)
          {
            recvWrappedBuffer.compact(); // Prepare for append.
            final int read = channel.read(recvWrappedBuffer);
            recvWrappedBuffer.flip(); // Restore for read.
            if (read < 0)
            {
              // Peer abort?
              sslEngine.closeInbound();
              return -1;
            }
          }
          else
          {
            needRead = true;
          }

          // Unwrap.
          recvUnwrappedBuffer.compact(); // Prepare for append.
          final SSLEngineResult result = sslEngine.unwrap(recvWrappedBuffer,
              recvUnwrappedBuffer);
          recvUnwrappedBuffer.flip(); // Restore for read.

          switch (result.getStatus())
          {
          case BUFFER_OVERFLOW:
            // The unwrapped buffer is not big enough: resize and repeat.
            final int newAppSize = sslEngine.getSession()
                .getApplicationBufferSize();
            final ByteBuffer newRecvUnwrappedBuffer = ByteBuffer
                .allocate(recvUnwrappedBuffer.limit() + newAppSize);
            newRecvUnwrappedBuffer.put(recvUnwrappedBuffer);
            newRecvUnwrappedBuffer.flip();
            recvUnwrappedBuffer = newRecvUnwrappedBuffer;
            needRead = false;
            break; // Retry unwrap.
          case BUFFER_UNDERFLOW:
            // Not enough data was read. This either means that the inbound
            // buffer was too small, or not enough data is available.
            final int newPktSize = sslEngine.getSession().getPacketBufferSize();
            if (newPktSize > recvWrappedBuffer.capacity())
            {
              // Buffer needs resizing.
              final ByteBuffer newRecvWrappedBuffer = ByteBuffer
                  .allocate(newPktSize);
              newRecvWrappedBuffer.put(recvWrappedBuffer);
              newRecvWrappedBuffer.flip();
              recvWrappedBuffer = newRecvWrappedBuffer;
              break;
            }
            else
            {
              // Not enough data is available to read a complete SSL packet.
              return 0;
            }
          case CLOSED:
            // Peer sent SSL close notification.
            sslEngine.closeInbound();
            return -1;
          default: // OK
            if (recvUnwrappedBuffer.hasRemaining())
            {
              // Some application data was read so return it.
              return recvUnwrappedBuffer.remaining();
            }
            else if (isHandshaking(result.getHandshakeStatus()))
            {
              // No application data was read, but if we are handshaking then
              // try to continue.
              if (result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP
                  && !recvWrappedBuffer.hasRemaining())
              {
                // Not enough data is available to continue handshake.
                return 0;
              }
              else
              {
                // Continue handshake.
                doHandshake(true /* isReading */);
              }
            }
            else
            {
              // No data available and not handshaking.
              return 0;
            }
          }
        }
      }
    }



    // Attempt to wrap and send the next SSL packet.
    private int doWrapAndSend(final ByteBuffer unwrappedData)
        throws IOException
    {
      // Synchronize SSL wrap with channel writes.
      synchronized (wrapLock)
      {
        // Repeat while there is overflow.
        while (true)
        {
          final SSLEngineResult result = sslEngine.wrap(unwrappedData,
              sendWrappedBuffer);
          switch (result.getStatus())
          {
          case BUFFER_OVERFLOW:
            // The wrapped buffer is not big enough: resize and repeat.
            final int newSize = sslEngine.getSession().getPacketBufferSize();
            final ByteBuffer newSendWrappedBuffer = ByteBuffer
                .allocate(sendWrappedBuffer.position() + newSize);
            sendWrappedBuffer.flip();
            newSendWrappedBuffer.put(sendWrappedBuffer);
            sendWrappedBuffer = newSendWrappedBuffer;
            break; // Retry.
          case BUFFER_UNDERFLOW:
            // This should not happen for sends.
            throw new SSLException("Got unexpected underflow while wrapping");
          case CLOSED:
            throw new ClosedChannelException();
          default: // OK
            // Write the SSL packet: our IO stack will block until all the
            // data is written.
            sendWrappedBuffer.flip();
            while (sendWrappedBuffer.hasRemaining())
            {
              channel.write(sendWrappedBuffer);
            }
            final int written = sendWrappedBuffer.position();
            sendWrappedBuffer.clear();
            return written;
          }
        }
      }
    }



    private boolean isHandshaking(final HandshakeStatus status)
    {
      return status != HandshakeStatus.NOT_HANDSHAKING;
    }

  }



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
  }

  private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
  private static final DebugTracer TRACER = getTracer();



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



  private final ByteChannelImpl pimpl = new ByteChannelImpl();
  private final ByteChannel channel;
  private final SSLEngine sslEngine;

  private ByteBuffer recvWrappedBuffer;
  private ByteBuffer recvUnwrappedBuffer;
  private ByteBuffer sendWrappedBuffer;

  private final Object handshakeLock = new Object();
  private final Object unwrapLock = new Object();
  private final Object wrapLock = new Object();
  private final Object readLock = new Object();
  private final Object writeLock = new Object();



  private TLSByteChannel(final LDAPConnectionHandlerCfg config,
      final ClientConnection c, final ByteChannel channel,
      final SSLContext sslContext)
  {

    this.channel = channel;

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

    // Allocate read/write buffers.
    final SSLSession session = sslEngine.getSession();
    final int wrappedBufferSize = session.getPacketBufferSize();
    final int unwrappedBufferSize = session.getApplicationBufferSize();

    sendWrappedBuffer = ByteBuffer.allocate(wrappedBufferSize);
    recvWrappedBuffer = ByteBuffer.allocate(wrappedBufferSize);
    recvUnwrappedBuffer = ByteBuffer.allocate(unwrappedBufferSize);

    // Initially nothing has been received.
    recvWrappedBuffer.flip();
    recvUnwrappedBuffer.flip();
  }



  /**
   * {@inheritDoc}
   */
  public ByteChannel getChannel()
  {
    return pimpl;
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
    final String cipherString = sslEngine.getSession().getCipherSuite();
    for (final Map.Entry<String, Integer> mapEntry : CIPHER_MAP.entrySet())
    {
      if (cipherString.indexOf(mapEntry.getKey()) >= 0)
      {
        return mapEntry.getValue().intValue();
      }
    }
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isSecure()
  {
    return true;
  }

}
