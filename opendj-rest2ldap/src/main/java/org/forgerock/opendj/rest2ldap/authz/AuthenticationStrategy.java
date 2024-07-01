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
package org.forgerock.opendj.rest2ldap.authz;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.promise.Promise;

/** Authenticate a user and create a {@link SecurityContext} as a result. */
public interface AuthenticationStrategy {
    /**
     * Authenticate a user.
     *
     * @param username
     *            User to authenticate.
     * @param password
     *            Password used to perform the authentication.
     * @param parentContext
     *            Context to use as parent for the created {@link SecurityContext}
     * @return A {@link Context} if the authentication succeed or an {@link LdapException} otherwise.
     */
    Promise<SecurityContext, LdapException> authenticate(String username, String password, Context parentContext);
}
