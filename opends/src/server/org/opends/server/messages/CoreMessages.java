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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.messages;



import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the core of the Directory Server.
 */
public class CoreMessages
{
  /**
   * The message ID for the message that will be used if a request attempts to
   * cancel an abandon operation.
   */
  public static final int MSGID_CANNOT_CANCEL_ABANDON =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 1;



  /**
   * The message ID for the message that will be used if a request attempts to
   * cancel a bind operation.
   */
  public static final int MSGID_CANNOT_CANCEL_BIND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 2;



  /**
   * The message ID for the message that will be used if a request attempts to
   * cancel an unbind operation.
   */
  public static final int MSGID_CANNOT_CANCEL_UNBIND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 3;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because the client issued an unbind request.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_UNBIND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 4;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because the client disconnected from the server.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_CLIENT_CLOSURE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 5;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because the server rejected the connection.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_REJECTED_CLIENT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 6;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because of an I/O error while trying to interact with
   * the client.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_IO_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 7;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because of an unrecoverable protocol error while
   * interacting with the client.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_PROTOCOL_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 8;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because the Directory Server was shutting down.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_SERVER_SHUTDOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 9;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was terminated by an administrator.
   */
  public static final int MSGID_DISCONNECT_BY_ADMINISTRATOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 10;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because of some kind of security problem.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_SECURITY_PROBLEM =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 11;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because the maximum request size was exceeded.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_MAX_REQUEST_SIZE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 12;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because an administrative limit was exceeded.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_ADMIN_LIMIT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 13;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because an idle time limit was exceeded.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_IDLE_TIME_LIMIT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 14;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because an I/O timeout occurred.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_IO_TIMEOUT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 15;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed by a plugin.
   */
  public static final int MSGID_DISCONNECT_BY_PLUGIN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 16;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed for some reason not covered bya predefined condition.
   */
  public static final int MSGID_DISCONNECT_OTHER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 17;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to access control processing.
   */
  public static final int MSGID_ERROR_CATEGORY_ACCESS_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 39;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to backend processing.
   */
  public static final int MSGID_ERROR_CATEGORY_BACKEND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 40;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to configuration processing.
   */
  public static final int MSGID_ERROR_CATEGORY_CONFIG =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 41;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to connection handling.
   */
  public static final int MSGID_ERROR_CATEGORY_CONNECTION_HANDLING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 42;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to core server processing.
   */
  public static final int MSGID_ERROR_CATEGORY_CORE_SERVER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 43;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to extended operation processing.
   */
  public static final int MSGID_ERROR_CATEGORY_EXTENDED_OPERATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 45;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to plugin processing.
   */
  public static final int MSGID_ERROR_CATEGORY_PLUGIN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 46;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to request handling.
   */
  public static final int MSGID_ERROR_CATEGORY_REQUEST_HANDLING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 47;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to SASL bind processing.
   */
  public static final int MSGID_ERROR_CATEGORY_SASL_MECHANISM =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 48;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to the Directory Server shutdown.
   */
  public static final int MSGID_ERROR_CATEGORY_SHUTDOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 49;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to the Directory Server startup.
   */
  public static final int MSGID_ERROR_CATEGORY_STARTUP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 50;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to synchronization processing.
   */
  public static final int MSGID_ERROR_CATEGORY_SYNCHRONIZATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 51;


  /**
   * The message ID for the string that will be used for the error message
   * severity for fatal error messages.
   */
  public static final int MSGID_ERROR_SEVERITY_FATAL_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 54;


  /**
   * The message ID for the string that will be used for the error message
   * severity for informational messages.
   */
  public static final int MSGID_ERROR_SEVERITY_INFORMATIONAL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 56;


  /**
   * The message ID for the string that will be used for the error message
   * severity for mild error messages.
   */
  public static final int MSGID_ERROR_SEVERITY_MILD_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 57;


  /**
   * The message ID for the string that will be used for the error message
   * severity for mild warning messages.
   */
  public static final int MSGID_ERROR_SEVERITY_MILD_WARNING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 58;


  /**
   * The message ID for the string that will be used for the error message
   * severity for severe error messages.
   */
  public static final int MSGID_ERROR_SEVERITY_SEVERE_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 59;


  /**
   * The message ID for the string that will be used for the error message
   * severity for severe warning messages.
   */
  public static final int MSGID_ERROR_SEVERITY_SEVERE_WARNING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 60;

  /**
   * The message ID for the string representation of the result code that will
   * be used for successful operations.
   */
  public static final int MSGID_RESULT_SUCCESS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 63;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because of an internal error.
   */
  public static final int MSGID_RESULT_OPERATIONS_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 64;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because of an protocol error.
   */
  public static final int MSGID_RESULT_PROTOCOL_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 65;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the operation time limit was
   * exceeded.
   */
  public static final int MSGID_RESULT_TIME_LIMIT_EXCEEDED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 66;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the operation size limit was
   * exceeded.
   */
  public static final int MSGID_RESULT_SIZE_LIMIT_EXCEEDED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 67;



  /**
   * The message ID for the string representation of the result code that will
   * be used for compare operations in which the assertion is false.
   */
  public static final int MSGID_RESULT_COMPARE_FALSE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 68;



  /**
   * The message ID for the string representation of the result code that will
   * be used for compare operations in which the assertion is true.
   */
  public static final int MSGID_RESULT_COMPARE_TRUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 69;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the requested authentication
   * method is not supported.
   */
  public static final int MSGID_RESULT_AUTH_METHOD_NOT_SUPPORTED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 70;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the requested operation requires
   * strong authentication.
   */
  public static final int MSGID_RESULT_STRONG_AUTH_REQUIRED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 71;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that encountered a referral during processing.
   */
  public static final int MSGID_RESULT_REFERRAL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 72;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because an administrative limit was
   * exceeded.
   */
  public static final int MSGID_RESULT_ADMIN_LIMIT_EXCEEDED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 73;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because a requested control is not
   * available for the operation but was designated critical.
   */
  public static final int MSGID_RESULT_UNAVAILABLE_CRITICAL_EXTENSION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 74;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the requested operation requires
   * confidentiality between the client and the server.
   */
  public static final int MSGID_RESULT_CONFIDENTIALITY_REQUIRED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 75;



  /**
   * The message ID for the string representation of the result code that will
   * be used for SASL bind operations that require multiple steps and have not
   * yet completed the sequence.
   */
  public static final int MSGID_RESULT_SASL_BIND_IN_PROGRESS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 76;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because a requested attribute does not
   * exist.
   */
  public static final int MSGID_RESULT_NO_SUCH_ATTRIBUTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 77;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because a requested attribute type is
   * not defined in the server schema.
   */
  public static final int MSGID_RESULT_UNDEFINED_ATTRIBUTE_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 78;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the client requested an
   * inappropriate type of matching against the server.
   */
  public static final int MSGID_RESULT_INAPPROPRIATE_MATCHING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 79;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because a server constraint would have
   * been violated.
   */
  public static final int MSGID_RESULT_CONSTRAINT_VIOLATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 80;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because an attribute or value conflict
   * was detected.
   */
  public static final int MSGID_RESULT_ATTRIBUTE_OR_VALUE_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 81;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request included an invalid
   * attribute syntax.
   */
  public static final int MSGID_RESULT_INVALID_ATTRIBUTE_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 82;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request targeted an entry
   * that does not exist.
   */
  public static final int MSGID_RESULT_NO_SUCH_OBJECT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 83;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because an alias was encountered for an
   * inappropriate type of operation.
   */
  public static final int MSGID_RESULT_ALIAS_PROBLEM =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 84;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request included a malformed
   * DN.
   */
  public static final int MSGID_RESULT_INVALID_DN_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 85;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because a problem was encountered while
   * dereferencing an alias while processing a search.
   */
  public static final int MSGID_RESULT_ALIAS_DEREFERENCING_PROBLEM =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 86;



  /**
   * The message ID for the string representation of the result code that will
   * be used for bind operations that failed because the request attempted a
   * type of authentication that is not appropriate for the target user.
   */
  public static final int MSGID_RESULT_INAPPROPRIATE_AUTHENTICATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 87;



  /**
   * The message ID for the string representation of the result code that will
   * be used for bind operations that failed because the client did not provide
   * valid credentials for the target user.
   */
  public static final int MSGID_RESULT_INVALID_CREDENTIALS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 88;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the client did not have
   * permission to perform the requested operation.
   */
  public static final int MSGID_RESULT_INSUFFICIENT_ACCESS_RIGHTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 89;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the server was too busy to
   * process the request.
   */
  public static final int MSGID_RESULT_BUSY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 90;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because one or more resources needed to
   * process the request were unavailable.
   */
  public static final int MSGID_RESULT_UNAVAILABLE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 91;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the server was unwilling to
   * perform the requested operation.
   */
  public static final int MSGID_RESULT_UNWILLING_TO_PERFORM =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 92;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the server detected a referral
   * or chaining loop.
   */
  public static final int MSGID_RESULT_LOOP_DETECT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 93;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request would have violated
   * the naming configuration for the server.
   */
  public static final int MSGID_RESULT_NAMING_VIOLATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 94;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request would have resulted
   * in an entry that violated the server schema.
   */
  public static final int MSGID_RESULT_OBJECTCLASS_VIOLATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 95;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the requested operation is not
   * allowed on non-leaf entries.
   */
  public static final int MSGID_RESULT_NOT_ALLOWED_ON_NONLEAF =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 96;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the requested operation is not
   * allowed on an RDN attribute.
   */
  public static final int MSGID_RESULT_NOT_ALLOWED_ON_RDN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 97;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request would have resulted
   * in an entry that conflicts with an entry that already exists.
   */
  public static final int MSGID_RESULT_ENTRY_ALREADY_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 98;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request attempted to alter
   * the set of objectclasses for an entry that is not permitted.
   */
  public static final int MSGID_RESULT_OBJECTCLASS_MODS_PROHIBITED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 99;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request would have impacted
   * information in multiple servers or repositories.
   */
  public static final int MSGID_RESULT_AFFECTS_MULTIPLE_DSAS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 100;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that were canceled.
   */
  public static final int MSGID_RESULT_CANCELED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 101;



  /**
   * The message ID for the string representation of the result code that will
   * be used for cases in which an attempted cancel operation failed because the
   * requested operation could not be found.
   */
  public static final int MSGID_RESULT_NO_SUCH_OPERATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 102;



  /**
   * The message ID for the string representation of the result code that will
   * be used for cases in which an attempted cancel operation failed because the
   * requested operation had already reached a point at which it was too late to
   * cancel.
   */
  public static final int MSGID_RESULT_TOO_LATE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 103;



  /**
   * The message ID for the string representation of the result code that will
   * be used for cases in which a cancel attempt failed because the server could
   * not cancel the specified operation.
   */
  public static final int MSGID_RESULT_CANNOT_CANCEL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 104;



  /**
   * The message ID for the string representation of the result code that will
   * be used for results that do not apply to any of the other result codes that
   * have been defined.
   */
  public static final int MSGID_RESULT_OTHER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 105;



  /**
   * The message ID for the warning that will be logged if it is not possible to
   * determine the attribute usage for an attribute type.
   */
  public static final int MSGID_UNKNOWN_ATTRIBUTE_USAGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_WARNING | 106;



  /**
   * The message ID for the response message that will be sent to a client if
   * an operation is canceled because the Directory Server is shutting down.
   */
  public static final int MSGID_CANCELED_BY_SHUTDOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 107;



  /**
   * The message ID for the error message that will be written if a worker
   * thread catches an exception while processing an operation that wasn't
   * caught higher in the call stack.  This message should have three arguments,
   * which are the name of the worker thread, a string representations of the
   * operation being processed, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_UNCAUGHT_WORKER_THREAD_EXCEPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 108;



  /**
   * The message ID for the error message that will be written if a worker
   * thread exits for any reason other than a Directory Server shutdown.  This
   * message should take a single string argument, which is the name of the
   * worker thread that is exiting.
   */
  public static final int MSGID_UNEXPECTED_WORKER_THREAD_EXIT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 109;



  /**
   * The message ID for the error message that will be written if the server
   * cannot create a worker thread for some reason.  This should take a single
   * string argument, which is a string representation of the exception that
   * was caught when trying to create the thread.
   */
  public static final int MSGID_CANNOT_CREATE_WORKER_THREAD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 110;



  /**
   * The message ID for the message that will be used if the work queue rejects
   * a new operation because the directory server has already started its
   * shutdown process.  It does not take any arguments.
   */
  public static final int MSGID_OP_REJECTED_BY_SHUTDOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_WARNING | 111;



  /**
   * The message ID for the message that will be used if the work queue rejects
   * a new operation because the queue already contains the maximum allowed
   * number of pending requests.  It should take a single integer argument,
   * which is the maximum number of pending requests.
   */
  public static final int MSGID_OP_REJECTED_BY_QUEUE_FULL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 112;



  /**
   * The message ID for the message that will be logged if a worker thread that
   * is waiting for work gets interrupted for any reason other than a Directory
   * Server shutdown.  It takes two string arguments, which are the name of
   * the worker thread that was interrupted and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_WORKER_INTERRUPTED_WITHOUT_SHUTDOWN =
      CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 113;



  /**
   * The message ID for the message that will be logged if an unexpected
   * exception is caught while a worker thread is waiting for more work.  It
   * takes two string arguments, which are the name of the worker thread and the
   * string representation of the exception that was caught.
   */
  public static final int MSGID_WORKER_WAITING_UNCAUGHT_EXCEPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 114;



  /**
   * The message DI for the message that will be logged if the work queue
   * catches an exception while trying to cancel a pending operation because
   * the server is shutting down.  It should take two string arguments, which
   * are string representations of the operation to cancel and of the exception
   * that was caught.
   */
  public static final int MSGID_QUEUE_UNABLE_TO_CANCEL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_WARNING | 115;



  /**
   * The message DI for the message that will be logged if the work queue
   * catches an exception while trying to notify a worker thread that the server
   * is shutting down.  It should take two string arguments, which is the name
   * of the worker thread it was trying to notify and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_QUEUE_UNABLE_TO_NOTIFY_THREAD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_WARNING | 116;



  /**
   * The message ID for the message that will be used to indicate that a client
   * connection was closed because of an internal error within the server.
   */
  public static final int MSGID_DISCONNECT_DUE_TO_SERVER_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 117;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * bootstrap the Directory Server configuration while the server is running.
   * This does not take any arguments.
   */
  public static final int MSGID_CANNOT_BOOTSTRAP_WHILE_RUNNING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 118;



  /**
   * The message ID for the message that will be used if the class specified as
   * the configuration handler cannot be loaded.  This takes two arguments,
   * which are the name of the configuration class and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_CANNOT_LOAD_CONFIG_HANDLER_CLASS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 119;



  /**
   * The message ID for the message that will be used if the server cannot
   * create an instance of the configuration handler class.  This takes two
   * arguments,  which are the name of the configuration class and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CANNOT_INSTANTIATE_CONFIG_HANDLER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 120;



  /**
   * The message ID for the message that will be used if the configuration
   * handler cannot be initialized.  This takes three arguments,  which are the
   * name of the configuration class, the path to the configuration file, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CANNOT_INITIALIZE_CONFIG_HANDLER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 121;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * start the server before it has been bootstrapped.  This does not take any
   * arguments.
   */
  public static final int MSGID_CANNOT_START_BEFORE_BOOTSTRAP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 122;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * start the server while another instance is already running.  This does not
   * take any arguments.
   */
  public static final int MSGID_CANNOT_START_WHILE_RUNNING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 123;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to schema processing.
   */
  public static final int MSGID_ERROR_CATEGORY_SCHEMA =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 124;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * normalize the value for an attribute type that does not have an equality
   * matching rule.  This takes two arguments, which are the value to normalize
   * and the name of the attribute type.
   */
  public static final int MSGID_ATTR_TYPE_NORMALIZE_NO_MR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 125;



  /**
   * The message ID for the message that will be used if an entry is missing an
   * attribute that is required by one of its objectclasses.  This takes three
   * arguments, which are the DN of the entry, the name of the missing
   * attribute, and the name of the objectclass that requires that attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_MISSING_REQUIRED_ATTR_FOR_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 126;



  /**
   * The message ID for the message that will be used if an entry includes a
   * user attribute that is not allowed by any of the associated objectclasses.
   * This takes two arguments, which are the DN of the entry and the name of the
   * attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_DISALLOWED_USER_ATTR_FOR_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 127;



  /**
   * The message ID for the string that will be used if an error occurs while
   * attempting to bootstrap an attribute matching rule.  This takes two
   * arguments, which are the class name of the matching rule class and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 129;



  /**
   * The message ID for the string that will be used if an error occurs while
   * attempting to bootstrap an attribute syntax.  This takes two arguments,
   * which are the class name of the matching rule class and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CANNOT_BOOTSTRAP_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 130;


  /**
   * The message ID for the string that will be used for the error message
   * severity for the most important informational messages.
   */
  public static final int MSGID_ERROR_SEVERITY_NOTICE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 131;



  /**
   * The message ID for the message that will be used when the Directory Server
   * is beginning its bootstrap process.  This takes a single argument, which is
   * the Directory Server version string.
   */
  public static final int MSGID_DIRECTORY_BOOTSTRAPPING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 132;



  /**
   * The message ID for the message that will be used when the Directory Server
   * has completed the bootstrap process.  This does not take any arguments.
   */
  public static final int MSGID_DIRECTORY_BOOTSTRAPPED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 133;



  /**
   * The message ID for the message that will be used when the Directory Server
   * is beginning the startup process.  This takes a single argument, which is
   * the directory server version string.
   */
  public static final int MSGID_DIRECTORY_SERVER_STARTING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_NOTICE | 134;



  /**
   * The message ID for the message that will be used when the Directory Server
   * has completed its startup process.  This does not take any arguments.
   */
  public static final int MSGID_DIRECTORY_SERVER_STARTED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_NOTICE | 135;



  /**
   * The message ID for the string that will be used for error messages related
   * to processing in server extensions.
   */
  public static final int MSGID_ERROR_CATEGORY_EXTENSIONS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 137;



  /**
   * The message ID for the string that will be used if an error occurs while
   * attempting to create the JMX MBean server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_CANNOT_CREATE_MBEAN_SERVER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 138;



  /**
   * The message ID for the string that will be used if the server sends an
   * alert notification.  This takes four arguments, which are the class name of
   * the alert generator, the alert type, the alert ID, and the alert message.
   */
  public static final int MSGID_SENT_ALERT_NOTIFICATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_NOTICE | 139;



  /**
   * The message ID for the string that will be used if a thread generates an
   * uncaught exception that would have caused it to terminate abnormally.  This
   * takes two arguments, which are the name of the thread that threw the
   * exception and a detailed stack trace for that exception.
   */
  public static final int MSGID_UNCAUGHT_THREAD_EXCEPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 140;



  /**
   * The message ID for the string that will be used when the Directory Server
   * begins its shutdown process.  This takes two arguments, which are the name
   * of the class that initiated the shutdown and the reason for the shutdown.
   */
  public static final int MSGID_SERVER_SHUTDOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_NOTICE | 141;



  /**
   * The message ID for the string that will be used if the Directory Server
   * shutdown process is initiated by the shutdown hook.  This does not take any
   * arguments.
   */
  public static final int MSGID_SHUTDOWN_DUE_TO_SHUTDOWN_HOOK =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 142;


  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because the string was null or empty.  This
   * does not take any arguments.
   */
  public static final int MSGID_SEARCH_FILTER_NULL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 143;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because an unexpected exception was caught.
   * This takes two arguments, which are the filter string and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SEARCH_FILTER_UNCAUGHT_EXCEPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 144;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because it contained a parenthesis mismatch.
   * This takes three arguments, which are the filter string and the lower and
   * upper bounds of the substring containing the mismatch.
   */
  public static final int MSGID_SEARCH_FILTER_MISMATCHED_PARENTHESES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 145;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because a simple component did not have an
   * equal sign.  This takes three arguments, which are the filter string and
   * the lower and upper bounds of the substring containing the mismatch.
   */
  public static final int MSGID_SEARCH_FILTER_NO_EQUAL_SIGN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 146;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because it had a value with a backslash that
   * was not followed by two hex characters.  This takes two arguments, which
   * are the filter string and the position of the invalid escaped character.
   */
  public static final int MSGID_SEARCH_FILTER_INVALID_ESCAPED_BYTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 147;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because the first element did not start with
   * a parenthesis or the last element did not end with a parenthesis.  This
   * takes three arguments, which are the filter string, the beginning of the
   * compound filter, and the end of the compound filter.
   */
  public static final int MSGID_SEARCH_FILTER_COMPOUND_MISSING_PARENTHESES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 148;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because it had a close parenthesis without
   * a corresponding open parenthesis.  This takes two arguments, which are the
   * filter string and the position of the unmatched close parenthesis.
   */
  public static final int
       MSGID_SEARCH_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 149;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because it had an open parenthesis without
   * a corresponding close parenthesis.  This takes two arguments, which are the
   * filter string and the position of the unmatched open parenthesis.
   */
  public static final int
       MSGID_SEARCH_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 150;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because a substring component did not have
   * any wildcard characters in the value.  This takes three arguments, which
   * are  the filter string and the beginning and end positions for the value.
   */
  public static final int MSGID_SEARCH_FILTER_SUBSTRING_NO_ASTERISKS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 151;



  /**
   * The message ID for the string that will be used if a filter string cannot
   * be decoded as a search filter because an extensible match component did not
   * have a colon to denote the end of the attribute type.  This takes two
   * arguments, which are  the filter string and the start position for the
   * extensible match filter.
   */
  public static final int MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_COLON =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 152;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because it had an invalid filter type.  This
   * takes three arguments, which are the DN of the entry, a string
   * representation of the filter, and the name of the invalid filter type.
   */
  public static final int MSGID_SEARCH_FILTER_INVALID_FILTER_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 153;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the evaluation returned an invalid
   * result type.  This takes three arguments, which are the DN of the entry, a
   * string representation of the filter, and the name of the invalid result
   * type.
   */
  public static final int MSGID_SEARCH_FILTER_INVALID_RESULT_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 154;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a compound filter
   * type in which the set of subcomponents was null.  This takes three
   * arguments, which are the DN of the entry, a string representation of the
   * filter, and the filter type containing the set of null elements.
   */
  public static final int MSGID_SEARCH_FILTER_COMPOUND_COMPONENTS_NULL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 155;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter had too many levels of
   * nesting.  This takes two arguments, which are the DN of the entry and a
   * string representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_NESTED_TOO_DEEP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 156;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a NOT filter in
   * which the subcomponent was null.  This takes two arguments, which are the
   * DN of the entry and a string representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_NOT_COMPONENT_NULL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 157;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included an equality
   * component in which the attribute type was null.  This takes two arguments,
   * which are the DN of the entry and a string representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_EQUALITY_NO_ATTRIBUTE_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 158;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included an equality
   * component in which the assertion value was null.  This takes three
   * arguments, which are the DN of the entry, a string representation of the
   * filter, and the name of the attribute type in the filter.
   */
  public static final int MSGID_SEARCH_FILTER_EQUALITY_NO_ASSERTION_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 159;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a substring
   * component in which the attribute type was null.  This takes two arguments,
   * which are the DN of the entry and a string representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_SUBSTRING_NO_ATTRIBUTE_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 160;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a substring
   * component without any subInitial/subAny/subFinal elements.  This takes
   * three arguments, which are the DN of the entry, a string representation of
   * the filter, and the name of the attribute type in the filter.
   */
  public static final int
       MSGID_SEARCH_FILTER_SUBSTRING_NO_SUBSTRING_COMPONENTS =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 161;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a greater-or-equal
   * component in which the attribute type was null.  This takes two arguments,
   * which are the DN of the entry and a string representation of the filter.
   */
  public static final int
       MSGID_SEARCH_FILTER_GREATER_OR_EQUAL_NO_ATTRIBUTE_TYPE =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 162;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a greater-or-equal
   * component in which the assertion value was null.  This takes three
   * arguments, which are the DN of the entry, a string representation of the
   * filter, and the name of the attribute type in the filter.
   */
  public static final int
       MSGID_SEARCH_FILTER_GREATER_OR_EQUAL_NO_VALUE =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 163;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a less-or-equal
   * component in which the attribute type was null.  This takes two arguments,
   * which are the DN of the entry and a string representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_LESS_OR_EQUAL_NO_ATTRIBUTE_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 164;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a less-or-equal
   * component in which the assertion value was null.  This takes three
   * arguments, which are the DN of the entry, a string representation of the
   * filter, and the name of the attribute type in the filter.
   */
  public static final int MSGID_SEARCH_FILTER_LESS_OR_EQUAL_NO_ASSERTION_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 165;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included a presence component
   * in which the attribute type was null.  This takes two arguments, which are
   * the DN of the entry and a string representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_PRESENCE_NO_ATTRIBUTE_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 166;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included an approximate
   * component in which the attribute type was null.  This takes two arguments,
   * which are the DN of the entry and a string representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_APPROXIMATE_NO_ATTRIBUTE_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 167;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included an approximate
   * component in which the assertion value was null.  This takes three
   * arguments, which are the DN of the entry, a string representation of the
   * filter, and the name of the attribute type in the filter.
   */
  public static final int MSGID_SEARCH_FILTER_APPROXIMATE_NO_ASSERTION_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 168;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included an extensibleMatch
   * component that did not have either an assertion value.  This takes two
   * arguments, which are the DN of the entry and a string representation of the
   * filter.
   */
  public static final int
       MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_ASSERTION_VALUE =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 169;



  /**
   * The message ID for the string that will be used if a filter cannot be
   * evaluated against an entry because the filter included an extensibleMatch
   * component that did not have either an attribute type or matching rule ID.
   * This takes two arguments, which are the DN of the entry and a string
   * representation of the filter.
   */
  public static final int MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_RULE_OR_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 170;



  /**
   * The message ID for the string that will be used if an attempt is made to
   * decode a null or empty string as an RDN.  This does not take any arguments.
   */
  public static final int MSGID_RDN_DECODE_NULL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 171;



  /**
   * The message ID for the string that will be used if an attempt is made to
   * decode a string as an RDN but that string ended with the attribute type but
   * did not have an equal sign or value.  This takes two arguments, which are
   * the RDN string to decode and the parsed attribute type.
   */
  public static final int MSGID_RDN_END_WITH_ATTR_NAME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 172;



  /**
   * The message ID for the string that will be used if an attempt is made to
   * decode a string as an RDN but an unexpected character was encountered where
   * an equal sign should have been.  This takes four arguments, which are
   * the RDN string to decode, and the parsed attribute type, the illegal
   * character found instead of an equal sign, and the position of that illegal
   * character.
   */
  public static final int MSGID_RDN_NO_EQUAL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 173;



  /**
   * The message ID for the string that will be used if an attempt is
   * made to decode a string as an RDN but that string contained an
   * unexpected plus, comma, or semicolon. This takes two arguments,
   * which are the RDN string to decode and the position of the
   * illegal plus, comma, or semicolon.
   */
  public static final int MSGID_RDN_UNEXPECTED_COMMA =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 174;



  /**
   * The message ID for the string that will be used if an attempt is made to
   * decode a string as an RDN but that string contained an illegal character
   * between RDN components.  This takes three arguments, which are the RDN
   * string to decode, the illegal character, and the position of that
   * character.
   */
  public static final int MSGID_RDN_ILLEGAL_CHARACTER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 175;



  /**
   * The message ID for the message that will be used if a failure occurs while
   * trying to create the work queue.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CANNOT_CREATE_WORK_QUEUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 176;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a backend with a suffix that is already in use.  This takes two
   * arguments, which are the DN of the suffix that is being registered, and the
   * fully-qualified name of the class that defines the existing backend for
   * that suffix.
   */
  public static final int MSGID_CANNOT_REGISTER_DUPLICATE_SUFFIX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 180;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a backend with a suffix that is already in use as a sub-suffix of
   * an existing suffix.  This takes two arguments, which are the DN of the
   * suffix that is being registered, and the DN of the suffix with which the
   * existing sub-suffix is associated.
   */
  public static final int MSGID_CANNOT_REGISTER_DUPLICATE_SUBSUFFIX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 181;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a private suffix as a sub-suffix below a non-private backend. This
   * takes two arguments, which are the DN of the suffix that is being
   * registered, and the DN of the non-private suffix that would be superior to
   * the private suffix.
   */
  public static final int
       MSGID_CANNOT_REGISTER_PRIVATE_SUFFIX_BELOW_USER_PARENT =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 182;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to retrieve the configuration entry from the root DSE.  This takes
   * a single argument, which is a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CANNOT_GET_ROOT_DSE_CONFIG_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 183;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register an attribute type with the server schema with an OID that
   * conflicts with the OID of an existing attribute type.  This takes three
   * arguments, which are the primary name for the rejected attribute type, the
   * conflicting OID, and the primary name for the existing attribute type.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_ATTRIBUTE_OID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 184;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register an attribute type with the server schema with a name that
   * conflicts with the name of an existing attribute type.  This takes three
   * arguments, which are the primary name for the rejected attribute type, the
   * conflicting name, and the primary name for the existing attribute type.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_ATTRIBUTE_NAME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 185;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register an objectclass with the server schema with an OID that conflicts
   * with the OID of an existing objectclass.  This takes three arguments, which
   * are the primary name for the rejected objectclass, the conflicting OID, and
   * the primary name for the existing objectclass.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_OBJECTCLASS_OID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 186;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register an attribute type with the server schema with a name that
   * conflicts with the name of an existing attribute type.  This takes three
   * arguments, which are the primary name for the rejected attribute type, the
   * conflicting name, and the primary name for the existing attribute type.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_OBJECTCLASS_NAME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 187;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register an attribute syntax with the server schema with an OID that
   * conflicts with the OID of an existing syntax.  This takes three arguments,
   * which are the primary name for the rejected attribute syntax, the
   * conflicting OID, and the primary name for the existing syntax.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_SYNTAX_OID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 188;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a matching rule with the server schema with an OID that conflicts
   * with the OID of an existing rule.  This takes three arguments, which are
   * the name for the rejected matching rule, the conflicting OID, and the name
   * for the existing matching rule.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_MR_OID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 189;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a matching rule with the server schema with a name that conflicts
   * with the name of an existing rule.  This takes three arguments, which are
   * the OID for the rejected matching rule, the conflicting name, and the OID
   * for the existing matching rule.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_MR_NAME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 190;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a matching rule use with the server schema with a matching rule
   * that conflicts with the matching rule for an existing matching rule use.
   * This takes three arguments, which are the name for the rejected matching
   * rule use, the name of the conflicting matching rule, and the name of the
   * existing matching rule use.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_MATCHING_RULE_USE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 191;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a DIT content rule with the server schema with an objectclass that
   * conflicts with the objectclass for an existing DIT content rule.  This
   * takes three arguments, which are the name for the rejected DIT content
   * rule, the name of the conflicting objectclass, and the name of the existing
   * DIT content rule.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_DIT_CONTENT_RULE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 192;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a DIT structure rule with the server schema with a name form that
   * conflicts with the name form for an existing DIT structure rule.  This
   * takes three arguments, which are the name for the rejected DIT structure
   * rule, the name of the conflicting name form, and the name of the existing
   * DIT structure rule.
   */
  public static final int
       MSGID_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_NAME_FORM =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 193;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a DIT structure rule with the server schema with a rule ID that
   * conflicts with the rule ID for an existing DIT structure rule.  This takes
   * three arguments, which are the name for the rejected DIT structure rule,
   * the conflicting rule ID, and the name of the existing DIT structure rule.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_ID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 194;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a name form with the server schema with an objectclass that
   * conflicts with the objectclass for an existing name form.  This takes three
   * arguments, which are the name for the rejected name form, the name of the
   * conflicting objectclass, and the name of the existing name form.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_NAME_FORM_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 195;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a name form with the server schema with an OID that conflicts with
   * the OID for an existing name form.  This takes three arguments, which are
   * the name for the rejected name form, the conflicting OID, and the name of
   * the existing name form.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_NAME_FORM_OID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 196;


  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a name form with the server schema with a name that conflicts with
   * the name for an existing name form.  This takes three arguments, which are
   * the name for the rejected name form, the conflicting name, and the name of
   * the existing name form.
   */
  public static final int MSGID_SCHEMA_CONFLICTING_NAME_FORM_NAME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 197;



