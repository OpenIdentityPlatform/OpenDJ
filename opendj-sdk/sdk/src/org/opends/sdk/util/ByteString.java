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
package org.opends.sdk.util;



import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;



/**
 * An immutable sequence of bytes backed by a byte array.
 */
public final class ByteString implements ByteSequence
{

  // Singleton empty byte string.
  private static final ByteString EMPTY = wrap(new byte[0]);



  /**
   * Returns an empty byte string.
   * 
   * @return An empty byte string.
   */
  public static ByteString empty()
  {
    return EMPTY;
  }



  /**
   * Returns a byte string containing the big-endian encoded bytes of
   * the provided integer.
   * 
   * @param i
   *          The integer to encode.
   * @return The byte string containing the big-endian encoded bytes of
   *         the provided integer.
   */
  public static ByteString valueOf(int i)
  {
    final byte[] bytes = new byte[4];
    for (int j = 3; j >= 0; j--)
    {
      bytes[j] = (byte) (i & 0xFF);
      i >>>= 8;
    }
    return wrap(bytes);
  }



  /**
   * Returns a byte string containing the big-endian encoded bytes of
   * the provided long.
   * 
   * @param l
   *          The long to encode.
   * @return The byte string containing the big-endian encoded bytes of
   *         the provided long.
   */
  public static ByteString valueOf(long l)
  {
    final byte[] bytes = new byte[8];
    for (int i = 7; i >= 0; i--)
    {
      bytes[i] = (byte) (l & 0xFF);
      l >>>= 8;
    }
    return wrap(bytes);
  }



  /**
   * Returns a byte string containing the provided object. If the object
   * is an instance of {@code ByteSequence} then it is converted to a
   * byte string using the {@code toByteString()} method. Otherwise a
   * new byte string is created containing the UTF-8 encoded bytes of
   * the string representation of the provided object.
   * 
   * @param o
   *          The object to use.
   * @return The byte string containing the provided object.
   */
  public static ByteString valueOf(Object o)
  {
    if (o instanceof ByteSequence)
    {
      return ((ByteSequence) o).toByteString();
    }
    else
    {
      return wrap(StaticUtils.getBytes(o.toString()));
    }
  }



  /**
   * Returns a byte string containing the UTF-8 encoded bytes of the
   * provided string.
   * 
   * @param s
   *          The string to use.
   * @return The byte string with the encoded bytes of the provided
   *         string.
   */
  public static ByteString valueOf(String s)
  {
    return wrap(StaticUtils.getBytes(s));
  }



  /**
   * Returns a byte string that wraps the provided byte array.
   * <p>
   * <b>NOTE:</b> this method takes ownership of the provided byte array
   * and, therefore, the byte array MUST NOT be altered directly after
   * this method returns.
   * 
   * @param b
   *          The byte array to wrap.
   * @return The byte string that wraps the given byte array.
   */
  public static ByteString wrap(byte[] b)
  {
    return new ByteString(b, 0, b.length);
  }



  /**
   * Returns a byte string that wraps a subsequence of the provided byte
   * array.
   * <p>
   * <b>NOTE:</b> this method takes ownership of the provided byte array
   * and, therefore, the byte array MUST NOT be altered directly after
   * this method returns.
   * 
   * @param b
   *          The byte array to wrap.
   * @param offset
   *          The offset of the byte array to be used; must be
   *          non-negative and no larger than {@code b.length} .
   * @param length
   *          The length of the byte array to be used; must be
   *          non-negative and no larger than {@code b.length - offset}.
   * @return The byte string that wraps the given byte array.
   * @throws IndexOutOfBoundsException
   *           If {@code offset} is negative or if {@code length} is
   *           negative or if {@code offset + length} is greater than
   *           {@code b.length}.
   */
  public static ByteString wrap(byte[] b, int offset, int length)
      throws IndexOutOfBoundsException
  {
    checkArrayBounds(b, offset, length);
    return new ByteString(b, offset, length);
  }



  /**
   * Checks the array bounds of the provided byte array sub-sequence,
   * throwing an {@code IndexOutOfBoundsException} if they are illegal.
   * 
   * @param b
   *          The byte array.
   * @param offset
   *          The offset of the byte array to be checked; must be
   *          non-negative and no larger than {@code b.length}.
   * @param length
   *          The length of the byte array to be checked; must be
   *          non-negative and no larger than {@code b.length - offset}.
   * @throws IndexOutOfBoundsException
   *           If {@code offset} is negative or if {@code length} is
   *           negative or if {@code offset + length} is greater than
   *           {@code b.length}.
   */
  static void checkArrayBounds(byte[] b, int offset, int length)
      throws IndexOutOfBoundsException
  {
    if (offset < 0 || offset > b.length || length < 0
        || offset + length > b.length || offset + length < 0)
    {
      throw new IndexOutOfBoundsException();
    }
  }



