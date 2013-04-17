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
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static org.forgerock.opendj.ldap.CoreMessages.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;

/**
 * An operation result code as defined in RFC 4511 section 4.1.9 is used to
 * indicate the final status of an operation. If a server detects multiple
 * errors for an operation, only one result code is returned. The server should
 * return the result code that best indicates the nature of the error
 * encountered. Servers may return substituted result codes to prevent
 * unauthorized disclosures.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511#section-4.1.9">RFC 4511 -
 *      Lightweight Directory Access Protocol (LDAP): The Protocol </a>
 */
public final class ResultCode {
    private static final ResultCode[] ELEMENTS = new ResultCode[16655];

    private static final List<ResultCode> IMMUTABLE_ELEMENTS = Collections.unmodifiableList(Arrays
            .asList(ELEMENTS));

    /**
     * The result code that indicates that the operation completed successfully.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 0}.
     */
    public static final ResultCode SUCCESS =
            registerSuccessResultCode(0, INFO_RESULT_SUCCESS.get());

    /**
     * The result code that indicates that an internal error prevented the
     * operation from being processed properly.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 1}.
     */
    public static final ResultCode OPERATIONS_ERROR = registerErrorResultCode(1,
            INFO_RESULT_OPERATIONS_ERROR.get());

    /**
     * The result code that indicates that the client sent a malformed or
     * illegal request to the server.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 2}.
     */
    public static final ResultCode PROTOCOL_ERROR = registerErrorResultCode(2,
            INFO_RESULT_PROTOCOL_ERROR.get());

    /**
     * The result code that indicates that a time limit was exceeded while
     * attempting to process the request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 3}.
     */
    public static final ResultCode TIME_LIMIT_EXCEEDED = registerErrorResultCode(3,
            INFO_RESULT_TIME_LIMIT_EXCEEDED.get());

    /**
     * The result code that indicates that a size limit was exceeded while
     * attempting to process the request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 4}.
     */
    public static final ResultCode SIZE_LIMIT_EXCEEDED = registerErrorResultCode(4,
            INFO_RESULT_SIZE_LIMIT_EXCEEDED.get());

    /**
     * The result code that indicates that the attribute value assertion
     * included in a compare request did not match the targeted entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 5}.
     */
    public static final ResultCode COMPARE_FALSE = registerSuccessResultCode(5,
            INFO_RESULT_COMPARE_FALSE.get());

    /**
     * The result code that indicates that the attribute value assertion
     * included in a compare request did match the targeted entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 6}.
     */
    public static final ResultCode COMPARE_TRUE = registerSuccessResultCode(6,
            INFO_RESULT_COMPARE_TRUE.get());

    /**
     * The result code that indicates that the requested authentication attempt
     * failed because it referenced an invalid SASL mechanism.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 7}.
     */
    public static final ResultCode AUTH_METHOD_NOT_SUPPORTED = registerErrorResultCode(7,
            INFO_RESULT_AUTH_METHOD_NOT_SUPPORTED.get());

    /**
     * The result code that indicates that the requested operation could not be
     * processed because it requires that the client has completed a strong form
     * of authentication.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 8}.
     */
    public static final ResultCode STRONG_AUTH_REQUIRED = registerErrorResultCode(8,
            INFO_RESULT_STRONG_AUTH_REQUIRED.get());

    /**
     * The result code that indicates that a referral was encountered.
     * <p>
     * Strictly speaking this result code should not be exceptional since it is
     * considered as a "success" response. However, referrals should occur
     * rarely in practice and, when they do occur, should not be ignored since
     * the application may believe that a request has succeeded when, in fact,
     * nothing was done.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 10}.
     */
    public static final ResultCode REFERRAL = registerErrorResultCode(10, INFO_RESULT_REFERRAL
            .get());

