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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;

import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.ByteString;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.io.IOException;
import java.io.InputStream;


/**
 * This class is for reading ASN.1 elements from a readable byte
 * channel. It will handle all partial element reads from the channel
 * and save any unread ASN.1 elements if required. All data read from
 * the channel will be ready to be read as ASN.1 elements no matter
 * how many times the channel is read. However, to minimize the the
 * amount of memory used by this reader, the client should read ASN.1
 * elements as soon as they are read off the channel.
 * <p>
 * {@code ASN1ByteChannelReader}s are created using the factory
 * methods in {@link ASN1}.
 * <p>
 * The client should use this class in the following manner:
 *<p>
 * When NIO signals new data is available in the channel, the client
 * should call {@link #processChannelData()}.
 *<p>
 * If bytes are read from the channel, the client should call
 * {@link #elementAvailable()} to see if a complete element is ready to
 * be read. However, if no data is actually read, the client should
 * wait for the next signal and try again.
 * <p>
 * As long as a complete element is ready, the client should read the
 * appropriate ASN.1 element(s). Once no more complete elements are
 * available, the client should call {@link #processChannelData()}
 * again to read more data (if available).
 * <p>
 * <b>NOTE:</b> Since this reader is non blocking, reading ASN.1
 * elements before making sure they are ready could result in
 * {@link IllegalBlockingModeException}s being thrown while reading
 * ASN.1 elements. Once an exception is thrown, the state of the reader
 * is no longer stable and can not be used again.
 */
public final class ASN1ByteChannelReader implements ASN1Reader
{
  // The byte channel to read from.
  private final ReadableByteChannel byteChannel;

  // The wrapped ASN.1 InputStream reader.
  private final ASN1InputStreamReader reader;

  // The NIO ByteStringBuilder that stores any immediate data read off
  // the channel.
  private final ByteBuffer byteBuffer;

  // The save buffer used to store any unprocessed data waiting
  // to be read as ASN.1 elements. (Usually due to reading
  // incomplete elements from the channel).
  private final ByteStringBuilder saveBuffer;

  // The save buffer reader.
  private final ByteSequenceReader saveBufferReader;

  /**
   * An adaptor class for reading from a save buffer and the NIO byte buffer
   * sequentially using the InputStream interface.
   *
   * Since the NIO byte buffer is re-used when reading off the channel, any
   * unused data will be appended to the save buffer before reading off the
   * channel again. This reader will always read the save buffer first before
   * the actual NIO byte buffer to ensure bytes are read in the same order
   * as they are received.
   *
   * The read methods of this stream will throw an IllegalBlockingModeException
   * if invoked when there are no data to read from the save buffer or the
   * channel buffer.
   *
   * The stream will not support the mark or reset methods.
   */
  private final class CombinedBufferInputStream extends InputStream
  {
    /**
     * {@inheritDoc}
     */
    @Override
    public int available()
    {
      // The number of available bytes is the sum of the save buffer
      // and the last read data in the NIO ByteStringBuilder.
      return saveBufferReader.remaining() + byteBuffer.remaining();
    }

    /**
     * Reads the next byte of data from the save buffer or channel buffer.
     * The value byte is returned as an int in the range 0 to 255.
     * If no byte is available in the save buffer or channel buffer,
     * IllegalBlockingModeException will be thrown.
     *
     * @return the next byte of data.
     * @throws IllegalBlockingModeException if there are more bytes available.
     */
    @Override
    public int read()
    {
      if(saveBufferReader.remaining() > 0)
      {
        // Try saved buffer first
        return 0xFF & saveBufferReader.get();
      }
      if(byteBuffer.remaining() > 0)
      {
        // Must still be on the channel buffer
        return 0xFF & byteBuffer.get();
      }

      throw new IllegalBlockingModeException();
    }

