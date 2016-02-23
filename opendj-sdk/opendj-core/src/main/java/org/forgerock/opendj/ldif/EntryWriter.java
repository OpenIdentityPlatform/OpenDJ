/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

import org.forgerock.opendj.ldap.Entry;

/**
 * An interface for writing entries to a data source, typically an LDIF file.
 */
public interface EntryWriter extends Closeable, Flushable {
    /**
     * Closes this entry writer, flushing it first. Closing a previously closed
     * entry writer has no effect.
     *
     * @throws IOException
     *             If an unexpected IO error occurred while closing.
     */
    @Override
    void close() throws IOException;

    /**
     * Flushes this entry writer so that any buffered data is written
     * immediately to underlying stream, flushing the stream if it is also
     * {@code Flushable}.
     * <p>
     * If the intended destination of this stream is an abstraction provided by
     * the underlying operating system, for example a file, then flushing the
     * stream guarantees only that bytes previously written to the stream are
     * passed to the operating system for writing; it does not guarantee that
     * they are actually written to a physical device such as a disk drive.
     *
     * @throws IOException
     *             If an unexpected IO error occurred while flushing.
     */
    @Override
    void flush() throws IOException;

    /**
     * Writes a comment.
     *
     * @param comment
     *            The {@code CharSequence} to be written as a comment.
     * @return A reference to this entry writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the comment.
     * @throws NullPointerException
     *             If {@code comment} was {@code null}.
     */
    EntryWriter writeComment(CharSequence comment) throws IOException;

    /**
     * Writes an entry.
     *
     * @param entry
     *            The {@code Entry} to be written.
     * @return A reference to this entry writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the entry.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    EntryWriter writeEntry(Entry entry) throws IOException;

}