    /**
     * The result code that indicates that processing on the requested operation
     * could not continue because an administrative limit was exceeded.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 11}.
     */
    public static final ResultCode ADMIN_LIMIT_EXCEEDED = registerErrorResultCode(11,
            INFO_RESULT_ADMIN_LIMIT_EXCEEDED.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it included a critical extension that is unsupported or
     * inappropriate for that request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 12}.
     */
    public static final ResultCode UNAVAILABLE_CRITICAL_EXTENSION = registerErrorResultCode(12,
            INFO_RESULT_UNAVAILABLE_CRITICAL_EXTENSION.get());

    /**
     * The result code that indicates that the requested operation could not be
     * processed because it requires confidentiality for the communication
     * between the client and the server.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 13}.
     */
    public static final ResultCode CONFIDENTIALITY_REQUIRED = registerErrorResultCode(13,
            INFO_RESULT_CONFIDENTIALITY_REQUIRED.get());

    /**
     * The result code that should be used for intermediate responses in
     * multi-stage SASL bind operations.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 14}.
     */
    public static final ResultCode SASL_BIND_IN_PROGRESS = registerSuccessResultCode(14,
            INFO_RESULT_SASL_BIND_IN_PROGRESS.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it targeted an attribute or attribute value that did not exist in
     * the specified entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 16}.
     */
    public static final ResultCode NO_SUCH_ATTRIBUTE = registerErrorResultCode(16,
            INFO_RESULT_NO_SUCH_ATTRIBUTE.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it referenced an attribute that is not defined in the server
     * schema.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 17}.
     */
    public static final ResultCode UNDEFINED_ATTRIBUTE_TYPE = registerErrorResultCode(17,
            INFO_RESULT_UNDEFINED_ATTRIBUTE_TYPE.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it attempted to perform an inappropriate type of matching against
     * an attribute.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 18}.
     */
    public static final ResultCode INAPPROPRIATE_MATCHING = registerErrorResultCode(18,
            INFO_RESULT_INAPPROPRIATE_MATCHING.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it would have violated some constraint defined in the server.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 19}.
     */
    public static final ResultCode CONSTRAINT_VIOLATION = registerErrorResultCode(19,
            INFO_RESULT_CONSTRAINT_VIOLATION.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in a conflict with an existing attribute
     * or attribute value in the target entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 20}.
     */
    public static final ResultCode ATTRIBUTE_OR_VALUE_EXISTS = registerErrorResultCode(20,
            INFO_RESULT_ATTRIBUTE_OR_VALUE_EXISTS.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it violated the syntax for a specified attribute.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 21}.
     */
    public static final ResultCode INVALID_ATTRIBUTE_SYNTAX = registerErrorResultCode(21,
            INFO_RESULT_INVALID_ATTRIBUTE_SYNTAX.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it referenced an entry that does not exist.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 32}.
     */
    public static final ResultCode NO_SUCH_OBJECT = registerErrorResultCode(32,
            INFO_RESULT_NO_SUCH_OBJECT.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it attempted to perform an illegal operation on an alias.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 33}.
     */
    public static final ResultCode ALIAS_PROBLEM = registerErrorResultCode(33,
            INFO_RESULT_ALIAS_PROBLEM.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in an entry with an invalid or malformed
     * DN.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 34}.
     */
    public static final ResultCode INVALID_DN_SYNTAX = registerErrorResultCode(34,
            INFO_RESULT_INVALID_DN_SYNTAX.get());

    /**
     * The result code that indicates that a problem was encountered while
     * attempting to dereference an alias for a search operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 36}.
     */
    public static final ResultCode ALIAS_DEREFERENCING_PROBLEM = registerErrorResultCode(36,
            INFO_RESULT_ALIAS_DEREFERENCING_PROBLEM.get());

    /**
     * The result code that indicates that an authentication attempt failed
     * because the requested type of authentication was not appropriate for the
     * targeted entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 48}.
     */
    public static final ResultCode INAPPROPRIATE_AUTHENTICATION = registerErrorResultCode(48,
            INFO_RESULT_INAPPROPRIATE_AUTHENTICATION.get());

    /**
     * The result code that indicates that an authentication attempt failed
     * because the user did not provide a valid set of credentials.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 49}.
     */
    public static final ResultCode INVALID_CREDENTIALS = registerErrorResultCode(49,
            INFO_RESULT_INVALID_CREDENTIALS.get());

