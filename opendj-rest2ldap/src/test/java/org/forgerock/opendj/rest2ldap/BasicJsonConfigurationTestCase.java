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
package org.forgerock.opendj.rest2ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newSuccessfulLdapPromise;
import static org.forgerock.opendj.rest2ldap.TestUtils.parseJson;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.forgerock.http.protocol.Headers;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.PlainSASLBindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategy;
import org.forgerock.services.context.RootContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Tests how the "basic" authorization configuration turns the HTTP Basic user name into an LDAP name. */
@Test
@SuppressWarnings("javadoc")
public final class BasicJsonConfigurationTestCase extends ForgeRockTestCase {
    private static final String USER_DN = "uid=bjensen,ou=People,dc=example,dc=com";

    private Rest2LdapHttpApplication fakeApp;
    private Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        connection = mock(Connection.class);
        when(connection.bindAsync(any(BindRequest.class)))
                .thenReturn(newSuccessfulLdapPromise(Responses.newBindResult(ResultCode.SUCCESS)));
        when(connection.searchSingleEntryAsync(any(SearchRequest.class)))
                .thenReturn(newSuccessfulLdapPromise(Responses.newSearchResultEntry(USER_DN)));
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.getConnectionAsync())
                .thenReturn(Promises.<Connection, LdapException> newResultPromise(connection));

        fakeApp = spy(Rest2LdapHttpApplication.class);
        doReturn(factory).when(fakeApp).getConnectionFactory(anyString());
    }

    @Test
    public void testSimpleDefaultTemplateTakesTheUserNameAsTheBindDn() throws Exception {
        authenticate("{'bind': 'simple', 'simple': {}}", USER_DN);

        assertThat(bindRequest(SimpleBindRequest.class).getName()).isEqualTo(USER_DN);
    }

    @Test
    public void testSimpleTemplateKeepsPercentLiterally() throws Exception {
        authenticate("{'bind': 'simple', 'simple': {'bindDnTemplate': 'uid={username},o=100%,dc=example,dc=com'}}",
                "bjensen");

        assertThat(bindRequest(SimpleBindRequest.class).getName()).isEqualTo("uid=bjensen,o=100%,dc=example,dc=com");
    }

    @Test
    public void testSaslPlainDefaultTemplateIsTheUserId() throws Exception {
        authenticate("{'bind': 'sasl-plain', 'sasl-plain': {}}", "bjensen");

        assertThat(bindRequest(PlainSASLBindRequest.class).getAuthenticationID()).isEqualTo("u:bjensen");
    }

    @Test
    public void testSaslPlainDnTemplateTakesTheUserNameAsTheBindDn() throws Exception {
        authenticate("{'bind': 'sasl-plain', 'sasl-plain': {'authzIdTemplate': 'dn:{username}'}}", USER_DN);

        assertThat(bindRequest(PlainSASLBindRequest.class).getAuthenticationID()).isEqualTo("dn:" + USER_DN);
    }

    @Test
    public void testSaslPlainTemplateKeepsPercentLiterally() throws Exception {
        authenticate("{'bind': 'sasl-plain', 'sasl-plain': {'authzIdTemplate': 'u:{username}%example'}}", "bjensen");

        assertThat(bindRequest(PlainSASLBindRequest.class).getAuthenticationID()).isEqualTo("u:bjensen%example");
    }

    @Test
    public void testSearchFilterTemplateKeepsPercentLiterally() throws Exception {
        authenticate("{'bind': 'search', 'search': {'baseDn': 'dc=example,dc=com', 'scope': 'sub',"
                + " 'filterTemplate': '(&(uid={username})(description=100%))'}}", "bjensen");

        final ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(connection).searchSingleEntryAsync(request.capture());
        assertThat(request.getValue().getFilter().toString()).isEqualTo("(&(uid=bjensen)(description=100%))");
        assertThat(bindRequest(SimpleBindRequest.class).getName()).isEqualTo(USER_DN);
    }

    @SuppressWarnings("unchecked")
    private void authenticate(final String basicConfig, final String username) throws Exception {
        final ArgumentCaptor<AuthenticationStrategy> strategy = ArgumentCaptor.forClass(AuthenticationStrategy.class);
        doReturn(null).when(fakeApp).newBasicAuthenticationFilter(strategy.capture(),
                (Function<Headers, Pair<String, String>, NeverThrowsException>) any(Function.class));
        fakeApp.buildBasicFilter(parseJson(basicConfig));
        strategy.getValue().authenticate(username, "secret", new RootContext()).getOrThrow();
    }

    private <T extends BindRequest> T bindRequest(final Class<T> type) {
        final ArgumentCaptor<BindRequest> request = ArgumentCaptor.forClass(BindRequest.class);
        verify(connection).bindAsync(request.capture());
        return type.cast(request.getValue());
    }
}
