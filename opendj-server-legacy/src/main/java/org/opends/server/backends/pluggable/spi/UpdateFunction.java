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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable.spi;

import org.forgerock.opendj.ldap.ByteSequence;

/**
 * Function that computes the new value of a record for a Read-Modify-Write operation inside a transaction.
 */
// @FunctionalInterface
public interface UpdateFunction {
    /**
     * Computes the new value for a record based on the record's existing
     * content.
     *
     * @param oldValue
     *            The record's existing content, or {@code null} if the record
     *            does not exist at the moment and is about to be created.
     * @return The new value for the record (which may be {@code null} if the record should be removed),
     *         or the old value if no update is required.
     */
    ByteSequence computeNewValue(ByteSequence oldValue);
}
