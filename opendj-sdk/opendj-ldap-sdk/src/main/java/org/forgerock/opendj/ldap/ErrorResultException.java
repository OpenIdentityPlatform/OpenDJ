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
 *      Portions copyright 2011-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.concurrent.ExecutionException;

import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;

/**
 * Thrown when the result code returned in a Result indicates that the Request
 * was unsuccessful. This class can be sub-classed in order to implement
 * application specific exceptions.
 */
@SuppressWarnings("serial")
public class ErrorResultException extends ExecutionException {

    /**
     * Creates a new error result exception with the provided result code and an
     * empty diagnostic message.
     *
     * @param resultCode
     *            The result code.
     * @return The new error result exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static ErrorResultException newErrorResult(ResultCode resultCode) {
        return newErrorResult(resultCode, null, null);
    }

    /**
     * Creates a new error result exception with the provided result code and
     * diagnostic message.
     *
     * @param resultCode
     *            The result code.
     * @param diagnosticMessage
     *            The diagnostic message, which may be empty or {@code null}
     *            indicating that none was provided.
     * @return The new error result exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     * @deprecated use {@link #newErrorResult(ResultCode, CharSequence)} instead
     */
    @Deprecated
    public static ErrorResultException newErrorResult(ResultCode resultCode,
            String diagnosticMessage) {
        return newErrorResult(resultCode, (CharSequence) diagnosticMessage);
    }

    /**
     * Creates a new error result exception with the provided result code and
     * diagnostic message.
     *
     * @param resultCode
     *            The result code.
     * @param diagnosticMessage
     *            The diagnostic message, which may be empty or {@code null}
     *            indicating that none was provided.
     * @return The new error result exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static ErrorResultException newErrorResult(ResultCode resultCode,
            CharSequence diagnosticMessage) {
        return newErrorResult(resultCode, diagnosticMessage, null);
    }

    /**
     * Creates a new error result exception with the provided result code and
     * cause. The diagnostic message will be taken from the cause, if provided.
     *
     * @param resultCode
     *            The result code.
     * @param cause
     *            The throwable cause, which may be {@code null} indicating that
     *            none was provided.
     * @return The new error result exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static ErrorResultException newErrorResult(ResultCode resultCode, Throwable cause) {
        return newErrorResult(resultCode, null, cause);
    }

    /**
     * Creates a new error result exception with the provided result code,
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
     * @return The new error result exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     * @deprecated use {@link #newErrorResult(ResultCode, CharSequence, Throwable)} instead
     */
    @Deprecated
    public static ErrorResultException newErrorResult(ResultCode resultCode,
            String diagnosticMessage, Throwable cause) {
        return newErrorResult(resultCode, (CharSequence) diagnosticMessage, cause);
    }

    /**
     * Creates a new error result exception with the provided result code,
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
     * @return The new error result exception.
     * @throws IllegalArgumentException
     *             If the provided result code does not represent a failure.
     * @throws NullPointerException
     *             If {@code resultCode} was {@code null}.
     */
    public static ErrorResultException newErrorResult(ResultCode resultCode,
            CharSequence diagnosticMessage, Throwable cause) {
        final Result result = Responses.newResult(resultCode);
        if (diagnosticMessage != null) {
            result.setDiagnosticMessage(diagnosticMessage.toString());
        } else if (cause != null) {
            result.setDiagnosticMessage(cause.getLocalizedMessage());
        }
        result.setCause(cause);
        return newErrorResult(result);
    }

    /**
     * Creates a new error result exception using the provided result.
     *
     * @param result
     *            The result whose result code indicates a failure.
     * @return The error result exception wrapping the provided result.
     * @throws IllegalArgumentException
     *             If the provided result does not represent a failure.
     * @throws NullPointerException
     *             If {@code result} was {@code null}.
     */
    public static ErrorResultException newErrorResult(final Result result) {
        if (!result.getResultCode().isExceptional()) {
            throw new IllegalArgumentException("Attempted to wrap a successful result: " + result);
        }

        ResultCode rc = result.getResultCode();
        if (rc == ResultCode.ASSERTION_FAILED) {
            return new AssertionFailureException(result);
        } else if (rc == ResultCode.AUTH_METHOD_NOT_SUPPORTED
                || rc == ResultCode.CLIENT_SIDE_AUTH_UNKNOWN
                || rc == ResultCode.INAPPROPRIATE_AUTHENTICATION
                || rc == ResultCode.INVALID_CREDENTIALS) {
            return new AuthenticationException(result);
        } else if (rc == ResultCode.AUTHORIZATION_DENIED
                || rc == ResultCode.CONFIDENTIALITY_REQUIRED
                || rc == ResultCode.INSUFFICIENT_ACCESS_RIGHTS
                || rc == ResultCode.STRONG_AUTH_REQUIRED) {
            return new AuthorizationException(result);
        } else if (rc == ResultCode.CLIENT_SIDE_USER_CANCELLED || rc == ResultCode.CANCELLED) {
            return new CancelledResultException(result);
        } else if (rc == ResultCode.CLIENT_SIDE_SERVER_DOWN
                || rc == ResultCode.CLIENT_SIDE_CONNECT_ERROR
                || rc == ResultCode.CLIENT_SIDE_DECODING_ERROR
                || rc == ResultCode.CLIENT_SIDE_ENCODING_ERROR) {
            return new ConnectionException(result);
        } else if (rc == ResultCode.ATTRIBUTE_OR_VALUE_EXISTS
                || rc == ResultCode.NO_SUCH_ATTRIBUTE
                || rc == ResultCode.CONSTRAINT_VIOLATION || rc == ResultCode.ENTRY_ALREADY_EXISTS
                || rc == ResultCode.INVALID_ATTRIBUTE_SYNTAX || rc == ResultCode.INVALID_DN_SYNTAX
                || rc == ResultCode.NAMING_VIOLATION || rc == ResultCode.NOT_ALLOWED_ON_NONLEAF
                || rc == ResultCode.NOT_ALLOWED_ON_RDN
                || rc == ResultCode.OBJECTCLASS_MODS_PROHIBITED
                || rc == ResultCode.OBJECTCLASS_VIOLATION
                || rc == ResultCode.UNDEFINED_ATTRIBUTE_TYPE) {
            return new ConstraintViolationException(result);
        } else if (rc == ResultCode.REFERRAL) {
            return new ReferralException(result);
        } else if (rc == ResultCode.NO_SUCH_OBJECT
                || rc == ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED) {
            return new EntryNotFoundException(result);
        } else if (rc == ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED) {
            return new MultipleEntriesFoundException(result);
        } else if (rc == ResultCode.CLIENT_SIDE_TIMEOUT || rc == ResultCode.TIME_LIMIT_EXCEEDED) {
            return new TimeoutResultException(result);
        }

        return new ErrorResultException(result);
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
     * Creates a new error result exception using the provided result.
     *
     * @param result
     *            The error result.
     */
    protected ErrorResultException(final Result result) {
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
