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
 *      Copyright 2014-2015 ForgeRock AS
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
