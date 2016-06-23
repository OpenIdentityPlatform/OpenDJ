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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newSuccessfulLdapPromise;
import static org.forgerock.opendj.rest2ldap.TestUtils.toValidJson;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newCtsAccessTokenResolver;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.forgerock.openig.oauth2.AccessTokenInfo;
import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.json.JsonPointer;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.spi.LdapPromises;
import org.forgerock.services.context.Context;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.promise.Promises;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("javadoc")
public class CtsAccessTokenResolverTestCase extends ForgeRockTestCase {

    @Mock
    private ConnectionFactory connectionFactory;

    @Mock
    private Connection connection;

    @Captor
    private ArgumentCaptor<SearchRequest> requestCapture;

    private final Context context = null;

    private AccessTokenResolver resolver;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        resolver = newCtsAccessTokenResolver(connectionFactory, "dc=cts,dc=example,dc=com");
    }

    @Test
    public void basicUseCase() throws Exception {
        final SearchResultEntry entry = mock(SearchResultEntry.class);
        final Attribute attribute = mock(Attribute.class);
        when(entry.getAttribute("coreTokenObject")).thenReturn(attribute);
        when(attribute.firstValueAsString()).thenReturn(toValidJson(
                  "{ 'tokenName': ['access_token'],"
                + "'scope': ['read', 'write', 'dolphin'],"
                + "'expireTime': ['1461057000'],"
                + "'an info': 'info value',"
                + "'user': {'id': 'bjensen'}}"));
        when(connection.searchSingleEntryAsync(requestCapture.capture()))
                .thenReturn(newSuccessfulLdapPromise(entry));
        when(connectionFactory.getConnectionAsync()).thenReturn(
                Promises.<Connection, LdapException> newResultPromise(connection));

        final String testToken = "test-token";
        final AccessTokenInfo accessToken = resolver.resolve(context, testToken).get();

        final SearchRequest request = requestCapture.getValue();
        assertThat(request.getName().toString()).isEqualTo("coreTokenId=test-token,dc=cts,dc=example,dc=com");
        assertThat(request.getScope()).isEqualTo(SearchScope.BASE_OBJECT);
        assertThat(request.getAttributes()).containsExactly("coreTokenObject");

        assertThat(accessToken.getExpiresAt()).isEqualTo(1461057000);
        assertThat(accessToken.getScopes()).containsOnly("dolphin", "read", "write");
        assertThat(accessToken.getInfo().get("an info")).isEqualTo("info value");
        assertThat(accessToken.asJsonValue().get(new JsonPointer("/user/id")).asString()).isEqualTo("bjensen");
    }

    @Test(expectedExceptions = ExecutionException.class,
          expectedExceptionsMessageRegExp = ".*Unable to find the token 'test-token' in the CTS because:.*")
    public void testConnectionFactoryError() throws Exception {
        when(connectionFactory.getConnectionAsync()).thenReturn(
                Promises.<Connection, LdapException> newExceptionPromise(
                        newLdapException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS)));
        resolver.resolve(context, "test-token").get();
    }

    @Test(expectedExceptions = ExecutionException.class,
            expectedExceptionsMessageRegExp = ".*Unable to find the token 'test-token' in the CTS because:.*")
    public void testConnectionSearchError() throws Exception {
        when(connectionFactory.getConnectionAsync()).thenReturn(
                Promises.<Connection, LdapException> newResultPromise(connection));
        when(connection.searchSingleEntryAsync(any(SearchRequest.class))).thenReturn(
                LdapPromises.<SearchResultEntry, LdapException> newFailedLdapPromise(
                        newLdapException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS)));
        resolver.resolve(context, "test-token").get();
    }

    @Test(expectedExceptions = ExecutionException.class, expectedExceptionsMessageRegExp =
                  ".*The token 'test-token' must be an access token, but it is a 'refresh_token'")
    public void testInvalidTokenType() throws Exception {
        final SearchResultEntry entry = mock(SearchResultEntry.class);
        final Attribute attribute = mock(Attribute.class);
        when(entry.getAttribute("coreTokenObject")).thenReturn(attribute);
        when(attribute.firstValueAsString()).thenReturn(toValidJson(
                "{ 'tokenName': ['refresh_token'],"
                + "'expireTime': ['1461057000']}"));
        when(connectionFactory.getConnectionAsync()).thenReturn(
                Promises.<Connection, LdapException> newResultPromise(connection));
        when(connection.searchSingleEntryAsync(any(SearchRequest.class))).thenReturn(newSuccessfulLdapPromise(entry));
        resolver.resolve(context, "test-token").get();
    }
}
