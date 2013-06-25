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
package org.opends.server.types;



/**
 * An interface for iteratively reading date from a
 * {@link ByteSequence}. {@code ByteSequenceReader} must be created
 * using the associated {@code ByteSequence}'s
 * {@code asReader()} method.
 */
public final class ByteSequenceReader
{

  // The current position in the byte sequence.
  private int pos = 0;

  // The underlying byte sequence.
  private final ByteSequence sequence;



  /**
   * Creates a new byte sequence reader whose source is the provided
   * byte sequence.
   * <p>
   * <b>NOTE:</b> any concurrent changes to the underlying byte
   * sequence (if mutable) may cause subsequent reads to overrun and
   * fail.
   * <p>
   * This constructor is package private: construction must be
   * performed using {@link ByteSequence#asReader()}.
   *
   * @param sequence
   *          The byte sequence to be read.
   */
  ByteSequenceReader(ByteSequence sequence)
  {
    this.sequence = sequence;
  }



  /**
   * Relative get method. Reads the byte at the current position.
   *
   * @return The byte at this reader's current position.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < 1}.
   */
  public byte get() throws IndexOutOfBoundsException
  {
    byte b = sequence.byteAt(pos);
    pos++;
    return b;
  }



  /**
   * Relative bulk get method. This method transfers bytes from this
   * reader into the given destination array. An invocation of this
   * method of the form:
   *
   * <pre>
   * src.get(b);
   * </pre>
   *
   * Behaves in exactly the same way as the invocation:
   *
   * <pre>
   * src.get(b, 0, b.length);
   * </pre>
   *
   * @param b
   *          The byte array into which bytes are to be written.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < b.length}.
   */
  public void get(byte[] b) throws IndexOutOfBoundsException
  {
    get(b, 0, b.length);
  }



  /**
   * Relative bulk get method. Copies {@code length} bytes from this
   * reader into the given array, starting at the current position of
   * this reader and at the given {@code offset} in the array. The
   * position of this reader is then incremented by {@code length}. In
   * other words, an invocation of this method of the form:
   *
   * <pre>
   * src.get(b, offset, length);
   * </pre>
   *
   * Has exactly the same effect as the loop:
   *
   * <pre>
   * for (int i = offset; i &lt; offset + length; i++)
   *   b[i] = src.get();
   * </pre>
   *
   * Except that it first checks that there are sufficient bytes in
   * this buffer and it is potentially much more efficient.
   *
   * @param b
   *          The byte array into which bytes are to be written.
   * @param offset
   *          The offset within the array of the first byte to be
   *          written; must be non-negative and no larger than {@code
   *          b.length}.
   * @param length
   *          The number of bytes to be written to the given array;
   *          must be non-negative and no larger than {@code b.length}
   *          .
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < length}.
   */
  public void get(byte[] b, int offset, int length)
      throws IndexOutOfBoundsException
  {
    if ((offset < 0) || (length < 0) || (offset + length > b.length)
        || (length > remaining()))
    {
      throw new IndexOutOfBoundsException();
    }

    sequence.subSequence(pos, pos + length).copyTo(b, offset);
    pos += length;
  }



  /**
   * Relative get method for reading a multi-byte BER length. Reads
   * the next one to five bytes at this reader's current position,
   * composing them into a integer value and then increments the
   * position by the number of bytes read.
   *
   * @return The integer value representing the length at this
   *         reader's current position.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request.
   */
  public int getBERLength() throws IndexOutOfBoundsException
  {
    // Make sure we have at least one byte to read.
    int newPos = pos + 1;
    if (newPos > sequence.length())
    {
      throw new IndexOutOfBoundsException();
    }

    int length = (sequence.byteAt(pos) & 0x7F);
    if (length != sequence.byteAt(pos))
    {
      // Its a multi-byte length
      int numLengthBytes = length;
      newPos = pos + 1 + numLengthBytes;
      // Make sure we have the bytes needed
      if (numLengthBytes > 4 || newPos > sequence.length())
      {
        // Shouldn't have more than 4 bytes
        throw new IndexOutOfBoundsException();
      }

      length = 0x00;
      for (int i = pos + 1; i < newPos; i++)
      {
        length = (length << 8) | (sequence.byteAt(i) & 0xFF);
      }
    }

    pos = newPos;
    return length;
  }



  /**
   * Relative bulk get method. Returns a {@link ByteSequence} whose
   * content is the next {@code length} bytes from this reader,
   * starting at the current position of this reader. The position of
   * this reader is then incremented by {@code length}.
   * <p>
   * <b>NOTE:</b> The value returned from this method should NEVER be
   * cached as it prevents the contents of the underlying byte stream
   * from being garbage collected.
   *
   * @param length
   *          The length of the byte sequence to be returned.
   * @return The byte sequence whose content is the next {@code
   *         length} bytes from this reader.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < length}.
   */
  public ByteSequence getByteSequence(int length)
      throws IndexOutOfBoundsException
  {
    int newPos = pos + length;
    ByteSequence subSequence = sequence.subSequence(pos, newPos);
    pos = newPos;
    return subSequence;
  }