  /**
   * Compares two byte array sub-sequences and returns a value that
   * indicates their relative order.
   * 
   * @param b1
   *          The byte array containing the first sub-sequence.
   * @param offset1
   *          The offset of the first byte array sub-sequence.
   * @param length1
   *          The length of the first byte array sub-sequence.
   * @param b2
   *          The byte array containing the second sub-sequence.
   * @param offset2
   *          The offset of the second byte array sub-sequence.
   * @param length2
   *          The length of the second byte array sub-sequence.
   * @return A negative integer if first byte array sub-sequence should
   *         come before the second byte array sub-sequence in ascending
   *         order, a positive integer if the first byte array
   *         sub-sequence should come after the byte array sub-sequence
   *         in ascending order, or zero if there is no difference
   *         between the two byte array sub-sequences with regard to
   *         ordering.
   */
  static int compareTo(byte[] b1, int offset1, int length1, byte[] b2,
      int offset2, int length2)
  {
    int count = Math.min(length1, length2);
    int i = offset1;
    int j = offset2;
    while (count-- != 0)
    {
      final int firstByte = 0xFF & b1[i++];
      final int secondByte = 0xFF & b2[j++];
      if (firstByte != secondByte)
      {
        return firstByte - secondByte;
      }
    }
    return length1 - length2;
  }



