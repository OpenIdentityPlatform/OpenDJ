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

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.types.DN;

/**
 * Byte array builder class encodes data into byte arrays to send messages over
 * the replication protocol. Built on top of {@link ByteStringBuilder}, it
 * isolates the latter against legacy type conversions from the replication
 * protocol. It exposes a fluent API.
 *
 * @see ByteArrayScanner ByteArrayScanner class that decodes messages built with
 *      current class.
 */
public class ByteArrayBuilder
{

  private final ByteStringBuilder builder;

  /**
   * Constructs a ByteArrayBuilder.
   */
  public ByteArrayBuilder()
  {
    builder = new ByteStringBuilder(256);
  }

  /**
   * Constructs a ByteArrayBuilder.
   *
   * @param capacity
   *          the capacity of the underlying ByteStringBuilder
   */
  public ByteArrayBuilder(int capacity)
  {
    builder = new ByteStringBuilder(capacity);
  }

  /**
   * Append a boolean to this ByteArrayBuilder.
   *
   * @param b
   *          the boolean to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendBoolean(boolean b)
  {
    appendByte((byte) (b ? 1 : 0));
    return this;
  }

  /**
   * Append a byte to this ByteArrayBuilder.
   *
   * @param b
   *          the byte to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendByte(byte b)
  {
    builder.append(b);
    return this;
  }

  /**
   * Append a short to this ByteArrayBuilder.
   *
   * @param s
   *          the short to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendShort(short s)
  {
    builder.append(s);
    return this;
  }

  /**
   * Append an int to this ByteArrayBuilder.
   *
   * @param i
   *          the long to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendInt(int i)
  {
    builder.append(i);
    return this;
  }

  /**
   * Append a long to this ByteArrayBuilder.
   *
   * @param l
   *          the long to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendLong(long l)
  {
    builder.append(l);
    return this;
  }

  /**
   * Append an int to this ByteArrayBuilder by converting it to a String then
   * encoding that string to a UTF-8 byte array.
   *
   * @param i
   *          the int to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendIntUTF8(int i)
  {
    return appendString(Integer.toString(i));
  }

  /**
   * Append a long to this ByteArrayBuilder by converting it to a String then
   * encoding that string to a UTF-8 byte array.
   *
   * @param l
   *          the long to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendLongUTF8(long l)
  {
    return appendString(Long.toString(l));
  }

  /**
   * Append a Collection of Strings to this ByteArrayBuilder.
   *
   * @param col
   *          the Collection of Strings to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendStrings(Collection<String> col)
  {
    //appendInt() would have been safer, but byte is compatible with legacy code
    appendByte((byte) col.size());
    for (String s : col)
    {
      appendString(s);
    }
    return this;
  }

  /**
   * Append a String with a zero separator to this ByteArrayBuilder,
   * or only the zero separator if the string is null
   * or if the string length is zero.
   *
   * @param s
   *          the String to append. Can be null.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendString(String s)
  {
    try
    {
      if (s != null && s.length() > 0)
      {
        appendByteArray(s.getBytes("UTF-8"));
      }
      return appendZeroSeparator();
    }
    catch (UnsupportedEncodingException e)
    {
      throw new RuntimeException("Should never happen", e);
    }
  }

  /**
   * Append a CSN to this ByteArrayBuilder.
   *
   * @param csn
   *          the CSN to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendCSN(CSN csn)
  {
    csn.toByteString(builder);
    return this;
  }

  /**
   * Append a CSN to this ByteArrayBuilder by converting it to a String then
   * encoding that string to a UTF-8 byte array.
   *
   * @param csn
   *          the CSN to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendCSNUTF8(CSN csn)
  {
    appendString(csn.toString());
    return this;
  }

  /**
   * Append a DN to this ByteArrayBuilder by converting it to a String then
   * encoding that string to a UTF-8 byte array.
   *
   * @param dn
   *          the DN to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendDN(DN dn)
  {
    appendString(dn.toString());
    return this;
  }

  /**
   * Append all the bytes from the byte array to this ByteArrayBuilder.
   *
   * @param bytes
   *          the byte array to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendByteArray(byte[] bytes)
  {
    builder.append(bytes);
    return this;
  }

  /**
   * Append all the bytes from the byte array to this ByteArrayBuilder
   * and then append a final zero byte separator for compatibility
   * with legacy implementations.
   * <p>
   * Note: the super long method name it is intentional:
   * nobody will want to use it, which is good because nobody should.
   *
   * @param bytes
   *          the byte array to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendZeroTerminatedByteArray(byte[] bytes)
  {
    builder.append(bytes);
    return appendZeroSeparator();
  }

  private ByteArrayBuilder appendZeroSeparator()
  {
    builder.append((byte) 0);
    return this;
  }

  /**
   * Append the byte representation of a ServerState to this ByteArrayBuilder
   * and then append a final zero byte separator.
   * <p>
   * Caution: ServerState MUST be the last field. Because ServerState can
   * contain null character (string termination of serverId string ..) it cannot
   * be decoded using {@link ByteArrayScanner#nextString()} like the other
   * fields. The only way is to rely on the end of the input buffer: and that
   * forces the ServerState to be the last field. This should be changed if we
   * want to have more than one ServerState field.
   * <p>
   * Note: the super long method name it is intentional:
   * nobody will want to use it, which is good because nobody should.
   *
   * @param serverState
   *          the ServerState to append.
   * @return this ByteArrayBuilder
   * @see ByteArrayScanner#nextServerStateMustComeLast()
   */
  public ByteArrayBuilder appendServerStateMustComeLast(ServerState serverState)
  {
    final Map<Integer, CSN> serverIdToCSN = serverState.getServerIdToCSNMap();
    for (Entry<Integer, CSN> entry : serverIdToCSN.entrySet())
    {
      // FIXME JNR: why append the serverId in addition to the CSN
      // since the CSN already contains the serverId?
      appendIntUTF8(entry.getKey()); // serverId
      appendCSNUTF8(entry.getValue()); // CSN
    }
    return appendZeroSeparator(); // stupid legacy zero separator
  }

