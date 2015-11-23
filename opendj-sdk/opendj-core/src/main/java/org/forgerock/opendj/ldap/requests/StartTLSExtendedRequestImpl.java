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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.Arrays;
import java.util.Collection;
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

import org.forgerock.util.Reject;

/**
 * Start TLS extended request implementation.
 */
final class StartTLSExtendedRequestImpl extends
        AbstractExtendedRequest<StartTLSExtendedRequest, ExtendedResult> implements
        StartTLSExtendedRequest {
    static final class RequestDecoder implements
            ExtendedRequestDecoder<StartTLSExtendedRequest, ExtendedResult> {

        @Override
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
        @Override
        public ExtendedResult decodeExtendedResult(final ExtendedResult result,
                final DecodeOptions options) throws DecodeException {
            // TODO: Should we check oid is NOT null and matches but
            // value is null?
            return result;
        }

        @Override
        public GenericExtendedResult newExtendedErrorResult(final ResultCode resultCode,
                final String matchedDN, final String diagnosticMessage) {
            return Responses.newGenericExtendedResult(resultCode).setMatchedDN(matchedDN)
                    .setDiagnosticMessage(diagnosticMessage);
        }
    }

    /** No need to expose this. */
    private static final ExtendedResultDecoder<ExtendedResult> RESULT_DECODER = new ResultDecoder();

    /** The list of cipher suite. */
    private final List<String> enabledCipherSuites = new LinkedList<>();

    /** The list of protocols. */
    private final List<String> enabledProtocols = new LinkedList<>();

    private SSLContext sslContext;

    StartTLSExtendedRequestImpl(final SSLContext sslContext) {
        Reject.ifNull(sslContext);
        this.sslContext = sslContext;
    }

    StartTLSExtendedRequestImpl(final StartTLSExtendedRequest startTLSExtendedRequest) {
        super(startTLSExtendedRequest);
        this.sslContext = startTLSExtendedRequest.getSSLContext();
        this.enabledCipherSuites.addAll(startTLSExtendedRequest.getEnabledCipherSuites());
        this.enabledProtocols.addAll(startTLSExtendedRequest.getEnabledProtocols());
    }

    /** Prevent instantiation. */
    private StartTLSExtendedRequestImpl() {
        // Nothing to do.
    }

    @Override
    public StartTLSExtendedRequest addEnabledCipherSuite(final String... suites) {
        return addEnabledCipherSuite(Arrays.asList(suites));
    }

    @Override
    public StartTLSExtendedRequest addEnabledCipherSuite(final Collection<String> suites) {
        for (final String suite : suites) {
            this.enabledCipherSuites.add(Reject.checkNotNull(suite));
        }
        return this;
    }

    @Override
    public StartTLSExtendedRequest addEnabledProtocol(final String... protocols) {
        return addEnabledProtocol(Arrays.asList(protocols));
    }

    @Override
    public StartTLSExtendedRequest addEnabledProtocol(final Collection<String> protocols) {
        for (final String protocol : protocols) {
            this.enabledProtocols.add(Reject.checkNotNull(protocol));
        }
        return this;
    }

    @Override
    public List<String> getEnabledCipherSuites() {
        return this.enabledCipherSuites;
    }

    @Override
    public List<String> getEnabledProtocols() {
        return this.enabledProtocols;
    }

    @Override
    public String getOID() {
        return OID;
    }

    @Override
    public ExtendedResultDecoder<ExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    @Override
    public SSLContext getSSLContext() {
        return sslContext;
    }

    @Override
    public ByteString getValue() {
        return null;
    }

    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public StartTLSExtendedRequest setSSLContext(final SSLContext sslContext) {
        Reject.ifNull(sslContext);
        this.sslContext = sslContext;
        return this;
    }

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