  /**
   * The message ID for the message that will be used if an entry includes
   * multiple structural objectclasses.  This takes three arguments, which are
   * the DN of the entry, and the names of two of the conflicting structural
   * objectclasses.
   */
  public static final int MSGID_ENTRY_SCHEMA_MULTIPLE_STRUCTURAL_CLASSES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 198;



  /**
   * The message ID for the message that will be used if an entry does not
   * include a structural objectclass.  This takes a single argument, which is
   * the DN of the entry.
   */
  public static final int MSGID_ENTRY_SCHEMA_NO_STRUCTURAL_CLASS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 199;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry with an attribute value that is invalid according to the
   * syntax for that attribute.  This takes four arguments, which are the DN of
   * the entry, the invalid value, the name of the attribute, and the reason
   * that the value is invalid.
   */
  public static final int MSGID_ADD_OP_INVALID_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 200;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a compare request with an attribute type that does not exist in the
   * specified entry.  This takes two arguments, which are the DN of the entry
   * and the name of the attribute.
   */
  public static final int MSGID_COMPARE_OP_NO_SUCH_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 201;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a compare request with an attribute type that does not exist in the
   * specified entry (or that exists but does not contain the requested set of
   * options).  This takes two arguments, which are the DN of the entry and the
   * name of the attribute.
   */
  public static final int MSGID_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 202;



  /**
   * The message ID for the string that will be used when the Directory Server
   * has essentially completed its shutdown process (with only the error and
   * debug loggers remaining to be stopped).  This does not take any arguments.
   */
  public static final int MSGID_SERVER_STOPPED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_NOTICE | 203;



  /**
   * The message ID for the message that will be logged if a worker thread is
   * stopped because the Directory Server thread count has been reduced.  This
   * takes a single argument, which is the name of the worker thread that is
   * being stopped.
   */
  public static final int MSGID_WORKER_STOPPED_BY_REDUCED_THREADNUMBER =
      CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 204;



  /**
   * The message ID for the message that will be used if an entry includes a
   * single-valued attribute that contains more than one value.  This takes two
   * arguments, which are the DN of the entry and the name of the attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_ATTR_SINGLE_VALUED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 205;



  /**
   * The message ID for the message that will be used if an entry has an RDN
   * that is missing an attribute that is required by a name form definition.
   * This takes three arguments, which are the DN of the entry, the name or OID
   * of the missing required attribute, and the name or OID of the name form
   * that requires that attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_RDN_MISSING_REQUIRED_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 206;



  /**
   * The message ID for the message that will be used if an entry has an RDN
   * that contains an attribute that is not allowed by a name form definition.
   * This takes three arguments, which are the DN of the entry, the name or OID
   * of the disallowed attribute, and the name or OID of the name form that does
   * not allow that attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_RDN_DISALLOWED_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 207;



  /**
   * The message ID for the message that will be used if an entry is missing an
   * attribute that is required by a DIT content rule.  This takes three
   * arguments, which are the DN of the entry, the name of the missing
   * attribute, and the name of the DIT content rule that requires that
   * attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_MISSING_REQUIRED_ATTR_FOR_DCR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 208;



  /**
   * The message ID for the message that will be used if an entry contains an
   * attribute that is prohibited by a DIT content rule.  This takes three
   * arguments, which are the DN of the entry, the name of the prohibited
   * attribute, and the name of the DIT content rule that requires that
   * attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_PROHIBITED_ATTR_FOR_DCR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 209;



  /**
   * The message ID for the message that will be used if an entry includes a
   * user attribute that is not allowed by the entry's DIT content rule.  This
   * takes three arguments, which are the DN of the entry, the name of the
   * attribute, and the name of the DIT content rule.
   */
  public static final int MSGID_ENTRY_SCHEMA_DISALLOWED_USER_ATTR_FOR_DCR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 210;



  /**
   * The message ID for the message that will be used if an entry includes an
   * auxiliary objectclass that is not allowed by the entry's DIT content rule.
   * This takes three arguments, which are the DN of the entry, the name of the
   * attribute, and the name of the DIT content rule.
   */
  public static final int MSGID_ENTRY_SCHEMA_DISALLOWED_AUXILIARY_CLASS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 211;



  /**
   * The message ID for the message that will be used if an entry could not be
   * checked against a DIT structure rule because the server was unable to
   * obtain a read lock on the parent entry.  This takes two arguments, which
   * are the DN of the entry and the DN of the parent entry.
   */
  public static final int MSGID_ENTRY_SCHEMA_DSR_COULD_NOT_LOCK_PARENT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 212;



  /**
   * The message ID for the message that will be used if an entry could not be
   * checked against a DIT structure rule because the server was unable to
   * retrieve its parent entry.  This takes two arguments, which are the DN of
   * the entry and the DN of the parent entry.
   */
  public static final int MSGID_ENTRY_SCHEMA_DSR_NO_PARENT_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 213;



  /**
   * The message ID for the message that will be used if an entry could not be
   * checked against a DIT structure rule because its parent entry did not
   * contain a single structural objectclass.  This takes two arguments, which
   * are the DN of the entry and the DN of the parent entry.
   */
  public static final int MSGID_ENTRY_SCHEMA_DSR_NO_PARENT_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 214;



  /**
   * The message ID for the message that will be used if an entry was not in
   * compliance with the DIT structure rule definition.  This takes four
   * arguments, which are the DN of the entry, the name of the DIT structure
   * rule, the structural objectclass of the entry being checked, and the
   * structural objectclass of that entry's parent.
   */
  public static final int MSGID_ENTRY_SCHEMA_DSR_DISALLOWED_SUPERIOR_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 215;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while attempting to evaluate a DIT structure rule for an entry.
   * This takes three arguments, which are the DN of the entry, the name of the
   * DIT structure rule, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_ENTRY_SCHEMA_COULD_NOT_CHECK_DSR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 216;



  /**
   * The message ID for the response message that will be sent to a client if
   * an operation is canceled because the client initiated a bind on the
   * connection.  This does not take any arguments.
   */
  public static final int MSGID_CANCELED_BY_BIND_REQUEST =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 217;



  /**
   * The message ID for the response message that will be sent to a client if
   * an attempt is made to perform a bind as a user that does not exist in the
   * server.  This takes a single argument, which is the bind DN.
   */
  public static final int MSGID_BIND_OPERATION_UNKNOWN_USER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 218;



  /**
   * The message ID for the response message that will be used if a bind
   * operation fails because the attempt to lock the user entry fails after
   * multiple attempts.  This takes a single argument, which is the bind DN.
   */
  public static final int MSGID_BIND_OPERATION_CANNOT_LOCK_USER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 219;



  /**
   * The message ID for the message that will be used if a startup plugin fails
   * and indicates that the startup process should be aborted.  This takes two
   * arguments, which are the error message from the startup plugin and the
   * unique ID for that message.
   */
  public static final int MSGID_STARTUP_PLUGIN_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 220;



  /**
   * The message ID for the response message that will be sent to a client if
   * an attempt is made to perform a bind as a user that does not have a
   * password.  This takes a single argument, which is the bind DN.
   */
  public static final int MSGID_BIND_OPERATION_NO_PASSWORD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 221;



  /**
   * The message ID for the response message that will be sent to a client if
   * an attempt is made to perform a bind with an unknown or unsupported SASL
   * mechanism.  This takes a single argument, which is the bind DN.
   */
  public static final int MSGID_BIND_OPERATION_UNKNOWN_SASL_MECHANISM =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 222;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * abandon an operation that the server doesn't know anything about.  This
   * takes a single argument, which is the message ID of the operation to
   * abandon.
   */
  public static final int MSGID_ABANDON_OP_NO_SUCH_OPERATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 223;



  /**
   * The message ID for the response message that will be used if an operation
   * is canceled because the client connection was terminated by a pre-parse
   * plugin.  This does not take any arguments.
   */
  public static final int MSGID_CANCELED_BY_PREPARSE_DISCONNECT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 224;



  /**
   * The message ID for the response message that will be used if an operation
   * is canceled because the client connection was terminated by a pre-operation
   * plugin.  This does not take any arguments.
   */
  public static final int MSGID_CANCELED_BY_PREOP_DISCONNECT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 225;



  /**
   * The message ID for the response message that will be used if an operation
   * is canceled because the client connection was terminated by a
   * post-operation plugin.  This does not take any arguments.
   */
  public static final int MSGID_CANCELED_BY_POSTOP_DISCONNECT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 226;



  /**
   * The message ID for the response message that will be used if it is not
   * possible to obtain a read lock on an entry for a compare operation.  This
   * takes a single argument, which is the DN of the entry that could not be
   * locked.
   */
  public static final int MSGID_COMPARE_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 227;



  /**
   * The message ID for the response message that will be used if a compare
   * operation fails because the requested entry does not exist.  This takes a
   * single argument, which is the DN of the requested entry.
   */
  public static final int MSGID_COMPARE_NO_SUCH_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 228;



  /**
   * The message ID for the response message that will be sent to a client if
   * an operation is canceled because the client requested that the operation be
   * abandoned.  This does not take any arguments.
   */
  public static final int MSGID_CANCELED_BY_ABANDON_REQUEST =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 229;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry with a null DN.  This does not take any arguments.
   */
  public static final int MSGID_ADD_CANNOT_ADD_ROOT_DSE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 230;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry with a DN that does not have a parent and is not defined as
   * a suffix in the server.  This takes a single argument, which is the DN of
   * the entry to add.
   */
  public static final int MSGID_ADD_ENTRY_NOT_SUFFIX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 231;



  /**
   * The message ID for the message that will be used if the attempt to lock the
   * parent entry fails when trying to perform an add.  This takes two
   * arguments, which are the DN of the entry to add and the DN of its parent.
   */
  public static final int MSGID_ADD_CANNOT_LOCK_PARENT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 232;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry when the parent does not exist.  This takes two arguments,
   * which are the DN of the entry to add and the DN of its parent.
   */
  public static final int MSGID_ADD_NO_PARENT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 233;



  /**
   * The message ID for the message that will be used if the attempt to lock the
   * target entry fails when trying to perform an add.  This takes a single
   * argument, which is the DN of the entry to add.
   */
  public static final int MSGID_ADD_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 234;



  /**
   * The message ID for the message that will be used if the attempt to lock the
   * target entry fails when trying to perform a delete.  This takes a single
   * argument, which is the DN of the entry to delete.
   */
  public static final int MSGID_DELETE_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 235;



  /**
   * The message ID for the response message that will be used if an operation
   * is canceled because the client connection was terminated by a search result
   * entry plugin.  This does not take any arguments.
   */
  public static final int MSGID_CANCELED_BY_SEARCH_ENTRY_DISCONNECT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 236;



  /**
   * The message ID for the response message that will be used if an operation
   * is canceled because the client connection was terminated by a search result
   * reference plugin.  This does not take any arguments.
   */
  public static final int MSGID_CANCELED_BY_SEARCH_REF_DISCONNECT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 237;



  /**
   * The message ID for the response message that will be used if search
   * operation processing is stopped prematurely because the time limit was
   * reached.  This takes a single argument, which is the effective time limit
   * for the search.
   */
  public static final int MSGID_SEARCH_TIME_LIMIT_EXCEEDED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 238;



  /**
   * The message ID for the response message that will be used if search
   * operation processing is stopped prematurely because the size limit was
   * reached.  This takes a single argument, which is the effective size limit
   * for the search.
   */
  public static final int MSGID_SEARCH_SIZE_LIMIT_EXCEEDED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 239;



  /**
   * The message ID for the response message that will be used if a search
   * request includes a base DN that is not associated with any backend.  This
   * takes a single argument, which is the search base DN.
   */
  public static final int MSGID_SEARCH_BASE_DOESNT_EXIST =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 240;



  /**
   * The message ID for the response message that will be used if an entry
   * cannot be deleted because it is not suitable for any of the backends in the
   * server.  This takes a single argument, which is the DN of the entry to
   * delete.
   */
  public static final int MSGID_DELETE_NO_SUCH_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 241;



  /**
   * The message ID for the response message that will be used if an entry
   * cannot be deleted because the backend that contains it has a subordinate
   * backend with a base DN that is below the target DN.  This takes two
   * arguments, which are the DN of the entry to delete and the base DN of the
   * subordinate backend.
   */
  public static final int MSGID_DELETE_HAS_SUB_BACKEND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 242;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because the new entry would not have a parent.  This takes
   * a single argument, which is the DN of the current entry.
   */
  public static final int MSGID_MODDN_NO_PARENT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 243;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because there is no backend for the current DN.  This takes
   * a single argument, which is the DN of the current entry.
   */
  public static final int MSGID_MODDN_NO_BACKEND_FOR_CURRENT_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 244;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because there is no backend for the new DN.  This takes two
   * arguments, which are the DN of the current entry and the DN of the new
   * entry.
   */
  public static final int MSGID_MODDN_NO_BACKEND_FOR_NEW_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 245;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because the current and new DNs are in different backends.
   * This takes two arguments, which are the DN of the current entry and the DN
   * of the new entry.
   */
  public static final int MSGID_MODDN_DIFFERENT_BACKENDS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 246;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because the server cannot obtain a write lock on the
   * current DN.  This takes a single argument, which is the DN of the current
   * entry.
   */
  public static final int MSGID_MODDN_CANNOT_LOCK_CURRENT_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 247;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because an exception is encountered while attempting to
   * lock the new DN for the entry.  This takes three arguments, which are the
   * DN of the current entry, the DN of the new entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_MODDN_EXCEPTION_LOCKING_NEW_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 248;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because the server cannot obtain a write lock on the new
   * DN.  This takes two arguments, which are the DN of the current entry and
   * the DN of the new entry.
   */
  public static final int MSGID_MODDN_CANNOT_LOCK_NEW_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 249;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because the current entry does not exist.  This takes a
   * single argument, which is the DN of the current entry.
   */
  public static final int MSGID_MODDN_NO_CURRENT_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 250;



  /**
   * The message ID for the message that will be used if the attempt to lock the
   * target entry fails when trying to perform a modify.  This takes a single
   * argument, which is the DN of the entry to modify.
   */
  public static final int MSGID_MODIFY_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 251;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because it does not exist.  This takes a single argument, which is
   * the DN of the target entry.
   */
  public static final int MSGID_MODIFY_NO_SUCH_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 252;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an add modification does not contain any values.  This
   * takes two arguments, which are the DN of the target entry and the name of
   * the attribute to add.
   */
  public static final int MSGID_MODIFY_ADD_NO_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 253;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an add modification includes values that violate the
   * associated attribute syntax.  This takes four arguments, which are the DN
   * of the target entry, the name of the attribute, the invalid value, and the
   * reason that the value is invalid.
   */
  public static final int MSGID_MODIFY_ADD_INVALID_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 254;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an add modification includes one or more values that are
   * already present in the entry.  This takes three arguments, which are the DN
   * of the entry, the name of the attribute, and the list of pre-existing
   * values.
   */
  public static final int MSGID_MODIFY_ADD_DUPLICATE_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 255;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because a delete modification would have removed an RDN value.
   * This takes two arguments, which are the DN of the entry and the name of the
   * RDN attribute.
   */
  public static final int MSGID_MODIFY_DELETE_RDN_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 256;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because a delete modification attempted to remove one or more
   * values that were not present in the entry.  This takes three arguments,
   * which are the DN of the entry, the name of the attribute, and the list of
   * missing values.
   */
  public static final int MSGID_MODIFY_DELETE_MISSING_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 257;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because a delete modification attempted to remove values from an
   * attribute that does not exist.  This takes two arguments, which are the DN
   * of the entry and the name of the attribute.
   */
  public static final int MSGID_MODIFY_DELETE_NO_SUCH_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 258;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because a replace modification attempted to use one or more values
   * that were invalid according to the associated syntax.  This takes four
   * arguments, which are the DN of the entry, the name of the attribute, the
   * invalid value, and a message explaining the reason that value is invalid.
   */
  public static final int MSGID_MODIFY_REPLACE_INVALID_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 259;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an increment modification attempted to update an RDN
   * attribute for the entry.  This takes two arguments, which are the DN of the
   * entry and the name of the RDN attribute.
   */
  public static final int MSGID_MODIFY_INCREMENT_RDN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 260;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an increment modification did not include a value.  This
   * takes two arguments, which are the DN of the entry and the name of the
   * attribute to increment.
   */
  public static final int MSGID_MODIFY_INCREMENT_REQUIRES_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 261;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an increment modification contained multiple values.  This
   * takes two arguments, which are the DN of the entry and the name of the
   * attribute to increment.
   */
  public static final int MSGID_MODIFY_INCREMENT_REQUIRES_SINGLE_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 262;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an increment modification included a value that was not
   * an integer.  This takes three arguments, which are the DN of the entry, the
   * name of the attribute to increment, and the invalid value.
   */
  public static final int MSGID_MODIFY_INCREMENT_PROVIDED_VALUE_NOT_INTEGER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 263;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an increment modification targeted an attribute that did
   * not exist in the entry.  This takes two arguments, which are the DN of the
   * entry and the name of the attribute to increment.
   */
  public static final int MSGID_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 264;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because an increment modification targeted an attribute that had a
   * value that could not be parsed as an integer.  This takes three arguments,
   * which are the DN of the entry, the name of the attribute to increment, and
   * the invalid value.
   */
  public static final int MSGID_MODIFY_INCREMENT_REQUIRES_INTEGER_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 265;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because the resulting change would have violated the schema.  This
   * takes two arguments, which are the DN of the entry and the reason that the
   * entry is v
   */
  public static final int MSGID_MODIFY_VIOLATES_SCHEMA =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 266;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because there is no backend that handles the targeted entry.
   * This takes a single argument, which is the DN of the entry.
   */
  public static final int MSGID_MODIFY_NO_BACKEND_FOR_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 267;



  /**
   * The message ID for the message that will be used if an extended operation
   * cannot be processed because there is no corresponding extended operation
   * handler.  This takes a single argument, which is the OID of the extended
   * request.
   */
  public static final int MSGID_EXTENDED_NO_HANDLER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 268;



  /**
   * The message ID for the message that will be used if an entry has an unknown
   * objectclass.  This takes two arguments, which are the DN of the entry and
   * the name or OID of the unrecognized objectclass.
   */
  public static final int MSGID_ENTRY_SCHEMA_UNKNOWN_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 269;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * during backend search processing.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SEARCH_BACKEND_EXCEPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 270;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because the new entry would not be valid according to the
   * server schema.  This takes two arguments, which are the DN of the current
   * entry and the reason that the new entry would be invalid.
   */
  public static final int MSGID_MODDN_VIOLATES_SCHEMA =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 271;



  /**
   * The message ID for the message that will be used if a connection handler is
   * finalized because the Directory Server is shutting down.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONNHANDLER_CLOSED_BY_SHUTDOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 272;



  /**
   * The message ID for the message that will be used if a connection handler is
   * finalized because it has been interactively disabled.  This does not take
   * any arguments.
   */
  public static final int MSGID_CONNHANDLER_CLOSED_BY_DISABLE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 273;



  /**
   * The message ID for the message that will be used if a connection handler is
   * finalized because it has been removed.  This does not take any arguments.
   */
  public static final int MSGID_CONNHANDLER_CLOSED_BY_DELETE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 274;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * include an undefined objectclass in an entry.  This takes two arguments,
   * which are the name of the objectclass and the DN of the entry.
   */
  public static final int MSGID_ENTRY_SET_UNKNOWN_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 275;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an undefined objectclass to an entry.  This takes two arguments, which
   * are the name of the objectclass and the DN of the entry.
   */
  public static final int MSGID_ENTRY_ADD_UNKNOWN_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 276;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a duplicate objectclass to an entry.  This takes two arguments, which
   * are the name of the objectclass and the DN of the entry.
   */
  public static final int MSGID_ENTRY_ADD_DUPLICATE_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 277;



  /**
   * The message ID for the error message that will be logged if an attempt is
   * made to perform a bind by a user with a password using an unknown storage
   * scheme.  This takes two arguments, which are the unknown storage scheme and
   * the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_UNKNOWN_STORAGE_SCHEME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 278;



  /**
   * The message ID for the message that will be used if a user attempts to bind
   * with the wrong password.  This does not take any arguments.
   */
  public static final int MSGID_BIND_OPERATION_WRONG_PASSWORD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 279;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to validate the user's password.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_BIND_OPERATION_PASSWORD_VALIDATION_EXCEPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 280;



  /**
   * The message ID for the message that will be used as the description for the
   * configClass command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 281;



  /**
   * The message ID for the message that will be used as the description for the
   * configFile command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 282;



  /**
   * The message ID for the message that will be used as the description for the
   * version command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_VERSION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 283;



  /**
   * The message ID for the message that will be used as the description for the
   * fullVersion command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_FULLVERSION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 284;



  /**
   * The message ID for the message that will be used as the description for the
   * systemInfo command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_SYSINFO =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 285;



  /**
   * The message ID for the message that will be used as the description for the
   * dumpMessages command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_DUMPMESSAGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 286;



  /**
   * The message ID for the message that will be used as the description for the
   * usage command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_USAGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 287;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line arguments.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_DSCORE_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 288;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to parse the command-line arguments.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_DSCORE_ERROR_PARSING_ARGS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 289;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to bootstrap the Directory Server.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_DSCORE_CANNOT_BOOTSTRAP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 290;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to start the Directory Server.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_DSCORE_CANNOT_START =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 291;



  /**
   * The message ID for the message that will be used if a backup information
   * structure contains a line with no equal sign.  This takes two arguments,
   * which are the line that was read and the path to the directory with this
   * information.
   */
  public static final int MSGID_BACKUPINFO_NO_DELIMITER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 292;



  /**
   * The message ID for the message that will be used if a backup information
   * structure contains a line with no property name.  This takes two arguments,
   * which are the line that was read and the path to the directory with this
   * information.
   */
  public static final int MSGID_BACKUPINFO_NO_NAME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 293;



  /**
   * The message ID for the message that will be used if a backup information
   * structure contains multiple backup IDs.  This takes three arguments, which
   * are the path to the backup directory, the first backup ID, and the second
   * backup ID.
   */
  public static final int MSGID_BACKUPINFO_MULTIPLE_BACKUP_IDS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 294;



  /**
   * The message ID for the message that will be used if a backup information
   * structure contains a line with an unknown property.  This takes three
   * arguments, which are the path to the backup directory, the name of the
   * unknown property, and the provided value for that property.
   */
  public static final int MSGID_BACKUPINFO_UNKNOWN_PROPERTY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 295;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to decode a backup info structure.  This takes two
   * arguments, which are the path to the backup directory and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_BACKUPINFO_CANNOT_DECODE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 296;



  /**
   * The message ID for the message that will be used if a backup information
   * structure does not contain a backup ID.  This takes a single argument,
   * which is the path to the backup directory.
   */
  public static final int MSGID_BACKUPINFO_NO_BACKUP_ID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 297;



  /**
   * The message ID for the message that will be used if a backup information
   * structure does not contain a backup date.  This takes two arguments, which
   * are the backup ID and the path to the backup directory.
   */
  public static final int MSGID_BACKUPINFO_NO_BACKUP_DATE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 298;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a backup to a backup directory with the same ID as an existing backup.
   * This takes two arguments, which are the conflicting backup ID and the path
   * to the backup directory.
   */
  public static final int MSGID_BACKUPDIRECTORY_ADD_DUPLICATE_ID =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 299;



  /**
   * The message ID for the message that will be used if a backup cannot be
   * removed from a backup directory because it does not exist.  This takes
   * two arguments, which are the provided backup ID and the path to the backup
   * directory.
   */
  public static final int MSGID_BACKUPDIRECTORY_NO_SUCH_BACKUP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 300;



  /**
   * The message ID for the message that will be used if a backup cannot be
   * removed because another backup is dependent on it.  This takes three
   * arguments, which are the provided backup ID, the path to the backup
   * directory, and the backup ID of the backup that depends on the specified
   * backup.
   */
  public static final int MSGID_BACKUPDIRECTORY_UNRESOLVED_DEPENDENCY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 301;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the directory to hold the backup descriptor.  This takes
   * two arguments, which are the path to the directory and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_BACKUPDIRECTORY_CANNOT_CREATE_DIRECTORY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 302;



  /**
   * The message ID for the message that will be used if the backup directory
   * path exists but is not a directory.  This takes a single argument, which is
   * the path to the specified directory.
   */
  public static final int MSGID_BACKUPDIRECTORY_NOT_DIRECTORY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 303;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to delete the saved backup descriptor so that the current
   * descriptor can be renamed to it.  This takes four arguments, which are the
   * path to the saved descriptor, a string representation of the exception that
   * was caught, the path to the new descriptor, and the path to which the new
   * descriptor should be renamed.
   */
  public static final int MSGID_BACKUPDIRECTORY_CANNOT_DELETE_SAVED_DESCRIPTOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 304;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to rename the current backup descriptor so that a new one can be
   * used in its place.  This takes five arguments, which are the path to the
   * current descriptor file, the path to which it was to be renamed, a string
   * representation of the exception that was caught, the path of the new
   * descriptor file, and the path to which the new descriptor file should be
   * renamed.
   */
  public static final int
       MSGID_BACKUPDIRECTORY_CANNOT_RENAME_CURRENT_DESCRIPTOR =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 305;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to rename the new descriptor with the correct name.  This takes
   * three arguments, which are the path to the new descriptor, the path to
   * which it should be renamed, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_BACKUPDIRECTORY_CANNOT_RENAME_NEW_DESCRIPTOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 306;



  /**
   * The message ID for the message that will be used the backup directory
   * descriptor file does not exist.  This takes a single argument, which is the
   * path to the specified file.
   */
  public static final int MSGID_BACKUPDIRECTORY_NO_DESCRIPTOR_FILE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 307;



  /**
   * The message ID for the message that will be used the first line of the
   * backup directory descriptor file is blank or null rather than the config
   * entry DN.  This takes a single argument, which is the path to the
   * descriptor file.
   */
  public static final int MSGID_BACKUPDIRECTORY_CANNOT_READ_CONFIG_ENTRY_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 308;



  /**
   * The message ID for the message that will be used the first line of the
   * backup directory descriptor file contains something other than the backend
   * config entry DN.  This takes two arguments, which are the path to the
   * descriptor file and the contents of the first line from that file.
   */
  public static final int MSGID_BACKUPDIRECTORY_FIRST_LINE_NOT_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 309;



  /**
   * The message ID for the message that will be used the value on the first
   * line of the backup directory descriptor file cannot be parsed as a DN.
   * This takes three arguments, which are the value that could not be decoded,
   * the path to the descriptor file, and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_BACKUPDIRECTORY_CANNOT_DECODE_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 310;



  /**
   * The message ID for the message that will be used if an attempt to acquire
   * a shared file lock is rejected because an exclusive lock is already held by
   * the same JVM.  This takes a single argument, which is the path to the file
   * that was to be locked.
   */
  public static final int MSGID_FILELOCKER_LOCK_SHARED_REJECTED_BY_EXCLUSIVE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 311;



  /**
   * The message ID for the message that will be used if an attempt to create a
   * lock file failed.  This takes two arguments, which are the path to the file
   * that was to be created and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_FILELOCKER_LOCK_SHARED_FAILED_CREATE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 312;



  /**
   * The message ID for the message that will be used if an attempt to open a
   * lock file failed.  This takes two arguments, which are the path to the file
   * that was to be opened and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_FILELOCKER_LOCK_SHARED_FAILED_OPEN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 313;



  /**
   * The message ID for the message that will be used if an attempt to lock a
   * file failed.  This takes two arguments, which are the path to the file
   * that was to be locked and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_FILELOCKER_LOCK_SHARED_FAILED_LOCK =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 314;



  /**
   * The message ID for the message that will be used if a shared lock is not
   * granted on a requested file.  This takes a single argument, which is the
   * path to the file that was to be locked.
   */
  public static final int MSGID_FILELOCKER_LOCK_SHARED_NOT_GRANTED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 315;



  /**
   * The message ID for the message that will be used if an attempt to acquire
   * an exclusive file lock is rejected because an exclusive lock is already
   * held by the same JVM.  This takes a single argument, which is the path to
   * the file that was to be locked.
   */
  public static final int
       MSGID_FILELOCKER_LOCK_EXCLUSIVE_REJECTED_BY_EXCLUSIVE =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 316;



  /**
   * The message ID for the message that will be used if an attempt to acquire
   * an exclusive file lock is rejected because a shared lock is already held by
   * the same JVM.  This takes a single argument, which is the path to the file
   * that was to be locked.
   */
  public static final int MSGID_FILELOCKER_LOCK_EXCLUSIVE_REJECTED_BY_SHARED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 317;



