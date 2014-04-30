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

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.replication.common.CSN;

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

  /** This is the null byte, also known as zero byte. */
  public static final byte NULL_BYTE = 0;
  private final ByteStringBuilder builder;

  /**
   * Constructs a ByteArrayBuilder.
   */
  public ByteArrayBuilder()
  {
    builder = new ByteStringBuilder();
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
  public ByteArrayBuilder append(boolean b)
  {
    append((byte) (b ? 1 : 0));
    return this;
  }

  /**
   * Append a byte to this ByteArrayBuilder.
   *
   * @param b
   *          the byte to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder append(byte b)
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
  public ByteArrayBuilder append(short s)
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
  public ByteArrayBuilder append(int i)
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
  public ByteArrayBuilder append(long l)
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
  public ByteArrayBuilder appendUTF8(int i)
  {
    return append(Integer.toString(i));
  }

  /**
   * Append a long to this ByteArrayBuilder by converting it to a String then
   * encoding that string to a UTF-8 byte array.
   *
   * @param l
   *          the long to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder appendUTF8(long l)
  {
    return append(Long.toString(l));
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
    append(col.size());
    for (String s : col)
    {
      append(s);
    }
    return this;
  }

  /**
   * Append a String to this ByteArrayBuilder.
   *
   * @param s
   *          the String to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder append(String s)
  {
    try
    {
      append(s.getBytes("UTF-8"));
    }
    catch (UnsupportedEncodingException e)
    {
      throw new RuntimeException("Should never happen", e);
    }
    return this;
  }

  /**
   * Append a CSN to this ByteArrayBuilder.
   *
   * @param csn
   *          the CSN to append.
   * @return this ByteArrayBuilder
   */
  public ByteArrayBuilder append(CSN csn)
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
  public ByteArrayBuilder appendUTF8(CSN csn)
  {
    append(csn.toString());
    return this;
  }

  private ByteArrayBuilder append(byte[] sBytes)
  {
    for (byte b : sBytes)
    {
      append(b);
    }
    append((byte) 0); // zero separator
    return this;
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
