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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;
import org.forgerock.i18n.LocalizableMessage;

import static org.opends.messages.CoreMessages.*;
/**
 * This class defines a set of constants that correspond to the result codes
 * defined in the LDAP protocol.  Note that many (but not all) of the result
 * codes numbered 81 and higher come from the LDAP C API specification and are
 * only intended for client-side use and should not be returned from the
 * Directory Server.  These are denoted with a "CLIENT_SIDE_" prefix.
 */
public class LDAPResultCode
{
  /** The LDAP result code for successful operations. */
  public static final int SUCCESS = 0;

  /** The LDAP result code for operations that fail due to an operations error. */
  public static final int OPERATIONS_ERROR = 1;

  /** The LDAP result code for operations that fail due to a protocol error. */
  public static final int PROTOCOL_ERROR = 2;

  /** The LDAP result code for operations that fail as a result of exceeding a time limit. */
  public static final int TIME_LIMIT_EXCEEDED = 3;

  /** The LDAP result code for operations that fail as a result of exceeding a size limit. */
  public static final int SIZE_LIMIT_EXCEEDED = 4;

  /** The LDAP result code for compare operations in which the assertion is false. */
  public static final int COMPARE_FALSE = 5;

  /** The LDAP result code for compare operations in which the assertion is true. */
  public static final int COMPARE_TRUE = 6;

  /**
   * The LDAP result code for operations that fail because the requested
   * authentication method is not supported.
   */
  public static final int AUTH_METHOD_NOT_SUPPORTED = 7;

  /** The LDAP result code for operations that fail because strong authentication is required. */
  public static final int STRONG_AUTH_REQUIRED = 8;

  /** The LDAP result code for operations that encountered a referral. */
  public static final int REFERRAL = 10;

  /**
   * The LDAP result code for operations that fail as a result of exceeding an
   * administrative limit.
   */
  public static final int ADMIN_LIMIT_EXCEEDED = 11;

  /**
   * The LDAP result code for operations that fail because they contain an
   * unavailable critical extension.
   */
  public static final int UNAVAILABLE_CRITICAL_EXTENSION = 12;

  /** The LDAP result code for operations that fail because confidentiality is required. */
  public static final int CONFIDENTIALITY_REQUIRED = 13;

  /** The LDAP result code used for multi-stage SASL bind operations that are not yet complete. */
  public static final int SASL_BIND_IN_PROGRESS = 14;

  /** The LDAP result code for operations that fail because a specified attribute does not exist. */
  public static final int NO_SUCH_ATTRIBUTE = 16;

  /**
   * The LDAP result code for operations that fail because a specified attribute
   * type is not defined in the server schema.
   */
  public static final int UNDEFINED_ATTRIBUTE_TYPE = 17;

  /**
   * The LDAP result code for operations that fail as a result of attempting an
   * inappropriate form of matching on an attribute.
   */
  public static final int INAPPROPRIATE_MATCHING = 18;

  /**
   * The LDAP result code for operations that fail because a defined constraint
   * has been violated.
   */
  public static final int CONSTRAINT_VIOLATION = 19;

  /**
   * The LDAP result code for operations that fail because of a conflict with an
   * existing attribute or value.
   */
  public static final int ATTRIBUTE_OR_VALUE_EXISTS = 20;

  /** The LDAP result code for operations that fail because of an invalid attribute syntax. */
  public static final int INVALID_ATTRIBUTE_SYNTAX = 21;

  /** The LDAP result code for operations that fail because a targeted entry does not exist. */
  public static final int NO_SUCH_OBJECT = 32;

  /**
   * The LDAP result code for operations that fail because the an alias was
   * encountered in an illegal context.
   */
  public static final int ALIAS_PROBLEM = 33;

  /** The LDAP result code for operations that fail because the request included a malformed DN. */
  public static final int INVALID_DN_SYNTAX = 34;

  /**
   * The LDAP result code for operations that fail because a problem occurred
   * while attempting to dereference an alias.
   */
  public static final int ALIAS_DEREFERENCING_PROBLEM = 36;

  /**
   * The LDAP result code for operations that fail because the user attempted to
   * perform a type of authentication that was inappropriate for the targeted
   * entry.
   */
  public static final int INAPPROPRIATE_AUTHENTICATION = 48;