  /**
   * Returns a new ASN1Writer that will append bytes to this ByteArrayBuilder.
   *
   * @return a new ASN1Writer that will append bytes to this ByteArrayBuilder.
   */
  public ASN1Writer getASN1Writer()
  {
    return ASN1.getWriter(builder);
  }

  /**
   * Converts the content of this ByteStringBuilder to a byte array.
   *
   * @return the content of this ByteStringBuilder converted to a byte array.
   */
  public byte[] toByteArray()
  {
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return builder.toString();
  }

  /**
   * Helper method that returns the number of bytes that would be used by the
   * boolean fields when appended to a ByteArrayBuilder.
   *
   * @param nbFields
   *          the number of boolean fields that will be appended to a
   *          ByteArrayBuilder
   * @return the number of bytes occupied by the appended boolean fields.
   */
  public static int booleans(int nbFields)
  {
    return nbFields;
  }

  /**
   * Helper method that returns the number of bytes that would be used by the
   * byte fields when appended to a ByteArrayBuilder.
   *
   * @param nbFields
   *          the number of byte fields that will be appended to a
   *          ByteArrayBuilder
   * @return the number of bytes occupied by the appended byte fields.
   */
  public static int bytes(int nbFields)
  {
    return nbFields;
  }

  /**
   * Helper method that returns the number of bytes that would be used by the
   * short fields when appended to a ByteArrayBuilder.
   *
   * @param nbFields
   *          the number of short fields that will be appended to a
   *          ByteArrayBuilder
   * @return the number of bytes occupied by the appended short fields.
   */
  public static int shorts(int nbFields)
  {
    return 2 * nbFields;
  }

  /**
   * Helper method that returns the number of bytes that would be used by the
   * int fields when appended to a ByteArrayBuilder.
   *
   * @param nbFields
   *          the number of int fields that will be appended to a
   *          ByteArrayBuilder
   * @return the number of bytes occupied by the appended int fields.
   */
  public static int ints(int nbFields)
  {
    return 4 * nbFields;
  }

  /**
   * Helper method that returns the number of bytes that would be used by the
   * long fields when appended to a ByteArrayBuilder.
   *
   * @param nbFields
   *          the number of long fields that will be appended to a
   *          ByteArrayBuilder
   * @return the number of bytes occupied by the appended long fields.
   */
  public static int longs(int nbFields)
  {
    return 8 * nbFields;
  }

  /**
   * Helper method that returns the number of bytes that would be used by the
   * CSN fields when appended to a ByteArrayBuilder.
   *
   * @param nbFields
   *          the number of CSN fields that will be appended to a
   *          ByteArrayBuilder
   * @return the number of bytes occupied by the appended CSN fields.
   */
  public static int csns(int nbFields)
  {
    return CSN.BYTE_ENCODING_LENGTH * nbFields;
  }

  /**
   * Helper method that returns the number of bytes that would be used by the
   * CSN fields encoded as a UTF8 string when appended to a ByteArrayBuilder.
   *
   * @param nbFields
   *          the number of CSN fields that will be appended to a
   *          ByteArrayBuilder
   * @return the number of bytes occupied by the appended legacy-encoded CSN
   *         fields.
   */
  public static int csnsUTF8(int nbFields)
  {
    return CSN.STRING_ENCODING_LENGTH * nbFields + 1 /* null byte */;
  }
}
