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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions copyright 2013 ForgeRock AS.
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
