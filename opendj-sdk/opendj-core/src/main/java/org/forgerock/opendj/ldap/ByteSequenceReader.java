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
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.Arrays;

/**
 * An interface for iteratively reading data from a {@link ByteSequence} .
 * {@code ByteSequenceReader} must be created using the associated
 * {@code ByteSequence}'s {@code asReader()} method.
 */
public final class ByteSequenceReader {

    private static final int[] DECODE_SIZE = new int[256];
    static {
        Arrays.fill(DECODE_SIZE, 0, 0x80, 1);
        Arrays.fill(DECODE_SIZE, 0x80, 0xc0, 2);
        Arrays.fill(DECODE_SIZE, 0xc0, 0xe0, 3);
        Arrays.fill(DECODE_SIZE, 0xe0, 0xf0, 4);
        Arrays.fill(DECODE_SIZE, 0xf0, 0xf8, 5);
        Arrays.fill(DECODE_SIZE, 0xf8, 0xfc, 6);
        Arrays.fill(DECODE_SIZE, 0xfc, 0xfe, 7);
        Arrays.fill(DECODE_SIZE, 0xfe, 0x100, 8);
    }

    /** The current position in the byte sequence. */
    private int pos;

    /** The underlying byte sequence. */
    private final ByteSequence sequence;

    /**
     * Creates a new byte sequence reader whose source is the provided byte
     * sequence.
     * <p>
     * <b>NOTE:</b> any concurrent changes to the underlying byte sequence (if
     * mutable) may cause subsequent reads to overrun and fail.
     * <p>
     * This constructor is package private: construction must be performed using
     * {@link ByteSequence#asReader()}.
     *
     * @param sequence
     *            The byte sequence to be read.
     */
    ByteSequenceReader(final ByteSequence sequence) {
        this.sequence = sequence;
    }

    /**
     * Relative get method. Reads the byte at the current position.
     *
     * @return The byte at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; 1}.
     */
    public byte get() {
        final byte b = sequence.byteAt(pos);
        pos++;
        return b;
    }

    /**
     * Relative bulk get method. This method transfers bytes from this reader
     * into the given destination array. An invocation of this method of the
     * form:
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
     *            The byte array into which bytes are to be written.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; b.length}.
     */
    public void get(final byte[] b) {
        get(b, 0, b.length);
    }

    /**
     * Relative bulk get method. Copies {@code length} bytes from this reader
     * into the given array, starting at the current position of this reader and
     * at the given {@code offset} in the array. The position of this reader is
     * then incremented by {@code length}. In other words, an invocation of this
     * method of the form:
     *
     * <pre>
     * src.get(b, offset, length);
     * </pre>
     *
     * Has exactly the same effect as the loop:
     *
     * <pre>
     * for (int i = offset; i &lt; offset + length; i++)
     *     b[i] = src.get();
     * </pre>
     *
     * Except that it first checks that there are sufficient bytes in this
     * buffer and it is potentially much more efficient.
     *
     * @param b
     *            The byte array into which bytes are to be written.
     * @param offset
     *            The offset within the array of the first byte to be written;
     *            must be non-negative and no larger than {@code b.length}.
     * @param length
     *            The number of bytes to be written to the given array; must be
     *            non-negative and no larger than {@code b.length} .
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; length}.
     */
    public void get(final byte[] b, final int offset, final int length) {
        if (offset < 0 || length < 0 || offset + length > b.length || length > remaining()) {
            throw new IndexOutOfBoundsException();
        }

        sequence.subSequence(pos, pos + length).copyTo(b, offset);
        pos += length;
    }

    /**
     * Relative get method for reading a multi-byte BER length. Reads the next
     * one to five bytes at this reader's current position, composing them into
     * a integer value and then increments the position by the number of bytes
     * read.
     *
     * @return The integer value representing the length at this reader's
     *         current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request.
     */
    public int getBERLength() {
        // Make sure we have at least one byte to read.
        int newPos = pos + 1;
        if (newPos > sequence.length()) {
            throw new IndexOutOfBoundsException();
        }

        int length = sequence.byteAt(pos) & 0x7F;
        if (length != sequence.byteAt(pos)) {
            // Its a multi-byte length
            final int numLengthBytes = length;
            newPos = pos + 1 + numLengthBytes;
            // Make sure we have the bytes needed
            if (numLengthBytes > 4 || newPos > sequence.length()) {
                // Shouldn't have more than 4 bytes
                throw new IndexOutOfBoundsException();
            }

            length = 0x00;
            for (int i = pos + 1; i < newPos; i++) {
                length = length << 8 | sequence.byteAt(i) & 0xFF;
            }
        }

        pos = newPos;
        return length;
    }

