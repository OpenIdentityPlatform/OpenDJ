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

import javax.security.auth.Subject;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests GSSAPI SASL Bind requests.
 */
@SuppressWarnings("javadoc")
public class GSSAPISASLBindRequestTestCase extends BindRequestTestCase {

    private static final GSSAPISASLBindRequest NEW_GSSAPISASL_BIND_REQUEST = Requests.newGSSAPISASLBindRequest("id1",
            EMPTY_BYTES);
    private static final GSSAPISASLBindRequest NEW_GSSAPISASL_BIND_REQUEST2 = Requests.newGSSAPISASLBindRequest("id2",
            getBytes("password"));

    @DataProvider(name = "GSSAPISASLBindRequests")
    private Object[][] getGSSAPISASLBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected GSSAPISASLBindRequest[] newInstance() {
        return new GSSAPISASLBindRequest[] {
            NEW_GSSAPISASL_BIND_REQUEST,
            NEW_GSSAPISASL_BIND_REQUEST2
        };
    }

    @Test(enabled = false)
    public void testBindClient(BindRequest request) throws Exception {
        // Should setup a test krb server...
        super.testBindClient(request);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests")
    public void testQOP(GSSAPISASLBindRequest request) throws Exception {
        String[] options =
                new String[] { GSSAPISASLBindRequest.QOP_AUTH, GSSAPISASLBindRequest.QOP_AUTH_INT,
                    GSSAPISASLBindRequest.QOP_AUTH_CONF };
        request.addQOP(options);
        assertEquals(request.getQOPs(), Arrays.asList(options));
    }

    @Test(dataProvider = "GSSAPISASLBindRequests")
    public void testServerAuth(GSSAPISASLBindRequest request) throws Exception {
        request.setServerAuth(true);
        assertEquals(request.isServerAuth(), true);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests")
    public void testSendBuffer(GSSAPISASLBindRequest request) throws Exception {
        request.setMaxSendBufferSize(512);
        assertEquals(request.getMaxSendBufferSize(), 512);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests")
    public void testRecieveBuffer(GSSAPISASLBindRequest request) throws Exception {
        request.setMaxReceiveBufferSize(512);
        assertEquals(request.getMaxReceiveBufferSize(), 512);
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfGSSAPISASLBindRequest((GSSAPISASLBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableGSSAPISASLBindRequest((GSSAPISASLBindRequest) original);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests")
    public void testModifiableRequest(final GSSAPISASLBindRequest original) {
        final String authID = "u:user.0";
        final String azID = "dn:user.0,dc=com";
        final String password = "pass";
        final int maxRBufferSize = 1024;
        final int maxSBufferSize = 2048;
        final String realm = "my.domain.com";
        final Subject subject = new Subject();
        subject.setReadOnly();

        final GSSAPISASLBindRequest copy = (GSSAPISASLBindRequest) copyOf(original);
        copy.setAuthenticationID(authID);
        copy.setAuthorizationID(azID);
        copy.setPassword(password.toCharArray());
        copy.setMaxReceiveBufferSize(maxRBufferSize);
        copy.setMaxSendBufferSize(maxSBufferSize);
        copy.setRealm(realm);
        copy.setServerAuth(true);
        copy.setSubject(subject);

        assertThat(copy.getAuthenticationID()).isEqualTo(authID);
        assertThat(copy.getAuthorizationID()).isEqualTo(azID);
        assertThat(copy.getMaxReceiveBufferSize()).isEqualTo(maxRBufferSize);
        assertThat(copy.getMaxSendBufferSize()).isEqualTo(maxSBufferSize);
        assertThat(copy.getRealm()).isEqualTo(realm);
        assertThat(original.getRealm()).isNotEqualTo(realm);
        assertThat(copy.getSubject()).isEqualTo(subject);
        assertThat(original.getSubject()).isNotEqualTo(subject);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests")
    public void testUnmodifiableRequest(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthorizationID()).isEqualTo(original.getAuthorizationID());
        assertThat(unmodifiable.getMaxReceiveBufferSize()).isEqualTo(original.getMaxReceiveBufferSize());
        assertThat(unmodifiable.getMaxSendBufferSize()).isEqualTo(original.getMaxSendBufferSize());
        assertThat(unmodifiable.getRealm()).isEqualTo(original.getRealm());
        assertThat(unmodifiable.getSubject()).isEqualTo(original.getSubject());
        assertThat(unmodifiable.getSASLMechanism()).isEqualTo(original.getSASLMechanism());
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddAdditionalAuthParam(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.addAdditionalAuthParam("id2", "value");
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddQOP(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.addQOP(GSSAPISASLBindRequest.QOP_AUTH, GSSAPISASLBindRequest.QOP_AUTH_CONF);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthenticationID(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthenticationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthorizationID(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthorizationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetMaxReceiveBufferSize(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setMaxReceiveBufferSize(1048);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetMaxSendBufferSize(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setMaxSendBufferSize(1048);
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".getBytes());
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword2(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".toCharArray());
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetRealm(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setRealm("my.domain");
    }

    @Test(dataProvider = "GSSAPISASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetServerAuth(final GSSAPISASLBindRequest original) {
        final GSSAPISASLBindRequest unmodifiable = (GSSAPISASLBindRequest) unmodifiableOf(original);
        unmodifiable.setServerAuth(true);
    }
}
