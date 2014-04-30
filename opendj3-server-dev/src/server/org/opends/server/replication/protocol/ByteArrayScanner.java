/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.Collection;
import java.util.zip.DataFormatException;

import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.replication.common.CSN;

/**
 * Byte array scanner class helps decode data from byte arrays received via
 * messages over the replication protocol. Built on top of
 * {@link ByteSequenceReader}, it isolates the latter against legacy type
 * conversions from the replication protocol.
 *
 * @see ByteArrayBuilder ByteArrayBuilder class that encodes messages read with
 *      current class.
 */
public class ByteArrayScanner
{

  private final ByteSequenceReader bytes;

  /**
   * Builds a ByteArrayScanner object that will read from the supplied byte
   * array.
   *
   * @param bytes
   *          the byte array input that will be read from
   */
  public ByteArrayScanner(byte[] bytes)
  {
    this.bytes = ByteString.wrap(bytes).asReader();
  }

  /**
   * Reads the next boolean.
   *
   * @return the next boolean
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public boolean nextBoolean() throws DataFormatException
  {
    return nextByte() != 0;
  }

  /**
   * Reads the next byte.
   *
   * @return the next byte
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public byte nextByte() throws DataFormatException
  {
    try
    {
      return bytes.get();
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Reads the next short.
   *
   * @return the next short
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public short nextShort() throws DataFormatException
  {
    try
    {
      return bytes.getShort();
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Reads the next int.
   *
   * @return the next int
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public int nextInt() throws DataFormatException
  {
    try
    {
      return bytes.getInt();
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Reads the next long.
   *
   * @return the next long
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public long nextLong() throws DataFormatException
  {
    try
    {
      return bytes.getLong();
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Reads the next int that was encoded as a UTF8 string.
   *
   * @return the next int that was encoded as a UTF8 string.
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public int nextIntUTF8() throws DataFormatException
  {
    return Integer.valueOf(nextString());
  }

  /**
   * Reads the next long that was encoded as a UTF8 string.
   *
   * @return the next long that was encoded as a UTF8 string.
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public long nextLongUTF8() throws DataFormatException
  {
    return Long.valueOf(nextString());
  }

  /**
   * Reads the next UTF8-encoded string.
   *
   * @return the next UTF8-encoded string.
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public String nextString() throws DataFormatException
  {
    try
    {
      final String s = bytes.getString(findZeroSeparator());
      bytes.skip(1); // skip the zero separator
      return s;
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  private int findZeroSeparator() throws DataFormatException
  {
    int offset = 0;
    final int remaining = bytes.remaining();
    while (bytes.peek(offset) != 0 && offset < remaining)
    {
      offset++;
    }
    if (offset == remaining)
    {
      throw new DataFormatException("No more data to read from");
    }
    return offset;
  }

  /**
   * Reads the next UTF8-encoded strings in the provided collection.
   *
   * @param output
   *          the collection where to add the next UTF8-encoded strings
   * @param <TCol>
   *          the collection's concrete type
   * @return the provided collection where the next UTF8-encoded strings have
   *         been added.
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public <TCol extends Collection<String>> TCol nextStrings(TCol output)
      throws DataFormatException
  {
    final int colSize = nextInt();
    for (int i = 0; i < colSize; i++)
    {
      output.add(nextString());
    }
    return output;
  }

  /**
   * Reads the next CSN.
   *
   * @return the next CSN.
   * @throws DataFormatException
   *           if CSN was incorrectly encoded or no more data can be read from
   *           the input
   */
  public CSN nextCSN() throws DataFormatException
  {
    try
    {
      return CSN.valueOf(bytes.getByteSequence(CSN.BYTE_ENCODING_LENGTH));
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Reads the next CSN that was encoded as a UTF8 string.
   *
   * @return the next CSN that was encoded as a UTF8 string.
   * @throws DataFormatException
   *           if legacy CSN was incorrectly encoded or no more data can be read
   *           from the input
   */
  public CSN nextCSNUTF8() throws DataFormatException
  {
    try
    {
      return CSN.valueOf(nextString());
    }
    catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Returns whether the scanner has more bytes to consume.
   *
   * @return true if the scanner has more bytes to consume, false otherwise.
   */
  public boolean isEmpty()
  {
    return bytes.remaining() == 0;
  }

}
