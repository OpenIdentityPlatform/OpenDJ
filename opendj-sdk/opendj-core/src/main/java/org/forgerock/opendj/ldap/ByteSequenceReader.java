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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.io.InputStream;

import com.forgerock.opendj.util.PackedLong;

/**
 * An interface for iteratively reading data from a {@link ByteSequence} .
 * {@code ByteSequenceReader} must be created using the associated
 * {@code ByteSequence}'s {@code asReader()} method.
 */
public final class ByteSequenceReader {

    /** The current position in the byte sequence. */
    private int pos;

    /** The underlying byte sequence. */
    private final ByteSequence sequence;

    /**
     * The lazily allocated input stream view of this reader. Synchronization is not necessary because the stream is
     * stateless and race conditions can be tolerated.
     */
    private InputStream inputStream;

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
     * Relative read method. Reads the byte at the current position.
     *
     * @return The byte at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining() < 1}.
     */
    public byte readByte() {
        final byte b = sequence.byteAt(pos);
        pos++;
        return b;
    }

    /**
     * Relative bulk read method. This method transfers bytes from this reader
     * into the given destination array. An invocation of this method of the
     * form:
     *
     * <pre>
     * src.readBytes(b);
     * </pre>
     *
     * Behaves in exactly the same way as the invocation:
     *
     * <pre>
     * src.readBytes(b, 0, b.length);
     * </pre>
     *
     * @param b
     *            The byte array into which bytes are to be written.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining() < b.length}.
     */
    public void readBytes(final byte[] b) {
        readBytes(b, 0, b.length);
    }

    /**
     * Relative bulk read method. Copies {@code length} bytes from this reader
     * into the given array, starting at the current position of this reader and
     * at the given {@code offset} in the array. The position of this reader is
     * then incremented by {@code length}. In other words, an invocation of this
     * method of the form:
     *
     * <pre>
     * src.read(b, offset, length);
     * </pre>
     *
     * Has exactly the same effect as the loop:
     *
     * <pre>
     * for (int i = offset; i &lt; offset + length; i++)
     *     b[i] = src.readByte();
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
     *             {@code remaining() < length}.
     */
    public void readBytes(final byte[] b, final int offset, final int length) {
        if (offset < 0 || length < 0 || offset + length > b.length || length > remaining()) {
            throw new IndexOutOfBoundsException();
        }

        sequence.subSequence(pos, pos + length).copyTo(b, offset);
        pos += length;
    }

    /**
     * Relative read method for reading a multi-byte BER length. Reads the next
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
    public int readBERLength() {
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
     * Relative bulk read method. Returns a {@link ByteSequence} whose content is
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
     *             {@code remaining() < length}.
     */
    public ByteSequence readByteSequence(final int length) {
        final int newPos = pos + length;
        final ByteSequence subSequence = sequence.subSequence(pos, newPos);
        pos = newPos;
        return subSequence;
    }

    /**
     * Relative bulk read method. Returns a {@link ByteString} whose content is
     * the next {@code length} bytes from this reader, starting at the current
     * position of this reader. The position of this reader is then incremented
     * by {@code length}.
     * <p>
     * An invocation of this method of the form:
     *
     * <pre>
     * src.readByteString(length);
     * </pre>
     *
     * Has exactly the same effect as:
     *
     * <pre>
     * src.readByteSequence(length).toByteString();
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
     *             {@code remaining() < length}.
     */
    public ByteString readByteString(final int length) {
        return readByteSequence(length).toByteString();
    }

    /**
     * Relative read method for reading an integer value. Reads the next four
     * bytes at this reader's current position, composing them into an integer
     * value according to big-endian byte order, and then increments the
     * position by four.
     *
     * @return The integer value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining() < 4}.
     */
    public int readInt() {
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
     * Relative read method for reading a long value. Reads the next eight bytes
     * at this reader's current position, composing them into a long value
     * according to big-endian byte order, and then increments the position by
     * eight.
     *
     * @return The long value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining() < 8}.
     */
    public long readLong() {
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
     * Relative read method for reading a compacted long value.
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
    public long readCompactUnsignedLong() {
        try {
            return PackedLong.readCompactUnsignedLong(asInputStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Relative read method for reading a compacted int value.
     * Compaction allows to reduce number of bytes needed to hold int types
     * depending on its value (i.e: if value < 128, value will be encoded using one byte only).
     * Reads the next bytes at this reader's current position, composing them into an int value
     * according to big-endian byte order, and then increments the position by the size of the
     * encoded int.
     *
     * @return The int value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request.
     */
    public int readCompactUnsignedInt() {
        long l = readCompactUnsignedLong();
        if (l > Integer.MAX_VALUE) {
            throw new IllegalStateException(ERR_INVALID_COMPACTED_UNSIGNED_INT.get(Integer.MAX_VALUE, l).toString());
        }
        return (int) l;
    }

    /**
     * Relative read method for reading an short value. Reads the next 2 bytes at
     * this reader's current position, composing them into an short value
     * according to big-endian byte order, and then increments the position by
     * two.
     *
     * @return The integer value at this reader's current position.
     * @throws IndexOutOfBoundsException
     *             If there are fewer bytes remaining in this reader than are
     *             required to satisfy the request, that is, if
     *             {@code remaining() < 2}.
     */
    public short readShort() {
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
     * Relative read method for reading a UTF-8 encoded string. Reads the next
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
     *             {@code remaining() < length}.
     */
    public String readStringUtf8(final int length) {
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

    @Override
    public String toString() {
        return sequence.toString();
    }

    private InputStream asInputStream() {
        if (inputStream == null) {
            inputStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    if (pos >= sequence.length()) {
                        return -1;
                    }
                    return sequence.byteAt(pos++) & 0xFF;
                }
            };
        }
        return inputStream;
    }
}
