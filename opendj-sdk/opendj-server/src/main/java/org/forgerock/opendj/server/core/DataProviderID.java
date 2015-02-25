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
 *       Portions copyright 2013-2015 ForgeRock AS.
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

    /**
     * Flag indicating whether or not this ID represents an internal
     * data provider.
     */
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

    /** {@inheritDoc} */
    @Override
    public int compareTo(final DataProviderID o) {
        if (isInternal != o.isInternal) {
            // Internal data providers sort last.
            return isInternal ? 1 : -1;
        } else {
            return normalizedName.compareTo(o.normalizedName);
        }
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return normalizedName.hashCode();
    }

    /**
     * Indicating whether or not this ID represents an internal data provider.
     *
     * @return <code>true</code> if this ID represents an internal data
     *         provider.
     */
    public boolean isInternal() {
        return isInternal;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        if (isInternal) {
            return "__" + name;
        } else {
            return name;
        }
    }

}
