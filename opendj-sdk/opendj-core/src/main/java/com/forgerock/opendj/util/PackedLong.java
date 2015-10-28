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
 *      Copyright 2015 ForgeRock AS.
 */
package com.forgerock.opendj.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Provides static methods to manipulate compact long representation. Compact long allow to stores unsigned long values
 * up to 56 bits using a variable number of bytes from 1 to 8. The binary representations of this compact encoding has
 * the interesting properties of maintaining correct order of values when compared.
 */
public final class PackedLong {

    /** Maximum size in bytes of a compact encoded value. */
    public static final int MAX_COMPACT_SIZE = 8;

    private PackedLong() {
    }

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

    /** Maximum value that can be stored with a compacted representation. */
    public static final long COMPACTED_MAX_VALUE = 0x00FFFFFFFFFFFFFFL;

    /**
     * Append the compact representation of the value into the {@link OutputStream}.
     *
     * @param os
     *            {@link OutputStream} where the compact representation will be written.
     * @param value
     *            Value to be encoded and written in the compact long format.
     * @throws IOException
     *             if problem appear in the underlying {@link OutputStream}
     * @return Number of bytes which has been written in the buffer.
     */
    public static int writeCompactUnsigned(OutputStream os, long value) throws IOException {
        final int size = getEncodedSize(value);
        switch (size) {
        case 1:
            os.write((int) value);
            break;
        case 2:
            os.write((int) ((value >>> 8) | 0x80L));
            os.write((int) value);
            break;
        case 3:
            os.write((int) ((value >>> 16) | 0xc0L));
            os.write((int) (value >>> 8));
            os.write((int) (value));
            break;
        case 4:
            os.write((int) ((value >>> 24) | 0xe0L));
            os.write((int) (value >>> 16));
            os.write((int) (value >>> 8));
            os.write((int) (value));
            break;
        case 5:
            os.write((int) ((value >>> 32) | 0xf0L));
            os.write((int) (value >>> 24));
            os.write((int) (value >>> 16));
            os.write((int) (value >>> 8));
            os.write((int) (value));
            break;
        case 6:
            os.write((int) ((value >>> 40) | 0xf8L));
            os.write((int) (value >>> 32));
            os.write((int) (value >>> 24));
            os.write((int) (value >>> 16));
            os.write((int) (value >>> 8));
            os.write((int) (value));
            break;
        case 7:
            os.write((int) ((value >>> 48) | 0xfcL));
            os.write((int) (value >>> 40));
            os.write((int) (value >>> 32));
            os.write((int) (value >>> 24));
            os.write((int) (value >>> 16));
            os.write((int) (value >>> 8));
            os.write((int) (value));
            break;
        case 8:
            os.write(0xfe);
            os.write((int) (value >>> 48));
            os.write((int) (value >>> 40));
            os.write((int) (value >>> 32));
            os.write((int) (value >>> 24));
            os.write((int) (value >>> 16));
            os.write((int) (value >>> 8));
            os.write((int) (value));
            break;
        default:
            throw new IllegalArgumentException();
        }
        return size;
    }

    /**
     * Get the number of bytes required to store the given value using the compact long representation.
     *
     * @param value
     *            Value to get the compact representation's size.
     * @return Number of bytes required to store the compact long representation of the value.
     */
    public static int getEncodedSize(long value) {
        if (value < 0x80L) {
            return 1;
        } else if (value < 0x4000L) {
            return 2;
        } else if (value < 0x200000L) {
            return 3;
        } else if (value < 0x10000000L) {
            return 4;
        } else if (value < 0x800000000L) {
            return 5;
        } else if (value < 0x40000000000L) {
            return 6;
        } else if (value < 0x2000000000000L) {
            return 7;
        } else if (value < 0x100000000000000L) {
            return 8;
        } else {
            throw new IllegalArgumentException("value out of range: " + value);
        }
    }

    /**
     * Decode and get the value of the compact long contained in the specified {@link InputStream}.
     *
     * @param is
     *            Stream where to read the compact unsigned long
     * @return The long value.
     * @throws IOException
     *             If the first byte cannot be read for any reason other than the end of the file, if the input stream
     *             has been closed, or if some other I/O error occurs.
     */
    public static long readCompactUnsignedLong(InputStream is) throws IOException {
        final int b0 = checkNotEndOfStream(is.read());
        final int size = decodeSize(b0);
        long value;
        switch (size) {
        case 1:
            value = b2l((byte) b0);
            break;
        case 2:
            value = (b0 & 0x3fL) << 8;
            value |= checkNotEndOfStream(is.read());
            break;
        case 3:
            value = (b0 & 0x1fL) << 16;
            value |= ((long) checkNotEndOfStream(is.read())) << 8;
            value |= checkNotEndOfStream(is.read());
            break;
        case 4:
            value = (b0 & 0x0fL) << 24;
            value |= ((long) checkNotEndOfStream(is.read())) << 16;
            value |= ((long) checkNotEndOfStream(is.read())) << 8;
            value |= is.read();
            break;
        case 5:
            value = (b0 & 0x07L) << 32;
            value |= ((long) checkNotEndOfStream(is.read())) << 24;
            value |= ((long) checkNotEndOfStream(is.read())) << 16;
            value |= ((long) checkNotEndOfStream(is.read())) << 8;
            value |= (is.read());
            break;
        case 6:
            value = (b0 & 0x03L) << 40;
            value |= ((long) checkNotEndOfStream(is.read())) << 32;
            value |= ((long) checkNotEndOfStream(is.read())) << 24;
            value |= ((long) checkNotEndOfStream(is.read())) << 16;
            value |= ((long) checkNotEndOfStream(is.read())) << 8;
            value |= is.read();
            break;
        case 7:
            value = (b0 & 0x01L) << 48;
            value |= ((long) checkNotEndOfStream(is.read())) << 40;
            value |= ((long) checkNotEndOfStream(is.read())) << 32;
            value |= ((long) checkNotEndOfStream(is.read())) << 24;
            value |= ((long) checkNotEndOfStream(is.read())) << 16;
            value |= ((long) checkNotEndOfStream(is.read())) << 8;
            value |= is.read();
            break;
        default:
            value = ((long) checkNotEndOfStream(is.read())) << 48;
            value |= ((long) checkNotEndOfStream(is.read())) << 40;
            value |= ((long) checkNotEndOfStream(is.read())) << 32;
            value |= ((long) checkNotEndOfStream(is.read())) << 24;
            value |= ((long) checkNotEndOfStream(is.read())) << 16;
            value |= ((long) checkNotEndOfStream(is.read())) << 8;
            value |= is.read();
        }
        return value;
    }

    private static int checkNotEndOfStream(final int byteValue) {
        if (byteValue == -1) {
            throw new IllegalArgumentException("End of stream reached.");
        }
        return byteValue;
    }

    private static int decodeSize(int b) {
        return DECODE_SIZE[b & 0xff];
    }

    private static long b2l(final byte b) {
        return b & 0xffL;
    }
}
