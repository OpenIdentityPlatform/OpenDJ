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

package org.forgerock.opendj.ldap.responses;

import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.ResultCode;

import org.forgerock.util.Reject;

/**
 * Modifiable result implementation.
 *
 * @param <S>
 *            The type of result.
 */
abstract class AbstractResultImpl<S extends Result> extends AbstractResponseImpl<S> implements
        Result {
    /** For local errors caused by internal exceptions. */
    private Throwable cause;
    private String diagnosticMessage = "";
    private String matchedDN = "";
    private final List<String> referralURIs = new LinkedList<>();
    private ResultCode resultCode;

    AbstractResultImpl(final Result result) {
        super(result);
        this.cause = result.getCause();
        this.diagnosticMessage = result.getDiagnosticMessage();
        this.matchedDN = result.getMatchedDN();
        this.referralURIs.addAll(result.getReferralURIs());
        this.resultCode = result.getResultCode();
    }

    AbstractResultImpl(final ResultCode resultCode) {
        this.resultCode = resultCode;
    }

    @Override
    public final S addReferralURI(final String uri) {
        Reject.ifNull(uri);

        referralURIs.add(uri);
        return getThis();
    }

    @Override
    public final Throwable getCause() {
        return cause;
    }

    @Override
    public final String getDiagnosticMessage() {
        return diagnosticMessage;
    }

    @Override
    public final String getMatchedDN() {
        return matchedDN;
    }

    @Override
    public final List<String> getReferralURIs() {
        return referralURIs;
    }

    @Override
    public final ResultCode getResultCode() {
        return resultCode;
    }

    @Override
    public final boolean isReferral() {
        final ResultCode code = getResultCode();
        return code.equals(ResultCode.REFERRAL);
    }

    @Override
    public final boolean isSuccess() {
        final ResultCode code = getResultCode();
        return !code.isExceptional();
    }

    @Override
    public final S setCause(final Throwable cause) {
        this.cause = cause;
        return getThis();
    }

    @Override
    public final S setDiagnosticMessage(final String message) {
        this.diagnosticMessage = message != null ? message : "";
        return getThis();
    }

    @Override
    public final S setMatchedDN(final String dn) {
        this.matchedDN = dn != null ? dn : "";
        return getThis();
    }

    @Override
    public final S setResultCode(final ResultCode resultCode) {
        Reject.ifNull(resultCode);

        this.resultCode = resultCode;
        return getThis();
    }

}
