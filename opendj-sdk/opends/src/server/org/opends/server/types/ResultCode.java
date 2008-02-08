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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;


import org.opends.server.protocols.ldap.LDAPResultCode;

import static org.opends.messages.CoreMessages.*;



/**
 * This enumeration defines the set of possible result codes that may
 * be used for providing clients with information about result of
 * processing an operation.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum ResultCode
{
  /**
   * The result code that should only be used if the actual result
   * code has not yet been determined.
   */
  UNDEFINED(-1, null),



  /**
   * The result code that indicates that the operation completed
   * successfully.
   */
  SUCCESS(LDAPResultCode.SUCCESS, INFO_RESULT_SUCCESS.get()),



  /**
   * The result code that indicates that an internal error prevented
   * the operation from being processed properly.
   */
  OPERATIONS_ERROR(LDAPResultCode.OPERATIONS_ERROR,
                   INFO_RESULT_OPERATIONS_ERROR.get()),



  /**
   * The result code that indicates that the client sent a malformed
   * or illegal request to the server.
   */
  PROTOCOL_ERROR(LDAPResultCode.PROTOCOL_ERROR,
                 INFO_RESULT_PROTOCOL_ERROR.get()),



  /**
   * The result code that indicates that a time limit was exceeded
   * while attempting to process the request.
   */
  TIME_LIMIT_EXCEEDED(LDAPResultCode.TIME_LIMIT_EXCEEDED,
                      INFO_RESULT_TIME_LIMIT_EXCEEDED.get()),



  /**
   * The result code that indicates that a size limit was exceeded
   * while attempting to process the request.
   */
  SIZE_LIMIT_EXCEEDED(LDAPResultCode.SIZE_LIMIT_EXCEEDED,
                      INFO_RESULT_SIZE_LIMIT_EXCEEDED.get()),



  /**
   * The result code that indicates that the attribute value assertion
   * included in a compare request did not match the targeted entry.
   */
  COMPARE_FALSE(LDAPResultCode.COMPARE_FALSE,
                INFO_RESULT_COMPARE_FALSE.get()),



  /**
   * The result code that indicates that the attribute value assertion
   * included in a compare request did match the targeted entry.
   */
  COMPARE_TRUE(LDAPResultCode.COMPARE_TRUE,
               INFO_RESULT_COMPARE_TRUE.get()),



  /**
   * The result code that indicates that the requested authentication
   * attempt failed because it referenced an invalid SASL mechanism.
   */
  AUTH_METHOD_NOT_SUPPORTED(LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED,
                         INFO_RESULT_AUTH_METHOD_NOT_SUPPORTED.get()),



  /**
   * The result code that indicates that the requested operation could
   * not be processed because it requires that the client has
   * completed a strong form of authentication.
   */
  STRONG_AUTH_REQUIRED(LDAPResultCode.STRONG_AUTH_REQUIRED,
                       INFO_RESULT_STRONG_AUTH_REQUIRED.get()),



  /**
   * The result code that indicates that a referral was encountered.
   */
  REFERRAL(LDAPResultCode.REFERRAL, INFO_RESULT_REFERRAL.get()),



  /**
   * The result code that indicates that processing on the requested
   * operation could not continue because an administrative limit was
   * exceeded.
   */
  ADMIN_LIMIT_EXCEEDED(LDAPResultCode.ADMIN_LIMIT_EXCEEDED,
                       INFO_RESULT_ADMIN_LIMIT_EXCEEDED.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it included a critical extension that is
   * unsupported or inappropriate for that request.
   */
  UNAVAILABLE_CRITICAL_EXTENSION(
       LDAPResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
       INFO_RESULT_UNAVAILABLE_CRITICAL_EXTENSION.get()),



  /**
   * The result code that indicates that the requested operation could
   * not be processed because it requires confidentiality for the
   * communication between the client and the server.
   */
  CONFIDENTIALITY_REQUIRED(LDAPResultCode.CONFIDENTIALITY_REQUIRED,
                          INFO_RESULT_CONFIDENTIALITY_REQUIRED.get()),



  /**
   * The result code that should be used for intermediate responses in
   * multi-stage SASL bind operations.
   */
  SASL_BIND_IN_PROGRESS(LDAPResultCode.SASL_BIND_IN_PROGRESS,
                        INFO_RESULT_SASL_BIND_IN_PROGRESS.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it targeted an attribute or attribute value that
   * did not exist in the specified entry.
   */
  NO_SUCH_ATTRIBUTE(LDAPResultCode.NO_SUCH_ATTRIBUTE,
                    INFO_RESULT_NO_SUCH_ATTRIBUTE.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it referenced an attribute that is not defined in
   * the server schema.
   */
  UNDEFINED_ATTRIBUTE_TYPE(LDAPResultCode.UNDEFINED_ATTRIBUTE_TYPE,
                          INFO_RESULT_UNDEFINED_ATTRIBUTE_TYPE.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it attempted to perform an inappropriate type of
   * matching against an attribute.
   */
  INAPPROPRIATE_MATCHING(LDAPResultCode.INAPPROPRIATE_MATCHING,
                         INFO_RESULT_INAPPROPRIATE_MATCHING.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it would have violated some constraint defined in
   * the server.
   */
  CONSTRAINT_VIOLATION(LDAPResultCode.CONSTRAINT_VIOLATION,
                       INFO_RESULT_CONSTRAINT_VIOLATION.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it would have resulted in a conflict with an
   * existing attribute or attribute value in the target entry.
   */
  ATTRIBUTE_OR_VALUE_EXISTS(LDAPResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
                         INFO_RESULT_ATTRIBUTE_OR_VALUE_EXISTS.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it violated the syntax for a specified attribute.
   */
  INVALID_ATTRIBUTE_SYNTAX(LDAPResultCode.INVALID_ATTRIBUTE_SYNTAX,
                          INFO_RESULT_INVALID_ATTRIBUTE_SYNTAX.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it referenced an entry that does not exist.
   */
  NO_SUCH_OBJECT(LDAPResultCode.NO_SUCH_OBJECT,
                 INFO_RESULT_NO_SUCH_OBJECT.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it attempted to perform an illegal operation on an
   * alias.
   */
  ALIAS_PROBLEM(LDAPResultCode.ALIAS_PROBLEM,
                INFO_RESULT_ALIAS_PROBLEM.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it would have resulted in an entry with an invalid
   * or malformed DN.
   */
  INVALID_DN_SYNTAX(LDAPResultCode.INVALID_DN_SYNTAX,
                    INFO_RESULT_INVALID_DN_SYNTAX.get()),



  /**
   * The result code that indicates that a problem was encountered
   * while attempting to dereference an alias for a search operation.
   */
  ALIAS_DEREFERENCING_PROBLEM(
       LDAPResultCode.ALIAS_DEREFERENCING_PROBLEM,
       INFO_RESULT_ALIAS_DEREFERENCING_PROBLEM.get()),



  /**
   * The result code that indicates that an authentication attempt
   * failed because the requested type of authentication was not
   * appropriate for the targeted entry.
   */
  INAPPROPRIATE_AUTHENTICATION(
       LDAPResultCode.INAPPROPRIATE_AUTHENTICATION,
       INFO_RESULT_INAPPROPRIATE_AUTHENTICATION.get()),



  /**
   * The result code that indicates that an authentication attempt
   * failed because the user did not provide a valid set of
   * credentials.
   */
  INVALID_CREDENTIALS(LDAPResultCode.INVALID_CREDENTIALS,
                      INFO_RESULT_INVALID_CREDENTIALS.get()),



  /**
   * The result code that indicates that the client does not have
   * sufficient permission to perform the requested operation.
   */
  INSUFFICIENT_ACCESS_RIGHTS(
       LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
       INFO_RESULT_INSUFFICIENT_ACCESS_RIGHTS.get()),



  /**
   * The result code that indicates that the server is too busy to
   * process the requested operation.
   */
  BUSY(LDAPResultCode.BUSY, INFO_RESULT_BUSY.get()),



  /**
   * The result code that indicates that either the entire server or
   * one or more required resources were not available for use in
   * processing the request.
   */
  UNAVAILABLE(LDAPResultCode.UNAVAILABLE,
          INFO_RESULT_UNAVAILABLE.get()),



  /**
   * The result code that indicates that the server is unwilling to
   * perform the requested operation.
   */
  UNWILLING_TO_PERFORM(LDAPResultCode.UNWILLING_TO_PERFORM,
                       INFO_RESULT_UNWILLING_TO_PERFORM.get()),



  /**
   * The result code that indicates that a referral or chaining
   * loop was detected while processing the request.
   */
  LOOP_DETECT(LDAPResultCode.LOOP_DETECT,
          INFO_RESULT_LOOP_DETECT.get()),



  /**
   * The result code that indicates that a search request included a
   * VLV request control without a server-side sort control.
   */
  SORT_CONTROL_MISSING(LDAPResultCode.SORT_CONTROL_MISSING,
                       INFO_RESULT_SORT_CONTROL_MISSING.get()),



  /**
   * The result code that indicates that a search request included a
   * VLV request control with an invalid offset.
   */
  OFFSET_RANGE_ERROR(LDAPResultCode.OFFSET_RANGE_ERROR,
                     INFO_RESULT_OFFSET_RANGE_ERROR.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it would have violated the server's naming
   * configuration.
   */
  NAMING_VIOLATION(LDAPResultCode.NAMING_VIOLATION,
                   INFO_RESULT_NAMING_VIOLATION.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it would have resulted in an entry that violated
   * the server schema.
   */
  OBJECTCLASS_VIOLATION(LDAPResultCode.OBJECTCLASS_VIOLATION,
                        INFO_RESULT_OBJECTCLASS_VIOLATION.get()),



  /**
   * The result code that indicates that the requested operation is
   * not allowed for non-leaf entries.
   */
  NOT_ALLOWED_ON_NONLEAF(LDAPResultCode.NOT_ALLOWED_ON_NONLEAF,
                         INFO_RESULT_NOT_ALLOWED_ON_NONLEAF.get()),



  /**
   * The result code that indicates that the requested operation is
   * not allowed on an RDN attribute.
   */
  NOT_ALLOWED_ON_RDN(LDAPResultCode.NOT_ALLOWED_ON_RDN,
                     INFO_RESULT_NOT_ALLOWED_ON_RDN.get()),



  /**
   * The result code that indicates that the requested operation
   * failed because it would have resulted in an entry that conflicts
   * with an entry that already exists.
   */
  ENTRY_ALREADY_EXISTS(LDAPResultCode.ENTRY_ALREADY_EXISTS,
                       INFO_RESULT_ENTRY_ALREADY_EXISTS.get()),



  /**
   * The result code that indicates that the operation could not be
   * processed because it would have modified the objectclasses
   * associated with an entry in an illegal manner.
   */
  OBJECTCLASS_MODS_PROHIBITED(
       LDAPResultCode.OBJECTCLASS_MODS_PROHIBITED,
       INFO_RESULT_OBJECTCLASS_MODS_PROHIBITED.get()),



  /**
   * The result code that indicates that the operation could not be
   * processed because it would impact multiple DSAs or other
   * repositories.
   */
  AFFECTS_MULTIPLE_DSAS(LDAPResultCode.AFFECTS_MULTIPLE_DSAS,
                        INFO_RESULT_AFFECTS_MULTIPLE_DSAS.get()),



  /**
   * The result code that indicates that the operation could not be
   * processed because there was an error while processing the virtual
   * list view control.
   */
  VIRTUAL_LIST_VIEW_ERROR(LDAPResultCode.VIRTUAL_LIST_VIEW_ERROR,
                          INFO_RESULT_VIRTUAL_LIST_VIEW_ERROR.get()),



  /**
   * The result code that should be used if no other result code is
   * appropriate.
   */
  OTHER(LDAPResultCode.OTHER, INFO_RESULT_OTHER.get()),



  /**
   * The client-side result code that should be used if an established
   * connection is lost.  This should not be used over protocol.
   */
  CLIENT_SIDE_SERVER_DOWN(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                          INFO_RESULT_CLIENT_SIDE_SERVER_DOWN.get()),



  /**
   * The client-side result code that should be used if a local
   * (client-side) error occurs.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_LOCAL_ERROR(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                          INFO_RESULT_CLIENT_SIDE_LOCAL_ERROR.get()),



  /**
   * The client-side result code that should be used if an error
   * occurs while encoding a request.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_ENCODING_ERROR(
       LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
       INFO_RESULT_CLIENT_SIDE_ENCODING_ERROR.get()),



  /**
   * The client-side result code that should be used if an error
   * occurs while decoding a response.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_DECODING_ERROR(
       LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
       INFO_RESULT_CLIENT_SIDE_DECODING_ERROR.get()),



  /**
   * The client-side result code that should be used if a client-side
   * timeout occurs.  This should not be used over protocol.
   */
  CLIENT_SIDE_TIMEOUT(LDAPResultCode.CLIENT_SIDE_TIMEOUT,
                      INFO_RESULT_CLIENT_SIDE_TIMEOUT.get()),



  /**
   * The client-side result code that should be used if an unknown or
   * unsupported authentication mechanism is requested.  This should
   * not be used over protocol.
   */
  CLIENT_SIDE_AUTH_UNKNOWN(LDAPResultCode.CLIENT_SIDE_AUTH_UNKNOWN,
                         INFO_RESULT_CLIENT_SIDE_AUTH_UNKNOWN.get()),



  /**
   * The client-side result code that should be used if a malformed
   * search filter is provided.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_FILTER_ERROR(LDAPResultCode.CLIENT_SIDE_FILTER_ERROR,
                         INFO_RESULT_CLIENT_SIDE_FILTER_ERROR.get()),



  /**
   * The client-side result code that should be used if a user
   * cancelled a client-side operation.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_USER_CANCELLED(
       LDAPResultCode.CLIENT_SIDE_USER_CANCELLED,
       INFO_RESULT_CLIENT_SIDE_USER_CANCELLED.get()),



  /**
   * The client-side result code that should be used if there was an
   * error in the parameter(s) provided.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_PARAM_ERROR(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                          INFO_RESULT_CLIENT_SIDE_PARAM_ERROR.get()),



  /**
   * The client-side result code that should be used if the client
   * cannot obtain enough memory to perform the requested operation.
   * This should not be used over protocol.
   */
  CLIENT_SIDE_NO_MEMORY(LDAPResultCode.CLIENT_SIDE_NO_MEMORY,
                        INFO_RESULT_CLIENT_SIDE_NO_MEMORY.get()),



  /**
   * The client-side result code that should be used if a connection
   * cannot be established.  This should not be used over protocol.
   */
  CLIENT_SIDE_CONNECT_ERROR(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
                         INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get()),



  /**
   * The client-side result code that should be used if a user
   * requests an unsupported operation.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_NOT_SUPPORTED(LDAPResultCode.CLIENT_SIDE_NOT_SUPPORTED,
                         INFO_RESULT_CLIENT_SIDE_NOT_SUPPORTED.get()),



  /**
   * The client-side result code that should be used if an expected
   * control is not found in a response.  This should not be used over
   * protocol.
   */
  CLIENT_SIDE_CONTROL_NOT_FOUND(
       LDAPResultCode.CLIENT_SIDE_CONTROL_NOT_FOUND,
       INFO_RESULT_CLIENT_SIDE_CONTROL_NOT_FOUND.get()),



  /**
   * The client-side result code that should be used if no results
   * were returned for a search operation that expected them.  This
   * should not be used over protocol.
   */
  CLIENT_SIDE_NO_RESULTS_RETURNED(
       LDAPResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED,
       INFO_RESULT_CLIENT_SIDE_NO_RESULTS_RETURNED.get()),



  /**
   * The client-side result code that should be used if there are more
   * results to be processed.  This should not be used over protocol.
   */
  CLIENT_SIDE_MORE_RESULTS_TO_RETURN(
       LDAPResultCode.CLIENT_SIDE_MORE_RESULTS_TO_RETURN,
       INFO_RESULT_CLIENT_SIDE_MORE_RESULTS_TO_RETURN.get()),



  /**
   * The client-side result code that should be used if a referral
   * loop is detected.  This should not be used over protocol.
   */
  CLIENT_SIDE_CLIENT_LOOP(LDAPResultCode.CLIENT_SIDE_CLIENT_LOOP,
                          INFO_RESULT_CLIENT_SIDE_CLIENT_LOOP.get()),



  /**
   * The client-side result code that should be used if the referral
   * hop limit was exceeded.  This should not be used over protocol.
   */
  CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED(
       LDAPResultCode.CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED,
       INFO_RESULT_CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED.get()),



  /**
   * The result code that indicates that a cancel request was
   * successful, or that the specified operation was canceled.
   */
  CANCELED(LDAPResultCode.CANCELED, INFO_RESULT_CANCELED.get()),



  /**
   * The result code that indicates that a cancel request was
   * unsuccessful because the targeted operation did not exist or had
   * already completed.
   */
  NO_SUCH_OPERATION(LDAPResultCode.NO_SUCH_OPERATION,
                    INFO_RESULT_NO_SUCH_OPERATION.get()),



  /**
   * The result code that indicates that a cancel request was
   * unsuccessful because processing on the targeted operation had
   * already reached a point at which it could not be canceled.
   */
  TOO_LATE(LDAPResultCode.TOO_LATE, INFO_RESULT_TOO_LATE.get()),



  /**
   * The result code that indicates that a cancel request was
   * unsuccessful because the targeted operation was one that could
   * not be canceled.
   */
  CANNOT_CANCEL(LDAPResultCode.CANNOT_CANCEL,
                INFO_RESULT_CANNOT_CANCEL.get()),



  /**
   * The result code that indicates that the filter contained in an
   * assertion control failed to match the target entry.
   */
  ASSERTION_FAILED(LDAPResultCode.ASSERTION_FAILED,
                   INFO_RESULT_ASSERTION_FAILED.get()),



  /**
   * The result code that should be used if the server will not allow
   * the client to use the requested authorization.
   */
  AUTHORIZATION_DENIED(LDAPResultCode.AUTHORIZATION_DENIED,
                       INFO_RESULT_AUTHORIZATION_DENIED.get()),



  /**
   * The result code that should be used if the server did not
   * actually complete processing on the associated operation because
   * the request included the LDAP No-Op control.
   */
  NO_OPERATION(LDAPResultCode.NO_OPERATION,
               INFO_RESULT_NO_OPERATION.get());



  // The integer value for this result code.
  private int intValue;

  // The short human-readable name for this result code.
  private Message resultCodeName;



  /**
   * Creates a new result code with the specified int value and unique
   * identifier.
   *
   * @param  intValue      The integer value for this result code.
   * @param  name          The name for this result code.
   */
  private ResultCode(int intValue, Message name)
  {
    this.intValue       = intValue;
    this.resultCodeName = name;
  }



  /**
   * Retrieves the integer value for this result code.
   *
   * @return  The integer value for this result code.
   */
  public int getIntValue()
  {
    return intValue;
  }



  /**
   * Retrieves the result code with the provided int value.
   *
   * @param  intValue  The value for which to retrieve the
   *                   corresponding result code.
   *
   * @return  The result code with the provided int value, or
   *          <CODE>ResultCode.OTHER</CODE> if there is no recognized
   *          result code with the provided int value.
   */
  public static ResultCode valueOf(int intValue)
  {
    switch (intValue)
    {
      case LDAPResultCode.SUCCESS:
        return SUCCESS;
      case LDAPResultCode.OPERATIONS_ERROR:
        return OPERATIONS_ERROR;
      case LDAPResultCode.PROTOCOL_ERROR:
        return PROTOCOL_ERROR;
      case LDAPResultCode.TIME_LIMIT_EXCEEDED:
        return TIME_LIMIT_EXCEEDED;
      case LDAPResultCode.SIZE_LIMIT_EXCEEDED:
        return SIZE_LIMIT_EXCEEDED;
      case LDAPResultCode.COMPARE_FALSE:
        return COMPARE_FALSE;
      case LDAPResultCode.COMPARE_TRUE:
        return COMPARE_TRUE;
      case LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED:
        return AUTH_METHOD_NOT_SUPPORTED;
      case LDAPResultCode.STRONG_AUTH_REQUIRED:
        return STRONG_AUTH_REQUIRED;
      case LDAPResultCode.REFERRAL:
        return REFERRAL;
      case LDAPResultCode.ADMIN_LIMIT_EXCEEDED:
        return ADMIN_LIMIT_EXCEEDED;
      case LDAPResultCode.UNAVAILABLE_CRITICAL_EXTENSION:
        return UNAVAILABLE_CRITICAL_EXTENSION;
      case LDAPResultCode.CONFIDENTIALITY_REQUIRED:
        return CONFIDENTIALITY_REQUIRED;
      case LDAPResultCode.SASL_BIND_IN_PROGRESS:
        return SASL_BIND_IN_PROGRESS;
      case LDAPResultCode.NO_SUCH_ATTRIBUTE:
        return NO_SUCH_ATTRIBUTE;
      case LDAPResultCode.UNDEFINED_ATTRIBUTE_TYPE:
        return UNDEFINED_ATTRIBUTE_TYPE;
      case LDAPResultCode.INAPPROPRIATE_MATCHING:
        return INAPPROPRIATE_MATCHING;
      case LDAPResultCode.CONSTRAINT_VIOLATION:
        return CONSTRAINT_VIOLATION;
      case LDAPResultCode.ATTRIBUTE_OR_VALUE_EXISTS:
        return ATTRIBUTE_OR_VALUE_EXISTS;
      case LDAPResultCode.INVALID_ATTRIBUTE_SYNTAX:
        return INVALID_ATTRIBUTE_SYNTAX;
      case LDAPResultCode.NO_SUCH_OBJECT:
        return NO_SUCH_OBJECT;
      case LDAPResultCode.ALIAS_PROBLEM:
        return ALIAS_PROBLEM;
      case LDAPResultCode.INVALID_DN_SYNTAX:
        return INVALID_DN_SYNTAX;
      case LDAPResultCode.ALIAS_DEREFERENCING_PROBLEM:
        return ALIAS_DEREFERENCING_PROBLEM;
      case LDAPResultCode.INAPPROPRIATE_AUTHENTICATION:
        return INAPPROPRIATE_AUTHENTICATION;
      case LDAPResultCode.INVALID_CREDENTIALS:
        return INVALID_CREDENTIALS;
      case LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS:
        return INSUFFICIENT_ACCESS_RIGHTS;
      case LDAPResultCode.BUSY:
        return BUSY;
      case LDAPResultCode.UNAVAILABLE:
        return UNAVAILABLE;
      case LDAPResultCode.UNWILLING_TO_PERFORM:
        return UNWILLING_TO_PERFORM;
      case LDAPResultCode.LOOP_DETECT:
        return LOOP_DETECT;
      case LDAPResultCode.SORT_CONTROL_MISSING:
        return SORT_CONTROL_MISSING;
      case LDAPResultCode.OFFSET_RANGE_ERROR:
        return OFFSET_RANGE_ERROR;
      case LDAPResultCode.NAMING_VIOLATION:
        return NAMING_VIOLATION;
      case LDAPResultCode.OBJECTCLASS_VIOLATION:
        return OBJECTCLASS_VIOLATION;
      case LDAPResultCode.NOT_ALLOWED_ON_NONLEAF:
        return NOT_ALLOWED_ON_NONLEAF;
      case LDAPResultCode.NOT_ALLOWED_ON_RDN:
        return NOT_ALLOWED_ON_RDN;
      case LDAPResultCode.ENTRY_ALREADY_EXISTS:
        return ENTRY_ALREADY_EXISTS;
      case LDAPResultCode.OBJECTCLASS_MODS_PROHIBITED:
        return OBJECTCLASS_MODS_PROHIBITED;
      case LDAPResultCode.AFFECTS_MULTIPLE_DSAS:
        return AFFECTS_MULTIPLE_DSAS;
      case LDAPResultCode.VIRTUAL_LIST_VIEW_ERROR:
        return VIRTUAL_LIST_VIEW_ERROR;
      case LDAPResultCode.CLIENT_SIDE_SERVER_DOWN:
        return CLIENT_SIDE_SERVER_DOWN;
      case LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR:
        return CLIENT_SIDE_LOCAL_ERROR;
      case LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR:
        return CLIENT_SIDE_ENCODING_ERROR;
      case LDAPResultCode.CLIENT_SIDE_DECODING_ERROR:
        return CLIENT_SIDE_DECODING_ERROR;
      case LDAPResultCode.CLIENT_SIDE_TIMEOUT:
        return CLIENT_SIDE_TIMEOUT;
      case LDAPResultCode.CLIENT_SIDE_AUTH_UNKNOWN:
        return CLIENT_SIDE_AUTH_UNKNOWN;
      case LDAPResultCode.CLIENT_SIDE_FILTER_ERROR:
        return CLIENT_SIDE_FILTER_ERROR;
      case LDAPResultCode.CLIENT_SIDE_USER_CANCELLED:
        return CLIENT_SIDE_USER_CANCELLED;
      case LDAPResultCode.CLIENT_SIDE_PARAM_ERROR:
        return CLIENT_SIDE_PARAM_ERROR;
      case LDAPResultCode.CLIENT_SIDE_NO_MEMORY:
        return CLIENT_SIDE_NO_MEMORY;
      case LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR:
        return CLIENT_SIDE_CONNECT_ERROR;
      case LDAPResultCode.CLIENT_SIDE_NOT_SUPPORTED:
        return CLIENT_SIDE_NOT_SUPPORTED;
      case LDAPResultCode.CLIENT_SIDE_CONTROL_NOT_FOUND:
        return CLIENT_SIDE_CONTROL_NOT_FOUND;
      case LDAPResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED:
        return CLIENT_SIDE_NO_RESULTS_RETURNED;
      case LDAPResultCode.CLIENT_SIDE_MORE_RESULTS_TO_RETURN:
        return CLIENT_SIDE_MORE_RESULTS_TO_RETURN;
      case LDAPResultCode.CLIENT_SIDE_CLIENT_LOOP:
        return CLIENT_SIDE_CLIENT_LOOP;
      case LDAPResultCode.CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED:
        return CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED;
      case LDAPResultCode.CANCELED:
        return CANCELED;
      case LDAPResultCode.NO_SUCH_OPERATION:
        return NO_SUCH_OPERATION;
      case LDAPResultCode.TOO_LATE:
        return TOO_LATE;
      case LDAPResultCode.CANNOT_CANCEL:
        return CANNOT_CANCEL;
      case LDAPResultCode.ASSERTION_FAILED:
        return ASSERTION_FAILED;
      case LDAPResultCode.AUTHORIZATION_DENIED:
        return AUTHORIZATION_DENIED;
      case LDAPResultCode.NO_OPERATION:
        // FIXME -- We will also need to handle the official result
        //          code when it is allocated.
        return NO_OPERATION;
      default:
        return ResultCode.OTHER;
    }
  }



  /**
   * Retrieves the short human-readable name for this result code.
   *
   * @return  The short human-readable name for this result code.
   */
  public Message getResultCodeName()
  {
    return resultCodeName;
  }



  /**
   * Retrieves a string representation of this result code.
   *
   * @return  A string representation of this result code.
   */
  public String toString()
  {
    return resultCodeName != null ? resultCodeName.toString() : null;
  }
}

