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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;

import com.forgerock.opendj.util.Validator;

/**
 * Start TLS extended request implementation.
 */
final class StartTLSExtendedRequestImpl extends
        AbstractExtendedRequest<StartTLSExtendedRequest, ExtendedResult> implements
        StartTLSExtendedRequest {
    static final class RequestDecoder implements
            ExtendedRequestDecoder<StartTLSExtendedRequest, ExtendedResult> {

        public StartTLSExtendedRequest decodeExtendedRequest(final ExtendedRequest<?> request,
                final DecodeOptions options) throws DecodeException {
            // TODO: Check the OID and that the value is not present.
            final StartTLSExtendedRequest newRequest = new StartTLSExtendedRequestImpl();
            for (final Control control : request.getControls()) {
                newRequest.addControl(control);
            }
            return newRequest;
        }
    }

    private static final class ResultDecoder extends AbstractExtendedResultDecoder<ExtendedResult> {
        public GenericExtendedResult newExtendedErrorResult(final ResultCode resultCode,
                final String matchedDN, final String diagnosticMessage) {
            return Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                    .setDiagnosticMessage(diagnosticMessage);
        }

        public ExtendedResult decodeExtendedResult(final ExtendedResult result,
                final DecodeOptions options) throws DecodeException {
            // TODO: Should we check oid is NOT null and matches but
            // value is null?
            return result;
        }
    }

    private SSLContext sslContext;

    /**
     * The list of cipher suite.
     */
    private List<String> enabledCipherSuites = new LinkedList<String>();

    /**
     * the list of protocols.
     */
    private List<String> enabledProtocols = new LinkedList<String>();

    // No need to expose this.
    private static final ExtendedResultDecoder<ExtendedResult> RESULT_DECODER = new ResultDecoder();

    StartTLSExtendedRequestImpl(final SSLContext sslContext) {
        Validator.ensureNotNull(sslContext);
        this.sslContext = sslContext;
    }

    /**
     * Creates a new startTLS extended request that is an exact copy of the
     * provided request.
     *
     * @param startTLSExtendedRequest
     *            The startTLS extended request to be copied.
     * @throws NullPointerException
     *             If {@code startTLSExtendedRequest} was {@code null} .
     */
    StartTLSExtendedRequestImpl(final StartTLSExtendedRequest startTLSExtendedRequest) {
        super(startTLSExtendedRequest);
        this.sslContext = startTLSExtendedRequest.getSSLContext();
        this.enabledCipherSuites.addAll(startTLSExtendedRequest.getEnabledCipherSuites());
        this.enabledProtocols.addAll(startTLSExtendedRequest.getEnabledProtocols());
    }

    // Prevent instantiation.
    private StartTLSExtendedRequestImpl() {
        // Nothing to do.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOID() {
        return OID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExtendedResultDecoder<ExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    /**
     * {@inheritDoc}
     */
    public SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * {@inheritDoc}
     */
    public StartTLSExtendedRequest addEnabledProtocol(String... protocols) {
        for (final String protocol : protocols) {
            this.enabledProtocols.add(Validator.ensureNotNull(protocol));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public StartTLSExtendedRequest addEnabledCipherSuite(String... suites) {
        for (final String suite : suites) {
            this.enabledCipherSuites.add(Validator.ensureNotNull(suite));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getEnabledProtocols() {
        return this.enabledProtocols;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getEnabledCipherSuites() {
        return this.enabledCipherSuites;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteString getValue() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasValue() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public StartTLSExtendedRequest setSSLContext(final SSLContext sslContext) {
        Validator.ensureNotNull(sslContext);
        this.sslContext = sslContext;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("StartTLSExtendedRequest(requestName=");
        builder.append(getOID());
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

}