    /**
     * Reads up to len bytes of data from the save buffer or channel buffer
     * into an array of bytes. An attempt is made to read as many as len bytes,
     * but a smaller number may be read. The number of bytes actually read is
     * returned as an integer.
     *
     * If b is null, a NullPointerException is thrown.
     *
     * If the length of b is zero, then no bytes are read and 0 is returned;
     * otherwise, there is an attempt to read at least one byte. If no byte is
     * available in the save buffer or channel buffer,
     * IllegalBlockingModeException will be thrown; otherwise, at least one
     * byte is read and stored into b.
     *
     * The first byte read is stored into element b[0], the next one into
     * b[o1], and so on. The number of bytes read is, at most, equal to the
     * length of b. Let k be the number of bytes actually read; these bytes
     * will be stored in elements b[0] through b[k-1], leaving elements b[k]
     * through b[b.length-1] unaffected.
     *
     * @return the total number of bytes read into the buffer.
     * @throws IllegalBlockingModeException if there are more bytes available.
     */
    @Override
    public int read(byte[] b)
    {
      return read(b, 0, b.length);
    }

    /**
     * Reads up to len bytes of data from the save buffer or channel buffer
     * into an array of bytes. An attempt is made to read as many as len bytes,
     * but a smaller number may be read. The number of bytes actually read is
     * returned as an integer.
     *
     * If b is null, a NullPointerException is thrown.
     *
     * If off is negative, or len is negative, or off+len is greater than the
     * length of the array b, then an IndexOutOfBoundsException is thrown.
     *
     * If len is zero, then no bytes are read and 0 is returned; otherwise,
     * there is an attempt to read at least one byte. If no byte is available
     * in the save buffer or channel buffer, IllegalBlockingModeException will
     * be thrown; otherwise, at least one byte is read and stored into b.
     *
     * The first byte read is stored into element b[off], the next one into
     * b[off+1], and so on. The number of bytes read is, at most, equal to len.
     * Let k be the number of bytes actually read; these bytes will be stored
     * in elements b[off] through b[off+k-1], leaving elements b[off+k]
     * through b[off+len-1] unaffected.
     *
     * In every case, elements b[0] through b[off] and elements b[off+len]
     * through b[b.length-1] are unaffected.
     *
     * @return the total number of bytes read into the buffer.
     * @throws IllegalBlockingModeException if there are more bytes available.
     */
    @Override
    public int read(byte[] b, int off, int len)
    {
      if ((off < 0) || (len < 0) || (off + len > b.length))
      {
        throw new IndexOutOfBoundsException();
      }

      if(len == 0)
      {
        return 0;
      }

      int bytesCopied=0;
      int getLen;
      if(saveBufferReader.remaining() > 0)
      {
        // Copy out of the last saved buffer first
        getLen = Math.min(saveBufferReader.remaining(), len);
        saveBufferReader.get(b, off, getLen);
        bytesCopied += getLen;
      }
      if(bytesCopied < len && byteBuffer.remaining() > 0)
      {
        // Copy out of the channel buffer if we haven't got
        // everything we needed.
        getLen = Math.min(byteBuffer.remaining(), len - bytesCopied);
        byteBuffer.get(b, off + bytesCopied, getLen);
        bytesCopied += getLen;
      }
      if(bytesCopied < len)
      {
        throw new IllegalBlockingModeException();
      }

      return bytesCopied;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long length)
    {
      int bytesSkipped=0;
      int len;
      if(saveBufferReader.remaining() > 0)
      {
        // Skip in the last saved buffer first
        len = Math.min(saveBufferReader.remaining(), (int)length);
        saveBufferReader.position(saveBufferReader.position() + len);
        bytesSkipped += len;
      }
      if(bytesSkipped < length && byteBuffer.remaining() > 0)
      {
        //Skip in the channel buffer if we haven't skipped enough.
        len = Math.min(byteBuffer.remaining(), (int)length - bytesSkipped);
        byteBuffer.position(byteBuffer.position() + len);
        bytesSkipped += len;
      }
      if(bytesSkipped < length)
      {
        throw new IllegalBlockingModeException();
      }

      return bytesSkipped;
    }
  }

  /**
   * Creates a new ASN.1 byte channel reader whose source is the
   * provided readable byte channel, having a user defined buffer
   * size, and user defined maximum BER element size.
   *
   * @param channel
   *          The readable byte channel to use.
   * @param bufferSize
   *          The buffer size to use when reading from the channel.
   * @param maxElementSize
   *          The max ASN.1 element size this reader will read.
   */
  ASN1ByteChannelReader(ReadableByteChannel channel, int bufferSize,
      int maxElementSize)
  {
    this.byteChannel = channel;
    this.byteBuffer = ByteBuffer.allocateDirect(bufferSize);
    this.byteBuffer.flip();
    this.saveBuffer = new ByteStringBuilder();
    this.saveBufferReader = saveBuffer.asReader();

    CombinedBufferInputStream bufferStream = new CombinedBufferInputStream();
    this.reader = new ASN1InputStreamReader(bufferStream, maxElementSize);
  }

