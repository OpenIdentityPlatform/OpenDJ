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
import static org.testng.Assert.assertEquals;

import java.util.Arrays;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests Digest MD5 SASL requests.
 */
@SuppressWarnings("javadoc")
public class DigestMD5SASLBindRequestTestCase extends BindRequestTestCase {
    private static final DigestMD5SASLBindRequest NEW_DIGEST_MD5SASL_BIND_REQUEST = Requests
            .newDigestMD5SASLBindRequest("id1", EMPTY_BYTES);
    private static final DigestMD5SASLBindRequest NEW_DIGEST_MD5SASL_BIND_REQUEST2 = Requests
            .newDigestMD5SASLBindRequest("id2", getBytes("password"));

    @DataProvider(name = "DigestMD5SASLBindRequests")
    private Object[][] getDigestMD5SASLBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected DigestMD5SASLBindRequest[] newInstance() {
        return new DigestMD5SASLBindRequest[] {
            NEW_DIGEST_MD5SASL_BIND_REQUEST,
            NEW_DIGEST_MD5SASL_BIND_REQUEST2
        };
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testQOP(DigestMD5SASLBindRequest request) throws Exception {
        String[] options =
                new String[] { DigestMD5SASLBindRequest.QOP_AUTH,
                    DigestMD5SASLBindRequest.QOP_AUTH_INT, DigestMD5SASLBindRequest.QOP_AUTH_CONF };
        request.addQOP(options);
        assertEquals(request.getQOPs(), Arrays.asList(options));
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testStrength(DigestMD5SASLBindRequest request) throws Exception {
        request.setCipher(DigestMD5SASLBindRequest.CIPHER_3DES);
        assertEquals(request.getCipher(), DigestMD5SASLBindRequest.CIPHER_3DES);

        request.setCipher(DigestMD5SASLBindRequest.CIPHER_MEDIUM);
        assertEquals(request.getCipher(), DigestMD5SASLBindRequest.CIPHER_MEDIUM);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testServerAuth(DigestMD5SASLBindRequest request) throws Exception {
        request.setServerAuth(true);
        assertEquals(request.isServerAuth(), true);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testSendBuffer(DigestMD5SASLBindRequest request) throws Exception {
        request.setMaxSendBufferSize(1024);
        assertEquals(request.getMaxSendBufferSize(), 1024);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testRecieveBuffer(DigestMD5SASLBindRequest request) throws Exception {
        request.setMaxReceiveBufferSize(1024);
        assertEquals(request.getMaxReceiveBufferSize(), 1024);
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfDigestMD5SASLBindRequest((DigestMD5SASLBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableDigestMD5SASLBindRequest((DigestMD5SASLBindRequest) original);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testModifiableRequest(final DigestMD5SASLBindRequest original) {
        final String authID = "u:user.0";
        final String azID = "dn:user.0,dc=com";
        final String cipher = DigestMD5SASLBindRequest.CIPHER_LOW;
        final String password = "pass";
        final int maxRBufferSize = 1024;
        final int maxSBufferSize = 2048;
        final String realm = "my.domain.com";

        final DigestMD5SASLBindRequest copy = (DigestMD5SASLBindRequest) copyOf(original);
        copy.setAuthenticationID(authID);
        copy.setAuthorizationID(azID);
        copy.setCipher(cipher);
        copy.setPassword(password.toCharArray());
        copy.setMaxReceiveBufferSize(maxRBufferSize);
        copy.setMaxSendBufferSize(maxSBufferSize);
        copy.setRealm(realm);
        copy.setServerAuth(true);

        assertThat(copy.getAuthenticationID()).isEqualTo(authID);
        assertThat(copy.getAuthorizationID()).isEqualTo(azID);
        assertThat(copy.getCipher()).isEqualTo(cipher);
        assertThat(copy.getMaxReceiveBufferSize()).isEqualTo(maxRBufferSize);
        assertThat(copy.getMaxSendBufferSize()).isEqualTo(maxSBufferSize);
        assertThat(copy.getRealm()).isEqualTo(realm);
        assertThat(original.getRealm()).isNotEqualTo(realm);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests")
    public void testUnmodifiableRequest(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthorizationID()).isEqualTo(original.getAuthorizationID());
        assertThat(unmodifiable.getCipher()).isEqualTo(original.getCipher());
        assertThat(unmodifiable.getMaxReceiveBufferSize()).isEqualTo(original.getMaxReceiveBufferSize());
        assertThat(unmodifiable.getMaxSendBufferSize()).isEqualTo(original.getMaxSendBufferSize());
        assertThat(unmodifiable.getRealm()).isEqualTo(original.getRealm());
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddAdditionalAuthParam(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.addAdditionalAuthParam("id2", "value");
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddQOP(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.addQOP(DigestMD5SASLBindRequest.QOP_AUTH, DigestMD5SASLBindRequest.QOP_AUTH_CONF);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthenticationID(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthenticationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthorizationID(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthorizationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetCipher(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setCipher(DigestMD5SASLBindRequest.CIPHER_LOW);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetMaxReceiveBufferSize(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setMaxReceiveBufferSize(1048);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetMaxSendBufferSize(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setMaxSendBufferSize(1048);
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".getBytes());
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword2(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("passworda".toCharArray());
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetRealm(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setRealm("my.domain");
    }

    @Test(dataProvider = "DigestMD5SASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetServerAuth(final DigestMD5SASLBindRequest original) {
        final DigestMD5SASLBindRequest unmodifiable = (DigestMD5SASLBindRequest) unmodifiableOf(original);
        unmodifiable.setServerAuth(true);
    }
}
