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
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import org.forgerock.opendj.ldap.responses.Result;

/**
 * Thrown when the result code returned in a Result indicates that the Bind
 * Request failed due to an authentication failure. More specifically, this
 * exception is used for the following error result codes:
 * <ul>
 * <li>{@link ResultCode#AUTH_METHOD_NOT_SUPPORTED AUTH_METHOD_NOT_SUPPORTED} -
 * the Bind request failed because it referenced an invalid SASL mechanism.
 * <li>{@link ResultCode#CLIENT_SIDE_AUTH_UNKNOWN CLIENT_SIDE_AUTH_UNKNOWN} -
 * the Bind request failed because the user requested an authentication
 * mechanism which is unknown or unsupported by the OpenDJ SDK.
 * <li>{@link ResultCode#INAPPROPRIATE_AUTHENTICATION
 * INAPPROPRIATE_AUTHENTICATION} - the Bind request failed because the requested
 * type of authentication was not appropriate for the targeted entry.
 * <li>{@link ResultCode#INVALID_CREDENTIALS INVALID_CREDENTIALS} - the Bind
 * request failed because the user did not provide a valid set of credentials.
 * </ul>
 */
@SuppressWarnings("serial")
public class AuthenticationException extends LdapException {
    AuthenticationException(final Result result) {
        super(result);
    }
}
