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

package org.forgerock.opendj.ldap.responses;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * The password modify extended result as defined in RFC 3062. The result
 * includes the generated password, if requested, but only if the modify request
 * succeeded.
 *
 * @see org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest
 * @see <a href="http://tools.ietf.org/html/rfc3062">RFC 3062 - LDAP Password
 *      Modify Extended Operation </a>
 */
public interface PasswordModifyExtendedResult extends ExtendedResult {

    @Override
    PasswordModifyExtendedResult addControl(Control control);

    @Override
    PasswordModifyExtendedResult addReferralURI(String uri);

    @Override
    Throwable getCause();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    String getDiagnosticMessage();

    /**
     * Returns the newly generated password, but only if the password modify
     * request succeeded and a generated password was requested.
     *
     * @return The newly generated password, or {@code null} if the password
     *         modify request failed or a generated password was not requested.
     */
    byte[] getGeneratedPassword();

    @Override
    String getMatchedDN();

    @Override
    String getOID();

    @Override
    List<String> getReferralURIs();

    @Override
    ResultCode getResultCode();

    @Override
    ByteString getValue();

    @Override
    boolean hasValue();

    @Override
    boolean isReferral();

    @Override
    boolean isSuccess();

    @Override
    PasswordModifyExtendedResult setCause(Throwable cause);

    @Override
    PasswordModifyExtendedResult setDiagnosticMessage(String message);

    /**
     * Sets the generated password.
     *
     * @param password
     *            The generated password, or {@code null} if there is no
     *            generated password associated with this result.
     * @return This password modify result.
     * @throws UnsupportedOperationException
     *             If this password modify extended result does not permit the
     *             generated password to be set.
     */
    PasswordModifyExtendedResult setGeneratedPassword(byte[] password);

    /**
     * Sets the generated password. The password will be converted to a UTF-8
     * octet string.
     *
     * @param password
     *            The generated password, or {@code null} if there is no
     *            generated password associated with this result.
     * @return This password modify result.
     * @throws UnsupportedOperationException
     *             If this password modify extended result does not permit the
     *             generated password to be set.
     */
    PasswordModifyExtendedResult setGeneratedPassword(char[] password);

    @Override
    PasswordModifyExtendedResult setMatchedDN(String dn);

    @Override
    PasswordModifyExtendedResult setResultCode(ResultCode resultCode);

}
