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
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import org.opends.messages.Message;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.SizeLimitInputStream;

/**
 * An ASN1Reader that reads from an input stream.
 */
final class ASN1InputStreamReader implements ASN1Reader
{

  private static final DebugTracer TRACER = getTracer();

  private int state = ELEMENT_READ_STATE_NEED_TYPE;
  private byte peekType = 0;
  private int peekLength = -1;
  private int lengthBytesNeeded = 0;
  private final int maxElementSize;

  private InputStream in;
  private final LinkedList<InputStream> streamStack;
  private byte[] buffer;

  /**
   * Creates a new ASN1 reader whose source is the provided input
   * stream and having a user defined maximum BER element size.
   *
   * @param stream
   *          The input stream to be read.
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   */
  ASN1InputStreamReader(InputStream stream, int maxElementSize)
  {
    this.in = stream;
    this.streamStack = new LinkedList<InputStream>();
    this.buffer = new byte[512];
    this.maxElementSize = maxElementSize;
  }

  /**
   * Determines if a complete ASN.1 element is ready to be read from the
   * input stream without blocking.
   *
   * @return <code>true</code> if another complete element is available or
   *         <code>false</code> otherwise.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  public boolean elementAvailable() throws ASN1Exception
  {
    try
    {
      if(state == ELEMENT_READ_STATE_NEED_TYPE &&
          !needTypeState(false, false)) {
        return false;
      }
      if(state == ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE &&
          !needFirstLengthByteState(false, false)) {
        return false;
      }
      if(state == ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES &&
          !needAdditionalLengthBytesState(false, false)) {
        return false;
      }

      return peekLength <= in.available();
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
  }

  /**
   * Determines if the input stream contains at least one ASN.1 element to
   * be read. This method will block until enough data is available on the
   * stream to determine if an element is available.
   *
   * @return <code>true</code> if another element is available or
   *         <code>false</code> otherwise.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  public boolean hasNextElement() throws ASN1Exception
  {
    try
    {
      if(!streamStack.isEmpty())
      {
        // We are reading a sub sequence. Return true as long as we haven't
        // exausted the size limit for the sub sequence sub input stream.
        SizeLimitInputStream subSq = (SizeLimitInputStream)in;
        return (subSq.getSizeLimit() - subSq.getBytesRead() > 0);
      }

      return state != ELEMENT_READ_STATE_NEED_TYPE ||
          needTypeState(true, false);
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
  }

  /**
   * Internal helper method reading the ASN.1 type byte and transition to
   * the next state if successful.
   *
   * @param isBlocking <code>true</code> to block if the type byte is not
   *                   available or <code>false</code> to check for
   *                   availability first.
   * @param throwEofException <code>true</code> to throw an exception when
   *                          an EOF is encountered or <code>false</code> to
   *                          return false.
   * @return <code>true</code> if the type byte was successfully read
   * @throws IOException If an error occurs while reading from the stream.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  private boolean needTypeState(boolean isBlocking, boolean throwEofException)
      throws IOException, ASN1Exception
  {
    // Read just the type.
    if(!isBlocking && in.available() <= 0)
    {
      return false;
    }

    int type = in.read();
    if(type == -1)
    {
      if(throwEofException)
      {
        Message message =
            ERR_ASN1_TRUCATED_TYPE_BYTE.get();
        throw new ASN1Exception(message);
      }
      return false;
    }

    peekType = (byte)type;
    state = ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
    return true;
  }

  /**
   * Internal helper method reading the first length bytes and transition to
   * the next state if successful.
   *
   * @param isBlocking <code>true</code> to block if the type byte is not
   *                   available or <code>false</code> to check for
   *                   availability first.
   * @param throwEofException <code>true</code> to throw an exception when
   *                          an EOF is encountered or <code>false</code> to
   *                          return false.
   * @return <code>true</code> if the length bytes was successfully read
   * @throws IOException If an error occurs while reading from the stream.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  private boolean needFirstLengthByteState(boolean isBlocking,
                                           boolean throwEofException)
      throws IOException, ASN1Exception
  {
    if(!isBlocking && in.available() <= 0)
    {
      return false;
    }

    int readByte = in.read();
    if(readByte == -1)
    {
      if(throwEofException)
      {
        Message message =
            ERR_ASN1_TRUNCATED_LENGTH_BYTE.get();
        throw new ASN1Exception(message);
      }
      return false;
    }
    peekLength = (readByte & 0x7F);
    if (peekLength != readByte)
    {
      lengthBytesNeeded = peekLength;
      if (lengthBytesNeeded > 4)
      {
        Message message =
            ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(lengthBytesNeeded);
        throw new ASN1Exception(message);
      }
      peekLength = 0x00;

      if(!isBlocking && in.available() < lengthBytesNeeded)
      {
        state = ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
        return false;
      }

      while(lengthBytesNeeded > 0)
      {
        readByte = in.read();
        if(readByte == -1)
        {
          state = ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
          if(throwEofException)
          {
            Message message =
                ERR_ASN1_TRUNCATED_LENGTH_BYTES.get(lengthBytesNeeded);
            throw new ASN1Exception(message);
          }
          return false;
        }
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
   * Internal helper method reading the additional ASN.1 length bytes and
   * transition to the next state if successful.
   *
   * @param isBlocking <code>true</code> to block if the type byte is not
   *                   available or <code>false</code> to check for
   *                   availability first.
   * @param throwEofException <code>true</code> to throw an exception when
   *                          an EOF is encountered or <code>false</code> to
   *                          return false.
   * @return <code>true</code> if the length bytes was successfully read.
   * @throws IOException If an error occurs while reading from the stream.
   * @throws ASN1Exception If an error occurs while trying to decode
   *                       an ASN1 element.
   */
  private boolean needAdditionalLengthBytesState(boolean isBlocking,
                                                 boolean throwEofException)
      throws IOException, ASN1Exception
  {
    if(!isBlocking && in.available() < lengthBytesNeeded)
    {
      return false;
    }

    int readByte;
    while(lengthBytesNeeded > 0)
    {
      readByte = in.read();
      if(readByte == -1)
      {
        state = ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
        if(throwEofException)
        {
          Message message =
              ERR_ASN1_TRUNCATED_LENGTH_BYTES.get(lengthBytesNeeded);
          throw new ASN1Exception(message);
        }
        return false;
      }
      peekLength = (peekLength << 8) | (readByte & 0xFF);
      lengthBytesNeeded--;
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
  public byte peekType() throws ASN1Exception
  {
    try
    {
      if(state == ELEMENT_READ_STATE_NEED_TYPE)
      {
        needTypeState(true, true);
      }

      return peekType;
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
  }

  /**
   * {@inheritDoc}
   */
  public int peekLength() throws ASN1Exception
  {
    peekType();

    try
    {
      switch(state)
      {
        case ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE:
          needFirstLengthByteState(true, true);
          break;

        case ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES:
          needAdditionalLengthBytesState(true, true);
      }

      return peekLength;
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
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

    try
    {
      int readByte = in.read();
      if(readByte == -1)
      {
        Message message = ERR_ASN1_BOOLEAN_TRUNCATED_VALUE.get(peekLength);
        throw new ASN1Exception(message);
      }

      if(debugEnabled())
      {
        TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
            String.format("READ ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)",
                peekType, peekLength, String.valueOf(readByte != 0x00)));
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
      return readByte != 0x00;
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
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

    try
    {
      if(peekLength > 4)
      {
        long longValue = 0;
        for (int i=0; i < peekLength; i++)
        {
          int readByte = in.read();
          if(readByte == -1)
          {
            Message message =
                ERR_ASN1_INTEGER_TRUNCATED_VALUE.get(peekLength);
            throw new ASN1Exception(message);
          }
          if(i == 0 && ((byte)readByte) < 0)
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
          int readByte = in.read();
          if(readByte == -1)
          {
            Message message =
                ERR_ASN1_INTEGER_TRUNCATED_VALUE.get(peekLength);
            throw new ASN1Exception(message);
          }
          if (i == 0 && ((byte)readByte) < 0)
          {
            intValue = 0xFFFFFFFF;
          }
          intValue = (intValue << 8) | (readByte & 0xFF);
        }

        if(debugEnabled())
        {
          TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
              String.format("READ ASN.1 INTEGER(type=0x%x, length=%d, " +
                  "value=%d)", peekType, peekLength, intValue));
        }

        state = ELEMENT_READ_STATE_NEED_TYPE;
        return intValue;
      }
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
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

    if(debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
          String.format("READ ASN.1 NULL(type=0x%x, length=%d)",
              peekType, peekLength));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public ByteString readOctetString() throws ASN1Exception {
    // Read the header if haven't done so already
    peekLength();

    if(peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return ByteString.empty();
    }

    try
    {
      // Copy the value and construct the element to return.
      byte[] value = new byte[peekLength];
      int bytesNeeded = peekLength;
      int bytesRead;
      while(bytesNeeded > 0)
      {
        bytesRead = in.read(value, peekLength - bytesNeeded, bytesNeeded);
        if(bytesRead < 0)
        {
          Message message =
            ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
          throw new ASN1Exception(message);
        }

        bytesNeeded -= bytesRead;
      }

      if(debugEnabled())
      {
        TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
            String.format("READ ASN.1 OCTETSTRING(type=0x%x, length=%d)",
                peekType, peekLength));
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
      return ByteString.wrap(value);
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
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

    if(peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return "";
    }

    // Resize the temp buffer if needed
    if(peekLength > buffer.length)
    {
      buffer = new byte[peekLength];
    }

    try
    {
      int bytesNeeded = peekLength;
      int bytesRead;
      while(bytesNeeded > 0)
      {
        bytesRead = in.read(buffer, peekLength - bytesNeeded, bytesNeeded);
        if(bytesRead < 0)
        {
          Message message =
            ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
          throw new ASN1Exception(message);
        }
        bytesNeeded -= bytesRead;
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }

    String str;
    try
    {
      str = new String(buffer, 0, peekLength, charSet);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      str = new String(buffer, 0, peekLength);
    }

    if(debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
          String.format("READ ASN.1 OCTETSTRING(type=0x%x, length=%d, " +
              "value=%s)", peekType, peekLength, str));
    }

    return str;
  }

  /**
   * {@inheritDoc}
   */
  public void readOctetString(ByteStringBuilder buffer) throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    if(peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return;
    }

    try
    {
      // Copy the value and construct the element to return.
      int bytesNeeded = peekLength;
      int bytesRead;
      while(bytesNeeded > 0)
      {
        bytesRead = buffer.append(in, bytesNeeded);
        if(bytesRead < 0)
        {
          Message message =
            ERR_ASN1_OCTET_STRING_TRUNCATED_VALUE.get(peekLength);
          throw new ASN1Exception(message);
        }
        bytesNeeded -= bytesRead;
      }

      if(debugEnabled())
      {
        TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
            String.format("READ ASN.1 OCTETSTRING(type=0x%x, length=%d)",
                peekType, peekLength));
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSequence() throws ASN1Exception
  {
    // Read the header if haven't done so already
    peekLength();

    SizeLimitInputStream subStream =
        new SizeLimitInputStream(in, peekLength);

    if(debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
          String.format("READ ASN.1 SEQUENCE(type=0x%x, length=%d)",
              peekType, peekLength));
    }

    streamStack.addFirst(in);
    in = subStream;

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
    if(streamStack.isEmpty())
    {
      Message message = ERR_ASN1_SEQUENCE_READ_NOT_STARTED.get();
      throw new ASN1Exception(message);
    }

    // Ignore all unused trailing components.
    SizeLimitInputStream subSq = (SizeLimitInputStream)in;
    if(subSq.getSizeLimit() - subSq.getBytesRead() > 0)
    {
      if(debugEnabled())
      {
        TRACER.debugWarning("Ignoring %d unused trailing bytes in " +
            "ASN.1 SEQUENCE", subSq.getSizeLimit() - subSq.getBytesRead());
      }

      try
      {
        subSq.skip(subSq.getSizeLimit() - subSq.getBytesRead());
      }
      catch(IOException ioe)
      {
        Message message =
            ERR_ASN1_READ_ERROR.get(ioe.toString());
        throw new ASN1Exception(message, ioe);
      }
    }

    in = streamStack.removeFirst();

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

    try
    {
      long bytesSkipped = in.skip(peekLength);
      if(bytesSkipped != peekLength)
      {
        Message message =
            ERR_ASN1_SKIP_TRUNCATED_VALUE.get(peekLength);
        throw new ASN1Exception(message);
      }
      state = ELEMENT_READ_STATE_NEED_TYPE;
    }
    catch(IOException ioe)
    {
      Message message =
          ERR_ASN1_READ_ERROR.get(ioe.toString());
      throw new ASN1Exception(message, ioe);
    }
  }

  /**
   * Closes this ASN.1 reader and the underlying stream.
   *
   * @throws IOException if an I/O error occurs
   */
  public void close() throws IOException
  {
    // Calling close of SizeLimitInputStream should close the parent stream.
    in.close();
    streamStack.clear();
  }
}
