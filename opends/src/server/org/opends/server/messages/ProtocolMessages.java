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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.messages;



import org.opends.server.protocols.ldap.LDAPResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with protocol handling.
 */
public class ProtocolMessages
{
  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element because the byte array to decode was
   * <CODE>null</CODE>.  It does not take any arguments.
   */
  public static final int MSGID_ASN1_NULL_ELEMENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 1;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element because the byte array was too short.  It takes a
   * single integer argument, which is the actual length of the byte array.
   */
  public static final int MSGID_ASN1_SHORT_ELEMENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 2;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element because the encoded length had an invalid number
   * of bytes.  It takes a single integer argument, which is the indicated
   * number of bytes in the length.
   */
  public static final int MSGID_ASN1_INVALID_NUM_LENGTH_BYTES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 3;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element because the element was missing one or more bytes
   * from a multi-byte length.  It takes a single argument, which is the
   * indicated number of bytes in the length.
   */
  public static final int MSGID_ASN1_TRUNCATED_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 4;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element because the decoded value length does not match the
   * number of remaining bytes in the array.  It takes two integer arguments,
   * which are the decoded length and the number of bytes left in the array.
   */
  public static final int MSGID_ASN1_LENGTH_MISMATCH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 5;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as a set of ASN.1 elements because the array was null.
   * This does not take any arguments.
   */
  public static final int MSGID_ASN1_ELEMENT_SET_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 6;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as a set of ASN.1 elements because the end of the array
   * was reached after reading only the type but none of the length for an
   * element.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_ELEMENT_SET_NO_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 7;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a set of ASN.1 elements because the encoded length for an element
   * had an invalid number of bytes.  It takes a single integer argument, which
   * is the indicated number of bytes in the length.
   */
  public static final int MSGID_ASN1_ELEMENT_SET_INVALID_NUM_LENGTH_BYTES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 8;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a set of ASN.1 elements because an element was missing one or more
   * bytes from a multi-byte length.  It takes a single argument, which is the
   * indicated number of bytes in the length.
   */
  public static final int MSGID_ASN1_ELEMENT_SET_TRUNCATED_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 9;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a set of ASN.1 elements because the byte array did not contain
   * enough bytes to hold the entire value.  This takes two integer arguments,
   * which are the decoded length for the element and the number of bytes
   * remaining.
   */
  public static final int MSGID_ASN1_ELEMENT_SET_TRUNCATED_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 10;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 Boolean element because it was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_BOOLEAN_SET_VALUE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 11;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 Boolean element because the
   * byte array was not exactly one byte long.  This takes a single integer
   * argument, which is the length of the provided array.
   */
  public static final int MSGID_ASN1_BOOLEAN_SET_VALUE_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 12;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a Boolean element because the provided element
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_BOOLEAN_DECODE_ELEMENT_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 13;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a Boolean element because the value of the
   * provided element was not exactly one byte.  This takes a single integer
   * argument, which is the length of the value of the provided element.
   */
  public static final int MSGID_ASN1_BOOLEAN_DECODE_ELEMENT_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 14;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 Boolean element because the provided array
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_BOOLEAN_DECODE_ARRAY_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 15;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 Boolean element because the byte array was too short.
   * It takes a single integer argument, which is the actual length of the byte
   * array.
   */
  public static final int MSGID_ASN1_BOOLEAN_SHORT_ELEMENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 16;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 Boolean element because the decoded value
   * was not exactly one byte.  This takes a single integer argument, which is
   * the decoded value.
   */
  public static final int MSGID_ASN1_BOOLEAN_DECODE_ARRAY_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 17;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 null element because the byte
   * array was not exactly zero bytes long.  This takes a single integer
   * argument, which is the length of the provided array.
   */
  public static final int MSGID_ASN1_NULL_SET_VALUE_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 18;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a null element because the provided element
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_NULL_DECODE_ELEMENT_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 19;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a null element because the value of the
   * provided element was not exactly zero byte.  This takes a single integer
   * argument, which is the length of the value of the provided element.
   */
  public static final int MSGID_ASN1_NULL_DECODE_ELEMENT_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 20;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 null element because the provided array
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_NULL_DECODE_ARRAY_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 21;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 null element because the decoded value
   * was not exactly zero byte.  This takes a single integer argument, which is
   * the decoded value.
   */
  public static final int MSGID_ASN1_NULL_DECODE_ARRAY_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 22;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an octet string element because the provided
   * element was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_OCTET_STRING_DECODE_ELEMENT_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 23;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 octet string element because the provided
   * array was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_OCTET_STRING_DECODE_ARRAY_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 24;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 integer element because it was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_INTEGER_SET_VALUE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 25;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 integer element because the
   * byte array was not between one and four bytes long.  This takes a single
   * integer argument, which is the length of the provided array.
   */
  public static final int MSGID_ASN1_INTEGER_SET_VALUE_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 26;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an integer element because the provided element
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_INTEGER_DECODE_ELEMENT_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 27;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an integer element because the value of the
   * provided element was not between one and four bytes.  This takes a single
   * integer argument, which is the length of the value of the provided element.
   */
  public static final int MSGID_ASN1_INTEGER_DECODE_ELEMENT_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 28;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 integer element because the provided array
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_INTEGER_DECODE_ARRAY_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 29;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 integer element because the byte array was too short.
   * It takes a single integer argument, which is the actual length of the byte
   * array.
   */
  public static final int MSGID_ASN1_INTEGER_SHORT_ELEMENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 30;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 integer element because the decoded value
   * was not between one and four bytes.  This takes a single integer argument,
   * which is the decoded value.
   */
  public static final int MSGID_ASN1_INTEGER_DECODE_ARRAY_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 31;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 enumerated element because it
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_ENUMERATED_SET_VALUE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 32;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 enumerated element because the
   * byte array was not between one and four bytes long.  This takes a single
   * integer argument, which is the length of the provided array.
   */
  public static final int MSGID_ASN1_ENUMERATED_SET_VALUE_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 33;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an enumerated element because the provided
   * element was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_ENUMERATED_DECODE_ELEMENT_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 34;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an enumerated element because the value of the
   * provided element was not between one and four bytes.  This takes a single
   * integer argument, which is the length of the value of the provided element.
   */
  public static final int MSGID_ASN1_ENUMERATED_DECODE_ELEMENT_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 35;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 enumerated element because the provided
   * array was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_ENUMERATED_DECODE_ARRAY_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 36;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 enumerated element because the byte array was too short.
   * It takes a single integer argument, which is the actual length of the byte
   * array.
   */
  public static final int MSGID_ASN1_ENUMERATED_SHORT_ELEMENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 37;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 enumerated element because the decoded
   * value was not between one and four bytes.  This takes a single integer
   * argument, which is the decoded value.
   */
  public static final int MSGID_ASN1_ENUMERATED_DECODE_ARRAY_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 38;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 sequence element because it
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_SEQUENCE_SET_VALUE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 39;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a sequence element because the provided element
   * was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_SEQUENCE_DECODE_ELEMENT_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 40;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 sequence element because the provided
   * array was null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_SEQUENCE_DECODE_ARRAY_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 41;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 set element because it was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_SET_SET_VALUE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 42;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a set element because the provided element was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_SET_DECODE_ELEMENT_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 43;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 set element because the provided array was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_ASN1_SET_DECODE_ARRAY_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 44;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 sequence as an LDAP message because the sequence was null.
   */
  public static final int MSGID_LDAP_MESSAGE_DECODE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 45;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 sequence as an LDAP message because the number of elements
   * in the sequence is invalid.  This takes a single integer argument, which is
   * the number of elements in the provided sequence.
   */
  public static final int MSGID_LDAP_MESSAGE_DECODE_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 46;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 sequence as an LDAP message because a problem occurred
   * while trying to decode the first element as the message ID.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_MESSAGE_DECODE_MESSAGE_ID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 47;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 sequence as an LDAP message because a problem occurred
   * while trying to decode the second element as the protocol op.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_MESSAGE_DECODE_PROTOCOL_OP =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 48;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 sequence as an LDAP message because a problem occurred
   * while trying to decode the third element as the set of controls.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_LDAP_MESSAGE_DECODE_CONTROLS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 49;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP control because the element was null.
   * This does not take any arguments.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 50;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP control because the element could not
   * be decoded as a sequence.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 51;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP control because the sequence contained
   * an invalid number of elements.  This takes a single integer argument, which
   * is the actual number of elements in the sequence.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 52;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP control because the OID could not be
   * decoded.  This takes a single argument, which is the string representation
   * of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_OID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 53;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP control because the criticality could
   * not be decoded.  This takes a single argument, which is the string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_CRITICALITY =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 54;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP control because the value could not be
   * decoded.  This takes a single argument, which is the string representation
   * of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 55;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP control because the BER type for the
   * second element was invalid.  This takes a single argument, which is the BER
   * type of the element.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_INVALID_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 56;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a set of LDAP controls because the element was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_CONTROLS_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 57;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a set of LDAP controls because the element could
   * not be decoded as a sequence.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONTROL_DECODE_CONTROLS_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 58;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP abandon request protocol op because of a
   * problem while interpreting the ID to abandon.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_ABANDON_REQUEST_DECODE_ID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 59;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP result protocol op type because the
   * element could not be decoded as a sequence.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_RESULT_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 60;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP result protocol op type because the
   * result sequence did not contain an appropriate number of elements.  This
   * takes a single integer argument, which is the number of elements in the
   * decoded sequence.
   */
  public static final int MSGID_LDAP_RESULT_DECODE_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 61;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP result protocol op type because a
   * problem occurred while trying to interpret the result code.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_RESULT_DECODE_RESULT_CODE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 62;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP result protocol op type because a
   * problem occurred while trying to interpret the matched DN.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_RESULT_DECODE_MATCHED_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 63;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP result protocol op type because a
   * problem occurred while trying to interpret the error message.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_RESULT_DECODE_ERROR_MESSAGE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 64;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP result protocol op type because a
   * problem occurred while trying to interpret the referral URLs.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_RESULT_DECODE_REFERRALS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 65;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind response protocol op type because
   * the result sequence did not contain an appropriate number of elements.
   * This takes a single integer argument, which is the number of elements in
   * the decoded sequence.
   */
  public static final int MSGID_LDAP_BIND_RESULT_DECODE_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 66;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a bind response result protocol op type because
   * a problem occurred while trying to interpret the server SASL credentials.
   * This takes a single argument, which is a string representation of the
   * exception that was caught.
   */
  public static final int
       MSGID_LDAP_BIND_RESULT_DECODE_SERVER_SASL_CREDENTIALS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 67;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a bind response protocol op because the fourth
   * element in the sequence had an invalid BER type.  This takes a single byte
   * argument, which is the BER type of the element.
   */
  public static final int MSGID_LDAP_BIND_RESULT_DECODE_INVALID_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 68;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP extended response protocol op type
   * because the result sequence did not contain an appropriate number of
   * elements.  This takes a single integer argument, which is the number of
   * elements in the decoded sequence.
   */
  public static final int
       MSGID_LDAP_EXTENDED_RESULT_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 69;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an extended response result protocol op because
   * a problem occurred while trying to interpret the set of referral URLs.
   * This takes a single argument, which is a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_LDAP_EXTENDED_RESULT_DECODE_REFERRALS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 70;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an extended response result protocol op because
   * a problem occurred while trying to interpret the response OID.  This takes
   * a single argument, which is a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_LDAP_EXTENDED_RESULT_DECODE_OID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 71;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an extended response result protocol op because
   * a problem occurred while trying to interpret the response value.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_LDAP_EXTENDED_RESULT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 72;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an extended response protocol op because one of
   * the elements in the sequence had an invalid BER type.  This takes a single
   * byte argument, which is the BER type of the element.
   */
  public static final int MSGID_LDAP_EXTENDED_RESULT_DECODE_INVALID_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 73;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP unbind request protocol op.  This takes
   * a single argument, which is a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_LDAP_UNBIND_DECODE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 74;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because the
   * element could not be decoded as a sequence.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 75;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because the
   * request sequence had an invalid number of elements.  This takes a single
   * integer argument, which is the number of elements in the decoded sequence.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 76;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because the
   * protocol version could not be decoded.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_VERSION =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 77;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because the
   * DN could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 78;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because the
   * simple authentication password could not be decoded.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_PASSWORD =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 79;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because the
   * SASL authentication info could not be decoded.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_SASL_INFO =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 80;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because the
   * authentication info element had an invalid BER type.  This takes a single
   * argument, which is the BER type of the associated element.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_INVALID_CRED_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 81;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP bind request protocol op because a
   * generic failure occurred while trying to decode the bind credentials.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_LDAP_BIND_REQUEST_DECODE_CREDENTIALS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 82;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP compare request protocol op because the
   * element could not be decoded into the request sequence.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_COMPARE_REQUEST_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 83;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP compare request protocol op because the
   * request sequence had an invalid number of elements.  This takes a single
   * argument, which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_COMPARE_REQUEST_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 84;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP compare request protocol op because the
   * DN could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_COMPARE_REQUEST_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 85;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP compare request protocol op because the
   * AVA sequence could not be decoded.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_COMPARE_REQUEST_DECODE_AVA =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 86;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP compare request protocol op because
   * there was an invalid number of elements in the AVA sequence.  This takes a
   * single argument, which is the number of elements in the sequence
   */
  public static final int MSGID_LDAP_COMPARE_REQUEST_DECODE_AVA_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 87;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP compare request protocol op because the
   * attribute type could not be decoded.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_COMPARE_REQUEST_DECODE_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 88;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP compare request protocol op because the
   * assertion value could not be decoded.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_COMPARE_REQUEST_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 89;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP delete request protocol op because the
   * DN could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_DELETE_REQUEST_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 90;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP extended request protocol op because the
   * element could not be decoded into the request sequence.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_EXTENDED_REQUEST_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 91;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP extended request protocol op because the
   * request sequence had an invalid number of elements.  This takes a single
   * argument, which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_EXTENDED_REQUEST_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 92;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP extended request protocol op because the
   * OID could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_EXTENDED_REQUEST_DECODE_OID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 93;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP extended request protocol op because the
   * value could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_EXTENDED_REQUEST_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 94;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify DN request protocol op because
   * the element could not be decoded into the request sequence.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 95;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify DN request protocol op because
   * the request sequence had an invalid number of elements.  This takes a
   * single argument, which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 96;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify DN request protocol op because
   * the entry DN could not be decoded.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 97;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify DN request protocol op because
   * the new RDN could not be decoded.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_RDN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 98;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify DN request protocol op because
   * the deleteOldRDN flag could not be decoded.  This takes a single argument,
   * which is  a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_DELETE_OLD_RDN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 99;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify DN request protocol op because
   * the new superior DN could not be decoded.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_SUPERIOR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 100;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP attribute because the element could not
   * be decoded into the request sequence.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_ATTRIBUTE_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 101;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP attribute because the request sequence
   * had an invalid number of elements.  This takes a single argument, which is
   * the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_ATTRIBUTE_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 102;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP attribute because the attribute type
   * could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_ATTRIBUTE_DECODE_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 103;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP attribute because the set of values
   * could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_ATTRIBUTE_DECODE_VALUES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 104;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP add request protocol op because the
   * element could not be decoded into the request sequence.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_ADD_REQUEST_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 105;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP add request protocol op because the
   * request sequence had an invalid number of elements.  This takes a single
   * argument, which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_ADD_REQUEST_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 106;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP add request protocol op because the
   * entry DN could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_ADD_REQUEST_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 107;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP add request protocol op because the set
   * of attributes could not be decoded.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_ADD_REQUEST_DECODE_ATTRS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 108;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modification because the element could
   * not be decoded into the request sequence.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFICATION_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 109;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modification because the request
   * sequence had an invalid number of elements.  This takes a single argument,
   * which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_MODIFICATION_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 110;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modification because it contained an
   * invalid modification type.  This takes a single integer argument, which is
   * the modification type contained in the request.
   */
  public static final int MSGID_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 111;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modification because the modification
   * type could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFICATION_DECODE_MOD_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 112;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modification because the attribute
   * could not be decoded.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFICATION_DECODE_ATTR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 113;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify request protocol op because the
   * element could not be decoded into the request sequence.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_MODIFY_REQUEST_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 114;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify request protocol op because the
   * request sequence had an invalid number of elements.  This takes a single
   * argument, which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_MODIFY_REQUEST_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 115;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify request protocol op because the
   * entry DN could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFY_REQUEST_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 116;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP modify request protocol op because the
   * set of modifications could not be decoded.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_MODIFY_REQUEST_DECODE_MODS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 117;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search result entry protocol op because
   * the element could not be decoded into the request sequence.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_SEARCH_ENTRY_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 118;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search result entry protocol op because
   * the request sequence had an invalid number of elements.  This takes a
   * single argument, which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_SEARCH_ENTRY_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 119;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search result entry protocol op because
   * the entry DN could not be decoded.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_ENTRY_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 120;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search result entry protocol op because
   * the set of attributes could not be decoded.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_ENTRY_DECODE_ATTRS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 121;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search result reference protocol op
   * because the element could not be decoded into the request sequence.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_REFERENCE_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 122;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search result reference protocol op
   * because the set of attributes could not be decoded.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_SEARCH_REFERENCE_DECODE_URLS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 123;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * element could not be decoded into the request sequence.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 124;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * request sequence had an invalid number of elements.  This takes a single
   * argument, which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 125;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * base DN could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_BASE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 126;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * scope value was invalid.  This takes a single argument, which is the value
   * provided for the scope.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_SCOPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 127;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * search scope could not be decoded.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_SCOPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 128;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * alias dereference policy value was invalid.  This takes a single argument,
   * which is the value provided for the dereference policy.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_DEREF =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 129;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * alias dereferencing policy could not be decoded.  This takes a single
   * argument,  which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_DEREF =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 130;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * size limit could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_SIZE_LIMIT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 131;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * time limit could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_TIME_LIMIT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 132;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * typesOnly flag could not be decoded.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_TYPES_ONLY =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 133;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * filter could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_FILTER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 134;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search request protocol op because the
   * set of requested attributes could not be decoded.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_SEARCH_REQUEST_DECODE_ATTRIBUTES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 135;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP protocol op because the element was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_PROTOCOL_OP_DECODE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 136;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP protocol op because the element had an
   * invalid BER type.  This takes a single byte argument, which is the BER type
   * of the element.
   */
  public static final int MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 137;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the element was
   * null.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 138;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the element had an
   * invalid BER type.  This takes a single byte argument, which is the BER type
   * of the element.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_INVALID_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 139;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the element could
   * not be decoded as an ASN.1 set.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_COMPOUND_SET =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 140;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because one of the filter
   * components could not be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_COMPOUND_COMPONENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 141;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the value of the
   * element could not be decoded as an ASN.1 element for the not filter
   * component.  This takes a single argument, which is a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_NOT_ELEMENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 142;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the NOT element
   * could not be decoded as a filter component.  This takes a single argument,
   * which is a string representation  of the exception that was caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_NOT_COMPONENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 143;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the element could
   * not be decoded into the type-and-value sequence.  This takes a single
   * argument, which is a string representation  of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_TV_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 144;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the type and value
   * sequence had an invalid number of elements.  This takes a single argument,
   * which is the number of elements in the request sequence.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_TV_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 145;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the attribute type
   * could not be decoded from the type-and-value sequence.  This takes a single
   * argument, which is a string representation  of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_TV_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 146;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the assertion
   * value could not be decoded from the type-and-value sequence.  This takes a
   * single argument, which is a string representation  of the exception that
   * was caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_TV_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 147;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the element could
   * not be decoded into the substring sequence.  This takes a single argument,
   * which is a string representation  of the exception that was caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_SUBSTRING_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 148;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the substring
   * sequence had an invalid number of elements.  This takes a single argument,
   * which is the number of elements in the request sequence.
   */
  public static final int
       MSGID_LDAP_FILTER_DECODE_SUBSTRING_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 149;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the attribute type
   * could not be decoded from the substring sequence.  This takes a single
   * argument, which is a string representation  of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_SUBSTRING_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 150;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the sequence of
   * substring  value elements could not be decoded.  This takes a single
   * argument, which is a string representation  of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_SUBSTRING_ELEMENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 151;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the substring
   * value sequence did not contain any elements.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_SUBSTRING_NO_SUBELEMENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 152;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the substring
   * value sequence contained an element with an invalid BER type.  This takes
   * a single byte argument, which is the invalid element type.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_SUBSTRING_INVALID_SUBTYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 153;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because a problem occurred
   * while decoding the set of substring value components.  This takes a single
   * argument, which is a string representation  of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_SUBSTRING_VALUES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 154;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the attribute type
   * could not be decoded for the presence filter.  This takes a single
   * argument, which is a string representation  of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_PRESENCE_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 155;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the element could
   * not be decoded into the extensible match sequence.  This takes a single
   * argument, which is a string representation  of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 156;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because the extensible
   * match sequence contained an element with an invalid BER type.  This takes
   * a single byte argument, which is the invalid element type.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_INVALID_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 157;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as an LDAP search filter because a problem occurred
   * while decoding the set of extensible match components.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_ELEMENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 158;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * send a response for an operation that does not have a result code.  This
   * message takes three string arguments:  the type of operation, the
   * connection ID, and the operation ID.
   */
  public static final int MSGID_LDAP_CLIENT_SEND_RESPONSE_NO_RESULT_CODE =
  CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 159;


