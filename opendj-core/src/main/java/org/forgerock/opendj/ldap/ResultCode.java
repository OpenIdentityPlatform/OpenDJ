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
 *      Portions copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;

import static com.forgerock.opendj.ldap.CoreMessages.*;

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

    /**
     * Contains equivalent values for the ResultCode values.
     * This allows easily using ResultCode values with switch statements.
     */
    public static enum Enum {
        //@Checkstyle:off
        /** @see ResultCode#UNDEFINED */
        UNDEFINED,
        /** @see ResultCode#SUCCESS */
        SUCCESS,
        /** @see ResultCode#OPERATIONS_ERROR */
        OPERATIONS_ERROR,
        /** @see ResultCode#PROTOCOL_ERROR */
        PROTOCOL_ERROR,
        /** @see ResultCode#TIME_LIMIT_EXCEEDED */
        TIME_LIMIT_EXCEEDED,
        /** @see ResultCode#SIZE_LIMIT_EXCEEDED */
        SIZE_LIMIT_EXCEEDED,
        /** @see ResultCode#COMPARE_FALSE */
        COMPARE_FALSE,
        /** @see ResultCode#COMPARE_TRUE */
        COMPARE_TRUE,
        /** @see ResultCode#AUTH_METHOD_NOT_SUPPORTED */
        AUTH_METHOD_NOT_SUPPORTED,
        /** @see ResultCode#STRONG_AUTH_REQUIRED */
        STRONG_AUTH_REQUIRED,
        /** @see ResultCode#REFERRAL */
        REFERRAL,
        /** @see ResultCode#ADMIN_LIMIT_EXCEEDED */
        ADMIN_LIMIT_EXCEEDED,
        /** @see ResultCode#UNAVAILABLE_CRITICAL_EXTENSION */
        UNAVAILABLE_CRITICAL_EXTENSION,
        /** @see ResultCode#CONFIDENTIALITY_REQUIRED */
        CONFIDENTIALITY_REQUIRED,
        /** @see ResultCode#SASL_BIND_IN_PROGRESS */
        SASL_BIND_IN_PROGRESS,
        /** @see ResultCode#NO_SUCH_ATTRIBUTE */
        NO_SUCH_ATTRIBUTE,
        /** @see ResultCode#UNDEFINED_ATTRIBUTE_TYPE */
        UNDEFINED_ATTRIBUTE_TYPE,
        /** @see ResultCode#INAPPROPRIATE_MATCHING */
        INAPPROPRIATE_MATCHING,
        /** @see ResultCode#CONSTRAINT_VIOLATION */
        CONSTRAINT_VIOLATION,
        /** @see ResultCode#ATTRIBUTE_OR_VALUE_EXISTS */
        ATTRIBUTE_OR_VALUE_EXISTS,
        /** @see ResultCode#INVALID_ATTRIBUTE_SYNTAX */
        INVALID_ATTRIBUTE_SYNTAX,
        /** @see ResultCode#NO_SUCH_OBJECT */
        NO_SUCH_OBJECT,
        /** @see ResultCode#ALIAS_PROBLEM */
        ALIAS_PROBLEM,
        /** @see ResultCode#INVALID_DN_SYNTAX */
        INVALID_DN_SYNTAX,
        /** @see ResultCode#ALIAS_DEREFERENCING_PROBLEM */
        ALIAS_DEREFERENCING_PROBLEM,
        /** @see ResultCode#INAPPROPRIATE_AUTHENTICATION */
        INAPPROPRIATE_AUTHENTICATION,
        /** @see ResultCode#INVALID_CREDENTIALS */
        INVALID_CREDENTIALS,
        /** @see ResultCode#INSUFFICIENT_ACCESS_RIGHTS */
        INSUFFICIENT_ACCESS_RIGHTS,
        /** @see ResultCode#BUSY */
        BUSY,
        /** @see ResultCode#UNAVAILABLE */
        UNAVAILABLE,
        /** @see ResultCode#UNWILLING_TO_PERFORM */
        UNWILLING_TO_PERFORM,
        /** @see ResultCode#LOOP_DETECT */
        LOOP_DETECT,
        /** @see ResultCode#SORT_CONTROL_MISSING */
        SORT_CONTROL_MISSING,
        /** @see ResultCode#OFFSET_RANGE_ERROR */
        OFFSET_RANGE_ERROR,
        /** @see ResultCode#NAMING_VIOLATION */
        NAMING_VIOLATION,
        /** @see ResultCode#OBJECTCLASS_VIOLATION */
        OBJECTCLASS_VIOLATION,
        /** @see ResultCode#NOT_ALLOWED_ON_NONLEAF */
        NOT_ALLOWED_ON_NONLEAF,
        /** @see ResultCode#NOT_ALLOWED_ON_RDN */
        NOT_ALLOWED_ON_RDN,
        /** @see ResultCode#ENTRY_ALREADY_EXISTS */
        ENTRY_ALREADY_EXISTS,
        /** @see ResultCode#OBJECTCLASS_MODS_PROHIBITED */
        OBJECTCLASS_MODS_PROHIBITED,
        /** @see ResultCode#AFFECTS_MULTIPLE_DSAS */
        AFFECTS_MULTIPLE_DSAS,
        /** @see ResultCode#VIRTUAL_LIST_VIEW_ERROR */
        VIRTUAL_LIST_VIEW_ERROR,
        /** @see ResultCode#OTHER */
        OTHER,
        /** @see ResultCode#CLIENT_SIDE_SERVER_DOWN */
        CLIENT_SIDE_SERVER_DOWN,
        /** @see ResultCode#CLIENT_SIDE_LOCAL_ERROR */
        CLIENT_SIDE_LOCAL_ERROR,
        /** @see ResultCode#CLIENT_SIDE_ENCODING_ERROR */
        CLIENT_SIDE_ENCODING_ERROR,
        /** @see ResultCode#CLIENT_SIDE_DECODING_ERROR */
        CLIENT_SIDE_DECODING_ERROR,
        /** @see ResultCode#CLIENT_SIDE_TIMEOUT */
        CLIENT_SIDE_TIMEOUT,
        /** @see ResultCode#CLIENT_SIDE_AUTH_UNKNOWN */
        CLIENT_SIDE_AUTH_UNKNOWN,
        /** @see ResultCode#CLIENT_SIDE_FILTER_ERROR */
        CLIENT_SIDE_FILTER_ERROR,
        /** @see ResultCode#CLIENT_SIDE_USER_CANCELLED */
        CLIENT_SIDE_USER_CANCELLED,
        /** @see ResultCode#CLIENT_SIDE_PARAM_ERROR */
        CLIENT_SIDE_PARAM_ERROR,
        /** @see ResultCode#CLIENT_SIDE_NO_MEMORY */
        CLIENT_SIDE_NO_MEMORY,
        /** @see ResultCode#CLIENT_SIDE_CONNECT_ERROR */
        CLIENT_SIDE_CONNECT_ERROR,
        /** @see ResultCode#CLIENT_SIDE_NOT_SUPPORTED */
        CLIENT_SIDE_NOT_SUPPORTED,
        /** @see ResultCode#CLIENT_SIDE_CONTROL_NOT_FOUND */
        CLIENT_SIDE_CONTROL_NOT_FOUND,
        /** @see ResultCode#CLIENT_SIDE_NO_RESULTS_RETURNED */
        CLIENT_SIDE_NO_RESULTS_RETURNED,
        /** @see ResultCode#CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED */
        CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
        /** @see ResultCode#CLIENT_SIDE_CLIENT_LOOP */
        CLIENT_SIDE_CLIENT_LOOP,
        /** @see ResultCode#CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED */
        CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED,
        /** @see ResultCode#CANCELLED */
        CANCELLED,
        /** @see ResultCode#NO_SUCH_OPERATION */
        NO_SUCH_OPERATION,
        /** @see ResultCode#TOO_LATE */
        TOO_LATE,
        /** @see ResultCode#CANNOT_CANCEL */
        CANNOT_CANCEL,
        /** @see ResultCode#ASSERTION_FAILED */
        ASSERTION_FAILED,
        /** @see ResultCode#AUTHORIZATION_DENIED */
        AUTHORIZATION_DENIED,
        /** @see ResultCode#NO_OPERATION */
        NO_OPERATION,
        /** Used for unknown search scopes. */
        UNKNOWN;
        //@Checkstyle:on
    }

    private static final Map<Integer, ResultCode> ELEMENTS = new LinkedHashMap<>();

    /**
     * The result code that should only be used if the actual result code has
     * not yet been determined.
     * <p>
     * Despite not being a standard result code, it is an implementation of the
     * null object design pattern for this type.
     */
    public static final ResultCode UNDEFINED = registerErrorResultCode(-1,
            INFO_RESULT_UNDEFINED.get(), Enum.UNDEFINED);

    /**
     * The result code that indicates that the operation completed successfully.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 0}.
     */
    public static final ResultCode SUCCESS =
            registerSuccessResultCode(0, INFO_RESULT_SUCCESS.get(), Enum.SUCCESS);

    /**
     * The result code that indicates that an internal error prevented the
     * operation from being processed properly.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 1}.
     */
    public static final ResultCode OPERATIONS_ERROR = registerErrorResultCode(1,
            INFO_RESULT_OPERATIONS_ERROR.get(), Enum.OPERATIONS_ERROR);

    /**
     * The result code that indicates that the client sent a malformed or
     * illegal request to the server.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 2}.
     */
    public static final ResultCode PROTOCOL_ERROR = registerErrorResultCode(2,
            INFO_RESULT_PROTOCOL_ERROR.get(), Enum.PROTOCOL_ERROR);

    /**
     * The result code that indicates that a time limit was exceeded while
     * attempting to process the request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 3}.
     */
    public static final ResultCode TIME_LIMIT_EXCEEDED = registerErrorResultCode(3,
            INFO_RESULT_TIME_LIMIT_EXCEEDED.get(), Enum.TIME_LIMIT_EXCEEDED);

    /**
     * The result code that indicates that a size limit was exceeded while
     * attempting to process the request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 4}.
     */
    public static final ResultCode SIZE_LIMIT_EXCEEDED = registerErrorResultCode(4,
            INFO_RESULT_SIZE_LIMIT_EXCEEDED.get(), Enum.SIZE_LIMIT_EXCEEDED);

    /**
     * The result code that indicates that the attribute value assertion
     * included in a compare request did not match the targeted entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 5}.
     */
    public static final ResultCode COMPARE_FALSE = registerSuccessResultCode(5,
            INFO_RESULT_COMPARE_FALSE.get(), Enum.COMPARE_FALSE);

    /**
     * The result code that indicates that the attribute value assertion
     * included in a compare request did match the targeted entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 6}.
     */
    public static final ResultCode COMPARE_TRUE = registerSuccessResultCode(6,
            INFO_RESULT_COMPARE_TRUE.get(), Enum.COMPARE_TRUE);

    /**
     * The result code that indicates that the requested authentication attempt
     * failed because it referenced an invalid SASL mechanism.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 7}.
     */
    public static final ResultCode AUTH_METHOD_NOT_SUPPORTED = registerErrorResultCode(7,
            INFO_RESULT_AUTH_METHOD_NOT_SUPPORTED.get(), Enum.AUTH_METHOD_NOT_SUPPORTED);

    /**
     * The result code that indicates that the requested operation could not be
     * processed because it requires that the client has completed a strong form
     * of authentication.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 8}.
     */
    public static final ResultCode STRONG_AUTH_REQUIRED = registerErrorResultCode(8,
            INFO_RESULT_STRONG_AUTH_REQUIRED.get(), Enum.STRONG_AUTH_REQUIRED);

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
            .get(), Enum.REFERRAL);

    /**
     * The result code that indicates that processing on the requested operation
     * could not continue because an administrative limit was exceeded.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 11}.
     */
    public static final ResultCode ADMIN_LIMIT_EXCEEDED = registerErrorResultCode(11,
            INFO_RESULT_ADMIN_LIMIT_EXCEEDED.get(), Enum.ADMIN_LIMIT_EXCEEDED);

    /**
     * The result code that indicates that the requested operation failed
     * because it included a critical extension that is unsupported or
     * inappropriate for that request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 12}.
     */
    public static final ResultCode UNAVAILABLE_CRITICAL_EXTENSION = registerErrorResultCode(12,
            INFO_RESULT_UNAVAILABLE_CRITICAL_EXTENSION.get(), Enum.UNAVAILABLE_CRITICAL_EXTENSION);

    /**
     * The result code that indicates that the requested operation could not be
     * processed because it requires confidentiality for the communication
     * between the client and the server.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 13}.
     */
    public static final ResultCode CONFIDENTIALITY_REQUIRED = registerErrorResultCode(13,
            INFO_RESULT_CONFIDENTIALITY_REQUIRED.get(), Enum.CONFIDENTIALITY_REQUIRED);

    /**
     * The result code that should be used for intermediate responses in
     * multi-stage SASL bind operations.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 14}.
     */
    public static final ResultCode SASL_BIND_IN_PROGRESS = registerSuccessResultCode(14,
            INFO_RESULT_SASL_BIND_IN_PROGRESS.get(), Enum.SASL_BIND_IN_PROGRESS);

    /**
     * The result code that indicates that the requested operation failed
     * because it targeted an attribute or attribute value that did not exist in
     * the specified entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 16}.
     */
    public static final ResultCode NO_SUCH_ATTRIBUTE = registerErrorResultCode(16,
            INFO_RESULT_NO_SUCH_ATTRIBUTE.get(), Enum.NO_SUCH_ATTRIBUTE);

    /**
     * The result code that indicates that the requested operation failed
     * because it referenced an attribute that is not defined in the server
     * schema.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 17}.
     */
    public static final ResultCode UNDEFINED_ATTRIBUTE_TYPE = registerErrorResultCode(17,
            INFO_RESULT_UNDEFINED_ATTRIBUTE_TYPE.get(), Enum.UNDEFINED_ATTRIBUTE_TYPE);

    /**
     * The result code that indicates that the requested operation failed
     * because it attempted to perform an inappropriate type of matching against
     * an attribute.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 18}.
     */
    public static final ResultCode INAPPROPRIATE_MATCHING = registerErrorResultCode(18,
            INFO_RESULT_INAPPROPRIATE_MATCHING.get(), Enum.INAPPROPRIATE_MATCHING);

    /**
     * The result code that indicates that the requested operation failed
     * because it would have violated some constraint defined in the server.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 19}.
     */
    public static final ResultCode CONSTRAINT_VIOLATION = registerErrorResultCode(19,
            INFO_RESULT_CONSTRAINT_VIOLATION.get(), Enum.CONSTRAINT_VIOLATION);

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in a conflict with an existing attribute
     * or attribute value in the target entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 20}.
     */
    public static final ResultCode ATTRIBUTE_OR_VALUE_EXISTS = registerErrorResultCode(20,
            INFO_RESULT_ATTRIBUTE_OR_VALUE_EXISTS.get(), Enum.ATTRIBUTE_OR_VALUE_EXISTS);

    /**
     * The result code that indicates that the requested operation failed
     * because it violated the syntax for a specified attribute.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 21}.
     */
    public static final ResultCode INVALID_ATTRIBUTE_SYNTAX = registerErrorResultCode(21,
            INFO_RESULT_INVALID_ATTRIBUTE_SYNTAX.get(), Enum.INVALID_ATTRIBUTE_SYNTAX);

    /**
     * The result code that indicates that the requested operation failed
     * because it referenced an entry that does not exist.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 32}.
     */
    public static final ResultCode NO_SUCH_OBJECT = registerErrorResultCode(32,
            INFO_RESULT_NO_SUCH_OBJECT.get(), Enum.NO_SUCH_OBJECT);

    /**
     * The result code that indicates that the requested operation failed
     * because it attempted to perform an illegal operation on an alias.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 33}.
     */
    public static final ResultCode ALIAS_PROBLEM = registerErrorResultCode(33,
            INFO_RESULT_ALIAS_PROBLEM.get(), Enum.ALIAS_PROBLEM);

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in an entry with an invalid or malformed
     * DN.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 34}.
     */
    public static final ResultCode INVALID_DN_SYNTAX = registerErrorResultCode(34,
            INFO_RESULT_INVALID_DN_SYNTAX.get(), Enum.INVALID_DN_SYNTAX);

    /**
     * The result code that indicates that a problem was encountered while
     * attempting to dereference an alias for a search operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 36}.
     */
    public static final ResultCode ALIAS_DEREFERENCING_PROBLEM = registerErrorResultCode(36,
            INFO_RESULT_ALIAS_DEREFERENCING_PROBLEM.get(), Enum.ALIAS_DEREFERENCING_PROBLEM);

    /**
     * The result code that indicates that an authentication attempt failed
     * because the requested type of authentication was not appropriate for the
     * targeted entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 48}.
     */
    public static final ResultCode INAPPROPRIATE_AUTHENTICATION = registerErrorResultCode(48,
            INFO_RESULT_INAPPROPRIATE_AUTHENTICATION.get(), Enum.INAPPROPRIATE_AUTHENTICATION);

    /**
     * The result code that indicates that an authentication attempt failed
     * because the user did not provide a valid set of credentials.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 49}.
     */
    public static final ResultCode INVALID_CREDENTIALS = registerErrorResultCode(49,
            INFO_RESULT_INVALID_CREDENTIALS.get(), Enum.INVALID_CREDENTIALS);

    /**
     * The result code that indicates that the client does not have sufficient
     * permission to perform the requested operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 50}.
     */
    public static final ResultCode INSUFFICIENT_ACCESS_RIGHTS = registerErrorResultCode(50,
            INFO_RESULT_INSUFFICIENT_ACCESS_RIGHTS.get(), Enum.INSUFFICIENT_ACCESS_RIGHTS);

    /**
     * The result code that indicates that the server is too busy to process the
     * requested operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 51}.
     */
    public static final ResultCode BUSY = registerErrorResultCode(51, INFO_RESULT_BUSY.get(), Enum.BUSY);

    /**
     * The result code that indicates that either the entire server or one or
     * more required resources were not available for use in processing the
     * request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 52}.
     */
    public static final ResultCode UNAVAILABLE = registerErrorResultCode(52,
            INFO_RESULT_UNAVAILABLE.get(), Enum.UNAVAILABLE);

    /**
     * The result code that indicates that the server is unwilling to perform
     * the requested operation.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 53}.
     */
    public static final ResultCode UNWILLING_TO_PERFORM = registerErrorResultCode(53,
            INFO_RESULT_UNWILLING_TO_PERFORM.get(), Enum.UNWILLING_TO_PERFORM);

    /**
     * The result code that indicates that a referral or chaining loop was
     * detected while processing the request.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 54}.
     */
    public static final ResultCode LOOP_DETECT = registerErrorResultCode(54,
            INFO_RESULT_LOOP_DETECT.get(), Enum.LOOP_DETECT);

    /**
     * The result code that indicates that a search request included a VLV
     * request control without a server-side sort control.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 60}.
     */
    public static final ResultCode SORT_CONTROL_MISSING = registerErrorResultCode(60,
            INFO_RESULT_SORT_CONTROL_MISSING.get(), Enum.SORT_CONTROL_MISSING);

    /**
     * The result code that indicates that a search request included a VLV
     * request control with an invalid offset.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 61}.
     */
    public static final ResultCode OFFSET_RANGE_ERROR = registerErrorResultCode(61,
            INFO_RESULT_OFFSET_RANGE_ERROR.get(), Enum.OFFSET_RANGE_ERROR);

    /**
     * The result code that indicates that the requested operation failed
     * because it would have violated the server's naming configuration.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 64}.
     */
    public static final ResultCode NAMING_VIOLATION = registerErrorResultCode(64,
            INFO_RESULT_NAMING_VIOLATION.get(), Enum.NAMING_VIOLATION);

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in an entry that violated the server
     * schema.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 65}.
     */
    public static final ResultCode OBJECTCLASS_VIOLATION = registerErrorResultCode(65,
            INFO_RESULT_OBJECTCLASS_VIOLATION.get(), Enum.OBJECTCLASS_VIOLATION);

    /**
     * The result code that indicates that the requested operation is not
     * allowed for non-leaf entries.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 66}.
     */
    public static final ResultCode NOT_ALLOWED_ON_NONLEAF = registerErrorResultCode(66,
            INFO_RESULT_NOT_ALLOWED_ON_NONLEAF.get(), Enum.NOT_ALLOWED_ON_NONLEAF);

    /**
     * The result code that indicates that the requested operation is not
     * allowed on an RDN attribute.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 67}.
     */
    public static final ResultCode NOT_ALLOWED_ON_RDN = registerErrorResultCode(67,
            INFO_RESULT_NOT_ALLOWED_ON_RDN.get(), Enum.NOT_ALLOWED_ON_RDN);

    /**
     * The result code that indicates that the requested operation failed
     * because it would have resulted in an entry that conflicts with an entry
     * that already exists.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 68}.
     */
    public static final ResultCode ENTRY_ALREADY_EXISTS = registerErrorResultCode(68,
            INFO_RESULT_ENTRY_ALREADY_EXISTS.get(), Enum.ENTRY_ALREADY_EXISTS);

    /**
     * The result code that indicates that the operation could not be processed
     * because it would have modified the objectclasses associated with an entry
     * in an illegal manner.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 69}.
     */
    public static final ResultCode OBJECTCLASS_MODS_PROHIBITED = registerErrorResultCode(69,
            INFO_RESULT_OBJECTCLASS_MODS_PROHIBITED.get(), Enum.OBJECTCLASS_MODS_PROHIBITED);

    /**
     * The result code that indicates that the operation could not be processed
     * because it would impact multiple DSAs or other repositories.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 71}.
     */
    public static final ResultCode AFFECTS_MULTIPLE_DSAS = registerErrorResultCode(71,
            INFO_RESULT_AFFECTS_MULTIPLE_DSAS.get(), Enum.AFFECTS_MULTIPLE_DSAS);

    /**
     * The result code that indicates that the operation could not be processed
     * because there was an error while processing the virtual list view
     * control.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 76}.
     */
    public static final ResultCode VIRTUAL_LIST_VIEW_ERROR = registerErrorResultCode(76,
            INFO_RESULT_VIRTUAL_LIST_VIEW_ERROR.get(), Enum.VIRTUAL_LIST_VIEW_ERROR);

    /**
     * The result code that should be used if no other result code is
     * appropriate.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 80}.
     */
    public static final ResultCode OTHER = registerErrorResultCode(80, INFO_RESULT_OTHER.get(), Enum.OTHER);

    /**
     * The client-side result code that indicates that a previously-established
     * connection to the server was lost. This is for client-side use only and
     * should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 81}.
     */
    public static final ResultCode CLIENT_SIDE_SERVER_DOWN = registerErrorResultCode(81,
            INFO_RESULT_CLIENT_SIDE_SERVER_DOWN.get(), Enum.CLIENT_SIDE_SERVER_DOWN);

    /**
     * The client-side result code that indicates that a local error occurred
     * that had nothing to do with interaction with the server. This is for
     * client-side use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 82}.
     */
    public static final ResultCode CLIENT_SIDE_LOCAL_ERROR = registerErrorResultCode(82,
            INFO_RESULT_CLIENT_SIDE_LOCAL_ERROR.get(), Enum.CLIENT_SIDE_LOCAL_ERROR);

    /**
     * The client-side result code that indicates that an error occurred while
     * encoding a request to send to the server. This is for client-side use
     * only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 83}.
     */
    public static final ResultCode CLIENT_SIDE_ENCODING_ERROR = registerErrorResultCode(83,
            INFO_RESULT_CLIENT_SIDE_ENCODING_ERROR.get(), Enum.CLIENT_SIDE_ENCODING_ERROR);

    /**
     * The client-side result code that indicates that an error occurred while
     * decoding a response from the server. This is for client-side use only and
     * should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 84}.
     */
    public static final ResultCode CLIENT_SIDE_DECODING_ERROR = registerErrorResultCode(84,
            INFO_RESULT_CLIENT_SIDE_DECODING_ERROR.get(), Enum.CLIENT_SIDE_DECODING_ERROR);

    /**
     * The client-side result code that indicates that the client did not
     * receive an expected response in a timely manner. This is for client-side
     * use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 85}.
     */
    public static final ResultCode CLIENT_SIDE_TIMEOUT = registerErrorResultCode(85,
            INFO_RESULT_CLIENT_SIDE_TIMEOUT.get(), Enum.CLIENT_SIDE_TIMEOUT);

    /**
     * The client-side result code that indicates that the user requested an
     * unknown or unsupported authentication mechanism. This is for client-side
     * use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 86}.
     */
    public static final ResultCode CLIENT_SIDE_AUTH_UNKNOWN = registerErrorResultCode(86,
            INFO_RESULT_CLIENT_SIDE_AUTH_UNKNOWN.get(), Enum.CLIENT_SIDE_AUTH_UNKNOWN);

    /**
     * The client-side result code that indicates that the filter provided by
     * the user was malformed and could not be parsed. This is for client-side
     * use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 87}.
     */
    public static final ResultCode CLIENT_SIDE_FILTER_ERROR = registerErrorResultCode(87,
            INFO_RESULT_CLIENT_SIDE_FILTER_ERROR.get(), Enum.CLIENT_SIDE_FILTER_ERROR);

    /**
     * The client-side result code that indicates that the user cancelled an
     * operation. This is for client-side use only and should never be
     * transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 88}.
     */
    public static final ResultCode CLIENT_SIDE_USER_CANCELLED = registerErrorResultCode(88,
            INFO_RESULT_CLIENT_SIDE_USER_CANCELLED.get(), Enum.CLIENT_SIDE_USER_CANCELLED);

    /**
     * The client-side result code that indicates that there was a problem with
     * one or more of the parameters provided by the user. This is for
     * client-side use only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 89}.
     */
    public static final ResultCode CLIENT_SIDE_PARAM_ERROR = registerErrorResultCode(89,
            INFO_RESULT_CLIENT_SIDE_PARAM_ERROR.get(), Enum.CLIENT_SIDE_PARAM_ERROR);

    /**
     * The client-side result code that indicates that the client application
     * was not able to allocate enough memory for the requested operation. This
     * is for client-side use only and should never be transferred over
     * protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 90}.
     */
    public static final ResultCode CLIENT_SIDE_NO_MEMORY = registerErrorResultCode(90,
            INFO_RESULT_CLIENT_SIDE_NO_MEMORY.get(), Enum.CLIENT_SIDE_NO_MEMORY);

    /**
     * The client-side result code that indicates that the client was not able
     * to establish a connection to the server. This is for client-side use only
     * and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 91}.
     */
    public static final ResultCode CLIENT_SIDE_CONNECT_ERROR = registerErrorResultCode(91,
            INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get(), Enum.CLIENT_SIDE_CONNECT_ERROR);

    /**
     * The client-side result code that indicates that the user requested an
     * operation that is not supported. This is for client-side use only and
     * should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 92}.
     */
    public static final ResultCode CLIENT_SIDE_NOT_SUPPORTED = registerErrorResultCode(92,
            INFO_RESULT_CLIENT_SIDE_NOT_SUPPORTED.get(), Enum.CLIENT_SIDE_NOT_SUPPORTED);

    /**
     * The client-side result code that indicates that the client expected a
     * control to be present in the response from the server but it was not
     * included. This is for client-side use only and should never be
     * transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 93}.
     */
    public static final ResultCode CLIENT_SIDE_CONTROL_NOT_FOUND = registerErrorResultCode(93,
            INFO_RESULT_CLIENT_SIDE_CONTROL_NOT_FOUND.get(), Enum.CLIENT_SIDE_CONTROL_NOT_FOUND);

    /**
     * The client-side result code that indicates that the requested single
     * entry search operation or read operation failed because the Directory
     * Server did not return any matching entries. This is for client-side use
     * only and should never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 94}.
     */
    public static final ResultCode CLIENT_SIDE_NO_RESULTS_RETURNED = registerErrorResultCode(94,
            INFO_RESULT_CLIENT_SIDE_NO_RESULTS_RETURNED.get(), Enum.CLIENT_SIDE_NO_RESULTS_RETURNED);

    /**
     * The client-side result code that the requested single entry search
     * operation or read operation failed because the Directory Server returned
     * multiple matching entries (or search references) when only a single
     * matching entry was expected. This is for client-side use only and should
     * never be transferred over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 95}.
     */
    public static final ResultCode CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED = registerErrorResultCode(95,
            INFO_RESULT_CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED.get(), Enum.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED);

    /**
     * The client-side result code that indicates that the client detected a
     * referral loop caused by servers referencing each other in a circular
     * manner. This is for client-side use only and should never be transferred
     * over protocol.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 96}.
     */
    public static final ResultCode CLIENT_SIDE_CLIENT_LOOP = registerErrorResultCode(96,
            INFO_RESULT_CLIENT_SIDE_CLIENT_LOOP.get(), Enum.CLIENT_SIDE_CLIENT_LOOP);

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
            97, INFO_RESULT_CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED.get(), Enum.CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED);

    /**
     * The result code that indicates that a cancel request was successful, or
     * that the specified operation was canceled.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 118}.
     */
    public static final ResultCode CANCELLED = registerErrorResultCode(118, INFO_RESULT_CANCELED
            .get(), Enum.CANCELLED);

    /**
     * The result code that indicates that a cancel request was unsuccessful
     * because the targeted operation did not exist or had already completed.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 119}.
     */
    public static final ResultCode NO_SUCH_OPERATION = registerErrorResultCode(119,
            INFO_RESULT_NO_SUCH_OPERATION.get(), Enum.NO_SUCH_OPERATION);

    /**
     * The result code that indicates that a cancel request was unsuccessful
     * because processing on the targeted operation had already reached a point
     * at which it could not be canceled.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 120}.
     */
    public static final ResultCode TOO_LATE = registerErrorResultCode(120, INFO_RESULT_TOO_LATE
            .get(), Enum.TOO_LATE);

    /**
     * The result code that indicates that a cancel request was unsuccessful
     * because the targeted operation was one that could not be canceled.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 121}.
     */
    public static final ResultCode CANNOT_CANCEL = registerErrorResultCode(121,
            INFO_RESULT_CANNOT_CANCEL.get(), Enum.CANNOT_CANCEL);

    /**
     * The result code that indicates that the filter contained in an assertion
     * control failed to match the target entry.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 122}.
     */
    public static final ResultCode ASSERTION_FAILED = registerErrorResultCode(122,
            INFO_RESULT_ASSERTION_FAILED.get(), Enum.ASSERTION_FAILED);

    /**
     * The result code that should be used if the server will not allow the
     * client to use the requested authorization.
     * <p>
     * This result code corresponds to the LDAP result code value of {@code 123}.
     */
    public static final ResultCode AUTHORIZATION_DENIED = registerErrorResultCode(123,
            INFO_RESULT_AUTHORIZATION_DENIED.get(), Enum.AUTHORIZATION_DENIED);

    /**
     * The result code that should be used if the server did not actually
     * complete processing on the associated operation because the request
     * included the LDAP No-Op control.
     * <p>
     * This result code corresponds to the LDAP result code value of
     * {@code 16654}.
     */
    public static final ResultCode NO_OPERATION = registerSuccessResultCode(16654,
            INFO_RESULT_NO_OPERATION.get(), Enum.NO_OPERATION);

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
        ResultCode result = ELEMENTS.get(intValue);
        if (result == null) {
            result = new ResultCode(
                intValue, LocalizableMessage.raw("unknown(" + intValue + ")"), true, Enum.UNKNOWN);
        }
        return result;
    }

    private static final List<ResultCode> IMMUTABLE_ELEMENTS = Collections.unmodifiableList(new ArrayList<ResultCode>(
            ELEMENTS.values()));

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
     * @param resultCodeEnum
     *            The enum equivalent for this result code
     * @return The new error result code.
     */
    private static ResultCode registerErrorResultCode(final int intValue,
            final LocalizableMessage name, final Enum resultCodeEnum) {
        final ResultCode t = new ResultCode(intValue, name, true, resultCodeEnum);
        ELEMENTS.put(intValue, t);
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
     * @param resultCodeEnum
     *            The enum equivalent for this result code
     * @return The new success result code.
     */
    private static ResultCode registerSuccessResultCode(final int intValue,
            final LocalizableMessage name, final Enum resultCodeEnum) {
        final ResultCode t = new ResultCode(intValue, name, false, resultCodeEnum);
        ELEMENTS.put(intValue, t);
        return t;
    }

    private final int intValue;

    private final LocalizableMessage name;

    private final boolean exceptional;

    private final Enum resultCodeEnum;

    /** Prevent direct instantiation. */
    private ResultCode(final int intValue, final LocalizableMessage name, final boolean exceptional,
            final Enum resultCodeEnum) {
        this.intValue = intValue;
        this.name = name;
        this.exceptional = exceptional;
        this.resultCodeEnum = resultCodeEnum;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
     * Returns the enum equivalent for this result code.
     *
     * @return The enum equivalent for this result code when a known mapping exists,
     *         or {@link Enum#UNKNOWN} if this is an unknown result code.
     */
    public Enum asEnum() {
        return this.resultCodeEnum;
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
