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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.opendj.rest2ldap.Rest2Ldap.asResourceException;
import static org.forgerock.util.Utils.closeSilently;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.http.oauth2.AccessTokenException;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

final class Utils {
    private static final AsyncFunction<LdapException, Response, NeverThrowsException> HANDLE_CONNECTION_FAILURE =
            new AsyncFunction<LdapException, Response, NeverThrowsException>() {
                @Override
                public Promise<Response, NeverThrowsException> apply(final LdapException exception) {
                    return asErrorResponse(exception);
                }
            };

    private Utils() { }

    static IllegalArgumentException newIllegalArgumentException(final LocalizableMessage message) {
        return new IllegalArgumentException(message.toString());
    }

    static AccessTokenException newAccessTokenException(final LocalizableMessage message) {
        return newAccessTokenException(message, null);
    }

    static AccessTokenException newAccessTokenException(final LocalizableMessage message, final Exception cause) {
        return new AccessTokenException(message.toString(), cause);
    }

    /**
     * Returns the DN which a bind DN template designates for a user name.
     *
     * @param dnTemplate
     *         The template, with {@code %s} in place of the user name. A template which is just {@code %s} takes the
     *         user name as the DN; otherwise the user name is escaped as an attribute value.
     * @param schema
     *         The schema used to parse the DN.
     * @param username
     *         The user name.
     * @return The DN.
     * @throws LdapException
     *         With {@link ResultCode#INVALID_CREDENTIALS} if the result is not a valid DN.
     */
    static DN formatBindDn(final String dnTemplate, final Schema schema, final String username)
            throws LdapException {
        try {
            return "%s".equals(dnTemplate)
                    ? DN.valueOf(username, schema)
                    : DN.format(dnTemplate, schema, username);
        } catch (final LocalizedIllegalArgumentException e) {
            throw LdapException.newLdapException(ResultCode.INVALID_CREDENTIALS, e.getMessageObject(), e);
        }
    }

    static Runnable close(final AtomicReference<? extends Closeable> holder) {
        return new Runnable() {
            @Override
            public void run() {
                closeSilently(holder.get());
            }
        };
    }

    static AsyncFunction<LdapException, Response, NeverThrowsException> handleConnectionFailure() {
        return HANDLE_CONNECTION_FAILURE;
    }

    static Promise<Response, NeverThrowsException> asErrorResponse(final Throwable t) {
        final ResourceException e = asResourceException(t);
        final Response response = new Response(Status.valueOf(e.getCode())).setEntity(e.toJsonValue() .getObject());
        if (response.getStatus() == Status.UNAUTHORIZED) {
            response.getHeaders().put("WWW-Authenticate", "Basic");
        }
        return Promises.newResultPromise(response);
    }
}