  /**
   * The message ID for the message that will be used if an attempt is made to
   * send a response for an operation that should not have a response.  This
   * message takes four string arguments:  the type of operation, the connection
   * ID, the operation ID, and a backtrace for the thread.
   */
  public static final int MSGID_LDAP_CLIENT_SEND_RESPONSE_INVALID_OP =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 160;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to encode an LDAP message to an ASN.1 element.  This takes the
   * following arguments:  a string representation of the LDAP message, the
   * client connection ID, the operation ID, and the string representation of
   * the exception that was caught.
   */
  public static final int MSGID_LDAP_CLIENT_SEND_MESSAGE_ENCODE_ASN1 =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 161;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to encode an LDAP message from an ASN.1 element to a byte array.
   * This takes the following arguments:  a string representation of the ASN.1
   * element, the client connection ID, the operation ID, and the string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CLIENT_SEND_MESSAGE_ENCODE_BYTES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 162;



  /**
   * The message ID for the message that will be used if an I/O problem is
   * encountered while trying to send an LDAP message to the client.  This takes
   * the following arguments:  a string representation of the LDAP message, the
   * client connection ID, the operation ID, and the string representation of
   * the exception that was caught.
   */
  public static final int MSGID_LDAP_CLIENT_SEND_MESSAGE_IO_PROBLEM =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 163;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * (not I/O-related) is encountered while trying to send an LDAP message to
   * the client.  This takes the following arguments:  a string representation
   * of the LDAP message, the client connection ID, the operation ID, and the
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CLIENT_SEND_MESSAGE_UNEXPECTED_PROBLEM =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 164;



  /**
   * The message ID for the message that will be included in the notice of
   * disconnection response sent to the client if no other more appropriate
   * message has been provided.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_CLIENT_GENERIC_NOTICE_OF_DISCONNECTION =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 165;



  /**
   * The message ID for the message that will be used if the client attempts to
   * request an operation while the server is in the process of disconnecting
   * that client.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_CLIENT_DISCONNECT_IN_PROGRESS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_WARNING | 166;



  /**
   * The message ID for the message that will be used if the client sends a
   * request that is an ASN.1 element with a zero-byte value (which cannot be a
   * valid LDAP message).  This does not take any arguments.
   */
  public static final int MSGID_LDAP_CLIENT_DECODE_ZERO_BYTE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 167;



  /**
   * The message ID for the message that will be used if the client sends a
   * request with a length that is larger than the maximum allowed request size.
   * This takes two integer arguments, which are the length of the element that
   * the client was trying to send and the maximum length allowed by the server.
   */
  public static final int MSGID_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 168;



  /**
   * The message ID for the message that will be used if the client sends a
   * request with a length that is encoded using multiple bytes, and more than
   * four bytes are required to express that length.  This takes one integer
   * argument, which is the number of bytes in the multi-byte length provided by
   * the client.
   */
  public static final int MSGID_LDAP_CLIENT_DECODE_INVALID_MULTIBYTE_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 169;



  /**
   * The message ID for the message that will be used if the client sends a
   * request that cannot be properly decoded as an ASN.1 element.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_CLIENT_DECODE_ASN1_FAILED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 170;



  /**
   * The message ID for the message that will be used if the client sends a
   * request that cannot be properly decoded as an LDAP message.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_CLIENT_DECODE_LDAP_MESSAGE_FAILED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 171;



  /**
   * The message ID for the message that will be used if the server somehow gets
   * into a state in which it cannot determine where it should pick up in the
   * decoding process.  This takes a single integer argument, which is the
   * internal decoding state for the client connection.
   */
  public static final int MSGID_LDAP_CLIENT_INVALID_DECODE_STATE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 172;



  /**
   * The message ID for the message that will be used if the client sends a
   * request that has an invalid protocol op type for a request (e.g., one that
   * should only be used for responses).  This takes a single argument, which is
   * a string representation of the LDAP message read from the client.
   */
  public static final int MSGID_LDAP_CLIENT_DECODE_INVALID_REQUEST_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 173;



  /**
   * The message ID for the message that will be used if the client sends a
   * request is properly decoded as an LDAP message but cannot be converted to
   * an operation.  This takes two arguments, which are a string representation
   * of the LDAP message and a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_LDAP_CLIENT_CANNOT_CONVERT_MESSAGE_TO_OPERATION =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 174;



  /**
   * The message ID for the message that will be used if the server cannot
   * convert an LDAP message into an abandon operation.  This takes two
   * arguments, which are a string representation of the LDAP message and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_ABANDON_INVALID_MESSAGE_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 175;



  /**
   * The message ID for the message that will be used if the server cannot
   * convert an LDAP message into an unbind operation.  This takes two
   * arguments, which are a string representation of the LDAP message and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_UNBIND_INVALID_MESSAGE_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 176;



  /**
   * The message ID for the message that will be used if it is not possible to
   * create a selector for an LDAP connection handler.  This takes two
   * arguments, which are the DN of the configuration entry for the connection
   * handler and a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_OPEN_SELECTOR_FAILED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 177;



  /**
   * The message ID for the message that will be used if it is not possible to
   * listen on one of the addressed associated with an LDAP connection handler.
   * This accepts several arguments:  the DN of the configuration entry of the
   * connection handler, the listen address, the port number, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CREATE_CHANNEL_FAILED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 178;



  /**
   * The message ID for the message that will be used if all of the attempts to
   * create acceptors for the LDAP connection handler failed.  This takes a
   * single argument, which is the DN of the configuration entry for the
   * connection handler.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NO_ACCEPTORS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 179;



  /**
   * The message ID for the message that will be used if a client connection is
   * rejected because it was included in a denied address range.  This takes
   * two arguments which are the host:port of the client and host:port of the
   * server.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DENIED_CLIENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 180;



  /**
   * The message ID for the message that will be used if a client connection is
   * rejected because it was not included in any allowed address range.  This
   * takes two arguments which are the host:port of the client and host:port of
   * the server.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DISALLOWED_CLIENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 181;



  /**
   * The message ID for the message that will be used if it is not possible to
   * register a client with a request handler for some reason.  This takes three
   * arguments, which are the host:port of the client, the host:port of the
   * server, and a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 182;



  /**
   * The message ID for the message that will be used if a failure occurs while
   * trying to accept a new client connection.  This takes two arguments, which
   * are the DN of the configuration entry associated with the connection
   * handler and the string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_ACCEPT_CONNECTION =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 183;



  /**
   * The message ID for the message that will be used if multiple consecutive
   * failures occurred while trying to accept client connections.  This takes
   * two arguments, which are the DN of the configuration entry associated with
   * the connection handler and the string representation of the exception that
   * was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CONSECUTIVE_ACCEPT_FAILURES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 184;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurred that will prevent the connection handler from being able to keep
   * processing.  This takes two arguments, which are the DN of the
   * configuration entry associated with the connection handler and the string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_UNCAUGHT_ERROR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 185;



  /**
   * The message ID for the message that will be used if it is not possible to
   * create a selector for an LDAP connection handler.  This takes two
   * arguments, which are the name of the request handler and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_REQHANDLER_OPEN_SELECTOR_FAILED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 186;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to register a client connection with a request handler.  This takes
   * two arguments:  the name of the request handler and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_LDAP_REQHANDLER_CANNOT_REGISTER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 187;



  /**
   * The message ID for the message that will be used if an attempt to register
   * a client connection with the request handler is rejected because the server
   * is shutting down.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_REQHANDLER_REJECT_DUE_TO_SHUTDOWN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 188;



  /**
   * The message ID for the message that will be used if an attempt to register
   * a client connection with the request handler is rejected because the
   * pending connection queue is full.  This takes a single argument, which is
   * the name of the request handler.
   */
  public static final int MSGID_LDAP_REQHANDLER_REJECT_DUE_TO_QUEUE_FULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 189;



  /**
   * The message ID for the message that will be used a client connection is
   * being deregistered from a request handler because the Directory Server is
   * shutting down.  This takes a single argument, which is the reason provided
   * for the shutdown.
   */
  public static final int MSGID_LDAP_REQHANDLER_DEREGISTER_DUE_TO_SHUTDOWN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_FATAL_ERROR | 190;



  /**
   * The message ID for the message that will be used if the ASN.1 reader
   * attempts to read an element that is larger than the maximum allowed size.
   * This takes two arguments, which are decoded element size and the maximum
   * allowed size.
   */
  public static final int MSGID_ASN1_READER_MAX_SIZE_EXCEEDED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 191;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode a null string as an LDAP search filter.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAP_FILTER_STRING_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 192;



  /**
   * The message ID for the message that will be used if an unexpected exception
   * is thrown while trying to decode a string as an LDAP filter.  This takes
   * two arguments, which are the provided filter string and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_FILTER_UNCAUGHT_EXCEPTION =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 193;



  /**
   * The message ID for the message that will be used if there is a mismatch in
   * the set of open and close parentheses for a search filter.  This takes
   * three arguments, which are the filter string, the start position, and the
   * end position for the filter.
   */
  public static final int MSGID_LDAP_FILTER_MISMATCHED_PARENTHESES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 194;



  /**
   * The message ID for the message that will be used if a filter string cannot
   * be decoded because it does not contain an equal sign.  This takes three
   * arguments, which are the filter string, the start position, and the end
   * position for the filter.
   */
  public static final int MSGID_LDAP_FILTER_NO_EQUAL_SIGN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 195;



  /**
   * The message ID for the message that will be used if a search filter string
   * contains a backslash that is not followed by two hexadecimal digits.  This
   * takes two arguments, which are the filter string and the location of the
   * invalid escaped byte.
   */
  public static final int MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 196;



  /**
   * The message ID for the message that will be used if a compound filter
   * element is missing a set of enclosing parentheses.  This takes three
   * arguments, which are the filter string, the start position, and the end
   * position for the filter.
   */
  public static final int MSGID_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 197;



  /**
   * The message ID for the message that will be used if a filter string has
   * a close parenthesis that is not matched by a corresponding open
   * parenthesis.  This takes two arguments, which are the filter string and the
   * position of the unmatched close parenthesis.
   */
  public static final int MSGID_LDAP_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 198;



  /**
   * The message ID for the message that will be used if a filter string has an
   * open parenthesis that is not matched by a corresponding close parenthesis.
   * This takes two arguments, which are the filter string and the position of
   * the unmatched open parenthesis.
   */
  public static final int MSGID_LDAP_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 199;



  /**
   * The message ID for the message that will be used if a substring filter does
   * not contain any asterisks in its value.  This takes three arguments, which
   * are the filter string, the start position for the value, and the end
   * position for the value.
   */
  public static final int MSGID_LDAP_FILTER_SUBSTRING_NO_ASTERISKS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 200;



  /**
   * The message ID for the message that will be used if an extensible match
   * filter does not contain a colon.  This takes two arguments, which are the
   * filter string and the start position of the extensible match filter.
   */
  public static final int MSGID_LDAP_FILTER_EXTENSIBLE_MATCH_NO_COLON =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 201;



  /**
   * The message ID for the message that will be used if a client sends a
   * request to the server with an invalid type.  This takes two arguments,
   * which are the request type that the client provided and the message ID of
   * the message containing the invalid request.
   */
  public static final int MSGID_LDAP_DISCONNECT_DUE_TO_INVALID_REQUEST_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 202;



  /**
   * The message ID for the message that will be used if an internal failure
   * occurs while trying to hand off a client request for processing.  This
   * takes three arguments, which are the request type that the client provided,
   * the message ID of the message containing the request, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_DISCONNECT_DUE_TO_PROCESSING_FAILURE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 203;



  /**
   * The message ID for the message that will be used if a bind request includes
   * an invalid authentication type.  This takes two arguments, which are the
   * LDAP message ID of the request and a string representation of the
   * authentication type.
   */
  public static final int MSGID_LDAP_INVALID_BIND_AUTH_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 204;



  /**
   * The message ID for the message that will be used if a connection is closed
   * because a bind operation failed with a protocol error.  This takes two
   * arguments, which are the LDAP message ID of the bind request and the error
   * message from the bind response.
   */
  public static final int MSGID_LDAP_DISCONNECT_DUE_TO_BIND_PROTOCOL_ERROR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 205;



  /**
   * The message ID for the message that will be used if an extended response is
   * not sent to an LDAPv2 client.  This takes three arguments, which are the
   * connection ID, operation ID, and string representation of the extended
   * response that would have been sent.
   */
  public static final int MSGID_LDAPV2_SKIPPING_EXTENDED_RESPONSE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 206;



  /**
   * The message ID for the message that will be used if a search reference is
   * not sent to an LDAPv2 client.  This takes three arguments, which are the
   * connection ID, operation ID, and string representation of the search
   * reference that would have been sent.
   */
  public static final int MSGID_LDAPV2_SKIPPING_SEARCH_REFERENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 207;



  /**
   * The message ID for the message that will be used if a search reference is
   * not sent to an LDAPv2 client.  This does not take any arguments.
   */
  public static final int MSGID_LDAPV2_REFERRAL_RESULT_CHANGED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 208;



  /**
   * The message ID for the message that will be used to indicate that a
   * response contained one or more referrals that were not provided to an
   * LDAPv2 client.  This takes one argument, which is a string representation
   * of the referral URLs that would have been used.
   */
  public static final int MSGID_LDAPV2_REFERRALS_OMITTED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 209;



  /**
   * The message ID for the message that will be used to indicate that a client
   * tried to bind using LDAPv2 but that was not allowed and the connection will
   * be closed.  This does not take any arguments.
   */
  public static final int MSGID_LDAPV2_CLIENTS_NOT_ALLOWED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 210;



