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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
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
