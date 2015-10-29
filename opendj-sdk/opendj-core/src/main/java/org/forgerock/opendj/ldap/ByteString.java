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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;

import org.forgerock.i18n.LocalizedIllegalArgumentException;

import com.forgerock.opendj.util.StaticUtils;

/**
 * An immutable sequence of bytes backed by a byte array.
 */
public final class ByteString implements ByteSequence {

    /** Singleton empty byte string. */
    private static final ByteString EMPTY = wrap(new byte[0]);

    /**
     * Returns an empty byte string.
     *
     * @return An empty byte string.
     */
    public static ByteString empty() {
        return EMPTY;
    }

    /**
     * Returns a byte string containing the big-endian encoded bytes of the
     * provided integer.
     *
     * @param i
     *            The integer to encode.
     * @return The byte string containing the big-endian encoded bytes of the
     *         provided integer.
     */
    public static ByteString valueOfInt(int i) {
        final byte[] bytes = new byte[4];
        for (int j = 3; j >= 0; j--) {
            bytes[j] = (byte) i;
            i >>>= 8;
        }
        return wrap(bytes);
    }

    /**
     * Returns a byte string containing the big-endian encoded bytes of the
     * provided long.
     *
     * @param l
     *            The long to encode.
     * @return The byte string containing the big-endian encoded bytes of the
     *         provided long.
     */
    public static ByteString valueOfLong(long l) {
        final byte[] bytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) l;
            l >>>= 8;
        }
        return wrap(bytes);
    }

    /**
     * Returns a byte string representation of the provided object. The object
     * is converted to a byte string as follows:
     * <ul>
     * <li>if the object is an instance of {@code ByteSequence} then this method
     * is equivalent to calling {@code o.toByteString()}
     * <li>if the object is a {@code byte[]} then this method is equivalent to
     * calling {@link #valueOfBytes(byte[])}
     * <li>if the object is a {@code char[]} then this method is equivalent to
     * calling {@link #valueOfUtf8(char[])}
     * <li>for all other types of object this method is equivalent to calling
     * {@link #valueOfUtf8(CharSequence)} with the {@code toString()} representation of
     * the provided object.
     * </ul>
     * <b>Note:</b> this method treats {@code Long} and {@code Integer} objects
     * like any other type of {@code Object}. More specifically, the following
     * invocations are not equivalent:
     * <ul>
     * <li>{@code valueOf(0)} is not equivalent to {@code valueOf((Object) 0)}
     * <li>{@code valueOf(0L)} is not equivalent to {@code valueOf((Object) 0L)}
     * </ul>
     *
     * @param o
     *            The object to use.
     * @return The byte string containing the provided object.
     */
    public static ByteString valueOfObject(final Object o) {
        if (o instanceof ByteSequence) {
            return ((ByteSequence) o).toByteString();
        } else if (o instanceof byte[]) {
            return valueOfBytes((byte[]) o);
        } else if (o instanceof char[]) {
            return valueOfUtf8((char[]) o);
        } else {
            return valueOfUtf8(o.toString());
        }
    }

    /**
     * Returns a byte string containing the UTF-8 encoded bytes of the provided
     * char sequence.
     *
     * @param s
     *            The char sequence to use.
     * @return The byte string with the encoded bytes of the provided string.
     */
    public static ByteString valueOfUtf8(final CharSequence s) {
        if (s.length() == 0) {
            return EMPTY;
        }
        return wrap(StaticUtils.getBytes(s));
    }

    /**
     * Returns a byte string containing the Base64 decoded bytes of the provided
     * string.
     *
     * @param s
     *            The string to use.
     * @return The byte string containing the Base64 decoded bytes of the
     *         provided string.
     * @throws LocalizedIllegalArgumentException
     *             If the provided string does not contain valid Base64 encoded
     *             content.
     * @see #toBase64String()
     */
    public static ByteString valueOfBase64(final String s) {
        if (s.length() == 0) {
            return EMPTY;
        }
        return Base64.decode(s);
    }

    /**
     * Returns a byte string containing the bytes of the provided hexadecimal string.
     *
     * @param hexString
     *            The hexadecimal string to convert to a byte array.
     * @return The byte string containing the binary representation of the
     *         provided hex string.
     * @throws LocalizedIllegalArgumentException
     *             If the provided string contains invalid hexadecimal digits or
     *             does not contain an even number of digits.
     */
    public static ByteString valueOfHex(final String hexString) {
        if (hexString == null || hexString.length() == 0) {
            return EMPTY;
        }

        final int length = hexString.length();
        if (length % 2 != 0) {
            throw new LocalizedIllegalArgumentException(ERR_HEX_DECODE_INVALID_LENGTH.get(hexString));
        }
        final int arrayLength = length / 2;
        final byte[] bytes = new byte[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            bytes[i] = hexToByte(hexString, hexString.charAt(i * 2), hexString.charAt(i * 2 + 1));
        }
        return valueOfBytes(bytes);
    }

    /**
     * Returns a byte string containing the contents of the provided byte array.
     * <p>
     * This method differs from {@link #wrap(byte[])} in that it defensively
     * copies the provided byte array.
     *
     * @param bytes
     *            The byte array to use.
     * @return A byte string containing a copy of the provided byte array.
     */
    public static ByteString valueOfBytes(final byte[] bytes) {
        if (bytes.length == 0) {
            return EMPTY;
        }
        return wrap(Arrays.copyOf(bytes, bytes.length));
    }

    /**
     * Returns a byte string containing a subsequence of the contents of the
     * provided byte array.
     * <p>
     * This method differs from {@link #wrap(byte[], int, int)} in that it
     * defensively copies the provided byte array.
     *
     * @param bytes
     *            The byte array to use.
     * @param offset
     *            The offset of the byte array to be used; must be non-negative
     *            and no larger than {@code bytes.length} .
     * @param length
     *            The length of the byte array to be used; must be non-negative
     *            and no larger than {@code bytes.length - offset}.
     * @return A byte string containing a copy of the subsequence of the
     *         provided byte array.
     */
    public static ByteString valueOfBytes(final byte[] bytes, final int offset, final int length) {
        checkArrayBounds(bytes, offset, length);
        if (offset == length) {
            return EMPTY;
        }
        return wrap(Arrays.copyOfRange(bytes, offset, offset + length));
    }

    /**
     * Returns a byte string containing the UTF-8 encoded bytes of the provided
     * char array.
     *
     * @param chars
     *            The char array to use.
     * @return A byte string containing the UTF-8 encoded bytes of the provided
     *         char array.
     */
    public static ByteString valueOfUtf8(final char[] chars) {
        if (chars.length == 0) {
            return EMPTY;
        }
        return wrap(StaticUtils.getBytes(chars));
    }

    /**
     * Returns a byte string that wraps the provided byte array.
     * <p>
     * <b>NOTE:</b> this method takes ownership of the provided byte array and,
     * therefore, the byte array MUST NOT be altered directly after this method
     * returns.
     *
     * @param bytes
     *            The byte array to wrap.
     * @return The byte string that wraps the given byte array.
     */
    public static ByteString wrap(final byte[] bytes) {
        return new ByteString(bytes, 0, bytes.length);
    }

    /**
     * Returns a byte string that wraps a subsequence of the provided byte
     * array.
     * <p>
     * <b>NOTE:</b> this method takes ownership of the provided byte array and,
     * therefore, the byte array MUST NOT be altered directly after this method
     * returns.
     *
     * @param bytes
     *            The byte array to wrap.
     * @param offset
     *            The offset of the byte array to be used; must be non-negative
     *            and no larger than {@code bytes.length} .
     * @param length
     *            The length of the byte array to be used; must be non-negative
     *            and no larger than {@code bytes.length - offset}.
     * @return The byte string that wraps the given byte array.
     * @throws IndexOutOfBoundsException
     *             If {@code offset} is negative or if {@code length} is
     *             negative or if {@code offset + length} is greater than
     *             {@code bytes.length}.
     */
    public static ByteString wrap(final byte[] bytes, final int offset, final int length) {
        checkArrayBounds(bytes, offset, length);
        return new ByteString(bytes, offset, length);
    }

    /**
     * Checks the array bounds of the provided byte array sub-sequence, throwing
     * an {@code IndexOutOfBoundsException} if they are illegal.
     *
     * @param b
     *            The byte array.
     * @param offset
     *            The offset of the byte array to be checked; must be
     *            non-negative and no larger than {@code b.length}.
     * @param length
     *            The length of the byte array to be checked; must be
     *            non-negative and no larger than {@code b.length - offset}.
     * @throws IndexOutOfBoundsException
     *             If {@code offset} is negative or if {@code length} is
     *             negative or if {@code offset + length} is greater than
     *             {@code b.length}.
     */
    static void checkArrayBounds(final byte[] b, final int offset, final int length) {
        if (offset < 0 || offset > b.length || length < 0 || offset + length > b.length
                || offset + length < 0) {
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * Compares two byte array sub-sequences and returns a value that indicates
     * their relative order.
     *
     * @param b1
     *            The byte array containing the first sub-sequence.
     * @param offset1
     *            The offset of the first byte array sub-sequence.
     * @param length1
     *            The length of the first byte array sub-sequence.
     * @param b2
     *            The byte array containing the second sub-sequence.
     * @param offset2
     *            The offset of the second byte array sub-sequence.
     * @param length2
     *            The length of the second byte array sub-sequence.
     * @return A negative integer if first byte array sub-sequence should come
     *         before the second byte array sub-sequence in ascending order, a
     *         positive integer if the first byte array sub-sequence should come
     *         after the byte array sub-sequence in ascending order, or zero if
     *         there is no difference between the two byte array sub-sequences
     *         with regard to ordering.
     */
    static int compareTo(final byte[] b1, final int offset1, final int length1, final byte[] b2,
            final int offset2, final int length2) {
        int count = Math.min(length1, length2);
        int i = offset1;
        int j = offset2;
        while (count-- != 0) {
            final int firstByte = 0xFF & b1[i++];
            final int secondByte = 0xFF & b2[j++];
            if (firstByte != secondByte) {
                return firstByte - secondByte;
            }
        }
        return length1 - length2;
    }

    /**
     * Indicates whether two byte array sub-sequences are equal. In order for
     * them to be considered equal, they must contain the same bytes in the same
     * order.
     *
     * @param b1
     *            The byte array containing the first sub-sequence.
     * @param offset1
     *            The offset of the first byte array sub-sequence.
     * @param length1
     *            The length of the first byte array sub-sequence.
     * @param b2
     *            The byte array containing the second sub-sequence.
     * @param offset2
     *            The offset of the second byte array sub-sequence.
     * @param length2
     *            The length of the second byte array sub-sequence.
     * @return {@code true} if the two byte array sub-sequences have the same
     *         content, or {@code false} if not.
     */
    static boolean equals(final byte[] b1, final int offset1, final int length1, final byte[] b2,
            final int offset2, final int length2) {
        if (length1 != length2) {
            return false;
        }

        int i = offset1;
        int j = offset2;
        int count = length1;
        while (count-- != 0) {
            if (b1[i++] != b2[j++]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a hash code for the provided byte array sub-sequence.
     *
     * @param b
     *            The byte array.
     * @param offset
     *            The offset of the byte array sub-sequence.
     * @param length
     *            The length of the byte array sub-sequence.
     * @return A hash code for the provided byte array sub-sequence.
     */
    static int hashCode(final byte[] b, final int offset, final int length) {
        int hashCode = 1;
        int i = offset;
        int count = length;
        while (count-- != 0) {
            hashCode = 31 * hashCode + b[i++];
        }
        return hashCode;
    }

    /**
     * Returns the UTF-8 decoded string representation of the provided byte
     * array sub-sequence. If UTF-8 decoding fails, the platform's default
     * encoding will be used.
     *
     * @param b
     *            The byte array.
     * @param offset
     *            The offset of the byte array sub-sequence.
     * @param length
     *            The length of the byte array sub-sequence.
     * @return The string representation of the byte array sub-sequence.
     */
    static String toString(final byte[] b, final int offset, final int length) {
        if (length == 0) {
            return "";
        }
        try {
            return new String(b, offset, length, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            // TODO: I18N
            throw new RuntimeException("Unable to decode bytes as UTF-8 string", e);
        }
    }

    /**
     * Returns a 7-bit ASCII string representation.
     * Non-ASCII characters will be expanded to percent (%) hexadecimal value.
     * @return a 7-bit ASCII string representation
     */
    public String toASCIIString() {
        if (length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            byte b = buffer[offset + i];
            if (StaticUtils.isPrintable(b)) {
                sb.append((char) b);
            } else {
                sb.append('%');
                sb.append(StaticUtils.byteToHex(b));
            }
        }
        return sb.toString();
    }

    private static byte hexToByte(final String value, final char c1, final char c2) {
        byte b;
        switch (c1) {
        case '0':
            b = 0x00;
            break;
        case '1':
            b = 0x10;
            break;
        case '2':
            b = 0x20;
            break;
        case '3':
            b = 0x30;
            break;
        case '4':
            b = 0x40;
            break;
        case '5':
            b = 0x50;
            break;
        case '6':
            b = 0x60;
            break;
        case '7':
            b = 0x70;
            break;
        case '8':
            b = (byte) 0x80;
            break;
        case '9':
            b = (byte) 0x90;
            break;
        case 'A':
        case 'a':
            b = (byte) 0xA0;
            break;
        case 'B':
        case 'b':
            b = (byte) 0xB0;
            break;
        case 'C':
        case 'c':
            b = (byte) 0xC0;
            break;
        case 'D':
        case 'd':
            b = (byte) 0xD0;
            break;
        case 'E':
        case 'e':
            b = (byte) 0xE0;
            break;
        case 'F':
        case 'f':
            b = (byte) 0xF0;
            break;
        default:
            throw new LocalizedIllegalArgumentException(ERR_HEX_DECODE_INVALID_CHARACTER.get(value, c1));
        }

        switch (c2) {
        case '0':
            // No action required.
            break;
        case '1':
            b |= 0x01;
            break;
        case '2':
            b |= 0x02;
            break;
        case '3':
            b |= 0x03;
            break;
        case '4':
            b |= 0x04;
            break;
        case '5':
            b |= 0x05;
            break;
        case '6':
            b |= 0x06;
            break;
        case '7':
            b |= 0x07;
            break;
        case '8':
            b |= 0x08;
            break;
        case '9':
            b |= 0x09;
            break;
        case 'A':
        case 'a':
            b |= 0x0A;
            break;
        case 'B':
        case 'b':
            b |= 0x0B;
            break;
        case 'C':
        case 'c':
            b |= 0x0C;
            break;
        case 'D':
        case 'd':
            b |= 0x0D;
            break;
        case 'E':
        case 'e':
            b |= 0x0E;
            break;
        case 'F':
        case 'f':
            b |= 0x0F;
            break;
        default:
            throw new LocalizedIllegalArgumentException(ERR_HEX_DECODE_INVALID_CHARACTER.get(value, c2));
        }

        return b;
    }

    // These are package private so that compression and crypto
    // functionality may directly access the fields.

    /** The buffer where data is stored. */
    final byte[] buffer;

    /** The number of bytes to expose from the buffer. */
    final int length;

    /** The start index of the range of bytes to expose through this byte string. */
    final int offset;

    /**
     * Creates a new byte string that wraps a subsequence of the provided byte
     * array.
     * <p>
     * <b>NOTE:</b> this method takes ownership of the provided byte array and,
     * therefore, the byte array MUST NOT be altered directly after this method
     * returns.
     *
     * @param b
     *            The byte array to wrap.
     * @param offset
     *            The offset of the byte array to be used; must be non-negative
     *            and no larger than {@code b.length} .
     * @param length
     *            The length of the byte array to be used; must be non-negative
     *            and no larger than {@code b.length - offset}.
     */
    private ByteString(final byte[] b, final int offset, final int length) {
        this.buffer = b;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Returns a {@link ByteSequenceReader} which can be used to incrementally
     * read and decode data from this byte string.
     *
     * @return The {@link ByteSequenceReader} which can be used to incrementally
     *         read and decode data from this byte string.
     */
    @Override
    public ByteSequenceReader asReader() {
        return new ByteSequenceReader(this);
    }

    /** {@inheritDoc} */
    @Override
    public byte byteAt(final int index) {
        if (index >= length || index < 0) {
            throw new IndexOutOfBoundsException();
        }
        return buffer[offset + index];
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final byte[] bytes, final int offset, final int length) {
        checkArrayBounds(bytes, offset, length);
        return compareTo(this.buffer, this.offset, this.length, bytes, offset, length);
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final ByteSequence o) {
        if (this == o) {
            return 0;
        }
        return -o.compareTo(buffer, offset, length);
    }

    /** {@inheritDoc} */
    @Override
    public byte[] copyTo(final byte[] bytes) {
        copyTo(bytes, 0);
        return bytes;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] copyTo(final byte[] bytes, final int offset) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException();
        }
        System.arraycopy(buffer, this.offset, bytes, offset, Math.min(length, bytes.length - offset));
        return bytes;
    }

    @Override
    public ByteBuffer copyTo(final ByteBuffer byteBuffer) {
        byteBuffer.put(buffer, offset, length);
        return byteBuffer;
    }

    @Override
    public ByteStringBuilder copyTo(final ByteStringBuilder builder) {
        builder.appendBytes(buffer, offset, length);
        return builder;
    }


    @Override
    public boolean copyTo(CharBuffer charBuffer, CharsetDecoder decoder) {
        return copyTo(ByteBuffer.wrap(buffer, offset, length), charBuffer, decoder);
    }

    /**
     * Convenience method to copy from a byte buffer to a char buffer using provided decoder to decode
     * bytes into characters.
     * <p>
     * It should not be used directly, prefer instance method of ByteString or ByteStringBuilder instead.
     */
    static boolean copyTo(ByteBuffer inBuffer, CharBuffer outBuffer, CharsetDecoder decoder) {
        final CoderResult result = decoder.decode(inBuffer, outBuffer, true);
        decoder.flush(outBuffer);
        return !result.isError() && !result.isOverflow();
    }

    /** {@inheritDoc} */
    @Override
    public OutputStream copyTo(final OutputStream stream) throws IOException {
        stream.write(buffer, offset, length);
        return stream;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final byte[] bytes, final int offset, final int length) {
        checkArrayBounds(bytes, offset, length);
        return equals(this.buffer, this.offset, this.length, bytes, offset, length);
    }

    /**
     * Indicates whether the provided object is equal to this byte string. In
     * order for it to be considered equal, the provided object must be a byte
     * sequence containing the same bytes in the same order.
     *
     * @param o
     *            The object for which to make the determination.
     * @return {@code true} if the provided object is a byte sequence whose
     *         content is equal to that of this byte string, or {@code false} if
     *         not.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof ByteSequence) {
            final ByteSequence other = (ByteSequence) o;
            return other.equals(buffer, offset, length);
        } else {
            return false;
        }
    }

    /**
     * Returns a hash code for this byte string. It will be the sum of all of
     * the bytes contained in the byte string.
     *
     * @return A hash code for this byte string.
     */
    @Override
    public int hashCode() {
        return hashCode(buffer, offset, length);
    }

    @Override
    public boolean isEmpty() {
        return length == 0;
    }

    /** {@inheritDoc} */
    @Override
    public int length() {
        return length;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString subSequence(final int start, final int end) {
        if (start < 0 || start > end || end > length) {
            throw new IndexOutOfBoundsException();
        }
        return new ByteString(buffer, offset + start, end - start);
    }

    /** {@inheritDoc} */
    @Override
    public boolean startsWith(ByteSequence prefix) {
        if (prefix == null || prefix.length() > length) {
            return false;
        }
        return prefix.equals(buffer, 0, prefix.length());
    }

    /** {@inheritDoc} */
    @Override
    public String toBase64String() {
        return Base64.encode(this);
    }

    /**
     * Returns a string representation of the contents of this byte sequence
     * using hexadecimal characters and a space between each byte.
     *
     * @return A string representation of the contents of this byte sequence
     *         using hexadecimal characters.
     */
    public String toHexString() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder((length - 1) * 3 + 2);
        builder.append(StaticUtils.byteToHex(buffer[offset]));
        for (int i = 1; i < length; i++) {
            builder.append(' ');
            builder.append(StaticUtils.byteToHex(buffer[offset + i]));
        }
        return builder.toString();
    }

    /**
     * Returns a string representation of the contents of this byte sequence
     * using hexadecimal characters and a percent prefix (%) before each char.
     *
     * @return A string representation of the contents of this byte sequence
     *         using percent + hexadecimal characters.
     */
    public String toPercentHexString() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(length * 3);
        for (int i = 0; i < length; i++) {
            builder.append('%');
            builder.append(StaticUtils.byteToHex(buffer[offset + i]));
        }
        return builder.toString();
    }

    /**
     * Returns a string representation of the data in this byte sequence using
     * the specified indent.
     * <p>
     * The data will be formatted with sixteen hex bytes in a row followed by
     * the ASCII representation, then wrapping to a new line as necessary. The
     * state of the byte buffer is not changed.
     *
     * @param indent
     *            The number of spaces to indent the output.
     * @return the string representation of this byte string
     */
    public String toHexPlusAsciiString(int indent) {
        StringBuilder builder = new StringBuilder();
        StringBuilder indentBuf = new StringBuilder(indent);
        for (int i = 0; i < indent; i++) {
            indentBuf.append(' ');
        }
        int pos = 0;
        while (length - pos >= 16) {
            StringBuilder asciiBuf = new StringBuilder(17);
            byte currentByte = buffer[offset + pos];
            builder.append(indentBuf);
            builder.append(byteToHex(currentByte));
            asciiBuf.append(byteToASCII(currentByte));
            pos++;

            for (int i = 1; i < 16; i++, pos++) {
                currentByte = buffer[offset + pos];
                builder.append(' ');
                builder.append(byteToHex(currentByte));
                asciiBuf.append(byteToASCII(currentByte));

                if (i == 7) {
                    builder.append("  ");
                    asciiBuf.append(' ');
                }
            }

            builder.append("  ");
            builder.append(asciiBuf);
            builder.append(EOL);
        }

        int remaining = length - pos;
        if (remaining > 0) {
            StringBuilder asciiBuf = new StringBuilder(remaining + 1);

            byte currentByte = buffer[offset + pos];
            builder.append(indentBuf);
            builder.append(byteToHex(currentByte));
            asciiBuf.append(byteToASCII(currentByte));
            pos++;

            for (int i = 1; i < 16; i++, pos++) {
                builder.append(' ');

                if (i < remaining) {
                    currentByte = buffer[offset + pos];
                    builder.append(byteToHex(currentByte));
                    asciiBuf.append(byteToASCII(currentByte));
                } else {
                    builder.append("  ");
                }

                if (i == 7) {
                    builder.append("  ");

                    if (i < remaining) {
                        asciiBuf.append(' ');
                    }
                }
            }

            builder.append("  ");
            builder.append(asciiBuf);
            builder.append(EOL);
        }
        return builder.toString();
    }

    /** {@inheritDoc} */
    @Override
    public byte[] toByteArray() {
        return copyTo(new byte[length]);
    }

    /** {@inheritDoc} */
    @Override
    public ByteString toByteString() {
        return this;
    }

    /**
     * Returns the UTF-8 decoded char array representation of this byte
     * sequence.
     *
     * @return The UTF-8 decoded char array representation of this byte
     *         sequence.
     */
    public char[] toCharArray() {
        Charset utf8 = Charset.forName("UTF-8");
        CharBuffer charBuffer = utf8.decode(ByteBuffer.wrap(buffer, offset, length));
        char[] chars = new char[charBuffer.remaining()];
        charBuffer.get(chars);
        return chars;
    }

    /**
     * Returns the integer value represented by the first four bytes of this
     * byte string in big-endian order.
     *
     * @return The integer value represented by the first four bytes of this
     *         byte string in big-endian order.
     * @throws IndexOutOfBoundsException
     *             If this byte string has less than four bytes.
     */
    public int toInt() {
        if (length < 4) {
            throw new IndexOutOfBoundsException();
        }

        int v = 0;
        for (int i = 0; i < 4; i++) {
            v <<= 8;
            v |= buffer[offset + i] & 0xFF;
        }
        return v;
    }

    /**
     * Returns the long value represented by the first eight bytes of this byte
     * string in big-endian order.
     *
     * @return The long value represented by the first eight bytes of this byte
     *         string in big-endian order.
     * @throws IndexOutOfBoundsException
     *             If this byte string has less than eight bytes.
     */
    public long toLong() {
        if (length < 8) {
            throw new IndexOutOfBoundsException();
        }

        long v = 0;
        for (int i = 0; i < 8; i++) {
            v <<= 8;
            v |= buffer[offset + i] & 0xFF;
        }
        return v;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return toString(buffer, offset, length);
    }
}