  /**
   * The message ID for the message that will be used if an attempt to create a
   * lock file failed.  This takes two arguments, which are the path to the file
   * that was to be created and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_CREATE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 318;



  /**
   * The message ID for the message that will be used if an attempt to open a
   * lock file failed.  This takes two arguments, which are the path to the file
   * that was to be opened and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_OPEN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 319;



  /**
   * The message ID for the message that will be used if an attempt to lock a
   * file failed.  This takes two arguments, which are the path to the file
   * that was to be locked and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_LOCK =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 320;



  /**
   * The message ID for the message that will be used if an exclusive lock is
   * not granted on a requested file.  This takes a single argument, which is
   * the path to the file that was to be locked.
   */
  public static final int MSGID_FILELOCKER_LOCK_EXCLUSIVE_NOT_GRANTED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 321;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release an exclusive lock.  This takes two arguments, which
   * are the path to the lock file and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_FILELOCKER_UNLOCK_EXCLUSIVE_FAILED_RELEASE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 322;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a shared lock.  This takes two arguments, which are
   * the path to the lock file and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_FILELOCKER_UNLOCK_SHARED_FAILED_RELEASE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 323;



  /**
   * The message ID for the message that will be used if a request is received
   * to unlock a file for which no lock was believed to be held.  This takes a
   * single argument, which is the path to the specified lock file.
   */
  public static final int MSGID_FILELOCKER_UNLOCK_UNKNOWN_FILE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 324;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when an established connection is lost.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_SERVER_DOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 325;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when a client-side error occurs.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_LOCAL_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 326;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when an error occurs when attempting to encode a request.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_ENCODING_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 327;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when an error occurs when attempting to decode a
   * response.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_DECODING_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 328;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when an operation times out.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_TIMEOUT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 329;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when the client requests an unknown or unsupported
   * authentication mechanism.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_AUTH_UNKNOWN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 330;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when the user provided a malformed search filter.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_FILTER_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 331;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when the user cancelled an operation.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_USER_CANCELLED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 332;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when there is a problem with the provided parameter(s).
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_PARAM_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 333;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when the client runs out of memory.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_NO_MEMORY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 334;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when an error occurs while trying to establish a
   * connection.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_CONNECT_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 335;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when a requested operation is not supported.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_NOT_SUPPORTED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 336;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when an expected control is not found.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_CONTROL_NOT_FOUND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 337;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when no results were returned for an operation that
   * expected them.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_NO_RESULTS_RETURNED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 338;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when there are more results to be processed.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_MORE_RESULTS_TO_RETURN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 339;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when a client referral loop is detected.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_CLIENT_LOOP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 340;



  /**
   * The message ID for the string representation of the client-side result code
   * that will be used when a referral hop limit is reached.
   */
  public static final int MSGID_RESULT_CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 341;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a shared lock for a Directory Server backend during
   * the server shutdown process.  This takes two arguments, which are the
   * backend ID for the associated backend and a message that explains the
   * problem that occurred.
   */
  public static final int MSGID_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 342;



  /**
   * The message ID for the message that will be used if the Directory Server
   * cannot acquire an exclusive lock for the server process.  This takes two
   * arguments, which are the path to the lock file and a message explaining why
   * the lock could not be acquired.
   */
  public static final int MSGID_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 343;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release the exclusive server lock.  This takes two arguments,
   * which are the path to the lock file and a message explaining why the lock
   * could not be released.
   */
  public static final int MSGID_CANNOT_RELEASE_EXCLUSIVE_SERVER_LOCK =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 344;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to task processing.
   */
  public static final int MSGID_ERROR_CATEGORY_TASK =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 345;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because one of the targeted attributes was marked
   * no-user-modification.  This takes two arguments, which are the DN of the
   * target entry and the name of the attribute that cannot be modified.
   */
  public static final int MSGID_MODIFY_ATTR_IS_NO_USER_MOD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 346;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * added because one of the included attributes was marked
   * no-user-modification.  This takes two arguments, which are the DN of the
   * target entry and the name of the attribute that cannot be modified.
   */
  public static final int MSGID_ADD_ATTR_IS_NO_USER_MOD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 347;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * renamed because one of the attributes in the old RDN was marked
   * no-user-modification.  This takes two arguments, which are the DN of the
   * target entry and the name of the attribute that cannot be modified.
   */
  public static final int MSGID_MODDN_OLD_RDN_ATTR_IS_NO_USER_MOD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 348;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * renamed because one of the attributes in the old RDN was marked
   * no-user-modification.  This takes two arguments, which are the DN of the
   * target entry and the name of the attribute that cannot be modified.
   */
  public static final int MSGID_MODDN_NEW_RDN_ATTR_IS_NO_USER_MOD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 349;



  /**
   * The message ID for the message that will be used if an attempt to increment
   * an attribute by a pre-operation modify DN plugin fails because there is no
   * such attribute in the target entry.  This takes two arguments, which are
   * the DN of the entry and the name of the attribute that could not be
   * incremented.
   */
  public static final int MSGID_MODDN_PREOP_INCREMENT_NO_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 350;



  /**
   * The message ID for the message that will be used if an attempt to increment
   * an attribute by a pre-operation modify DN plugin fails because there are
   * multiple values for that attribute in the target entry.  This takes two
   * arguments, which are the DN of the entry and the name of the attribute that
   * could not be incremented.
   */
  public static final int MSGID_MODDN_PREOP_INCREMENT_MULTIPLE_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 351;



  /**
   * The message ID for the message that will be used if an attempt to increment
   * an attribute by a pre-operation modify DN plugin fails because the value of
   * the specified attribute is not an integer.  This takes two arguments, which
   * are the DN of the entry and the name of the attribute that could not be
   * incremented.
   */
  public static final int MSGID_MODDN_PREOP_INCREMENT_VALUE_NOT_INTEGER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 352;



  /**
   * The message ID for the message that will be used if an attempt to increment
   * an attribute by a pre-operation modify DN plugin fails because there is no
   * value for the increment amount.  This takes two arguments, which are the DN
   * of the entry and the name of the attribute that could not be incremented.
   */
  public static final int MSGID_MODDN_PREOP_INCREMENT_NO_AMOUNT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 353;



  /**
   * The message ID for the message that will be used if an attempt to increment
   * an attribute by a pre-operation modify DN plugin fails because there are
   * multiple increment amount values.  This takes two arguments, which are the
   * DN of the entry and the name of the attribute that could not be
   * incremented.
   */
  public static final int MSGID_MODDN_PREOP_INCREMENT_MULTIPLE_AMOUNTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 354;



  /**
   * The message ID for the message that will be used if an attempt to increment
   * an attribute by a pre-operation modify DN plugin fails because the provided
   * increment amount value is not an integer.  This takes two arguments, which
   * are the DN of the entry and the name of the attribute that could not be
   * incremented.
   */
  public static final int MSGID_MODDN_PREOP_INCREMENT_AMOUNT_NOT_INTEGER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 355;



  /**
   * The message ID for the message that will be used if an attempt to alter the
   * target entry during a pre-operation modify plugin would have caused that
   * entry to violate the server schema.  This takes two arguments, which are
   * the DN of the entry and a message explaining the violation that occurred.
   */
  public static final int MSGID_MODDN_PREOP_VIOLATES_SCHEMA =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 356;



  /**
   * The message ID for the message that will be used if a modify request
   * contains an LDAP assertion control and the associated filter does not match
   * the target entry.  This takes a single argument, which is the DN of the
   * target entry.
   */
  public static final int MSGID_MODIFY_ASSERTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 357;



  /**
   * The message ID for the message that will be used if a modify request
   * contains an LDAP assertion control but a problem occurred while trying to
   * compare the associated filter against the target entry.  This takes two
   * arguments, which are the DN of the target entry and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 358;



  /**
   * The message ID for the message that will be used if a modify request
   * contains a critical control that is not supported by the core server or the
   * associated backend.  This takes two arguments, which are the DN of the
   * target entry and the OID of the unsupported control.
   */
  public static final int MSGID_MODIFY_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 359;



  /**
   * The message ID for the message that will be used if a delete request
   * contains the LDAP assertion control but an error occurred while attempting
   * to retrieve the target entry for comparison.  This takes two arguments,
   * which are the DN of the target entry and a message explaining the problem
   * that occurred.
   */
  public static final int MSGID_DELETE_CANNOT_GET_ENTRY_FOR_ASSERTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 360;



  /**
   * The message ID for the message that will be used if a delete request
   * contains the LDAP assertion control but the target entry does not exist.
   * This takes a single argument, which is the DN of the target entry.
   */
  public static final int MSGID_DELETE_NO_SUCH_ENTRY_FOR_ASSERTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 361;



  /**
   * The message ID for the message that will be used if a delete request
   * contains an LDAP assertion control and the associated filter does not match
   * the target entry.  This takes a single argument, which is the DN of the
   * target entry.
   */
  public static final int MSGID_DELETE_ASSERTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 362;



  /**
   * The message ID for the message that will be used if a delete request
   * contains an LDAP assertion control but a problem occurred while trying to
   * compare the associated filter against the target entry.  This takes two
   * arguments, which are the DN of the target entry and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_DELETE_CANNOT_PROCESS_ASSERTION_FILTER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 363;



  /**
   * The message ID for the message that will be used if a delete request
   * contains a critical control that is not supported by the core server or the
   * associated backend.  This takes two arguments, which are the DN of the
   * target entry and the OID of the unsupported control.
   */
  public static final int MSGID_DELETE_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 364;



  /**
   * The message ID for the message that will be used if a modify DN request
   * contains an LDAP assertion control and the associated filter does not match
   * the target entry.  This takes a single argument, which is the DN of the
   * target entry.
   */
  public static final int MSGID_MODDN_ASSERTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 365;



  /**
   * The message ID for the message that will be used if a modify DN request
   * contains an LDAP assertion control but a problem occurred while trying to
   * compare the associated filter against the target entry.  This takes two
   * arguments, which are the DN of the target entry and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_MODDN_CANNOT_PROCESS_ASSERTION_FILTER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 366;



  /**
   * The message ID for the message that will be used if a modify DN request
   * contains a critical control that is not supported by the core server or the
   * associated backend.  This takes two arguments, which are the DN of the
   * target entry and the OID of the unsupported control.
   */
  public static final int MSGID_MODDN_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 367;



  /**
   * The message ID for the message that will be used if an add request contains
   * an LDAP assertion control and the associated filter does not match the
   * provided entry.  This takes a single argument, which is the DN of the
   * provided entry.
   */
  public static final int MSGID_ADD_ASSERTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 368;



  /**
   * The message ID for the message that will be used if an add request contains
   * an LDAP assertion control but a problem occurred while trying to compare
   * the associated filter against the provided entry.  This takes two
   * arguments, which are the DN of the provided entry and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_ADD_CANNOT_PROCESS_ASSERTION_FILTER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 369;



  /**
   * The message ID for the message that will be used if an add request contains
   * a critical control that is not supported by the core server or the
   * associated backend.  This takes two arguments, which are the DN of the
   * provided entry and the OID of the unsupported control.
   */
  public static final int MSGID_ADD_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 370;



  /**
   * The message ID for the message that will be used if a search request
   * contains the LDAP assertion control but an error occurred while attempting
   * to retrieve the base entry for comparison.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_SEARCH_CANNOT_GET_ENTRY_FOR_ASSERTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 371;



  /**
   * The message ID for the message that will be used if a search request
   * contains the LDAP assertion control but the base entry does not exist.
   * This does not take any arguments.
   */
  public static final int MSGID_SEARCH_NO_SUCH_ENTRY_FOR_ASSERTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 372;



  /**
   * The message ID for the message that will be used if a search request
   * contains an LDAP assertion control and the associated filter does not match
   * the target entry.  This does not take any arguments.
   */
  public static final int MSGID_SEARCH_ASSERTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 373;



  /**
   * The message ID for the message that will be used if a search request
   * contains an LDAP assertion control but a problem occurred while trying to
   * compare the associated filter against the base entry.  This takes a single
   * arguments, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 374;



  /**
   * The message ID for the message that will be used if a search request
   * contains a critical control that is not supported by the core server or the
   * associated backend.  This takes a single argument, which is the OID of the
   * unsupported control.
   */
  public static final int MSGID_SEARCH_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 375;



  /**
   * The message ID for the message that will be used if a compare request
   * contains an LDAP assertion control and the associated filter does not match
   * the target entry.  This takes a single argument, which is the DN of the
   * target entry.
   */
  public static final int MSGID_COMPARE_ASSERTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 376;



  /**
   * The message ID for the message that will be used if a compare request
   * contains an LDAP assertion control but a problem occurred while trying to
   * compare the associated filter against the target entry.  This takes two
   * arguments, which are the DN of the target entry and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_COMPARE_CANNOT_PROCESS_ASSERTION_FILTER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 377;



  /**
   * The message ID for the message that will be used if a compare request
   * contains a critical control that is not supported by the core server or the
   * associated backend.  This takes two arguments, which are the DN of the
   * target entry and the OID of the unsupported control.
   */
  public static final int MSGID_COMPARE_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 378;



  /**
   * The message ID for the message that will be used if an add operation was
   * not actually performed in the backend because the LDAP no-op control was
   * included in the request from the client.  This does not take any arguments.
   */
  public static final int MSGID_ADD_NOOP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 379;



  /**
   * The message ID for the message that will be used if a delete operation was
   * not actually performed in the backend because the LDAP no-op control was
   * included in the request from the client.  This does not take any arguments.
   */
  public static final int MSGID_DELETE_NOOP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 380;



  /**
   * The message ID for the message that will be used if an add operation was
   * not actually performed in the backend because the LDAP no-op control was
   * included in the request from the client.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_NOOP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 381;



  /**
   * The message ID for the message that will be used if an add operation was
   * not actually performed in the backend because the LDAP no-op control was
   * included in the request from the client.  This does not take any arguments.
   */
  public static final int MSGID_MODDN_NOOP =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 382;



  /**
   * The message ID for the message that will be used if a delete request
   * contains an LDAP pre-read request control but the target entry does not
   * exist.  This takes a single argument, which is the DN of the target entry.
   */
  public static final int MSGID_DELETE_PREREAD_NO_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 383;



  /**
   * The message ID for the string representation of the result code that will
   * be used if a proxied authorization request is refused.  This does not take
   * any arguments.
   */
  public static final int MSGID_RESULT_AUTHORIZATION_DENIED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 384;



  /**
   * The message ID for the message that will be used if the entry being added
   * is missing one or more of the RDN attributes.  This takes two arguments,
   * which are the DN of the entry to add and the name of the missing RDN
   * attribute.
   */
  public static final int MSGID_ADD_MISSING_RDN_ATTRIBUTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 385;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a change listener.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_ADD_ERROR_NOTIFYING_CHANGE_LISTENER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 386;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a persistent search.  This takes two arguments, which are a
   * string representation of the persistent search and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_ADD_ERROR_NOTIFYING_PERSISTENT_SEARCH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 387;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a change listener.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_DELETE_ERROR_NOTIFYING_CHANGE_LISTENER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 388;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a persistent search.  This takes two arguments, which are a
   * string representation of the persistent search and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_DELETE_ERROR_NOTIFYING_PERSISTENT_SEARCH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 389;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a change listener.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_MODIFY_ERROR_NOTIFYING_CHANGE_LISTENER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 390;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a persistent search.  This takes two arguments, which are a
   * string representation of the persistent search and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_MODIFY_ERROR_NOTIFYING_PERSISTENT_SEARCH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 391;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a change listener.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_MODDN_ERROR_NOTIFYING_CHANGE_LISTENER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 392;



  /**
   * The message ID for the message that will be used if an error occurs while
   * notifying a persistent search.  This takes two arguments, which are a
   * string representation of the persistent search and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_MODDN_ERROR_NOTIFYING_PERSISTENT_SEARCH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 393;



  /**
   * The message ID for the message that will be used if a bind request contains
   * a critical control that is not supported.  This takes a single argument,
   * which is the OID of the control.
   */
  public static final int MSGID_BIND_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 394;



  /**
   * The message ID for the message that will be used if a user entry contains
   * multiple values for the user-specific size limit attribute.  This takes a
   * single argument, which is the DN of the user entry.
   */
  public static final int MSGID_BIND_MULTIPLE_USER_SIZE_LIMITS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 395;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a user-specific size limit value as an integer.  This takes
   * two arguments, which are the provided size limit value and the DN of the
   * user entry.
   */
  public static final int MSGID_BIND_CANNOT_PROCESS_USER_SIZE_LIMIT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 396;



  /**
   * The message ID for the message that will be used if a user entry contains
   * multiple values for the user-specific time limit attribute.  This takes a
   * single argument, which is the DN of the user entry.
   */
  public static final int MSGID_BIND_MULTIPLE_USER_TIME_LIMITS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 397;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a user-specific time limit value as an integer.  This takes
   * two arguments, which are the provided time limit value and the DN of the
   * user entry.
   */
  public static final int MSGID_BIND_CANNOT_PROCESS_USER_TIME_LIMIT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 398;



  /**
   * The message ID for the string representation of the result code that will
   * be used for cases in which a request is rejected because the associated
   * assertion failed to match the target entry.
   */
  public static final int MSGID_RESULT_ASSERTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 399;



  /**
   * The message ID for the message that will be used if the target entry of an
   * add operation already exists.  This takes a single argument, which is the
   * DN of the entry to add.
   */
  public static final int MSGID_ADD_ENTRY_ALREADY_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 400;



  /**
   * The message ID for the message that will be used if an error occurs during
   * preoperation synchronization processing for an add operation.  This takes
   * three arguments, which are the connection ID, operation ID, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_ADD_SYNCH_PREOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 401;



  /**
   * The message ID for the message that will be used if an error occurs during
   * postoperation synchronization processing for an add operation.  This takes
   * three arguments, which are the connection ID, operation ID, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_ADD_SYNCH_POSTOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 402;



  /**
   * The message ID for the message that will be used if an error occurs during
   * preoperation synchronization processing for a delete operation.  This takes
   * three arguments, which are the connection ID, operation ID, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_DELETE_SYNCH_PREOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 403;



  /**
   * The message ID for the message that will be used if an error occurs during
   * postoperation synchronization processing for a delete operation.  This
   * takes three arguments, which are the connection ID, operation ID, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_DELETE_SYNCH_POSTOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 404;



  /**
   * The message ID for the message that will be used if an error occurs during
   * preoperation synchronization processing for a modify operation.  This takes
   * three arguments, which are the connection ID, operation ID, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_MODIFY_SYNCH_PREOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 405;



  /**
   * The message ID for the message that will be used if an error occurs during
   * postoperation synchronization processing for a modify operation.  This
   * takes three arguments, which are the connection ID, operation ID, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_MODIFY_SYNCH_POSTOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 406;



  /**
   * The message ID for the message that will be used if an error occurs during
   * preoperation synchronization processing for a modify DN operation.  This
   * takes three arguments, which are the connection ID, operation ID, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_MODDN_SYNCH_PREOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 407;



  /**
   * The message ID for the message that will be used if an error occurs during
   * postoperation synchronization processing for a modify DN operation.  This
   * takes three arguments, which are the connection ID, operation ID, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_MODDN_SYNCH_POSTOP_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 408;



  /**
   * The message ID for the message that will be used if an error occurs during
   * conflict resolution synchronization processing for an add operation.  This
   * takes three arguments, which are the connection ID, operation ID, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 409;



  /**
   * The message ID for the message that will be used if an error occurs during
   * conflict resolution synchronization processing for a delete operation.
   * This takes three arguments, which are the connection ID, operation ID, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_DELETE_SYNCH_CONFLICT_RESOLUTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 410;



  /**
   * The message ID for the message that will be used if an error occurs during
   * conflict resolution synchronization processing for a modify operation.
   * This takes three arguments, which are the connection ID, operation ID, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_MODIFY_SYNCH_CONFLICT_RESOLUTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 411;



  /**
   * The message ID for the message that will be used if an error occurs during
   * conflict resolution synchronization processing for a modify DN operation.
   * This takes three arguments, which are the connection ID, operation ID, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_MODDN_SYNCH_CONFLICT_RESOLUTION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 412;



  /**
   * The message ID for the message that will be used if an add operation is
   * refused because the server is read-only.  This takes a single argument,
   * which is the DN of the entry to add.
   */
  public static final int MSGID_ADD_SERVER_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 413;



  /**
   * The message ID for the message that will be used if an add operation is
   * refused because the backend is read-only.  This takes a single argument,
   * which is the DN of the entry to add.
   */
  public static final int MSGID_ADD_BACKEND_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 414;



  /**
   * The message ID for the message that will be used if a delete operation is
   * refused because the server is read-only.  This takes a single argument,
   * which is the DN of the entry to delete.
   */
  public static final int MSGID_DELETE_SERVER_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 415;



  /**
   * The message ID for the message that will be used if a delete operation is
   * refused because the backend is read-only.  This takes a single argument,
   * which is the DN of the entry to delete.
   */
  public static final int MSGID_DELETE_BACKEND_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 416;



  /**
   * The message ID for the message that will be used if a modify operation is
   * refused because the server is read-only.  This takes a single argument,
   * which is the DN of the entry to modify.
   */
  public static final int MSGID_MODIFY_SERVER_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 417;



  /**
   * The message ID for the message that will be used if a modify operation is
   * refused because the backend is read-only.  This takes a single argument,
   * which is the DN of the entry to modify.
   */
  public static final int MSGID_MODIFY_BACKEND_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 418;



  /**
   * The message ID for the message that will be used if a modify DN operation
   * is refused because the server is read-only.  This takes a single argument,
   * which is the DN of the entry to rename.
   */
  public static final int MSGID_MODDN_SERVER_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 419;



  /**
   * The message ID for the message that will be used if a modify DN operation
   * is refused because the backend is read-only.  This takes a single argument,
   * which is the DN of the entry to rename.
   */
  public static final int MSGID_MODDN_BACKEND_READONLY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 420;



  /**
   * The message ID for the message that will be used if a simple bind request
   * contains a DN but does not contain a password.  This does not take any
   * arguments.
   */
  public static final int MSGID_BIND_DN_BUT_NO_PASSWORD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 421;



  /**
   * The message ID for the message that will be used if the default password
   * policy entry does not contain a password attribute.  This takes a single
   * argument, which is the DN of the password policy configuration entry.
   */
  public static final int MSGID_PWPOLICY_NO_PASSWORD_ATTRIBUTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 422;



  /**
   * The message ID for the message that will be used if the default password
   * policy entry does not contain any default storage schemes.  This takes a
   * single argument, which is the DN of the password policy configuration
   * entry.
   */
  public static final int MSGID_PWPOLICY_NO_DEFAULT_STORAGE_SCHEMES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 423;



  /**
   * The message ID for the message that will be used as the description for the
   * password configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_PW_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 424;



  /**
   * The message ID for the message that will be used if the password attribute
   * is not defined in the server schema.  This takes two arguments, which are
   * the DN of the password policy configuration entry and the name of the
   * undefined attribute.
   */
  public static final int MSGID_PWPOLICY_UNDEFINED_PASSWORD_ATTRIBUTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 425;



  /**
   * The message ID for the message that will be used if the password attribute
   * has an invalid syntax.  This takes three arguments, which are the DN of
   * the password policy configuration entry, the name of the password policy
   * attribute, and the syntax OID for that attribute.
   */
  public static final int MSGID_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 426;



  /**
   * The message ID for the message that will be used if the an error occurs
   * while trying to determine the password attribute.  This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_ATTRIBUTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 427;



  /**
   * The message ID for the message that will be used as the description for the
   * default storage schemes configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_DEFAULT_STORAGE_SCHEMES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 428;



  /**
   * The message ID for the message that will be used if a requested default
   * storage scheme is not defined in the server.  This takes two arguments,
   * which are the DN of the password policy configuration entry and the name
   * of the requested storage scheme.
   */
  public static final int MSGID_PWPOLICY_NO_SUCH_DEFAULT_SCHEME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 429;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the default storage schemes.  This takes two arguments,
   * which are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_DEFAULT_STORAGE_SCHEMES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 430;



  /**
   * The message ID for the message that will be used as the description for the
   * deprecated storage schemes configuration attribute.  This does not take any
   * arguments.
   */
  public static final int
       MSGID_PWPOLICY_DESCRIPTION_DEPRECATED_STORAGE_SCHEMES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 431;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the deprecated storage schemes.  This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_DEPRECATED_STORAGE_SCHEMES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 432;



  /**
   * The message ID for the message that will be used as the description for the
   * password validators configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_PASSWORD_VALIDATORS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 433;



  /**
   * The message ID for the message that will be used if a requested password
   * validator is not defined in the server.  This takes two arguments, which
   * are the DN of the password policy configuration entry and the DN of the
   * requested password validator.
   */
  public static final int MSGID_PWPOLICY_NO_SUCH_VALIDATOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 434;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of password validators.  This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_VALIDATORS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 435;



  /**
   * The message ID for the message that will be used as the description for the
   * notification handlers configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_NOTIFICATION_HANDLERS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 436;



  /**
   * The message ID for the message that will be used if a requested account
   * status notification handler is not defined in the server.  This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * the DN of the requested notification handler.
   */
  public static final int MSGID_PWPOLICY_NO_SUCH_NOTIFICATION_HANDLER =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 437;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of account status notification handlers.  This
   * takes two arguments, which are the DN of the password policy configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_NOTIFICATION_HANDLERS =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 438;



  /**
   * The message ID for the message that will be used as the description for the
   * allow user password changes configuration attribute.  This does not take
   * any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_ALLOW_USER_PW_CHANGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 439;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to allow user password changes.  This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_USER_PW_CHANGES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 440;



  /**
   * The message ID for the message that will be used as the description for the
   * require current password configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CURRENT_PW =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 441;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to require the current password for password
   * changes.  This takes two arguments, which are the DN of the password policy
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CURRENT_PW =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 442;



  /**
   * The message ID for the message that will be used as the description for the
   * force change on reset configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_RESET =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 443;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to force a password change on administrative
   * reset.  This takes two arguments, which are the DN of the password policy
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_FORCE_CHANGE_ON_RESET =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 444;



  /**
   * The message ID for the message that will be used as the description for the
   * skip validation for administrators configuration attribute.  This does not
   * take any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_SKIP_ADMIN_VALIDATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 445;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to skip admin validation.  This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_SKIP_ADMIN_VALIDATION =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 446;



  /**
   * The message ID for the message that will be used as the description for the
   * password generator configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_PASSWORD_GENERATOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 447;



  /**
   * The message ID for the message that will be used if a requested password
   * generator does not exist.  This takes two arguments, which are the DN of
   * the password policy configuration entry and the DN of the requested
   * password generator configuration entry.
   */
  public static final int MSGID_PWPOLICY_NO_SUCH_GENERATOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 448;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the password generator. This takes two arguments, which
   * are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_GENERATOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 449;



  /**
   * The message ID for the message that will be used as the description for the
   * require secure authentication configuration attribute.  This does not take
   * any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_AUTH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 450;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to require secure authentication. This takes
   * two arguments, which are the DN of the password policy configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_SECURE_AUTH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 451;



  /**
   * The message ID for the message that will be used as the description for the
   * require secure password changes configuration attribute.  This does not
   * take any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_CHANGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 452;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to require secure password changes. This takes
   * two arguments, which are the DN of the password policy configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_SECURE_CHANGES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 453;



  /**
   * The message ID for the message that will be used as the description for the
   * allow pre-encoded passwords configuration attribute.  This does not take
   * any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_ALLOW_PREENCODED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 454;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to allow pre-encoded passwords. This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_PREENCODED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 455;



  /**
   * The message ID for the message that will be used as the description for the
   * minimum password age configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_MIN_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 456;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the minimum password age. This takes two arguments,
   * which are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_MIN_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 457;



  /**
   * The message ID for the message that will be used as the description for the
   * maximum password age configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_MAX_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 458;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the maximum password age. This takes two arguments,
   * which are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_MAX_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 459;



  /**
   * The message ID for the message that will be used as the description for the
   * maximum password reset age configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_MAX_RESET_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 460;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the maximum password reset age. This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_MAX_RESET_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 461;



  /**
   * The message ID for the message that will be used as the description for the
   * expiration warning interval configuration attribute.  This does not take
   * any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_WARNING_INTERVAL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 462;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the expiration warning interval. This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_WARNING_INTERVAL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 463;



  /**
   * The message ID for the message that will be used as the description for the
   * expire without warning configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_EXPIRE_WITHOUT_WARNING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 464;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the expire without warning setting. This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_EXPIRE_WITHOUT_WARNING =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 465;



  /**
   * The message ID for the message that will be used as the description for the
   * allow expired password changes configuration attribute.  This does not take
   * any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_ALLOW_EXPIRED_CHANGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 466;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the allow expired password changes setting. This takes
   * two arguments, which are the DN of the password policy configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_EXPIRED_CHANGES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 467;



  /**
   * The message ID for the message that will be used as the description for the
   * grace login count configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_GRACE_LOGIN_COUNT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 468;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the grace login count. This takes two arguments, which
   * are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_GRACE_LOGIN_COUNT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 469;



  /**
   * The message ID for the message that will be used as the description for the
   * lockout failure count configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_FAILURE_COUNT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 470;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the lockout failure count. This takes two arguments,
   * which are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_LOCKOUT_FAILURE_COUNT =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 471;



  /**
   * The message ID for the message that will be used as the description for the
   * lockout duration configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_DURATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 472;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the lockout duration. This takes two arguments, which
   * are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_LOCKOUT_DURATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 473;



  /**
   * The message ID for the message that will be used as the description for the
   * lockout failure expiration interval configuration attribute.  This does not
   * take any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_FAILURE_EXPIRATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 474;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the lockout failure expiration interval. This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_FAILURE_EXPIRATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 475;



  /**
   * The message ID for the message that will be used as the description for the
   * require change time configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CHANGE_BY_TIME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 476;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the require change time. This takes two arguments,
   * which are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CHANGE_BY_TIME =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 477;



  /**
   * The message ID for the message that will be used as the description for the
   * last login time attribute configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 478;



  /**
   * The message ID for the message that will be used if the last login time
   * attribute is not defined in the server schema.  This takes two arguments,
   * which are the DN of the password policy configuration entry and the name
   * of the requested attribute type.
   */
  public static final int MSGID_PWPOLICY_UNDEFINED_LAST_LOGIN_TIME_ATTRIBUTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 479;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the last login time attribute. This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_LAST_LOGIN_TIME_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 480;