  /**
   * The message ID for the message that will be used to indicate that an LDAPv2
   * client tried to send an extended request and will be disconnected.  This
   * takes two arguments, which are the connection ID for the client and the
   * message ID of the extended request.
   */
  public static final int MSGID_LDAPV2_EXTENDED_REQUEST_NOT_ALLOWED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 211;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * initialize the LDAP statistics monitor provider from the configuration.
   * This takes a single argument, which is the DN of the configuration entry
   * that targeted the LDAP statistics monitor.
   */
  public static final int MSGID_LDAP_STATS_INVALID_MONITOR_INITIALIZATION =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 212;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs in the request handler while attempting to iterate over connections
   * with data available for reading.  This takes two arguments, which are the
   * name of the request handler thread and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_LDAP_REQHANDLER_UNEXPECTED_SELECT_EXCEPTION =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 213;



  /**
   * The message ID for the message that will be used if a connection attempt is
   * rejected when trying to register it with the core server.  This does not
   * take any arguments.
   */
  public static final int MSGID_LDAP_CONNHANDLER_REJECTED_BY_SERVER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 214;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the address(es) on which to accept
   * client connections.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_ADDRESS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 215;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the port on which to accept client
   * connections.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 216;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the address masks used to determine
   * which clients will be allowed to establish connections.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOWED_CLIENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 217;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the address masks used to determine
   * which clients will not be allowed to establish connections.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_DENIED_CLIENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 218;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether LDAPv2 clients will be allowed.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_LDAPV2 =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 219;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the number of request handlers to use to
   * read requestsfrom clients.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_DESCRIPTION_NUM_REQUEST_HANDLERS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 220;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether to send a notice of
   * disconnection to rejected client connections.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_DESCRIPTION_SEND_REJECTION_NOTICE =
           CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 221;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether to use the TCP_KEEPALIVE socket
   * option for client connections.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_KEEPALIVE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 222;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether to use the TCP_NODELAY socket
   * option for client connections.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_NODELAY =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 223;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether to use the SO_REUSEADDR socket
   * option for the server listen socket.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_REUSE_ADDRESS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 224;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the maximum allowed request size.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_MAX_REQUEST_SIZE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 225;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether to use SSL when accepting
   * connections.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_SSL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 226;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether to allow the use of the
   * StartTLS extended operation.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 227;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the policy to use for
   * requesting/requiring SSL client authentication.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CLIENT_AUTH_POLICY =
           CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 228;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the nickname of the certificate to use
   * for SSL and StartTLS communication.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 229;



  /**
   * The message ID for the message that will be used if it is not possible to
   * resolve one of the listen addresses.  This takes three arguments, which are
   * the listen address, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_UNKNOWN_LISTEN_ADDRESS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 230;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the set of listen addresses.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_ADDRESS =
           CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 231;



  /**
   * The message ID for the message that will be used if no listen port is
   * specified.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NO_LISTEN_PORT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 232;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the listen port.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 233;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the set of allowed client masks.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOWED_CLIENTS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 234;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the set of denied client masks.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_DENIED_CLIENTS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 235;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to allow LDAPv2 clients.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_LDAPV2 =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 236;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the number of request handlers.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_NUM_REQUEST_HANDLERS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 237;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to send a notice of disconnection
   * to clients whose connections are rejected.  This takes two arguments, which
   * are the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SEND_REJECTION_NOTICE =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 238;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to use the TCP KeepAlive option.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_KEEPALIVE =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 239;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to use the TCP NoDelay option.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_NODELAY =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 240;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to use the SO_REUSEADDR option.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_REUSE_ADDRESS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 241;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the maximum client request size to allow.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_MAX_REQUEST_SIZE =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 242;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to use SSL when accepting
   * connections.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_SSL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 243;



  /**
   * The message ID for the message that will be used if both SSL and StartTLS
   * are enabled.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_HAVE_SSL_AND_STARTTLS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 244;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to allow the StartTLS operation.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_STARTTLS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 245;



  /**
   * The message ID for the message that will be used if an invalid SSL client
   * authentication policy is specified.  This takes two arguments, which are
   * the specified policy and the DN of the configuration entry.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_INVALID_SSL_CLIENT_AUTH_POLICY =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 246;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the policy for requesting/requiring SSL
   * client authentication.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CLIENT_AUTH_POLICY =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 247;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the nickname of the SSL certificate to
   * use.  This takes two arguments, which are the DN of the configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 248;



  /**
   * The message ID for the message that will be used if an address mask defined
   * in the connection handler configuration is invalid.  This takes four
   * arguments, which are the address mask string, the name of the configuration
   * attribute, the DN of the configuration entry, and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 249;



  /**
   * The message ID for the message that will be used to indicate that a new
   * set of allowed clients has been configured.  This takes a single argument,
   * which is the DN of the configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_ALLOWED_CLIENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 250;



  /**
   * The message ID for the message that will be used to indicate that a new
   * set of denied clients has been configured.  This takes a single argument,
   * which is the DN of the configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_DENIED_CLIENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 251;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the allow LDAPv2 configuration option.  This
   * takes two arguments, which are the new value and the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_ALLOW_LDAPV2 =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 252;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the send rejection notice configuration option.
   * This takes two arguments, which are the new value and the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_SEND_REJECTION_NOTICE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 253;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the use keep-alive configuration option.  This
   * takes two arguments, which are the new value and the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_USE_KEEPALIVE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 254;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the use TCP NoDelay configuration option.  This
   * takes two arguments, which are the new value and the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_USE_TCP_NODELAY =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 255;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the max request size configuration option.  This
   * takes two arguments, which are the new value and the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_MAX_REQUEST_SIZE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 256;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the allow StartTLS configuration option.  This
   * takes two arguments, which are the new value and the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_ALLOW_STARTTLS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 257;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether the LDAP connection handler
   * should keep statistical data.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_KEEP_STATS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 258;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to maintain LDAP statistics.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_KEEP_STATS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 259;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the keep statistics configuration option.  This
   * takes two arguments, which are the new value and the DN of the
   * configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_KEEP_STATS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 260;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as the value of an ASN.1 long element because the
   * byte array was not between one and eight bytes long.  This takes a single
   * integer argument, which is the length of the provided array.
   */
  public static final int MSGID_ASN1_LONG_SET_VALUE_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 261;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a long element because the value of the
   * provided element was not between one and eight bytes.  This takes a single
   * integer argument, which is the length of the value of the provided element.
   */
  public static final int MSGID_ASN1_LONG_DECODE_ELEMENT_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 262;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode a byte array as an ASN.1 long element because the decoded value
   * was not between one and eight bytes.  This takes a single integer argument,
   * which is the decoded value.
   */
  public static final int MSGID_ASN1_LONG_DECODE_ARRAY_INVALID_LENGTH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 263;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the internal operation DN.  This takes two arguments,
   * which are the string representation of that DN and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_INTERNAL_CANNOT_DECODE_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 264;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying which SSL protocols should be allowed
   * for SSL and StartTLS communication.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_PROTOCOLS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 265;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the set of SSL protocols to use.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_PROTOCOLS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 266;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying which SSL cipher suites should be
   * allowed for SSL and StartTLS communication.
   */
  public static final int
       MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_CIPHERS =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 267;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the set of SSL cipher suites to use.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CIPHERS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 268;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the enabled SSL protocols configuration option.
   * This takes two arguments, which are a string representation of the new
   * enabled protocols and the DN of the configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_SSL_PROTOCOLS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 269;



  /**
   * The message ID for the message that will be used to indicate that a new
   * value has been applied for the enabled SSL cipher suites configuration
   * option.  This takes two arguments, which are a string representation of the
   * new enabled cipher suites and the DN of the configuration entry.
   */
  public static final int MSGID_LDAP_CONNHANDLER_NEW_SSL_CIPHERS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 270;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * use StartTLS on a connection that is already using some other security
   * mechanism.  This takes a single argument, which is the name of the security
   * mechanism that is already in use.
   */
  public static final int MSGID_LDAP_TLS_EXISTING_SECURITY_PROVIDER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 271;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * use StartTLS when the LDAP connection handler has StartTLS support
   * disabled.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_TLS_STARTTLS_NOT_ALLOWED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 272;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create a TLS security provider instance for a client
   * connection that is to use StartTLS.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_TLS_CANNOT_CREATE_TLS_PROVIDER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 273;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * enable the TLS connection security provider on a connection that does not
   * have an instance of that provider.  This does not take any arguments.
   */
  public static final int MSGID_LDAP_TLS_NO_PROVIDER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 274;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * close a TLS session while leaving the underlying TCP connection open.  This
   * does not take any arguments.
   */
  public static final int MSGID_LDAP_TLS_CLOSURE_NOT_ALLOWED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 275;



  /**
   * The message ID for the message that will be used for the message logged
   * when the LDAP connection handler starts listening for client connections.
   * This takes a single argument, which is a string representation of the
   * connection handler.
   */
  public static final int MSGID_LDAP_CONNHANDLER_STARTED_LISTENING =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 276;



  /**
   * The message ID for the message that will be used for the message logged
   * when the LDAP connection handler stops listening for client connections.
   * This takes a single argument, which is a string representation of the
   * connection handler.
   */
  public static final int MSGID_LDAP_CONNHANDLER_STOPPED_LISTENING =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 277;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a paged results control value because the
   * element was null. This does not take any arguments.
   */
  public static final int MSGID_LDAP_PAGED_RESULTS_DECODE_NULL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 278;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a paged results control value because the
   * element could not be decoded as a sequence.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_PAGED_RESULTS_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 279;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a paged results control value because the
   * sequence contained an invalid number of elements.  This takes a single
   * integer argument, which is the actual number of elements in the sequence.
   */
  public static final int
       MSGID_LDAP_PAGED_RESULTS_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 280;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a paged results control value because the size
   * could not be decoded.  This takes a single argument, which is the string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_PAGED_RESULTS_DECODE_SIZE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 281;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an ASN.1 element as a paged results control value because the size
   * could not be decoded.  This takes a single argument, which is the string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAP_PAGED_RESULTS_DECODE_COOKIE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 282;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an LDAP assertion control because it did not have a value.  This
   * does not take any arguments.
   */
  public static final int MSGID_LDAPASSERT_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 283;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an LDAP assertion control because an error occurred while trying to
   * decode the control value contents as an ASN.1 element.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPASSERT_INVALID_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 284;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an LDAP pre-read request control because it did not have a value.
   * This does not take any arguments.
   */
  public static final int MSGID_PREREADREQ_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 285;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the value of the pre-read request control.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_PREREADREQ_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 286;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an LDAP post-read request control because it did not have a value.
   * This does not take any arguments.
   */
  public static final int MSGID_POSTREADREQ_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 287;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the value of the post-read request control.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_POSTREADREQ_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 288;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an LDAP pre-read response control because it did not have a value.
   * This does not take any arguments.
   */
  public static final int MSGID_PREREADRESP_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 289;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the value of the pre-read response control.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_PREREADRESP_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 290;



  /**
   * The message ID for the message that will be used if it is not possible to
   * decode an LDAP post-read response control because it did not have a value.
   * This does not take any arguments.
   */
  public static final int MSGID_POSTREADRESP_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 291;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the value of the post-read response control.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_POSTREADRESP_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 292;



  /**
   * The message ID for the message that will be used if a proxied auth V1
   * control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_PROXYAUTH1_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 293;



  /**
   * The message ID for the message that will be used if a proxied auth V1
   * control value has a sequence with an invalid number of elements.  This
   * takes a single argument, which is the number of elements found.
   */
  public static final int MSGID_PROXYAUTH1_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 294;



  /**
   * The message ID for the message that will be used if a proxied auth V1
   * control value cannot be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PROXYAUTH1_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 295;



  /**
   * The message ID for the message that will be used if the target user for the
   * proxied auth V1 control does not exist.  This takes a single argument,
   * which is the DN of the target user.
   */
  public static final int MSGID_PROXYAUTH1_NO_SUCH_USER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 296;



  /**
   * The message ID for the message that will be used if a proxied auth V2
   * control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_PROXYAUTH2_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 297;



  /**
   * The message ID for the message that will be used if a proxied auth V2
   * control value cannot be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PROXYAUTH2_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 298;



  /**
   * The message ID for the message that will be used if the Directory Server
   * is not configured with an identity mapper for use with the proxied
   * authorization control.  This does not take any arguments.
   */
  public static final int MSGID_PROXYAUTH2_NO_IDENTITY_MAPPER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 299;



  /**
   * The message ID for the message that will be used if the proxied
   * authorization V2 control contains an invalid authorization ID.  This takes
   * a single argument, which is the invalid authorization ID.
   */
  public static final int MSGID_PROXYAUTH2_INVALID_AUTHZID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 300;



  /**
   * The message ID for the message that will be used if the target user for the
   * proxied auth V2 control does not exist.  This takes a single argument,
   * which is the authorization ID from the proxied auth control.
   */
  public static final int MSGID_PROXYAUTH2_NO_SUCH_USER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 301;



  /**
   * The message ID for the message that will be used if an integer value does
   * not correspond with a valid change type.  This takes a single argument,
   * which is the provided integer value.
   */
  public static final int MSGID_PSEARCH_CHANGETYPES_INVALID_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 302;



  /**
   * The message ID for the message that will be used if the an encoded set of
   * change types indicates that there are no change types.  This does not take
   * any arguments.
   */
  public static final int MSGID_PSEARCH_CHANGETYPES_NO_TYPES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 303;



  /**
   * The message ID for the message that will be used if the an encoded set of
   * change types is outside the range of acceptable values.  This takes a
   * single argument, which is the encoded change types value.
   */
  public static final int MSGID_PSEARCH_CHANGETYPES_INVALID_TYPES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 304;



  /**
   * The message ID for the message that will be used if a persistent search
   * control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_PSEARCH_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 305;



  /**
   * The message ID for the message that will be used if a persistent search
   * control value sequence has an invalid number of elements.  This takes a
   * single argument, which is the number of elements contained in the value.
   */
  public static final int MSGID_PSEARCH_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 306;



  /**
   * The message ID for the message that will be used if a persistent search
   * control value cannot be decoded.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PSEARCH_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 307;



  /**
   * The message ID for the message that will be used if an entry change
   * notification control does not have a value.  This does not take any
   * arguments.
   */
  public static final int MSGID_ECN_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 308;



  /**
   * The message ID for the message that will be used if an entry change
   * notification control value sequence has an invalid number of elements.
   * This takes a single argument, which is the number of elements contained in
   * the value.
   */
  public static final int MSGID_ECN_INVALID_ELEMENT_COUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 309;



  /**
   * The message ID for the message that will be used if an entry change
   * notification control has a previous DN but the change type is something
   * other than modify DN.  This takes a single argument, which is a string
   * representation of the change type.
   */
  public static final int MSGID_ECN_ILLEGAL_PREVIOUS_DN =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 310;



  /**
   * The message ID for the message that will be used if an entry change
   * notification control has two elements in the value sequence but the second
   * has a type that is not appropriate for either a previous DN or a change
   * number.  This takes a single argument, which is a string containing a hex
   * representation of the ASN.1 element type.
   */
  public static final int MSGID_ECN_INVALID_ELEMENT_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 311;




  /**
   * The message ID for the message that will be used if an entry change
   * notification control value cannot be decoded.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_ECN_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 312;



  /**
   * The message ID for the message that will be used if an authorization ID
   * response control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_AUTHZIDRESP_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 313;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode an intermediate response protocol op as an ASN.1
   * sequence.  This takes a single argument, which is a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDAP_INTERMEDIATE_RESPONSE_DECODE_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 314;



  /**
   * The message ID for the message that will be used if the intermediate
   * response sequence contains an invalid number of elements.  This takes a
   * single argument, which is the number of elements in the sequence.
   */
  public static final int
       MSGID_LDAP_INTERMEDIATE_RESPONSE_DECODE_INVALID_ELEMENT_COUNT =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 315;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode the OID of an intermediate response protocol op.  This
   * takes a single argument, which is a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_OID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 316;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode the value of an intermediate response protocol op.
   * This takes a single argument, which is a message explaining the problem
   * that occurred.
   */
  public static final int MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 317;



  /**
   * The message ID for the message that will be used if an intermediate
   * response sequence element contains an invalid BER type.  This takes a
   * single argument, which is a hex string representation of the element type.
   */
  public static final int
       MSGID_LDAP_INTERMEDIATE_RESPONSE_INVALID_ELEMENT_TYPE =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 318;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the accept backlog.  This does not take
   * any arguments.
   */
  public static final int MSGID_LDAP_CONNHANDLER_DESCRIPTION_BACKLOG =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 319;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the backlog.  This takes two arguments,
   * which are the DN of the configuration entry and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_BACKLOG =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 320;



  /**
   * The message ID for the message that will be used if an LDAP filter contains
   * a filter type that is not valid for use in a matched values filter.  This
   * takes two arguments, which are a string representation of the provided LDAP
   * filter and a string representation of the filter type.
   */
  public static final int MSGID_MVFILTER_INVALID_LDAP_FILTER_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 321;



  /**
   * The message ID for the message that will be used if an LDAP filter is an
   * extensible match filter with the DN attributes flag set.  This takes a
   * single argument, which is a string representation of the provided LDAP
   * filter.
   */
  public static final int MSGID_MVFILTER_INVALID_DN_ATTRIBUTES_FLAG =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 322;



  /**
   * The message ID for the message that will be used if an AVA sequence does
   * not contain exactly two elements.  This takes a single argument, which is
   * the number of elements that it did contain.
   */
  public static final int MSGID_MVFILTER_INVALID_AVA_SEQUENCE_SIZE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 323;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode an attribute value assertion.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_MVFILTER_CANNOT_DECODE_AVA =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 324;



