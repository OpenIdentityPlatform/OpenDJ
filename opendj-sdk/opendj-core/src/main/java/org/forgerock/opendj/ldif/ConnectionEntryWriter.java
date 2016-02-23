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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;

import org.forgerock.util.Reject;

/**
 * A {@code ConnectionEntryWriter} is a bridge from {@code Connection}s to
 * {@code EntryWriter}s. A connection entry writer writes entries by sending Add
 * requests to an underlying connection.
 * <p>
 * All Add requests are performed synchronously, blocking until an Add result is
 * received. If an Add result indicates that an Add request has failed for some
 * reason then the error result is propagated to the caller using an
 * {@code LdapException}.
 * <p>
 * <b>Note:</b> comments are not supported by connection change record writers.
 * Attempts to write comments will be ignored.
 */
public final class ConnectionEntryWriter implements EntryWriter {
    private final Connection connection;

    /**
     * Creates a new connection entry writer whose destination is the provided
     * connection.
     *
     * @param connection
     *            The connection to use.
     * @throws NullPointerException
     *             If {@code connection} was {@code null}.
     */
    public ConnectionEntryWriter(final Connection connection) {
        Reject.ifNull(connection);
        this.connection = connection;
    }

    /**
     * Closes this connection entry writer, including the underlying connection.
     * Closing a previously closed entry writer has no effect.
     */
    @Override
    public void close() {
        connection.close();
    }

    /**
     * Connection entry writers do not require flushing, so this method has no
     * effect.
     */
    @Override
    public void flush() {
        // Do nothing.
    }

    /**
     * Connection entry writers do not support comments, so the provided comment
     * will be ignored.
     *
     * @param comment
     *            The {@code CharSequence} to be written as a comment.
     * @return A reference to this connection entry writer.
     * @throws NullPointerException
     *             If {@code comment} was {@code null}.
     */
    @Override
    public ConnectionEntryWriter writeComment(final CharSequence comment) {
        Reject.ifNull(comment);

        // Do nothing.
        return this;
    }

    /**
     * Writes an entry to the underlying connection using an Add request,
     * blocking until the request completes.
     *
     * @param entry
     *            The {@code Entry} to be written.
     * @return A reference to this connection entry writer.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    @Override
    public ConnectionEntryWriter writeEntry(final Entry entry) throws LdapException {
        Reject.ifNull(entry);
        connection.add(entry);
        return this;
    }

}
