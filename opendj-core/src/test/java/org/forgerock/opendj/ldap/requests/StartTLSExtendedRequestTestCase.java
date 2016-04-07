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
 * Copyright 2013 ForgeRock AS.
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
