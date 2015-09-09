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
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.util.Comparator;

/**
 * A {@code ByteSequence} is a readable sequence of byte values. This interface
 * provides uniform, read-only access to many different kinds of byte sequences.
 */
public interface ByteSequence extends Comparable<ByteSequence> {

    /** A byte array comparator. */
    Comparator<byte[]> BYTE_ARRAY_COMPARATOR = new Comparator<byte[]>() {
        @Override
        public int compare(final byte[] b1, final byte[] b2) {
            return ByteString.compareTo(b1, 0, b1.length, b2, 0, b2.length);
        }
    };

    /** A ByteSequence comparator. */
    Comparator<ByteSequence> COMPARATOR = new Comparator<ByteSequence>() {
        @Override
        public int compare(final ByteSequence o1, final ByteSequence o2) {
            return o1.compareTo(o2);
        }
    };

    /**
     * Returns a {@link ByteSequenceReader} which can be used to incrementally
     * read and decode data from this byte sequence.
     * <p>
     * <b>NOTE:</b> any concurrent changes to the underlying byte sequence (if
     * mutable) may cause subsequent reads to overrun and fail.
     *
     * @return The {@link ByteSequenceReader} which can be used to incrementally
     *         read and decode data from this byte sequence.
     */
    ByteSequenceReader asReader();

    /**
     * Returns the byte value at the specified index.
     * <p>
     * An index ranges from zero to {@code length() - 1}. The first byte value
     * of the sequence is at index zero, the next at index one, and so on, as
     * for array indexing.
     *
     * @param index
     *            The index of the byte to be returned.
     * @return The byte value at the specified index.
     * @throws IndexOutOfBoundsException
     *             If the index argument is negative or not less than length().
     */
    byte byteAt(int index);

    /**
     * Compares this byte sequence with the specified byte array sub-sequence
     * for order. Returns a negative integer, zero, or a positive integer
     * depending on whether this byte sequence is less than, equal to, or
     * greater than the specified byte array sub-sequence.
     *
     * @param bytes
     *            The byte array to compare.
     * @param offset
     *            The offset of the sub-sequence in the byte array to be
     *            compared; must be non-negative and no larger than
     *            {@code bytes.length} .
     * @param length
     *            The length of the sub-sequence in the byte array to be
     *            compared; must be non-negative and no larger than
     *            {@code bytes.length - offset}.
     * @return A negative integer, zero, or a positive integer depending on
     *         whether this byte sequence is less than, equal to, or greater
     *         than the specified byte array sub-sequence.
     * @throws IndexOutOfBoundsException
     *             If {@code offset} is negative or if {@code length} is
     *             negative or if {@code offset + length} is greater than
     *             {@code bytes.length}.
     */
    int compareTo(byte[] bytes, int offset, int length);

    /**
     * Compares this byte sequence with the specified byte sequence for order.
     * Returns a negative integer, zero, or a positive integer depending on
     * whether this byte sequence is less than, equal to, or greater than the
     * specified object.
     *
     * @param o
     *            The byte sequence to be compared.
     * @return A negative integer, zero, or a positive integer depending on
     *         whether this byte sequence is less than, equal to, or greater
     *         than the specified object.
     */
    @Override
    int compareTo(ByteSequence o);

    /**
     * Copies the contents of this byte sequence to the provided byte array.
     * <p>
     * Copying will stop when either the entire content of this sequence has
     * been copied or if the end of the provided byte array has been reached.
     * <p>
     * An invocation of the form:
     *
     * <pre>
     * src.copyTo(bytes)
     * </pre>
     *
     * Behaves in exactly the same way as the invocation:
     *
     * <pre>
     * src.copyTo(bytes, 0);
     * </pre>
     *
     * @param bytes
     *            The byte array to which bytes are to be copied.
     * @return The byte array.
     */
    byte[] copyTo(byte[] bytes);

    /**
     * Copies the contents of this byte sequence to the specified location in
     * the provided byte array.
     * <p>
     * Copying will stop when either the entire content of this sequence has
     * been copied or if the end of the provided byte array has been reached.
     * <p>
     * An invocation of the form:
     *
     * <pre>
     * src.copyTo(bytes, offset)
     * </pre>
     *
     * Behaves in exactly the same way as the invocation:
     *
     * <pre>
     * int len = Math.min(src.length(), bytes.length - offset);
     * for (int i = 0; i &lt; len; i++)
     *     bytes[offset + i] = src.get(i);
     * </pre>
     *
     * Except that it is potentially much more efficient.
     *
     * @param bytes
     *            The byte array to which bytes are to be copied.
     * @param offset
     *            The offset within the array of the first byte to be written;
     *            must be non-negative and no larger than bytes.length.
     * @return The byte array.
     * @throws IndexOutOfBoundsException
     *             If {@code offset} is negative.
     */
    byte[] copyTo(byte[] bytes, int offset);

    /**
     * Appends the entire contents of this byte sequence to the provided
     * {@link ByteStringBuilder}.
     *
     * @param builder
     *            The builder to copy to.
     * @return The builder.
     */
    ByteStringBuilder copyTo(ByteStringBuilder builder);

    /**
     * Appends the content of this byte sequence to the provided {@link ByteBuffer} starting at it's current position.
     * The position of the buffer is then incremented by the length of this sequence.
     *
     * @param buffer
     *            The buffer to copy to.
     *            It must be large enough to receive all bytes.
     * @return The buffer.
     * @throws BufferOverflowException
     *            If there is insufficient space in the provided buffer
     */
    ByteBuffer copyTo(ByteBuffer buffer);