    /**
     * The result code that indicates that the client does not have sufficient
     * permission to perform the requested operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 50}.
     */
    public static final ResultCode INSUFFICIENT_ACCESS_RIGHTS = registerErrorResultCode(50,
            INFO_RESULT_INSUFFICIENT_ACCESS_RIGHTS.get());

    /**
     * The result code that indicates that the server is too busy to process the
     * requested operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 51}.
     */
    public static final ResultCode BUSY = registerErrorResultCode(51, INFO_RESULT_BUSY.get());

    /**
     * The result code that indicates that either the entire server or one or
     * more required resources were not available for use in processing the
     * request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 52}.
     */
    public static final ResultCode UNAVAILABLE = registerErrorResultCode(52,
            INFO_RESULT_UNAVAILABLE.get());

    /**
     * The result code that indicates that the server is unwilling to perform
     * the requested operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 53}.
     */
    public static final ResultCode UNWILLING_TO_PERFORM = registerErrorResultCode(53,
            INFO_RESULT_UNWILLING_TO_PERFORM.get());

    /**
     * The result code that indicates that a referral or chaining loop was
     * detected while processing the request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 54}.
     */
    public static final ResultCode LOOP_DETECT = registerErrorResultCode(54,
            INFO_RESULT_LOOP_DETECT.get());

    /**
     * The result code that indicates that a search request included a VLV
     * request control without a server-side sort control.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 60}.
     */
    public static final ResultCode SORT_CONTROL_MISSING = registerErrorResultCode(60,
            INFO_RESULT_SORT_CONTROL_MISSING.get());

    /**
     * The result code that indicates that a search request included a VLV
     * request control with an invalid offset.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 61}.
     */
    public static final ResultCode OFFSET_RANGE_ERROR = registerErrorResultCode(61,
            INFO_RESULT_OFFSET_RANGE_ERROR.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it would have violated the server's naming configuration.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 64}.
     */
    public static final ResultCode NAMING_VIOLATION = registerErrorResultCode(64,
            INFO_RESULT_NAMING_VIOLATION.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in an entry that violated the server
     * schema.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 65}.
     */
    public static final ResultCode OBJECTCLASS_VIOLATION = registerErrorResultCode(65,
            INFO_RESULT_OBJECTCLASS_VIOLATION.get());

    /**
     * The result code that indicates that the requested operation is not
     * allowed for non-leaf entries.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 66}.
     */
    public static final ResultCode NOT_ALLOWED_ON_NONLEAF = registerErrorResultCode(66,
            INFO_RESULT_NOT_ALLOWED_ON_NONLEAF.get());

    /**
     * The result code that indicates that the requested operation is not
     * allowed on an RDN attribute.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 67}.
     */
    public static final ResultCode NOT_ALLOWED_ON_RDN = registerErrorResultCode(67,
            INFO_RESULT_NOT_ALLOWED_ON_RDN.get());

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in an entry that conflicts with an entry
     * that already exists.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 68}.
     */
    public static final ResultCode ENTRY_ALREADY_EXISTS = registerErrorResultCode(68,
            INFO_RESULT_ENTRY_ALREADY_EXISTS.get());

    /**
     * The result code that indicates that the operation could not be processed
     * because it would have modified the objectclasses associated with an entry
     * in an illegal manner.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 69}.
     */
    public static final ResultCode OBJECTCLASS_MODS_PROHIBITED = registerErrorResultCode(69,
            INFO_RESULT_OBJECTCLASS_MODS_PROHIBITED.get());

    /**
     * The result code that indicates that the operation could not be processed
     * because it would impact multiple DSAs or other repositories.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 71}.
     */
    public static final ResultCode AFFECTS_MULTIPLE_DSAS = registerErrorResultCode(71,
            INFO_RESULT_AFFECTS_MULTIPLE_DSAS.get());

