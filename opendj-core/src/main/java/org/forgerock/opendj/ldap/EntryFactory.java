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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

/**
 * Entry factories are included with a set of {@code DecodeOptions} in order to
 * allow application to control how {@code Entry} instances are created when
 * decoding requests and responses.
 *
 * @see Entry
 * @see DecodeOptions
 */
public interface EntryFactory {
    /**
     * Creates an empty entry using the provided distinguished name and no
     * attributes.
     *
     * @param name
     *            The distinguished name of the entry to be created.
     * @return The new entry.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    Entry newEntry(DN name);
}
