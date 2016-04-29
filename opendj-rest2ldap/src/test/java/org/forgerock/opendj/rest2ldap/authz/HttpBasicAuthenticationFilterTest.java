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

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.rest2ldap.authz.HttpBasicAuthenticationFilter.HttpBasicExtractor.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.rest2ldap.authz.HttpBasicAuthenticationFilter.CustomHeaderExtractor;
import org.forgerock.opendj.rest2ldap.authz.HttpBasicAuthenticationFilter.HttpBasicExtractor;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Pair;
import org.forgerock.util.encode.Base64;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class HttpBasicAuthenticationFilterTest extends ForgeRockTestCase {

    private static final String USERNAME = "Aladdin";
    private static final String PASSWORD = "open sesame";
    private static final String BASE64_USERPASS = Base64.encode((USERNAME + ":" + PASSWORD).getBytes());

    @DataProvider(name = "Invalid HTTP basic auth strings")
    public Object[][] getInvalidHttpBasicAuthStrings() {
        return new Object[][] { { null }, { "bla" }, { "basic " + Base64.encode("la:bli:blu".getBytes()) } };
    }

    @Test(dataProvider = "Invalid HTTP basic auth strings")
    public void parseUsernamePasswordFromInvalidAuthZHeader(String authZHeader) throws Exception {
        final AuthenticationStrategy strategy = mock(AuthenticationStrategy.class);
        final HttpBasicAuthenticationFilter filter =
                new HttpBasicAuthenticationFilter(strategy, HttpBasicExtractor.INSTANCE, false);

        final Request req = new Request();
        req.getHeaders().put(HTTP_BASIC_AUTH_HEADER, authZHeader);

        assertThat(filter.canApplyFilter(null, req)).isFalse();
    }

    @DataProvider(name = "Valid HTTP basic auth strings")
    public Object[][] getValidHttpBasicAuthStrings() {
        return new Object[][] { { "basic " + BASE64_USERPASS }, { "Basic " + BASE64_USERPASS } };
    }

    @Test(dataProvider = "Valid HTTP basic auth strings")
    public void parseUsernamePasswordFromValidAuthZHeader(String authZHeader) throws Exception {
        final Headers headers = new Headers();
        headers.put(HTTP_BASIC_AUTH_HEADER, authZHeader);
        assertThat(HttpBasicExtractor.INSTANCE.apply(headers)).isEqualTo(Pair.of(USERNAME, PASSWORD));
    }

    @Test
    public void sendUnauthorizedResponseWithHttpBasicAuthWillChallengeUserAgent() throws Exception {
        final AuthenticationStrategy failureStrategy = mock(AuthenticationStrategy.class);
        when(failureStrategy
                .authenticate(any(String.class), any(String.class), any(Context.class), any(AtomicReference.class)))
                .thenReturn(Promises.<SecurityContext, LdapException>newResultPromise(null));

        final HttpBasicAuthenticationFilter filter =
                new HttpBasicAuthenticationFilter(failureStrategy, HttpBasicExtractor.INSTANCE, false);

        final Response response = filter.filter(null, new Request(), mock(Handler.class)).get();
        verifyUnauthorizedOutputMessage(response);
    }

    private void verifyUnauthorizedOutputMessage(Response response) throws IOException {
        final SoftAssertions softly = new SoftAssertions();
        softly.assertThat(response.getStatus().getCode()).isEqualTo(401);
        softly.assertThat(response.getStatus().getReasonPhrase()).isEqualTo("Unauthorized");
        softly.assertThat(response.getEntity().getJson().toString())
                .isEqualTo("{code=401, reason=Unauthorized, message=Invalid Credentials}");
        softly.assertAll();
    }

    @Test
    public void extractUsernamePasswordHttpBasicAuthWillAcceptUserAgent() throws Exception {
        final Headers headers = new Headers();
        headers.add(HTTP_BASIC_AUTH_HEADER, "Basic " + BASE64_USERPASS);
        assertThat(HttpBasicExtractor.INSTANCE.apply(headers)).isEqualTo(Pair.of(USERNAME, PASSWORD));
    }

    @Test
    public void extractUsernamePasswordCustomHeaders() throws Exception {
        final String customHeaderUsername = "X-OpenIDM-Username";
        final String customHeaderPassword = "X-OpenIDM-Password";
        CustomHeaderExtractor cha = new CustomHeaderExtractor(customHeaderUsername, customHeaderPassword);
        Headers headers = new Headers();
        headers.add(customHeaderUsername, USERNAME);
        headers.add(customHeaderPassword, PASSWORD);

        assertThat(cha.apply(headers)).isEqualTo(Pair.of(USERNAME, PASSWORD));
    }

}
