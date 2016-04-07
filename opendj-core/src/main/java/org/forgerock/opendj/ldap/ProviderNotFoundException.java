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
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.spi.Provider;

/**
 * Exception thrown when a provider of a service can't be found.
 */
@SuppressWarnings("serial")
public class ProviderNotFoundException extends RuntimeException {

    private final Class<? extends Provider> providerType;
    private final String providerName;

    /**
     * Creates the exception with a provider type, provider name and a message.
     *
     * @param providerClass
     *            the provider class
     * @param providerName
     *            the name of the provider implementation that was requested
     * @param message
     *            the detail message
     */
    public ProviderNotFoundException(final Class<? extends Provider> providerClass, final String providerName,
            final String message) {
        super(message);
        this.providerType = providerClass;
        this.providerName = providerName;
    }

    /**
     * Returns the type of provider.
     *
     * @return the provider class
     */
    public Class<?> getProviderType() {
        return providerType;
    }

    /**
     * Returns the name of provider.
     *
     * @return the name of the provider implementation that was requested, or
     *         null if the default provider was requested.
     */
    public String getProviderName() {
        return providerName;
    }

}