  /**
   * The LDAP result code for operations that fail because the user supplied
   * invalid credentials for an authentication attempt.
   */
  public static final int INVALID_CREDENTIALS = 49;

  /**
   * The LDAP result code for operations that fail because the client does not
   * have permission to perform the requested operation.
   */
  public static final int INSUFFICIENT_ACCESS_RIGHTS = 50;

  /** The LDAP result code for operations that fail because the server was too busy to process it. */
  public static final int BUSY = 51;

  /**
   * The LDAP result code for operations that fail because the server or a
   * required resource was unavailable.
   */
  public static final int UNAVAILABLE = 52;

  /**
   * The LDAP result code for operations that fail because the server was
   * unwilling to perform the requested operation.
   */
  public static final int UNWILLING_TO_PERFORM = 53;

  /**
   * The LDAP result code for operations that fail because a referral or
   * chaining loop was detected.
   */
  public static final int LOOP_DETECT = 54;

  /**
   * The LDAP result code for operations that fail because the request included
   * a VLV request control without a server-side sort control.
   */
  public static final int SORT_CONTROL_MISSING = 60;

  /**
   * The LDAP result code for operations that fail because the request included
   * a VLV request control with an invalid offset.
   */
  public static final int OFFSET_RANGE_ERROR = 61;

  /** The LDAP result code for operations that fail due to a naming violation. */
  public static final int NAMING_VIOLATION = 64;

  /**
   * The LDAP result code for operations that fail because the requested
   * operation would have resulted in an entry that violates the server schema.
   */
  public static final int OBJECTCLASS_VIOLATION = 65;

  /**
   * The LDAP result code for operations that fail because the requested
   * operation is not allowed on non-leaf entries.
   */
  public static final int NOT_ALLOWED_ON_NONLEAF = 66;

  /**
   * The LDAP result code for operations that fail because the requested
   * operation is not allowed on an RDN attribute.
   */
  public static final int NOT_ALLOWED_ON_RDN = 67;

  /**
   * The LDAP result code for operations that fail because the requested
   * operation would have resulted in an entry that conflicts with one that
   * already exists.
   */
  public static final int ENTRY_ALREADY_EXISTS = 68;

  /**
   * The LDAP result code for operations that fail because the requested
   * operation attempted to modify objectclass values in an illegal manner.
   */
  public static final int OBJECTCLASS_MODS_PROHIBITED = 69;

  /**
   * The LDAP result code for operations that fail because the requested
   * operation would have required interaction with multiple DSAs.
   */
  public static final int AFFECTS_MULTIPLE_DSAS = 71;

  /**
   * The LDAP result code for operations that fail due to an error in
   * virtual list view processing.
   */
  public static final int VIRTUAL_LIST_VIEW_ERROR = 76;

  /**
   * The LDAP result code for use in cases in which none of the other defined
   * result codes are appropriate.
   */
  public static final int OTHER = 80;

  /**
   * The client-side result code that indicates that a previously-established
   * connection to the server was lost.  This is for client-side use only and
   * should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_SERVER_DOWN = 81;

  /**
   * The client-side result code that indicates that a local error occurred that
   * had nothing to do with interaction with the server.  This is for
   * client-side use only and should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_LOCAL_ERROR = 82;

  /**
   * The client-side result code that indicates that an error occurred while
   * encoding a request to send to the server.  This is for client-side use only
   * and should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_ENCODING_ERROR = 83;

  /**
   * The client-side result code that indicates that an error occurred while
   * decoding a response from the server.  This is for client-side use only and
   * should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_DECODING_ERROR = 84;

  /**
   * The client-side result code that indicates that the client did not receive
   * an expected response in a timely manner.  This is for client-side use only
   * and should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_TIMEOUT = 85;

  /**
   * The client-side result code that indicates that the user requested an
   * unknown or unsupported authentication mechanism.  This is for client-side
   * use only and should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_AUTH_UNKNOWN = 86;

  /**
   * The client-side result code that indicates that the filter provided by the
   * user was malformed and could not be parsed.  This is for client-side use
   * only and should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_FILTER_ERROR = 87;

  /**
   * The client-side result code that indicates that the user cancelled an
   * operation.  This is for client-side use only and should never be
   * transferred over protocol.
   */
  public static final int CLIENT_SIDE_USER_CANCELLED = 88;

