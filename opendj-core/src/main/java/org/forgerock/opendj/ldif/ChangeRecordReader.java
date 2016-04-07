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

/**
 * An interface for reading change records from a data source, typically an LDIF
 * file.
 * <p>
 * Implementations must specify the following:
 * <ul>
 * <li>Whether or not it is possible for the implementation to encounter
 * malformed change records and, if it is possible, how they are handled.
 * <li>Any synchronization limitations.
 * </ul>
 */
public interface ChangeRecordReader extends Closeable {

    /**
     * Closes this change record reader if it not already closed. Note that this
     * method does not need to be called if a previous call of
     * {@link #readChangeRecord()} has returned {@code null}.
     *
     * @throws IOException
     *             If an unexpected IO error occurred while closing.
     */
    @Override
    void close() throws IOException;

    /**
     * Returns {@code true} if this reader contains another change record,
     * blocking if necessary until either the next change record is available or
     * the end of the stream is reached.
     *
     * @return {@code true} if this reader contains another change record.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    boolean hasNext() throws IOException;

    /**
     * Reads the next change record, blocking if necessary until a change record
     * is available. If the next change record does not contain a change type
     * then it will be treated as an {@code Add} change record.
     *
     * @return The next change record.
     * @throws IOException
     *             If an unexpected IO error occurred while reading the change
     *             record.
     * @throws NoSuchElementException
     *             If this reader does not contain any more change records.
     */
    ChangeRecord readChangeRecord() throws IOException;
}