  /**
   * The message ID for the message that will be used if a substring sequence
   * does not contain exactly two elements.  This takes a single argument, which
   * is the number of elements that it did contain.
   */
  public static final int MSGID_MVFILTER_INVALID_SUBSTRING_SEQUENCE_SIZE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 325;



  /**
   * The message ID for the message that will be used if a substring filter does
   * not contain any elements.  This does not take any arguments.
   */
  public static final int MSGID_MVFILTER_NO_SUBSTRING_ELEMENTS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 326;



  /**
   * The message ID for the message that will be used if a substring filter has
   * multiple subInitial components.  This does not take any arguments.
   */
  public static final int MSGID_MVFILTER_MULTIPLE_SUBINITIALS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 327;



  /**
   * The message ID for the message that will be used if a substring filter has
   * multiple subFinal components.  This does not take any arguments.
   */
  public static final int MSGID_MVFILTER_MULTIPLE_SUBFINALS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 328;



  /**
   * The message ID for the message that will be used if a substring filter has
   * an element with an invalid BER type.  This takes a single argument, which
   * is a hex string representation of the invalid type.
   */
  public static final int MSGID_MVFILTER_INVALID_SUBSTRING_ELEMENT_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 329;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode a substring filter.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_MVFILTER_CANNOT_DECODE_SUBSTRINGS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 330;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode a presence filter.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_MVFILTER_CANNOT_DECODE_PRESENT_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 331;



  /**
   * The message ID for the message that will be used if an extensible match
   * sequence contains an invalid number of elements.  This takes a single
   * argument, which is the number of elements contained in the sequence.
   */
  public static final int MSGID_MVFILTER_INVALID_EXTENSIBLE_SEQUENCE_SIZE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 332;



  /**
   * The message ID for the message that will be used if an extensible match
   * sequence contains multiple matching rule IDs.  This does not take any
   * arguments.
   */
  public static final int MSGID_MVFILTER_MULTIPLE_MATCHING_RULE_IDS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 333;



  /**
   * The message ID for the message that will be used if an extensible match
   * sequence contains multiple attribute type elements.  This does not take any
   * arguments.
   */
  public static final int MSGID_MVFILTER_MULTIPLE_ATTRIBUTE_TYPES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 334;



  /**
   * The message ID for the message that will be used if an extensible match
   * sequence contains multiple assertion value elements.  This does not take
   * any arguments.
   */
  public static final int MSGID_MVFILTER_MULTIPLE_ASSERTION_VALUES =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 335;



  /**
   * The message ID for the message that will be used if an extensible match
   * sequence contains an element with an invalid type.  This takes a single
   * argument, which is a hex string representation of the invalid type.
   */
  public static final int MSGID_MVFILTER_INVALID_EXTENSIBLE_ELEMENT_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 336;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode an extensible match filter.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_MVFILTER_CANNOT_DECODE_EXTENSIBLE_MATCH =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 337;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode an extensible match filter.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_MVFILTER_INVALID_ELEMENT_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 338;



  /**
   * The message ID for the message that will be used if a matched values
   * control did not have a value.  This does not take any arguments.
   */
  public static final int MSGID_MATCHEDVALUES_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 339;



  /**
   * The message ID for the message that will be used if an error occurs while
   * decoding a matched values control value as a sequence.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_MATCHEDVALUES_CANNOT_DECODE_VALUE_AS_SEQUENCE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 340;



  /**
   * The message ID for the message that will be used if a matched values
   * control did not contain any filters.  This does not take any arguments.
   */
  public static final int MSGID_MATCHEDVALUES_NO_FILTERS =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 341;



  /**
   * The message ID for the message that will be used if a password expired
   * control has a value.  This does not take any arguments.
   */
  public static final int MSGID_PWEXPIRED_CONTROL_HAS_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 342;



  /**
   * The message ID for the message that will be used if a password expiring
   * control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_PWEXPIRING_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 343;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to decode the number of seconds until expiration.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 344;




  /**
   * The message ID for the message that will be used if the client attempts to
   * request an operation with the same message ID as another outstanding
   * operation.  This takes a single argument, which is the conflicting message
   * ID.
   */
  public static final int MSGID_LDAP_CLIENT_DUPLICATE_MESSAGE_ID =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_WARNING | 345;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to enqueue a request from the client.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAP_CLIENT_CANNOT_ENQUEUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_WARNING | 346;


  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the port on which to accept
   * administrative connections.
   */
  public static final int MSGID_JMX_CONNHANDLER_DESCRIPTION_LISTEN_PORT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 347;

  /**
   * The message ID for the message that will be used if no listen port is
   * specified.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_JMX_CONNHANDLER_NO_LISTEN_PORT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 348;

  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the listen port.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 349;


  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute indicating whether to use SSL when accepting
   * connections.
   */
  public static final int MSGID_JMX_CONNHANDLER_DESCRIPTION_USE_SSL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 350;


  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine whether to use SSL when accepting
   * connections.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_USE_SSL =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 351;

  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the nickname of the certificate to use
   * for SSL and StartTLS communication.
   */
  public static final int MSGID_JMX_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 352;

  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to determine the nickname of the SSL certificate to
   * use.  This takes two arguments, which are the DN of the configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 353;



  /**
   * The message ID for the message that will be used if a password policy
   * request control has a value.  This does not take any arguments.
   */
  public static final int MSGID_PWPOLICYREQ_CONTROL_HAS_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 354;



  /**
   * The message ID for the message that will be used if a password policy
   * response control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_PWPOLICYRES_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 355;



  /**
   * The message ID for the message that will be used if the warning element has
   * an invalid type.  This takes a single argument, which is a hex string
   * representation of the warning element type.
   */
  public static final int MSGID_PWPOLICYRES_INVALID_WARNING_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 356;



  /**
   * The message ID for the message that will be used if the error element has
   * an invalid type.  This takes a single argument, which is the invalid
   * error element value as an integer.
   */
  public static final int MSGID_PWPOLICYRES_INVALID_ERROR_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 357;



  /**
   * The message ID for the message that will be used if the password policy
   * response control sequence has an element with an invalid type.  This takes
   * a single argument, which is a hex string representation of the element
   * type.
   */
  public static final int MSGID_PWPOLICYRES_INVALID_ELEMENT_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 358;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the control value.  This takes a single argument, which is
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_PWPOLICYRES_DECODE_ERROR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 359;



  /**
   * The message ID for the message that will be used as the description of the
   * passwordExpired error.
   */
  public static final int MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_EXPIRED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 360;



  /**
   * The message ID for the message that will be used as the description of the
   * accountLocked error.
   */
  public static final int MSGID_PWPERRTYPE_DESCRIPTION_ACCOUNT_LOCKED =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 361;



  /**
   * The message ID for the message that will be used as the description of the
   * changeAfterReset error.
   */
  public static final int MSGID_PWPERRTYPE_DESCRIPTION_CHANGE_AFTER_RESET =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 362;



  /**
   * The message ID for the message that will be used as the description of the
   * passwordModNotAllowed error.
   */
  public static final int
       MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_MOD_NOT_ALLOWED =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 363;



  /**
   * The message ID for the message that will be used as the description of the
   * mustSupplyOldPassword error.
   */
  public static final int
       MSGID_PWPERRTYPE_DESCRIPTION_MUST_SUPPLY_OLD_PASSWORD =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 364;



  /**
   * The message ID for the message that will be used as the description of the
   * passwordExpired error.
   */
  public static final int
       MSGID_PWPERRTYPE_DESCRIPTION_INSUFFICIENT_PASSWORD_QUALITY =
            CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 365;



  /**
   * The message ID for the message that will be used as the description of the
   * passwordTooShort error.
   */
  public static final int MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_SHORT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 366;



  /**
   * The message ID for the message that will be used as the description of the
   * passwordTooYoung error.
   */
  public static final int MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_YOUNG =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 367;



  /**
   * The message ID for the message that will be used as the description of the
   * passwordInHistory error.
   */
  public static final int MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_IN_HISTORY =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 368;



  /**
   * The message ID for the message that will be used as the description of the
   * timeBeforeExpiration warning.
   */
  public static final int MSGID_PWPWARNTYPE_DESCRIPTION_TIME_BEFORE_EXPIRATION =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 369;



  /**
   * The message ID for the message that will be used as the description of the
   * graceAuthNsRemaining warning.
   */
  public static final int MSGID_PWPWARNTYPE_DESCRIPTION_GRACE_LOGINS_REMAINING =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_INFORMATIONAL | 370;



  /**
   * The message ID for the message that will be used if the server is unable to
   * obtain a lock on the proxied authorization V1 control user.  This takes a
   * single argument, which is the DN of the target user.
   */
  public static final int MSGID_PROXYAUTH1_CANNOT_LOCK_USER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 371;



  /**
   * The message ID for the message that will be used if the proxied auth V1
   * control cannot be used because the attempt was rejected by password policy
   * processing.  This takes a single argument, which is the DN of the target
   * user.
   */
  public static final int MSGID_PROXYAUTH1_UNUSABLE_ACCOUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 372;



  /**
   * The message ID for the message that will be used if the server is unable to
   * obtain a lock on the proxied authorization V2 control user.  This takes a
   * single argument, which is the DN of the target user.
   */
  public static final int MSGID_PROXYAUTH2_CANNOT_LOCK_USER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 373;



  /**
   * The message ID for the message that will be used if the proxied auth V2
   * control cannot be used because the attempt was rejected by password policy
   * processing.  This takes a single argument, which is the DN of the target
   * user.
   */
  public static final int MSGID_PROXYAUTH2_UNUSABLE_ACCOUNT =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 374;



  /**
   * The message ID for the message that will be used if an account availability
   * request control has a value.  This does not take any arguments.
   */
  public static final int MSGID_ACCTUSABLEREQ_CONTROL_HAS_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 375;



  /**
   * The message ID for the message that will be used if an account availability
   * response control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_ACCTUSABLERES_NO_CONTROL_VALUE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 376;



  /**
   * The message ID for the message that will be used if an account availability
   * response control has a MORE_INFO element with an unknown type.  This takes
   * a single argument, which is a hex string representation of the unknown
   * type.
   */
  public static final int MSGID_ACCTUSABLERES_UNKNOWN_UNAVAILABLE_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 377;



  /**
   * The message ID for the message that will be used if an account availability
   * response control has a value element with an unknown type.  This takes a
   * single argument, which is a hex string representation of the unknown type.
   */
  public static final int MSGID_ACCTUSABLERES_UNKNOWN_VALUE_ELEMENT_TYPE =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 378;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode the value of an account usability response control.
   * This takes a single argument, which is a message explaining the problem
   * that occurred.
   */
  public static final int MSGID_ACCTUSABLERES_DECODE_ERROR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 379;

  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode an invalid AddressMask rule prefix.
   */
  public static final int MSGID_ADDRESSMASK_PREFIX_DECODE_ERROR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 380;

    /**
   * The message ID for the message that will be used if an error occurs
   * because a address mask prefix was specified with an wild card
   * character "*".
   */
  public static final int MSGID_ADDRESSMASK_WILDCARD_DECODE_ERROR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 381;