    /**
     * The result code that indicates that the operation could not be processed
     * because there was an error while processing the virtual list view
     * control.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 76}.
     */
    public static final ResultCode VIRTUAL_LIST_VIEW_ERROR = registerErrorResultCode(76,
            INFO_RESULT_VIRTUAL_LIST_VIEW_ERROR.get());

    /**
     * The result code that should be used if no other result code is
     * appropriate.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 80}.
     */
    public static final ResultCode OTHER = registerErrorResultCode(80, INFO_RESULT_OTHER.get());

    /**
     * The client-side result code that indicates that a previously-established
     * connection to the server was lost. This is for client-side use only and
     * should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 81}.
     */
    public static final ResultCode CLIENT_SIDE_SERVER_DOWN = registerErrorResultCode(81,
            INFO_RESULT_CLIENT_SIDE_SERVER_DOWN.get());

    /**
     * The client-side result code that indicates that a local error occurred
     * that had nothing to do with interaction with the server. This is for
     * client-side use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 82}.
     */
    public static final ResultCode CLIENT_SIDE_LOCAL_ERROR = registerErrorResultCode(82,
            INFO_RESULT_CLIENT_SIDE_LOCAL_ERROR.get());

    /**
     * The client-side result code that indicates that an error occurred while
     * encoding a request to send to the server. This is for client-side use
     * only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 83}.
     */
    public static final ResultCode CLIENT_SIDE_ENCODING_ERROR = registerErrorResultCode(83,
            INFO_RESULT_CLIENT_SIDE_ENCODING_ERROR.get());

    /**
     * The client-side result code that indicates that an error occurred while
     * decoding a response from the server. This is for client-side use only and
     * should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 84}.
     */
    public static final ResultCode CLIENT_SIDE_DECODING_ERROR = registerErrorResultCode(84,
            INFO_RESULT_CLIENT_SIDE_DECODING_ERROR.get());

    /**
     * The client-side result code that indicates that the client did not
     * receive an expected response in a timely manner. This is for client-side
     * use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 85}.
     */
    public static final ResultCode CLIENT_SIDE_TIMEOUT = registerErrorResultCode(85,
            INFO_RESULT_CLIENT_SIDE_TIMEOUT.get());

    /**
     * The client-side result code that indicates that the user requested an
     * unknown or unsupported authentication mechanism. This is for client-side
     * use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 86}.
     */
    public static final ResultCode CLIENT_SIDE_AUTH_UNKNOWN = registerErrorResultCode(86,
            INFO_RESULT_CLIENT_SIDE_AUTH_UNKNOWN.get());

    /**
     * The client-side result code that indicates that the filter provided by
     * the user was malformed and could not be parsed. This is for client-side
     * use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 87}.
     */
    public static final ResultCode CLIENT_SIDE_FILTER_ERROR = registerErrorResultCode(87,
            INFO_RESULT_CLIENT_SIDE_FILTER_ERROR.get());

    /**
     * The client-side result code that indicates that the user cancelled an
     * operation. This is for client-side use only and should never be
     * transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 88}.
     */
    public static final ResultCode CLIENT_SIDE_USER_CANCELLED = registerErrorResultCode(88,
            INFO_RESULT_CLIENT_SIDE_USER_CANCELLED.get());

    /**
     * The client-side result code that indicates that there was a problem with
     * one or more of the parameters provided by the user. This is for
     * client-side use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 89}.
     */
    public static final ResultCode CLIENT_SIDE_PARAM_ERROR = registerErrorResultCode(89,
            INFO_RESULT_CLIENT_SIDE_PARAM_ERROR.get());

    /**
     * The client-side result code that indicates that the client application
     * was not able to allocate enough memory for the requested operation. This
     * is for client-side use only and should never be transferred over
     * protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 90}.
     */
    public static final ResultCode CLIENT_SIDE_NO_MEMORY = registerErrorResultCode(90,
            INFO_RESULT_CLIENT_SIDE_NO_MEMORY.get());

    /**
     * The client-side result code that indicates that the client was not able
     * to establish a connection to the server. This is for client-side use only
     * and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 91}.
     */
    public static final ResultCode CLIENT_SIDE_CONNECT_ERROR = registerErrorResultCode(91,
            INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get());

