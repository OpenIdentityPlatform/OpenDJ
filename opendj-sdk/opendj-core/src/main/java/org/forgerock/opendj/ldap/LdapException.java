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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.io.IOException;

import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;

/**
 * Thrown when the result code returned in a Result indicates that the Request
 * was unsuccessful. This class can be sub-classed in order to implement
 * application specific exceptions.
 */
@SuppressWarnings("serial")
public class LdapException extends IOException {

    /**
     * Creates a new LDAP exception with the provided result code and an
     * empty diagnostic message.
     *
     * @param resultCode
     *            The result code.
     * @return The new LDAP exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static LdapException newLdapException(ResultCode resultCode) {
        return newLdapException(resultCode, null, null);
    }

    /**
     * Creates a new LDAP exception with the provided result code and
     * diagnostic message.
     *
     * @param resultCode
     *            The result code.
     * @param diagnosticMessage
     *            The diagnostic message, which may be empty or {@code null}
     *            indicating that none was provided.
     * @return The new LDAP exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static LdapException newLdapException(ResultCode resultCode,
            CharSequence diagnosticMessage) {
        return newLdapException(resultCode, diagnosticMessage, null);
    }

    /**
     * Creates a new LDAP exception with the provided result code and
     * cause. The diagnostic message will be taken from the cause, if provided.
     *
     * @param resultCode
     *            The result code.
     * @param cause
     *            The throwable cause, which may be {@code null} indicating that
     *            none was provided.
     * @return The new LDAP exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static LdapException newLdapException(ResultCode resultCode, Throwable cause) {
        return newLdapException(resultCode, null, cause);
    }

    /**
     * Creates a new LDAP exception with the provided result code,
     * diagnostic message, and cause.
     *
     * @param resultCode
     *            The result code.
     * @param diagnosticMessage
     *            The diagnostic message, which may be empty or {@code null}
     *            indicating that none was provided.
     * @param cause
     *            The throwable cause, which may be {@code null} indicating that
     *            none was provided.
     * @return The new LDAP exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static LdapException newLdapException(ResultCode resultCode,
            CharSequence diagnosticMessage, Throwable cause) {
        final Result result = Responses.newResult(resultCode);
        if (diagnosticMessage != null) {
            result.setDiagnosticMessage(diagnosticMessage.toString());
        } else if (cause != null) {
            result.setDiagnosticMessage(cause.getLocalizedMessage());
        }
        result.setCause(cause);
        return newLdapException(result);
    }

    /**
     * Creates a new LDAP exception using the provided result.
     *
     * @param result
     *            The result whose result code indicates a failure.
     * @return The LDAP exception wrapping the provided result.
     * @throws IllegalArgumentException
     *             If the provided result does not represent a failure.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static LdapException newLdapException(final Result result) {
        if (!result.getResultCode().isExceptional()) {
            throw new IllegalArgumentException("Attempted to wrap a successful result: " + result);
        }

        switch (result.getResultCode().asEnum()) {
        case ASSERTION_FAILED:
            return new AssertionFailureException(result);
        case AUTH_METHOD_NOT_SUPPORTED:
        case CLIENT_SIDE_AUTH_UNKNOWN:
        case INAPPROPRIATE_AUTHENTICATION:
        case INVALID_CREDENTIALS:
            return new AuthenticationException(result);
        case AUTHORIZATION_DENIED:
        case CONFIDENTIALITY_REQUIRED:
        case INSUFFICIENT_ACCESS_RIGHTS:
        case STRONG_AUTH_REQUIRED:
            return new AuthorizationException(result);
        case CLIENT_SIDE_USER_CANCELLED:
        case CANCELLED:
            return new CancelledResultException(result);
        case CLIENT_SIDE_SERVER_DOWN:
        case CLIENT_SIDE_CONNECT_ERROR:
        case CLIENT_SIDE_DECODING_ERROR:
        case CLIENT_SIDE_ENCODING_ERROR:
            return new ConnectionException(result);
        case ATTRIBUTE_OR_VALUE_EXISTS:
        case NO_SUCH_ATTRIBUTE:
        case CONSTRAINT_VIOLATION:
        case ENTRY_ALREADY_EXISTS:
        case INVALID_ATTRIBUTE_SYNTAX:
        case INVALID_DN_SYNTAX:
        case NAMING_VIOLATION:
        case NOT_ALLOWED_ON_NONLEAF:
        case NOT_ALLOWED_ON_RDN:
        case OBJECTCLASS_MODS_PROHIBITED:
        case OBJECTCLASS_VIOLATION:
        case UNDEFINED_ATTRIBUTE_TYPE:
            return new ConstraintViolationException(result);
        case REFERRAL:
            return new ReferralException(result);
        case NO_SUCH_OBJECT:
        case CLIENT_SIDE_NO_RESULTS_RETURNED:
            return new EntryNotFoundException(result);
        case CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED:
            return new MultipleEntriesFoundException(result);
        case CLIENT_SIDE_TIMEOUT:
        case TIME_LIMIT_EXCEEDED:
            return new TimeoutResultException(result);
        default:
            return new LdapException(result);
        }
    }

    private static String getMessage(final Result result) {
        if (result.getDiagnosticMessage() == null || result.getDiagnosticMessage().isEmpty()) {
            return result.getResultCode().toString();
        } else {
            return result.getResultCode() + ": " + result.getDiagnosticMessage();
        }
    }

    private final Result result;

    /**
     * Creates a new LDAP exception using the provided result.
     *
     * @param result
     *            The error result.
     */
    protected LdapException(final Result result) {
        super(getMessage(result), result.getCause());
        this.result = result;
    }

    /**
     * Returns the error result which caused this exception to be thrown. The
     * type of result returned corresponds to the expected result type of the
     * original request.
     *
     * @return The error result which caused this exception to be thrown.
     */
    public final Result getResult() {
        return result;
    }
}