    /**
     * Relative bulk get method. Returns a {@link ByteSequence} whose content is
     * the next {@code length} bytes from this reader, starting at the current
     * position of this reader. The position of this reader is then incremented
     * by {@code length}.
     * <p>
     * <b>NOTE:</b> The value returned from this method should NEVER be cached
     * as it prevents the contents of the underlying byte stream from being
     * garbage collected.
     *
     * @param length
     *            The length of the byte sequence to be returned.
     * @return The byte sequence whose content is the next {@code length} bytes
     *         from this reader.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; length}.
     */
    public ByteSequence getByteSequence(final int length) {
        final int newPos = pos + length;
        final ByteSequence subSequence = sequence.subSequence(pos, newPos);
        pos = newPos;
        return subSequence;
    }

    /**
     * Relative bulk get method. Returns a {@link ByteString} whose content is
     * the next {@code length} bytes from this reader, starting at the current
     * position of this reader. The position of this reader is then incremented
     * by {@code length}.
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
     * <b>NOTE:</b> The value returned from this method should NEVER be cached
     * as it prevents the contents of the underlying byte stream from being
     * garbage collected.
     *
     * @param length
     *            The length of the byte string to be returned.
     * @return The byte string whose content is the next {@code length} bytes
     *         from this reader.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; length}.
     */
    public ByteString getByteString(final int length) {
        return getByteSequence(length).toByteString();
    }

    /**
     * Relative get method for reading an integer value. Reads the next four
     * bytes at this reader's current position, composing them into an integer
     * value according to big-endian byte order, and then increments the
     * position by four.
     *
     * @return The integer value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; 4}.
     */
    public int getInt() {
        if (remaining() < 4) {
            throw new IndexOutOfBoundsException();
        }

        int v = 0;
        for (int i = 0; i < 4; i++) {
            v <<= 8;
            v |= sequence.byteAt(pos++) & 0xFF;
        }

        return v;
    }

    /**
     * Relative get method for reading a long value. Reads the next eight bytes
     * at this reader's current position, composing them into a long value
     * according to big-endian byte order, and then increments the position by
     * eight.
     *
     * @return The long value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; 8}.
     */
    public long getLong() {
        if (remaining() < 8) {
            throw new IndexOutOfBoundsException();
        }

        long v = 0;
        for (int i = 0; i < 8; i++) {
            v <<= 8;
            v |= sequence.byteAt(pos++) & 0xFF;
        }

        return v;
    }

    /**
     * Relative get method for reading a compacted long value.
     * Compaction allows to reduce number of bytes needed to hold long types
     * depending on its value (i.e: if value < 128, value will be encoded using one byte only).
     * Reads the next bytes at this reader's current position, composing them into a long value
     * according to big-endian byte order, and then increments the position by the size of the
     * encoded long.
     * Note that the maximum value of a compact long is 2^56.
     *
     * @return The long value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request.
     */
    public long getCompactUnsigned() {
        final int b0 = get();
        final int size = decodeSize(b0);
        long value;
        switch (size) {
        case 1:
            value = b2l((byte) b0);
            break;
        case 2:
            value = (b0 & 0x3fL) << 8;
            value |= b2l(get());
            break;
        case 3:
            value = (b0 & 0x1fL) << 16;
            value |= b2l(get()) << 8;
            value |= b2l(get());
            break;
        case 4:
            value = (b0 & 0x0fL) << 24;
            value |= b2l(get()) << 16;
            value |= b2l(get()) << 8;
            value |= b2l(get());
            break;
        case 5:
            value = (b0 & 0x07L) << 32;
            value |= b2l(get()) << 24;
            value |= b2l(get()) << 16;
            value |= b2l(get()) << 8;
            value |= b2l(get());
            break;
        case 6:
            value = (b0 & 0x03L) << 40;
            value |= b2l(get()) << 32;
            value |= b2l(get()) << 24;
            value |= b2l(get()) << 16;
            value |= b2l(get()) << 8;
            value |= b2l(get());
            break;
        case 7:
            value = (b0 & 0x01L) << 48;
            value |= b2l(get()) << 40;
            value |= b2l(get()) << 32;
            value |= b2l(get()) << 24;
            value |= b2l(get()) << 16;
            value |= b2l(get()) << 8;
            value |= b2l(get());
            break;
        default:
            value = b2l(get()) << 48;
            value |= b2l(get()) << 40;
            value |= b2l(get()) << 32;
            value |= b2l(get()) << 24;
            value |= b2l(get()) << 16;
            value |= b2l(get()) << 8;
            value |= b2l(get());
        }
        return value;
    }

