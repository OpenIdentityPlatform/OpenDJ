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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newSuccessfulLdapPromise;
import static org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategies.newSaslPlainStrategy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("javadoc")
public final class SaslPlainStrategyTest extends ForgeRockTestCase {
    private static final String USER_DN = "uid=bjensen,ou=People,dc=example,dc=com";

    private ConnectionFactory factory;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        connection = mock(Connection.class);
        when(connection.bindAsync(any(BindRequest.class)))
                .thenReturn(newSuccessfulLdapPromise(Responses.newBindResult(ResultCode.SUCCESS)));
        factory = mock(ConnectionFactory.class);
        when(factory.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException> newResultPromise(connection));
    }

    @DataProvider
    public Object[][] authcIdTemplates() {
        // @formatter:off
        // [template, "{username}" already replaced with "%s"] [user name] [expected SASL authentication ID]
        return new Object[][] {
            // The user name is the bind DN.
            { "dn:%s", USER_DN, "dn:" + USER_DN },
            { "dn:uid=%s,ou=People,dc=example,dc=com", "bjensen", "dn:" + USER_DN },
            // A user name inside a DN template stays one attribute value.
            { "dn:uid=%s,ou=People,dc=example,dc=com", "a,ou=x", "dn:uid=a\\,ou\\=x,ou=People,dc=example,dc=com" },
            { "u:%s", "bjensen", "u:bjensen" },
        };
        // @formatter:on
    }

    @Test(dataProvider = "authcIdTemplates")
    public void testAuthenticationIdSentToTheServer(final String template, final String username,
            final String expectedAuthcId) throws Exception {
        final SecurityContext context = newSaslPlainStrategy(factory, Schema.getDefaultSchema(), template)
                .authenticate(username, "secret", new RootContext()).getOrThrow();

        final ArgumentCaptor<BindRequest> request = ArgumentCaptor.forClass(BindRequest.class);
        verify(connection).bindAsync(request.capture());
        assertThat(((PlainSASLBindRequest) request.getValue()).getAuthenticationID()).isEqualTo(expectedAuthcId);
        assertThat(context.getAuthenticationId()).isEqualTo(expectedAuthcId);
    }

    /** A user name which is not a DN fails the returned promise and sends no bind request. */
    @Test
    public void testUserNameWhichIsNotADnFailsThePromise() throws Exception {
        final Promise<SecurityContext, LdapException> promise =
                newSaslPlainStrategy(factory, Schema.getDefaultSchema(), "dn:%s")
                        .authenticate("bjensen", "secret", new RootContext());
        try {
            promise.getOrThrow();
            fail("The authentication should have failed");
        } catch (final LdapException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.INVALID_CREDENTIALS);
        }
        verify(connection, never()).bindAsync(any(BindRequest.class));
    }
}
