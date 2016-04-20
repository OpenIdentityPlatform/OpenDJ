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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.ReadableByteChannel;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;

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
final class ASN1ByteChannelReader implements ASN1Reader
{
  /** The byte channel to read from. */
  private final ReadableByteChannel byteChannel;

  /** The wrapped ASN.1 reader. */
  private final ASN1Reader reader;

  /** The NIO ByteStringBuilder that stores any immediate data read off the channel. */
  private final ByteBuffer byteBuffer;

  /**
   * The save buffer used to store any unprocessed data waiting to be read as
   * ASN.1 elements. (Usually due to reading incomplete elements from the
   * channel).
   */
  private final ByteStringBuilder saveBuffer;

  /** The save buffer reader. */
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
        return 0xFF & saveBufferReader.readByte();
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
      if (off < 0 || len < 0 || off + len > b.length)
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
        saveBufferReader.readBytes(b, off, getLen);
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
    this.byteBuffer = ByteBuffer.allocate(bufferSize);
    this.byteBuffer.flip();
    this.saveBuffer = new ByteStringBuilder();
    this.saveBufferReader = saveBuffer.asReader();

    CombinedBufferInputStream bufferStream = new CombinedBufferInputStream();
    this.reader = ASN1.getReader(bufferStream, maxElementSize);
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
      saveBuffer.appendBytes(byteBuffer, byteBuffer.remaining());
    }

    byteBuffer.clear();
    try
    {
      return byteChannel.read(byteBuffer);
    }
    finally
    {
      // Make sure that the buffer is flipped even if the read fails in order to
      // ensure that subsequent calls which query the remaining data return
      // valid results.
      byteBuffer.flip();
    }
  }

  /**
   * Determines if a complete ASN.1 element is ready to be read from
   * channel.
   *
   * @return <code>true</code> if another complete element is available or
   *         <code>false</code> otherwise.
   * @throws IOException If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  @Override
  public boolean elementAvailable() throws IOException
  {
    return reader.elementAvailable();
  }

  /**
   * Determines if the channel contains at least one ASN.1 element to be read.
   *
   * @return <code>true</code> if another element is available or
   *         <code>false</code> otherwise.
   * @throws IOException If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  @Override
  public boolean hasNextElement() throws IOException {
    return reader.hasNextElement();
  }

  /**
   * Returns {@code true} if this ASN.1 reader contains unread data.
   *
   * @return {@code true} if this ASN.1 reader contains unread data.
   */
  public boolean hasRemainingData()
  {
    return saveBufferReader.remaining() != 0 || byteBuffer.remaining() != 0;
  }

  @Override
  public int peekLength() throws IOException {
    return reader.peekLength();
  }

  @Override
  public byte peekType() throws IOException {
    return reader.peekType();
  }

  @Override
  public boolean readBoolean() throws IOException {
    return reader.readBoolean();
  }

  @Override
  public boolean readBoolean(byte type) throws IOException {
    return reader.readBoolean(type);
  }

  @Override
  public void readEndExplicitTag() throws IOException {
    reader.readEndExplicitTag();
  }

  @Override
  public void readEndSequence() throws IOException {
    reader.readEndSequence();
  }

  @Override
  public void readEndSet() throws IOException {
    reader.readEndSet();
  }

  @Override
  public int readEnumerated() throws IOException {
    return reader.readEnumerated();
  }

  @Override
  public int readEnumerated(byte type) throws IOException {
    return reader.readEnumerated(type);
  }

  @Override
  public long readInteger() throws IOException {
    return reader.readInteger();
  }

  @Override
  public long readInteger(byte type) throws IOException {
    return reader.readInteger(type);
  }

  @Override
  public void readNull() throws IOException {
    reader.readNull();
  }

  @Override
  public void readNull(byte type) throws IOException {
    reader.readNull(type);
  }

  @Override
  public ByteString readOctetString() throws IOException {
    return reader.readOctetString();
  }

  @Override
  public ByteString readOctetString(byte type) throws IOException {
    return readOctetString(type);
  }

  @Override
  public ByteStringBuilder readOctetString(ByteStringBuilder buffer) throws IOException {
    return reader.readOctetString(buffer);
  }

  @Override
  public ByteStringBuilder readOctetString(byte type, ByteStringBuilder builder) throws IOException {
    return readOctetString(type, builder);
  }

  @Override
  public String readOctetStringAsString() throws IOException {
    return reader.readOctetStringAsString();
  }

  @Override
  public String readOctetStringAsString(byte type) throws IOException {
    return readOctetStringAsString(type);
  }

  @Override
  public void readStartExplicitTag() throws IOException {
    reader.readStartExplicitTag();
  }

  @Override
  public void readStartExplicitTag(byte type) throws IOException {
    reader.readStartExplicitTag(type);
  }

  @Override
  public void readStartSequence() throws IOException {
    reader.readStartSequence();
  }

  @Override
  public void readStartSequence(byte type) throws IOException {
    reader.readStartSequence(type);
  }

  @Override
  public void readStartSet() throws IOException {
    reader.readStartSet();
  }

  @Override
  public void readStartSet(byte type) throws IOException {
    reader.readStartSet(type);
  }

  @Override
  public void close() throws IOException {
    reader.close();
    byteChannel.close();
  }

  @Override
  public ASN1Reader skipElement() throws IOException {
    reader.skipElement();
    return this;
  }

  @Override
  public ASN1Reader skipElement(byte type) throws DecodeException, IOException
  {
    reader.skipElement(type);
    return this;
  }
}
