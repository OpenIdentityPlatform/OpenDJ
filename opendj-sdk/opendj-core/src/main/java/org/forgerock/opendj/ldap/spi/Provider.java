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
package org.forgerock.opendj.ldap.spi;

/**
 * Interface for providers, which provide an implementation of one or more interfaces.
 * <p>
 * A provider must be declared in the provider-configuration file
 * {@code META-INF/services/org.forgerock.opendj.ldap.spi.<ProviderClass>}
 * in order to allow automatic loading of the implementation classes using the
 * {@code java.util.ServiceLoader} facility.
 */
public interface Provider {

    /**
     * Returns the name of this provider.
     *
     * @return name of provider
     */
    String getName();

}
