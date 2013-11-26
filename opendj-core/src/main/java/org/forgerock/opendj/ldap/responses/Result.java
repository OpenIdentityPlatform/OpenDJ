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

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A Result is used to indicate the status of an operation performed by the
 * server. A Result is comprised of several fields:
 * <ul>
 * <li>The <b>result code</b> can be retrieved using the method
 * {@link #getResultCode}. This indicates the overall outcome of the operation.
 * In particular, whether or not it succeeded which is indicated using a value
 * of {@link ResultCode#SUCCESS}.
 * <li>The optional <b>diagnostic message</b> can be retrieved using the method
 * {@link #getDiagnosticMessage}. At the server's discretion, a diagnostic
 * message may be included in a Result in order to supplement the result code
 * with additional human-readable information.
 * <li>The optional <b>matched DN</b> can be retrieved using the method
 * {@link #getMatchedDN}. For certain result codes, this is used to indicate to
 * the client the last entry used in finding the Request's target (or base)
 * entry.
 * <li>The optional <b>referrals</b> can be retrieved using the method
 * {@link #getReferralURIs}. Referrals are present in a Result if the result
 * code is set to {@link ResultCode#REFERRAL}, and it are absent with all other
 * result codes.
 * </ul>
 */
public interface Result extends Response {

    @Override
    Result addControl(Control control);

    /**
     * Adds the provided referral URI to this result.
     *
     * @param uri
     *            The referral URI to be added.
     * @return This result.
     * @throws UnsupportedOperationException
     *             If this result does not permit referrals to be added.
     * @throws NullPointerException
     *             If {@code uri} was {@code null}.
     */
    Result addReferralURI(String uri);

    /**
     * Returns the throwable cause associated with this result if available. A
     * cause may be provided in cases where a result indicates a failure due to
     * a client-side error.
     *
     * @return The throwable cause, or {@code null} if none was provided.
     */
    Throwable getCause();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the diagnostic message associated with this result.
     *
     * @return The diagnostic message, which may be empty if none was provided
     *         (never {@code null}).
     */
    String getDiagnosticMessage();

    /**
     * Returns the matched DN associated with this result.
     *
     * @return The matched DN, which may be empty if none was provided (never
     *         {@code null}).
     */
    String getMatchedDN();

    /**
     * Returns a {@code List} containing the referral URIs included with this
     * result. The returned {@code List} may be modified if permitted by this
     * result.
     *
     * @return A {@code List} containing the referral URIs.
     */
    List<String> getReferralURIs();

    /**
     * Returns the result code associated with this result.
     *
     * @return The result code.
     */
    ResultCode getResultCode();

    /**
     * Indicates whether or not a referral needs to be chased in order to
     * complete the operation.
     * <p>
     * Specifically, this method returns {@code true} if the result code is
     * equal to {@link ResultCode#REFERRAL}.
     *
     * @return {@code true} if a referral needs to be chased, otherwise
     *         {@code false}.
     */
    boolean isReferral();

    /**
     * Indicates whether or not the request succeeded or not. This method will
     * return {code true} for all non-error responses.
     *
     * @return {@code true} if the request succeeded, otherwise {@code false}.
     */
    boolean isSuccess();

    /**
     * Sets the throwable cause associated with this result if available. A
     * cause may be provided in cases where a result indicates a failure due to
     * a client-side error.
     *
     * @param cause
     *            The throwable cause, which may be {@code null} indicating that
     *            none was provided.
     * @return This result.
     * @throws UnsupportedOperationException
     *             If this result does not permit the cause to be set.
     */
    Result setCause(Throwable cause);

    /**
     * Sets the diagnostic message associated with this result.
     *
     * @param message
     *            The diagnostic message, which may be empty or {@code null}
     *            indicating that none was provided.
     * @return This result.
     * @throws UnsupportedOperationException
     *             If this result does not permit the diagnostic message to be
     *             set.
     */
    Result setDiagnosticMessage(String message);

    /**
     * Sets the matched DN associated with this result.
     *
     * @param dn
     *            The matched DN associated, which may be empty or {@code null}
     *            indicating that none was provided.
     * @return This result.
     * @throws UnsupportedOperationException
     *             If this result does not permit the matched DN to be set.
     */
    Result setMatchedDN(String dn);

    /**
     * Sets the result code associated with this result.
     *
     * @param resultCode
     *            The result code.
     * @return This result.
     * @throws UnsupportedOperationException
     *             If this result does not permit the result code to be set.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    Result setResultCode(ResultCode resultCode);

}
