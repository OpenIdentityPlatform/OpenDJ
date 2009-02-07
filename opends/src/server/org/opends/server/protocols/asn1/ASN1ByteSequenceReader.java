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

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;

import java.util.LinkedList;
import java.io.IOException;

import org.opends.messages.Message;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;

/**
 * An ASN.1 reader that reads from a {@link ByteSequenceReader}.
 */
final class ASN1ByteSequenceReader implements ASN1Reader
{
  private int state = ELEMENT_READ_STATE_NEED_TYPE;
  private byte peekType = 0;
  private int peekLength = -1;
  private final int maxElementSize;

  private ByteSequenceReader reader;
  private final LinkedList<ByteSequenceReader> readerStack;

  /**
   * Creates a new ASN1 reader whose source is the provided byte
   * sequence reader and having a user defined maximum BER element
   * size.
   *
   * @param reader
   *          The byte sequence reader to be read.
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   */
  ASN1ByteSequenceReader(ByteSequenceReader reader, int maxElementSize)
  {
    this.reader = reader;
    this.readerStack = new LinkedList<ByteSequenceReader>();
    this.maxElementSize = maxElementSize;
  }

  /**
   * {@inheritDoc}
   */
  public byte peekType() throws ASN1Exception
  {
    if(state == ELEMENT_READ_STATE_NEED_TYPE)
    {
      // Read just the type.
      if(reader.remaining() <= 0)
      {
        Message message =
            ERR_ASN1_TRUCATED_TYPE_BYTE.get();
        throw new ASN1Exception(message);
      }
      int type = reader.get();

      peekType = (byte)type;
      state = ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
    }

    return peekType;
  }

  /**
   * {@inheritDoc}
   */
  public int peekLength() throws ASN1Exception
  {
    peekType();

    if(state == ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE)
    {
      needFirstLengthByteState(true);
    }

    return peekLength;
  }

  /**
   * Determines if a complete ASN.1 element is waiting to be read from the
   * byte sequence.
   *
   * @return <code>true</code> if another complete element is available or
   *         <code>false</code> otherwise.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  public boolean elementAvailable() throws ASN1Exception
  {
    if(state == ELEMENT_READ_STATE_NEED_TYPE &&
        !needTypeState(false)) {
      return false;
    }
    if(state == ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE &&
        !needFirstLengthByteState(false)) {
      return false;
    }

    return peekLength <= reader.remaining();
  }

  /**
   * Determines if the byte sequence contains at least one ASN.1 element to
   * be read.
   *
   * @return <code>true</code> if another element is available or
   *         <code>false</code> otherwise.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  public boolean hasNextElement() throws ASN1Exception
  {
    return state != ELEMENT_READ_STATE_NEED_TYPE || needTypeState(false);
  }

  /**
   * Internal helper method reading the ASN.1 type byte and transition to
   * the next state if successful.
   *
   * @param throwEofException <code>true</code> to throw an exception when
   *                          the end of the sequence is encountered.
   * @return <code>true</code> if the type byte was successfully read
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  private boolean needTypeState(boolean throwEofException)
      throws ASN1Exception
  {
    // Read just the type.
    if(reader.remaining() <= 0)
    {
      if(throwEofException)
      {
        Message message =
            ERR_ASN1_TRUCATED_TYPE_BYTE.get();
        throw new ASN1Exception(message);
      }
      return false;
    }
    int type = reader.get();

    peekType = (byte)type;
    state = ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
    return true;
  }

  /**
   * Internal helper method reading the first length bytes and transition to
   * the next state if successful.
   *
   * @param throwEofException <code>true</code> to throw an exception when
   *                          the end of the sequence is encountered.
   * @return <code>true</code> if the length bytes was successfully read
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  private boolean needFirstLengthByteState(boolean throwEofException)
      throws ASN1Exception
  {
    if(reader.remaining() <= 0)
    {
      if(throwEofException)
      {
        Message message =
            ERR_ASN1_TRUNCATED_LENGTH_BYTE.get();
        throw new ASN1Exception(message);
      }
      return false;
    }
    int readByte = reader.get();
    peekLength = (readByte & 0x7F);
    if (peekLength != readByte)
    {
      int lengthBytesNeeded = peekLength;
      if (lengthBytesNeeded > 4)
      {
        Message message =
            ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(lengthBytesNeeded);
        throw new ASN1Exception(message);
      }

      peekLength = 0x00;
      if(reader.remaining() < lengthBytesNeeded)
      {
        if(throwEofException)
        {
          Message message =
              ERR_ASN1_TRUNCATED_LENGTH_BYTES.get(lengthBytesNeeded);
          throw new ASN1Exception(message);
        }
        return false;
      }

      while(lengthBytesNeeded > 0)
      {
        readByte = reader.get();
        peekLength = (peekLength << 8) | (readByte & 0xFF);
        lengthBytesNeeded--;
      }
    }

    // Make sure that the element is not larger than the maximum allowed
    // message size.
    if ((maxElementSize > 0) && (peekLength > maxElementSize))
    {
      Message m = ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
          peekLength, maxElementSize);
      throw new ASN1Exception(m);
    }
    state = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean readBoolean() throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    if (peekLength != 1)
    {
      Message message =
          ERR_ASN1_BOOLEAN_INVALID_LENGTH.get(peekLength);
      throw new ASN1Exception(message);
    }

    if(reader.remaining() < peekLength)
    {
      Message message =
          ERR_ASN1_BOOLEAN_TRUNCATED_VALUE.get(peekLength);
      throw new ASN1Exception(message);
    }
    int readByte = reader.get();

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return readByte != 0x00;
  }

  /**
   * {@inheritDoc}
   */
  public int readEnumerated() throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    if ((peekLength < 1) || (peekLength > 4))
    {
      Message message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
      throw new ASN1Exception(message);
    }

