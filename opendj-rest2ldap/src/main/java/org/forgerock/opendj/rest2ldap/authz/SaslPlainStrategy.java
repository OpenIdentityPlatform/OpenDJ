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

import static org.forgerock.opendj.ldap.requests.Requests.newPlainSASLBindRequest;
import static org.forgerock.services.context.SecurityContext.AUTHZID_DN;
import static org.forgerock.services.context.SecurityContext.AUTHZID_ID;
import static org.forgerock.util.Reject.checkNotNull;
import static org.forgerock.opendj.rest2ldap.authz.Utils.close;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityRequestControl;
import org.forgerock.opendj.ldap.controls.AuthorizationIdentityResponseControl;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;

/** Bind using a computed DN from a template and the current request/context. */
final class SaslPlainStrategy implements AuthenticationStrategy {

    private final ConnectionFactory connectionFactory;
    private final Function<String, String, LdapException> formatter;

    /**
     * Create a new SASLPlainStrategy.
     *
     * @param connectionFactory
     *            Factory used to get {@link Connection} receiving the sasl-bind requests
     * @param authcIdTemplate
     *            Authentication identity template containing a single %s which will be replaced by the authenticating
     *            user's name. (i.e: (u:%s)
     * @param schema
     *            Schema used to perform DN validation.
     * @throws NullPointerException
     *             If a parameter is null
     */
    public SaslPlainStrategy(final ConnectionFactory connectionFactory, final Schema schema,
                             final String authcIdTemplate) {
        this.connectionFactory = checkNotNull(connectionFactory, "connectionFactory cannot be null");
        checkNotNull(schema, "schema cannot be null");
        checkNotNull(authcIdTemplate, "authcIdTemplate cannot be null");
        if (authcIdTemplate.startsWith("dn:")) {
            formatter = new Function<String, String, LdapException>() {
                @Override
                public String apply(String value) throws LdapException {
                    try {
                        return DN.format(authcIdTemplate, schema, value).toString();
                    } catch (LocalizedIllegalArgumentException e) {
                        throw LdapException.newLdapException(ResultCode.INVALID_DN_SYNTAX, e.getMessageObject(), e);
                    }
                }
            };
        } else {
            formatter = new Function<String, String, LdapException>() {
                @Override
                public String apply(String value) throws LdapException {
                    return String.format(authcIdTemplate, value);
                }
            };
        }
    }

    @Override
    public Promise<SecurityContext, LdapException> authenticate(final String username, final String password,
            final Context parentContext) {
        final AtomicReference<Connection> connectionHolder = new AtomicReference<Connection>();
        return connectionFactory
                .getConnectionAsync()
                .thenAsync(new AsyncFunction<Connection, SecurityContext, LdapException>() {
                    @Override
                    public Promise<SecurityContext, LdapException> apply(Connection connection) throws LdapException {
                        connectionHolder.set(connection);
                        return doSaslPlainBind(connection, parentContext, username, password);
                    }
                }).thenFinally(close(connectionHolder));
    }

    private Promise<SecurityContext, LdapException> doSaslPlainBind(final Connection connection,
                                                                    final Context parentContext, final String authzId,
                                                                    final String password) throws LdapException {
        final String authcId = formatter.apply(authzId);
        return connection
                .bindAsync(newPlainSASLBindRequest(authcId, password.toCharArray())
                            .addControl(AuthorizationIdentityRequestControl.newControl(true)))
                .then(new Function<BindResult, SecurityContext, LdapException>() {
                    @Override
                    public SecurityContext apply(BindResult result) throws LdapException {
                        final Map<String, Object> authz = new LinkedHashMap<>(2);
                        try {
                            final AuthorizationIdentityResponseControl control =
                                    result.getControl(AuthorizationIdentityResponseControl.DECODER,
                                                      new DecodeOptions());
                            if (control != null) {
                                final String authzDN = control.getAuthorizationID();
                                if (authzDN.startsWith("dn:")) {
                                    authz.put(AUTHZID_DN, authzDN.substring(3));
                                }
                            }
                        } catch (DecodeException e) {
                            // Just ignore
                        }
                        authz.put(AUTHZID_ID, authzId);
                        return new SecurityContext(parentContext, authcId, authz);
                    }
                });
    }
}
