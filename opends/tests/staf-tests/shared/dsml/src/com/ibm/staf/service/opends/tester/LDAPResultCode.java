/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2009 Sun Microsystems, Inc.
 */

package com.ibm.staf.service.opends.tester;

/**
 * This class defines a set of constants that correspond to the result codes
 * defined in the LDAP protocol.  Note that many (but not all) of the result
 * codes numbered 81 and higher come from the LDAP C API specification and are
 * only intended for client-side use and should not be returned from the
 * Directory Server.  These are denoted with a "CLIENT_SIDE_" prefix.
 */
public class LDAPResultCode
{
  /**
   * The LDAP result code for successful operations.
   */
  public static final int SUCCESS = 0;



  /**
   * The LDAP result code for operations that fail due to an operations error.
   */
  public static final int OPERATIONS_ERROR = 1;



  /**
   * The LDAP result code for operations that fail due to a protocol error.
   */
  public static final int PROTOCOL_ERROR = 2;



  /**
   * The LDAP result code for operations that fail as a result of exceeding a
   * time limit.
   */
  public static final int TIME_LIMIT_EXCEEDED = 3;



  /**
   * The LDAP result code for operations that fail as a result of exceeding a
   * size limit.
   */
  public static final int SIZE_LIMIT_EXCEEDED = 4;



  /**
   * The LDAP result code for compare operations in which the assertion is
   * false.
   */
  public static final int COMPARE_FALSE = 5;



  /**
   * The LDAP result code for compare operations in which the assertion is true.
   */
  public static final int COMPARE_TRUE = 6;



  /**
   * The LDAP result code for operations that fail because the requested
   * authentication method is not supported.
   */
  public static final int AUTH_METHOD_NOT_SUPPORTED = 7;



  /**
   * The LDAP result code for operations that fail because strong authentication
   * is required.
   */
  public static final int STRONG_AUTH_REQUIRED = 8;



  /**
   * The LDAP result code for operations that encountered a referral.
   */
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



  /**
   * The LDAP result code for operations that fail because confidentiality is
   * required.
   */
  public static final int CONFIDENTIALITY_REQUIRED = 13;



  /**
   * The LDAP result code used for multi-stage SASL bind operations that are not
   * yet complete.
   */
  public static final int SASL_BIND_IN_PROGRESS = 14;



  /**
   * The LDAP result code for operations that fail because a specified attribute
   * does not exist.
   */
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



  /**
   * The LDAP result code for operations that fail because of an invalid
   * attribute syntax.
   */
  public static final int INVALID_ATTRIBUTE_SYNTAX = 21;



  /**
   * The LDAP result code for operations that fail because a targeted entry does
   * not exist.
   */
  public static final int NO_SUCH_OBJECT = 32;



  /**
   * The LDAP result code for operations that fail because the an alias was
   * encountered in an illegal context.
   */
  public static final int ALIAS_PROBLEM = 33;



  /**
   * The LDAP result code for operations that fail because the request included
   * a malformed DN.
   */
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



  /**
   * The LDAP result code for operations that fail because the server was too
   * busy to process it.
   */
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



  /**
   * The LDAP result code for operations that fail due to a naming violation.
   */
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
    String message;