    // From an implementation point of view, an enumerated value is
    // equivalent to an integer.
    return (int) readInteger();
  }

  /**
   * {@inheritDoc}
   */
  public long readInteger() throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    if ((peekLength < 1) || (peekLength > 8))
    {
      Message message =
          ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
      throw new ASN1Exception(message);
    }

    if(reader.remaining() < peekLength)
    {
      Message message =
          ERR_ASN1_INTEGER_TRUNCATED_VALUE.get(peekLength);
      throw new ASN1Exception(message);
    }
    if(peekLength > 4)
    {
      long longValue = 0;
      for (int i=0; i < peekLength; i++)
      {
        int readByte = reader.get();
        if (i == 0 && readByte < 0)
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
      for (int i=0; i < peekLength; i++)
      {
        int readByte = reader.get();
        if (i == 0 && readByte < 0)
        {
          intValue = 0xFFFFFFFF;
        }
        intValue = (intValue << 8) | (readByte & 0xFF);
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
      return intValue;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void readNull() throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    // Make sure that the decoded length is exactly zero byte.
    if (peekLength != 0)
    {
      Message message =
          ERR_ASN1_NULL_INVALID_LENGTH.get(peekLength);
      throw new ASN1Exception(message);
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public ByteString readOctetString() throws ASN1Exception {
    // Read the header if haven't done so already
    peekLength();

    if(reader.remaining() < peekLength)
    {
      Message message =
          ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
      throw new ASN1Exception(message);
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return reader.getByteString(peekLength);
  }

  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString() throws ASN1Exception
  {
    // We could cache the UTF-8 CharSet if performance proves to be an
    // issue.
    return readOctetStringAsString("UTF-8");
  }

  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString(String charSet) throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    if(reader.remaining() < peekLength)
    {
      Message message =
          ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
      throw new ASN1Exception(message);
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return reader.getString(peekLength);
  }

  /**
   * {@inheritDoc}
   */
  public void readOctetString(ByteStringBuilder buffer) throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    // Copy the value.
    if(reader.remaining() < peekLength)
    {
      Message message =
          ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
      throw new ASN1Exception(message);
    }
    buffer.append(reader, peekLength);

    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSequence() throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    if(reader.remaining() < peekLength)
    {
      Message message =
          ERR_ASN1_SEQUENCE_SET_TRUNCATED_VALUE.get(peekLength);
      throw new ASN1Exception(message);
    }

    ByteSequenceReader subByteString = reader.getByteSequence(peekLength)
        .asReader();
    readerStack.addFirst(reader);
    reader = subByteString;

    // Reset the state
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSet() throws ASN1Exception
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    readStartSequence();
  }

  /**
   * {@inheritDoc}
   */
  public void readEndSequence() throws ASN1Exception
  {
    if(readerStack.isEmpty())
    {
      Message message = ERR_ASN1_SEQUENCE_READ_NOT_STARTED.get();
      throw new ASN1Exception(message);
    }

    if(reader.remaining() > 0)
    {
      Message message =
          ERR_ASN1_SEQUENCE_READ_NOT_ENDED.get(reader.remaining(), peekLength);
      throw new ASN1Exception(message);
    }

    reader = readerStack.removeFirst();

    // Reset the state
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public void readEndSet() throws ASN1Exception
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    readEndSequence();
  }

  /**
   * {@inheritDoc}
   */
  public void skipElement() throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    if(reader.remaining() < peekLength)
    {
      Message message =
          ERR_ASN1_SKIP_TRUNCATED_VALUE.get(peekLength);
      throw new ASN1Exception(message);
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    reader.skip(peekLength);
  }

  /**
   * {@inheritDoc}
   */
  public void close() throws IOException
  {
    readerStack.clear();
  }
}
