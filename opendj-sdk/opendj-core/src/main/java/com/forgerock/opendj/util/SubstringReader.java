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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package com.forgerock.opendj.util;

import org.forgerock.util.Reject;

/**
 * A sub-string reader.
 */
public class SubstringReader {
    /** The source string. */
    private final String source;
    /** The current position. */
    private int pos;
    /** The marked position. */
    private int mark;
    /** The length of the source. */
    private final int length;

    /**
     * Creates an instance of SubstringReader.
     *
     * @param s
     *            the source of the reader.
     */
    public SubstringReader(final String s) {
        Reject.ifNull(s);
        source = s;
        length = s.length();
        pos = 0;
        mark = 0;
    }

    /**
     * Returns the source string.
     *
     * @return source string.
     */
    public String getString() {
        return source;
    }

    /**
     * Marks the present position in the stream. Subsequent calls to reset()
     * will reposition the stream to this point.
     */
    public void mark() {
        mark = pos;
    }

    /**
     * Returns the current position of the reader.
     *
     * @return current position of the reader.
     */
    public int pos() {
        return pos;
    }

    /**
     * Attempts to read a character from the current position. The caller must
     * ensure that the source string has the data available from the current
     * position.
     *
     * @return The character at the current position.
     * @throws StringIndexOutOfBoundsException
     *             If there is no more data available to read.
     */
    public char read() {
        if (pos >= length) {
            throw new StringIndexOutOfBoundsException();
        }
        return source.charAt(pos++);
    }

    /**
     * Attempts to read a substring of the specified length from the current
     * position. The caller must ensure that the requested length is within the
     * bounds i.e. the requested length from the current position should not
     * exceed the length of the source string.
     *
     * @param length
     *            The number of characters to read.
     * @return The substring.
     * @throws StringIndexOutOfBoundsException
     *             If the length exceeds the allowed length.
     */
    public String read(final int length) {
        if (length > this.length || pos + length > this.length) {
            throw new StringIndexOutOfBoundsException();
        }
        final String substring = source.substring(pos, pos + length);
        pos += length;
        return substring;
    }

    /**
     * Returns the remaining length of the available data.
     *
     * @return remaining length.
     */
    public int remaining() {
        return length - pos;
    }

    /**
     * Resets the stream to the most recent mark, or to the beginning of the
     * string if it has never been marked.
     */
    public void reset() {
        pos = mark;
    }

    /**
     * Skips the whitespace characters and advances the reader to the next non
     * whitespace character.
     *
     * @return number of whitespace characters skipped.
     */
    public int skipWhitespaces() {
        int skipped = 0;
        while (pos < length && source.charAt(pos) == ' ') {
            skipped++;
            pos++;
        }
        return skipped;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "("
                + "source=" + source
                + ", remaining=" + source.substring(pos, length)
                + ")";
    }
}
