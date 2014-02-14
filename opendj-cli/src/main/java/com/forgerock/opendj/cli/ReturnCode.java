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
 *      Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

/**
 * List of return codes used by CLIs.
 */
public enum ReturnCode {
    /**
     * Successful setup.
     * <PRE>
     * Code value of 0.
     * </PRE>
     */
    SUCCESS(0),
    /**
     * Unexpected error (potential bug).
     * <PRE>
     * Code value of 1.
     * </PRE>
     */
    ERROR_UNEXPECTED(1),
    /**
     * Cannot parse arguments or data provided by user is not valid.
     * <PRE>
     * Code value of 2.
     * </PRE>
     */
    ERROR_USER_DATA(2),
    /**
     * Error server already installed.
     * <PRE>
     * Code value of 3.
     * </PRE>
     */
    ERROR_SERVER_ALREADY_INSTALLED(3),
    /**
     * Error initializing server.
     * <PRE>
     * Code value of 4.
     * </PRE>
     */
    ERROR_INITIALIZING_SERVER(4),
    /**
     * The user failed providing password (for the key store for instance).
     * <PRE>
     * Code value of 5.
     * </PRE>
     */
    ERROR_PASSWORD_LIMIT(5),
    /**
     * The user cancelled the setup.
     * <PRE>
     * Code value of 6.
     * </PRE>
     */
    ERROR_USER_CANCELLED(6),
    /**
     * The user doesn't accept the license.
     * <PRE>
     * Code value of 7.
     * </PRE>
     */
    ERROR_LICENSE_NOT_ACCEPTED(7),
    /**
     * Incompatible java version.
     * <PRE>
     * Code value of 8.
     * </PRE>
     */
    JAVA_VERSION_INCOMPATIBLE(8),
    /**
     * The LDAP result code for operations that fail because a defined constraint
     * has been violated.
     */
    CONSTRAINT_VIOLATION(19),
    /**
     * The LDAP result code for operations that fail because a targeted entry does
     * not exist.
     */
    NO_SUCH_OBJECT(32),
    /**
     * The LDAP result code for operations that fail because the client does not
     * have permission to perform the requested operation.
     */
    INSUFFICIENT_ACCESS_RIGHTS(50),
    /**
     * The LDAP result code for operations that fail because the requested
     * operation would have resulted in an entry that conflicts with one that
     * already exists.
     */
    ENTRY_ALREADY_EXISTS(68),
    /**
     * The LDAP result code for use in cases in which none of the other defined
     * result codes are appropriate.
     */
    OTHER(80),
    /**
     * The client-side result code that indicates that a previously-established
     * connection to the server was lost.  This is for client-side use only and
     * should never be transferred over protocol.
     */
    CLIENT_SIDE_SERVER_DOWN(81),
    /**
     * The client-side result code that indicates that the client was not able to
     * establish a connection to the server.  This is for client-side use only and
     * should never be transferred over protocol.
     */
    CLIENT_SIDE_CONNECT_ERROR(91),
    /**
     * The client-side result code that indicates that there was a problem with one or more of the parameters provided
     * by the user.
     * <PRE>
     * Code value of 89.
     * </PRE>
     */
    CLIENT_SIDE_PARAM_ERROR(89),

    /**
     * TODO to review.
     */
    TODO(99);

    private int returnCode;

    private ReturnCode(int returnCode) {
        this.returnCode = returnCode;
    }

    /**
     * Get the corresponding return code value.
     *
     * @return The corresponding return code value.
     */
    public int get() {
        return returnCode;
    }
}