  /**
   * The client-side result code that indicates that there was a problem with
   * one or more of the parameters provided by the user.  This is for
   * client-side use only and should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_PARAM_ERROR = 89;

  /**
   * The client-side result code that indicates that the client application was
   * not able to allocate enough memory for the requested operation.  This is
   * for client-side use only and should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_NO_MEMORY = 90;

  /**
   * The client-side result code that indicates that the client was not able to
   * establish a connection to the server.  This is for client-side use only and
   * should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_CONNECT_ERROR = 91;

  /**
   * The client-side result code that indicates that the user requested an
   * operation that is not supported.  This is for client-side use only and
   * should never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_NOT_SUPPORTED = 92;

  /**
   * The client-side result code that indicates that the client expected a
   * control to be present in the response from the server but it was not
   * included.  This is for client-side use only and should never be transferred
   * over protocol.
   */
  public static final int CLIENT_SIDE_CONTROL_NOT_FOUND = 93;

  /**
   * The client-side result code that indicates that the server did not return
   * any results for a search operation that was expected to match at least one
   * entry.  This is for client-side use only and should never be transferred
   * over protocol.
   */
  public static final int CLIENT_SIDE_NO_RESULTS_RETURNED = 94;

  /**
   * The client-side result code that indicates that the server has returned
   * more matching entries for a search operation than have been processed so
   * far.  This is for client-side use only and should never be transferred over
   * protocol.
   */
  public static final int CLIENT_SIDE_MORE_RESULTS_TO_RETURN = 95;

  /**
   * The client-side result code that indicates that the client detected a
   * referral loop caused by servers referencing each other in a circular
   * manner.  This is for client-side use only and should never be transferred
   * over protocol.
   */
  public static final int CLIENT_SIDE_CLIENT_LOOP = 96;

  /**
   * The client-side result code that indicates that the client reached the
   * maximum number of hops allowed when attempting to follow a referral (i.e.,
   * following one referral resulted in another referral which resulted in
   * another referral and so on).  This is for client-side use only and should
   * never be transferred over protocol.
   */
  public static final int CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED = 97;

  /**
   * The LDAP result code for cancel operations that are successful, or for
   * operations that are canceled.
   */
  public static final int CANCELED = 118;

  /**
   * The LDAP result code for cancel operations that fail because the specified
   * operation could not be found.
   */
  public static final int NO_SUCH_OPERATION = 119;

  /**
   * The LDAP result code for cancel operations that fail because the specified
   * operation has already progressed too far to be canceled.
   */
  public static final int TOO_LATE = 120;

  /**
   * The LDAP result code for cancel operations that fail because the specified
   * operation cannot be canceled.
   */
  public static final int CANNOT_CANCEL = 121;

  /**
   * The LDAP result code for operations that are rejected because the
   * filter in the LDAP assertion control did not match the target entry.
   */
  public static final int ASSERTION_FAILED = 122;

  /**
   * The LDAP result code for operations that fail because the server refused
   * the client's requested authorization.
   */
  public static final int AUTHORIZATION_DENIED = 123;

  /**
   * The LDAP result code for operations in which no action is taken because the
   * request include the LDAP no-op control.
   *
   * FIXME -- This is a temporary result code for use until
   *          draft-zeilenga-ldap-noop is updated and an official result code is
   *          allocated for it.  In the meantime, this result appears to be the
   *          one used by OpenLDAP as per the message at
   *          http://www.openldap.org/lists/openldap-devel/200601/msg00143.html
   *          (0x410e = 16654).
   */
  public static final int NO_OPERATION = 16654;

  /**
   * Retrieves a string representation of the provided LDAP result code.
   *
   * @param  resultCode  The LDAP result code value for which to obtain the
   *                     string representation.
   *
   * @return  The string representation of the provided LDAP result code.
   */
  public static String toString(int resultCode)
  {
    return toLocalizableMessage(resultCode).toString();
  }

