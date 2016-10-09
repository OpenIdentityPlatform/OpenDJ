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

import org.forgerock.opendj.ldap.ByteSequence;

/**
 * A service provider interface for externalizing the strategy used for wrapping individual private/secret keys.
 * Applications can configure an LDAP keystore to delegate key wrapping responsibilities by setting the
 * {@link KeyStoreParameters#EXTERNAL_KEY_WRAPPING_STRATEGY} option.
 */
public interface ExternalKeyWrappingStrategy {
    /**
     * Wraps the provided encoded key.
     *
     * @param unwrappedKey
     *         The non-{@code null} key to be wrapped. The format of the unwrapped key is unspecified.
     * @return The non-{@code null} protected key. The format of the returned wrapped key is implementation defined.
     * @throws LocalizedKeyStoreException
     *         If an unexpected problem occurred when wrapping the key.
     */
    ByteSequence wrapKey(ByteSequence unwrappedKey) throws LocalizedKeyStoreException;

    /**
     * Unwraps the provided {@link #wrapKey(ByteSequence) wrapped} key.
     *
     * @param wrappedKey
     *         The non-{@code null} key to be unwrapped. The format of the wrapped key is implementation
     *         defined and must have been produced via a call to {@link #wrapKey(ByteSequence)}.
     * @return The non-{@code null} unwrapped key which must contain exactly the same content passed to {@link
     * #wrapKey(ByteSequence)}.
     * @throws LocalizedKeyStoreException
     *         If an unexpected problem occurred when unwrapping the key.
     */
    ByteSequence unwrapKey(ByteSequence wrappedKey) throws LocalizedKeyStoreException;
}
