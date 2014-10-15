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
 *       Copyright 2008 Sun Microsystems, Inc.
 *       Portions copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.server.core;

/**
 * The status of a data provider. A data provider may be enabled, disabled, or
 * providing a restricted service.
 */
public enum DataProviderStatus {
    /**
     * The data provider is disabled and rejecting all operations.
     */
    DISABLED("disabled"),

    /**
     * The data provider is enabled and accepting all operations.
     */
    ENABLED("enabled"),

    /**
     * The data provider is only accepting read operations; all write operations
     * will be rejected.
     */
    READ_ONLY("read-only"),

    /**
     * The data provider is accepting read operations, internal write
     * operations, and updates through synchronization; all other write
     * operations will be rejected.
     */
    WRITE_INTERNAL_ONLY("write-internal-only");

    /** The human-readable name for this status. */
    private String name;

    /**
     * Creates a new data provider status with the provided name.
     *
     * @param name
     *            The human-readable name for this status.
     */
    private DataProviderStatus(final String name) {
        this.name = name;
    }

    /**
     * Retrieves a string representation of this status.
     *
     * @return A string representation of this status.
     */
    @Override
    public String toString() {
        return name;
    }
}
