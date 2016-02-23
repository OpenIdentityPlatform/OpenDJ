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
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ResultCode;

/**
 * Unmodifiable result implementation.
 *
 * @param <S>
 *            The type of result.
 */
abstract class AbstractUnmodifiableResultImpl<S extends Result> extends
        AbstractUnmodifiableResponseImpl<S> implements Result {

    AbstractUnmodifiableResultImpl(final S impl) {
        super(impl);
    }

    @Override
    public final S addReferralURI(final String uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Throwable getCause() {
        return impl.getCause();
    }

    @Override
    public final String getDiagnosticMessage() {
        return impl.getDiagnosticMessage();
    }

    @Override
    public final String getMatchedDN() {
        return impl.getMatchedDN();
    }

    @Override
    public final List<String> getReferralURIs() {
        return Collections.unmodifiableList(impl.getReferralURIs());
    }

    @Override
    public final ResultCode getResultCode() {
        return impl.getResultCode();
    }

    @Override
    public final boolean isReferral() {
        return impl.isReferral();
    }

    @Override
    public final boolean isSuccess() {
        return impl.isSuccess();
    }

    @Override
    public final S setCause(final Throwable cause) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final S setDiagnosticMessage(final String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final S setMatchedDN(final String dn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final S setResultCode(final ResultCode resultCode) {
        throw new UnsupportedOperationException();
    }

}
