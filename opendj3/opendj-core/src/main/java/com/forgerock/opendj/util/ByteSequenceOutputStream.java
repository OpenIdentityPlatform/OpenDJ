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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package com.forgerock.opendj.util;

import java.io.IOException;
import java.io.OutputStream;

import org.forgerock.opendj.ldap.ByteStringBuilder;

/**
 * An adapter class that allows writing to an byte string builder with the
 * outputstream interface.
 */
public final class ByteSequenceOutputStream extends OutputStream {

    private final ByteStringBuilder buffer;

    /**
     * Creates a new byte string builder output stream.
     *
     * @param buffer
     *            The underlying byte string builder.
     */
    public ByteSequenceOutputStream(final ByteStringBuilder buffer) {
        this.buffer = buffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        buffer.clear();
    }

    /**
     * Gets the length of the underlying byte string builder.
     *
     * @return The length of the underlying byte string builder.
     */
    public int length() {
        return buffer.length();
    }

    /**
     * Resets this output stream such that the underlying byte string builder is
     * empty.
     */
    public void reset() {
        buffer.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final byte[] bytes) throws IOException {
        buffer.append(bytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final byte[] bytes, final int i, final int i1) throws IOException {
        buffer.append(bytes, i, i1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final int i) throws IOException {
        buffer.append(((byte) (i & 0xFF)));
    }

    /**
     * Writes the content of the underlying byte string builder to the provided
     * output stream.
     *
     * @param stream
     *            The output stream.
     * @throws IOException
     *             If an I/O error occurs. In particular, an {@code IOException}
     *             is thrown if the output stream is closed.
     */
    public void writeTo(final OutputStream stream) throws IOException {
        buffer.copyTo(stream);
    }
}
