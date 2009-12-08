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
package org.opends.sdk.ldap;



import static com.sun.opends.sdk.messages.Messages.*;
import static org.opends.sdk.ldap.LDAPConstants.*;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.logging.Level;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.AbstractASN1Reader;

import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.utils.PoolableObject;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * Grizzly ASN1 reader implementation.
 */
final class ASN1StreamReader extends AbstractASN1Reader implements
    PoolableObject, ASN1Reader
{
  class ChildSequenceLimiter implements SequenceLimiter
  {
    private SequenceLimiter parent;

    private ChildSequenceLimiter child;

    private int readLimit;

    private int bytesRead;



    public void checkLimit(int readSize) throws IOException,
        BufferUnderflowException
    {
      if ((readLimit > 0) && (bytesRead + readSize > readLimit))
      {
        throw new BufferUnderflowException();
      }

      parent.checkLimit(readSize);

      bytesRead += readSize;
    }



    public SequenceLimiter endSequence() throws IOException
    {
      parent.checkLimit(remaining());

      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINE)
          && remaining() > 0)
      {
        StaticUtils.DEBUG_LOG.fine(String.format(
            "Ignoring %d unused trailing bytes in ASN.1 SEQUENCE",
            remaining()));
      }

      for (int i = 0; i < remaining(); i++)
      {
        streamReader.readByte();
      }

      return parent;
    }



    public int remaining()
    {
      return readLimit - bytesRead;
    }



    public ChildSequenceLimiter startSequence(int readLimit)
    {
      if (child == null)
      {
        child = new ChildSequenceLimiter();
        child.parent = this;
      }

      child.readLimit = readLimit;
      child.bytesRead = 0;

      return child;
    }
  }



  class RootSequenceLimiter implements SequenceLimiter
  {
    private ChildSequenceLimiter child;



    public void checkLimit(int readSize)
    {
    }



    public ChildSequenceLimiter endSequence() throws DecodeException
    {
      LocalizableMessage message = ERR_ASN1_SEQUENCE_READ_NOT_STARTED.get();
      throw new IllegalStateException(message.toString());
    }



    public int remaining()
    {
      return streamReader.availableDataSize();
    }



    public ChildSequenceLimiter startSequence(int readLimit)
    {
      if (child == null)
      {
        child = new ChildSequenceLimiter();
        child.parent = this;
      }

      child.readLimit = readLimit;
      child.bytesRead = 0;

      return child;
    }
  }



  private interface SequenceLimiter
  {
    public void checkLimit(int readSize) throws IOException,
        BufferUnderflowException;



    public SequenceLimiter endSequence() throws IOException;



    public int remaining();



    public SequenceLimiter startSequence(int readLimit);
  }



  private static final int MAX_STRING_BUFFER_SIZE = 1024;

  private int state = ELEMENT_READ_STATE_NEED_TYPE;

  private byte peekType = 0;

  private int peekLength = -1;

  private int lengthBytesNeeded = 0;

  private final int maxElementSize;

  private StreamReader streamReader;

  private final RootSequenceLimiter rootLimiter;

  private SequenceLimiter readLimiter;

  private final byte[] buffer;



  /**
   * Creates a new ASN1 reader whose source is the provided input stream
   * and having a user defined maximum BER element size.
   *
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   */
  public ASN1StreamReader(int maxElementSize)
  {
    this.readLimiter = this.rootLimiter = new RootSequenceLimiter();
    this.buffer = new byte[MAX_STRING_BUFFER_SIZE];
    this.maxElementSize = maxElementSize;
  }



  /**
   * Closes this ASN.1 reader and the underlying stream.
   *
   * @throws IOException
   *           if an I/O error occurs
   */
  public void close() throws IOException
  {
    // close the stream reader.
    streamReader.close();
  }



  /**
   * Determines if a complete ASN.1 element is ready to be read from the
   * stream reader.
   *
   * @return <code>true</code> if another complete element is available
   *         or <code>false</code> otherwise.
   * @throws IOException
   *           If an error occurs while trying to decode an ASN1
   *           element.
   */
  public boolean elementAvailable() throws IOException
  {
    if ((state == ELEMENT_READ_STATE_NEED_TYPE) && !needTypeState(true))
    {
      return false;
    }
    if ((state == ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE)
        && !needFirstLengthByteState(true))
    {
      return false;
    }
    if ((state == ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES)
        && !needAdditionalLengthBytesState(true))
    {
      return false;
    }

    return peekLength <= readLimiter.remaining();
  }



  /**
   * Determines if the input stream contains at least one ASN.1 element
   * to be read.
   *
   * @return <code>true</code> if another element is available or
   *         <code>false</code> otherwise.
   * @throws IOException
   *           If an error occurs while trying to decode an ASN1
   *           element.
   */
  public boolean hasNextElement() throws IOException
  {
    return (state != ELEMENT_READ_STATE_NEED_TYPE)
        || needTypeState(true);
  }



  /**
   * {@inheritDoc}
   */
  public int peekLength() throws IOException
  {
    peekType();

    switch (state)
    {
    case ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE:
      needFirstLengthByteState(false);
      break;

    case ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES:
      needAdditionalLengthBytesState(false);
    }

    return peekLength;
  }



  /**
   * {@inheritDoc}
   */
  public byte peekType() throws IOException
  {
    if (state == ELEMENT_READ_STATE_NEED_TYPE)
    {
      needTypeState(false);
    }

    return peekType;
  }



  public void prepare()
  {
    // Nothing to do
  }



  /**
   * {@inheritDoc}
   */
  public boolean readBoolean() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    if (peekLength != 1)
    {
      LocalizableMessage message = ERR_ASN1_BOOLEAN_INVALID_LENGTH.get(peekLength);
      throw DecodeException.fatalError(message);
    }

    readLimiter.checkLimit(peekLength);
    byte readByte = streamReader.readByte();

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "READ ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)",
          peekType, peekLength, String.valueOf(readByte != 0x00)));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return readByte != 0x00;
  }



  /**
   * {@inheritDoc}
   */
  public void readEndSequence() throws IOException,
      IllegalStateException
  {
    readLimiter = readLimiter.endSequence();

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String
          .format("READ ASN.1 END SEQUENCE"));
    }

    // Reset the state
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }



  /**
   * {@inheritDoc}
   */
  public void readEndSet() throws IOException
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    readEndSequence();
  }



  /**
   * {@inheritDoc}
   */
  public int readEnumerated() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    if ((peekLength < 1) || (peekLength > 4))
    {
      LocalizableMessage message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
      throw DecodeException.fatalError(message);
    }

    // From an implementation point of view, an enumerated value is
    // equivalent to an integer.
    return (int) readInteger();
  }



  /**
   * {@inheritDoc}
   */
  public long readInteger() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    if ((peekLength < 1) || (peekLength > 8))
    {
      LocalizableMessage message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
      throw DecodeException.fatalError(message);
    }

    readLimiter.checkLimit(peekLength);
    if (peekLength > 4)
    {
      long longValue = 0;
      for (int i = 0; i < peekLength; i++)
      {
        int readByte = streamReader.readByte();
        if ((i == 0) && (((byte) readByte) < 0))
        {
          longValue = 0xFFFFFFFFFFFFFFFFL;
        }
        longValue = (longValue << 8) | (readByte & 0xFF);
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
      return longValue;
    }
    else
    {
      int intValue = 0;
      for (int i = 0; i < peekLength; i++)
      {
        int readByte = streamReader.readByte();
        if ((i == 0) && (((byte) readByte) < 0))
        {
          intValue = 0xFFFFFFFF;
        }
        intValue = (intValue << 8) | (readByte & 0xFF);
      }

      if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
      {
        StaticUtils.DEBUG_LOG.finest(String.format(
            "READ ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
            peekType, peekLength, intValue));
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
      return intValue;
    }
  }



  /**
   * {@inheritDoc}
   */
  public void readNull() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    // Make sure that the decoded length is exactly zero byte.
    if (peekLength != 0)
    {
      LocalizableMessage message = ERR_ASN1_NULL_INVALID_LENGTH.get(peekLength);
      throw DecodeException.fatalError(message);
    }

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String
          .format("READ ASN.1 NULL(type=0x%x, length=%d)", peekType,
              peekLength));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString readOctetString() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    if (peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return ByteString.empty();
    }

    readLimiter.checkLimit(peekLength);
    // Copy the value and construct the element to return.
    byte[] value = new byte[peekLength];
    streamReader.readByteArray(value);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "READ ASN.1 OCTETSTRING(type=0x%x, length=%d)", peekType,
          peekLength));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return ByteString.wrap(value);
  }



  /**
   * {@inheritDoc}
   */
  public ByteStringBuilder readOctetString(ByteStringBuilder builder)
      throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    if (peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return builder;
    }

    readLimiter.checkLimit(peekLength);
    // Copy the value and construct the element to return.
    // TODO: Is there a more efficient way to do this?
    for (int i = 0; i < peekLength; i++)
    {
      builder.append(streamReader.readByte());
    }

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "READ ASN.1 OCTETSTRING(type=0x%x, length=%d)", peekType,
          peekLength));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return builder;
  }



  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    if (peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return "";
    }

    byte[] readBuffer;
    if (peekLength <= buffer.length)
    {
      readBuffer = buffer;
    }
    else
    {
      readBuffer = new byte[peekLength];
    }

    readLimiter.checkLimit(peekLength);
    streamReader.readByteArray(readBuffer, 0, peekLength);

    state = ELEMENT_READ_STATE_NEED_TYPE;

    String str;
    try
    {
      str = new String(readBuffer, 0, peekLength, "UTF-8");
    }
    catch (Exception e)
    {
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
      {
        StaticUtils.DEBUG_LOG
            .warning("Unable to decode ASN.1 OCTETSTRING bytes as UTF-8 string: "
                + e.toString());
      }

      str = new String(buffer, 0, peekLength);
    }

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "READ ASN.1 OCTETSTRING(type=0x%x, length=%d, value=%s)",
          peekType, peekLength, str));
    }

    return str;
  }



  /**
   * {@inheritDoc}
   */
  public void readStartSequence() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    readLimiter = readLimiter.startSequence(peekLength);

    if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
    {
      StaticUtils.DEBUG_LOG.finest(String.format(
          "READ ASN.1 START SEQUENCE(type=0x%x, length=%d)", peekType,
          peekLength));
    }

    // Reset the state
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }



  /**
   * {@inheritDoc}
   */
  public void readStartSet() throws IOException
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    readStartSequence();
  }



  public void release()
  {
    streamReader = null;
    peekLength = -1;
    peekType = 0;
    readLimiter = rootLimiter;
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }



  public void setStreamReader(StreamReader streamReader)
  {
    this.streamReader = streamReader;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Reader skipElement() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    readLimiter.checkLimit(peekLength);
    for (int i = 0; i < peekLength; i++)
    {
      streamReader.readByte();
    }
    state = ELEMENT_READ_STATE_NEED_TYPE;
    return this;
  }



  /**
   * Internal helper method reading the additional ASN.1 length bytes
   * and transition to the next state if successful.
   *
   * @param ensureRead
   *          <code>true</code> to check for availability first.
   * @return <code>true</code> if the length bytes was successfully
   *         read.
   * @throws IOException
   *           If an error occurs while reading from the stream.
   */
  private boolean needAdditionalLengthBytesState(boolean ensureRead)
      throws IOException
  {
    if (ensureRead && (readLimiter.remaining() < lengthBytesNeeded))
    {
      return false;
    }

    byte readByte;
    readLimiter.checkLimit(lengthBytesNeeded);
    while (lengthBytesNeeded > 0)
    {
      readByte = streamReader.readByte();
      peekLength = (peekLength << 8) | (readByte & 0xFF);
      lengthBytesNeeded--;
    }

    // Make sure that the element is not larger than the maximum allowed
    // message size.
    if ((maxElementSize > 0) && (peekLength > maxElementSize))
    {
      LocalizableMessage m = ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
          peekLength, maxElementSize);
      throw DecodeException.fatalError(m);
    }
    state = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
    return true;
  }



  /**
   * Internal helper method reading the first length bytes and
   * transition to the next state if successful.
   *
   * @param ensureRead
   *          <code>true</code> to check for availability first.
   * @return <code>true</code> if the length bytes was successfully read
   * @throws IOException
   *           If an error occurs while trying to decode an ASN1
   *           element.
   */
  private boolean needFirstLengthByteState(boolean ensureRead)
      throws IOException
  {
    if (ensureRead && (readLimiter.remaining() <= 0))
    {
      return false;
    }

    readLimiter.checkLimit(1);
    byte readByte = streamReader.readByte();
    peekLength = (readByte & 0x7F);
    if (peekLength != readByte)
    {
      lengthBytesNeeded = peekLength;
      if (lengthBytesNeeded > 4)
      {
        LocalizableMessage message = ERR_ASN1_INVALID_NUM_LENGTH_BYTES
            .get(lengthBytesNeeded);
        throw DecodeException.fatalError(message);
      }
      peekLength = 0x00;

      if (ensureRead && (readLimiter.remaining() < lengthBytesNeeded))
      {
        state = ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
        return false;
      }

      readLimiter.checkLimit(lengthBytesNeeded);
      while (lengthBytesNeeded > 0)
      {
        readByte = streamReader.readByte();
        peekLength = (peekLength << 8) | (readByte & 0xFF);
        lengthBytesNeeded--;
      }
    }

    // Make sure that the element is not larger than the maximum allowed
    // message size.
    if ((maxElementSize > 0) && (peekLength > maxElementSize))
    {
      LocalizableMessage m = ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
          peekLength, maxElementSize);
      throw DecodeException.fatalError(m);
    }
    state = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
    return true;
  }



  /**
   * Internal helper method reading the ASN.1 type byte and transition
   * to the next state if successful.
   *
   * @param ensureRead
   *          <code>true</code> to check for availability first.
   * @return <code>true</code> if the type byte was successfully read
   * @throws IOException
   *           If an error occurs while trying to decode an ASN1
   *           element.
   */
  private boolean needTypeState(boolean ensureRead) throws IOException
  {
    // Read just the type.
    if (ensureRead && (readLimiter.remaining() <= 0))
    {
      return false;
    }

    readLimiter.checkLimit(1);
    peekType = streamReader.readByte();
    state = ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
    return true;
  }
}
