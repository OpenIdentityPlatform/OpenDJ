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
