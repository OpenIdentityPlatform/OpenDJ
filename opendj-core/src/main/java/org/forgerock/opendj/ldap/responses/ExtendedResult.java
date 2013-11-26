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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A Extended result indicates the status of an Extended operation and any
 * additional information associated with the Extended operation, including the
 * optional response name and value. These can be retrieved using the
 * {@link #getOID} and {@link #getValue} methods respectively.
 */
public interface ExtendedResult extends Result {

    @Override
    ExtendedResult addControl(Control control);

    @Override
    ExtendedResult addReferralURI(String uri);

    @Override
    Throwable getCause();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    String getDiagnosticMessage();

    @Override
    String getMatchedDN();

    /**
     * Returns the numeric OID, if any, associated with this extended result.
     *
     * @return The numeric OID associated with this extended result, or
     *         {@code null} if there is no OID.
     */
    String getOID();

    @Override
    List<String> getReferralURIs();

    @Override
    ResultCode getResultCode();

    /**
     * Returns the value, if any, associated with this extended result. Its
     * format is defined by the specification of this extended result.
     *
     * @return The value associated with this extended result, or {@code null}
     *         if there is no value.
     */
    ByteString getValue();

    /**
     * Returns {@code true} if this extended result has a value. In some
     * circumstances it may be useful to determine if a extended result has a
     * value, without actually calculating the value and incurring any
     * performance costs.
     *
     * @return {@code true} if this extended result has a value, or
     *         {@code false} if there is no value.
     */
    boolean hasValue();

    @Override
    boolean isReferral();

    @Override
    boolean isSuccess();

    @Override
    ExtendedResult setCause(Throwable cause);

    @Override
    ExtendedResult setDiagnosticMessage(String message);

    @Override
    ExtendedResult setMatchedDN(String dn);

    @Override
    ExtendedResult setResultCode(ResultCode resultCode);
}