    /**
     * The client-side result code that indicates that the user requested an
     * operation that is not supported. This is for client-side use only and
     * should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 92}.
     */
    public static final ResultCode CLIENT_SIDE_NOT_SUPPORTED = registerErrorResultCode(92,
            INFO_RESULT_CLIENT_SIDE_NOT_SUPPORTED.get());

    /**
     * The client-side result code that indicates that the client expected a
     * control to be present in the response from the server but it was not
     * included. This is for client-side use only and should never be
     * transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 93}.
     */
    public static final ResultCode CLIENT_SIDE_CONTROL_NOT_FOUND = registerErrorResultCode(93,
            INFO_RESULT_CLIENT_SIDE_CONTROL_NOT_FOUND.get());

    /**
     * The client-side result code that indicates that the requested single
     * entry search operation or read operation failed because the Directory
     * Server did not return any matching entries. This is for client-side use
     * only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 94}.
     */
    public static final ResultCode CLIENT_SIDE_NO_RESULTS_RETURNED = registerErrorResultCode(94,
            INFO_RESULT_CLIENT_SIDE_NO_RESULTS_RETURNED.get());

    /**
     * The client-side result code that the requested single entry search
     * operation or read operation failed because the Directory Server returned
     * multiple matching entries (or search references) when only a single
     * matching entry was expected. This is for client-side use only and should
     * never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 95}.
     */
    public static final ResultCode CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED =
            registerErrorResultCode(95, INFO_RESULT_CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED.get());

    /**
     * The client-side result code that indicates that the client detected a
     * referral loop caused by servers referencing each other in a circular
     * manner. This is for client-side use only and should never be transferred
     * over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 96}.
     */
    public static final ResultCode CLIENT_SIDE_CLIENT_LOOP = registerErrorResultCode(96,
            INFO_RESULT_CLIENT_SIDE_CLIENT_LOOP.get());

    /**
     * The client-side result code that indicates that the client reached the
     * maximum number of hops allowed when attempting to follow a referral
     * (i.e., following one referral resulted in another referral which resulted
     * in another referral and so on). This is for client-side use only and
     * should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 97}.
     */
    public static final ResultCode CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED = registerErrorResultCode(
            97, INFO_RESULT_CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED.get());

    /**
     * The result code that indicates that a cancel request was successful, or
     * that the specified operation was canceled.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 118}.
     */
    public static final ResultCode CANCELLED = registerErrorResultCode(118, INFO_RESULT_CANCELED
            .get());

    /**
     * The result code that indicates that a cancel request was unsuccessful
     * because the targeted operation did not exist or had already completed.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 119}.
     */
    public static final ResultCode NO_SUCH_OPERATION = registerErrorResultCode(119,
            INFO_RESULT_NO_SUCH_OPERATION.get());

    /**
     * The result code that indicates that a cancel request was unsuccessful
     * because processing on the targeted operation had already reached a point
     * at which it could not be canceled.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 120}.
     */
    public static final ResultCode TOO_LATE = registerErrorResultCode(120, INFO_RESULT_TOO_LATE
            .get());

    /**
     * The result code that indicates that a cancel request was unsuccessful
     * because the targeted operation was one that could not be canceled.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 121}.
     */
    public static final ResultCode CANNOT_CANCEL = registerErrorResultCode(121,
            INFO_RESULT_CANNOT_CANCEL.get());

    /**
     * The result code that indicates that the filter contained in an assertion
     * control failed to match the target entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 122}.
     */
    public static final ResultCode ASSERTION_FAILED = registerErrorResultCode(122,
            INFO_RESULT_ASSERTION_FAILED.get());

    /**
     * The result code that should be used if the server will not allow the
     * client to use the requested authorization.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 123}.
     */
    public static final ResultCode AUTHORIZATION_DENIED = registerErrorResultCode(123,
            INFO_RESULT_AUTHORIZATION_DENIED.get());

