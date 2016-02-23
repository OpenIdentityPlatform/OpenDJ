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

import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;

/**
 * An interface for writing change records to a data source, typically an LDIF
 * file.
 */
public interface ChangeRecordWriter extends Closeable, Flushable {
    /**
     * Closes this change record writer, flushing it first. Closing a previously
     * closed change record writer has no effect.
     *
     * @throws IOException
     *             If an unexpected IO error occurred while closing.
     */
    @Override
    void close() throws IOException;

    /**
     * Flushes this change record writer so that any buffered data is written
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
     * Writes an {@code Add} change record.
     *
     * @param change
     *            The {@code AddRequest} to be written as an {@code Add} change
     *            record.
     * @return A reference to this change record writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the change
     *             record.
     * @throws NullPointerException
     *             If {@code change} was {@code null}.
     */
    ChangeRecordWriter writeChangeRecord(AddRequest change) throws IOException;

    /**
     * Writes a change record.
     *
     * @param change
     *            The {@code ChangeRecord} to be written.
     * @return A reference to this change record writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the change
     *             record.
     * @throws NullPointerException
     *             If {@code change} was {@code null}.
     */
    ChangeRecordWriter writeChangeRecord(ChangeRecord change) throws IOException;

    /**
     * Writes a {@code Delete} change record.
     *
     * @param change
     *            The {@code DeleteRequest} to be written as an {@code Delete}
     *            change record.
     * @return A reference to this change record writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the change
     *             record.
     * @throws NullPointerException
     *             If {@code change} was {@code null}.
     */
    ChangeRecordWriter writeChangeRecord(DeleteRequest change) throws IOException;

    /**
     * Writes a {@code ModifyDN} change record.
     *
     * @param change
     *            The {@code ModifyDNRequest} to be written as an
     *            {@code ModifyDN} change record.
     * @return A reference to this change record writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the change
     *             record.
     * @throws NullPointerException
     *             If {@code change} was {@code null}.
     */
    ChangeRecordWriter writeChangeRecord(ModifyDNRequest change) throws IOException;

    /**
     * Writes a {@code Modify} change record.
     *
     * @param change
     *            The {@code ModifyRequest} to be written as an {@code Modify}
     *            change record.
     * @return A reference to this change record writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the change
     *             record.
     * @throws NullPointerException
     *             If {@code change} was {@code null}.
     */
    ChangeRecordWriter writeChangeRecord(ModifyRequest change) throws IOException;

    /**
     * Writes a comment.
     *
     * @param comment
     *            The {@code CharSequence} to be written as a comment.
     * @return A reference to this change record writer.
     * @throws IOException
     *             If an unexpected IO error occurred while writing the comment.
     * @throws NullPointerException
     *             If {@code comment} was {@code null}.
     */
    ChangeRecordWriter writeComment(CharSequence comment) throws IOException;

}