    private static long b2l(final byte b) {
        return b & 0xffL;
    }

    private static int decodeSize(int b) {
        return DECODE_SIZE[b & 0xff];
    }

    /**
     * Relative get method for reading an short value. Reads the next 2 bytes at
     * this reader's current position, composing them into an short value
     * according to big-endian byte order, and then increments the position by
     * two.
     *
     * @return The integer value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; 2}.
     */
    public short getShort() {
        if (remaining() < 2) {
            throw new IndexOutOfBoundsException();
        }

        short v = 0;
        for (int i = 0; i < 2; i++) {
            v <<= 8;
            v |= sequence.byteAt(pos++) & 0xFF;
        }

        return v;
    }

    /**
     * Relative get method for reading a UTF-8 encoded string. Reads the next
     * number of specified bytes at this reader's current position, decoding
     * them into a string using UTF-8 and then increments the position by the
     * number of bytes read. If UTF-8 decoding fails, the platform's default
     * encoding will be used.
     *
     * @param length
     *            The number of bytes to read and decode.
     * @return The string value at the reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining()
     *           &lt; length}.
     */
    public String getString(final int length) {
        if (remaining() < length) {
            throw new IndexOutOfBoundsException();
        }

        final int newPos = pos + length;
        final String str = sequence.subSequence(pos, pos + length).toString();
        pos = newPos;
        return str;
    }

    /**
     * Returns this reader's position.
     *
     * @return The position of this reader.
     */
    public int position() {
        return pos;
    }

    /**
     * Sets this reader's position.
     *
     * @param pos
     *            The new position value; must be non-negative and no larger
     *            than the length of the underlying byte sequence.
     * @throws IndexOutOfBoundsException
     *             If the position is negative or larger than the length of the
     *             underlying byte sequence.
     */
    public void position(final int pos) {
        if (pos > sequence.length() || pos < 0) {
            throw new IndexOutOfBoundsException();
        }

        this.pos = pos;
    }

    /**
     * Returns the number of bytes between the current position and the end of
     * the underlying byte sequence.
     *
     * @return The number of bytes between the current position and the end of
     *         the underlying byte sequence.
     */
    public int remaining() {
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
    public void rewind() {
        position(0);
    }

    /**
     * Returns the byte situated at the current position. The byte is not
     * consumed.
     *
     * @return the byte situated at the current position
     * @throws IndexOutOfBoundsException
     *           If the position is negative or larger than the length of the
     *           underlying byte sequence.
     */
    public byte peek() {
        return sequence.byteAt(pos);
    }

    /**
     * Returns the byte situated at the given offset from current position. The
     * byte is not consumed.
     *
     * @param offset
     *          The offset where to look at from current position.
     * @return the byte situated at the given offset from current position
     * @throws IndexOutOfBoundsException
     *           If the position is negative or larger than the length of the
     *           underlying byte sequence.
     */
    public byte peek(int offset) {
        return sequence.byteAt(pos + offset);
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
     *            The number of bytes to skip.
     * @throws IndexOutOfBoundsException
     *             If the new position is less than 0 or greater than the length
     *             of the underlying byte sequence.
     */
    public void skip(final int length) {
        position(pos + length);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return sequence.toString();
    }
}
