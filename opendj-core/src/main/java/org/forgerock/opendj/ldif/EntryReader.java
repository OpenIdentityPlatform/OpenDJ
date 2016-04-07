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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.io.Closeable;
import java.io.IOException;
import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.Entry;

/**
 * An interface for reading entries from a data source, typically an LDIF file.
 * <p>
 * Implementations must specify the following:
 * <ul>
 * <li>Whether or not it is possible for the implementation to encounter
 * malformed change records and, if it is possible, how they are handled.
 * <li>Any synchronization limitations.
 * </ul>
 */
public interface EntryReader extends Closeable {

    /**
     * Closes this entry reader if it is not already closed. Note that this
     * method does not need to be called if a previous call of
     * {@link #readEntry()} has returned {@code null}.
     *
     * @throws IOException
     *             If an unexpected IO error occurred while closing.
     */
    @Override
    void close() throws IOException;

    /**
     * Returns {@code true} if this reader contains another entry, blocking if
     * necessary until either the next entry is available or the end of the
     * stream is reached.
     *
     * @return {@code true} if this reader contains another entry.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    boolean hasNext() throws IOException;

    /**
     * Reads the next entry, blocking if necessary until an entry is available.
     *
     * @return The next entry.
     * @throws IOException
     *             If an unexpected IO error occurred while reading the entry.
     * @throws NoSuchElementException
     *             If this reader does not contain any more entries.
     */
    Entry readEntry() throws IOException;
}