  /**
   * Indicates whether two byte array sub-sequences are equal. In order
   * for them to be considered equal, they must contain the same bytes
   * in the same order.
   * 
   * @param b1
   *          The byte array containing the first sub-sequence.
   * @param offset1
   *          The offset of the first byte array sub-sequence.
   * @param length1
   *          The length of the first byte array sub-sequence.
   * @param b2
   *          The byte array containing the second sub-sequence.
   * @param offset2
   *          The offset of the second byte array sub-sequence.
   * @param length2
   *          The length of the second byte array sub-sequence.
   * @return {@code true} if the two byte array sub-sequences have the
   *         same content, or {@code false} if not.
   */
  static boolean equals(byte[] b1, int offset1, int length1, byte[] b2,
      int offset2, int length2)
  {
    if (length1 != length2)
    {
      return false;
    }

    int i = offset1;
    int j = offset2;
    int count = length1;
    while (count-- != 0)
    {
      if (b1[i++] != b2[j++])
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Returns a hash code for the provided byte array sub-sequence.
   * 
   * @param b
   *          The byte array.
   * @param offset
   *          The offset of the byte array sub-sequence.
   * @param length
   *          The length of the byte array sub-sequence.
   * @return A hash code for the provided byte array sub-sequence.
   */
  static int hashCode(byte[] b, int offset, int length)
  {
    int hashCode = 1;
    int i = offset;
    int count = length;
    while (count-- != 0)
    {
      hashCode = 31 * hashCode + b[i++];
    }
    return hashCode;
  }



  /**
   * Returns the UTF-8 decoded string representation of the provided
   * byte array sub-sequence. If UTF-8 decoding fails, the platform's
   * default encoding will be used.
   * 
   * @param b
   *          The byte array.
   * @param offset
   *          The offset of the byte array sub-sequence.
   * @param length
   *          The length of the byte array sub-sequence.
   * @return The string representation of the byte array sub-sequence.
   */
  static String toString(byte[] b, int offset, int length)
  {
    String stringValue;
    try
    {
      stringValue = new String(b, offset, length, "UTF-8");
    }
    catch (final Exception e)
    {
      if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
      {
        StaticUtils.DEBUG_LOG.warning("Unable to decode ByteString "
            + "bytes as UTF-8 string: " + e.toString());
      }

      stringValue = new String(b, offset, length);
    }

    return stringValue;
  }

  // These are package private so that compression and crypto
  // functionality may directly access the fields.

  // The buffer where data is stored.
  final byte[] buffer;

  // The number of bytes to expose from the buffer.
  final int length;

  // The start index of the range of bytes to expose through this byte
  // string.
  final int offset;



  /**
   * Creates a new byte string that wraps a subsequence of the provided
   * byte array.
   * <p>
   * <b>NOTE:</b> this method takes ownership of the provided byte array
   * and, therefore, the byte array MUST NOT be altered directly after
   * this method returns.
   * 
   * @param b
   *          The byte array to wrap.
   * @param offset
   *          The offset of the byte array to be used; must be
   *          non-negative and no larger than {@code b.length} .
   * @param length
   *          The length of the byte array to be used; must be
   *          non-negative and no larger than {@code b.length - offset}.
   */
  private ByteString(byte[] b, int offset, int length)
  {
    this.buffer = b;
    this.offset = offset;
    this.length = length;
  }



  /**
   * Returns a {@link ByteSequenceReader} which can be used to
   * incrementally read and decode data from this byte string.
   * 
   * @return The {@link ByteSequenceReader} which can be used to
   *         incrementally read and decode data from this byte string.
   */
  public ByteSequenceReader asReader()
  {
    return new ByteSequenceReader(this);
  }



  /**
   * {@inheritDoc}
   */
  public byte byteAt(int index) throws IndexOutOfBoundsException
  {
    if (index >= length || index < 0)
    {
      throw new IndexOutOfBoundsException();
    }
    return buffer[offset + index];
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(byte[] b, int offset, int length)
      throws IndexOutOfBoundsException
  {
    checkArrayBounds(b, offset, length);
    return compareTo(this.buffer, this.offset, this.length, b, offset,
        length);
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(ByteSequence o)
  {
    if (this == o)
    {
      return 0;
    }
    return -o.compareTo(buffer, offset, length);
  }



  /**
   * {@inheritDoc}
   */
  public byte[] copyTo(byte[] b)
  {
    copyTo(b, 0);
    return b;
  }



  /**
   * {@inheritDoc}
   */
  public byte[] copyTo(byte[] b, int offset)
      throws IndexOutOfBoundsException
  {
    if (offset < 0)
    {
      throw new IndexOutOfBoundsException();
    }
    System.arraycopy(buffer, this.offset, b, offset, Math.min(length,
        b.length - offset));
    return b;
  }



  /**
   * {@inheritDoc}
   */
  public ByteStringBuilder copyTo(ByteStringBuilder builder)
  {
    builder.append(buffer, offset, length);
    return builder;
  }



  /**
   * {@inheritDoc}
   */
  public OutputStream copyTo(OutputStream stream) throws IOException
  {
    stream.write(buffer, offset, length);
    return stream;
  }



  /**
   * {@inheritDoc}
   */
  public boolean equals(byte[] b, int offset, int length)
      throws IndexOutOfBoundsException
  {
    checkArrayBounds(b, offset, length);
    return equals(this.buffer, this.offset, this.length, b, offset,
        length);
  }



  /**
   * Indicates whether the provided object is equal to this byte string.
   * In order for it to be considered equal, the provided object must be
   * a byte sequence containing the same bytes in the same order.
   * 
   * @param o
   *          The object for which to make the determination.
   * @return {@code true} if the provided object is a byte sequence
   *         whose content is equal to that of this byte string, or
   *         {@code false} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    else if (o instanceof ByteSequence)
    {
      final ByteSequence other = (ByteSequence) o;
      return other.equals(buffer, offset, length);
    }
    else
    {
      return false;
    }
  }



  /**
   * Returns a hash code for this byte string. It will be the sum of all
   * of the bytes contained in the byte string.
   * 
   * @return A hash code for this byte string.
   */
  @Override
  public int hashCode()
  {
    return hashCode(buffer, offset, length);
  }



  /**
   * {@inheritDoc}
   */
  public int length()
  {
    return length;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString subSequence(int start, int end)
      throws IndexOutOfBoundsException
  {
    if (start < 0 || start > end || end > length)
    {
      throw new IndexOutOfBoundsException();
    }
    return new ByteString(buffer, offset + start, end - start);
  }



  /**
   * {@inheritDoc}
   */
  public byte[] toByteArray()
  {
    return copyTo(new byte[length]);
  }



  /**
   * {@inheritDoc}
   */
  public ByteString toByteString()
  {
    return this;
  }



  /**
   * Returns the integer value represented by the first four bytes of
   * this byte string in big-endian order.
   * 
   * @return The integer value represented by the first four bytes of
   *         this byte string in big-endian order.
   * @throws IndexOutOfBoundsException
   *           If this byte string has less than four bytes.
   */
  public int toInt() throws IndexOutOfBoundsException
  {
    if (length < 4)
    {
      throw new IndexOutOfBoundsException();
    }

    int v = 0;
    for (int i = 0; i < 4; i++)
    {
      v <<= 8;
      v |= buffer[offset + i] & 0xFF;
    }
    return v;
  }



  /**
   * Returns the long value represented by the first eight bytes of this
   * byte string in big-endian order.
   * 
   * @return The long value represented by the first eight bytes of this
   *         byte string in big-endian order.
   * @throws IndexOutOfBoundsException
   *           If this byte string has less than eight bytes.
   */
  public long toLong() throws IndexOutOfBoundsException
  {
    if (length < 8)
    {
      throw new IndexOutOfBoundsException();
    }

    long v = 0;
    for (int i = 0; i < 8; i++)
    {
      v <<= 8;
      v |= buffer[offset + i] & 0xFF;
    }
    return v;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return toString(buffer, offset, length);
  }
}
