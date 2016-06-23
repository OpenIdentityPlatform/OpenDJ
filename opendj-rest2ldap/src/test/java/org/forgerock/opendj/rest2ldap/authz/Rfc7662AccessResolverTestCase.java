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
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newRfc7662AccessTokenResolver;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.forgerock.openig.oauth2.AccessTokenInfo;
import org.forgerock.openig.oauth2.AccessTokenException;
import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.http.Handler;
import org.forgerock.http.MutableUri;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("javadoc")
public final class Rfc7662AccessResolverTestCase extends ForgeRockTestCase {

    @Mock
    private Handler client;

    @Captor
    private ArgumentCaptor<Request> requestCapture;

    private AccessTokenResolver resolver;

    private Context context;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        resolver = newRfc7662AccessTokenResolver(
                client, new URI("http://www.example.com/introspect"), "client_id", "client_secret");
        context = new AttributesContext(new RootContext());
    }

    @Test
    public void testBasicUseCase() throws Exception {
        final AccessTokenInfo token = createFakeTokenResponse(
                "active-access-token", true, "  read    write dolphin ", 1461057).get();

        assertThat(token.getExpiresAt()).isEqualTo(1461057000);
        assertThat(token.getScopes()).containsOnly("dolphin", "read", "write");
        assertThat(token.getInfo().get("an info")).isEqualTo("info value");
    }

    @Test(expectedExceptions = ExecutionException.class,
          expectedExceptionsMessageRegExp =
                  ".*AccessTokenException.*Access token returned by authorization server is not currently active.*")
    public void testInactiveTokenInRefused() throws Exception {
        createFakeTokenResponse("inactive-access-token", false, " read  write dolphin ", 1461057).get();
    }

    @Test(expectedExceptions = ExecutionException.class,
          expectedExceptionsMessageRegExp = ".*AccessTokenException.*Authorization server returned an error:.*")
    public void testErrorResponse() throws Exception {
        when(client.handle(eq(context), any(Request.class))).thenReturn(
                Response.newResponsePromise(new Response().setStatus(Status.UNAUTHORIZED)));
        resolver.resolve(context, "fake-access-token").get();
    }

    private Promise<AccessTokenInfo, AccessTokenException> createFakeTokenResponse(
            final String token, final boolean active, final String scopes, final long expiresAt) throws Exception {
        final Map<String, Object> jsonResponse = new HashMap<>();
        jsonResponse.put("active", active);
        jsonResponse.put("scope", scopes);
        jsonResponse.put("exp", expiresAt);
        jsonResponse.put("an info", "info value");

        when(client.handle(eq(context), any(Request.class))).thenReturn(
                Response.newResponsePromise(new Response().setStatus(Status.OK).setEntity(jsonResponse)));
        final Promise<AccessTokenInfo, AccessTokenException> promise = resolver.resolve(context, token);
        ensureRequestIsCorrect(token);
        return promise;
    }

    private void ensureRequestIsCorrect(final String token) throws Exception {
        verify(client).handle(eq(context), requestCapture.capture());
        final Request request = requestCapture.getValue();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getUri()).isEqualTo(new MutableUri("http://www.example.com/introspect"));
        assertThat(request.getForm().get("token").get(0)).isEqualTo(token);
        assertThat(request.getForm().get("token_type_hint").get(0)).isEqualTo("access_token");
        assertThat(request.getHeaders().get("Accept").getFirstValue()).isEqualTo("application/json");
        final String credentials = request.getHeaders().getFirst("Authorization").substring("basic".length() + 1);
        assertThat(new String(Base64.decode(credentials)).split(":")).containsExactly("client_id", "client_secret");
    }
}
