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
package org.forgerock.opendj.rest2ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.http.Handler;
import org.forgerock.http.oauth2.AccessTokenException;
import org.forgerock.http.oauth2.AccessTokenInfo;
import org.forgerock.http.oauth2.AccessTokenResolver;
import org.forgerock.http.protocol.Request;
import org.forgerock.json.JsonValue;
import org.forgerock.opendj.rest2ldap.authz.Authorization;
import org.forgerock.opendj.rest2ldap.authz.ConditionalFilters;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.time.TimeService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("javadoc")
public final class AuthorizationTestCase extends ForgeRockTestCase {

    private static final String TEST_TOKEN = "2YotnFZFEjr1zCsicMWpAA";

    private Context context;

    @Mock
    private AccessTokenResolver resolver;

    @Mock
    private Handler next;

    @Captor
    private ArgumentCaptor<Request> requestCapture;

    @Captor
    private ArgumentCaptor<Context> contextCapture;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @DataProvider
    private Object[][] basicUseCaseData() {
        return new Object[][] {
            { "dn:uid={user/id},dc=com", "uid=bjensen,dc=com", SecurityContext.AUTHZID_DN },
            { "u:{user/id}", "bjensen", SecurityContext.AUTHZID_ID }
        };
    }

    @Test(dataProvider = "basicUseCaseData")
    public void testBasicUseCase(final String template,
                                 final String resolvedTemplate,
                                 final String securityContextKey) throws Exception {
        newContextChain();
        prepareResolverResponse();

        final ConditionalFilters.ConditionalFilter filter = Authorization.newConditionalOAuth2ResourceServerFilter(
                "realm",
                new HashSet<>(Arrays.asList("read", "write", "dolphin")),
                resolver,
                template
        );
        final Request request = new Request();
        request.getHeaders().add("Authorization", "Bearer " + TEST_TOKEN);
        filter.getFilter().filter(context, request, next);

        verify(next).handle(contextCapture.capture(), requestCapture.capture());
        assertThat(requestCapture.getValue()).isEqualTo(request);

        final Context responseContext = contextCapture.getValue();
        assertThat(responseContext.asContext(AttributesContext.class).getAttributes().get("test")).isEqualTo("value");
        final String resolvedDn = responseContext.asContext(SecurityContext.class).getAuthorization()
                                                                                  .get(securityContextKey)
                                                                                  .toString();
        assertThat(resolvedDn).isEqualTo(resolvedTemplate);
    }

    private void prepareResolverResponse() {
        final Map<String, Object> jsonResponse = new HashMap<>();
        final Set<String> scopes = new HashSet<>(Arrays.asList("read", "write", "dolphin"));
        jsonResponse.put("access_token", TEST_TOKEN);
        jsonResponse.put("expires_in", 120000);
        jsonResponse.put("scope", scopes);
        jsonResponse.put("user", Collections.singletonMap("id", "bjensen"));
        final AccessTokenInfo token = new AccessTokenInfo(
                new JsonValue(jsonResponse), TEST_TOKEN, scopes, TimeService.SYSTEM.now() + 120000);
        when(resolver.resolve(eq(context), eq(TEST_TOKEN)))
                .thenReturn(Promises.<AccessTokenInfo, AccessTokenException> newResultPromise(token));
    }

    private void newContextChain() {
        context = new AttributesContext(new RootContext());
        context.asContext(AttributesContext.class).getAttributes().put("test", "value");
    }
}
