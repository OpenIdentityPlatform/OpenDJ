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

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

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
  private final byte[] byteArray;

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
    this.byteArray = bytes;
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
   * @return the next UTF8-encoded string or null if the string length is zero
   * @throws DataFormatException
   *           if no more data can be read from the input
   */
  public String nextString() throws DataFormatException
  {
    try
    {
      final int offset = findZeroSeparator();
      if (offset > 0)
      {
        final String s = bytes.getString(offset);
        skipZeroSeparator();
        return s;
      }
      skipZeroSeparator();
      return null;
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
    // nextInt() would have been safer, but byte is compatible with legacy code.
    final int colSize = nextByte();
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
   * Reads the next DN.
   *
   * @return the next DN.
   * @throws DataFormatException
   *           if DN was incorrectly encoded or no more data can be read from
   *           the input
   */
  public DN nextDN() throws DataFormatException
  {
    try
    {
      return DN.valueOf(nextString());
    }
    catch (DirectoryException e)
    {
      throw new DataFormatException(e.getLocalizedMessage());
    }
  }

  /**
   * Return a new byte array containing all remaining bytes in this
   * ByteArrayScanner.
   *
   * @return new byte array containing all remaining bytes
   */
  public byte[] remainingBytes()
  {
    final int length = byteArray.length - bytes.position();
    return nextByteArray(length);
  }

  /**
   * Return a new byte array containing all remaining bytes in this
   * ByteArrayScanner bar the last one which is a zero terminated byte
   * (compatible with legacy code).
   *
   * @return new byte array containing all remaining bytes bar the last one
   */
  public byte[] remainingBytesZeroTerminated()
  {
    /* do not copy stupid legacy zero separator */
    final int length = byteArray.length - (bytes.position() + 1);
    final byte[] result = nextByteArray(length);
    bytes.skip(1); // ignore last (supposedly) zero byte
    return result;
  }

  /**
   * Return a new byte array containing the requested number of bytes.
   *
   * @param length
   *          the number of bytes to be read and copied to the new byte array.
   * @return new byte array containing the requested number of bytes.
   */
  public byte[] nextByteArray(final int length)
  {
    final byte[] result = new byte[length];
    System.arraycopy(byteArray, bytes.position(), result, 0, length);
    bytes.skip(length);
    return result;
  }

  /**
   * Reads the next ServerState.
   * <p>
   * Caution: ServerState MUST be the last field (see
   * {@link ByteArrayBuilder#appendServerStateMustComeLast(ServerState)} javadoc).
   * <p>
   * Note: the super long method name it is intentional:
   * nobody will want to use it, which is good because nobody should.
   *
   * @return the next ServerState.
   * @throws DataFormatException
   *           if ServerState was incorrectly encoded or no more data can be
   *           read from the input
   * @see ByteArrayBuilder#appendServerStateMustComeLast(ServerState)
   */
  public ServerState nextServerStateMustComeLast() throws DataFormatException
  {
    final ServerState result = new ServerState();

    final int maxPos = byteArray.length - 1 /* stupid legacy zero separator */;
    while (bytes.position() < maxPos)
    {
      final int serverId = nextIntUTF8();
      final CSN csn = nextCSNUTF8();
      if (serverId != csn.getServerId())
      {
        throw new DataFormatException("Expected serverId=" + serverId
            + " to be the same as serverId for CSN=" + csn);
      }
      result.update(csn);
    }
    skipZeroSeparator();
    return result;
  }

  /**
   * Skips the next byte and verifies it is effectively the zero separator.
   *
   * @throws DataFormatException
   *           if the next byte is not the zero separator.
   */
  public void skipZeroSeparator() throws DataFormatException
  {
    if (bytes.peek() != (byte) 0)
    {
      throw new DataFormatException("Expected a zero separator at position "
          + bytes.position() + " but found byte " + bytes.peek());
    }
    bytes.skip(1);
  }

  /**
   * Returns a new ASN1Reader that will read bytes from this ByteArrayScanner.
   *
   * @return a new ASN1Reader that will read bytes from this ByteArrayScanner.
   */
  public ASN1Reader getASN1Reader()
  {
    return ASN1.getReader(bytes);
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

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return bytes.toString();
  }
}
