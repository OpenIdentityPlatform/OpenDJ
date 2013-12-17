/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.EMPTY_BYTES;
import static com.forgerock.opendj.util.StaticUtils.getBytes;
import static org.fest.assertions.Assertions.assertThat;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests Plain SASL Bind requests.
 */
@SuppressWarnings("javadoc")
public class PlainSASLBindRequestTestCase extends RequestsTestCase {

    private static final PlainSASLBindRequest NEW_PLAIN_SASL_BIND_REQUEST = Requests.newPlainSASLBindRequest("id1",
            EMPTY_BYTES);
    private static final PlainSASLBindRequest NEW_PLAIN_SASL_BIND_REQUEST2 = Requests.newPlainSASLBindRequest("id2",
            getBytes("password"));

    @DataProvider(name = "plainSASLBindRequests")
    private Object[][] getPlainSASLBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected PlainSASLBindRequest[] newInstance() {
        return new PlainSASLBindRequest[] {
            NEW_PLAIN_SASL_BIND_REQUEST,
            NEW_PLAIN_SASL_BIND_REQUEST2
        };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfPlainSASLBindRequest((PlainSASLBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiablePlainSASLBindRequest((PlainSASLBindRequest) original);
    }

    @Test(dataProvider = "plainSASLBindRequests")
    public void testModifiableRequest(final PlainSASLBindRequest original) {
        final String authID = "u:user.0";
        final String azID = "dn:user.0,dc=com";
        final String password = "pass";

        final PlainSASLBindRequest copy = (PlainSASLBindRequest) copyOf(original);
        copy.setAuthenticationID(authID);
        copy.setAuthorizationID(azID);
        copy.setPassword(password.toCharArray());

        assertThat(copy.getAuthenticationID()).isEqualTo(authID);
        assertThat(copy.getAuthorizationID()).isEqualTo(azID);
        assertThat(original.getAuthenticationID()).isNotEqualTo(copy.getAuthenticationID());
        assertThat(original.getAuthorizationID()).isNotEqualTo(copy.getAuthorizationID());
    }

    @Test(dataProvider = "plainSASLBindRequests")
    public void testUnmodifiableRequest(final PlainSASLBindRequest original) {
        final PlainSASLBindRequest unmodifiable = (PlainSASLBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getName()).isEqualTo(original.getName());
        assertThat(unmodifiable.getAuthorizationID()).isEqualTo(original.getAuthorizationID());
        assertThat(unmodifiable.getAuthenticationID()).isEqualTo(original.getAuthenticationID());
        assertThat(unmodifiable.getSASLMechanism()).isEqualTo(original.getSASLMechanism());
    }

    @Test(dataProvider = "plainSASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthenticationID(final PlainSASLBindRequest original) {
        final PlainSASLBindRequest unmodifiable = (PlainSASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthenticationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "plainSASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthorizationID(final PlainSASLBindRequest original) {
        final PlainSASLBindRequest unmodifiable = (PlainSASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthorizationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "plainSASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword(final PlainSASLBindRequest original) {
        final PlainSASLBindRequest unmodifiable = (PlainSASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".toCharArray());
    }

    @Test(dataProvider = "plainSASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword2(final PlainSASLBindRequest original) {
        final PlainSASLBindRequest unmodifiable = (PlainSASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".getBytes());
    }
}