  private static LocalizableMessage toLocalizableMessage(int resultCode)
  {
    switch (resultCode)
    {
      case SUCCESS:
        return INFO_RESULT_SUCCESS.get();
      case OPERATIONS_ERROR:
        return INFO_RESULT_OPERATIONS_ERROR.get();
      case PROTOCOL_ERROR:
        return INFO_RESULT_PROTOCOL_ERROR.get();
      case TIME_LIMIT_EXCEEDED:
        return INFO_RESULT_TIME_LIMIT_EXCEEDED.get();
      case SIZE_LIMIT_EXCEEDED:
        return INFO_RESULT_SIZE_LIMIT_EXCEEDED.get();
      case COMPARE_FALSE:
        return INFO_RESULT_COMPARE_FALSE.get();
      case COMPARE_TRUE:
        return INFO_RESULT_COMPARE_TRUE.get();
      case AUTH_METHOD_NOT_SUPPORTED:
        return INFO_RESULT_AUTH_METHOD_NOT_SUPPORTED.get();
      case STRONG_AUTH_REQUIRED:
        return INFO_RESULT_STRONG_AUTH_REQUIRED.get();
      case REFERRAL:
        return INFO_RESULT_REFERRAL.get();
      case ADMIN_LIMIT_EXCEEDED:
        return INFO_RESULT_ADMIN_LIMIT_EXCEEDED.get();
      case UNAVAILABLE_CRITICAL_EXTENSION:
        return INFO_RESULT_UNAVAILABLE_CRITICAL_EXTENSION.get();
      case CONFIDENTIALITY_REQUIRED:
        return INFO_RESULT_CONFIDENTIALITY_REQUIRED.get();
      case SASL_BIND_IN_PROGRESS:
        return INFO_RESULT_SASL_BIND_IN_PROGRESS.get();
      case NO_SUCH_ATTRIBUTE:
        return INFO_RESULT_NO_SUCH_ATTRIBUTE.get();
      case UNDEFINED_ATTRIBUTE_TYPE:
        return INFO_RESULT_UNDEFINED_ATTRIBUTE_TYPE.get();
      case INAPPROPRIATE_MATCHING:
        return INFO_RESULT_INAPPROPRIATE_MATCHING.get();
      case CONSTRAINT_VIOLATION:
        return INFO_RESULT_CONSTRAINT_VIOLATION.get();
      case ATTRIBUTE_OR_VALUE_EXISTS:
        return INFO_RESULT_ATTRIBUTE_OR_VALUE_EXISTS.get();
      case INVALID_ATTRIBUTE_SYNTAX:
        return INFO_RESULT_INVALID_ATTRIBUTE_SYNTAX.get();
      case NO_SUCH_OBJECT:
        return INFO_RESULT_NO_SUCH_OBJECT.get();
      case ALIAS_PROBLEM:
        return INFO_RESULT_ALIAS_PROBLEM.get();
      case INVALID_DN_SYNTAX:
        return INFO_RESULT_INVALID_DN_SYNTAX.get();
      case ALIAS_DEREFERENCING_PROBLEM:
        return INFO_RESULT_ALIAS_DEREFERENCING_PROBLEM.get();
      case INAPPROPRIATE_AUTHENTICATION:
        return INFO_RESULT_INAPPROPRIATE_AUTHENTICATION.get();
      case INVALID_CREDENTIALS:
        return INFO_RESULT_INVALID_CREDENTIALS.get();
      case INSUFFICIENT_ACCESS_RIGHTS:
        return INFO_RESULT_INSUFFICIENT_ACCESS_RIGHTS.get();
      case BUSY:
        return INFO_RESULT_BUSY.get();
      case UNAVAILABLE:
        return INFO_RESULT_UNAVAILABLE.get();
      case UNWILLING_TO_PERFORM:
        return INFO_RESULT_UNWILLING_TO_PERFORM.get();
      case LOOP_DETECT:
        return INFO_RESULT_LOOP_DETECT.get();
      case SORT_CONTROL_MISSING:
        return INFO_RESULT_SORT_CONTROL_MISSING.get();
      case OFFSET_RANGE_ERROR:
        return INFO_RESULT_OFFSET_RANGE_ERROR.get();
      case NAMING_VIOLATION:
        return INFO_RESULT_NAMING_VIOLATION.get();
      case OBJECTCLASS_VIOLATION:
        return INFO_RESULT_OBJECTCLASS_VIOLATION.get();
      case NOT_ALLOWED_ON_NONLEAF:
        return INFO_RESULT_NOT_ALLOWED_ON_NONLEAF.get();
      case NOT_ALLOWED_ON_RDN:
        return INFO_RESULT_NOT_ALLOWED_ON_RDN.get();
      case ENTRY_ALREADY_EXISTS:
        return INFO_RESULT_ENTRY_ALREADY_EXISTS.get();
      case OBJECTCLASS_MODS_PROHIBITED:
        return INFO_RESULT_OBJECTCLASS_MODS_PROHIBITED.get();
      case AFFECTS_MULTIPLE_DSAS:
        return INFO_RESULT_AFFECTS_MULTIPLE_DSAS.get();
      case VIRTUAL_LIST_VIEW_ERROR:
        return INFO_RESULT_VIRTUAL_LIST_VIEW_ERROR.get();
      case CLIENT_SIDE_SERVER_DOWN:
        return INFO_RESULT_CLIENT_SIDE_SERVER_DOWN.get();
      case CLIENT_SIDE_LOCAL_ERROR:
        return INFO_RESULT_CLIENT_SIDE_LOCAL_ERROR.get();
      case CLIENT_SIDE_ENCODING_ERROR:
        return INFO_RESULT_CLIENT_SIDE_ENCODING_ERROR.get();
      case CLIENT_SIDE_DECODING_ERROR:
        return INFO_RESULT_CLIENT_SIDE_DECODING_ERROR.get();
      case CLIENT_SIDE_TIMEOUT:
        return INFO_RESULT_CLIENT_SIDE_TIMEOUT.get();
      case CLIENT_SIDE_AUTH_UNKNOWN:
        return INFO_RESULT_CLIENT_SIDE_AUTH_UNKNOWN.get();
      case CLIENT_SIDE_FILTER_ERROR:
        return INFO_RESULT_CLIENT_SIDE_FILTER_ERROR.get();
      case CLIENT_SIDE_USER_CANCELLED:
        return INFO_RESULT_CLIENT_SIDE_USER_CANCELLED.get();
      case CLIENT_SIDE_PARAM_ERROR:
        return INFO_RESULT_CLIENT_SIDE_PARAM_ERROR.get();
      case CLIENT_SIDE_NO_MEMORY:
        return INFO_RESULT_CLIENT_SIDE_NO_MEMORY.get();
      case CLIENT_SIDE_CONNECT_ERROR:
        return INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get();
      case CLIENT_SIDE_NOT_SUPPORTED:
        return INFO_RESULT_CLIENT_SIDE_NOT_SUPPORTED.get();
      case CLIENT_SIDE_CONTROL_NOT_FOUND:
        return INFO_RESULT_CLIENT_SIDE_CONTROL_NOT_FOUND.get();
      case CLIENT_SIDE_NO_RESULTS_RETURNED:
        return INFO_RESULT_CLIENT_SIDE_NO_RESULTS_RETURNED.get();
      case CLIENT_SIDE_MORE_RESULTS_TO_RETURN:
        return INFO_RESULT_CLIENT_SIDE_MORE_RESULTS_TO_RETURN.get();
      case CLIENT_SIDE_CLIENT_LOOP:
        return INFO_RESULT_CLIENT_SIDE_CLIENT_LOOP.get();
      case CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED:
        return INFO_RESULT_CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED.get();
      case CANCELED:
        return INFO_RESULT_CANCELED.get();
      case NO_SUCH_OPERATION:
        return INFO_RESULT_NO_SUCH_OPERATION.get();
      case TOO_LATE:
        return INFO_RESULT_TOO_LATE.get();
      case CANNOT_CANCEL:
        return INFO_RESULT_CANNOT_CANCEL.get();
      case ASSERTION_FAILED:
        return INFO_RESULT_ASSERTION_FAILED.get();
      case AUTHORIZATION_DENIED:
        return INFO_RESULT_AUTHORIZATION_DENIED.get();
      case NO_OPERATION:
        return INFO_RESULT_NO_OPERATION.get();
      default:
        return INFO_RESULT_OTHER.get();
    }
  }
}
