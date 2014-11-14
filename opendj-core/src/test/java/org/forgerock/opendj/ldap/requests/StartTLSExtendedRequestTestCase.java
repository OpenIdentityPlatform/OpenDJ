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
 *      Copyright 2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.assertThat;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests Start TLS Extended Requests.
 */
@SuppressWarnings("javadoc")
public class StartTLSExtendedRequestTestCase extends RequestsTestCase {

    @DataProvider(name = "StartTLSExtendedRequests")
    private Object[][] getPlainSASLBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected StartTLSExtendedRequest[] newInstance() {
        try {
            return new StartTLSExtendedRequest[] { Requests.newStartTLSExtendedRequest(SSLContext.getDefault()) };
        } catch (NoSuchAlgorithmException e) {
            // nothing to do
        }
        return null;
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfStartTLSExtendedRequest((StartTLSExtendedRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableStartTLSExtendedRequest((StartTLSExtendedRequest) original);
    }

    @Test(dataProvider = "StartTLSExtendedRequests")
    public void testModifiableRequest(final StartTLSExtendedRequest original) throws NoSuchAlgorithmException {

        final StartTLSExtendedRequest copy = (StartTLSExtendedRequest) copyOf(original);
        copy.setSSLContext(SSLContext.getInstance("TLS"));

        assertThat(copy.getSSLContext().getProtocol()).isEqualTo("TLS");
        assertThat(original.getSSLContext().getProtocol()).isEqualTo("Default");
    }

    @Test(dataProvider = "StartTLSExtendedRequests")
    public void testUnmodifiableRequest(final StartTLSExtendedRequest original) {
        final StartTLSExtendedRequest unmodifiable = (StartTLSExtendedRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getSSLContext()).isEqualTo(original.getSSLContext());
        assertThat(original.getSSLContext().getProtocol()).isEqualTo("Default");
        assertThat(unmodifiable.getOID()).isEqualTo(original.getOID());
    }

    @Test(dataProvider = "StartTLSExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddEnabledCipherSuite(final StartTLSExtendedRequest original)
            throws NoSuchAlgorithmException {
        final StartTLSExtendedRequest unmodifiable = (StartTLSExtendedRequest) unmodifiableOf(original);
        unmodifiable.addEnabledCipherSuite("suite");
    }

    @Test(dataProvider = "StartTLSExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddEnabledProtocol(final StartTLSExtendedRequest original)
            throws NoSuchAlgorithmException {
        final StartTLSExtendedRequest unmodifiable = (StartTLSExtendedRequest) unmodifiableOf(original);
        unmodifiable.addEnabledProtocol("SSL", "TLS");
    }

    @Test(dataProvider = "StartTLSExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthenticationID(final StartTLSExtendedRequest original)
            throws NoSuchAlgorithmException {
        final StartTLSExtendedRequest unmodifiable = (StartTLSExtendedRequest) unmodifiableOf(original);
        unmodifiable.setSSLContext(SSLContext.getInstance("SSL"));
    }

    @Test(dataProvider = "StartTLSExtendedRequests")
    public void testModifiableRequestDecode(final StartTLSExtendedRequest original) throws DecodeException,
            NoSuchAlgorithmException {
        final GenericControl control = GenericControl.newControl("1.2.3".intern());

        final StartTLSExtendedRequest copy = (StartTLSExtendedRequest) copyOf(original);
        copy.addControl(control);
        copy.addEnabledCipherSuite("TLSv1");
        copy.addEnabledProtocol("TLS");
        copy.setSSLContext(SSLContext.getInstance("TLS"));
        assertThat(original.getControls().contains(control)).isFalse();
        assertThat(original.getEnabledCipherSuites().contains("TLSv1")).isFalse();
        assertThat(original.getSSLContext().getProtocol()).isNotEqualTo("TLS");
        assertThat(copy.getEnabledCipherSuites().contains("TLSv1")).isTrue();
        assertThat(copy.getSSLContext().getProtocol()).isEqualTo("TLS");

        try {
            final StartTLSExtendedRequest decoded = StartTLSExtendedRequest.DECODER.decodeExtendedRequest(copy,
                    new DecodeOptions());
            assertThat(decoded.getControls().contains(control)).isTrue();
        } catch (DecodeException e) {
            throw e;
        }
    }
}