  /**
   * The message ID for the message that will be used if an error occurs
   * because a address mask format was invalid.
   *
   */
  public static final int MSGID_ADDRESSMASK_FORMAT_DECODE_ERROR =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_SEVERE_ERROR | 382;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * send a clear-text response over a client connection that doesn't have a
   * handle to the clear-text security provider.  This takes a single argument,
   * which is a string representation of the client connection.
   */
  public static final int MSGID_LDAP_NO_CLEAR_SECURITY_PROVIDER =
       CATEGORY_MASK_PROTOCOL | SEVERITY_MASK_MILD_ERROR | 383;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_ASN1_NULL_ELEMENT,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "element because the array was null.");
    registerMessage(MSGID_ASN1_SHORT_ELEMENT,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "element because the length of the array (%d bytes) is " +
                    "less than the minimum required for an ASN.1 element (2 " +
                    "bytes).");
    registerMessage(MSGID_ASN1_INVALID_NUM_LENGTH_BYTES,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "element because it contained a multi-byte length with " +
                    "an invalid number of bytes (%d).");
    registerMessage(MSGID_ASN1_TRUNCATED_LENGTH,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "element because it contained a multi-byte length of %d " +
                    "bytes but the array was too short to contain the " +
                    "entire length.");
    registerMessage(MSGID_ASN1_LENGTH_MISMATCH,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "element because the decoded value length (%d bytes) " +
                    "does not equal the number of bytes remaining in the " +
                    "provided array (%d).");
    registerMessage(MSGID_ASN1_ELEMENT_SET_NULL,
                    "Cannot decode the provided byte array as a set of ASN.1 " +
                    "elements because the array was null.");
    registerMessage(MSGID_ASN1_ELEMENT_SET_NO_LENGTH,
                    "Cannot decode the provided byte array as a set of ASN.1 " +
                    "elements because the end of the array was reached after " +
                    "having read the BER type but none of the value for an " +
                    "element.");
    registerMessage(MSGID_ASN1_ELEMENT_SET_INVALID_NUM_LENGTH_BYTES,
                    "Cannot decode the provided byte array as a set of ASN.1 " +
                    "elements because it contained a multi-byte length with " +
                    "an invalid number of bytes (%d).");
    registerMessage(MSGID_ASN1_ELEMENT_SET_TRUNCATED_LENGTH,
                    "Cannot decode the provided byte array as a set of ASN.1 " +
                    "elements because it contained a multi-byte length of %d " +
                    "bytes but the array was too short to contain the " +
                    "entire length.");
    registerMessage(MSGID_ASN1_ELEMENT_SET_TRUNCATED_VALUE,
                    "Cannot decode the provided byte array as a set of ASN.1 " +
                    "elements because the decoded length of an element (%d) " +
                    "is more than the number of bytes remaining (%d).");


    registerMessage(MSGID_ASN1_BOOLEAN_SET_VALUE_NULL,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 Boolean element because the array was null.");
    registerMessage(MSGID_ASN1_BOOLEAN_SET_VALUE_INVALID_LENGTH,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 Boolean element because the array did not have " +
                    "a length of exactly one byte (provided length was %d).");
    registerMessage(MSGID_ASN1_BOOLEAN_DECODE_ELEMENT_NULL,
                    "Cannot decode the provided ASN.1 element as a Boolean " +
                    "element because the provided element was null.");
    registerMessage(MSGID_ASN1_BOOLEAN_DECODE_ELEMENT_INVALID_LENGTH,
                    "Cannot decode the provided ASN.1 element as a Boolean " +
                    "element because the length of the element value was not " +
                    "exactly one byte (actual length was %d).");
    registerMessage(MSGID_ASN1_BOOLEAN_DECODE_ARRAY_NULL,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "Boolean element because the array was null.");
    registerMessage(MSGID_ASN1_BOOLEAN_SHORT_ELEMENT,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "Boolean element because the length of the array (%d " +
                    "bytes) is less than the minimum required for a Boolean " +
                    "element (3 bytes).");
    registerMessage(MSGID_ASN1_BOOLEAN_DECODE_ARRAY_INVALID_LENGTH,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "Boolean element because the decoded value length was " +
                    "not exactly one byte (decoded length was %d).");


    registerMessage(MSGID_ASN1_NULL_SET_VALUE_INVALID_LENGTH,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 null element because the array did not have " +
                    "a length of exactly zero byte (provided length was %d).");
    registerMessage(MSGID_ASN1_NULL_DECODE_ELEMENT_NULL,
                    "Cannot decode the provided ASN.1 element as a null " +
                    "element because the provided element was null.");
    registerMessage(MSGID_ASN1_NULL_DECODE_ELEMENT_INVALID_LENGTH,
                    "Cannot decode the provided ASN.1 element as a null " +
                    "element because the length of the element value was not " +
                    "exactly zero bytes (actual length was %d).");
    registerMessage(MSGID_ASN1_NULL_DECODE_ARRAY_NULL,
                    "Cannot decode the provided byte array as an ASN.1 null " +
                    "element because the array was null.");
    registerMessage(MSGID_ASN1_NULL_DECODE_ARRAY_INVALID_LENGTH,
                    "Cannot decode the provided byte array as an ASN.1 null " +
                    "element because the decoded value length was not " +
                    "exactly zero bytes (decoded length was %d).");


    registerMessage(MSGID_ASN1_OCTET_STRING_DECODE_ELEMENT_NULL,
                    "Cannot decode the provided ASN.1 element as an octet " +
                    "string element because the provided element was null.");
    registerMessage(MSGID_ASN1_OCTET_STRING_DECODE_ARRAY_NULL,
                    "Cannot decode the provided byte array as an ASN.1 octet " +
                    "string element because the array was null.");


    registerMessage(MSGID_ASN1_INTEGER_SET_VALUE_NULL,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 integer element because the array was null.");
    registerMessage(MSGID_ASN1_INTEGER_SET_VALUE_INVALID_LENGTH,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 integer element because the array did not have " +
                    "a length between 1 and 4 bytes (provided length was %d).");
    registerMessage(MSGID_ASN1_INTEGER_DECODE_ELEMENT_NULL,
                    "Cannot decode the provided ASN.1 element as an integer " +
                    "element because the provided element was null.");
    registerMessage(MSGID_ASN1_INTEGER_DECODE_ELEMENT_INVALID_LENGTH,
                    "Cannot decode the provided ASN.1 element as an integer " +
                    "element because the length of the element value was not " +
                    "between one and four bytes (actual length was %d).");
    registerMessage(MSGID_ASN1_INTEGER_DECODE_ARRAY_NULL,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "integer element because the array was null.");
    registerMessage(MSGID_ASN1_INTEGER_SHORT_ELEMENT,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "integer element because the length of the array (%d " +
                    "bytes) is less than the minimum required for an integer " +
                    "element (3 bytes).");
    registerMessage(MSGID_ASN1_INTEGER_DECODE_ARRAY_INVALID_LENGTH,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "integer element because the decoded value length was " +
                    "not between 1 and 4 bytes (decoded length was %d).");


    registerMessage(MSGID_ASN1_LONG_SET_VALUE_INVALID_LENGTH,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 long element because the array did not have " +
                    "a length between 1 and 8 bytes (provided length was %d).");
    registerMessage(MSGID_ASN1_LONG_DECODE_ELEMENT_INVALID_LENGTH,
                    "Cannot decode the provided ASN.1 element as a long " +
                    "element because the length of the element value was not " +
                    "between one and eight bytes (actual length was %d).");
    registerMessage(MSGID_ASN1_LONG_DECODE_ARRAY_INVALID_LENGTH,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "long element because the decoded value length was " +
                    "not between 1 and 8 bytes (decoded length was %d).");


    registerMessage(MSGID_ASN1_ENUMERATED_SET_VALUE_NULL,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 enumerated element because the array was null.");
    registerMessage(MSGID_ASN1_ENUMERATED_SET_VALUE_INVALID_LENGTH,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 enumerated element because the array did not " +
                    "have a length between 1 and 4 bytes (provided length " +
                    "was %d).");
    registerMessage(MSGID_ASN1_ENUMERATED_DECODE_ELEMENT_NULL,
                    "Cannot decode the provided ASN.1 element as an " +
                    "enumerated element because the provided element was " +
                    "null.");
    registerMessage(MSGID_ASN1_ENUMERATED_DECODE_ELEMENT_INVALID_LENGTH,
                    "Cannot decode the provided ASN.1 element as an " +
                    "enumerated element because the length of the element " +
                    "value was not between one and four bytes (actual length " +
                    "was %d).");
    registerMessage(MSGID_ASN1_ENUMERATED_DECODE_ARRAY_NULL,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "enumerated element because the array was null.");
    registerMessage(MSGID_ASN1_ENUMERATED_SHORT_ELEMENT,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "enumerated element because the length of the array (%d " +
                    "bytes) is less than the minimum required for an " +
                    "enumerated element (3 bytes).");
    registerMessage(MSGID_ASN1_ENUMERATED_DECODE_ARRAY_INVALID_LENGTH,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "enumerated element because the decoded value length was " +
                    "not between 1 and 4 bytes (decoded length was %d).");


    registerMessage(MSGID_ASN1_SEQUENCE_SET_VALUE_NULL,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 sequence element because the array was null.");
    registerMessage(MSGID_ASN1_SEQUENCE_DECODE_ELEMENT_NULL,
                    "Cannot decode the provided ASN.1 element as a sequence " +
                    "element because the provided element was null.");
    registerMessage(MSGID_ASN1_SEQUENCE_DECODE_ARRAY_NULL,
                    "Cannot decode the provided byte array as an ASN.1 " +
                    "sequence element because the array was null.");


    registerMessage(MSGID_ASN1_SET_SET_VALUE_NULL,
                    "Cannot decode the provided byte array as the value of " +
                    "an ASN.1 set element because the array was null.");
    registerMessage(MSGID_ASN1_SET_DECODE_ELEMENT_NULL,
                    "Cannot decode the provided ASN.1 element as a set " +
                    "element because the provided element was null.");
    registerMessage(MSGID_ASN1_SET_DECODE_ARRAY_NULL,
                    "Cannot decode the provided byte array as an ASN.1 set " +
                    "element because the array was null.");


    registerMessage(MSGID_ASN1_READER_MAX_SIZE_EXCEEDED,
                    "Cannot decode the data read as an ASN.1 element " +
                    "because the decoded element length of %d bytes was " +
                    "larger than the maximum allowed element length of %d " +
                    "bytes.  The underlying input stream has been closed " +
                    "and this reader may no longer be used.");


    registerMessage(MSGID_LDAP_MESSAGE_DECODE_NULL,
                    "Cannot decode the provided ASN.1 sequence as an LDAP " +
                    "message because the sequence was null.");
    registerMessage(MSGID_LDAP_MESSAGE_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 sequence as an LDAP " +
                    "message because the sequence contained an invalid " +
                    "number of elements (expected 2 or 3, got %d).");
    registerMessage(MSGID_LDAP_MESSAGE_DECODE_MESSAGE_ID,
                    "Cannot decode the provided ASN.1 sequence as an LDAP " +
                    "message because the first element of the sequence " +
                    "could not be decoded as an integer message ID:  %s.");
    registerMessage(MSGID_LDAP_MESSAGE_DECODE_PROTOCOL_OP,
                    "Cannot decode the provided ASN.1 sequence as an LDAP " +
                    "message because the second element of the sequence " +
                    "could not be decoded as the protocol op:  %s.");
    registerMessage(MSGID_LDAP_MESSAGE_DECODE_CONTROLS,
                    "Cannot decode the provided ASN.1 sequence as an LDAP " +
                    "message because the third element of the sequence " +
                    "could not be decoded as the set of controls:  %s.");


    registerMessage(MSGID_LDAP_CONTROL_DECODE_NULL,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "control because the element was null.");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "control because the element could not be decoded as a " +
                    "sequence:  %s.");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "control because the control sequence contained an " +
                    "invalid number of elements (expected 1 to 3, got %d).");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_OID,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "control because the OID could not be decoded as a " +
                    "string:  %s.");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_CRITICALITY,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "control because the criticality could not be decoded as " +
                    "Boolean value:  %s.");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_VALUE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "control because the value could not be decoded as an " +
                    "octet string:  %s.");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_INVALID_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "control because the BER type for the second element " +
                    "in the sequence was invalid (expected 01 or 04, got %x).");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_CONTROLS_NULL,
                    "Cannot decode the provided ASN.1 element as a set of " +
                    "LDAP controls because the element was null.");
    registerMessage(MSGID_LDAP_CONTROL_DECODE_CONTROLS_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as a set of " +
                    "LDAP controls because the element could not be decoded " +
                    "as a sequence:  %s.");


    registerMessage(MSGID_LDAP_ABANDON_REQUEST_DECODE_ID,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "abandon request protocol op because a problem occurred " +
                    "while trying to obtain the message ID of the operation " +
                    "to abandon:  %s.");
    registerMessage(MSGID_LDAP_ABANDON_INVALID_MESSAGE_TYPE,
                    "Cannot convert the provided LDAP message (%s) to an " +
                    "abandon operation:  %s.");


    registerMessage(MSGID_LDAP_RESULT_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "result protocol op because a problem occurred while " +
                    "trying to parse the result sequence:  %s.");
    registerMessage(MSGID_LDAP_RESULT_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "result protocol op because the result sequence did not " +
                    "contain a valid number of elements (expected 3 or 4, " +
                    "got %d).");
    registerMessage(MSGID_LDAP_RESULT_DECODE_RESULT_CODE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "result protocol op because the first element in the " +
                    "result sequence could not be decoded as an integer " +
                    "result code:  %s.");
    registerMessage(MSGID_LDAP_RESULT_DECODE_MATCHED_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "result protocol op because the second element in the " +
                    "result sequence could not be decoded as the matched " +
                    "DN:  %s.");
    registerMessage(MSGID_LDAP_RESULT_DECODE_ERROR_MESSAGE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "result protocol op because the third element in the " +
                    "result sequence could not be decoded as the error " +
                    "message:  %s.");
    registerMessage(MSGID_LDAP_RESULT_DECODE_REFERRALS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "result protocol op because the fourth element in the " +
                    "result sequence could not be decoded as a set of " +
                    "referral URLs:  %s.");


    registerMessage(MSGID_LDAP_BIND_RESULT_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind response protocol op because the result sequence " +
                    "did not contain a valid number of elements (expected 3 " +
                    "to 5, got %d).");
    registerMessage(MSGID_LDAP_BIND_RESULT_DECODE_SERVER_SASL_CREDENTIALS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind response protocol op because the final element in " +
                    "the result sequence could not be decoded as the server " +
                    "SASL credentials:  %s.");
    registerMessage(MSGID_LDAP_BIND_RESULT_DECODE_INVALID_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind response protocol op because the BER type for the " +
                    "fourth element in the sequence was invalid (expected A3 " +
                    "or 87, got %x).");


    registerMessage(MSGID_LDAP_EXTENDED_RESULT_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind response protocol op because the result sequence " +
                    "did not contain a valid number of elements (expected 3 " +
                    "to 6, got %d).");
    registerMessage(MSGID_LDAP_EXTENDED_RESULT_DECODE_REFERRALS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind response protocol op because the set of referral " +
                    "URLs could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_EXTENDED_RESULT_DECODE_OID,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind response protocol op because the response OID " +
                    "could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_EXTENDED_RESULT_DECODE_VALUE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind response protocol op because the response value " +
                    "could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_EXTENDED_RESULT_DECODE_INVALID_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "extended response protocol op because one of the " +
                    "elements it contained had an invalid BER type (expected " +
                    "A3, 8A, or 8B, got %x).");


    registerMessage(MSGID_LDAP_UNBIND_DECODE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "unbind request protocol op:  %s.");
    registerMessage(MSGID_LDAP_UNBIND_INVALID_MESSAGE_TYPE,
                    "Cannot convert the provided LDAP message (%s) to an " +
                    "unbind operation:  %s.");


    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because the element could " +
                    "not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because the request sequence " +
                    "had an invalid number of elements (expected 3, got %d).");
    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_VERSION,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because the protocol " +
                    "version could not be decoded as an integer:  %s.");
    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because the bind DN could not " +
                    "be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_PASSWORD,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because the password to use " +
                    "for simple authentication could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_SASL_INFO,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because the SASL " +
                    "authentication information could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_INVALID_CRED_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because the authentication " +
                    "info element had an invalid BER type (expected 80 or " +
                    "A3, got %x).");
    registerMessage(MSGID_LDAP_BIND_REQUEST_DECODE_CREDENTIALS,
                    "Cannot decoded the provided ASN.1 element as an LDAP " +
                    "bind request protocol op because an unexpected error " +
                    "occurred while trying to decode the authentication " +
                    "info element:  %s.");


    registerMessage(MSGID_LDAP_COMPARE_REQUEST_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "compare request protocol op because the element could " +
                    "not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_COMPARE_REQUEST_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "compare request protocol op because the request " +
                    "sequence had an invalid number of elements (expected 2, " +
                    "got %d).");
    registerMessage(MSGID_LDAP_COMPARE_REQUEST_DECODE_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "compare request protocol op because the target DN could " +
                    "not be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_COMPARE_REQUEST_DECODE_AVA,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "compare request protocol op because the attribute value " +
                    "assertion could not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_COMPARE_REQUEST_DECODE_AVA_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "compare request protocol op because the attribute value " +
                    "assertion sequence had an invalid number of elements " +
                    "(expected 2, got %d).");
    registerMessage(MSGID_LDAP_COMPARE_REQUEST_DECODE_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "compare request protocol op because the attribute type " +
                    "could not be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_COMPARE_REQUEST_DECODE_VALUE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "compare request protocol op because the assertion value " +
                    "could not be properly decoded:  %s.");


    registerMessage(MSGID_LDAP_DELETE_REQUEST_DECODE_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "delete request protocol op because the target DN could " +
                    "not be properly decoded:  %s.");


    registerMessage(MSGID_LDAP_EXTENDED_REQUEST_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "extended request protocol op because the element could " +
                    "not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_EXTENDED_REQUEST_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "extended request protocol op because the request " +
                    "sequence had an invalid number of elements (expected 1 " +
                    "or 2, got %d).");
    registerMessage(MSGID_LDAP_EXTENDED_REQUEST_DECODE_OID,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "extended request protocol op because the OID could not " +
                    "be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_EXTENDED_REQUEST_DECODE_VALUE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "extended request protocol op because the value could " +
                    "not be properly decoded:  %s.");


    registerMessage(MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify DN request protocol op because the element could " +
                    "not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify DN request protocol op because the request " +
                    "sequence had an invalid number of elements (expected 3 " +
                    "or 4, got %d).");
    registerMessage(MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify DN request protocol op because the entry DN " +
                    "could not be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_RDN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify DN request protocol op because the new RDN could " +
                    "not be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_DELETE_OLD_RDN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify DN request protocol op because the deleteOldRDN " +
                    "flag could not be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_MODIFY_DN_REQUEST_DECODE_NEW_SUPERIOR,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify DN request protocol op because the new superior " +
                    "DN could not be properly decoded:  %s.");


    registerMessage(MSGID_LDAP_ATTRIBUTE_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "attribute because the element could not be decoded as a " +
                    "sequence:  %s.");
    registerMessage(MSGID_LDAP_ATTRIBUTE_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "attribute because the request sequence had an invalid " +
                    "number of elements (expected 2, got %d).");
    registerMessage(MSGID_LDAP_ATTRIBUTE_DECODE_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "attribute because the attribute type could not be " +
                    "decoded:  %s.");
    registerMessage(MSGID_LDAP_ATTRIBUTE_DECODE_VALUES,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "attribute because the set of values could not be " +
                    "decoded:  %s.");


    registerMessage(MSGID_LDAP_ADD_REQUEST_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP add " +
                    "request protocol op because the element could not be " +
                    "decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_ADD_REQUEST_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP add " +
                    "request protocol op because the request sequence had an " +
                    "invalid number of elements (expected 2, got %d).");
    registerMessage(MSGID_LDAP_ADD_REQUEST_DECODE_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP add " +
                    "request protocol op because the entry DN could not be " +
                    "decoded:  %s.");
    registerMessage(MSGID_LDAP_ADD_REQUEST_DECODE_ATTRS,
                    "Cannot decode the provided ASN.1 element as an LDAP add " +
                    "request protocol op because the set of attributes could " +
                    "not be decoded:  %s.");


    registerMessage(MSGID_LDAP_MODIFICATION_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modification because the element could not be decoded " +
                    "as a sequence:  %s.");
    registerMessage(MSGID_LDAP_MODIFICATION_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modification because the request sequence had an " +
                    "invalid number of elements (expected 2, got %d).");
    registerMessage(MSGID_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modification because it contained an invalid " +
                    "modification type (%d).");
    registerMessage(MSGID_LDAP_MODIFICATION_DECODE_MOD_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modification because the modification type could not be " +
                    "decoded:  %s.");
    registerMessage(MSGID_LDAP_MODIFICATION_DECODE_ATTR,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modification because the attribute could not be " +
                    "decoded:  %s.");


    registerMessage(MSGID_LDAP_MODIFY_REQUEST_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify request protocol op because the element could " +
                    "not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_MODIFY_REQUEST_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify request protocol op because the request sequence " +
                    "had an invalid number of elements (expected 2, got %d).");
    registerMessage(MSGID_LDAP_MODIFY_REQUEST_DECODE_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify request protocol op because the entry DN could " +
                    "not be decoded:  %s.");
    registerMessage(MSGID_LDAP_MODIFY_REQUEST_DECODE_MODS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "modify request protocol op because the set of " +
                    "modifications could not be decoded:  %s.");


    registerMessage(MSGID_LDAP_SEARCH_ENTRY_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search result entry protocol op because the element " +
                    "could not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_ENTRY_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search result entry protocol op because the request " +
                    "sequence had an invalid number of elements (expected 2, " +
                    "got %d).");
    registerMessage(MSGID_LDAP_SEARCH_ENTRY_DECODE_DN,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search result entry protocol op because the entry DN " +
                    "could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_ENTRY_DECODE_ATTRS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search result entry protocol op because the set of " +
                    "attributes could not be decoded:  %s.");


    registerMessage(MSGID_LDAP_SEARCH_REFERENCE_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search result reference protocol op because the " +
                    "element could not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REFERENCE_DECODE_URLS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search result reference protocol op because a problem " +
                    "occurred while trying to decode the sequence elements " +
                    "as referral URLs:  %s.");


    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the element could " +
                    "not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the request sequence " +
                    "had an invalid number of elements (expected 8, got %d).");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_BASE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the base DN could " +
                    "not be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_SCOPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the provided scope " +
                    "value (%d) is invalid.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_SCOPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the scope could not " +
                    "be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_DEREF,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the provided alias " +
                    "dereferencing policy value (%d) is invalid.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_DEREF,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the alias " +
                    "dereferencing policy could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_SIZE_LIMIT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the size limit could " +
                    "not be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_TIME_LIMIT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the time limit could " +
                    "not be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_TYPES_ONLY,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the typesOnly flag " +
                    "could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_FILTER,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the filter could not " +
                    "be decoded:  %s.");
    registerMessage(MSGID_LDAP_SEARCH_REQUEST_DECODE_ATTRIBUTES,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search request protocol op because the requested " +
                    "attribute set could not be decoded:  %s.");


    registerMessage(MSGID_LDAP_PROTOCOL_OP_DECODE_NULL,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "protocol op because the element was null.");
    registerMessage(MSGID_LDAP_PROTOCOL_OP_DECODE_INVALID_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "protocol op because the element had an invalid BER " +
                    "type (%x) for an LDAP protocol op.");


    registerMessage(MSGID_LDAP_FILTER_DECODE_NULL,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the element was null.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_INVALID_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the element had an invalid BER " +
                    "type (%x) for a search filter.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_COMPOUND_SET,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the compound filter set could " +
                    "not be decoded:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_COMPOUND_COMPONENTS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because an unexpected error occurred " +
                    "while trying to decode one of the compound filter " +
                    "components.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_NOT_ELEMENT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the value of the element cannot " +
                    "itself be decoded as an ASN.1 element for a NOT filter " +
                    "component:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_NOT_COMPONENT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the NOT component element could " +
                    "not be decoded as an LDAP filter:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_TV_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the element could not be decoded " +
                    "as a type-and-value sequence:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_TV_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the type-and-value sequence had " +
                    "an invalid number of elements (expected 2, got %d).");
    registerMessage(MSGID_LDAP_FILTER_DECODE_TV_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the attribute type could not be " +
                    "decoded from the type-and-value sequence:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_TV_VALUE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the assertion value could not be " +
                    "decoded from the type-and-value sequence:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_SUBSTRING_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the element could not be decoded " +
                    "as a substring sequence:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_SUBSTRING_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the substring sequence had an " +
                    "invalid number of elements (expected 2, got %d).");
    registerMessage(MSGID_LDAP_FILTER_DECODE_SUBSTRING_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the attribute type could not be " +
                    "decoded from the substring sequence:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_SUBSTRING_ELEMENTS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the substring value sequence " +
                    "could not be decoded:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_SUBSTRING_NO_SUBELEMENTS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the substring value sequence did " +
                    "not contain any elements.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_SUBSTRING_INVALID_SUBTYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the substring value sequence had " +
                    "an element with an invalid BER type (%x).");
    registerMessage(MSGID_LDAP_FILTER_DECODE_SUBSTRING_VALUES,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because a problem occurred while trying " +
                    "to parse the substring value elements:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_PRESENCE_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the element could not be decoded " +
                    "as the presence attribute type:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the element could not be decoded " +
                    "as an extensible matching sequence:  %s.");
    registerMessage(MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_INVALID_TYPE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because the extensible matching sequence " +
                    "had an element with an invalid BER type (%x).");
    registerMessage(MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_ELEMENTS,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "search filter because a problem occurred while trying " +
                    "to parse the extensible match sequence elements:  %s.");
    registerMessage(MSGID_LDAP_FILTER_STRING_NULL,
                    "Cannot decode the provided string as an LDAP search " +
                    "filter because the string was null.");
    registerMessage(MSGID_LDAP_FILTER_UNCAUGHT_EXCEPTION,
                    "Cannot decode the provided string %s as an LDAP search " +
                    "filter because an unexpected exception was thrown " +
                    "during processing:  %s.");
    registerMessage(MSGID_LDAP_FILTER_MISMATCHED_PARENTHESES,
                    "The provided search filter \"%s\" had mismatched " +
                    "parentheses around the portion between positions %d and " +
                    "%d.");
    registerMessage(MSGID_LDAP_FILTER_NO_EQUAL_SIGN,
                    "The provided search filter \"%s\" was missing an equal " +
                    "sign in the suspected simple filter component between " +
                    "positions %d and %d.");
    registerMessage(MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE,
                    "The provided search filter \"%s\" had an invalid " +
                    "escaped byte value at position %d.  A backslash in a " +
                    "value must be followed by two hexadecimal characters " +
                    "that define the byte that has been encoded.");
    registerMessage(MSGID_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the compound filter between positions %d and %d " +
                    "did not start with an open parenthesis and end with a " +
                    "close parenthesis (they may be parentheses for " +
                    "different filter components).");
    registerMessage(MSGID_LDAP_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the closing parenthesis at position %d did not " +
                    "have a corresponding open parenthesis.");
    registerMessage(MSGID_LDAP_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the closing parenthesis at position %d did not " +
                    "have a corresponding close parenthesis.");
    registerMessage(MSGID_LDAP_FILTER_SUBSTRING_NO_ASTERISKS,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the assumed substring filter value between " +
                    "positions %d and %d did not have any asterisk wildcard " +
                    "characters.");
    registerMessage(MSGID_LDAP_FILTER_EXTENSIBLE_MATCH_NO_COLON,
                    "The provided search filter \"%s\" could not be decoded " +
                    "because the extensible match component starting at " +
                    "position %d did not have a colon to denote the end of " +
                    "the attribute type name.");


    registerMessage(MSGID_LDAP_CLIENT_SEND_RESPONSE_NO_RESULT_CODE,
                    "The server attempted to send a response to the %s " +
                    "operation (conn=%d, op=%d), but the operation did not " +
                    "have a result code.  This could indicate that the " +
                    "operation did not complete properly or that it is one " +
                    "that is not allowed to have a response.  Using a " +
                    "generic 'Operations Error' response.");
    registerMessage(MSGID_LDAP_CLIENT_SEND_RESPONSE_INVALID_OP,
                    "The server attempted to send a response to the %s " +
                    "operation (conn=%d, op=%d), but this type of operation " +
                    "is not allowed to have responses.  Backtrace:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_SEND_MESSAGE_ENCODE_ASN1,
                    "The server was unable to encode the provided LDAP " +
                    "message %s (conn=%d, op=%d) into an ASN.1 element:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_SEND_MESSAGE_ENCODE_BYTES,
                    "The server was unable to encode the ASN.1 element " +
                    "generated from LDAP message %s (conn=%d, op=%d) into a " +
                    "byte array:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_SEND_MESSAGE_IO_PROBLEM,
                    "The server was unable to send the LDAP message %s " +
                    "(conn=%d, op=%d) to the client because an I/O problem " +
                    "was encountered:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_SEND_MESSAGE_UNEXPECTED_PROBLEM,
                    "The server was unable to send the LDAP message %s " +
                    "(conn=%d, op=%d) to the client because an unexpected " +
                    "problem was encountered:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_GENERIC_NOTICE_OF_DISCONNECTION,
                    "The Directory Server is closing the connection to this " +
                    "client.");
    registerMessage(MSGID_LDAP_CLIENT_DISCONNECT_IN_PROGRESS,
                    "The Directory Server is currently in the process of " +
                    "closing this client connection.");
    registerMessage(MSGID_LDAP_CLIENT_DUPLICATE_MESSAGE_ID,
                    "The Directory Server is already processing another " +
                    "request on the same client connection with the same " +
                    "message ID of %d.");
    registerMessage(MSGID_LDAP_CLIENT_CANNOT_ENQUEUE,
                    "The Directory Server encountered an unexpected error " +
                    "while attempting to add the client request to the work " +
                    "queue:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_DECODE_ZERO_BYTE_VALUE,
                    "The client sent a request to the Directory Server that " +
                    "was an ASN.1 element with a zero-byte value.  This " +
                    "cannot possibly be a valid LDAP message.");
    registerMessage(MSGID_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED,
                    "The client sent a request to the Directory Server with " +
                    "an ASN.1 element value length of %d bytes.  This " +
                    "exceeds the maximum allowed request size of %d bytes, " +
                    "so processing cannot continue on this connection.");
    registerMessage(MSGID_LDAP_CLIENT_DECODE_INVALID_MULTIBYTE_LENGTH,
                    "The client sent a request to the Directory Server with " +
                    "an ASN.1 element using multiple bytes to express the " +
                    "value length.  The request indicated that %d bytes were " +
                    "needed to express the length, but this exceeds the " +
                    "maximum allowed limit of four bytes.");
    registerMessage(MSGID_LDAP_CLIENT_DECODE_ASN1_FAILED,
                    "The client sent a request to the Directory Server that " +
                    "could not be properly decoded as an ASN.1 element:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_DECODE_LDAP_MESSAGE_FAILED,
                    "The client sent a request to the Directory Server that " +
                    "could not be properly decoded as an LDAP message:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_INVALID_DECODE_STATE,
                    "An internal error has occurred within the Directory " +
                    "Server to cause it to lose track of where it is in " +
                    "decoding requests on this client connection.  It had an " +
                    "invalid decode state of %d, and this connection must be " +
                    "terminated.");
    registerMessage(MSGID_LDAP_CLIENT_DECODE_INVALID_REQUEST_TYPE,
                    "The client sent an LDAP message to the Directory Server " +
                    "that was not a valid message for a client request:  %s.");
    registerMessage(MSGID_LDAP_CLIENT_CANNOT_CONVERT_MESSAGE_TO_OPERATION,
                    "The Directory Server was unable to convert the LDAP " +
                    "message read from the client (%s) to an internal " +
                    "operation for processing:  %s.");


    registerMessage(MSGID_LDAP_CONNHANDLER_OPEN_SELECTOR_FAILED,
                    "The LDAP connection handler defined in configuration " +
                    "entry %s was unable to open a selector to allow it to " +
                    "multiplex the associated accept sockets:  %s.  This " +
                    "connection handler will be disabled.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CREATE_CHANNEL_FAILED,
                    "The LDAP connection handler defined in configuration " +
                    "entry %s was unable to create a server socket channel " +
                    "to accept connections on %s:%d:  %s.  The Directory " +
                    "Server will not listen for new connections on that " +
                    "address.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NO_ACCEPTORS,
                    "The LDAP connection handler defined in configuration " +
                    "entry %s was unable to create any of the socket " +
                    "channels on any of the configured addresses.  This " +
                    "connection handler will be disabled.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DENIED_CLIENT,
                    "The connection attempt from client %s to %s has been " +
                    "rejected because the client was included in one of the " +
                    "denied address ranges.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DISALLOWED_CLIENT,
                    "The connection attempt from client %s to %s has been " +
                    "rejected because the client was not included in one of " +
                    "the allowed address ranges.");
    registerMessage(MSGID_LDAP_CONNHANDLER_UNABLE_TO_REGISTER_CLIENT,
                    "An internal error prevented the Directory Server from " +
                    "properly registering the client connection from %s to " +
                    "%s with an appropriate request handler:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_ACCEPT_CONNECTION,
                    "The LDAP connection handler defined in configuration " +
                    "entry %s was unable to accept a new client connection:  " +
                    "%s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CONSECUTIVE_ACCEPT_FAILURES,
                    "The LDAP connection handler defined in configuration " +
                    "entry %s has experienced consecutive failures while " +
                    "trying to accept client connections:  %s.  This " +
                    "connection handler will be disabled.");
    registerMessage(MSGID_LDAP_CONNHANDLER_UNCAUGHT_ERROR,
                    "The LDAP connection handler defined in configuration " +
                    "entry %s caught an unexpected error while trying to " +
                    "listen for new connections:  %s.  This connection " +
                    "handler will be disabled.");
    registerMessage(MSGID_LDAP_CONNHANDLER_REJECTED_BY_SERVER,
                    "The attempt to register this connection with the " +
                    "Directory Server was rejected.  This may indicate that " +
                    "the server already has the maximum allowed number of " +
                    "concurrent connections established.");


    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_ADDRESS,
                    "Specifies the address or set of addresses on which this " +
                    "connection handler may accept client connections.  If " +
                    "no value is specified, then the server will accept " +
                    "connections on all active addresses.  Changes to this " +
                    "configuration attribute will not take effect until the " +
                    "connection handler is disabled and re-enabled, or until " +
                    "the Directory Server is restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_LISTEN_PORT,
                    "Specifies the TCP port on which this connection handler " +
                    "may accept client connections.  Changes to this " +
                    "configuration attribute will not take effect until the " +
                    "connection handler is disabled and re-enabled, or until " +
                    "the Directory Server is restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_BACKLOG,
                    "Specifies the accept queue size, which controls the " +
                    "number of new connection attempts that may be allowed " +
                    "to queue up in the backlog before being rejected.  This " +
                    "should only need to be changed if it is expected that " +
                    "the Directory Server will receive large numbers of new " +
                    "connection attempts at the same time.  Changes to this " +
                    "configuration attribute will not take effect until the " +
                    "connection handler is disabled and re-enabled, or until " +
                    "the Directory Server is restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOWED_CLIENTS,
                    "Specifies a set of address masks that may be used to " +
                    "determine the addresses of the clients that are allowed " +
                    "to establish connections to this connection handler.  " +
                    "If no values are specified, then all clients with " +
                    "addresses that do not match an address on the deny " +
                    "list will be allowed.  Changes to this configuration " +
                    "attribute will take effect immediately but will not " +
                    "interfere with connections that may already be " +
                    "established.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_DENIED_CLIENTS,
                    "Specifies a set of address masks that may be used to " +
                    "determine the set of addresses of the clients that are " +
                    "not allowed to establish connections to this connection " +
                    "handler.  If both allowed and denied client masks are " +
                    "defined and a client connection matches one or more " +
                    "masks in both lists, then the connection will be " +
                    "denied.  If only a denied list is specified, then any " +
                    "client not matching a mask in that list will be " +
                    "allowed.  Changes to this configuration attribute will " +
                    "take effect immediately but will not interfere with " +
                    "connections that may already be established.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_KEEP_STATS,
                    "Indicates whether the connection handler should keep " +
                    "statistics regarding LDAP client communication.  " +
                    "Maintaining this information may cause a slight " +
                    "decrease in performance, but can be useful for " +
                    "understanding client usage patterns.  Changes to this " +
                    "configuration attribute will take effect immediately, " +
                    "but will only apply for new connections and will have " +
                    "the side effect of clearing any existing statistical " +
                    "data that may have been collected.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_LDAPV2,
                    "Indicates whether to allow communication with LDAPv2 " +
                    "clients.  LDAPv2 is considered an obsolete protocol, " +
                    "and clients using it will not be allowed to take " +
                    "advantage of all features offered by the server.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately, but will not interfere with " +
                    "connections that may already be established.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_NUM_REQUEST_HANDLERS,
                    "Specifies the number of threads that should be used to " +
                    "read requests from clients and place them in the " +
                    "work queue for processing.  On large systems accepting " +
                    "many concurrent requests, it may be more efficient to " +
                    "have multiple threads reading requests from clients.  " +
                    "Changes to this configuration attribute will not take " +
                    "effect until the connection handler is disabled and " +
                    "re-enabled, or until the Directory Server is " +
                    "restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_SEND_REJECTION_NOTICE,
                    "Indicates whether to send an LDAPv3 notice of " +
                    "disconnection message to client connections that " +
                    "are rejected before closing the connection.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_KEEPALIVE,
                    "Indicates whether to use the TCP KeepAlive feature for " +
                    "client connections established through this connection " +
                    "handler.  This is recommended because it may help the " +
                    "server detect client connections that are no longer " +
                    "valid, and may help prevent intermediate network " +
                    "devices from closing connections due to a lack of " +
                    "communication.  Changes to this configuration attribute " +
                    "will take effect immediately but will only be applied " +
                    "to connections established after the change.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_TCP_NODELAY,
                    "Indicates whether to use the TCP NoDelay feature for " +
                    "client connections established through this connection " +
                    "handler.  This is recommended because it will generally " +
                    "allow faster responses to clients, although directories " +
                    "that frequently process searches that match multiple " +
                    "entries may be able to achieve higher throughput if it" +
                    " is disabled.  Changes to this configuration attribute " +
                    "will take effect immediately but will only be applied " +
                    "to connections established after the change.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_REUSE_ADDRESS,
                    "Indicates whether to use the SO_REUSEADDR socket option " +
                    "for the socket accepting connections for this " +
                    "connection handler.  It should generally be enabled " +
                    "unless you have been instructed to disable it by " +
                    "support engineers.  Changes to this configuration " +
                    "attribute will not take effect until the connection " +
                    "handler is disabled and re-enabled, or until the " +
                    "Directory Server is restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_MAX_REQUEST_SIZE,
                    "Specifies the maximum size in bytes that will be " +
                    "allowed when reading requests from clients.  This can " +
                    "be used to prevent denial of service attacks from " +
                    "clients that send extremely large requests.  A value of " +
                    "zero indicates that no limit should be imposed.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_USE_SSL,
                    "Indicates whether this connection handler should use " +
                    "SSL when accepting connections from clients.  Changes " +
                    "to this configuration attribute will not take effect " +
                    "until the connection handler is disabled and " +
                    "re-enabled, or until the Directory Server is restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_ALLOW_STARTTLS,
                    "Indicates whether this connection handler should allow " +
                    "clients to use the StartTLS extended operation to " +
                    "initiate secure communication over a non-SSL LDAP " +
                    "connection.  This may not be used if SSL is enabled " +
                    "for the connection handler.  Changes to this " +
                    "configuration attribute will take effect immediately " +
                    "for LDAP clients.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CLIENT_AUTH_POLICY,
                    "Specifies the policy that should be used regarding " +
                    "requesting or requiring the client to present its own " +
                    "certificate when establishing an SSL-based connection " +
                    "or using StartTLS to initiate a secure channel in an "+
                    "established connection.  Changes to this configuration " +
                    "attribute will not take effect until the connection " +
                    "handler is disabled and re-enabled, or until the " +
                    "Directory Server is restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME,
                    "Specifies the nickname of the certificate that the " +
                    "connection handler should use when accepting SSL-based " +
                    "connections or performing StartTLS negotiation.  " +
                    "Changes to this configuration attribute will not take " +
                    "effect until the connection handler is disabled and " +
                    "re-enabled, or until the Directory Server is restarted.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_PROTOCOLS,
                    "Specifies the names of the SSL protocols that will be " +
                    "allowed for use in SSL or StartTLS communication.  " +
                    "Changes to this configuration attribute will take " +
                    "immediately but will only impact new SSL/TLS-based " +
                    "sessions created after the change.");
    registerMessage(MSGID_LDAP_CONNHANDLER_DESCRIPTION_SSL_ENABLED_CIPHERS,
                    "Specifies the names of the SSL cipher suites that will " +
                    "be allowed for use in SSL or StartTLS communication.  " +
                    "Changes to this configuration attribute will take " +
                    "immediately but will only impact new SSL/TLS-based " +
                    "sessions created after the change.");


    registerMessage(MSGID_LDAP_CONNHANDLER_UNKNOWN_LISTEN_ADDRESS,
                    "The specified listen address \"%s\" in configuration " +
                    "entry \"%s\" could not be resolved:  %s.  Please make " +
                    "sure that name resolution is properly configured on " +
                    "this system.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_ADDRESS,
                    "An unexpected error occurred while processing the " +
                    ATTR_LISTEN_ADDRESS + " attribute in configuration entry " +
                    "%s, which is used to specify the address or set of " +
                    "addresses on which to listen for client connections:  " +
                    "%s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NO_LISTEN_PORT,
                    "No listen port was defined using configuration " +
                    ATTR_LISTEN_PORT + " in configuration entry %s.  This is " +
                    "a required attribute.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT,
                    "An unexpected error occurred while processing the " +
                    ATTR_LISTEN_PORT + " attribute in configuration entry " +
                    "%s, which is used to specify the port on which to " +
                    "listen for client connections:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_BACKLOG,
                    "An unexpected error occurred while processing the " +
                    ATTR_ACCEPT_BACKLOG + " attribute in configuration entry " +
                    "%s, which is used to specify the accept backlog size:  " +
                    "%s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOWED_CLIENTS,
                    "An unexpected error occurred while processing the " +
                    ATTR_ALLOWED_CLIENT + " attribute in configuration entry " +
                    "%s, which is used to specify the address mask(s) of the " +
                    "clients that are allowed to establish connections to " +
                    "this connection handler:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_DENIED_CLIENTS,
                    "An unexpected error occurred while processing the " +
                    ATTR_DENIED_CLIENT + " attribute in configuration entry " +
                    "%s, which is used to specify the address mask(s) of the " +
                    "clients that are not allowed to establish connections " +
                    "to this connection handler:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_LDAPV2,
                    "An unexpected error occurred while processing the " +
                    ATTR_ALLOW_LDAPV2 + " attribute in configuration entry " +
                    "%s, which is used to indicate whether LDAPv2 clients " +
                    "will be allowed to access this connection handler:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_KEEP_STATS,
                    "An unexpected error occurred while processing the " +
                    ATTR_KEEP_LDAP_STATS + " attribute in configuration " +
                    "entry %s, which is used to indicate whether LDAP usage " +
                    "statistics should be enabled for this connection " +
                    "handler:  %s.");
    registerMessage(
         MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_NUM_REQUEST_HANDLERS,
         "An unexpected error occurred while processing the " +
         ATTR_NUM_REQUEST_HANDLERS + " attribute in configuration entry %s, " +
         "which is used to specify the number of request handlers to use " +
         "to read requests from clients: %s.");
    registerMessage(
         MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SEND_REJECTION_NOTICE,
         "An unexpected error occurred while processing the " +
         ATTR_SEND_REJECTION_NOTICE + " attribute in configuration entry %s, " +
         "which is used to indicate whether to send a notice of " +
         "disconnection message to rejected client connections: %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_KEEPALIVE,
                    "An unexpected error occurred while processing the " +
                    ATTR_USE_TCP_KEEPALIVE + " attribute in configuration " +
                    "entry %s, which is used to periodically send TCP " +
                    "Keep-Alive messages over idle connections:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_TCP_NODELAY,
                    "An unexpected error occurred while processing the " +
                    ATTR_USE_TCP_NODELAY + " attribute in configuration " +
                    "entry %s, which is used to determine whether to " +
                    "immediately flush responses to clients:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_REUSE_ADDRESS,
                    "An unexpected error occurred while processing the " +
                    ATTR_ALLOW_REUSE_ADDRESS + " attribute in configuration " +
                    "entry %s, which is used to determine whether to set the " +
                    "SO_REUSEADDR option on the listen socket:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_MAX_REQUEST_SIZE,
                    "An unexpected error occurred while processing the " +
                    ATTR_MAX_REQUEST_SIZE + " attribute in configuration " +
                    "entry %s, which is used to determine the maximum size " +
                    "in bytes that may be used for a client request:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_USE_SSL,
                    "An unexpected error occurred while processing the " +
                    ATTR_USE_SSL + " attribute in configuration entry %s, " +
                    "which is used to indicate whether to use SSL when " +
                    "accepting client connections:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_HAVE_SSL_AND_STARTTLS,
                    "The LDAP connection handler defined in configuration " +
                    "entry %s is configured to communicate over SSL and " +
                    "also to allow clients to use the StartTLS extended " +
                    "operation.  These options may not be used at the same " +
                    "time, so clients will not be allowed to use the " +
                    "StartTLS operation.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_ALLOW_STARTTLS,
                    "An unexpected error occurred while processing the " +
                    ATTR_ALLOW_STARTTLS + " attribute in configuration entry " +
                    "%s, which is used to indicate whether clients may use " +
                    "the StartTLS extended operation:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_INVALID_SSL_CLIENT_AUTH_POLICY,
                    "The SSL client authentication policy \"%s\" specified " +
                    "in attribute " + ATTR_SSL_CLIENT_AUTH_POLICY +
                    " of configuration entry %s is invalid.  The value must " +
                    "be one of \"disabled\", \"optional\", or \"required\".");
    registerMessage(
         MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CLIENT_AUTH_POLICY,
         "An unexpected error occurred while processing the " +
         ATTR_SSL_CLIENT_AUTH_POLICY + " attribute in configuration entry " +
         "%s, which is used to specify the policy that should be used " +
         "for requesting/requiring SSL client authentication:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME,
                    "An unexpected error occurred while processing the " +
                    ATTR_SSL_CERT_NICKNAME + " attribute in configuration " +
                    "entry %s, which is used to specify the nickname of the " +
                    "certificate to use for accepting SSL/TSL connections:  " +
                    "%s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_PROTOCOLS,
                    "An unexpected error occurred while processing the " +
                    ATTR_SSL_PROTOCOLS + " attribute in configuration " +
                    "entry %s, which is used to specify the names of the " +
                    "SSL protocols to allow for SSL/TLS sessions:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_CANNOT_DETERMINE_SSL_CIPHERS,
                    "An unexpected error occurred while processing the " +
                    ATTR_SSL_PROTOCOLS + " attribute in configuration " +
                    "entry %s, which is used to specify the names of the " +
                    "SSL cipher suites to allow for SSL/TLS sessions:  %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_INVALID_ADDRESS_MASK,
                    "The string %s defined in attribute %s of configuration " +
                    "entry %s could not be decoded as a valid address mask:  "+
                    "%s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_ALLOWED_CLIENTS,
                    "A new set of allowed client address masks has been " +
                    "applied for configuration entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_DENIED_CLIENTS,
                    "A new set of denied client address masks has been " +
                    "applied for configuration entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_ALLOW_LDAPV2,
                    "The value of the " + ATTR_ALLOW_LDAPV2 +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_KEEP_STATS,
                    "The value of the " + ATTR_KEEP_LDAP_STATS +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_SEND_REJECTION_NOTICE,
                    "The value of the " + ATTR_SEND_REJECTION_NOTICE +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_USE_KEEPALIVE,
                    "The value of the " + ATTR_USE_TCP_KEEPALIVE +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_USE_TCP_NODELAY,
                    "The value of the " + ATTR_USE_TCP_NODELAY +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_MAX_REQUEST_SIZE,
                    "The value of the " + ATTR_MAX_REQUEST_SIZE +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_ALLOW_STARTTLS,
                    "The value of the " + ATTR_ALLOW_STARTTLS +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_SSL_PROTOCOLS,
                    "The value of the " + ATTR_SSL_PROTOCOLS +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_NEW_SSL_CIPHERS,
                    "The value of the " + ATTR_SSL_CIPHERS +
                    " attribute has been updated to %s in configuration " +
                    "entry %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_STARTED_LISTENING,
                    "Started listening for new connections on %s.");
    registerMessage(MSGID_LDAP_CONNHANDLER_STOPPED_LISTENING,
                    "Stopped listening for new connections on %s.");


    registerMessage(MSGID_LDAP_REQHANDLER_OPEN_SELECTOR_FAILED,
                    "%s was unable to open a selector to multiplex reads " +
                    "from clients:  %s.  This request handler cannot " +
                    "continue processing.");
    registerMessage(MSGID_LDAP_REQHANDLER_CANNOT_REGISTER,
                    "%s was unable to register this client connection with " +
                    "the selector:  %s.");
    registerMessage(MSGID_LDAP_REQHANDLER_REJECT_DUE_TO_SHUTDOWN,
                    "This connection could not be registered with a " +
                    "request handler because the Directory Server is " +
                    "shutting down.");
    registerMessage(MSGID_LDAP_REQHANDLER_REJECT_DUE_TO_QUEUE_FULL,
                    "This connection could not be registered with a request " +
                    "handler because the pending queue associated with %s " +
                    "is too full.");
    registerMessage(MSGID_LDAP_REQHANDLER_DEREGISTER_DUE_TO_SHUTDOWN,
                    "This client connection is being deregistered from the " +
                    "associated request handler because the Directory Server " +
                    "is shutting down:  %s.");
    registerMessage(MSGID_LDAP_REQHANDLER_UNEXPECTED_SELECT_EXCEPTION,
                    "The LDAP request handler thread \"%s\" encountered an " +
                    "unexpected error that would have caused the thread to " +
                    "die:  %s.  The error has been caught and the request " +
                    "handler should continue operating as normal.");


    registerMessage(MSGID_LDAP_DISCONNECT_DUE_TO_INVALID_REQUEST_TYPE,
                    "Terminating this connection because the client sent " +
                    "an invalid message of type %s (LDAP message ID %d) that " +
                    "is not allowed for request messages.");
    registerMessage(MSGID_LDAP_DISCONNECT_DUE_TO_PROCESSING_FAILURE,
                    "An unexpected failure occurred while trying to process " +
                    "a request of type %s (LDAP message ID %d):  %s.  The " +
                    "client connection will be terminated.");
    registerMessage(MSGID_LDAP_INVALID_BIND_AUTH_TYPE,
                    "The bind request message (LDAP message ID %d) included " +
                    "an invalid authentication type of %s.  This is a " +
                    "protocol error, and this connection will be terminated " +
                    "as per RFC 2251 section 4.2.3.");
    registerMessage(MSGID_LDAP_DISCONNECT_DUE_TO_BIND_PROTOCOL_ERROR,
                    "This client connection is being terminated because a " +
                    "protocol error occurred while trying to process a bind " +
                    "request.  The LDAP message ID was %d and the error " +
                    "message for the bind response was %s.");


    registerMessage(MSGID_LDAPV2_SKIPPING_EXTENDED_RESPONSE,
                    "An extended response message would have been sent to an " +
                    "LDAPv2 client (connection ID=%d, operation ID=%d):  " +
                    "%s.  LDAPv2 does not allow extended operations, so this " +
                    "response will not be sent.");
    registerMessage(MSGID_LDAPV2_SKIPPING_SEARCH_REFERENCE,
                    "A search performed by an LDAPv2 client (connection " +
                    "ID=%d, operation ID=%d) would have included a search " +
                    "result reference %s.  Referrals are not allowed for " +
                    "LDAPv2 clients, so this search reference will not be " +
                    "sent.");
    registerMessage(MSGID_LDAPV2_REFERRAL_RESULT_CHANGED,
                    "The original result code for this message was " +
                    LDAPResultCode.REFERRAL + " but this result is not " +
                    "allowed for LDAPv2 clients.");
    registerMessage(MSGID_LDAPV2_REFERRALS_OMITTED,
                    "The response included one or more referrals, which are " +
                    "not allowed for LDAPv2 clients.  The referrals included " +
                    "were:  %s.");
    registerMessage(MSGID_LDAPV2_CLIENTS_NOT_ALLOWED,
                    "The Directory Server has been configured to deny access " +
                    "to LDAPv2 clients.  This connection will be closed.");
    registerMessage(MSGID_LDAPV2_EXTENDED_REQUEST_NOT_ALLOWED,
                    "The client with connection ID %d authenticated to the " +
                    "Directory Server using LDAPv2, but attempted to send an " +
                    "extended operation request (LDAP message ID %d), which " +
                    "is not allowed for LDAPv2 clients.  The connection will " +
                    "be terminated.");



    registerMessage(MSGID_LDAP_STATS_INVALID_MONITOR_INITIALIZATION,
                    "An attempt was made to initialize the LDAP statistics " +
                    "monitor provider as defined in configuration entry %s.  " +
                    "This monitor provider should only be dynamically " +
                    "created within the Directory Server itself and not " +
                    "from within the configuration.");


    registerMessage(MSGID_INTERNAL_CANNOT_DECODE_DN,
                    "An unexpected error occurred while trying to decode the " +
                    "DN %s used for internal operations as a root user:  %s.");


    registerMessage(MSGID_LDAP_TLS_EXISTING_SECURITY_PROVIDER,
                    "The TLS connection security provider cannot be enabled " +
                    "on this client connection because it is already using " +
                    "the %s provider.  StartTLS may only be used on " +
                    "clear-text connections.");
    registerMessage(MSGID_LDAP_TLS_STARTTLS_NOT_ALLOWED,
                    "StartTLS cannot be enabled on this LDAP client " +
                    "connection because the corresponding LDAP connection " +
                    "handler is configured to reject StartTLS requests.  " +
                    "The use of StartTLS may be enabled using the " +
                    ATTR_ALLOW_STARTTLS + " configuration attribute.");
    registerMessage(MSGID_LDAP_TLS_CANNOT_CREATE_TLS_PROVIDER,
                    "An error occurred while attempting to create a TLS " +
                    "connection security provider for this client connection " +
                    "for use with StartTLS:  %s.");
    registerMessage(MSGID_LDAP_TLS_NO_PROVIDER,
                    "StartTLS is not available on this client connection " +
                    "because the connection does not have access to a TLS " +
                    "connection security provider.");
    registerMessage(MSGID_LDAP_TLS_CLOSURE_NOT_ALLOWED,
                    "The LDAP connection handler does not allow clients to " +
                    "close a StartTLS session on a client connection while " +
                    "leaving the underlying TCP connection active.  The " +
                    "TCP connection will be closed.");
    registerMessage(MSGID_LDAP_NO_CLEAR_SECURITY_PROVIDER,
                    "LDAP connection handler %s could not send a clear-text " +
                    "response to the client because it does not have a " +
                    "reference to a clear connection security provider.");


    registerMessage(MSGID_LDAP_PAGED_RESULTS_DECODE_NULL,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "paged results control value because the element is " +
                    "null.");
    registerMessage(MSGID_LDAP_PAGED_RESULTS_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "paged results control value because the element could " +
                    "not be decoded as a sequence:  %s.");
    registerMessage(MSGID_LDAP_PAGED_RESULTS_DECODE_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "paged results control value because the request " +
                    "sequence has an invalid number of elements (expected 2, " +
                    "got %d).");
    registerMessage(MSGID_LDAP_PAGED_RESULTS_DECODE_SIZE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "paged results control value because the size element " +
                    "could not be properly decoded:  %s.");
    registerMessage(MSGID_LDAP_PAGED_RESULTS_DECODE_COOKIE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "paged results control value because the cookie could " +
                    "not be properly decoded:  %s.");


    registerMessage(MSGID_LDAPASSERT_NO_CONTROL_VALUE,
                    "Cannot decode the provided LDAP assertion control " +
                    "because the control does not have a value.");
    registerMessage(MSGID_LDAPASSERT_INVALID_CONTROL_VALUE,
                    "Cannot decode the provided LDAP assertion control " +
                    "because the control value cannot be decoded as an " +
                    "ASN.1 element:  %s.");


    registerMessage(MSGID_PREREADREQ_NO_CONTROL_VALUE,
                    "Cannot decode the provided LDAP pre-read request " +
                    "control because the control does not have a value.");
    registerMessage(MSGID_PREREADREQ_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided LDAP pre-read request " +
                    "control because an error occurred while trying to " +
                    "decode the control value:  %s.");


    registerMessage(MSGID_POSTREADREQ_NO_CONTROL_VALUE,
                    "Cannot decode the provided LDAP post-read request " +
                    "control because the control does not have a value.");
    registerMessage(MSGID_POSTREADREQ_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided LDAP post-read request " +
                    "control because an error occurred while trying to " +
                    "decode the control value:  %s.");


    registerMessage(MSGID_PREREADRESP_NO_CONTROL_VALUE,
                    "Cannot decode the provided LDAP pre-read response " +
                    "control because the control does not have a value.");
    registerMessage(MSGID_PREREADRESP_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided LDAP pre-read response " +
                    "control because an error occurred while trying to " +
                    "decode the control value:  %s.");


    registerMessage(MSGID_POSTREADRESP_NO_CONTROL_VALUE,
                    "Cannot decode the provided LDAP post-read response " +
                    "control because the control does not have a value.");
    registerMessage(MSGID_POSTREADRESP_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided LDAP post-read response " +
                    "control because an error occurred while trying to " +
                    "decode the control value:  %s.");


    registerMessage(MSGID_PROXYAUTH1_NO_CONTROL_VALUE,
                    "Cannot decode the provided proxied authorization V1 " +
                    "control because it does not have a value.");
    registerMessage(MSGID_PROXYAUTH1_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided proxied authorization V1 " +
                    "control because the ASN.1 sequence in the control " +
                    "value has an invalid number of elements (expected 1, " +
                    "got %d).");
    registerMessage(MSGID_PROXYAUTH1_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided proxied authorization V1 " +
                    "control because an error occurred while attempting to " +
                    "decode the control value:  %s.");
    registerMessage(MSGID_PROXYAUTH1_CANNOT_LOCK_USER,
                    "Unable to obtain a lock on user entry %s for the " +
                    "proxied authorization V1 control validation.");
    registerMessage(MSGID_PROXYAUTH1_NO_SUCH_USER,
                    "User %s specified in the proxied authorization V1 " +
                    "control does not exist in the Directory Server.");
    registerMessage(MSGID_PROXYAUTH1_UNUSABLE_ACCOUNT,
                    "Use of the proxied authorization V1 control for user %s " +
                    "is not allowed by the password policy configuration.");


    registerMessage(MSGID_PROXYAUTH2_NO_CONTROL_VALUE,
                    "Cannot decode the provided proxied authorization V2 " +
                    "control because it does not have a value.");
    registerMessage(MSGID_PROXYAUTH2_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided proxied authorization V2 " +
                    "control because an error occurred while attempting to " +
                    "decode the control value:  %s.");
    registerMessage(MSGID_PROXYAUTH2_CANNOT_LOCK_USER,
                    "Unable to obtain a lock on user entry %s for the " +
                    "proxied authorization V2 control validation.");
    registerMessage(MSGID_PROXYAUTH2_NO_IDENTITY_MAPPER,
                    "Unable to process proxied authorization V2 control " +
                    "because it contains an authorization ID based on a " +
                    "username and no proxied authorization identity mapper " +
                    "is configured in the Directory Server.");
    registerMessage(MSGID_PROXYAUTH2_INVALID_AUTHZID,
                    "The authorization ID \"%s\" contained in the proxied " +
                    "authorization V2 control is invalid because it does not " +
                    "start with \"dn:\" to indicate a user DN or \"u:\" to " +
                    "indicate a username.");
    registerMessage(MSGID_PROXYAUTH2_NO_SUCH_USER,
                    "User %s specified in the proxied authorization V2 " +
                    "control does not exist in the Directory Server.");
    registerMessage(MSGID_PROXYAUTH2_UNUSABLE_ACCOUNT,
                    "Use of the proxied authorization V2 control for user %s " +
                    "is not allowed by the password policy configuration.");


    registerMessage(MSGID_PSEARCH_CHANGETYPES_INVALID_TYPE,
                    "The provided integer value %d does not correspond to " +
                    "any persistent search change type.");
    registerMessage(MSGID_PSEARCH_CHANGETYPES_NO_TYPES,
                    "The provided integer value indicated that there were no " +
                    "persistent search change types, which is not allowed.");
    registerMessage(MSGID_PSEARCH_CHANGETYPES_INVALID_TYPES,
                    "The provided integer value %d was outside the range of " +
                    "acceptable values for an encoded change type set.");


    registerMessage(MSGID_PSEARCH_NO_CONTROL_VALUE,
                    "Cannot decode the provided persistent search control " +
                    "because it does not have a value.");
    registerMessage(MSGID_PSEARCH_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided persistent search control " +
                    "because the value sequence has an invalid number of " +
                    "elements (expected 3, got %d).");
    registerMessage(MSGID_PSEARCH_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided persistent search control " +
                    "because an error occurred while attempting to decode " +
                    "the control value:  %s.");


    registerMessage(MSGID_ECN_NO_CONTROL_VALUE,
                    "Cannot decode the provided entry change notification " +
                    "control because it does not have a value.");
    registerMessage(MSGID_ECN_INVALID_ELEMENT_COUNT,
                    "Cannot decode the provided entry change notification " +
                    "control because the value sequence has an invalid " +
                    "number of elements (expected between 1 and 3, got %d).");
    registerMessage(MSGID_ECN_ILLEGAL_PREVIOUS_DN,
                    "Cannot decode the provided entry change notification " +
                    "control because it contains a previous DN element but " +
                    "had a change type of %s.  The previous DN element may " +
                    "only be provided with the modify DN change type.");
    registerMessage(MSGID_ECN_INVALID_ELEMENT_TYPE,
                    "Cannot decode the provided entry change notification " +
                    "control because the second element in the value " +
                    "sequence has an invalid type of %s that is not " +
                    "appropriate for either a previous DN or a change number.");
    registerMessage(MSGID_ECN_CANNOT_DECODE_VALUE,
                    "Cannot decode the provided entry change notification " +
                    "control because an error occurred while attempting to " +
                    "decode the control value:  %s.");


    registerMessage(MSGID_AUTHZIDRESP_NO_CONTROL_VALUE,
                    "Cannot decode the provided authorization identity " +
                    "response control because it does not have a value.");


    registerMessage(MSGID_LDAP_INTERMEDIATE_RESPONSE_DECODE_SEQUENCE,
                    "Cannot decode the provided ASN.1 element as an LDAP " +
                    "intermediate response protocol op because the element " +
                    "could not be decoded as a sequence:  %s.");
    registerMessage(
         MSGID_LDAP_INTERMEDIATE_RESPONSE_DECODE_INVALID_ELEMENT_COUNT,
         "Cannot decode the provided ASN.1 element as an LDAP intermediate " +
         "response protocol op because the request sequence had an invalid " +
         "number of elements (expected 0, 1, or or 2, got %d).");
    registerMessage(MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_OID,
                    "An error occurred while attempting to decode the " +
                    "intermediate response OID:  %s.");
    registerMessage(MSGID_LDAP_INTERMEDIATE_RESPONSE_CANNOT_DECODE_VALUE,
                    "An error occurred while attempting to decode the " +
                    "intermediate response value:  %s.");
    registerMessage(MSGID_LDAP_INTERMEDIATE_RESPONSE_INVALID_ELEMENT_TYPE,
                    "The intermediate response sequence element contained " +
                    "an invalid BER type %s that was not appropriate for " +
                    "either the OID or the value.");


    registerMessage(MSGID_MVFILTER_INVALID_LDAP_FILTER_TYPE,
                    "The provided LDAP filter \"%s\" cannot be used as a " +
                    "matched values filter because filters of type %s are " +
                    "not allowed for use in matched values filters.");
    registerMessage(MSGID_MVFILTER_INVALID_DN_ATTRIBUTES_FLAG,
                    "The provided LDAP filter \"%s\" cannot be used as a " +
                    "matched values filter because it is an extensible " +
                    "match filter that contains the dnAttributes flag, which " +
                    "is not allowed for matched values filters.");
    registerMessage(MSGID_MVFILTER_INVALID_AVA_SEQUENCE_SIZE,
                    "The provided matched values filter could not be decoded " +
                    "because there were an invalid number of elements in the " +
                    "attribute value assertion (expected 2, got %d).");
    registerMessage(MSGID_MVFILTER_CANNOT_DECODE_AVA,
                    "An error occurred while attempting to decode the " +
                    "attribute value assertion in the provided matched " +
                    "values filter:  %s.");
    registerMessage(MSGID_MVFILTER_INVALID_SUBSTRING_SEQUENCE_SIZE,
                    "The provided matched values filter could not be decoded " +
                    "because there were an invalid number of elements " +
                    "in the substring sequence (expected 2, got %d).");
    registerMessage(MSGID_MVFILTER_NO_SUBSTRING_ELEMENTS,
                    "The provided matched values filter could not be decoded " +
                    "because there were no subInitial, subAny, or subFinal " +
                    "components in the substring filter.");
    registerMessage(MSGID_MVFILTER_MULTIPLE_SUBINITIALS,
                    "The provided matched values filter could not be decoded " +
                    "because there were multiple subInitial components in " +
                    "the substring filter.");
    registerMessage(MSGID_MVFILTER_MULTIPLE_SUBFINALS,
                    "The provided matched values filter could not be decoded " +
                    "because there were multiple subFinal components in " +
                    "the substring filter.");
    registerMessage(MSGID_MVFILTER_INVALID_SUBSTRING_ELEMENT_TYPE,
                    "The provided matched values filter could not be decoded " +
                    "because there was an invalid element of type %s in the " +
                    "substring filter.");
    registerMessage(MSGID_MVFILTER_CANNOT_DECODE_SUBSTRINGS,
                    "The provided matched values filter could not be decoded " +
                    "because an error occurred while decoding the " +
                    "substring filter component:  %s.");
    registerMessage(MSGID_MVFILTER_CANNOT_DECODE_PRESENT_TYPE,
                    "The provided matched values filter could not be decoded " +
                    "because an error occurred while decoding the presence " +
                    "filter component:  %s.");
    registerMessage(MSGID_MVFILTER_INVALID_EXTENSIBLE_SEQUENCE_SIZE,
                    "The provided matched values filter could not be decoded " +
                    "because there were an invalid number of elements in the " +
                    "extensible match sequence (expected 2 or 3, found %d).");
    registerMessage(MSGID_MVFILTER_MULTIPLE_MATCHING_RULE_IDS,
                    "The provided matched values filter could not be decoded " +
                    "because there were multiple matching rule ID elements " +
                    "found in the extensible match filter sequence.");
    registerMessage(MSGID_MVFILTER_MULTIPLE_ATTRIBUTE_TYPES,
                    "The provided matched values filter could not be decoded " +
                    "because there were multiple attribute type elements " +
                    "found in the extensible match filter sequence.");
    registerMessage(MSGID_MVFILTER_MULTIPLE_ASSERTION_VALUES,
                    "The provided matched values filter could not be decoded " +
                    "because there were multiple assertion value elements " +
                    "found in the extensible match filter sequence.");
    registerMessage(MSGID_MVFILTER_INVALID_EXTENSIBLE_ELEMENT_TYPE,
                    "The provided matched values filter could not be decoded " +
                    "because there was an invalid element of type %s in the " +
                    "extensible match filter.");
    registerMessage(MSGID_MVFILTER_CANNOT_DECODE_EXTENSIBLE_MATCH,
                    "The provided matched values filter could not be decoded " +
                    "because an error occurred while decoding the extensible " +
                    "match filter component:  %s.");
    registerMessage(MSGID_MVFILTER_INVALID_ELEMENT_TYPE,
                    "The provided matched values filter could not be decoded " +
                    "because it had an invalid BER type of %s.");


    registerMessage(MSGID_MATCHEDVALUES_NO_CONTROL_VALUE,
                    "Cannot decode the provided matched values control "+
                    "because it does not have a value.");
    registerMessage(MSGID_MATCHEDVALUES_CANNOT_DECODE_VALUE_AS_SEQUENCE,
                    "Cannot decode the provided matched values control " +
                    "because an error occurred while attempting to decode " +
                    "the value as an ASN.1 sequence:  %s.");
    registerMessage(MSGID_MATCHEDVALUES_NO_FILTERS,
                    "Cannot decode the provided matched values control " +
                    "because the control value does not specify any filters " +
                    "for use in matching attribute values.");


    registerMessage(MSGID_PWEXPIRED_CONTROL_HAS_VALUE,
                    "Cannot decode the provided control as a password " +
                    "expired control because the provided control had a " +
                    "value but the password expired control should not have " +
                    "a value.");


    registerMessage(MSGID_PWEXPIRING_NO_CONTROL_VALUE,
                    "Cannot decode the provided password expiring control "+
                    "because it does not have a value.");
    registerMessage(MSGID_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION,
                    "Cannot decode the provided control as a password " +
                    "expiring control because an error occurred while " +
                    "attempting to decode the number of seconds until " +
                    "expiration:  %s.");


    registerMessage(MSGID_JMX_CONNHANDLER_DESCRIPTION_LISTEN_PORT,
            "Specifies the TCP port on which this connection handler " +
            "may accept administrative connections.  Changes to this " +
            "configuration attribute will not take effect until the " +
            "connection handler is disabled and re-enabled, or until " +
            "the Directory Server is restarted.");
    registerMessage(MSGID_JMX_CONNHANDLER_NO_LISTEN_PORT,
            "No listen port was defined using configuration " +
            ATTR_LISTEN_PORT + " in configuration entry %s.  This is " +
            "a required attribute.");
    registerMessage(MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_LISTEN_PORT,
            "An unexpected error occurred while processing the " +
            ATTR_LISTEN_PORT + " attribute in configuration entry " +
            "%s, which is used to specify the port on which to " +
            "listen for client connections:  %s.");
    registerMessage(MSGID_JMX_CONNHANDLER_DESCRIPTION_USE_SSL,
            "Indicates whether this connection handler should use " +
            "SSL when accepting connections from clients.  Changes " +
            "to this configuration attribute will not take effect " +
            "until the connection handler is disabled and " +
            "re-enabled, or until the Directory Server is restarted.");
    registerMessage(MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_USE_SSL,
            "An unexpected error occurred while processing the " +
            ATTR_USE_SSL + " attribute in configuration entry %s, " +
            "which is used to indicate whether to use SSL when " +
            "accepting client connections:  %s.");
    registerMessage(MSGID_JMX_CONNHANDLER_DESCRIPTION_SSL_CERT_NICKNAME,
            "Specifies the nickname of the certificate that the " +
            "connection handler should use when accepting SSL-based " +
            "connections or performing StartTLS negotiation.  " +
            "Changes to this configuration attribute will not take " +
            "effect until the connection handler is disabled and " +
            "re-enabled, or until the Directory Server is restarted.");
    registerMessage(MSGID_JMX_CONNHANDLER_CANNOT_DETERMINE_SSL_CERT_NICKNAME,
            "An unexpected error occurred while processing the " +
            ATTR_SSL_CERT_NICKNAME + " attribute in configuration " +
            "entry %s, which is used to specify the nickname of the " +
            "certificate to use for accepting SSL/TSL connections:  " +
            "%s.");


    registerMessage(MSGID_PWPOLICYREQ_CONTROL_HAS_VALUE,
                    "Cannot decode the provided control as a password policy " +
                    "request control because the provided control had a " +
                    "value but the password policy request control should " +
                    "not have a value.");


    registerMessage(MSGID_PWPOLICYRES_NO_CONTROL_VALUE,
                    "Cannot decode the provided password policy response " +
                    "control because it does not have a value.");
    registerMessage(MSGID_PWPOLICYRES_INVALID_WARNING_TYPE,
                    "Cannot decode the provided password policy response " +
                    "control because the warning element has an invalid " +
                    "type of %s.");
    registerMessage(MSGID_PWPOLICYRES_INVALID_ERROR_TYPE,
                    "Cannot decode the provided password policy response " +
                    "control because the error element has an invalid type " +
                    "of %d.");
    registerMessage(MSGID_PWPOLICYRES_INVALID_ELEMENT_TYPE,
                    "Cannot decode the provided password policy response " +
                    "control because the value sequence has an element with " +
                    "an invalid type of %s.");
    registerMessage(MSGID_PWPOLICYRES_DECODE_ERROR,
                    "Cannot decode the provided password policy response " +
                    "control:  %s.");


    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_EXPIRED,
                    "passwordExpired");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_ACCOUNT_LOCKED,
                    "accountLocked");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_CHANGE_AFTER_RESET,
                    "changeAfterReset");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_MOD_NOT_ALLOWED,
                    "passwordModNotAllowed");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_MUST_SUPPLY_OLD_PASSWORD,
                    "mustSupplyOldPassword");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_INSUFFICIENT_PASSWORD_QUALITY,
                    "insufficientPasswordQuality");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_SHORT,
                    "passwordTooShort");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_TOO_YOUNG,
                    "passwordTooYoung");
    registerMessage(MSGID_PWPERRTYPE_DESCRIPTION_PASSWORD_IN_HISTORY,
                    "passwordInHistory");


    registerMessage(MSGID_PWPWARNTYPE_DESCRIPTION_TIME_BEFORE_EXPIRATION,
                    "timeBeforeExpiration");
    registerMessage(MSGID_PWPWARNTYPE_DESCRIPTION_GRACE_LOGINS_REMAINING,
                    "graceAuthNsRemaining");


    registerMessage(MSGID_ACCTUSABLEREQ_CONTROL_HAS_VALUE,
                    "Cannot decode the provided control as an account " +
                    "availability request control because the provided " +
                    "control had a value but the account availability " +
                    "request control should not have a value.");


    registerMessage(MSGID_ACCTUSABLERES_NO_CONTROL_VALUE,
                    "Cannot decode the provided account availability " +
                    "response control because it does not have a value.");
    registerMessage(MSGID_ACCTUSABLERES_UNKNOWN_UNAVAILABLE_TYPE,
                    "The account availability response control indicated " +
                    "that the account was unavailable but had an unknown " +
                    "unavailable element type of %s.");
    registerMessage(MSGID_ACCTUSABLERES_UNKNOWN_VALUE_ELEMENT_TYPE,
                    "The account availability response control had an " +
                    "unknown ACCOUNT_USABLE_RESPONSE element type of %s.");
    registerMessage(MSGID_ACCTUSABLERES_DECODE_ERROR,
            "Cannot decode the provided account availability " +
            "response control:  %s.");
    registerMessage(MSGID_ADDRESSMASK_PREFIX_DECODE_ERROR,
            "Cannot decode the provided address mask prefix because an" +
            "invalid value was specified. The permitted values for IPv4" +
            "are 0 to32 and for IPv6 0 to128");
    registerMessage(MSGID_ADDRESSMASK_WILDCARD_DECODE_ERROR,
            "Cannot decode the provided address mask because an prefix mask"+
            "was specified with an wild card \"*\" match character.");
    registerMessage(MSGID_ADDRESSMASK_FORMAT_DECODE_ERROR,
            "Cannot decode the provided address mask because the it has an" +
            "invalid format.");
  }
}

