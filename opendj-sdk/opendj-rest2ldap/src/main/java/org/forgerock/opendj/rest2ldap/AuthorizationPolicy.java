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

/**
 * The policy which should be for performing authorization.
 */
public enum AuthorizationPolicy {
    /**
     * Use connections acquired from the LDAP connection factory. Don't use
     * proxied authorization, and don't use cached pre-authenticated
     * connections.
     */
    NONE,

    /**
     * Use the connection obtained during LDAP authentication. If no connection
     * was passed through the authorization will fail.
     */
    REUSE,

    /**
     * Use proxied authorization with an authorization ID derived from the
     * proxied authorization ID template. Proxied authorization will only be
     * used if there is no pre-authenticated connection available.
     */
    PROXY;
}