  /**
   * The message ID for the message that will be used as the description for the
   * last login time format configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_FORMAT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 481;



  /**
   * The message ID for the message that will be used if the last login time
   * format string is invalid.  This takes two arguments, which are the DN of
   * the password policy configuration entry and the invalid format string.
   */
  public static final int MSGID_PWPOLICY_INVALID_LAST_LOGIN_TIME_FORMAT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 482;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the last login time format. This takes two arguments,
   * which are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_LAST_LOGIN_TIME_FORMAT =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 483;



  /**
   * The message ID for the message that will be used as the description for the
   * previous last login time format configuration attribute.  This does not
   * take any arguments.
   */
  public static final int
       MSGID_PWPOLICY_DESCRIPTION_PREVIOUS_LAST_LOGIN_TIME_FORMAT =
            CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 484;



  /**
   * The message ID for the message that will be used if a previous last login
   * time format string is invalid.  This takes two arguments, which are the DN
   * of the password policy configuration entry and the invalid format string.
   */
  public static final int
       MSGID_PWPOLICY_INVALID_PREVIOUS_LAST_LOGIN_TIME_FORMAT =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 485;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the previous last login time formats. This takes two
   * arguments, which are the DN of the password policy configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_PREVIOUS_LAST_LOGIN_TIME_FORMAT =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 486;



  /**
   * The message ID for the message that will be used as the description for the
   * idle lockout duration configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_IDLE_LOCKOUT_INTERVAL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 487;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the idle lockout duration. This takes two arguments,
   * which are the DN of the password policy configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_IDLE_LOCKOUT_INTERVAL =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 488;



  /**
   * The message ID for the message that will be used to indicate that the
   * password policy has been updated.  This takes a single argument, which is
   * the DN of the password policy configuration entry.
   */
  public static final int MSGID_PWPOLICY_UPDATED_POLICY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 489;



  /**
   * The message ID for the message that will be used if a password policy
   * subentry DN specified in an entry is invalid.  This does not take any
   * arguments.
   */
  public static final int MSGID_ADD_INVALID_PWPOLICY_DN_SYNTAX =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 490;



  /**
   * The message ID for the message that will be used if an entry references a
   * password policy that doesn't exist.  This takes a single argument, which is
   * the DN of the password policy subentry.
   */
  public static final int MSGID_ADD_NO_SUCH_PWPOLICY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 491;



  /**
   * The message ID for the message that will be used as the description for the
   * force change on add configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_ADD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 492;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to force a password change on first
   * authentication.  This takes two arguments, which are the DN of the password
   * policy configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_PWPOLICY_CANNOT_DETERMINE_FORCE_CHANGE_ON_ADD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 493;



  /**
   * The message ID for the message that will be used as the description for the
   * allow multiple password values configuration attribute.  This does not take
   * any arguments.
   */
  public static final int MSGID_PWPOLICY_DESCRIPTION_ALLOW_MULTIPLE_PW_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 494;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether a user entry may have multiple password values.
   * This takes two arguments, which are the DN of the password policy
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_MULTIPLE_PW_VALUES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 495;



  /**
   * The message ID for the message that will be used if attempt is made to
   * use attribute options with the password attribute.  This takes a single
   * argument, which is the name of the password attribute.
   */
  public static final int MSGID_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 496;



  /**
   * The message ID for the message that will be used if attempt is made to use
   * multiple password values.  This takes a single argument, which is the name
   * of the password attribute.
   */
  public static final int MSGID_PWPOLICY_MULTIPLE_PW_VALUES_NOT_ALLOWED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 497;



  /**
   * The message ID for the message that will be used if attempt is made to use
   * a pre-encoded password value.  This takes a single argument, which is the
   * name of the password attribute.
   */
  public static final int MSGID_PWPOLICY_PREENCODED_NOT_ALLOWED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 498;



  /**
   * The message ID for the message that will be used if one of the password
   * validators rejected the provided password.  This takes two arguments, which
   * are the name of the password attribute and a message explaining the
   * failure.
   */
  public static final int MSGID_PWPOLICY_VALIDATION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 499;



  /**
   * The message ID for the message that will be used if expire without warning
   * is disabled but there is no warning interval.  This takes a single
   * argument, which is the DN of the password policy configuration entry.
   */
  public static final int
       MSGID_PWPOLICY_MUST_HAVE_WARNING_IF_NOT_EXPIRE_WITHOUT_WARNING =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 500;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * request an operation on a client connection while a bind is in progress for
   * that connection.  This does not take any arguments.
   */
  public static final int MSGID_ENQUEUE_BIND_IN_PROGRESS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 501;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * request an operation other than a password change when the user's account
   * is in a forced change mode.  This does not take any arguments.
   */
  public static final int MSGID_ENQUEUE_MUST_CHANGE_PASSWORD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 502;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a password policy subentry value as a DN.  This takes
   * three arguments, which are the password policy subentry value, the DN of
   * the user's entry, and a message explaining the problem that occurred.
   */
  public static final int MSGID_PWPSTATE_CANNOT_DECODE_SUBENTRY_VALUE_AS_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 504;



  /**
   * The message ID for the message that will be used if a requested password
   * policy is not defined in the Directory Server.  This takes two arguments,
   * which are the user DN and the DN of the password policy subentry.
   */
  public static final int MSGID_PWPSTATE_NO_SUCH_POLICY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 505;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode an attribute value according to the generalized time
   * syntax.  This takes four arguments, which are the provided value, the
   * name of the attribute, the DN of the user entry, and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_PWPSTATE_CANNOT_DECODE_GENERALIZED_TIME =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 506;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode an attribute value as a Boolean.  This takes three
   * arguments, which are the provided value, the name of the attribute, and
   * the DN of the user entry.
   */
  public static final int MSGID_PWPSTATE_CANNOT_DECODE_BOOLEAN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 507;



  /**
   * The message ID for the message that will be used if an add
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the name of the entry that
   * could not be added.
   */
  public static final int MSGID_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 508;

  /**
   * The message ID for the message that will be used if a bind
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the name of the entry/user
   * attempting to bind.
   */
  public static final int MSGID_BIND_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 509;

  /**
   * The message ID for the message that will be used if a compare
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the name of the entry that
   * could not be compared.
   */
  public static final int MSGID_COMPARE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 510;

  /**
   * The message ID for the message that will be used if a delete
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the name of the entry that
   * could not be deleted.
   */
  public static final int MSGID_DELETE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 511;

  /**
   * The message ID for the message that will be used if an extended
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the OID of the extended
   * operation.
   */
  public static final int MSGID_EXTENDED_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 512;

  /**
   * The message ID for the message that will be used if a modify DN
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the name of the entry that
   * could not be renamed.
   */
  public static final int MSGID_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 513;

  /**
   * The message ID for the message that will be used if a modify
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the name of the entry that
   * could not be modified.
   */
  public static final int MSGID_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 514;

  /**
   * The message ID for the message that will be used if a search
   * operation cannot be processed due to insufficient access rights.
   * This message takes a single argument, the name of the search
   * operation's base entry.
   */
  public static final int MSGID_SEARCH_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 515;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using an insecure simple bind in violation of the password
   * policy.  This takes a single argument, which is the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_INSECURE_SIMPLE_BIND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 516;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using a disabled account.  This takes a single argument, which
   * is the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_ACCOUNT_DISABLED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 517;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using a failure locked account.  This takes a single argument,
   * which is the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_ACCOUNT_FAILURE_LOCKED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 518;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using a reset locked account.  This takes a single argument,
   * which is the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_ACCOUNT_RESET_LOCKED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 519;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using an idle locked account.  This takes a single argument,
   * which is the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_ACCOUNT_IDLE_LOCKED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 520;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using an expired password.  This takes a single argument,
   * which is the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_PASSWORD_EXPIRED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 521;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to perform an internal modification to update the user's entry
   * with password policy state information.  This takes two arguments, which
   * are the DN of the user and the error message from the modify operation.
   */
  public static final int MSGID_PWPSTATE_CANNOT_UPDATE_USER_ENTRY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 522;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using an insecure simple bind in violation of the password
   * policy.  This takes two arguments, which are the name of the SASL mechanism
   * and the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_INSECURE_SASL_BIND =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 523;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to parse the DN for the work queue configuration entry.  This
   * takes two arguments, which are the DN and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_WORKQ_CANNOT_PARSE_DN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 524;



  /**
   * The message ID for the message that will be used if the work queue
   * configuration entry does not exist.  This takes a single argument, which is
   * the DN of the entry.
   */
  public static final int MSGID_WORKQ_NO_CONFIG =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 525;



  /**
   * The message ID for the message that will be used as the description of the
   * class configuration attribute.  It does not take any arguments.
   */
  public static final int MSGID_WORKQ_DESCRIPTION_CLASS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 526;



  /**
   * The message ID for the message that will be used if the work queue
   * configuration entry does not specify the class name.  This takes two
   * arguments, which are the DN of the configuration entry and the name of the
   * missing attribute.
   */
  public static final int MSGID_WORKQ_NO_CLASS_ATTR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 527;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the work queue class.  This takes two arguments, which are
   * the name of the class and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_WORKQ_CANNOT_LOAD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 528;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate the work queue.  This takes two arguments, which are
   * the name of the class and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_WORKQ_CANNOT_INSTANTIATE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 529;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register an alternate root bind DN with a DN that is already in use.  This
   * takes two arguments, which are the alternate bind DN and the real DN of the
   * user to which the mapping is established.
   */
  public static final int
       MSGID_CANNOT_REGISTER_DUPLICATE_ALTERNATE_ROOT_BIND_DN =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 530;



  /**
   * The message ID for the message that will be used if a user tries to
   * authenticate using an expired account.  This takes a single argument, which
   * is the DN of the user.
   */
  public static final int MSGID_BIND_OPERATION_ACCOUNT_EXPIRED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 531;



  /**
   * The message ID for the message that will be used if a change to the
   * password attribute included one or more attribute options.  This does not
   * take any arguments.
   */
  public static final int MSGID_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 532;



  /**
   * The message ID for the message that will be used if a user password change
   * is refused because users cannot change their own passwords.  This does not
   * take any arguments.
   */
  public static final int MSGID_MODIFY_NO_USER_PW_CHANGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 533;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because it was not attempted over a secure channel.  This does not
   * take any arguments.
   */
  public static final int MSGID_MODIFY_REQUIRE_SECURE_CHANGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 534;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because the password was within the minimum age.  This does not
   * take any arguments.
   */
  public static final int MSGID_MODIFY_WITHIN_MINIMUM_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 535;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because multiple password values were provided.  This does not
   * take any arguments.
   */
  public static final int MSGID_MODIFY_MULTIPLE_VALUES_NOT_ALLOWED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 536;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because the password was pre-encoded.  This does not take any
   * arguments.
   */
  public static final int MSGID_MODIFY_NO_PREENCODED_PASSWORDS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 537;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because it included an invalid modification type on the password
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_INVALID_MOD_TYPE_FOR_PASSWORD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 538;



  /**
   * The message ID for the message that will be used if an attempt to delete a
   * user password value is rejected because there are no existing passwords in
   * the user's entry.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_NO_EXISTING_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 539;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a user password.  This takes a single argument, which
   * is a message explaining the problem that occurred.
   */
  public static final int MSGID_MODIFY_CANNOT_DECODE_PW =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 540;



  /**
   * The message ID for the message that will be used if a provided password to
   * delete does not match any passwords in the user's entry.  This does not
   * take any arguments.
   */
  public static final int MSGID_MODIFY_INVALID_PASSWORD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 541;



  /**
   * The message ID for the message that will be used if the user did not
   * provide the current password.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_PW_CHANGE_REQUIRES_CURRENT_PW =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 542;



  /**
   * The message ID for the message that will be used if the password change
   * would result in multiple passwords.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_MULTIPLE_PASSWORDS_NOT_ALLOWED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 543;



  /**
   * The message ID for the message that will be used if password validation
   * fails.  This takes a single argument, which is a message explaining the
   * rejection.
   */
  public static final int MSGID_MODIFY_PW_VALIDATION_FAILED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 544;



  /**
   * The message ID for the message that will be used if the user's password
   * needs to be changed but the modification doesn't update the password.  This
   * does not take any arguments.
   */
  public static final int MSGID_MODIFY_MUST_CHANGE_PASSWORD =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 545;



  /**
   * The message ID for the string that will be used for the error log category
   * for messages related to password policy processing.
   */
  public static final int MSGID_ERROR_CATEGORY_PASSWORD_POLICY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 546;



  /**
   * The message ID for the message that will be used if the user's password is
   * about to expire.  This takes a single argument, which is the length of time
   * until the password expires.
   */
  public static final int MSGID_BIND_PASSWORD_EXPIRING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_WARNING | 547;



  /**
   * The message ID for the message that will be used if the user's account
   * becomes temporarily locked due to too many failed attempts.  This takes a
   * single argument, which is a string representation of the length of time
   * until the account is unlocked.
   */
  public static final int MSGID_BIND_ACCOUNT_TEMPORARILY_LOCKED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 548;



  /**
   * The message ID for the message that will be used if the user's account
   * becomes permanently locked due to too many failed attempts.  This does not
   * take any arguments.
   */
  public static final int MSGID_BIND_ACCOUNT_PERMANENTLY_LOCKED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 549;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * modify the account disabled attribute using an invalid value.  This takes
   * two arguments, which are the name of the attribute and a message explaining
   * the reason the value was invalid.
   */
  public static final int MSGID_MODIFY_INVALID_DISABLED_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 550;



  /**
   * The message ID for the message that will be used in the account status
   * notification indicating that a user's password has been changed.  This does
   * not take any arguments.
   */
  public static final int MSGID_MODIFY_PASSWORD_CHANGED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 551;



  /**
   * The message ID for the message that will be used in the account status
   * notification indicating that a user's password has been reset by an
   * administrator.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_PASSWORD_RESET =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 552;



  /**
   * The message ID for the message that will be used in the account status
   * notification indicating that a user's account has been enabled.  This does
   * not take any arguments.
   */
  public static final int MSGID_MODIFY_ACCOUNT_ENABLED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 553;



  /**
   * The message ID for the message that will be used in the account status
   * notification indicating that a user's account has been disabled.  This does
   * not take any arguments.
   */
  public static final int MSGID_MODIFY_ACCOUNT_DISABLED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 554;



  /**
   * The message ID for the message that will be used in the account status
   * notification indicating that a user's account has been unlocked.  This does
   * not take any arguments.
   */
  public static final int MSGID_MODIFY_ACCOUNT_UNLOCKED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 555;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a password that already exists.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_PASSWORD_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 556;


   /**
   * The message ID for the message that will be used if a user entry contains
   * multiple values for the user-specific lookthrough limit attribute.
    * This takes a single argument, which is the DN of the user entry.
   */
  public static final int MSGID_BIND_MULTIPLE_USER_LOOKTHROUGH_LIMITS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 557;


  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a user-specific lookthrough value as an integer. This takes
   * two arguments, which are the provided lookthrough limit value and the DN
   * of the user entry.
   */
  public static final int MSGID_BIND_CANNOT_PROCESS_USER_LOOKTHROUGH_LIMIT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 558;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an attribute with one or more values that conflict with existing
   * values.  This takes a single argument, which is the name of the attribute.
   */
  public static final int MSGID_ENTRY_DUPLICATE_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 559;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove an attribute value that doesn't exist in the entry.  This takes a
   * single argument, which is the name of the attribute.
   */
  public static final int MSGID_ENTRY_NO_SUCH_VALUE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 560;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an increment on the objectClass attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_ENTRY_OC_INCREMENT_NOT_SUPPORTED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 561;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a modify with an unknown modification type.  This does not take any
   * arguments.
   */
  public static final int MSGID_ENTRY_UNKNOWN_MODIFICATION_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 562;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * increment an attribute that has multiple values.  This takes a single
   * argument, which is the name of the attribute.
   */
  public static final int MSGID_ENTRY_INCREMENT_MULTIPLE_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 563;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an increment but there was not exactly one value in the provided
   * modification. This takes a single argument, which is the name of the
   * attribute.
   */
  public static final int MSGID_ENTRY_INCREMENT_INVALID_VALUE_COUNT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 564;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an increment but either the current value or the increment cannot
   * be parsed as an integer.  This takes a single argument, which is the name
   * of the attribute.
   */
  public static final int MSGID_ENTRY_INCREMENT_CANNOT_PARSE_AS_INT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 565;



  /**
   * The message ID for the message that will be used if a modify operation does
   * not contain any modifications.  This takes a single argument, which is the
   * DN of the entry to modify.
   */
  public static final int MSGID_MODIFY_NO_MODIFICATIONS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 566;



  /**
   * The message ID for the message that will be used as the description for the
   * noDetach command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_NODETACH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 567;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * increment an attribute that does not exist.  This takes a single argument,
   * which is the name of the attribute.
   */
  public static final int MSGID_ENTRY_INCREMENT_NO_SUCH_ATTRIBUTE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 568;



  /**
   * The message ID for the message that will be used as the description for the
   * start-ds tool.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_TOOL_DESCRIPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 569;



  /**
   * The message ID for the message that will be used if an extended operation
   * cannot be processed because it contains an unsupported critical control.
   * This takes two arguments, which are the OID of the extended request and the
   * OID of the unsupported control.
   */
  public static final int MSGID_EXTENDED_UNSUPPORTED_CRITICAL_CONTROL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 570;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a backend with an ID that is already registered.  This takes a
   * single argument, which is the conflicting backend ID.
   */
  public static final int MSGID_REGISTER_BACKEND_ALREADY_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 571;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a base DN that is already registered.  This takes three arguments,
   * which are the base DN, the backend ID for the new backend, and the backend
   * ID for the already-registered backend.
   */
  public static final int MSGID_REGISTER_BASEDN_ALREADY_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 572;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a base DN with a backend that already has another base DN that
   * contains conflicting hierarchy.  This takes three arguments, which are the
   * base DN to be registered, the backend ID, and the already-registered base
   * DN.
   */
  public static final int MSGID_REGISTER_BASEDN_HIERARCHY_CONFLICT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 573;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a base DN with a backend that already contains other base DNs
   * that do not share the same parent base DN.  This takes three arguments,
   * which are the base DN to be registered, the backend ID, and the
   * already-registered base DN.
   */
  public static final int MSGID_REGISTER_BASEDN_DIFFERENT_PARENT_BASES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 574;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a base DN with a backend that already contains other base DNs
   * that are subordinate to a backend whereas the new base DN is not
   * subordinate to any backend.  This takes three arguments, which are the base
   * DN to be registered, the backend ID, and the backend ID of the parent
   * backend.
   */
  public static final int MSGID_REGISTER_BASEDN_NEW_BASE_NOT_SUBORDINATE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 575;



  /**
   * The message ID for the message that will be used if a newly-registered
   * backend has a base DN for which a corresponding entry already exists in the
   * server.  This takes three arguments, which are the backend ID for the
   * existing backend, the base DN for the new backend, and the backend ID for
   * the new backend.
   */
  public static final int MSGID_REGISTER_BASEDN_ENTRIES_IN_MULTIPLE_BACKENDS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 576;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * deregister a base DN that is not registered.  This takes a single argument,
   * which is the base DN to deregister.
   */
  public static final int MSGID_DEREGISTER_BASEDN_NOT_REGISTERED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 577;



  /**
   * The message ID for the message that will be used if a base DN containing
   * both superior and subordinate backends is deregistered, leaving the
   * possibility for missing entries in the data hierarchy.  This takes two
   * arguments, which are the base DN that has been deregistered and the backend
   * ID of the associated backend.
   */
  public static final int MSGID_DEREGISTER_BASEDN_MISSING_HIERARCHY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 578;


  /**
   * The message ID for the message that will be used if a circular reference is
   * detected when attempting to rebuild schema element dependencies.  This
   * takes a single argument, which is the definition string for the schema
   * element that triggered the circular reference error.
   */
  public static final int MSGID_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 579;


  /**
   * The message ID for the message that will be used if an attempt is made to
   * do certain operations using an unauthenticated connection.
   */
  public static final int MSGID_REJECT_UNAUTHENTICATED_OPERATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 580;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry with an attribute type that is marked OBSOLETE.  This takes
   * two arguments, which are the DN of the entry and the name of the attribute
   * type.
   */
  public static final int MSGID_ADD_ATTR_IS_OBSOLETE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 581;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry with an objectclass that is marked OBSOLETE.  This takes two
   * arguments, which are the DN of the entry and the name of the objectclass.
   */
  public static final int MSGID_ADD_OC_IS_OBSOLETE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 582;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because one of the targeted attributes was marked OBSOLETE.  This
   * takes two arguments, which are the DN of the target entry and the name of
   * the attribute.
   */
  public static final int MSGID_MODIFY_ATTR_IS_OBSOLETE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 583;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an OBSOLETE objectclass to an entry.  This takes two arguments, which
   * are the name of the objectclass and the DN of the entry.
   */
  public static final int MSGID_ENTRY_ADD_OBSOLETE_OC =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 584;



  /**
   * The message ID for the response message that will be used if a modify DN
   * operation fails because the new RDN contains an attribute type which is
   * marked OBSOLETE in the server schema.  This takes two arguments, which are
   * the DN of the current entry and the name or OID of the obsolete attribute
   * type.
   */
  public static final int MSGID_MODDN_NEWRDN_ATTR_IS_OBSOLETE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 585;



  /**
   * The message ID for the message that will be used if there was no DIT
   * structure rule associated with an entry, but there was a DIT structure rule
   * for its parent.  This takes two arguments, which are the DN of the entry
   * and the DN of the parent entry.
   */
  public static final int MSGID_ENTRY_SCHEMA_VIOLATES_PARENT_DSR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 586;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while attempting to evaluate a DIT structure rule for an entry's
   * parent.  This takes two arguments, which are the DN of the entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_ENTRY_SCHEMA_COULD_NOT_CHECK_PARENT_DSR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 587;



  /**
   * The message ID for the message that will be used if a client connection is
   * terminated because the associated authentication or authorization entry was
   * removed from the server.  It takes a single argument, which is the DN of
   * the entry that has been removed.
   */
  public static final int MSGID_CLIENTCONNECTION_DISCONNECT_DUE_TO_DELETE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_WARNING | 588;



  /**
   * The message ID for the message that will be used if a modify request
   * includes an attempt to reset another user's password by an individual that
   * does not have the appropriate privileges.  This does not take any
   * arguments.
   */
  public static final int MSGID_MODIFY_PWRESET_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 589;



  /**
   * The message ID for the message that will be used if a compare request
   * targets the server configuration but the requester doesn't have the
   * appropriate privileges.  This does not take any arguments.
   */
  public static final int MSGID_COMPARE_CONFIG_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 590;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry with one or more privileges but the user doesn't have
   * sufficient privilege to update privileges.  This does not take any
   * arguments.
   */
  public static final int MSGID_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 591;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * modify the set of privileges contained in an entry but the user doesn't
   * have sufficient privileges to make that change.  This does not take any
   * arguments.
   */
  public static final int
       MSGID_MODIFY_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES =
            CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 592;



  /**
   * The message ID for the audit message that will be generated when a client
   * attempts to perform a privileged operation that requires a single
   * privilege.  This takes five arguments, which are the connection ID, the
   * operation ID, the authentication DN, the name of the requested privilege,
   * and the result of the determination.
   */
  public static final int MSGID_CLIENTCONNECTION_AUDIT_HASPRIVILEGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 593;



  /**
   * The message ID for the audit message that will be generated when a client
   * attempts to perform a privileged operation that requires a multiple
   * privileges.  This takes five arguments, which are the connection ID, the
   * operation ID, the authentication DN, a formatted list of the names of the
   * requested privileges, and the result of the determination.
   */
  public static final int MSGID_CLIENTCONNECTION_AUDIT_HASPRIVILEGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 594;



  /**
   * The message ID for the message that will be used when a client attempts to
   * use the proxied authorization control without sufficient privileges.  This
   * does not take any arguments.
   */
  public static final int MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 595;



  /**
   * The message ID for the message that will be used as the description for the
   * checkStartability command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_CHECK_STARTABILITY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 596;

  /**
   * The message ID for the message that will be used if an entry includes an
   * attribute with no values.  This takes two arguments, which are the DN of
   * the entry and the name of the attribute.
   */
  public static final int MSGID_ENTRY_SCHEMA_ATTR_NO_VALUES =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 597;


  /**
   * The message ID for the message that will be used when the user asks to run
   * the server in no-detach mode and the server is configured to run as a
   * service.  This message does not take arguments.
   */
  public static final int MSGID_DSCORE_ERROR_NODETACH_AND_WINDOW_SERVICE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 598;

  /**
   * The message ID for the message that will be used as the description for the
   * windowsNetStart command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_DSCORE_DESCRIPTION_WINDOWS_NET_START =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 599;



  /**
   * The message ID for the message that will be used if an entry is encoded
   * with a version number that we don't support.  This takes a single argument,
   * which is a hexadecimal representation of the version number byte.
   */
  public static final int MSGID_ENTRY_DECODE_UNRECOGNIZED_VERSION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 600;



  /**
   * The message ID for the message that will be used if an exception is caught
   * while attempting to decode an entry.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_ENTRY_DECODE_EXCEPTION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 601;



  /**
   * The message ID for the message that will be used if a NOT filter does not
   * contain exactly one filter component.  This takes three arguments, which
   * are the filter string and the start and end position of the NOT filter.
   */
  public static final int MSGID_SEARCH_FILTER_NOT_EXACTLY_ONE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 602;



  /**
   * The message ID for the message that will be used if a sort key string
   * has an invalid order indicator.  This takes a single argument, which is the
   * sort key string.
   */
  public static final int MSGID_SORTKEY_INVALID_ORDER_INDICATOR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 603;



  /**
   * The message ID for the message that will be used if a sort key string
   * has an undefined attribute type.  This takes two arguments, which are the
   * sort key string and the name of the attribute type.
   */
  public static final int MSGID_SORTKEY_UNDEFINED_TYPE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 604;



  /**
   * The message ID for the message that will be used if a sort key string
   * has an attribute type with no ordering matching rule.  This takes two
   * arguments, which are the sort key string and the name of the attribute
   * type.
   */
  public static final int MSGID_SORTKEY_NO_ORDERING_RULE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 605;



  /**
   * The message ID for the message that will be used if a sort key string
   * has an undefined ordering rule.  This takes two arguments, which are the
   * sort key string and the name of the ordering rule.
   */
  public static final int MSGID_SORTKEY_UNDEFINED_ORDERING_RULE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 606;



  /**
   * The message ID for the message that will be used if a sort order string
   * does not include any sort keys.  This takes a single argument, which is the
   * sort order string.
   */
  public static final int MSGID_SORTORDER_DECODE_NO_KEYS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 607;

  /**
   * The message ID for the string representation of the result code that will
   * be used for search operations containing the VLV request control that do
   * not also include the server-side sort control.
   */
  public static final int MSGID_RESULT_SORT_CONTROL_MISSING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 608;



  /**
   * The message ID for the string representation of the result code that will
   * be used for search operations containing the VLV request control with an
   * invalid offset or target count.
   */
  public static final int MSGID_RESULT_OFFSET_RANGE_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 609;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the request would have impacted
   * information in multiple servers or repositories.
   */
  public static final int MSGID_RESULT_VIRTUAL_LIST_VIEW_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 610;


  /**
   * The message ID for the message that will be used if a request control
   * cannot be used due to insufficient access rights. This message takes a
   * single argument, which is the OID string of the control being used.
   */
  public static final
  int MSGID_CONTROL_INSUFFICIENT_ACCESS_RIGHTS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 611;


  /**
   * The message ID for the message that will be used if a connection handler
   * has a host port already in use by another connection handler.  This takes
   * two parameters: the connection handler name and the host port that
   * is trying to be used twice.
   */
  public static final int MSGID_HOST_PORT_ALREADY_SPECIFIED =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 612;


  /**
   * The message ID for the message that will be used if a connection handler
   * has a host port that we could not use.  This takes two parameters: the host
   * port and the connection handler name.
   */
  public static final int MSGID_HOST_PORT_CANNOT_BE_USED =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 613;

  /**
   * The message ID for the message that will be used if no connection handler
   * was enabled and we are not in the mode where we start
   * the server with no connection handlers.  This does not take any arguments.
   */
  public static final int MSGID_NOT_AVAILABLE_CONNECTION_HANDLERS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 614;


  /**
   * The message ID for the message that will be used if no connection handler
   * could be properly started and we are not in the mode where we start
   * the server with no connection handlers.  This does not take any arguments.
   */
  public static final int MSGID_ERROR_STARTING_CONNECTION_HANDLERS =
    CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 615;



  /**
   * The message ID for the message that will be used if a bind is rejected '
   * because the server is in lockdown mode and the client was not a root user.
   * This does not take any arguments.
   */
  public static final int MSGID_BIND_REJECTED_LOCKDOWN_MODE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 616;



  /**
   * The message ID for the message that will be used as the alert message
   * string when the server enters lockdown mode.  It does not take any
   * arguments.
   */
  public static final int MSGID_DIRECTORY_SERVER_ENTERING_LOCKDOWN_MODE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 617;



  /**
   * The message ID for the message that will be as used the alert message
   * string when the server leaves lockdown mode.  It does not take any
   * arguments.
   */
  public static final int MSGID_DIRECTORY_SERVER_LEAVING_LOCKDOWN_MODE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_NOTICE | 618;



  /**
   * The message ID for the message that will be used if an unauthorized client
   * tries to submit a request with the server in lockdown mode.
   */
  public static final int MSGID_REJECT_OPERATION_IN_LOCKDOWN_MODE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_NOTICE | 619;



  /**
   * The message ID for the message that will be used if an attribute used an
   * undefined token.  This takes a single argument, which is the hex
   * representation of the token bytes.
   */
  public static final int MSGID_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 620;



  /**
   * The message ID for the message that will be used if an object class set
   * used an undefined token.  This takes a single argument, which is the hex
   * representation of the token bytes.
   */
  public static final int MSGID_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 621;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write the updated compressed schema definitions.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_COMPRESSEDSCHEMA_CANNOT_WRITE_UPDATED_DATA =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 622;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an entry encode config object because it had an invalid
   * length.  This does not take any arguments.
   */
  public static final int MSGID_ENTRYENCODECFG_INVALID_LENGTH =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 623;



  /**
   * The message ID for the string representation of the result code that will
   * be used to indicate no action was taken as a result of the LDAP no-op
   * control.  This does not take any arguments.
   */
  public static final int MSGID_RESULT_NO_OPERATION =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 624;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * create an extensible match search filter without providing either an
   * attribute type or a matching rule ID.  This does not take any arguments.
   */
  public static final int
       MSGID_SEARCH_FILTER_CREATE_EXTENSIBLE_MATCH_NO_AT_OR_MR =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 625;



