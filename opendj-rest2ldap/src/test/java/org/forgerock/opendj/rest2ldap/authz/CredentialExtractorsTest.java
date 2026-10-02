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

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.HTTP_BASIC_AUTH_HEADER;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.httpBasicExtractor;
import static org.forgerock.opendj.rest2ldap.authz.CredentialExtractors.newCustomHeaderExtractor;

import org.forgerock.http.protocol.Headers;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Pair;
import org.forgerock.util.encode.Base64;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class CredentialExtractorsTest extends ForgeRockTestCase {

    @Test
    public void testBasicCanExtractValidCredentials() {
        final Headers headers = new Headers();
        headers.put(HTTP_BASIC_AUTH_HEADER, "basic " + Base64.encode("foo:bar".getBytes()));
        assertThat(httpBasicExtractor().apply(headers)).isEqualTo(Pair.of("foo", "bar"));
    }

    @DataProvider
    public Object[][] validBasicCredentials() {
        // @formatter:off
        return new Object[][] {
            // RFC 7617 section 2 forbids a colon only in the user-id: the password is everything after the first one.
            { "bjensen:se:cret", "bjensen", "se:cret" },
            { "bjensen::", "bjensen", ":" },
            { "uid=bjensen,ou=People,dc=example,dc=com:secret", "uid=bjensen,ou=People,dc=example,dc=com", "secret" },
            // An empty password is a well-formed credential; the filter, not the parser, refuses it.
            { "bjensen:", "bjensen", "" },
        };
        // @formatter:on
    }

    @Test(dataProvider = "validBasicCredentials")
    public void testBasicSplitsAtTheFirstColon(final String credentials, final String username,
            final String password) {
        final Headers headers = new Headers();
        headers.put(HTTP_BASIC_AUTH_HEADER, "Basic " + Base64.encode(credentials.getBytes()));
        assertThat(httpBasicExtractor().apply(headers)).isEqualTo(Pair.of(username, password));
    }

    @DataProvider
    public Object[][] invalidBasicHeaders() {
        // @formatter:off
        return new Object[][] {
            { "*invalid*" },
            { "Basic " + Base64.encode("bjensen".getBytes()) },
            { "Basic !!!" },
            { "Basic" },
        };
        // @formatter:on
    }

    @Test(dataProvider = "invalidBasicHeaders")
    public void testBasicReturnNullOnInvalidCredentials(final String header) {
        final Headers headers = new Headers();
        headers.put(HTTP_BASIC_AUTH_HEADER, header);
        assertThat(httpBasicExtractor().apply(headers)).isNull();
    }

    @Test
    public void testBasicReturnNullOnMissingCredentials() {
        assertThat(httpBasicExtractor().apply(new Headers())).isNull();
    }

    @Test
    public void testCustomCanExtractValidCredentials() {
        final Headers headers = new Headers();
        headers.put("X-user", "foo");
        headers.put("X-password", "bar");
        assertThat(newCustomHeaderExtractor("X-user", "X-password").apply(headers)).isEqualTo(Pair.of("foo", "bar"));
    }

    @Test
    public void testCustomFallbackOnBasicIfMissingCustomCredentials() {
        final Headers headers = new Headers();
        headers.put(HTTP_BASIC_AUTH_HEADER, "basic " + Base64.encode("foo:bar".getBytes()));
        assertThat(newCustomHeaderExtractor("X-user", "X-password").apply(headers)).isEqualTo(Pair.of("foo", "bar"));
    }

    @Test
    public void testCustomReturnNullOnMissingCredentials() {
        assertThat(httpBasicExtractor().apply(new Headers())).isNull();
    }
}
