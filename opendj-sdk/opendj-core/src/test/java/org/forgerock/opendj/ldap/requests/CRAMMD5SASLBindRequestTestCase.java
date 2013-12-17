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
 * Tests CRAM MD5 SASL bind requests.
 */
@SuppressWarnings("javadoc")
public class CRAMMD5SASLBindRequestTestCase extends BindRequestTestCase {

    private static final CRAMMD5SASLBindRequest NEW_CRAMMD5SASL_BIND_REQUEST = Requests.newCRAMMD5SASLBindRequest(
            "id1", EMPTY_BYTES);
    private static final CRAMMD5SASLBindRequest NEW_CRAMMD5SASL_BIND_REQUEST2 = Requests.newCRAMMD5SASLBindRequest(
            "id2", getBytes("test"));

    @DataProvider(name = "CRAMMD5SASLBindRequests")
    private Object[][] getCRAMMD5SASLBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected CRAMMD5SASLBindRequest[] newInstance() {
        return new CRAMMD5SASLBindRequest[] {
            NEW_CRAMMD5SASL_BIND_REQUEST,
            NEW_CRAMMD5SASL_BIND_REQUEST2
        };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfCRAMMD5SASLBindRequest((CRAMMD5SASLBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableCRAMMD5SASLBindRequest((CRAMMD5SASLBindRequest) original);
    }

    @Test(dataProvider = "CRAMMD5SASLBindRequests")
    public void testModifiableRequest(final CRAMMD5SASLBindRequest original) {
        final String authId = "newAuthId";
        final String pwd = "pass";
        final String pwd2 = "pass2";

        final CRAMMD5SASLBindRequest copy = (CRAMMD5SASLBindRequest) copyOf(original);
        copy.setAuthenticationID(authId);
        assertThat(copy.getAuthenticationID()).isEqualTo(authId);
        assertThat(original.getAuthenticationID()).isNotEqualTo(authId);

        copy.setPassword(pwd.toCharArray());
        assertThat(copy.getPassword()).isEqualTo(pwd.getBytes());
        assertThat(original.getPassword()).isNotEqualTo(pwd.getBytes());

        copy.setPassword(pwd2.getBytes());
        assertThat(copy.getPassword()).isEqualTo(pwd2.getBytes());
        assertThat(original.getPassword()).isNotEqualTo(pwd2.getBytes());
    }

    @Test(dataProvider = "CRAMMD5SASLBindRequests")
    public void testUnmodifiableRequest(final CRAMMD5SASLBindRequest original) {
        final CRAMMD5SASLBindRequest unmodifiable = (CRAMMD5SASLBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthenticationID()).isEqualTo(original.getAuthenticationID());
        assertThat(unmodifiable.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(unmodifiable.getName()).isEqualTo(original.getName());
        assertThat(unmodifiable.getPassword()).isEqualTo(original.getPassword());
        assertThat(unmodifiable.getSASLMechanism()).isEqualTo(original.getSASLMechanism());
    }

    @Test(dataProvider = "CRAMMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthenticationId(final CRAMMD5SASLBindRequest original) {
        final CRAMMD5SASLBindRequest unmodifiable = (CRAMMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthenticationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "CRAMMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword(final CRAMMD5SASLBindRequest original) {
        final CRAMMD5SASLBindRequest unmodifiable = (CRAMMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".getBytes());
    }

    @Test(dataProvider = "CRAMMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword2(final CRAMMD5SASLBindRequest original) {
        final CRAMMD5SASLBindRequest unmodifiable = (CRAMMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".toCharArray());
    }
}
