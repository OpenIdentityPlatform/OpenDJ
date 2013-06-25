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
 *      Portions Copyright 2012 ForgeRock AS
 */

package org.opends.server.extensions;



import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.cert.Certificate;

import org.opends.server.api.ClientConnection;



/**
 * This class implements a SASL byte channel that can be used during
 * confidentiality and integrity.
 */
public final class SASLByteChannel implements ConnectionSecurityProvider
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
          saslContext.dispose();
          channel.close();
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen()
    {
      return saslContext != null;
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
        // Write data in sendBufferSize segments.
        while (unwrappedData.hasRemaining())
        {
          final int remaining = unwrappedData.remaining();
          final int wrapSize = (remaining < sendUnwrappedBufferSize) ? remaining
              : sendUnwrappedBufferSize;

          final byte[] wrappedDataBytes;
          if (unwrappedData.hasArray())
          {
            // Avoid extra copy if ByteBuffer is array based.
            wrappedDataBytes = saslContext.wrap(unwrappedData.array(),
                unwrappedData.arrayOffset() + unwrappedData.position(),
                wrapSize);
          }
          else
          {
            // Non-array based ByteBuffer, so copy.
            unwrappedData.get(sendUnwrappedBytes, 0, wrapSize);
            wrappedDataBytes = saslContext
                .wrap(sendUnwrappedBytes, 0, wrapSize);
          }
          unwrappedData.position(unwrappedData.position() + wrapSize);

          // Encode SASL packet: 4 byte length + wrapped data.
          if (sendWrappedBuffer.capacity() < wrappedDataBytes.length + 4)
          {
            // Resize the send buffer.
            sendWrappedBuffer =
                ByteBuffer.allocate(wrappedDataBytes.length + 4);
          }
          sendWrappedBuffer.clear();
          sendWrappedBuffer.putInt(wrappedDataBytes.length);
          sendWrappedBuffer.put(wrappedDataBytes);
          sendWrappedBuffer.flip();

          // Write the SASL packet: our IO stack will block until all the data
          // is written.
          channel.write(sendWrappedBuffer);
        }
      }

      return bytesWritten;
    }



    // Attempt to read and unwrap the next SASL packet.
    private int doRecvAndUnwrap() throws IOException
    {
      // Read SASL packets until some unwrapped data is produced or no more
      // data is available on the underlying channel.
      while (true)
      {
        // Read the wrapped packet length first.
        if (recvWrappedLength < 0)
        {
          // The channel read may only partially fill the buffer due to
          // buffering in the underlying channel layer (e.g. SSL layer), so
          // repeatedly read until the length has been read or we are sure
          // that we are unable to proceed.
          while (recvWrappedLengthBuffer.hasRemaining())
          {
            final int read = channel.read(recvWrappedLengthBuffer);
            if (read <= 0)
            {
              // Not enough data available or end of stream.
              return read;
            }
          }

          // Decode the length and reset the length buffer.
          recvWrappedLengthBuffer.flip();
          recvWrappedLength = recvWrappedLengthBuffer.getInt();
          recvWrappedLengthBuffer.clear();

          // Check that the length is valid.
          if (recvWrappedLength > recvWrappedBufferMaximumSize)
          {
            throw new IOException(
                "Client sent a SASL packet specifying a length "
                    + recvWrappedLength
                    + " which exceeds the negotiated limit of "
                    + recvWrappedBufferMaximumSize);
          }

          if (recvWrappedLength < 0)
          {
            throw new IOException(
                "Client sent a SASL packet specifying a negative length "
                    + recvWrappedLength);
          }

          // Prepare the recv buffer for reading.
          recvWrappedBuffer.clear();
          recvWrappedBuffer.limit(recvWrappedLength);
        }

        // Read the wrapped packet data.

        // The channel read may only partially fill the buffer due to
        // buffering in the underlying channel layer (e.g. SSL layer), so
        // repeatedly read until the data has been read or we are sure
        // that we are unable to proceed.
        while (recvWrappedBuffer.hasRemaining())
        {
          final int read = channel.read(recvWrappedBuffer);
          if (read <= 0)
          {
            // Not enough data available or end of stream.
            return read;
          }
        }

        // The complete packet has been read, so unwrap it.
        recvWrappedBuffer.flip();
        final byte[] unwrappedDataBytes = saslContext.unwrap(
            recvWrappedBuffer.array(), 0, recvWrappedLength);
        recvWrappedLength = -1;

        // Only return the unwrapped data if it was non-empty, otherwise try to
        // read another SASL packet.
        if (unwrappedDataBytes.length > 0)
        {
          recvUnwrappedBuffer = ByteBuffer.wrap(unwrappedDataBytes);
          return recvUnwrappedBuffer.remaining();
        }
      }
    }
  }



  /**
   * Return a SASL byte channel instance created using the specified parameters.
   *
   * @param c
   *          A client connection associated with the instance.
   * @param name
   *          The name of the instance (SASL mechanism name).
   * @param context
   *          A SASL context associated with the instance.
   * @return A SASL byte channel.
   */
  public static SASLByteChannel getSASLByteChannel(final ClientConnection c,
      final String name, final SASLContext context)
  {
    return new SASLByteChannel(c, name, context);
  }



  private final String name;
  private final ByteChannel channel;
  private final ByteChannelImpl pimpl = new ByteChannelImpl();
  private final SASLContext saslContext;

  private ByteBuffer recvUnwrappedBuffer;
  private final ByteBuffer recvWrappedBuffer;
  private final int recvWrappedBufferMaximumSize;
  private int recvWrappedLength = -1;
  private final ByteBuffer recvWrappedLengthBuffer = ByteBuffer.allocate(4);

  private final int sendUnwrappedBufferSize;
  private final byte[] sendUnwrappedBytes;
  private ByteBuffer sendWrappedBuffer;

  private final Object readLock = new Object();
  private final Object writeLock = new Object();



  /**
   * Create a SASL byte channel with the specified parameters that is capable of
   * processing a confidentiality/integrity SASL connection.
   *
   * @param connection
   *          The client connection to read/write the bytes.
   * @param name
   *          The SASL mechanism name.
   * @param saslContext
   *          The SASL context to process the data through.
   */
  private SASLByteChannel(final ClientConnection connection, final String name,
      final SASLContext saslContext)
  {
    this.name = name;
    this.saslContext = saslContext;

    channel = connection.getChannel();
    recvWrappedBufferMaximumSize = saslContext.getMaxReceiveBufferSize();
    sendUnwrappedBufferSize = saslContext.getMaxRawSendBufferSize();

    recvWrappedBuffer = ByteBuffer.allocate(recvWrappedBufferMaximumSize);
    recvUnwrappedBuffer = ByteBuffer.allocate(0);
    sendUnwrappedBytes = new byte[sendUnwrappedBufferSize];
    sendWrappedBuffer = ByteBuffer.allocate(sendUnwrappedBufferSize + 64);
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
    return new Certificate[0];
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getName()
  {
    return name;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int getSSF()
  {
    return saslContext.getSSF();
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
