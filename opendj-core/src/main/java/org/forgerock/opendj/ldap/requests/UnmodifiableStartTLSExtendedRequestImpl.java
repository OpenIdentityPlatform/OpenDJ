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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.responses.ExtendedResult;

/**
 * Unmodifiable start TLS extended request implementation.
 */
final class UnmodifiableStartTLSExtendedRequestImpl extends
        AbstractUnmodifiableExtendedRequest<StartTLSExtendedRequest, ExtendedResult> implements
        StartTLSExtendedRequest {
    UnmodifiableStartTLSExtendedRequestImpl(final StartTLSExtendedRequest impl) {
        super(impl);
    }

    @Override
    public StartTLSExtendedRequest addEnabledCipherSuite(final Collection<String> suites) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StartTLSExtendedRequest addEnabledCipherSuite(final String... suites) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StartTLSExtendedRequest addEnabledProtocol(final Collection<String> protocols) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StartTLSExtendedRequest addEnabledProtocol(final String... protocols) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getEnabledCipherSuites() {
        return Collections.unmodifiableList(impl.getEnabledCipherSuites());
    }

    @Override
    public List<String> getEnabledProtocols() {
        return Collections.unmodifiableList(impl.getEnabledProtocols());
    }

    @Override
    public SSLContext getSSLContext() {
        return impl.getSSLContext();
    }

    @Override
    public StartTLSExtendedRequest setSSLContext(final SSLContext sslContext) {
        throw new UnsupportedOperationException();
    }
}