  /**
   * The message ID for the message that will be used if a filter string cannot
   * be decoded because it contained an extensible match component that did not
   * include either an attribute description or a matching rule ID.  This takes
   * two arguments, which are the filter string and the start position of the
   * extensible match component within that filter string.
   */
  public static final int MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_AD_OR_MR =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 626;



  /**
   * The message ID for the message that will be used if a filter string cannot
   * be decoded because it contained an extensible match component that
   * referenced an unknown matching rule ID.  This takes three arguments, which
   * are the filter string, the start position of the extensible match component
   * within that filter string, and the unknown matching rule ID.
   */
  public static final int MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_SUCH_MR =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 627;



  /**
   * The message ID for the message that will be used if a bind attempt is
   * rejected because either the entire server or the user's backend has a
   * writability mode of "disabled" and the server would not be able to update
   * the authentication failure count or the last login time.  This takes a
   * single argument, which is the target user DN.
   */
  public static final int MSGID_BIND_OPERATION_WRITABILITY_DISABLED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 628;



  /**
   * The message ID for the message that will be used if a new password is found
   * in the password history.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_PW_IN_HISTORY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 629;



  /**
   * The message ID for the message that will be used if a user entry contains
   * multiple values for the user-specific idle time limit attribute.  This
   * takes a single argument, which is the DN of the user entry.
   */
  public static final int MSGID_BIND_MULTIPLE_USER_IDLE_TIME_LIMITS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 630;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a user-specific idle time limit value as an integer.  This
   * takes two arguments, which are the provided time limit value and the DN of
   * the user entry.
   */
  public static final int MSGID_BIND_CANNOT_PROCESS_USER_IDLE_TIME_LIMIT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_WARNING | 631;



  /**
   * The message ID for the message that will be used to indicate that the idle
   * time limit has been exceeded for a client connection.  It does not take any
   * arguments.
   */
  public static final int MSGID_IDLETIME_LIMIT_EXCEEDED =
       CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 632;


  /**
   * The message ID for the message that will be used if the maximum password
   * age is enabled, but the warning interval is longer than the maximum age.
   * This takes a single argument, which is the DN of the password policy
   * configuration entry.
   */
  public static final int MSGID_PWPOLICY_WARNING_INTERVAL_LARGER_THAN_MAX_AGE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 633;



  /**
   * The message ID for the message that will be used if the maximum password
   * age is enabled, but the sum of the warning interval and the minimum age is
   * greater than the maximum age.  This takes a single argument, which is the
   * DN of the password policy configuration entry.
   */
  public static final int
       MSGID_PWPOLICY_MIN_AGE_PLUS_WARNING_GREATER_THAN_MAX_AGE =
            CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 634;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a workflow with an ID that is already registered with the server.
   * This takes a single argument, which is the conflicting workflow ID.
   */
  public static final int MSGID_REGISTER_WORKFLOW_ALREADY_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 635;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a workflow node with an ID that is already registered with a
   * network group.  This takes two arguments, which are the conflicting
   * workflow ID and the network group ID.
   */
  public static final int MSGID_REGISTER_WORKFLOW_NODE_ALREADY_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 636;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a network group with an ID that is already registered.  This
   * takes a single argument, which is the conflicting network group ID.
   */
  public static final int MSGID_REGISTER_NETWORK_GROUP_ALREADY_EXISTS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 637;



  /**
   * The message ID for the message that will be used if an error occurred while
   * trying to terminate a client connection.  It takes two arguments, which are
   * the connection ID and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_IDLETIME_DISCONNECT_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_MILD_ERROR | 638;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurred in the time thread.  It takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_IDLETIME_UNEXPECTED_ERROR =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 639;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * change the directory environment configuration while the server is running.
   * This does not take any arguments.
   */
  public static final int MSGID_DIRCFG_SERVER_ALREADY_RUNNING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 640;



  /**
   * The message ID for the message that will be used if an invalid server root
   * directory is specified.  This takes a single argument, which is the invalid
   * server root directory.
   */
  public static final int MSGID_DIRCFG_INVALID_SERVER_ROOT =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 641;



  /**
   * The message ID for the message that will be used if an invalid
   * configuration file is specified.  This takes a single argument, which is
   * the invalid configuration file.
   */
  public static final int MSGID_DIRCFG_INVALID_CONFIG_FILE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 642;



  /**
   * The message ID for the message that will be used if an invalid config
   * handler class is specified.  This takes a single argument, which is the
   * fully-qualified class name.
   */
  public static final int MSGID_DIRCFG_INVALID_CONFIG_CLASS =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 643;



  /**
   * The message ID for the message that will be used if an invalid schema
   * directory is specified.  This takes a single argument, which is the invalid
   * schema directory.
   */
  public static final int MSGID_DIRCFG_INVALID_SCHEMA_DIRECTORY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 644;



  /**
   * The message ID for the message that will be used if an invalid lock
   * directory is specified.  This takes a single argument, which is the invalid
   * lock directory.
   */
  public static final int MSGID_DIRCFG_INVALID_LOCK_DIRECTORY =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 645;



  /**
   * The message ID for the message that will be used if an invalid concurrency
   * level is specified.  This takes a single argument, which is the invalid
   * concurrency level.
   */
  public static final int MSGID_DIRCFG_INVALID_CONCURRENCY_LEVEL =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 646;



  /**
   * The message ID for the message that will be used if an invalid lock table
   * size is specified.  This takes a single argument, which is the invalid lock
   * table size.
   */
  public static final int MSGID_DIRCFG_INVALID_LOCK_TABLE_SIZE =
       CATEGORY_MASK_CORE | SEVERITY_MASK_SEVERE_ERROR | 647;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * alter the server environment configuration while the server is running.
   * This does not take any arguments.
   */
  public static final int MSGID_CANNOT_SET_ENVIRONMENT_CONFIG_WHILE_RUNNING =
       CATEGORY_MASK_CORE | SEVERITY_MASK_FATAL_ERROR | 648;



