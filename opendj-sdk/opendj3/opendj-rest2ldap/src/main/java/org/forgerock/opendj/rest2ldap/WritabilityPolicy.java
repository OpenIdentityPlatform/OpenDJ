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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

import org.forgerock.opendj.ldap.AttributeDescription;

/**
 * The writability policy determines whether or not an attribute supports
 * updates.
 */
public enum WritabilityPolicy {
    // @formatter:off
    /**
     * The attribute cannot be provided when creating a new resource, nor
     * modified afterwards. Attempts to update the attribute will result in an
     * error.
     */
    READ_ONLY(false),

    /**
     * The attribute cannot be provided when creating a new resource, nor
     * modified afterwards. Attempts to update the attribute will not result in
     * an error (the new values will be ignored).
     */
    READ_ONLY_DISCARD_WRITES(true),

    /**
     * The attribute may be provided when creating a new resource, but cannot be
     * modified afterwards. Attempts to update the attribute will result in an
     * error.
     */
    CREATE_ONLY(false),

    /**
     * The attribute may be provided when creating a new resource, but cannot be
     * modified afterwards. Attempts to update the attribute will not result in
     * an error (the new values will be ignored).
     */
    CREATE_ONLY_DISCARD_WRITES(true),

    /**
     * The attribute may be provided when creating a new resource, and modified
     * afterwards.
     */
    READ_WRITE(false);
    // @formatter:on

    private final boolean discardWrites;

    private WritabilityPolicy(final boolean discardWrites) {
        this.discardWrites = discardWrites;
    }

    boolean canCreate(final AttributeDescription attribute) {
        return this != READ_ONLY && !attribute.getAttributeType().isNoUserModification();
    }

    boolean canWrite(final AttributeDescription attribute) {
        return this == READ_WRITE && !attribute.getAttributeType().isNoUserModification();
    }

    boolean discardWrites() {
        return discardWrites;
    }
}