    switch (resultCode)
    {
      case SUCCESS:
        message = "SUCCESS";
        break;
      case OPERATIONS_ERROR:
        message = "OPERATIONS_ERROR";
        break;
      case PROTOCOL_ERROR:
        message = "PROTOCOL_ERROR";
        break;
      case TIME_LIMIT_EXCEEDED:
        message = "TIME_LIMIT_EXCEEDED";
        break;
      case SIZE_LIMIT_EXCEEDED:
        message = "SIZE_LIMIT_EXCEEDED";
        break;
      case COMPARE_FALSE:
        message = "COMPARE_FALSE";
        break;
      case COMPARE_TRUE:
        message = "COMPARE_TRUE";
        break;
      case AUTH_METHOD_NOT_SUPPORTED:
        message = "AUTH_METHOD_NOT_SUPPORTED";
        break;
      case STRONG_AUTH_REQUIRED:
        message = "STRONG_AUTH_REQUIRED";
        break;
      case REFERRAL:
        message = "REFERRAL";
        break;
      case ADMIN_LIMIT_EXCEEDED:
        message = "ADMIN_LIMIT_EXCEEDED";
        break;
      case UNAVAILABLE_CRITICAL_EXTENSION:
        message = "UNAVAILABLE_CRITICAL_EXTENSION";
        break;
      case CONFIDENTIALITY_REQUIRED:
        message = "CONFIDENTIALITY_REQUIRED";
        break;
      case SASL_BIND_IN_PROGRESS:
        message = "SASL_BIND_IN_PROGRESS";
        break;
      case NO_SUCH_ATTRIBUTE:
        message = "NO_SUCH_ATTRIBUTE";
        break;
      case UNDEFINED_ATTRIBUTE_TYPE:
        message = "UNDEFINED_ATTRIBUTE_TYPE";
        break;
      case INAPPROPRIATE_MATCHING:
        message = "INAPPROPRIATE_MATCHING";
        break;
      case CONSTRAINT_VIOLATION:
        message = "CONSTRAINT_VIOLATION";
        break;
      case ATTRIBUTE_OR_VALUE_EXISTS:
        message = "ATTRIBUTE_OR_VALUE_EXISTS";
        break;
      case INVALID_ATTRIBUTE_SYNTAX:
        message = "INVALID_ATTRIBUTE_SYNTAX";
        break;
      case NO_SUCH_OBJECT:
        message = "NO_SUCH_OBJECT";
        break;
      case ALIAS_PROBLEM:
        message = "ALIAS_PROBLEM";
        break;
      case INVALID_DN_SYNTAX:
        message = "INVALID_DN_SYNTAX";
        break;
      case ALIAS_DEREFERENCING_PROBLEM:
        message = "ALIAS_DEREFERENCING_PROBLEM";
        break;
      case INAPPROPRIATE_AUTHENTICATION:
        message = "INAPPROPRIATE_AUTHENTICATION";
        break;
      case INVALID_CREDENTIALS:
        message = "INVALID_CREDENTIALS";
        break;
      case INSUFFICIENT_ACCESS_RIGHTS:
        message = "INSUFFICIENT_ACCESS_RIGHTS";
        break;
      case BUSY:
        message = "BUSY";
        break;
      case UNAVAILABLE:
        message = "UNAVAILABLE";
        break;
      case UNWILLING_TO_PERFORM:
        message = "UNWILLING_TO_PERFORM";
        break;
      case LOOP_DETECT:
        message = "LOOP_DETECT";
        break;
      case SORT_CONTROL_MISSING:
        message = "SORT_CONTROL_MISSING";
        break;
      case OFFSET_RANGE_ERROR:
        message = "OFFSET_RANGE_ERROR";
        break;
      case NAMING_VIOLATION:
        message = "NAMING_VIOLATION";
        break;
      case OBJECTCLASS_VIOLATION:
        message = "OBJECTCLASS_VIOLATION";
        break;
      case NOT_ALLOWED_ON_NONLEAF:
        message = "NOT_ALLOWED_ON_NONLEAF";
        break;
      case NOT_ALLOWED_ON_RDN:
        message = "NOT_ALLOWED_ON_RDN";
        break;
      case ENTRY_ALREADY_EXISTS:
        message = "ENTRY_ALREADY_EXISTS";
        break;
      case OBJECTCLASS_MODS_PROHIBITED:
        message = "OBJECTCLASS_MODS_PROHIBITED";
        break;
      case AFFECTS_MULTIPLE_DSAS:
        message = "AFFECTS_MULTIPLE_DSAS";
        break;
      case VIRTUAL_LIST_VIEW_ERROR:
        message = "VIRTUAL_LIST_VIEW_ERROR";
        break;
      case CLIENT_SIDE_SERVER_DOWN:
        message = "CLIENT_SIDE_SERVER_DOWN";
        break;
      case CLIENT_SIDE_LOCAL_ERROR:
        message = "CLIENT_SIDE_LOCAL_ERROR";
        break;
      case CLIENT_SIDE_ENCODING_ERROR:
        message = "CLIENT_SIDE_ENCODING_ERROR";
        break;
      case CLIENT_SIDE_DECODING_ERROR:
        message = "CLIENT_SIDE_DECODING_ERROR";
        break;
      case CLIENT_SIDE_TIMEOUT:
        message = "CLIENT_SIDE_TIMEOUT";
        break;
      case CLIENT_SIDE_AUTH_UNKNOWN:
        message = "CLIENT_SIDE_AUTH_UNKNOWN";
        break;
      case CLIENT_SIDE_FILTER_ERROR:
        message = "CLIENT_SIDE_FILTER_ERROR";
        break;
      case CLIENT_SIDE_USER_CANCELLED:
        message = "CLIENT_SIDE_USER_CANCELLED";
        break;
      case CLIENT_SIDE_PARAM_ERROR:
        message = "CLIENT_SIDE_PARAM_ERROR";
        break;
      case CLIENT_SIDE_NO_MEMORY:
        message = "CLIENT_SIDE_NO_MEMORY";
        break;
      case CLIENT_SIDE_CONNECT_ERROR:
        message = "CLIENT_SIDE_CONNECT_ERROR";
        break;
      case CLIENT_SIDE_NOT_SUPPORTED:
        message = "CLIENT_SIDE_NOT_SUPPORTED";
        break;
      case CLIENT_SIDE_CONTROL_NOT_FOUND:
        message = "CLIENT_SIDE_CONTROL_NOT_FOUND";
        break;
      case CLIENT_SIDE_NO_RESULTS_RETURNED:
        message = "CLIENT_SIDE_NO_RESULTS_RETURNED";
        break;
      case CLIENT_SIDE_MORE_RESULTS_TO_RETURN:
        message = "CLIENT_SIDE_MORE_RESULTS_TO_RETURN";
        break;
      case CLIENT_SIDE_CLIENT_LOOP:
        message = "CLIENT_SIDE_CLIENT_LOOP";
        break;
      case CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED:
        message = "CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED";
        break;
      case CANCELED:
        message = "CANCELED";
        break;
      case NO_SUCH_OPERATION:
        message = "NO_SUCH_OPERATION";
        break;
      case TOO_LATE:
        message = "TOO_LATE";
        break;
      case CANNOT_CANCEL:
        message = "CANNOT_CANCEL";
        break;
      case ASSERTION_FAILED:
        message = "ASSERTION_FAILED";
        break;
      case AUTHORIZATION_DENIED:
        message = "AUTHORIZATION_DENIED";
        break;
      case NO_OPERATION:
        message = "NO_OPERATION";
        break;
      default:
        message = "OTHER";
        break;
    }

    return message;
  }
}

