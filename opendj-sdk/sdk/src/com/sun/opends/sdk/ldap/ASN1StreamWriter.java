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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package com.sun.opends.sdk.ldap;



import static com.sun.opends.sdk.messages.Messages.*;
import static org.opends.sdk.asn1.ASN1Constants.*;

import java.io.IOException;
import java.util.logging.Level;

import org.opends.sdk.ByteSequence;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.asn1.AbstractASN1Writer;

import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.PoolableObject;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * Grizzly ASN1 writer implementation.
 */
public final class ASN1StreamWriter extends AbstractASN1Writer implements
    ASN1Writer, PoolableObject
{
  private class ChildSequenceBuffer implements SequenceBuffer
  {
    private SequenceBuffer parent;

    private ChildSequenceBuffer child;

    private final ByteStringBuilder buffer = new ByteStringBuilder(
        SUB_SEQUENCE_BUFFER_INIT_SIZE);



    public SequenceBuffer endSequence() throws IOException
    {
      writeLength(parent, buffer.length());
      parent.writeByteArray(buffer.getBackingArray(), 0, buffer
          .length());

      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 END SEQUENCE(length=%d)", buffer.length()));
      }

      return parent;
    }



    public SequenceBuffer startSequence(byte type) throws IOException
    {
      if (child == null)
      {
        child = new ChildSequenceBuffer();
        child.parent = this;
      }

      buffer.append(type);
      child.buffer.clear();

      return child;
    }



    public void writeByte(byte b) throws IOException
    {
      buffer.append(b);
    }



    public void writeByteArray(byte[] bs, int offset, int length)
        throws IOException
    {
      buffer.append(bs, offset, length);
    }
  }



  private class RootSequenceBuffer implements SequenceBuffer
  {
    private ChildSequenceBuffer child;



    public SequenceBuffer endSequence() throws IOException
    {
      LocalizableMessage message = ERR_ASN1_SEQUENCE_WRITE_NOT_STARTED.get();
      throw new IllegalStateException(message.toString());
    }



    public SequenceBuffer startSequence(byte type) throws IOException
    {
      if (child == null)
      {
        child = new ChildSequenceBuffer();
        child.parent = this;
      }

      streamWriter.writeByte(type);
      child.buffer.clear();

      return child;
    }



    public void writeByte(byte b) throws IOException
    {
      streamWriter.writeByte(b);
    }



    public void writeByteArray(byte[] bs, int offset, int length)
        throws IOException
    {
      streamWriter.writeByteArray(bs, offset, length);
    }
  }



  private interface SequenceBuffer
  {
    public SequenceBuffer endSequence() throws IOException;



    public SequenceBuffer startSequence(byte type) throws IOException;



    public void writeByte(byte b) throws IOException;



    public void writeByteArray(byte[] bs, int offset, int length)
        throws IOException;
  }



  private static final int SUB_SEQUENCE_BUFFER_INIT_SIZE = 1024;

  private StreamWriter streamWriter;

  private SequenceBuffer sequenceBuffer;

  private final RootSequenceBuffer rootBuffer;



  /**
   * Creates a new ASN.1 writer that writes to a StreamWriter.
   */
  public ASN1StreamWriter()
  {
    this.sequenceBuffer = this.rootBuffer = new RootSequenceBuffer();
  }



  /**
   * Closes this ASN.1 writer and the underlying outputstream. Any
   * unfinished sequences will be ended.
   *
   * @throws IOException
   *           if an error occurs while closing the stream.
   */
  public void close() throws IOException
  {
    streamWriter.close();
  }



  /**
   * Flushes the stream.
   *
   * @throws IOException
   *           If an I/O error occurs
   */
  public void flush() throws IOException
  {
    streamWriter.flush();
  }



  public void prepare()
  {
    // nothing to do
  }



  public void release()
  {
    streamWriter = null;
    sequenceBuffer = rootBuffer;
  }



  public void setStreamWriter(StreamWriter streamWriter)
  {
    this.streamWriter = streamWriter;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeBoolean(byte type, boolean booleanValue)
      throws IOException
  {
    sequenceBuffer.writeByte(type);
    writeLength(sequenceBuffer, 1);
    sequenceBuffer.writeByte(booleanValue ? BOOLEAN_VALUE_TRUE
        : BOOLEAN_VALUE_FALSE);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "WRITE ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)", type,
          1, String.valueOf(booleanValue)));
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeEndSequence() throws IOException,
      IllegalStateException
  {
    sequenceBuffer = sequenceBuffer.endSequence();

    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeEndSet() throws IOException
  {
    return writeEndSequence();
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeEnumerated(byte type, int intValue)
      throws IOException
  {
    return writeInteger(type, intValue);
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeInteger(byte type, int intValue)
      throws IOException
  {
    sequenceBuffer.writeByte(type);
    if (((intValue < 0) && ((intValue & 0xFFFFFF80) == 0xFFFFFF80))
        || ((intValue & 0x0000007F) == intValue))
    {
      writeLength(sequenceBuffer, 1);
      sequenceBuffer.writeByte((byte) (intValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 1, intValue));
      }
    }
    else if (((intValue < 0) && ((intValue & 0xFFFF8000) == 0xFFFF8000))
        || ((intValue & 0x00007FFF) == intValue))
    {
      writeLength(sequenceBuffer, 2);
      sequenceBuffer.writeByte((byte) ((intValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (intValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 2, intValue));
      }
    }
    else if (((intValue < 0) && ((intValue & 0xFF800000) == 0xFF800000))
        || ((intValue & 0x007FFFFF) == intValue))
    {
      writeLength(sequenceBuffer, 3);
      sequenceBuffer.writeByte((byte) ((intValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((intValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (intValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 3, intValue));
      }
    }
    else
    {
      writeLength(sequenceBuffer, 4);
      sequenceBuffer.writeByte((byte) ((intValue >> 24) & 0xFF));
      sequenceBuffer.writeByte((byte) ((intValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((intValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (intValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 4, intValue));
      }
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeInteger(byte type, long longValue)
      throws IOException
  {
    sequenceBuffer.writeByte(type);
    if (((longValue < 0) && ((longValue & 0xFFFFFFFFFFFFFF80L) == 0xFFFFFFFFFFFFFF80L))
        || ((longValue & 0x000000000000007FL) == longValue))
    {
      writeLength(sequenceBuffer, 1);
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 1, longValue));
      }
    }
    else if (((longValue < 0) && ((longValue & 0xFFFFFFFFFFFF8000L) == 0xFFFFFFFFFFFF8000L))
        || ((longValue & 0x0000000000007FFFL) == longValue))
    {
      writeLength(sequenceBuffer, 2);
      sequenceBuffer.writeByte((byte) ((longValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 2, longValue));
      }
    }
    else if (((longValue < 0) && ((longValue & 0xFFFFFFFFFF800000L) == 0xFFFFFFFFFF800000L))
        || ((longValue & 0x00000000007FFFFFL) == longValue))
    {
      writeLength(sequenceBuffer, 3);
      sequenceBuffer.writeByte((byte) ((longValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 3, longValue));
      }
    }
    else if (((longValue < 0) && ((longValue & 0xFFFFFFFF80000000L) == 0xFFFFFFFF80000000L))
        || ((longValue & 0x000000007FFFFFFFL) == longValue))
    {
      writeLength(sequenceBuffer, 4);
      sequenceBuffer.writeByte((byte) ((longValue >> 24) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 4, longValue));
      }
    }
    else if (((longValue < 0) && ((longValue & 0xFFFFFF8000000000L) == 0xFFFFFF8000000000L))
        || ((longValue & 0x0000007FFFFFFFFFL) == longValue))
    {
      writeLength(sequenceBuffer, 5);
      sequenceBuffer.writeByte((byte) ((longValue >> 32) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 24) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 5, longValue));
      }
    }
    else if (((longValue < 0) && ((longValue & 0xFFFF800000000000L) == 0xFFFF800000000000L))
        || ((longValue & 0x00007FFFFFFFFFFFL) == longValue))
    {
      writeLength(sequenceBuffer, 6);
      sequenceBuffer.writeByte((byte) ((longValue >> 40) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 32) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 24) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 6, longValue));
      }
    }
    else if (((longValue < 0) && ((longValue & 0xFF80000000000000L) == 0xFF80000000000000L))
        || ((longValue & 0x007FFFFFFFFFFFFFL) == longValue))
    {
      writeLength(sequenceBuffer, 7);
      sequenceBuffer.writeByte((byte) ((longValue >> 48) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 40) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 32) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 24) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 7, longValue));
      }
    }
    else
    {
      writeLength(sequenceBuffer, 8);
      sequenceBuffer.writeByte((byte) ((longValue >> 56) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 48) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 40) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 32) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 24) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 16) & 0xFF));
      sequenceBuffer.writeByte((byte) ((longValue >> 8) & 0xFF));
      sequenceBuffer.writeByte((byte) (longValue & 0xFF));
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "WRITE ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            type, 8, longValue));
      }
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeNull(byte type) throws IOException
  {
    sequenceBuffer.writeByte(type);
    writeLength(sequenceBuffer, 0);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "WRITE ASN.1 NULL(type=0x%x, length=%d)", type, 0));
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(byte type, byte[] value,
      int offset, int length) throws IOException
  {
    sequenceBuffer.writeByte(type);
    writeLength(sequenceBuffer, length);
    sequenceBuffer.writeByteArray(value, offset, length);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String
          .format("WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d)",
              type, length));
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(byte type, ByteSequence value)
      throws IOException
  {
    sequenceBuffer.writeByte(type);
    writeLength(sequenceBuffer, value.length());
    // TODO: Is there a more efficient way to do this?
    for (int i = 0; i < value.length(); i++)
    {
      sequenceBuffer.writeByte(value.byteAt(i));
    }

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d)", type, value
              .length()));
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeOctetString(byte type, String value)
      throws IOException
  {
    sequenceBuffer.writeByte(type);

    if (value == null)
    {
      writeLength(sequenceBuffer, 0);
      return this;
    }

    byte[] bytes = StaticUtils.getBytes(value);
    writeLength(sequenceBuffer, bytes.length);
    sequenceBuffer.writeByteArray(bytes, 0, bytes.length);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "WRITE ASN.1 OCTETSTRING(type=0x%x, length=%d, "
              + "value=%s)", type, bytes.length, value));
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeStartSequence(byte type) throws IOException
  {
    // Get a child sequence buffer
    sequenceBuffer = sequenceBuffer.startSequence(type);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "WRITE ASN.1 START SEQUENCE(type=0x%x)", type));
    }
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Writer writeStartSet(byte type) throws IOException
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    return writeStartSequence(type);
  }



  /**
   * Writes the provided value for use as the length of an ASN.1
   * element.
   *
   * @param buffer
   *          The sequence buffer to write to.
   * @param length
   *          The length to encode for use in an ASN.1 element.
   * @throws IOException
   *           if an error occurs while writing.
   */
  private void writeLength(SequenceBuffer buffer, int length)
      throws IOException
  {
    if (length < 128)
    {
      buffer.writeByte((byte) length);
    }
    else if ((length & 0x000000FF) == length)
    {
      buffer.writeByte((byte) 0x81);
      buffer.writeByte((byte) (length & 0xFF));
    }
    else if ((length & 0x0000FFFF) == length)
    {
      buffer.writeByte((byte) 0x82);
      buffer.writeByte((byte) ((length >> 8) & 0xFF));
      buffer.writeByte((byte) (length & 0xFF));
    }
    else if ((length & 0x00FFFFFF) == length)
    {
      buffer.writeByte((byte) 0x83);
      buffer.writeByte((byte) ((length >> 16) & 0xFF));
      buffer.writeByte((byte) ((length >> 8) & 0xFF));
      buffer.writeByte((byte) (length & 0xFF));
    }
    else
    {
      buffer.writeByte((byte) 0x84);
      buffer.writeByte((byte) ((length >> 24) & 0xFF));
      buffer.writeByte((byte) ((length >> 16) & 0xFF));
      buffer.writeByte((byte) ((length >> 8) & 0xFF));
      buffer.writeByte((byte) (length & 0xFF));
    }
  }
}
