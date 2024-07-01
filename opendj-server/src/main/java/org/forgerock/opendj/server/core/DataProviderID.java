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
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.server.core;

import java.util.Locale;

/**
 * A unique ID which can be used for identifying data providers.
 * <p>
 * There are two types of data provider:
 * <ul>
 * <li><b>User configured</b>: these are data providers which have been defined
 * using the server's configuration.
 * <li><b>Internal</b>: these are data providers which have been created
 * internally.
 * </ul>
 */
public final class DataProviderID implements Comparable<DataProviderID> {

    /**
     * Creates a new ID for an internal data provider.
     *
     * @param name
     *            The name of the internal data provider.
     * @return The new data provider ID.
     */
    public static DataProviderID newInternalID(final String name) {
        return new DataProviderID(name, true /* internal */);
    }

    /**
     * Creates a new ID for a user configured data provider.
     *
     * @param name
     *            The name of the user configured data provider.
     * @return The new data provider ID.
     */
    public static DataProviderID newUserID(final String name) {
        return new DataProviderID(name, false /* user */);
    }

    /** Whether this ID represents an internal data provider. */
    private final boolean isInternal;
    /** The data provider name. */
    private final String name;
    /** The normalized name. */
    private final String normalizedName;

    /** Prevent direct instantiation. */
    private DataProviderID(final String name, final boolean isInternal) {
        this.name = name;
        this.normalizedName = name.trim().toLowerCase(Locale.ENGLISH);
        this.isInternal = isInternal;
    }

    @Override
    public int compareTo(final DataProviderID o) {
        if (isInternal != o.isInternal) {
            // Internal data providers sort last.
            return isInternal ? 1 : -1;
        } else {
            return normalizedName.compareTo(o.normalizedName);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof DataProviderID) {
            final DataProviderID other = (DataProviderID) obj;
            return isInternal == other.isInternal
                && normalizedName.equals(other.normalizedName);
        } else {
            return false;
        }
    }

    /**
     * Returns the data provider name associated with this data provider ID.
     *
     * @return The data provider name associated with this data provider ID.
     */
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return normalizedName.hashCode();
    }

    /**
     * Indicating whether this ID represents an internal data provider.
     *
     * @return <code>true</code> if this ID represents an internal data
     *         provider.
     */
    public boolean isInternal() {
        return isInternal;
    }

    @Override
    public String toString() {
        if (isInternal) {
            return "__" + name;
        } else {
            return name;
        }
    }

}
