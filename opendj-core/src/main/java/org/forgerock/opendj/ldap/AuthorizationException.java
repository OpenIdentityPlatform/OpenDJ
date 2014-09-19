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
 * Thrown when the result code returned in a Result indicates that the Request
 * failed due to an authorization failure. More specifically, this exception is
 * used for the following error result codes:
 * <ul>
 * <li>{@link ResultCode#AUTHORIZATION_DENIED AUTHORIZATION_DENIED} - the
 * Request failed because the server has not allowed the client to use the
 * requested authorization.
 * <li>{@link ResultCode#CONFIDENTIALITY_REQUIRED CONFIDENTIALITY_REQUIRED} -
 * the Request failed because it requires confidentiality for the communication
 * between the client and the server.
 * <li>{@link ResultCode#INSUFFICIENT_ACCESS_RIGHTS INSUFFICIENT_ACCESS_RIGHTS}
 * - the Request failed because the client does not have sufficient permission
 * to perform the requested operation.
 * <li>{@link ResultCode#STRONG_AUTH_REQUIRED STRONG_AUTH_REQUIRED} - the
 * Request failed because it requires that the client has completed a strong
 * form of authentication.
 * </ul>
 */
@SuppressWarnings("serial")
public class AuthorizationException extends LdapException {
    AuthorizationException(final Result result) {
        super(result);
    }
}
