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
 * Portions Copyright 2014 ForgeRock AS.
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