  /**
   * Process any new data on the channel so they can be read as ASN.1
   * elements. This method should only be called when there are no
   * more complete elements in the reader. Calling this method when
   * there are complete elements still in the reader will result in
   * unnecessary memory allocations to store any unread data. This
   * method will perform the following operations:
   * <ul>
   * <li>Clear the save buffer if everything was read.
   * <li>Append any unread data from the NIO byte buffer to the save
   * buffer.
   * <li>Clear the NIO byte buffer and read from the channel.
   * </ul>
   *
   * @return The number of bytes read from the channel or -1 if
   *         channel is closed.
   * @throws IOException
   *           If an exception occurs while reading from the channel.
   */
  public int processChannelData() throws IOException
  {
    // Clear the save buffer if we have read all of it
    if (saveBufferReader.remaining() == 0)
    {
      saveBuffer.clear();
      saveBufferReader.rewind();
    }

    // Append any unused data in the channel buffer to the save buffer
    if (byteBuffer.remaining() > 0)
    {
      saveBuffer.append(byteBuffer, byteBuffer.remaining());
    }

    byteBuffer.clear();
    int read = byteChannel.read(byteBuffer);
    byteBuffer.flip();
    return read;
  }

  /**
   * Determines if a complete ASN.1 element is ready to be read from
   * channel.
   *
   * @return <code>true</code> if another complete element is available or
   *         <code>false</code> otherwise.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  public boolean elementAvailable() throws ASN1Exception
  {
    return reader.elementAvailable();
  }

  /**
   * Determines if the channel contains at least one ASN.1 element to be read.
   *
   * @return <code>true</code> if another element is available or
   *         <code>false</code> otherwise.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  public boolean hasNextElement() throws ASN1Exception {
    return reader.hasNextElement();
  }

  /**
   * {@inheritDoc}
   */
  public int peekLength() throws ASN1Exception {
    return reader.peekLength();
  }

  /**
   * {@inheritDoc}
   */
  public byte peekType() throws ASN1Exception {
    return reader.peekType();
  }

  /**
   * {@inheritDoc}
   */
  public boolean readBoolean() throws ASN1Exception {
    return reader.readBoolean();
  }

  /**
   * {@inheritDoc}
   */
  public void readEndSequence() throws ASN1Exception {
    reader.readEndSequence();
  }

  /**
   * {@inheritDoc}
   */
  public void readEndSet() throws ASN1Exception {
    reader.readEndSet();
  }

  /**
   * {@inheritDoc}
   */
  public int readEnumerated() throws ASN1Exception {
    return reader.readEnumerated();
  }

  /**
   * {@inheritDoc}
   */
  public long readInteger() throws ASN1Exception {
    return reader.readInteger();
  }

  /**
   * {@inheritDoc}
   */
  public void readNull() throws ASN1Exception {
    reader.readNull();
  }

  /**
   * {@inheritDoc}
   */
  public ByteString readOctetString() throws ASN1Exception {
    return reader.readOctetString();
  }

  /**
   * {@inheritDoc}
   */
  public void readOctetString(ByteStringBuilder buffer) throws ASN1Exception {
    reader.readOctetString(buffer);
  }

  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString() throws ASN1Exception {
    return reader.readOctetStringAsString();
  }

  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString(String charSet) throws ASN1Exception {
    return reader.readOctetStringAsString(charSet);
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSequence() throws ASN1Exception {
    reader.readStartSequence();
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSet() throws ASN1Exception {
    reader.readStartSet();
  }

  /**
   * {@inheritDoc}
   */
  public void close() throws IOException {
    reader.close();
    byteChannel.close();
  }

  /**
   * {@inheritDoc}
   */
  public void skipElement() throws ASN1Exception
  {
    reader.skipElement();
  }
}
