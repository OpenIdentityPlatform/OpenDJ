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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.security;

/**
 * A service provider interface for implementing key store caches. The key store cache can be configured using the
 * {@link KeyStoreParameters#CACHE} option. It is strongly recommended that cache implementations evict key store
 * objects after a period of time in order to avoid returning stale objects.
 *
 * @see OpenDJProvider#newKeyStoreObjectCacheFromMap(java.util.Map)
 */
public interface KeyStoreObjectCache {
    /** A cache implementation that does not cache anything. */
    KeyStoreObjectCache NONE = new KeyStoreObjectCache() {
        @Override
        public void put(final KeyStoreObject keyStoreObject) {
            // Do nothing.
        }

        @Override
        public KeyStoreObject get(final String alias) {
            // Always miss.
            return null;
        }
    };

    /**
     * Puts a key store object in the cache replacing any existing key store object with the same alias.
     *
     * @param keyStoreObject
     *         The key store object.
     */
    void put(KeyStoreObject keyStoreObject);

    /**
     * Returns the named key store object from the cache if present, or {@code null} if the object is not present or
     * has been removed.
     *
     * @param alias
     *         The alias of the key store object to be retrieved.
     * @return The key store object or {@code null} if the object is not present.
     */
    KeyStoreObject get(String alias);
}
