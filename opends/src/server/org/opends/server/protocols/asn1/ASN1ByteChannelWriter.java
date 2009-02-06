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

import org.opends.server.types.ByteSequence;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.io.OutputStream;
import java.io.IOException;

/**
 * This class is for writing ASN.1 elements directly to an
 * NIO WritableByteChannel with an embedded ByteBuffer.
 * The NIO ByteBuffer will be flushed to the channel automatically
 * when full or when the flush() method is called.
 */
final class ASN1ByteChannelWriter implements ASN1Writer
{
  // The byte channel to write to.
  private final WritableByteChannel byteChannel;

  // The wrapped ASN.1 OutputStream writer.
  private final ASN1OutputStreamWriter writer;

  // The NIO ByteStringBuilder to write to.
  private final ByteBuffer byteBuffer;

  /**
   * An adaptor class provides a streaming interface to write to a
   * NIO ByteBuffer. This class is also responsible for writing
   * the ByteBuffer out to the channel when full and clearing it.
   */
  private class ByteBufferOutputStream extends OutputStream
  {
    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int i) throws IOException {
      if(!byteBuffer.hasRemaining())
      {
        // No more space left in the buffer, send out to the channel.
        ASN1ByteChannelWriter.this.flush();
      }
      byteBuffer.put((byte)i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] bytes) throws IOException {
      write(bytes, 0, bytes.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] bytes, int i, int i1) throws IOException {
      if(i < 0 || i1 < 0 || i + i1 > bytes.length)
        throw new IndexOutOfBoundsException();

      int bytesToWrite = i1;
      int len;
      while(bytesToWrite > 0)
      {
        len = byteBuffer.remaining();
        if(len < bytesToWrite)
        {
          byteBuffer.put(bytes, i + i1 - bytesToWrite, len);
          bytesToWrite -= len;
          ASN1ByteChannelWriter.this.flush();
        }
        else
        {
          byteBuffer.put(bytes, i + i1 - bytesToWrite, bytesToWrite);
          bytesToWrite = 0;
        }
      }
    }
  }

  /**
   * Constructs a new ASN1ByteChannelWriter.
   *
   * @param byteChannel The WritableByteChannel to write to.
   * @param writeBufferSize The NIO ByteBuffer size.
   */
  ASN1ByteChannelWriter(WritableByteChannel byteChannel,
                               int writeBufferSize)
  {
    this.byteChannel = byteChannel;
    this.byteBuffer = ByteBuffer.allocateDirect(writeBufferSize);

    ByteBufferOutputStream bufferStream = new ByteBufferOutputStream();
    this.writer = new ASN1OutputStreamWriter(bufferStream);
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeBoolean(boolean booleanValue) throws IOException {
    writer.writeBoolean(booleanValue);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeBoolean(byte type, boolean booleanValue)
      throws IOException {
    writer.writeBoolean(type, booleanValue);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeEndSet() throws IOException {
    writer.writeEndSet();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeEndSequence() throws IOException {
    writer.writeEndSequence();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeInteger(int intValue) throws IOException {
    writer.writeInteger(intValue);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeInteger(long longValue) throws IOException {
    writer.writeInteger(longValue);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeInteger(byte type, int intValue) throws IOException {
    writer.writeInteger(type, intValue);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeInteger(byte type, long longValue) throws IOException {
    writer.writeInteger(type, longValue);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeEnumerated(int intValue) throws IOException {
    writer.writeEnumerated(intValue);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeNull() throws IOException {
    writer.writeNull();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeNull(byte type) throws IOException {
    writer.writeNull(type);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(byte type, byte[] value,
                                     int offset, int length)
      throws IOException {
    writer.writeOctetString(type, value, offset, length);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(byte type, ByteSequence value)
      throws IOException {
    writer.writeOctetString(type, value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(byte type, String value)
      throws IOException {
    writer.writeOctetString(type, value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(byte[] value, int offset, int length)
      throws IOException {
    writer.writeOctetString(value, offset, length);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(ByteSequence value) throws IOException {
    writer.writeOctetString(value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(String value) throws IOException {
    writer.writeOctetString(value);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeStartSequence() throws IOException {
    writer.writeStartSequence();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeStartSequence(byte type) throws IOException {
    writer.writeStartSequence(type);
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeStartSet() throws IOException {
    writer.writeStartSet();
    return this;
  }

  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeStartSet(byte type) throws IOException {
    writer.writeStartSet(type);
    return this;
  }

  /**
   * Flush the entire contents of the NIO ByteBuffer out to the
   * channel.
   *
   * @throws IOException If an error occurs while flushing.
   */
  public void flush() throws IOException
  {
    byteBuffer.flip();
    while(byteBuffer.hasRemaining())
    {
      byteChannel.write(byteBuffer);
    }
    byteBuffer.clear();
  }

  /**
   * Closes this ASN.1 writer and the underlying channel.
   *
   * @throws IOException if an error occurs while closing the writer.
   */
  public void close() throws IOException {
    // Close the writer first to flush the writer to the NIO byte buffer.
    writer.close();
    flush();
    byteChannel.close();
  }
}
