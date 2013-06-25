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
 *      Portions copyright 2012-2013 ForgeRock AS
 */
package org.opends.server.extensions;



import static org.opends.server.loggers.debug.DebugLogger.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

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
    @Override
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
    @Override
    public boolean isOpen()
    {
      return !sslEngine.isOutboundDone() || !sslEngine.isInboundDone();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final ByteBuffer unwrappedData) throws IOException
    {
      synchronized (readLock)
      {
        // Only read and unwrap new data if needed.
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
          // Unwrapped data does not fit in client buffer so copy one byte at a
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
    @Override
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



    // It seems that the SSL engine does not remember if an error has already
    // occurred so we must cache it here and rethrow. See OPENDJ-652.
    private void abortOnSSLException() throws IOException
    {
      if (sslException != null)
      {
        throw sslException;
      }
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
        // Read SSL packets until some unwrapped data is produced or no more
        // data is available on the underlying channel.
        while (true)
        {
          // Unwrap any remaining data in the buffer.
          abortOnSSLException();
          recvUnwrappedBuffer.compact(); // Prepare for append.
          final SSLEngineResult result;
          try
          {
            result = sslEngine.unwrap(recvWrappedBuffer, recvUnwrappedBuffer);
          }
          catch (final SSLException e)
          {
            // Save the error - see abortOnSSLException().
            sslException = e;
            throw e;
          }
          finally
          {
            recvUnwrappedBuffer.flip(); // Restore for read.
          }

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
            break; // Retry unwrap.
          case BUFFER_UNDERFLOW:
            // Not enough data was read. This either means that the inbound
            // buffer was too small, or not enough data was read.
            final int newPktSize = sslEngine.getSession().getPacketBufferSize();
            if (newPktSize > recvWrappedBuffer.capacity())
            {
              // Increase the buffer size.
              final ByteBuffer newRecvWrappedBuffer = ByteBuffer
                  .allocate(newPktSize);
              newRecvWrappedBuffer.put(recvWrappedBuffer);
              newRecvWrappedBuffer.flip();
              recvWrappedBuffer = newRecvWrappedBuffer;
            }
            // Read wrapped data from underlying channel.
            recvWrappedBuffer.compact(); // Prepare for append.
            final int read = channel.read(recvWrappedBuffer);
            recvWrappedBuffer.flip(); // Restore for read.
            if (read <= 0)
            {
              // Not enough data is available to read a complete SSL packet, or
              // channel closed.
              return read;
            }
            // Loop and unwrap.
            break;
          case CLOSED:
            // Peer sent SSL close notification.
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
              doHandshake(true /* isReading */);
            }
            break;
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
          abortOnSSLException();
          final SSLEngineResult result;
          try
          {
            result = sslEngine.wrap(unwrappedData, sendWrappedBuffer);
          }
          catch (SSLException e)
          {
            // Save the error - see abortOnSSLException().
            sslException = e;
            throw e;
          }

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
            sslException =
              new SSLException("Got unexpected underflow while wrapping");
            throw sslException;
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



  /**
   * Map of cipher phrases to effective key size (bits). Taken from the
   * following RFCs: 5289, 4346, 3268,4132 and 4162 and the IANA Transport Layer
   * Security (TLS) Parameters.
   *
   * @see <a
   * href="http://www.iana.org/assignments/tls-parameters/tls-parameters.xml">
   * Transport Layer Security (TLS) Parameters, TLS Cipher Suite Registry</a>
   */
  static final Map<String, Integer> CIPHER_MAP;
  static
  {
    final Map<String, Integer> map = new LinkedHashMap<String, Integer>();
    map.put("_WITH_AES_256_", 256);
    map.put("_WITH_ARIA_256_", 256);
    map.put("_WITH_CAMELLIA_256_", 256);
    map.put("_WITH_AES_128_", 128);
    map.put("_WITH_ARIA_128_", 128);
    map.put("_WITH_SEED_", 128);
    map.put("_WITH_CAMELLIA_128_", 128);
    map.put("_WITH_IDEA_", 128);
    map.put("_WITH_RC4_128_", 128);
    map.put("_WITH_3DES_EDE_", 112);
    map.put("_WITH_FORTEZZA_", 96);
    map.put("_WITH_RC4_56_", 56);
    map.put("_WITH_DES_CBC_40_", 40);
    map.put("_WITH_RC2_CBC_40_", 40);
    map.put("_WITH_RC4_40_", 40);
    map.put("_WITH_DES40_", 40);
    map.put("_WITH_DES_", 56);
    map.put("_WITH_NULL_", 0);
    CIPHER_MAP = Collections.unmodifiableMap(map);
  }

  private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
  private static final DebugTracer TRACER = getTracer();

  private final ByteChannelImpl pimpl = new ByteChannelImpl();
  private final ByteChannel channel;
  private final SSLEngine sslEngine;

  private volatile SSLException sslException = null;
  private ByteBuffer recvWrappedBuffer;
  private ByteBuffer recvUnwrappedBuffer;
  private ByteBuffer sendWrappedBuffer;

  private final Object handshakeLock = new Object();
  private final Object unwrapLock = new Object();
  private final Object wrapLock = new Object();
  private final Object readLock = new Object();
  private final Object writeLock = new Object();



  /**
   * Creates an TLS byte channel instance using the specified LDAP connection
   * configuration, client connection, SSL context and socket channel
   * parameters.
   *
   * @param channel
   *          The underlying channel.
   * @param sslEngine
   *          The SSL engine to use.
   */
  public TLSByteChannel(final ByteChannel channel, final SSLEngine sslEngine)
  {
    this.channel = channel;
    this.sslEngine = sslEngine;

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
  @Override
  public ByteChannel getChannel()
  {
    return pimpl;
  }



  /**
   * {@inheritDoc}
   */
  @Override
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
  @Override
  public String getName()
  {
    return "TLS";
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int getSSF()
  {
    final Integer ssf = getSSF(sslEngine.getSession().getCipherSuite());
    if (ssf != null)
    {
      return ssf.intValue();
    }
    return 0;
  }

  /**
   * Returns the Security Strength Factor corresponding to the supplied cipher
   * string.
   *
   * @param cipherString
   *          the cipher to test for SSF
   * @return the Security Strength Factor corresponding to the supplied cipher
   *         string, null if the cipher cannot be recognized.
   */
  static Integer getSSF(final String cipherString)
  {
    for (final Map.Entry<String, Integer> mapEntry : CIPHER_MAP.entrySet())
    {
      if (cipherString.contains(mapEntry.getKey()))
      {
        return mapEntry.getValue();
      }
    }
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSecure()
  {
    return true;
  }

}