    /**
     * The result code that should be used if the server did not actually
     * complete processing on the associated operation because the request
     * included the LDAP No-Op control.
     * <p>
     * This result code corresponds to the LDAP result code value of
     * {@code 16654}.
     */
    public static final ResultCode NO_OPERATION = registerSuccessResultCode(16654,
            INFO_RESULT_NO_OPERATION.get());

    /**
     * Returns the result code having the specified integer value as defined in
     * RFC 4511 section 4.1.9. If there is no result code associated with the
     * specified integer value then a temporary result code is automatically
     * created in order to handle cases where unexpected result codes are
     * returned from the server.
     *
     * @param intValue
     *            The integer value of the result code.
     * @return The result code.
     */
    public static ResultCode valueOf(final int intValue) {
        ResultCode resultCode = null;

        if (intValue >= 0 || intValue < ELEMENTS.length) {
            resultCode = ELEMENTS[intValue];
        }

        if (resultCode == null) {
            resultCode =
                    new ResultCode(intValue, LocalizableMessage.raw("undefined(" + intValue + ")"),
                            true);
        }

        return resultCode;
    }

    /**
     * Returns an unmodifiable list containing the set of available result codes
     * indexed on their integer value as defined in RFC 4511 section 4.1.9.
     *
     * @return An unmodifiable list containing the set of available result
     *         codes.
     */
    public static List<ResultCode> values() {
        return IMMUTABLE_ELEMENTS;
    }

    /**
     * Creates and registers a new error result code with the application.
     *
     * @param intValue
     *            The integer value of the error result code as defined in RFC
     *            4511 section 4.1.9.
     * @param name
     *            The name of the error result code.
     * @return The new error result code.
     */
    private static ResultCode registerErrorResultCode(final int intValue,
            final LocalizableMessage name) {
        final ResultCode t = new ResultCode(intValue, name, true);
        ELEMENTS[intValue] = t;
        return t;
    }

    /**
     * Creates and registers a new success result code with the application.
     *
     * @param intValue
     *            The integer value of the success result code as defined in RFC
     *            4511 section 4.1.9.
     * @param name
     *            The name of the success result code.
     * @return The new success result code.
     */
    private static ResultCode registerSuccessResultCode(final int intValue,
            final LocalizableMessage name) {
        final ResultCode t = new ResultCode(intValue, name, false);
        ELEMENTS[intValue] = t;
        return t;
    }

    private final int intValue;

    private final LocalizableMessage name;

    private final boolean exceptional;

    // Prevent direct instantiation.
    private ResultCode(final int intValue, final LocalizableMessage name, final boolean exceptional) {
        this.intValue = intValue;
        this.name = name;
        this.exceptional = exceptional;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ResultCode) {
            return this.intValue == ((ResultCode) obj).intValue;
        } else {
            return false;
        }
    }

    /**
     * Returns the short human-readable name of this result code.
     *
     * @return The short human-readable name of this result code.
     */
    public LocalizableMessage getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return intValue;
    }

    /**
     * Returns the integer value of this result code.
     *
     * @return The integer value of this result code.
     */
    public int intValue() {
        return intValue;
    }

    /**
     * Indicates whether or not this result code represents an error result.
     * <p>
     * The following result codes are NOT interpreted as error results:
     * <ul>
     * <li>{@link #SUCCESS}
     * <li>{@link #COMPARE_FALSE}
     * <li>{@link #COMPARE_TRUE}
     * <li>{@link #SASL_BIND_IN_PROGRESS}
     * <li>{@link #NO_OPERATION}
     * </ul>
     * In order to make it easier for application to detect referrals, the
     * {@link #REFERRAL} result code is interpreted as an error result (the LDAP
     * RFCs treat referrals as a success response).
     *
     * @return {@code true} if this result code represents an error result,
     *         otherwise {@code false}.
     */
    public boolean isExceptional() {
        return exceptional;
    }

    /**
     * Returns the string representation of this result code.
     *
     * @return The string representation of this result code.
     */
    @Override
    public String toString() {
        return name.toString();
    }

}
