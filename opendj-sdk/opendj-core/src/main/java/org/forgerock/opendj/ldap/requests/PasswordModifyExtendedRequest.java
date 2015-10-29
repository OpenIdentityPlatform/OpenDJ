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

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;

/**
 * The password modify extended request as defined in RFC 3062. This operation
 * allows directory clients to update user passwords. The user may or may not be
 * associated with a directory entry. The user may or may not be represented as
 * an LDAP DN. The user's password may or may not be stored in the directory. In
 * addition, it includes support for requiring the user's current password as
 * well as for generating a new password if none was provided.
 *
 * <pre>
 * String userIdentity = ...; // For example, u:&lt;uid> or dn:&lt;DN>
 * char[] oldPassword = ...;
 * char[] newPassword = ...;
 * Connection connection = ...;
 *
 * PasswordModifyExtendedRequest request =
 *         Requests.newPasswordModifyExtendedRequest()
 *             .setUserIdentity(userIdentity)
 *             .setOldPassword(oldPassword)
 *             .setNewPassword(newPassword);
 *
 * PasswordModifyExtendedResult result = connection.extendedRequest(request);
 * if (result.isSuccess()) {
 *     // Changed password
 * } else {
 *     // Use result to diagnose error.
 * }
 * </pre>
 *
 * @see PasswordModifyExtendedResult
 * @see <a href="http://tools.ietf.org/html/rfc3062">RFC 3062 - LDAP Password
 *      Modify Extended Operation </a>
 */
public interface PasswordModifyExtendedRequest extends
        ExtendedRequest<PasswordModifyExtendedResult> {

    /**
     * A decoder which can be used to decode password modify extended operation
     * requests.
     */
    ExtendedRequestDecoder<PasswordModifyExtendedRequest, PasswordModifyExtendedResult> DECODER =
            new PasswordModifyExtendedRequestImpl.RequestDecoder();

    /**
     * The OID for the password modify extended operation request.
     */
    String OID = "1.3.6.1.4.1.4203.1.11.1";

    @Override
    PasswordModifyExtendedRequest addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the desired password for the user, or {@code null} if a new
     * password should be generated.
     *
     * @return The desired password for the user, or {@code null} if a new
     *         password should be generated.
     */
    byte[] getNewPassword();

    @Override
    String getOID();

    /**
     * Returns the current password for the user, if known.
     *
     * @return The current password for the user, or {@code null} if the
     *         password is not known.
     */
    byte[] getOldPassword();

    @Override
    ExtendedResultDecoder<PasswordModifyExtendedResult> getResultDecoder();

    /**
     * Returns the identity of the user whose password is to be modified, or
     * {@code null} if the request should be applied to the user currently
     * associated with the session. The returned identity may or may not be a
     * distinguished name.
     *
     * @return The identity of the user whose password is to be modified, or
     *         {@code null} if the request should be applied to the user
     *         currently associated with the session.
     */
    ByteString getUserIdentity();

    /**
     * Returns the identity of the user whose password is to be modified decoded
     * as a UTF-8 string, or {@code null} if the request should be applied to
     * the user currently associated with the session. The returned identity may
     * or may not be a distinguished name.
     *
     * @return The identity of the user whose password is to be modified decoded
     *         as a UTF-8 string, or {@code null} if the request should be
     *         applied to the user currently associated with the session.
     */
    String getUserIdentityAsString();

    @Override
    ByteString getValue();

    @Override
    boolean hasValue();

    /**
     * Sets the desired password for the user.
     *
     * @param newPassword
     *            The desired password for the user, or {@code null} if a new
     *            password should be generated.
     * @return This password modify request.
     * @throws UnsupportedOperationException
     *             If this password modify extended request does not permit the
     *             new password to be set.
     */
    PasswordModifyExtendedRequest setNewPassword(byte[] newPassword);

    /**
     * Sets the desired password for the user. The password will be converted to
     * a UTF-8 octet string.
     *
     * @param newPassword
     *            The desired password for the user, or {@code null} if a new
     *            password should be generated.
     * @return This password modify request.
     * @throws UnsupportedOperationException
     *             If this password modify extended request does not permit the
     *             new password to be set.
     */
    PasswordModifyExtendedRequest setNewPassword(char[] newPassword);

    /**
     * Sets the current password for the user.
     *
     * @param oldPassword
     *            The current password for the user, or {@code null} if the
     *            password is not known.
     * @return This password modify request.
     * @throws UnsupportedOperationException
     *             If this password modify extended request does not permit the
     *             old password to be set.
     */
    PasswordModifyExtendedRequest setOldPassword(byte[] oldPassword);

    /**
     * Sets the current password for the user. The password will be converted to
     * a UTF-8 octet string.
     *
     * @param oldPassword
     *            The current password for the user, or {@code null} if the
     *            password is not known.
     * @return This password modify request.
     * @throws UnsupportedOperationException
     *             If this password modify extended request does not permit the
     *             old password to be set.
     */
    PasswordModifyExtendedRequest setOldPassword(char[] oldPassword);

    /**
     * Sets the identity of the user whose password is to be modified. The
     * identity may or may not be a distinguished name.
     * <p>
     * If {@code userIdentity} is not an instance of {@code ByteString} then it
     * will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param userIdentity
     *            The identity of the user whose password is to be modified, or
     *            {@code null} if the request should be applied to the user
     *            currently associated with the session.
     * @return This password modify request.
     * @throws UnsupportedOperationException
     *             If this password modify extended request does not permit the
     *             user identity to be set.
     */
    PasswordModifyExtendedRequest setUserIdentity(Object userIdentity);

}