  /**
   * Associates a set of generic messages with the message IDs defined
   * in this class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_CANNOT_CANCEL_ABANDON,
                    "Abandon requests cannot be canceled");
    registerMessage(MSGID_CANNOT_CANCEL_BIND,
                    "Bind requests cannot be canceled");
    registerMessage(MSGID_CANNOT_CANCEL_UNBIND,
                    "Unbind requests cannot be canceled");


    registerMessage(MSGID_DISCONNECT_DUE_TO_UNBIND, "Client Unbind");
    registerMessage(MSGID_DISCONNECT_DUE_TO_CLIENT_CLOSURE,
                    "Client Disconnect");
    registerMessage(MSGID_DISCONNECT_DUE_TO_REJECTED_CLIENT,
                    "Client Connection Rejected");
    registerMessage(MSGID_DISCONNECT_DUE_TO_IO_ERROR, "I/O Error");
    registerMessage(MSGID_DISCONNECT_DUE_TO_PROTOCOL_ERROR, "Protocol Error");
    registerMessage(MSGID_DISCONNECT_DUE_TO_SERVER_SHUTDOWN, "Server Shutdown");
    registerMessage(MSGID_DISCONNECT_BY_ADMINISTRATOR,
                    "Administrative Termination");
    registerMessage(MSGID_DISCONNECT_DUE_TO_SECURITY_PROBLEM,
                    "Security Problem");
    registerMessage(MSGID_DISCONNECT_DUE_TO_MAX_REQUEST_SIZE,
                    "Maximum Request Size Exceeded");
    registerMessage(MSGID_DISCONNECT_DUE_TO_ADMIN_LIMIT,
                    "Administrative Limit Exceeded");
    registerMessage(MSGID_DISCONNECT_DUE_TO_IDLE_TIME_LIMIT,
                    "Idle Time Limit Exceeded");
    registerMessage(MSGID_DISCONNECT_DUE_TO_IO_TIMEOUT, "I/O Timeout");
    registerMessage(MSGID_DISCONNECT_BY_PLUGIN, "Connection Closed by Plugin");
    registerMessage(MSGID_DISCONNECT_OTHER, "Unknown Closure Reason");
    registerMessage(MSGID_DISCONNECT_DUE_TO_SERVER_ERROR, "Server Error");


    registerMessage(MSGID_ERROR_CATEGORY_ACCESS_CONTROL,
                    ERROR_CATEGORY_ACCESS_CONTROL);
    registerMessage(MSGID_ERROR_CATEGORY_BACKEND, ERROR_CATEGORY_BACKEND);
    registerMessage(MSGID_ERROR_CATEGORY_CONFIG, ERROR_CATEGORY_CONFIG);
    registerMessage(MSGID_ERROR_CATEGORY_CONNECTION_HANDLING,
                    ERROR_CATEGORY_CONNECTION_HANDLING);
    registerMessage(MSGID_ERROR_CATEGORY_CORE_SERVER,
                    ERROR_CATEGORY_CORE_SERVER);
    registerMessage(MSGID_ERROR_CATEGORY_EXTENDED_OPERATION,
                    ERROR_CATEGORY_EXTENDED_OPERATION);
    registerMessage(MSGID_ERROR_CATEGORY_EXTENSIONS, ERROR_CATEGORY_EXTENSIONS);
    registerMessage(MSGID_ERROR_CATEGORY_PASSWORD_POLICY,
                    ERROR_CATEGORY_PASSWORD_POLICY);
    registerMessage(MSGID_ERROR_CATEGORY_PLUGIN, ERROR_CATEGORY_PLUGIN);
    registerMessage(MSGID_ERROR_CATEGORY_REQUEST_HANDLING,
                    ERROR_CATEGORY_REQUEST);
    registerMessage(MSGID_ERROR_CATEGORY_SASL_MECHANISM,
                    ERROR_CATEGORY_SASL_MECHANISM);
    registerMessage(MSGID_ERROR_CATEGORY_SCHEMA, ERROR_CATEGORY_SCHEMA);
    registerMessage(MSGID_ERROR_CATEGORY_SHUTDOWN, ERROR_CATEGORY_SHUTDOWN);
    registerMessage(MSGID_ERROR_CATEGORY_STARTUP, ERROR_CATEGORY_STARTUP);
    registerMessage(MSGID_ERROR_CATEGORY_SYNCHRONIZATION,
                    ERROR_CATEGORY_SYNCHRONIZATION);
    registerMessage(MSGID_ERROR_CATEGORY_TASK, ERROR_CATEGORY_TASK);


    registerMessage(MSGID_ERROR_SEVERITY_FATAL_ERROR, ERROR_SEVERITY_FATAL);
    registerMessage(MSGID_ERROR_SEVERITY_INFORMATIONAL,
                    ERROR_SEVERITY_INFORMATIONAL);
    registerMessage(MSGID_ERROR_SEVERITY_MILD_ERROR,
                    ERROR_SEVERITY_MILD_ERROR);
    registerMessage(MSGID_ERROR_SEVERITY_MILD_WARNING,
                    ERROR_SEVERITY_MILD_WARNING);
    registerMessage(MSGID_ERROR_SEVERITY_NOTICE, ERROR_SEVERITY_NOTICE);
    registerMessage(MSGID_ERROR_SEVERITY_SEVERE_ERROR,
                    ERROR_SEVERITY_SEVERE_ERROR);
    registerMessage(MSGID_ERROR_SEVERITY_SEVERE_WARNING,
                    ERROR_SEVERITY_SEVERE_WARNING);


    registerMessage(MSGID_RESULT_SUCCESS, "Success");
    registerMessage(MSGID_RESULT_OPERATIONS_ERROR, "Operations Error");
    registerMessage(MSGID_RESULT_PROTOCOL_ERROR, "Protocol Error");
    registerMessage(MSGID_RESULT_TIME_LIMIT_EXCEEDED, "Time Limit Exceeded");
    registerMessage(MSGID_RESULT_SIZE_LIMIT_EXCEEDED, "Size Limit Exceeded");
    registerMessage(MSGID_RESULT_COMPARE_FALSE, "Compare False");
    registerMessage(MSGID_RESULT_COMPARE_TRUE, "Compare True");
    registerMessage(MSGID_RESULT_AUTH_METHOD_NOT_SUPPORTED,
                    "Authentication Method Not Supported");
    registerMessage(MSGID_RESULT_STRONG_AUTH_REQUIRED,
                    "Strong Authentication Required");
    registerMessage(MSGID_RESULT_REFERRAL, "Referral");
    registerMessage(MSGID_RESULT_ADMIN_LIMIT_EXCEEDED,
                    "Administrative Limit Exceeded");
    registerMessage(MSGID_RESULT_UNAVAILABLE_CRITICAL_EXTENSION,
                    "Unavailable Critical Extension");
    registerMessage(MSGID_RESULT_CONFIDENTIALITY_REQUIRED,
                    "Confidentiality Required");
    registerMessage(MSGID_RESULT_SASL_BIND_IN_PROGRESS,
                    "SASL Bind in Progress");
    registerMessage(MSGID_RESULT_NO_SUCH_ATTRIBUTE, "No Such Attribute");
    registerMessage(MSGID_RESULT_UNDEFINED_ATTRIBUTE_TYPE,
                    "Undefined Attribute Type");
    registerMessage(MSGID_RESULT_INAPPROPRIATE_MATCHING,
                    "Inappropriate Matching");
    registerMessage(MSGID_RESULT_CONSTRAINT_VIOLATION, "Constraint Violation");
    registerMessage(MSGID_RESULT_ATTRIBUTE_OR_VALUE_EXISTS,
                    "Attribute or Value Exists");
    registerMessage(MSGID_RESULT_INVALID_ATTRIBUTE_SYNTAX,
                    "Invalid Attribute Syntax");
    registerMessage(MSGID_RESULT_NO_SUCH_OBJECT, "No Such Entry");
    registerMessage(MSGID_RESULT_ALIAS_PROBLEM, "Alias Problem");
    registerMessage(MSGID_RESULT_INVALID_DN_SYNTAX, "Invalid DN Syntax");
    registerMessage(MSGID_RESULT_ALIAS_DEREFERENCING_PROBLEM,
                    "Alias Dereferencing Problem");
    registerMessage(MSGID_RESULT_INAPPROPRIATE_AUTHENTICATION,
                    "Inappropriate Authentication");
    registerMessage(MSGID_RESULT_INVALID_CREDENTIALS, "Invalid Credentials");
    registerMessage(MSGID_RESULT_INSUFFICIENT_ACCESS_RIGHTS,
                    "Insufficient Access Rights");
    registerMessage(MSGID_RESULT_BUSY, "Busy");
    registerMessage(MSGID_RESULT_UNAVAILABLE, "Unavailable");
    registerMessage(MSGID_RESULT_UNWILLING_TO_PERFORM, "Unwilling to Perform");
    registerMessage(MSGID_RESULT_LOOP_DETECT, "Loop Detected");
    registerMessage(MSGID_RESULT_SORT_CONTROL_MISSING, "Sort Control Missing");
    registerMessage(MSGID_RESULT_OFFSET_RANGE_ERROR, "Offset Range Error");
    registerMessage(MSGID_RESULT_NAMING_VIOLATION, "Naming Violation");
    registerMessage(MSGID_RESULT_OBJECTCLASS_VIOLATION,
                    "ObjectClass Violation");
    registerMessage(MSGID_RESULT_NOT_ALLOWED_ON_NONLEAF,
                    "Not Allowed on Non-Leaf");
    registerMessage(MSGID_RESULT_NOT_ALLOWED_ON_RDN, "Not Allowed on RDN");
    registerMessage(MSGID_RESULT_ENTRY_ALREADY_EXISTS, "Entry Already Exists");
    registerMessage(MSGID_RESULT_OBJECTCLASS_MODS_PROHIBITED,
                    "ObjectClass Modifications Prohibited");
    registerMessage(MSGID_RESULT_AFFECTS_MULTIPLE_DSAS,
                    "Affects Multiple DSAs");
    registerMessage(MSGID_RESULT_VIRTUAL_LIST_VIEW_ERROR,
                    "Virtual List View Error");
    registerMessage(MSGID_RESULT_OTHER, "Other");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_SERVER_DOWN,
                            "Server Connection Closed");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_LOCAL_ERROR, "Local Error");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_ENCODING_ERROR, "Encoding Error");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_DECODING_ERROR, "Decoding Error");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_TIMEOUT, "Client-Side Timeout");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_AUTH_UNKNOWN,
                    "Unknown Authentication Mechanism");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_FILTER_ERROR, "Filter Error");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_USER_CANCELLED,
                    "Cancelled by User");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_PARAM_ERROR, "Parameter Error");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_NO_MEMORY, "Out of Memory");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_CONNECT_ERROR, "Connect Error");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_NOT_SUPPORTED,
                    "Operation Not Supported");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_CONTROL_NOT_FOUND,
                    "Control Not Found");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_NO_RESULTS_RETURNED,
                    "No Results Returned");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_MORE_RESULTS_TO_RETURN,
                    "More Results to Return");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_CLIENT_LOOP,
                    "Referral Loop Detected");
    registerMessage(MSGID_RESULT_CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED,
                    "Referral Hop Limit Exceeded");
    registerMessage(MSGID_RESULT_CANCELED, "Canceled");
    registerMessage(MSGID_RESULT_NO_SUCH_OPERATION, "No Such Operation");
    registerMessage(MSGID_RESULT_TOO_LATE, "Too Late");
    registerMessage(MSGID_RESULT_CANNOT_CANCEL, "Cannot Cancel");
    registerMessage(MSGID_RESULT_ASSERTION_FAILED, "Assertion Failed");
    registerMessage(MSGID_RESULT_AUTHORIZATION_DENIED, "Authorization Denied");
    registerMessage(MSGID_RESULT_NO_OPERATION, "No Operation");


    registerMessage(MSGID_UNKNOWN_ATTRIBUTE_USAGE,
                    "Unable to determine the attribute usage type for " +
                    "attribute %s.  The server will assume that it is " +
                    "user-defined");
    registerMessage(MSGID_ATTR_TYPE_NORMALIZE_NO_MR,
                    "Unable to normalize value %s for attribute type %s " +
                    "because no equality matching rule is defined for that " +
                    "attribute");


    registerMessage(MSGID_CANCELED_BY_SHUTDOWN,
                    "Processing on this operation has been canceled because " +
                    "the Directory Server is shutting down");
    registerMessage(MSGID_UNCAUGHT_WORKER_THREAD_EXCEPTION,
                    "%s encountered an uncaught exception while processing " +
                    "operation %s:  %s");
    registerMessage(MSGID_UNEXPECTED_WORKER_THREAD_EXIT,
                    "%s is unexpectedly exiting when the Directory Server " +
                    "is not in the process of shutting down.  This likely " +
                    "indicates that the thread encountered an unexpected " +
                    "error");
    registerMessage(MSGID_CANNOT_CREATE_WORKER_THREAD,
                    "An unexpected error occurred while trying to create a " +
                    "worker thread:  %s");
    registerMessage(MSGID_OP_REJECTED_BY_SHUTDOWN,
                    "The request to process this operation has been rejected " +
                    "because the Directory Server has already started its " +
                    "shutdown process");
    registerMessage(MSGID_OP_REJECTED_BY_QUEUE_FULL,
                    "The request to process this operation has been rejected " +
                    "because the work queue has already reached its maximum " +
                    "capacity of %d pending operations");
    registerMessage(MSGID_WORKER_INTERRUPTED_WITHOUT_SHUTDOWN,
                    "%s was interrupted while waiting for new work:  %s.  " +
                    "This should not happen, but the thread will resume " +
                    "waiting for new work so there should be no adverse " +
                    "effects");
    registerMessage(MSGID_WORKER_WAITING_UNCAUGHT_EXCEPTION,
                    "An unexpected exception was caught while %s was waiting " +
                    "for new work:  %s.  This should not happen, but the " +
                    "thread will resume waiting for new work so there should " +
                    "be no adverse effects");
    registerMessage(MSGID_QUEUE_UNABLE_TO_CANCEL,
                    "The work queue caught an exception while trying to " +
                    "cancel pending operation %s when the Directory Server " +
                    "was shutting down:  %s");
    registerMessage(MSGID_QUEUE_UNABLE_TO_NOTIFY_THREAD,
                    "The work queue caught an exception while trying to " +
                    "notify %s that the Directory Server was shutting " +
                    "down:  %s");
    registerMessage(MSGID_WORKER_STOPPED_BY_REDUCED_THREADNUMBER,
                    "%s has been stopped because the total number of worker " +
                    "threads in the Directory Server was reduced");


    registerMessage(MSGID_CANNOT_SET_ENVIRONMENT_CONFIG_WHILE_RUNNING,
                    "The Directory Server is currently running.  The " +
                    "environment configuration may not be altered while the " +
                    "server is online");
    registerMessage(MSGID_CANNOT_BOOTSTRAP_WHILE_RUNNING,
                    "The Directory Server is currently running.  The " +
                    "configuration may not be bootstrapped while the server " +
                    "is online");
    registerMessage(MSGID_DIRECTORY_BOOTSTRAPPING,
                    "The Directory Server is beginning the configuration " +
                    "bootstrapping process");
    registerMessage(MSGID_DIRECTORY_BOOTSTRAPPED,
                    "The Directory Server has completed the configuration " +
                    "bootstrapping process");
    registerMessage(MSGID_CANNOT_LOAD_CONFIG_HANDLER_CLASS,
                    "Unable to load class %s to serve as the Directory " +
                    "Server configuration handler:  %s");
    registerMessage(MSGID_CANNOT_INSTANTIATE_CONFIG_HANDLER,
                    "Unable to create an instance of class %s to serve as " +
                    "the Directory Server configuration handler: %s");
    registerMessage(MSGID_CANNOT_INITIALIZE_CONFIG_HANDLER,
                    "An error occurred while trying to initialize the " +
                    "configuration handler %s using configuration file %s:  " +
                    "%s");
    registerMessage(MSGID_CANNOT_START_BEFORE_BOOTSTRAP,
                    "The Directory Server may not be started before the " +
                    "configuration has been bootstrapped");
    registerMessage(MSGID_CANNOT_START_WHILE_RUNNING,
                    "The Directory Server may not be started while it is " +
                    "already running.   Please stop the running instance " +
                    "before attempting to start it again");
    registerMessage(MSGID_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK,
                    "The Directory Server could not acquire an exclusive " +
                    "lock on file %s:  %s.  This generally means that " +
                    "another instance of this server is already running");
    registerMessage(MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE,
                    "An error occurred while attempting to bootstrap the " +
                    "matching rule defined in class %s:  %s");
    registerMessage(MSGID_CANNOT_BOOTSTRAP_SYNTAX,
                    "An error occurred while attempting to bootstrap the " +
                    "attribute syntax defined in class %s:  %s");
    registerMessage(MSGID_DIRECTORY_SERVER_STARTING, "%s starting up");
    registerMessage(MSGID_DIRECTORY_SERVER_STARTED,
                    "The Directory Server has started successfully");
    registerMessage(MSGID_CANNOT_CREATE_MBEAN_SERVER,
                    "An error occurred while attempting to create the JMX " +
                    "MBean server that will be used for monitoring, " +
                    "notification, and configuration interaction within the " +
                    "Directory Server:  %s");
    registerMessage(MSGID_SENT_ALERT_NOTIFICATION,
                    "The Directory Server has sent an alert notification " +
                    "generated by class %s (alert type %s, alert ID %s):  %s");
    registerMessage(MSGID_UNCAUGHT_THREAD_EXCEPTION,
                    "An uncaught exception during processing for thread %s " +
                    "has caused it to terminate abnormally.  The stack trace " +
                    "for that exception is:  %s");
    registerMessage(MSGID_SERVER_SHUTDOWN,
                    "The Directory Server has started the shutdown process.  " +
                    "The shutdown was initiated by an instance of class %s " +
                    "and the reason provided for the shutdown was %s");
    registerMessage(MSGID_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK,
                    "An error occurred while attempting to release a shared " +
                    "lock for backend %s:  %s.  This lock should be " +
                    "automatically cleaned when the Directory Server process " +
                    "exits, so no additional action should be necessary");
    registerMessage(MSGID_CANNOT_RELEASE_EXCLUSIVE_SERVER_LOCK,
                    "An error occurred while attempting to release the " +
                    "exclusive server lock held on file %s:  %s.  This lock " +
                    "should be automatically cleaned when the Directory " +
                    "Server process exits, so no additional action should be " +
                    "necessary");
    registerMessage(MSGID_SHUTDOWN_DUE_TO_SHUTDOWN_HOOK,
                    "The Directory Server shutdown hook detected that the " +
                    "JVM is shutting down.  This generally indicates that " +
                    "JVM received an external request to stop (e.g., through " +
                    "a kill signal)");
    registerMessage(MSGID_SERVER_STOPPED,
                    "The Directory Server is now stopped");
    registerMessage(MSGID_CANNOT_CREATE_WORK_QUEUE,
                    "An error occurred while trying to create the Directory " +
                    "Server work queue:  %s.  This is an unrecoverable error " +
                    "and the startup process will not be able to continue");
    registerMessage(MSGID_CANNOT_REGISTER_DUPLICATE_ALTERNATE_ROOT_BIND_DN,
                    "The alternate root bind DN \"%s\" is already registered " +
                    "with the Directory Server for actual root entry DN " +
                    "\"%s\"");
    registerMessage(MSGID_CANNOT_REGISTER_DUPLICATE_SUFFIX,
                    "The suffix \"%s\" is already registered with the " +
                    "Directory Server with a backend of type %s");
    registerMessage(MSGID_CANNOT_REGISTER_DUPLICATE_SUBSUFFIX,
                    "The suffix \"%s\" is already registered with the " +
                    "Directory Server as a sub-suffix of the backend for " +
                    "suffix \"%s\"");
    registerMessage(MSGID_CANNOT_REGISTER_PRIVATE_SUFFIX_BELOW_USER_PARENT,
                    "The private suffix \"%s\" is below a non-private suffix " +
                    "defined with a base DN of \"%s\".  A private sub-suffix " +
                    "may not exist below a non-private suffix");
    registerMessage(MSGID_CANNOT_GET_ROOT_DSE_CONFIG_ENTRY,
                    "An error occurred while trying to retrieve the root " +
                    "DSE configuration entry (" + DN_ROOT_DSE_CONFIG +
                    ") from the Directory Server configuration:  %s");
    registerMessage(MSGID_STARTUP_PLUGIN_ERROR,
                    "A fatal error occurred when executing one of the " +
                    "Directory Server startup plugins:  %s (error ID %d).  " +
                    "The Directory Server startup process has been aborted");


    registerMessage(MSGID_ENTRY_SCHEMA_UNKNOWN_OC,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it contains an unknown " +
                    "objectclass %s");
    registerMessage(MSGID_ENTRY_SCHEMA_MISSING_REQUIRED_ATTR_FOR_OC,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it is missing attribute %s " +
                    "which is required by objectclass %s");
    registerMessage(MSGID_ENTRY_SCHEMA_DISALLOWED_USER_ATTR_FOR_OC,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it includes attribute %s which is " +
                    "not allowed by any of the objectclasses defined in that " +
                    "entry");
    registerMessage(MSGID_ENTRY_SCHEMA_ATTR_NO_VALUES,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it includes attribute %s without " +
                    "any values");
    registerMessage(MSGID_ENTRY_SCHEMA_ATTR_SINGLE_VALUED,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it includes multiple values for " +
                    "attribute %s, which is defined as a single-valued " +
                    "attribute");
    registerMessage(MSGID_ENTRY_SCHEMA_MULTIPLE_STRUCTURAL_CLASSES,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it includes multiple conflicting " +
                    "structural objectclasses %s and %s.  Only a single " +
                    "structural objectclass is allowed in an entry");
    registerMessage(MSGID_ENTRY_SCHEMA_NO_STRUCTURAL_CLASS,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it does not include a structural " +
                    "objectclass.  All entries must contain a structural " +
                    "objectclass");
    registerMessage(MSGID_ENTRY_SCHEMA_RDN_MISSING_REQUIRED_ATTR,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because its RDN does not contain " +
                    "attribute %s that is required by name form %s");
    registerMessage(MSGID_ENTRY_SCHEMA_RDN_DISALLOWED_ATTR,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because its RDN contains attribute %s " +
                    "that is not allowed by name form %s");
    registerMessage(MSGID_ENTRY_SCHEMA_MISSING_REQUIRED_ATTR_FOR_DCR,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it is missing attribute %s " +
                    "which is required by DIT content rule %s");
    registerMessage(MSGID_ENTRY_SCHEMA_PROHIBITED_ATTR_FOR_DCR,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it contains attribute %s which is " +
                    "prohibited by DIT content rule %s");
    registerMessage(MSGID_ENTRY_SCHEMA_DISALLOWED_USER_ATTR_FOR_DCR,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it includes attribute %s which is " +
                    "not in the list of allowed or required attributes for " +
                    "DIT content rule %s");
    registerMessage(MSGID_ENTRY_SCHEMA_DISALLOWED_AUXILIARY_CLASS,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because it includes auxiliary objectClass " +
                    "%s that is not allowed by DIT content rule %s");
    registerMessage(MSGID_ENTRY_SCHEMA_DSR_COULD_NOT_LOCK_PARENT,
                    "The Directory Server was unable to evaluate entry %s to " +
                    "determine whether it was compliant with the DIT " +
                    "structure rule configuration because it was unable to " +
                    "obtain a read lock on parent entry %s");
    registerMessage(MSGID_ENTRY_SCHEMA_DSR_NO_PARENT_ENTRY,
                    "The Directory Server was unable to evaluate entry %s to " +
                    "determine whether it was compliant with the DIT " +
                    "structure rule configuration because parent entry %s " +
                    "either does not exist or could not be retrieved");
    registerMessage(MSGID_ENTRY_SCHEMA_DSR_NO_PARENT_OC,
                    "The Directory Server was unable to evaluate entry %s to " +
                    "determine whether it was compliant with the DIT " +
                    "rule configuration because the parent entry %s does not " +
                    "appear to contain a valid structural objectclass");
    registerMessage(MSGID_ENTRY_SCHEMA_VIOLATES_PARENT_DSR,
                    "Entry %s is invalid according to the server schema " +
                    "because there is no DIT structure rule that applies " +
                    "to that entry, but there is a DIT structure rule for " +
                    "the parent entry %s");
    registerMessage(MSGID_ENTRY_SCHEMA_DSR_DISALLOWED_SUPERIOR_OC,
                    "Entry %s violates the Directory Server schema " +
                    "configuration because DIT structure rule %s does not " +
                    "allow entries of type %s to be placed immediately below " +
                    "entries of type %s");
    registerMessage(MSGID_ENTRY_SCHEMA_COULD_NOT_CHECK_DSR,
                    "An unexpected error occurred while attempting to check " +
                    "entry %s against DIT structure rule %s:  %s");
    registerMessage(MSGID_ENTRY_SCHEMA_COULD_NOT_CHECK_PARENT_DSR,
                    "An unexpected error occurred while attempting to " +
                    "perform DIT structure rule processing for the parent of " +
                    "entry %s:  %s");
    registerMessage(MSGID_ENTRY_SET_UNKNOWN_OC,
                    "Objectclass %s cannot be used in entry %s because that " +
                    "class is not defined in the Directory Server schema");
    registerMessage(MSGID_ENTRY_ADD_UNKNOWN_OC,
                    "Objectclass %s cannot be added to entry %s because that " +
                    "class is not defined in the Directory Server schema");
    registerMessage(MSGID_ENTRY_ADD_DUPLICATE_OC,
                    "Objectclass %s is already present in entry %s and " +
                    "cannot be added a second time");
    registerMessage(MSGID_ENTRY_ADD_OBSOLETE_OC,
                    "Objectclass %s added to entry %s is marked OBSOLETE in " +
                    "the server schema");
    registerMessage(MSGID_ENTRY_DUPLICATE_VALUES,
                    "Unable to add one or more values to attribute %s " +
                    "because at least one of the values already exists");
    registerMessage(MSGID_ENTRY_NO_SUCH_VALUE,
                    "Unable to remove one or more values from attribute %s " +
                    "because at least one of the attributes does not exist " +
                    "in the entry");
    registerMessage(MSGID_ENTRY_OC_INCREMENT_NOT_SUPPORTED,
                    "The increment operation is not supported for the " +
                    "objectClass attribute");
    registerMessage(MSGID_ENTRY_UNKNOWN_MODIFICATION_TYPE,
                    "Unknown modification type %s requested");
    registerMessage(MSGID_ENTRY_INCREMENT_NO_SUCH_ATTRIBUTE,
                    "Unable to increment the value of attribute %s because " +
                    "that attribute does not exist in the entry");
    registerMessage(MSGID_ENTRY_INCREMENT_MULTIPLE_VALUES,
                    "Unable to increment the value of attribute %s because " +
                    "there are multiple values for that attribute");
    registerMessage(MSGID_ENTRY_INCREMENT_INVALID_VALUE_COUNT,
                    "Unable to increment the value of attribute %s because " +
                    "the provided modification did not have exactly one " +
                    "value to use as the increment");
    registerMessage(MSGID_ENTRY_INCREMENT_CANNOT_PARSE_AS_INT,
                    "Unable to increment the value of attribute %s because " +
                    "either the current value or the increment could not " +
                    "be parsed as an integer");
    registerMessage(MSGID_ENTRY_DECODE_UNRECOGNIZED_VERSION,
                    "Unable to decode an entry because it had an unsupported " +
                    "entry version byte value of %s");
    registerMessage(MSGID_ENTRY_DECODE_EXCEPTION,
                    "Unable to decode an entry because an unexpected " +
                    "exception was caught during processing:  %s");


    registerMessage(MSGID_SEARCH_FILTER_CREATE_EXTENSIBLE_MATCH_NO_AT_OR_MR,
                    "Unable to create an extensible match search filter " +
                    "using the provided information because it did not " +
                    "contain either an attribute type or a matching rule " +
                    "ID.  At least one of these must be provided");
    registerMessage(MSGID_SEARCH_FILTER_NULL,
                    "Unable to decode the provided filter string as a search " +
                    "filter because the provided string was empty or null");
    registerMessage(MSGID_SEARCH_FILTER_UNCAUGHT_EXCEPTION,
                    "An unexpected error occurred while attempting to decode " +
                    "the string \"%s\" as a search filter:  %s");
    registerMessage(MSGID_SEARCH_FILTER_MISMATCHED_PARENTHESES,
                    "The provided search filter \"%s\" had mismatched " +
                    "parentheses around the portion between positions %d and " +
                    "%d");
    registerMessage(MSGID_SEARCH_FILTER_NO_EQUAL_SIGN,
                    "The provided search filter \"%s\" was missing an equal " +
                    "sign in the suspected simple filter component between " +
                    "positions %d and %d");
    registerMessage(MSGID_SEARCH_FILTER_INVALID_ESCAPED_BYTE,
                    "The provided search filter \"%s\" had an invalid " +
                    "escaped byte value at position %d.  A backslash in a " +
                    "value must be followed by two hexadecimal characters " +
                    "that define the byte that has been encoded");
    registerMessage(MSGID_SEARCH_FILTER_COMPOUND_MISSING_PARENTHESES,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the compound filter between positions %d and %d " +
                    "did not start with an open parenthesis and end with a " +
                    "close parenthesis (they may be parentheses for " +
                    "different filter components)");
    registerMessage(MSGID_SEARCH_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the closing parenthesis at position %d did not " +
                    "have a corresponding open parenthesis");
    registerMessage(MSGID_SEARCH_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the closing parenthesis at position %d did not " +
                    "have a corresponding close parenthesis");
    registerMessage(MSGID_SEARCH_FILTER_SUBSTRING_NO_ASTERISKS,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the assumed substring filter value between " +
                    "positions %d and %d did not have any asterisk wildcard " +
                    "characters");
    registerMessage(MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_COLON,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the extensible match component starting at " +
                    "position %d did not have a colon to denote the end of " +
                    "the attribute type name");
    registerMessage(MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_AD_OR_MR,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the extensible match component starting at " +
                    "position %d did not contain either an attribute " +
                    "description or a matching rule ID.  At least one of " +
                    "these must be provided");
    registerMessage(MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_SUCH_MR,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the extensible match component starting at " +
                    "position %d referenced an unknown matching rule %s");
    registerMessage(MSGID_SEARCH_FILTER_NOT_EXACTLY_ONE,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the NOT filter between positions %d and %d " +
                    "did not contain exactly one filter component");
    registerMessage(MSGID_SEARCH_FILTER_INVALID_FILTER_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because it contained an unknown filter type %s");
    registerMessage(MSGID_SEARCH_FILTER_INVALID_RESULT_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because the internal check returned an unknown " +
                    "result type \"%s\"");
    registerMessage(MSGID_SEARCH_FILTER_COMPOUND_COMPONENTS_NULL,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because the set of filter components for an %s " +
                    "component was NULL");
    registerMessage(MSGID_SEARCH_FILTER_NESTED_TOO_DEEP,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because the filter was nested beyond the maximum " +
                    "allowed depth of " + MAX_NESTED_FILTER_DEPTH + " levels");
    registerMessage(MSGID_SEARCH_FILTER_NOT_COMPONENT_NULL,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because the NOT filter component did not include " +
                    "a subcomponent");
    registerMessage(MSGID_SEARCH_FILTER_EQUALITY_NO_ATTRIBUTE_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because an equality component had a NULL " +
                    "attribute type");
    registerMessage(MSGID_SEARCH_FILTER_EQUALITY_NO_ASSERTION_VALUE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because an equality component for attribute %s " +
                    "had a NULL assertion value");
    registerMessage(MSGID_SEARCH_FILTER_SUBSTRING_NO_ATTRIBUTE_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a substring component had a NULL " +
                    "attribute type");
    registerMessage(MSGID_SEARCH_FILTER_SUBSTRING_NO_SUBSTRING_COMPONENTS,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a substring component for attribute %s " +
                    "did not have any subInitial, subAny, or subFinal " +
                    "elements");
    registerMessage(MSGID_SEARCH_FILTER_GREATER_OR_EQUAL_NO_ATTRIBUTE_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a greater-or-equal component had a NULL " +
                    "attribute type");
    registerMessage(MSGID_SEARCH_FILTER_GREATER_OR_EQUAL_NO_VALUE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a greater-or-equal component for " +
                    "attribute %s had a NULL assertion value");
    registerMessage(MSGID_SEARCH_FILTER_LESS_OR_EQUAL_NO_ATTRIBUTE_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a less-or-equal component had a NULL " +
                    "attribute type");
    registerMessage(MSGID_SEARCH_FILTER_LESS_OR_EQUAL_NO_ASSERTION_VALUE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a less-or-equal component for attribute " +
                    "%s had a NULL assertion value");
    registerMessage(MSGID_SEARCH_FILTER_PRESENCE_NO_ATTRIBUTE_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a presence component had a NULL " +
                    "attribute type");
    registerMessage(MSGID_SEARCH_FILTER_APPROXIMATE_NO_ATTRIBUTE_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because an approximate component had a NULL " +
                    "attribute type");
    registerMessage(MSGID_SEARCH_FILTER_APPROXIMATE_NO_ASSERTION_VALUE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because an approximate component for attribute " +
                    "%s had a NULL assertion value");
    registerMessage(MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_ASSERTION_VALUE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a contained extensible match filter did " +
                    "not have an assertion value");
    registerMessage(MSGID_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_RULE_OR_TYPE,
                    "Unable to determine whether entry \"%s\" matches filter " +
                    "\"%s\" because a contained extensible match filter did " +
                    "not have either an attribute type or a matching rule ID");
    registerMessage(MSGID_SEARCH_BACKEND_EXCEPTION,
                    "An unexpected error was encountered while processing " +
                    "a search in one of the Directory Server backends:  %s");


    registerMessage(MSGID_RDN_DECODE_NULL,
                    "Unable to decode the provided string as a relative " +
                    "distinguished name because the provided string was " +
                    "empty or null");
    registerMessage(MSGID_RDN_END_WITH_ATTR_NAME,
                    "Unable to decode the provided string \"%s\" as a " +
                    "relative distinguished name because the string ended " +
                    "with an attribute type name (%s)");
    registerMessage(MSGID_RDN_NO_EQUAL,
                    "Unable to decode the provided string \"%s\" as a " +
                    "relative distinguished name because the first non-blank " +
                    "character after the attribute type %s was not an " +
                    "equal sign (character read was %c)");
    registerMessage(MSGID_RDN_UNEXPECTED_COMMA,
                    "Unable to decode the provided string \"%s\" as a " +
                    "relative distinguished name because it contained an " +
                    "unexpected plus, comma, or semicolon at position %d, "+
                    "which is not allowed in an RDN");
    registerMessage(MSGID_RDN_ILLEGAL_CHARACTER,
                    "Unable to decode the provided string \"%s\" as a " +
                    "relative distinguished name because an illegal " +
                    "character %c was found at position %d, where either the " +
                    "end of the string or a '+' sign were expected");


    registerMessage(MSGID_SCHEMA_CONFLICTING_ATTRIBUTE_OID,
                    "Unable to register attribute type %s with the server " +
                    "schema because its OID %s conflicts with the OID of an " +
                    "existing attribute type %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_ATTRIBUTE_NAME,
                    "Unable to register attribute type %s with the server " +
                    "schema because its name %s conflicts with the name of " +
                    "an existing attribute type %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_OBJECTCLASS_OID,
                    "Unable to register objectclass %s with the server " +
                    "schema because its OID %s conflicts with the OID of an " +
                    "existing objectclass %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_OBJECTCLASS_NAME,
                    "Unable to register objectclass %s with the server " +
                    "schema because its name %s conflicts with the name of " +
                    "an existing objectclass %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_SYNTAX_OID,
                    "Unable to register attribute syntax %s with the server " +
                    "schema because its OID %s conflicts with the OID of an " +
                    "existing syntax %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_MR_OID,
                    "Unable to register matching rule %s with the server " +
                    "schema because its OID %s conflicts with the OID of an " +
                    "existing matching rule %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_MR_NAME,
                    "Unable to register matching rule %s with the server " +
                    "schema because its name %s conflicts with the name of " +
                    "an existing matching rule %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_MATCHING_RULE_USE,
                    "Unable to register matching rule use %s with the server " +
                    "schema because its matching rule %s conflicts with the " +
                    "matching rule for an existing matching rule use %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_DIT_CONTENT_RULE,
                    "Unable to register DIT content rule %s with the server " +
                    "schema because its structural objectclass %s conflicts " +
                    "with the structural objectclass for an existing DIT " +
                    "content rule %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_NAME_FORM,
                    "Unable to register DIT structure rule %s with the " +
                    "server schema because its name form %s conflicts with " +
                    "the name form for an existing DIT structure rule %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_ID,
                    "Unable to register DIT structure rule %s with the " +
                    "server schema because its rule ID %d conflicts with the " +
                    "rule ID for an existing DIT structure rule %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_NAME_FORM_OC,
                    "Unable to register name form %s with the server schema " +
                    "because its structural objectclass %s conflicts with " +
                    "the structural objectclass for an existing name form %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_NAME_FORM_OID,
                    "Unable to register name form %s with the server schema " +
                    "because its OID %s conflicts with the OID for an " +
                    "existing name form %s");
    registerMessage(MSGID_SCHEMA_CONFLICTING_NAME_FORM_NAME,
                    "Unable to register name form %s with the server schema " +
                    "because its name %s conflicts with the name for an " +
                    "existing name form %s");
    registerMessage(MSGID_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE,
                    "Unable to update the schema element with definition " +
                    "\"%s\" because a circular reference was identified " +
                    "when attempting to rebuild other schema elements " +
                    "dependent upon it");


    registerMessage(MSGID_ADD_OP_INVALID_SYNTAX,
                    "Entry \"%s\" contains a value \"%s\" for attribute %s " +
                    "that is invalid according to the syntax for that " +
                    "attribute:  %s");
    registerMessage(MSGID_ADD_ATTR_IS_OBSOLETE,
                    "Entry \"%s\" cannot be added because it contains " +
                    "attribute type %s which is declared OBSOLETE in the " +
                    "server schema");
    registerMessage(MSGID_ADD_OC_IS_OBSOLETE,
                    "Entry \"%s\" cannot be added because it contains " +
                    "objectclass %s which is declared OBSOLETE in the server " +
                    "schema");
    registerMessage(MSGID_ADD_INVALID_PWPOLICY_DN_SYNTAX,
                    "Entry \"%s\" cannot be added because it contains an " +
                    "invalid password policy subentry DN:  %s");
    registerMessage(MSGID_ADD_NO_SUCH_PWPOLICY,
                    "Entry \"%s\" cannot be added because it references " +
                    "password policy subentry %s that does not exist or does " +
                    "not contain a valid password policy subentry definition");
    registerMessage(MSGID_ADD_ASSERTION_FAILED,
                    "Entry %s cannot be added because the request contained " +
                    "an LDAP assertion control and the associated filter did " +
                    "not match the contents of the provided entry");
    registerMessage(MSGID_ADD_CANNOT_PROCESS_ASSERTION_FILTER,
                    "Entry %s cannot be added because the request contained " +
                    "an LDAP assertion control, but an error occurred while " +
                    "attempting to compare the provided entry against the " +
                    "filter contained in that control:  %s");
    registerMessage(MSGID_ADD_UNSUPPORTED_CRITICAL_CONTROL,
                    "Entry %s cannot be added because the request contained " +
                    "a critical control with OID %s that is not supported by " +
                    "the Directory Server for this type of operation");
    registerMessage(MSGID_ADD_ATTR_IS_NO_USER_MOD,
                    "Entry %s cannot be added because it includes attribute " +
                    "%s which is defined as NO-USER-MODIFICATION in the " +
                    "server schema");
    registerMessage(MSGID_ADD_CANNOT_ADD_ROOT_DSE,
                    "The provided entry cannot be added because it contains " +
                    "a null DN.  This DN is reserved for the root DSE, and " +
                    "that entry may not be added over protocol");
    registerMessage(MSGID_ADD_ENTRY_NOT_SUFFIX,
                    "The provided entry %s cannot be added because it does " +
                    "not have a parent and is not defined as one of the " +
                    "suffixes within the Directory Server");
    registerMessage(MSGID_ADD_CANNOT_LOCK_PARENT,
                    "Entry %s cannot be added because the server failed to " +
                    "obtain a read lock on the parent entry %s after " +
                    "multiple attempts");
    registerMessage(MSGID_ADD_NO_PARENT,
                    "Entry %s cannot be added because its parent entry %s " +
                    "does not exist in the server");
    registerMessage(MSGID_ADD_CANNOT_LOCK_ENTRY,
                    "Entry %s cannot be added because the server failed " +
                    "to obtain a write lock for this entry after multiple " +
                    "attempts");
    registerMessage(MSGID_ADD_MISSING_RDN_ATTRIBUTE,
                    "Entry %s cannot be added because it is missing " +
                    "attribute %s that is contained in the entry's RDN.  " +
                    "All attributes used in the RDN must also be provided in " +
                    "the attribute list for the entry");
    registerMessage(MSGID_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to add entries " +
                    "that include privileges");
    registerMessage(MSGID_ADD_NOOP,
                    "The add operation was not actually performed in the " +
                    "Directory Server backend because the LDAP no-op control " +
                    "was present in the request");
    registerMessage(MSGID_ADD_ERROR_NOTIFYING_CHANGE_LISTENER,
                    "An unexpected error occurred while notifying a change " +
                    "notification listener of an add operation:  %s");
    registerMessage(MSGID_ADD_ERROR_NOTIFYING_PERSISTENT_SEARCH,
                    "An unexpected error occurred while notifying persistent " +
                    "search %s of an add operation:  %s.  The persistent " +
                    "search has been terminated");


    registerMessage(MSGID_COMPARE_OP_NO_SUCH_ATTR,
                    "Entry \"%s\" does not contain any values for attribute " +
                    "\"%s\"");
    registerMessage(MSGID_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS,
                    "Entry \"%s\" does not contain any values for attribute " +
                    "\"%s\" with the specified set of options");


    registerMessage(MSGID_CANCELED_BY_BIND_REQUEST,
                    "Processing on this operation has been canceled because " +
                    "the Directory Server received a bind request on this " +
                    "connection, which requires that all operations in " +
                    "progress to be abandoned");
    registerMessage(MSGID_BIND_OPERATION_UNKNOWN_USER,
                    "Unable to bind to the Directory Server as user %s " +
                    "because no such user exists in the server");
    registerMessage(MSGID_BIND_UNSUPPORTED_CRITICAL_CONTROL,
                    "Unable to process the bind request because it " +
                    "contained a control with OID %s that was marked " +
                    "critical but this control is not supported for the bind " +
                    "operation");
    registerMessage(MSGID_BIND_REJECTED_LOCKDOWN_MODE,
                    "Unable to process the non-root bind because the server " +
                    "is in lockdown mode");
    registerMessage(MSGID_BIND_DN_BUT_NO_PASSWORD,
                    "Unable to process the simple bind request because it " +
                    "contained a bind DN but no password, which is forbidden " +
                    "by the server configuration");
    registerMessage(MSGID_BIND_OPERATION_CANNOT_LOCK_USER,
                    "Unable to process the bind because the server was " +
                    "unable to obtain a read lock on the entry %s");
    registerMessage(MSGID_BIND_OPERATION_NO_PASSWORD,
                    "Unable to bind to the Directory Server as user %s " +
                    "using simple authentication because that user does " +
                    "not have a password");
    registerMessage(MSGID_BIND_OPERATION_UNKNOWN_SASL_MECHANISM,
                    "Unable to process the bind request because it attempted " +
                    "to use an unknown SASL mechanism %s that is not " +
                    "available in the Directory Server");
    registerMessage(MSGID_BIND_OPERATION_UNKNOWN_STORAGE_SCHEME,
                    "Password with unknown storage scheme %s included in " +
                    "user entry %s will be ignored");
    registerMessage(MSGID_BIND_MULTIPLE_USER_SIZE_LIMITS,
                    "There are multiple user-specific size limit values " +
                    "contained in user entry %s.  The default server size " +
                    "limit will be used");
    registerMessage(MSGID_BIND_CANNOT_PROCESS_USER_SIZE_LIMIT,
                    "The user-specific size limit value %s contained in " +
                    "user entry %s could not be parsed as an integer.  The " +
                    "default server size limit will be used");
    registerMessage(MSGID_BIND_MULTIPLE_USER_TIME_LIMITS,
                    "There are multiple user-specific time limit values " +
                    "contained in user entry %s.  The default server time " +
                    "limit will be used");
    registerMessage(MSGID_BIND_CANNOT_PROCESS_USER_TIME_LIMIT,
                    "The user-specific time limit value %s contained in " +
                    "user entry %s could not be parsed as an integer.  The " +
                    "default server time limit will be used");
    registerMessage(MSGID_BIND_MULTIPLE_USER_IDLE_TIME_LIMITS,
                    "There are multiple user-specific idle time limit values " +
                    "contained in user entry %s.  The default server idle " +
                    "time limit will be used");
    registerMessage(MSGID_BIND_CANNOT_PROCESS_USER_IDLE_TIME_LIMIT,
                    "The user-specific idle time limit value %s contained in " +
                    "user entry %s could not be parsed as an integer.  The " +
                    "default server idle time limit will be used");
    registerMessage(MSGID_BIND_PASSWORD_EXPIRING,
                    "The user password is about to expire (time to " +
                    "expiration:  %s)");
    registerMessage(MSGID_BIND_OPERATION_WRONG_PASSWORD,
                    "The password provided by the user did not match any " +
                    "password(s) stored in the user's entry");
    registerMessage(MSGID_BIND_ACCOUNT_TEMPORARILY_LOCKED,
                    "The account has been locked as a result of too many " +
                    "failed authentication attempts (time to unlock:  %s)");
    registerMessage(MSGID_BIND_ACCOUNT_PERMANENTLY_LOCKED,
                    "The account has been locked as a result of too many " +
                    "failed authentication attempts.  It may only be " +
                    "unlocked by an administrator");
    registerMessage(MSGID_BIND_OPERATION_PASSWORD_VALIDATION_EXCEPTION,
                    "An unexpected error occurred while attempting to " +
                    "validate the provided password:  %s");


    registerMessage(MSGID_ABANDON_OP_NO_SUCH_OPERATION,
                    "Unable to abandon the operation with message ID %d " +
                    "because no information is available about that " +
                    "operation.  This could mean that the target operation " +
                    "has already completed or was never requested");
    registerMessage(MSGID_CANCELED_BY_ABANDON_REQUEST,
                    "The operation was canceled because the client issued " +
                    "an abandon request (message ID %d) for this operation");


    registerMessage(MSGID_CANCELED_BY_PREPARSE_DISCONNECT,
                    "The operation was canceled because the client " +
                    "connection was terminated by a pre-parse plugin");
    registerMessage(MSGID_CANCELED_BY_PREOP_DISCONNECT,
                    "The operation was canceled because the client " +
                    "connection was terminated by a pre-operation plugin");
    registerMessage(MSGID_CANCELED_BY_POSTOP_DISCONNECT,
                    "The operation was canceled because the client " +
                    "connection was terminated by a post-operation plugin");
    registerMessage(MSGID_CANCELED_BY_SEARCH_ENTRY_DISCONNECT,
                    "The operation was canceled because the client " +
                    "connection was terminated by a search result entry " +
                    "plugin working on entry %s");
    registerMessage(MSGID_CANCELED_BY_SEARCH_REF_DISCONNECT,
                    "The operation was canceled because the client " +
                    "connection was terminated by a search result reference " +
                    "plugin working on referral %s");


    registerMessage(MSGID_COMPARE_CONFIG_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to access the " +
                    "server configuration");
    registerMessage(MSGID_COMPARE_CANNOT_LOCK_ENTRY,
                    "The Directory Server was unable to obtain a read " +
                    "lock on entry %s after multiple attempts.  Processing " +
                    "on this operation cannot continue");
    registerMessage(MSGID_COMPARE_NO_SUCH_ENTRY,
                    "The specified entry %s does not exist in the " +
                    "Directory Server");
    registerMessage(MSGID_COMPARE_ASSERTION_FAILED,
                    "Cannot perform the compare operation on entry %s " +
                    "because the request contained an LDAP assertion control " +
                    "and the associated filter did not match the contents of " +
                    "the that entry");
    registerMessage(MSGID_COMPARE_CANNOT_PROCESS_ASSERTION_FILTER,
                    "Cannot perform the compare operation on entry %s " +
                    "because the request contained an LDAP assertion " +
                    "control, but an error occurred while attempting to " +
                    "compare the target entry against the filter contained " +
                    "in that control:  %s");
    registerMessage(MSGID_COMPARE_UNSUPPORTED_CRITICAL_CONTROL,
                    "Cannot perform the compare operation on entry %s " +
                    "because the request contained a critical control with " +
                    "OID %s that is not supported by the Directory Server " +
                    "for this type of operation");


    registerMessage(MSGID_DELETE_CANNOT_LOCK_ENTRY,
                    "Entry %s cannot be removed because the server failed " +
                    "to obtain a write lock for this entry after multiple " +
                    "attempts");
    registerMessage(MSGID_DELETE_CANNOT_GET_ENTRY_FOR_ASSERTION,
                    "Entry %s cannot be removed because the delete request " +
                    "contains an LDAP assertion control and an error " +
                    "occurred while trying to retrieve the target entry to " +
                    "compare it against the associated filter:  %s");
    registerMessage(MSGID_DELETE_NO_SUCH_ENTRY_FOR_ASSERTION,
                    "Entry %s cannot be removed because it was determined " +
                    "that the target entry does not exist while attempting " +
                    "to process it against the LDAP assertion control " +
                    "contained in the request");
    registerMessage(MSGID_DELETE_ASSERTION_FAILED,
                    "Entry %s cannot be removed because the request " +
                    "contained an LDAP assertion control and the associated " +
                    "filter did not match the contents of the that entry");
    registerMessage(MSGID_DELETE_CANNOT_PROCESS_ASSERTION_FILTER,
                    "Entry %s cannot be removed because the request " +
                    "contained an LDAP assertion control, but an error " +
                    "occurred while attempting to compare the target entry " +
                    "against the filter contained in that control:  %s");
    registerMessage(MSGID_DELETE_PREREAD_NO_ENTRY,
                    "Entry %s cannot be removed because it was determined " +
                    "that the target entry does not exist while attempting " +
                    "to process it against the LDAP pre-read request control");
    registerMessage(MSGID_DELETE_UNSUPPORTED_CRITICAL_CONTROL,
                    "Entry %s cannot be removed because the request " +
                    "contained a critical control with OID %s that is not " +
                    "supported by the Directory Server for this type of " +
                    "operation");
    registerMessage(MSGID_DELETE_NO_SUCH_ENTRY,
                    "Entry %s does not exist in the Directory Server");
    registerMessage(MSGID_DELETE_HAS_SUB_BACKEND,
                    "Entry %s cannot be removed because the backend that " +
                    "should contain that entry has a subordinate backend " +
                    "with a base DN of %s that is below the target DN");
    registerMessage(MSGID_DELETE_NOOP,
                    "The delete operation was not actually performed in the " +
                    "Directory Server backend because the LDAP no-op control " +
                    "was present in the request");
    registerMessage(MSGID_DELETE_ERROR_NOTIFYING_CHANGE_LISTENER,
                    "An unexpected error occurred while notifying a change " +
                    "notification listener of a delete operation:  %s");
    registerMessage(MSGID_DELETE_ERROR_NOTIFYING_PERSISTENT_SEARCH,
                    "An unexpected error occurred while notifying persistent " +
                    "search %s of a delete operation:  %s.  The persistent " +
                    "search has been terminated");


    registerMessage(MSGID_SEARCH_TIME_LIMIT_EXCEEDED,
                    "The maximum time limit of %d seconds for processing " +
                    "this search operation has expired");
    registerMessage(MSGID_SEARCH_SIZE_LIMIT_EXCEEDED,
                    "This search operation has sent the maximum of %d " +
                    "entries to the client");
    registerMessage(MSGID_SEARCH_CANNOT_GET_ENTRY_FOR_ASSERTION,
                    "The search request cannot be processed because it " +
                    "contains an LDAP assertion control and an error " +
                    "occurred while trying to retrieve the base entry to " +
                    "compare it against the assertion filter:  %s");
    registerMessage(MSGID_SEARCH_NO_SUCH_ENTRY_FOR_ASSERTION,
                    "The search request cannot be processed because it " +
                    "contains an LDAP assertion control but the search base " +
                    "entry does not exist");
    registerMessage(MSGID_SEARCH_ASSERTION_FAILED,
                    "The search request cannot be processed because it " +
                    "contains an LDAP assertion control and the assertion " +
                    "filter did not match the contents of the base entry");
    registerMessage(MSGID_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER,
                    "The search request cannot be processed because it " +
                    "contains an LDAP assertion control, but an error " +
                    "occurred while attempting to compare the base entry " +
                    "against the assertion filter:  %s");
    registerMessage(MSGID_SEARCH_UNSUPPORTED_CRITICAL_CONTROL,
                    "The search request cannot be processed because it " +
                    "contains a critical control with OID %s that is not " +
                    "supported by the Directory Server for this type of " +
                    "operation");
    registerMessage(MSGID_SEARCH_BASE_DOESNT_EXIST,
                    "The entry %s specified as the search base does not " +
                    "exist in the Directory Server");


    registerMessage(MSGID_MODDN_NO_PARENT,
                    "A modify DN operation cannot be performed on entry %s " +
                    "because the new RDN would not have a parent DN");
    registerMessage(MSGID_MODDN_NO_BACKEND_FOR_CURRENT_ENTRY,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because no backend is registered to handle " +
                    "that DN");
    registerMessage(MSGID_MODDN_NO_BACKEND_FOR_NEW_ENTRY,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because no backend is registered to handle " +
                    "the new DN %s");
    registerMessage(MSGID_MODDN_DIFFERENT_BACKENDS,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because the backend holding the current entry " +
                    "is different from the backend used to handle the new DN " +
                    "%s.  Modify DN operations may not span multiple " +
                    "backends");
    registerMessage(MSGID_MODDN_CANNOT_LOCK_CURRENT_DN,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because the server was unable to obtain a " +
                    "write lock for that DN");
    registerMessage(MSGID_MODDN_EXCEPTION_LOCKING_NEW_DN,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because an exception was caught while " +
                    "attempting to obtain a write lock for new DN %s:  %s");
    registerMessage(MSGID_MODDN_CANNOT_LOCK_NEW_DN,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because the server was unable to obtain a " +
                    "write lock for the new DN %s");
    registerMessage(MSGID_MODDN_NO_CURRENT_ENTRY,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because that entry does not exist in the " +
                    "server");
    registerMessage(MSGID_MODDN_ASSERTION_FAILED,
                    "Entry %s cannot be renamed because the request " +
                    "contained an LDAP assertion control and the associated " +
                    "filter did not match the contents of the that entry");
    registerMessage(MSGID_MODDN_CANNOT_PROCESS_ASSERTION_FILTER,
                    "Entry %s cannot be renamed because the request " +
                    "contained an LDAP assertion control, but an error " +
                    "occurred while attempting to compare the target entry " +
                    "against the filter contained in that control:  %s");
    registerMessage(MSGID_MODDN_UNSUPPORTED_CRITICAL_CONTROL,
                    "Entry %s cannot be renamed because the request " +
                    "contained a critical control with OID %s that is not " +
                    "supported by the Directory Server for this type of " +
                    "operation");
    registerMessage(MSGID_MODDN_OLD_RDN_ATTR_IS_NO_USER_MOD,
                    "Entry %s cannot be renamed because the current DN " +
                    "includes attribute %s which is defined as " +
                    "NO-USER-MODIFICATION in the server schema and the " +
                    "deleteOldRDN flag was set in the modify DN request");
    registerMessage(MSGID_MODDN_NEW_RDN_ATTR_IS_NO_USER_MOD,
                    "Entry %s cannot be renamed because the new RDN " +
                    "includes attribute %s which is defined as " +
                    "NO-USER-MODIFICATION in the server schema, and the " +
                    "target value for that attribute is not already " +
                    "included in the entry");
    registerMessage(MSGID_MODDN_VIOLATES_SCHEMA,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because the change would have violated the " +
                    "server schema:  %s");
    registerMessage(MSGID_MODDN_NEWRDN_ATTR_IS_OBSOLETE,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because the new RDN includes attribute type " +
                    "%s which is declared OBSOLETE in the server schema");
    registerMessage(MSGID_MODDN_PREOP_INCREMENT_NO_ATTR,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because a pre-operation plugin attempted to " +
                    "increment attribute %s but that attribute does not " +
                    "exist in the target entry");
    registerMessage(MSGID_MODDN_PREOP_INCREMENT_MULTIPLE_VALUES,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because a pre-operation plugin attempted to " +
                    "increment attribute %s but that attribute has multiple " +
                    "values in the target entry");
    registerMessage(MSGID_MODDN_PREOP_INCREMENT_VALUE_NOT_INTEGER,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because a pre-operation plugin attempted to " +
                    "increment attribute %s but the value of that attribute " +
                    "is not an integer");
    registerMessage(MSGID_MODDN_PREOP_INCREMENT_NO_AMOUNT,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because a pre-operation plugin attempted to " +
                    "increment attribute %s but no increment amount was " +
                    "provided");
    registerMessage(MSGID_MODDN_PREOP_INCREMENT_MULTIPLE_AMOUNTS,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because a pre-operation plugin attempted to " +
                    "increment attribute %s but multiple increment amount " +
                    "values were provided");
    registerMessage(MSGID_MODDN_PREOP_INCREMENT_AMOUNT_NOT_INTEGER,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because a pre-operation plugin attempted to " +
                    "increment attribute %s but the increment amount value " +
                    "was not an integer");
    registerMessage(MSGID_MODDN_PREOP_VIOLATES_SCHEMA,
                    "The modify DN operation for entry %s cannot be " +
                    "performed because a pre-operation plugin modified the " +
                    "entry in a way that caused it to violate the server " +
                    "schema:  %s");
    registerMessage(MSGID_MODDN_NOOP,
                    "The modify DN operation was not actually performed in " +
                    "the Directory Server backend because the LDAP no-op " +
                    "control was present in the request");
    registerMessage(MSGID_MODDN_ERROR_NOTIFYING_CHANGE_LISTENER,
                    "An unexpected error occurred while notifying a change " +
                    "notification listener of a modify DN operation:  %s");
    registerMessage(MSGID_MODDN_ERROR_NOTIFYING_PERSISTENT_SEARCH,
                    "An unexpected error occurred while notifying persistent " +
                    "search %s of a modify DN operation:  %s.  The " +
                    "persistent search has been terminated");


    registerMessage(MSGID_MODIFY_NO_MODIFICATIONS,
                    "Entry %s cannot be updated because the request did not " +
                    "contain any modifications");
    registerMessage(MSGID_MODIFY_CANNOT_LOCK_ENTRY,
                    "Entry %s cannot be modified because the server failed " +
                    "to obtain a write lock for this entry after multiple " +
                    "attempts");
    registerMessage(MSGID_MODIFY_NO_SUCH_ENTRY,
                    "Entry %s cannot be modified because no such entry " +
                    "exists in the server");
    registerMessage(MSGID_MODIFY_ASSERTION_FAILED,
                    "Entry %s cannot be modified because the request " +
                    "contained an LDAP assertion control and the associated " +
                    "filter did not match the contents of the that entry");
    registerMessage(MSGID_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER,
                    "Entry %s cannot be modified because the request " +
                    "contained an LDAP assertion control, but an error " +
                    "occurred while attempting to compare the target entry " +
                    "against the filter contained in that control:  %s");
    registerMessage(MSGID_MODIFY_UNSUPPORTED_CRITICAL_CONTROL,
                    "Entry %s cannot be modified because the request " +
                    "contained a critical control with OID %s that is not " +
                    "supported by the Directory Server for this type of " +
                    "operation");
    registerMessage(MSGID_MODIFY_PWRESET_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to reset user " +
                    "passwords");
    registerMessage(MSGID_MODIFY_MUST_CHANGE_PASSWORD,
                    "You must change your password before you will be " +
                    "allowed to perform any other operations");
    registerMessage(MSGID_MODIFY_ATTR_IS_NO_USER_MOD,
                    "Entry %s cannot be modified because the modification " +
                    "attempted to update attribute %s which is defined as " +
                    "NO-USER-MODIFICATION in the server schema");
    registerMessage(MSGID_MODIFY_ATTR_IS_OBSOLETE,
                    "Entry %s cannot be modified because the modification " +
                    "attempted to set one or more new values for attribute " +
                    "%s which is marked OBSOLETE in the server schema");
    registerMessage(MSGID_MODIFY_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to modify the " +
                    "set of privileges contained in an entry");
    registerMessage(MSGID_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS,
                    "Attributes used to hold user passwords are not allowed " +
                    "to have any attribute options");
    registerMessage(MSGID_MODIFY_NO_USER_PW_CHANGES,
                    "Users are not allowed to change their own passwords");
    registerMessage(MSGID_MODIFY_REQUIRE_SECURE_CHANGES,
                    "Password changes must be performed over a secure " +
                    "authentication channel");
    registerMessage(MSGID_MODIFY_WITHIN_MINIMUM_AGE,
                    "The password cannot be changed because it has not been " +
                    "long enough since the last password change");
    registerMessage(MSGID_MODIFY_MULTIPLE_VALUES_NOT_ALLOWED,
                    "Multiple password values are not allowed in user " +
                    "entries");
    registerMessage(MSGID_MODIFY_NO_PREENCODED_PASSWORDS,
                    "User passwords may not be provided in pre-encoded form");
    registerMessage(MSGID_MODIFY_PASSWORD_EXISTS,
                    "The specified password value already exists in the " +
                    "user entry");
    registerMessage(MSGID_MODIFY_NO_EXISTING_VALUES,
                    "The user entry does not have any existing passwords to " +
                    "remove");
    registerMessage(MSGID_MODIFY_CANNOT_DECODE_PW,
                    "An error occurred while attempting to decode an " +
                    "existing user password:  %s");
    registerMessage(MSGID_MODIFY_INVALID_PASSWORD,
                    "The provided user password does not match any password " +
                    "in the user's entry");
    registerMessage(MSGID_MODIFY_INVALID_MOD_TYPE_FOR_PASSWORD,
                    "Invalid modification type %s attempted on password " +
                    "attribute %s");
    registerMessage(MSGID_MODIFY_INVALID_DISABLED_VALUE,
                    "Invalid value provided for operational attribute %s:  " +
                    "%s");
    registerMessage(MSGID_MODIFY_ADD_NO_VALUES,
                    "Entry %s cannot be modified because the modification " +
                    "contained an add component for attribute %s but no " +
                    "values were provided");
    registerMessage(MSGID_MODIFY_ADD_INVALID_SYNTAX,
                    "When attempting to modify entry %s to add one or more " +
                    "values for attribute %s, value \"%s\" was found to be " +
                    "invalid according to the associated syntax:  %s");
    registerMessage(MSGID_MODIFY_ADD_DUPLICATE_VALUE,
                    "Entry %s cannot be modified because it would have " +
                    "resulted in one or more duplicate values for attribute " +
                    "%s:  %s");
    registerMessage(MSGID_MODIFY_DELETE_RDN_ATTR,
                    "Entry %s cannot be modified because the change to " +
                    "attribute %s would have removed a value used in the RDN");
    registerMessage(MSGID_MODIFY_DELETE_MISSING_VALUES,
                    "Entry %s cannot be modified because the attempt to " +
                    "update attribute %s would have removed one or more " +
                    "values from the attribute that were not present:  %s");
    registerMessage(MSGID_MODIFY_DELETE_NO_SUCH_ATTR,
                    "Entry %s cannot be modified because an attempt was made " +
                    "to remove one or more values from attribute %s but this " +
                    "attribute is not present in the entry");
    registerMessage(MSGID_MODIFY_REPLACE_INVALID_SYNTAX,
                    "When attempting to modify entry %s to replace the set " +
                    "of values for attribute %s, value \"%s\" was found to " +
                    "be invalid according to the associated syntax:  %s");
    registerMessage(MSGID_MODIFY_INCREMENT_RDN,
                    "Entry %s cannot be modified because an attempt was made " +
                    "to increment the value of attribute %s which is used as " +
                    "an RDN attribute for the entry");
    registerMessage(MSGID_MODIFY_INCREMENT_REQUIRES_VALUE,
                    "Entry %s cannot be modified because an attempt was made " +
                    "to increment the value of attribute %s but the request " +
                    "did not include a value for that attribute specifying " +
                    "the amount by which to increment the value");
    registerMessage(MSGID_MODIFY_INCREMENT_REQUIRES_SINGLE_VALUE,
                    "Entry %s cannot be modified because an attempt was made " +
                    "to increment the value of attribute %s but the request " +
                    "contained multiple values, where only a single integer " +
                    "value is allowed");
    registerMessage(MSGID_MODIFY_INCREMENT_PROVIDED_VALUE_NOT_INTEGER,
                    "Entry %s cannot be modified because an attempt was made " +
                    "to increment the value of attribute %s but the value " +
                    "\"%s\" contained in the request could not be parsed as " +
                    "an integer");
    registerMessage(MSGID_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE,
                    "Entry %s cannot be modified because an attempt was made " +
                    "to increment the value of attribute %s but that " +
                    "attribute did not have any values in the target entry");
    registerMessage(MSGID_MODIFY_PW_CHANGE_REQUIRES_CURRENT_PW,
                    "The password policy requires that user password changes " +
                    "include the current password in the request");
    registerMessage(MSGID_MODIFY_MULTIPLE_PASSWORDS_NOT_ALLOWED,
                    "The password change would result in multiple password " +
                    "values in the user entry, which is not allowed");
    registerMessage(MSGID_MODIFY_PW_VALIDATION_FAILED,
                    "The provided password value was rejected by a password " +
                    "validator:  %s");
    registerMessage(MSGID_MODIFY_PW_IN_HISTORY,
                    "The provided new password was found in the password " +
                    "history for the user");
    registerMessage(MSGID_MODIFY_INCREMENT_REQUIRES_INTEGER_VALUE,
                    "Entry %s cannot be modified because an attempt was " +
                    "made to increment the value of attribute %s but the " +
                    "value \"%s\" could not be parsed as an integer");
    registerMessage(MSGID_MODIFY_VIOLATES_SCHEMA,
                    "Entry %s cannot not be modified because the resulting " +
                    "entry would have violated the server schema:  %s");
    registerMessage(MSGID_MODIFY_NO_BACKEND_FOR_ENTRY,
                    "Entry %s cannot be modified because there is no backend " +
                    "registered to handle operations for that entry");
    registerMessage(MSGID_MODIFY_NOOP,
                    "The modify operation was not actually performed in the " +
                    "Directory Server backend because the LDAP no-op control " +
                    "was present in the request");
    registerMessage(MSGID_MODIFY_PASSWORD_CHANGED,
                    "The user password has been changed");
    registerMessage(MSGID_MODIFY_PASSWORD_RESET,
                    "The user password has been administratively reset");
    registerMessage(MSGID_MODIFY_ACCOUNT_ENABLED,
                    "The user account has been administratively enabled");
    registerMessage(MSGID_MODIFY_ACCOUNT_DISABLED,
                    "The user account has been administratively disabled");
    registerMessage(MSGID_MODIFY_ACCOUNT_UNLOCKED,
                    "The user account has been administratively unlocked");
    registerMessage(MSGID_MODIFY_ERROR_NOTIFYING_CHANGE_LISTENER,
                    "An unexpected error occurred while notifying a change " +
                    "notification listener of a modify operation:  %s");
    registerMessage(MSGID_MODIFY_ERROR_NOTIFYING_PERSISTENT_SEARCH,
                    "An unexpected error occurred while notifying persistent " +
                    "search %s of a modify operation:  %s.  The persistent " +
                    "search has been terminated");


    registerMessage(MSGID_EXTENDED_NO_HANDLER,
                    "There is no extended operation handler registered with " +
                    "the Directory Server for handling extended operations " +
                    "with a request OID of %s");
    registerMessage(MSGID_EXTENDED_UNSUPPORTED_CRITICAL_CONTROL,
                    "Unable to process the request for extended operation %s " +
                    "because it contained an unsupported critical control " +
                    "with OID %s");


    registerMessage(MSGID_CONNHANDLER_CLOSED_BY_SHUTDOWN,
                    "The Directory Server is shutting down");
    registerMessage(MSGID_CONNHANDLER_CLOSED_BY_DISABLE,
                    "The connection handler that accepted this connection " +
                    "has been disabled");
    registerMessage(MSGID_CONNHANDLER_CLOSED_BY_DELETE,
                    "The connection handler that accepted this connection " +
                    "has been removed from the server");


    registerMessage(MSGID_DSCORE_TOOL_DESCRIPTION,
                    "This utility may be used to start the Directory Server, " +
                    "as well as to obtain the server version and other forms " +
                    "of general server information");
    registerMessage(MSGID_DSCORE_DESCRIPTION_CONFIG_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "to use as the Directory Server configuration handler");
    registerMessage(MSGID_DSCORE_DESCRIPTION_CONFIG_FILE,
                    "Specifies the path to the file containing the " +
                    "information needed by the configuration handler to " +
                    "obtain the Directory Server configuration");
    registerMessage(MSGID_DSCORE_DESCRIPTION_CHECK_STARTABILITY,
                    "Used to determine whether a server can be started or not" +
                    "and the mode to be used to start it");
    registerMessage(MSGID_DSCORE_DESCRIPTION_WINDOWS_NET_START,
                    "Used by the window service code to inform that start-ds "+
                    "is being called from the window services after a call "+
                    "to net start");
    registerMessage(MSGID_DSCORE_DESCRIPTION_VERSION,
                    "Display Directory Server version information");
    registerMessage(MSGID_DSCORE_DESCRIPTION_FULLVERSION,
                    "Display extended Directory Server version information");
    registerMessage(MSGID_DSCORE_DESCRIPTION_SYSINFO,
                    "Display general system information");
    registerMessage(MSGID_DSCORE_DESCRIPTION_DUMPMESSAGES,
                    "Dump a list of all defined messages");
    registerMessage(MSGID_DSCORE_DESCRIPTION_NODETACH,
                    "Do not detach from the terminal and continue running in " +
                    "the foreground");
    registerMessage(MSGID_DSCORE_DESCRIPTION_USAGE,
                    "Display this usage information");
    registerMessage(MSGID_DSCORE_CANNOT_INITIALIZE_ARGS,
                    "An error occurred while attempting to initialize the " +
                    "command-line arguments:  %s");
    registerMessage(MSGID_DSCORE_ERROR_PARSING_ARGS,
                    "An error occurred while attempting to parse the " +
                    "provided set of command line arguments:  %s");
    registerMessage(MSGID_DSCORE_ERROR_NODETACH_AND_WINDOW_SERVICE,
                    "OpenDS is configured to run as a window service and it "+
                    "cannot run in no-detach mode");
    registerMessage(MSGID_DSCORE_CANNOT_BOOTSTRAP,
                    "An error occurred while attempting to bootstrap the " +
                    "Directory Server:  %s");
    registerMessage(MSGID_DSCORE_CANNOT_START,
                    "An error occurred while trying to start the Directory " +
                    "Server:  %s");


    registerMessage(MSGID_BACKUPINFO_NO_DELIMITER,
                    "The line \"%s\" associated with the backup information " +
                    "in directory %s could not be parsed because it did not " +
                    "contain an equal sign to delimit the property name from " +
                    "the value");
    registerMessage(MSGID_BACKUPINFO_NO_NAME,
                    "The line \"%s\" associated with the backup information " +
                    "in directory %s could not be parsed because it did not " +
                    "include a property name");
    registerMessage(MSGID_BACKUPINFO_MULTIPLE_BACKUP_IDS,
                    "The backup information structure in directory %s could " +
                    "not be parsed because it contained multiple backup IDs " +
                    "(%s and %s)");
    registerMessage(MSGID_BACKUPINFO_UNKNOWN_PROPERTY,
                    "The backup information structure in directory %s could " +
                    "not be parsed because it contained an unknown property " +
                    "%s with value %s");
    registerMessage(MSGID_BACKUPINFO_CANNOT_DECODE,
                    "An unexpected error occurred while trying to decode a " +
                    "backup information structure in directory %s:  %s");
    registerMessage(MSGID_BACKUPINFO_NO_BACKUP_ID,
                    "Unable to decode a backup information structure in " +
                    "directory %s because the structure did not include a " +
                    "backup ID");
    registerMessage(MSGID_BACKUPINFO_NO_BACKUP_DATE,
                    "The backup information structure with backup ID %s in " +
                    "Unable to decode a backup information structure in " +
                    "directory %s was not valid because it did not contain " +
                    "the backup date");


    registerMessage(MSGID_BACKUPDIRECTORY_ADD_DUPLICATE_ID,
                    "Cannot add a backup with ID %s to backup directory %s " +
                    "because another backup already exists with that ID");
    registerMessage(MSGID_BACKUPDIRECTORY_NO_SUCH_BACKUP,
                    "Cannot remove backup %s from backup directory %s " +
                    "because no backup with that ID exists in that directory");
    registerMessage(MSGID_BACKUPDIRECTORY_UNRESOLVED_DEPENDENCY,
                    "Cannot remove backup %s from backup directory %s " +
                    "because it is listed as a dependency for backup %s");
    registerMessage(MSGID_BACKUPDIRECTORY_CANNOT_CREATE_DIRECTORY,
                    "Backup directory %s does not exist and an error " +
                    "occurred while attempting to create it:  %s");
    registerMessage(MSGID_BACKUPDIRECTORY_NOT_DIRECTORY,
                    "The path %s specifies as a backup directory exists but " +
                    "does not reference a directory");
    registerMessage(MSGID_BACKUPDIRECTORY_CANNOT_DELETE_SAVED_DESCRIPTOR,
                    "An error occurred while trying to remove saved backup " +
                    "descriptor file %s:  %s.  The new backup descriptor " +
                    "has been written to %s but will not be used until it is " +
                    "manually renamed to %s");
    registerMessage(MSGID_BACKUPDIRECTORY_CANNOT_RENAME_CURRENT_DESCRIPTOR,
                    "An error occurred while trying to rename the current " +
                    "backup descriptor file %s to %s:  %s.  The new backup " +
                    "descriptor has been written to %s but will not be used " +
                    "until it is manually renamed to %s");
    registerMessage(MSGID_BACKUPDIRECTORY_CANNOT_RENAME_NEW_DESCRIPTOR,
                    "An error occurred while trying to rename the new backup " +
                    "descriptor file %s to %s:  %s.  The new backup " +
                    "descriptor will not be used until it is manually " +
                    "renamed");
    registerMessage(MSGID_BACKUPDIRECTORY_NO_DESCRIPTOR_FILE,
                    "No backup directory descriptor file was found at %s");
    registerMessage(MSGID_BACKUPDIRECTORY_CANNOT_READ_CONFIG_ENTRY_DN,
                    "The backup descriptor file %s is invalid because the " +
                    "first line should have contained the DN of the backend " +
                    "configuration entry but was blank");
    registerMessage(MSGID_BACKUPDIRECTORY_FIRST_LINE_NOT_DN,
                    "The backup descriptor file %s is invalid because the " +
                    "first line of the file was \"%s\", but the DN of the " +
                    "backend configuration entry was expected");
    registerMessage(MSGID_BACKUPDIRECTORY_CANNOT_DECODE_DN,
                    "An error occurred while trying to decode the value " +
                    "\"%s\" read from the first line of %s as the DN of " +
                    "the backend configuration entry:  %s");


    registerMessage(MSGID_FILELOCKER_LOCK_SHARED_REJECTED_BY_EXCLUSIVE,
                    "The attempt to obtain a shared lock on file %s was " +
                    "rejected because an exclusive lock was already held on " +
                    "that file");
    registerMessage(MSGID_FILELOCKER_LOCK_SHARED_FAILED_CREATE,
                    "The attempt to obtain a shared lock on file %s was " +
                    "rejected because the attempt to create the lock file " +
                    "failed:  %s");
    registerMessage(MSGID_FILELOCKER_LOCK_SHARED_FAILED_OPEN,
                    "The attempt to obtain a shared lock on file %s was " +
                    "rejected because the attempt to open the lock file " +
                    "failed:  %s");
    registerMessage(MSGID_FILELOCKER_LOCK_SHARED_FAILED_LOCK,
                    "The attempt to obtain a shared lock on file %s was " +
                    "rejected because an error occurred while attempting to " +
                    "acquire the lock:  %s");
    registerMessage(MSGID_FILELOCKER_LOCK_SHARED_NOT_GRANTED,
                    "The shared lock requested for file %s was not granted, " +
                    "which indicates that another process already holds an "+
                    "exclusive lock on that file");
    registerMessage(MSGID_FILELOCKER_LOCK_EXCLUSIVE_REJECTED_BY_EXCLUSIVE,
                    "The attempt to obtain an exclusive lock on file %s was " +
                    "rejected because an exclusive lock was already held on " +
                    "that file");
    registerMessage(MSGID_FILELOCKER_LOCK_EXCLUSIVE_REJECTED_BY_SHARED,
                    "The attempt to obtain an exclusive lock on file %s was " +
                    "rejected because a shared lock was already held on that " +
                    "file");
    registerMessage(MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_CREATE,
                    "The attempt to obtain an exclusive lock on file %s was " +
                    "rejected because the attempt to create the lock file " +
                    "failed:  %s");
    registerMessage(MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_OPEN,
                    "The attempt to obtain an exclusive lock on file %s was " +
                    "rejected because the attempt to open the lock file " +
                    "failed:  %s");
    registerMessage(MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_LOCK,
                    "The attempt to obtain an exclusive lock on file %s was " +
                    "rejected because an error occurred while attempting to " +
                    "acquire the lock:  %s");
    registerMessage(MSGID_FILELOCKER_LOCK_EXCLUSIVE_NOT_GRANTED,
                    "The exclusive lock requested for file %s was not " +
                    "granted, which indicates that another process already " +
                    "holds a shared or exclusive lock on that file");
    registerMessage(MSGID_FILELOCKER_UNLOCK_EXCLUSIVE_FAILED_RELEASE,
                    "The attempt to release the exclusive lock held on %s " +
                    "failed:  %s");
    registerMessage(MSGID_FILELOCKER_UNLOCK_SHARED_FAILED_RELEASE,
                    "The attempt to release the shared lock held on %s " +
                    "failed:  %s");
    registerMessage(MSGID_FILELOCKER_UNLOCK_UNKNOWN_FILE,
                    "The attempt to release the lock held on %s failed " +
                    "because no record of a lock on that file was found");
    registerMessage(MSGID_ADD_ENTRY_ALREADY_EXISTS,
                    "The entry %s cannot be added because an entry with " +
                    "that name already exists");


    registerMessage(MSGID_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED,
                    "An error occurred during conflict resolution " +
                    "synchronization processing for the add operation with " +
                    "connection ID %d and operation ID %d:  %s");
    registerMessage(MSGID_ADD_SYNCH_PREOP_FAILED,
                    "An error occurred during preoperation synchronization " +
                    "processing for the add operation with connection ID %d " +
                    "and operation ID %d:  %s");
    registerMessage(MSGID_ADD_SYNCH_POSTOP_FAILED,
                    "An error occurred during postoperation synchronization " +
                    "processing for the add operation with connection ID %d " +
                    "and operation ID %d:  %s");
    registerMessage(MSGID_DELETE_SYNCH_CONFLICT_RESOLUTION_FAILED,
                    "An error occurred during conflict resolution " +
                    "synchronization processing for the delete operation " +
                    "with connection ID %d and operation ID %d:  %s");
    registerMessage(MSGID_DELETE_SYNCH_PREOP_FAILED,
                    "An error occurred during preoperation synchronization " +
                    "processing for the delete operation with connection ID " +
                    "%d and operation ID %d:  %s");
    registerMessage(MSGID_DELETE_SYNCH_POSTOP_FAILED,
                    "An error occurred during postoperation synchronization " +
                    "processing for the delete operation with connection ID " +
                    "%d and operation ID %d:  %s");
    registerMessage(MSGID_MODIFY_SYNCH_CONFLICT_RESOLUTION_FAILED,
                    "An error occurred during conflict resolution " +
                    "synchronization processing for the modify operation " +
                    "with connection ID %d and operation ID %d:  %s");
    registerMessage(MSGID_MODIFY_SYNCH_PREOP_FAILED,
                    "An error occurred during preoperation synchronization " +
                    "processing for the modify operation with connection ID " +
                    "%d and operation ID %d:  %s");
    registerMessage(MSGID_MODIFY_SYNCH_POSTOP_FAILED,
                    "An error occurred during postoperation synchronization " +
                    "processing for the modify operation with connection ID " +
                    "%d and operation ID %d:  %s");
    registerMessage(MSGID_MODDN_SYNCH_CONFLICT_RESOLUTION_FAILED,
                    "An error occurred during conflict resolution " +
                    "synchronization processing for the modify DN operation " +
                    "with connection ID %d and operation ID %d:  %s");
    registerMessage(MSGID_MODDN_SYNCH_PREOP_FAILED,
                    "An error occurred during preoperation synchronization " +
                    "processing for the modify DN operation with connection " +
                    "ID %d and operation ID %d:  %s");
    registerMessage(MSGID_MODDN_SYNCH_POSTOP_FAILED,
                    "An error occurred during postoperation synchronization " +
                    "processing for the modify DN operation with connection " +
                    "ID %d and operation ID %d:  %s");


    registerMessage(MSGID_ADD_SERVER_READONLY,
                    "Unable to add entry %s because the Directory Server " +
                    "is configured in read-only mode");
    registerMessage(MSGID_ADD_BACKEND_READONLY,
                    "Unable to add entry %s because the backend that should " +
                    "hold that entry is configured in read-only mode");
    registerMessage(MSGID_DELETE_SERVER_READONLY,
                    "Unable to delete entry %s because the Directory Server " +
                    "is configured in read-only mode");
    registerMessage(MSGID_DELETE_BACKEND_READONLY,
                    "Unable to delete entry %s because the backend that " +
                    "holds that entry is configured in read-only mode");
    registerMessage(MSGID_MODIFY_SERVER_READONLY,
                    "Unable to modify entry %s because the Directory Server " +
                    "is configured in read-only mode");
    registerMessage(MSGID_MODIFY_BACKEND_READONLY,
                    "Unable to modify entry %s because the backend that " +
                    "holds that entry is configured in read-only mode");
    registerMessage(MSGID_MODDN_SERVER_READONLY,
                    "Unable to rename entry %s because the Directory Server " +
                    "is configured in read-only mode");
    registerMessage(MSGID_MODDN_BACKEND_READONLY,
                    "Unable to rename entry %s because the backend that " +
                    "holds that entry is configured in read-only mode");


    registerMessage(MSGID_PWPOLICY_NO_PASSWORD_ATTRIBUTE,
                    "The password policy configuration entry \"%s\" does not " +
                    "contain a value for attribute " +
                    ATTR_PWPOLICY_PASSWORD_ATTRIBUTE + ", which specifies " +
                    "the attribute to hold user passwords");
    registerMessage(MSGID_PWPOLICY_NO_DEFAULT_STORAGE_SCHEMES,
                    "The password policy configuration entry \"%s\" does not " +
                    "contain any values for attribute " +
                    ATTR_PWPOLICY_DEFAULT_SCHEME + ", which specifies " +
                    "the set of default password storage schemes");
    registerMessage(MSGID_PWPOLICY_WARNING_INTERVAL_LARGER_THAN_MAX_AGE,
                    "The password policy configuration entry \"%s\" is " +
                    "invalid because if a maximum password age is " +
                    "configured, then the password expiration warning " +
                    "interval must be shorter than the maximum password age");
    registerMessage(MSGID_PWPOLICY_MIN_AGE_PLUS_WARNING_GREATER_THAN_MAX_AGE,
                    "The password policy configuration entry \"%s\" is " +
                    "invalid because if both a minimum password age and a " +
                    "maximum password age are configured, then the sum of " +
                    "the minimum password age and the password expiration " +
                    "warning interval must be shorter than the maximum " +
                    "password age");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_PW_ATTR,
                    "Specifies the attribute type used to hold user " +
                    "passwords.  This attribute type must be defined in the " +
                    "server schema.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_UNDEFINED_PASSWORD_ATTRIBUTE,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because the " +
                    "specified password attribute \"%s\" is not defined in " +
                    "the server schema");
    registerMessage(MSGID_PWPOLICY_INVALID_PASSWORD_ATTRIBUTE_SYNTAX,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because the " +
                    "specified password attribute \"%s\" has a syntax OID of " +
                    "%s.  The password attribute must have a syntax OID of " +
                    "either " + SYNTAX_USER_PASSWORD_OID + " (for the user " +
                    "password syntax) or " + SYNTAX_AUTH_PASSWORD_OID +
                    " (for the authentication password syntax)");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_ATTRIBUTE,
                    "An error occurred while attempting to determine the " +
                    "value of attribute " + ATTR_PWPOLICY_PASSWORD_ATTRIBUTE +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_DEFAULT_STORAGE_SCHEMES,
                    "Specifies the password storage scheme (or set of " +
                    "schemes) that will be used to encode clear-text " +
                    "passwords.  If multiple default storage schemes are " +
                    "defined for a password policy, then the same password " +
                    "will be encoded using all of those schemes.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_NO_SUCH_DEFAULT_SCHEME,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because it " +
                    "references a default password storage scheme \"%s\" " +
                    "that is not defined in the server configuration");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_DEFAULT_STORAGE_SCHEMES,
                    "An error occurred while attempting to determine the " +
                    "values for attribute " + ATTR_PWPOLICY_DEFAULT_SCHEME +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_DEPRECATED_STORAGE_SCHEMES,
                    "Specifies the password storage scheme (or set of " +
                    "schemes) that should be considered deprecated.  If an " +
                    "authenticating user has a password encoded with one of " +
                    "these schemes, those passwords will be removed and " +
                    "replaced with passwords encoded using the default " +
                    "schemes.  Changes to this configuration attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_DEPRECATED_STORAGE_SCHEMES,
                    "An error occurred while attempting to determine the " +
                    "values for attribute " + ATTR_PWPOLICY_DEPRECATED_SCHEME +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_PASSWORD_VALIDATORS,
                    "Specifies the DN(s) of the password validator(s) that " +
                    "should be used with the associated password storage " +
                    "scheme.  Changes to this configuration attribute will " +
                    "take effect immediately");
    registerMessage(MSGID_PWPOLICY_NO_SUCH_VALIDATOR,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because it " +
                    "references a password validator \"%s\" that is not " +
                    "defined in the server configuration");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_VALIDATORS,
                    "An error occurred while attempting to determine the " +
                    "values for attribute " + ATTR_PWPOLICY_PASSWORD_VALIDATOR +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_NOTIFICATION_HANDLERS,
                    "Specifies the DN(s) of the account status notification " +
                    "handler(s) that should be used with the associated " +
                    "password storage scheme.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_NO_SUCH_NOTIFICATION_HANDLER,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because it " +
                    "references account status notification handler \"%s\" " +
                    "that is not defined in the server configuration");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_NOTIFICATION_HANDLERS,
                    "An error occurred while attempting to determine the " +
                    "values for attribute " +
                    ATTR_PWPOLICY_NOTIFICATION_HANDLER +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_ALLOW_USER_PW_CHANGES,
                    "Indicates whether users will be allowed to change " +
                    "their own passwords.  This check is made in addition " +
                    "to access control evaluation, and therefore both must " +
                    "allow the password change for it to occur.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_USER_PW_CHANGES,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " + ATTR_PWPOLICY_ALLOW_USER_CHANGE +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CURRENT_PW,
                    "Indicates whether user password changes will be " +
                    "required to use the password modify extended operation " +
                    "and include the user's current password before the " +
                    "change will be allowed.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CURRENT_PW,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " + ATTR_PWPOLICY_ALLOW_USER_CHANGE +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_ADD,
                    "Indicates whether users will be forced to change their " +
                    "passwords upon first authenticating to the Directory " +
                    "Server after their account has been created.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_FORCE_CHANGE_ON_ADD,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_FORCE_CHANGE_ON_ADD +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_FORCE_CHANGE_ON_RESET,
                    "Indicates whether users will be forced to change their " +
                    "passwords if they are reset by an administrator.  " +
                    "For this purpose, anyone with permission to change a " +
                    "given user's password other than that user will be " +
                    "considered an administrator.  Changes to this " +
                    "configuration attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_FORCE_CHANGE_ON_RESET,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_FORCE_CHANGE_ON_RESET +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_SKIP_ADMIN_VALIDATION,
                    "Indicates whether passwords set by administrators (in " +
                    "add, modify, or password modify operations) will be " +
                    "allowed to bypass the password validation process that " +
                    "will be required for user password changes.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_SKIP_ADMIN_VALIDATION,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_SKIP_ADMIN_VALIDATION +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_PASSWORD_GENERATOR,
                    "Specifies the DN of the configuration entry that " +
                    "references the password generator for use with the " +
                    "associated password policy.  This will be used in " +
                    "conjunction with the password modify extended operation " +
                    "to generate a new password for a user when none was " +
                    "provided in the request.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_NO_SUCH_GENERATOR,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because it " +
                    "references password generator \"%s\" that is not " +
                    "defined in the server configuration");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_PASSWORD_GENERATOR,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " + ATTR_PWPOLICY_PASSWORD_GENERATOR +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_AUTH,
                    "Indicates whether users with the associated password " +
                    "policy will be required to authenticate in a secure " +
                    "manner.  This could mean either using a secure " +
                    "communication channel between the client and the " +
                    "server, or using a SASL mechanism that does not expose " +
                    "the credentials.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_SECURE_AUTH,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_REQUIRE_SECURE_CHANGES,
                    "Indicates whether users with the associated password " +
                    "policy will be required to change their password in " +
                    "a secure manner that does not expose the credentials.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_SECURE_CHANGES,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_ALLOW_MULTIPLE_PW_VALUES,
                    "Indicates whether user entries will be allowed to have " +
                    "multiple distinct values for the password attribute.  " +
                    "This is potentially dangerous because many mechanisms " +
                    "used to change the password do not work well with such " +
                    "a configuration.  If multiple password values are " +
                    "allowed, then any of them may be used to authenticate, " +
                    "and they will all be subject to the same policy " +
                    "constraints.  Changes to this configuration attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_MULTIPLE_PW_VALUES,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_ALLOW_PREENCODED,
                    "Indicates whether users will be allowed to change their " +
                    "passwords by providing a pre-encoded value.  This can " +
                    "cause a security risk because the clear-text version of " +
                    "the password is not known and therefore validation " +
                    "checks cannot be applied to it.  Changes to this " +
                    "configuration attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_PREENCODED,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_MIN_AGE,
                    "Specifies the minimum length of time that must pass " +
                    "after a password change before the user will be allowed " +
                    "to change the password again.  The value of this " +
                    "attribute should be an integer followed by a unit of " +
                    "seconds, minutes, hours, days, or weeks.  This setting " +
                    "can be used to prevent users from changing their " +
                    "passwords repeatedly over a short period of time to " +
                    "flush and old password from the history so that it may " +
                    "be re-used.  Changes to this configuration attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_MIN_AGE,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_MINIMUM_PASSWORD_AGE +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_MAX_AGE,
                    "Specifies the maximum length of time that a user may " +
                    "continue using the same password before it must be " +
                    "changed (i.e., the password expiration interval).  The " +
                    "value of this attribute should be an integer followed " +
                    "by a unit of seconds, minutes, hours, days, or weeks.  " +
                    "A value of 0 seconds will disable password expiration.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_MAX_AGE,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_MAXIMUM_PASSWORD_AGE +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_MAX_RESET_AGE,
                    "Specifies the maximum length of time that users have to " +
                    "change passwords after they have been reset by an " +
                    "administrator before they become locked.  The value " +
                    "of this attribute should be an integer followed by a " +
                    "unit of seconds, minutes, hours, days, or weeks.  A " +
                    "value of 0 seconds will disable this feature.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_MAX_RESET_AGE,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_WARNING_INTERVAL,
                    "Specifies the maximum length of time before a user's " +
                    "password actually expires that the server will begin " +
                    "to include warning notifications in bind responses for " +
                    "that user.  The value of this attribute should be an " +
                    "integer followed by a unit of seconds, minutes, hours, " +
                    "days, or weeks.  A value of 0 seconds will disable " +
                    "the warning interval.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_WARNING_INTERVAL,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " + ATTR_PWPOLICY_WARNING_INTERVAL +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_EXPIRE_WITHOUT_WARNING,
                    "Indicates whether the Directory Server should allow " +
                    "a user's password to expire even if that user has " +
                    "never seen an expiration warning notification.  If " +
                    "this setting is enabled, then accounts will always be " +
                    "expired when the expiration time arrives.  If it is " +
                    "disabled, then the user will always receive at least " +
                    "one warning notification, and the password expiration " +
                    "will be set to the warning time plus the warning " +
                    "interval.  Changes to this configuration attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_EXPIRE_WITHOUT_WARNING,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_EXPIRE_WITHOUT_WARNING +
                    " in configuration entry %s:  %s");
    registerMessage(
         MSGID_PWPOLICY_MUST_HAVE_WARNING_IF_NOT_EXPIRE_WITHOUT_WARNING,
         "The password policy defined in configuration entry %s is " +
         "configured to always send at least one warning notification before " +
         "the password is expired, but no warning interval has been set.  " +
         "If configuration attribute " + ATTR_PWPOLICY_EXPIRE_WITHOUT_WARNING +
         " is set to \"false\", then configuration attribute " +
         ATTR_PWPOLICY_WARNING_INTERVAL + " must have a positive value");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_ALLOW_EXPIRED_CHANGES,
                    "Indicates whether a user whose password is expired " +
                    "will still be allowed to change that password using " +
                    "the password modify extended operation.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_ALLOW_EXPIRED_CHANGES,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_ALLOW_EXPIRED_CHANGES +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_GRACE_LOGIN_COUNT,
                    "Specifies the number of grace logins that a user will " +
                    "be allowed after the account has expired to allow that " +
                    "user to choose a new password.  A value of 0 " +
                    "indicates that no grace logins will be allowed.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_GRACE_LOGIN_COUNT,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " + ATTR_PWPOLICY_GRACE_LOGIN_COUNT +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_FAILURE_COUNT,
                    "Specifies the maximum number of authentication failures " +
                    "that a user should be allowed before the account is " +
                    "locked out.  A value of 0 indicates that accounts " +
                    "should never be locked out due to failed attempts.  " +
                    "changes to this configuration attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_LOCKOUT_FAILURE_COUNT,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_LOCKOUT_FAILURE_COUNT +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_LOCKOUT_DURATION,
                    "Specifies the length of time that an account should be " +
                    "locked after too many authentication failures.  The " +
                    "value of this attribute should be an integer followed " +
                    "by a unit of seconds, minutes, hours, days, or weeks.  " +
                    "A value of 0 seconds indicates that the account should " +
                    "remain locked until an administrator resets the " +
                    "password.  Changes to this configuration attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_LOCKOUT_DURATION,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " + ATTR_PWPOLICY_LOCKOUT_DURATION +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_FAILURE_EXPIRATION,
                    "Specifies the length of time that should pass before " +
                    "an authentication failure is no longer counted against " +
                    "a user for the purposes of account lockout.  The " +
                    "value of this attribute should be an integer followed " +
                    "by a unit of seconds, minutes, hours, days, or weeks.  " +
                    "A value of 0 seconds indicates that the authentication " +
                    "failures should never expire.  The failure count will " +
                    "always be cleared upon a successful authentication.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_FAILURE_EXPIRATION,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_REQUIRE_CHANGE_BY_TIME,
                    "Specifies the time by which all users with the " +
                    "associated password policy must change their " +
                    "passwords.  The value should be expressed in " +
                    "a generalized time format.  If this time is equal to " +
                    "the current time or is in the past, then all users will " +
                    "be required to change their passwords immediately.  The " +
                    "behavior of the server in this mode will be identical " +
                    "to the behavior observed when users are forced to " +
                    "change their passwords after an administrative reset.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_REQUIRE_CHANGE_BY_TIME,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_REQUIRE_CHANGE_BY_TIME +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_ATTR,
                    "Specifies the name or OID of the attribute type that "+
                    "should be used to hold the last login time for users " +
                    "with the associated password policy.   This attribute " +
                    "type must be defined in the Directory Server schema and " +
                    "must either be defined as an operational attribute or " +
                    "must be allowed by the set of objectClasses for all " +
                    "users with the associated password policy.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_UNDEFINED_LAST_LOGIN_TIME_ATTRIBUTE,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because the " +
                    "specified last login time attribute \"%s\" is not " +
                    "defined in the server schema");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_LAST_LOGIN_TIME_ATTR,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_LAST_LOGIN_TIME_ATTRIBUTE +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_LAST_LOGIN_TIME_FORMAT,
                    "Specifies the format string that should be used to " +
                    "generate the last login time value for users with the " +
                    "associated password policy.  This format string should " +
                    "conform to the syntax described in the API " +
                    "documentation for the " +
                    "<CODE>java.text.SimpleDateFormat</CODE> class.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_INVALID_LAST_LOGIN_TIME_FORMAT,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because the " +
                    "specified last login time format \"%s\" is not " +
                    "a valid format string  The last login time format " +
                    "string should conform to the syntax described in the " +
                    "API documentation for the " +
                    "<CODE>java.text.SimpleDateFormat</CODE> class");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_LAST_LOGIN_TIME_FORMAT,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_LAST_LOGIN_TIME_FORMAT +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_PREVIOUS_LAST_LOGIN_TIME_FORMAT,
                    "Specifies the format string(s) that may have been " +
                    "used with the last login time at any point in the " +
                    "past for users associated with the password policy.  " +
                    "These values are used to make it possible to parse " +
                    "previous values, but will not be used to set new " +
                    "values.  These format strings should conform to the " +
                    "syntax described in the API documentation for the " +
                    "<CODE>java.text.SimpleDateFormat</CODE> class.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_PWPOLICY_INVALID_PREVIOUS_LAST_LOGIN_TIME_FORMAT,
                    "The password policy definition contained in " +
                    "configuration entry \"%s\" is invalid because the " +
                    "specified previous last login time format \"%s\" is not " +
                    "a valid format string  The previous last login time " +
                    "format strings should conform to the syntax described " +
                    "in the API documentation for the " +
                    "<CODE>java.text.SimpleDateFormat</CODE> class");
    registerMessage(
         MSGID_PWPOLICY_CANNOT_DETERMINE_PREVIOUS_LAST_LOGIN_TIME_FORMAT,
         "An error occurred while attempting to determine the values for " +
         "attribute " + ATTR_PWPOLICY_PREVIOUS_LAST_LOGIN_TIME_FORMAT +
         " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_DESCRIPTION_IDLE_LOCKOUT_INTERVAL,
                    "Specifies the maximum length of time that an account " +
                    "may remain idle (i.e., the associated user does not" +
                    "authenticate to the server) before that user is locked " +
                    "out.  The value of this attribute should be an integer " +
                    "followed by a unit of seconds, minutes, hours, days, or " +
                    "weeks.  A value of 0 seconds indicates that idle " +
                    "accounts should not automatically be locked out.  This " +
                    "feature will only be available if the last login time " +
                    "is maintained.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_PWPOLICY_CANNOT_DETERMINE_IDLE_LOCKOUT_INTERVAL,
                    "An error occurred while attempting to determine the " +
                    "value for attribute " +
                    ATTR_PWPOLICY_IDLE_LOCKOUT_INTERVAL +
                    " in configuration entry %s:  %s");
    registerMessage(MSGID_PWPOLICY_UPDATED_POLICY,
                    "The password policy defined in configuration entry %s " +
                    "has been successfully updated");
    registerMessage(MSGID_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED,
                    "Attribute options are not allowed for the password " +
                    "attribute %s");
    registerMessage(MSGID_PWPOLICY_MULTIPLE_PW_VALUES_NOT_ALLOWED,
                    "Only a single value may be provided for the password " +
                    "attribute %s");
    registerMessage(MSGID_PWPOLICY_PREENCODED_NOT_ALLOWED,
                    "Pre-encoded passwords are not allowed for the password " +
                    "attribute %s");
    registerMessage(MSGID_PWPOLICY_VALIDATION_FAILED,
                    "The password value for attribute %s was found to be " +
                    "unacceptable:  %s");


    registerMessage(MSGID_ENQUEUE_BIND_IN_PROGRESS,
                    "A bind operation is currently in progress on the " +
                    "associated client connection.  No other requests may " +
                    "be made on this client connection until the bind " +
                    "processing has completed");
    registerMessage(MSGID_ENQUEUE_MUST_CHANGE_PASSWORD,
                    "You must change your password before you will be " +
                    "allowed to request any other operations");


    registerMessage(MSGID_PWPSTATE_CANNOT_DECODE_SUBENTRY_VALUE_AS_DN,
                    "An error occurred while attempting to decode the " +
                    OP_ATTR_PWPOLICY_POLICY_DN + " value \"%s\" in user " +
                    "entry \"%s\" as a DN:  %s");
    registerMessage(MSGID_PWPSTATE_NO_SUCH_POLICY,
                    "User entry %s is configured to use a password policy " +
                    "subentry of %s but no such password policy has been " +
                    "defined in the server configuration");
    registerMessage(MSGID_PWPSTATE_CANNOT_DECODE_GENERALIZED_TIME,
                    "An error occurred while attempting to decode value " +
                    "\"%s\" for attribute %s in user entry %s in accordance " +
                    "with the generalized time format:  %s");
    registerMessage(MSGID_PWPSTATE_CANNOT_DECODE_BOOLEAN,
                    "Unable to decode value \"%s\" for attribute %s in user " +
                    "entry %s as a Boolean value");
    registerMessage(MSGID_PWPSTATE_CANNOT_UPDATE_USER_ENTRY,
                    "An error occurred while attempting to update password " +
                    "policy state information for user %s:  %s");

    registerMessage(MSGID_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The entry %s cannot be added due to insufficient access rights");
    registerMessage(MSGID_BIND_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The user %s cannot bind due to insufficient access rights");
    registerMessage(MSGID_COMPARE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The entry %s cannot be compared due to insufficient access rights");
    registerMessage(MSGID_DELETE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The entry %s cannot be deleted due to insufficient access rights");
    registerMessage(MSGID_EXTENDED_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The extended operation %s cannot be performed "
            + "due to insufficient access rights");
    registerMessage(MSGID_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The entry %s cannot be renamed due to insufficient access rights");
    registerMessage(MSGID_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The entry %s cannot be modified due to insufficient access rights");
    registerMessage(MSGID_SEARCH_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS,
        "The entry %s cannot be searched due to insufficient access rights");
    registerMessage(MSGID_CONTROL_INSUFFICIENT_ACCESS_RIGHTS,
        "The request control with Object Identifier (OID) \"%s\" cannot be" +
        " used due to insufficient access rights");
    registerMessage(MSGID_BIND_OPERATION_INSECURE_SIMPLE_BIND,
                    "Rejecting a simple bind request for user %s because the " +
                    "password policy requires secure authentication");
    registerMessage(MSGID_BIND_OPERATION_WRITABILITY_DISABLED,
                    "Rejecting a bind request for user %s because either " +
                    "the entire server or the user's backend has a " +
                    "writability mode of 'disabled' and password policy " +
                    "state updates would not be allowed");
    registerMessage(MSGID_BIND_OPERATION_ACCOUNT_DISABLED,
                    "Rejecting a bind request for user %s because the " +
                    "account has been administrative disabled");
    registerMessage(MSGID_BIND_OPERATION_ACCOUNT_EXPIRED,
                    "Rejecting a bind request for user %s because the " +
                    "account has expired");
    registerMessage(MSGID_BIND_OPERATION_ACCOUNT_FAILURE_LOCKED,
                    "Rejecting a bind request for user %s because the " +
                    "account has been locked due to too many failed " +
                    "authentication attempts");
    registerMessage(MSGID_BIND_OPERATION_ACCOUNT_RESET_LOCKED,
                    "Rejecting a bind request for user %s because the " +
                    "account has been locked after the user's password was " +
                    "not changed in a timely manner after an administrative " +
                    "reset");
    registerMessage(MSGID_BIND_OPERATION_ACCOUNT_IDLE_LOCKED,
                    "Rejecting a bind request for user %s because the " +
                    "account has been locked after remaining idle for too " +
                    "long");
    registerMessage(MSGID_BIND_OPERATION_PASSWORD_EXPIRED,
                    "Rejecting a bind request for user %s because that " +
                    "user's password is expired");
    registerMessage(MSGID_BIND_OPERATION_INSECURE_SASL_BIND,
                    "Rejecting a SASL %s bind request for user %s because " +
                    "the password policy requires secure authentication");


    registerMessage(MSGID_WORKQ_CANNOT_PARSE_DN,
                    "An error occurred while attempting to parse string %s " +
                    "as the DN of the work queue configuration entry:  %s");
    registerMessage(MSGID_WORKQ_NO_CONFIG,
                    "Work queue configuration entry %s does not exist in " +
                    "the server configuration");
    registerMessage(MSGID_WORKQ_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that provides the core work queue logic for the " +
                    "Directory Server.  Changes to this configuration " +
                    "attribute require that the server be restarted for the " +
                    "change to take effect");
    registerMessage(MSGID_WORKQ_NO_CLASS_ATTR,
                    "Configuration entry %s does not contain required " +
                    "attribute %s that specifies the fully-qualified class " +
                    "name for the work queue implementation");
    registerMessage(MSGID_WORKQ_CANNOT_LOAD,
                    "An error occurred while trying to load class %s to use " +
                    "as the Directory Server work queue implementation:  %s");
    registerMessage(MSGID_WORKQ_CANNOT_INSTANTIATE,
                    "An error occurred while trying to create an instance " +
                    "of class %s to use as the Directory Server work queue:  " +
                    "%s");
    registerMessage(MSGID_BIND_MULTIPLE_USER_LOOKTHROUGH_LIMITS,
                    "There are multiple user-specific lookthrough limit " +
                    "values contained in user entry %s.  The default server " +
                    "lookthrough limit will be used");
    registerMessage(MSGID_BIND_CANNOT_PROCESS_USER_LOOKTHROUGH_LIMIT,
                    "The user-specific lookthrough limit value %s contained " +
                    "in user entry %s could not be parsed as an integer.  " +
                    "The default server lookthrough limit will be used");


    registerMessage(MSGID_REGISTER_BACKEND_ALREADY_EXISTS,
                    "Unable to register backend %s with the Directory Server " +
                    "because another backend with the same backend ID is " +
                    "already registered");
    registerMessage(MSGID_REGISTER_BASEDN_ALREADY_EXISTS,
                    "Unable to register base DN %s with the Directory Server " +
                    "for backend %s because that base DN is already " +
                    "registered for backend %s");
    registerMessage(MSGID_REGISTER_BASEDN_HIERARCHY_CONFLICT,
                    "Unable to register base DN %s with the Directory Server " +
                    "for backend %s because that backend already contains " +
                    "another base DN %s that is within the same hierarchical " +
                    "path");
    registerMessage(MSGID_REGISTER_BASEDN_DIFFERENT_PARENT_BASES,
                    "Unable to register base DN %s with the Directory Server " +
                    "for backend %s because that backend already contains " +
                    "another base DN %s that is not subordinate to the same " +
                    "base DN in the parent backend");
    registerMessage(MSGID_REGISTER_BASEDN_NEW_BASE_NOT_SUBORDINATE,
                    "Unable to register base DN %s with the Directory Server " +
                    "for backend %s because that backend already contains " +
                    "one or more other base DNs that are subordinate to " +
                    "backend %s but the new base DN is not");
    registerMessage(MSGID_REGISTER_BASEDN_ENTRIES_IN_MULTIPLE_BACKENDS,
                    "Backend %s already contains entry %s which has just " +
                    "been registered as the base DN for backend %s.  " +
                    "These conflicting entries may cause unexpected or " +
                    "errant search results, and both backends should be " +
                    "reinitialized to ensure that each has the correct " +
                    "content");
    registerMessage(MSGID_DEREGISTER_BASEDN_NOT_REGISTERED,
                    "Unable to de-register base DN %s with the Directory " +
                    "Server because that base DN is not registered for any " +
                    "active backend");
    registerMessage(MSGID_DEREGISTER_BASEDN_MISSING_HIERARCHY,
                    "Base DN %s has been deregistered from the Directory " +
                    "Server for backend %s.  This base DN had both superior " +
                    "and subordinate entries in other backends, and there " +
                    "may be inconsistent or unexpected behavior when " +
                    "accessing entries in this portion of the hierarchy " +
                    "because of the missing entries that had been held in " +
                    "the de-registered backend");
    registerMessage(MSGID_REJECT_UNAUTHENTICATED_OPERATION,
                     "Rejecting the requested operation  " +
                     "because the connection has not been authenticated");


    registerMessage(MSGID_CLIENTCONNECTION_DISCONNECT_DUE_TO_DELETE,
                    "Terminating the client connection because its " +
                    "associated authentication or authorization entry %s has " +
                    "been deleted");
    registerMessage(MSGID_CLIENTCONNECTION_AUDIT_HASPRIVILEGE,
                    "hasPrivilege determination for connID=%d opID=%d " +
                    "requesterDN=\"%s\" privilege=\"%s\" result=%b");
    registerMessage(MSGID_CLIENTCONNECTION_AUDIT_HASPRIVILEGES,
                    "hasPrivilege determination for connID=%d opID=%d " +
                    "requesterDN=\"%s\" privilegeSet=\"%s\" result=%b");
    registerMessage(MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to use the " +
                    "proxied authorization control");

    registerMessage(MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to use the " +
                    "proxied authorization control");


    registerMessage(MSGID_SORTKEY_INVALID_ORDER_INDICATOR,
                    "The provided sort key value %s is invalid because it " +
                    "does not start with either '+' (to indicate sorting in " +
                    "ascending order) or '-' (to indicate sorting in " +
                    "descending order)");
    registerMessage(MSGID_SORTKEY_UNDEFINED_TYPE,
                    "The provided sort key value %s is invalid because it " +
                    "references undefined attribute type %s");
    registerMessage(MSGID_SORTKEY_NO_ORDERING_RULE,
                    "The provided sort key value %s is invalid because " +
                    "attribute type %s does not have a default ordering " +
                    "matching rule and no specific rule was provided");
    registerMessage(MSGID_SORTKEY_UNDEFINED_ORDERING_RULE,
                    "The provided sort key value %s is invalid because " +
                    "it references undefined ordering matching rule %s");


    registerMessage(MSGID_SORTORDER_DECODE_NO_KEYS,
                    "The provided sort order string \"%s\" is invalid " +
                    "because it does not contain any sort keys");

    registerMessage(MSGID_HOST_PORT_ALREADY_SPECIFIED,
                    "The connection handler %s is trying to use the listener " +
                    "%s which is already in use by another connection handler");

    registerMessage(MSGID_HOST_PORT_CANNOT_BE_USED,
                    "The server cannot use the listener %s of connection " +
                    "handler %s because it is already being used by another " +
                    "process or because it does not have the rights to use it");

    registerMessage(MSGID_NOT_AVAILABLE_CONNECTION_HANDLERS,
                    "No enabled connection handler available");

    registerMessage(MSGID_ERROR_STARTING_CONNECTION_HANDLERS,
                    "Could not start connection handlers");


    registerMessage(MSGID_REJECT_OPERATION_IN_LOCKDOWN_MODE,
                    "Rejecting the requested operation because the server " +
                    "is in lockdown mode and will only accept requests from " +
                    "root users over loopback connections");
    registerMessage(MSGID_DIRECTORY_SERVER_ENTERING_LOCKDOWN_MODE,
                    "The Directory Server is entering lockdown mode, in " +
                    "which clients will only be allowed to connect via a " +
                    "loopback address, and only root users will be allowed " +
                    "to process operations");
    registerMessage(MSGID_DIRECTORY_SERVER_LEAVING_LOCKDOWN_MODE,
                    "The Directory Server is leaving lockdown mode and will " +
                    "resume normal operation");


    registerMessage(MSGID_COMPRESSEDSCHEMA_UNRECOGNIZED_AD_TOKEN,
                    "Unable to decode the provided attribute because it " +
                    "used an undefined attribute description token %s");
    registerMessage(MSGID_COMPRESSEDSCHEMA_UNKNOWN_OC_TOKEN,
                    "Unable to decode the provided object class set because " +
                    "it used an undefined token %s");
    registerMessage(MSGID_COMPRESSEDSCHEMA_CANNOT_WRITE_UPDATED_DATA,
                    "Unable to write the updated compressed schema token " +
                    "data:  %s");


    registerMessage(MSGID_ENTRYENCODECFG_INVALID_LENGTH,
                    "Unable to decode the provided entry encode " +
                    "configuration element because it has an invalid length");


    registerMessage(MSGID_IDLETIME_LIMIT_EXCEEDED,
                    "This connection has been teriminated because it has " +
                    "remained idle for too long");
    registerMessage(MSGID_IDLETIME_DISCONNECT_ERROR,
                    "An error occurred while attempting to disconnect " +
                    "client connection %d:  %s");
    registerMessage(MSGID_IDLETIME_UNEXPECTED_ERROR,
                    "An unexpected error occurred in the idle time limit " +
                    "thread:  %s");
    registerMessage(MSGID_REGISTER_WORKFLOW_ALREADY_EXISTS,
                    "Unable to register workflow %s with the Directory " +
                    "Server because another workflow with the same " +
                    "workflow ID is already registered");
    registerMessage(MSGID_REGISTER_WORKFLOW_NODE_ALREADY_EXISTS,
                    "Unable to register workflow node %s with the network " +
                    "group %s because another workflow node with the same " +
                    "workflow node ID is already registered");
    registerMessage(MSGID_REGISTER_NETWORK_GROUP_ALREADY_EXISTS,
                    "Unable to register network group %s with the Directory " +
                    "Server because another network group with the same " +
                    "network group ID is already registered");


    registerMessage(MSGID_DIRCFG_SERVER_ALREADY_RUNNING,
                    "The Directory Server is currently running.  Environment " +
                    "configuration changes are not allowed with the server " +
                    "running");
    registerMessage(MSGID_DIRCFG_INVALID_SERVER_ROOT,
                    "The specified server root directory '%s' is invalid.  " +
                    "The specified path must exist and must be a directory");
    registerMessage(MSGID_DIRCFG_INVALID_CONFIG_FILE,
                    "The specified config file path '%s' is invalid.  " +
                    "The specified path must exist and must be a file");
    registerMessage(MSGID_DIRCFG_INVALID_CONFIG_CLASS,
                    "The specified config handler class '%s' is invalid.  " +
                    "The specified class must be a subclass of the " +
                    "org.opends.server.api.ConfigHandler superclass");
    registerMessage(MSGID_DIRCFG_INVALID_SCHEMA_DIRECTORY,
                    "The specified schema configuration directory '%s' is " +
                    "invalid.  The specified path must exist and must be a " +
                    "directory");
    registerMessage(MSGID_DIRCFG_INVALID_LOCK_DIRECTORY,
                    "The specified lock directory '%s' is invalid.  The " +
                    "specified path must exist and must be a directory");
    registerMessage(MSGID_DIRCFG_INVALID_CONCURRENCY_LEVEL,
                    "The specified lock table concurrency level %d is " +
                    "invalid.  It must be an integer value greater than zero");
    registerMessage(MSGID_DIRCFG_INVALID_LOCK_TABLE_SIZE,
                    "The specified initial lock table size %d is invalid.  " +
                    "It must be an integer value greater than zero");
  }
}

