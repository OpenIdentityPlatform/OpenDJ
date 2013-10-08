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
 *      Copyright 2013 ForgeRock AS.
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