    /**
     * Appends the content of this byte sequence decoded using provided charset decoder to the provided
     * {@link CharBuffer} starting at it's current position. The position of charBuffer is then incremented by the
     * length of this sequence.
     *
     * @param charBuffer
     *            The buffer to copy to, if decoding is successful.
     *            It must be large enough to receive all decoded characters.
     * @param decoder
     *            The charset decoder to use for decoding.
     * @return {@code true} if byte string was successfully decoded and charBuffer is
     *         large enough to receive the resulting string, {@code false} otherwise
     */
    boolean copyTo(CharBuffer charBuffer, CharsetDecoder decoder);

    /**
     * Copies the entire contents of this byte sequence to the provided
     * {@code OutputStream}.
     *
     * @param stream
     *            The {@code OutputStream} to copy to.
     * @return The {@code OutputStream}.
     * @throws IOException
     *             If an error occurs while writing to the {@code OutputStream}.
     */
    OutputStream copyTo(OutputStream stream) throws IOException;

    /**
     * Indicates whether the provided byte array sub-sequence is equal to this
     * byte sequence. In order for it to be considered equal, the provided byte
     * array sub-sequence must contain the same bytes in the same order.
     *
     * @param bytes
     *            The byte array for which to make the determination.
     * @param offset
     *            The offset of the sub-sequence in the byte array to be
     *            compared; must be non-negative and no larger than
     *            {@code bytes.length} .
     * @param length
     *            The length of the sub-sequence in the byte array to be
     *            compared; must be non-negative and no larger than
     *            {@code bytes.length - offset}.
     * @return {@code true} if the content of the provided byte array
     *         sub-sequence is equal to that of this byte sequence, or
     *         {@code false} if not.
     * @throws IndexOutOfBoundsException
     *             If {@code offset} is negative or if {@code length} is
     *             negative or if {@code offset + length} is greater than
     *             {@code bytes.length}.
     */
    boolean equals(byte[] bytes, int offset, int length);

    /**
     * Indicates whether the provided object is equal to this byte sequence. In
     * order for it to be considered equal, the provided object must be a byte
     * sequence containing the same bytes in the same order.
     *
     * @param o
     *            The object for which to make the determination.
     * @return {@code true} if the provided object is a byte sequence whose
     *         content is equal to that of this byte sequence, or {@code false}
     *         if not.
     */
    @Override
    boolean equals(Object o);

    /**
     * Returns a hash code for this byte sequence. It will be the sum of all of
     * the bytes contained in the byte sequence.
     *
     * @return A hash code for this byte sequence.
     */
    @Override
    int hashCode();

    /**
     * Returns {@code true} if this byte sequence has a length of zero.
     *
     * @return {@code true} if this byte sequence has a length of zero.
     */
    boolean isEmpty();

    /**
     * Returns the length of this byte sequence.
     *
     * @return The length of this byte sequence.
     */
    int length();

    /**
     * Returns a new byte sequence that is a subsequence of this byte sequence.
     * <p>
     * The subsequence starts with the byte value at the specified {@code start}
     * index and ends with the byte value at index {@code end - 1}. The length
     * (in bytes) of the returned sequence is {@code end - start}, so if
     * {@code start
     * == end} then an empty sequence is returned.
     * <p>
     * <b>NOTE:</b> changes to the underlying byte sequence (if mutable) may
     * render the returned sub-sequence invalid.
     *
     * @param start
     *            The start index, inclusive.
     * @param end
     *            The end index, exclusive.
     * @return The newly created byte subsequence.
     * @throws IndexOutOfBoundsException
     *             If {@code start} or {@code end} are negative, if {@code end}
     *             is greater than {@code length()}, or if {@code start} is
     *             greater than {@code end}.
     */
    ByteSequence subSequence(int start, int end);

    /**
     * Tests if this ByteSequence starts with the specified prefix.
     *
     * @param prefix
     *            The prefix.
     * @return true if the byte sequence represented by the argument is a prefix of the byte sequence represented by
     *         this ByteSequence; false otherwise. Note also that true will be returned if the argument is an empty
     *         sequence or is equal to this ByteSequence object as determined by the equals(Object) method.
     */
    boolean startsWith(ByteSequence prefix);

    /**
     * Returns the Base64 encoded string representation of this byte string.
     *
     * @return The Base64 encoded string representation of this byte string.
     * @see ByteString#valueOfBase64(String)
     */
    String toBase64String();

    /**
     * Returns a byte array containing the bytes in this sequence in the same
     * order as this sequence. The length of the byte array will be the length
     * of this sequence.
     * <p>
     * An invocation of the form:
     *
     * <pre>
     * src.toByteArray()
     * </pre>
     *
     * Behaves in exactly the same way as the invocation:
     *
     * <pre>
     * src.copyTo(new byte[src.length()]);
     * </pre>
     *
     * @return A byte array consisting of exactly this sequence of bytes.
     */
    byte[] toByteArray();

    /**
     * Returns the {@link ByteString} representation of this byte sequence.
     *
     * @return The {@link ByteString} representation of this byte sequence.
     */
    ByteString toByteString();

    /**
     * Returns the UTF-8 decoded string representation of this byte sequence. If
     * UTF-8 decoding fails, the platform's default encoding will be used.
     *
     * @return The string representation of this byte sequence.
     */
    @Override
    String toString();
}
