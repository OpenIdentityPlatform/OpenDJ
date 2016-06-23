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

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.rest2ldap.authz.Authorization.newConditionalHttpBasicAuthenticationFilter;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promises;
import org.testng.annotations.Test;

@Test
public class HttpBasicAuthenticationFilterTest extends ForgeRockTestCase {

    @SuppressWarnings("unchecked")
    @Test
    public void testRespondUnauthorizedIfCredentialMissing()
            throws InterruptedException, ExecutionException, IOException {
        final ConditionalFilter filter = newConditionalHttpBasicAuthenticationFilter(mock(AuthenticationStrategy.class),
                mock(Function.class));

        assertThat(filter.getCondition().canApplyFilter(new RootContext(), new Request())).isFalse();
        verifyUnauthorizedOutputMessage(
                filter.getFilter().filter(mock(Context.class), new Request(), mock(Handler.class)).get());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRespondUnauthorizedIfCredentialWrong()
            throws InterruptedException, ExecutionException, IOException {
        final Function<Headers, Pair<String, String>, NeverThrowsException> credentials = mock(Function.class);
        when(credentials.apply(any(Headers.class))).thenReturn(Pair.of("user", "password"));

        final AuthenticationStrategy authStrategy = mock(AuthenticationStrategy.class);
        when(authStrategy.authenticate(eq("user"), eq("password"), any(Context.class)))
                .thenReturn(Promises.<SecurityContext, LdapException> newExceptionPromise(
                        LdapException.newLdapException(ResultCode.INVALID_CREDENTIALS)));

        final Response response = new HttpBasicAuthenticationFilter(authStrategy, credentials)
                .filter(mock(Context.class), new Request(), mock(Handler.class)).get();

        verifyUnauthorizedOutputMessage(response);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testContinueProcessOnSuccessfullAuthentication() {
        final Function<Headers, Pair<String, String>, NeverThrowsException> credentials = mock(Function.class);
        when(credentials.apply(any(Headers.class))).thenReturn(Pair.of("user", "password"));

        final AuthenticationStrategy authStrategy = mock(AuthenticationStrategy.class);
        when(authStrategy.authenticate(eq("user"), eq("password"), any(Context.class)))
                .thenReturn(Promises.<SecurityContext, LdapException> newResultPromise(
                        new SecurityContext(new RootContext(), "user", Collections.<String, Object> emptyMap())));

        final Handler handler = mock(Handler.class);
        new HttpBasicAuthenticationFilter(authStrategy, credentials)
            .filter(mock(Context.class), new Request(), handler);

        verify(handler).handle(any(SecurityContext.class), any(Request.class));
    }

    private void verifyUnauthorizedOutputMessage(Response response) throws IOException {
        final SoftAssertions softly = new SoftAssertions();
        softly.assertThat(response.getStatus().getCode()).isEqualTo(401);
        softly.assertThat(response.getStatus().getReasonPhrase()).isEqualTo("Unauthorized");
        softly.assertThat(response.getEntity().getJson().toString())
                .isEqualTo("{code=401, reason=Unauthorized, message=Invalid Credentials}");
        softly.assertAll();
    }
}