  /**
   * Relative bulk get method. Returns a {@link ByteString} whose
   * content is the next {@code length} bytes from this reader,
   * starting at the current position of this reader. The position of
   * this reader is then incremented by {@code length}.
   * <p>
   * An invocation of this method of the form:
   *
   * <pre>
   * src.getByteString(length);
   * </pre>
   *
   * Has exactly the same effect as:
   *
   * <pre>
   * src.getByteSequence(length).toByteString();
   * </pre>
   *
   * <b>NOTE:</b> The value returned from this method should NEVER be
   * cached as it prevents the contents of the underlying byte stream
   * from being garbage collected.
   *
   * @param length
   *          The length of the byte string to be returned.
   * @return The byte string whose content is the next {@code length}
   *         bytes from this reader.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < length}.
   */
  public ByteString getByteString(int length)
      throws IndexOutOfBoundsException
  {
    return getByteSequence(length).toByteString();
  }



  /**
   * Relative get method for reading an short value. Reads the next
   * 2 bytes at this reader's current position, composing them into
   * an short value according to big-endian byte order, and then
   * increments the position by two.
   *
   * @return The integer value at this reader's current position.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < 2}.
   */
  public short getShort() throws IndexOutOfBoundsException
  {
    if (remaining() < 2)
    {
      throw new IndexOutOfBoundsException();
    }

    short v = 0;
    for (int i = 0; i < 2; i++)
    {
      v <<= 8;
      v |= (sequence.byteAt(pos++) & 0xFF);
    }

    return v;
  }



  /**
   * Relative get method for reading an integer value. Reads the next
   * four bytes at this reader's current position, composing them into
   * an integer value according to big-endian byte order, and then
   * increments the position by four.
   *
   * @return The integer value at this reader's current position.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < 4}.
   */
  public int getInt() throws IndexOutOfBoundsException
  {
    if (remaining() < 4)
    {
      throw new IndexOutOfBoundsException();
    }

    int v = 0;
    for (int i = 0; i < 4; i++)
    {
      v <<= 8;
      v |= (sequence.byteAt(pos++) & 0xFF);
    }

    return v;
  }



  /**
   * Relative get method for reading a long value. Reads the next
   * eight bytes at this reader's current position, composing them
   * into a long value according to big-endian byte order, and then
   * increments the position by eight.
   *
   * @return The long value at this reader's current position.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < 8}.
   */
  public long getLong() throws IndexOutOfBoundsException
  {
    if (remaining() < 8)
    {
      throw new IndexOutOfBoundsException();
    }

    long v = 0;
    for (int i = 0; i < 8; i++)
    {
      v <<= 8;
      v |= (sequence.byteAt(pos++) & 0xFF);
    }

    return v;
  }



  /**
   * Relative get method for reading a UTF-8 encoded string. Reads the
   * next number of specified bytes at this reader's current position,
   * decoding them into a string using UTF-8 and then increments the
   * position by the number of bytes read. If UTF-8 decoding fails,
   * the platform's default encoding will be used.
   *
   * @param length
   *          The number of bytes to read and decode.
   * @return The string value at the reader's current position.
   * @throws IndexOutOfBoundsException
   *           If there are fewer bytes remaining in this reader than
   *           are required to satisfy the request, that is, if
   *           {@code remaining() < length}.
   */
  public String getString(int length) throws IndexOutOfBoundsException
  {
    if (remaining() < length)
    {
      throw new IndexOutOfBoundsException();
    }

    int newPos = pos + length;
    String str = sequence.subSequence(pos, pos + length).toString();
    pos = newPos;
    return str;
  }



  /**
   * Returns this reader's position.
   *
   * @return The position of this reader.
   */
  public int position()
  {
    return pos;
  }



  /**
   * Sets this reader's position.
   *
   * @param pos
   *          The new position value; must be non-negative and no
   *          larger than the length of the underlying byte sequence.
   * @throws IndexOutOfBoundsException
   *           If the position is negative or larger than the length
   *           of the underlying byte sequence.
   */
  public void position(int pos) throws IndexOutOfBoundsException
  {
    if (pos > sequence.length() || pos < 0)
    {
      throw new IndexOutOfBoundsException();
    }

    this.pos = pos;
  }



  /**
   * Returns the number of bytes between the current position and the
   * end of the underlying byte sequence.
   *
   * @return The number of bytes between the current position and the
   *         end of the underlying byte sequence.
   */
  public int remaining()
  {
    return sequence.length() - pos;
  }



  /**
   * Rewinds this reader's position to zero.
   * <p>
   * An invocation of this method of the form:
   *
   * <pre>
   * src.rewind();
   * </pre>
   *
   * Has exactly the same effect as:
   *
   * <pre>
   * src.position(0);
   * </pre>
   */
  public void rewind()
  {
    position(0);
  }



  /**
   * Skips the given number of bytes. Negative values are allowed.
   * <p>
   * An invocation of this method of the form:
   *
   * <pre>
   * src.skip(length);
   * </pre>
   *
   * Has exactly the same effect as:
   *
   * <pre>
   * src.position(position() + length);
   * </pre>
   *
   * @param length
   *          The number of bytes to skip.
   * @throws IndexOutOfBoundsException
   *           If the new position is less than 0 or greater than the
   *           length of the underlying byte sequence.
   */
  public void skip(int length) throws IndexOutOfBoundsException
  {
    position(pos + length);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return sequence.toString();
  }
}
